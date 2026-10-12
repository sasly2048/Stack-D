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

    private companion object {
        val KeyDevTools = booleanPreferencesKey("dev_tools_enabled")
        val KeyFingerprint = stringPreferencesKey("device_fingerprint")
    }
}
