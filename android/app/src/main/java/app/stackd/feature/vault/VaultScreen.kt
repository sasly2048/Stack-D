package app.stackd.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.core.AppContainer
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.data.vault.VaultItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class VaultUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    /** Null until the entitlement resolves; false shows the Elite gate. */
    val hasAccess: Boolean? = null,
    val items: List<VaultItem> = emptyList(),
    val saving: Boolean = false,
)

/** Memory Vault — web's `vault.tsx`, Elite-gated like `requireFeature("vault")`. */
class VaultViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state

    init {
        load()
    }

    fun load() {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            val ent = runCatching { container.premium.myEntitlement() }.getOrNull()
            if (ent == null) {
                _state.value = _state.value.copy(loading = false, error = true)
                return@launch
            }
            if (!ent.isElite && !ent.isAdmin) {
                _state.value = VaultUiState(loading = false, hasAccess = false)
                return@launch
            }
            runCatching { container.vault.listVault(userId) }.fold(
                onSuccess = { _state.value = VaultUiState(loading = false, hasAccess = true, items = it) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = true) },
            )
        }
    }

    fun add(title: String, body: String, url: String, tags: String) {
        val userId = container.auth.currentUserId ?: return
        if (title.isBlank() || _state.value.saving) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            val item = runCatching {
                container.vault.createVaultItem(
                    userId = userId,
                    title = title.trim(),
                    body = body,
                    url = url,
                    tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                )
            }.getOrNull()
            _state.value = _state.value.copy(
                saving = false,
                items = if (item != null) listOf(item) + _state.value.items else _state.value.items,
            )
        }
    }

    fun delete(id: String) {
        _state.value = _state.value.copy(items = _state.value.items.filterNot { it.id == id })
        viewModelScope.launch { runCatching { container.vault.deleteVaultItem(id) } }
    }
}

@Composable
fun VaultRoute(
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
    vm: VaultViewModel = viewModel(factory = stackdViewModel { VaultViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    VaultScreen(
        state = state,
        onAdd = vm::add,
        onDelete = vm::delete,
        onRetry = vm::load,
        onBack = onBack,
        onUpgrade = onUpgrade,
        modifier = modifier,
    )
}

@Composable
fun VaultScreen(
    state: VaultUiState,
    onAdd: (title: String, body: String, url: String, tags: String) -> Unit,
    onDelete: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ResponsiveColumn {
            Text("STACK'D / VAULT", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("MEMORY VAULT")
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> Text(
                    "Unlocking…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                state.error -> {
                    Text(
                        "Couldn't open the vault.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                state.hasAccess == false -> EliteGate(
                    "Keep what mattered from every deep-work session — notes, links and artifacts, forever searchable.",
                    onUpgrade,
                )
                else -> {
                    var showForm by remember { mutableStateOf(false) }
                    GhostButton(
                        text = if (showForm) "Cancel" else "New entry",
                        onClick = { showForm = !showForm },
                    )
                    if (showForm) {
                        Spacer(Modifier.height(12.dp))
                        var title by remember { mutableStateOf("") }
                        var body by remember { mutableStateOf("") }
                        var url by remember { mutableStateOf("") }
                        var tags by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = title, onValueChange = { if (it.length <= 200) title = it },
                            label = { Text("Title") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = body, onValueChange = { body = it },
                            label = { Text("Notes") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = url, onValueChange = { url = it },
                            label = { Text("Link (optional)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tags, onValueChange = { tags = it },
                            label = { Text("Tags, comma-separated") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        EmberButton(
                            text = if (state.saving) "Saving…" else "Store it",
                            onClick = { onAdd(title, body, url, tags); showForm = false },
                            enabled = title.isNotBlank(),
                            busy = state.saving,
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    // Web searches title/notes/summary server-side with ilike;
                    // Android already holds the full page (limit 200), so the
                    // same match runs in memory with no extra round-trip.
                    var query by remember { mutableStateOf("") }
                    if (state.items.isNotEmpty()) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search title, notes, summary") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    val q = query.trim().lowercase()
                    val shown = if (q.isEmpty()) state.items else state.items.filter {
                        it.title.lowercase().contains(q) ||
                            it.body?.lowercase()?.contains(q) == true ||
                            it.aiSummary?.lowercase()?.contains(q) == true
                    }
                    if (state.items.isEmpty()) {
                        Text(
                            "Nothing stored yet.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                        )
                    } else if (shown.isEmpty()) {
                        Text(
                            "No entries match “$query”.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                        )
                    }
                    shown.forEach { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
                                .border(1.dp, colors.border, Radius2Xl)
                                .padding(14.dp),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "DELETE",
                                    style = MonoLabelSmall,
                                    color = colors.textMuted,
                                    modifier = Modifier
                                        .clickable { onDelete(item.id) }
                                        .padding(start = 8.dp),
                                )
                            }
                            item.body?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    it, style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted, maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (item.tags.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    item.tags.joinToString("  ") { "#$it" },
                                    style = MonoLabelSmall, color = colors.accent,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Shared Elite upsell block, mirroring the web's <PremiumGate>. */
@Composable
internal fun EliteGate(description: String, onUpgrade: () -> Unit) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.04f), Radius2Xl)
            .border(1.dp, colors.accent.copy(alpha = 0.3f), Radius2Xl)
            .padding(20.dp),
    ) {
        Text("ELITE FEATURE", style = MonoLabelSmall, color = colors.accent)
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        Spacer(Modifier.height(16.dp))
        EmberButton(text = "See plans", onClick = onUpgrade)
    }
}
