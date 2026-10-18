package net.moesli.scan7.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.moesli.scan7.R
import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.AvatarCatalog
import net.moesli.scan7.ui.text.avatarDescription
import net.moesli.scan7.ui.text.resolve
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * Height of the tilted card inside the 100 × 100 avatar viewBox (y 7.5–92.5). The kit gives
 * in-app sizes as card heights ("32 dp tall"); [avatarSizeForHeight] turns them into the box size.
 */
private const val CARD_HEIGHT_FRACTION = 0.85f

/** Box size of a [PlayerAvatar] whose visible card is [cardHeight] tall. */
fun avatarSizeForHeight(cardHeight: Dp): Dp = cardHeight / CARD_HEIGHT_FRACTION

/** The player color of [Avatar.color] (unknown id → first catalog color). */
val Avatar.colorValue: Color get() = Color(AvatarCatalog.color(color).argb)

/** Face layer for [id] (unknown → happy). A `when`, no reflection: keeps R8 resource shrinking happy. */
@DrawableRes
fun faceRes(id: String): Int = when (id) {
    "happy" -> R.drawable.avatar_face_happy
    "cool" -> R.drawable.avatar_face_cool
    "wink" -> R.drawable.avatar_face_wink
    "wow" -> R.drawable.avatar_face_wow
    "grin" -> R.drawable.avatar_face_grin
    "sleepy" -> R.drawable.avatar_face_sleepy
    "cheeky" -> R.drawable.avatar_face_cheeky
    "focus" -> R.drawable.avatar_face_focus
    "hearts" -> R.drawable.avatar_face_hearts
    "grumpy" -> R.drawable.avatar_face_grumpy
    "silly" -> R.drawable.avatar_face_silly
    "dizzy" -> R.drawable.avatar_face_dizzy
    else -> R.drawable.avatar_face_happy
}

/** Pattern layer for [id], `null` for `plain` and unknown ids (no pattern layer). */
@DrawableRes
fun patternRes(id: String): Int? = when (id) {
    "stripes" -> R.drawable.avatar_pattern_stripes
    "dots" -> R.drawable.avatar_pattern_dots
    "zigzag" -> R.drawable.avatar_pattern_zigzag
    "grid" -> R.drawable.avatar_pattern_grid
    "waves" -> R.drawable.avatar_pattern_waves
    else -> null
}

/**
 * A player's avatar: three stacked vector layers — the card (`avatar_base`, tinted with the
 * player color), the pattern (skipped for `plain`) and the face. The −8° tilt is baked into the
 * shapes. [size] is the square box (see [avatarSizeForHeight]). [contentDescription] like
 * "Mia, sand avatar, grin face" ([net.moesli.scan7.ui.text.avatarDescription]); `null` = decorative
 * (the parent already names the player).
 */
@Composable
fun PlayerAvatar(
    avatar: Avatar,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        Image(
            painterResource(R.drawable.avatar_base),
            contentDescription = null,
            colorFilter = ColorFilter.tint(avatar.colorValue),
            modifier = Modifier.matchParentSize(),
        )
        patternRes(avatar.pattern)?.let { Image(painterResource(it), contentDescription = null, modifier = Modifier.matchParentSize()) }
        Image(painterResource(faceRes(avatar.face)), contentDescription = null, modifier = Modifier.matchParentSize())
    }
}

/**
 * The kit's `examples` presets at the three sizes of avatar_preview.png (large / 36 / 24 px), for
 * comparing against design/avatars/avatar_preview.png and examples/ (debug menu → Avatars).
 */
@Composable
fun AvatarExamplesSheet(modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        modifier = modifier,
    ) {
        AvatarCatalog.examples.forEach { (name, avatar) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xs),
                modifier = Modifier
                    .background(colors.surface, RoundedCornerShape(Scan7Radii.card))
                    .padding(Scan7Spacing.s),
            ) {
                PlayerAvatar(avatar, 88.dp, contentDescription = avatarDescription(name, avatar).resolve())
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerAvatar(avatar, 36.dp)
                    PlayerAvatar(avatar, 24.dp)
                }
                Text(
                    "$name: ${avatar.color} / ${avatar.face} / ${avatar.pattern}",
                    color = colors.textMuted,
                    style = Scan7Type.text10,
                )
            }
        }
    }
}

@Preview(widthDp = 420, heightDp = 900)
@Composable
private fun AvatarExamplesPreview() {
    Scan7Theme {
        AvatarExamplesSheet(Modifier.background(Scan7Theme.colors.ground).padding(Scan7Spacing.l))
    }
}
