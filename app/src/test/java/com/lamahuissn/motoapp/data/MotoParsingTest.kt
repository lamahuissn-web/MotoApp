package com.lamahuissn.motoapp.data

import com.lamahuissn.motoapp.ui.Money
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire contract, against the bodies the RUNNING server actually returns.
 *
 * Every string below marked «live» was copied from a response to the office's own API on 2026-10-04
 * (an authenticated read-only call, then the token deleted). An invented fixture would prove the
 * parser agrees with my imagination; these prove it agrees with the server — and this is the place to
 * refresh the shapes when the API changes.
 */
class MotoParsingTest {

    private val areasBody = """{"period": "2026-10", "areas": [{"id": 2, "name": "شارع الدعبول", "houses": 6, "active": 6, "owed": "21.25", "reading_done": 4, "reading_total": 4}, {"id": 1, "name": "شارع السيسو", "houses": 6, "active": 6, "owed": "37.50", "reading_done": 4, "reading_total": 4}, {"id": 4, "name": "شارع الصفوري", "houses": 5, "active": 5, "owed": "45.00", "reading_done": 4, "reading_total": 4}, {"id": 3, "name": "شارع شريف", "houses": 5, "active": 5, "owed": "14.37", "reading_done": 3, "reading_total": 4}]}"""

    private val sheetBody = """{"period": "2026-10", "area": {"id": 1, "name": "شارع السيسو"}, "houses": [{"id": 5, "name": "مخبز الشارع", "phone": null, "address": null, "type": "flat_rate", "status": "active", "area_name": "شارع السيسو", "meter_no": "M-1005", "exempt": false, "exempt_reason": null, "flat_fee": "75.00", "maintenance_fee": "15.00", "discount_percent": "0.00", "balance": "37.50", "credit": "0.00", "unpaid_months": [{"period": "2026-10", "label": "تشرين الأول 2026", "remaining": "37.50", "type": "flat_rate"}], "unpaid_months_count": 1, "period_invoiced": true, "reading_done": false, "reading_value": null, "reading_estimated": false, "previous_value": "100.00"}, {"id": 1, "name": "أبو أحمد الحاج", "phone": null, "address": null, "type": "metered", "status": "active", "area_name": "شارع السيسو", "meter_no": "M-1001", "exempt": false, "exempt_reason": null, "flat_fee": null, "maintenance_fee": "15.00", "discount_percent": "0.00", "balance": "0.00", "credit": "0.00", "unpaid_months": [], "unpaid_months_count": 0, "period_invoiced": true, "reading_done": true, "reading_value": "220.00", "reading_estimated": false, "previous_value": "100.00"}, {"id": 6, "name": "بيت الحاج مصطفى", "phone": null, "address": null, "type": "flat_rate", "status": "active", "area_name": "شارع السيسو", "meter_no": "M-1006", "exempt": false, "exempt_reason": null, "flat_fee": "65.00", "maintenance_fee": "15.00", "discount_percent": "0.00", "balance": "0.00", "credit": "0.00", "unpaid_months": [], "unpaid_months_count": 0, "period_invoiced": true, "reading_done": false, "reading_value": null, "reading_estimated": false, "previous_value": "100.00"}]}"""

    private val meBody = """{"user": {"id": 1, "name": "المدير", "username": "admin", "role": "admin", "locale": "ar", "phone": null}}"""

    private val loginRefusal = """{"error": {"code": "auth_failed", "message": "البريد أو كلمة المرور غير صحيحة."}}"""

    /** Not a live capture: the shape a cycle produces before the monthly run has billed it. */
    private val notBilledBody = """{"period": "2026-11", "area": {"id": 1, "name": "شارع السيسو"}, "houses": [{"id": 9, "name": "بيت بلا فاتورة", "phone": null, "address": null, "type": "flat_rate", "status": "active", "area_name": "شارع السيسو", "meter_no": null, "exempt": false, "exempt_reason": null, "flat_fee": "60.00", "maintenance_fee": "0.00", "discount_percent": "0.00", "balance": "0.00", "credit": "0.00", "unpaid_months": [], "unpaid_months_count": 0, "period_invoiced": false, "reading_done": false, "reading_value": null, "reading_estimated": false, "previous_value": "0.00"}]}"""

    @Test
    fun `the board parses, and every figure arrives as text`() {
        val board = MotoApi.parseBoard(areasBody)

        assertNotNull(board)
        assertEquals("2026-10", board!!.period)
        assertEquals(4, board.areas.size)

        val first = board.areas.first()
        assertEquals("شارع الدعبول", first.name)
        assertEquals(6, first.houses)
        // A String, not a number: the app displays money, it never does arithmetic on it.
        assertEquals("21.25", first.owed)
        assertTrue(first.owes)
        assertEquals(4, first.readingDone)
        assertEquals(0, first.readingLeft)
    }

    @Test
    fun `a house owing a month carries the month's own label from the server`() {
        val house = MotoApi.parseSheet(sheetBody)!!.houses.first { it.unpaidMonthsCount > 0 }

        assertEquals("مخبز الشارع", house.name)
        assertEquals("37.50", house.balance)
        assertTrue(house.owes)
        assertEquals("تشرين الأول 2026", house.unpaidMonths.first().label)
        assertEquals("37.50", house.unpaidMonths.first().remaining)
        assertTrue(house.isFlat)
        assertFalse(house.isMetered)
    }

    @Test
    fun `a metered house says what the meter stood at and what it stands at now`() {
        val house = MotoApi.parseSheet(sheetBody)!!.houses.first { it.isMetered }

        assertEquals("220.00", house.readingValue)
        assertEquals("100.00", house.previousValue)
        assertTrue(house.readingDone)
        assertFalse(house.readingEstimated)
        assertEquals("M-1001", house.meterNo)
    }

    @Test
    fun `a flat house that has paid is not the same as one whose month was never billed`() {
        // Live: the office's own area, where every house carries this cycle's invoice.
        val paid = MotoApi.parseSheet(sheetBody)!!.houses.first { it.isFlat && it.unpaidMonths.isEmpty() }
        assertTrue(paid.periodInvoiced)
        assertFalse(paid.owes)
        assertFalse(paid.flatAndNotBilled)

        // The other state — a cycle the office has not billed yet — is read from the SERVER's own flag,
        // never inferred from a zero balance: «صفر» and «لا فاتورة لهذا الشهر» are two different
        // sentences at a door, and only one of them is true.
        val notBilled = MotoApi.parseSheet(notBilledBody)!!.houses.first()
        assertFalse(notBilled.periodInvoiced)
        assertFalse(notBilled.owes)
        assertTrue(notBilled.flatAndNotBilled)
    }

    @Test
    fun `the signed-in account parses with its role`() {
        val user = MotoApi.parseUser(JSONObject(meBody).getJSONObject("user"))

        assertEquals("المدير", user.name)
        assertEquals("admin", user.role)
        assertTrue(user.isAdmin)
    }

    @Test
    fun `a refused sign-in is read as the office's own sentence`() {
        val answer = MotoApi.readAnswer(422, loginRefusal)

        assertEquals("auth_failed", answer.code)
        assertEquals("البريد أو كلمة المرور غير صحيحة.", answer.message)
        assertFalse(answer.ok)
    }

    @Test
    fun `a session survives the round trip through the stored text`() {
        val session = Session(
            token = "12|abcdef",
            user = MotoUser(7, "جابر", "jaber", "collector", "ar", null),
        )

        val back = SessionCodec.decode(SessionCodec.encode(session))

        assertNotNull(back)
        assertEquals(session.token, back!!.token)
        assertEquals("جابر", back.user.name)
        assertEquals("collector", back.user.role)
        assertNull(back.user.phone)
    }

    @Test
    fun `a token with no user is not a session`() {
        assertNull(SessionCodec.decode("{\"token\": \"\"}"))
        assertNull(SessionCodec.decode("not json at all"))
    }

    @Test
    fun `money is displayed the way the invoices print it`() {
        // The isolate characters keep `$37.50` as one left-to-right run inside an Arabic sentence.
        assertEquals("\u2066$37.50\u2069", Money.usd("37.50"))
        assertEquals("\u2066LBP 5,400,000\u2069", Money.lira(5_400_000))
    }
}
