package app.stackd.data.social

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Accountability partners — web's `mentor.functions.ts`. All direct
 * `mentor_relationships` table ops under RLS: insert is forced `pending`, only
 * the invitee can flip it to `active`/`declined`, and either party can delete.
 */

@Serializable
internal data class MentorRow(
    val id: String,
    @SerialName("mentor_id") val mentorId: String,
    @SerialName("mentee_id") val menteeId: String,
    val status: String,
    @SerialName("initiator_id") val initiatorId: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/** Web's `Partner`: the other party plus this user's role + invite direction. */
data class Partner(
    val relationshipId: String,
    val partnerId: String,
    /** mentor | mentee — this user's role in the pairing. */
    val role: String,
    val displayName: String?,
    val status: String,
    /** True when the other party sent the invite and it awaits this user. */
    val incoming: Boolean,
    val createdAt: String,
)

class PartnersRepository(private val client: SupabaseClient) {

    suspend fun listPartners(userId: String): List<Partner> {
        val rels = client.postgrest.from("mentor_relationships")
            .select(Columns.list("id", "mentor_id", "mentee_id", "status", "initiator_id", "created_at")) {
                filter {
                    or {
                        eq("mentor_id", userId)
                        eq("mentee_id", userId)
                    }
                }
            }
            .decodeList<MentorRow>()
        if (rels.isEmpty()) return emptyList()

        val partnerIds = rels.map { if (it.mentorId == userId) it.menteeId else it.mentorId }.distinct()
        val names = client.postgrest.from("profiles")
            .select(Columns.list("id", "display_name", "avatar_url")) {
                filter { isIn("id", partnerIds) }
            }
            .decodeList<PersonRef>()
            .associateBy { it.id }

        return rels.map { r ->
            val isMentor = r.mentorId == userId
            val pid = if (isMentor) r.menteeId else r.mentorId
            Partner(
                relationshipId = r.id,
                partnerId = pid,
                role = if (isMentor) "mentor" else "mentee",
                displayName = names[pid]?.displayName,
                status = r.status,
                incoming = r.status == "pending" && r.initiatorId != userId,
                createdAt = r.createdAt,
            )
        }
    }

    /**
     * Sends a pairing invite as [asRole]. Upsert on the (mentor_id, mentee_id)
     * pair so re-inviting the same person doesn't stack duplicate rows; RLS
     * pins the row to `pending` on insert regardless of what's sent.
     */
    suspend fun pairPartner(userId: String, partnerId: String, asRole: String) {
        require(partnerId != userId) { "self" }
        val mentor = if (asRole == "mentor") userId else partnerId
        val mentee = if (asRole == "mentor") partnerId else userId
        client.postgrest.from("mentor_relationships").upsert(
            buildJsonObject {
                put("mentor_id", mentor)
                put("mentee_id", mentee)
                put("status", "pending")
                put("initiator_id", userId)
            },
        ) { onConflict = "mentor_id,mentee_id" }
    }

    /** Invitee-only: accept (→ active) or decline. */
    suspend fun respondToPairing(relationshipId: String, accept: Boolean) {
        client.postgrest.from("mentor_relationships").update(
            { set("status", if (accept) "active" else "declined") },
        ) {
            filter { eq("id", relationshipId) }
        }
    }

    suspend fun endPartnership(relationshipId: String) {
        client.postgrest.from("mentor_relationships").delete {
            filter { eq("id", relationshipId) }
        }
    }
}
