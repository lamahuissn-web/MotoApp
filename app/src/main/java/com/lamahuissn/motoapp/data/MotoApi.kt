package com.lamahuissn.motoapp.data

import com.lamahuissn.motoapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * The office's API, as this app uses it.
 *
 * The address is compiled in (`BuildConfig.API_BASE_URL`): the phone talks to the same host the
 * office's panel lives on, through the same tunnel — one door, one certificate, nothing for a
 * collector to configure at a door.
 *
 * Every call answers with one of four things (`ApiCall`), and the screens show them apart because
 * they mean different things to the man holding the phone: the server did the work, the server
 * refused and said why, the server answered in a shape this app cannot read, or the phone never
 * reached the server at all.
 *
 * The parsers are pure functions taking the body as text, so the contract can be tested against the
 * bodies the running server actually returns instead of against an invented one.
 */
object MotoApi {

    /**
     * Every request carries this.
     *
     * The office reads Arabic, and the server writes its refusals, its month labels and its
     * validation sentences at the moment it answers — so the language has to be asked for on the
     * request, not chosen on a screen. Without it the collector gets English sentences about his own
     * Arabic household.
     */
    const val LANGUAGE = "ar"

    /** No trailing slash, so `baseUrl + "api/v1/me"` is always a well-formed URL. */
    val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /* ------------------------------------------------------------------ calls */

    /** The door. A token per DEVICE: the office can retire one phone without changing a password. */
    suspend fun login(identity: String, password: String, device: String): ApiCall<Session> {
        val body = JSONObject()
            .put("identity", identity)
            .put("password", password)
            .put("device", device)
            .toString()

        return call(
            Request.Builder().url("$baseUrl/api/v1/login").post(body.toRequestBody(jsonMedia)).build(),
        ) { parseSession(it) }
    }

    suspend fun me(token: String): ApiCall<MotoUser> = call(
        Request.Builder().url("$baseUrl/api/v1/me").authorized(token).build(),
    ) { parseUser(JSONObject(it).optJSONObject("user") ?: JSONObject()) }

    /**
     * Signing out revokes THIS device's token on the server, not just on the phone.
     *
     * Clearing the phone alone would leave a working token behind wherever the phone had been; the
     * office's record of the device is the thing that has to change when a phone is handed on.
     */
    suspend fun logout(token: String): ApiCall<Boolean> = call(
        Request.Builder()
            .url("$baseUrl/api/v1/logout")
            .header("Accept", "application/json")
            .header("Accept-Language", LANGUAGE)
            .header("Authorization", "Bearer $token")
            .post("".toRequestBody(jsonMedia))
            .build(),
    ) { true }

    /** The areas, and what each one owes and has left to read. */
    suspend fun areas(token: String, period: String? = null, sink: ((String) -> Unit)? = null): ApiCall<AreasBoard> = call(
        Request.Builder().url("$baseUrl/api/v1/areas${periodQuery(period)}").authorized(token).build(),
    ) { body ->
        // The office's OWN answer, as it arrived, is what the phone keeps for a day without signal.
        sink?.invoke(body)
        parseBoard(body)
    }

    /**
     * One area's houses.
     *
     * This is the sheet the collector walks with: what each house owes, which months the figure is
     * made of, and whether the meter was read this cycle.
     */
    suspend fun houses(token: String, routeId: Int, period: String? = null, sink: ((String) -> Unit)? = null): ApiCall<AreaSheet> = call(
        Request.Builder().url("$baseUrl/api/v1/areas/$routeId/houses${periodQuery(period)}").authorized(token).build(),
    ) { body ->
        sink?.invoke(body)
        parseSheet(body)
    }

    /**
     * One household, from anywhere in the network.
     *
     * A collector in a street has a question («بيت أبو علي؟»), not a map: the answer carries the same
     * balance his own screen will show, so the two can never disagree.
     */
    suspend fun search(token: String, query: String): ApiCall<List<SearchHit>> {
        val url = "$baseUrl/api/v1/search?q=" + URLEncoder.encode(query, "UTF-8")

        return call(Request.Builder().url(url).authorized(token).build()) { parseSearch(it) }
    }

    /**
     * The manager's board (spec 009, phase A). Read-only, and behind `api.role:admin` on the server.
     */
    suspend fun managerBoard(token: String, period: String? = null): ApiCall<ManagerBoard> = call(
        Request.Builder().url("$baseUrl/api/v1/manager/board${periodQuery(period)}").authorized(token).build(),
    ) { parseManagerBoard(it) }

    /**
     * The signed-in user's own purse (spec 011) — his cash, his day, his readings, his handovers.
     *
     * Answered about whoever holds the token: the call carries no id, so a collector cannot ask about
     * another man's cash even by mistake.
     */
    suspend fun wallet(token: String, period: String? = null, sink: ((String) -> Unit)? = null): ApiCall<WalletSummary> = call(
        Request.Builder().url("$baseUrl/api/v1/me/summary${periodQuery(period)}").authorized(token).build(),
    ) { body ->
        // The office's own answer, as it arrived, is what the phone keeps for a day without signal.
        sink?.invoke(body)
        parseWallet(body)
    }

    /**
     * A collector hands cash to the office (spec 009, phase B) — the one write a manager's phone makes.
     *
     * It needs a key like every money write: a manager who taps «تأكيد» twice, or a request answered by a
     * timeout and sent again, records one handover. There is no queue behind it on purpose — a handover
     * happens in the office, and a handover that the office never answered is not a handover.
     */
    suspend fun handover(
        token: String,
        collectorId: Int,
        amount: String,
        date: String,
        note: String?,
        key: String,
    ): ApiCall<HandoverAnswer> = money(
        endpoint = "manager/handovers",
        token = token,
        key = key,
        body = JSONObject()
            .put("collector_id", collectorId)
            .put("amount", amount)
            .put("handover_date", date)
            .put("note", note ?: JSONObject.NULL)
            .toString(),
    ) { parseHandover(it) }

    /**
     * A receipt's own words, to send again (spec 011 §8).
     *
     * A household that lost the message in WhatsApp needs the words, not a file — and they come from the
     * office's builder, so what arrives the second time is what was handed over the first time.
     */
    suspend fun receiptText(token: String, paymentId: Int): ApiCall<ReceiptMessage> = call(
        Request.Builder().url("$baseUrl/api/v1/receipts/$paymentId/text").authorized(token).build(),
    ) { body -> parseReceiptMessage(body) }

    /**
     * The receipt's words and, when the office vouchsafes the number, the household's own chat to open.
     * Nothing if the office sent no words: an empty receipt is not a receipt.
     */
    fun parseReceiptMessage(body: String): ReceiptMessage? {
        val row = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val text = row.optString("receipt_text").takeIf { it.isNotEmpty() && it != "null" } ?: return null

        return ReceiptMessage(
            text = text,
            waLink = row.optString("wa_link").takeIf { it.isNotEmpty() && it != "null" },
        )
    }

    /**
     * Take one receipt back (spec 009, phase C) — the most dangerous write this app makes.
     *
     * A written reason travels with it: it is what the audit trail will say in six months, and the desk
     * reads the same trail. The reason is short of nothing else on the wire — no amount, no date: the
     * receipt is named, and what it did to the books is the office's to decide and to undo.
     */
    suspend fun reversal(
        token: String,
        paymentId: Int,
        reason: String,
        key: String,
    ): ApiCall<ReversalAnswer> = money(
        endpoint = "manager/reversals",
        token = token,
        key = key,
        body = JSONObject()
            .put("payment_id", paymentId)
            .put("reason", reason)
            .toString(),
    ) { parseReversal(it) }

    /** One house in full, with the invoices the office holds against it. */
    suspend fun house(token: String, subscriberId: Int, period: String? = null, sink: ((String) -> Unit)? = null): ApiCall<House> = call(
        Request.Builder().url("$baseUrl/api/v1/houses/$subscriberId${periodQuery(period)}").authorized(token).build(),
    ) { body ->
        sink?.invoke(body)
        parseHouseBody(body)
    }

    /**
     * A meter reading, taken at the door.
     *
     * `key` is the attempt's own key (see `AttemptKey`): it goes on the first try and on every retry of
     * the SAME attempt, so a request lost in a dead spot cannot raise two invoices for one month.
     */
    suspend fun read(
        token: String,
        subscriberId: Int,
        period: String,
        currentReading: String,
        note: String?,
        key: String,
    ): ApiCall<ReadingAnswer> = money(
        endpoint = "readings",
        token = token,
        key = key,
        body = JSONObject()
            .put("subscriber_id", subscriberId)
            .put("period", period)
            .put("current_reading", currentReading)
            .also { if (! note.isNullOrBlank()) it.put("note", note) }
            .toString(),
        parse = { parseReading(it) },
    )

    /** A reading the office accepts as an estimate, with the reason that will be read months later. */
    suspend fun estimate(
        token: String,
        subscriberId: Int,
        period: String,
        kwh: String,
        reason: String,
        key: String,
    ): ApiCall<ReadingAnswer> = money(
        endpoint = "readings",
        token = token,
        key = key,
        body = JSONObject()
            .put("subscriber_id", subscriberId)
            .put("period", period)
            .put("estimated", true)
            .put("estimated_kwh", kwh)
            .put("reason", reason)
            .toString(),
        parse = { parseReading(it) },
    )

    /**
     * Money taken at the door, in ONE currency.
     *
     * The office decided a household paying dollars and lira in one visit is two receipts, each in its
     * own currency at that moment's rate — so the app never mixes them, and the rate is the office's own
     * (`lbpRate`), never one the collector typed from memory.
     */
    suspend fun collect(
        token: String,
        subscriberId: Int,
        amount: String,
        currency: String,
        rate: String?,
        method: String,
        note: String?,
        collectedOn: String,
        key: String,
    ): ApiCall<CollectionAnswer> = money(
        endpoint = "collections",
        token = token,
        key = key,
        body = JSONObject()
            .put("subscriber_id", subscriberId)
            .put("amount", amount)
            .put("currency", currency)
            .put("method", method)
            .put("collected_on", collectedOn)
            .also {
                if (! rate.isNullOrBlank()) it.put("rate", rate)
                if (! note.isNullOrBlank()) it.put("note", note)
            }
            .toString(),
        parse = { parseCollection(it) },
    )

    /** The one door every money write goes through: the endpoint, the attempt key, and the body. */
    private suspend fun <T> money(
        endpoint: String,
        token: String,
        key: String,
        body: String,
        parse: (String) -> T?,
    ): ApiCall<T> = call(
        Request.Builder()
            .url("$baseUrl/api/v1/$endpoint")
            .header("Accept", "application/json")
            .header("Accept-Language", LANGUAGE)
            .header("Authorization", "Bearer $token")
            .header("Idempotency-Key", key)
            .post(body.toRequestBody(jsonMedia))
            .build(),
        parse,
    )

    /* ---------------------------------------------------------------- parsing */

    fun parseSession(body: String): Session? {
        val row = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val token = row.optString("token").takeIf { it.isNotEmpty() } ?: return null
        val user = row.optJSONObject("user") ?: return null

        return Session(token, parseUser(user))
    }

    fun parseUser(row: JSONObject): MotoUser = MotoUser(
        id = row.optInt("id"),
        name = row.optString("name"),
        username = row.optString("username").takeIf { it.isNotEmpty() && it != "null" },
        role = row.optString("role"),
        locale = row.optString("locale").takeIf { it.isNotEmpty() && it != "null" },
        phone = row.optString("phone").takeIf { it.isNotEmpty() && it != "null" },
    )

    fun parseBoard(body: String): AreasBoard? {
        val row = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val areas = row.optJSONArray("areas") ?: return null

        return AreasBoard(
            period = row.optString("period"),
            label = row.optString("label").takeIf { it.isNotEmpty() && it != "null" },
            lbpRate = row.optDouble("lbp_rate").takeIf { !row.isNull("lbp_rate") && !it.isNaN() },
            areas = (0 until areas.length()).map { parseArea(areas.getJSONObject(it)) },
            stats = row.optJSONObject("stats")?.let { parseStats(it) },
        )
    }

    /** The collector's own day. Absent on a panel that has not been updated — not zeroed. */
    private fun parseStats(row: JSONObject): CollectorStats? {
        val today = row.optJSONObject("today") ?: return null
        val month = row.optJSONObject("month") ?: return null
        val handovers = row.optJSONArray("handovers")

        return CollectorStats(
            todayTotal = today.optString("total"),
            todayCount = today.optInt("count"),
            monthTotal = month.optString("total"),
            monthCount = month.optInt("count"),
            inHand = row.optString("in_hand"),
            handedTotal = row.optString("handed_total"),
            handovers = if (handovers == null) {
                emptyList()
            } else {
                (0 until handovers.length()).map { index ->
                    val line = handovers.getJSONObject(index)
                    Handover(
                        date = line.optString("date").takeIf { it.isNotEmpty() && it != "null" },
                        amount = line.optString("amount"),
                        note = line.optString("note").takeIf { it.isNotEmpty() && it != "null" },
                    )
                }
            },
        )
    }

    fun parseSearch(body: String): List<SearchHit>? {
        val row = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val results = row.optJSONArray("results") ?: return null

        return (0 until results.length()).map { index ->
            val hit = results.getJSONObject(index)

            SearchHit(
                id = hit.optInt("id"),
                name = hit.optString("name"),
                meterNo = hit.optString("meter_no").takeIf { it.isNotEmpty() && it != "null" },
                type = hit.optString("type"),
                status = hit.optString("status"),
                route = hit.optString("route").takeIf { it.isNotEmpty() && it != "null" },
                balance = hit.optString("balance"),
                credit = hit.optString("credit"),
                owes = hit.optBoolean("owes"),
            )
        }
    }

    fun parseReversal(body: String): ReversalAnswer? {
        val row = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val reversed = row.optJSONObject("reversed") ?: return null

        return ReversalAnswer(
            id = reversed.optInt("id"),
            number = reversed.optString("number").takeIf { it.isNotEmpty() && it != "null" },
            subscriberId = reversed.optInt("subscriber_id"),
            amount = reversed.optString("amount"),
            collector = reversed.optString("collector").takeIf { it.isNotEmpty() && it != "null" },
            collectedOn = reversed.optString("collected_on").takeIf { it.isNotEmpty() && it != "null" },
            subscriberBalance = row.optString("subscriber_balance"),
        )
    }

    fun parseHandover(body: String): HandoverAnswer? {
        val row = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val handover = row.optJSONObject("handover") ?: return null

        return HandoverAnswer(
            id = handover.optInt("id"),
            collectorId = handover.optInt("collector_id"),
            collector = handover.optString("collector"),
            amount = handover.optString("amount"),
            date = handover.optString("handover_date"),
            note = handover.optString("note").takeIf { it.isNotEmpty() && it != "null" },
            collectorInHand = row.optString("collector_in_hand"),
        )
    }

    fun parseWalletBody(body: String): WalletSummary? = parseWallet(body)

    /** The collector's purse, or nothing if the office did not send one. */
    fun parseWallet(body: String): WalletSummary? {
        val row = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val user = row.optJSONObject("user") ?: return null
        val money = row.optJSONObject("money") ?: return null
        val readings = row.optJSONObject("readings")
        val handovers = row.optJSONArray("handovers")

        return WalletSummary(
            name = user.optString("name"),
            period = row.optString("period"),
            label = row.optString("label").takeIf { it.isNotEmpty() && it != "null" },
            inHand = money.optString("in_hand"),
            todayTotal = money.optString("today_total"),
            todayCount = money.optInt("today_count"),
            monthTotal = money.optString("month_total"),
            monthCount = money.optInt("month_count"),
            handedTotal = money.optString("handed_total"),
            readingsToday = readings?.optInt("today") ?: 0,
            readingsCycle = readings?.optInt("cycle") ?: 0,
            receipts = row.optJSONArray("receipts").toWalletReceipts(),
            invoices = row.optJSONArray("invoices").toInvoices(),
            handovers = if (handovers == null) {
                emptyList()
            } else {
                (0 until handovers.length()).map { i ->
                    val h = handovers.getJSONObject(i)

                    WalletHandover(
                        date = h.optString("date").takeIf { it.isNotEmpty() && it != "null" },
                        amount = h.optString("amount"),
                        note = h.optString("note").takeIf { it.isNotEmpty() && it != "null" },
                        receivedBy = h.optString("received_by").takeIf { it.isNotEmpty() && it != "null" },
                    )
                }
            },
        )
    }

    private fun JSONArray?.toWalletReceipts(): List<WalletReceipt> {
        if (this == null) return emptyList()

        return (0 until length()).map { i ->
            val row = getJSONObject(i)

            WalletReceipt(
                id = row.optInt("id"),
                number = row.optString("number").takeIf { it.isNotEmpty() && it != "null" },
                houseId = row.optInt("subscriber_id"),
                house = row.optString("subscriber").takeIf { it.isNotEmpty() && it != "null" },
                amount = row.optString("amount"),
                collectedOn = row.optString("collected_on").takeIf { it.isNotEmpty() && it != "null" },
            )
        }
    }

    private fun JSONArray?.toInvoices(): List<WalletInvoice> {
        if (this == null) return emptyList()

        return (0 until length()).map { i ->
            val row = getJSONObject(i)

            WalletInvoice(
                id = row.optInt("id"),
                number = row.optString("number").takeIf { it.isNotEmpty() && it != "null" },
                houseId = row.optInt("subscriber_id"),
                house = row.optString("subscriber").takeIf { it.isNotEmpty() && it != "null" },
                label = row.optString("label").takeIf { it.isNotEmpty() && it != "null" },
                total = row.optString("total"),
                consumption = row.optString("consumption").takeIf { it.isNotEmpty() && it != "null" },
                remaining = row.optString("remaining"),
            )
        }
    }

    fun parseManagerBoard(body: String): ManagerBoard? {
        val row = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val money = row.optJSONObject("money") ?: return null
        val houses = row.optJSONObject("houses")
        val kwh = row.optJSONObject("kwh")
        val collectors = row.optJSONArray("collectors")
        val recent = row.optJSONArray("recent_payments")

        return ManagerBoard(
            period = row.optString("period"),
            label = row.optString("label"),
            invoiced = money.optString("invoiced"),
            collected = money.optString("collected"),
            rate = money.optDouble("rate", 0.0),
            ratePrevious = money.optDouble("rate_previous", 0.0),
            outstanding = money.optString("outstanding"),
            outstandingInactive = money.optString("outstanding_inactive"),
            active = houses?.optInt("active") ?: 0,
            metered = houses?.optInt("metered") ?: 0,
            flat = houses?.optInt("flat") ?: 0,
            kwh = kwh?.optString("total").orEmpty(),
            kwhPrice = kwh?.optString("price").orEmpty(),
            collectors = if (collectors == null) {
                emptyList()
            } else {
                (0 until collectors.length()).map { index ->
                    val line = collectors.getJSONObject(index)
                    ManagerCollector(
                        id = line.optInt("id"),
                        name = line.optString("name"),
                        todayTotal = line.optString("today_total"),
                        todayCount = line.optInt("today_count"),
                        collected = line.optString("collected"),
                        paymentsCount = line.optInt("payments_count"),
                        handed = line.optString("handed"),
                        inHand = line.optString("in_hand"),
                    )
                }
            },
            recentPayments = if (recent == null) {
                emptyList()
            } else {
                (0 until recent.length()).map { index ->
                    val line = recent.getJSONObject(index)
                    RecentPayment(
                        id = line.optInt("id"),
                        subscriberId = line.optInt("subscriber_id"),
                        number = line.optString("number").takeIf { it.isNotEmpty() && it != "null" },
                        amount = line.optString("amount"),
                        subscriber = line.optString("subscriber").takeIf { it.isNotEmpty() && it != "null" },
                        collector = line.optString("collector").takeIf { it.isNotEmpty() && it != "null" },
                        collectedOn = line.optString("collected_on").takeIf { it.isNotEmpty() && it != "null" },
                    )
                }
            },
        )
    }

    /** One household's body as the API sends it — read back through the same parser as the live answer. */
    fun parseHouseBody(body: String): House? =
        runCatching { JSONObject(body).getJSONObject("house") }.getOrNull()?.let { parseHouse(it) }

    private fun parseArea(row: JSONObject): MotoArea = MotoArea(
        id = if (row.isNull("id")) null else row.optInt("id"),
        name = row.optString("name"),
        houses = row.optInt("houses"),
        active = row.optInt("active"),
        owed = row.optString("owed"),
        readingDone = row.optInt("reading_done"),
        readingTotal = row.optInt("reading_total"),
    )

    fun parseSheet(body: String): AreaSheet? {
        val row = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val houses = row.optJSONArray("houses") ?: return null
        val area = row.optJSONObject("area") ?: return null

        return AreaSheet(
            period = row.optString("period"),
            label = row.optString("label").takeIf { it.isNotEmpty() && it != "null" },
            lbpRate = row.optDouble("lbp_rate").takeIf { !row.isNull("lbp_rate") && !it.isNaN() },
            areaId = area.optInt("id"),
            areaName = area.optString("name"),
            houses = (0 until houses.length()).map { parseHouse(houses.getJSONObject(it)) },
        )
    }

    private fun parseHouse(row: JSONObject): House = House(
        id = row.optInt("id"),
        period = row.optString("period"),
        name = row.optString("name"),
        phone = row.optString("phone").takeIf { it.isNotEmpty() && it != "null" },
        type = row.optString("type"),
        status = row.optString("status"),
        areaName = row.optString("area_name"),
        meterNo = row.optString("meter_no").takeIf { it.isNotEmpty() && it != "null" },
        exempt = row.optBoolean("exempt"),
        exemptReason = row.optString("exempt_reason").takeIf { it.isNotEmpty() && it != "null" },
        flatFee = row.optString("flat_fee").takeIf { it.isNotEmpty() && it != "null" },
        maintenanceFee = row.optString("maintenance_fee"),
        discountPercent = row.optString("discount_percent"),
        balance = row.optString("balance"),
        credit = row.optString("credit"),
        unpaidMonths = row.optJSONArray("unpaid_months").toMonths(),
        unpaidMonthsCount = row.optInt("unpaid_months_count"),
        periodInvoiced = row.optBoolean("period_invoiced"),
        readingDone = row.optBoolean("reading_done"),
        readingValue = row.optString("reading_value").takeIf { it.isNotEmpty() && it != "null" },
        readingEstimated = row.optBoolean("reading_estimated"),
        previousValue = row.optString("previous_value"),
        bills = row.optJSONArray("bills").toBills(),
        payments = row.optJSONArray("payments").toReceipts(),
        readings = row.optJSONArray("readings").toReadings(),
    )

    private fun JSONArray?.toReadings(): List<ReadingLogRow> {
        if (this == null) return emptyList()

        return (0 until length()).map { i ->
            val row = getJSONObject(i)

            ReadingLogRow(
                id = row.optInt("id"),
                period = row.optString("period"),
                label = row.optString("label").takeIf { it.isNotEmpty() && it != "null" } ?: row.optString("period"),
                previous = row.optString("previous"),
                current = row.optString("current"),
                consumption = row.optString("consumption").takeIf { it.isNotEmpty() && it != "null" },
                estimated = row.optBoolean("estimated"),
                reader = row.optString("reader").takeIf { it.isNotEmpty() && it != "null" },
                readOn = row.optString("read_on").takeIf { it.isNotEmpty() && it != "null" },
            )
        }
    }

    private fun JSONArray?.toReceipts(): List<HouseReceipt> {
        if (this == null) return emptyList()

        return (0 until length()).map { parseReceipt(getJSONObject(it)) }
    }

    /** One receipt from a household's log: what changed hands, and what it cleared. */
    fun parseReceipt(row: JSONObject): HouseReceipt {
        val settled = row.optJSONArray("settled")

        return HouseReceipt(
            id = row.optInt("id"),
            number = row.optString("number").takeIf { it.isNotEmpty() && it != "null" },
            amount = row.optString("amount"),
            method = row.optString("method"),
            methodLabel = row.optString("method_label").takeIf { it.isNotEmpty() && it != "null" },
            collectedOn = row.optString("collected_on").takeIf { it.isNotEmpty() && it != "null" },
            collector = row.optString("collector").takeIf { it.isNotEmpty() && it != "null" },
            collectorId = row.optInt("collector_id").takeIf { !row.isNull("collector_id") },
            note = row.optString("note").takeIf { it.isNotEmpty() && it != "null" },
            lbpAmount = row.optInt("lbp_amount").takeIf { !row.isNull("lbp_amount") },
            settled = if (settled == null) {
                emptyList()
            } else {
                (0 until settled.length()).map { i ->
                    val line = settled.getJSONObject(i)

                    SettledLine(
                        period = line.optString("period").takeIf { it.isNotEmpty() && it != "null" },
                        label = line.optString("label").takeIf { it.isNotEmpty() && it != "null" },
                        amount = line.optString("amount"),
                    )
                }
            },
        )
    }

    private fun JSONArray?.toBills(): List<BillLine> {
        if (this == null) return emptyList()

        return (0 until length()).map { parseBill(getJSONObject(it)) }
    }

    /** One invoice, as both the house detail and a reading's answer carry it. */
    fun parseBill(row: JSONObject): BillLine = BillLine(
        id = row.optInt("id"),
        number = row.optString("number").takeIf { it.isNotEmpty() && it != "null" },
        period = row.optString("period"),
        label = row.optString("label"),
        type = row.optString("type"),
        total = row.optString("total"),
        paid = row.optString("paid"),
        remaining = row.optString("remaining"),
        consumption = row.optString("consumption").takeIf { it.isNotEmpty() && it != "null" },
        discount = row.optString("discount"),
        subsidy = row.optString("subsidy"),
    )

    fun parseReading(body: String): ReadingAnswer? {
        val row = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val reading = row.optJSONObject("reading") ?: return null
        val bill = row.optJSONObject("bill") ?: return null
        val house = row.optJSONObject("house") ?: return null

        return ReadingAnswer(
            reading = ReadingRow(
                id = reading.optInt("id"),
                period = reading.optString("period"),
                label = reading.optString("label"),
                currentReading = reading.optString("current_reading"),
                previousReading = reading.optString("previous_reading"),
                consumption = reading.optString("consumption"),
                estimated = reading.optBoolean("estimated"),
                note = reading.optString("note").takeIf { it.isNotEmpty() && it != "null" },
                enteredBy = if (reading.isNull("entered_by")) null else reading.optInt("entered_by"),
            ),
            bill = parseBill(bill),
            house = HouseSummary(id = house.optInt("id"), balance = house.optString("balance"), credit = house.optString("credit")),
            replay = row.optBoolean("replay"),
        )
    }

    fun parseCollection(body: String): CollectionAnswer? {
        val row = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val payment = row.optJSONObject("payment") ?: return null
        val house = row.optJSONObject("house") ?: return null
        val settled = row.optJSONArray("settled")

        return CollectionAnswer(
            payment = PaymentRow(
                id = payment.optInt("id"),
                number = payment.optString("number"),
                amount = payment.optString("amount"),
                applied = payment.optString("applied"),
                credited = payment.optString("credited"),
                method = payment.optString("method"),
                collectedOn = payment.optString("collected_on"),
                lbpAmount = if (payment.isNull("lbp_amount")) null else payment.optLong("lbp_amount"),
                lbpRate = if (payment.isNull("lbp_rate")) null else payment.optDouble("lbp_rate"),
            ),
            settled = if (settled == null) emptyList() else (0 until settled.length()).map { index ->
                val line = settled.getJSONObject(index)
                SettledMonth(
                    billId = line.optInt("bill_id"),
                    period = line.optString("period").takeIf { it.isNotEmpty() && it != "null" },
                    label = line.optString("label").takeIf { it.isNotEmpty() && it != "null" },
                    amount = line.optString("amount"),
                    remaining = line.optString("remaining"),
                )
            },
            house = HouseSummary(id = house.optInt("id"), balance = house.optString("balance"), credit = house.optString("credit")),
            receiptUrl = row.optString("receipt_url"),
            receiptText = row.optString("receipt_text").takeIf { it.isNotEmpty() && it != "null" },
            replay = row.optBoolean("replay"),
        )
    }

    private fun JSONArray?.toMonths(): List<UnpaidMonth> {
        if (this == null) return emptyList()

        return (0 until length()).map { index ->
            val row = getJSONObject(index)
            UnpaidMonth(
                period = row.optString("period"),
                label = row.optString("label"),
                remaining = row.optString("remaining"),
                type = row.optString("type"),
            )
        }
    }

    /* -------------------------------------------------------------- transport */

    private suspend fun <T> call(request: Request, parse: (String) -> T?): ApiCall<T> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val answer = readAnswer(response.code, body)

                if (!response.isSuccessful) {
                    return@withContext ApiCall.Refused(answer)
                }

                val parsed = parse(body)
                    ?: return@withContext ApiCall.Unreadable(answer.copy(code = "unreadable"))

                ApiCall.Success(parsed)
            }
        } catch (failure: IOException) {
            ApiCall.Unreachable(failure.message ?: failure::class.java.simpleName)
        } catch (failure: Exception) {
            // A crash inside parsing must not take the screen down with it: the collector is standing
            // at a door, and «the answer was not one I know» is a sentence he can act on.
            ApiCall.Unreadable(readAnswer(0, "").copy(trouble = failure.message ?: failure::class.java.simpleName))
        }
    }

    private fun Request.Builder.authorized(token: String): Request.Builder = this
        .header("Accept", "application/json")
        .header("Accept-Language", LANGUAGE)
        .header("Authorization", "Bearer $token")
        .get()

    private fun periodQuery(period: String?): String =
        if (period.isNullOrBlank()) "" else "?period=$period"

    /**
     * Reads the API's error shape — `{"error": {"code": "...", "message": "...", "fields": {...}}}` —
     * from a body.
     *
     * Kept as a separate pure function because this is the parsing every screen depends on, and a
     * shape the app mis-reads is a message the collector never sees. `fields` is the per-field map a
     * validation refusal carries; it is what lets a screen mark the field that was refused instead of
     * showing one sentence for the whole form.
     */
    fun readAnswer(httpStatus: Int, body: String): ServerAnswer {
        val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()

        val fields = error?.optJSONObject("fields")?.let { map ->
            map.keys().asSequence().associateWith { key ->
                when (val value = map.opt(key)) {
                    is JSONArray -> value.optString(0)
                    null -> ""
                    else -> value.toString()
                }
            }
        }.orEmpty()

        return ServerAnswer(
            httpStatus = httpStatus,
            code = error?.optString("code")?.takeIf { it.isNotEmpty() && it != "null" },
            message = error?.optString("message")?.takeIf { it.isNotEmpty() && it != "null" },
            reachable = true,
            trouble = null,
            fields = fields,
        )
    }
}

/** One answer from the server: the status a person never sees, and the words he does. */
data class ServerAnswer(
    val httpStatus: Int,
    val code: String?,
    val message: String?,
    val reachable: Boolean,
    val trouble: String?,
    val fields: Map<String, String> = emptyMap(),
) {
    val ok: Boolean get() = reachable && httpStatus in 200..299
}

/** What a call came back with. Four cases, because the man holding the phone must be told which. */
sealed interface ApiCall<out T> {
    /** The server did it and this is what it produced. */
    data class Success<T>(val value: T) : ApiCall<T>

    /** The server said no, in its own words: a month with no invoice, a reading lower than the last. */
    data class Refused(val answer: ServerAnswer) : ApiCall<Nothing>

    /** The server answered, but not in a shape this app can read. */
    data class Unreadable(val answer: ServerAnswer) : ApiCall<Nothing>

    /** The phone never reached the server: no signal, no tunnel, no answer. */
    data class Unreachable(val trouble: String) : ApiCall<Nothing>
}
