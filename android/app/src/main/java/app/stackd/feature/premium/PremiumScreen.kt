package app.stackd.feature.premium

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.BuildConfig
import app.stackd.core.feedback.Sfx
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.Ember
import app.stackd.core.theme.EmberGlow
import app.stackd.core.theme.IvoryInk
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.RadiusXl
import app.stackd.core.theme.SerifFamily
import app.stackd.core.theme.Silver
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EaseRitual
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.glassSurface
import app.stackd.core.ui.pageGlow
import app.stackd.core.ui.reveal
import app.stackd.data.premium.Plan
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.ChartLine
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Dna
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Infinity
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Telescope
import com.composables.icons.lucide.TrendingUp
import com.composables.icons.lucide.Trophy
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Premium — the web's upgrade dialog + manage-subscription + lifetime coupon +
 * AI usage meter, as one screen.
 *
 * Payment happens on the web: Razorpay's key secret lives on the web server,
 * and Google Play policy bars in-app third-party billing for digital goods.
 * Every "upgrade" action opens the browser at the web app.
 *
 * Design: a one-screen decision. One member card that IS the plan selector
 * (swipe it, or tap Pro · Elite beneath it; it turns over), the price, the
 * billing toggle, the price, the three things the selected tier gives you,
 * and one pinned ivory action. Everything else unfolds in place under
 * "Everything included".
 *
 * Type: four sizes (displayMedium/headlineLarge serif, bodyLarge/bodySmall
 * sans), two weights (Normal, SemiBold).
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

/**
 * Exactly three value lines per tier: catalog label → one-line outcome.
 * Elite's are what it ADDS over Pro, so the price gap reads at a glance.
 */
private val VALUE = mapOf(
    "pro" to listOf(
        "Focus DNA" to "your focus fingerprint: when, how long, how deep",
        "Deep analytics" to "trends across weeks, not just today",
        "Unlimited history" to "every session, kept forever",
    ),
    "elite" to listOf(
        "Atlas AI coach" to "reads your sessions and nudges you",
        "Focus forecast" to "your best hours, before the day starts",
        "Memory vault" to "keep the sessions that mattered",
    ),
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

// The page's four type sizes.
@Composable private fun serifXl() = MaterialTheme.typography.displayMedium.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Normal)
@Composable private fun serifLg() = MaterialTheme.typography.headlineLarge.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Normal)
@Composable private fun body() = MaterialTheme.typography.bodyLarge
@Composable private fun caption() = MaterialTheme.typography.bodySmall

private fun cap(tier: String) = tier.replaceFirstChar { it.uppercase() }

private fun inr(v: Long) = "₹" + String.format(Locale.ENGLISH, "%,d", v)

@Composable
fun PremiumScreen(
    state: PremiumUiState,
    onOpenWeb: (path: String) -> Unit,
    onRedeem: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    val view = LocalView.current
    val ent = state.entitlement
    val tiers = listOf("pro", "elite").filter { t -> state.plans.any { it.tier == t } && !(ent.isPro && t == "pro") }
    val selling = !ent.isElite && tiers.isNotEmpty()
    var tier by rememberSaveable { mutableStateOf("elite") }
    if (tier !in tiers && tiers.isNotEmpty()) tier = tiers.last()
    var annual by rememberSaveable { mutableStateOf(true) }
    fun plan(t: String, yearly: Boolean) = state.plans.firstOrNull { it.tier == t && (it.interval == "annual") == yearly }
    val chosen = plan(tier, annual) ?: plan(tier, !annual)
    fun select(t: String) {
        if (t == tier || t !in tiers) return
        Sfx.play(Sfx.Kind.SELECT)
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        tier = t
    }

    Box(modifier.fillMaxSize().background(colors.background).pageGlow()) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ResponsiveColumn {
                app.stackd.core.ui.ScreenHeader("STACK'D / PREMIUM", onBack, Modifier.reveal(0))
                Spacer(Modifier.height(8.dp))

                if (selling) {
                    // Tier-aware promise, kept to one line; crossfades with the plan.
                    AnimatedContent(
                        tier,
                        transitionSpec = { fadeIn(tween(320, easing = EaseRitual)) togetherWith fadeOut(tween(160)) },
                        label = "headline",
                        modifier = Modifier.reveal(1),
                    ) { t ->
                        Text(
                            if (ent.isPro) "Go all the way." else "Go further with ${cap(t)}.",
                            style = serifLg(),
                            color = colors.textPrimary,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    MemberCard(
                        tier,
                        holder = state.holderName,
                        modifier = Modifier.reveal(2),
                        onSwipe = { dir -> tiers.getOrNull(tiers.indexOf(tier) + dir)?.let(::select) },
                    )
                    if (tiers.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        TierPager(tiers, tier, Modifier.reveal(3), ::select)
                    }
                    // Billing first, then the price and value read against it.
                    val hasBoth = tiers.any { plan(it, true) != null && plan(it, false) != null }
                    if (hasBoth) {
                        Spacer(Modifier.height(12.dp))
                        BillingToggle(annual, savePct(plan(tier, false), plan(tier, true)), Modifier.reveal(4)) { annual = it }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (chosen != null) {
                        PriceBlock(chosen, savePct(plan(tier, false), plan(tier, true)), Modifier.reveal(5))
                        Spacer(Modifier.height(20.dp))
                    }
                    ValueLines(tier, Modifier.reveal(6))
                    Spacer(Modifier.height(8.dp))
                    EverythingIncluded(tier, compare = tiers.size == 2, modifier = Modifier.reveal(7)) {
                        if (state.promo.active && !state.promo.alreadyRedeemed) {
                            Spacer(Modifier.height(24.dp))
                            LifetimeCode(state, onRedeem)
                        }
                    }
                } else if (!state.loading) {
                    Membership(state, onOpenWeb)
                    if (state.promo.active && !state.promo.alreadyRedeemed) {
                        Spacer(Modifier.height(32.dp))
                        Box(Modifier.reveal(7)) { LifetimeCode(state, onRedeem) }
                    }
                }
                Spacer(Modifier.height(if (selling) 180.dp else 48.dp))
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

private fun perMonth(plan: Plan): Long =
    if (plan.interval == "annual") Math.round(plan.priceInr / 12.0) else plan.priceInr

/** Card look, motion and detail level; debug builds switch them via MainActivity extras. */
internal enum class CardStyle { MATTE_FOIL, FROSTED_GLASS }
internal enum class CardMotion { TILT_ONLY, SHINE_ON_OPEN }
internal enum class CardDetails { CREST, MINIMAL }
internal val cardPreview = mutableStateOf(Triple(CardStyle.MATTE_FOIL, CardMotion.SHINE_ON_OPEN, CardDetails.CREST))

/**
 * The tactile hero and, when selling, the plan selector: a card drawn in code.
 * [CardStyle.MATTE_FOIL] is matte obsidian with gold (Elite) or silver (Pro)
 * foil; [CardStyle.FROSTED_GLASS] is translucent glass over a coloured glow.
 * A single soft light rests top-left and follows the tilt under your finger;
 * with [CardMotion.SHINE_ON_OPEN] one slow shine also crosses the card on
 * open and on each tier change. Nothing animates while idle. A horizontal
 * swipe past a threshold asks for the neighbouring tier ([onSwipe] gets +1
 * for next, -1 for previous) and the card springs home. When [tier] changes
 * it turns over to reveal the other card.
 */
@Composable
private fun MemberCard(tier: String, holder: String?, modifier: Modifier = Modifier, onSwipe: ((Int) -> Unit)? = null) {
    val density = LocalDensity.current
    val (style, motion, details) = cardPreview.value
    val glass = style == CardStyle.FROSTED_GLASS
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val pull = remember { Animatable(0f) }
    val flip = remember { Animatable(0f) }
    val shine = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(tier) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tier) {
        if (tier == shown) return@LaunchedEffect
        // One continuous half-turn, starting the instant the swipe lets go; the
        // face swaps edge-on at 90°, so speed never breaks mid-flip.
        flip.animateTo(180f, tween(600, easing = FastOutSlowInEasing)) {
            if (value >= 90f && shown != tier) shown = tier
        }
        flip.snapTo(0f)
        // Then the light crosses the new face and leaves fully off the edge. A sweep
        // still running carries on from where it is rather than restarting.
        if (motion == CardMotion.SHINE_ON_OPEN) {
            if (shine.value >= 1f) shine.snapTo(0f)
            shine.animateTo(1f, tween(((1f - shine.value) * 1300).toInt().coerceAtLeast(1), easing = FastOutSlowInEasing))
        }
    }
    // The landing sweep; tier changes sweep after the flip (above).
    LaunchedEffect(motion) {
        if (motion != CardMotion.SHINE_ON_OPEN) {
            shine.snapTo(1f)
            return@LaunchedEffect
        }
        shine.snapTo(0f)
        // Symmetric ease: the band glides across evenly instead of rushing in and crawling out.
        shine.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
    }
    val elite = shown == "elite"
    val glow by animateColorAsState(
        if (elite) Ember.copy(alpha = 0.32f) else Color(0xFFC9D2DC).copy(alpha = 0.20f),
        tween(500, easing = EaseRitual),
        label = "underglow",
    )
    val blob by animateColorAsState(
        if (elite) Color(0xFFE0743A) else Color(0xFF8FB0D8),
        tween(500, easing = EaseRitual),
        label = "blob",
    )
    // Light position as a fraction of the card, read only at draw time.
    val light = { Offset(0.22f + tiltY.value / 16f * 0.3f, 0.18f - tiltX.value / 12f * 0.25f) }
    // Matte: content is drawn white and the foil is masked over it.
    val ink = if (glass) GlassInk else Color.White
    val lift = if (glass) Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 1f), 6f) else null
    val caps = caption().copy(fontSize = 11.sp, letterSpacing = 2.2.sp, fontWeight = FontWeight.SemiBold, shadow = lift)
    val year = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val threshold = with(density) { 64.dp.toPx() }
    val maxPull = with(density) { 120.dp.toPx() }
    val settle = {
        scope.launch { tiltX.animateTo(0f, spring(0.6f, Spring.StiffnessLow)) }
        scope.launch { tiltY.animateTo(0f, spring(0.6f, Spring.StiffnessLow)) }
        scope.launch { pull.animateTo(0f, spring(0.55f, Spring.StiffnessMediumLow)) }
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                // Underglow: a flattened pool of light under the card's lower
                // edge; for glass, also the coloured light seen through it.
                .drawBehind {
                    val c = Offset(size.width / 2f, size.height * 0.92f)
                    val r = size.width * 0.62f
                    scale(1f, 0.42f, pivot = c) {
                        drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), center = c, radius = r), r, c)
                    }
                    if (glass) {
                        val a = Offset(size.width * 0.32f, size.height * 0.4f)
                        val ra = size.width * 0.45f
                        val b = Offset(size.width * 0.72f, size.height * 0.65f)
                        val rb = size.width * 0.4f
                        drawCircle(Brush.radialGradient(listOf(blob.copy(alpha = 0.85f), Color.Transparent), a, ra), ra, a)
                        drawCircle(Brush.radialGradient(listOf(blob.copy(alpha = 0.6f), Color.Transparent), b, rb), rb, b)
                    }
                }
                .aspectRatio(1.586f) // ISO card
                .pointerInput(onSwipe != null) {
                    detectDragGestures(
                        onDragEnd = {
                            val dir = when {
                                pull.value < -threshold -> 1
                                pull.value > threshold -> -1
                                else -> 0
                            }
                            if (dir != 0) onSwipe?.invoke(dir)
                            settle()
                        },
                        onDragCancel = { settle() },
                    ) { change, drag ->
                        change.consume()
                        if (onSwipe != null) {
                            scope.launch { pull.snapTo((pull.value + drag.x * 0.6f).coerceIn(-maxPull, maxPull)) }
                        }
                        scope.launch { tiltY.snapTo((tiltY.value + drag.x / 12f).coerceIn(-16f, 16f)) }
                        scope.launch { tiltX.snapTo((tiltX.value - drag.y / 12f).coerceIn(-12f, 12f)) }
                    }
                }
                .graphicsLayer {
                    cameraDistance = 14f * density.density
                    translationX = pull.value
                    rotationX = tiltX.value
                    rotationY = tiltY.value + (if (flip.value > 90f) flip.value - 180f else flip.value) // back half shows the new face
                    // A translucent card would show its own shadow through it.
                    shadowElevation = if (glass) 0f else 24.dp.toPx()
                    shape = CardShape
                    clip = true
                    ambientShadowColor = if (elite) Ember else Color.Black
                    spotShadowColor = if (elite) Ember else Color.Black
                }
                .cardSurface(glass),
        ) {
            Box(Modifier.fillMaxSize().cardFace(glass, elite, light) { shine.value }.padding(20.dp)) {
                Text(
                    "Stack'd",
                    style = body().copy(fontFamily = SerifFamily, shadow = lift),
                    color = ink.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopStart),
                )
                if (details == CardDetails.CREST) {
                    StackCrest(ink, Modifier.align(Alignment.TopEnd).size(width = 26.dp, height = 28.dp))
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                    Text(cap(shown).ifBlank { "Free" }, style = serifXl().copy(shadow = lift), color = ink)
                    if (details == CardDetails.CREST) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (holder ?: "Stack'd member").uppercase(Locale.ROOT),
                                style = caps,
                                color = ink.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${shown.ifBlank { "free" }.uppercase(Locale.ROOT)} · $year",
                                style = caps.copy(fontSize = 9.sp, letterSpacing = 1.6.sp),
                                color = ink.copy(alpha = 0.55f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            // The light, on its own layer so tilt and shine re-record only this.
            Box(Modifier.matchParentSize().graphicsLayer().cardLight(light) { shine.value })
        }
    }
}

private val CardShape = RoundedCornerShape(24.dp)
private val GlassInk = Color(0xFFF7F1E8)
private val GoldFoil = listOf(Color(0xFF8A5A2B), Color(0xFFF0C27A), Color(0xFFB07A3E))
private val SilverFoil = listOf(Color(0xFF8E9196), Color(0xFFF2F3F5), Color(0xFF9DA1A7))

/** The card body, cached: matte obsidian, or frosted glass lighter at the top. */
private fun Modifier.cardSurface(glass: Boolean): Modifier = drawWithCache {
    val fill = if (glass) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.09f)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF121214), Color(0xFF0C0C0E)))
    }
    onDrawBehind { drawRect(fill) }
}

/**
 * The card's printed face plus its edge line. Matte: everything (content and
 * a thin edge inset 1.5dp) is drawn white, then foil is masked over it with
 * its highlight sitting where the [light] is. Glass: a white 20% → 5% edge.
 */
private fun Modifier.cardFace(glass: Boolean, elite: Boolean, light: () -> Offset, shine: () -> Float): Modifier =
    if (glass) {
        drawWithCache {
            val sw = 1.dp.toPx()
            val edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.05f)))
            val r = 24.dp.toPx() - sw / 2f
            onDrawWithContent {
                drawContent()
                drawRoundRect(edge, Offset(sw / 2f, sw / 2f), Size(size.width - sw, size.height - sw), CornerRadius(r), style = Stroke(sw))
            }
        }
    } else {
        graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithCache {
            val w = size.width
            val h = size.height
            val sw = 0.8.dp.toPx()
            val inset = 1.5.dp.toPx() + sw / 2f
            val r = 24.dp.toPx() - inset
            val (c0, c1, c2) = if (elite) GoldFoil else SilverFoil
            onDrawWithContent {
                drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(inset, inset), Size(w - inset * 2, h - inset * 2), CornerRadius(r), style = Stroke(sw))
                drawContent()
                // Project the light onto the top-left → bottom-right diagonal.
                val l = light()
                val rest = (l.x * w * w + l.y * h * h) / (w * w + h * h)
                // While the shine passes, the foil's gleam rides with it, then eases home.
                val s = shine()
                val k = sweepWeight(s)
                val p = (rest + (sweepX(s) - rest) * k).coerceIn(0.08f, 0.92f)
                drawRect(
                    Brush.linearGradient(0f to c0, p to c1, 1f to c2, start = Offset.Zero, end = Offset(w, h)),
                    blendMode = BlendMode.SrcIn,
                )
            }
        }
    }

/**
 * A soft elliptical reflection at [light] (fractions of the card), plus, while
 * [shine] is between 0 and 1, a low diagonal band sweeping left to right.
 */
/** Shine band centre as a fraction of card width: starts and ends fully off the card. */
private fun sweepX(s: Float) = -0.6f + s * 2.2f

/** How much the passing shine owns the light: 0 at rest, 1 mid-sweep, eased both ways. */
private fun sweepWeight(s: Float) = if (s > 0f && s < 1f) kotlin.math.sin(Math.PI * s).toFloat() else 0f

private fun Modifier.cardLight(light: () -> Offset, shine: () -> Float): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    val l = light()
    val c = Offset(w * l.x, h * l.y)
    val r = w * 0.7f
    val s = shine()
    // One light, not two: the resting glow gives way while the shine crosses
    // and fades back in as it leaves.
    val glow = 0.12f * (1f - 0.85f * sweepWeight(s))
    scale(1f, 0.65f, pivot = c) {
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = glow), Color.Transparent), c, r), r, c)
    }
    if (s > 0f && s < 1f) {
        val x = w * sweepX(s)
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to Color.White.copy(alpha = 0.10f),
                1f to Color.Transparent,
                start = Offset(x - w * 0.25f, h),
                end = Offset(x + w * 0.25f, 0f),
            ),
        )
    }
}

/** Stack'd crest: three offset rounded "phones", the front one solid. */
@Composable
private fun StackCrest(ink: Color, modifier: Modifier) {
    Box(
        modifier.drawWithCache {
            val pw = size.width * 0.62f
            val ph = size.height * 0.74f
            val dx = (size.width - pw) / 2f
            val dy = (size.height - ph) / 2f
            val cr = CornerRadius(3.dp.toPx())
            val line = Stroke(1.2.dp.toPx())
            onDrawBehind {
                drawRoundRect(ink.copy(alpha = 0.35f), Offset(dx * 2, 0f), Size(pw, ph), cr, line)
                drawRoundRect(ink.copy(alpha = 0.6f), Offset(dx, dy), Size(pw, ph), cr, line)
                drawRoundRect(ink.copy(alpha = 0.88f), Offset(0f, dy * 2), Size(pw, ph), cr)
            }
        },
    )
}

/** "Pro · Elite" under the card: selected bright, other muted, both tappable. */
@Composable
private fun TierPager(tiers: List<String>, selected: String, modifier: Modifier, onSelect: (String) -> Unit) {
    val colors = Stackd.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        tiers.forEachIndexed { i, t ->
            if (i > 0) Text("·", style = body(), color = colors.textMuted.copy(alpha = 0.5f))
            val on = t == selected
            val fg by animateColorAsState(if (on) colors.textPrimary else colors.textMuted.copy(alpha = 0.6f), tween(260, easing = EaseRitual), label = "pager")
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .selectable(on, role = Role.Tab) { onSelect(t) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(cap(t), style = body(), fontWeight = FontWeight.SemiBold, color = fg)
                if (t == "elite") {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Best value",
                        style = caption(),
                        color = colors.accent,
                        modifier = Modifier
                            .background(colors.accent.copy(alpha = 0.14f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** "₹150 /mo", what's actually billed, and the yearly saving; crossfades on change. */
@Composable
private fun PriceBlock(plan: Plan, save: Int, modifier: Modifier = Modifier) {
    val colors = Stackd.colors
    val yearly = plan.interval == "annual"
    AnimatedContent(
        Triple(perMonth(plan), plan.priceInr, yearly),
        transitionSpec = { fadeIn(tween(300, easing = EaseRitual)) togetherWith fadeOut(tween(150)) },
        label = "price",
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) { (month, total, y) ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(inr(month), style = serifLg(), color = colors.textPrimary)
                Text(" /mo", style = caption(), color = colors.textMuted, modifier = Modifier.padding(bottom = 6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (y) "billed ${inr(total)} yearly" else "billed monthly",
                    style = caption(),
                    color = colors.textMuted,
                )
                if (y && save > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Save $save%",
                        style = caption(),
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent,
                        modifier = Modifier
                            .background(colors.accent.copy(alpha = 0.14f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Three lines that answer "why this tier". Elite's are what it adds over
 * Pro, followed by a quiet "Everything in Pro included".
 */
@Composable
private fun ValueLines(tier: String, modifier: Modifier = Modifier) {
    val colors = Stackd.colors
    AnimatedContent(
        tier,
        transitionSpec = { fadeIn(tween(300, easing = EaseRitual)) togetherWith fadeOut(tween(150)) },
        label = "value",
        modifier = modifier.fillMaxWidth(),
    ) { t ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            VALUE[t].orEmpty().forEach { (label, line) ->
                val perk = CATALOG.firstOrNull { it.label == label } ?: return@forEach
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).background(colors.accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(perk.icon, null, tint = colors.accent, modifier = Modifier.size(16.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.SemiBold)) { append(label) }
                            withStyle(SpanStyle(color = colors.textMuted)) {
                                append(" — $line")
                                if (perk.status == "beta") append(" · Beta")
                            }
                        },
                        style = body(),
                    )
                }
            }
            if (t == "elite") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Icon(Lucide.Check, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Everything in Pro included", style = body(), color = colors.textMuted)
                }
            }
        }
    }
}

/** Monthly | Yearly — a compact pill; the saving shows here only while on monthly. */
@Composable
private fun BillingToggle(annual: Boolean, save: Int, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val colors = Stackd.colors
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .glassSurface(CircleShape)
                .padding(4.dp),
        ) {
            listOf(false to "Monthly", true to "Yearly").forEach { (yearly, label) ->
                val on = annual == yearly
                val bg by animateColorAsState(if (on) Silver else Color.Transparent, tween(260, easing = EaseRitual), label = "bill")
                val fg by animateColorAsState(if (on) IvoryInk else colors.textMuted, tween(260, easing = EaseRitual), label = "billInk")
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .selectable(on, role = Role.Tab) {
                            if (!on) Sfx.play(Sfx.Kind.SELECT)
                            onChange(yearly)
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = caption(), fontWeight = FontWeight.SemiBold, color = fg)
                    if (yearly && save > 0 && !annual) {
                        Text(" · −$save%", style = caption(), color = colors.accent)
                    }
                }
            }
        }
    }
}

/**
 * "Everything included ⌄": unfolds in place (no sheet) into every perk of
 * [tier], the Pro vs Elite comparison when [compare], then [extra].
 */
@Composable
private fun EverythingIncluded(
    tier: String,
    compare: Boolean,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    val colors = Stackd.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(320, easing = EaseRitual), label = "chevron")
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        Sfx.play(if (open) Sfx.Kind.CLOSE else Sfx.Kind.OPEN)
                        open = !open
                    }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Everything included", style = caption(), color = colors.textMuted)
                Spacer(Modifier.width(4.dp))
                Icon(Lucide.ChevronDown, null, tint = colors.textMuted, modifier = Modifier.size(16.dp).rotate(turn))
            }
        }
        AnimatedVisibility(
            open,
            enter = expandVertically(tween(380, easing = EaseRitual)) + fadeIn(tween(320, delayMillis = 60)),
            exit = shrinkVertically(tween(280, easing = EaseRitual)) + fadeOut(tween(160)),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                val perks = perksFor(tier)
                val live = perks.filter { it.status != "soon" }
                val soon = perks.size - live.size
                Column(Modifier.fillMaxWidth().glassSurface(Radius2Xl).animateContentSize().padding(vertical = 8.dp)) {
                    live.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(p.icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(p.label, style = body(), color = colors.textPrimary, modifier = Modifier.weight(1f))
                            if (p.status == "beta") Text("Beta", style = caption(), color = colors.textMuted)
                        }
                    }
                    if (soon > 0) {
                        Text(
                            "$soon on the way, free when they land",
                            style = caption(),
                            color = colors.textMuted,
                            modifier = Modifier.padding(start = 46.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                        )
                    }
                }
                if (compare) {
                    Spacer(Modifier.height(24.dp))
                    Text("Pro vs Elite", style = body(), fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    CompareTable()
                }
                extra()
            }
        }
    }
}

/** The headline perks, side by side. */
private val COMPARE = listOf(
    "Focus DNA", "Deep analytics", "Unlimited history", "Advanced leaderboards",
    "Atlas AI coach", "Focus forecast", "Memory vault", "Time capsules", "Weekly elite reports",
).mapNotNull { l -> CATALOG.firstOrNull { it.label == l } }

@Composable
private fun CompareTable(modifier: Modifier = Modifier) {
    val colors = Stackd.colors
    val col = Modifier.width(56.dp)
    Column(modifier.fillMaxWidth().glassSurface(Radius2Xl).padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Spacer(Modifier.weight(1f))
            listOf("Pro", "Elite").forEach {
                Text(it, style = caption(), fontWeight = FontWeight.SemiBold, color = colors.textMuted, textAlign = TextAlign.Center, modifier = col)
            }
        }
        COMPARE.forEach { p ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.label, style = body(), color = colors.textPrimary, modifier = Modifier.weight(1f))
                listOf(p.tier == "pro", true).forEach { has ->
                    Box(col, contentAlignment = Alignment.Center) {
                        if (has) Icon(Lucide.Check, "Included", tint = colors.accent, modifier = Modifier.size(18.dp))
                        else Text("—", style = body(), color = colors.textMuted.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

/** Pinned purchase action and one line of reassurance; content fades out beneath it. */
@Composable
private fun CheckoutBar(plan: Plan, modifier: Modifier, onCheckout: () -> Unit) {
    val colors = Stackd.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to colors.background))
            .navigationBarsPadding()
            .padding(top = 32.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).padding(horizontal = 20.dp)) {
            EmberButton(
                text = "Get ${cap(plan.tier)} — ${inr(perMonth(plan))}/mo",
                onClick = {
                    Sfx.play(Sfx.Kind.PURCHASE)
                    onCheckout()
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.ShieldCheck, null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancel anytime · Secure checkout", style = caption(), color = colors.textMuted)
            }
        }
    }
}

/** Members: status, the same card as hero, usage, perks unlocked, and the way to manage it. */
@Composable
private fun Membership(state: PremiumUiState, onOpenWeb: (String) -> Unit) {
    val colors = Stackd.colors
    val ent = state.entitlement
    Column(Modifier.reveal(1)) {
        Text(
            when {
                ent.source == "lifetime" -> "Yours for life."
                ent.isPremium -> "You're a member."
                else -> "Focus, free forever."
            },
            style = serifXl(),
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                ent.source == "lifetime" -> "Lifetime access. Every feature, every update."
                ent.isPremium && ent.expiresAt != null -> "Renews ${ent.expiresAt.take(10)}"
                ent.isPremium -> TAGLINE[ent.tier] ?: "Every perk, unlocked."
                else -> "Plans are unavailable right now. Try again later."
            },
            style = body(),
            color = colors.textMuted,
        )
    }
    Spacer(Modifier.height(32.dp))
    MemberCard(ent.tier, holder = state.holderName, modifier = Modifier.reveal(2))

    if (state.aiUsage.allowance > 0 || state.aiUsage.unlimited) {
        Spacer(Modifier.height(40.dp))
        Column(Modifier.fillMaxWidth().reveal(3).glassSurface(Radius2Xl).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AI this period", style = body(), fontWeight = FontWeight.SemiBold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    if (state.aiUsage.unlimited) "Unlimited" else "${state.aiUsage.used} of ${state.aiUsage.allowance} used",
                    style = caption(),
                    color = if (state.aiUsage.unlimited) colors.accent else colors.textMuted,
                )
            }
            if (!state.aiUsage.unlimited) {
                Spacer(Modifier.height(16.dp))
                val target = (state.aiUsage.used.toFloat() / state.aiUsage.allowance).coerceIn(0f, 1f)
                val pct by animateFloatAsState(target, tween(700, easing = EaseRitual), label = "usage")
                Box(Modifier.fillMaxWidth().height(8.dp).background(colors.textPrimary.copy(alpha = 0.06f), CircleShape)) {
                    Box(
                        Modifier
                            .fillMaxWidth(pct)
                            .height(8.dp)
                            .background(Brush.horizontalGradient(listOf(colors.accent, EmberGlow)), CircleShape),
                    )
                }
            }
        }
    }
    if (ent.isPremium) {
        Spacer(Modifier.height(40.dp))
        Column(Modifier.reveal(4)) {
            Text("Perks unlocked", style = body(), fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Spacer(Modifier.height(16.dp))
            ValueLines(ent.tier)
            Spacer(Modifier.height(8.dp))
            EverythingIncluded(ent.tier, compare = false)
        }
    }
    state.subscription?.let { sub ->
        Spacer(Modifier.height(32.dp))
        Column(Modifier.fillMaxWidth().reveal(5).glassSurface(Radius2Xl).padding(16.dp)) {
            Text("Subscription", style = body(), fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(
                sub.status.replaceFirstChar { it.uppercase() } + if (sub.cancelAtPeriodEnd) " · ends at period end" else "",
                style = caption(),
                color = colors.textMuted,
            )
            Spacer(Modifier.height(16.dp))
            GhostButton(text = "Manage on the web", onClick = { onOpenWeb("/profile") })
        }
    }
}

/** Lifetime coupon, folded away: most people never need it. */
@Composable
private fun LifetimeCode(state: PremiumUiState, onRedeem: (String) -> Unit) {
    val colors = Stackd.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Radius2Xl)
                .clickable(role = Role.Button) {
                    Sfx.play(if (open) Sfx.Kind.CLOSE else Sfx.Kind.OPEN)
                    open = !open
                }
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Have a lifetime code?", style = body(), color = colors.textPrimary)
                Text(
                    "${state.promo.seatsRemaining} of ${state.promo.seatsTotal} lifetime seats left",
                    style = caption(),
                    color = colors.accent,
                )
            }
            Icon(Lucide.Sparkles, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Spacer(Modifier.height(8.dp))
                var code by remember { mutableStateOf("") }
                // Soft filled field, no outline.
                BasicTextField(
                    value = code,
                    onValueChange = { code = it.take(120) },
                    singleLine = true,
                    textStyle = body().merge(TextStyle(color = colors.textPrimary)),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(
                            Modifier.fillMaxWidth().glassSurface(RadiusXl).heightIn(min = 56.dp).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (code.isEmpty()) Text("Code", style = body(), color = colors.textMuted)
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
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
                style = caption(),
                color = if (state.redeemSucceeded) colors.accent else colors.textMuted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
