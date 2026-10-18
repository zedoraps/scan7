package net.moesli.scan7.ui.theme

import androidx.compose.ui.unit.dp

/** Spacing steps from the design handoff (px in the handoff = dp). No ad-hoc paddings. */
object Scan7Spacing {
    val xxs = 4.dp
    val xs = 6.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp

    /** Left/right screen margin. */
    val screenMargin = 16.dp

    /** Header: distance from the top of the screen and its height. */
    val headerTop = 48.dp
    val headerHeight = 48.dp

    /** Bottom buttons: distance from the bottom and height. */
    val bottomButtonsBottom = 24.dp
    val bottomButtonsHeight = 64.dp

    /** Minimum touch target; in-card actions on compact sizes may go down to [minCompactTouch]. */
    val minTouch = 48.dp
    val minCompactTouch = 36.dp
}

/** Corner radii from the design handoff. */
object Scan7Radii {
    /** Player card / row, large size class. */
    val cardLarge = 18.dp

    /** Player card / row, medium and compact size classes. */
    val card = 14.dp

    /** Bottom buttons (primary / secondary). */
    val button = 16.dp

    /** Icon buttons and header buttons. */
    val iconButton = 12.dp

    /** In-card action buttons (table view). */
    val actionCard = 8.dp

    /** In-row action buttons (list view). */
    val actionRow = 10.dp

    /** Status tag on a card. */
    val tagCard = 6.dp

    /** Status tag in a row. */
    val tagRow = 8.dp

    /** View toggle pill: outer container and active segment. */
    val toggleOuter = 14.dp
    val toggleInner = 10.dp

    /** Player token (the small rotated card). */
    val token = 5.dp
}
