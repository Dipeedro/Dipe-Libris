package com.example.core.bibliographic.domain

import com.example.core.model.BibliographicMath

data class GoogleBooksQuerySpec(
  val qParam: String,
  val maxResults: Int,
  val startIndex: Int,
  val langRestrict: String?,
  val isFallbackUnstructured: Boolean = false,
)

data class GoogleBooksQueryPlan(
  val primaryQueries: List<GoogleBooksQuerySpec>,
  val fallbackQuery: GoogleBooksQuerySpec? = null,
)

data class OpenLibraryQuerySpec(
  val mode: Mode,
  val queryValue: String,
  val extraAuthorValue: String? = null,
  val sort: String? = null,
  val limit: Int,
  val page: Int,
) {
  enum class Mode {
    ISBN,
    AUTHOR,
    TITLE,
    TITLE_AND_AUTHOR,
    GENERAL_Q,
  }
}

data class OpenLibraryQueryPlan(
  val primaryQueries: List<OpenLibraryQuerySpec>,
  val fallbackQuery: OpenLibraryQuerySpec? = null,
  val shouldEnrichMissingPtWorks: Boolean = false,
)

data class AppleBooksQueryPlan(
  val term: String,
  val attribute: String? = null,
  val limit: Int = 20,
)

object QueryPlanner {

  fun planGoogleBooks(
    intent: ParsedSearchIntent,
    page: Int,
    pageSize: Int,
  ): GoogleBooksQueryPlan {
    val cleanQ = intent.cleanQuery.trim()
    val startIndex = ((page - 1).coerceAtLeast(0)) * pageSize
    val apiLang = intent.explicitLanguageRequest?.substringBefore('-')?.takeIf { it.length == 2 }

    if (intent.intentType == QueryIntentType.ISBN_EXACT && intent.parsedIsbn != null) {
      val isbn = intent.parsedIsbn.isbn13 ?: intent.parsedIsbn.cleaned
      return GoogleBooksQueryPlan(
        primaryQueries =
          listOf(
            GoogleBooksQuerySpec(
              qParam = "isbn:$isbn",
              maxResults = 10,
              startIndex = 0,
              langRestrict = null,
            )
          )
      )
    }

    val primary =
      when {
        apiLang != null -> {
          val target = intent.authorQueryPart?.takeIf { intent.intentType == QueryIntentType.EXPLICIT_AUTHOR }
          val qStr =
            if (target != null) "inauthor:\"${escapeQuotes(target)}\""
            else "intitle:\"${escapeQuotes(cleanQ)}\""
          listOf(
            GoogleBooksQuerySpec(
              qParam = qStr,
              maxResults = pageSize,
              startIndex = startIndex,
              langRestrict = apiLang,
            )
          )
        }

        intent.intentType == QueryIntentType.EXPLICIT_AUTHOR && intent.authorQueryPart != null -> {
          val auth = escapeQuotes(intent.authorQueryPart.trim())
          buildList {
            add(GoogleBooksQuerySpec("inauthor:\"$auth\"", maxResults = pageSize, startIndex = startIndex, langRestrict = "pt"))
            add(GoogleBooksQuerySpec("inauthor:\"$auth\"", maxResults = pageSize.coerceAtMost(20), startIndex = startIndex, langRestrict = null))
          }
        }

        intent.intentType == QueryIntentType.TITLE_AND_AUTHOR &&
          intent.titleQueryPart != null &&
          intent.authorQueryPart != null -> {
          val t = escapeQuotes(intent.titleQueryPart.trim())
          val a = escapeQuotes(intent.authorQueryPart.trim())
          val combined = "intitle:\"$t\" inauthor:\"$a\""
          listOf(
            GoogleBooksQuerySpec(combined, maxResults = pageSize, startIndex = startIndex, langRestrict = "pt"),
            GoogleBooksQuerySpec(combined, maxResults = 15, startIndex = startIndex, langRestrict = null),
          )
        }

        else -> {
          val exactTitleQ = "intitle:\"${escapeQuotes(cleanQ)}\""
          val sigTokens =
            intent.significantTokens.ifEmpty {
              BibliographicMath.normalizeText(cleanQ).split(' ').filter { it.isNotBlank() }
            }
          val tokenTitleQ =
            if (sigTokens.size >= 2) {
              sigTokens.joinToString(" ") { "intitle:$it" }
            } else {
              exactTitleQ
            }
          buildList {
            add(GoogleBooksQuerySpec(exactTitleQ, maxResults = pageSize, startIndex = startIndex, langRestrict = "pt"))
            if (page == 1) {
              // Segunda chamada prioriza títulos sem restrição rígida de aspas (acento/ortografia/tradução)
              val secondaryQ = if (tokenTitleQ != exactTitleQ) tokenTitleQ else exactTitleQ
              add(GoogleBooksQuerySpec(secondaryQ, maxResults = 20, startIndex = 0, langRestrict = null))
              // Sondagem de autor SEPARADA (nunca via OR!) apenas se formato lembrar nome de pessoa
              if (intent.canProbeStructuredAuthor && intent.authorQueryPart != null) {
                val auth = escapeQuotes(intent.authorQueryPart.trim())
                add(GoogleBooksQuerySpec("inauthor:\"$auth\"", maxResults = 15, startIndex = 0, langRestrict = "pt"))
              }
            }
          }
        }
      }

    // Fallback sem intitle: só é disparado pelo adaptador na pág. 1 se TODAS as consultas estruturadas voltarem vazias
    val fallback =
      if (page == 1 && intent.intentType != QueryIntentType.EXPLICIT_AUTHOR) {
        GoogleBooksQuerySpec(
          qParam = cleanQ,
          maxResults = 15,
          startIndex = 0,
          langRestrict = apiLang ?: "pt",
          isFallbackUnstructured = true,
        )
      } else {
        null
      }

    return GoogleBooksQueryPlan(primaryQueries = primary, fallbackQuery = fallback)
  }

  fun planOpenLibrary(
    intent: ParsedSearchIntent,
    page: Int,
    limit: Int,
  ): OpenLibraryQueryPlan {
    val cleanQ = intent.cleanQuery.trim()
    if (intent.intentType == QueryIntentType.ISBN_EXACT && intent.parsedIsbn != null) {
      val isbn = intent.parsedIsbn.isbn13 ?: intent.parsedIsbn.cleaned
      return OpenLibraryQueryPlan(
        primaryQueries = listOf(OpenLibraryQuerySpec(OpenLibraryQuerySpec.Mode.ISBN, isbn, limit = 8, page = 1))
      )
    }

    val primary =
      when {
        intent.intentType == QueryIntentType.EXPLICIT_AUTHOR && intent.authorQueryPart != null -> {
          listOf(
            OpenLibraryQuerySpec(
              mode = OpenLibraryQuerySpec.Mode.AUTHOR,
              queryValue = intent.authorQueryPart.trim(),
              sort = "readinglog",
              limit = limit,
              page = page,
            )
          )
        }

        intent.intentType == QueryIntentType.TITLE_AND_AUTHOR &&
          intent.titleQueryPart != null &&
          intent.authorQueryPart != null -> {
          listOf(
            OpenLibraryQuerySpec(
              mode = OpenLibraryQuerySpec.Mode.TITLE_AND_AUTHOR,
              queryValue = intent.titleQueryPart.trim(),
              extraAuthorValue = intent.authorQueryPart.trim(),
              limit = limit,
              page = page,
            )
          )
        }

        intent.canProbeStructuredAuthor && intent.authorQueryPart != null && page == 1 -> {
          listOf(
            OpenLibraryQuerySpec(
              mode = OpenLibraryQuerySpec.Mode.TITLE,
              queryValue = cleanQ,
              limit = limit,
              page = page,
            ),
            OpenLibraryQuerySpec(
              mode = OpenLibraryQuerySpec.Mode.AUTHOR,
              queryValue = intent.authorQueryPart.trim(),
              sort = "readinglog",
              limit = 16,
              page = 1,
            ),
          )
        }

        else -> {
          listOf(
            OpenLibraryQuerySpec(
              mode = OpenLibraryQuerySpec.Mode.TITLE,
              queryValue = cleanQ,
              limit = limit,
              page = page,
            )
          )
        }
      }

    val fallback =
      if (page == 1 && intent.intentType != QueryIntentType.EXPLICIT_AUTHOR) {
        OpenLibraryQuerySpec(
          mode = OpenLibraryQuerySpec.Mode.GENERAL_Q,
          queryValue = cleanQ,
          limit = limit,
          page = 1,
        )
      } else {
        null
      }

    return OpenLibraryQueryPlan(
      primaryQueries = primary,
      fallbackQuery = fallback,
      shouldEnrichMissingPtWorks = (page == 1 && intent.intentType != QueryIntentType.EXPLICIT_AUTHOR),
    )
  }

  fun planAppleBooks(intent: ParsedSearchIntent): AppleBooksQueryPlan {
    val term =
      when {
        intent.intentType == QueryIntentType.EXPLICIT_AUTHOR && intent.authorQueryPart != null ->
          intent.authorQueryPart.trim()
        intent.intentType == QueryIntentType.EXPLICIT_TITLE && intent.titleQueryPart != null ->
          intent.titleQueryPart.trim()
        else -> intent.cleanQuery.trim()
      }
    val attribute =
      when (intent.intentType) {
        QueryIntentType.EXPLICIT_AUTHOR -> "authorTerm"
        QueryIntentType.EXPLICIT_TITLE -> "titleTerm"
        else -> null
      }
    return AppleBooksQueryPlan(
      term = term,
      attribute = attribute,
      limit = 20,
    )
  }

  fun shouldRunWikidataBridge(intent: ParsedSearchIntent, expandSecondary: Boolean): Boolean {
    if (intent.intentType == QueryIntentType.ISBN_EXACT) return false
    if (expandSecondary) return true
    return intent.significantTokens.size in 1..4 && !intent.wantsBoxOrCollection
  }

  private fun escapeQuotes(raw: String): String = raw.replace("\"", "").trim()
}
