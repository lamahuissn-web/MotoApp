package com.lamahuissn.motoapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The table the whole queue rests on: what an answer means, and whether the walk continues.
 *
 * All of it is decidable without a server, and it must be — the one rule that may never break is that a
 * refusal does not become another attempt, and «it broke in the field» is not an acceptable way to find
 * that out.
 */
class QueueDecisionTest {

    private fun entry(
        key: String = "moto-abc123def456",
        kind: QueuedWrite.Kind = QueuedWrite.Kind.Collection,
        state: QueueState = QueueState.Waiting,
    ) = QueuedWrite(
        kind = kind,
        key = key,
        userId = 1,
        houseId = 10,
        houseName = "بيت أبو علي",
        createdAt = "2026-10-05T18:20:00",
        collectedOn = "2026-10-05",
        amount = "6.25",
        currency = "usd",
        state = state,
    )

    @Test
    fun `an applied collection becomes done and keeps the office's receipt`() {
        val done = QueueDecision.afterSuccess(entry(), "وصلنا منكم 6.25$")

        assertEquals(QueueState.Done, done.state)
        assertEquals("وصلنا منكم 6.25$", done.receiptText)
        assertNull(done.lastError)
        assertEquals("moto-abc123def456", done.key)
        assertFalse(done.isOpen)
    }

    @Test
    fun `an answer with no receipt does not erase a receipt already held`() {
        val first = QueueDecision.afterSuccess(entry(), "وصلنا منكم 6.25$")
        val again = QueueDecision.afterSuccess(first, null)

        assertEquals("وصلنا منكم 6.25$", again.receiptText)
    }

    @Test
    fun `a refusal needs a human, keeps its reason, and never retries itself`() {
        val refused = QueueDecision.afterRefusal(entry(), "لا فاتورة غير مدفوعة على هذا البيت")

        assertEquals(QueueState.NeedsReconciliation, refused.state)
        assertEquals("لا فاتورة غير مدفوعة على هذا البيت", refused.lastError)
        assertEquals("moto-abc123def456", refused.key)
        assertFalse(refused.isOpen)
    }

    @Test
    fun `no answer keeps the attempt waiting, with its key`() {
        val waiting = QueueDecision.afterUnreachable(entry(), "لا شبكة")

        assertEquals(QueueState.Waiting, waiting.state)
        assertEquals("لا شبكة", waiting.lastError)
        assertEquals("moto-abc123def456", waiting.key)
        assertTrue(waiting.isOpen)
    }

    @Test
    fun `the walk continues past a settled entry and stops at an unanswered one`() {
        assertTrue(QueueDecision.continueAfter(QueueDecision.afterSuccess(entry(), null)))
        assertTrue(QueueDecision.continueAfter(QueueDecision.afterRefusal(entry(), "سبب")))
        assertFalse(QueueDecision.continueAfter(QueueDecision.afterUnreachable(entry(), "لا شبكة")))
    }

    @Test
    fun `only what the office has not answered is still owed`() {
        val queue = listOf(
            entry(key = "moto-recorded-one-1", state = QueueState.Recorded),
            entry(key = "moto-waiting-one-11", state = QueueState.Waiting),
            entry(key = "moto-done-one-11111", state = QueueState.Done),
            entry(key = "moto-refused-one-111", state = QueueState.NeedsReconciliation),
            entry(key = "moto-discarded-one-11", state = QueueState.Discarded),
        )

        assertEquals(
            listOf("moto-recorded-one-1", "moto-waiting-one-11"),
            QueueDecision.openEntries(queue).map { it.key },
        )
    }

    @Test
    fun `the flush takes the oldest thing owed, not the newest`() {
        val queue = listOf(
            entry(key = "moto-oldest-abcdef", state = QueueState.Done),
            entry(key = "moto-second-abcdef", state = QueueState.Waiting),
            entry(key = "moto-third-abcdefg", state = QueueState.Recorded),
        )

        assertEquals("moto-second-abcdef", QueueDecision.next(queue)?.key)
        assertNull(QueueDecision.next(listOf(entry(state = QueueState.Done))))
        assertNull(QueueDecision.next(emptyList()))
    }

    @Test
    fun `the same write pressed twice is recognised as one attempt`() {
        val first = entry()
        val same = first.copy(key = "moto-a-different-key-1")

        assertTrue(first.sameWriteAs(same))

        // A different amount, a different currency, a different note or a different house is a different
        // write — and the collector is owed a second receipt for it.
        assertFalse(first.sameWriteAs(same.copy(amount = "7.00")))
        assertFalse(first.sameWriteAs(same.copy(currency = "lbp")))
        assertFalse(first.sameWriteAs(same.copy(note = "دفعة ثانية")))
        assertFalse(first.sameWriteAs(same.copy(houseId = 11)))
        assertFalse(first.sameWriteAs(same.copy(kind = QueuedWrite.Kind.Reading, amount = null)))
    }

    @Test
    fun `a reading and a collection are told apart for the money rules`() {
        assertTrue(entry(kind = QueuedWrite.Kind.Collection).isMoney)
        assertFalse(entry(kind = QueuedWrite.Kind.Reading).isMoney)
    }
}
