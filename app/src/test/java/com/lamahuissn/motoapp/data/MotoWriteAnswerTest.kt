package com.lamahuissn.motoapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The answers to the two writes, and the key that makes a retry a retry.
 *
 * The bodies below are the shape the office's API produces for a reading and a collection (the panel's
 * own presenter, `App\Services\ApiPayload`), not a live capture: taking a live one would mean writing
 * money on the office's own ledger to produce a fixture. The key tests are the ones that matter — the
 * shape is checked against the presenter, the KEY is checked against the server's rule, because a
 * malformed key is a 422 the collector cannot fix from a door.
 */
class MotoWriteAnswerTest {

    private val readingBody = """
        {"reading":{"id":9,"subscriber_id":1,"period":"2026-10","label":"تشرين الأول 2026","current_reading":"1150.00","previous_reading":"1000.00","consumption":"150.00","estimated":false,"note":null,"entered_by":2,"created_at":null},
         "bill":{"id":21,"number":"INV-2026-10-0001","period":"2026-10","label":"تشرين الأول 2026","type":"metered","consumption":"150.00","kwh_price":"0.2500","usage_amount":"37.50","maintenance_fee":"15.00","flat_amount":"0.00","credit_applied":"0.00","credit_created":"0.00","subsidy_amount":"0.00","discount_amount":"0.00","total":"52.50","paid_amount":"0.00"},
         "house":{"id":1,"balance":"52.50","credit":"0.00"},
         "replay":false}
    """.trimIndent()

    private val collectionBody = """
        {"payment":{"id":7,"number":"REC-2026-10-0007","subscriber_id":1,"collector_id":2,"amount":"100.00","applied":"60.00","credited":"40.00","excess":"40.00","method":"cash_usd","collected_on":"2026-10-04","lbp_amount":null,"lbp_rate":null,"note":null,"created_at":null},
         "settled":[{"bill_id":3,"period":"2026-09","label":"أيلول 2026","amount":"60.00","remaining":"0.00"}],
         "house":{"id":1,"balance":"0.00","credit":"40.00"},
         "receipt_url":"/api/v1/receipts/7",
         "replay":false}
    """.trimIndent()

    private val liraBody = """
        {"payment":{"id":8,"number":"REC-2026-10-0008","subscriber_id":1,"collector_id":2,"amount":"60.00","applied":"60.00","credited":"0.00","excess":"0.00","method":"cash_lbp","collected_on":"2026-10-04","lbp_amount":5400000,"lbp_rate":90000.0,"note":null,"created_at":null},
         "settled":[{"bill_id":3,"period":"2026-09","label":"أيلول 2026","amount":"60.00","remaining":"0.00"}],
         "house":{"id":1,"balance":"0.00","credit":"0.00"},
         "receipt_url":"/api/v1/receipts/8",
         "replay":false}
    """.trimIndent()

    @Test
    fun `a reading's answer carries the invoice it raised and the balance it left`() {
        val answer = MotoApi.parseReading(readingBody)

        assertNotNull(answer)
        assertEquals("150.00", answer!!.reading.consumption)
        assertEquals("1000.00", answer.reading.previousReading)
        assertFalse(answer.reading.estimated)
        assertEquals("INV-2026-10-0001", answer.bill.number)
        assertEquals("52.50", answer.bill.total)
        assertEquals("تشرين الأول 2026", answer.bill.label)
        assertEquals("52.50", answer.house.balance)
        assertFalse(answer.replay)
    }

    @Test
    fun `a collection's answer says which months it closed and what waited on his account`() {
        val answer = MotoApi.parseCollection(collectionBody)

        assertNotNull(answer)
        assertEquals("REC-2026-10-0007", answer!!.payment.number)
        assertEquals("100.00", answer.payment.amount)
        assertEquals("60.00", answer.payment.applied)
        assertEquals("40.00", answer.payment.credited)
        assertEquals("2026-10-04", answer.payment.collectedOn)

        assertEquals(1, answer.settled.size)
        assertEquals("أيلول 2026", answer.settled.first().label)
        assertEquals("0.00", answer.settled.first().remaining)

        assertEquals("40.00", answer.house.credit)
        assertEquals("/api/v1/receipts/7", answer.receiptUrl)
    }

    @Test
    fun `a lira collection keeps the lira that was handed over, not only the dollars it settled`() {
        val answer = MotoApi.parseCollection(liraBody)!!

        assertEquals("cash_lbp", answer.payment.method)
        assertEquals(5_400_000L, answer.payment.lbpAmount)
        assertEquals(90_000.0, answer.payment.lbpRate!!, 0.001)
        // The dollars are what the ledger stores; the lira is what the household actually handed over.
        assertEquals("60.00", answer.payment.applied)
    }

    @Test
    fun `an answer that is not the shape the office sends is refused rather than half-read`() {
        assertNull(MotoApi.parseReading("""{"bill":{"total":"10.00"}}"""))
        assertNull(MotoApi.parseCollection("""{"payment":{"number":"REC-1"}}"""))
        assertNull(MotoApi.parseReading("not json"))
    }

    @Test
    fun `every attempt key fits the server's own rule`() {
        val keys = (1..200).map { AttemptKey.next() }

        keys.forEach { key ->
            assertTrue("the key must be accepted by the API: $key", AttemptKey.isValid(key))
        }

        // One key per attempt, or two attempts look like a replay of each other.
        assertEquals(200, keys.toSet().size)
    }

    @Test
    fun `a key that would be refused by the server is refused here first`() {
        assertFalse(AttemptKey.isValid("abc"))
        assertFalse(AttemptKey.isValid("has a space in it"))
        assertFalse(AttemptKey.isValid("slash/and/back\\slash"))
        assertFalse(AttemptKey.isValid("x".repeat(129)))
        assertTrue(AttemptKey.isValid("x".repeat(16)))
    }
}
