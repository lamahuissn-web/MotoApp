package com.lamahuissn.motoapp.data

/**
 * The wire shapes, as the office's API actually sends them (copied from live responses, 2026-10-04).
 *
 * Three rules are visible in the types themselves and are not accidents:
 *
 *  - **Money is a String.** `"37.50"` arrives as text and is displayed as text. Parsing it into a
 *    Double on the phone would be the first step towards the phone deciding a balance, and the phone
 *    never decides a balance.
 *  - **A month carries its own label.** `period` is `"2026-10"` for the app to compare with, and
 *    `label` is «تشرين الأول 2026» for it to print — the app never builds an Arabic month name itself.
 *  - **The rate belongs to the office.** `lbpRate` is null when the office has set none, and null is
 *    not zero: a phone that converted at a rate it invented would be inventing money.
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

/** The areas board for one cycle. The label is the month's name in the requester's language. */
data class AreasBoard(
    val period: String,
    val label: String?,
    val lbpRate: Double?,
    val areas: List<MotoArea>,
    /** The collector's own day: what he took and what is still in his pocket. */
    val stats: CollectorStats? = null,
) {
    val periodText: String get() = label ?: period
}

/**
 * One collector's own money, as the office's dashboard shows it.
 *
 * Every figure is the server's — totals, counts and the cash position all come from the panel's own
 * service, so the phone cannot hold a second version of «today».
 */
data class CollectorStats(
    val todayTotal: String,
    val todayCount: Int,
    val monthTotal: String,
    val monthCount: Int,
    val inHand: String,
    val handedTotal: String,
    val handovers: List<Handover>,
)

/** Cash he has already walked to the office: a date, an amount, and whatever he wrote with it. */
data class Handover(val date: String?, val amount: String, val note: String?)

/**
 * The manager's board (spec 009, phase A) — the office's month, and what every collector is holding.
 *
 * Read-only by decision: a manager away from his desk reads, he does not post. Every figure is the
 * office's own (OfficeBoard / CollectionService / CollectorStats), so the desk and the pocket cannot
 * disagree about the month.
 */
data class ManagerBoard(
    val period: String,
    val label: String,
    val invoiced: String,
    val collected: String,
    val rate: Double,
    val ratePrevious: Double,
    val outstanding: String,
    val outstandingInactive: String,
    val active: Int,
    val metered: Int,
    val flat: Int,
    val kwh: String,
    val kwhPrice: String,
    val collectors: List<ManagerCollector>,
    val recentPayments: List<RecentPayment>,
)

/** Cash a collector handed to the office, as the office recorded it (spec 009, phase B). */
data class HandoverAnswer(
    val id: Int,
    val collectorId: Int,
    val collector: String,
    val amount: String,
    val date: String,
    val note: String?,
    /**
     * What the collector still holds afterwards — the figure the office reconciles its drawer against.
     * It is the server's, not the phone's: the phone never subtracts.
     */
    val collectorInHand: String,
)

/**
 * A collector's own purse (spec 011) — the screen about HIM rather than about a house.
 *
 * Every figure is the office's: the day, the month, and the cash in his pocket are computed by
 * `CollectorStats` and `CollectionService::inHand` on the server. The phone displays them and never adds
 * them up — two "today" figures for one man is a dispute, not a report.
 */
data class WalletSummary(
    val name: String,
    val period: String,
    val label: String?,
    val inHand: String,
    val todayTotal: String,
    val todayCount: Int,
    val monthTotal: String,
    val monthCount: Int,
    val handedTotal: String,
    val readingsToday: Int,
    val readingsCycle: Int,
    val handovers: List<WalletHandover> = emptyList(),
    /** His own review (spec 011 §7): the lines behind the cash, and what his readings produced. */
    val receipts: List<WalletReceipt> = emptyList(),
    val invoices: List<WalletInvoice> = emptyList(),
) {
    /** A purse with nothing in it and nothing done — the screen says so instead of a wall of zeroes. */
    val untouched: Boolean
        get() = inHand == "0.00" && todayCount == 0 && monthCount == 0 &&
            handedTotal == "0.00" && readingsToday == 0 && readingsCycle == 0 && handovers.isEmpty()
}

/** One of his own receipts: the line behind the cash he is carrying. */
data class WalletReceipt(
    val id: Int,
    val number: String?,
    val houseId: Int,
    val house: String?,
    val amount: String,
    val collectedOn: String?,
)

/**
 * One invoice his reading produced this cycle.
 *
 * The link is the engine's (`bills.reading_id`) — the phone never guesses which invoice followed which
 * reading. `remaining` is the office's figure: what is still open on it, or nothing if it is settled.
 */
data class WalletInvoice(
    val id: Int,
    val number: String?,
    val houseId: Int,
    val house: String?,
    val label: String?,
    val total: String,
    val consumption: String?,
    val remaining: String,
)

/** One handover the office signed for. */
data class WalletHandover(
    val date: String?,
    val amount: String,
    val note: String?,
    val receivedBy: String?,
)

/** A receipt that was taken back, as the office recorded it (spec 009, phase C). */
data class ReversalAnswer(
    val id: Int,
    val number: String?,
    val subscriberId: Int,
    val amount: String,
    val collector: String?,
    val collectedOn: String?,
    /**
     * The household's balance after the receipt went, as the office computes it.
     * Never the phone's: reversing a payment makes a house owe again, and the phone must not guess by how much.
     */
    val subscriberBalance: String,
)

/** One collector as the manager sees him: his day, his cycle, and the cash the office is waiting for. */
data class ManagerCollector(
    val id: Int,
    val name: String,
    val todayTotal: String,
    val todayCount: Int,
    val collected: String,
    val paymentsCount: Int,
    val handed: String,
    val inHand: String,
) {
    val holds: Boolean get() = inHand != "0.00"
}

data class RecentPayment(
    val id: Int,
    val subscriberId: Int,
    val number: String?,
    val amount: String,
    val subscriber: String?,
    val collector: String?,
    val collectedOn: String?,
)

/** One household found by search: enough to recognise the door before walking to it. */
data class SearchHit(
    val id: Int,
    val name: String,
    val meterNo: String?,
    val type: String,
    val status: String,
    val route: String?,
    val balance: String,
    val credit: String,
    val owes: Boolean,
) {
    val isMetered: Boolean get() = type == "metered"
}

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

data class AreaSheet(
    val period: String,
    val label: String?,
    val lbpRate: Double?,
    val areaId: Int,
    val areaName: String,
    val houses: List<House>,
) {
    val periodText: String get() = label ?: period
}

data class House(
    val id: Int,
    /**
     * The cycle this file is read as of (`2026-10`) — the month the office is standing in.
     *
     * The phone needs it for one sentence: which row of the meter log is «هذا الشهر», so a log that
     * repeats this month's figures under this month's card does not read as a duplicate.
     */
    val period: String = "",
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
    /** Only the house detail carries the invoices; the cards in a list do not. */
    val bills: List<BillLine> = emptyList(),
    /**
     * The household's receipts, newest first — only the file carries them (spec 009, phase D).
     *
     * This is the log a person reads before deciding anything about a house: what he paid, when, who took it,
     * and which months it cleared. Empty for a house that never paid, and empty on the cards in a list.
     */
    val payments: List<HouseReceipt> = emptyList(),
    /**
     * Every month this meter has been read, newest first — only the file carries them.
     *
     * A household argues about a reading more often than about anything else, and the collector standing at
     * the meter needs last month's figure while he types this month's.
     */
    val readings: List<ReadingLogRow> = emptyList(),
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

/** One month of the meter's log (spec 009, phase D): the pair, what it burned, and whether it was an estimate. */
data class ReadingLogRow(
    val id: Int,
    val period: String,
    val label: String,
    val previous: String,
    val current: String,
    val consumption: String?,
    val estimated: Boolean,
    val reader: String?,
    val readOn: String?,
)

/** What one receipt cleared, month by month — what a household disputes. */
data class SettledLine(val period: String?, val label: String?, val amount: String)

/**
 * One receipt in a household's file (spec 009, phase D).
 *
 * Everything here comes from the office as text: the amount was never the phone's to read as a number, and
 * the lira was handed over at the rate stored on this very receipt — never re-converted at today's.
 */
data class HouseReceipt(
    val id: Int,
    val number: String?,
    val amount: String,
    val method: String,
    val methodLabel: String?,
    val collectedOn: String?,
    val collector: String?,
    /** Who took it: the rule for sending a receipt again is the office's, and it is about this id. */
    val collectorId: Int?,
    val note: String?,
    val lbpAmount: Int?,
    val settled: List<SettledLine> = emptyList(),
)

/** One invoice as the house detail carries it. */
data class BillLine(
    val id: Int,
    val number: String?,
    val period: String,
    val label: String,
    val type: String,
    val total: String,
    val paid: String,
    val remaining: String,
    val consumption: String?,
    val discount: String,
    val subsidy: String,
)

/** The three small facts every write answers with: what the house owes NOW. */
data class HouseSummary(val id: Int, val balance: String, val credit: String)

data class ReadingRow(
    val id: Int,
    val period: String,
    val label: String,
    val currentReading: String,
    val previousReading: String,
    val consumption: String,
    val estimated: Boolean,
    val note: String?,
    val enteredBy: Int?,
)

/** What a reading produced: the row, the invoice it raised, and the house's new balance. */
data class ReadingAnswer(
    val reading: ReadingRow,
    val bill: BillLine,
    val house: HouseSummary,
    val replay: Boolean,
)

data class PaymentRow(
    val id: Int,
    val number: String,
    val amount: String,
    val applied: String,
    val credited: String,
    val method: String,
    val collectedOn: String,
    val lbpAmount: Long?,
    val lbpRate: Double?,
)

/** The months a receipt settled, with what is left on each afterwards. */
data class SettledMonth(
    val billId: Int,
    val period: String?,
    val label: String?,
    val amount: String,
    val remaining: String,
)

/** What a collection produced: the receipt, the months it closed, and the house's new balance. */
data class CollectionAnswer(
    val payment: PaymentRow,
    val settled: List<SettledMonth>,
    val house: HouseSummary,
    val receiptUrl: String,
    /** The receipt as the office words it, ready to hand to the household's own phone. */
    val receiptText: String? = null,
    val replay: Boolean,
)
