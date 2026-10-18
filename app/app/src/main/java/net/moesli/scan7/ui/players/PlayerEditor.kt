package net.moesli.scan7.ui.players

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.data.outlivingUi
import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.Player
import net.moesli.scan7.ui.components.BottomSheetPanel
import net.moesli.scan7.ui.components.Confirmations
import net.moesli.scan7.ui.components.Scan7ConfirmDialog
import net.moesli.scan7.ui.components.sampleAvatar
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.Scan7BottomSheet
import net.moesli.scan7.ui.components.Scan7SheetState
import net.moesli.scan7.ui.components.rememberScan7SheetState
import net.moesli.scan7.ui.components.Scan7TextField
import net.moesli.scan7.ui.components.SecondaryButton
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

/**
 * Player editor (2a) as a bottom sheet with the avatar editor (design/avatars/AVATARS.md): header
 * with shuffle, live avatar preview (88 dp) + name, Face / Pattern / Color pickers (scroll when the
 * sheet is short), Save + Delete pinned at the bottom. Colors of [dotPlayers] get a dot (duplicates
 * are allowed); Delete asks in a [Scan7ConfirmDialog], on confirm the host deletes, then slides the
 * sheet down.
 *
 * Stateless apart from the name/avatar being edited; [onSave] gets the trimmed name and the avatar.
 */
@Composable
fun PlayerEditorSheet(
    target: EditorTarget,
    players: List<Player>,
    onSave: (name: String, avatar: Avatar) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dotPlayers: List<Player> = emptyList(),
    onDelete: (() -> Unit)? = null,
    error: String? = null,
    busy: Boolean = false,
    sheetState: Scan7SheetState = rememberScan7SheetState(),
) {
    // Delete asks in a centred Scan7ConfirmDialog over the sheet; the sheet's layout never changes.
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scan7BottomSheet(onDismiss = onDismiss, modifier = modifier, state = sheetState) {
        PlayerEditorContent(target, players, onSave, onDelete?.let { { confirmDelete = true } }, error, busy, dotPlayers)
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
    onSave: (name: String, avatar: Avatar) -> Unit,
    onDelete: (() -> Unit)? = null,
    error: String? = null,
    busy: Boolean = false,
    dotPlayers: List<Player> = emptyList(),
) {
    val colors = Scan7Theme.colors
    val editing = (target as? EditorTarget.Edit)?.player
    var name by rememberSaveable { mutableStateOf(editing?.name ?: "") }
    var avatar by rememberSaveable(stateSaver = AvatarSaver) { mutableStateOf(editing?.avatar ?: suggestedAvatar(players)) }
    var nameMissing by rememberSaveable { mutableStateOf(false) }
    val owners = remember(dotPlayers, editing) { colorOwners(dotPlayers, editing?.id) }
    val usedBy = remember(owners) { owners.mapValues { (_, ps) -> ps.map { it.name } } }
    val canSave = name.isNotBlank() && !busy

    Column {
        AvatarEditorHeader(
            title = if (editing == null) "New player" else "Edit player",
            onShuffle = {
                val taken = (players + dotPlayers).filter { it.id != editing?.id }.map { it.avatar.color }
                avatar = shuffledAvatar(avatar, taken)
            },
        )
        // Pickers scroll when the sheet is short (e.g. 720 × 1600); Save stays pinned below.
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(vertical = Scan7Spacing.m),
        ) {
            AvatarPreviewRow(avatar, name) {
                Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xs)) {
                    Scan7TextField(
                        value = name,
                        onValueChange = {
                            name = it.take(MAX_NAME_LENGTH)
                            if (name.isNotBlank()) nameMissing = false
                        },
                        placeholder = "e.g. Anna",
                        label = "Name",
                        // IME Done = Save; on an empty name it says why nothing happened (also to TalkBack).
                        onDone = { if (canSave) onSave(name.trim(), avatar) else if (name.isBlank()) nameMissing = true },
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
            }
            AvatarPickers(avatar = avatar, onChange = { avatar = it }, usedBy = usedBy)
        }
        if (error != null) {
            Text(
                error,
                color = colors.text,
                style = Scan7Type.text13,
                modifier = Modifier
                    .padding(bottom = Scan7Spacing.s)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
            if (onDelete != null) {
                SecondaryButton("Delete", onDelete, Modifier.width(112.dp), height = 56.dp, enabled = !busy)
            }
            PrimaryButton("Save", { onSave(name.trim(), avatar) }, Modifier.weight(1f), height = 56.dp, enabled = canSave)
        }
    }
}

private const val MAX_NAME_LENGTH = 16
private const val NAME_MISSING = "Enter a name to save"

/**
 * [PlayerEditorSheet] wired to the [repository]: adds or updates the player, deletes (the
 * repository archives players that played) and reports errors. A new player gets a
 * color none of [players] uses; [dotPlayers] (the current game or the game being set up) mark their
 * colors with a dot. [onSaved] gets the id of the saved player; [onClose] closes the sheet.
 */
@Composable
fun PlayerEditorHost(
    target: EditorTarget,
    players: List<Player>,
    repository: GameRepository,
    onClose: () -> Unit,
    onSaved: (Long) -> Unit = {},
    dotPlayers: List<Player> = emptyList(),
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
        dotPlayers = dotPlayers,
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
                    error = it.message ?: "Couldn't save"
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
    Player(1, "Philip", sampleAvatar("Philip")),
    Player(2, "Anna", sampleAvatar("Anna")),
    Player(3, "Ben", sampleAvatar("Ben")),
    Player(4, "Mia", sampleAvatar("Mia")),
    Player(5, "Lena", sampleAvatar("Lena")),
    Player(6, "David", sampleAvatar("Anna").copy(face = "grumpy")),
)

@Preview(widthDp = 390)
@Composable
private fun PlayerEditorPreview() {
    Scan7Theme {
        Box(Modifier.background(Scan7Theme.colors.ground).padding(top = Scan7Spacing.xl)) {
            BottomSheetPanel {
                PlayerEditorContent(EditorTarget.Edit(SampleStoredPlayers[1]), SampleStoredPlayers, { _, _ -> }, onDelete = {}, dotPlayers = SampleStoredPlayers.take(4))
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
