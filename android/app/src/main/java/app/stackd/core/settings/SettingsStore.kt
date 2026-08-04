package app.stackd.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    private companion object {
        val KeyDevTools = booleanPreferencesKey("dev_tools_enabled")
    }
}
