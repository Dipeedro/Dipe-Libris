package com.example.core.localization

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.R
import com.example.core.model.AppLanguage
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.EnumMap
import java.util.Locale
import kotlin.math.roundToInt

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

  fun formatPercent(percent: Float, language: AppLanguage): String {
    val rounded = percent.coerceIn(0f, 100f).roundToInt()
    return "${formatInteger(rounded, language)}%"
  }

  fun formatDuration(durationMillis: Long, resources: Resources): String {
    val safeMillis = durationMillis.coerceAtLeast(0L)
    val totalMinutes = safeMillis / 60_000L
    val totalHours = totalMinutes / 60L
    val totalDays = totalHours / 24L
    return when {
      totalDays >= 1L -> {
        val days = totalDays.toInt().coerceAtLeast(1)
        resources.getQuantityString(R.plurals.duration_days, days, days)
      }
      totalHours >= 1L -> {
        val hours = totalHours.toInt().coerceAtLeast(1)
        resources.getQuantityString(R.plurals.duration_hours, hours, hours)
      }
      else -> resources.getString(R.string.duration_less_than_hour)
    }
  }
}
