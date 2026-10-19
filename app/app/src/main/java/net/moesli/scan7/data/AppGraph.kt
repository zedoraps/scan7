package net.moesli.scan7.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Minimal app-wide container (no DI framework). The UI calls `AppGraph.repository(context)`;
 * tests construct [GameRepository] directly with an in-memory [Scan7Database].
 */
object AppGraph {
    @Volatile
    private var database: Scan7Database? = null

    @Volatile
    private var repository: GameRepository? = null

    /**
     * Process-wide scope for repository writes that must finish even when the screen that started
     * them leaves composition (back, stack reset). Interim fix until ViewModels (platform audit #13).
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun database(context: Context): Scan7Database =
        database ?: synchronized(this) {
            database ?: Scan7Database.build(context.applicationContext).also { database = it }
        }

    fun repository(context: Context): GameRepository =
        repository ?: synchronized(this) {
            repository ?: GameRepository(database(context)).also { repository = it }
        }

    /**
     * Instrumented UI tests only: every later [repository] / [database] call returns these (e.g. a
     * fresh in-memory DB per test); `null` goes back to the app's own database on the next call.
     */
    @VisibleForTesting
    fun overrideForTests(database: Scan7Database?, repository: GameRepository?) = synchronized(this) {
        this.database = database
        this.repository = repository
    }
}

/**
 * Runs [block] in [scope] and waits for it: if the caller (e.g. a composition scope) is cancelled,
 * only the waiting stops; the write itself completes and its transaction commits.
 * Use with [AppGraph.appScope] for "fire, then update the UI" repository calls.
 */
suspend fun <T> outliving(scope: CoroutineScope, block: suspend CoroutineScope.() -> T): T =
    scope.async(block = block).await()

/** [outliving] on [AppGraph.appScope]. */
suspend fun <T> outlivingUi(block: suspend CoroutineScope.() -> T): T = outliving(AppGraph.appScope, block)
