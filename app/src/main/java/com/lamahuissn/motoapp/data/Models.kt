package com.lamahuissn.motoapp.data

/**
 * The wire shapes, as the office's API actually sends them (copied from live responses, 2026-10-04).
 *
 * Two rules are visible in the types themselves and are not accidents:
 *
 *  - **Money is a String.** `"37.50"` arrives as text and is displayed as text. Parsing it into a
 *    Double on the phone would be the first step towards the phone deciding a balance, and the phone
 *    never decides a balance.
 *  - **A month carries its own label.** `period` is `"2026-10"` for the app to compare with, and
 *    `label` is «تشرين الأول 2026» for it to print — the app never builds an Arabic month name itself.
 */

data class Session(val token: String, val user: MotoUser)

data class MotoUser(
    val id: Int,
    val name: String,
    val username: String?,
    val role: String,
    val locale: String?,
    val phone: String?,
) {
    val isAdmin: Boolean get() = role == "admin"
}

/**
 * The areas board for one cycle.
 *
 * `period` is `"2026-10"` — the server sends no Arabic label here (it does on every house's unpaid
 * months, which is where a label is read aloud), so this screen shows the cycle as the machine writes
 * it and the houses carry the month names.
 */
data class AreasBoard(val period: String, val areas: List<MotoArea>)


data class MotoArea(
    /** Null for the bucket the API adds for houses whose area was removed (`mobile.no_area`). */
    val id: Int?,
    val name: String,
    val houses: Int,
    val active: Int,
    val owed: String,
    val readingDone: Int,
    val readingTotal: Int,
) {
    val owes: Boolean get() = owed != "0.00"
    val readingLeft: Int get() = (readingTotal - readingDone).coerceAtLeast(0)
}

data class AreaSheet(val period: String, val areaId: Int, val areaName: String, val houses: List<House>)

data class House(
    val id: Int,
    val name: String,
    val phone: String?,
    val type: String,
    val status: String,
    val areaName: String,
    val meterNo: String?,
    val exempt: Boolean,
    val exemptReason: String?,
    val flatFee: String?,
    val maintenanceFee: String,
    val discountPercent: String,
    val balance: String,
    val credit: String,
    val unpaidMonths: List<UnpaidMonth>,
    val unpaidMonthsCount: Int,
    val periodInvoiced: Boolean,
    val readingDone: Boolean,
    val readingValue: String?,
    val readingEstimated: Boolean,
    /** What the meter stood at last time: the figure the collector reads back at the door. */
    val previousValue: String,
) {
    val isMetered: Boolean get() = type == "metered"
    val isFlat: Boolean get() = type == "flat_rate"
    val owes: Boolean get() = balance != "0.00"

    /**
     * A flat house whose cycle has not been billed owes nothing yet — «لا شيء يُقبض» is a different
     * fact from «صفر», and at the door those two must not look the same.
     */
    val flatAndNotBilled: Boolean get() = isFlat && !periodInvoiced && !owes
}

data class UnpaidMonth(val period: String, val label: String, val remaining: String, val type: String)
