package net.moesli.scan7.multiplayer

import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.EnderPenalty
import net.moesli.scan7.game.GameKind
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.MAX_PLAYERS
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.CardKind
import net.moesli.scan7.scoring.Deck
import net.moesli.scan7.skyjo.SkyjoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WireCodecTest {
    private val codec = WireCodec()

    private fun roundTrip(msg: WireMessage): WireMessage = (codec.decode(codec.encode(msg)) as Decoded.Message).message

    @Test
    fun messages_roundTrip() {
        val msgs = listOf(
            Hello("s", "t", "d", lastSeq = 4),
            Intent("d:1", 3, ScoreCards(1, 2, listOf(WireCard("07", false), WireCard("freeze", true)), manual = true)),
            Intent("d:2", 3, AdjustTotal(1, -5, "penalty")),
            Event(5, 99, CloseRoundAndStartNext(2)),
            Event(6, 100, MovePosition(1, 0.25f, 0.5f), "d:3", "d"),
            IntentAck("d:3", 6),
            IntentRejected("d:2", RejectReason.STALE),
            HelloRejected(JoinRejection.VERSION_MISMATCH, 1),
            ResyncRequest(4),
        )
        msgs.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun longTenPlayerGame_fitsOneFrame_andRoundTrips() {
        val players = (1L..MAX_PLAYERS).map { Player(it, "Player with a long name $it") }
        var s = GameState.newGame(players, target = 1_000_000, startedAt = 1)
        // Different rows everywhere (identical ones would flatter gzip): 7 numbers + 3 other cards each.
        val random = Random(1)
        val numbers = Deck.FLIP7.cards.filter { it.kind == CardKind.NUMBER }
        val others = Deck.FLIP7.cards.filter { it.kind != CardKind.NUMBER }
        fun row() = (numbers.shuffled(random).take(7) + others.shuffled(random).take(3)).map { ConfirmedCard(it) }
        repeat(30) { r ->
            val now = 1_700_000_000_000 + r * 60_000L + random.nextInt(60_000)
            players.forEach { s = s.scoreCards(it.id, row(), ResultSource.SCAN, now) }
            s = s.closeAndStartNext(now)
        }
        s = s.adjustTotal(1, 7, "house rule", 5).let { it.copy(adjustments = it.adjustments.map { a -> a.copy(id = 1) }) }
        val welcome = Welcome("s", 42, GameSnapshot(GameDto.of(s), players.map { PlayerDto.of(it) }))
        val frame = codec.encode(welcome)
        assertEquals(WireCodec.GZIP, frame[0])
        assertTrue("frame is ${frame.size} bytes", frame.size < WireCodec.MAX_FRAME_BYTES)
        println("30 rounds x 10 players x 10 cards: ${frame.size} bytes gzipped")
        val back = roundTrip(welcome) as Welcome
        assertEquals(s, back.snapshot.game.toState())
    }

    @Test
    fun skyjoGame_roundTripsKindRuleSlotsAndEnder() {
        val players = listOf(Player(1, "Anna"), Player(2, "Ben"))
        val grid = listOf(ConfirmedCard(SkyjoCard.N12, slot = 0), ConfirmedCard(SkyjoCard.M2, slot = 11))
        val s = GameState.newGame(players, target = 100, kind = GameKind.SKYJO, enderPenalty = EnderPenalty.OFF)
            .scoreCards(1, grid, ResultSource.MANUAL, 1)
            .scorePoints(2, -3, 1)
            .withRoundEnder(1, 2)
            .closeAndStartNext(3)
        val welcome = roundTrip(Welcome("s", 1, GameSnapshot(GameDto.of(s), players.map { PlayerDto.of(it) }))) as Welcome
        assertEquals(s, welcome.snapshot.game.toState())
        val event = Event(7, 9, SetRoundEnder(1, 2))
        assertEquals(event, roundTrip(event))
    }

    @Test
    fun otherVersion_isNotDecoded_exceptFrozenMessages() {
        val newer = WireCodec(version = 2)
        assertEquals(Decoded.UnsupportedVersion(2, "event"), codec.decode(newer.encode(Event(1, 1, Bust(1, 1)))))
        val hello = codec.decode(newer.encode(Hello("s", "t", "d"))) as Decoded.Message
        assertEquals(2, hello.version)
        assertTrue(newer.decode(codec.encode(HelloRejected(JoinRejection.VERSION_MISMATCH, 1))) is Decoded.Message)
    }

    @Test
    fun garbage_isMalformed() {
        assertTrue(codec.decode(byteArrayOf()) is Decoded.Malformed)
        assertTrue(codec.decode(byteArrayOf(0) + "{not json".encodeToByteArray()) is Decoded.Malformed)
        assertTrue(codec.decode(byteArrayOf(0) + """{"v":1,"m":{"type":"nope"}}""".encodeToByteArray()) is Decoded.Malformed)
        assertTrue(codec.decode(byteArrayOf(9, 1, 2)) is Decoded.Malformed)
    }
}
