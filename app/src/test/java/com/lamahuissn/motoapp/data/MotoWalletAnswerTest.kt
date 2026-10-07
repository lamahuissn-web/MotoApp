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
    fun `his review carries his receipts and the invoices his readings produced`() {
        val body = """
            {"user":{"id":3,"name":"سليم ميعاري","role":"collector"},"period":"2026-10",
             "label":"تشرين الأول 2026",
             "money":{"in_hand":"70.00","today_total":"40.00","today_count":2,"month_total":"70.00",
                      "month_count":3,"handed_total":"0.00"},
             "readings":{"today":1,"cycle":1},
             "receipts":[
               {"id":41,"number":"PAY-2026-10-0009","subscriber_id":12,"subscriber":"بيت العيّنة",
                "amount":"25.00","collected_on":"2026-10-06"}],
             "invoices":[
               {"id":88,"number":"INV-2026-10-0023","subscriber_id":12,"subscriber":"بيت العيّنة",
                "period":"2026-10","label":"تشرين الأول 2026","total":"45.00","consumption":"120.00",
                "remaining":"30.00"}],
             "handovers":[]}
        """.trimIndent()

        val wallet = MotoApi.parseWallet(body)!!

        assertEquals(1, wallet.receipts.size)
        assertEquals(12, wallet.receipts[0].houseId)
        assertEquals("بيت العيّنة", wallet.receipts[0].house)
        assertEquals("25.00", wallet.receipts[0].amount)
        assertEquals("2026-10-06", wallet.receipts[0].collectedOn)

        assertEquals(1, wallet.invoices.size)
        assertEquals("تشرين الأول 2026", wallet.invoices[0].label)
        assertEquals("45.00", wallet.invoices[0].total)
        assertEquals("120.00", wallet.invoices[0].consumption)
        // What is still open on his invoice — the office's figure, not the phone's subtraction.
        assertEquals("30.00", wallet.invoices[0].remaining)
    }

    @Test
    fun `a purse with no review is still a purse`() {
        // An older answer (or a collector who has just signed in): the keys are simply absent.
        val body = """
            {"user":{"id":3,"name":"سليم ميعاري","role":"collector"},"period":"2026-10","label":"تشرين الأول 2026",
             "money":{"in_hand":"0.00","today_total":"0.00","today_count":0,"month_total":"0.00",
                      "month_count":0,"handed_total":"0.00"},
             "readings":{"today":0,"cycle":0}}
        """.trimIndent()

        val wallet = MotoApi.parseWallet(body)!!

        assertEquals(0, wallet.receipts.size)
        assertEquals(0, wallet.invoices.size)
    }

    @Test
    fun `the words of an old receipt are read, and an empty one is refused`() {
        val text = MotoApi.parseReceiptText(
            """{"receipt_text":"شركة المولّد\nإيصال قبض · PAY-2026-10-0009\nالبيت: بيت العيّنة",
                "payment":{"id":41,"number":"PAY-2026-10-0009","amount":"25.00","collector_id":3}}""",
        )

        assertNotNull(text)
        assertTrue(text!!.contains("PAY-2026-10-0009"))

        // A receipt with no words is not a receipt: nothing is sent rather than an empty message.
        assertNull(MotoApi.parseReceiptText("""{"payment":{"id":41}}"""))
        assertNull(MotoApi.parseReceiptText("not json at all"))
    }

    @Test
    fun `an answer that is not a purse is refused, not guessed at`() {
        assertNull(MotoApi.parseWallet("""{"period":"2026-10"}"""))
        assertNull(MotoApi.parseWallet("not json at all"))
    }
}
