package app.stackd.data.auth

import app.stackd.core.settings.SettingsStore
import app.stackd.core.supabase.SupabaseModule
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
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
 * The order of operations is the web's, from `src/routes/auth.tsx`: consult the
 * guard first, and only touch Supabase Auth if it says yes — otherwise a
 * throttled attempt would still cost a real auth call. Every terminal outcome
 * is logged back through the guard so `auth_attempts` sees Android traffic the
 * same way it sees web traffic.
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

    val isSignedIn: Flow<Boolean>
        get() = client.auth.sessionStatus.map { it is SessionStatus.Authenticated }

    suspend fun signIn(email: String, password: String): AuthOutcome {
        val guard = guard(AuthProvider.EMAIL, email)
        if (!guard.ok) {
            return Failed(guard, fallback = "Email sign-in failed. Please retry.")
        }

        return runCatching {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }.fold(
            onSuccess = {
                log(AuthProvider.EMAIL, email, success = true)
                AuthOutcome.SignedIn
            },
            onFailure = { err ->
                val message = err.message ?: "Email sign-in failed. Please retry."
                log(AuthProvider.EMAIL, email, success = false, reason = message.take(120))
                AuthOutcome.Failed(message)
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
            onSuccess = {
                log(AuthProvider.EMAIL, email, success = true, reason = "signup_email_sent")
                AuthOutcome.ConfirmationEmailSent
            },
            onFailure = { err ->
                val message = err.message ?: "Email sign-up failed. Please retry."
                log(AuthProvider.EMAIL, email, success = false, reason = message.take(120))
                AuthOutcome.Failed(message)
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
            onSuccess = {
                log(AuthProvider.GOOGLE, email = null, success = true)
                AuthOutcome.SignedIn
            },
            onFailure = { err ->
                val message = err.message ?: "Google sign-in failed. Please retry."
                log(AuthProvider.GOOGLE, null, success = false, reason = message.take(120))
                AuthOutcome.Failed(message)
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
            val response: HttpResponse = client.functions.invoke(
                function = GUARD_FUNCTION,
                body = buildJsonObject {
                    put("provider", provider.wire)
                    email?.let { put("email", it) }
                    put("fp", settings.deviceFingerprint())
                },
            )
            json.decodeFromString<GuardVerdict>(response.body())
        }.getOrElse { GuardVerdict(ok = true) }

    /** Fire-and-forget; a failed log must never block or fail an auth attempt. */
    private suspend fun log(
        provider: AuthProvider,
        email: String?,
        success: Boolean,
        reason: String? = null,
    ) {
        runCatching {
            client.functions.invoke(
                function = LOG_FUNCTION,
                body = buildJsonObject {
                    put("provider", provider.wire)
                    email?.let { put("email", it) }
                    put("success", success)
                    reason?.let { put("reason", it.take(200)) }
                },
            )
        }
    }

    private companion object {
        const val GUARD_FUNCTION = "auth-guard"
        const val LOG_FUNCTION = "auth-guard/log"
        val json = Json { ignoreUnknownKeys = true }
    }
}
