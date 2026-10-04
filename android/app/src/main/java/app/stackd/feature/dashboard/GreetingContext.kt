package app.stackd.feature.dashboard

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** The extra stat lines web's DynamicGreeting shows (friends online, challenge %). */
data class GreetingExtras(val friendsOnline: Int = 0, val challengeProgress: Float = 0f)

@Serializable
private data class FriendshipRow(
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
)

@Serializable
private data class IdRow(val id: String)

@Serializable
private data class ChallengeRef(val target: Double = 0.0)

@Serializable
private data class ChallengeProgressRow(val progress: Double = 0.0, val challenge: ChallengeRef? = null)

/** Best-effort: any failure yields zeros so the greeting just omits those lines. */
suspend fun loadGreetingExtras(client: SupabaseClient): GreetingExtras = runCatching {
    val uid = client.auth.currentUserOrNull()?.id ?: return GreetingExtras()
    val friends = client.postgrest.from("friendships")
        .select(Columns.list("requester_id", "addressee_id")) { filter { eq("status", "accepted") } }
        .decodeList<FriendshipRow>()
        .map { if (it.requesterId == uid) it.addresseeId else it.requesterId }
        .distinct()
    val online = if (friends.isEmpty()) 0 else {
        val since = Instant.now().minusSeconds(300).toString()
        client.postgrest.from("public_profiles")
            .select(Columns.list("id")) {
                filter { isIn("id", friends); gte("last_active_at", since) }
            }.decodeList<IdRow>().size
    }
    val cp = client.postgrest.from("challenge_progress")
        .select(Columns.raw("progress,challenge:challenges(target)")) {
            filter { eq("user_id", uid) }
            order("updated_at", Order.DESCENDING)
            limit(1)
        }.decodeList<ChallengeProgressRow>().firstOrNull()
    val t = cp?.challenge?.target ?: 0.0
    GreetingExtras(online, if (t > 0) (cp!!.progress / t).coerceIn(0.0, 1.0).toFloat() else 0f)
}.getOrDefault(GreetingExtras())
