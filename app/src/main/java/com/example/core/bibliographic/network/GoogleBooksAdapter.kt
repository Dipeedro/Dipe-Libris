package com.example.core.bibliographic.network

import com.example.BuildConfig
import com.example.core.bibliographic.domain.CoverRanker
import com.example.core.bibliographic.domain.ParsedSearchIntent
import com.example.core.bibliographic.domain.QueryIntentType
import com.example.core.bibliographic.domain.QueryPlanner
import com.example.core.model.BibliographicMath
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SourceQueryStatus
import com.example.core.model.SynopsisCandidate
import java.net.SocketTimeoutException
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class GoogleBooksResponse(
  val totalItems: Int = 0,
  val items: List<GoogleBookItem> = emptyList(),
)

@Serializable
private data class GoogleBookItem(
  val id: String? = null,
  val volumeInfo: GoogleVolumeInfo? = null,
)

@Serializable
private data class GoogleVolumeInfo(
  val title: String? = null,
  val subtitle: String? = null,
  val authors: List<String> = emptyList(),
  val publisher: String? = null,
  val publishedDate: String? = null,
  val description: String? = null,
  val industryIdentifiers: List<GoogleIndustryId> = emptyList(),
  val pageCount: Int? = null,
  val categories: List<String> = emptyList(),
  val imageLinks: GoogleImageLinks? = null,
  val language: String? = null,
)

@Serializable
private data class GoogleIndustryId(
  val type: String? = null,
  val identifier: String? = null,
)

@Serializable
private data class GoogleImageLinks(
  val smallThumbnail: String? = null,
  val thumbnail: String? = null,
  val small: String? = null,
  val medium: String? = null,
  val large: String? = null,
  val extraLarge: String? = null,
)

class GoogleBooksAdapter(
  private val client: OkHttpClient = BibliographicHttpClients.okHttpClient,
  private val apiKeyProvider: () -> String = { BuildConfig.GOOGLE_BOOKS_API_KEY },
  private val baseUrl: String = "https://www.googleapis.com/books/v1",
) : BibliographicSourceAdapter {

  override val source: MetadataSource = MetadataSource.GOOGLE_BOOKS
  private val json = BibliographicHttpClients.json

  override suspend fun searchByIsbn(
    isbn13: String?,
    isbn10: String?,
    rawIsbn: String,
  ): SourceAdapterResult {
    val target = isbn13 ?: isbn10 ?: BibliographicMath.cleanIsbn(rawIsbn)
    if (target.isBlank()) {
      return SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
    }
    val startMs = System.currentTimeMillis()
    val res = executeSingleQuery(qParam = "isbn:$target", maxResults = 10, startIndex = 0, langRestrict = null, explicitRequestedLang = null)
    return toAdapterResult(listOf(res), requestedCount = 10, startIndex = 0, elapsedMs = System.currentTimeMillis() - startMs)
  }

  override suspend fun searchByQuery(query: String): SourceAdapterResult {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
      return SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
    }
    val startMs = System.currentTimeMillis()
    val res = executeSingleQuery(qParam = trimmed, maxResults = 25, startIndex = 0, langRestrict = null, explicitRequestedLang = null)
    return toAdapterResult(listOf(res), requestedCount = 25, startIndex = 0, elapsedMs = System.currentTimeMillis() - startMs)
  }

  override suspend fun searchPaginated(request: AdapterSearchRequest): SourceAdapterResult =
    withContext(Dispatchers.IO) {
      val intent = request.parsedIntent
      if (intent.intentType == QueryIntentType.ISBN_EXACT && intent.parsedIsbn != null) {
        return@withContext if (request.page > 1) {
          SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
        } else {
          searchByIsbn(intent.parsedIsbn.isbn13, intent.parsedIsbn.isbn10, intent.parsedIsbn.cleaned)
        }
      }

      val cleanQ = intent.cleanQuery.trim()
      if (cleanQ.isEmpty()) {
        return@withContext SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
      }

      val startMs = System.currentTimeMillis()
      val page = request.page.coerceAtLeast(1)
      val pageSize = request.pageSize.coerceIn(15, 40)
      val startIndex = (page - 1) * pageSize
      val explicitLang = intent.explicitLanguageRequest
      val plan = QueryPlanner.planGoogleBooks(intent = intent, page = page, pageSize = pageSize)

      val primaryResults =
        coroutineScope {
          plan.primaryQueries
            .map { spec ->
              async {
                executeSingleQuery(
                  qParam = spec.qParam,
                  maxResults = spec.maxResults,
                  startIndex = spec.startIndex,
                  langRestrict = spec.langRestrict,
                  explicitRequestedLang = explicitLang,
                )
              }
            }
            .awaitAll()
        }

      val primaryCandidateCount =
        primaryResults.filterIsInstance<SingleCallOutcome.Success>().sumOf { it.candidates.size }
      val shouldRunFallback =
        plan.fallbackQuery != null &&
          primaryResults.any { it is SingleCallOutcome.Success } &&
          (primaryCandidateCount == 0 || (intent.significantTokens.size >= 3 && primaryCandidateCount < 2))

      val allResults =
        if (shouldRunFallback) {
          val fb = plan.fallbackQuery!!
          val fallbackOutcome =
            executeSingleQuery(
              qParam = fb.qParam,
              maxResults = fb.maxResults,
              startIndex = fb.startIndex,
              langRestrict = fb.langRestrict,
              explicitRequestedLang = explicitLang,
            )
          primaryResults + fallbackOutcome
        } else {
          primaryResults
        }

      toAdapterResult(
        results = allResults,
        requestedCount = pageSize,
        startIndex = startIndex,
        elapsedMs = System.currentTimeMillis() - startMs,
      )
    }

  private sealed interface SingleCallOutcome {
    data class Success(
      val totalItems: Int,
      val rawItemCount: Int,
      val candidates: List<SearchEditionCandidate>,
    ) : SingleCallOutcome

    data class SkippedNoKey(val message: String? = null) : SingleCallOutcome
    data class RateLimited(val message: String? = null) : SingleCallOutcome
    data class Error(val message: String?) : SingleCallOutcome
  }

  private suspend fun executeSingleQuery(
    qParam: String,
    maxResults: Int,
    startIndex: Int,
    langRestrict: String?,
    explicitRequestedLang: String?,
  ): SingleCallOutcome {
    val key = apiKeyProvider().trim()
    val hasValidKey = key.isNotEmpty() && key != "MY_GOOGLE_BOOKS_API_KEY"
    return runCatching {
        val encodedQ = URLEncoder.encode(qParam, "UTF-8")
        val url = buildString {
          append("$baseUrl/volumes?q=$encodedQ&maxResults=$maxResults&printType=books&orderBy=relevance")
          if (startIndex > 0) append("&startIndex=$startIndex")
          if (!langRestrict.isNullOrBlank()) append("&langRestrict=${URLEncoder.encode(langRestrict, "UTF-8")}")
          if (hasValidKey) append("&key=${URLEncoder.encode(key, "UTF-8")}")
        }
        val request = Request.Builder().url(url).get().build()
        val response = client.executeTextCall(request)
        val body = response.body
        if (response.code == 429) {
          return if (!hasValidKey) {
            SingleCallOutcome.SkippedNoKey("HTTP 429 without configured API key")
          } else {
            SingleCallOutcome.RateLimited("HTTP 429")
          }
        }
        if (response.code == 403) {
          if (!hasValidKey && (isRateLimitErrorBody(body) || isUnconfiguredKey403Body(body))) {
            return SingleCallOutcome.SkippedNoKey("HTTP 403 without configured API key")
          }
          if (isRateLimitErrorBody(body)) {
            return SingleCallOutcome.RateLimited("HTTP 403")
          }
          if (hasValidKey && isInvalidKeyErrorBody(body)) {
            return SingleCallOutcome.Error("API key invalid (HTTP 403)")
          }
          return SingleCallOutcome.Error("HTTP 403")
        }
        if (!response.isSuccessful || body == null) {
          return SingleCallOutcome.Error("HTTP ${response.code}")
        }
        val parsed = json.decodeFromString<GoogleBooksResponse>(body)
        val candidates =
          parsed.items.mapNotNull { item ->
            mapItemToCandidate(item, explicitRequestedLang)
          }
        SingleCallOutcome.Success(
          totalItems = parsed.totalItems.coerceAtLeast(parsed.items.size),
          rawItemCount = parsed.items.size,
          candidates = candidates,
        )
      }
      .getOrElse { err ->
        if (err is CancellationException) throw err
        val msg = if (err is SocketTimeoutException) "Timeout: ${err.message ?: "socket read"}" else err.message
        SingleCallOutcome.Error(msg)
      }
  }

  private fun isRateLimitErrorBody(body: String?): Boolean {
    val text = body.orEmpty()
    return text.contains("limit", ignoreCase = true) ||
      text.contains("quota", ignoreCase = true) ||
      text.contains("rateLimitExceeded", ignoreCase = true) ||
      text.contains("dailyLimitExceeded", ignoreCase = true) ||
      text.contains("userRateLimitExceeded", ignoreCase = true)
  }

  private fun isUnconfiguredKey403Body(body: String?): Boolean {
    val text = body.orEmpty()
    return text.contains("unregistered", ignoreCase = true) ||
      text.contains("api key", ignoreCase = true) ||
      text.contains("keyInvalid", ignoreCase = true) ||
      text.contains("accessNotConfigured", ignoreCase = true)
  }

  private fun isInvalidKeyErrorBody(body: String?): Boolean {
    val text = body.orEmpty()
    return text.contains("keyInvalid", ignoreCase = true) ||
      text.contains("API key not valid", ignoreCase = true) ||
      text.contains("API_KEY_INVALID", ignoreCase = true)
  }

  private fun mapItemToCandidate(
    item: GoogleBookItem,
    explicitRequestedLang: String?,
  ): SearchEditionCandidate? {
    val info = item.volumeInfo ?: return null
    val title = BibliographicMath.sanitizeDisplayText(info.title) ?: return null
    val contributors =
      info.authors.mapIndexedNotNull { idx, name ->
        val cleanName = BibliographicMath.sanitizeDisplayText(name) ?: return@mapIndexedNotNull null
        CandidateContributor(
          name = cleanName,
          role = if (idx == 0) ContributorRole.AUTHOR else ContributorRole.CO_AUTHOR,
        )
      }
    val parsedIds = info.industryIdentifiers.mapNotNull { BibliographicMath.parseIsbn(it.identifier) }
    val isbn13 = parsedIds.firstNotNullOfOrNull { it.isbn13 }
    val isbn10 = parsedIds.firstNotNullOfOrNull { it.isbn10 }
    val pub = BibliographicMath.sanitizeDisplayText(info.publisher)
    val year = extractYear(info.publishedDate)
    val langAssessment =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = info.language,
        isbn13Or10 = isbn13 ?: isbn10,
        publisher = pub,
        editionTitle = title,
        explicitRequestedLanguage = explicitRequestedLang,
      )
    val lang = langAssessment.normalizedCode
    val cleanSynopsis = BibliographicMath.stripHtmlTags(info.description)
    val rawImages =
      listOfNotNull(
        info.imageLinks?.extraLarge,
        info.imageLinks?.large,
        info.imageLinks?.medium,
        info.imageLinks?.small,
        info.imageLinks?.thumbnail,
        info.imageLinks?.smallThumbnail,
      )

    val covers =
      CoverRanker.buildGoogleBooksCoverCandidates(
        rawUrls = rawImages,
        matchesIsbn = isbn13 != null || isbn10 != null,
        matchesPublisherYear = pub != null && year != null,
        matchesLanguage = lang != null,
      )

    val synopses =
      if (cleanSynopsis != null) {
        listOf(
          SynopsisCandidate(
            text = cleanSynopsis,
            language = lang,
            source = source,
            editionLabel = pub ?: title,
          )
        )
      } else {
        emptyList()
      }

    return SearchEditionCandidate(
      candidateId = "gb_${item.id ?: title.hashCode()}",
      workTitle = title,
      subtitle = BibliographicMath.sanitizeDisplayText(info.subtitle),
      editionTitle = title,
      contributors = contributors,
      publisher = pub,
      publicationYear = year,
      totalPages = info.pageCount?.takeIf { it in 1..50_000 },
      isbn10 = isbn10,
      isbn13 = isbn13,
      language = lang,
      categories = info.categories.take(8),
      coverCandidates = covers,
      synopsisCandidates = synopses,
      externalIds =
        buildMap {
          item.id?.let { put("google_books_id", it) }
        },
      sources = listOf(source),
      canonicalLanguageTag = langAssessment.canonicalTag,
      languageTierRank = langAssessment.tier.rank,
      languageConfidenceName = langAssessment.confidence.name,
      hasLanguageConflict = langAssessment.hasConflict,
    )
  }

  private fun toAdapterResult(
    results: List<SingleCallOutcome>,
    requestedCount: Int,
    startIndex: Int,
    elapsedMs: Long,
  ): SourceAdapterResult {
    val successes = results.filterIsInstance<SingleCallOutcome.Success>()
    if (successes.isEmpty()) {
      val skipped = results.filterIsInstance<SingleCallOutcome.SkippedNoKey>().firstOrNull()
      if (skipped != null) {
        return SourceAdapterResult(
          SourceQueryStatus(source, ProviderOutcomeState.SKIPPED_NO_API_KEY, message = skipped.message, durationMs = elapsedMs)
        )
      }
      val rateLimited = results.filterIsInstance<SingleCallOutcome.RateLimited>().firstOrNull()
      if (rateLimited != null) {
        return SourceAdapterResult(
          SourceQueryStatus(source, ProviderOutcomeState.RATE_LIMITED, message = rateLimited.message, durationMs = elapsedMs)
        )
      }
      val error = results.filterIsInstance<SingleCallOutcome.Error>().firstOrNull()
      return SourceAdapterResult(
        SourceQueryStatus(
          source = source,
          state = if (error != null) ProviderOutcomeState.ERROR else ProviderOutcomeState.EMPTY,
          message = error?.message,
          durationMs = elapsedMs,
        )
      )
    }

    val dedupedCandidates =
      successes
        .flatMap { it.candidates }
        .distinctBy { it.externalIds["google_books_id"] ?: it.isbn13 ?: it.candidateId }

    val totalAvailable = successes.maxOfOrNull { it.totalItems } ?: dedupedCandidates.size
    val anyMoreRaw = successes.any { it.rawItemCount > 0 && (startIndex + it.rawItemCount) < it.totalItems && it.rawItemCount >= (requestedCount / 3).coerceAtLeast(5) }
    val state =
      if (dedupedCandidates.isEmpty()) ProviderOutcomeState.EMPTY else ProviderOutcomeState.SUCCESS

    return SourceAdapterResult(
      status =
        SourceQueryStatus(
          source = source,
          state = state,
          count = dedupedCandidates.size,
          totalAvailable = totalAvailable.takeIf { it > 0 },
          hasMore = anyMoreRaw,
          durationMs = elapsedMs,
        ),
      candidates = dedupedCandidates,
    )
  }
}
