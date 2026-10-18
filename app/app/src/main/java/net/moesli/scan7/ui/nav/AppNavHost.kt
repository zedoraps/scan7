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
import net.moesli.scan7.camera.CameraScreen
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.debug.ComponentGalleryScreen
import net.moesli.scan7.debug.DebugHomeScreen
import net.moesli.scan7.debug.rememberDebugHomeState
import net.moesli.scan7.debug.PhotoDetectScreen
import net.moesli.scan7.debug.ScanDemoLaunch
import net.moesli.scan7.debug.ScanFlowDemoScreen
import net.moesli.scan7.debug.ScoreTesterScreen
import net.moesli.scan7.ui.gameover.GameOverScreen
import net.moesli.scan7.ui.gameover.toGameOverUi
import net.moesli.scan7.ui.history.ScoreboardScreen
import net.moesli.scan7.ui.history.toScoreboardUi
import net.moesli.scan7.ui.home.HomeRoute
import net.moesli.scan7.ui.newgame.NewGameRoute
import net.moesli.scan7.ui.players.PlayersRoute
import net.moesli.scan7.ui.round.RoundNavigator
import net.moesli.scan7.ui.round.RoundRoute
import net.moesli.scan7.ui.round.scanNextAfter
import net.moesli.scan7.ui.scan.ScanCapture
import net.moesli.scan7.ui.scan.ScanRoute
import net.moesli.scan7.ui.theme.Scan7Theme

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
)

/** First stack for a fresh start: Home, or a debug screen when launched with a debug hook. */
fun initialStack(debug: DebugLaunch): List<Route> = when {
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
fun AppNavHost(debug: DebugLaunch, modelInfo: String) {
    val stack = rememberSaveable(saver = BackStack.Saver) { BackStack(initialStack(debug)) }
    val holder = rememberSaveableStateHolder()
    val keys = stack.entries.map { it.stateKey }.toSet()
    var previousKeys by remember { mutableStateOf(keys) }
    LaunchedEffect(keys) {
        (previousKeys - keys).forEach(holder::removeState)
        previousKeys = keys
    }

    // Registered before the screens' own handlers, so those win while enabled.
    BackHandler(enabled = stack.entries.size > 1) { stack.pop() }

    val route = stack.top
    holder.SaveableStateProvider(route.stateKey) {
        Screen(route, stack, debug, modelInfo)
    }
}

@Composable
private fun Screen(route: Route, stack: BackStack, debug: DebugLaunch, modelInfo: String) {
    val back: () -> Unit = { stack.pop() }
    when (route) {
        Route.Home -> HomeRoute(
            onResume = { stack.push(Route.Round(it)) },
            onNewGame = { stack.push(Route.NewGame()) },
            onPlayers = { stack.push(Route.Players) },
            onDebug = { stack.push(Route.Debug(DebugScreen.HOME)) },
            modelInfo = modelInfo,
        )
        Route.Players -> PlayersRoute(onBack = back)
        is Route.NewGame -> NewGameRoute(
            onBack = back,
            onGameStarted = { stack.replaceTop(Route.Round(it)) },
            initialSeating = route.seating,
            initialTarget = route.target,
        )
        is Route.Round -> {
            val navigator = remember(route.gameId, stack) { roundNavigator(route.gameId, stack) }
            RoundRoute(gameId = route.gameId, navigator = navigator)
        }
        is Route.Scan -> ScanRoute(
            gameId = route.gameId,
            playerId = route.playerId,
            start = route.start,
            round = route.round,
            onSaved = { state ->
                val next = if (route.start == ScanStart.NEXT) state.scanNextAfter(route.playerId) else null
                if (next != null) stack.replaceTop(route.copy(playerId = next)) else stack.pop()
            },
            onLeave = back,
            debugCapture = debug.gamePhotoCapture,
        )
        is Route.Scoreboard -> ScoreboardRoute(route.gameId, stack)
        is Route.GameOver -> GameOverRoute(route.gameId, stack)
        is Route.Debug -> DebugRoute(route.screen, stack, debug)
    }
}

private fun roundNavigator(gameId: Long, stack: BackStack) = object : RoundNavigator {
    override fun scan(playerId: Long, start: ScanStart, round: Int?) = stack.push(Route.Scan(gameId, playerId, start, round))

    override fun gameOver() = stack.reset(Route.Home, Route.GameOver(gameId))

    override fun scoreboard() = stack.push(Route.Scoreboard(gameId))

    override fun home() = stack.reset(Route.Home)
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
    ScoreboardScreen(
        state = snap.toScoreboardUi(),
        onEditResult = { round, playerId -> stack.push(Route.Scan(gameId, playerId, ScanStart.EDIT, round)) },
        onBack = { stack.pop() },
    )
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
        onNewGameSamePlayers = { stack.reset(Route.Home, Route.NewGame(snap.state.playerIds, snap.state.target)) },
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
                onScoreTester = { open(DebugScreen.SCORE_TESTER) },
                onComponentGallery = { open(DebugScreen.GALLERY) },
                onSaveScansChange = { on -> scope.launch { repository.setSaveScansForTraining(on) } },
                onLoadDetector = home::loadDetector,
            )
        }
        DebugScreen.CAMERA -> CameraScreen(onBack = back)
        DebugScreen.SCORE_TESTER -> ScoreTesterScreen(onBack = back)
        DebugScreen.PHOTO -> PhotoDetectScreen(onBack = back, initialUri = debug.photo)
        DebugScreen.GALLERY -> ComponentGalleryScreen(onBack = back)
        DebugScreen.SCAN_DEMO -> ScanFlowDemoScreen(onBack = back, launch = debug.scanDemo)
    }
}
