package net.moesli.scan7.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.EnderPenalty
import net.moesli.scan7.game.GameKind
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.skyjo.SkyjoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exported-schema migration tests: data written by an older version must survive the upgrade. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), Scan7Database::class.java)

    @Test
    fun v2ToV3_keepsGames_andAddsScoreAdjustments() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL("INSERT INTO players (id, name, colorIndex, createdAt, archived) VALUES (1, 'Anna', 0, 10, 0), (2, 'Ben', 1, 11, 0)")
            db.execSQL("INSERT INTO games (id, startedAt, finishedAt, abandoned, endedEarly, target, viewMode) VALUES (7, 100, NULL, 0, 0, 200, 'LIST')")
            db.execSQL("INSERT INTO game_players (gameId, playerId, seatIndex, tableX, tableY) VALUES (7, 1, 0, 0.5, 0.0), (7, 2, 1, 0.5, 1.0)")
            db.execSQL("INSERT INTO rounds (id, gameId, number, closedAt) VALUES (1, 7, 1, 150), (2, 7, 2, NULL)")
            db.execSQL(
                "INSERT INTO round_results (id, roundId, playerId, status, points, source, updatedAt) VALUES " +
                    "(1, 1, 1, 'SCORED', 42, 'SCAN', 120), (2, 1, 2, 'BUST', 0, 'BUST_TAP', 130), " +
                    "(3, 2, 1, 'OPEN', 0, NULL, 150), (4, 2, 2, 'SCORED', 17, 'MANUAL', 160)",
            )
            db.execSQL("INSERT INTO result_cards (id, resultId, position, label, ignored) VALUES (1, 1, 0, '12', 0)")
            db.execSQL("INSERT INTO settings (`key`, value) VALUES ('view_mode', 'LIST')")
        }

        // Validates the migrated schema against the exported 3.json.
        helper.runMigrationsAndValidate(dbName, 3, true).use { db ->
            db.query("SELECT COUNT(*) FROM score_adjustments").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Opens at the current version: 3 → 4 runs too.
        val room = Room.databaseBuilder(context, Scan7Database::class.java, dbName).addMigrations(*MIGRATIONS).build()
        try {
            runBlocking {
                val repo = GameRepository(room, clock = { 1000L })
                val snap = repo.activeGame.first()
                assertNotNull(snap)
                val s = snap!!.state
                assertEquals(7L, s.id)
                assertEquals(listOf(1L, 2L), s.playerIds)
                assertEquals(2, s.rounds.size)
                assertEquals(ResultStatus.BUST, s.result(2, round = 1).status)
                assertEquals(1, s.result(1, round = 1).cards.size)
                assertEquals(mapOf(1L to 42, 2L to 17), s.totals)
                assertEquals(emptyList<Any>(), s.adjustments)

                // The new table works on the migrated database.
                repo.adjustTotal(7, 2, -12, "penalty")
                val adjusted = repo.activeGame.first()!!.state
                assertEquals(mapOf(1L to 42, 2L to 5), adjusted.totals)
                assertEquals(2, adjusted.adjustments.single().round)
            }
        } finally {
            room.close()
        }
    }

    @Test
    fun v3ToV4_turnsColorIndexIntoAvatars_andKeepsGames() {
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL(
                "INSERT INTO players (id, name, colorIndex, createdAt, archived) VALUES " +
                    "(1, 'Anna', 0, 10, 0), (2, 'Ben', 3, 11, 0), (3, 'Cleo', 3, 12, 1), (4, 'Dan', 9, 13, 0)",
            )
            db.execSQL("INSERT INTO games (id, startedAt, finishedAt, abandoned, endedEarly, target, viewMode) VALUES (7, 100, NULL, 0, 0, 200, NULL)")
            db.execSQL("INSERT INTO game_players (gameId, playerId, seatIndex, tableX, tableY) VALUES (7, 1, 0, 0.5, 0.0), (7, 2, 1, 0.5, 1.0)")
            db.execSQL("INSERT INTO rounds (id, gameId, number, closedAt) VALUES (1, 7, 1, NULL)")
            db.execSQL(
                "INSERT INTO round_results (id, roundId, playerId, status, points, source, updatedAt) VALUES " +
                    "(1, 1, 1, 'SCORED', 42, 'SCAN', 120), (2, 1, 2, 'OPEN', 0, NULL, 130)",
            )
            db.execSQL("INSERT INTO score_adjustments (id, gameId, playerId, delta, note, createdAt, roundNumber) VALUES (1, 7, 2, 5, 'house', 140, 1)")
        }

        // Validates the rebuilt players table against the exported 4.json.
        helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT id, name, avatarFace, avatarPattern, avatarColor, createdAt, archived FROM players ORDER BY id").use { c ->
                val rows = buildList {
                    while (c.moveToNext()) add(listOf(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5), c.getInt(6)))
                }
                assertEquals(listOf("Anna", "Ben", "Cleo", "Dan"), rows.map { it[1] })
                // Nearest catalog color = the same hex for the 10 old palette colors.
                assertEquals(listOf("amber", "sand", "sand", "chartreuse"), rows.map { it[4] })
                // Deterministic face + pattern per player id.
                rows.forEach { r ->
                    val expected = migratedAvatar(r[0] as Long, 0)
                    assertEquals(expected.face, r[2])
                    assertEquals(expected.pattern, r[3])
                }
                assertEquals(listOf(10L, 11L, 12L, 13L), rows.map { it[5] })
                assertEquals(listOf(0, 0, 1, 0), rows.map { it[6] })
            }
            db.query("PRAGMA foreign_key_check").use { c -> assertEquals(0, c.count) }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(context, Scan7Database::class.java, dbName).addMigrations(*MIGRATIONS).build()
        try {
            runBlocking {
                val repo = GameRepository(room, clock = { 1000L })
                val s = repo.activeGame.first()!!.state
                assertEquals(listOf(1L, 2L), s.playerIds)
                assertEquals(mapOf(1L to 42, 2L to 5), s.totals)
                assertEquals(listOf("Anna", "Ben", "Dan"), repo.players.first().map { it.name })
                // New ids continue after the migrated ones; the new player avoids the stored colors.
                val eve = repo.addPlayer("Eve")
                assertEquals(5L, eve)
                assertEquals("violet", repo.players.first().single { it.id == eve }.avatar.color)
            }
        } finally {
            room.close()
        }
    }

    @Test
    fun v4ToV5_addsJoinedAndLeftRounds_andKeepsGames() {
        helper.createDatabase(dbName, 4).use { db ->
            db.execSQL(
                "INSERT INTO players (id, name, avatarFace, avatarPattern, avatarColor, createdAt, archived) VALUES " +
                    "(1, 'Anna', 'grin', 'plain', 'amber', 10, 0), (2, 'Ben', 'grin', 'plain', 'sand', 11, 0), (3, 'Cleo', 'grin', 'plain', 'lime', 12, 0)",
            )
            db.execSQL("INSERT INTO games (id, startedAt, finishedAt, abandoned, endedEarly, target, viewMode) VALUES (7, 100, NULL, 0, 0, 200, NULL)")
            db.execSQL("INSERT INTO game_players (gameId, playerId, seatIndex, tableX, tableY) VALUES (7, 1, 0, 0.5, 0.0), (7, 2, 1, 0.5, 1.0)")
            db.execSQL("INSERT INTO rounds (id, gameId, number, closedAt) VALUES (1, 7, 1, 150), (2, 7, 2, NULL)")
            db.execSQL(
                "INSERT INTO round_results (id, roundId, playerId, status, points, source, updatedAt) VALUES " +
                    "(1, 1, 1, 'SCORED', 42, 'SCAN', 120), (2, 1, 2, 'BUST', 0, 'BUST_TAP', 130), " +
                    "(3, 2, 1, 'OPEN', 0, NULL, 150), (4, 2, 2, 'SCORED', 17, 'MANUAL', 160)",
            )
        }

        // Validates the new columns against the exported 5.json; existing seats played from round 1.
        helper.runMigrationsAndValidate(dbName, 5, true).use { db ->
            db.query("SELECT playerId, joinedRound, leftRound FROM game_players ORDER BY seatIndex").use { c ->
                val rows = buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getInt(1), if (c.isNull(2)) null else c.getInt(2))) }
                assertEquals(listOf(Triple(1L, 1, null), Triple(2L, 1, null)), rows)
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(context, Scan7Database::class.java, dbName).addMigrations(*MIGRATIONS).build()
        try {
            runBlocking {
                val repo = GameRepository(room, clock = { 1000L })
                val s = repo.activeGame.first()!!.state
                assertEquals(listOf(1L, 2L), s.playerIds)
                assertEquals(emptyList<Any>(), s.departed)
                assertEquals(mapOf(1L to 42, 2L to 17), s.totals)

                // The new columns work on the migrated database: Cleo joins, Ben leaves (his 17 dropped).
                val changed = repo.changePlayers(7, s.seatsFor(listOf(1L, 3L)))
                assertEquals(listOf(1L, 3L), changed.playerIds)
                val reloaded = repo.activeGame.first()!!.state
                assertEquals(listOf(1L, 3L), reloaded.playerIds)
                assertEquals(listOf(1L, 3L, 2L), reloaded.everyoneIds)
                assertEquals(2, reloaded.joinedRound(3))
                assertEquals(2, reloaded.leftRound(2))
                assertEquals(mapOf(1L to 42, 3L to 0, 2L to 0), reloaded.totals)
            }
        } finally {
            room.close()
        }
    }

    @Test
    fun v5ToV6_addsMultiplayerColumns_andKeepsGames() {
        helper.createDatabase(dbName, 5).use { db ->
            db.execSQL(
                "INSERT INTO players (id, name, avatarFace, avatarPattern, avatarColor, createdAt, archived) VALUES " +
                    "(1, 'Anna', 'grin', 'plain', 'amber', 10, 0), (2, 'Ben', 'grin', 'plain', 'sand', 11, 0)",
            )
            db.execSQL("INSERT INTO games (id, startedAt, finishedAt, abandoned, endedEarly, target, viewMode) VALUES (7, 100, NULL, 0, 0, 200, NULL)")
            db.execSQL(
                "INSERT INTO game_players (gameId, playerId, seatIndex, tableX, tableY, joinedRound, leftRound) VALUES " +
                    "(7, 1, 0, 0.5, 0.0, 1, NULL), (7, 2, 1, 0.5, 1.0, 1, NULL)",
            )
            db.execSQL("INSERT INTO rounds (id, gameId, number, closedAt) VALUES (1, 7, 1, NULL)")
            db.execSQL(
                "INSERT INTO round_results (id, roundId, playerId, status, points, source, updatedAt) VALUES " +
                    "(1, 1, 1, 'SCORED', 42, 'SCAN', 120), (2, 1, 2, 'OPEN', 0, NULL, 130)",
            )
        }

        // Validates against the exported 6.json: existing games are local, players visible.
        helper.runMigrationsAndValidate(dbName, 6, true).use { db ->
            db.query("SELECT joinedFrom, gameUid FROM games").use { c ->
                c.moveToFirst()
                assertEquals(true, c.isNull(0) && c.isNull(1))
            }
            db.query("SELECT COUNT(*) FROM players WHERE joinedOnly = 0").use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM share_sessions").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(context, Scan7Database::class.java, dbName).addMigrations(*MIGRATIONS).build()
        try {
            runBlocking {
                val repo = GameRepository(room, clock = { 1000L })
                val s = repo.activeGame.first()!!.state
                assertEquals(mapOf(1L to 42, 2L to 0), s.totals)
                // The action path and a joined copy work on the migrated database.
                repo.scorePoints(7, 2, 9)
                assertEquals(9, repo.activeGame.first()!!.state.total(2))
                val local = repo.joinedPlayers(listOf(net.moesli.scan7.game.Player(50, "Philip")), emptyMap())
                val copy = net.moesli.scan7.game.GameState.newGame(listOf(net.moesli.scan7.game.Player(local.getValue(50), "Philip"), net.moesli.scan7.game.Player(1, "Anna")))
                val joinedId = repo.saveJoinedGame(null, "uid-1", "Philip", copy)
                assertEquals(joinedId, repo.joinedGameId("uid-1"))
                assertEquals(7L, repo.activeGame.first()!!.state.id) // the joined copy is not "the game to resume"
                assertEquals(listOf("Anna", "Ben"), repo.players.first().map { it.name }) // hidden guest player
            }
        } finally {
            room.close()
        }
    }

    @Test
    fun v6ToV7_oldGamesAreFlip7_andSkyjoGamesStore() {
        helper.createDatabase(dbName, 6).use { db ->
            db.execSQL(
                "INSERT INTO players (id, name, avatarFace, avatarPattern, avatarColor, createdAt, archived, joinedOnly) VALUES " +
                    "(1, 'Anna', 'grin', 'plain', 'amber', 10, 0, 0), (2, 'Ben', 'grin', 'plain', 'sand', 11, 0, 0)",
            )
            db.execSQL("INSERT INTO games (id, startedAt, finishedAt, abandoned, endedEarly, target, viewMode) VALUES (7, 100, 300, 0, 0, 200, NULL)")
            db.execSQL(
                "INSERT INTO game_players (gameId, playerId, seatIndex, tableX, tableY, joinedRound, leftRound) VALUES " +
                    "(7, 1, 0, 0.5, 0.0, 1, NULL), (7, 2, 1, 0.5, 1.0, 1, NULL)",
            )
            db.execSQL("INSERT INTO rounds (id, gameId, number, closedAt) VALUES (1, 7, 1, 250)")
            db.execSQL(
                "INSERT INTO round_results (id, roundId, playerId, status, points, source, updatedAt) VALUES " +
                    "(1, 1, 1, 'SCORED', 12, 'SCAN', 120), (2, 1, 2, 'BUST', 0, 'BUST_TAP', 130)",
            )
            db.execSQL("INSERT INTO result_cards (id, resultId, position, label, ignored) VALUES (1, 1, 0, '12', 0)")
        }

        // Validates against the exported 7.json.
        helper.runMigrationsAndValidate(dbName, 7, true).use { db ->
            db.query("SELECT kind, enderPenalty FROM games").use { c ->
                c.moveToFirst()
                assertEquals("FLIP7", c.getString(0))
                assertEquals("OFFICIAL", c.getString(1))
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(context, Scan7Database::class.java, dbName).addMigrations(*MIGRATIONS).build()
        try {
            runBlocking {
                val repo = GameRepository(room, clock = { 1000L })
                val old = checkNotNull(repo.loadGame(7))
                assertEquals(GameKind.FLIP7, old.kind)
                assertEquals(Card.N12, old.result(1, 1).cards.single().card)

                // A Skyjo game round-trips its kind, rule, grid slots and round ender.
                val id = repo.startGame(listOf(1, 2), target = 100, kind = GameKind.SKYJO, enderPenalty = EnderPenalty.ALWAYS)
                repo.scoreCards(id, 1, listOf(ConfirmedCard(SkyjoCard.N5, slot = 3), ConfirmedCard(SkyjoCard.M2, slot = 7)), ResultSource.MANUAL)
                repo.scorePoints(id, 2, 1)
                repo.setRoundEnder(id, 1)
                repo.closeRoundAndStartNext(id)
                val s = checkNotNull(repo.loadGame(id))
                assertEquals(GameKind.SKYJO, s.kind)
                assertEquals(EnderPenalty.ALWAYS, s.enderPenalty)
                assertEquals(listOf(3, 7), s.result(1, 1).cards.map { it.slot })
                assertEquals(1L, s.round(1).endedBy)
                assertEquals(6, s.total(1))
            }
        } finally {
            room.close()
        }
    }

    @Test
    fun v1ToV4_runsEveryMigration() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO players (id, name, colorIndex, createdAt, archived) VALUES (1, 'Anna', 4, 10, 0)")
            db.execSQL("INSERT INTO games (id, startedAt, finishedAt, abandoned, target, viewMode) VALUES (7, 100, 200, 0, 200, NULL)")
        }
        helper.runMigrationsAndValidate(dbName, 4, true, *MIGRATIONS).use { db ->
            db.query("SELECT avatarColor FROM players").use { c ->
                c.moveToFirst()
                assertEquals("sky", c.getString(0))
            }
            db.query("SELECT endedEarly FROM games").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }
    }
}
