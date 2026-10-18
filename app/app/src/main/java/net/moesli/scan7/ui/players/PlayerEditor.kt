package net.moesli.scan7.ui.players

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.moesli.scan7.data.ColorInUseException
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.data.outlivingUi
import net.moesli.scan7.game.PALETTE_SIZE
import net.moesli.scan7.game.Player
import net.moesli.scan7.ui.components.BottomSheetPanel
import net.moesli.scan7.ui.components.Confirmations
import net.moesli.scan7.ui.components.Scan7ConfirmDialog
import net.moesli.scan7.ui.components.PlayerToken
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.Scan7BottomSheet
import net.moesli.scan7.ui.components.Scan7SheetState
import net.moesli.scan7.ui.components.rememberScan7SheetState
import net.moesli.scan7.ui.components.Scan7TextField
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.components.TokenSize
import net.moesli.scan7.ui.theme.PlayerColors
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** What the editor opens on: a new player or an existing one. */
sealed interface EditorTarget {
    data object New : EditorTarget

    data class Edit(val player: Player) : EditorTarget

    /** Saveable form (for `rememberSaveable`): [NEW_KEY] or the player id. */
    val key: Long get() = when (this) {
        New -> NEW_KEY
        is Edit -> player.id
    }

    companion object {
        const val NEW_KEY = -1L

        /** The target behind a saved [key], with the live [players]; null if that player is gone. */
        fun fromKey(key: Long?, players: List<Player>): EditorTarget? = when (key) {
            null -> null
            NEW_KEY -> New
            else -> players.firstOrNull { it.id == key }?.let(::Edit)
        }
    }
}

private val SwatchToken = TokenSize(30.dp, 40.dp, 14.sp)
private val PreviewToken = TokenSize(36.dp, 48.dp, 17.sp)

/**
 * Player editor (2a) as a bottom sheet: name, color from the 10-color palette (colors other
 * stored players use are labelled with their names; still selectable, since colors only have to
 * be unique within a game), Save, and Delete for existing players (asks in a [Scan7ConfirmDialog];
 * on confirm the host deletes, then slides the sheet down).
 *
 * Stateless apart from the text/color being edited; [onSave] gets the trimmed name and color.
 */
@Composable
fun PlayerEditorSheet(
    target: EditorTarget,
    players: List<Player>,
    onSave: (name: String, colorIndex: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    error: String? = null,
    busy: Boolean = false,
    sheetState: Scan7SheetState = rememberScan7SheetState(),
) {
    // Delete asks in a centred Scan7ConfirmDialog over the sheet; the sheet's layout never changes.
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scan7BottomSheet(onDismiss = onDismiss, modifier = modifier, state = sheetState) {
        PlayerEditorContent(target, players, onSave, onDelete?.let { { confirmDelete = true } }, error, busy)
    }
    val editing = (target as? EditorTarget.Edit)?.player
    if (confirmDelete && onDelete != null && editing != null) {
        Scan7ConfirmDialog(
            Confirmations.deletePlayer(editing.name),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onCancel = { confirmDelete = false },
        )
    }
}

/** Content of [PlayerEditorSheet] (previews, embedding); [onDelete] is the Delete tap (the sheet confirms). */
@Composable
fun PlayerEditorContent(
    target: EditorTarget,
    players: List<Player>,
    onSave: (name: String, colorIndex: Int) -> Unit,
    onDelete: (() -> Unit)? = null,
    error: String? = null,
    busy: Boolean = false,
) {
    val colors = Scan7Theme.colors
    val editing = (target as? EditorTarget.Edit)?.player
    var name by rememberSaveable { mutableStateOf(editing?.name ?: "") }
    var colorIndex by rememberSaveable { mutableIntStateOf(editing?.colorIndex ?: suggestedColor(players)) }
    var nameMissing by rememberSaveable { mutableStateOf(false) }
    val owners = remember(players, editing) { colorOwners(players, editing?.id) }
    val canSave = name.isNotBlank() && !busy
    val initial = name.trim().take(1).uppercase().ifEmpty { "?" }

    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                PlayerToken(initial, PlayerColors[colorIndex], PreviewToken)
            }
            Text(
                text = if (editing == null) "New player" else "Edit player",
                color = colors.text,
                style = Scan7Type.headline16,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
            SectionLabel("Name")
            Scan7TextField(
                value = name,
                onValueChange = {
                    name = it.take(MAX_NAME_LENGTH)
                    if (name.isNotBlank()) nameMissing = false
                },
                placeholder = "e.g. Anna",
                label = "Name",
                // IME Done = Save; on an empty name it says why nothing happened (also to TalkBack).
                onDone = { if (canSave) onSave(name.trim(), colorIndex) else if (name.isBlank()) nameMissing = true },
                modifier = Modifier.semantics { if (nameMissing) error(NAME_MISSING) },
            )
            if (nameMissing) {
                Text(
                    NAME_MISSING,
                    color = colors.textMuted,
                    style = Scan7Type.text12,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
            SectionLabel("Color")
            ColorGrid(selected = colorIndex, initial = initial, owners = owners, onSelect = { colorIndex = it })
        }
        if (error != null) {
            Text(
                error,
                color = colors.text,
                style = Scan7Type.text13,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
            if (onDelete != null) {
                SecondaryButton("Delete", onDelete, Modifier.width(112.dp), height = 56.dp, enabled = !busy)
            }
            PrimaryButton("Save", { onSave(name.trim(), colorIndex) }, Modifier.weight(1f), height = 56.dp, enabled = canSave)
        }
    }
}

/** 5 × 2 grid of palette swatches (token shaped, with the player's initial). */
@Composable
private fun ColorGrid(selected: Int, initial: String, owners: Map<Int, List<Player>>, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.selectableGroup()) {
        (0 until PALETTE_SIZE).chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
                row.forEach { index ->
                    Swatch(
                        index = index,
                        initial = initial,
                        selected = index == selected,
                        owners = owners[index].orEmpty(),
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Swatch(index: Int, initial: String, selected: Boolean, owners: List<Player>, onClick: () -> Unit, modifier: Modifier) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    val ownerText = owners.joinToString(", ") { it.name }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs),
        modifier = modifier
            .clip(shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .clearAndSetSemantics {
                contentDescription = colorName(index) + if (owners.isEmpty()) "" else ", used by $ownerText"
                this.selected = selected
                role = Role.RadioButton
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(if (selected) colors.ground else colors.surface)
                .border(if (selected) 2.dp else 1.5.dp, if (selected) colors.accent else colors.line, shape),
        ) {
            PlayerToken(initial, PlayerColors[index], SwatchToken)
        }
        Text(
            text = ownerText.ifEmpty { " " },
            color = colors.textMuted,
            style = Scan7Type.text10,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val MAX_NAME_LENGTH = 16
private const val NAME_MISSING = "Enter a name to save"

/**
 * [PlayerEditorSheet] wired to the [repository]: adds or updates the player, deletes (the
 * repository archives players that played) and reports errors (e.g. a color already used in the
 * running game). [onSaved] gets the id of the saved player; [onClose] closes the sheet.
 */
@Composable
fun PlayerEditorHost(
    target: EditorTarget,
    players: List<Player>,
    repository: GameRepository,
    onClose: () -> Unit,
    onSaved: (Long) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // Save / Delete close the sheet with its slide-down once the repository call succeeded.
    val sheetState = rememberScan7SheetState()
    var error by remember(target) { mutableStateOf<String?>(null) }
    var busy by remember(target) { mutableStateOf(false) }
    val editing = (target as? EditorTarget.Edit)?.player
    PlayerEditorSheet(
        target = target,
        players = players,
        error = error,
        busy = busy,
        sheetState = sheetState,
        onDismiss = onClose,
        onSave = { name, color ->
            busy = true
            scope.launch {
                runCatching {
                    // The write finishes even if the sheet / screen is gone meanwhile.
                    outlivingUi {
                        if (editing == null) {
                            repository.addPlayer(name, color)
                        } else {
                            repository.updatePlayer(editing.id, name, color)
                            editing.id
                        }
                    }
                }.onSuccess { id ->
                    // Stays busy while the sheet slides down (no second save).
                    sheetState.hideThen({ onSaved(id) })
                }.onFailure {
                    error = when (it) {
                        is ColorInUseException -> "Another player in the unfinished game has this color. Pick a different one."
                        else -> it.message ?: "Couldn't save"
                    }
                    busy = false
                }
            }
        },
        onDelete = editing?.let { p ->
            {
                busy = true
                scope.launch {
                    runCatching { outlivingUi { repository.deletePlayer(p.id) } }
                        .onSuccess { sheetState.hideThen() }
                        .onFailure {
                            error = it.message ?: "Couldn't delete"
                            busy = false
                        }
                }
            }
        },
    )
}

internal val SampleStoredPlayers: List<Player> = listOf(
    Player(1, "Philip", 0),
    Player(2, "Anna", 1),
    Player(3, "Ben", 2),
    Player(4, "Mia", 3),
    Player(5, "Lena", 4),
    Player(6, "David", 1),
)

@Preview(widthDp = 390)
@Composable
private fun PlayerEditorPreview() {
    Scan7Theme {
        Box(Modifier.background(Scan7Theme.colors.ground).padding(top = Scan7Spacing.xl)) {
            BottomSheetPanel {
                PlayerEditorContent(EditorTarget.Edit(SampleStoredPlayers[1]), SampleStoredPlayers, { _, _ -> }, onDelete = {})
            }
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun PlayerEditorNewPreview() {
    Scan7Theme {
        BottomSheetPanel { PlayerEditorContent(EditorTarget.New, SampleStoredPlayers, { _, _ -> }) }
    }
}
