package app.stackd.feature.premium

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.BuildConfig
import app.stackd.core.feedback.Sfx
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.Ember
import app.stackd.core.theme.EmberGlow
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Obsidian
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.RadiusXl
import app.stackd.core.theme.SerifFamily
import app.stackd.core.theme.Silver
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EaseRitual
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.pressFeedback
import app.stackd.data.premium.Plan
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.ChartLine
import com.composables.icons.lucide.Dna
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Infinity
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Telescope
import com.composables.icons.lucide.TrendingUp
import com.composables.icons.lucide.Trophy
import com.composables.icons.lucide.Zap
import kotlinx.coroutines.launch

/**
 * Premium — the web's upgrade dialog + manage-subscription + lifetime coupon +
 * AI usage meter, as one screen.
 *
 * Payment happens on the web: Razorpay's key secret lives on the web server,
 * and Google Play policy bars in-app third-party billing for digital goods.
 * Every "upgrade" action opens the browser at the web app.
 *
 * Design: sell the outcome, not the tier table. One tactile hero (a member
 * card you can tilt, that flips when you change plan), two plan cards with
 * the monthly price up front, only benefits that exist today, a trust row,
 * and one pinned action.
 */

/** The web's premium-catalog.ts, display fields only. */
private data class Perk(val label: String, val tier: String, val status: String, val icon: ImageVector)

private val CATALOG = listOf(
    Perk("Focus DNA", "pro", "live", Lucide.Dna),
    Perk("Deep analytics", "pro", "live", Lucide.ChartLine),
    Perk("Unlimited history", "pro", "live", Lucide.Infinity),
    Perk("Custom protocols", "pro", "soon", Lucide.Sparkles),
    Perk("Advanced session recaps", "pro", "beta", Lucide.Sparkles),
    Perk("Advanced leaderboards", "pro", "live", Lucide.Trophy),
    Perk("Progress insights", "pro", "beta", Lucide.TrendingUp),
    Perk("Custom themes", "pro", "beta", Lucide.Palette),
    Perk("Atlas AI coach", "elite", "beta", Lucide.Bot),
    Perk("Focus forecast", "elite", "live", Lucide.Telescope),
    Perk("Adaptive sessions", "elite", "soon", Lucide.Sparkles),
    Perk("Focus autopilot", "elite", "soon", Lucide.Sparkles),
    Perk("Private focus circles", "elite", "soon", Lucide.Sparkles),
    Perk("Advanced room controls", "elite", "soon", Lucide.Sparkles),
    Perk("Weekly elite reports", "elite", "beta", Lucide.FileText),
    Perk("Memory vault", "elite", "live", Lucide.Archive),
    Perk("Time capsules", "elite", "live", Lucide.Hourglass),
    Perk("Early access", "elite", "soon", Lucide.Sparkles),
)

/** Elite includes everything in Pro. */
private fun perksFor(tier: String) = CATALOG.filter { it.tier == "pro" || tier == "elite" }

private val TAGLINE = mapOf("pro" to "Understand your focus", "elite" to "Optimize your focus")

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
    val ent = state.entitlement
    val tiers = listOf("pro", "elite").filter { t -> state.plans.any { it.tier == t } && !(ent.isPro && t == "pro") }
    val selling = !ent.isElite && tiers.isNotEmpty()
    var tier by rememberSaveable { mutableStateOf("elite") }
    if (tier !in tiers && tiers.isNotEmpty()) tier = tiers.last()
    var annual by rememberSaveable { mutableStateOf(true) }
    fun plan(t: String, yearly: Boolean) = state.plans.firstOrNull { it.tier == t && (it.interval == "annual") == yearly }
    val chosen = plan(tier, annual) ?: plan(tier, !annual)

    Box(modifier.fillMaxSize().background(colors.background)) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ResponsiveColumn {
                app.stackd.core.ui.ScreenHeader("STACK'D / PREMIUM", onBack)
                Spacer(Modifier.height(8.dp))

                // Hero: the card you're buying (or already hold).
                val heroTier = if (selling) tier else ent.tier
                MemberCard(heroTier, owned = !selling && ent.isPremium)
                Spacer(Modifier.height(28.dp))

                if (selling) {
                    Text(
                        if (ent.isPro) "Go all the way." else "Unlock your best focus.",
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SerifFamily),
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Deeper insight into how you focus, an AI coach, and a vault for your best sessions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(24.dp))

                    val hasBoth = tiers.any { plan(it, true) != null && plan(it, false) != null }
                    if (hasBoth) {
                        BillingToggle(annual, savePct(plan(tier, false), plan(tier, true))) { annual = it }
                        Spacer(Modifier.height(16.dp))
                    }
                    tiers.reversed().forEach { t ->
                        val p = plan(t, annual) ?: plan(t, !annual) ?: return@forEach
                        PlanOption(t, p, selected = t == tier, best = t == "elite" && tiers.size > 1) { tier = t }
                        Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Benefits(tier)
                    Spacer(Modifier.height(24.dp))
                    TrustRow()
                } else {
                    Membership(state, onOpenWeb)
                }

                if (state.promo.active && !state.promo.alreadyRedeemed) {
                    Spacer(Modifier.height(28.dp))
                    LifetimeCode(state, onRedeem)
                }
                Spacer(Modifier.height(if (selling) 150.dp else 48.dp))
            }
        }
        if (selling && chosen != null) {
            CheckoutBar(chosen, Modifier.align(Alignment.BottomCenter)) { onOpenWeb("/dashboard") }
        }
    }
}

private fun savePct(monthly: Plan?, yearly: Plan?): Int =
    if (monthly != null && yearly != null && monthly.priceInr > 0) {
        Math.round((1 - yearly.priceInr / (monthly.priceInr * 12.0)) * 100).toInt()
    } else 0

/**
 * The tactile hero: a member card that tilts under your finger and springs
 * back, with a light sheen that tracks the tilt and drifts on its own. When
 * the plan changes it turns over to reveal the other card.
 */
@Composable
private fun MemberCard(tier: String, owned: Boolean) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val flip = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(tier) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tier) {
        if (tier == shown) return@LaunchedEffect
        flip.animateTo(90f, tween(220, easing = androidx.compose.animation.core.FastOutLinearInEasing))
        shown = tier
        flip.snapTo(-90f)
        flip.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
    }
    val drift by rememberInfiniteTransition(label = "sheen").animateFloat(
        0f, 1f, infiniteRepeatable(tween(5200, easing = EaseRitual), RepeatMode.Reverse), label = "drift",
    )
    val elite = shown == "elite"
    val base = if (elite) {
        Brush.linearGradient(listOf(Color(0xFF3A2414), Color(0xFF8A5530), Ember, Color(0xFF5A351C)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF2A2A2C), Color(0xFF6E6E72), Silver, Color(0xFF3C3C40)))
    }
    val ink = if (elite) Color(0xFFFFF4E8) else Color(0xFF111113)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f) // ISO card
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch { tiltX.animateTo(0f, spring(0.45f, Spring.StiffnessLow)) }
                        scope.launch { tiltY.animateTo(0f, spring(0.45f, Spring.StiffnessLow)) }
                    },
                ) { change, drag ->
                    change.consume()
                    scope.launch { tiltY.snapTo((tiltY.value + drag.x / 12f).coerceIn(-16f, 16f)) }
                    scope.launch { tiltX.snapTo((tiltX.value - drag.y / 12f).coerceIn(-12f, 12f)) }
                }
            }
            .graphicsLayer {
                cameraDistance = 14f * density.density
                rotationX = tiltX.value
                rotationY = tiltY.value + flip.value
                shadowElevation = 24.dp.toPx()
                shape = RoundedCornerShape(22.dp)
                clip = true
                ambientShadowColor = if (elite) Ember else Color.Black
                spotShadowColor = if (elite) Ember else Color.Black
            }
            .background(base)
            .drawWithContent {
                drawContent()
                // Light catching the card: a soft band whose position follows
                // the tilt, plus a slow idle drift so it never sits dead.
                val w = size.width
                val x = w * (drift * 0.6f - 0.3f + tiltY.value / 40f)
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        0.45f to Color.White.copy(alpha = 0.16f),
                        0.5f to Color.White.copy(alpha = 0.28f),
                        0.55f to Color.White.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                        start = Offset(x, size.height),
                        end = Offset(x + w * 0.9f, 0f),
                    ),
                )
                drawRect(Color.White.copy(alpha = 0.12f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
            .padding(22.dp),
    ) {
        Text(
            "Stack'd",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily),
            color = ink,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            if (owned) "MEMBER" else "PREVIEW",
            style = MonoLabelSmall,
            color = ink.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(
                shown.replaceFirstChar { it.uppercase() }.ifBlank { "Free" },
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = SerifFamily),
                color = ink,
            )
            Text(
                TAGLINE[shown] ?: "Focus, together",
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun BillingToggle(annual: Boolean, save: Int, onChange: (Boolean) -> Unit) {
    val colors = Stackd.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.05f), CircleShape)
            .padding(4.dp),
    ) {
        listOf(false to "Monthly", true to "Yearly").forEach { (yearly, label) ->
            val on = annual == yearly
            val bg by animateColorAsState(if (on) colors.textPrimary else Color.Transparent, label = "bill")
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(bg, CircleShape)
                    .selectable(on, role = Role.Tab) {
                        if (!on) Sfx.play(Sfx.Kind.SELECT)
                        onChange(yearly)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) colors.background else colors.textMuted,
                )
                if (yearly && save > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "−$save%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (on) Obsidian else colors.accent,
                        modifier = Modifier
                            .background(if (on) Ember else colors.accent.copy(alpha = 0.14f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** One tier as a selectable card: name + promise left, monthly price right. */
@Composable
private fun PlanOption(tier: String, plan: Plan, selected: Boolean, best: Boolean, onClick: () -> Unit) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    val border by animateColorAsState(if (selected) colors.accent else colors.border, tween(250, easing = EaseRitual), label = "planBorder")
    val fill by animateColorAsState(
        if (selected) colors.accent.copy(alpha = 0.08f) else colors.textPrimary.copy(alpha = 0.03f),
        label = "planFill",
    )
    val yearly = plan.interval == "annual"
    val perMonth = if (yearly) Math.round(plan.priceInr / 12.0) else plan.priceInr
    Row(
        Modifier
            .fillMaxWidth()
            .pressFeedback(source, pressedScale = 0.985f, sound = Sfx.Kind.SELECT)
            .selectable(selected, interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick)
            .background(fill, RadiusXl)
            .border(if (selected) 1.5.dp else 1.dp, border, RadiusXl)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Radio
        Box(
            Modifier.size(22.dp).border(1.5.dp, if (selected) colors.accent else colors.textMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + androidx.compose.animation.scaleIn(),
                exit = fadeOut() + androidx.compose.animation.scaleOut(),
            ) {
                Box(Modifier.size(12.dp).background(colors.accent, CircleShape))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tier.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily),
                    color = colors.textPrimary,
                )
                if (best) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Best value",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Obsidian,
                        modifier = Modifier.background(Ember, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(TAGLINE[tier].orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            AnimatedContent(perMonth, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "price") { v ->
                Text(
                    "₹$v",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
            }
            Text(
                if (yearly) "/mo · ₹${plan.priceInr}/yr" else "/month",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

/** What you actually get — only what exists today; the roadmap is one line. */
@Composable
private fun Benefits(tier: String) {
    val colors = Stackd.colors
    AnimatedContent(tier, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }, label = "perks") { t ->
        val perks = perksFor(t)
        val now = perks.filter { it.status != "soon" }
        val soon = perks.count { it.status == "soon" }
        Column {
            Text(
                if (t == "elite") "Everything in Elite" else "Everything in Pro",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))
            now.forEach { p ->
                Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).background(colors.accent.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(p.icon, null, tint = colors.accent, modifier = Modifier.size(17.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Text(p.label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    if (p.status == "beta") {
                        Text("Beta", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                }
            }
            if (soon > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "+ $soon more on the way, included when they land",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun TrustRow() {
    val colors = Stackd.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(
            Lucide.RotateCcw to "Cancel anytime",
            Lucide.ShieldCheck to "Secure checkout",
            Lucide.Zap to "Instant access",
        ).forEach { (icon, label) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Pinned purchase action; content fades out beneath it. */
@Composable
private fun CheckoutBar(plan: Plan, modifier: Modifier, onCheckout: () -> Unit) {
    val colors = Stackd.colors
    val yearly = plan.interval == "annual"
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to colors.background))
            .navigationBarsPadding()
            .padding(top = 28.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).padding(horizontal = 20.dp)) {
            EmberButton(
                text = "Get ${plan.tier.replaceFirstChar { it.uppercase() }}",
                onClick = {
                    Sfx.play(Sfx.Kind.PURCHASE)
                    onCheckout()
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                (if (yearly) "₹${plan.priceInr} billed yearly" else "₹${plan.priceInr} billed monthly") +
                    " · checkout opens in your browser",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Members: status, usage, and the way to manage it. */
@Composable
private fun Membership(state: PremiumUiState, onOpenWeb: (String) -> Unit) {
    val colors = Stackd.colors
    val ent = state.entitlement
    Text(
        when {
            ent.source == "lifetime" -> "Yours for life."
            ent.isPremium -> "You're a member."
            else -> "Focus, free forever."
        },
        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SerifFamily),
        color = colors.textPrimary,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        when {
            ent.source == "lifetime" -> "Lifetime access. Every feature, every update."
            ent.isPremium && ent.expiresAt != null -> "Renews ${ent.expiresAt.take(10)}"
            else -> "Plans are unavailable right now. Try again later."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textMuted,
    )
    if (state.aiUsage.allowance > 0 || state.aiUsage.unlimited) {
        Spacer(Modifier.height(24.dp))
        Text("AI this period", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        if (state.aiUsage.unlimited) {
            Text("Unlimited", style = MaterialTheme.typography.bodyMedium, color = colors.accent)
        } else {
            val pct = (state.aiUsage.used.toFloat() / state.aiUsage.allowance).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(8.dp).background(colors.textPrimary.copy(alpha = 0.06f), CircleShape)) {
                Box(Modifier.fillMaxWidth(pct).height(8.dp).background(colors.accent, CircleShape))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${state.aiUsage.used} of ${state.aiUsage.allowance} used",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
    if (ent.isPremium) {
        Spacer(Modifier.height(24.dp))
        Benefits(ent.tier)
    }
    state.subscription?.let { sub ->
        Spacer(Modifier.height(24.dp))
        Text(
            "Subscription ${sub.status}" + if (sub.cancelAtPeriodEnd) " · ends at period end" else "",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(10.dp))
        GhostButton(text = "Manage on the web", onClick = { onOpenWeb("/profile") })
    }
}

/** Lifetime coupon, folded away: most people never need it. */
@Composable
private fun LifetimeCode(state: PremiumUiState, onRedeem: (String) -> Unit) {
    val colors = Stackd.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius2Xl)
            .clickable(role = Role.Button) {
                Sfx.play(if (open) Sfx.Kind.CLOSE else Sfx.Kind.OPEN)
                open = !open
            }
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Have a lifetime code?", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(
                "${state.promo.seatsRemaining} of ${state.promo.seatsTotal} lifetime seats left",
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent,
            )
        }
        Icon(
            Lucide.Sparkles,
            null,
            tint = colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column {
            Spacer(Modifier.height(10.dp))
            var code by remember { mutableStateOf("") }
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(120) },
                label = { Text("Code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
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
        Text(it, style = MaterialTheme.typography.bodySmall, color = if (state.redeemSucceeded) colors.accent else colors.textMuted)
    }
}
