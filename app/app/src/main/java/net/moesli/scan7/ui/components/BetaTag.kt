package net.moesli.scan7.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.moesli.scan7.R
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** Small "BETA" pill next to multiplayer entry points (Share this game, Join a game). */
@Composable
fun BetaTag(modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    CappedFontScale {
        Text(
            text = stringResource(R.string.beta_tag),
            color = colors.accent,
            style = Scan7Type.tag11,
            maxLines = 1,
            modifier = modifier
                .border(1.dp, colors.accent, RoundedCornerShape(50))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Preview
@Composable
private fun BetaTagPreview() {
    Scan7Theme { BetaTag() }
}
