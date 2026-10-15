package app.stackd.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stackd_settings")

/**
 * Local device preferences.
 *
 * [devToolsEnabled] gates the webhooks / SDK / MCP screens, which are developer
 * surfaces rather than product ones. Off by default; the destinations stay in
 * the nav graph and simply aren't linked to while it is off.
 */
class SettingsStore(private val context: Context) {

    val devToolsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KeyDevTools] ?: false }

    suspend fun setDevToolsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KeyDevTools] = enabled }
    }

    /**
     * Opaque per-install id sent to `auth-guard`, standing in for the web's
     * `getDeviceFingerprint()`. It only has to be stable enough to throttle a
     * device across a minute-long window.
     *
     * Deliberately a random UUID rather than `ANDROID_ID`: the latter is stable
     * across our own uninstalls and shared with every app signed by the same
     * key, which is far more identifying than throttling needs. Clearing app
     * data resets this, and that is fine — it only widens a rate limit.
     */
    suspend fun deviceFingerprint(): String {
        val existing = context.dataStore.data.map { it[KeyFingerprint] }.first()
        if (existing != null) return existing

        // Two racing callers would each generate a value; edit() resolves the
        // race by letting the last write win, and either value works.
        val generated = UUID.randomUUID().toString().replace("-", "").take(32)
        context.dataStore.edit { it[KeyFingerprint] = generated }
        return generated
    }

    /**
     * Last timezone successfully reported for this user. The web dedupes its
     * `set_my_timezone` call per (user, zone) via localStorage; this is the
     * DataStore equivalent.
     */
    suspend fun reportedTimezone(userId: String): String? =
        context.dataStore.data.map { it[stringPreferencesKey("tz_$userId")] }.first()

    suspend fun markTimezoneReported(userId: String, tz: String) {
        context.dataStore.edit { it[stringPreferencesKey("tz_$userId")] = tz }
    }

    /**
     * Enforcement profile, persisted across sessions like the web's
     * `localStorage["stackd:mode"]`. Defaults to `absolute` — the stricter of
     * the two — so a first run never silently enforces less than the user
     * assumes it does.
     */
    val enforcementMode: Flow<String> =
        context.dataStore.data.map { it[KeyMode] ?: MODE_ABSOLUTE }

    suspend fun setEnforcementMode(mode: String) {
        context.dataStore.edit { it[KeyMode] = if (mode == MODE_GENTLE) MODE_GENTLE else MODE_ABSOLUTE }
    }

    /**
     * The duration of the last session actually started — mirrors the web's
     * `getLastSessionMinutes`. Written only once a room exists: a duration
     * picked but never started is an abandoned draft, not a preference.
     */
    val lastSessionMinutes: Flow<Int?> =
        context.dataStore.data.map { it[KeyLastMinutes] }

    suspend fun setLastSessionMinutes(minutes: Int) {
        context.dataStore.edit { it[KeyLastMinutes] = minutes }
    }

    /**
     * Whether the user has ever finished a session — mirrors the web's
     * `has-completed-session` flag. Gates first-run coaching (the "what is a
     * room" tip): someone who has held a room before doesn't need it. Set once
     * at the first finalize and never cleared.
     */
    val hasCompletedSession: Flow<Boolean> =
        context.dataStore.data.map { it[KeyHasCompleted] ?: false }

    suspend fun markCompletedSession() {
        context.dataStore.edit { it[KeyHasCompleted] = true }
    }

    /**
     * Dismissed once, stays dismissed — the web keys tips by id in a set, but
     * Android has exactly one dismissible tip (the Start intro), so a single
     * flag is the whole feature. Add a keyed set only if a second tip appears.
     */
    /**
     * The post-session Premium offer is rationed so it never becomes the reason
     * someone quits: only after a clean stack, never in the first three, at most
     * once a week, and two "Not now"s buy thirty days of quiet. Call once per
     * clean ceremony; returns whether to show the offer this time.
     */
    suspend fun premiumAskAfterCleanStack(now: Long = System.currentTimeMillis()): Boolean {
        var show = false
        context.dataStore.edit { p ->
            val clean = (p[KeyCleanSeen] ?: 0) + 1
            p[KeyCleanSeen] = clean
            val week = 7L * 86_400_000L
            show = clean > 3 &&
                now >= (p[KeyAskQuietUntil] ?: 0L) &&
                now - (p[KeyAskShownAt] ?: 0L) >= week
            if (show) p[KeyAskShownAt] = now
        }
        return show
    }

    suspend fun dismissPremiumAsk(now: Long = System.currentTimeMillis()) {
        context.dataStore.edit { p ->
            val n = (p[KeyAskDismisses] ?: 0) + 1
            if (n >= 2) {
                p[KeyAskQuietUntil] = now + 30L * 86_400_000L
                p[KeyAskDismisses] = 0
            } else {
                p[KeyAskDismisses] = n
            }
        }
    }

    val startIntroDismissed: Flow<Boolean> =
        context.dataStore.data.map { it[KeyStartIntro] ?: false }

    suspend fun dismissStartIntro() {
        context.dataStore.edit { it[KeyStartIntro] = true }
    }

    /** Provider ("email"/"google") of the last successful sign-in — web `getLastAuthProvider`. */
    val lastAuthProvider: Flow<String?> =
        context.dataStore.data.map { it[KeyLastAuth] }

    suspend fun setLastAuthProvider(provider: String) {
        context.dataStore.edit { it[KeyLastAuth] = provider }
    }

    /** UI sound effects on unless turned off — web `stackd:sound`. */
    val soundEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KeySound] ?: true }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KeySound] = enabled }
    }

    companion object {
        private val KeySound = booleanPreferencesKey("sound_enabled")
        private val KeyLastAuth = stringPreferencesKey("last_auth_provider")
        const val MODE_GENTLE = "gentle"
        const val MODE_ABSOLUTE = "absolute"

        private val KeyDevTools = booleanPreferencesKey("dev_tools_enabled")
        private val KeyFingerprint = stringPreferencesKey("device_fingerprint")
        private val KeyMode = stringPreferencesKey("enforcement_mode")
        private val KeyLastMinutes = androidx.datastore.preferences.core.intPreferencesKey(
            "last_session_minutes",
        )
        private val KeyHasCompleted = booleanPreferencesKey("has_completed_session")
        private val KeyStartIntro = booleanPreferencesKey("dismissed_start_intro")
        private val KeyCleanSeen = androidx.datastore.preferences.core.intPreferencesKey("clean_stacks_seen")
        private val KeyAskShownAt = androidx.datastore.preferences.core.longPreferencesKey("premium_ask_shown_at")
        private val KeyAskDismisses = androidx.datastore.preferences.core.intPreferencesKey("premium_ask_dismisses")
        private val KeyAskQuietUntil = androidx.datastore.preferences.core.longPreferencesKey("premium_ask_quiet_until")
    }
}
