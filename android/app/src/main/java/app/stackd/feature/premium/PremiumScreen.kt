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
            Text("STACK'D / PREMIUM", style = MonoLabel, color = colors.textMuted)
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
                Spacer(Modifier.height(12.dp))
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
                SectionLabel("PLANS")
                Spacer(Modifier.height(8.dp))
                state.plans.forEach { plan ->
                    PlanCard(plan, onOpenWeb)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Payment opens in your browser — subscriptions are handled on the web app.",
                    style = MonoLabelSmall,
                    color = colors.textMuted,
                )
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
                    EmberButton(
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
                    Spacer(Modifier.height(8.dp))
                }

            Spacer(Modifier.height(16.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, onOpenWeb: (String) -> Unit) {
    val colors = Stackd.colors
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    plan.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "₹${plan.priceInr} / ${if (plan.interval == "annual") "year" else "month"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        EmberButton(
            text = "Continue on the web",
            onClick = { onOpenWeb("/dashboard") },
        )
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
