package net.moesli.scan7.data

import android.content.Context

/**
 * Minimal app-wide container (no DI framework). The UI calls `AppGraph.repository(context)`;
 * tests construct [GameRepository] directly with an in-memory [Scan7Database].
 */
object AppGraph {
    @Volatile
    private var database: Scan7Database? = null

    @Volatile
    private var repository: GameRepository? = null

    fun database(context: Context): Scan7Database =
        database ?: synchronized(this) {
            database ?: Scan7Database.build(context.applicationContext).also { database = it }
        }

    fun repository(context: Context): GameRepository =
        repository ?: synchronized(this) {
            repository ?: GameRepository(database(context)).also { repository = it }
        }
}
