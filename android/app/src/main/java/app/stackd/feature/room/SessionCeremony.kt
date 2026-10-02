package app.stackd.feature.room

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stackd.core.feedback.Sfx
import app.stackd.core.theme.SerifFamily
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.Confetti
import app.stackd.core.ui.EaseRitual
import app.stackd.core.ui.EmberButton
import app.stackd.data.recap.SessionSummary
import com.composables.icons.lucide.Award
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TrendingUp
import com.composables.icons.lucide.Trophy
import com.composables.icons.lucide.Users
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Post-session ceremony (web SessionCeremony, Duolingo lesson-complete).
 *
 * One hero — the score ring with an ember bloom — then three stat tiles pop
 * in, the level bar fills, and any extras (awards, rank, friends) fade in as
 * quiet rows. Continue is pinned at the bottom and fades in at ~1.8s.
 * Type scale: 64sp serif score, headlineMedium serif title, titleLarge tile
 * values, bodyMedium for everything else; Normal + SemiBold only.
 */
@Composable
fun SessionCeremony(summary: SessionSummary, onContinue: () -> Unit) {
    val colors = Stackd.colors
    val view = LocalView.current
    val extras = remember(summary) { extrasFor(summary) }

    val ring = remember(summary) { Animatable(0f) }
    val bloom = remember(summary) { Animatable(0f) }
    val xp = remember(summary) { Animatable(0f) }
    var tiles by remember(summary) { mutableIntStateOf(0) }
    var levelOn by remember(summary) { mutableStateOf(false) }
    var extrasShown by remember(summary) { mutableIntStateOf(0) }
    var ctaOn by remember(summary) { mutableStateOf(false) }

    LaunchedEffect(summary) {
        Sfx.play(Sfx.Kind.SUCCESS)
        view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
        launch { bloom.animateTo(1f, tween(1400, easing = EaseRitual)) }
        launch { delay(1800); ctaOn = true }
        ring.animateTo(1f, tween(1100, easing = EaseRitual))
        launch { xp.animateTo(summary.xpEarned.toFloat(), tween(800, easing = EaseRitual)) }
        repeat(3) { i ->
            tiles = i + 1
            Sfx.play(if (i == 0) Sfx.Kind.XP else Sfx.Kind.SELECT)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            delay(120)
        }
        levelOn = true
        delay(200)
        extras.forEachIndexed { i, e ->
            extrasShown = i + 1
            e.sound?.let { Sfx.play(it) }
            delay(120)
        }
    }

    val mins = maxOf(1, Math.round(summary.durationSeconds / 60.0).toInt())

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .then(with(app.stackd.core.ui.CeremonyGlow) { Modifier.glow() })
            .statusBarsPadding(),
    ) {
        // Scrolling story; bottom padding reserves the pinned bar's space.
        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
                Column(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Hero: bloom + ring.
                    Box(Modifier.size(256.dp), contentAlignment = Alignment.Center) {
                        val b = bloom.value
                        Canvas(
                            Modifier.fillMaxSize().graphicsLayer {
                                val s = 0.6f + 0.5f * b
                                scaleX = s; scaleY = s
                                alpha = if (b < 0.4f) b / 0.4f else 1f - 0.65f * ((b - 0.4f) / 0.6f)
                            },
                        ) {
                            drawCircle(Brush.radialGradient(listOf(colors.accent.copy(alpha = 0.55f), Color.Transparent)))
                        }
                        ScoreRing(summary.score, ring.value)
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        tierTitle(summary.tier),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SerifFamily),
                        fontWeight = FontWeight.Normal,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$mins ${if (mins == 1) "minute" else "minutes"} held · " + when (summary.breaches) {
                            0 -> "no breaks"
                            1 -> "1 break"
                            else -> "${summary.breaches} breaks"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                    )

                    // Three equal stat tiles.
                    Spacer(Modifier.height(32.dp))
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val mod = Modifier.weight(1f).fillMaxHeight()
                        StatTile(tiles >= 1, "XP", "+${xp.value.toInt()}", colors.accent, mod)
                        StatTile(tiles >= 2, "Time", "${mins}m", colors.textMuted, mod)
                        if (summary.streak > 0) {
                            StatTile(tiles >= 3, "Streak", "${summary.streak} ${if (summary.streak == 1) "day" else "days"}", colors.textMuted, mod)
                        } else {
                            StatTile(tiles >= 3, "Breaks", "${summary.breaches}", colors.textMuted, mod)
                        }
                    }

                    // Level line.
                    Spacer(Modifier.height(24.dp))
                    LevelLine(summary, levelOn)

                    // Extras as quiet rows.
                    if (extras.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        extras.forEachIndexed { i, e ->
                            AnimatedVisibility(
                                visible = extrasShown > i,
                                enter = fadeIn(tween(320, easing = EaseRitual)) +
                                    slideInVertically(tween(320, easing = EaseRitual)) { it / 3 },
                            ) { ExtraRow(e) }
                        }
                    }
                }
        }

            // Pinned action over a fade; space always reserved, button fades in.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to colors.background))
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val a by animateFloatAsState(if (ctaOn) 1f else 0f, tween(400, easing = EaseRitual), label = "cta")
                Box(Modifier.widthIn(max = 420.dp).graphicsLayer { alpha = a }) {
                    EmberButton(text = "Continue", onClick = onContinue, enabled = ctaOn)
                }
            }

        if (summary.score >= 80 && tiles > 0) Confetti(modifier = Modifier.fillMaxSize())
    }
}

private fun tierTitle(tier: String) = when (tier.lowercase()) {
    "pristine" -> "Pristine."
    "flow" -> "Flow state."
    "steady" -> "Steady."
    "compromised" -> "Protocol compromised."
    else -> tier.replace('_', ' ').replaceFirstChar { it.uppercase() } + "."
}

/** Score as a filling arc; the number counts with the arc. */
@Composable
private fun ScoreRing(score: Int, p: Float) {
    val colors = Stackd.colors
    Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(colors.textPrimary.copy(alpha = 0.07f), -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(
                colors.accent,
                -90f, 360f * (score / 100f) * p, false, Offset(inset, inset), arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            "${(score * p).toInt()}",
            style = MaterialTheme.typography.displayLarge.copy(fontFamily = SerifFamily, fontSize = 64.sp),
            fontWeight = FontWeight.Normal,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun StatTile(visible: Boolean, label: String, value: String, labelColor: Color, modifier: Modifier) {
    val colors = Stackd.colors
    // Spring pop driven by state so the tile keeps its slot (equal heights).
    val p by animateFloatAsState(
        if (visible) 1f else 0f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "tile",
    )
    Column(
        modifier
            .graphicsLayer {
                val s = 0.8f + 0.2f * p
                scaleX = s; scaleY = s
                alpha = p.coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(16.dp))
            .background(colors.textPrimary.copy(alpha = 0.04f))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = labelColor, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun LevelLine(summary: SessionSummary, on: Boolean) {
    val colors = Stackd.colors
    val span = summary.levelXpSpan.coerceAtLeast(1).toFloat()
    val now = (summary.levelXpInto / span).coerceIn(0f, 1f)
    val before = ((summary.levelXpInto - summary.xpEarned).coerceAtLeast(0) / span).coerceIn(0f, 1f)
    val fill by animateFloatAsState(if (on) now else before, tween(700, easing = EaseRitual), label = "level")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val prestige = if (summary.prestige > 0) "P${summary.prestige} · " else ""
            Text("${prestige}Level ${summary.level}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text("${summary.levelXpInto} / ${summary.levelXpSpan} XP", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.07f))) {
            // Gained segment (brighter) under the prior progress.
            Box(Modifier.fillMaxWidth(fill).fillMaxHeight().clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.85f)))
            Box(Modifier.fillMaxWidth(before).fillMaxHeight().clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.35f)))
        }
    }
}

private class Extra(val icon: ImageVector, val title: String, val sub: String?, val accent: Boolean, val sound: Sfx.Kind?)

private fun extrasFor(s: SessionSummary): List<Extra> = buildList {
    s.achievements.forEachIndexed { i, a ->
        add(Extra(Lucide.Award, a.name, a.description.takeIf { it.isNotBlank() }, true, if (i == 0) Sfx.Kind.ACHIEVEMENT else null))
    }
    s.milestones.forEachIndexed { i, m ->
        val sound = if (i == 0 && s.achievements.isEmpty()) Sfx.Kind.ACHIEVEMENT else null
        add(Extra(Lucide.Trophy, m.name, m.description.takeIf { it.isNotBlank() }, true, sound))
    }
    if (s.rankNow != s.rankBefore) {
        val delta = s.rankBefore - s.rankNow
        if (delta > 0) {
            add(Extra(Lucide.TrendingUp, "Up $delta ${if (delta == 1) "place" else "places"}", "Now #${s.rankNow} on the board", true, Sfx.Kind.SUCCESS))
        } else {
            add(Extra(Lucide.TrendingUp, "Now #${s.rankNow} on the board", null, false, null))
        }
    }
    if (s.friendsFinished.isNotEmpty()) {
        val names = s.friendsFinished.take(3).joinToString(", ") {
            it.displayName?.substringBefore(' ')?.takeIf { n -> n.isNotBlank() } ?: "A friend"
        }
        add(Extra(Lucide.Users, "$names also focused today", null, false, null))
    }
}

@Composable
private fun ExtraRow(e: Extra) {
    val colors = Stackd.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).background(colors.textPrimary.copy(alpha = 0.04f), CircleShape).border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(e.icon, null, tint = if (e.accent) colors.accent else colors.textMuted, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            e.sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted) }
        }
    }
}

/** Debug-only sample so the ceremony can be previewed without finishing a session. */
internal fun sampleSessionSummary() = SessionSummary(
    score = 92, tier = "flow", durationSeconds = 45 * 60, breaches = 0, xpEarned = 640,
    lifetimeXp = 2860, prestige = 0, level = 7, levelXpInto = 380, levelXpSpan = 600, streak = 3,
    achievements = listOf(app.stackd.data.recap.AwardCard("deep", "Deep Diver", "Held a 45-minute stack")),
    milestones = emptyList(), rankNow = 12, rankBefore = 15, personality = null,
    friendsFinished = emptyList(),
)
