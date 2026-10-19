package net.moesli.scan7.ui.components

import net.moesli.scan7.ui.text.uiPlural
import net.moesli.scan7.ui.text.uiText
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.R
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.AvatarCatalog

/** A player's state in the current round, as shown by the components. */
@Immutable
sealed interface PlayerStatus {
    /** Not scored yet this round. */
    data object Open : PlayerStatus

    /** Scored this round with [points]. */
    data class Scored(val points: Int) : PlayerStatus

    /** Busted this round (0 points). */
    data object Bust : PlayerStatus
}

val PlayerStatus.isDone: Boolean get() = this != PlayerStatus.Open

/**
 * UI model for one player in the round screen components. [total] is the game total so far,
 * [target] the winning total (ring progress = total / target). [adjustment] = the sum of the
 * player's total adjustments ("Adjust total", counted in [total]), `null` without any.
 */
@Immutable
data class PlayerUi(
    val name: String,
    val avatar: Avatar,
    val total: Int,
    val status: PlayerStatus = PlayerStatus.Open,
    val target: Int = 200,
    val isLeader: Boolean = false,
    val id: String = name,
    /** Multiplayer: which phone scans this player (badge), `null` = not shared / nobody. */
    val share: ShareMark? = null,
    val adjustment: Int? = null,
) {
    val initial: String get() = name.take(1).uppercase()

    /** The avatar's player color (ring progress, progress strip). */
    val color: Color get() = avatar.colorValue
}

/**
 * Multiplayer badge on a card / row (docs/dev/MULTI_DEVICE.md §2): [mine] = this phone scans the
 * player (highlight), else [initial] of the person whose phone does. [description] for TalkBack.
 */
@Immutable
data class ShareMark(val mine: Boolean, val initial: String, val description: UiText)

/** Interaction mode of a card or row. */
enum class CardMode {
    /** Normal round screen: actions on open players, tags on finished ones. */
    Normal,

    /** Arrange mode: dashed border, wiggle, actions hidden (tags shown for everyone). */
    Arrange,

    /** This card is being dragged (arrange mode): accent border, lifted, no wiggle. */
    Dragging,
}

/** Avatar slot of a card / row: [height] = the avatar card height (kit sizes); [width] and [fontSize] are left from the old initial token. */
@Immutable
data class TokenSize(val width: Dp, val height: Dp, val fontSize: TextUnit)

/**
 * Table-view card size classes (handoff §3, "Player card – table view"). The round screen picks the
 * largest one that fits the screen (`ui.round.tableCardSize`); the player counts are the handoff's
 * reference-phone defaults.
 */
enum class CardSize(
    val width: Dp,
    val height: Dp,
    val padding: Dp,
    val ring: Dp,
    val token: TokenSize,
    val nameSize: TextUnit,
    val tagHeight: Dp,
    /** In-card Scan/Bust height, or null when the size class has no in-card actions. */
    val actionHeight: Dp?,
) {
    /** ≤ 4 players on the reference phone. */
    Large(112.dp, 140.dp, 8.dp, 56.dp, TokenSize(24.dp, 32.dp, 12.sp), 12.sp, 30.dp, 36.dp),

    /** 5–6 players on the reference phone. */
    Medium(96.dp, 124.dp, 6.dp, 46.dp, TokenSize(20.dp, 27.dp, 11.sp), 11.sp, 26.dp, 30.dp),

    /** 7–10 players: tag only, no in-card actions. */
    Compact(72.dp, 96.dp, 6.dp, 36.dp, TokenSize(16.dp, 21.dp, 9.sp), 11.sp, 20.dp, null),
    ;
}

/**
 * List-view row size classes (handoff §3, "Player row – list view"). The round screen uses Regular
 * when all rows fit the screen, else Compact (`ui.round.listRowSize`).
 */
enum class RowSize(
    val height: Dp,
    /** Vertical gap between rows (used by the list layout). */
    val gap: Dp,
    val ring: Dp,
    val token: TokenSize,
    val nameSize: TextUnit,
    /** Width of the actions/tag column. */
    val actionsWidth: Dp,
    val actionHeight: Dp,
) {
    /** ≤ 6 players. */
    Regular(72.dp, 8.dp, 48.dp, TokenSize(22.dp, 30.dp, 11.sp), 15.sp, 116.dp, 48.dp),

    /** 7–10 players. */
    Compact(48.dp, 6.dp, 32.dp, TokenSize(15.dp, 20.dp, 9.sp), 14.sp, 108.dp, 36.dp),
    ;
}

/** Content description for a card/row body, e.g. "Mia, 96 points, not scored yet". */
internal fun PlayerUi.accessibilityLabel(): UiText {
    val state = when (val s = status) {
        PlayerStatus.Open -> uiText(R.string.a11y_player_open)
        is PlayerStatus.Scored -> uiPlural(R.plurals.a11y_player_scored, s.points)
        PlayerStatus.Bust -> uiText(R.string.a11y_player_bust)
    }
    val parts = listOfNotNull(UiText.Raw(name), uiPlural(R.plurals.points, total), adjustmentText(), state)
    return UiText.Join(if (isLeader) parts + uiText(R.string.a11y_player_leading) else parts, ", ")
}

/** TalkBack note on the total: "adjusted by minus 5" / "korrigiert um minus 5"; null without adjustments. */
fun PlayerUi.adjustmentText(): UiText? = adjustment?.let(::adjustmentText)

/** "adjusted by plus 10" / "adjusted by minus 5"; adjustments that cancel out: "includes adjustments". */
fun adjustmentText(sum: Int): UiText = when {
    sum > 0 -> uiText(R.string.a11y_adjusted_by, uiText(R.string.adjust_plus, sum))
    sum < 0 -> uiText(R.string.a11y_adjusted_by, uiText(R.string.adjust_minus, -sum))
    else -> uiText(R.string.a11y_adjusted)
}

/** The avatar kit's example preset of [name] (Philip, Anna, … Sara), else the fallback avatar. */
internal fun sampleAvatar(name: String): Avatar =
    AvatarCatalog.examples.firstOrNull { it.first == name }?.second ?: Avatar.DEFAULT

/**
 * Demo players from the prototype (design/reference/RoundScreen.dc.html) with the kit's example
 * avatars, for previews and the gallery.
 */
internal val SamplePlayers: List<PlayerUi> = listOf(
    PlayerUi("Philip", sampleAvatar("Philip"), 142, PlayerStatus.Scored(46)),
    PlayerUi("Anna", sampleAvatar("Anna"), 118, PlayerStatus.Bust),
    PlayerUi("Ben", sampleAvatar("Ben"), 167, PlayerStatus.Open),
    PlayerUi("Mia", sampleAvatar("Mia"), 96, PlayerStatus.Open),
    PlayerUi("Lena", sampleAvatar("Lena"), 131, PlayerStatus.Scored(22)),
    PlayerUi("Tom", sampleAvatar("Tom"), 74, PlayerStatus.Open),
    PlayerUi("Noah", sampleAvatar("Noah"), 158, PlayerStatus.Scored(31)),
    PlayerUi("Zoe", sampleAvatar("Zoe"), 102, PlayerStatus.Bust),
    PlayerUi("Elias", sampleAvatar("Elias"), 88, PlayerStatus.Open),
    PlayerUi("Sara", sampleAvatar("Sara"), 120, PlayerStatus.Scored(18)),
)

/** The first [count] sample players with the leader (highest total) marked. */
internal fun samplePlayers(count: Int): List<PlayerUi> {
    val players = SamplePlayers.take(count)
    val leader = players.maxBy { it.total }
    return players.map { it.copy(isLeader = it == leader) }
}
