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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
            // Prior turns as context, like the web — but bounded. The route
            // rejects more than 30 history messages (and only reads the last 20),
            // so sending the whole chat broke it after ~15 exchanges. Local
            // "unavailable" notices aren't real assistant turns; leave them out.
            val context = history
                .filterNot { it.role == "assistant" && it.content.startsWith(ERROR_PREFIX) }
                .takeLast(HISTORY_LIMIT)
            val reply = container.ai.askCompanion(CompanionInput(history = context, message = text))
            val bubble = reply?.reply
                ?: "$ERROR_PREFIX Companion is unavailable right now. Try again in a moment."
            _state.value = _state.value.copy(
                messages = withUser + CompanionMessage(role = "assistant", content = bubble),
                busy = false,
            )
        }
    }

    private companion object {
        const val HISTORY_LIMIT = 20
        const val ERROR_PREFIX = "⚠"
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
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Follow the conversation: every new turn (and the "thinking" row) scrolls
    // the newest line into view instead of leaving it below the fold.
    val lastIndex = state.messages.size + (if (state.busy) 1 else 0) + (if (state.messages.isEmpty()) 1 else 0)
    androidx.compose.runtime.LaunchedEffect(lastIndex) {
        if (lastIndex > 0) listState.animateScrollToItem(lastIndex - 1)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            // Status bar, nav bar AND keyboard: the input row rides above the IME.
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = app.stackd.core.ui.DEFAULT_MAX_CONTENT_WIDTH)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("STACK'D / COMPANION", style = MonoLabel, color = colors.textMuted)
                androidx.compose.material3.TextButton(onClick = onBack) {
                    Text("Back", style = MonoLabel, color = colors.textMuted)
                }
            }
            Text(
                "Study Companion",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Private coach. Reads your protocol. Never leaves your account.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(16.dp))

            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (state.messages.isEmpty()) {
                    item(key = "openers") {
                        Column {
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
                                        .heightIn(min = 48.dp)
                                        .border(1.dp, colors.border, CircleShape)
                                        .clip(CircleShape)
                                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                            onSend(opener)
                                        }
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(opener, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
                items(state.messages.size) { i -> MessageBubble(state.messages[i]) }
                if (state.busy) {
                    item(key = "thinking") {
                        Text(
                            "Companion is thinking…",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }

            // Pinned composer: always reachable, however long the chat grows.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 2000) input = it },
                    placeholder = { Text("Ask the companion…") },
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                EmberButton(
                    text = if (state.busy) "…" else "Send",
                    onClick = { onSend(input); input = "" },
                    enabled = !state.busy && input.isNotBlank(),
                    busy = state.busy,
                    modifier = Modifier.width(96.dp),
                )
            }
        }
    }
}

/** One chat turn — user bubbles right/tinted, assistant left/plain (web parity). */
@Composable
private fun MessageBubble(m: CompanionMessage) {
    val colors = Stackd.colors
    val isUser = m.role == "user"
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        // A MAX width (web's max-w-[80%]): short messages hug their text
        // instead of stretching to a fixed 80% slab.
        val cap = maxWidth * 0.8f
        Box(
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = cap)
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
