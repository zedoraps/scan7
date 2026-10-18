package net.moesli.scan7.ui.players

import net.moesli.scan7.ui.text.avatarPatternLabel
import net.moesli.scan7.ui.text.avatarFaceName
import net.moesli.scan7.ui.text.avatarColorName
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.ui.text.resolve
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.moesli.scan7.R
import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.AvatarCatalog
import net.moesli.scan7.game.AvatarColor
import net.moesli.scan7.ui.text.avatarDescription
import net.moesli.scan7.ui.components.BottomSheetPanel
import net.moesli.scan7.ui.components.IconButton
import net.moesli.scan7.ui.components.PlayerAvatar
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type
import kotlin.random.Random

/** Avatar editor preview (kit: 88 dp). */
val AvatarPreviewSize: Dp = 88.dp

/** Selected picker item: 2.5 dp cyan border (kit). */
private val SelectedBorder = 2.5.dp
private val FaceButtonHeight = 56.dp
private val FaceAvatar = 50.dp
private val PatternButtonHeight = 50.dp
private val PatternAvatar = 42.dp
private val ColorButtonHeight = 48.dp
private val ColorSwatch = 38.dp

/**
 * Header of the avatar editor: [title] and the shuffle button (random face, pattern and a color
 * nobody else in the game uses).
 */
@Composable
fun AvatarEditorHeader(title: String, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(
            title,
            color = colors.text,
            style = Scan7Type.headline16,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        IconButton(onClick = onShuffle, contentDescription = stringResource(R.string.editor_shuffle)) {
            Icon(ShuffleVector, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
    }
}

/** Live avatar preview (88 dp) with [nameField] next to it; described as "Mia, sand avatar, grin face". */
@Composable
fun AvatarPreviewRow(
    avatar: Avatar,
    name: String,
    modifier: Modifier = Modifier,
    nameField: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        PlayerAvatar(avatar, AvatarPreviewSize, contentDescription = avatarDescription(name, avatar).resolve())
        Box(Modifier.weight(1f)) { nameField() }
    }
}

/**
 * Face (3 × 4), pattern (1 × 6) and color (3 × 5) pickers. Face and pattern buttons show the
 * current avatar with that option. [usedBy] maps a color id to the other players in the current
 * game (or the game being set up) that use it: those swatches get a small dot (still selectable).
 */
@Composable
fun AvatarPickers(
    avatar: Avatar,
    onChange: (Avatar) -> Unit,
    usedBy: Map<String, List<String>>,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l), modifier = modifier.fillMaxWidth()) {
        PickerSection(stringResource(R.string.editor_face)) {
            AvatarCatalog.faces.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
                    row.forEach { face ->
                        PickerButton(
                            selected = face == avatar.face,
                            label = stringResource(R.string.editor_face_option, avatarFaceName(face).resolve()),
                            onClick = { onChange(avatar.copy(face = face)) },
                            height = FaceButtonHeight,
                            modifier = Modifier.weight(1f),
                        ) { PlayerAvatar(avatar.copy(face = face), FaceAvatar) }
                    }
                }
            }
        }
        PickerSection(stringResource(R.string.editor_pattern)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
                AvatarCatalog.patterns.forEach { pattern ->
                    PickerButton(
                        selected = pattern == avatar.pattern,
                        label = avatarPatternLabel(pattern).resolve(),
                        onClick = { onChange(avatar.copy(pattern = pattern)) },
                        height = PatternButtonHeight,
                        modifier = Modifier.weight(1f),
                    ) { PlayerAvatar(avatar.copy(pattern = pattern), PatternAvatar) }
                }
            }
        }
        PickerSection(stringResource(R.string.editor_color)) {
            AvatarCatalog.colors.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
                    row.forEach { c ->
                        val owners = usedBy[c.id].orEmpty()
                        val colorName = avatarColorName(c.id).resolve()
                        PickerButton(
                            selected = c.id == avatar.color,
                            label = if (owners.isEmpty()) colorName else stringResource(R.string.editor_color_used, colorName, UiText.Names(owners).resolve()),
                            onClick = { onChange(avatar.copy(color = c.id)) },
                            height = ColorButtonHeight,
                            modifier = Modifier.weight(1f),
                        ) { ColorSwatch(c, used = owners.isNotEmpty()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
        SectionLabel(label)
        Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.selectableGroup()) {
            content()
        }
    }
}

/** One radio-style picker item (≥ 44 dp), selected = 2.5 dp cyan border. */
@Composable
private fun PickerButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(if (selected) colors.ground else colors.surfaceDone)
            .border(if (selected) SelectedBorder else 1.dp, if (selected) colors.accent else colors.line, shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .clearAndSetSemantics {
                contentDescription = label
                this.selected = selected
                role = Role.RadioButton
            },
    ) {
        content()
    }
}

/** A tilted mini card in [color] (the avatar base layer), with a dot when another player uses it. */
@Composable
private fun ColorSwatch(color: AvatarColor, used: Boolean) {
    val colors = Scan7Theme.colors
    Box(Modifier.size(ColorSwatch)) {
        Image(
            painterResource(R.drawable.avatar_base),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color(color.argb)),
            modifier = Modifier.matchParentSize(),
        )
        if (used) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
                    .background(colors.ground, CircleShape)
                    .padding(2.dp)
                    .background(colors.text, CircleShape),
            )
        }
    }
}

/** Saves an [Avatar] (its three ids) for `rememberSaveable`. */
val AvatarSaver: Saver<Avatar, Any> = listSaver(
    save = { listOf(it.face, it.pattern, it.color) },
    restore = { Avatar(it[0], it[1], it[2]) },
)

/**
 * A shuffled avatar: random face and pattern, and a random color none of [usedColors] has (any
 * color once all are used). Never returns [current] itself.
 */
fun shuffledAvatar(current: Avatar, usedColors: Collection<String>, random: Random = Random.Default): Avatar {
    val free = AvatarCatalog.colors.map { it.id }.filter { it !in usedColors }.ifEmpty { AvatarCatalog.colors.map { it.id } }
    while (true) {
        val next = AvatarCatalog.random(free.random(random), random)
        if (next != current) return next
    }
}

/** Two crossing arrows (shuffle), 24 grid, stroked; drawn by M3 [Icon] like the other app icons. */
private val ShuffleVector: ImageVector by lazy {
    ImageVector.Builder("Shuffle", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString("M4 7h3.5c4 0 5 10 9 10H20M4 17h3.5c1.6 0 2.7-1.6 3.6-3.5M13 10.5c.9-1.9 2-3.5 3.5-3.5H20M17.5 4.5L20 7l-2.5 2.5M17.5 14.5L20 17l-2.5 2.5").toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
private fun AvatarEditorPreview() {
    Scan7Theme {
        BottomSheetPanel {
            val avatar = AvatarCatalog.examples[3].second
            Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l)) {
                AvatarEditorHeader("Edit player", {})
                AvatarPreviewRow(avatar, "Mia") { Text("Mia", color = Scan7Theme.colors.text) }
                AvatarPickers(avatar, {}, mapOf("amber" to listOf("Philip"), "lime" to listOf("Ben")))
            }
        }
    }
}
