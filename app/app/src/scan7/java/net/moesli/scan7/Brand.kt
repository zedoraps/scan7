package net.moesli.scan7

import net.moesli.scan7.game.GameKind

/**
 * Scan7, the Flip 7 score keeper: the default brand (app/build.gradle.kts "Brand"; brand/BRAND.md).
 * Scanjo has its own copy in src/scanjo.
 */
object Brand {
    /** Scheme of the QR join link (`scan7://join…`), same as the manifest placeholder. */
    const val JOIN_SCHEME = "scan7"

    /** The games New game offers, the first is the default (debug builds can add the Skyjo preview). */
    val games: List<GameKind> = listOf(GameKind.FLIP7)

    /** Always dark: people play in dim rooms, and the brand has no light palette. */
    const val FOLLOWS_SYSTEM_THEME = false

    /** Display font (ui/theme/BrandTheme.kt), named for the About notices. */
    const val DISPLAY_FONT = "Unbounded"

    /** Wordmark: "scan" + this in the accent color. */
    const val WORDMARK_ACCENT = "7"
}
