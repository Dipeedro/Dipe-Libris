package com.example.core.bibliographic.domain

import com.example.core.model.BibliographicMath
import com.example.core.model.ParsedIsbn

enum class QueryIntentType {
  ISBN_EXACT,
  EXPLICIT_AUTHOR,
  EXPLICIT_TITLE,
  TITLE_AND_AUTHOR,
  TITLE_STRONG,
  AMBIGUOUS_OR_SHORT,
}

data class ParsedSearchIntent(
  val rawQuery: String,
  val cleanQuery: String,
  val normalizedQuery: String,
  val intentType: QueryIntentType,
  val parsedIsbn: ParsedIsbn?,
  val allTokens: List<String>,
  val significantTokens: List<String>,
  val authorQueryPart: String? = null,
  val titleQueryPart: String? = null,
  val subjectQueryPart: String? = null,
  val detachedModifierTokens: List<String> = emptyList(),
  val explicitLanguageRequest: String? = null,
  val allowGuidesOrAnalyses: Boolean = false,
  val allowColoringBooks: Boolean = false,
  val allowNotebooks: Boolean = false,
  val wantsBoxOrCollection: Boolean = false,
) {
  /**
   * Sondagem separada (nunca via OR em busca full-text!) para nomes potenciais de 2–3 palavras
   * ou busca explícita de autor.
   */
  val canProbeStructuredAuthor: Boolean
    get() =
      intentType == QueryIntentType.EXPLICIT_AUTHOR ||
        (intentType == QueryIntentType.AMBIGUOUS_OR_SHORT &&
          authorQueryPart != null &&
          titleQueryPart == null &&
          detachedModifierTokens.isEmpty() &&
          !allowGuidesOrAnalyses &&
          !allowColoringBooks &&
          !allowNotebooks &&
          !wantsBoxOrCollection)
}

object QueryIntentParser {

  // Lista estrita de artigos/preposições: NÃO remover palavras de expressão ("que", "tal", "mais", "sem", "como", "não")
  // para impedir que títulos como "Que tal mais um gato?" virem busca genérica de 1 substantivo.
  private val stopwords =
    setOf(
      "a", "o", "as", "os", "um", "uma", "uns", "umas",
      "de", "da", "do", "das", "dos", "em", "no", "na", "nos", "nas",
      "e", "ou", "ao", "aos",
      "the", "an", "and", "or", "of", "in", "on", "to", "for",
      "el", "la", "los", "las", "y", "del", "le", "les",
    )

  private val strongTitleMarkers =
    Regex(
      "\\b(vol(?:ume)?\\.?|trilogia|trilogy|box|colecao|collection|saga|parte|livro|edicao|edition)\\b|[?!]",
      RegexOption.IGNORE_CASE,
    )

  private val explicitAuthorPrefixRegex = Regex("^autor\\s*:\\s*(.+)$|^author\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
  private val explicitTitlePrefixRegex = Regex("^titulo\\s*:\\s*(.+)$|^title\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
  private val explicitDoiRegex = Regex("^(?:doi\\s*:\\s*)?(10\\.\\d{4,9}/\\S+)$", RegexOption.IGNORE_CASE)
  private val splitTitleDashAuthorRegex = Regex("^(.{3,}?)\\s+[-–—]\\s+(.{3,})$")
  private val splitTitleByAuthorRegex = Regex("^(.{3,}?)\\s+(?:by)\\s+(.{3,})$", RegexOption.IGNORE_CASE)

  private val languageModifierRules =
    listOf(
      Regex("\\b(portugal|pt-pt|edicao portuguesa)\\b", RegexOption.IGNORE_CASE) to "pt-pt",
      Regex("\\b(em ingles|english edition|original english)\\b", RegexOption.IGNORE_CASE) to "en",
      Regex("\\b(em espanhol|espanhol|spanish|espanol)\\b", RegexOption.IGNORE_CASE) to "es",
      Regex("\\b(em frances|frances|french|francais)\\b", RegexOption.IGNORE_CASE) to "fr",
      Regex("\\b(em alemao|alemao|german|deutsch)\\b", RegexOption.IGNORE_CASE) to "de",
      Regex("\\b(em italiano|italiano|italian)\\b", RegexOption.IGNORE_CASE) to "it",
      Regex("\\b(em russo|russo|russian)\\b", RegexOption.IGNORE_CASE) to "ru",
      Regex("\\b(em japones|japones|japanese)\\b", RegexOption.IGNORE_CASE) to "ja",
    )

  private val leadingTitleArticles = setOf("a", "o", "as", "os", "um", "uma", "the", "an", "le", "la")

  private val trailingSingleDetachedModifiers =
    setOf(
      "guia",
      "guide",
      "manual",
      "handbook",
      "companion",
      "analise",
      "analysis",
      "resumo",
      "summary",
      "fichamento",
      "colorir",
      "coloring",
      "colouring",
      "box",
      "trilogia",
      "trilogy",
      "colecao",
      "collection",
      "omnibus",
    )

  private val trailingMultiDetachedModifiers =
    listOf(
      listOf("livro", "de", "colorir"),
      listOf("caderno", "de", "colorir"),
      listOf("guia", "do", "filme"),
      listOf("guia", "de", "estudo"),
      listOf("guia", "de", "leitura"),
      listOf("guia", "do", "leitor"),
      listOf("guia", "nao", "oficial"),
      listOf("guia", "oficial"),
      listOf("guia", "ilustrado"),
      listOf("guia", "completo"),
      listOf("guia", "definitivo"),
      listOf("official", "guide"),
      listOf("tribute", "guide"),
      listOf("study", "guide"),
      listOf("coloring", "book"),
      listOf("colouring", "book"),
      listOf("para", "colorir"),
    )

  private val leadingDetachedModifierPrefixes =
    listOf(
      listOf("livro", "de", "colorir"),
      listOf("caderno", "de", "colorir"),
      listOf("coloring", "book", "of"),
      listOf("coloring", "book", "for"),
      listOf("coloring", "book"),
      listOf("colouring", "book"),
      listOf("guia", "oficial", "de"),
      listOf("guia", "oficial", "da"),
      listOf("guia", "oficial", "do"),
      listOf("guia", "oficial"),
      listOf("guia", "sobre"),
      listOf("official", "guide", "to"),
      listOf("official", "guide"),
      listOf("analise", "de"),
      listOf("analise", "da"),
      listOf("analise", "do"),
      listOf("analise", "sobre"),
      listOf("resumo", "de"),
      listOf("resumo", "da"),
      listOf("resumo", "do"),
      listOf("box", "trilogia"),
      listOf("box", "colecao"),
      listOf("box", "completo"),
      listOf("box", "completa"),
      listOf("box"),
    )

  private val prepositionsAndArticles =
    setOf("de", "da", "do", "das", "dos", "para", "o", "a", "os", "as", "um", "uma", "of", "the", "an", "for", "to")

  fun isExplicitDoi(raw: String): Boolean = explicitDoiRegex.matches(raw.trim())

  fun parse(rawQuery: String): ParsedSearchIntent {
    val trimmed = BibliographicMath.sanitizeDisplayText(rawQuery).orEmpty()
    val parsedIsbn = BibliographicMath.parseIsbn(trimmed)
    if (parsedIsbn != null) {
      return ParsedSearchIntent(
        rawQuery = trimmed,
        cleanQuery = parsedIsbn.isbn13 ?: parsedIsbn.cleaned,
        normalizedQuery = parsedIsbn.isbn13 ?: parsedIsbn.cleaned,
        intentType = QueryIntentType.ISBN_EXACT,
        parsedIsbn = parsedIsbn,
        allTokens = listOf(parsedIsbn.isbn13 ?: parsedIsbn.cleaned),
        significantTokens = listOf(parsedIsbn.isbn13 ?: parsedIsbn.cleaned),
      )
    }

    var explicitLang: String? = null
    var workingQuery = trimmed
    for ((regex, langCode) in languageModifierRules) {
      if (regex.containsMatchIn(workingQuery)) {
        explicitLang = langCode
        workingQuery = workingQuery.replace(regex, " ").replace(Regex("\\s+"), " ").trim()
        break
      }
    }
    if (workingQuery.isBlank()) {
      workingQuery = trimmed
    }

    val explicitAuthorMatch = explicitAuthorPrefixRegex.find(workingQuery)
    val explicitTitleMatch = explicitTitlePrefixRegex.find(workingQuery)

    val normAll = BibliographicMath.normalizeText(workingQuery)
    val allTokens = normAll.split(' ').filter { it.isNotBlank() }
    val significantTokens =
      allTokens.filter { it !in stopwords && it.length > 1 }.ifEmpty { allTokens }

    val allowGuides =
      allTokens.any {
        it in
          setOf(
            "guia",
            "guide",
            "manual",
            "handbook",
            "companion",
            "analise",
            "analysis",
            "resumo",
            "summary",
            "estudo",
            "study",
            "fichamento",
            "comentado",
            "dissertacao",
            "tese",
            "reflexao",
          )
      }
    val allowColoring = allTokens.any { it in setOf("colorir", "coloring", "colouring") }
    val allowNotebooks =
      allTokens.any {
        it in setOf("caderno", "notebook", "journal", "diary", "diario", "sketchbook", "planner")
      }
    val wantsBox =
      allTokens.any { it in setOf("box", "trilogia", "trilogy", "colecao", "collection", "omnibus") }

    if (explicitAuthorMatch != null) {
      val authorVal =
        (explicitAuthorMatch.groupValues[1].ifBlank { explicitAuthorMatch.groupValues[2] }).trim()
      val authorNorm = BibliographicMath.normalizeText(authorVal)
      val authorTokens = authorNorm.split(' ').filter { it.isNotBlank() }
      return ParsedSearchIntent(
        rawQuery = trimmed,
        cleanQuery = authorVal,
        normalizedQuery = authorNorm,
        intentType = QueryIntentType.EXPLICIT_AUTHOR,
        parsedIsbn = null,
        allTokens = authorTokens,
        significantTokens = authorTokens.filter { it !in stopwords }.ifEmpty { authorTokens },
        authorQueryPart = authorVal,
        explicitLanguageRequest = explicitLang,
        allowGuidesOrAnalyses = allowGuides,
        allowColoringBooks = allowColoring,
        allowNotebooks = allowNotebooks,
        wantsBoxOrCollection = wantsBox,
      )
    }

    if (explicitTitleMatch != null) {
      val titleVal =
        (explicitTitleMatch.groupValues[1].ifBlank { explicitTitleMatch.groupValues[2] }).trim()
      val titleNorm = BibliographicMath.normalizeText(titleVal)
      val titleTokens = titleNorm.split(' ').filter { it.isNotBlank() }
      return ParsedSearchIntent(
        rawQuery = trimmed,
        cleanQuery = titleVal,
        normalizedQuery = titleNorm,
        intentType = QueryIntentType.EXPLICIT_TITLE,
        parsedIsbn = null,
        allTokens = titleTokens,
        significantTokens = titleTokens.filter { it !in stopwords && it.length > 1 }.ifEmpty { titleTokens },
        titleQueryPart = titleVal,
        explicitLanguageRequest = explicitLang,
        allowGuidesOrAnalyses = allowGuides,
        allowColoringBooks = allowColoring,
        allowNotebooks = allowNotebooks,
        wantsBoxOrCollection = wantsBox,
      )
    }

    val titleDashAuthor = splitTitleDashAuthorRegex.find(workingQuery) ?: splitTitleByAuthorRegex.find(workingQuery)
    if (titleDashAuthor != null) {
      val tPart = titleDashAuthor.groupValues[1].trim()
      val aPart = titleDashAuthor.groupValues[2].trim()
      val aTokens = BibliographicMath.normalizeText(aPart).split(' ').filter { it.isNotBlank() }
      if (aTokens.size in 1..4) {
        return ParsedSearchIntent(
          rawQuery = trimmed,
          cleanQuery = "$tPart $aPart",
          normalizedQuery = normAll,
          intentType = QueryIntentType.TITLE_AND_AUTHOR,
          parsedIsbn = null,
          allTokens = allTokens,
          significantTokens = significantTokens,
          authorQueryPart = aPart,
          titleQueryPart = tPart,
          explicitLanguageRequest = explicitLang,
          allowGuidesOrAnalyses = allowGuides,
          allowColoringBooks = allowColoring,
          allowNotebooks = allowNotebooks,
          wantsBoxOrCollection = wantsBox,
        )
      }
    }

    val (subjectPart, detachedModifiers) = extractDetachedModifiers(workingQuery)
    val effectiveTokensForTitleSignal =
      if (subjectPart != null) {
        BibliographicMath.normalizeText(subjectPart).split(' ').filter { it.isNotBlank() }
      } else {
        allTokens
      }

    val startsWithArticle =
      effectiveTokensForTitleSignal.firstOrNull() in leadingTitleArticles &&
        effectiveTokensForTitleSignal.size >= 2
    val hasStrongTitleSignal =
      strongTitleMarkers.containsMatchIn(subjectPart ?: workingQuery) ||
        effectiveTokensForTitleSignal.size >= 4 ||
        startsWithArticle

    val intentType =
      if (hasStrongTitleSignal) QueryIntentType.TITLE_STRONG else QueryIntentType.AMBIGUOUS_OR_SHORT

    // Sonda autor separadamente APENAS em consultas curtas de 2-3 palavras sem pontuação nem sinais de título/modificador
    val possibleAuthorProbe =
      if (intentType == QueryIntentType.AMBIGUOUS_OR_SHORT &&
        detachedModifiers.isEmpty() &&
        !allowGuides &&
        !allowColoring &&
        !allowNotebooks &&
        !wantsBox &&
        allTokens.size in 2..3 &&
        allTokens.none { it.all(Char::isDigit) } &&
        !workingQuery.any { it in setOf('?', '!', ':') }
      ) {
        workingQuery
      } else {
        null
      }

    return ParsedSearchIntent(
      rawQuery = trimmed,
      cleanQuery = workingQuery,
      normalizedQuery = normAll,
      intentType = intentType,
      parsedIsbn = null,
      allTokens = allTokens,
      significantTokens = significantTokens,
      authorQueryPart = possibleAuthorProbe,
      titleQueryPart = if (intentType == QueryIntentType.TITLE_STRONG) (subjectPart ?: workingQuery) else null,
      subjectQueryPart = subjectPart,
      detachedModifierTokens = detachedModifiers,
      explicitLanguageRequest = explicitLang,
      allowGuidesOrAnalyses = allowGuides,
      allowColoringBooks = allowColoring,
      allowNotebooks = allowNotebooks,
      wantsBoxOrCollection = wantsBox,
    )
  }

  private fun extractDetachedModifiers(workingQuery: String): Pair<String?, List<String>> {
    val rawWords = workingQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (rawWords.size < 2) return null to emptyList()
    val normWords = rawWords.map { BibliographicMath.normalizeText(it) }
    var startIdx = 0
    var endIdx = rawWords.size
    val collected = mutableListOf<String>()

    // 1. Verificar prefixos destacados (ex.: "livro de colorir harry potter", "Box Harry Potter")
    for (prefix in leadingDetachedModifierPrefixes) {
      if (endIdx - startIdx > prefix.size && normWords.subList(startIdx, startIdx + prefix.size) == prefix) {
        collected.addAll(prefix.filter { it !in stopwords })
        startIdx += prefix.size
        break
      }
    }

    // 2. Verificar sufixos destacados compostos (ex.: "Jogos Vorazes guia oficial", "Coraline livro de colorir")
    var matchedTrailingMulti = false
    for (suffix in trailingMultiDetachedModifiers) {
      if (endIdx - startIdx > suffix.size && normWords.subList(endIdx - suffix.size, endIdx) == suffix) {
        collected.addAll(suffix.filter { it !in stopwords })
        endIdx -= suffix.size
        matchedTrailingMulti = true
        break
      }
    }

    // 3. Verificar sufixo simples destacado (ex.: "Jogos Vorazes guia"), evitando preposições/artigos antes
    if (!matchedTrailingMulti && endIdx - startIdx >= 2) {
      val last = normWords[endIdx - 1]
      val prev = normWords[endIdx - 2]
      if (last in trailingSingleDetachedModifiers && prev !in prepositionsAndArticles) {
        collected.add(last)
        endIdx -= 1
      }
    }

    if (collected.isEmpty() || startIdx >= endIdx) return null to emptyList()
    val subjectWords = rawWords.subList(startIdx, endIdx)
    val subjectNormTokens =
      subjectWords
        .map { BibliographicMath.normalizeText(it) }
        .filter { it.isNotBlank() && it !in stopwords }
    if (subjectNormTokens.isEmpty()) return null to emptyList()

    val subjectRaw =
      subjectWords
        .joinToString(" ")
        .trim(' ', '-', ':', '–', '—')
        .takeIf { it.isNotBlank() } ?: return null to emptyList()
    return subjectRaw to collected.distinct()
  }
}
