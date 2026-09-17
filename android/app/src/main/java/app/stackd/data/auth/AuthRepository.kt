package app.stackd.data.auth

import app.stackd.BuildConfig
import app.stackd.core.settings.SettingsStore
import app.stackd.core.supabase.SupabaseModule
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Mirrors the web's `AuthProvider` union — the values `auth_attempts.provider` accepts. */
enum class AuthProvider(val wire: String) {
    EMAIL("email"),
    GOOGLE("google"),
}

/**
 * Verdict from the `auth-guard` Edge Function, matching the web's `guardSignIn`
 * return shape. [code] is a stable machine string (`rate_limited`,
 * `locked_out`, `invalid_input`); [message] is the user-facing copy, authored
 * server-side so Android and web read identically.
 */
@Serializable
data class GuardVerdict(
    val ok: Boolean,
    val code: String? = null,
    val message: String? = null,
)

/**
 * Result of a server-side sign-in. On success it carries the session tokens
 * the Edge Function obtained, which the client installs locally — the client
 * never sees the password outcome as a claim it could have forged.
 */
@Serializable
data class SignInVerdict(
    val ok: Boolean,
    val code: String? = null,
    val message: String? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

/** What a sign-in/sign-up attempt produced, in the terms the UI cares about. */
sealed interface AuthOutcome {
    /** A session now exists; proceed to the confirm-identity step. */
    data object SignedIn : AuthOutcome

    /**
     * Sign-up succeeded but the project requires email confirmation
     * (`mailer_autoconfirm: false`), so there is no session yet.
     */
    data object ConfirmationEmailSent : AuthOutcome

    /** Blocked or failed. [message] is shown verbatim. */
    data class Failed(val message: String, val code: String? = null) : AuthOutcome
}

/**
 * Auth against the same Supabase project the web app uses.
 *
 * Password sign-in runs entirely inside the `auth-guard` Edge Function: it
 * applies the throttle, performs the credential check, records the observed
 * outcome, and returns session tokens. The client therefore never reports
 * whether an attempt succeeded — see [signIn] for why that matters. Providers
 * without a password (Google) still use the advisory guard, since the token
 * exchange must happen on the device.
 *
 * Two deliberate divergences from web, both settled with the user:
 *  - No Turnstile CAPTCHA. There is no native widget, and a signed APK is a
 *    weaker bot target than an open web form. Every other guard check stands.
 *  - Google arrives as an ID token from Credential Manager rather than through
 *    Lovable's OAuth broker. Different mechanism, same Supabase user. Apple is
 *    not offered on Android at all.
 */
class AuthRepository(
    private val settings: SettingsStore,
    private val client: io.github.jan.supabase.SupabaseClient = SupabaseModule.client,
) {

    val sessionStatus: Flow<SessionStatus> get() = client.auth.sessionStatus

    /** Null until a session exists. */
    val currentUserId: String? get() = client.auth.currentUserOrNull()?.id

    /**
     * Null when no session exists — and also null when the provider returned no
     * address at all, which the confirm-identity step handles by falling back to
     * a derived challenge instead of showing an empty card.
     */
    val currentEmail: String? get() = client.auth.currentUserOrNull()?.email?.takeIf { it.isNotBlank() }

    val isSignedIn: Flow<Boolean>
        get() = client.auth.sessionStatus.map { it is SessionStatus.Authenticated }

    /**
     * Signs in through the `auth-guard` Edge Function rather than calling
     * Supabase Auth directly.
     *
     * The throttle that guards this account is driven by the failure count in
     * `auth_attempts`, so whoever writes that row decides who gets locked out.
     * Letting the client write it — or even report its own outcome for the
     * server to write — means anyone can lock out any address they know. So the
     * credential check happens server-side and the client never asserts an
     * outcome at all; it receives a session or a refusal.
     *
     * The returned tokens are installed locally via [importSession], which is
     * what makes the SDK's own session handling (refresh, `sessionStatus`)
     * work from here on exactly as if we had signed in directly.
     */
    suspend fun signIn(email: String, password: String): AuthOutcome {
        android.util.Log.i("StackdAuth", "signIn start → $webBase$SIGNIN_ROUTE")
        val verdict = runCatching {
            val response = http.post("$webBase$SIGNIN_ROUTE") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("email", email)
                        put("password", password)
                        put("fp", settings.deviceFingerprint())
                    }.toString(),
                )
            }
            val body = response.bodyAsText()
            android.util.Log.i("StackdAuth", "signIn HTTP ${response.status.value}, body ${body.length} chars")
            json.decodeFromString<SignInVerdict>(body)
        }.getOrElse { err ->
            // Fails CLOSED, unlike the advisory guard: this call *is* the
            // sign-in, so an unreachable route means no session, not a free pass.
            android.util.Log.e("StackdAuth", "signin POST to $webBase$SIGNIN_ROUTE failed", err)
            return AuthOutcome.Failed(
                "Couldn't reach the server (${err.javaClass.simpleName}). Check your connection and retry.",
            )
        }

        if (!verdict.ok || verdict.accessToken == null || verdict.refreshToken == null) {
            return AuthOutcome.Failed(
                verdict.message ?: "Email sign-in failed. Please retry.",
                verdict.code,
            )
        }

        android.util.Log.i("StackdAuth", "verdict ok, importing session")
        return runCatching {
            client.auth.importSession(
                UserSession(
                    accessToken = verdict.accessToken,
                    refreshToken = verdict.refreshToken,
                    expiresIn = verdict.expiresIn ?: DEFAULT_EXPIRES_IN,
                    tokenType = "bearer",
                    user = null,
                ),
            )
        }.fold(
            onSuccess = {
                android.util.Log.i("StackdAuth", "importSession OK → SignedIn")
                AuthOutcome.SignedIn
            },
            onFailure = {
                android.util.Log.e("StackdAuth", "importSession failed", it)
                AuthOutcome.Failed("Couldn't start your session. Please retry.")
            },
        )
    }

    /**
     * Creates an account. The live project has `mailer_autoconfirm: false`, so
     * this never yields a session — the caller must not route to the dashboard.
     */
    suspend fun signUp(email: String, password: String, displayName: String?): AuthOutcome {
        val guard = guard(AuthProvider.EMAIL, email)
        if (!guard.ok) {
            return Failed(guard, fallback = "Email sign-up failed. Please retry.")
        }

        val name = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: email.substringBefore("@")

        return runCatching {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                this.data = buildJsonObject { put("display_name", name) }
            }
        }.fold(
            onSuccess = { AuthOutcome.ConfirmationEmailSent },
            onFailure = { err ->
                AuthOutcome.Failed(err.message ?: "Email sign-up failed. Please retry.")
            },
        )
    }

    /**
     * Exchanges a Google ID token from Credential Manager for a Supabase
     * session. The web reaches the same user through Lovable's OAuth broker;
     * only the transport differs.
     */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String?): AuthOutcome {
        val guard = guard(AuthProvider.GOOGLE, email = null)
        if (!guard.ok) {
            return Failed(guard, fallback = "Google sign-in failed. Please retry.")
        }

        return runCatching {
            client.auth.signInWith(IDToken) {
                this.idToken = idToken
                this.provider = Google
                this.nonce = rawNonce
            }
        }.fold(
            onSuccess = { AuthOutcome.SignedIn },
            onFailure = { err ->
                AuthOutcome.Failed(err.message ?: "Google sign-in failed. Please retry.")
            },
        )
    }

    suspend fun signOut() {
        runCatching { client.auth.signOut() }
    }

    private fun Failed(verdict: GuardVerdict, fallback: String) =
        AuthOutcome.Failed(verdict.message ?: fallback, verdict.code)

    /**
     * Asks `auth-guard` whether this attempt may proceed.
     *
     * Fails **open** on transport errors, matching the web's `catch → null`:
     * the guard is a throttle, not the authorization boundary. RLS and Supabase
     * Auth still stand behind it, so a guard outage must not lock everyone out.
     */
    private suspend fun guard(provider: AuthProvider, email: String?): GuardVerdict =
        runCatching {
            val response = http.post("$webBase$GUARD_ROUTE") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("provider", provider.wire)
                        email?.let { put("email", it) }
                        put("fp", settings.deviceFingerprint())
                    }.toString(),
                )
            }
            json.decodeFromString<GuardVerdict>(response.bodyAsText())
        }.getOrElse { GuardVerdict(ok = true) }

    private companion object {
        /**
         * The auth-guard lives as TanStack public API routes on the web app, not
         * as a Supabase Edge Function — this stack has no edge functions. The
         * base URL is configured per build in local.properties (WEB_BASE_URL).
         */
        const val GUARD_ROUTE = "/api/public/auth-guard"
        const val SIGNIN_ROUTE = "/api/public/auth-guard/signin"

        /** Supabase's default access-token lifetime; only used if the server omits it. */
        const val DEFAULT_EXPIRES_IN = 3600L

        val json = Json { ignoreUnknownKeys = true }

        /**
         * Plain HTTP client for the two public routes; the SDK client stays for
         * auth/db. A hard call timeout matters: without it a stalled connection
         * hangs the sign-in button on "Signing in…" forever with no error — the
         * timeout turns that into a clean, visible failure the user can retry.
         */
        val http = HttpClient(OkHttp) {
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
        }
    }

    /** Web base with any trailing slash trimmed, so route concatenation is clean. */
    private val webBase: String get() = BuildConfig.WEB_BASE_URL.trimEnd('/')
}
