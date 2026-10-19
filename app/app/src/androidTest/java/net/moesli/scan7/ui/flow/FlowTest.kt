package net.moesli.scan7.ui.flow

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.moesli.scan7.R
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.data.Scan7Database
import net.moesli.scan7.detection.Box
import net.moesli.scan7.detection.Detection
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.ui.components.CONFIRM_ARM_DELAY_MS
import net.moesli.scan7.ui.nav.AppNavHost
import net.moesli.scan7.ui.nav.DebugLaunch
import net.moesli.scan7.ui.scan.CaptureMeta
import net.moesli.scan7.ui.scan.ScanCapture
import net.moesli.scan7.ui.theme.Scan7Theme
import org.junit.After
import org.junit.Before
import org.junit.Rule

/**
 * Base for the end-to-end Compose UI tests of the main flows: the real [AppNavHost] on a fresh
 * in-memory database per test (via [AppGraph.overrideForTests]), without the camera: camera scans
 * start in Review on [captureCards] (a drawn photo with one fake detection per card, like the
 * debug `debug_game_photo` hook), everything else goes through manual entry.
 *
 * Texts come from the app's string resources ([str], [plural]) so the tests survive wording changes.
 */
abstract class FlowTest {
    @get:Rule
    val rule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity> =
        createAndroidComposeRule<ComponentActivity>()

    protected val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    protected lateinit var db: Scan7Database
    protected lateinit var repo: GameRepository

    /** Cards of the synthetic capture every camera scan reviews (change before the scan). */
    protected var captureCards: List<Card> = listOf(Card.N5, Card.N9, Card.PLUS_2)

    @Before
    fun setUpDatabase() {
        db = Scan7Database.inMemory(context)
        // No training scans in tests (would write to the shared Pictures / Documents folders).
        repo = GameRepository(db, trainingScansAllowed = false)
        AppGraph.overrideForTests(db, repo)
    }

    @After
    fun tearDownDatabase() {
        AppGraph.overrideForTests(null, null)
        db.close()
    }

    /** Shows the app (Home, or the debug-seeded stack the nav host gets) in [locale] (null = the device's). */
    protected fun launch(locale: Locale? = null) {
        val debug = DebugLaunch(gamePhotoCapture = { syntheticCapture(captureCards) })
        rule.setContent {
            Localized(locale) {
                Scan7Theme { AppNavHost(debug = debug, modelInfo = "test") }
            }
        }
        rule.waitForIdle()
    }

    /** [launch], then Resume on Home: the round screen of the unfinished game. */
    protected fun launchRound(locale: Locale? = null) {
        launch(locale)
        clickLabel(localized(locale).getString(R.string.home_resume))
    }

    protected fun localized(locale: Locale?): Context = if (locale == null) {
        context
    } else {
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
    }

    @Composable
    private fun Localized(locale: Locale?, content: @Composable () -> Unit) {
        if (locale == null) return content()
        val base = LocalContext.current
        val localized = remember(base, locale) {
            val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
            base.createConfigurationContext(config)
        }
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalConfiguration provides localized.resources.configuration,
            LocalResources provides localized.resources,
            content = content,
        )
    }

    // ---- Strings --------------------------------------------------------------------------

    protected fun str(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    protected fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, count, *(if (args.isEmpty()) arrayOf(count) else args))

    // ---- Repository helpers ---------------------------------------------------------------

    protected fun <T> io(block: suspend GameRepository.() -> T): T = runBlocking { repo.block() }

    protected fun snapshot(gameId: Long): GameSnapshot = io { observeGame(gameId).first()!! }

    /** Three stored players and a game with them in this seating order. */
    protected fun seedGame(vararg names: String = arrayOf("Anna", "Ben", "Cleo"), target: Int = 200): Pair<Long, List<Long>> = io {
        val ids = names.map { addPlayer(it) }
        startGame(ids, target) to ids
    }

    // ---- Finders / actions ----------------------------------------------------------------

    /** Text or content description equal to [label]. */
    protected fun labeled(label: String): SemanticsMatcher = hasText(label) or hasContentDescription(label)

    /** A player card / row of the round screen ("Anna, 26 points, scored 26 this round"). */
    protected fun card(name: String): SemanticsNodeInteraction =
        rule.onNode(hasContentDescription("$name, ", substring = true) and hasClickActionMatcher())

    protected fun hasClickActionMatcher(): SemanticsMatcher = SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.OnClick)

    protected fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = TIMEOUT_MS) {
        try {
            rule.waitUntil(timeoutMs) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("No node: ${matcher.description}\n" + rule.onAllNodes(isRoot()).printToString(Int.MAX_VALUE), e)
        }
    }

    protected fun waitGone(matcher: SemanticsMatcher, timeoutMs: Long = TIMEOUT_MS) {
        rule.waitUntil(timeoutMs) { rule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
    }

    protected fun waitForText(text: String, substring: Boolean = false) = waitFor(hasText(text, substring = substring))

    /** Waits for [matcher] and clicks it. */
    protected fun click(matcher: SemanticsMatcher) {
        waitFor(matcher)
        rule.onNode(matcher).performClick()
        rule.waitForIdle()
    }

    protected fun clickLabel(label: String) = click(labeled(label))

    /** Waits until [condition] holds on the stored game (writes run off the UI thread). */
    protected fun waitForGame(gameId: Long, timeoutMs: Long = TIMEOUT_MS, condition: (GameSnapshot) -> Boolean) {
        rule.waitUntil(timeoutMs) { condition(snapshot(gameId)) }
    }

    /**
     * Confirms the open [net.moesli.scan7.ui.components.Scan7ConfirmDialog] with its [action]
     * button, after its arm delay (taps in the first 400 ms are ignored on purpose).
     */
    protected fun confirmDialog(action: String) {
        val button = hasText(action) and hasAnyAncestor(isDialog())
        waitFor(button)
        rule.mainClock.advanceTimeBy(CONFIRM_ARM_DELAY_MS + 100)
        rule.onNode(button).performClick()
        rule.waitForIdle()
        waitGone(button)
    }

    /** A player row of a picker (New game, round menu → Players): a checkbox "Anna, Amber avatar, …". */
    protected fun playerRow(name: String): SemanticsMatcher =
        hasContentDescription("$name, ", substring = true) and androidx.compose.ui.test.isToggleable()

    protected fun countText(text: String): Int = rule.onAllNodesWithText(text).fetchSemanticsNodes().size
}

/**
 * A drawn "photo" (plain tiles on a dark table) with one fake detection per tile, like the debug
 * scan demo's synthetic capture. Not card artwork.
 */
fun syntheticCapture(cards: List<Card>): ScanCapture {
    val w = 960
    val h = 1280
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.rgb(74, 58, 44))
    val tile = Paint().apply { color = Color.rgb(236, 232, 222) }
    val cw = 240f
    val ch = 300f
    val gap = 50f
    val left0 = (w - 3 * cw - 2 * gap) / 2
    val detections = cards.mapIndexed { i, card ->
        val x = left0 + (i % 3) * (cw + gap)
        val y = 60f + (i / 3) * (ch + 40f)
        canvas.drawRect(x, y, x + cw, y + ch, tile)
        Detection(card.label, 0.9f, Box(x / w, y / h, (x + cw) / w, (y + ch) / h))
    }
    return ScanCapture(bitmap, detections, meta = CaptureMeta(origin = "synthetic"))
}

/** Upper bound for every wait: generous for a loaded emulator; waits end as soon as the condition holds. */
const val TIMEOUT_MS = 10_000L
