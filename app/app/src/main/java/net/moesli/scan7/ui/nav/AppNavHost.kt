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
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import net.moesli.scan7.BuildConfig
import net.moesli.scan7.R
import net.moesli.scan7.camera.CameraScreen
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.outlivingUi
import net.moesli.scan7.debug.AvatarGalleryScreen
import net.moesli.scan7.debug.DebugHomeScreen
import net.moesli.scan7.debug.MultiplayerDemoScreen
import net.moesli.scan7.debug.PhotoDetectScreen
import net.moesli.scan7.debug.ScanDemoLaunch
import net.moesli.scan7.debug.ScanFlowDemoScreen
import net.moesli.scan7.debug.rememberDebugHomeState
import net.moesli.scan7.ui.about.AboutScreen
import net.moesli.scan7.ui.about.LicenseText
import net.moesli.scan7.ui.about.LicenseTextScreen
import net.moesli.scan7.ui.components.TextLinkButton
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
import net.moesli.scan7.ui.share.JoinRoute
import net.moesli.scan7.ui.share.SavePlayersDialog
import net.moesli.scan7.ui.theme.Scan7Theme

/**
 * The app's back stack: Navigation 3's [NavBackStack] of [Route]s (Home at the bottom), changed
 * only through the pure [Stack] operations.
 */
@Stable
class BackStack(val entries: NavBackStack<Route>) {
    val top: Route get() = entries.last()

    fun push(route: Route) = set(Stack.push(entries.toList(), route))

    /** Returns false on Home alone (the system then leaves the app). */
    fun pop(): Boolean {
        set(Stack.pop(entries.toList()) ?: return false)
        return true
    }

    fun replaceTop(route: Route) = set(Stack.replaceTop(entries.toList(), route))

    fun reset(vararg routes: Route) = set(Stack.normalize(routes.toList()))

    /** Keeps the common bottom part (those entries keep their state), replaces the rest. */
    private fun set(stack: List<Route>) {
        val common = entries.zip(stack).takeWhile { (a, b) -> a == b }.size
        while (entries.size > common) entries.removeAt(entries.lastIndex)
        entries.addAll(stack.drop(common))
    }
}

/**
 * Navigation for one screen: acts only while its [route] is still the top entry. During a
 * transition the leaving screen is still drawn (and tappable); a second quick tap on its Back
 * must not pop the screen below as well.
 */
@Stable
private class EntryNav(private val stack: BackStack, private val route: Route) {
    private val current: Boolean get() = stack.top == route

    fun push(route: Route) {
        if (current) stack.push(route)
    }

    fun pop() {
        if (current) stack.pop()
    }

    fun replaceTop(route: Route) {
        if (current) stack.replaceTop(route)
    }

    fun reset(vararg routes: Route) {
        if (current) stack.reset(*routes)
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
 * Hosts the app's screens with Navigation 3: a [NavDisplay] on a saveable [NavBackStack] (saved
 * with the sealed [Route] serializer, so it survives rotation and process death; screens reload
 * their game from the database). System back pops with a predictive-back preview ([NavMotion]);
 * screens with their own back handling (camera, manual entry, arrange mode, sheets, dialogs)
 * take precedence. Each entry keeps its saved UI state and its own ViewModel store while it is
 * on the stack.
 */
@Composable
fun AppNavHost(debug: DebugLaunch, modelInfo: String, joinLink: String? = null) {
    val entries = rememberSerializable(serializer = NavBackStack.serializer(Route.serializer())) {
        val initial = if (joinLink != null) listOf(Route.Home, Route.Join(joinLink)) else initialStack(debug)
        NavBackStack(*Stack.normalize(initial).toTypedArray())
    }
    val stack = remember(entries) { BackStack(entries) }

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

    NavDisplay(
        backStack = entries,
        modifier = Modifier.fillMaxSize().background(Scan7Theme.colors.ground),
        onBack = { stack.pop() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = NavMotion.forward,
        popTransitionSpec = NavMotion.pop,
        predictivePopTransitionSpec = NavMotion.predictivePop,
        entryProvider = { route ->
            NavEntry(route, contentKey = route.stateKey, metadata = NavMotion.metadata(route)) { current ->
                // Opaque per screen: during a transition the other screen must not show through.
                Box(Modifier.fillMaxSize().background(Scan7Theme.colors.ground)) {
                    val nav = remember(stack, current) { EntryNav(stack, current) }
                    Screen(current, nav, debug, modelInfo, bustToast)
                }
            }
        },
    )
}

@Composable
private fun Screen(route: Route, nav: EntryNav, debug: DebugLaunch, modelInfo: String, bustToast: BustToastState) {
    val back: () -> Unit = nav::pop
    val shareRuntime = AppGraph.share(LocalContext.current)
    when (route) {
        Route.Home -> HomeRoute(
            onResume = { nav.push(Route.Round(it)) },
            onNewGame = { nav.push(Route.NewGame()) },
            onPlayers = { nav.push(Route.Players) },
            onDebug = { nav.push(Route.Debug(DebugScreen.HOME)) },
            onAbout = { nav.push(Route.About()) },
            // The live joined game opens its round screen; other past games their scoreboard.
            onPastGame = { if (it == shareRuntime.state.value.guest?.localGameId) nav.push(Route.Round(it)) else nav.push(Route.Scoreboard(it)) },
            // Multiplayer: with a radio (play flavor), or the in-process demo in debug builds.
            onJoin = if (shareRuntime.radioAvailable || BuildConfig.DEBUG) ({ nav.push(Route.Join()) }) else null,
            // Like Game over's "Same players": New game opens on Seats with that game's order and target.
            onSamePlayers = { g -> nav.push(Route.NewGame(g.lineup, g.target, g.kind, g.enderPenalty)) },
        )
        Route.Players -> PlayersRoute(onBack = back)
        is Route.NewGame -> NewGameRoute(
            onBack = back,
            onGameStarted = {
                nav.replaceTop(Route.Round(it))
                // "Same players, new game" while sharing: the guests move along.
                shareRuntime.hostMovedTo(it)
            },
            initialSeating = route.seating,
            initialTarget = route.target,
            initialKind = route.kind,
            initialPenalty = route.enderPenalty,
        )
        is Route.Round -> {
            val navigator = remember(route.gameId, nav) { roundNavigator(route.gameId, nav) }
            RoundRoute(gameId = route.gameId, navigator = navigator, toast = bustToast)
        }
        is Route.GamePlayers -> GamePlayersRoute(gameId = route.gameId, onDone = back, onBack = back)
        is Route.Scan -> ScanRoute(
            gameId = route.gameId,
            playerId = route.playerId,
            start = route.start,
            round = route.round,
            onSaved = { state, choice, entry -> route.afterSave(state, choice, entry)?.let(nav::replaceTop) ?: nav.pop() },
            onLeave = back,
            bustToast = bustToast,
            debugCapture = debug.gamePhotoCapture,
        )
        is Route.Scoreboard -> ScoreboardRoute(route.gameId, nav)
        is Route.GameOver -> GameOverRoute(route.gameId, nav)
        is Route.About -> when (val license = LicenseText.fromId(route.license)) {
            null -> AboutScreen(modelInfo = modelInfo, onBack = back, onLicense = { nav.push(Route.About(it.id)) })
            else -> LicenseTextScreen(license, onBack = back)
        }
        // Release builds have no debug menu (nothing pushes a debug route there; just in case: Home).
        is Route.Debug -> if (BuildConfig.DEBUG) DebugRoute(route.screen, nav, debug) else LaunchedEffect(Unit) { nav.reset(Route.Home) }
        is Route.Join -> JoinRoute(route.link, onBack = back, onJoined = { nav.replaceTop(Route.Round(it)) })
    }
}

private fun roundNavigator(gameId: Long, nav: EntryNav) = object : RoundNavigator {
    override fun scan(playerId: Long, start: ScanStart, round: Int?) = nav.push(Route.Scan(gameId, playerId, start, round))

    override fun gameOver() = nav.reset(Route.Home, Route.GameOver(gameId))

    override fun scoreboard() = nav.push(Route.Scoreboard(gameId))

    override fun home() = nav.reset(Route.Home)

    override fun managePlayers() = nav.push(Route.GamePlayers(gameId))

    override fun join() = nav.push(Route.Join())
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize().background(Scan7Theme.colors.ground))
}

@Composable
private fun ScoreboardRoute(gameId: Long, nav: EntryNav) {
    val repository = AppGraph.repository(LocalContext.current)
    val game by remember(gameId) { repository.observeGame(gameId) }.collectAsStateWithLifecycle(initialValue = null)
    val snap = game ?: return Loading()
    var saving by remember { mutableStateOf(false) }
    ScoreboardScreen(
        state = snap.toScoreboardUi(),
        // A joined game is a read-only copy.
        onEditResult = { round, playerId -> if (!snap.isJoined) nav.push(Route.Scan(gameId, playerId, ScanStart.EDIT, round)) },
        onBack = nav::pop,
        trailing = { if (snap.isJoined) TextLinkButton(stringResource(R.string.save_players_action), { saving = true }) },
    )
    if (saving) SavePlayersDialog(snap, onDone = { saving = false })
}

@Composable
private fun GameOverRoute(gameId: Long, nav: EntryNav) {
    val repository = AppGraph.repository(LocalContext.current)
    val game by remember(gameId) { repository.observeGame(gameId) }.collectAsStateWithLifecycle(initialValue = null)
    // Back from Game over goes Home (the stack below may be gone after a reset anyway).
    BackHandler { nav.reset(Route.Home) }
    val snap = game ?: return Loading()
    if (!snap.state.isOver) {
        // An edit on the scoreboard took the game below the target again: back to the round.
        LaunchedEffect(gameId) { nav.replaceTop(Route.Round(gameId)) }
        return Loading()
    }
    GameOverScreen(
        state = snap.toGameOverUi(),
        // A joined game: only the host starts the next one (this phone follows it).
        onNewGameSamePlayers = if (snap.isJoined) null else ({ nav.reset(Route.Home, Route.NewGame.samePlayers(snap.state)) }),
        onHome = { nav.reset(Route.Home) },
        onScoreboard = { nav.push(Route.Scoreboard(gameId)) },
    )
}

@Composable
private fun DebugRoute(screen: DebugScreen, nav: EntryNav, debug: DebugLaunch) {
    val back: () -> Unit = nav::pop
    val open = { s: DebugScreen -> nav.push(Route.Debug(s)) }
    when (screen) {
        DebugScreen.HOME -> {
            val repository = AppGraph.repository(LocalContext.current)
            val scope = rememberCoroutineScope()
            val saveScans by repository.saveScansForTraining.collectAsStateWithLifecycle(initialValue = true)
            val skyjo by repository.skyjoPreview.collectAsStateWithLifecycle(initialValue = false)
            val home = rememberDebugHomeState()
            DebugHomeScreen(
                state = home.ui(saveScans, skyjo),
                onBack = back,
                onLiveCamera = { open(DebugScreen.CAMERA) },
                onDetectFromPhoto = { open(DebugScreen.PHOTO) },
                onSaveScansChange = { on -> scope.launch { runCatching { outlivingUi { repository.setSaveScansForTraining(on) } } } },
                onSkyjoPreviewChange = { on -> scope.launch { runCatching { outlivingUi { repository.setSkyjoPreview(on) } } } },
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
            onOpenRound = { nav.push(Route.Round(it)) },
            onJoinScreen = { nav.push(Route.Join()) },
        )
    }
}
