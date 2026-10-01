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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.unit.sp
import app.stackd.core.theme.Obsidian
import dev.chrisbanes.haze.hazeChild
import app.stackd.core.theme.Obsidian2
import app.stackd.core.theme.Silver
import app.stackd.core.theme.Stackd

/** True on a tab's root screen: there is nowhere to go "back" to, so headers hide the chevron. */
val LocalIsTabRoot = compositionLocalOf { false }

private val BarHeight = 66.dp
private val BarMargin = 14.dp

/** Vertical space the floating bar occupies above the system nav inset (bar + its bottom margin). */
val TabBarHeight = BarHeight + BarMargin

data class TabItem(val route: String, val label: String, val icon: ImageVector, val iconSelected: ImageVector)

val StackdTabs = listOf(
    TabItem("dashboard", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    TabItem("insights", "Progress", Icons.Outlined.Insights, Icons.Filled.Insights),
    TabItem("feed", "Social", Icons.Outlined.People, Icons.Filled.People),
    TabItem("profile", "Profile", Icons.Outlined.Person, Icons.Filled.Person),
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
                                blurRadius = 24.dp,
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
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StackdTabs.take(2).forEach { TabCell(it, it.route == currentRoute, onSelect, Modifier.weight(1f)) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { StartButton(onStart) }
            StackdTabs.drop(2).forEach { TabCell(it, it.route == currentRoute, onSelect, Modifier.weight(1f)) }
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
            .padding(vertical = 7.dp)
            .clip(RoundedCornerShape(18.dp))
            // Soft capsule behind the active tab — position at a glance.
            .background(Color.White.copy(alpha = 0.05f * glow))
            .clickable(interactionSource = source, indication = null, role = Role.Tab) { onSelect(item.route) }
            .pressFeedback(source, pressedScale = 0.92f)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (selected) item.iconSelected else item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(item.label, color = tint, fontSize = 10.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        // Web's active-nav glow dot (`--dot-glow`).
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(4.dp)
                .graphicsLayer { alpha = glow }
                .drawBehind {
                    drawCircle(colors.accent.copy(alpha = 0.45f), radius = size.minDimension * 1.6f, center = Offset(size.width / 2, size.height / 2))
                    drawCircle(colors.accent)
                },
        )
    }
}

@Composable
private fun StartButton(onStart: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(50.dp)
            .pressFeedback(source, pressedScale = 0.9f)
            .clip(CircleShape)
            .background(Silver)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onStart)
            .semantics { contentDescription = "Start a focus session" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Obsidian, modifier = Modifier.size(28.dp))
    }
}
