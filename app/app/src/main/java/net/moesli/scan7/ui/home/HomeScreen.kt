package net.moesli.scan7.ui.home

import net.moesli.scan7.ui.text.uiPlural
import net.moesli.scan7.ui.text.uiText
import net.moesli.scan7.ui.text.resolve
import net.moesli.scan7.ui.text.UiText
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import net.moesli.scan7.game.AvatarCatalog
import net.moesli.scan7.ui.components.ContentWithBottomBar
import net.moesli.scan7.ui.components.avatarSizeForHeight
import net.moesli.scan7.ui.components.PlayerAvatar
import android.os.SystemClock
import android.widget.Toast
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.DpOffset
import kotlinx.coroutines.launch
import net.moesli.scan7.data.outlivingUi
import net.moesli.scan7.ui.components.Confirmations
import net.moesli.scan7.ui.components.Scan7ConfirmDialog
import net.moesli.scan7.ui.components.ChevronRightIcon
import net.moesli.scan7.ui.components.ScoreboardIcon
import net.moesli.scan7.ui.components.WarningIcon
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.flow.map
import net.moesli.scan7.ui.components.scan7Clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.Alignment
import net.moesli.scan7.ui.components.TextLinkButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import net.moesli.scan7.BuildConfig
import net.moesli.scan7.R
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.ui.components.LeaderBadge
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.components.headerInsets
import net.moesli.scan7.ui.components.openCardShadow
import net.moesli.scan7.ui.players.playerUis
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type
import net.moesli.scan7.ui.theme.Scan7Wordmark

/** The unfinished game shown on Home. [players] in seating order; [leaders] empty while nobody has points. */
data class ResumeGameUi(
    val gameId: Long,
    val round: Int,
    val target: Int,
    val players: List<PlayerUi>,
    val leaders: List<PlayerUi>,
    val startedAt: Long = 0,
) {
    /** "Mia leads · 46", "Ben and Mia lead · 46", "No points yet". */
    val leaderText: UiText
        get() = when {
            leaders.isEmpty() -> uiText(R.string.home_no_points)
            leaders.size == 1 -> uiText(R.string.home_leader_one, leaders[0].name, leaders[0].total)
            else -> uiText(R.string.home_leader_many, UiText.Names(leaders.map { it.name }), leaders[0].total)
        }
}

/**
 * Home (1) state. [past] = finished / abandoned games, newest first; [now] for their relative
 * dates. [version] e.g. "Scan7 1.0 (1)" (first line of the ⋮ menu).
 */
data class HomeUiState(
    val resume: ResumeGameUi?,
    val playerCount: Int,
    val version: String,
    val past: List<PastGameUi> = emptyList(),
    val now: Long = 0,
)

/** Resume card data for the repository's active game. */
fun GameSnapshot.toResumeUi(): ResumeGameUi {
    val uis = state.playerUis(players)
    return ResumeGameUi(
        gameId = state.id,
        round = state.currentRound.number,
        target = state.target,
        players = uis,
        leaders = uis.filter { it.isLeader },
        startedAt = state.startedAt,
    )
}

/** Resume card avatars: 27 dp tall (kit: 24–36 dp in bars). */
private val ResumeAvatar = avatarSizeForHeight(27.dp)

/** History row avatars: 22 dp tall, so 10 players fit one line on a 360 dp phone. */
private val PastAvatar = avatarSizeForHeight(22.dp)
private val HeroAvatar = avatarSizeForHeight(58.dp)

/**
 * Home (1) = game history: wordmark, Resume card (round, avatars, leader) when a game is
 * unfinished, then "Past games" (finished / abandoned, newest first: date, avatars, winner +
 * points, rounds; tap = [onPastGame], that game's scoreboard) in one `LazyColumn`. Players
 * (secondary) + New game (primary) float at the bottom. The ⋮ top right ([HomeMenuButton]) shows
 * the version, About & licenses ([onAbout]) and the app language. The debug menu (debug builds
 * only) is hidden: tap the wordmark [DEBUG_TAPS] times (TalkBack: custom action "Open debug menu").
 * Without any game: a short intro; without players it says New game starts by adding them.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onResume: (gameId: Long) -> Unit,
    onNewGame: () -> Unit,
    onPlayers: () -> Unit,
    onDebug: () -> Unit,
    onPastGame: (gameId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onAbout: () -> Unit = {},
    onDeleteGame: (gameId: Long) -> Unit = {},
    /** Multiplayer "Join a game"; `null` hides it (no radio in this build). */
    onJoin: (() -> Unit)? = null,
) {
    val colors = Scan7Theme.colors
    val dates = rememberHomeDateFormat()
    // The game whose "Delete game" confirmation is open (survives rotation).
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    pendingDelete?.let { id -> DeleteGameDialog(state, id, dates, onDelete = onDeleteGame, onDismiss = { pendingDelete = null }) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .headerInsets()
                .fillMaxWidth()
                .height(Scan7Spacing.headerHeight),
        ) {
            Scan7Wordmark(fontSize = 32.sp, modifier = if (BuildConfig.DEBUG) Modifier.debugTaps(onDebug) else Modifier)
            Spacer(Modifier.weight(1f))
            HomeMenuButton(version = state.version, onAbout = onAbout)
        }
        ContentWithBottomBar(
            bottomBar = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(stringResource(R.string.home_players), onPlayers, Modifier.width(128.dp))
                        PrimaryButton(stringResource(R.string.action_new_game), onNewGame, Modifier.weight(1f))
                    }
                    if (onJoin != null) TextLinkButton(stringResource(R.string.home_join), onJoin)
                }
            },
        ) { bottomPadding ->
            val listState = rememberLazyListState()
            // The Resume card can arrive after the history (separate flows): LazyColumn would keep
            // the first past game in place and open scrolled past the card, so go back to the top.
            val hasResume = state.resume != null
            LaunchedEffect(hasResume) {
                if (hasResume && listState.firstVisibleItemIndex <= 2) listState.scrollToItem(0)
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = Scan7Spacing.xl, bottom = Scan7Spacing.xl + bottomPadding),
                verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
                modifier = Modifier.fillMaxSize(),
            ) {
                val resume = state.resume
                when {
                    resume != null -> item(key = "resume") {
                        ResumeCard(
                            resume,
                            onResume = { onResume(resume.gameId) },
                            onDelete = { pendingDelete = resume.gameId },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    state.past.isEmpty() -> item(key = "intro") { Intro(hasPlayers = state.playerCount > 0) }
                }
                if (state.past.isNotEmpty()) {
                    item(key = "past-label") {
                        SectionLabel(
                            stringResource(R.string.home_past_games),
                            Modifier
                                .padding(top = if (resume != null) Scan7Spacing.l else 0.dp, bottom = Scan7Spacing.xxs)
                                .semantics { heading() },
                        )
                    }
                    items(state.past, key = { it.gameId }) { game ->
                        PastGameRow(
                            game,
                            state.now,
                            dates,
                            onClick = { onPastGame(game.gameId) },
                            onDelete = { pendingDelete = game.gameId },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hidden debug entry on the wordmark: [DEBUG_TAPS] taps within [DEBUG_TAP_WINDOW_MS] open it, with
 * a countdown toast from the [DEBUG_TAPS_BEFORE_HINT]rd tap (like Android's "Build number"). No
 * ripple and no click semantics (the wordmark stays a plain text for TalkBack), but a custom
 * accessibility action "Open debug menu".
 */
private fun Modifier.debugTaps(onDebug: () -> Unit): Modifier = composed {
    val context = LocalContext.current
    val counter = remember { DebugTapCounter() }
    val toast = remember { arrayOfNulls<Toast>(1) }
    val open by rememberUpdatedState(onDebug)
    val actionLabel = stringResource(R.string.home_debug_action)
    DisposableEffect(Unit) { onDispose { toast[0]?.cancel() } }
    pointerInput(Unit) {
        detectTapGestures {
            toast[0]?.cancel()
            when (val r = counter.tap(SystemClock.uptimeMillis())) {
                DebugTapResult.None -> Unit
                is DebugTapResult.Countdown -> {
                    toast[0] = Toast.makeText(context, r.message, Toast.LENGTH_SHORT).also { it.show() }
                }
                DebugTapResult.Open -> {
                    toast[0] = null
                    open()
                }
            }
        }
    }.semantics {
        customActions = listOf(CustomAccessibilityAction(actionLabel) { open(); true })
    }
}

/**
 * The unfinished game. Tap anywhere = [onResume] (like the Resume button); long-press = the game
 * menu (Resume, Delete game → [onDelete] opens the confirmation). TalkBack: the card reads as one
 * button with the custom action "Delete game".
 */
@Composable
private fun ResumeCard(game: ResumeGameUi, onResume: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.cardLarge)
    val leaderText = game.leaderText.resolve()
    val description = pluralStringResource(R.plurals.home_resume_description, game.players.size, game.players.size, game.round, leaderText)
    var menu by remember { mutableStateOf(false) }
    val resumeLabel = stringResource(R.string.home_game_resume)
    val deleteLabel = stringResource(R.string.home_game_delete)
    Box(modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
            modifier = Modifier
                .fillMaxWidth()
                .openCardShadow(shape)
                .clip(shape)
                .background(colors.surface, shape)
                .border(1.5.dp, colors.line, shape)
                .gameLongPress(onClick = onResume, clickLabel = resumeLabel, onLongPress = { menu = true })
                .semantics { customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true }) }
                .padding(Scan7Spacing.l),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = description
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(stringResource(R.string.home_resume_title), Modifier.weight(1f))
                    Text(stringResource(R.string.target_short, game.target), color = colors.textMuted, style = Scan7Type.text12)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.round_label), color = colors.textMuted, style = Scan7Type.capsLabel, modifier = Modifier.padding(bottom = 4.dp))
                    Text(game.round.toString(), color = colors.text, style = Scan7Type.roundNumberList)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs)) {
                    game.players.forEach { p ->
                        PlayerAvatar(p.avatar, ResumeAvatar)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    if (game.leaders.isNotEmpty()) LeaderBadge(size = 20.dp)
                    Text(leaderText, color = colors.text, style = Scan7Type.text13)
                }
            }
            PrimaryButton(
                stringResource(R.string.home_resume),
                onResume,
                Modifier.fillMaxWidth(),
                height = Scan7Spacing.minTouch + Scan7Spacing.s,
                cornerRadius = Scan7Radii.iconButton,
            )
        }
        GameMenu(
            expanded = menu,
            onDismiss = { menu = false },
            openLabel = resumeLabel,
            openIcon = { ChevronRightIcon(it, size = 20.dp) },
            onOpen = onResume,
            onDelete = onDelete,
        )
    }
}

/**
 * One past game: an M3 `ListItem` on the done surface (outlined card, press dim): date over the
 * winner headline with the leader badge, avatars + rounds below, the winning points on the right.
 * Abandoned games are muted ("Mia led", no badge). TalkBack reads one sentence + "Open scoreboard".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PastGameRow(game: PastGameUi, now: Long, dates: HomeDateFormat, onClick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    var menu by remember { mutableStateOf(false) }
    val openLabel = stringResource(R.string.home_game_open_scoreboard)
    val deleteLabel = stringResource(R.string.home_game_delete)
    val shape = RoundedCornerShape(Scan7Radii.card)
    val won = game.outcome != PastOutcome.ABANDONED && game.winners.isNotEmpty()
    val date = historyDate(game.finishedAt, now, dates::time, dates::date).resolve()
    val headline = game.headline.resolve()
    val detail = game.detail.resolve()
    val withPoints = if (game.winners.isEmpty()) {
        headline
    } else {
        stringResource(R.string.history_with_points, headline, pluralStringResource(R.plurals.points, game.points, game.points))
    }
    val players = pluralStringResource(R.plurals.history_players, game.players.size, game.players.size, UiText.Names(game.players.map { it.name }).resolve())
    val description = listOf(date, withPoints, detail, players).joinToString(", ")
    Box(modifier) {
        ListItem(
            overlineContent = { Text(date, style = Scan7Type.text12, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            headlineContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    if (won) LeaderBadge(size = 20.dp)
                    Text(headline, style = Scan7Type.text15, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xs), modifier = Modifier.padding(top = Scan7Spacing.xs)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        game.players.forEach { PlayerAvatar(it.avatar, PastAvatar) }
                    }
                    Text(detail, style = Scan7Type.text12, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            trailingContent = if (game.winners.isEmpty()) {
                null
            } else {
                {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(game.points.toString(), color = if (won) colors.text else colors.textMuted, style = Scan7Type.total18, maxLines = 1)
                        Text(stringResource(R.string.points_short), color = colors.textMuted, style = Scan7Type.tag10, maxLines = 1)
                    }
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = colors.surfaceDone,
                headlineColor = if (won) colors.text else colors.textMuted,
                overlineColor = colors.textMuted,
                supportingColor = colors.textMuted,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, colors.lineSubtle, shape)
                .gameLongPress(onClick = onClick, clickLabel = openLabel, onLongPress = { menu = true })
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick(label = openLabel) { onClick(); true }
                    // A long-press isn't discoverable with TalkBack: offer the menu's action directly.
                    customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
                },
        )
        GameMenu(
            expanded = menu,
            onDismiss = { menu = false },
            openLabel = openLabel,
            openIcon = { ScoreboardIcon(it) },
            onOpen = onClick,
            onDelete = onDelete,
        )
    }
}

/** Tap = [onClick]; long-press = [onLongPress] with the platform's long-press haptic. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.gameLongPress(onClick: () -> Unit, clickLabel: String, onLongPress: () -> Unit): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    combinedClickable(
        role = Role.Button,
        onClickLabel = clickLabel,
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongPress()
        },
        onClick = onClick,
    )
}

/**
 * Long-press menu of a Home game: an M3 [DropdownMenu] anchored at the row, restyled like the
 * scan split-button menu (`surface`, line border, radius 12, no shadow) with round-menu rows
 * (text 15, >= 56 dp, leading icons): [openLabel] (Open scoreboard / Resume) and "Delete game" in
 * the warning style (→ [onDelete], the confirmation). Both close the menu first.
 */
@Composable
private fun GameMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    openLabel: String,
    openIcon: (@Composable (Color) -> Unit)?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Scan7Theme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(Scan7Spacing.l, -Scan7Spacing.s),
        shape = RoundedCornerShape(Scan7Radii.iconButton),
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.5.dp, colors.line),
        modifier = Modifier.widthIn(min = 220.dp),
    ) {
        GameMenuItem(openLabel, colors.text, openIcon) {
            onDismiss()
            onOpen()
        }
        GameMenuItem(stringResource(R.string.home_game_delete), colors.warning, { WarningIcon(it, size = 20.dp) }) {
            onDismiss()
            onDelete()
        }
    }
}

@Composable
private fun GameMenuItem(label: String, ink: Color, icon: (@Composable (Color) -> Unit)?, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = Scan7Type.text15, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        leadingIcon = icon?.let { { it(ink) } },
        colors = MenuDefaults.itemColors(textColor = ink, leadingIconColor = ink),
        contentPadding = PaddingValues(horizontal = Scan7Spacing.l),
        modifier = Modifier.heightIn(min = 56.dp),
    )
}

/**
 * "Delete this game?" for game [gameId] (a past game or the running one), via
 * [Confirmations.deleteGame]: date · winner / leader · rounds, then "This can't be undone." Closes
 * itself when the game is gone.
 */
@Composable
private fun DeleteGameDialog(state: HomeUiState, gameId: Long, dates: HomeDateFormat, onDelete: (Long) -> Unit, onDismiss: () -> Unit) {
    val resume = state.resume?.takeIf { it.gameId == gameId }
    val past = state.past.firstOrNull { it.gameId == gameId }
    val summary = when {
        resume != null -> listOf(
            historyDate(resume.startedAt, state.now, dates::time, dates::date),
            uiText(R.string.home_game_round, resume.round),
            resume.leaderText,
        )
        past != null -> listOf(
            historyDate(past.finishedAt, state.now, dates::time, dates::date),
            past.headline,
            uiPlural(R.plurals.home_game_rounds, past.rounds),
        )
        else -> {
            LaunchedEffect(gameId) { onDismiss() }
            return
        }
    }
    val spec = Confirmations.deleteGame(summary, running = resume != null)
    Scan7ConfirmDialog(
        title = spec.title.resolve(),
        confirmLabel = spec.confirmLabel.resolve(),
        onConfirm = {
            onDismiss()
            onDelete(gameId)
        },
        onCancel = onDismiss,
        body = spec.body?.resolve(),
        destructive = spec.destructive,
        cancelLabel = stringResource(R.string.confirm_delete_game_cancel),
    )
}

@Composable
private fun Intro(hasPlayers: Boolean) {
    val colors = Scan7Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l), modifier = Modifier.fillMaxWidth()) {
        TokenFan(Modifier.padding(vertical = Scan7Spacing.xl).align(Alignment.CenterHorizontally))
        Text(
            stringResource(R.string.home_intro_title),
            color = colors.text,
            style = Scan7Type.total24,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(if (hasPlayers) R.string.home_intro_players else R.string.home_intro_no_players),
            color = colors.textMuted,
            style = Scan7Type.text13,
        )
    }
}

/** Five of the kit's example avatars fanned out (decorative). */
@Composable
private fun TokenFan(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(176.dp, 80.dp).clearAndSetSemantics {}) {
        val angles = listOf(-20f, -10f, 0f, 10f, 20f)
        angles.forEachIndexed { i, a ->
            PlayerAvatar(
                avatar = AvatarCatalog.examples[i].second,
                size = HeroAvatar,
                modifier = Modifier
                    .offset(x = (24 + i * 26).dp, y = (10 + kotlin.math.abs(i - 2) * 4).dp)
                    .rotate(a + 8f),
            )
        }
    }
}

/** Home wired to the real [GameRepository] (active game, player count) and the model info. */
@Composable
fun HomeRoute(
    onResume: (gameId: Long) -> Unit,
    onNewGame: () -> Unit,
    onPlayers: () -> Unit,
    onDebug: () -> Unit,
    onPastGame: (gameId: Long) -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
    repository: GameRepository = AppGraph.repository(LocalContext.current),
    onJoin: (() -> Unit)? = null,
) {
    val active by repository.activeGame.collectAsStateWithLifecycle(initialValue = null)
    val players by repository.players.collectAsStateWithLifecycle(initialValue = emptyList())
    val pastFlow = remember(repository) { repository.pastGames.map { list -> list.map { it.toPastGameUi() } } }
    val past by pastFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    HomeScreen(
        state = HomeUiState(
            resume = active?.toResumeUi(),
            playerCount = players.size,
            version = stringResource(R.string.home_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            past = past,
            // Relative dates ("Today") only need to be right when Home is (re)composed.
            now = System.currentTimeMillis(),
        ),
        onResume = onResume,
        onNewGame = onNewGame,
        onPlayers = onPlayers,
        onDebug = onDebug,
        onPastGame = onPastGame,
        modifier = modifier,
        onAbout = onAbout,
        onJoin = onJoin,
        onDeleteGame = { id ->
            scope.launch {
                runCatching { outlivingUi { repository.deleteGame(id) } }.onFailure { Log.w("HomeRoute", "delete game $id failed", it) }
            }
        },
    )
}

internal val SampleResume = ResumeGameUi(
    gameId = 1,
    round = 4,
    target = 200,
    players = net.moesli.scan7.ui.components.samplePlayers(4),
    leaders = net.moesli.scan7.ui.components.samplePlayers(4).filter { it.isLeader },
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeResumePreview() {
    Scan7Theme {
        HomeScreen(HomeUiState(SampleResume, 4, "Scan7 1.0 (1)"), {}, {}, {}, {}, {})
    }
}

private val SamplePast = run {
    val p = net.moesli.scan7.ui.components.samplePlayers(4)
    val day = 86_400_000L
    listOf(
        PastGameUi(5, 9 * day, p, emptyList(), 0, 0, 200, PastOutcome.ABANDONED),
        PastGameUi(4, 7 * day, p, listOf(p[3]), 132, 4, 200, PastOutcome.ENDED_EARLY),
        PastGameUi(3, 3 * day, p.take(3), listOf(p[0], p[2]), 205, 6, 200, PastOutcome.WON),
        PastGameUi(2, 1 * day, p, listOf(p[1]), 214, 7, 200, PastOutcome.WON),
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeHistoryPreview() {
    Scan7Theme {
        HomeScreen(HomeUiState(SampleResume, 4, "Scan7 1.0 (1)", SamplePast, 10 * 86_400_000L), {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeEmptyPreview() {
    Scan7Theme {
        HomeScreen(HomeUiState(null, 0, "Scan7 1.0 (1)"), {}, {}, {}, {}, {})
    }
}
