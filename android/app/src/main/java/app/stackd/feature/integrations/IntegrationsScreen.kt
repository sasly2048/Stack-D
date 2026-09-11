package app.stackd.feature.integrations

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.stackd.BuildConfig
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel

/**
 * Ecosystem catalog — web's `integrations.tsx`. Purely static content, so no
 * repository or ViewModel. The three "Live" tiles (Webhooks/SDK/MCP) are
 * approved-skip dev surfaces with no Android screen, so they open the web app
 * — the same web-handoff pattern the premium screen uses for checkout.
 */

private data class Integration(
    val name: String,
    val tagline: String,
    /** "live" | "soon" */
    val status: String,
    /** Web path for Live tiles; null for Soon. */
    val webPath: String? = null,
)

private val INTEGRATIONS = listOf(
    Integration("Webhooks", "Push every session event to your own endpoint.", "live", "/webhooks"),
    Integration("TypeScript SDK", "Verify signatures and parse events in five lines.", "live", "/sdk"),
    Integration("Agent (MCP)", "Let Claude or Cursor read your focus history.", "live", "/mcp"),
    Integration("Calendar", "Auto-block deep-work slots on Google or Apple Calendar.", "soon"),
    Integration("Notion", "Send session notes and tags straight into a database.", "soon"),
    Integration("Discord", "Announce room openings and streak milestones to a channel.", "soon"),
    Integration("Slack", "Focus-mode presence and shared session invites.", "soon"),
    Integration("Raycast", "Start a session without leaving your keyboard.", "soon"),
)

@Composable
fun IntegrationsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IntegrationsScreen(onBack = onBack, modifier = modifier)
}

@Composable
fun IntegrationsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Stackd.colors
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ResponsiveColumn {
            Text("STACK'D / ECOSYSTEM", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("INTEGRATIONS")
            Spacer(Modifier.height(8.dp))
            Text(
                "Connect Stack'd to the rest of your stack.",
                style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
            )
            Spacer(Modifier.height(16.dp))

            INTEGRATIONS.forEach { i ->
                val live = i.status == "live"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
                        .border(1.dp, colors.border, Radius2Xl)
                        .then(
                            if (live && i.webPath != null) {
                                Modifier.clickable {
                                    val url = BuildConfig.WEB_BASE_URL + i.webPath
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                }
                            } else Modifier,
                        )
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            i.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (live) "LIVE" else "SOON",
                            style = MonoLabelSmall,
                            color = if (live) colors.live else colors.textMuted,
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    if (live) colors.live.copy(alpha = 0.5f) else colors.border,
                                    CircleShape,
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(i.tagline, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    if (live) {
                        Spacer(Modifier.height(4.dp))
                        Text("Opens on the web →", style = MonoLabelSmall, color = colors.accent)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}
