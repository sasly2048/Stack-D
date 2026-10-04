package app.stackd.data.premium

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Mirrors the web's `subscription.functions.ts` — every read is a user-context
 * RPC or an RLS-scoped table, so Android calls the same objects directly.
 *
 * Deliberately absent: checkout, cancel and resume. Those run Razorpay with the
 * key secret on the web server, and Play policy forbids a sideloadable app from
 * taking digital-goods payments in-app anyway — the UI hands off to the web.
 */

/** The caller's effective access, resolved server-side by `my_entitlement`. */
@Serializable
data class Entitlement(
    val tier: String = "free",
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("is_premium") val isPremium: Boolean = false,
    val source: String = "none",
    @SerialName("expires_at") val expiresAt: String? = null,
) {
    val isPro: Boolean get() = tier == "pro" || tier == "elite"
    val isElite: Boolean get() = tier == "elite"
}

@Serializable
data class Plan(
    val id: String,
    val tier: String,
    val interval: String,
    @SerialName("price_inr") val priceInr: Long,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class LifetimePromoStatus(
    val active: Boolean = false,
    @SerialName("seats_total") val seatsTotal: Int = 0,
    @SerialName("seats_remaining") val seatsRemaining: Int = 0,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("already_redeemed") val alreadyRedeemed: Boolean = false,
)

@Serializable
data class AiUsage(
    val used: Int = 0,
    val allowance: Int = 0,
    val remaining: Int = 0,
    val unlimited: Boolean = false,
)

/** The caller's own subscription row (RLS-scoped), for the manage section. */
@Serializable
data class SubscriptionRow(
    val id: String,
    val status: String,
    @SerialName("plan_id") val planId: String? = null,
    @SerialName("current_period_end") val currentPeriodEnd: String? = null,
    @SerialName("cancel_at_period_end") val cancelAtPeriodEnd: Boolean = false,
)

class PremiumRepository(private val client: SupabaseClient) {

    suspend fun myEntitlement(): Entitlement =
        client.postgrest.rpc("my_entitlement")
            .decodeList<Entitlement>()
            .firstOrNull() ?: Entitlement()

    suspend fun listPlans(): List<Plan> =
        client.postgrest.from("plans")
            .select(
                io.github.jan.supabase.postgrest.query.Columns.list(
                    "id", "tier", "interval", "price_inr", "display_name",
                ),
            ) {
                filter { eq("is_active", true) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    suspend fun lifetimePromoStatus(): LifetimePromoStatus =
        client.postgrest.rpc("lifetime_promo_status")
            .decodeList<LifetimePromoStatus>()
            .firstOrNull() ?: LifetimePromoStatus()

    /**
     * Redeems a lifetime coupon. Atomicity, seat cap, duplicate guard and code
     * match all live in the row-locked RPC; the return is one of the web's
     * `RedeemResult` strings (ok | inactive | bad_code | sold_out | already).
     */
    suspend fun redeemLifetime(code: String): String =
        client.postgrest.rpc(
            "redeem_lifetime",
            buildJsonObject { put("_code", code.trim().take(120)) },
        ).decodeAs<String>()

    suspend fun aiUsage(): AiUsage =
        client.postgrest.rpc("ai_usage_status")
            .decodeList<AiUsage>()
            .firstOrNull() ?: AiUsage()

    /** Newest of the caller's subscription rows, if any. */
    suspend fun mySubscription(): SubscriptionRow? =
        client.postgrest.from("subscriptions")
            .select(
                io.github.jan.supabase.postgrest.query.Columns.list(
                    "id", "status", "plan_id", "current_period_end", "cancel_at_period_end",
                ),
            ) {
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<SubscriptionRow>()
            .firstOrNull()
}
