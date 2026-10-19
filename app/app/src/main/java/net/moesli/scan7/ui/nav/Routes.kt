package net.moesli.scan7.ui.nav

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
 * One entry of the app's back stack. Plain data, encoded to a string for `rememberSaveable`
 * (rotation / process death) with [encode] / [decodeRoute].
 */
sealed interface Route {
    data object Home : Route

    data object Players : Route

    /** New game; with [seating] ("New game with same players") it opens prefilled on the seating step. */
    data class NewGame(val seating: List<Long>? = null, val target: Int = DEFAULT_TARGET) : Route

    data class Round(val gameId: Long) : Route

    /** Round menu → Players: pick who plays on (step 1), then Seats (step 2), back to the round. */
    data class GamePlayers(val gameId: Long) : Route

    /** Scan / manual entry for [playerId]; [round] `null` = the running round. */
    data class Scan(val gameId: Long, val playerId: Long, val start: ScanStart, val round: Int? = null) : Route

    data class Scoreboard(val gameId: Long) : Route

    data class GameOver(val gameId: Long) : Route

    /** About & licenses (version, AGPL source link, third-party notices); [license] = open notice. */
    data class About(val license: String? = null) : Route

    data class Debug(val screen: DebugScreen) : Route

    /** Multiplayer: join a game by scanning its QR code; [link] = a scanned / deep-linked `scan7://join…`. */
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
 * Key for the screen's saved UI state. A "Scan next" chain keeps one key for all players, so the
 * camera stays bound while it moves on to the next player.
 */
val Route.stateKey: String
    get() = when (this) {
        is Route.Scan -> if (start == ScanStart.NEXT) "scan:$gameId:next" else encode()
        else -> encode()
    }

fun Route.encode(): String = when (this) {
    Route.Home -> "home"
    Route.Players -> "players"
    is Route.NewGame -> "newgame:$target:" + seating?.joinToString(",").orEmpty() + if (seating == null) "" else ";"
    is Route.Round -> "round:$gameId"
    is Route.GamePlayers -> "gameplayers:$gameId"
    is Route.Scan -> "scan:$gameId:$playerId:${start.name}:${round ?: ""}"
    is Route.Scoreboard -> "scoreboard:$gameId"
    is Route.GameOver -> "gameover:$gameId"
    is Route.About -> "about:${license.orEmpty()}"
    is Route.Debug -> "debug:${screen.name}"
    is Route.Join -> "join:" + link?.let { java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
}

/** Inverse of [encode]; `null` for anything it can't read. */
fun decodeRoute(text: String): Route? = runCatching {
    val parts = text.split(":")
    when (parts[0]) {
        "home" -> Route.Home
        "players" -> Route.Players
        "newgame" -> {
            val seat = parts[2]
            val seating = if (seat.endsWith(";")) seat.dropLast(1).split(",").filter { it.isNotEmpty() }.map { it.toLong() } else null
            Route.NewGame(seating, parts[1].toInt())
        }
        "round" -> Route.Round(parts[1].toLong())
        "gameplayers" -> Route.GamePlayers(parts[1].toLong())
        "scan" -> Route.Scan(parts[1].toLong(), parts[2].toLong(), ScanStart.valueOf(parts[3]), parts[4].toIntOrNull())
        "scoreboard" -> Route.Scoreboard(parts[1].toLong())
        "gameover" -> Route.GameOver(parts[1].toLong())
        "about" -> Route.About(parts.getOrNull(1)?.takeIf { it.isNotEmpty() })
        "debug" -> Route.Debug(DebugScreen.valueOf(parts[1]))
        "join" -> Route.Join(parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { java.net.URLDecoder.decode(it, "UTF-8") })
        else -> null
    }
}.getOrNull()

/**
 * Pure back-stack operations. The stack always starts with [Route.Home] and is never empty.
 */
object Stack {
    /** Back: drops the top entry; on Home alone returns `null` (leave the app). */
    fun pop(stack: List<Route>): List<Route>? = if (stack.size <= 1) null else stack.dropLast(1)

    fun push(stack: List<Route>, route: Route): List<Route> = if (stack.last() == route) stack else stack + route

    /** Replaces the top entry (e.g. New game → Round, Scan next → next player). */
    fun replaceTop(stack: List<Route>, route: Route): List<Route> = normalize(stack.dropLast(1) + route)

    /** Ensures Home at the bottom (restored or hand-made stacks). */
    fun normalize(stack: List<Route>): List<Route> = when {
        stack.isEmpty() -> listOf(Route.Home)
        stack.first() != Route.Home -> listOf(Route.Home) + stack
        else -> stack
    }
}
