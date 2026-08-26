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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.core.AppContainer
import app.stackd.core.parseIsoMillis
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.data.vault.Capsule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CapsuleUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val hasAccess: Boolean? = null,
    val rows: List<Capsule> = emptyList(),
    val saving: Boolean = false,
)

/**
 * Time capsules — web's `capsule.tsx`. Sealing is client-side; unsealing goes
 * through the `open_capsule` RPC, which refuses until `open_at` has passed, so
 * a clock-rolled device can't peek early.
 */
class CapsuleViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(CapsuleUiState())
    val state: StateFlow<CapsuleUiState> = _state

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
                _state.value = CapsuleUiState(loading = false, hasAccess = false)
                return@launch
            }
            runCatching { container.vault.listCapsules(userId) }.fold(
                onSuccess = { _state.value = CapsuleUiState(loading = false, hasAccess = true, rows = it) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = true) },
            )
        }
    }

    fun seal(message: String, days: Int) {
        val userId = container.auth.currentUserId ?: return
        if (message.isBlank() || _state.value.saving) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            runCatching { container.vault.writeCapsule(userId, message, days) }
            _state.value = _state.value.copy(saving = false)
            load()
        }
    }

    fun open(id: String) {
        viewModelScope.launch {
            runCatching { container.vault.openCapsule(id) }
            load()
        }
    }
}

@Composable
fun CapsuleRoute(
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
    vm: CapsuleViewModel = viewModel(factory = stackdViewModel { CapsuleViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    CapsuleScreen(
        state = state,
        onSeal = vm::seal,
        onOpen = vm::open,
        onRetry = vm::load,
        onBack = onBack,
        onUpgrade = onUpgrade,
        modifier = modifier,
    )
}

@Composable
fun CapsuleScreen(
    state: CapsuleUiState,
    onSeal: (message: String, days: Int) -> Unit,
    onOpen: (String) -> Unit,
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
            Text("STACK'D / CAPSULE", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("TIME CAPSULES")
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                state.error -> {
                    Text(
                        "Couldn't load your capsules.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                state.hasAccess == false -> EliteGate(
                    "Write to your future self. Sealed until the date you choose — not even you can peek early.",
                    onUpgrade,
                )
                else -> {
                    var message by remember { mutableStateOf("") }
                    var days by remember { mutableStateOf("30") }
                    OutlinedTextField(
                        value = message,
                        onValueChange = { if (it.length <= 4000) message = it },
                        label = { Text("Message to future you") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = days,
                        onValueChange = { days = it.filter(Char::isDigit).take(3) },
                        label = { Text("Sealed for (days, 1–365)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    EmberButton(
                        text = if (state.saving) "Sealing…" else "Seal it",
                        onClick = { onSeal(message, days.toIntOrNull() ?: 30); message = "" },
                        enabled = message.isNotBlank(),
                        busy = state.saving,
                    )

                    Spacer(Modifier.height(20.dp))
                    if (state.rows.isEmpty()) {
                        Text(
                            "No capsules yet.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                        )
                    }
                    val now = System.currentTimeMillis()
                    state.rows.forEach { c ->
                        val openMs = parseIsoMillis(c.openAt) ?: Long.MAX_VALUE
                        val unlockable = c.openedAt == null && openMs <= now
                        val opened = c.openedAt != null
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
                                .border(
                                    1.dp,
                                    if (unlockable) colors.accent.copy(alpha = 0.5f) else colors.border,
                                    Radius2Xl,
                                )
                                .padding(14.dp),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    when {
                                        opened -> "OPENED"
                                        unlockable -> "READY TO OPEN"
                                        else -> "SEALED · opens ${c.openAt.take(10)}"
                                    },
                                    style = MonoLabelSmall,
                                    color = if (unlockable) colors.accent else colors.textMuted,
                                    modifier = Modifier.weight(1f),
                                )
                                if (unlockable) {
                                    Text(
                                        "OPEN",
                                        style = MonoLabelSmall,
                                        color = colors.accent,
                                        modifier = Modifier.clickable { onOpen(c.id) },
                                    )
                                }
                            }
                            if (opened) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    c.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
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
