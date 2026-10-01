package app.stackd.feature.premium

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.BuildConfig
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.core.ui.pressFeedback
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import app.stackd.data.premium.Plan

/**
 * Premium — ported from the web's upgrade dialog + manage-subscription +
 * lifetime coupon + AI usage meter, condensed into one screen.
 *
 * Payment itself happens on the web: Razorpay's key secret lives on the web
 * server, and Google Play policy bars in-app third-party billing for digital
 * goods regardless. Every "upgrade" action opens the browser at the web app.
 */

/** Feature comparison rows — the web's premium-catalog.ts, display fields only. */
private data class CatalogRow(val label: String, val tier: String, val status: String)

private val CATALOG = listOf(
    CatalogRow("Focus DNA", "pro", "live"),
    CatalogRow("Deep Analytics", "pro", "live"),
    CatalogRow("Unlimited History", "pro", "live"),
    CatalogRow("Custom Protocols", "pro", "soon"),
    CatalogRow("Advanced Session Recaps", "pro", "beta"),
    CatalogRow("Advanced Leaderboards", "pro", "live"),
    CatalogRow("Progress Insights", "pro", "beta"),
    CatalogRow("Custom Themes", "pro", "beta"),
    CatalogRow("Atlas AI Coach", "elite", "beta"),
    CatalogRow("Focus Forecast", "elite", "live"),
    CatalogRow("Adaptive Sessions", "elite", "soon"),
    CatalogRow("Focus Autopilot", "elite", "soon"),
    CatalogRow("Private Focus Circles", "elite", "soon"),
    CatalogRow("Advanced Room Controls", "elite", "soon"),
    CatalogRow("Elite Weekly Reports", "elite", "beta"),
    CatalogRow("Memory Vault", "elite", "live"),
    CatalogRow("Time Capsules", "elite", "live"),
    CatalogRow("Early Access", "elite", "soon"),
)

@Composable
fun PremiumRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: PremiumViewModel = viewModel(factory = stackdViewModel { PremiumViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    PremiumScreen(
        state = state,
        onOpenWeb = { path ->
            val url = BuildConfig.WEB_BASE_URL.trimEnd('/') + path
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
            }
        },
        onRedeem = vm::redeemLifetime,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun PremiumScreen(
    state: PremiumUiState,
    onOpenWeb: (path: String) -> Unit,
    onRedeem: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ResponsiveColumn {
            app.stackd.core.ui.ScreenHeader("STACK'D / PREMIUM", onBack)
            Spacer(Modifier.height(24.dp))

            val ent = state.entitlement
            SectionLabel("YOUR ACCESS")
            Spacer(Modifier.height(8.dp))
            Text(
                ent.tier.uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    ent.source == "lifetime" -> "Lifetime access — yours forever."
                    ent.isPremium && ent.expiresAt != null -> "Renews / expires ${ent.expiresAt.take(10)}"
                    ent.isPremium -> "Active subscription."
                    else -> "Free tier. Upgrade to unlock the intelligence layer."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )

            // Manage / cancel lives on the web (Razorpay key secret is server-side).
            state.subscription?.let { sub ->
                Spacer(Modifier.height(16.dp))
                Card {
                    Text("SUBSCRIPTION", style = MonoLabelSmall, color = colors.textMuted)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Status: ${sub.status}" +
                            if (sub.cancelAtPeriodEnd) " · cancels at period end" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textPrimary,
                    )
                    sub.currentPeriodEnd?.let {
                        Text(
                            "Current period ends ${it.take(10)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    GhostButton(text = "Manage on the web", onClick = { onOpenWeb("/profile") })
                }
            }

            // AI usage meter — transparent counter, same numbers as the web.
            if (state.aiUsage.allowance > 0 || state.aiUsage.unlimited) {
                Spacer(Modifier.height(16.dp))
                Card {
                    Text("AI USAGE THIS PERIOD", style = MonoLabelSmall, color = colors.textMuted)
                    Spacer(Modifier.height(6.dp))
                    if (state.aiUsage.unlimited) {
                        Text("Unlimited", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                    } else {
                        Text(
                            "${state.aiUsage.used} / ${state.aiUsage.allowance}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        val pct = (state.aiUsage.used.toFloat() / state.aiUsage.allowance).coerceIn(0f, 1f)
                        Box(
                            Modifier.fillMaxWidth().height(6.dp)
                                .background(colors.textPrimary.copy(alpha = 0.05f), CircleShape),
                        ) {
                            Box(
                                Modifier.fillMaxWidth(pct).height(6.dp)
                                    .background(colors.accent, CircleShape),
                            )
                        }
                    }
                }
            }

            // Plans — price display from the live `plans` table; pay on web.
            if (!state.entitlement.isElite && state.plans.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionLabel("CHOOSE YOUR PLAN")
                Spacer(Modifier.height(12.dp))
                PlanPicker(state.plans, state.entitlement.isPro, onOpenWeb)
            }

            // Lifetime coupon.
            if (state.promo.active && !state.promo.alreadyRedeemed) {
                Spacer(Modifier.height(24.dp))
                Card {
                    Text("LIFETIME ACCESS", style = MonoLabelSmall, color = colors.accent)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${state.promo.seatsRemaining} of ${state.promo.seatsTotal} seats left",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    var code by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(120) },
                        label = { Text("Coupon code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    // Secondary: the plan CTA above is this screen's one primary action.
                    GhostButton(
                        text = if (state.redeeming) "Redeeming…" else "Redeem",
                        onClick = { onRedeem(code) },
                        enabled = code.isNotBlank(),
                        busy = state.redeeming,
                    )
                }
            }
            state.redeemMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.redeemSucceeded) colors.accent else colors.textMuted,
                )
            }

            // Feature comparison.
            Spacer(Modifier.height(24.dp))
            SectionLabel("WHAT EACH TIER UNLOCKS")
            Spacer(Modifier.height(8.dp))
            listOf("pro" to "PRO — UNDERSTAND YOUR FOCUS", "elite" to "ELITE — OPTIMIZE YOUR FOCUS")
                .forEach { (tier, heading) ->
                    Card {
                        Text(heading, style = MonoLabelSmall, color = colors.accent)
                        Spacer(Modifier.height(6.dp))
                        CATALOG.filter { it.tier == tier }.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    row.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    when (row.status) {
                                        "live" -> "LIVE"
                                        "beta" -> "BETA"
                                        else -> "SOON"
                                    },
                                    style = MonoLabelSmall,
                                    color = if (row.status == "live") colors.accent else colors.textMuted,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

            Spacer(Modifier.height(40.dp))
        }
    }
}

/**
 * One plan picker instead of four identical "Continue on the web" cards:
 * tier + billing toggles, the resulting price (with the real annual saving
 * computed from the live `plans` rows), what that tier unlocks, one CTA.
 */
@Composable
private fun PlanPicker(plans: List<Plan>, alreadyPro: Boolean, onOpenWeb: (String) -> Unit) {
    val colors = Stackd.colors
    val tiers = listOf("pro", "elite").filter { t -> plans.any { it.tier == t } && !(alreadyPro && t == "pro") }
    if (tiers.isEmpty()) return
    var tier by remember { mutableStateOf(tiers.last()) }
    var annual by remember { mutableStateOf(true) }
    fun plan(t: String, yearly: Boolean) = plans.firstOrNull { it.tier == t && (it.interval == "annual") == yearly }
    val monthly = plan(tier, false)
    val yearly = plan(tier, true)
    val selected = (if (annual) yearly else monthly) ?: monthly ?: yearly ?: return
    val savePct = if (monthly != null && yearly != null && monthly.priceInr > 0) {
        Math.round((1 - yearly.priceInr / (monthly.priceInr * 12.0)) * 100).toInt()
    } else 0

    if (tiers.size > 1) {
        Segmented(tiers.map { it.uppercase() }, tiers.indexOf(tier)) { tier = tiers[it] }
        Spacer(Modifier.height(8.dp))
    }
    if (monthly != null && yearly != null) {
        Segmented(
            listOf("MONTHLY", if (savePct > 0) "ANNUAL · SAVE $savePct%" else "ANNUAL"),
            if (annual) 1 else 0,
        ) { annual = it == 1 }
        Spacer(Modifier.height(12.dp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.05f), Radius2Xl)
            .border(1.dp, colors.accent.copy(alpha = 0.35f), Radius2Xl)
            .padding(20.dp),
    ) {
        Text(
            if (tier == "elite") "ELITE — OPTIMIZE YOUR FOCUS" else "PRO — UNDERSTAND YOUR FOCUS",
            style = MonoLabelSmall,
            color = colors.accent,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "₹${selected.priceInr}",
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                if (selected.interval == "annual") "/ year" else "/ month",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (selected.interval == "annual") {
            Text(
                "₹${Math.round(selected.priceInr / 12.0)} / month, billed yearly",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
        Spacer(Modifier.height(16.dp))
        CATALOG.filter { it.tier == tier && it.status == "live" }.take(4).forEach { row ->
            Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("✓", style = MaterialTheme.typography.bodySmall, color = colors.accent)
                Text(row.label, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
            }
        }
        Spacer(Modifier.height(16.dp))
        EmberButton(
            text = "Continue with ${selected.displayName}",
            onClick = { onOpenWeb("/dashboard") },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Checkout opens securely in your browser.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Pill segmented control; each segment is a full 48dp target. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = Stackd.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.04f), CircleShape)
            .border(1.dp, colors.border, CircleShape)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val source = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .pressFeedback(source)
                    .clip(CircleShape)
                    .background(if (on) colors.accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                    .selectable(
                        selected = on,
                        interactionSource = source,
                        indication = null,
                        role = androidx.compose.ui.semantics.Role.Tab,
                    ) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MonoLabelSmall, color = if (on) colors.accent else colors.textMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.03f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(16.dp),
        content = content,
    )
}
