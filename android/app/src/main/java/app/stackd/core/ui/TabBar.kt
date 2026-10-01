package app.stackd.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stackd.core.theme.Obsidian
import app.stackd.core.theme.Stackd

/** True on a tab's root screen: there is nowhere to go "back" to, so headers hide the chevron. */
val LocalIsTabRoot = compositionLocalOf { false }

/** Height of the bar's content row (excludes the system navigation inset). */
val TabBarHeight = 64.dp

data class TabItem(val route: String, val label: String, val icon: ImageVector, val iconSelected: ImageVector)

val StackdTabs = listOf(
    TabItem("dashboard", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    TabItem("insights", "Progress", Icons.Outlined.Insights, Icons.Filled.Insights),
    TabItem("feed", "Social", Icons.Outlined.People, Icons.Filled.People),
    TabItem("profile", "Profile", Icons.Outlined.Person, Icons.Filled.Person),
)

/**
 * Persistent bottom navigation: four destinations you can always see (vs a
 * 21-item hidden menu) around a raised Start button — the app's one core
 * action gets the largest, most central, most reachable target.
 */
@Composable
fun TabBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Obsidian.copy(alpha = 0.97f))
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Row(
                Modifier.fillMaxWidth().height(TabBarHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StackdTabs.take(2).forEach { TabCell(it, it.route == currentRoute, onSelect, Modifier.weight(1f)) }
                Spacer(Modifier.weight(1f)) // room for the raised Start button
                StackdTabs.drop(2).forEach { TabCell(it, it.route == currentRoute, onSelect, Modifier.weight(1f)) }
            }
        }
        StartButton(onStart, Modifier.align(Alignment.TopCenter).offset(y = (-18).dp))
    }
}

@Composable
private fun TabCell(item: TabItem, selected: Boolean, onSelect: (String) -> Unit, modifier: Modifier) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    val tint by animateColorAsState(if (selected) colors.accent else colors.textMuted, label = "tint")
    Column(
        modifier
            .height(TabBarHeight)
            .clickable(interactionSource = source, indication = null, role = Role.Tab) { onSelect(item.route) }
            .pressFeedback(source, pressedScale = 0.92f)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (selected) item.iconSelected else item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            item.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun StartButton(onStart: () -> Unit, modifier: Modifier) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(60.dp)
            .pressFeedback(source, pressedScale = 0.9f)
            .shadow(16.dp, CircleShape, ambientColor = colors.accent, spotColor = colors.accent)
            .clip(CircleShape)
            .background(colors.accent)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onStart)
            .semantics { contentDescription = "Start a focus session" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Obsidian, modifier = Modifier.size(30.dp))
    }
}
