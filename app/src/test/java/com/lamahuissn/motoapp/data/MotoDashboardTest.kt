package com.lamahuissn.motoapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dashboard the office already runs, and the search that goes with it.
 *
 * The bodies below are the shape `/api/v1/areas` and `/api/v1/search` produce (the panel's own
 * presenter), which is why the assertions are about what a collector reads — his takings, the cash in
 * his pocket, the dates behind it — and not about field names.
 */
class MotoDashboardTest {

    private val boardBody = """
        {"period":"2026-10",
         "areas":[{"id":1,"name":"شارع الدعبول","houses":6,"active":6,"owed":"6.25","reading_done":6,"reading_total":6}],
         "stats":{"today":{"total":"37.50","count":1},
                  "month":{"total":"967.38","count":23},
                  "in_hand":"967.38",
                  "handed_total":"0.00",
                  "handovers":[{"date":"2026-10-01","amount":"100.00","note":"تسليم الصباح"}]}}
    """.trimIndent()

    private val searchBody = """
        {"query":"أبو علي",
         "results":[{"id":7,"name":"بيت أبو علي","meter_no":"M-4478","type":"metered","status":"active","route":"شارع الدعبول","balance":"6.25","credit":"0.00","owes":true},
                    {"id":9,"name":"بيت موقوف","meter_no":null,"type":"flat_rate","status":"inactive","route":"شارع شريف","balance":"0.00","credit":"0.00","owes":false}]}
    """.trimIndent()

    @Test
    fun `the boards day carries his takings and the cash in his pocket`() {
        val stats = MotoApi.parseBoard(boardBody)!!.stats!!

        assertEquals("37.50", stats.todayTotal)
        assertEquals(1, stats.todayCount)
        assertEquals("967.38", stats.monthTotal)
        assertEquals(23, stats.monthCount)
        assertEquals("967.38", stats.inHand)
        assertEquals("0.00", stats.handedTotal)

        assertEquals(1, stats.handovers.size)
        assertEquals("2026-10-01", stats.handovers.first().date)
        assertEquals("100.00", stats.handovers.first().amount)
        assertEquals("تسليم الصباح", stats.handovers.first().note)
    }

    @Test
    fun `a panel that has not been updated leaves the day unstated rather than zeroed`() {
        // No stats key: the app shows the areas it has and says nothing about money. «0.00» would be a
        // statement about the collector's pocket that this build has no right to make.
        val board = MotoApi.parseBoard("""{"period":"2026-10","areas":[]}""")!!

        assertNull(board.stats)
    }

    @Test
    fun `a search answer carries the door and what it owes`() {
        val hits = MotoApi.parseSearch(searchBody)!!

        assertEquals(2, hits.size)

        val (first, second) = hits
        assertEquals(7, first.id)
        assertEquals("بيت أبو علي", first.name)
        assertEquals("M-4478", first.meterNo)
        assertEquals("شارع الدعبول", first.route)
        assertEquals("6.25", first.balance)
        assertTrue(first.owes)
        assertTrue(first.isMetered)

        assertNull(second.meterNo)
        assertFalse(second.isMetered)
        assertEquals("inactive", second.status)
    }

    @Test
    fun `an answer that is not a search answer is refused rather than half-read`() {
        assertNull(MotoApi.parseSearch("""{"query":"ب"}"""))
        assertNull(MotoApi.parseSearch("not json"))
    }
}
