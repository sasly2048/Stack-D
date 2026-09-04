package app.stackd.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("NAVIGATE", style = MonoLabelSmall, color = colors.textMuted)
            Spacer(Modifier.height(8.dp))
            entries.forEach { (label, go) ->
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            go()
                        }
                        .padding(vertical = 14.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
