package app.stackd.data.vault

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Memory Vault + Time Capsules — web's `memory-vault.functions.ts` and
 * `capsules.functions.ts`. Both tables carry Elite-enforcing RLS
 * (20260823180000), so the DB is the real gate; the screens also check the
 * entitlement first for a decent upgrade prompt instead of empty lists.
 *
 * Deliberately absent: `summarizeVaultItem` — it calls an LLM server-side and
 * has no client path (same deferral as the dashboard AI cards).
 */

@Serializable
data class VaultItem(
    val id: String,
    @SerialName("history_id") val historyId: String? = null,
    val title: String,
    val body: String? = null,
    val url: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("ai_summary") val aiSummary: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class Capsule(
    val id: String,
    val message: String,
    @SerialName("open_at") val openAt: String,
    @SerialName("opened_at") val openedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

class VaultRepository(private val client: SupabaseClient) {

    suspend fun listVault(userId: String): List<VaultItem> =
        client.postgrest.from("memory_vault_items")
            .select(
                Columns.list(
                    "id", "history_id", "title", "body", "url", "tags", "ai_summary", "created_at",
                ),
            ) {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(200)
            }
            .decodeList()

    suspend fun createVaultItem(
        userId: String,
        title: String,
        body: String?,
        url: String?,
        tags: List<String>,
    ): VaultItem? =
        client.postgrest.from("memory_vault_items").insert(
            buildJsonObject {
                put("user_id", userId)
                put("title", title.take(200))
                body?.takeIf { it.isNotBlank() }?.let { put("body", it.take(20_000)) }
                url?.takeIf { it.isNotBlank() }?.let { put("url", it.take(2_000)) }
                put(
                    "tags",
                    kotlinx.serialization.json.buildJsonArray {
                        tags.map { it.trim().lowercase().take(24) }
                            .filter { it.isNotBlank() }
                            .take(12)
                            .forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    },
                )
            },
        ) { select() }.decodeSingleOrNull()

    suspend fun deleteVaultItem(id: String) {
        client.postgrest.from("memory_vault_items").delete {
            filter { eq("id", id) }
        }
    }

    suspend fun listCapsules(userId: String): List<Capsule> =
        client.postgrest.from("time_capsules")
            .select(Columns.list("id", "message", "open_at", "opened_at", "created_at")) {
                filter { eq("user_id", userId) }
                order("open_at", Order.ASCENDING)
            }
            .decodeList()

    /** Message sealed for [days] days (clamped 1..365 like the web). */
    suspend fun writeCapsule(userId: String, message: String, days: Int): Capsule? {
        val msg = message.trim().take(4000)
        if (msg.isEmpty()) return null
        val openAt = java.time.Instant.now()
            .plusSeconds(days.coerceIn(1, 365) * 86_400L)
            .toString()
        return client.postgrest.from("time_capsules").insert(
            buildJsonObject {
                put("user_id", userId)
                put("message", msg)
                put("open_at", openAt)
            },
        ) { select() }.decodeSingleOrNull()
    }

    /** Server-checked unlock: the RPC refuses until `open_at` has passed. */
    suspend fun openCapsule(id: String) {
        client.postgrest.rpc(
            "open_capsule",
            buildJsonObject { put("_id", id) },
        )
    }
}
