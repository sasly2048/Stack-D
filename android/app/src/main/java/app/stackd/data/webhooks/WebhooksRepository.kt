package app.stackd.data.webhooks

import app.stackd.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** The event names a webhook can subscribe to — web's EVENT_TYPES, same order. */
val WEBHOOK_EVENTS = listOf(
    "session.complete",
    "session.start",
    "achievement.unlock",
    "challenge.complete",
    "friend.add",
    "streak.milestone",
)

@Serializable
data class Webhook(
    val id: String,
    val url: String,
    val events: List<String> = emptyList(),
    val secret: String = "",
    val active: Boolean = true,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class WebhookDelivery(
    val id: String,
    val event: String,
    @SerialName("status_code") val statusCode: Int? = null,
    val ok: Boolean = false,
    @SerialName("response_snippet") val responseSnippet: String? = null,
    val attempt: Int = 1,
    @SerialName("created_at") val createdAt: String = "",
)

/**
 * Webhooks, via the web app's public webhook routes under /api/public/webhooks.
 *
 * Create / toggle / test need the service-role client on the server (client
 * writes to `webhooks` are revoked so the SSRF URL check can't be bypassed), so
 * — like AI and auth-guard — Android sends only its Supabase bearer token and
 * the server runs the exact same code the web page does.
 */
class WebhooksRepository(private val client: SupabaseClient) {

    private val base: String get() = BuildConfig.WEB_BASE_URL.trimEnd('/') + "/api/public/webhooks"

    suspend fun list(): Result<List<Webhook>> = call("/list", post = false) { json.decodeFromString(it) }

    suspend fun create(url: String, events: List<String>): Result<Webhook> =
        call(
            "/create",
            body = buildJsonObject {
                put("url", JsonPrimitive(url.trim()))
                put("events", JsonArray(events.map { JsonPrimitive(it) }))
            }.toString(),
        ) { json.decodeFromString(it) }

    suspend fun toggle(id: String, active: Boolean): Result<Unit> =
        call(
            "/toggle",
            body = buildJsonObject {
                put("id", JsonPrimitive(id))
                put("active", JsonPrimitive(active))
            }.toString(),
        ) { }

    suspend fun delete(id: String): Result<Unit> =
        call("/delete", body = buildJsonObject { put("id", JsonPrimitive(id)) }.toString()) { }

    suspend fun deliveries(id: String): Result<List<WebhookDelivery>> =
        call("/deliveries", body = buildJsonObject { put("webhookId", JsonPrimitive(id)) }.toString()) {
            json.decodeFromString(it)
        }

    suspend fun test(id: String): Result<WebhookDelivery> =
        call("/test", body = buildJsonObject { put("webhookId", JsonPrimitive(id)) }.toString()) {
            json.decodeFromString(it)
        }

    /** One request; a non-2xx becomes a failure carrying the server's message. */
    private suspend fun <T> call(
        path: String,
        post: Boolean = true,
        body: String? = null,
        parse: (String) -> T,
    ): Result<T> = runCatching {
        val token = client.auth.currentAccessTokenOrNull() ?: error("Sign in again to manage webhooks.")
        val response: HttpResponse = if (post) {
            http.post(base + path) {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body ?: "{}")
            }
        } else {
            http.get(base + path) { header(HttpHeaders.Authorization, "Bearer $token") }
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error(messageFor(text))
        parse(text)
    }

    private fun messageFor(body: String): String {
        val err = runCatching { json.decodeFromString<ErrorBody>(body) }.getOrNull()
        return err?.message ?: when (err?.error) {
            "url_not_public" -> "That URL isn't a public http(s) endpoint."
            "not_found" -> "That webhook no longer exists."
            "invalid_input" -> "Check the URL and pick at least one event."
            else -> "Couldn't reach the server. Try again."
        }
    }

    @Serializable
    private data class ErrorBody(val error: String? = null, val message: String? = null)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val http = HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
        }
    }
}
