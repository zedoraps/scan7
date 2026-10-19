package net.moesli.scan7.ui.players

import androidx.compose.ui.res.pluralStringResource
import net.moesli.scan7.ui.text.resolve
import androidx.compose.ui.res.stringResource
import net.moesli.scan7.R
import androidx.compose.ui.semantics.clearAndSetSemantics
import net.moesli.scan7.ui.text.avatarDescription
import net.moesli.scan7.ui.components.ContentWithBottomBar
import net.moesli.scan7.ui.components.avatarSizeForHeight
import net.moesli.scan7.ui.components.PlayerAvatar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.game.Player
import net.moesli.scan7.ui.components.ChevronRightIcon
import net.moesli.scan7.ui.components.PlusIcon
import net.moesli.scan7.ui.components.PressDim
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.RowSize
import net.moesli.scan7.ui.components.ScreenHeader
import net.moesli.scan7.ui.components.bottomButtonsInsets
import net.moesli.scan7.ui.components.dashedBorder
import net.moesli.scan7.ui.components.headerInsets
import net.moesli.scan7.ui.components.openCardShadow
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.components.centeredMaxWidth
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * Players list (2): stored players (avatar + name), tap → editor; Add player at the bottom.
 * Empty state: "Add your first player".
 */
@Composable
fun PlayersScreen(
    players: List<Player>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Player) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .centeredMaxWidth()
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        ScreenHeader(
            title = stringResource(R.string.players_title),
            subtitle = if (players.isEmpty()) null else pluralStringResource(R.plurals.players_subtitle, players.size, players.size),
            onBack = onBack,
            modifier = Modifier.headerInsets(),
        )
        ContentWithBottomBar(
            bottomBar = { PrimaryButton(stringResource(R.string.players_add), onAdd, Modifier.fillMaxWidth()) },
        ) { bottomPadding ->
            if (players.isEmpty()) {
                EmptyPlayers(Modifier.fillMaxSize().padding(bottom = bottomPadding))
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = Scan7Spacing.l, bottom = Scan7Spacing.l + bottomPadding),
                ) {
                    players.forEach { p ->
                        SetupPlayerRow(p, onClick = { onEdit(p) }, onClickLabel = stringResource(R.string.action_edit)) {
                            ChevronRightIcon(colors.textMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPlayers(modifier: Modifier) {
    val colors = Scan7Theme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m, Alignment.CenterVertically),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.players_add_first), color = colors.text, style = Scan7Type.headline16, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.players_empty_body),
            color = colors.textMuted,
            style = Scan7Type.text13,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A stored player as a 72 dp row in the card styling: avatar, name, [trailing] content. [active]
 * rows pop (open-card look); inactive ones recede (done-card look). Used by Players and New game.
 * With [checked] the row is a checkbox (toggleable, [Role.Checkbox]; [onClick] toggles it).
 */
@Composable
fun SetupPlayerRow(
    player: Player,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    supporting: String? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.cardLarge)
    val size = RowSize.Regular
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(size.height)
            .alpha(if (enabled) 1f else 0.4f)
            .then(if (active) Modifier.openCardShadow(shape) else Modifier)
            .clip(shape)
            .background(if (active) colors.surface else colors.surfaceDone)
            .border(1.5.dp, if (active) colors.line else colors.lineSubtleDone, shape)
            .then(
                when {
                    onClick == null -> Modifier
                    // Checkbox row: real toggle state, so TalkBack says "checked / not checked" itself.
                    checked != null -> Modifier.toggleable(
                        value = checked,
                        interactionSource = null,
                        indication = PressDim,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { onClick() },
                    )
                    else -> Modifier.scan7Clickable(onClick, enabled, role, onClickLabel)
                },
            )
            .padding(horizontal = Scan7Spacing.l),
    ) {
        leading?.invoke(this)
        // "Mia, sand avatar, grin face": the avatar's description already says the name.
        PlayerAvatar(
            player.avatar,
            avatarSizeForHeight(size.token.height),
            contentDescription = avatarDescription(player.name, player.avatar).resolve(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                player.name,
                color = if (active) colors.text else colors.textDone,
                style = Scan7Type.text15,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {},
            )
            if (supporting != null) {
                Text(supporting, color = colors.textMuted, style = Scan7Type.text12, maxLines = 2)
            }
        }
        trailing()
    }
}

/** Dashed "+ New player" row (same height as [SetupPlayerRow]). */
@Composable
fun AddPlayerRow(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, text: String = stringResource(R.string.players_new)) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.cardLarge)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(RowSize.Regular.height)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .dashedBorder(1.5.dp, colors.openOutline, Scan7Radii.cardLarge)
            .scan7Clickable(onClick, enabled)
            .padding(horizontal = Scan7Spacing.l),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(avatarSizeForHeight(RowSize.Regular.token.height))) {
            PlusIcon(colors.accent)
        }
        Text(text, color = colors.accent, style = Scan7Type.text15)
    }
}

/** Players (2) + editor (2a) wired to the real [GameRepository]. */
@Composable
fun PlayersRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    repository: GameRepository = AppGraph.repository(LocalContext.current),
) {
    val players by repository.players.collectAsStateWithLifecycle(initialValue = null)
    val active by repository.activeGame.collectAsStateWithLifecycle(initialValue = null)
    // Saved as a key, so an open editor survives rotation / process death.
    var editorKey by rememberSaveable { mutableStateOf<Long?>(null) }
    val list = players ?: return
    PlayersScreen(
        players = list,
        onBack = onBack,
        onAdd = { editorKey = EditorTarget.New.key },
        onEdit = { editorKey = EditorTarget.Edit(it).key },
        modifier = modifier,
    )
    EditorTarget.fromKey(editorKey, list)?.let { target ->
        // Color dots: the unfinished game's players; a new player's color avoids every stored player.
        PlayerEditorHost(
            target,
            list,
            repository,
            onClose = { editorKey = null },
            dotPlayers = active?.seatedPlayers.orEmpty(),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PlayersScreenPreview() {
    Scan7Theme { PlayersScreen(SampleStoredPlayers, {}, {}, {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PlayersScreenEmptyPreview() {
    Scan7Theme { PlayersScreen(emptyList(), {}, {}, {}) }
}
