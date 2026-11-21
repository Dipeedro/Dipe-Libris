package com.example.core.bibliographic.network

import com.example.core.bibliographic.domain.OpenLibraryQuerySpec
import com.example.core.bibliographic.domain.QueryIntentType
import com.example.core.bibliographic.domain.QueryPlanner
import com.example.core.model.BibliographicMath
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.CoverCandidate
import com.example.core.model.EditionFormat
import com.example.core.model.LanguageTier
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
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class OlSearchResponse(
  val numFound: Int = 0,
  val docs: List<OlSearchDoc> = emptyList(),
)

@Serializable
private data class OlSearchEditionsWrapper(
  val numFound: Int = 0,
  val docs: List<OlSearchEditionDoc> = emptyList(),
)

@Serializable
private data class OlSearchEditionDoc(
  val key: String? = null,
  val title: String? = null,
  val language: List<String> = emptyList(),
  val publisher: List<String> = emptyList(),
  val isbn: List<String> = emptyList(),
  @SerialName("cover_i") val coverId: Long? = null,
  @SerialName("publish_year") val publishYear: List<Int> = emptyList(),
  @SerialName("number_of_pages_median") val pagesMedian: Int? = null,
)

@Serializable
private data class OlSearchDoc(
  val key: String? = null,
  val title: String? = null,
  val subtitle: String? = null,
  @SerialName("author_name") val authorNames: List<String> = emptyList(),
  @SerialName("author_key") val authorKeys: List<String> = emptyList(),
  @SerialName("first_publish_year") val firstPublishYear: Int? = null,
  val isbn: List<String> = emptyList(),
  @SerialName("edition_key") val editionKeys: List<String> = emptyList(),
  @SerialName("edition_count") val editionCount: Int? = null,
  @SerialName("ratings_count") val ratingsCount: Int? = null,
  @SerialName("readinglog_count") val readingLogCount: Int? = null,
  val language: List<String> = emptyList(),
  val subject: List<String> = emptyList(),
  @SerialName("cover_i") val coverId: Long? = null,
  @SerialName("number_of_pages_median") val medianPages: Int? = null,
  val publisher: List<String> = emptyList(),
  val editions: OlSearchEditionsWrapper? = null,
)

@Serializable
private data class OlKeyRef(
  val key: String? = null,
)

@Serializable
private data class OlEditionRecord(
  val key: String? = null,
  val title: String? = null,
  val subtitle: String? = null,
  val publishers: List<String> = emptyList(),
  @SerialName("publish_date") val publishDate: String? = null,
  @SerialName("number_of_pages") val numberOfPages: Int? = null,
  @SerialName("isbn_10") val isbn10: List<String> = emptyList(),
  @SerialName("isbn_13") val isbn13: List<String> = emptyList(),
  val covers: List<Long> = emptyList(),
  val languages: List<OlKeyRef> = emptyList(),
  val works: List<OlKeyRef> = emptyList(),
  val authors: List<OlKeyRef> = emptyList(),
  val contributors: List<OlContributorObj> = emptyList(),
  @SerialName("physical_format") val physicalFormat: String? = null,
  @SerialName("edition_name") val editionName: String? = null,
  val series: List<String> = emptyList(),
  val description: JsonElement? = null,
  val subjects: List<String> = emptyList(),
)

@Serializable
private data class OlContributorObj(
  val name: String? = null,
  val role: String? = null,
)

@Serializable
private data class OlWorkRecord(
  val key: String? = null,
  val title: String? = null,
  val subtitle: String? = null,
  val description: JsonElement? = null,
  val subjects: List<String> = emptyList(),
  val covers: List<Long> = emptyList(),
  val authors: List<OlWorkAuthorWrapper> = emptyList(),
)

@Serializable
private data class OlWorkAuthorWrapper(
  val author: OlKeyRef? = null,
)

@Serializable
private data class OlAuthorRecord(
  val key: String? = null,
  val name: String? = null,
)

@Serializable
private data class OlWorkEditionsResponse(
  val size: Int? = null,
  val entries: List<OlEditionRecord> = emptyList(),
)

class OpenLibraryAdapter(
  private val client: OkHttpClient = BibliographicHttpClients.okHttpClient,
  private val baseUrl: String = "https://openlibrary.org",
) : BibliographicSourceAdapter {

  override val source: MetadataSource = MetadataSource.OPEN_LIBRARY
  private val json = BibliographicHttpClients.json

  override suspend fun searchByIsbn(
    isbn13: String?,
    isbn10: String?,
    rawIsbn: String,
  ): SourceAdapterResult =
    withContext(Dispatchers.IO) {
      val startMs = System.currentTimeMillis()
      val target = isbn13 ?: isbn10 ?: BibliographicMath.cleanIsbn(rawIsbn)
      if (target.isBlank()) {
        return@withContext SourceAdapterResult(
          SourceQueryStatus(source, ProviderOutcomeState.EMPTY)
        )
      }
      runCatching {
          val editionResp = fetchJson("$baseUrl/isbn/$target.json")
          if (editionResp.code == 429) {
            return@withContext SourceAdapterResult(
              SourceQueryStatus(source, ProviderOutcomeState.RATE_LIMITED, durationMs = System.currentTimeMillis() - startMs)
            )
          }
          val editionBody = editionResp.body
          if (editionResp.code == 200 && editionBody != null) {
            val edition = json.decodeFromString<OlEditionRecord>(editionBody)
            val workKey = edition.works.firstOrNull()?.key
            val editionAuthorKeys = edition.authors.mapNotNull { it.key }.distinct().take(4)

            val (work, resolvedAuthors) =
              coroutineScope {
                val workDef = async {
                  workKey?.let { wk ->
                    val workResp = fetchJson("$baseUrl$wk.json")
                    workResp.body?.takeIf { workResp.code == 200 }?.let {
                      runCatching { json.decodeFromString<OlWorkRecord>(it) }.getOrNull()
                    }
                  }
                }
                val edAuthDefs =
                  editionAuthorKeys.map { aKey -> async { fetchAuthorContributor(aKey) } }
                val fetchedWork = workDef.await()
                val workOnlyAuthorKeys =
                  fetchedWork?.authors?.mapNotNull { it.author?.key }.orEmpty()
                    .filter { it !in editionAuthorKeys }
                    .distinct()
                    .take((4 - editionAuthorKeys.size).coerceAtLeast(0))
                val extraAuthDefs =
                  workOnlyAuthorKeys.map { aKey -> async { fetchAuthorContributor(aKey) } }
                val authors = (edAuthDefs.awaitAll() + extraAuthDefs.awaitAll()).filterNotNull()
                fetchedWork to authors
              }

            val candidate = buildCandidateFromEdition(edition, work, resolvedAuthors, explicitRequestedLang = null)
            return@withContext SourceAdapterResult(
              status =
                SourceQueryStatus(
                  source = source,
                  state = ProviderOutcomeState.SUCCESS,
                  count = 1,
                  totalAvailable = 1,
                  hasMore = false,
                  durationMs = System.currentTimeMillis() - startMs,
                ),
              candidates = listOf(candidate),
            )
          }

          // Fallback to search.json?isbn=...
          val searchUrl =
            "$baseUrl/search.json?isbn=${URLEncoder.encode(target, "UTF-8")}" +
              "&fields=$SEARCH_FIELDS&limit=8"
          executeSearchUrl(searchUrl, page = 1, limit = 8, explicitRequestedLang = null, startMs = startMs)
        }
        .getOrElse { err ->
          if (err is CancellationException) throw err
          val msg = if (err is SocketTimeoutException) "Timeout: ${err.message ?: "socket read"}" else err.message
          SourceAdapterResult(
            SourceQueryStatus(source, ProviderOutcomeState.ERROR, message = msg, durationMs = System.currentTimeMillis() - startMs)
          )
        }
    }

  override suspend fun searchByQuery(query: String): SourceAdapterResult =
    withContext(Dispatchers.IO) {
      val trimmed = query.trim()
      if (trimmed.isEmpty()) {
        return@withContext SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
      }
      val startMs = System.currentTimeMillis()
      runCatching {
          val encoded = URLEncoder.encode(trimmed, "UTF-8")
          val url = "$baseUrl/search.json?q=$encoded&fields=$SEARCH_FIELDS&limit=25"
          executeSearchUrl(url, page = 1, limit = 25, explicitRequestedLang = null, startMs = startMs)
        }
        .getOrElse { err ->
          if (err is CancellationException) throw err
          val msg = if (err is SocketTimeoutException) "Timeout: ${err.message ?: "socket read"}" else err.message
          SourceAdapterResult(
            SourceQueryStatus(source, ProviderOutcomeState.ERROR, message = msg, durationMs = System.currentTimeMillis() - startMs)
          )
        }
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
      val limit = request.pageSize.coerceIn(15, 30)
      val explicitLang = intent.explicitLanguageRequest
      val plan = QueryPlanner.planOpenLibrary(intent = intent, page = page, limit = limit)

      runCatching {
          val urls = plan.primaryQueries.map(::buildSearchUrl)

          val outcomes =
            coroutineScope {
              urls
                .map { url ->
                  async {
                    executeSearchUrl(url, page = page, limit = limit, explicitRequestedLang = explicitLang, startMs = startMs)
                  }
                }
                .awaitAll()
            }

          val successful = outcomes.filter { it.status.state == ProviderOutcomeState.SUCCESS || it.status.state == ProviderOutcomeState.EMPTY }
          if (successful.isEmpty()) {
            val elapsed = System.currentTimeMillis() - startMs
            return@withContext outcomes.firstOrNull()
              ?: SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.ERROR, durationMs = elapsed))
          }

          val combinedInitial =
            outcomes
              .flatMap { it.candidates }
              .distinctBy { it.candidateId }

          val shouldRunFallback =
            plan.fallbackQuery != null &&
              (combinedInitial.isEmpty() || (intent.significantTokens.size >= 3 && combinedInitial.size < 2))

          val allOutcomes =
            if (shouldRunFallback) {
              val fbOutcome =
                executeSearchUrl(
                  url = buildSearchUrl(plan.fallbackQuery!!),
                  page = 1,
                  limit = plan.fallbackQuery.limit,
                  explicitRequestedLang = explicitLang,
                  startMs = startMs,
                )
              outcomes + fbOutcome
            } else {
              outcomes
            }

          var combined =
            allOutcomes
              .flatMap { it.candidates }
              .distinctBy { it.candidateId }

          if (plan.shouldEnrichMissingPtWorks && combined.isNotEmpty()) {
            val enrichedExtras =
              withTimeoutOrNull(1_800L) {
                enrichWorksMissingPortugueseEditions(combined, explicitLang)
              }.orEmpty()
            if (enrichedExtras.isNotEmpty()) {
              combined = (combined + enrichedExtras).distinctBy { it.candidateId }
            }
          }

          val elapsed = System.currentTimeMillis() - startMs
          val maxTotal = allOutcomes.mapNotNull { it.status.totalAvailable }.maxOrNull()
          val anyMore = allOutcomes.any { it.status.hasMore }
          SourceAdapterResult(
            status =
              SourceQueryStatus(
                source = source,
                state = if (combined.isEmpty()) ProviderOutcomeState.EMPTY else ProviderOutcomeState.SUCCESS,
                count = combined.size,
                totalAvailable = maxTotal,
                hasMore = anyMore,
                durationMs = elapsed,
              ),
            candidates = combined,
          )
        }
        .getOrElse { err ->
          if (err is CancellationException) throw err
          val msg = if (err is SocketTimeoutException) "Timeout: ${err.message ?: "socket read"}" else err.message
          SourceAdapterResult(
            SourceQueryStatus(source, ProviderOutcomeState.ERROR, message = msg, durationMs = System.currentTimeMillis() - startMs)
          )
        }
    }

  private fun buildSearchUrl(spec: OpenLibraryQuerySpec): String {
    val encoded = URLEncoder.encode(spec.queryValue.trim(), "UTF-8")
    return when (spec.mode) {
      OpenLibraryQuerySpec.Mode.ISBN ->
        "$baseUrl/search.json?isbn=$encoded&fields=$SEARCH_FIELDS&limit=${spec.limit}"
      OpenLibraryQuerySpec.Mode.AUTHOR ->
        "$baseUrl/search.json?author=$encoded&sort=${spec.sort ?: "readinglog"}&fields=$SEARCH_FIELDS&limit=${spec.limit}&page=${spec.page}"
      OpenLibraryQuerySpec.Mode.TITLE ->
        "$baseUrl/search.json?title=$encoded&fields=$SEARCH_FIELDS&limit=${spec.limit}&page=${spec.page}"
      OpenLibraryQuerySpec.Mode.TITLE_AND_AUTHOR -> {
        val encAuthor = URLEncoder.encode(spec.extraAuthorValue.orEmpty().trim(), "UTF-8")
        "$baseUrl/search.json?title=$encoded&author=$encAuthor&fields=$SEARCH_FIELDS&limit=${spec.limit}&page=${spec.page}"
      }
      OpenLibraryQuerySpec.Mode.GENERAL_Q ->
        "$baseUrl/search.json?q=$encoded&fields=$SEARCH_FIELDS&limit=${spec.limit}&page=${spec.page}"
    }
  }

  private suspend fun enrichWorksMissingPortugueseEditions(
    candidates: List<SearchEditionCandidate>,
    explicitRequestedLang: String?,
  ): List<SearchEditionCandidate> = supervisorScope {
    val byWork = candidates.filter { it.externalWorkKey?.startsWith("/works/") == true }.groupBy { it.externalWorkKey!! }
    val targetWorks =
      byWork.entries
        .mapNotNull { (workKey, workCands) ->
          val rep = workCands.maxByOrNull { it.workEditionCount } ?: return@mapNotNull null
          val claimsPt = "pt" in rep.availableWorkLanguages
          val embeddedPtCount = workCands.count { it.languageTierRank <= LanguageTier.PT_UNCERTAIN.rank }
          if ((claimsPt && embeddedPtCount < 5 && rep.workEditionCount >= 8) ||
            (workCands.size < 4 && rep.workEditionCount >= 12)
          ) {
            Triple(workKey, rep, workCands.mapNotNull { it.externalIds["openlibrary_edition"] }.toSet())
          } else {
            null
          }
        }
        .sortedByDescending { it.second.workReadingLogCount + (it.second.workEditionCount * 20) }
        .take(2)

    if (targetWorks.isEmpty()) return@supervisorScope emptyList()

    targetWorks
      .map { (workKey, rep, existingEdKeys) ->
        async {
          withTimeoutOrNull(1_400L) {
            runCatching {
                val resp = fetchJson("$baseUrl$workKey/editions.json?limit=60")
                val body = resp.body?.takeIf { resp.code == 200 } ?: return@runCatching emptyList()
                val parsed = json.decodeFromString<OlWorkEditionsResponse>(body)
                val syntheticWork =
                  OlWorkRecord(
                    key = workKey,
                    title = rep.workTitle,
                    subtitle = rep.subtitle,
                  )
                parsed.entries
                  .asSequence()
                  .filter { ed -> ed.key == null || ed.key !in existingEdKeys }
                  .map { ed ->
                    buildCandidateFromEdition(
                      edition = ed,
                      work = syntheticWork,
                      resolvedWorkAuthors = rep.contributors,
                      explicitRequestedLang = explicitRequestedLang,
                    ).copy(
                      workEditionCount = rep.workEditionCount,
                      workReadingLogCount = rep.workReadingLogCount,
                      workRatingsCount = rep.workRatingsCount,
                      availableWorkLanguages = rep.availableWorkLanguages,
                    )
                  }
                  .filter { it.languageTierRank <= LanguageTier.EN.rank }
                  .sortedWith(editionRichnessComparator())
                  .take(16)
                  .toList()
              }
              .getOrElse { err ->
                if (err is CancellationException) throw err
                emptyList()
              }
          }.orEmpty()
        }
      }
      .awaitAll()
      .flatten()
  }

  override suspend fun fetchEditionsForWork(externalWorkKey: String): SourceAdapterResult =
    withContext(Dispatchers.IO) {
      val startMs = System.currentTimeMillis()
      if (!externalWorkKey.startsWith("/works/")) {
        return@withContext SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
      }
      runCatching {
          val (work, editionsResp) =
            coroutineScope {
              val wDef = async {
                val resp = fetchJson("$baseUrl$externalWorkKey.json")
                resp.body?.takeIf { resp.code == 200 }?.let {
                  runCatching { json.decodeFromString<OlWorkRecord>(it) }.getOrNull()
                }
              }
              val eDef = async { fetchJson("$baseUrl$externalWorkKey/editions.json?limit=80") }
              wDef.await() to eDef.await()
            }

          if (editionsResp.code == 429) {
            return@withContext SourceAdapterResult(
              SourceQueryStatus(source, ProviderOutcomeState.RATE_LIMITED, durationMs = System.currentTimeMillis() - startMs)
            )
          }
          val body = editionsResp.body
          if (editionsResp.code != 200 || body == null) {
            return@withContext SourceAdapterResult(
              SourceQueryStatus(source, ProviderOutcomeState.EMPTY, durationMs = System.currentTimeMillis() - startMs)
            )
          }
          val parsed = json.decodeFromString<OlWorkEditionsResponse>(body)
          val authorNames =
            coroutineScope {
              work?.authors?.mapNotNull { it.author?.key }?.take(3)?.map { aKey ->
                async { fetchAuthorContributor(aKey) }
              }?.awaitAll()?.filterNotNull().orEmpty()
            }

          val candidates =
            parsed.entries
              .filter { !it.title.isNullOrBlank() || work?.title != null }
              .map { ed -> buildCandidateFromEdition(ed, work, authorNames, explicitRequestedLang = null) }
              .sortedWith(editionRichnessComparator())

          SourceAdapterResult(
            status =
              SourceQueryStatus(
                source = source,
                state = if (candidates.isEmpty()) ProviderOutcomeState.EMPTY else ProviderOutcomeState.SUCCESS,
                count = candidates.size,
                totalAvailable = parsed.size ?: candidates.size,
                durationMs = System.currentTimeMillis() - startMs,
              ),
            candidates = candidates,
          )
        }
        .getOrElse { err ->
          if (err is CancellationException) throw err
          val msg = if (err is SocketTimeoutException) "Timeout: ${err.message ?: "socket read"}" else err.message
          SourceAdapterResult(
            SourceQueryStatus(source, ProviderOutcomeState.ERROR, message = msg, durationMs = System.currentTimeMillis() - startMs)
          )
        }
    }

  private suspend fun fetchAuthorContributor(aKey: String): CandidateContributor? {
    val resp = fetchJson("$baseUrl$aKey.json")
    val name =
      resp.body?.takeIf { resp.code == 200 }?.let { body ->
        runCatching { json.decodeFromString<OlAuthorRecord>(body) }.getOrNull()?.name
      } ?: return null
    val clean = BibliographicMath.sanitizeDisplayText(name) ?: return null
    return CandidateContributor(name = clean, role = ContributorRole.AUTHOR, externalId = aKey)
  }

  private suspend fun executeSearchUrl(
    url: String,
    page: Int,
    limit: Int,
    explicitRequestedLang: String?,
    startMs: Long,
  ): SourceAdapterResult {
    val resp = fetchJson(url)
    val elapsed = System.currentTimeMillis() - startMs
    if (resp.code == 429) {
      return SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.RATE_LIMITED, durationMs = elapsed))
    }
    val body = resp.body
    if (resp.code != 200 || body == null) {
      return SourceAdapterResult(
        SourceQueryStatus(
          source = source,
          state = if (resp.code >= 500 || resp.code == 403) ProviderOutcomeState.ERROR else ProviderOutcomeState.EMPTY,
          message = if (resp.code != 200) "HTTP ${resp.code}" else null,
          durationMs = elapsed,
        )
      )
    }
    val parsed = json.decodeFromString<OlSearchResponse>(body)
    val candidates = mutableListOf<SearchEditionCandidate>()

    for (doc in parsed.docs) {
      val workTitle = BibliographicMath.sanitizeDisplayText(doc.title) ?: continue
      val contributors =
        doc.authorNames.mapIndexedNotNull { idx, name ->
          val cleanName = BibliographicMath.sanitizeDisplayText(name) ?: return@mapIndexedNotNull null
          CandidateContributor(
            name = cleanName,
            role = if (idx == 0) ContributorRole.AUTHOR else ContributorRole.CO_AUTHOR,
            externalId = doc.authorKeys.getOrNull(idx),
          )
        }
      val workLangs = doc.language.mapNotNull { BibliographicMath.normalizeLanguageCode(it) }.distinct()
      val workEditionCount = doc.editionCount ?: 0
      val workReadingLog = doc.readingLogCount ?: 0
      val workRatings = doc.ratingsCount ?: 0

      // 1. Parse nested concrete editions returned by Open Library (each keeps its OWN explicit language!)
      val nestedCandidates =
        doc.editions?.docs.orEmpty().mapNotNull { ed ->
          val edTitle = BibliographicMath.sanitizeDisplayText(ed.title) ?: workTitle
          val parsedIsbns = ed.isbn.mapNotNull { BibliographicMath.parseIsbn(it) }
          val isbn13 = parsedIsbns.firstNotNullOfOrNull { it.isbn13 }
          val isbn10 = parsedIsbns.firstNotNullOfOrNull { it.isbn10 }
          val pub = BibliographicMath.sanitizeDisplayText(ed.publisher.firstOrNull())
          val explicitEdLang = ed.language.firstOrNull()
          val langAssessment =
            BibliographicMath.assessLanguage(
              explicitLanguageRaw = explicitEdLang,
              isbn13Or10 = isbn13 ?: isbn10,
              publisher = pub,
              editionTitle = edTitle,
              explicitRequestedLanguage = explicitRequestedLang,
            )
          val pubYear = ed.publishYear.firstOrNull { it in 1400..2100 } ?: doc.firstPublishYear?.takeIf { it in 1400..2100 }
          val covers = buildList {
            (ed.coverId ?: doc.coverId)?.takeIf { it > 0 }?.let { cid ->
              add(
                CoverCandidate(
                  imageUrl = "https://covers.openlibrary.org/b/id/$cid-L.jpg?default=false",
                  source = source,
                  matchesIsbn = isbn13 != null || isbn10 != null,
                  matchesLanguage = langAssessment.normalizedCode != null,
                  matchesPublisherYear = pub != null && pubYear != null,
                  widthHint = 333,
                  heightHint = 500,
                )
              )
            }
            isbn13?.let { code ->
              add(
                CoverCandidate(
                  imageUrl = "https://covers.openlibrary.org/b/isbn/$code-L.jpg?default=false",
                  source = source,
                  matchesIsbn = true,
                  matchesLanguage = langAssessment.normalizedCode != null,
                  widthHint = 333,
                  heightHint = 500,
                )
              )
            }
          }
          val origTitle =
            workTitle.takeIf {
              BibliographicMath.normalizeBaseTitle(edTitle) != BibliographicMath.normalizeBaseTitle(workTitle)
            }
          SearchEditionCandidate(
            candidateId = "ol_ed_${ed.key ?: doc.key ?: edTitle.hashCode()}_${isbn13 ?: ed.hashCode()}",
            workTitle = workTitle,
            subtitle = BibliographicMath.sanitizeDisplayText(doc.subtitle),
            originalTitle = origTitle,
            editionTitle = edTitle,
            contributors = contributors,
            publisher = pub,
            publicationYear = pubYear,
            totalPages = (ed.pagesMedian ?: doc.medianPages)?.takeIf { it in 1..50_000 },
            isbn10 = isbn10,
            isbn13 = isbn13,
            language = langAssessment.normalizedCode,
            categories = doc.subject.take(8),
            coverCandidates = covers,
            externalWorkKey = doc.key,
            externalIds =
              buildMap {
                doc.key?.let { put("openlibrary_work", it) }
                ed.key?.let { put("openlibrary_edition", it) }
              },
            sources = listOf(source),
            canonicalLanguageTag = langAssessment.canonicalTag,
            languageTierRank = langAssessment.tier.rank,
            languageConfidenceName = langAssessment.confidence.name,
            hasLanguageConflict = langAssessment.hasConflict,
            workEditionCount = workEditionCount,
            workReadingLogCount = workReadingLog,
            workRatingsCount = workRatings,
            availableWorkLanguages = workLangs,
          )
        }

      if (nestedCandidates.isNotEmpty()) {
        val sortedNested = nestedCandidates.sortedWith(editionRichnessComparator())
        val primaryTiered = sortedNested.filter { it.languageTierRank <= LanguageTier.EN.rank }.take(16)
        val secondaryTiered = sortedNested.filter { it.languageTierRank > LanguageTier.EN.rank }.take(4)
        val prioritized = (primaryTiered + secondaryTiered).ifEmpty { sortedNested.take(12) }
        candidates.addAll(prioritized)
      } else {
        // Fallback when nested editions are absent (e.g., author sort query):
        // Do NOT blindly pick doc.language.firstOrNull() ("cat", "epo", "rus") when work clearly offers "pt" or "en".
        val parsedIsbns = doc.isbn.mapNotNull { BibliographicMath.parseIsbn(it) }
        val isbn13 = parsedIsbns.firstNotNullOfOrNull { it.isbn13 }
        val isbn10 = parsedIsbns.firstNotNullOfOrNull { it.isbn10 }
        val pub = BibliographicMath.sanitizeDisplayText(doc.publisher.firstOrNull())

        val selectedDocLang =
          when {
            explicitRequestedLang != null && explicitRequestedLang.substringBefore('-') in workLangs ->
              explicitRequestedLang
            "pt" in workLangs && (BibliographicMath.isBrazilianIsbn(isbn13 ?: isbn10) || looksPortugueseTitle(workTitle)) -> "por"
            "en" in workLangs && !looksPortugueseTitle(workTitle) -> "eng"
            "pt" in workLangs -> "por"
            "en" in workLangs -> "eng"
            else -> doc.language.firstOrNull()
          }

        val langAssessment =
          BibliographicMath.assessLanguage(
            explicitLanguageRaw = selectedDocLang,
            isbn13Or10 = isbn13 ?: isbn10,
            publisher = pub,
            editionTitle = workTitle,
            explicitRequestedLanguage = explicitRequestedLang,
          )

        val covers = buildList {
          doc.coverId?.takeIf { it > 0 }?.let { cid ->
            add(
              CoverCandidate(
                imageUrl = "https://covers.openlibrary.org/b/id/$cid-L.jpg?default=false",
                source = source,
                matchesLanguage = langAssessment.normalizedCode != null,
                matchesPublisherYear = doc.firstPublishYear != null,
                widthHint = 333,
                heightHint = 500,
              )
            )
          }
          isbn13?.let { code ->
            add(
              CoverCandidate(
                imageUrl = "https://covers.openlibrary.org/b/isbn/$code-L.jpg?default=false",
                source = source,
                matchesIsbn = true,
                widthHint = 333,
                heightHint = 500,
              )
            )
          }
        }
        candidates.add(
          SearchEditionCandidate(
            candidateId = "ol_${doc.key ?: workTitle.hashCode()}_${isbn13 ?: doc.editionKeys.firstOrNull().orEmpty()}",
            workTitle = workTitle,
            subtitle = BibliographicMath.sanitizeDisplayText(doc.subtitle),
            contributors = contributors,
            publisher = pub,
            publicationYear = doc.firstPublishYear?.takeIf { it in 1400..2100 },
            totalPages = doc.medianPages?.takeIf { it in 1..50_000 },
            isbn10 = isbn10,
            isbn13 = isbn13,
            language = langAssessment.normalizedCode,
            categories = doc.subject.take(8),
            coverCandidates = covers,
            externalWorkKey = doc.key,
            externalIds =
              buildMap {
                doc.key?.let { put("openlibrary_work", it) }
                doc.editionKeys.firstOrNull()?.let { put("openlibrary_edition", "/books/$it") }
              },
            sources = listOf(source),
            canonicalLanguageTag = langAssessment.canonicalTag,
            languageTierRank = langAssessment.tier.rank,
            languageConfidenceName = langAssessment.confidence.name,
            hasLanguageConflict = langAssessment.hasConflict,
            workEditionCount = workEditionCount,
            workReadingLogCount = workReadingLog,
            workRatingsCount = workRatings,
            availableWorkLanguages = workLangs,
          )
        )
      }
    }

    val totalAvailable = parsed.numFound.takeIf { it > 0 } ?: parsed.docs.size
    val hasMore = (page * limit) < parsed.numFound && parsed.docs.isNotEmpty()

    return SourceAdapterResult(
      status =
        SourceQueryStatus(
          source = source,
          state = if (candidates.isEmpty()) ProviderOutcomeState.EMPTY else ProviderOutcomeState.SUCCESS,
          count = candidates.size,
          totalAvailable = totalAvailable,
          hasMore = hasMore,
          durationMs = elapsed,
        ),
      candidates = candidates,
    )
  }

  private fun looksPortugueseTitle(title: String): Boolean {
    val lower = title.lowercase()
    return Regex("[ãõçáéíóúâêôà]").containsMatchIn(lower) ||
      Regex("\\b(e a|e o|dos|das|uma|um|memorias|senhor|principe|casmurro|alienista|vorazes)\\b")
        .containsMatchIn(BibliographicMath.normalizeText(lower))
  }

  private fun buildCandidateFromEdition(
    edition: OlEditionRecord,
    work: OlWorkRecord?,
    resolvedWorkAuthors: List<CandidateContributor>,
    explicitRequestedLang: String?,
  ): SearchEditionCandidate {
    val parsed13 = edition.isbn13.firstNotNullOfOrNull { BibliographicMath.parseIsbn(it) }
    val parsed10 = edition.isbn10.firstNotNullOfOrNull { BibliographicMath.parseIsbn(it) }
    val final13 = parsed13?.isbn13 ?: parsed10?.isbn13
    val final10 = parsed10?.isbn10 ?: parsed13?.isbn10
    val pubYear = extractYear(edition.publishDate)
    val pub = BibliographicMath.sanitizeDisplayText(edition.publishers.firstOrNull())
    val edTitle = BibliographicMath.sanitizeDisplayText(edition.title)
    val workTitle = BibliographicMath.sanitizeDisplayText(work?.title) ?: edTitle.orEmpty()
    val rawEdLang = edition.languages.firstOrNull()?.key?.substringAfterLast("/")
    val langAssessment =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = rawEdLang,
        isbn13Or10 = final13 ?: final10,
        publisher = pub,
        editionTitle = edTitle ?: workTitle,
        explicitRequestedLanguage = explicitRequestedLang,
      )
    val lang = langAssessment.normalizedCode

    val editionContributors =
      edition.contributors.mapNotNull { c ->
        val name = BibliographicMath.sanitizeDisplayText(c.name) ?: return@mapNotNull null
        val roleLower = c.role.orEmpty().lowercase()
        val role =
          when {
            "translat" in roleLower || "tradut" in roleLower -> ContributorRole.TRANSLATOR
            "illustrat" in roleLower || "ilustra" in roleLower -> ContributorRole.ILLUSTRATOR
            "editor" in roleLower || "organiz" in roleLower -> ContributorRole.EDITOR_ORGANIZER
            "preface" in roleLower || "introduc" in roleLower -> ContributorRole.PREFACE_BY
            else -> ContributorRole.OTHER
          }
        CandidateContributor(name = name, role = role)
      }

    val allContributors = (resolvedWorkAuthors + editionContributors).distinctBy {
      "${BibliographicMath.normalizeText(it.name)}_${it.role}"
    }

    val editionDesc = extractDescription(edition.description)
    val workDesc = extractDescription(work?.description)
    val synopses = buildList {
      editionDesc?.let {
        add(
          SynopsisCandidate(
            text = it,
            language = lang,
            source = source,
            editionLabel = edition.editionName ?: pub,
          )
        )
      }
      if (workDesc != null && workDesc != editionDesc) {
        add(
          SynopsisCandidate(
            text = workDesc,
            language = null,
            source = source,
            editionLabel = workTitle,
          )
        )
      }
    }

    val covers = buildList {
      edition.covers.filter { it > 0 }.take(3).forEach { cid ->
        add(
          CoverCandidate(
            imageUrl = "https://covers.openlibrary.org/b/id/$cid-L.jpg?default=false",
            source = source,
            matchesIsbn = final13 != null || final10 != null,
            matchesPublisherYear = pub != null && pubYear != null,
            matchesLanguage = lang != null,
            widthHint = 333,
            heightHint = 500,
          )
        )
      }
      final13?.let { code ->
        add(
          CoverCandidate(
            imageUrl = "https://covers.openlibrary.org/b/isbn/$code-L.jpg?default=false",
            source = source,
            matchesIsbn = true,
            matchesPublisherYear = pub != null && pubYear != null,
            matchesLanguage = lang != null,
            widthHint = 333,
            heightHint = 500,
          )
        )
      }
    }

    val format =
      edition.physicalFormat?.lowercase()?.let { fmt ->
        when {
          "hardcover" in fmt || "capa dura" in fmt -> EditionFormat.HARDCOVER
          "paperback" in fmt || "brochura" in fmt -> EditionFormat.PAPERBACK
          "pocket" in fmt || "mass market" in fmt -> EditionFormat.POCKET
          "ebook" in fmt || "kindle" in fmt || "digital" in fmt -> EditionFormat.EBOOK
          else -> null
        }
      }

    val origTitle =
      edTitle?.let { edT ->
        workTitle.takeIf {
          BibliographicMath.normalizeBaseTitle(edT) != BibliographicMath.normalizeBaseTitle(it)
        }
      }

    return SearchEditionCandidate(
      candidateId = "ol_ed_${edition.key ?: final13 ?: final10 ?: edition.title.hashCode()}",
      workTitle = workTitle,
      subtitle = BibliographicMath.sanitizeDisplayText(edition.subtitle) ?: BibliographicMath.sanitizeDisplayText(work?.subtitle),
      originalTitle = origTitle,
      editionTitle = edTitle,
      contributors = allContributors,
      publisher = pub,
      publicationYear = pubYear,
      totalPages = edition.numberOfPages?.takeIf { it in 1..50_000 },
      isbn10 = final10,
      isbn13 = final13,
      language = lang,
      format = format,
      editionLabel = BibliographicMath.sanitizeDisplayText(edition.editionName),
      seriesName = BibliographicMath.sanitizeDisplayText(edition.series.firstOrNull()),
      categories = (edition.subjects + work?.subjects.orEmpty()).distinct().take(10),
      coverCandidates = covers,
      synopsisCandidates = synopses,
      externalWorkKey = work?.key ?: edition.works.firstOrNull()?.key,
      externalIds =
        buildMap {
          (work?.key ?: edition.works.firstOrNull()?.key)?.let { put("openlibrary_work", it) }
          edition.key?.let { put("openlibrary_edition", it) }
        },
      sources = listOf(source),
      canonicalLanguageTag = langAssessment.canonicalTag,
      languageTierRank = langAssessment.tier.rank,
      languageConfidenceName = langAssessment.confidence.name,
      hasLanguageConflict = langAssessment.hasConflict,
    )
  }

  private fun extractDescription(element: JsonElement?): String? {
    if (element == null) return null
    val raw =
      when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> element.jsonObject["value"]?.jsonPrimitive?.contentOrNull
        else -> null
      }
    return BibliographicMath.stripHtmlTags(raw)
  }

  private fun editionRichnessComparator(): Comparator<SearchEditionCandidate> =
    compareBy(
      { it.languageTierRank },
      { !BibliographicMath.isBrazilianIsbn(it.bestIsbn) },
      { !BibliographicMath.isKnownBrazilianPublisher(it.publisher) },
      { it.coverCandidates.isEmpty() },
      { it.bestIsbn == null },
      { it.publisher.isNullOrBlank() },
    )

  private suspend fun fetchJson(url: String): HttpTextSnapshot {
    val request = Request.Builder().url(url).get().build()
    return client.executeTextCall(request)
  }

  private companion object {
    const val SEARCH_FIELDS =
      "key,title,subtitle,author_name,author_key,first_publish_year,isbn,edition_key,edition_count,ratings_count,readinglog_count,language,subject,cover_i,number_of_pages_median,publisher,editions,editions.key,editions.title,editions.language,editions.publisher,editions.isbn,editions.cover_i,editions.publish_year,editions.number_of_pages_median"
  }
}
