package app.stackd.feature.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stackd.core.AppContainer
import app.stackd.data.premium.AiUsage
import app.stackd.data.premium.Entitlement
import app.stackd.data.premium.LifetimePromoStatus
import app.stackd.data.premium.Plan
import app.stackd.data.premium.SubscriptionRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PremiumUiState(
    val loading: Boolean = true,
    val entitlement: Entitlement = Entitlement(),
    val plans: List<Plan> = emptyList(),
    val promo: LifetimePromoStatus = LifetimePromoStatus(),
    val aiUsage: AiUsage = AiUsage(),
    val subscription: SubscriptionRow? = null,
    val redeeming: Boolean = false,
    /** Feedback from the last coupon attempt, shown inline. */
    val redeemMessage: String? = null,
    val redeemSucceeded: Boolean = false,
    /** Name engraved on the member card; null until (or unless) the profile loads. */
    val holderName: String? = null,
)

/** The web's `RedeemResult` → user copy map, verbatim. */
private val REDEEM_MESSAGES = mapOf(
    "ok" to "Lifetime access unlocked. Welcome to Elite, forever.",
    "inactive" to "This promotion isn't currently active.",
    "bad_code" to "That coupon code isn't valid.",
    "sold_out" to "All lifetime seats have been claimed.",
    "already" to "You've already redeemed lifetime access.",
    "unauth" to "Please sign in to redeem.",
)

class PremiumViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(PremiumUiState())
    val state: StateFlow<PremiumUiState> = _state

    init {
        refresh()
    }

    // Getter: init { load() } runs before stored properties declared below it.
    private val cacheKey: String get() = "premium:${container.auth.currentUserId ?: "anon"}"

    fun refresh() {
        // Stale-while-revalidate: seed from the last cached state so re-entry
        // shows data instantly instead of a spinner, then revalidate below.
        val cached: PremiumUiState? = container.cache.get(cacheKey)
        _state.value = (cached ?: _state.value).copy(loading = cached == null)
        // Card name only: its own launch so a slow profile read never holds up plans.
        container.auth.currentUserId?.let { uid ->
            viewModelScope.launch {
                val name = runCatching { container.profiles.getProfile(uid)?.displayName }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: return@launch
                _state.value = _state.value.copy(holderName = name)
            }
        }
        viewModelScope.launch {
            val premium = container.premium
            val entRead = runCatching { premium.myEntitlement() }
            // Only a real read feeds the upgrade detector — a failure default
            // of "free" would make the next real read look like an upgrade.
            entRead.getOrNull()?.let { e ->
                container.auth.currentUserId?.let { uid ->
                    app.stackd.core.premium.Celebration.observe(container.appContextForWork, uid, e.tier)
                }
            }
            val ent = entRead.getOrDefault(Entitlement())
            val plans = runCatching { premium.listPlans() }.getOrDefault(emptyList())
            val promo = runCatching { premium.lifetimePromoStatus() }.getOrDefault(LifetimePromoStatus())
            val usage = runCatching { premium.aiUsage() }.getOrDefault(AiUsage())
            val sub = runCatching { premium.mySubscription() }.getOrNull()
            val fresh = _state.value.copy(
                loading = false,
                entitlement = ent,
                plans = plans,
                promo = promo,
                aiUsage = usage,
                subscription = sub,
            )
            _state.value = fresh
            container.cache.put(cacheKey, fresh)
        }
    }

    fun redeemLifetime(code: String) {
        if (code.isBlank() || _state.value.redeeming) return
        _state.value = _state.value.copy(redeeming = true, redeemMessage = null)
        viewModelScope.launch {
            val result = runCatching { container.premium.redeemLifetime(code) }
                .getOrDefault("bad_code")
            val ok = result == "ok"
            _state.value = _state.value.copy(
                redeeming = false,
                redeemMessage = REDEEM_MESSAGES[result] ?: REDEEM_MESSAGES.getValue("bad_code"),
                redeemSucceeded = ok,
            )
            if (ok) refresh()
        }
    }
}
