package app.stackd.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Stackd
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * One avatar renderer for every social surface — feed, friends, leaderboard,
 * circles, profile. Shows the remote image when there's a usable URL, and
 * falls back to the initial(s) in an accent circle when the URL is null, blank,
 * or fails to load. The fallback is the exact chrome the screens drew inline
 * before Coil, so nothing shifts visually for users with no avatar set.
 */
@Composable
fun Avatar(
    url: String?,
    name: String?,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    // Track load failure so a broken/expired URL degrades to initials rather
    // than a blank circle. Keyed on url so a new url retries.
    var failed by remember(url) { mutableStateOf(false) }
    val hasImage = !url.isNullOrBlank() && !failed

    Box(
        modifier = modifier
            .size(size)
            .background(colors.textPrimary.copy(alpha = 0.05f), CircleShape)
            .border(1.dp, colors.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (hasImage) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                initials(name),
                style = MonoLabelSmall,
                color = colors.accent,
            )
        }
    }
}

/** First letter, matching the screens' prior `take(1).uppercase()` fallback. */
private fun initials(name: String?): String =
    (name?.trim()?.takeIf { it.isNotEmpty() }?.take(1) ?: "?").uppercase()
