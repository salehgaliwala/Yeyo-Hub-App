package com.trackigniter.client

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import java.util.Locale

object LocaleHelper {
    const val KEY_LANGUAGE = "language"
    const val DEFAULT_LANGUAGE = "en"

    fun getLanguage(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    fun setLocale(context: Context, language: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()

        val appLocales = LocaleListCompat.forLanguageTags(language)
        AppCompatDelegate.setApplicationLocales(appLocales)
    }

    fun attachBaseContext(context: Context): Context {
        val language = getLanguage(context)
        return updateResources(context, language)
    }

    fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            config.setLocales(LocaleList(locale))
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
            context
        }
    }
}
