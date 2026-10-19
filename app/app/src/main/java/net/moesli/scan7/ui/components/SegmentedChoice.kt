package net.moesli.scan7.ui.components

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.ui.text.resolve
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * One choice of a few [options]: an M3 `SingleChoiceSegmentedButtonRow` in the Scan7 colors (selected
 * = cyan). [spoken] replaces a label for TalkBack (no arrow glyphs). Used by the round menu settings
 * and New game's game options.
 */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> UiText,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    spoken: ((T) -> UiText)? = null,
) {
    val colors = Scan7Theme.colors
    SingleChoiceSegmentedButtonRow(
        modifier
            .fillMaxWidth()
            // Equal segment heights when one label wraps to two lines.
            .height(IntrinsicSize.Min),
    ) {
        options.forEachIndexed { i, option ->
            val text = label(option).resolve()
            val description = spoken?.invoke(option)?.resolve() ?: text
            SegmentedButton(
                selected = option == selected,
                onClick = { if (option != selected) onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size, RoundedCornerShape(Scan7Radii.iconButton)),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.accent,
                    activeContentColor = colors.onAccent,
                    activeBorderColor = colors.accent,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = colors.text,
                    inactiveBorderColor = colors.line,
                ),
                icon = {},
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .fillMaxHeight()
                    .semantics { contentDescription = description },
            ) {
                // Two lines at large font scales on narrow screens ("Nächster / Spieler") instead of cutting the label;
                // a label too long for one word per line carries its own break ("↺ Gegen-\nuhrzeigersinn").
                Text(text, style = Scan7Type.text13, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}
