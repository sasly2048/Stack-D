package app.stackd.core.premium

import android.content.Context
import app.stackd.core.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Post-upgrade celebration trigger — web celebration-bus.ts.
 *
 * Payment happens in the browser (Razorpay's secret is server-side), so there
 * is no in-app onSuccess to hook like web's upgrade dialog. Instead every
 * entitlement read is fed to [observe], which compares against the last tier
 * seen for this user: a free→pro/elite (or pro→elite) jump fires the matching
 * celebration once. The first observation for a user only records a baseline,
 * so existing subscribers never get a stray celebration.
 */
object Celebration {
    /** "pro" | "elite" waiting to show; the root host clears it on close. */
    val pending = MutableStateFlow<String?>(null)

    private fun rank(tier: String) = when (tier) {
        "elite" -> 2
        "pro" -> 1
        else -> 0
    }

    /** Call only with a tier that was actually read — never a failure default. */
    fun observe(context: Context, userId: String, tier: String) {
        val prefs = context.getSharedPreferences("stackd_tier", Context.MODE_PRIVATE)
        val key = "tier:$userId"
        val prev = prefs.getString(key, null)
        prefs.edit().putString(key, tier).apply()
        if (prev != null && rank(tier) > rank(prev)) {
            pending.value = if (rank(tier) == 2) "elite" else "pro"
        }
    }

    /** Re-reads the entitlement (app resume — e.g. back from web checkout). */
    suspend fun check(container: AppContainer) {
        val uid = container.auth.currentUserId ?: return
        val ent = runCatching { container.premium.myEntitlement() }.getOrNull() ?: return
        observe(container.appContextForWork, uid, ent.tier)
    }
}
