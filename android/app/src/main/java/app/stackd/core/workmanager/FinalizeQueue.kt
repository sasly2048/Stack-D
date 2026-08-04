package app.stackd.core.workmanager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.finalizeStore: DataStore<Preferences> by preferencesDataStore(
    name = "stackd_finalize_queue",
)

/**
 * A session result waiting to reach the server.
 *
 * Mirrors the payload the web app parks in localStorage, including [owner] so a
 * result can never be replayed under a different account after a sign-out.
 */
@Serializable
data class FinalizePayload(
    val roomId: String,
    val score: Int,
    val xp: Int,
    val durationSeconds: Int,
    val breachesCount: Int,
    val tier: String,
    val owner: String,
    val queuedAt: Long,
)

/**
 * Holds session results that couldn't be submitted — usually because the room
 * ended somewhere with no signal.
 *
 * This is a handful of rows at most, so it lives in DataStore as JSON rather
 * than earning a database. Entries are keyed by (owner, room): retrying a
 * result replaces the pending one instead of stacking up duplicates, matching
 * the web app's dedupe and leaning on `finalize_focus_session` being
 * idempotent per (profile, room) server-side.
 */
class FinalizeQueue(private val context: Context) {

    suspend fun enqueue(payload: FinalizePayload) {
        context.finalizeStore.edit { prefs ->
            val existing = decode(prefs[KEY])
            val deduped = existing.filterNot {
                it.owner == payload.owner && it.roomId == payload.roomId
            }
            prefs[KEY] = json.encodeToString(serializer,deduped + payload)
        }
    }

    suspend fun readAll(): List<FinalizePayload> =
        decode(context.finalizeStore.data.map { it[KEY] }.first())

    suspend fun readFor(owner: String): List<FinalizePayload> =
        readAll().filter { it.owner == owner }

    /**
     * Replaces this owner's pending results, leaving other accounts' rows
     * untouched — the device may have been shared or signed out mid-queue.
     */
    suspend fun replaceFor(owner: String, survivors: List<FinalizePayload>) {
        context.finalizeStore.edit { prefs ->
            val others = decode(prefs[KEY]).filterNot { it.owner == owner }
            prefs[KEY] = json.encodeToString(serializer,others + survivors)
        }
    }

    suspend fun size(owner: String): Int = readFor(owner).size

    private fun decode(raw: String?): List<FinalizePayload> {
        if (raw.isNullOrBlank()) return emptyList()
        // A corrupt blob shouldn't wedge the queue forever; drop it and move on.
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
    }

    private companion object {
        val KEY = stringPreferencesKey("pending")
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(FinalizePayload.serializer())
    }
}
