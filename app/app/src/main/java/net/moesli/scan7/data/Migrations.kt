package net.moesli.scan7.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.AvatarCatalog
import kotlin.random.Random

/** Manual migrations (the auto-migrations are listed on [Scan7Database]). */
internal val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_3_4)

/**
 * The 10-color player palette of DB versions 1–3 (`players.colorIndex` 0–9), frozen here for the
 * 3 → 4 migration. Same hex values as the first 10 avatar catalog colors.
 */
internal val LegacyPalette: List<Long> = listOf(
    0xFFFFC24B, 0xFFB39BFF, 0xFFA8E55C, 0xFFF2E3C4, 0xFF7FA8FF,
    0xFF4FE0A8, 0xFFC9B28A, 0xFFFF9C8A, 0xFFA9B8CC, 0xFFE2F06B,
)

/**
 * Avatar for a player stored with the old [colorIndex]: their color mapped to the nearest catalog
 * color (1:1 for the 10 old colors) and a face + pattern picked at random, seeded by [playerId] (the
 * same player always gets the same avatar).
 */
internal fun migratedAvatar(playerId: Long, colorIndex: Int): Avatar {
    val color = AvatarCatalog.nearestColor(LegacyPalette[colorIndex.coerceIn(0, LegacyPalette.lastIndex)]).id
    return AvatarCatalog.random(color, Random(playerId))
}

/**
 * 4: `players.colorIndex` → `avatarFace`, `avatarPattern`, `avatarColor` (avatar ids). SQLite on
 * minSdk 29 can't drop a column, so the table is rebuilt (foreign keys are off during Room
 * migrations; the other tables keep referencing `players` by name).
 */
internal object MIGRATION_3_4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `players_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `avatarFace` TEXT NOT NULL, `avatarPattern` TEXT NOT NULL, " +
                "`avatarColor` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `archived` INTEGER NOT NULL)",
        )
        db.query("SELECT id, name, colorIndex, createdAt, archived FROM players").use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val avatar = migratedAvatar(id, c.getInt(2))
                db.execSQL(
                    "INSERT INTO players_new (id, name, avatarFace, avatarPattern, avatarColor, createdAt, archived) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(id, c.getString(1), avatar.face, avatar.pattern, avatar.color, c.getLong(3), c.getInt(4)),
                )
            }
        }
        db.execSQL("DROP TABLE players")
        db.execSQL("ALTER TABLE players_new RENAME TO players")
    }
}
