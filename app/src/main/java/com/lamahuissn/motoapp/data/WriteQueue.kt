package com.lamahuissn.motoapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The queue of writes the office has not seen yet — the collector's work, on disk.
 *
 * A collector works where there is no signal, and the app used to hold ONE attempt in memory. A phone
 * killed in a basement then lost the record of a collection whose cash is in the collector's pocket:
 * not a failed request, a **missing entry in the books**. So every confirmed write is written to disk
 * BEFORE it is sent, and it stays there until the office itself says what became of it.
 *
 * The four states, and what each means for the money:
 *
 *  - `Recorded`  — on disk, not sent yet (the app was offline when it was taken).
 *  - `Waiting`   — sent, no answer: no signal, no tunnel, a timeout. The SAME key goes out next time,
 *                  because the office charges once per key and a retry is not a second attempt.
 *  - `Done`      — the office applied it. The receipt it worded is stored here so it can still be
 *                  handed to the household after the fact.
 *  - `NeedsReconciliation` — the office REFUSED it. It never retries itself: a human decides, because
 *                  the collector may be holding cash against a receipt the books do not have.
 *  - `Discarded` — a person cancelled it on purpose. It stays visible rather than disappearing: a
 *                  problem that can be deleted silently is a problem nobody can audit.
 */
enum class QueueState {
    Recorded,
    Waiting,
    Done,
    NeedsReconciliation,
    Discarded;

    /** Still the collector's problem — the office has not answered about it yet. */
    val isOpen: Boolean get() = this == Recorded || this == Waiting
}

/**
 * One recorded write.
 *
 * The identity of this row is its attempt `key`: one confirmed attempt, one key, for as long as the row
 * lives. Everything the replay needs is here, including the date the money was taken and the period the
 * reading was for — a write that waited for the network overnight must still be booked to the day at the
 * door, not to the morning it finally went out.
 */
data class QueuedWrite(
    val kind: Kind,
    val key: String,
    val userId: Int,
    val houseId: Int,
    val houseName: String,
    val createdAt: String,
    val period: String? = null,
    val collectedOn: String? = null,
    val currentReading: String? = null,
    val estimatedKwh: String? = null,
    val reason: String? = null,
    val note: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val rate: String? = null,
    val state: QueueState = QueueState.Recorded,
    val lastError: String? = null,
    val receiptText: String? = null,
) {
    enum class Kind { Reading, Collection }

    val isMoney: Boolean get() = kind == Kind.Collection

    /** Still owed to the office: nothing has answered for this row yet. */
    val isOpen: Boolean get() = state.isOpen

    /**
     * The same write, field for field.
     *
     * Used to recognise a second press of the same button while the first is still unanswered: that is
     * ONE attempt, and sending it under a new key would be a second receipt for money handed over once.
     */
    fun sameWriteAs(other: QueuedWrite): Boolean =
        kind == other.kind &&
            houseId == other.houseId &&
            currentReading == other.currentReading &&
            estimatedKwh == other.estimatedKwh &&
            amount == other.amount &&
            currency == other.currency &&
            rate == other.rate &&
            note == other.note
}

/** The queue as text — kept apart from the file so the part that can be WRONG is testable, like the session. */
object WriteQueueCodec {

    private const val VERSION = 1

    fun encode(entries: List<QueuedWrite>): String {
        val rows = JSONArray()

        entries.forEach { entry ->
            rows.put(
                JSONObject()
                    .put("kind", entry.kind.name.lowercase())
                    .put("key", entry.key)
                    .put("user_id", entry.userId)
                    .put("house_id", entry.houseId)
                    .put("house_name", entry.houseName)
                    .put("created_at", entry.createdAt)
                    .put("state", entry.state.toText())
                    .put("period", entry.period ?: JSONObject.NULL)
                    .put("collected_on", entry.collectedOn ?: JSONObject.NULL)
                    .put("current_reading", entry.currentReading ?: JSONObject.NULL)
                    .put("estimated_kwh", entry.estimatedKwh ?: JSONObject.NULL)
                    .put("reason", entry.reason ?: JSONObject.NULL)
                    .put("note", entry.note ?: JSONObject.NULL)
                    .put("amount", entry.amount ?: JSONObject.NULL)
                    .put("currency", entry.currency ?: JSONObject.NULL)
                    .put("rate", entry.rate ?: JSONObject.NULL)
                    .put("last_error", entry.lastError ?: JSONObject.NULL)
                    .put("receipt_text", entry.receiptText ?: JSONObject.NULL),
            )
        }

        return JSONObject().put("version", VERSION).put("entries", rows).toString()
    }

    /**
     * A queue that cannot be read is an EMPTY queue, never a crash: this file is read on every start, and
     * a half-written tail must not be able to stop the collector's app from opening. (An empty queue is
     * visible and can be acted on; an exception at startup is neither.)
     */
    fun decode(text: String?): List<QueuedWrite> {
        if (text.isNullOrBlank()) return emptyList()

        return runCatching {
            val rows = JSONObject(text).optJSONArray("entries") ?: return emptyList()
            (0 until rows.length()).mapNotNull { index -> row(rows.optJSONObject(index) ?: return@mapNotNull null) }
        }.getOrDefault(emptyList())
    }

    private fun row(json: JSONObject): QueuedWrite? {
        val kind = when (json.optString("kind")) {
            "reading" -> QueuedWrite.Kind.Reading
            "collection" -> QueuedWrite.Kind.Collection
            else -> return null
        }

        val key = json.optString("key")
        if (key.isEmpty()) return null

        return QueuedWrite(
            kind = kind,
            key = key,
            userId = json.optInt("user_id"),
            houseId = json.optInt("house_id"),
            houseName = json.optString("house_name"),
            createdAt = json.optString("created_at"),
            period = json.text("period"),
            collectedOn = json.text("collected_on"),
            currentReading = json.text("current_reading"),
            estimatedKwh = json.text("estimated_kwh"),
            reason = json.text("reason"),
            note = json.text("note"),
            amount = json.text("amount"),
            currency = json.text("currency"),
            rate = json.text("rate"),
            state = json.optString("state").toState(),
            lastError = json.text("last_error"),
            receiptText = json.text("receipt_text"),
        )
    }

    /**
     * How a state is written down.
     *
     * Written out explicitly rather than from `name.lowercase()`: that shortcut turns
     * `NeedsReconciliation` into «needsreconciliation», which does not match the reader's
     * «needs_reconciliation» — and a refused collection coming back as `Recorded` after a restart is a
     * collection the app would send a second time. A test proved exactly that before this shipped.
     */
    private fun QueueState.toText(): String = when (this) {
        QueueState.Recorded -> "recorded"
        QueueState.Waiting -> "waiting"
        QueueState.Done -> "done"
        QueueState.NeedsReconciliation -> "needs_reconciliation"
        QueueState.Discarded -> "discarded"
    }

    private fun String.toState(): QueueState = when (this) {
        "recorded" -> QueueState.Recorded
        "waiting" -> QueueState.Waiting
        "done" -> QueueState.Done
        "needs_reconciliation" -> QueueState.NeedsReconciliation
        "discarded" -> QueueState.Discarded
        else -> QueueState.Recorded
    }

    /** A JSON null and an absent key both mean «nothing here», never the text "null". */
    private fun JSONObject.text(name: String): String? =
        optString(name).takeIf { it.isNotEmpty() && it != "null" }
}

/**
 * The queue on disk.
 *
 * One small file, replaced whole through a temp file and a rename, so a phone that dies mid-write leaves
 * the PREVIOUS queue intact rather than half of a new one. Money on this file is worth more than the
 * milliseconds a database would save.
 */
class WriteQueueStore(private val file: File) {

    constructor(context: android.content.Context) : this(File(context.filesDir, FILE_NAME))

    @Synchronized
    fun load(): List<QueuedWrite> = WriteQueueCodec.decode(file.takeIf { it.exists() }?.readText())

    @Synchronized
    fun save(entries: List<QueuedWrite>) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(WriteQueueCodec.encode(entries))
        if (!temp.renameTo(file)) {
            // Some filesystems refuse the rename; the queue matters more than the trick.
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    companion object {
        const val FILE_NAME = "write-queue.json"
    }
}

/**
 * What an answer means for the entry it was sent for.
 *
 * Pure on purpose: this is the table the whole queue rests on, and it must be provable without a server,
 * a phone, or a network. The one rule it may never break — a refusal never turns into another attempt.
 */
object QueueDecision {

    fun afterSuccess(entry: QueuedWrite, receiptText: String?): QueuedWrite = entry.copy(
        state = QueueState.Done,
        lastError = null,
        receiptText = receiptText ?: entry.receiptText,
    )

    /** The office said no. Its reason is kept in the collector's own words so he can act on it. */
    fun afterRefusal(entry: QueuedWrite, message: String?): QueuedWrite = entry.copy(
        state = QueueState.NeedsReconciliation,
        lastError = message,
    )

    /** No answer at all: the key stays, and the next flush is the same attempt. */
    fun afterUnreachable(entry: QueuedWrite, trouble: String): QueuedWrite = entry.copy(
        state = QueueState.Waiting,
        lastError = trouble,
    )

    /**
     * Whether a flush keeps going after this entry.
     *
     * A settled entry (applied, or refused) says nothing about the next one, so the walk continues. An
     * unanswered one almost always means the network is gone — stopping there saves the rest of the
     * queue from being sent into the same dead spot, and none of them loses its place.
     */
    fun continueAfter(entry: QueuedWrite): Boolean =
        entry.state == QueueState.Done || entry.state == QueueState.NeedsReconciliation

    /** The entries a flush still owes the office, oldest first. Settled and discarded rows stay put. */
    fun openEntries(entries: List<QueuedWrite>): List<QueuedWrite> = entries.filter { it.state.isOpen }

    /**
     * The rows belonging to one collector.
     *
     * A queued write is sent under whatever token is on the phone, and the office attributes the receipt
     * to THAT token's owner — so a row recorded by one collector must never be flushed by another on a
     * shared phone. The file keeps everyone's rows; each session sees and sends only its own.
     */
    fun forUser(entries: List<QueuedWrite>, userId: Int): List<QueuedWrite> =
        entries.filter { it.userId == userId }

    /** The entry a flush should try next, or null when nothing is owed. */
    fun next(entries: List<QueuedWrite>): QueuedWrite? = openEntries(entries).firstOrNull()
}
