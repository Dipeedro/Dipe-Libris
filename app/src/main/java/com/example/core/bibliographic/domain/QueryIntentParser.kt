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
          titleQueryPart == null)
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
            "analise",
            "analysis",
            "resumo",
            "summary",
            "estudo",
            "study",
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

    val startsWithArticle = allTokens.firstOrNull() in leadingTitleArticles && allTokens.size >= 2
    val hasStrongTitleSignal =
      strongTitleMarkers.containsMatchIn(workingQuery) || allTokens.size >= 4 || startsWithArticle

    val intentType =
      if (hasStrongTitleSignal) QueryIntentType.TITLE_STRONG else QueryIntentType.AMBIGUOUS_OR_SHORT

    // Sonda autor separadamente APENAS em consultas curtas de 2-3 palavras sem pontuação nem sinais de título
    val possibleAuthorProbe =
      if (intentType == QueryIntentType.AMBIGUOUS_OR_SHORT &&
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
      titleQueryPart = if (intentType == QueryIntentType.TITLE_STRONG) workingQuery else null,
      explicitLanguageRequest = explicitLang,
      allowGuidesOrAnalyses = allowGuides,
      allowColoringBooks = allowColoring,
      allowNotebooks = allowNotebooks,
      wantsBoxOrCollection = wantsBox,
    )
  }
}
