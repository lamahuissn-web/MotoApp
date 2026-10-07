package com.lamahuissn.motoapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The warning that must arrive while the man is still standing at the meter.
 *
 * It mirrors the office's rule exactly — three times the household's own average, nothing counted from an
 * estimated month, whole kilowatt-hours — because a preview that disagrees with the judge is worse than no
 * preview: the collector would learn the warning is noise, and press through it.
 */
class ReadingSuspicionTest {

    /** A household whose last three real months were 50, 60 and 70 kWh — average 60, like the office's. */
    private fun house(
        readings: String = """
            {"id":31,"period":"2026-10","label":"تشرين الأول 2026","previous":"120","current":"190",
             "consumption":"50","estimated":false,"reader":"سليم","read_on":"2026-10-06"},
            {"id":30,"period":"2026-09","label":"أيلول 2026","previous":"60","current":"120",
             "consumption":"60","estimated":false,"reader":"سليم","read_on":"2026-09-06"},
            {"id":29,"period":"2026-08","label":"آب 2026","previous":"0","current":"60",
             "consumption":"70","estimated":false,"reader":"سليم","read_on":"2026-08-06"},
            {"id":28,"period":"2026-07","label":"تموز 2026","previous":"0","current":"400",
             "consumption":"400","estimated":true,"reader":null,"read_on":"2026-07-06"}
        """,
        period: String? = "2026-11",
        previousValue: String = "190",
    ): House {
        val body = """
            {"house":{"id":9,"name":"بيت العيّنة","phone":null,"type":"metered","status":"active",
             "area_name":"شارع السيسو","meter_no":"M-9002","exempt":false,"exempt_reason":null,
             "flat_fee":null,"maintenance_fee":"0.00","discount_percent":"0.00","balance":"60.00",
             "credit":"0.00","unpaid_months":[],"unpaid_months_count":0,"period_invoiced":false,
             "period":${if (period == null) "null" else "\"$period\""},"reading_done":false,"reading_value":null,"reading_estimated":false,
             "previous_value":"$previousValue","readings":[$readings]}}
        """.trimIndent()

        return MotoApi.parseHouseBody(body)!!
    }

    @Test
    fun `the average is this household's own three real months, never an estimate`() {
        // 400 kWh was an ESTIMATE (a broken meter month). Counting it would raise this house's "normal" to
        // 143 and hide a real fault — the office ignores estimates, and so must the phone.
        assertEquals(60.0, ReadingSuspicion.average(house(), "2026-11")!!, 0.001)
    }

    @Test
    fun `the cycle being read is not part of its own average`() {
        // With the cycle being read excluded, only the two months before it count: (60 + 70) / 2. The
        // office filters `period < the one being entered`, and a preview that averaged the month it is
        // judging would always be too high.
        assertEquals(65.0, ReadingSuspicion.average(house(period = "2026-10"), "2026-10")!!, 0.001)
    }

    @Test
    fun `an ordinary month is silent`() {
        // 190 -> 250: 60 kWh, exactly this house's average. No warning at all: a man who is warned every
        // month presses through the one month that mattered.
        assertNull(ReadingSuspicion.check(house(), "250"))
    }

    @Test
    fun `a number at exactly three times the average is still ordinary`() {
        // The office's rule is STRICTLY greater than 3x (180). 190 -> 370 is 180 kWh, and it passes.
        assertNull(ReadingSuspicion.check(house(), "370"))

        val one = ReadingSuspicion.check(house(), "371")
        assertNotNull(one)
        assertEquals(ReadingSuspicion.Verdict.JUMP, one!!.verdict)
        assertEquals(181.0, one.consumption, 0.001)
        assertEquals(60.0, one.average!!, 0.001)
    }

    @Test
    fun `the owner's own case warns while he types`() {
        // The screenshot he sent: 10,000 against a house that burns 60. The warning must exist BEFORE the
        // save button is pressed — that is the whole point of asking on the phone.
        val warning = ReadingSuspicion.check(house(), "10000")

        assertNotNull(warning)
        assertEquals(ReadingSuspicion.Verdict.JUMP, warning!!.verdict)
        assertEquals(9810.0, warning.consumption, 0.001)
    }

    @Test
    fun `a meter that has not moved is a question`() {
        val same = ReadingSuspicion.check(house(), "190")

        assertNotNull(same)
        assertEquals(ReadingSuspicion.Verdict.ZERO, same!!.verdict)
    }

    @Test
    fun `a value below the last one is left to the engine, never flashed while typing`() {
        // The engine refuses a lower value outright (no question, no confirmation), and «1» on the way to
        // «188» is indistinguishable from a dropped digit: warning on it would warn on every first keystroke.
        assertNull(ReadingSuspicion.check(house(), "150"))
        assertNull(ReadingSuspicion.check(house(), "1"))
    }

    @Test
    fun `half-typed digits and unknown houses say nothing`() {
        // An empty box and a half-typed value are not numbers a man meant to send.
        assertNull(ReadingSuspicion.check(house(), ""))
        assertNull(ReadingSuspicion.check(house(), "19."))

        // A house with no history has no average to judge against — and a warning invented from nothing is
        // the one kind this app cannot afford.
        assertNull(ReadingSuspicion.check(house(readings = ""), "5000"))

        // No cycle known: nothing to filter against, so nothing is claimed.
        assertNull(ReadingSuspicion.check(house(period = null), "5000"))
    }

    @Test
    fun `a house that just moved in fits inside three times its own average`() {
        // One single past month of 40 kWh: a genuine 100 (a new air conditioner) must pass. Three times a
        // small number is still a small number, and the office judges the same way.
        val fresh = house(
            readings = """{"id":1,"period":"2026-10","label":"تشرين الأول 2026","previous":"0",
                "current":"40","consumption":"40","estimated":false,"reader":"سليم","read_on":"2026-10-06"}""",
            previousValue = "40",
        )

        assertNull(ReadingSuspicion.check(fresh, "140"))
        assertNotNull(ReadingSuspicion.check(fresh, "200"))
    }
}
