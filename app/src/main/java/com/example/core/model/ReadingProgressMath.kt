package com.example.core.model

import kotlin.math.roundToInt

data class ProgressCalculation(
  val currentPage: Int,
  val progressPercent: Float,
)

object ReadingProgressMath {

  fun effectiveTotalPages(editionTotalPages: Int?, snapshotTotalPages: Int?): Int? =
    editionTotalPages?.takeIf { it > 0 } ?: snapshotTotalPages?.takeIf { it > 0 }

  fun calculateFromPage(
    page: Int,
    totalPages: Int?,
    fallbackPercent: Float = 0f,
  ): ProgressCalculation {
    val normalizedTotal = totalPages?.takeIf { it > 0 }
    return if (normalizedTotal != null) {
      val clampedPage = page.coerceIn(0, normalizedTotal)
      val pct = ((clampedPage.toFloat() / normalizedTotal.toFloat()) * 100f).coerceIn(0f, 100f)
      ProgressCalculation(currentPage = clampedPage, progressPercent = pct)
    } else {
      val safePage = page.coerceAtLeast(0)
      val pct =
        if (safePage == 0 && fallbackPercent <= 0f) 0f else fallbackPercent.coerceIn(0f, 100f)
      ProgressCalculation(currentPage = safePage, progressPercent = pct)
    }
  }

  fun calculateFromPercent(
    percent: Float,
    totalPages: Int?,
    fallbackPage: Int = 0,
  ): ProgressCalculation {
    val clampedPercent = percent.coerceIn(0f, 100f)
    val normalizedTotal = totalPages?.takeIf { it > 0 }
    return if (normalizedTotal != null) {
      val computedPage =
        ((clampedPercent / 100f) * normalizedTotal.toFloat()).roundToInt().coerceIn(0, normalizedTotal)
      ProgressCalculation(currentPage = computedPage, progressPercent = clampedPercent)
    } else {
      ProgressCalculation(currentPage = fallbackPage.coerceAtLeast(0), progressPercent = clampedPercent)
    }
  }

  fun effectiveDurationMillis(
    startEpochMillis: Long,
    endEpochMillis: Long?,
    pausedAtEpochMillis: Long?,
    accumulatedPausedMillis: Long,
    nowEpochMillis: Long = System.currentTimeMillis(),
  ): Long {
    val effectiveEnd = endEpochMillis ?: nowEpochMillis
    val rawSpan = (effectiveEnd - startEpochMillis).coerceAtLeast(0L)
    val activePauseSpan =
      if (endEpochMillis == null && pausedAtEpochMillis != null) {
        (nowEpochMillis - pausedAtEpochMillis).coerceAtLeast(0L)
      } else {
        0L
      }
    return (rawSpan - accumulatedPausedMillis.coerceAtLeast(0L) - activePauseSpan).coerceAtLeast(0L)
  }

  fun isValidIsbn(raw: String): Boolean {
    val cleaned = raw.trim().replace("-", "").replace(" ", "").uppercase()
    if (cleaned.isEmpty()) return true
    return when (cleaned.length) {
      10 -> isValidIsbn10(cleaned)
      13 -> isValidIsbn13(cleaned)
      else -> false
    }
  }

  fun normalizeIsbn(raw: String?): String? =
    raw?.trim()?.takeIf { it.isNotEmpty() }

  private fun isValidIsbn10(code: String): Boolean {
    if (code.length != 10) return false
    var sum = 0
    for (i in 0 until 10) {
      val c = code[i]
      val digit =
        when {
          i == 9 && c == 'X' -> 10
          c.isDigit() -> c - '0'
          else -> return false
        }
      sum += (10 - i) * digit
    }
    return sum % 11 == 0
  }

  private fun isValidIsbn13(code: String): Boolean {
    if (code.length != 13) return false
    if (!code.all { it.isDigit() }) return false
    if (!code.startsWith("978") && !code.startsWith("979")) return false
    var sum = 0
    for (i in 0 until 12) {
      val digit = code[i] - '0'
      sum += if (i % 2 == 0) digit else digit * 3
    }
    val checkDigit = (10 - (sum % 10)) % 10
    return checkDigit == (code[12] - '0')
  }
}
