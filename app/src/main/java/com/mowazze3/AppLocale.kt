package com.mowazze3

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class AppLanguage(val key: String) { SYSTEM("system"), ARABIC("ar"), ENGLISH("en") }

/**
 * The app's language: the device's by default, or one the person picks in Settings. Arabic keeps Latin
 * digits (`nu-latn`), as designed. A device in any other language gets English, since only those two exist.
 */
object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY = "language"

    // Plain SharedPreferences because attachBaseContext runs before anything asynchronous could load.
    fun language(context: Context): AppLanguage {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return AppLanguage.entries.firstOrNull { it.key == saved } ?: AppLanguage.SYSTEM
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.key).commit()
    }

    fun wrap(base: Context): Context {
        val deviceLanguage = base.resources.configuration.locales[0].language
        val config = Configuration(base.resources.configuration)
        config.setLocale(resolve(language(base), deviceLanguage))
        return base.createConfigurationContext(config)
    }

    fun resolve(language: AppLanguage, deviceLanguage: String): Locale = when (language) {
        AppLanguage.ARABIC -> ARABIC
        AppLanguage.ENGLISH -> Locale.ENGLISH
        AppLanguage.SYSTEM -> if (deviceLanguage == "ar") ARABIC else Locale.ENGLISH
    }

    private val ARABIC: Locale = Locale.forLanguageTag("ar-u-nu-latn")
}
