package com.lamahuissn.motoapp.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** What one drain of the queue did — what the office took, what it refused, and what is still owed. */
data class DrainReport(
    val applied: Int,
    val refused: Int,
    val stillOwed: Int,
    val reached: Boolean,
) {
    val sentAnything: Boolean get() = applied > 0
    val settled: Int get() = applied + refused
}

/**
 * The queue, drained — by the app while its screens are open, and by Android's own job while the phone is
 * in the collector's pocket (spec 010, phase B).
 *
 * It lives outside the view model because TWO callers must behave identically: a second implementation
 * reached from WorkManager would be a second set of money rules, and the rules here are the ones that
 * decide whether a man is holding unrecorded cash. The lock is shared by both callers, so two drains can
 * never offer the same row at the same moment.
 *
 * What it does NOT do is decide anything about the money: each row carries what the collector did, each
 * row keeps the key it was born with, and the server decides. This walks, writes down the answers, and
 * stops at the first row the network did not answer — ten failures in a row are one fact.
 */
object QueueRunner {

    private val draining = Mutex()

    /**
     * Send everything this collector still owes, oldest first.
     *
     * `send` is last so a caller can pass it as the trailing lambda, and it is injectable so the rules
     * above can be proven without a server — the tests hand it a table of answers and check what became
     * of the queue.
     */
    suspend fun drain(
        store: WriteQueueStore,
        userId: Int,
        token: String,
        onSettled: (QueuedWrite) -> Unit = {},
        send: suspend (QueuedWrite, String) -> ApiCall<*> = ::carry,
    ): DrainReport = draining.withLock {
        val rows = store.load().toMutableList()
        val owed = QueueDecision.openEntries(QueueDecision.forUser(rows, userId))

        if (owed.isEmpty()) {
            return@withLock DrainReport(applied = 0, refused = 0, stillOwed = 0, reached = true)
        }

        var applied = 0
        var refused = 0
        var reached = true

        for (entry in owed) {
            val settled = settleTo(entry, send(entry, token))

            val index = rows.indexOfFirst { it.key == settled.key }
            if (index >= 0) rows[index] = settled else rows.add(settled)

            onSettled(settled)

            when (settled.state) {
                QueueState.Done -> applied++
                QueueState.NeedsReconciliation -> refused++
                else -> {
                    // No answer: almost always the network. The rest of the queue keeps its place.
                    reached = false
                    break
                }
            }
        }

        // One write of the whole list, after the walk. The file is the record; the screens read it.
        store.save(rows)

        DrainReport(
            applied = applied,
            refused = refused,
            stillOwed = QueueDecision.openEntries(QueueDecision.forUser(rows, userId)).size,
            reached = reached,
        )
    }

    /**
     * What the office's answer means for the row it was sent for.
     *
     * This is the one place that decides, and the decisions are money decisions:
     *
     *  - applied → `done`, and the receipt the office worded is kept on the row;
     *  - refused → `needs_reconciliation`, and it never retries itself;
     *  - **an expired session is not a refusal** — nothing was said about the money, so the row waits for
     *    the next sign-in rather than being marked as a problem for a human;
     *  - an answer the app cannot read is not an answer about money either: the row stays owed and goes out
     *    again with the same key, which the office reads as the same attempt.
     */
    fun settleTo(entry: QueuedWrite, call: ApiCall<*>): QueuedWrite = when (call) {
        is ApiCall.Success<*> -> QueueDecision.afterSuccess(entry, (call.value as? CollectionAnswer)?.receiptText)

        is ApiCall.Refused -> when {
            call.answer.code.orEmpty() in listOf("unauthenticated", "account_disabled") ->
                QueueDecision.afterUnreachable(entry, call.answer.message.orEmpty())

            // A question about a reading (spec 013) is not a refusal: the row stays owed, and the sentence
            // travels back to the screen where the number was typed. The office's words are the warning.
            call.answer.code.orEmpty().startsWith("refuse.anomaly_") ->
                QueueDecision.afterQuestion(entry, call.answer.message.orEmpty())

            else -> QueueDecision.afterRefusal(entry, call.answer.message)
        }

        is ApiCall.Unreadable -> QueueDecision.afterUnreachable(entry, call.answer.message.orEmpty())

        is ApiCall.Unreachable -> QueueDecision.afterUnreachable(entry, call.trouble)
    }

    /** One row as the call that carries it. The key it already has travels with it, every time. */
    suspend fun carry(entry: QueuedWrite, token: String): ApiCall<*> = when (entry.kind) {
        QueuedWrite.Kind.Reading -> if (entry.estimatedKwh != null) {
            MotoApi.estimate(
                token = token,
                subscriberId = entry.houseId,
                period = entry.period ?: currentPeriod(),
                kwh = entry.estimatedKwh,
                reason = entry.reason.orEmpty(),
                key = entry.key,
            )
        } else {
            MotoApi.read(
                token = token,
                subscriberId = entry.houseId,
                period = entry.period ?: currentPeriod(),
                currentReading = entry.currentReading.orEmpty(),
                note = entry.note,
                key = entry.key,
                confirmedAnomaly = entry.confirmedAnomaly,
            )
        }

        QueuedWrite.Kind.Collection -> MotoApi.collect(
            token = token,
            subscriberId = entry.houseId,
            amount = entry.amount.orEmpty(),
            currency = entry.currency ?: "usd",
            rate = entry.rate,
            method = if (entry.currency == "lbp") "cash_lbp" else "cash_usd",
            note = entry.note,
            // The day the money was TAKEN, written down when it was taken — a payment collected at eleven
            // at night and sent in the morning still belongs to the night it was collected.
            collectedOn = entry.collectedOn ?: LocalDate.now().toString(),
            key = entry.key,
        )
    }

    private fun currentPeriod(): String = LocalDate.now().toString().substring(0, 7)
}
