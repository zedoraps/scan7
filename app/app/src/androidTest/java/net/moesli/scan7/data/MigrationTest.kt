package net.moesli.scan7.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.moesli.scan7.game.ResultStatus
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
        val room = Room.databaseBuilder(context, Scan7Database::class.java, dbName).build()
        try {
            runBlocking {
                val repo = GameRepository(room) { 1000L }
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
}
