package com.example.core.localization

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.core.model.AppLanguage
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.EnumMap
import java.util.Locale

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

object LocaleManager {
  private val dateFormatters =
    object : ThreadLocal<EnumMap<AppLanguage, DateFormat>>() {
      override fun initialValue(): EnumMap<AppLanguage, DateFormat> =
        EnumMap(AppLanguage::class.java)
    }

  private val integerFormatters =
    object : ThreadLocal<EnumMap<AppLanguage, NumberFormat>>() {
      override fun initialValue(): EnumMap<AppLanguage, NumberFormat> =
        EnumMap(AppLanguage::class.java)
    }

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
    val map = dateFormatters.get()!!
    val formatter =
      map.getOrPut(language) {
        DateFormat.getDateInstance(DateFormat.MEDIUM, language.toLocale())
      }
    return formatter.format(Date(epochMillis))
  }

  fun formatInteger(value: Int, language: AppLanguage): String {
    val map = integerFormatters.get()!!
    val formatter =
      map.getOrPut(language) {
        NumberFormat.getIntegerInstance(language.toLocale())
      }
    return formatter.format(value)
  }
}
