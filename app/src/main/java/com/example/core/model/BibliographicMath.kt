package com.example.core.model

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

data class ParsedIsbn(
  val cleaned: String,
  val isbn10: String?,
  val isbn13: String?,
)

enum class LanguageTier(val rank: Int) {
  EXPLICIT_MATCH(0),
  PT_BR(1),
  EN(2),
  PT_UNCERTAIN(3),
  PT_PT_OR_OTHER(4),
  UNKNOWN(5),
}

enum class LanguageConfidence {
  CONFIRMED,
  INFERRED_WITH_CONFLICT,
  UNCERTAIN,
}

data class LanguageAssessment(
  val normalizedCode: String?,
  val canonicalTag: String?,
  val tier: LanguageTier,
  val confidence: LanguageConfidence,
  val hasConflict: Boolean = false,
)

enum class TitleBundleKind {
  SINGLE,
  COLLECTION_OR_BOX,
}

data class TitleStructure(
  val coreNormalized: String,
  val volumeNumber: Int?,
  val bundleKind: TitleBundleKind,
  val cleanDisplayTitle: String,
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

  fun isPortuguesePortugalIsbn(isbn13Or10: String?): Boolean {
    val parsed = parseIsbn(isbn13Or10) ?: return false
    val isbn13 = parsed.isbn13 ?: return false
    return isbn13.startsWith("978972") || isbn13.startsWith("978989")
  }

  fun sanitizeDisplayText(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return raw
      .replace(Regex("[\\u0000-\\u001F\\u007F-\\u009F]"), "")
      .replace(Regex("\\s+"), " ")
      .trim()
      .takeIf { it.isNotEmpty() }
  }

  fun normalizeText(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val clean = raw.replace(Regex("[\\u0000-\\u001F\\u007F-\\u009F]"), " ")
    val decomposed = Normalizer.normalize(clean.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
    return decomposed
      .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
      .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  private val leadingArticles = setOf("a", "o", "as", "os", "um", "uma", "the", "an", "le", "la", "les", "el")

  fun normalizeTitleCore(rawTitle: String?): String {
    val sanitized = sanitizeDisplayText(rawTitle).orEmpty()
    val base = normalizeText(sanitized.substringBefore(':').substringBefore('('))
    val tokens = base.split(' ').filter { it.isNotBlank() }
    val trimmed =
      if (tokens.size > 1 && tokens.first() in leadingArticles) tokens.drop(1) else tokens
    return trimmed.joinToString(" ")
  }

  private val volumeRegex =
    Regex("\\b(?:vol(?:ume)?\\.?|livro|book|part(?:e)?|tomo|tome)\\s*([0-9]+|i{1,3}|iv|v|vi{0,3}|ix|x)\\b")
  private val trailingRomanRegex = Regex("\\b(i{1,3}|iv|v|vi{0,3})\\s*$")
  private val bundleRegex =
    Regex("\\b(trilogia|trilogy|box\\b|colecao|collection|omnibus|complete\\s+series|obra\\s+completa|volume\\s+unico)\\b")

  fun extractTitleStructure(rawTitle: String?, subtitle: String? = null): TitleStructure {
    val cleanTitle = sanitizeDisplayText(rawTitle).orEmpty()
    val cleanSubtitle = sanitizeDisplayText(subtitle).orEmpty()
    val combinedNorm = normalizeText("$cleanTitle $cleanSubtitle")
    val volMatch = volumeRegex.find(combinedNorm) ?: trailingRomanRegex.find(normalizeText(cleanTitle))
    val volumeNumber = volMatch?.groupValues?.getOrNull(1)?.let(::parseVolumeToken)
    val bundleKind =
      if (bundleRegex.containsMatchIn(combinedNorm)) TitleBundleKind.COLLECTION_OR_BOX
      else TitleBundleKind.SINGLE
    return TitleStructure(
      coreNormalized = normalizeTitleCore(cleanTitle),
      volumeNumber = volumeNumber,
      bundleKind = bundleKind,
      cleanDisplayTitle = cleanTitle,
    )
  }

  private fun parseVolumeToken(token: String): Int? =
    token.toIntOrNull()
      ?: when (token.lowercase(Locale.ROOT)) {
        "i" -> 1
        "ii" -> 2
        "iii" -> 3
        "iv" -> 4
        "v" -> 5
        "vi" -> 6
        "vii" -> 7
        "viii" -> 8
        "ix" -> 9
        "x" -> 10
        else -> null
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
    val clean = raw?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-')?.takeIf { it.isNotEmpty() } ?: return null
    return when {
      clean == "pt-pt" -> "pt-pt"
      clean in setOf("por", "pt", "pt-br", "portuguese", "português") -> "pt"
      clean in setOf("eng", "en", "en-us", "en-gb", "en-au", "en-ca", "english", "inglês") -> "en"
      clean in setOf("spa", "es", "es-es", "es-419", "spanish", "espanhol", "español") -> "es"
      clean in setOf("fra", "fre", "fr", "french", "francês", "français") -> "fr"
      clean in setOf("deu", "ger", "de", "german", "alemão", "deutsch") -> "de"
      clean in setOf("ita", "it", "italian", "italiano") -> "it"
      clean in setOf("rus", "ru") -> "ru"
      clean in setOf("cat", "ca") -> "ca"
      clean in setOf("jpn", "ja") -> "ja"
      else -> clean.take(5)
    }
  }

  private val brazilianPublisherTokens =
    setOf(
      "rocco",
      "intrinseca",
      "companhia das letras",
      "companhiadasletras",
      "seguinte",
      "principis",
      "ciranda cultural",
      "harpercollins brasil",
      "martins fontes",
      "wmf martins fontes",
      "record",
      "bertrand brasil",
      "galera",
      "arqueiro",
      "sextante",
      "globo livros",
      "globo",
      "aleph",
      "darkside",
      "l pm",
      "zahar",
      "todavia",
      "antofagica",
      "atila",
      "atica",
      "scipione",
      "martin claret",
      "nova fronteira",
      "saraiva",
      "moderna",
      "novo seculo",
      "faro editorial",
      "gutenberg",
      "nemo",
      "suma",
      "objetiva",
      "alfaguara brasil",
      "penguin companhia",
      "planeta do brasil",
      "editora 34",
      "paulus",
      "vozes",
      "loyola",
      "clube de autores",
    )

  private val portugalPublisherTokens =
    setOf(
      "editorial presenca",
      "presenca",
      "penguin comercial",
      "penguin random house grupo editorial portugal",
      "guerra e paz",
      "porto editora",
      "leya",
      "bertrand editora",
      "dom quixote",
      "publicacoes dom quixote",
      "relogio d agua",
      "asa",
      "asa editores",
      "civilizacao",
      "gradiva",
      "tintas da china",
      "tinta da china",
      "planeta portugal",
      "saida de emergencia",
      "marcador",
      "topseller",
      "clube do autor",
    )

  fun isKnownBrazilianPublisher(publisher: String?): Boolean {
    val norm = normalizeText(publisher)
    if (norm.isEmpty()) return false
    return brazilianPublisherTokens.any { it in norm }
  }

  fun isKnownPortugalPublisher(publisher: String?): Boolean {
    val norm = normalizeText(publisher)
    if (norm.isEmpty()) return false
    return portugalPublisherTokens.any { it in norm }
  }

  private val nonLatinScriptRegex = Regex("[\\u0400-\\u04FF\\u3040-\\u30FF\\u4E00-\\u9FFF\\u0600-\\u06FF]")
  private val ptPtTitleClueRegex = Regex("\\b(receitar-lhe|diz-me|fe-lo|ha-de)\\b", RegexOption.IGNORE_CASE)

  /**
   * Classifica o idioma da edição priorizando o metadado explícito da edição e tratando
   * prefixo ISBN, editora e pistas do título apenas como evidências auxiliares para confirmar,
   * resolver códigos genéricos ("pt"/"por") ou sinalizar conflito sem sobrescritas cegas.
   */
  fun assessLanguage(
    explicitLanguageRaw: String?,
    isbn13Or10: String? = null,
    publisher: String? = null,
    editionTitle: String? = null,
    explicitRequestedLanguage: String? = null,
  ): LanguageAssessment {
    val cleanExplicit =
      explicitLanguageRaw?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-')?.takeIf { it.isNotEmpty() }
    val normExplicit = normalizeLanguageCode(cleanExplicit)
    val isBrIsbn = isBrazilianIsbn(isbn13Or10)
    val isPtIsbn = isPortuguesePortugalIsbn(isbn13Or10)
    val isBrPub = isKnownBrazilianPublisher(publisher)
    val isPtPub = isKnownPortugalPublisher(publisher)
    val hasNonLatinTitle = editionTitle != null && nonLatinScriptRegex.containsMatchIn(editionTitle)
    val hasPtPtOrthography = editionTitle != null && ptPtTitleClueRegex.containsMatchIn(editionTitle)

    val (canonicalTag, confidence, hasConflict) =
      when {
        // Explicit pt-PT is trusted directly unless contradicted strongly by multiple BR signals
        cleanExplicit == "pt-pt" -> {
          val strongBrConflict = isBrIsbn && isBrPub
          if (strongBrConflict) {
            Triple("pt-br", LanguageConfidence.INFERRED_WITH_CONFLICT, true)
          } else {
            Triple("pt-pt", LanguageConfidence.CONFIRMED, isBrIsbn || isBrPub)
          }
        }

        // Explicit pt-BR: trust by default, downgrade to pt-PT only on convergent PT-PT signals
        cleanExplicit == "pt-br" -> {
          val ptSignalCount = (if (isPtIsbn) 1 else 0) + (if (isPtPub) 1 else 0) + (if (hasPtPtOrthography) 1 else 0)
          val brSignalCount = (if (isBrIsbn) 1 else 0) + (if (isBrPub) 1 else 0)
          when {
            ptSignalCount >= 2 && brSignalCount == 0 ->
              Triple("pt-pt", LanguageConfidence.INFERRED_WITH_CONFLICT, true)
            isPtIsbn && brSignalCount == 0 && !publisher.isNullOrBlank() ->
              Triple("pt-pt", LanguageConfidence.INFERRED_WITH_CONFLICT, true)
            isPtIsbn && brSignalCount == 0 ->
              Triple("pt-pt", LanguageConfidence.UNCERTAIN, true)
            else ->
              Triple("pt-br", LanguageConfidence.CONFIRMED, isPtIsbn || isPtPub)
          }
        }

        // Generic Portuguese ("pt" or "por"): resolve variant using auxiliary evidence
        normExplicit == "pt" -> {
          val brScore = (if (isBrIsbn) 2 else 0) + (if (isBrPub) 2 else 0)
          val ptScore = (if (isPtIsbn) 2 else 0) + (if (isPtPub) 2 else 0) + (if (hasPtPtOrthography) 1 else 0)
          when {
            brScore > 0 && ptScore == 0 -> Triple("pt-br", LanguageConfidence.CONFIRMED, false)
            ptScore > 0 && brScore == 0 -> Triple("pt-pt", LanguageConfidence.CONFIRMED, false)
            brScore > ptScore -> Triple("pt-br", LanguageConfidence.INFERRED_WITH_CONFLICT, true)
            ptScore > brScore -> Triple("pt-pt", LanguageConfidence.INFERRED_WITH_CONFLICT, true)
            else -> Triple("pt", LanguageConfidence.UNCERTAIN, false)
          }
        }

        // Explicit English: flag conflict if edition title clearly uses non-Latin script without bilingual slash or has BR ISBN
        normExplicit == "en" -> {
          val conflictWithBr = isBrIsbn && isBrPub
          val scriptConflict = hasNonLatinTitle && !editionTitle.orEmpty().contains("/")
          when {
            conflictWithBr -> Triple("pt-br", LanguageConfidence.INFERRED_WITH_CONFLICT, true)
            scriptConflict -> Triple("en", LanguageConfidence.UNCERTAIN, true)
            else -> Triple("en", LanguageConfidence.CONFIRMED, isBrIsbn || isPtIsbn)
          }
        }

        normExplicit != null -> Triple(normExplicit, LanguageConfidence.CONFIRMED, false)

        // Missing explicit metadata: use conservative auxiliary evidence, marking uncertain if single weak cue
        isBrIsbn && isBrPub -> Triple("pt-br", LanguageConfidence.INFERRED_WITH_CONFLICT, false)
        isPtIsbn && isPtPub -> Triple("pt-pt", LanguageConfidence.INFERRED_WITH_CONFLICT, false)
        isBrIsbn || isBrPub -> Triple("pt-br", LanguageConfidence.UNCERTAIN, false)
        isPtIsbn || isPtPub -> Triple("pt-pt", LanguageConfidence.UNCERTAIN, false)
        else -> Triple(null, LanguageConfidence.UNCERTAIN, false)
      }

    val normForStorage =
      when (canonicalTag) {
        "pt-br", "pt" -> "pt"
        "pt-pt" -> "pt-pt"
        else -> canonicalTag
      }

    val tier =
      computeTierForCanonical(
        canonicalTag = canonicalTag,
        confidence = confidence,
        explicitRequestedLanguage = explicitRequestedLanguage,
      )

    return LanguageAssessment(
      normalizedCode = normForStorage,
      canonicalTag = canonicalTag,
      tier = tier,
      confidence = confidence,
      hasConflict = hasConflict,
    )
  }

  fun computeTierForCanonical(
    canonicalTag: String?,
    confidence: LanguageConfidence,
    explicitRequestedLanguage: String? = null,
  ): LanguageTier {
    if (explicitRequestedLanguage != null && canonicalTag != null) {
      val req = explicitRequestedLanguage.lowercase(Locale.ROOT)
      val matchesExplicit =
        when (req) {
          "pt-br" -> canonicalTag == "pt-br" || canonicalTag == "pt"
          "pt-pt" -> canonicalTag == "pt-pt"
          else -> canonicalTag == req || canonicalTag.startsWith("$req-")
        }
      if (matchesExplicit) return LanguageTier.EXPLICIT_MATCH
    }
    return when (canonicalTag) {
      "pt-br" -> LanguageTier.PT_BR
      "pt" -> if (confidence == LanguageConfidence.CONFIRMED) LanguageTier.PT_BR else LanguageTier.PT_UNCERTAIN
      "en" -> LanguageTier.EN
      "pt-pt" -> LanguageTier.PT_PT_OR_OTHER
      null -> LanguageTier.UNKNOWN
      else -> LanguageTier.PT_PT_OR_OTHER
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
