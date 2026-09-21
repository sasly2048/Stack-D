package app.stackd.data.social

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Friendships — web's `friends.functions.ts`, one method per server function.
 * The canonical-pair unique index and block-enforcement triggers all live in
 * the DB (20260824010000/20260824020000), so a duplicate or blocked request
 * fails there regardless of what the client sends.
 */

@Serializable
internal data class FriendshipRow(
    val id: String,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PersonRef(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/** Web's `FriendRow`: the OTHER person plus direction of the relationship. */
data class Friend(
    val id: String,
    val userId: String,
    val displayName: String?,
    val status: String,
    /** incoming | outgoing | friend */
    val direction: String,
    val since: String,
)

class FriendsRepository(private val client: SupabaseClient) {

    suspend fun listFriends(userId: String): List<Friend> {
        val rows = client.postgrest.from("friendships")
            .select(Columns.list("id", "requester_id", "addressee_id", "status", "created_at")) {
                filter {
                    or {
                        eq("requester_id", userId)
                        eq("addressee_id", userId)
                    }
                }
                order("created_at", Order.DESCENDING)
                limit(1000)
            }
            .decodeList<FriendshipRow>()
        if (rows.isEmpty()) return emptyList()

        val otherIds = rows.map { if (it.requesterId == userId) it.addresseeId else it.requesterId }
            .distinct()
        val profiles = client.postgrest.from("profiles")
            .select(Columns.list("id", "display_name", "avatar_url")) {
                filter { isIn("id", otherIds) }
            }
            .decodeList<PersonRef>()
            .associateBy { it.id }

        return rows.map { r ->
            val other = if (r.requesterId == userId) r.addresseeId else r.requesterId
            Friend(
                id = r.id,
                userId = other,
                displayName = profiles[other]?.displayName,
                status = r.status,
                direction = when {
                    r.status == "accepted" -> "friend"
                    r.requesterId == userId -> "outgoing"
                    else -> "incoming"
                },
                since = r.createdAt,
            )
        }
    }

    suspend fun searchPeople(userId: String, q: String): List<PersonRef> =
        client.postgrest.from("profiles")
            .select(Columns.list("id", "display_name", "avatar_url")) {
                filter {
                    ilike("display_name", "%${q.trim().take(60)}%")
                    neq("id", userId)
                }
                limit(20)
            }
            .decodeList()

    suspend fun sendRequest(userId: String, addresseeId: String) {
        if (addresseeId == userId) return
        // Duplicates hit the canonical-pair unique index; treat as already-sent.
        runCatching {
            client.postgrest.from("friendships").insert(
                buildJsonObject {
                    put("requester_id", userId)
                    put("addressee_id", addresseeId)
                    put("status", "pending")
                },
            )
        }.onFailure { if ("duplicate" !in (it.message ?: "").lowercase()) throw it }
    }

    /** Accept or decline an incoming request. Addressee-scoped like the web. */
    suspend fun respond(id: String, userId: String, accept: Boolean) {
        if (accept) {
            client.postgrest.from("friendships").update({ set("status", "accepted") }) {
                filter {
                    eq("id", id)
                    eq("addressee_id", userId)
                }
            }
        } else {
            client.postgrest.from("friendships").delete {
                filter {
                    eq("id", id)
                    eq("addressee_id", userId)
                }
            }
        }
    }

    /** Remove a friendship (or cancel an outgoing request). */
    suspend fun remove(id: String) {
        client.postgrest.from("friendships").delete {
            filter { eq("id", id) }
        }
    }
}
