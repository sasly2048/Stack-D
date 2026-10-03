package app.stackd.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.stackd.core.theme.Obsidian
import dev.chrisbanes.haze.hazeChild
import app.stackd.core.theme.Obsidian2
import app.stackd.core.theme.Silver
import app.stackd.core.theme.Stackd

/** True on a tab's root screen: there is nowhere to go "back" to, so headers hide the chevron. */
val LocalIsTabRoot = compositionLocalOf { false }

private val BarHeight = 60.dp
private val BarMargin = 12.dp
/** Gap between the bar's edge and the active capsule — equal on all sides. */
private val CapsuleInset = 5.dp
/** Concentric with the bar: outer radius minus the inset. */
private val CapsuleShape = RoundedCornerShape(BarHeight / 2 - CapsuleInset)

/** Vertical space the floating bar occupies above the system nav inset (bar + its bottom margin). */
val TabBarHeight = BarHeight + BarMargin

data class TabItem(val route: String, val label: String, val icon: ImageVector, val iconSelected: ImageVector)

val StackdTabs = listOf(
    TabItem("dashboard", "Home", app.stackd.core.ui.StackdIcons.Home, app.stackd.core.ui.StackdIcons.Home),
    TabItem("insights", "Progress", app.stackd.core.ui.StackdIcons.Insights, app.stackd.core.ui.StackdIcons.Insights),
    TabItem("feed", "Social", app.stackd.core.ui.StackdIcons.People, app.stackd.core.ui.StackdIcons.People),
    TabItem("profile", "Profile", app.stackd.core.ui.StackdIcons.Person, app.stackd.core.ui.StackdIcons.Person),
)

/**
 * Floating navigation: an inset glass capsule (web `glass-strong` — obsidian
 * at ~92%, white/10% hairline, deep soft shadow) rather than an edge-to-edge
 * slab. Detached from the screen edge it reads as a control layer over the
 * content, the convention of current premium apps. Four destinations stay
 * visible (recognition over recall) around a silver Start — the web's primary
 * colour — for the one action the app exists for.
 */
@Composable
fun TabBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
) {
    val shape = RoundedCornerShape(BarHeight / 2)
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = BarMargin),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                // Real backdrop blur where supported (Android 12+); a denser
                // scrim stands in on older devices.
                .then(
                    if (hazeState != null) {
                        Modifier.hazeChild(
                            state = hazeState,
                            style = dev.chrisbanes.haze.HazeStyle(
                                backgroundColor = Obsidian,
                                tint = dev.chrisbanes.haze.HazeTint(Obsidian2.copy(alpha = 0.62f)),
                                // 20dp reads as glass at a lower per-frame cost while scrolling.
                                blurRadius = 20.dp,
                                noiseFactor = 0f,
                            ),
                        )
                    } else {
                        Modifier.background(Obsidian2.copy(alpha = 0.94f))
                    },
                )
                // Top-edge sheen: a hint of light on the upper rim sells "glass".
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent)))
                .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                .padding(horizontal = CapsuleInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Four destinations only: starting a Stack lives in Home's pinned pill.
            StackdTabs.forEach { TabCell(it, it.route == currentRoute, onSelect, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TabCell(item: TabItem, selected: Boolean, onSelect: (String) -> Unit, modifier: Modifier) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    val tint by animateColorAsState(if (selected) colors.accent else colors.textMuted, label = "tint")
    val glow by animateFloatAsState(if (selected) 1f else 0f, label = "glow")
    Column(
        modifier
            .fillMaxHeight()
            .padding(vertical = CapsuleInset)
            .clip(CapsuleShape)
            // Soft capsule behind the active tab — position at a glance.
            .background(Color.White.copy(alpha = 0.045f * glow))
            .clickable(interactionSource = source, indication = null, role = Role.Tab) { onSelect(item.route) }
            .pressFeedback(source, pressedScale = 0.92f, sound = app.stackd.core.feedback.Sfx.Kind.SELECT)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Icon + one label line, centred as a pair. Lucide is stroke-only:
        // selection is carried by the accent tint and the soft capsule.
        Icon(if (selected) item.iconSelected else item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            item.label,
            color = tint,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun StartButton(onStart: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(50.dp)
            .pressFeedback(source, pressedScale = 0.9f, sound = app.stackd.core.feedback.Sfx.Kind.OPEN)
            .clip(CircleShape)
            .background(Silver)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onStart)
            .semantics { contentDescription = "Start a focus session" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(app.stackd.core.ui.StackdIcons.Add, contentDescription = null, tint = Obsidian, modifier = Modifier.size(28.dp))
    }
}

/** Height the pinned Start pill adds above the tab bar (pill + gap). */
val StartPillSpace = 76.dp

/**
 * Home's primary action, pinned just above the tab bar (Regain pattern): a
 * bright silver pill — label left, ember play disc right — always under the
 * thumb, never scrolled away. Pressing it floods it with ember (web
 * `.btn-ember`) and Start fires when the fill completes.
 */
@Composable
fun StartPill(onStart: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    // Press: ember floods the pill from the left; Start fires once it's full.
    val (fill, click) = rememberFillClick(source, onStart)
    val p = fill()
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = TabBarHeight + 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .pressFeedback(source, pressedScale = 0.97f, sound = null)
                .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(Silver)
                .fillSweep(fill, EmberFill, app.stackd.core.theme.EmberGlow.copy(alpha = 0.7f))
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = click)
                .semantics { contentDescription = "Start a Stack" }
                .padding(start = 26.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Start a Stack",
                color = androidx.compose.ui.graphics.lerp(app.stackd.core.theme.IvoryInk, Obsidian, p),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (0.04f * p).em,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(48.dp)
                    // The disc swells as the fill reaches it, then settles into the ember.
                    .graphicsLayer {
                        val s = 1f + 0.16f * kotlin.math.sin(Math.PI.toFloat() * fill())
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(app.stackd.core.theme.EmberGlow, app.stackd.core.theme.Ember),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(app.stackd.core.ui.StackdIcons.PlayArrow, contentDescription = null, tint = Obsidian, modifier = Modifier.size(24.dp))
            }
        }
    }
}
