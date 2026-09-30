package app.stackd.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Stackd

/**
 * The app's navigation menu — the Android shape of the web's nav bar. One
 * bottom sheet, one row per destination; picking one dismisses the sheet
 * before navigating so Back never returns to a stale open sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavMenuSheet(
    onDismiss: () -> Unit,
    entries: List<Pair<String, () -> Unit>>,
) {
    val colors = Stackd.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
    ) {
        // Lazy + scrollable: 22 destinations are taller than the sheet on a
        // phone, and the old plain Column cut off everything after "Partners"
        // (Trust & Safety, Integrations, Profile were unreachable).
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.padding(horizontal = 24.dp),
            contentPadding = androidx.compose.foundation.layout.WindowInsets.navigationBars
                .asPaddingValues(),
        ) {
            item {
                Text("NAVIGATE", style = MonoLabelSmall, color = colors.textMuted)
                Spacer(Modifier.height(8.dp))
            }
            items(entries.size) { i ->
                val (label, go) = entries[i]
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            onDismiss()
                            go()
                        }
                        .padding(vertical = 14.dp),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
