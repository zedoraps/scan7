package net.moesli.scan7.ui.nav

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.moesli.scan7.BuildConfig
import net.moesli.scan7.camera.CameraScreen
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.debug.AvatarGalleryScreen
import net.moesli.scan7.debug.DebugHomeScreen
import net.moesli.scan7.debug.rememberDebugHomeState
import net.moesli.scan7.debug.PhotoDetectScreen
import net.moesli.scan7.debug.ScanDemoLaunch
import net.moesli.scan7.debug.ScanFlowDemoScreen
import net.moesli.scan7.ui.about.AboutScreen
import net.moesli.scan7.ui.about.LicenseText
import net.moesli.scan7.ui.about.LicenseTextScreen
import net.moesli.scan7.ui.gameover.GameOverScreen
import net.moesli.scan7.ui.gameover.toGameOverUi
import net.moesli.scan7.ui.history.ScoreboardScreen
import net.moesli.scan7.ui.history.toScoreboardUi
import net.moesli.scan7.ui.home.HomeRoute
import net.moesli.scan7.ui.newgame.GamePlayersRoute
import net.moesli.scan7.ui.newgame.NewGameRoute
import net.moesli.scan7.ui.players.PlayersRoute
import net.moesli.scan7.ui.round.BustToastState
import net.moesli.scan7.ui.round.RoundNavigator
import net.moesli.scan7.ui.round.RoundRoute
import net.moesli.scan7.ui.round.rememberBustToastState
import net.moesli.scan7.ui.scan.ScanCapture
import net.moesli.scan7.ui.scan.ScanRoute
import net.moesli.scan7.ui.theme.Scan7Theme
import androidx.compose.ui.res.stringResource
import net.moesli.scan7.R
import net.moesli.scan7.debug.MultiplayerDemoScreen
import net.moesli.scan7.ui.components.TextLinkButton
import net.moesli.scan7.ui.share.JoinRoute
import net.moesli.scan7.ui.share.SavePlayersDialog

/**
 * The app's back stack: a list of [Route]s with Home at the bottom, saved as strings so it
 * survives rotation and process death (screens reload their game from the database).
 */
@Stable
class BackStack(initial: List<Route>) {
    var entries: List<Route> by mutableStateOf(Stack.normalize(initial))
        private set

    val top: Route get() = entries.last()

    fun push(route: Route) {
        entries = Stack.push(entries, route)
    }

    /** Returns false on Home alone (the system then leaves the app). */
    fun pop(): Boolean {
        entries = Stack.pop(entries) ?: return false
        return true
    }

    fun replaceTop(route: Route) {
        entries = Stack.replaceTop(entries, route)
    }

    fun reset(vararg routes: Route) {
        entries = Stack.normalize(routes.toList())
    }

    companion object {
        val Saver = listSaver<BackStack, String>(
            save = { s -> s.entries.map { it.encode() } },
            restore = { saved -> BackStack(saved.mapNotNull(::decodeRoute)) },
        )
    }
}

/** DEBUG-only launch options from the activity intent (all `null` in release builds). */
class DebugLaunch(
    val photo: Uri? = null,
    val scanDemo: ScanDemoLaunch? = null,
    /** Camera scans in the real game flow start in Review on this photo (real detector). */
    val gamePhotoCapture: (suspend (Context) -> ScanCapture)? = null,
    /** Stack to open after `debug_seed` seeded the database (debug/DebugSeed.kt). */
    val seededStack: List<Route>? = null,
)

/** First stack for a fresh start: Home, or a debug screen when launched with a debug hook. */
fun initialStack(debug: DebugLaunch): List<Route> = when {
    debug.seededStack != null -> debug.seededStack
    debug.scanDemo != null -> listOf(Route.Home, Route.Debug(DebugScreen.HOME), Route.Debug(DebugScreen.SCAN_DEMO))
    debug.photo != null -> listOf(Route.Home, Route.Debug(DebugScreen.HOME), Route.Debug(DebugScreen.PHOTO))
    else -> listOf(Route.Home)
}

/**
 * Hosts the app's screens on a small self-made back stack (no navigation library): system back
 * pops (screens with their own back handling — camera, manual entry, arrange mode, dialogs — take
 * precedence), each entry keeps its saved UI state while it is on the stack.
 */
@Composable
fun AppNavHost(debug: DebugLaunch, modelInfo: String, joinLink: String? = null) {
    val stack = rememberSaveable(saver = BackStack.Saver) {
        BackStack(if (joinLink != null) listOf(Route.Home, Route.Join(joinLink)) else initialStack(debug))
    }
    val holder = rememberSaveableStateHolder()
    val keys = stack.entries.map { it.stateKey }.toSet()
    var previousKeys by remember { mutableStateOf(keys) }
    LaunchedEffect(keys) {
        (previousKeys - keys).forEach(holder::removeState)
        previousKeys = keys
    }

    // Registered before the screens' own handlers, so those win while enabled.
    BackHandler(enabled = stack.entries.size > 1) { stack.pop() }

    // One "<name> marked bust · Undo" toast for the round screen and the scan camera, so a camera
    // Bust tap can show it on the next player's camera or after returning to the round screen.
    val bustToast = rememberBustToastState()

    // Multiplayer guest: the host started the next game ("Same players, new game") → follow it.
    val context = LocalContext.current
    val share = remember { AppGraph.share(context) }
    val shareState by share.state.collectAsStateWithLifecycle()
    val joinedId = shareState.guest?.localGameId
    var lastJoinedId by remember { mutableStateOf(joinedId) }
    LaunchedEffect(joinedId) {
        val top = stack.top
        if (joinedId != null && lastJoinedId != null && joinedId != lastJoinedId &&
            ((top is Route.Round && top.gameId == lastJoinedId) || (top is Route.GameOver && top.gameId == lastJoinedId))
        ) {
            stack.reset(Route.Home, Route.Round(joinedId))
        }
        lastJoinedId = joinedId
    }

    val route = stack.top
    holder.SaveableStateProvider(route.stateKey) {
        Screen(route, stack, debug, modelInfo, bustToast)
    }
}

@Composable
private fun Screen(route: Route, stack: BackStack, debug: DebugLaunch, modelInfo: String, bustToast: BustToastState) {
    val back: () -> Unit = { stack.pop() }
    val shareRuntime = AppGraph.share(LocalContext.current)
    when (route) {
        Route.Home -> HomeRoute(
            onResume = { stack.push(Route.Round(it)) },
            onNewGame = { stack.push(Route.NewGame()) },
            onPlayers = { stack.push(Route.Players) },
            onDebug = { stack.push(Route.Debug(DebugScreen.HOME)) },
            onAbout = { stack.push(Route.About()) },
            // The live joined game opens its round screen; other past games their scoreboard.
            onPastGame = { if (it == shareRuntime.state.value.guest?.localGameId) stack.push(Route.Round(it)) else stack.push(Route.Scoreboard(it)) },
            // Multiplayer: with a radio (play flavor), or the in-process demo in debug builds.
            onJoin = if (shareRuntime.radioAvailable || BuildConfig.DEBUG) ({ stack.push(Route.Join()) }) else null,
            // Like Game over's "Same players": New game opens on Seats with that game's order and target.
            onSamePlayers = { seating, target -> stack.push(Route.NewGame(seating, target)) },
        )
        Route.Players -> PlayersRoute(onBack = back)
        is Route.NewGame -> NewGameRoute(
            onBack = back,
            onGameStarted = {
                stack.replaceTop(Route.Round(it))
                // "Same players, new game" while sharing: the guests move along.
                shareRuntime.hostMovedTo(it)
            },
            initialSeating = route.seating,
            initialTarget = route.target,
        )
        is Route.Round -> {
            val navigator = remember(route.gameId, stack) { roundNavigator(route.gameId, stack) }
            RoundRoute(gameId = route.gameId, navigator = navigator, toast = bustToast)
        }
        is Route.GamePlayers -> GamePlayersRoute(gameId = route.gameId, onDone = back, onBack = back)
        is Route.Scan -> ScanRoute(
            gameId = route.gameId,
            playerId = route.playerId,
            start = route.start,
            round = route.round,
            onSaved = { state, choice, entry -> route.afterSave(state, choice, entry)?.let(stack::replaceTop) ?: stack.pop() },
            onLeave = back,
            bustToast = bustToast,
            debugCapture = debug.gamePhotoCapture,
        )
        is Route.Scoreboard -> ScoreboardRoute(route.gameId, stack)
        is Route.GameOver -> GameOverRoute(route.gameId, stack)
        is Route.About -> when (val license = LicenseText.fromId(route.license)) {
            null -> AboutScreen(modelInfo = modelInfo, onBack = back, onLicense = { stack.push(Route.About(it.id)) })
            else -> LicenseTextScreen(license, onBack = back)
        }
        // Release builds have no debug menu (nothing pushes a debug route there; just in case: Home).
        is Route.Debug -> if (BuildConfig.DEBUG) DebugRoute(route.screen, stack, debug) else LaunchedEffect(Unit) { stack.reset(Route.Home) }
        is Route.Join -> JoinRoute(route.link, onBack = back, onJoined = { stack.replaceTop(Route.Round(it)) })
    }
}

private fun roundNavigator(gameId: Long, stack: BackStack) = object : RoundNavigator {
    override fun scan(playerId: Long, start: ScanStart, round: Int?) = stack.push(Route.Scan(gameId, playerId, start, round))

    override fun gameOver() = stack.reset(Route.Home, Route.GameOver(gameId))

    override fun scoreboard() = stack.push(Route.Scoreboard(gameId))

    override fun home() = stack.reset(Route.Home)

    override fun managePlayers() = stack.push(Route.GamePlayers(gameId))

    override fun join() = stack.push(Route.Join())
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize().background(Scan7Theme.colors.ground))
}

@Composable
private fun ScoreboardRoute(gameId: Long, stack: BackStack) {
    val repository = AppGraph.repository(LocalContext.current)
    val game by remember(gameId) { repository.observeGame(gameId) }.collectAsStateWithLifecycle(initialValue = null)
    val snap = game ?: return Loading()
    var saving by remember { mutableStateOf(false) }
    ScoreboardScreen(
        state = snap.toScoreboardUi(),
        // A joined game is a read-only copy.
        onEditResult = { round, playerId -> if (!snap.isJoined) stack.push(Route.Scan(gameId, playerId, ScanStart.EDIT, round)) },
        onBack = { stack.pop() },
        trailing = { if (snap.isJoined) TextLinkButton(stringResource(R.string.save_players_action), { saving = true }) },
    )
    if (saving) SavePlayersDialog(snap, onDone = { saving = false })
}

@Composable
private fun GameOverRoute(gameId: Long, stack: BackStack) {
    val repository = AppGraph.repository(LocalContext.current)
    val game by remember(gameId) { repository.observeGame(gameId) }.collectAsStateWithLifecycle(initialValue = null)
    BackHandler { stack.reset(Route.Home) }
    val snap = game ?: return Loading()
    if (!snap.state.isOver) {
        // An edit on the scoreboard took the game below the target again: back to the round.
        LaunchedEffect(gameId) { stack.replaceTop(Route.Round(gameId)) }
        return Loading()
    }
    GameOverScreen(
        state = snap.toGameOverUi(),
        // A joined game: only the host starts the next one (this phone follows it).
        onNewGameSamePlayers = if (snap.isJoined) null else ({ stack.reset(Route.Home, Route.NewGame(snap.state.playerIds, snap.state.target)) }),
        onHome = { stack.reset(Route.Home) },
        onScoreboard = { stack.push(Route.Scoreboard(gameId)) },
    )
}

@Composable
private fun DebugRoute(screen: DebugScreen, stack: BackStack, debug: DebugLaunch) {
    val back: () -> Unit = { stack.pop() }
    val open = { s: DebugScreen -> stack.push(Route.Debug(s)) }
    when (screen) {
        DebugScreen.HOME -> {
            val repository = AppGraph.repository(LocalContext.current)
            val scope = rememberCoroutineScope()
            val saveScans by repository.saveScansForTraining.collectAsStateWithLifecycle(initialValue = true)
            val home = rememberDebugHomeState()
            DebugHomeScreen(
                state = home.ui(saveScans),
                onBack = back,
                onLiveCamera = { open(DebugScreen.CAMERA) },
                onDetectFromPhoto = { open(DebugScreen.PHOTO) },
                onSaveScansChange = { on -> scope.launch { repository.setSaveScansForTraining(on) } },
                onLoadDetector = home::loadDetector,
                onAvatars = { open(DebugScreen.AVATARS) },
                onMultiplayer = { open(DebugScreen.MULTIPLAYER) },
            )
        }
        DebugScreen.CAMERA -> CameraScreen(onBack = back)
        DebugScreen.PHOTO -> PhotoDetectScreen(onBack = back, initialUri = debug.photo)
        DebugScreen.SCAN_DEMO -> ScanFlowDemoScreen(onBack = back, launch = debug.scanDemo)
        DebugScreen.AVATARS -> AvatarGalleryScreen(onBack = back)
        DebugScreen.MULTIPLAYER -> MultiplayerDemoScreen(
            onBack = back,
            onOpenRound = { stack.push(Route.Round(it)) },
            onJoinScreen = { stack.push(Route.Join()) },
        )
    }
}
