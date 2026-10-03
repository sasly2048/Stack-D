package app.stackd.feature.room

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stackd.core.feedback.Sfx
import app.stackd.core.theme.LocalReduceMotion
import app.stackd.core.theme.SerifFamily
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.Avatar
import app.stackd.core.ui.EaseRitual
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.StackArt
import app.stackd.core.ui.glassSurface
import app.stackd.core.ui.pageGlow
import app.stackd.core.ui.pressFeedback
import app.stackd.data.recap.FriendFinish
import app.stackd.data.recap.SessionSummary
import com.composables.icons.lucide.Award
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TrendingUp
import com.composables.icons.lucide.Trophy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Post-session ceremony, "the table": a ritual close rather than a scoreboard.
 *
 * Choreography (~2.8s, EaseRitual throughout, no springs, no bounces):
 *  0.0s  screen fades up from black onto obsidian + page glow; a 4-phone
 *        stack rests centred at the top.
 *  0.25s phones lift off one at a time, top first (220ms apart), rising and
 *        fading: everyone picked their phone back up. The ember glow stays
 *        and settles to a soft residue.
 *  1.1s  as the last phone lifts, the serif headline rises in ("The stack
 *        held.") with its muted subline. One SUCCESS sound + gentle haptic.
 *  1.35s quiet score line ("92 · Flow state · +640 XP"), XP counts up.
 *  1.6s  hairline, then the level row; the bar fills from before to now.
 *  1.85s "Held with you" avatars (only when friends finished too).
 *  ~2.0s extras rise in; the first award row gets one slow light sweep
 *        (+ ACHIEVEMENT sound).
 *  last  reflection chips and the pinned Continue fade in (~2.5-2.8s).
 * With system animations off everything is shown at rest immediately.
 *
 * Type: displaySmall serif headline, titleLarge serif score, bodyMedium for
 * everything else; Normal + SemiBold only.
 *
 * [onReflect] (optional) shows one-tap "How did it feel?" chips; the chosen
 * label is handed back (RoomScreen stores it as a session tag).
 */
@Composable
fun SessionCeremony(
    summary: SessionSummary,
    onReflect: ((String) -> Unit)? = null,
    onContinue: () -> Unit,
) {
    val colors = Stackd.colors
    val view = LocalView.current
    val reduce = LocalReduceMotion.current
    val extras = remember(summary) { extrasFor(summary) }
    val sweepIndex = remember(extras) { extras.indexOfFirst { it.award } }
    val friends = summary.friendsFinished

    val fade = remember(summary) { Animatable(0f) }
    val title = remember(summary) { Animatable(0f) }
    val score = remember(summary) { Animatable(0f) }
    val xp = remember(summary) { Animatable(0f) }
    val levelIn = remember(summary) { Animatable(0f) }
    val level = remember(summary) { Animatable(0f) }
    val held = remember(summary) { Animatable(0f) }
    val rows = remember(summary) { List(extras.size) { Animatable(0f) } }
    val sweep = remember(summary) { Animatable(0f) }
    val tail = remember(summary) { Animatable(0f) }
    var ctaOn by remember(summary) { mutableStateOf(false) }

    LaunchedEffect(summary) {
        fun landed() {
            Sfx.play(Sfx.Kind.SUCCESS)
            view.performHapticFeedback(
                if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK,
            )
        }
        if (reduce) {
            landed()
            (listOf(fade, title, score, levelIn, level, held, tail) + rows).forEach { it.snapTo(1f) }
            xp.snapTo(summary.xpEarned.toFloat())
            ctaOn = true
            return@LaunchedEffect
        }
        fun go(at: Long, a: Animatable<Float, AnimationVector1D>, ms: Int, to: Float = 1f) =
            launch { delay(at); a.animateTo(to, tween(ms, easing = EaseRitual)) }

        go(0, fade, 500)
        // StackArt(liftOff) lifts its last phone at ~0.91s; the headline lands with it.
        launch { delay(1100); landed() }
        go(1100, title, 700)
        go(1350, score, 600)
        go(1350, xp, 900, summary.xpEarned.toFloat())
        go(1600, levelIn, 500)
        go(1700, level, 800)
        val extrasAt = if (friends.isNotEmpty()) { go(1850, held, 500); 2000L } else 1850L
        rows.forEachIndexed { i, r -> go(extrasAt + i * 120, r, 450) }
        if (sweepIndex >= 0) launch {
            delay(extrasAt + sweepIndex * 120 + 300)
            Sfx.play(Sfx.Kind.ACHIEVEMENT)
            sweep.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
        val ctaAt = maxOf(2800L, extrasAt + extras.size * 120 + 300)
        go(ctaAt - 300, tail, 450)
        delay(ctaAt)
        ctaOn = true
    }

    val mins = maxOf(1, Math.round(summary.durationSeconds / 60.0).toInt())
    val body = MaterialTheme.typography.bodyMedium

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fade.value }
                .background(colors.background)
                .pageGlow()
                .statusBarsPadding(),
        ) {
            // Scrolling story; bottom padding reserves the pinned bar's space.
            Box(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The table: the stack, then everyone picks their phone back up.
                    // Once the phones have lifted, the art's space folds away so the
                    // result settles into the upper-middle instead of under a void.
                    val artH = remember { androidx.compose.animation.core.Animatable(if (reduce) 72f else 200f) }
                    LaunchedEffect(Unit) {
                        if (!reduce) {
                            delay(1150)
                            artH.animateTo(72f, tween(800, easing = EaseRitual))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(artH.value.dp), contentAlignment = Alignment.Center) {
                        StackArt(size = 200.dp, phones = 4, liftOff = true, modifier = Modifier.requiredSize(200.dp))
                    }

                    val clean = summary.breaches == 0
                    Column(Modifier.rise(title.value, 16f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (clean) "The stack held."
                            else "Held, with ${summary.breaches} ${if (summary.breaches == 1) "break" else "breaks"}.",
                            style = MaterialTheme.typography.displaySmall.copy(fontFamily = SerifFamily),
                            fontWeight = FontWeight.Normal,
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            buildList {
                                add("$mins ${if (mins == 1) "minute" else "minutes"}")
                                if (clean) add("no breaks")
                                if (summary.streak > 0) add("${summary.streak}-day streak")
                            }.joinToString(" · "),
                            style = body,
                            color = colors.textMuted,
                            textAlign = TextAlign.Center,
                        )
                    }

                    // Score as a quiet line, not a hero.
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.rise(score.value)) {
                        Text(
                            "${summary.score}",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily),
                            fontWeight = FontWeight.Normal,
                            color = colors.textPrimary,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Text(
                            " · ${tierTitle(summary.tier).removeSuffix(".")} · ",
                            style = body, color = colors.textMuted, modifier = Modifier.alignByBaseline(),
                        )
                        Text(
                            "+${xp.value.toInt()} XP",
                            style = body, fontWeight = FontWeight.SemiBold, color = colors.accent,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }

                    // Hairline, then the level row.
                    Spacer(Modifier.height(32.dp))
                    Column(Modifier.rise(levelIn.value)) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.textPrimary.copy(alpha = 0.08f)))
                        Spacer(Modifier.height(24.dp))
                        LevelRow(summary, level.value)
                    }

                    if (friends.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        HeldWithYou(friends, Modifier.rise(held.value))
                    }

                    // Extras as quiet rows; slots are reserved so nothing shifts.
                    if (extras.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        extras.forEachIndexed { i, e ->
                            ExtraRow(e, if (i == sweepIndex) sweep.value else 0f, Modifier.rise(rows[i].value))
                        }
                    }

                    if (onReflect != null) {
                        Spacer(Modifier.height(32.dp))
                        ReflectChips(onReflect, Modifier.rise(tail.value))
                    }
                }
            }

            // Pinned action over a fade; space always reserved, button fades in last.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to colors.background))
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.widthIn(max = 420.dp).graphicsLayer { alpha = tail.value }) {
                    EmberButton(text = "Continue", onClick = onContinue, enabled = ctaOn)
                }
            }
        }
    }
}

/** Fade + short upward drift, driven by an eased 0..1 progress. */
private fun Modifier.rise(p: Float, dy: Float = 12f) = graphicsLayer {
    alpha = p
    translationY = (1f - p) * dy * density
}

private fun tierTitle(tier: String) = when (tier.lowercase()) {
    "pristine" -> "Pristine."
    "flow" -> "Flow state."
    "steady" -> "Steady."
    "compromised" -> "Protocol compromised."
    else -> tier.replace('_', ' ').replaceFirstChar { it.uppercase() } + "."
}

/** "Level 7 ──── 380 / 600": the slim bar fills from before this session to now. */
@Composable
private fun LevelRow(summary: SessionSummary, p: Float) {
    val colors = Stackd.colors
    val span = summary.levelXpSpan.coerceAtLeast(1).toFloat()
    val now = (summary.levelXpInto / span).coerceIn(0f, 1f)
    val before = ((summary.levelXpInto - summary.xpEarned).coerceAtLeast(0) / span).coerceIn(0f, 1f)
    val fill = before + (now - before) * p
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val prestige = if (summary.prestige > 0) "P${summary.prestige} · " else ""
        Text("${prestige}Level ${summary.level}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.07f))) {
            // Gained segment (brighter) under the prior progress.
            Box(Modifier.fillMaxWidth(fill).fillMaxHeight().clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.85f)))
            Box(Modifier.fillMaxWidth(before).fillMaxHeight().clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.35f)))
        }
        Spacer(Modifier.width(16.dp))
        Text("${summary.levelXpInto} / ${summary.levelXpSpan}", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
    }
}

/** Friends who also finished a stack today: overlapping avatars + first names. */
@Composable
private fun HeldWithYou(friends: List<FriendFinish>, modifier: Modifier) {
    val colors = Stackd.colors
    val shown = friends.take(4)
    Column(modifier.fillMaxWidth()) {
        Text("Held with you", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textMuted)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                shown.forEach { f ->
                    Avatar(f.avatarUrl, f.displayName, 32.dp, Modifier.border(2.dp, colors.background, CircleShape))
                }
            }
            Spacer(Modifier.width(12.dp))
            val names = shown.joinToString(", ") {
                it.displayName?.substringBefore(' ')?.takeIf { n -> n.isNotBlank() } ?: "A friend"
            } + if (friends.size > shown.size) " +${friends.size - shown.size}" else ""
            Text(names, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** [award] rows (achievements, milestones) are eligible for the one light sweep. */
private class Extra(val icon: ImageVector, val title: String, val sub: String?, val accent: Boolean, val award: Boolean = false)

private fun extrasFor(s: SessionSummary): List<Extra> = buildList {
    s.achievements.forEach { a ->
        add(Extra(Lucide.Award, a.name, a.description.takeIf { it.isNotBlank() }, true, award = true))
    }
    s.milestones.forEach { m ->
        add(Extra(Lucide.Trophy, m.name, m.description.takeIf { it.isNotBlank() }, true, award = true))
    }
    if (s.rankNow != s.rankBefore) {
        val delta = s.rankBefore - s.rankNow
        if (delta > 0) {
            add(Extra(Lucide.TrendingUp, "Up $delta ${if (delta == 1) "place" else "places"}", "Now #${s.rankNow} on the board", true))
        } else {
            add(Extra(Lucide.TrendingUp, "Now #${s.rankNow} on the board", null, false))
        }
    }
}

@Composable
private fun ExtraRow(e: Extra, sweep: Float, modifier: Modifier) {
    val colors = Stackd.colors
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .glassSurface(CircleShape)
                .clip(CircleShape)
                .drawWithContent {
                    drawContent()
                    // One diagonal band of ember light crossing the chip, like a glint.
                    if (sweep > 0f && sweep < 1f) {
                        val w = size.width
                        val x = -w + sweep * 3f * w
                        drawRect(
                            Brush.linearGradient(
                                0f to Color.Transparent,
                                0.5f to colors.accentGlow.copy(alpha = 0.45f),
                                1f to Color.Transparent,
                                start = Offset(x - w * 0.5f, 0f),
                                end = Offset(x + w * 0.5f, size.height),
                            ),
                        )
                    }
                },
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

/** "How did it feel?" — one tap, last pick wins, ember when selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReflectChips(onReflect: (String) -> Unit, modifier: Modifier) {
    val colors = Stackd.colors
    var picked by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("How did it feel?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Deep flow", "Distracted", "Low energy").forEach { label ->
                val on = picked == label
                val source = remember { MutableInteractionSource() }
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) colors.accent else colors.textPrimary,
                    modifier = Modifier
                        .pressFeedback(source, sound = Sfx.Kind.SELECT)
                        .clip(CircleShape)
                        .background(if (on) colors.accent.copy(alpha = 0.12f) else colors.textPrimary.copy(alpha = 0.04f))
                        .border(1.dp, if (on) colors.accent else colors.border, CircleShape)
                        .clickable(source, indication = null, role = Role.Button) {
                            if (!on) { picked = label; onReflect(label) }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Debug-only sample so the ceremony can be previewed without finishing a session. */
internal fun sampleSessionSummary() = SessionSummary(
    score = 92, tier = "flow", durationSeconds = 45 * 60, breaches = 0, xpEarned = 640,
    lifetimeXp = 2860, prestige = 0, level = 7, levelXpInto = 380, levelXpSpan = 600, streak = 3,
    achievements = listOf(app.stackd.data.recap.AwardCard("deep", "Deep Diver", "Held a 45-minute stack")),
    milestones = emptyList(), rankNow = 12, rankBefore = 15, personality = null,
    friendsFinished = listOf(FriendFinish("a", "Maya Chen", null, 420), FriendFinish("b", "Theo Park", null, 300)),
)
