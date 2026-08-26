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
    val startIntroDismissed: Flow<Boolean> =
        context.dataStore.data.map { it[KeyStartIntro] ?: false }

    suspend fun dismissStartIntro() {
        context.dataStore.edit { it[KeyStartIntro] = true }
    }

    companion object {
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
    }
}
