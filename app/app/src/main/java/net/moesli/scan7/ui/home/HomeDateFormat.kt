package net.moesli.scan7.ui.home

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Locale-aware date / time formats of the Home history ([historyDate]): Android's best pattern
 * for a skeleton in the app locale, e.g. "21:40" / "9:40 PM" (follows the 12/24 h setting),
 * "Sun 11 Oct" / "So., 11. Okt." and "11 Oct 2025" / "11. Okt. 2025".
 */
class HomeDateFormat(locale: Locale, is24Hour: Boolean) {
    private val time = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale)
    private val day = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
    private val dayWithYear = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dMMMy"), locale)

    fun time(epochMs: Long): String = time.format(Date(epochMs))

    fun date(epochMs: Long, withYear: Boolean): String = (if (withYear) dayWithYear else day).format(Date(epochMs))
}

/** [HomeDateFormat] for the current app locale; recreated when the language or 24 h setting changes. */
@Composable
fun rememberHomeDateFormat(): HomeDateFormat {
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, is24Hour) { HomeDateFormat(locale, is24Hour) }
}
