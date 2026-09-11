package app.stackd.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Stackd

/**
 * The small uppercase mono eyebrow the web app puts above every heading
 * (`font-mono text-[10px] tracking-[0.3em] uppercase text-ember`).
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Stackd.colors.accent,
) = Text(
    text = text.uppercase(),
    style = MonoLabel,
    color = color,
    modifier = modifier,
)

/** Primary call to action — the web's `.btn-ember` with its silver hairline. */
@Composable
fun EmberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val colors = Stackd.colors
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RadiusMd,
        modifier = modifier.fillMaxWidth().height(52.dp),
        border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.4f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.textPrimary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.textPrimary.copy(alpha = 0.5f),
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = colors.textPrimary,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/** Secondary/ghost button — translucent fill, hairline border. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val colors = Stackd.colors
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RadiusMd,
        modifier = modifier.fillMaxWidth().height(52.dp),
        border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.15f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.textPrimary.copy(alpha = 0.05f),
            contentColor = colors.textPrimary,
            disabledContainerColor = colors.textPrimary.copy(alpha = 0.03f),
            disabledContentColor = colors.textMuted,
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = colors.textPrimary,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/** Bare text link in the mono voice — "Already have one? Sign in →". */
@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Stackd.colors.textMuted,
) = TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
    Text(text.uppercase(), style = MonoLabel, color = color, textAlign = TextAlign.Center)
}

/**
 * Labelled input matching the web's `.auth-input`: translucent fill, hairline
 * border, ember focus ring.
 *
 * [hint] is only shown once the field has been left and is actually wrong, so
 * the form never turns red while it is being filled in for the first time.
 */
@Composable
fun StackdField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    hint: String? = null,
    isError: Boolean = false,
    placeholder: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
    centeredMono: Boolean = false,
    onFocusLost: () -> Unit = {},
) {
    val colors = Stackd.colors
    var hadFocus by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = MonoLabel, color = colors.textMuted)
            if (required) {
                Text(" *", style = MonoLabel, color = colors.accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            isError = isError,
            shape = RadiusMd,
            placeholder = placeholder?.let { { Text(it, color = colors.textMuted) } },
            visualTransformation =
                if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            textStyle =
                if (centeredMono) {
                    MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.textPrimary.copy(alpha = 0.07f),
                unfocusedContainerColor = colors.textPrimary.copy(alpha = 0.05f),
                errorContainerColor = colors.textPrimary.copy(alpha = 0.05f),
                focusedBorderColor = colors.accentDeep.copy(alpha = 0.6f),
                unfocusedBorderColor = colors.textPrimary.copy(alpha = 0.1f),
                errorBorderColor = colors.breach,
                cursorColor = colors.accent,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    // Validate on blur, not on keystroke: flagging "invalid
                    // email" while someone is typing the first character is noise.
                    if (hadFocus && !focusState.isFocused) onFocusLost()
                    hadFocus = focusState.isFocused
                },
        )
        if (hint != null && isError) {
            Spacer(Modifier.height(6.dp))
            // Announced the moment it appears — a hint that only exists
            // visually leaves a screen-reader user with a form that silently
            // refuses to submit.
            Text(
                hint,
                style = MonoLabelSmall,
                color = colors.breach,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
    }
}

/**
 * Inline error with a retry affordance, mirroring the web's `ProviderError`.
 * Assertive: an auth failure the user cannot see is an auth failure they will
 * keep repeating.
 */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val colors = Stackd.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.breach.copy(alpha = 0.05f), RadiusMd)
            .border(1.dp, colors.breach.copy(alpha = 0.4f), RadiusMd)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.breach,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                "RETRY →",
                style = MonoLabelSmall,
                color = colors.breach,
                modifier = Modifier.clickable(onClick = onRetry),
            )
        }
    }
}

/** Neutral notice — used for "check your email", which is not an error. */
@Composable
fun NoticeBanner(message: String, modifier: Modifier = Modifier) {
    val colors = Stackd.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.06f), RadiusMd)
            .border(1.dp, colors.accent.copy(alpha = 0.35f), RadiusMd)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = colors.accentGlow)
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) =
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = Stackd.colors.border)
