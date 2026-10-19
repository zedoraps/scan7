package net.moesli.scan7.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Room schema version (bump together with a migration; schemas are exported to app/schemas). */
const val DB_SCHEMA_VERSION = 7

@Database(
    entities = [
        PlayerEntity::class,
        GameEntity::class,
        GamePlayerEntity::class,
        RoundEntity::class,
        RoundResultEntity::class,
        ResultCardEntity::class,
        SettingEntity::class,
        ScoreAdjustmentEntity::class,
        ShareSessionEntity::class,
    ],
    version = DB_SCHEMA_VERSION,
    exportSchema = true,
    autoMigrations = [
        // 2: games.endedEarly ("End game" from the round menu).
        AutoMigration(from = 1, to = 2),
        // 3: new table score_adjustments ("Adjust total" in the player sheet); existing data untouched.
        AutoMigration(from = 2, to = 3),
        // 4: players.colorIndex → avatar ids (manual, see Migrations.kt: MIGRATION_3_4).
        // 5: game_players.joinedRound (default 1) + leftRound (null): players join / leave mid-game.
        AutoMigration(from = 4, to = 5),
        // 6: multiplayer: games.joinedFrom + gameUid, players.joinedOnly, table share_sessions.
        AutoMigration(from = 5, to = 6),
        // 7: Skyjo: games.kind (default FLIP7) + enderPenalty, rounds.endedBy, result_cards.slot.
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class Scan7Database : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun gameDao(): GameDao
    abstract fun settingsDao(): SettingsDao
    abstract fun shareDao(): ShareDao

    companion object {
        const val NAME = "scan7.db"

        fun build(context: Context): Scan7Database =
            Room.databaseBuilder(context.applicationContext, Scan7Database::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()

        fun inMemory(context: Context): Scan7Database =
            Room.inMemoryDatabaseBuilder(context.applicationContext, Scan7Database::class.java).build()
    }
}
