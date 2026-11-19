package com.example.core.model

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

data class ParsedIsbn(
  val cleaned: String,
  val isbn10: String?,
  val isbn13: String?,
)

object BibliographicMath {

  fun cleanIsbn(raw: String?): String =
    raw?.trim()?.replace("-", "")?.replace(" ", "")?.uppercase(Locale.ROOT).orEmpty()

  fun parseIsbn(raw: String?): ParsedIsbn? {
    val cleaned = cleanIsbn(raw)
    if (cleaned.isEmpty()) return null
    return when {
      cleaned.length == 10 && ReadingProgressMath.isValidIsbn(cleaned) -> {
        ParsedIsbn(
          cleaned = cleaned,
          isbn10 = cleaned,
          isbn13 = convertIsbn10To13(cleaned),
        )
      }
      cleaned.length == 13 && ReadingProgressMath.isValidIsbn(cleaned) -> {
        ParsedIsbn(
          cleaned = cleaned,
          isbn10 = convertIsbn13To10(cleaned),
          isbn13 = cleaned,
        )
      }
      else -> null
    }
  }

  fun looksLikeIsbnQuery(raw: String): Boolean {
    val cleaned = cleanIsbn(raw)
    if (cleaned.length != 10 && cleaned.length != 13) return false
    return parseIsbn(cleaned) != null
  }

  fun convertIsbn10To13(isbn10: String): String? {
    val cleaned = cleanIsbn(isbn10)
    if (cleaned.length != 10 || !ReadingProgressMath.isValidIsbn(cleaned)) return null
    val core = "978" + cleaned.substring(0, 9)
    var sum = 0
    for (i in 0 until 12) {
      val digit = core[i] - '0'
      sum += if (i % 2 == 0) digit else digit * 3
    }
    val check = (10 - (sum % 10)) % 10
    return "$core$check"
  }

  fun convertIsbn13To10(isbn13: String): String? {
    val cleaned = cleanIsbn(isbn13)
    if (cleaned.length != 13 || !cleaned.startsWith("978")) return null
    if (!ReadingProgressMath.isValidIsbn(cleaned)) return null
    val core = cleaned.substring(3, 12)
    var sum = 0
    for (i in 0 until 9) {
      sum += (10 - i) * (core[i] - '0')
    }
    val remainder = sum % 11
    val check = (11 - remainder) % 11
    val checkChar = if (check == 10) 'X' else ('0' + check)
    return "$core$checkChar"
  }

  fun isBrazilianIsbn(isbn13Or10: String?): Boolean {
    val parsed = parseIsbn(isbn13Or10) ?: return false
    val isbn13 = parsed.isbn13 ?: return false
    return isbn13.startsWith("97885") || isbn13.startsWith("97865")
  }

  fun normalizeText(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val decomposed = Normalizer.normalize(raw.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
    return decomposed
      .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
      .replace(Regex("[^a-z0-9\\s]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  private val leadingArticles = setOf("a", "o", "as", "os", "um", "uma", "the", "an", "le", "la", "les", "el")

  fun normalizeTitleCore(rawTitle: String?): String {
    val base = normalizeText(rawTitle?.substringBefore(':')?.substringBefore('('))
    val tokens = base.split(' ').filter { it.isNotBlank() }
    val trimmed =
      if (tokens.size > 1 && tokens.first() in leadingArticles) tokens.drop(1) else tokens
    return trimmed.joinToString(" ")
  }

  fun normalizeAuthorSurname(rawAuthor: String?): String {
    val clean = normalizeText(rawAuthor)
    if (clean.isEmpty()) return ""
    val firstAuthor = rawAuthor?.split(',', ';', '&')?.firstOrNull()?.let(::normalizeText).orEmpty()
    val tokens = firstAuthor.split(' ').filter { it.isNotBlank() }
    return tokens.lastOrNull() ?: clean
  }

  fun tokenSimilarity(a: String?, b: String?): Float {
    val normA = normalizeText(a)
    val normB = normalizeText(b)
    if (normA.isEmpty() || normB.isEmpty()) return 0f
    if (normA == normB) return 1f
    val setA = normA.split(' ').filter { it.length > 1 }.toSet()
    val setB = normB.split(' ').filter { it.length > 1 }.toSet()
    if (setA.isEmpty() || setB.isEmpty()) {
      return levenshteinSimilarity(normA, normB)
    }
    val intersection = setA.intersect(setB).size.toFloat()
    val union = setA.union(setB).size.toFloat()
    val jaccard = if (union > 0f) intersection / union else 0f
    val lev = levenshteinSimilarity(normA, normB)
    return max(jaccard, lev)
  }

  fun levenshteinSimilarity(a: String, b: String): Float {
    if (a == b) return 1f
    if (a.isEmpty() || b.isEmpty()) return 0f
    val maxLen = max(a.length, b.length)
    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
      curr[0] = i
      for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
      }
      val temp = prev
      prev = curr
      curr = temp
    }
    val dist = prev[b.length]
    return (1f - dist.toFloat() / maxLen.toFloat()).coerceIn(0f, 1f)
  }

  fun normalizeLanguageCode(raw: String?): String? {
    val clean = raw?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
    return when {
      clean in setOf("por", "pt", "pt-br", "pt_br", "portuguese", "português") -> "pt"
      clean in setOf("eng", "en", "en-us", "en-gb", "english", "inglês") -> "en"
      clean in setOf("spa", "es", "spanish", "espanhol", "español") -> "es"
      clean in setOf("fra", "fre", "fr", "french", "francês", "français") -> "fr"
      clean in setOf("deu", "ger", "de", "german", "alemão", "deutsch") -> "de"
      clean in setOf("ita", "it", "italian", "italiano") -> "it"
      else -> clean.take(5)
    }
  }

  fun stripHtmlTags(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return raw
      .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
      .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
      .replace(Regex("<[^>]+>"), " ")
      .replace("&nbsp;", " ")
      .replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace(Regex("[ \\t]+"), " ")
      .replace(Regex("\\n{3,}"), "\n\n")
      .trim()
      .takeIf { it.isNotEmpty() }
  }
}
