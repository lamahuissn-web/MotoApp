package com.lamahuissn.motoapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The collector's purse as the office sends it (spec 011).
 *
 * The app does not compute any of this. These tests pin the reading of it: the day, the month, the cash in
 * the pocket, his readings and the handovers he signed for — and the one thing the screen has to know how to
 * say, which is «لا حركة بعد».
 */
class MotoWalletAnswerTest {
    private val purse = """
        {"user":{"id":3,"name":"سليم ميعاري","role":"collector"},
         "period":"2026-10","label":"تشرين الأول 2026",
         "money":{"in_hand":"973.63","today_total":"43.75","today_count":2,
                  "month_total":"973.63","month_count":24,"handed_total":"50.00"},
         "readings":{"today":3,"cycle":12},
         "handovers":[{"date":"2026-10-05","amount":"50.00","note":null,"received_by":"المدير"}]}
    """.trimIndent()

    @Test
    fun `a purse carries the day, the month and the cash in his pocket`() {
        val wallet = MotoApi.parseWallet(purse)

        assertNotNull(wallet)
        assertEquals("سليم ميعاري", wallet!!.name)
        assertEquals("2026-10", wallet.period)
        assertEquals("تشرين الأول 2026", wallet.label)

        // The figure that runs his day, and it is the OFFICE'S: the phone never adds it up.
        assertEquals("973.63", wallet.inHand)
        assertEquals("43.75", wallet.todayTotal)
        assertEquals(2, wallet.todayCount)
        assertEquals("973.63", wallet.monthTotal)
        assertEquals(24, wallet.monthCount)
        assertEquals("50.00", wallet.handedTotal)

        assertEquals(3, wallet.readingsToday)
        assertEquals(12, wallet.readingsCycle)
    }

    @Test
    fun `a handover remembers who signed for it`() {
        val wallet = MotoApi.parseWallet(purse)!!

        assertEquals(1, wallet.handovers.size)
        assertEquals("2026-10-05", wallet.handovers[0].date)
        assertEquals("50.00", wallet.handovers[0].amount)
        assertEquals("المدير", wallet.handovers[0].receivedBy)
    }

    @Test
    fun `a collector who has done nothing is untouched, not broken`() {
        val empty = """
            {"user":{"id":9,"name":"جابٍ جديد","role":"collector"},"period":"2026-10",
             "label":"تشرين الأول 2026",
             "money":{"in_hand":"0.00","today_total":"0.00","today_count":0,"month_total":"0.00",
                      "month_count":0,"handed_total":"0.00"},
             "readings":{"today":0,"cycle":0},"handovers":[]}
        """.trimIndent()

        val wallet = MotoApi.parseWallet(empty)!!

        assertEquals("0.00", wallet.inHand)
        assertTrue(wallet.handovers.isEmpty())
        assertTrue(wallet.untouched)
    }

    @Test
    fun `a purse with a handover is not untouched even when it is empty`() {
        // He handed everything in: the pocket is zero and the day is the record of it.
        val settled = """
            {"user":{"id":9,"name":"جابٍ سلّم","role":"collector"},"period":"2026-10","label":"تشرين الأول 2026",
             "money":{"in_hand":"0.00","today_total":"0.00","today_count":0,"month_total":"10.00",
                      "month_count":1,"handed_total":"10.00"},
             "readings":{"today":0,"cycle":0},
             "handovers":[{"date":"2026-10-05","amount":"10.00","note":null,"received_by":"المدير"}]}
        """.trimIndent()

        assertFalse(MotoApi.parseWallet(settled)!!.untouched)
    }

    @Test
    fun `an answer that is not a purse is refused, not guessed at`() {
        assertNull(MotoApi.parseWallet("""{"period":"2026-10"}"""))
        assertNull(MotoApi.parseWallet("not json at all"))
    }
}
