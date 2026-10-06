package com.lamahuissn.motoapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manager's board as the office sends it (spec 009, phase A).
 *
 * The app does not compute any of this: the figures are the office's, and the app's only job is to read
 * them without turning a `null` into a zero or a string into a number of its own invention.
 */
class MotoManagerAnswerTest {

    private val board = """
        {
          "period": "2026-10",
          "label": "تشرين الأول 2026",
          "money": {
            "invoiced": "420.00",
            "collected": "152.50",
            "rate": 36.3,
            "rate_previous": 33.8,
            "outstanding": "267.50",
            "outstanding_inactive": "45.00"
          },
          "houses": { "active": 22, "metered": 16, "flat": 6 },
          "kwh": { "total": "1400.00", "price": "0.09" },
          "collectors": [
            {
              "id": 7, "name": "سليم ميعاري",
              "today_total": "43.75", "today_count": 2,
              "collected": "152.50", "payments_count": 24,
              "handed": "50.00", "in_hand": "102.50"
            }
          ],
          "recent_payments": [
            {
              "id": 91, "subscriber_id": 12, "number": "PAY-2026-10-0091",
              "amount": "18.75", "subscriber": "بيت أبو علي",
              "collector": "سليم ميعاري", "collected_on": "2026-10-05"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `the board carries the month, the houses and each collector's hand`() {
        val parsed = MotoApi.parseManagerBoard(board)!!

        assertEquals("2026-10", parsed.period)
        assertEquals("تشرين الأول 2026", parsed.label)
        assertEquals("420.00", parsed.invoiced)
        assertEquals(36.3, parsed.rate, 0.001)
        assertEquals(33.8, parsed.ratePrevious, 0.001)
        assertEquals("267.50", parsed.outstanding)
        assertEquals("45.00", parsed.outstandingInactive)
        assertEquals(22, parsed.active)
        assertEquals(16, parsed.metered)
        assertEquals(6, parsed.flat)
        assertEquals("1400.00", parsed.kwh)

        val collector = parsed.collectors.single()
        assertEquals(7, collector.id)
        assertEquals("سليم ميعاري", collector.name)
        assertEquals("43.75", collector.todayTotal)
        assertEquals(2, collector.todayCount)
        assertEquals("152.50", collector.collected)
        assertEquals("50.00", collector.handed)
        assertEquals("102.50", collector.inHand)
        assertTrue(collector.holds)

        val payment = parsed.recentPayments.single()
        assertEquals(12, payment.subscriberId)
        assertEquals("18.75", payment.amount)
        assertEquals("2026-10-05", payment.collectedOn)
    }

    @Test
    fun `a collector who handed everything over is not holding cash`() {
        val settled = board.replace("\"in_hand\": \"102.50\"", "\"in_hand\": \"0.00\"")

        assertFalse(MotoApi.parseManagerBoard(settled)!!.collectors.single().holds)
    }

    @Test
    fun `a board without collectors or receipts is still a board`() {
        val empty = """
            {
              "period": "2026-09", "label": "أيلول 2026",
              "money": {"invoiced": "0.00", "collected": "0.00", "rate": 0.0, "rate_previous": 0.0,
                        "outstanding": "0.00", "outstanding_inactive": "0.00"},
              "houses": {"active": 0, "metered": 0, "flat": 0},
              "kwh": {"total": "0.00", "price": "0.00"},
              "collectors": [], "recent_payments": []
            }
        """.trimIndent()

        val parsed = MotoApi.parseManagerBoard(empty)!!

        assertTrue(parsed.collectors.isEmpty())
        assertTrue(parsed.recentPayments.isEmpty())
        assertEquals(0.0, parsed.rate, 0.001)
    }

    @Test
    fun `a recorded handover reads back with the figure left in hand`() {
        val body = """
            {
              "handover": {
                "id": 7, "collector_id": 3, "collector": "سليم ميعاري",
                "amount": "25.00", "handover_date": "2026-10-06", "note": "نقد الأسبوع"
              },
              "collector_in_hand": "35.00",
              "replay": false
            }
        """.trimIndent()

        val answer = MotoApi.parseHandover(body)!!

        assertEquals(7, answer.id)
        assertEquals(3, answer.collectorId)
        assertEquals("سليم ميعاري", answer.collector)
        assertEquals("25.00", answer.amount)
        assertEquals("2026-10-06", answer.date)
        assertEquals("نقد الأسبوع", answer.note)
        // The office's own figure for what is still held — the phone never subtracts to find it.
        assertEquals("35.00", answer.collectorInHand)
    }

    @Test
    fun `a handover answer without a handover is refused, not guessed at`() {
        assertNull(MotoApi.parseHandover("""{"error":{"code":"refuse.handover_exceeds_in_hand"}}"""))
        assertNull(MotoApi.parseHandover("not json at all"))
    }

    @Test
    fun `an answer that is not a board is refused, not guessed at`() {
        assertNull(MotoApi.parseManagerBoard("""{"detail": "nope"}"""))
        assertNull(MotoApi.parseManagerBoard("not json at all"))
    }
}
