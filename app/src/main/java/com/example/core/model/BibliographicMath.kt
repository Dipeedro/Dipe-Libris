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
  PT_UNCERTAIN(2),
  EN(3),
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
  SERIES_VOLUME,
  OMNIBUS_SINGLE_VOLUME,
  COLLECTION_OR_BOX,
}

data class TitleStructure(
  val coreNormalized: String,
  val baseNormalizedWithArticle: String,
  val subtitleNormalized: String,
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

  private val controlCharsRegex = Regex("[\\u0000-\\u001F\\u007F-\\u009F]")
  private val whitespaceCollapseRegex = Regex("\\s+")
  private val combiningDiacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
  private val nonLetterDigitSpaceRegex = Regex("[^\\p{L}\\p{N}\\s]")

  fun sanitizeDisplayText(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return raw
      .replace(controlCharsRegex, "")
      .replace(whitespaceCollapseRegex, " ")
      .trim()
      .takeIf { it.isNotEmpty() }
  }

  fun normalizeText(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val clean = raw.replace(controlCharsRegex, " ")
    val decomposed = Normalizer.normalize(clean.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
    return decomposed
      .replace(combiningDiacriticsRegex, "")
      .replace(nonLetterDigitSpaceRegex, " ")
      .replace(whitespaceCollapseRegex, " ")
      .trim()
  }

  private val leadingArticles = setOf("a", "o", "as", "os", "um", "uma", "the", "an", "le", "la", "les", "el")

  fun normalizeBaseTitle(rawTitle: String?): String {
    val sanitized = sanitizeDisplayText(rawTitle).orEmpty()
    val beforeDelimiter =
      sanitized
        .substringBefore(':')
        .substringBefore('(')
        .substringBefore(" - ")
        .substringBefore(" — ")
        .substringBefore(" – ")
        .trim()
    return normalizeText(beforeDelimiter)
  }

  /**
   * Normalização estritamente para indexação de blocos e busca aproximada de duplicatas.
   * NÃO usar como igualdade exata de título na validação bibliográfica.
   */
  fun normalizeTitleCore(rawTitle: String?): String {
    val base = normalizeBaseTitle(rawTitle)
    val tokens = base.split(' ').filter { it.isNotBlank() }
    val trimmed =
      if (tokens.size > 1 && tokens.first() in leadingArticles) tokens.drop(1) else tokens
    return trimmed.joinToString(" ")
  }

  private val volumeRegex =
    Regex("\\b(?:vol(?:ume)?\\.?|livro|book|part(?:e)?|tomo|tome)\\s*([0-9]+|i{1,3}|iv|v|vi{0,3}|ix|x)\\b")
  private val trailingRomanRegex = Regex("\\b(i{1,3}|iv|v|vi{0,3})\\s*$")
  private val omnibusSingleVolumeRegex =
    Regex("\\b(volume\\s+unico|edicao\\s+integral|livro\\s+unico|trilogia\\s+completa\\s+em\\s+volume\\s+unico)\\b")
  private val bundleRegex =
    Regex("\\b(trilogia|trilogy|box\\b|colecao|collection|omnibus|complete\\s+series|obra\\s+completa|kit\\s+(?:com\\s+)?\\d+|coffret)\\b")

  fun extractTitleStructure(rawTitle: String?, subtitle: String? = null): TitleStructure {
    val cleanTitle = sanitizeDisplayText(rawTitle).orEmpty()
    val cleanSubtitle = sanitizeDisplayText(subtitle).orEmpty()
    val delimiterMatch =
      listOfNotNull(
          cleanTitle.indexOf(':').takeIf { it >= 0 }?.let { it to 1 },
          cleanTitle.indexOf(" - ").takeIf { it >= 0 }?.let { it to 3 },
          cleanTitle.indexOf(" — ").takeIf { it >= 0 }?.let { it to 3 },
          cleanTitle.indexOf(" – ").takeIf { it >= 0 }?.let { it to 3 },
        )
        .minByOrNull { it.first }
    val inlineSubtitle =
      if (delimiterMatch != null) cleanTitle.substring(delimiterMatch.first + delimiterMatch.second).trim() else ""
    val effectiveSubtitleRaw =
      listOf(inlineSubtitle, cleanSubtitle).filter { it.isNotBlank() }.joinToString(" ")
    val subtitleNorm = normalizeText(effectiveSubtitleRaw)
    val combinedNorm = normalizeText("$cleanTitle $cleanSubtitle")
    val isOmnibusSingle = omnibusSingleVolumeRegex.containsMatchIn(combinedNorm)
    val isBox = !isOmnibusSingle && bundleRegex.containsMatchIn(combinedNorm)
    val volMatch =
      if (isOmnibusSingle || isBox) null
      else (volumeRegex.find(combinedNorm) ?: trailingRomanRegex.find(normalizeText(cleanTitle)))
    val volumeNumber = volMatch?.groupValues?.getOrNull(1)?.let(::parseVolumeToken)
    val bundleKind =
      when {
        isBox -> TitleBundleKind.COLLECTION_OR_BOX
        isOmnibusSingle -> TitleBundleKind.OMNIBUS_SINGLE_VOLUME
        volumeNumber != null -> TitleBundleKind.SERIES_VOLUME
        else -> TitleBundleKind.SINGLE
      }
    return TitleStructure(
      coreNormalized = normalizeTitleCore(cleanTitle),
      baseNormalizedWithArticle = normalizeBaseTitle(cleanTitle),
      subtitleNormalized = subtitleNorm,
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
      "rocco digital",
      "rocco jovens leitores",
      "intrinseca",
      "editora intrinseca",
      "companhia das letras",
      "companhiadasletras",
      "cia das letras",
      "companhia de bolso",
      "companhia das letrinhas",
      "penguin companhia",
      "seguinte",
      "editora seguinte",
      "paralela",
      "editora paralela",
      "principis",
      "ciranda cultural",
      "harpercollins brasil",
      "harper collins brasil",
      "martins fontes",
      "wmf martins fontes",
      "editora record",
      "grupo editorial record",
      "bertrand brasil",
      "galera record",
      "arqueiro",
      "editora arqueiro",
      "sextante",
      "editora sextante",
      "globo livros",
      "editora globo",
      "editora aleph",
      "darkside",
      "darkside books",
      "l pm",
      "l pm editores",
      "lpm",
      "zahar",
      "jorge zahar",
      "todavia",
      "antofagica",
      "editora atica",
      "scipione",
      "martin claret",
      "nova fronteira",
      "saraiva",
      "editora moderna",
      "novo seculo",
      "faro editorial",
      "gutenberg",
      "editora nemo",
      "suma brasil",
      "editora suma",
      "editora objetiva",
      "fontanar",
      "alfaguara brasil",
      "planeta do brasil",
      "editora planeta do brasil",
      "planeta brasil",
      "global editora",
      "editora global",
      "editora 34",
      "paulus",
      "vozes",
      "editora vozes",
      "loyola",
      "edicoes loyola",
      "clube de autores",
      "editora agir",
      "melhoramentos",
      "autentica",
      "autentica editora",
      "editora abril",
      "abril cultural",
      "ediouro",
      "ftd",
      "ftd educacao",
      "carambaia",
      "benvira",
      "casa da palavra",
      "verus",
      "verus editora",
      "plataforma21",
      "universo dos livros",
      "pedra azul",
      "alta books",
      "novatec",
      "paz e terra",
      "estacao liberdade",
      "cosac naify",
      "best seller",
      "bestseller",
      "valentina",
      "editora valentina",
      "jangada",
      "morro branco",
      "excelsior",
      "circulo do livro",
      "lafonte",
      "pandorga",
      "camelot",
      "grupo autentica",
      "fabrica231",
    )

  /**
   * Editoras brasileiras reais cujos nomes curtos também aparecem em selos internacionais
   * (ex: "Record Books", "La Galera", "Editorial Planeta", "Suma de Letras" Espanha).
   * Só contam como evidência brasileira quando aparecem de forma exata/qualificada
   * e corroboradas por ISBN brasileiro ou título claramente em português.
   */
  private val ambiguousBrazilianPublisherTokens =
    setOf(
      "record",
      "galera",
      "suma",
      "globo",
      "atica",
      "planeta",
      "global",
      "abril",
      "moderna",
      "objetiva",
      "nemo",
      "agir",
      "aleph",
    )

  private val nonBrazilianPublisherMarkersRegex =
    Regex(
      "\\b(ediciones|editions|publishing|publishers|press|verlag|librairie|casa editrice|grupo planeta|la galera|mexico|argentina|colombia|espana|madrid|barcelona|london|new york|oxford|cambridge|bloomsbury|scholastic|penguin random house grupo editorial)\\b",
      RegexOption.IGNORE_CASE,
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
      "edicoes asa",
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

  private fun containsWholeTokenOrPhrase(paddedNorm: String, token: String): Boolean =
    " $token " in paddedNorm

  fun isKnownBrazilianPublisher(publisher: String?): Boolean {
    val norm = normalizeText(publisher)
    if (norm.isEmpty()) return false
    if (nonBrazilianPublisherMarkersRegex.containsMatchIn(norm)) {
      val explicitBrazil =
        containsWholeTokenOrPhrase(" $norm ", "brasil") ||
          containsWholeTokenOrPhrase(" $norm ", "brazil")
      if (!explicitBrazil) return false
    }
    val padded = " $norm "
    return brazilianPublisherTokens.any { containsWholeTokenOrPhrase(padded, it) }
  }

  private fun isExactOrCorroboratedAmbiguousBrazilianPublisher(
    publisher: String?,
    hasBrazilianIsbn: Boolean,
    hasPortugueseTitle: Boolean,
  ): Boolean {
    if (!hasBrazilianIsbn && !hasPortugueseTitle) return false
    val norm = normalizeText(publisher)
    if (norm.isEmpty()) return false
    if (nonBrazilianPublisherMarkersRegex.containsMatchIn(norm)) return false
    val padded = " $norm "
    if (" editorial " in padded && " grupo editorial record " !in padded) return false
    return ambiguousBrazilianPublisherTokens.any { token ->
      norm == token || norm == "editora $token" || containsWholeTokenOrPhrase(padded, "editora $token")
    }
  }

  fun isKnownPortugalPublisher(publisher: String?): Boolean {
    val norm = normalizeText(publisher)
    if (norm.isEmpty()) return false
    val padded = " $norm "
    return norm == "asa" || portugalPublisherTokens.any { containsWholeTokenOrPhrase(padded, it) }
  }

  private val nonLatinScriptRegex = Regex("[\\u0400-\\u04FF\\u3040-\\u30FF\\u4E00-\\u9FFF\\u0600-\\u06FF]")
  private val ptPtTitleClueRegex = Regex("\\b(receitar-lhe|diz-me|fe-lo|ha-de)\\b", RegexOption.IGNORE_CASE)

  fun hasNonLatinScript(rawText: String?): Boolean =
    rawText != null && nonLatinScriptRegex.containsMatchIn(rawText)

  /**
   * Classifica o idioma da edição mantendo hierarquia estrita de evidências:
   * 1. Metadado explícito confiável da edição (não é sobrescrito cegamente por ISBN/editora BR);
   * 2. Resolução de código genérico ("pt"/"por") via sinais auxiliares convergentes;
   * 3. Inferência conservadora quando o idioma explícito está ausente, separando português inequívoco
   *    de acentos/vocabulário compartilhados e evitando promoção indevida de títulos estrangeiros.
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
    val hasNonLatinTitle = hasNonLatinScript(editionTitle)
    val hasPtPtOrthography = editionTitle != null && ptPtTitleClueRegex.containsMatchIn(editionTitle)
    val titleIsPortuguese = !hasNonLatinTitle && !hasPtPtOrthography && looksPortugueseEditionTitle(editionTitle)
    val foreignTitleLang = if (!hasNonLatinTitle) detectClearForeignLatinLanguage(editionTitle) else null

    val isStrongBrPub = isKnownBrazilianPublisher(publisher)
    val isBrPub =
      isStrongBrPub ||
        isExactOrCorroboratedAmbiguousBrazilianPublisher(
          publisher = publisher,
          hasBrazilianIsbn = isBrIsbn,
          hasPortugueseTitle = titleIsPortuguese,
        )
    val isPtPub = isKnownPortugalPublisher(publisher)

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
              Triple("pt-br", LanguageConfidence.CONFIRMED, isPtIsbn || isPtPub || hasNonLatinTitle)
          }
        }

        // Generic Portuguese ("pt" or "por"): resolve variant using auxiliary evidence
        normExplicit == "pt" -> {
          val brScore = (if (isBrIsbn) 2 else 0) + (if (isBrPub) 2 else 0)
          val ptScore = (if (isPtIsbn) 2 else 0) + (if (isPtPub) 2 else 0) + (if (hasPtPtOrthography) 1 else 0)
          when {
            brScore > 0 && ptScore == 0 ->
              Triple("pt-br", LanguageConfidence.CONFIRMED, hasNonLatinTitle)
            ptScore > 0 && brScore == 0 ->
              Triple("pt-pt", LanguageConfidence.CONFIRMED, hasNonLatinTitle)
            brScore > ptScore ->
              Triple("pt-br", LanguageConfidence.INFERRED_WITH_CONFLICT, true)
            ptScore > brScore ->
              Triple("pt-pt", LanguageConfidence.INFERRED_WITH_CONFLICT, true)
            else -> Triple("pt", LanguageConfidence.UNCERTAIN, hasNonLatinTitle || foreignTitleLang != null)
          }
        }

        // Explicit English: preserve "en"; flag contextual conflict instead of overwriting to pt-br
        normExplicit == "en" -> {
          val scriptConflict = hasNonLatinTitle && !editionTitle.orEmpty().contains("/")
          val contextualConflict = isBrIsbn || isPtIsbn || isStrongBrPub || isPtPub || titleIsPortuguese
          when {
            scriptConflict -> Triple("en", LanguageConfidence.UNCERTAIN, true)
            else -> Triple("en", LanguageConfidence.CONFIRMED, contextualConflict)
          }
        }

        // Any other explicit non-Portuguese language (es, fr, de, it, cat, ja...): preserve with conflict flag if contextual signals diverge
        normExplicit != null -> {
          val contextualConflict = isBrIsbn || isPtIsbn || isStrongBrPub || isPtPub || titleIsPortuguese
          Triple(normExplicit, LanguageConfidence.CONFIRMED, contextualConflict)
        }

        // Missing explicit metadata: reject non-Latin titles and apply strict evidence hierarchy
        hasNonLatinTitle -> Triple(null, LanguageConfidence.UNCERTAIN, isBrIsbn || isPtIsbn || isBrPub || isPtPub)

        // Portuguese-Portugal clues without explicit metadata
        hasPtPtOrthography && foreignTitleLang == null ->
          Triple("pt-pt", LanguageConfidence.UNCERTAIN, isBrIsbn || isBrPub)

        isPtIsbn && isPtPub && foreignTitleLang == null ->
          Triple("pt-pt", LanguageConfidence.UNCERTAIN, false)

        // Foreign edition title without explicit metadata: only allow PT_UNCERTAIN when BOTH BR ISBN and BR Publisher converge
        foreignTitleLang != null -> {
          when {
            isBrIsbn && isBrPub ->
              Triple("pt", LanguageConfidence.UNCERTAIN, true)
            isPtIsbn && isPtPub ->
              Triple("pt-pt", LanguageConfidence.UNCERTAIN, true)
            else ->
              Triple(null, LanguageConfidence.UNCERTAIN, isBrIsbn || isBrPub || isPtIsbn || isPtPub)
          }
        }

        // Convergent Brazilian signals without explicit language and non-foreign title -> inferred pt-br (never CONFIRMED without API lang)
        isBrIsbn && isBrPub ->
          Triple("pt-br", LanguageConfidence.UNCERTAIN, false)

        // Single weak contextual signal (ISBN BR OR Editora BR) without foreign title contradiction -> PT_UNCERTAIN
        isBrIsbn || isBrPub ->
          Triple("pt", LanguageConfidence.UNCERTAIN, false)

        isPtIsbn || isPtPub ->
          Triple("pt-pt", LanguageConfidence.UNCERTAIN, false)

        titleIsPortuguese ->
          Triple("pt", LanguageConfidence.UNCERTAIN, false)

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
      "pt-br", "pt" -> if (confidence == LanguageConfidence.CONFIRMED) LanguageTier.PT_BR else LanguageTier.PT_UNCERTAIN
      "en" -> LanguageTier.EN
      "pt-pt" -> LanguageTier.PT_PT_OR_OTHER
      null -> LanguageTier.UNKNOWN
      else -> LanguageTier.PT_PT_OR_OTHER
    }
  }

  private val htmlBrRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
  private val htmlClosingPRegex = Regex("</p>", RegexOption.IGNORE_CASE)
  private val htmlAnyTagRegex = Regex("<[^>]+>")
  private val horizontalSpacesRegex = Regex("[ \\t]+")
  private val multipleNewlinesRegex = Regex("\\n{3,}")

  fun stripHtmlTags(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return raw
      .replace(htmlBrRegex, "\n")
      .replace(htmlClosingPRegex, "\n\n")
      .replace(htmlAnyTagRegex, " ")
      .replace("&nbsp;", " ")
      .replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace(horizontalSpacesRegex, " ")
      .replace(multipleNewlinesRegex, "\n\n")
      .trim()
      .takeIf { it.isNotEmpty() }
  }

  // Apenas diacríticos distintivos do português (exclui á/é/í/ó/ú isolados que são compartilhados com espanhol)
  private val ptDistinctiveDiacriticsRegex = Regex("[ãõçâêô]", RegexOption.IGNORE_CASE)
  private val nonPtDistinctiveDiacriticsRegex = Regex("[ñ¿¡ßäöüèìùïîûœæ]", RegexOption.IGNORE_CASE)
  private val spanishAccentedCueRegex =
    Regex("(?:\\b[\\p{L}]+(?:c|s)ión(?:es)?\\b|\\bcámara\\b|(?<![\\p{L}-])la\\b)", RegexOption.IGNORE_CASE)

  private val ptDistinctiveWordsRegex =
    Regex(
      "\\b(e o|e a|e os|e as|e um|e uma|um livro|uma historia|edicao|edicoes|colecao|colecoes|traducao|versao|portugues|portuguesa|brasileiro|brasileira|livro|livros|prisioneiro|calice|fenix|senhor|senhora|aneis|anel|sociedade|vorazes|pequeno|pequena|nao|voce|voces|tambem|quem|tudo|agora|ainda|depois|hoje|ontem|amanha|mulher|mulheres|homem|homens|crianca|criancas|coracao|coracoes|solidao|prisao|cidade|cidades|verdade|liberdade|estrelas|noite|noites|olhos|garota|garoto|menino|menina|primeiro|primeira|terceiro|terceira|feiticeiro|alienista|casmurro|receitar|duas)\\b",
      RegexOption.IGNORE_CASE,
    )

  private val clearSpanishWordsRegex =
    Regex(
      "\\b(el|los|las|del|y|una|unos|unas|con|sin|libro|libros|edicion|ediciones|coleccion|colecciones|traduccion|ilustracion|prisionero|piedra|caliz|hechicero|hechicera|jovenes|lectores|espanol|espanola|castellano|reliquias de la|misterio del|orden del)\\b",
      RegexOption.IGNORE_CASE,
    )
  private val clearFrenchWordsRegex =
    Regex(
      "\\b(le|les|du|des|et|une|avec|pour|dans|sur|chez|aux|prisonnier|chambre|coupe|ecole|sorciers|tome|francaise)\\b",
      RegexOption.IGNORE_CASE,
    )
  private val clearGermanWordsRegex =
    Regex(
      "\\b(der|die|und|mit|von|ein|eine|gefangene|stein|kammer|feuerkelch|ausgabe|buch)\\b",
      RegexOption.IGNORE_CASE,
    )
  private val clearItalianWordsRegex =
    Regex(
      "\\b(il|lo|gli|della|dello|delle|prigioniero|pietra)\\b",
      RegexOption.IGNORE_CASE,
    )
  private val clearEnglishWordsRegex =
    Regex(
      "\\b(the|and|of|with|for|from|into|prisoner|philosopher|sorcerer|chamber|goblet|phoenix|deathly|hallows|edition|illustrated|complete|novel|story|stories|books|handbook)\\b",
      RegexOption.IGNORE_CASE,
    )

  private val spanishDiacriticsRegex = Regex("[ñ¿¡]")
  private val germanDiacriticsRegex = Regex("[äöß]")
  private val frenchDiacriticsRegex = Regex("[èùïîûœæ]")
  private val startsWithDasRegex = Regex("^das\\b")

  fun detectClearForeignLatinLanguage(rawText: String?): String? {
    val clean = sanitizeDisplayText(rawText) ?: return null
    if (nonLatinScriptRegex.containsMatchIn(clean)) return null
    val norm = normalizeText(clean)
    if (norm.isBlank()) return null

    if (spanishDiacriticsRegex.containsMatchIn(clean) || spanishAccentedCueRegex.containsMatchIn(clean) || clearSpanishWordsRegex.containsMatchIn(norm)) {
      return "es"
    }
    if (germanDiacriticsRegex.containsMatchIn(clean) || clearGermanWordsRegex.containsMatchIn(norm)) {
      return "de"
    }
    if (frenchDiacriticsRegex.containsMatchIn(clean) || clearFrenchWordsRegex.containsMatchIn(norm)) {
      return "fr"
    }
    if (clearItalianWordsRegex.containsMatchIn(norm)) {
      return "it"
    }
    if (clearEnglishWordsRegex.containsMatchIn(norm)) {
      return "en"
    }
    if (startsWithDasRegex.containsMatchIn(norm) && !ptDistinctiveDiacriticsRegex.containsMatchIn(clean) && !ptDistinctiveWordsRegex.containsMatchIn(norm)) {
      return "de"
    }
    return null
  }

  fun looksPortugueseEditionTitle(rawTitle: String?): Boolean {
    val clean = sanitizeDisplayText(rawTitle) ?: return false
    if (nonLatinScriptRegex.containsMatchIn(clean)) return false
    if (nonPtDistinctiveDiacriticsRegex.containsMatchIn(clean)) return false
    val norm = normalizeText(clean)
    if (norm.isBlank()) return false
    if (detectClearForeignLatinLanguage(clean) != null) return false
    val hasPtDiacritics = ptDistinctiveDiacriticsRegex.containsMatchIn(clean)
    val hasPtWords = ptDistinctiveWordsRegex.containsMatchIn(norm)
    return hasPtDiacritics || hasPtWords
  }

  private val audiobookSignalRegex =
    Regex(
      "\\b(audiobook|audio\\s*book|audiolivro|audio\\s+cd|audio\\s+cassette|compact\\s+disc|mp3\\s*cd|unabridged\\s+audio|audible\\s+audio|spoken\\s+word|cd\\s+de\\s+audio|livro\\s+falado|audiotape)\\b",
      RegexOption.IGNORE_CASE,
    )

  fun isAudiobookSignal(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    return audiobookSignalRegex.containsMatchIn(normalizeText(raw))
  }

  fun isAudiobookCandidate(
    workTitle: String?,
    editionTitle: String?,
    subtitle: String?,
    editionLabel: String?,
    categories: List<String> = emptyList(),
  ): Boolean {
    val combined = listOfNotNull(workTitle, editionTitle, subtitle, editionLabel)
      .plus(categories)
      .joinToString(" ")
    return isAudiobookSignal(combined)
  }
}
