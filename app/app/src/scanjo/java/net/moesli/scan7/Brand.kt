package net.moesli.scan7

import net.moesli.scan7.game.GameKind

/**
 * Scanjo, the Skyjo score counter (-Pscan7.brand=scanjo, app/build.gradle.kts "Brand";
 * brand/scanjo/BRAND.md). Scan7's copy is in src/scan7.
 */
object Brand {
    /** Scheme of the QR join link (`scanjo://join…`), same as the manifest placeholder. */
    const val JOIN_SCHEME = "scanjo"

    /** Skyjo only. */
    val games: List<GameKind> = listOf(GameKind.SKYJO)

    /** Sky Day by default, Sky Night when the system is in dark mode. */
    const val FOLLOWS_SYSTEM_THEME = true

    /** Display font (ui/theme/BrandTheme.kt), named for the About notices. */
    const val DISPLAY_FONT = "Fredoka"

    /** Wordmark: "scan" + this in the accent color. */
    const val WORDMARK_ACCENT = "jo"
}
