package app.stackd.core.supabase

import app.stackd.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import kotlin.time.Duration.Companion.seconds

/**
 * The app talks to the same Supabase project as the web build, with the same
 * publishable/anon key. There is no server tier of our own: authorization is
 * enforced entirely by RLS policies and SECURITY DEFINER RPCs, exactly the
 * boundary the web app's server functions already rely on.
 *
 * The service_role key must never appear here — it would ship inside the APK.
 * The two flows that need it (pre-auth rate limiting, webhook delivery tests)
 * go through Edge Functions instead.
 */
object SupabaseModule {

    val hasCredentials: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val client: SupabaseClient by lazy {
        check(hasCredentials) {
            "Supabase credentials missing. Set SUPABASE_URL and " +
                "SUPABASE_PUBLISHABLE_KEY in android/local.properties."
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Functions)

            // Without a ceiling, a stalled request hangs the calling screen's
            // spinner forever with no error. A hard timeout turns that into a
            // clean failure the existing runCatching paths surface (the lobby's
            // error state, a retry button) instead of an infinite "Loading…".
            // `requestTimeout` is supabase-kt's public per-request ceiling; it
            // does not apply to Realtime's own long-lived socket.
            requestTimeout = 15.seconds
        }
    }
}
