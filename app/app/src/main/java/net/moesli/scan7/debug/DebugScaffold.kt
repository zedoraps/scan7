package net.moesli.scan7.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.moesli.scan7.ui.components.ScreenHeader
import net.moesli.scan7.ui.components.headerInsets
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme

/**
 * Debug screen frame in the Scan7 look: ground background, the app's [ScreenHeader] (back button,
 * Unbounded title, optional muted [subtitle]) and the content below it. [content] gets the padding
 * for the space under the header and above the navigation bar.
 */
@Composable
fun DebugScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(modifier.fillMaxSize().background(Scan7Theme.colors.ground)) {
        ScreenHeader(
            title = title,
            onBack = onBack,
            subtitle = subtitle,
            modifier = Modifier
                .headerInsets()
                .padding(horizontal = Scan7Spacing.screenMargin),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content(PaddingValues(top = Scan7Spacing.s, bottom = navBottom))
        }
    }
}
