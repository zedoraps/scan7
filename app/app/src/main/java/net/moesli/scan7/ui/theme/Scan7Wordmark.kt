package net.moesli.scan7.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.moesli.scan7.Brand
import net.moesli.scan7.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The brand wordmark ("scan7" / "scanjo"): "scan" in the text color + [Brand.WORDMARK_ACCENT] in accent, display font Bold. */
@Composable
fun Scan7Wordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 32.sp) {
    val colors = Scan7Theme.colors
    val appName = stringResource(R.string.app_name)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.text)) { append("scan") }
            withStyle(SpanStyle(color = colors.accent)) { append(Brand.WORDMARK_ACCENT) }
        },
        style = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = fontSize),
        modifier = modifier.semantics { contentDescription = appName },
    )
}

@Preview
@Composable
private fun Scan7WordmarkPreview() {
    Scan7Theme {
        Scan7Wordmark(Modifier.background(Scan7Theme.colors.ground).padding(16.dp))
    }
}
