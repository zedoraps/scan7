package net.moesli.scan7.ui.text

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.core.content.edit
import java.util.Locale
import net.moesli.scan7.R

/**
 * The app's UI language: follow the system, or English / German (res/xml/locales_config.xml).
 * [tag] is the BCP 47 language tag, `null` = system. The names of the fixed languages are shown in
 * their own language ("English", "Deutsch") in every locale, like Android's language picker.
 */
enum class AppLanguage(val tag: String?, @StringRes val label: Int) {
    SYSTEM(null, R.string.language_system),
    ENGLISH("en", R.string.language_english),
    GERMAN("de", R.string.language_german),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag != null && tag != null && Locale.forLanguageTag(tag).language == it.tag } ?: SYSTEM
    }
}

/**
 * Per-app language. API 33+: the platform [LocaleManager] (also shown in the system's "App
 * languages" settings; the system recreates the activity). API 29–32 has no per-app locales and
 * the AppCompat backport needs an AppCompatActivity + AppCompat theme, so there the choice is kept
 * in prefs and applied in [wrap] (MainActivity.attachBaseContext) + an activity recreate.
 */
object AppLocales {
    private const val PREFS = "app_language"
    private const val KEY = "tag"

    fun current(context: Context): AppLanguage = if (Build.VERSION.SDK_INT >= 33) {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        AppLanguage.fromTag(if (locales.isEmpty) null else locales[0].toLanguageTag())
    } else {
        AppLanguage.fromTag(prefs(context).getString(KEY, null))
    }

    fun set(activity: Activity, language: AppLanguage) {
        if (current(activity) == language) return
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            prefs(activity).edit { if (language.tag == null) remove(KEY) else putString(KEY, language.tag) }
            activity.recreate()
        }
    }

    /** API 29–32: [base] with the stored language applied (unchanged on 33+ or for "System"). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = prefs(base).getString(KEY, null)
        if (tag == null) {
            Locale.setDefault(Resources.getSystem().configuration.locales[0])
            return base
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
