package net.moesli.scan7.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlayerEntity::class,
        GameEntity::class,
        GamePlayerEntity::class,
        RoundEntity::class,
        RoundResultEntity::class,
        ResultCardEntity::class,
        SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class Scan7Database : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun gameDao(): GameDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "scan7.db"

        fun build(context: Context): Scan7Database =
            Room.databaseBuilder(context.applicationContext, Scan7Database::class.java, NAME).build()

        fun inMemory(context: Context): Scan7Database =
            Room.inMemoryDatabaseBuilder(context.applicationContext, Scan7Database::class.java).build()
    }
}
