package app.stackd.feature.companion

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import app.stackd.data.ai.CompanionInput
import app.stackd.data.ai.CompanionMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val OPENERS = listOf(
    "Why did I break last session?",
    "Schedule my week for a 40-hour goal.",
    "Am I heading toward burnout?",
    "What tier am I closest to unlocking?",
)

data class CompanionUiState(
    val messages: List<CompanionMessage> = emptyList(),
    val busy: Boolean = false,
)

/**
 * Study Companion — a chat with the AI focus coach. Mirrors the web
 * `/companion` route: the LLM reads the caller's protocol/sessions server-side
 * (the route holds the key), so there's no local fallback — an unreachable
 * backend surfaces as an inline error bubble, same as the web catch.
 */
class CompanionViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(CompanionUiState())
    val state: StateFlow<CompanionUiState> = _state

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || _state.value.busy) return
        val history = _state.value.messages
        val withUser = history + CompanionMessage(role = "user", content = text)
        _state.value = _state.value.copy(messages = withUser, busy = true)
        viewModelScope.launch {
            // Pass the prior history (before this turn) as context, like the web.
            val reply = container.ai.askCompanion(CompanionInput(history = history, message = text))
            val bubble = reply?.reply
                ?: "⚠ Companion is unavailable right now. Try again in a moment."
            _state.value = _state.value.copy(
                messages = withUser + CompanionMessage(role = "assistant", content = bubble),
                busy = false,
            )
        }
    }
}

@Composable
fun CompanionRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: CompanionViewModel = viewModel(factory = stackdViewModel { CompanionViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    CompanionScreen(state = state, onSend = vm::send, onBack = onBack, modifier = modifier)
}

@Composable
fun CompanionScreen(
    state: CompanionUiState,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    var input by remember { mutableStateOf("") }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ResponsiveColumn {
            Text("STACK'D / COMPANION", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(8.dp))
            Text(
                "Study Companion",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Private coach · reads your protocol · never leaves your account",
                style = MonoLabelSmall,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(20.dp))

            if (state.messages.isEmpty()) {
                Text(
                    "Ask anything about your focus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                OPENERS.forEach { opener ->
                    Box(
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .border(1.dp, colors.border, CircleShape)
                            .clickable { onSend(opener) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(opener, style = MonoLabelSmall, color = colors.textMuted)
                    }
                }
            }

            state.messages.forEach { m -> MessageBubble(m) }

            if (state.busy) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Companion is thinking…",
                    style = MonoLabelSmall,
                    color = colors.textMuted,
                )
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 2000) input = it },
                label = { Text("Ask the companion…") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            EmberButton(
                text = if (state.busy) "Thinking…" else "Send",
                onClick = { onSend(input); input = "" },
                enabled = !state.busy && input.isNotBlank(),
                busy = state.busy,
            )

            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** One chat turn — user bubbles right/tinted, assistant left/plain (web parity). */
@Composable
private fun MessageBubble(m: CompanionMessage) {
    val colors = Stackd.colors
    val isUser = m.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .then(
                    if (isUser) {
                        Modifier
                            .background(colors.accent.copy(alpha = 0.15f), Radius2Xl)
                            .border(1.dp, colors.accent.copy(alpha = 0.3f), Radius2Xl)
                    } else {
                        Modifier
                    },
                )
                .padding(if (isUser) 12.dp else 0.dp),
        ) {
            Text(
                m.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) colors.textPrimary else colors.textPrimary.copy(alpha = 0.9f),
            )
        }
    }
}
