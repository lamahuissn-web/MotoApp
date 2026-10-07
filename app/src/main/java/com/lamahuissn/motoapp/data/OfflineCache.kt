package com.lamahuissn.motoapp.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime

/**
 * The office's last answer, kept on the phone — so a collector can OPEN the app with no network.
 *
 * Without this the app was useless the moment it was closed somewhere without signal: the areas board,
 * the area sheet and the household file lived in memory, so a cold start offline showed a red card and
 * nothing else — and the write queue could not help, because there was nothing on screen to record FROM.
 *
 * Two decisions make this safe enough to keep:
 *
 *  - **What is stored is the server's own response TEXT**, read back through the same parser that reads it
 *    live. Not a second shape of the same figures that could drift from the first one.
 *  - **Only successful answers are stored**, and every screen that shows a stored copy says so, with the
 *    time it was stored. A collector must never mistake an old balance for a fresh one — the office's own
 *    figure is what the desk will charge from, and it is the server that decides the money at write time.
 *
 * On a phone shared by two collectors this copy is cleared with the session that read it (the queue is
 * not: that is the collector's work, not the office's figures).
 */
class OfflineCache(private val root: File) {

    constructor(context: Context) : this(File(context.filesDir, DIR))

    /** The stored body for a key, or null when nothing was ever stored for this collector. */
    fun body(userId: Int, key: String): String? = read(userId, key)?.optString("body")?.takeIf { it.isNotEmpty() && it != "null" }

    /** When that body was stored, as text — what the screens show beside «نسخة محفوظة». */
    fun storedAt(userId: Int, key: String): String? =
        read(userId, key)?.optString("stored_at")?.takeIf { it.isNotEmpty() && it != "null" }

    fun put(userId: Int, key: String, body: String) {
        val file = file(userId, key)
        file.parentFile?.mkdirs()

        val text = JSONObject()
            .put("stored_at", LocalDateTime.now().withNano(0).toString())
            .put("body", body)
            .toString()

        // Same discipline as the queue: write a temp file and rename, so a phone that dies mid-write
        // leaves the PREVIOUS copy intact rather than half of a new one.
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(text)

        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
    }

    /** Forget everything stored for one collector. Called when his session ends. */
    fun clear(userId: Int) {
        directory(userId).deleteRecursively()
    }

    fun clearAll() {
        root.deleteRecursively()
    }

    private fun read(userId: Int, key: String): JSONObject? {
        val file = file(userId, key)
        if (!file.exists()) return null

        // A copy that cannot be read is NO copy — never an exception on the way into a screen.
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    private fun file(userId: Int, key: String) = File(directory(userId), "$key.json")

    private fun directory(userId: Int) = File(root, userId.toString())

    companion object {
        const val DIR = "offline"
        const val BOARD = "board"
        const val WALLET = "wallet"

        fun sheet(areaId: Int) = "sheet-$areaId"
        fun house(houseId: Int) = "house-$houseId"
    }
}
