package dev.glasstv.launcher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.glasstv.launcher.remote.RemoteBus
import dev.glasstv.launcher.remote.TextTarget
import dev.glasstv.launcher.ui.theme.GlassType
import dev.glasstv.launcher.ui.theme.LocalGlass

/**
 * TV-friendly text field. D-pad focus lands on a glass button (so the on-screen keyboard doesn't pop
 * up as you scroll past); OK opens editing. While focused, the paired phone's keyboard types here too.
 */
@Composable
fun GlassTextField(
    value: String,
    onCommit: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onChange: ((String) -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
) {
    val c = LocalGlass.current
    var editing by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val latestText by rememberUpdatedState(text)
    val commit by rememberUpdatedState(onCommit)
    val change by rememberUpdatedState(onChange)
    val fieldFocus = remember { FocusRequester() }
    val buttonFocus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var restoreFocus by remember { mutableStateOf(false) }

    val target = remember {
        TextTarget { t, submit, backspace ->
            val cur = latestText.text
            val next = if (backspace) cur.dropLast(1) else cur + t
            text = TextFieldValue(next, TextRange(next.length))
            change?.invoke(next)
            if (submit) commit(next)
        }
    }
    DisposableEffect(focused) {
        if (focused) RemoteBus.textTarget = target
        onDispose { if (RemoteBus.textTarget === target) RemoteBus.textTarget = null }
    }

    Box(modifier.onFocusChanged { focused = it.hasFocus }) {
        if (editing) {
            LaunchedEffect(Unit) { fieldFocus.requestFocus() }
            BasicTextField(
                value = text,
                onValueChange = { text = it; change?.invoke(it.text) },
                singleLine = true,
                textStyle = GlassType.body.copy(color = c.text),
                cursorBrush = SolidColor(c.rose),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    commit(text.text); editing = false; restoreFocus = true
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .onFocusChanged { if (!it.isFocused && editing) { commit(text.text); editing = false } }
                    .glassPanel(shape, 4.dp)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        } else {
            LaunchedEffect(Unit) {
                if (restoreFocus) { restoreFocus = false; runCatching { buttonFocus.requestFocus() } }
            }
            GlassButton(
                onClick = { editing = true },
                shape = shape,
                focusScale = 1.03f,
                modifier = Modifier.fillMaxWidth().focusRequester(buttonFocus),
            ) {
                GlassText(
                    text.text.ifEmpty { placeholder },
                    GlassType.body,
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    color = if (text.text.isEmpty()) c.textSoft else c.text,
                    maxLines = 1,
                )
            }
        }
    }
}
