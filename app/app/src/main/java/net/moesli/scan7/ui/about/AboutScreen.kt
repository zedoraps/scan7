package net.moesli.scan7.ui.about

import androidx.annotation.StringRes
import net.moesli.scan7.ui.components.centeredMaxWidth
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import android.text.format.DateFormat
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.moesli.scan7.BuildConfig
import net.moesli.scan7.Brand
import net.moesli.scan7.R
import net.moesli.scan7.ui.components.ChevronRightIcon
import net.moesli.scan7.ui.components.ScreenHeader
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.components.headerInsets
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type
import net.moesli.scan7.ui.theme.Scan7Wordmark

/**
 * A license text bundled in the APK (`assets/licenses/<id>.txt`, copied there at build time from
 * the repo's LICENSE and docs/third_party/, see app/build.gradle.kts `copyLicenseTexts`).
 * Ids contain no ':' (they are part of the encoded route).
 */
enum class LicenseText(val id: String, val title: String) {
    AGPL("agpl-3.0", "GNU AGPL v3.0"),
    APACHE("apache-2.0", "Apache License 2.0"),
    OFL_UNBOUNDED("unbounded-ofl", "SIL Open Font License 1.1 (Unbounded)"),
    OFL_FREDOKA("fredoka-ofl", "SIL Open Font License 1.1 (Fredoka)"),
    OFL_DM_MONO("dmmono-ofl", "SIL Open Font License 1.1 (DM Mono)"),
    /** Proprietary (Google terms) + Scan7's AGPL §7 permission to link it: notice, not a license text. */
    PLAY_SERVICES("play-services-nearby", "Google Play services"),
    ;

    companion object {
        fun fromId(id: String?): LicenseText? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One third-party notice: names and license names stay as they are (proper names); the [detail]
 * line (what it is + license) is a string resource.
 */
data class ThirdParty(val name: String, @StringRes val detail: Int, val license: LicenseText)

/**
 * What the APK contains from others. A static list instead of a generated one (AboutLibraries /
 * oss-licenses): the app has few dependency families, all under three licenses, and a plugin that
 * must keep up with AGP 9 and adds a UI library isn't worth it for that (see docs/dev/RELEASE.md).
 * Keep it current when a dependency family is added.
 */
val ThirdPartyNotices = listOf(
    ThirdParty("Ultralytics YOLO", R.string.about_tp_yolo, LicenseText.AGPL),
    ThirdParty("LiteRT (Google AI Edge)", R.string.about_tp_litert, LicenseText.APACHE),
    ThirdParty("Android Jetpack", R.string.about_tp_jetpack, LicenseText.APACHE),
    ThirdParty("Kotlin", R.string.about_tp_kotlin, LicenseText.APACHE),
    ThirdParty("ZXing", R.string.about_tp_zxing, LicenseText.APACHE),
    ThirdParty("Google Play services Nearby", R.string.about_tp_nearby, LicenseText.PLAY_SERVICES),
    // The brand's display font (Scan7: Unbounded, Scanjo: Fredoka).
    if (Brand.DISPLAY_FONT == "Fredoka") {
        ThirdParty("Fredoka", R.string.about_tp_fredoka, LicenseText.OFL_FREDOKA)
    } else {
        ThirdParty("Unbounded", R.string.about_tp_unbounded, LicenseText.OFL_UNBOUNDED)
    },
    ThirdParty("DM Mono", R.string.about_tp_dmmono, LicenseText.OFL_DM_MONO),
)

/**
 * About & licenses (opened from the Home version footer): wordmark, version + model, the Flip 7
 * disclaimer, the AGPL notice with links to the license text, the public source and the privacy
 * policy, then the third-party notices; each license opens its bundled text ([LicenseTextScreen]).
 */
@Composable
fun AboutScreen(
    modelInfo: String,
    onBack: () -> Unit,
    onLicense: (LicenseText) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    val uriHandler = LocalUriHandler.current
    // No browser installed: nothing to do (the URL is still shown on the row).
    val open: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    val showLicense = stringResource(R.string.about_show_license)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .centeredMaxWidth()
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        ScreenHeader(title = stringResource(R.string.about_title), onBack = onBack, modifier = Modifier.headerInsets())
        LazyColumn(
            contentPadding = PaddingValues(
                top = Scan7Spacing.xl,
                bottom = Scan7Spacing.xl + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
            modifier = Modifier.weight(1f),
        ) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xs), modifier = Modifier.padding(bottom = Scan7Spacing.m)) {
                    Scan7Wordmark(fontSize = 28.sp)
                    Text(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        color = colors.text,
                        style = Scan7Type.text13,
                    )
                    ModelLines(modelInfo)
                    Text(
                        stringResource(R.string.about_disclaimer),
                        color = colors.textMuted,
                        style = Scan7Type.text12,
                        modifier = Modifier.padding(top = Scan7Spacing.s),
                    )
                    Text(
                        stringResource(R.string.about_license_body),
                        color = colors.text,
                        style = Scan7Type.text13,
                        modifier = Modifier.padding(top = Scan7Spacing.s),
                    )
                }
            }
            item(key = "agpl") {
                LinkRow(LicenseText.AGPL.title, showLicense, onClick = { onLicense(LicenseText.AGPL) })
            }
            item(key = "source") {
                LinkRow(stringResource(R.string.about_source), BuildConfig.SOURCE_URL, onClick = { open(BuildConfig.SOURCE_URL) })
            }
            item(key = "privacy") {
                LinkRow(stringResource(R.string.about_privacy), BuildConfig.PRIVACY_URL, onClick = { open(BuildConfig.PRIVACY_URL) })
            }
            item(key = "third-label") {
                SectionLabel(
                    stringResource(R.string.about_third_party),
                    Modifier
                        .padding(top = Scan7Spacing.l, bottom = Scan7Spacing.xxs)
                        .semantics { heading() },
                )
            }
            items(ThirdPartyNotices, key = { it.name }) { notice ->
                LinkRow(notice.name, stringResource(notice.detail), onClick = { onLicense(notice.license) }, onClickLabel = showLicense)
            }
        }
    }
}

/**
 * The model in words ("Card model 18 Oct 2026", from the date in its version line) and the
 * technical line (file, training run, precision, size) small below it; only the technical line
 * when the version has no date.
 */
@Composable
private fun ModelLines(modelInfo: String) {
    val colors = Scan7Theme.colors
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(modelInfo) { modelDate(modelInfo) }
    if (date == null) {
        Text(stringResource(R.string.about_model, modelInfo), color = colors.textMuted, style = Scan7Type.text12)
        return
    }
    val formatted = remember(date, locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMy"), locale).format(date)
    }
    Text(stringResource(R.string.about_model_date, formatted), color = colors.textMuted, style = Scan7Type.text12)
    Text(modelInfo, color = colors.textMuted.copy(alpha = 0.7f), style = Scan7Type.text10)
}

/** An M3 `ListItem` on the done surface (outlined, like the Home history rows) with a chevron. */
@Composable
private fun LinkRow(title: String, supporting: String, onClick: () -> Unit, onClickLabel: String? = null) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.card)
    ListItem(
        headlineContent = { Text(title, style = Scan7Type.text14) },
        supportingContent = { Text(supporting, style = Scan7Type.text12) },
        trailingContent = { ChevronRightIcon(colors.textMuted) },
        colors = ListItemDefaults.colors(
            containerColor = colors.surfaceDone,
            headlineColor = colors.text,
            supportingColor = colors.textMuted,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.lineSubtle, shape)
            .scan7Clickable(onClick, onClickLabel = onClickLabel),
    )
}

/**
 * Full text of one bundled [license] (monospace, selectable, scrolls). The 80-column hard breaks
 * are unwrapped into paragraphs ([reflowLicense]) that wrap to the screen; indented items keep
 * their indent.
 */
@Composable
fun LicenseTextScreen(license: LicenseText, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val context = LocalContext.current
    val paragraphs by produceState(initialValue = emptyList<LicenseParagraph>(), license) {
        value = reflowLicense(readLicense(context, license))
    }
    val style = Scan7Type.text11
    // DM Mono: every character is 0.6 em wide.
    val charWidth = with(LocalDensity.current) { (style.fontSize * 0.6f).toDp() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .centeredMaxWidth()
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        ScreenHeader(title = license.title, onBack = onBack, modifier = Modifier.headerInsets())
        SelectionContainer(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = Scan7Spacing.l, bottom = Scan7Spacing.xl)
                    .padding(WindowInsets.navigationBars.asPaddingValues()),
            ) {
                paragraphs.forEach { p ->
                    if (p.text.isEmpty()) {
                        Spacer(Modifier.height(Scan7Spacing.m))
                    } else {
                        Text(
                            text = p.text,
                            color = colors.text,
                            style = style,
                            modifier = Modifier.padding(start = charWidth * p.indent),
                        )
                    }
                }
            }
        }
    }
}

private suspend fun readLicense(context: Context, license: LicenseText): String = withContext(Dispatchers.IO) {
    runCatching { context.assets.open("licenses/${license.id}.txt").bufferedReader().use { it.readText() } }
        .getOrDefault(license.title)
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AboutPreview() {
    Scan7Theme {
        AboutScreen(modelInfo = "model.tflite ft_v1/best · fp32 (9.9 MB)", onBack = {}, onLicense = {})
    }
}
