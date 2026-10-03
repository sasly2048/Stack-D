package app.stackd.data.ai

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
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Client for the web app's public AI routes under {WEB_BASE_URL}/api/public/ai.
 *
 * The LLM these features use needs a server-side key (LOVABLE_API_KEY) that must
 * never ship in the APK, so — exactly like [app.stackd.data.auth.AuthRepository]
 * with auth-guard — Android calls a public web route that holds the key and
 * returns JSON. The caller's Supabase access token is the only credential sent;
 * the web route re-verifies it and enforces the same RLS the web RPCs do.
 *
 * Every method returns null on any failure (no token, non-2xx, timeout, parse
 * error). Callers keep their existing local/deterministic behaviour as the
 * fallback, so a missing or undeployed AI backend never breaks a screen.
 */
class AiRepository(private val client: SupabaseClient) {

    private val webBase: String get() = BuildConfig.WEB_BASE_URL.trimEnd('/')

    suspend fun recommendNextSession(fresh: Boolean = false): SessionRecommendation? =
        cached("/api/public/ai/recommend", fresh) { getJson("/api/public/ai/recommend", post = true) }

    suspend fun dashboardInsights(fresh: Boolean = false): DashboardInsights? =
        cached("/api/public/ai/dashboard-insights", fresh) {
            getJson("/api/public/ai/dashboard-insights", post = true)
        }

    suspend fun sessionRecap(input: SessionRecapInput): SessionRecap? =
        getJson("/api/public/ai/session-recap", post = true, body = json.encodeToString(input))

    suspend fun weeklyStory(fresh: Boolean = false): WeeklyStory? =
        cached("/api/public/ai/weekly-story", fresh) { getJson("/api/public/ai/weekly-story", post = true) }

    suspend fun discoverPatterns(fresh: Boolean = false): DiscoveredPatterns? =
        cached("/api/public/ai/discover-patterns", fresh) {
            getJson("/api/public/ai/discover-patterns", post = true)
        }

    suspend fun proactiveInsights(): ProactiveInsight? =
        cached("/api/public/ai/proactive") { getJson("/api/public/ai/proactive", post = false) }

    /**
     * Per-user TTL cache for the read-style AI routes. Screens are recreated on
     * every navigation, so the Dashboard alone fired two LLM calls per visit —
     * that is what drained the AI credits. These answers describe the user's
     * history and don't change minute to minute; one per [AI_TTL_MS] is plenty.
     * Only successes are cached, so a failure retries on the next visit.
     * Chat, recaps and vault summaries are never cached.
     */
    private suspend inline fun <reified T : Any> cached(route: String, fresh: Boolean = false, fetch: () -> T?): T? {
        val user = client.auth.currentUserOrNull()?.id ?: return fetch()
        val key = "$user:$route"
        val hit = aiCache[key]
        if (!fresh && hit != null && System.currentTimeMillis() - hit.first < AI_TTL_MS) {
            (hit.second as? T)?.let { return it }
        }
        return fetch()?.also { aiCache[key] = System.currentTimeMillis() to it }
    }

    /**
     * Companion is an explicit, tier-metered action (1 AI action per message).
     * Unlike the passive cards it must tell the user WHY it can't answer, so the
     * route's typed error is surfaced as a notice instead of a silent null.
     */
    suspend fun askCompanion(input: CompanionInput): CompanionResult {
        val token = client.auth.currentAccessTokenOrNull()
            ?: return CompanionResult.Refused("Sign in again to use the Companion.")
        return runCatching {
            val response = http.post(webBase + "/api/public/ai/companion") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(input))
            }
            val text = response.bodyAsText()
            if (response.status == HttpStatusCode.OK) {
                CompanionResult.Reply(json.decodeFromString<CompanionReply>(text).reply)
            } else {
                val err = runCatching { json.decodeFromString<AiError>(text) }.getOrNull()
                CompanionResult.Refused(
                    when (err?.error) {
                        // Server's own wording: "available on Pro and Elite" /
                        // "You've used all N of your AI actions…"
                        "ai_quota" -> err.message ?: "AI features are available on Pro and Elite."
                        "ai_unavailable" -> "The Companion is paused right now. Try again later."
                        "invalid_input" -> "That message couldn't be sent. Try a shorter one."
                        else -> "Companion is unavailable right now. Try again in a moment."
                    },
                )
            }
        }.getOrElse { CompanionResult.Refused("Couldn't reach the Companion. Check your connection.") }
    }

    suspend fun summarizeVaultItem(itemId: String): VaultSummary? =
        getJson(
            "/api/public/ai/vault-summarize",
            post = true,
            body = json.encodeToString(VaultSummarizeInput(itemId)),
        )

    /** Shared request/parse. Returns null on any failure so callers can fall back. */
    private suspend inline fun <reified T> getJson(
        path: String,
        post: Boolean,
        body: String? = null,
    ): T? = runCatching {
        val token = client.auth.currentAccessTokenOrNull() ?: return null
        val url = webBase + path
        val response: HttpResponse =
            if (post) {
                http.post(url) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    if (body != null) setBody(body)
                }
            } else {
                http.get(url) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        if (response.status != HttpStatusCode.OK) return null
        json.decodeFromString<T>(response.bodyAsText())
    }.getOrNull()

    private companion object {
        const val AI_TTL_MS = 30 * 60 * 1000L
        val aiCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Any>>()
        val json = Json { ignoreUnknownKeys = true }

        // A hard timeout matters: a stalled AI call must fail fast to the local
        // fallback rather than hang the surface waiting.
        val http = HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
        }
    }
}

/* --------------------------- response models ----------------------------- */
/* Field names mirror each web route's return type exactly (ignoreUnknownKeys
 * tolerates extras like generatedAt when a surface doesn't need them). */

@Serializable
data class SessionRecommendation(
    val durationMinutes: Int,
    val topic: String,
    val rationale: String,
    val confidence: String,
    val basedOnSessions: Int,
)

@Serializable
data class DashboardInsights(
    val paragraphs: List<String> = emptyList(),
    val headline: String = "",
    val basedOnSessions: Int = 0,
)

@Serializable
data class SessionRecapInput(
    val roomId: String,
    val score: Int,
    val xp: Int,
    val durationSeconds: Int,
    val breachesCount: Int,
    val tier: String,
    val roomCode: String,
)

@Serializable
data class SessionRecap(
    val title: String,
    val summary: String,
    val reflections: List<String> = emptyList(),
    val nextStep: String,
    val score: Int = 0,
    val xp: Int = 0,
)

@Serializable
data class WeeklyStory(val story: String)

@Serializable
data class DiscoveredPatterns(val patterns: List<String> = emptyList())

@Serializable
data class ProactiveInsight(
    val smartSchedule: SmartSchedule? = null,
    val focusPrediction: FocusPrediction,
    val burnout: Burnout,
) {
    @Serializable
    data class SmartSchedule(val hour: Int, val label: String, val rationale: String)

    @Serializable
    data class FocusPrediction(val nextScore: Int, val confidence: String, val note: String)

    @Serializable
    data class Burnout(
        val risk: String,
        val signals: List<String> = emptyList(),
        val recommendation: String,
    )
}

@Serializable
data class CompanionMessage(val role: String, val content: String)

@Serializable
data class CompanionInput(
    val history: List<CompanionMessage> = emptyList(),
    val message: String,
)

@Serializable
data class CompanionReply(val reply: String)

/** The public routes' typed error body: { error, message?, reason? }. */
@Serializable
data class AiError(val error: String? = null, val message: String? = null, val reason: String? = null)

/** A companion turn: a reply, or a user-facing reason it was refused. */
sealed interface CompanionResult {
    data class Reply(val text: String) : CompanionResult
    data class Refused(val notice: String) : CompanionResult
}

@Serializable
private data class VaultSummarizeInput(val id: String)

@Serializable
data class VaultSummary(val summary: String)
