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
    suspend fun areas(token: String, period: String? = null): ApiCall<AreasBoard> = call(
        Request.Builder().url("$baseUrl/api/v1/areas${periodQuery(period)}").authorized(token).build(),
    ) { parseBoard(it) }

    /**
     * One area's houses.
     *
     * This is the sheet the collector walks with: what each house owes, which months the figure is
     * made of, and whether the meter was read this cycle.
     */
    suspend fun houses(token: String, routeId: Int, period: String? = null): ApiCall<AreaSheet> = call(
        Request.Builder().url("$baseUrl/api/v1/areas/$routeId/houses${periodQuery(period)}").authorized(token).build(),
    ) { parseSheet(it) }

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
            areas = (0 until areas.length()).map { parseArea(areas.getJSONObject(it)) },
        )
    }

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
            areaId = area.optInt("id"),
            areaName = area.optString("name"),
            houses = (0 until houses.length()).map { parseHouse(houses.getJSONObject(it)) },
        )
    }

    private fun parseHouse(row: JSONObject): House = House(
        id = row.optInt("id"),
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
    )

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
