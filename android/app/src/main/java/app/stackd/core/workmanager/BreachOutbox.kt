package app.stackd.core.workmanager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.breachStore: DataStore<Preferences> by preferencesDataStore(
    name = "stackd_breach_outbox",
)

/**
 * A breach detected on this device that the server hasn't acknowledged yet.
 *
 * [id] is local only — it lets the drain remove exactly the rows it sent.
 */
@Serializable
data class PendingBreach(
    val id: String,
    val roomId: String,
    val participantId: String,
    val reason: String,
    val severity: String,
    val integrity: Int,
    val owner: String,
    val at: Long,
)

/**
 * Durable outbox for breaches.
 *
 * `record_breach` used to be fire-and-forget: a lift in airplane mode, or while
 * the room screen was gone, never reached the server, and the session scored as
 * clean. Every breach now lands here first and drains in detection order. The
 * finalize path drains this BEFORE submitting a result, because the server
 * derives the score from the recorded breaks.
 */
class BreachOutbox(private val context: Context) {

    suspend fun enqueue(breach: PendingBreach) {
        context.breachStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(serializer, decode(prefs[KEY]) + breach)
        }
    }

    suspend fun readFor(owner: String): List<PendingBreach> =
        decode(context.breachStore.data.map { it[KEY] }.first())
            .filter { it.owner == owner }
            .sortedBy { it.at }

    /** Removes exactly [ids], atomically, so a breach enqueued mid-drain survives. */
    suspend fun remove(ids: Set<String>) {
        if (ids.isEmpty()) return
        context.breachStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(serializer, decode(prefs[KEY]).filterNot { it.id in ids })
        }
    }

    /**
     * Sends [owner]'s pending breaches oldest-first. Stops at the first failure
     * so order is preserved (a later severe must not land before an earlier
     * minor). Returns true when nothing is left pending for [owner].
     */
    suspend fun drain(owner: String, submit: suspend (PendingBreach) -> Unit): Boolean =
        // The service, the room screen and the worker can all trigger a drain;
        // serialize them so one breach is never sent twice.
        drainLock.withLock {
            val pending = readFor(owner)
            val sent = mutableSetOf<String>()
            for (b in pending) {
                if (runCatching { submit(b) }.isFailure) break
                sent += b.id
            }
            remove(sent)
            sent.size == pending.size
        }

    private fun decode(raw: String?): List<PendingBreach> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    private companion object {
        val drainLock = kotlinx.coroutines.sync.Mutex()
        val KEY = stringPreferencesKey("pending")
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(PendingBreach.serializer())
    }
}
