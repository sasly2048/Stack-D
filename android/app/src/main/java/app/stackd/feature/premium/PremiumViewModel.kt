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

    fun refresh() {
        viewModelScope.launch {
            val premium = container.premium
            val ent = runCatching { premium.myEntitlement() }.getOrDefault(Entitlement())
            val plans = runCatching { premium.listPlans() }.getOrDefault(emptyList())
            val promo = runCatching { premium.lifetimePromoStatus() }.getOrDefault(LifetimePromoStatus())
            val usage = runCatching { premium.aiUsage() }.getOrDefault(AiUsage())
            val sub = runCatching { premium.mySubscription() }.getOrNull()
            _state.value = _state.value.copy(
                loading = false,
                entitlement = ent,
                plans = plans,
                promo = promo,
                aiUsage = usage,
                subscription = sub,
            )
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
