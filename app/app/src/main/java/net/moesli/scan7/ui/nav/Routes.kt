package net.moesli.scan7.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import net.moesli.scan7.game.DEFAULT_TARGET
import net.moesli.scan7.game.GameState
import net.moesli.scan7.ui.round.scanNextAfter

/** How a scan route opens [net.moesli.scan7.ui.scan.ScanFlow]. */
enum class ScanStart {
    /** Camera for one player (card Scan, sheet Scan / Rescan). */
    CAMERA,

    /** Camera opened by "Scan next" or by continuing after a save ([afterSave]): header "Next: Ben". */
    NEXT,

    /** Manual screen, empty (sheet "Enter manually"). */
    MANUAL,

    /** Manual screen prefilled with the stored cards of that result (Edit, scoreboard cell). */
    EDIT,
}

/**
 * Debug screens (debug menu + the screens it opens). [SCAN_DEMO] has no menu entry: only the
 * `debug_scan_demo` / `debug_scan_photo` launch hooks open it.
 */
enum class DebugScreen { HOME, CAMERA, PHOTO, SCAN_DEMO, AVATARS, MULTIPLAYER }

/**
 * One entry of the app's back stack: a Navigation 3 key. Plain `@Serializable` data, so the stack
 * survives rotation and process death (saved by AppNavHost with the sealed serializer, no reflection).
 */
@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    @Serializable
    data object Players : Route

    /** New game; with [seating] ("New game with same players") it opens prefilled on the seating step. */
    @Serializable
    data class NewGame(val seating: List<Long>? = null, val target: Int = DEFAULT_TARGET) : Route

    @Serializable
    data class Round(val gameId: Long) : Route

    /** Round menu → Players: pick who plays on (step 1), then Seats (step 2), back to the round. */
    @Serializable
    data class GamePlayers(val gameId: Long) : Route

    /** Scan / manual entry for [playerId]; [round] `null` = the running round. */
    @Serializable
    data class Scan(val gameId: Long, val playerId: Long, val start: ScanStart, val round: Int? = null) : Route

    @Serializable
    data class Scoreboard(val gameId: Long) : Route

    @Serializable
    data class GameOver(val gameId: Long) : Route

    /** About & licenses (version, AGPL source link, third-party notices); [license] = open notice. */
    @Serializable
    data class About(val license: String? = null) : Route

    @Serializable
    data class Debug(val screen: DebugScreen) : Route

    /** Multiplayer: join a game by scanning its QR code; [link] = a scanned / deep-linked `scan7://join…`. */
    @Serializable
    data class Join(val link: String? = null) : Route
}

/**
 * What the review's OK (and Confirm bust, manual Save, the camera's Bust tap) does after saving:
 * continue with the next open player's camera, or go back to the table. Chosen with the ▾ of the
 * split OK button or in the round menu ("After a score"), one remembered setting for both
 * (settings key [AfterScan.SETTING_KEY]); default [NEXT].
 */
enum class AfterScan {
    NEXT,
    TABLE,
    ;

    companion object {
        const val SETTING_KEY = "after_scan"

        /** The stored value, [NEXT] if never chosen (or unreadable). */
        fun fromSetting(value: String?): AfterScan = entries.firstOrNull { it.name == value } ?: NEXT
    }
}

/**
 * How results are entered (round menu "Entry", app-wide, settings key [EntryMode.SETTING_KEY]):
 * [SCAN] with the camera (default), or [TYPE]: every Scan entry point (in-card Scan, Scan next,
 * sheet Scan / Rescan, double-tap) opens the manual entry instead, without the camera.
 */
enum class EntryMode {
    SCAN,
    TYPE,
    ;

    /** The start for a scan of the running round: [TYPE] turns the camera starts into [ScanStart.MANUAL]. */
    fun startFor(start: ScanStart): ScanStart =
        if (this == TYPE && (start == ScanStart.CAMERA || start == ScanStart.NEXT)) ScanStart.MANUAL else start

    companion object {
        const val SETTING_KEY = "entry_mode"

        /** The stored value, [SCAN] if never chosen (or unreadable). */
        fun fromSetting(value: String?): EntryMode = entries.firstOrNull { it.name == value } ?: SCAN
    }
}

/**
 * The player a save on this scan route could continue with: the next open player of the running
 * round after this one (in the game's scan direction, see scanNextAfter), whatever started the
 * scan (card Scan, Scan next, manual entry). `null` when nobody else is open, and for edits (Edit,
 * scoreboard cell, closed rounds): those always go back.
 */
fun Route.Scan.nextPlayer(state: GameState): Long? =
    if (start == ScanStart.EDIT || round != null) null else state.scanNextAfter(playerId)

/**
 * Where a scan route goes once its player's result is saved (scan, manual entry or the camera's
 * Bust tap), on the state after the save: with [AfterScan.NEXT] the next open player's camera (a
 * "Scan next" chain; with [EntryMode.TYPE] their manual entry), otherwise (or when nobody is open
 * any more) `null` = back to the round screen.
 */
fun Route.Scan.afterSave(state: GameState, choice: AfterScan, entry: EntryMode = EntryMode.SCAN): Route.Scan? =
    if (choice == AfterScan.TABLE) null else nextPlayer(state)?.let { Route.Scan(gameId, it, entry.startFor(ScanStart.NEXT)) }

/**
 * Key for the screen's saved UI state: the Navigation 3 entry's content key (also its scene key,
 * so equal keys swap the screen in place, without a transition). A "Scan next" chain keeps one key
 * for all players, so the camera stays bound while it moves on to the next player.
 */
val Route.stateKey: String
    get() = when (this) {
        is Route.Scan -> if (start == ScanStart.NEXT) "Scan(gameId=$gameId, next)" else toString()
        else -> toString()
    }

/** Screens with a camera: no transition between two of them (two live previews would fight over the camera). */
val Route.hasCamera: Boolean
    get() = (this is Route.Scan && (start == ScanStart.CAMERA || start == ScanStart.NEXT)) ||
        (this is Route.Debug && screen == DebugScreen.CAMERA)

/**
 * Pure back-stack operations. The stack always starts with [Route.Home], is never empty and holds
 * each [stateKey] at most once (Navigation 3 keeps one saved state per content key).
 */
object Stack {
    /** Back: drops the top entry; on Home alone returns `null` (leave the app). */
    fun pop(stack: List<Route>): List<Route>? = if (stack.size <= 1) null else stack.dropLast(1)

    /**
     * Opens [route] on top. A route already on the stack (same [stateKey], e.g. a double tap)
     * is not added twice: the stack goes back to that entry, with [route] in its place.
     */
    fun push(stack: List<Route>, route: Route): List<Route> {
        val existing = stack.indexOfFirst { it.stateKey == route.stateKey }
        return normalize(if (existing >= 0) stack.take(existing) + route else stack + route)
    }

    /** Replaces the top entry (e.g. New game → Round, Scan next → next player). */
    fun replaceTop(stack: List<Route>, route: Route): List<Route> = push(stack.dropLast(1), route)

    /** Ensures Home at the bottom (restored or hand-made stacks) and no repeated [stateKey]. */
    fun normalize(stack: List<Route>): List<Route> {
        val withHome = if (stack.firstOrNull() == Route.Home) stack else listOf(Route.Home) + stack
        return withHome.distinctBy { it.stateKey }
    }
}
