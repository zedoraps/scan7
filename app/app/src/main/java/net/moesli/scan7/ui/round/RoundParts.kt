package net.moesli.scan7.ui.round

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.ui.components.ArrangeIcon
import net.moesli.scan7.ui.components.IconButton
import net.moesli.scan7.ui.components.MenuIcon
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.RoundView
import net.moesli.scan7.ui.components.ViewToggle
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** App icon (prototype header SVG, viewBox 100): cyan tile, dark card with the "7", scan line. */
@Composable
fun AppIcon(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val c = Scan7Theme.colors
    val seven = Path().apply {
        moveTo(42f, 35f)
        relativeLineTo(16f, 0f)
        relativeLineTo(-9f, 25f)
    }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 100f, pivot = Offset.Zero) {
            drawRoundRect(c.accent, size = Size(100f, 100f), cornerRadius = CornerRadius(24f))
            rotate(-8f, pivot = Offset(50f, 50f)) {
                drawRoundRect(c.ground, topLeft = Offset(30f, 20f), size = Size(40f, 60f), cornerRadius = CornerRadius(7f))
                drawPath(seven, c.accent, style = Stroke(9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawRoundRect(c.text, topLeft = Offset(12f, 66f), size = Size(76f, 10f), cornerRadius = CornerRadius(5f))
                drawRoundRect(
                    c.ground,
                    topLeft = Offset(12f, 66f),
                    size = Size(76f, 10f),
                    cornerRadius = CornerRadius(5f),
                    style = Stroke(2.5f),
                )
            }
        }
    }
}

/** Header (top 48, height 48): icon · view toggle · Arrange + Menu; arrange mode: title + hint + Done. */
@Composable
internal fun RoundHeader(
    view: RoundView,
    arranging: Boolean,
    actions: RoundActions,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(Scan7Spacing.headerHeight)
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        if (arranging) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs),
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
            ) {
                Text("Arrange seats", color = colors.text, style = Scan7Type.headline16, maxLines = 1)
                Text(
                    text = if (view == RoundView.Table) "Drag players to where they sit" else "Drag rows into seating order",
                    color = colors.textMuted,
                    style = Scan7Type.text12,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PrimaryButton(
                text = "Done",
                onClick = actions::onDone,
                height = Scan7Spacing.minTouch,
                cornerRadius = Scan7Radii.iconButton,
                textStyle = Scan7Type.button.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
        } else {
            AppIcon(Modifier.clearAndSetSemantics {})
            ViewToggle(selected = view, onSelect = actions::onToggleView)
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
                IconButton(onClick = actions::onEnterArrange, contentDescription = "Arrange seats") {
                    ArrangeIcon(colors.text)
                }
                IconButton(onClick = actions::onMenu, contentDescription = "Menu") {
                    MenuIcon(colors.text)
                }
            }
        }
    }
}

/**
 * Rank badge for the end-of-round view: 24 dp circle with the rank (Unbounded), overlapping the
 * card's top-left corner (the leader badge sits top-right). Rank 1 is cyan, the others `surface`
 * with a `line` outline.
 */
@Composable
fun RankBadge(rank: Int, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val c = Scan7Theme.colors
    val first = rank == 1
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(if (first) c.accent else c.surface, CircleShape)
            .then(if (first) Modifier else Modifier.border(1.5.dp, c.line, CircleShape))
            .semantics { contentDescription = "Rank $rank this round" },
    ) {
        Text(
            text = rank.toString(),
            color = if (first) c.onAccent else c.text,
            style = Scan7Type.total13.copy(fontSize = 11.sp, lineHeight = 11.sp),
            maxLines = 1,
        )
    }
}

/**
 * Bust undo toast ("Anna marked bust · Undo"): `surface` panel, 48 high, radius 12, at the bottom
 * above the buttons. Announced politely to TalkBack.
 */
@Composable
internal fun BustToast(name: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val c = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(Scan7Spacing.minTouch)
            .background(c.surface, shape)
            .border(1.5.dp, c.line, shape)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = "$name marked bust",
            color = c.text,
            style = Scan7Type.text13,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = Scan7Spacing.l),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = Scan7Spacing.minTouch)
                .clip(shape)
                .scan7Clickable(onUndo)
                .clearAndSetSemantics {
                    contentDescription = "Undo bust for $name"
                    role = Role.Button
                }
                .padding(horizontal = Scan7Spacing.l),
        ) {
            Text("UNDO", color = c.accent, style = Scan7Type.tag12.copy(letterSpacing = Scan7Type.capsButton.letterSpacing))
        }
    }
}
