package app.stackd.feature.room

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.stackd.R
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Stackd

/** The four ambient beds — mirrors the web's `AmbientPlayer` track list. */
private enum class AmbientTrack(val label: String, val rawRes: Int?) {
    SILENCE("Silence", null),
    RAIN("Rain", R.raw.ambient_rain),
    FOREST("Forest", R.raw.ambient_forest),
    LOFI("Lo-fi Hum", R.raw.ambient_lofi),
}

/**
 * In-room ambient soundscapes — the Android counterpart to the web's
 * `ambient-player.tsx`. The web synthesizes tone via the Web Audio API; Android
 * has no equivalent, so this plays short seamless loops (res/raw, generated to
 * match the web's rain/forest/lofi character) through a single looping
 * ExoPlayer, with a live volume control. Same four choices, same "Live" badge.
 *
 * The player is created with the composable and released when it leaves the
 * tree, so audio never outlives the room screen.
 */
@OptIn(UnstableApi::class)
@Composable
fun AmbientPlayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = Stackd.colors

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0.4f
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    var active by remember { mutableStateOf(AmbientTrack.SILENCE) }
    var vol by remember { mutableFloatStateOf(0.4f) }

    fun choose(track: AmbientTrack) {
        active = track
        val res = track.rawRes
        if (res == null) {
            player.stop()
            player.clearMediaItems()
            return
        }
        player.setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/$res"))
        player.prepare()
        player.playWhenReady = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.02f), RadiusMd)
            .border(1.dp, colors.border, RadiusMd)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("AMBIENT", style = MonoLabelSmall, color = colors.textMuted)
            if (active != AmbientTrack.SILENCE) {
                Text("LIVE", style = MonoLabelSmall, color = colors.accent)
            }
        }

        // Track pills. FlowRow would wrap prettier, but a plain wrapping Row of
        // four short labels fits the room column at every width we support.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AmbientTrack.entries.forEach { track ->
                val selected = active == track
                Text(
                    track.label.uppercase(),
                    style = MonoLabelSmall,
                    color = if (selected) colors.accent else colors.textMuted,
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            1.dp,
                            if (selected) colors.accent else colors.border,
                            RadiusMd,
                        )
                        .clickable { choose(track) }
                        .padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        if (active != AmbientTrack.SILENCE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("VOL", style = MonoLabelSmall, color = colors.textMuted)
                Spacer(Modifier.height(0.dp))
                Slider(
                    value = vol,
                    onValueChange = {
                        vol = it
                        player.volume = it
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
            }
        }
    }
}
