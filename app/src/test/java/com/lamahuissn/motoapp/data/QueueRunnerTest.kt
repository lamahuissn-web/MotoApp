package com.lamahuissn.motoapp.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The walk that sends the queue — the same code the app runs with a screen open and Android runs with the
 * phone in a pocket.
 *
 * The sender is injected, so the money rules can be proven without a server. What is being proven is what
 * BECAME of each row: applied, refused, waiting, and above all that a walk that loses the network stops
 * there instead of inventing answers for the rest.
 */
class QueueRunnerTest {

    private fun store(): WriteQueueStore {
        val dir = Files.createTempDirectory("moto-runner").toFile()
        return WriteQueueStore(File(dir, WriteQueueStore.FILE_NAME))
    }

    private fun entry(
        key: String,
        houseId: Int = 10,
        userId: Int = 1,
        kind: QueuedWrite.Kind = QueuedWrite.Kind.Collection,
        createdAt: String = "2026-10-05T18:20:00",
    ) = QueuedWrite(
        kind = kind,
        key = key,
        userId = userId,
        houseId = houseId,
        houseName = "بيت $houseId",
        createdAt = createdAt,
        collectedOn = "2026-10-05",
        amount = "6.25",
        currency = "usd",
    )

    private fun refusal(code: String?, message: String?) = ApiCall.Refused(
        ServerAnswer(httpStatus = 422, code = code, message = message, reachable = true, trouble = null),
    )

    @Test
    fun `an applied row becomes done`() = runBlocking {
        val store = store()
        store.save(listOf(entry("moto-applied-abcdef")))

        val report = QueueRunner.drain(store, userId = 1, token = "t") { _, _ -> ApiCall.Success("ok") }

        assertEquals(1, report.applied)
        assertEquals(0, report.stillOwed)
        assertTrue(report.reached)
        assertEquals(QueueState.Done, store.load().single().state)
    }

    @Test
    fun `a refusal needs a human, and the walk continues past it`() = runBlocking {
        val store = store()
        store.save(listOf(entry("moto-refused-abcdef"), entry("moto-next-abcdefgh")))

        val report = QueueRunner.drain(store, userId = 1, token = "t") { row, _ ->
            if (row.key == "moto-refused-abcdef") refusal("refuse.period_not_invoiced", "لا فاتورة") else ApiCall.Success("ok")
        }

        val rows = store.load().associateBy { it.key }
        assertEquals(1, report.refused)
        assertEquals(1, report.applied)
        assertEquals(0, report.stillOwed)
        assertEquals(QueueState.NeedsReconciliation, rows.getValue("moto-refused-abcdef").state)
        assertEquals("لا فاتورة", rows.getValue("moto-refused-abcdef").lastError)
        assertEquals(QueueState.Done, rows.getValue("moto-next-abcdefgh").state)
    }

    @Test
    fun `losing the network stops the walk and leaves the rest waiting`() = runBlocking {
        val store = store()
        store.save(listOf(entry("moto-first-abcdefg"), entry("moto-second-abcdef")))

        var sent = 0
        val report = QueueRunner.drain(store, userId = 1, token = "t") { _, _ ->
            sent++
            ApiCall.Unreachable("dead spot")
        }

        assertEquals(1, sent)                  // the second row is not thrown at the same dead spot
        assertFalse(report.reached)
        assertEquals(2, report.stillOwed)

        val rows = store.load().associateBy { it.key }
        // The row that was tried and got no answer waits; the one never tried keeps its place untouched.
        assertEquals(QueueState.Waiting, rows.getValue("moto-first-abcdefg").state)
        assertEquals(QueueState.Recorded, rows.getValue("moto-second-abcdef").state)
        assertNull(rows.getValue("moto-first-abcdefg").receiptText)
    }

    @Test
    fun `an expired session is not a refusal about the money`() = runBlocking {
        val store = store()
        store.save(listOf(entry("moto-expired-abcdef")))

        val report = QueueRunner.drain(store, userId = 1, token = "t") { _, _ ->
            refusal("unauthenticated", "انتهت الجلسة")
        }

        // Waiting, not «needs reconciliation»: nothing was said about the payment, and the same key goes
        // out again as soon as the collector signs in.
        assertEquals(0, report.refused)
        assertEquals(1, report.stillOwed)
        assertEquals(QueueState.Waiting, store.load().single().state)
    }

    @Test
    fun `another collector's rows are never sent by this one`() = runBlocking {
        val store = store()
        store.save(listOf(entry("moto-mine-abcdefgh", userId = 1), entry("moto-his-abcdefghi", userId = 2)))

        val seen = mutableListOf<String>()
        QueueRunner.drain(store, userId = 1, token = "t") { row, _ ->
            seen += row.key
            ApiCall.Success("ok")
        }

        assertEquals(listOf("moto-mine-abcdefgh"), seen)
        assertEquals(QueueState.Recorded, store.load().first { it.userId == 2 }.state)
    }

    @Test
    fun `every row goes out with the key it was born with, oldest first`() = runBlocking {
        val store = store()
        store.save(
            listOf(
                entry("moto-older-abcdefghi", createdAt = "2026-10-05T08:00:00"),
                entry("moto-newer-abcdefghi", createdAt = "2026-10-05T19:00:00"),
            ),
        )

        val sent = mutableListOf<String>()
        QueueRunner.drain(store, userId = 1, token = "t") { row, _ ->
            sent += row.key
            ApiCall.Success("ok")
        }

        assertEquals(listOf("moto-older-abcdefghi", "moto-newer-abcdefghi"), sent)
        // Same keys in the file afterwards: a retry is never a new attempt.
        assertEquals(setOf("moto-older-abcdefghi", "moto-newer-abcdefghi"), store.load().map { it.key }.toSet())
    }

    @Test
    fun `nothing owed is nothing sent`() = runBlocking {
        val store = store()
        store.save(listOf(entry("moto-done-abcdefgh").copy(state = QueueState.Done)))

        var sent = 0
        val report = QueueRunner.drain(store, userId = 1, token = "t") { _, _ ->
            sent++
            ApiCall.Success("ok")
        }

        assertEquals(0, sent)
        assertEquals(0, report.settled)
        assertTrue(report.reached)
    }
}
