package com.example.core.localization

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.core.model.AppLanguage
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

object LocaleManager {
  fun createLocalizedContext(baseContext: Context, language: AppLanguage): Context {
    val locale = language.toLocale()
    val configuration = Configuration(baseContext.resources.configuration)
    configuration.setLocale(locale)
    configuration.setLayoutDirection(locale)
    return baseContext.createConfigurationContext(configuration)
  }

  fun applyDefaultLocale(language: AppLanguage) {
    val targetLocale = language.toLocale()
    if (Locale.getDefault() != targetLocale) {
      Locale.setDefault(targetLocale)
    }
  }

  fun formatShortDate(epochMillis: Long, language: AppLanguage): String {
    val locale = language.toLocale()
    val formatter = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
    return formatter.format(Date(epochMillis))
  }

  fun formatInteger(value: Int, language: AppLanguage): String {
    val formatter = NumberFormat.getIntegerInstance(language.toLocale())
    return formatter.format(value)
  }
}
