// Covers: NFR-07 (Sinhala / Tamil / English) · Figma: language switch on "My Style Profile"
package com.group12.salonlink.core.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** The app's languages. [SYSTEM] follows the phone's language. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    SINHALA("si"),
    TAMIL("ta"),
}

/**
 * Remembers the chosen language on this phone and applies it to every screen.
 *
 * HOW: [wrap] is called from `attachBaseContext` of the Application and the Activity, so all
 * `stringResource(...)` calls read `values-si` / `values-ta`. Changing the language saves the
 * choice and recreates the Activity.
 * WHY not per-app locales from AppCompat: the app has no AppCompat dependency, and this way
 * works the same from Android 8 to 17. WHY on the phone, not in Firestore: the language must
 * be known before anyone signs in (Welcome and Sign-in screens are translated too).
 */
object LanguageStore {
    private const val PREFS = "salonlink_settings"
    private const val KEY = "language"

    fun get(context: Context): AppLanguage {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return AppLanguage.entries.firstOrNull { it.tag == saved } ?: AppLanguage.SYSTEM
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.tag).apply()
    }

    /** [base] with the saved language applied (unchanged when following the phone). */
    fun wrap(base: Context): Context {
        val language = get(base)
        if (language == AppLanguage.SYSTEM) return base
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
