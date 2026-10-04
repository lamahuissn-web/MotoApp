package com.lamahuissn.motoapp.data

import com.lamahuissn.motoapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The first honest piece of the client: one unauthenticated call, so the app proves it can reach the
 * office's server before any screen is built on top of it.
 *
 * The address is compiled in (`BuildConfig.API_BASE_URL`) and the app talks to the same host the
 * office's panel lives on, through the same tunnel — one door, one certificate, nothing to configure
 * on a phone at a door.
 *
 * What this file deliberately does NOT do yet: sign in, hold a token, or write anything. Those come
 * with the collector's screens, and none of them will compute money on the device — the server is the
 * only place a balance, a price or a receipt number is decided.
 */
object MotoApi {

    /** No trailing slash, so `baseUrl + "api/v1/me"` is always a well-formed URL. */
    val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * The server's own answer to `GET /api/v1/me` with no token — an answer this app must be able to
     * read as a sentence, because it is the one it will get on every expired session.
     */
    suspend fun ping(): ServerAnswer = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/v1/me")
            .header("Accept", "application/json")
            .get()
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                readAnswer(response.code, response.body?.string().orEmpty())
            }
        }.getOrElse { failure ->
            ServerAnswer(
                httpStatus = 0,
                code = null,
                message = null,
                reachable = false,
                trouble = failure.message ?: failure::class.java.simpleName,
            )
        }
    }

    /**
     * Reads the API's error shape — `{"error": {"code": "...", "message": "...", "fields": {...}}}` —
     * from a body.
     *
     * Kept as a separate pure function because this is the parsing every screen will depend on, and
     * a shape the app mis-reads is a message the collector never sees. `fields` is the per-field map
     * a validation refusal carries; it is what lets the app mark the field that was refused instead
     * of showing one sentence for the whole form.
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
