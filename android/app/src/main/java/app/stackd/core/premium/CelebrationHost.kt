package app.stackd.core.premium

import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stackd.core.feedback.Sfx
import app.stackd.core.theme.Accent
import app.stackd.core.theme.Ember
import app.stackd.core.theme.EmberGlow
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.Obsidian
import app.stackd.core.theme.Silver
import app.stackd.core.theme.SilverDim
import app.stackd.core.ui.vibrate
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private val EaseOutExpo = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * Root-level celebration mount — web CelebrationHost. Lives in MainActivity
 * above the nav graph so no screen transition can kill it. [suppressed] holds
 * a pending celebration while a focus session is on screen (a full-screen
 * overlay there would fight the touch-breach rule); it shows on leaving.
 */
@Composable
fun CelebrationHost(suppressed: Boolean) {
    val tier by Celebration.pending.collectAsStateWithLifecycle()
    if (suppressed) return
    val close = { Celebration.pending.value = null }
    when (tier) {
        "pro" -> CelebratePro(onClose = close)
        "elite" -> CelebrateElite(onClose = close)
    }
}

/** Reduced motion (animator scale 0) or battery saver — web useLowPower. */
@Composable
private fun rememberLowPower(): Boolean {
    val ctx = LocalContext.current
    return remember {
        val scale = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        scale == 0f || ctx.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    }
}

/** Full-screen scrim; a tap anywhere outside the content closes. */
@Composable
private fun Scrim(
    label: String,
    color: Color,
    onClose: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(color)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .semantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            },
    ) { content() }
}

@Composable
private fun PillButton(text: String, color: Color, fill: Color, shown: Float, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .alpha(shown)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text.uppercase(), style = MonoLabel, color = color) }
}

/* ------------------------------------------------------------------ Pro --- */

/**
 * PRO — "Understand your focus". Scan sweep, self-drawing hexagonal sigil that
 * locks, then the celebration line. ~3.5s: reveal → activate → celebrate.
 */
@Composable
private fun CelebratePro(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val low = rememberLowPower()
    var beat by remember { mutableIntStateOf(0) } // 0 reveal, 1 activate, 2 celebrate
    LaunchedEffect(Unit) {
        vibrate(ctx, 12)
        Sfx.play(Sfx.Kind.CELEBRATE_PRO)
        delay(700)
        beat = 1
        vibrate(ctx, 35)
        delay(1200)
        beat = 2
    }
    val draw by animateFloatAsState(
        if (beat >= 1) 1f else 0f, if (low) snap() else tween(1100, easing = EaseOutExpo), label = "sigil",
    )
    val dot by animateFloatAsState(
        if (beat >= 1) 1f else 0f, if (low) snap() else tween(400, delayMillis = 800, easing = EaseOutExpo), label = "dot",
    )
    val late by animateFloatAsState(if (beat == 2) 1f else 0f, if (low) snap() else tween(500), label = "late")
    val scan = remember { Animatable(0f) }
    LaunchedEffect(Unit) { if (!low) scan.animateTo(1f, tween(1100, easing = EaseOutExpo)) }

    // 0.985 not web's 0.95: no backdrop blur here, so the page shows through more.
    Scrim("Pro membership activated", Obsidian.copy(alpha = 0.985f), onClose) {
        if (!low && scan.value < 1f) {
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height * scan.value
                drawLine(
                    Brush.horizontalGradient(listOf(Color.Transparent, Ember, Color.Transparent)),
                    Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(132.dp)) {
                val s = size.width / 132f
                val pts = listOf(66f to 10f, 114f to 38f, 114f to 94f, 66f to 122f, 18f to 94f, 18f to 38f)
                val hex = Path().apply {
                    pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * s, y * s) else lineTo(x * s, y * s) }
                    close()
                }
                val measure = PathMeasure().apply { setPath(hex, false) }
                val seg = Path()
                measure.getSegment(0f, measure.length * draw, seg, true)
                if (late > 0f) drawPath(seg, Ember.copy(alpha = 0.3f * late), style = Stroke(8.dp.toPx()))
                drawPath(seg, Ember, style = Stroke(1.5.dp.toPx()))
                drawCircle(Ember, radius = 6f * s * dot, center = Offset(66f * s, 66f * s))
            }
            Spacer(Modifier.height(32.dp))
            Text(if (beat == 0) "ACQUIRING SIGNAL" else "SIGNAL LOCKED", style = MonoLabel, color = Ember)
            Spacer(Modifier.height(12.dp))
            Text("Pro", fontFamily = FontFamily.Serif, fontSize = 52.sp, color = Silver)
            Spacer(Modifier.height(16.dp))
            Text(
                "Your focus, now fully mapped. Analytics, DNA and unlimited history are yours.",
                style = MaterialTheme.typography.bodyMedium,
                color = SilverDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp).alpha(late),
            )
            Spacer(Modifier.height(32.dp))
            PillButton("Understand your focus", Ember, Color.Transparent, late, beat == 2, onClose)
        }
    }
}

/* ---------------------------------------------------------------- Elite --- */

private class Spark(
    var x: Float, var y: Float, val vx: Float, var vy: Float,
    val r: Float, val color: Color, var life: Float, val decay: Float, var flicker: Float,
)

/**
 * ELITE — "Optimize your focus". Ignition flash, ~280 rising embers, three
 * expanding light rings, a molten shimmering crest. ~6s:
 * ignite → storm → crest → settle.
 */
@Composable
private fun CelebrateElite(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val low = rememberLowPower()
    val density = LocalDensity.current.density
    var beat by remember { mutableIntStateOf(0) } // 0 ignite, 1 storm, 2 crest, 3 settle
    LaunchedEffect(Unit) {
        vibrate(ctx, 70)
        Sfx.play(Sfx.Kind.CELEBRATE_ELITE)
        delay(500)
        beat = 1
        delay(1000)
        beat = 2
        vibrate(ctx, 35)
        delay(1700)
        beat = 3
    }

    // Particle field, stepped in 60fps units so 120Hz panels don't double speed.
    var box by remember { mutableStateOf(IntSize.Zero) }
    val sparks = remember { ArrayList<Spark>(280) }
    val rings = remember { ArrayList<FloatArray>(3) } // [radius, life]
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(box, low) {
        if (low || box == IntSize.Zero) return@LaunchedEffect
        val w = box.width.toFloat()
        val h = box.height.toFloat()
        val palette = listOf(Ember, EmberGlow, Accent, Color.White)
        sparks.clear()
        rings.clear()
        repeat(280) {
            val fromCenter = Random.nextFloat() < 0.5f
            sparks += Spark(
                x = if (fromCenter) w / 2 + (Random.nextFloat() - 0.5f) * 120 * density else Random.nextFloat() * w,
                y = if (fromCenter) h / 2 else h + Random.nextFloat() * 40 * density,
                vx = (Random.nextFloat() - 0.5f) * 1.4f * density,
                vy = -(0.6f + Random.nextFloat() * 2.6f) * density,
                r = (1f + Random.nextFloat() * 3.5f) * density,
                color = palette.random(),
                life = 1f,
                decay = 0.004f + Random.nextFloat() * 0.006f,
                flicker = Random.nextFloat() * PI.toFloat(),
            )
        }
        var frames = 0f
        var ringsAdded = 0
        var last = withFrameNanos { it }
        while (frames < 360f) {
            val now = withFrameNanos { it }
            val step = ((now - last) / 16_666_667f).coerceIn(0.25f, 4f)
            last = now
            val before = frames.toInt()
            frames += step
            if (ringsAdded < 3 && frames.toInt() / 8 > before / 8) {
                rings += floatArrayOf(20f * density, 1f)
                ringsAdded++
            }
            for (ring in rings) {
                ring[0] += 7f * density * step
                ring[1] -= 0.012f * step
            }
            for (s in sparks) {
                s.x += s.vx * step
                s.y += s.vy * step
                s.vy *= Math.pow(0.996, step.toDouble()).toFloat()
                s.flicker += 0.3f * step
                s.life -= s.decay * step
            }
            tick++
        }
    }

    val flash = remember { Animatable(if (low) 0f else 0.85f) }
    LaunchedEffect(Unit) { if (!low) flash.animateTo(0f, tween(900)) }
    val crest by animateFloatAsState(
        if (beat >= 2) 1f else 0f, if (low) snap() else tween(700, easing = EaseOutExpo), label = "crest",
    )
    val settle by animateFloatAsState(if (beat == 3) 1f else 0f, if (low) snap() else tween(600, 200), label = "settle")
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val sweep by shimmer.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "sweep",
    )

    Scrim("Elite membership activated", Obsidian, onClose) {
        Canvas(Modifier.fillMaxSize().onSizeChanged { box = it }) {
            val radius = max(size.width, size.height) * 0.7f
            // Molten radial glow.
            drawRect(
                Brush.radialGradient(
                    0f to EmberGlow.copy(alpha = 0.28f),
                    0.28f to Ember.copy(alpha = 0.14f),
                    0.5f to Accent.copy(alpha = 0.05f),
                    0.7f to Color.Transparent,
                    center = center, radius = radius,
                ),
            )
            // Ignition bloom.
            if (flash.value > 0f) {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = flash.value), EmberGlow.copy(alpha = flash.value * 0.5f), Color.Transparent),
                        center = center, radius = radius,
                    ),
                )
            }
            if (tick < 0) return@Canvas // reading tick redraws on every simulation step
            for (ring in rings) {
                if (ring[1] <= 0f) continue
                drawCircle(
                    Ember.copy(alpha = (ring[1] * 0.5f).coerceIn(0f, 1f)),
                    radius = ring[0], center = center, style = Stroke(2.dp.toPx()),
                )
            }
            for (s in sparks) {
                if (s.life <= 0f) continue
                val a = (s.life * (0.6f + 0.4f * sin(s.flicker))).coerceIn(0f, 1f)
                drawCircle(s.color.copy(alpha = a * 0.3f), radius = s.r * 2.6f, center = Offset(s.x, s.y))
                drawCircle(s.color.copy(alpha = a), radius = s.r, center = Offset(s.x, s.y))
            }
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .graphicsLayer {
                    alpha = crest
                    translationY = (1f - crest) * 16.dp.toPx()
                    val sc = 0.94f + 0.06f * crest
                    scaleX = sc
                    scaleY = sc
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ACCESS GRANTED", style = MonoLabel, color = EmberGlow)
            Spacer(Modifier.height(12.dp))
            val shift = sweep * 600f
            Text(
                "Elite",
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 68.sp,
                    brush = if (low) {
                        Brush.linearGradient(listOf(Accent, Ember, EmberGlow))
                    } else {
                        Brush.linearGradient(
                            listOf(Accent, Ember, Color.White, EmberGlow, Accent),
                            start = Offset(shift - 300f, 0f),
                            end = Offset(shift, 0f),
                            tileMode = TileMode.Mirror,
                        )
                    },
                ),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "The intelligence layer is yours. Atlas, forecasting, adaptive sessions and the vault — " +
                    "everything Stack'd can do, optimized around you.",
                style = MaterialTheme.typography.bodyMedium,
                color = SilverDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
            Spacer(Modifier.height(32.dp))
            PillButton("Optimize your focus", EmberGlow, Ember.copy(alpha = 0.1f), settle, beat == 3, onClose)
        }
    }
}
