package com.example.core.bibliographic.domain

import com.example.core.bibliographic.network.AdapterSearchRequest
import com.example.core.bibliographic.network.BibliographicSourceAdapter
import com.example.core.bibliographic.network.ConnectivityChecker
import com.example.core.bibliographic.network.SourceAdapterResult
import com.example.core.data.local.db.BookDao
import com.example.core.data.local.db.EditionDao
import com.example.core.data.local.db.SearchCacheDao
import com.example.core.data.local.db.SearchCacheEntity
import com.example.core.data.repository.LibraryRepository
import com.example.core.model.BibliographicMath
import com.example.core.model.CatalogBook
import com.example.core.model.HybridSearchResponse
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import com.example.core.model.SourceQueryStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class CachedSearchPayload(
  val primaryGroups: List<SearchWorkGroup> = emptyList(),
  val secondaryGroups: List<SearchWorkGroup> = emptyList(),
  val totalFoundHint: Int? = null,
  val hasMoreResults: Boolean = false,
)

private data class PaginationSession(
  val intent: ParsedSearchIntent,
  val preferredLanguage: String?,
  val expandSecondary: Boolean,
  val currentPage: Int,
  val accumulatedCandidates: List<SearchEditionCandidate>,
  val statuses: Map<MetadataSource, SourceQueryStatus>,
)

class HybridSearchOrchestrator(
  private val openLibraryAdapter: BibliographicSourceAdapter,
  private val googleBooksAdapter: BibliographicSourceAdapter,
  private val crossrefAdapter: BibliographicSourceAdapter,
  private val wikidataAdapter: BibliographicSourceAdapter,
  private val brasilApiAdapter: BibliographicSourceAdapter,
  private val connectivityChecker: ConnectivityChecker,
  private val searchCacheDao: SearchCacheDao,
  private val editionDao: EditionDao,
  private val bookDao: BookDao,
  private val libraryRepository: LibraryRepository,
  private val json: Json = com.example.core.bibliographic.network.BibliographicHttpClients.json,
) {

  private val memoryCacheLock = Any()
  private val memoryCache =
    object : LinkedHashMap<String, HybridSearchResponse>(MEMORY_CACHE_MAX_ENTRIES, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HybridSearchResponse>?): Boolean =
        size > MEMORY_CACHE_MAX_ENTRIES
    }

  private val paginationLock = Any()
  private val paginationSessions = mutableMapOf<String, PaginationSession>()

  suspend fun findLocalMatchesOnly(rawQuery: String): List<CatalogBook> {
    val trimmed = rawQuery.trim()
    if (trimmed.isEmpty()) return emptyList()
    val parsedIsbn = BibliographicMath.parseIsbn(trimmed)
    return findLocalMatches(trimmed, parsedIsbn?.isbn13, parsedIsbn?.isbn10, parsedIsbn?.cleaned)
  }

  suspend fun search(
    rawQuery: String,
    preferredLanguage: String? = null,
    expandSecondary: Boolean = false,
    forceRefresh: Boolean = false,
  ): HybridSearchResponse =
    searchProgressive(
      rawQuery = rawQuery,
      preferredLanguage = preferredLanguage,
      expandSecondary = expandSecondary,
      forceRefresh = forceRefresh,
    ).last()

  fun searchProgressive(
    rawQuery: String,
    preferredLanguage: String? = null,
    expandSecondary: Boolean = false,
    forceRefresh: Boolean = false,
  ): Flow<HybridSearchResponse> = channelFlow {
    val startClockMs = System.currentTimeMillis()
    val intent = QueryIntentParser.parse(rawQuery)
    val trimmed = intent.rawQuery
    val parsedIsbn = intent.parsedIsbn
    val isIsbn = intent.intentType == QueryIntentType.ISBN_EXACT

    if (trimmed.isBlank()) {
      send(
        HybridSearchResponse(
          query = trimmed,
          isIsbnQuery = false,
          isFromCache = false,
          isOffline = !connectivityChecker.isOnline(),
          localMatches = emptyList(),
          workGroups = emptyList(),
          secondaryWorkGroups = emptyList(),
          sourceStatuses = emptyList(),
          expandedSecondarySources = expandSecondary,
        )
      )
      return@channelFlow
    }

    val cacheKey = buildCacheKey(intent, preferredLanguage, expandSecondary)
    val localMatches =
      findLocalMatches(trimmed, parsedIsbn?.isbn13, parsedIsbn?.isbn10, parsedIsbn?.cleaned)
    val online = connectivityChecker.isOnline()

    // 1. Check fast in-memory cache first
    if (!forceRefresh) {
      val memHit = synchronized(memoryCacheLock) { memoryCache[cacheKey] }
      if (memHit != null) {
        val elapsed = (System.currentTimeMillis() - startClockMs).coerceAtLeast(1L)
        val refreshed =
          annotateResponseWithLocalMatches(
            memHit.copy(
              isFromCache = true,
              isOffline = !online,
              localMatches = localMatches,
              isPartialResult = false,
              firstResultLatencyMs = elapsed,
              totalLatencyMs = elapsed,
            )
          )
        send(refreshed)
        return@channelFlow
      }
    }

    // 2. Check persistent Room cache
    if (!forceRefresh) {
      val cached =
        if (online) searchCacheDao.getValidEntry(cacheKey, startClockMs)
        else searchCacheDao.getAnyEntry(cacheKey)
      if (cached != null) {
        val decodedPayload = decodeCachedPayload(cached.payloadJson)
        if (decodedPayload != null) {
          searchCacheDao.touchEntry(cacheKey, startClockMs)
          val allLocalBooks = libraryRepository.observeAllBooks().first()
          val primaryAnnotated = annotateGroups(decodedPayload.primaryGroups, allLocalBooks)
          val secondaryAnnotated = annotateGroups(decodedPayload.secondaryGroups, allLocalBooks)
          val cachedCount = (primaryAnnotated + secondaryAnnotated).sumOf { it.editions.size }
          val cachedStatuses =
            cached.sourcesUsed
              .split(",")
              .mapNotNull { name ->
                runCatching { MetadataSource.valueOf(name.trim()) }.getOrNull()?.let { src ->
                  SourceQueryStatus(src, ProviderOutcomeState.CACHED, count = cachedCount)
                }
              }
          val elapsed = (System.currentTimeMillis() - startClockMs).coerceAtLeast(1L)
          val cachedResponse =
            HybridSearchResponse(
              query = trimmed,
              isIsbnQuery = isIsbn,
              isFromCache = true,
              isOffline = !online,
              localMatches = localMatches,
              workGroups = primaryAnnotated,
              secondaryWorkGroups = secondaryAnnotated,
              sourceStatuses = cachedStatuses,
              expandedSecondarySources = expandSecondary,
              hasMoreResults = decodedPayload.hasMoreResults,
              isPartialResult = false,
              totalFoundHint = decodedPayload.totalFoundHint,
              firstResultLatencyMs = elapsed,
              totalLatencyMs = elapsed,
            )
          synchronized(memoryCacheLock) { memoryCache[cacheKey] = cachedResponse }
          send(cachedResponse)
          return@channelFlow
        }
      }
    }

    // 3. Offline without cache hit
    if (!online) {
      val elapsed = System.currentTimeMillis() - startClockMs
      send(
        HybridSearchResponse(
          query = trimmed,
          isIsbnQuery = isIsbn,
          isFromCache = false,
          isOffline = true,
          localMatches = localMatches,
          workGroups = emptyList(),
          secondaryWorkGroups = emptyList(),
          sourceStatuses =
            listOf(
              SourceQueryStatus(MetadataSource.OPEN_LIBRARY, ProviderOutcomeState.OFFLINE),
              SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.OFFLINE),
            ),
          expandedSecondarySources = expandSecondary,
          totalLatencyMs = elapsed,
        )
      )
      return@channelFlow
    }

    // 4. Online parallel source orchestration with progressive emissions
    val adapters = selectAdapters(parsedIsbn = parsedIsbn, isIsbn = isIsbn, expandSecondary = expandSecondary)
    val statusMap = linkedMapOf<MetadataSource, SourceQueryStatus>()
    adapters.forEach { adapter ->
      statusMap[adapter.source] = SourceQueryStatus(adapter.source, ProviderOutcomeState.LOADING)
    }

    // Emit immediate local matches + loading status pills before network completes
    send(
      HybridSearchResponse(
        query = trimmed,
        isIsbnQuery = isIsbn,
        isFromCache = false,
        isOffline = false,
        localMatches = localMatches,
        workGroups = emptyList(),
        secondaryWorkGroups = emptyList(),
        sourceStatuses = statusMap.values.toList(),
        expandedSecondarySources = expandSecondary,
        isPartialResult = true,
        firstResultLatencyMs = if (localMatches.isNotEmpty()) System.currentTimeMillis() - startClockMs else null,
      )
    )

    val mutex = Mutex()
    val accumulatedCandidates = mutableListOf<SearchEditionCandidate>()
    var completedAdapters = 0
    var firstResultLatency: Long? = null
    val allLocalBooks = libraryRepository.observeAllBooks().first()
    val request =
      AdapterSearchRequest(
        rawQuery = trimmed,
        parsedIntent = intent,
        page = 1,
        pageSize = DEFAULT_PAGE_SIZE,
        expandSecondary = expandSecondary,
      )

    var latestResponse: HybridSearchResponse? = null

    supervisorScope {
      adapters.forEach { adapter ->
        launch {
          val adapterStart = System.currentTimeMillis()
          val result =
            try {
              withTimeoutOrNull(ADAPTER_TIMEOUT_MS) {
                if (parsedIsbn != null) {
                  adapter.searchPaginated(request)
                } else {
                  adapter.searchPaginated(request)
                }
              }
                ?: SourceAdapterResult(
                  SourceQueryStatus(
                    source = adapter.source,
                    state = ProviderOutcomeState.ERROR,
                    message = "Timeout",
                    durationMs = System.currentTimeMillis() - adapterStart,
                  )
                )
            } catch (ce: CancellationException) {
              throw ce
            } catch (t: Throwable) {
              SourceAdapterResult(
                SourceQueryStatus(
                  source = adapter.source,
                  state = ProviderOutcomeState.ERROR,
                  message = t.message,
                  durationMs = System.currentTimeMillis() - adapterStart,
                )
              )
            }

          val snapshot =
            mutex.withLock {
              completedAdapters++
              statusMap[adapter.source] = result.status
              accumulatedCandidates.addAll(result.candidates)
              val isFinal = completedAdapters >= adapters.size

              val grouped =
                WorkGroupingEngine.groupSeparated(
                  rawCandidates = accumulatedCandidates,
                  preferredLanguage = preferredLanguage,
                  parsedIntent = intent,
                )
              val primaryAnnotated = annotateGroups(grouped.primaryGroups, allLocalBooks)
              val secondaryAnnotated = annotateGroups(grouped.secondaryGroups, allLocalBooks)

              val nowMs = System.currentTimeMillis()
              if (firstResultLatency == null && (primaryAnnotated.isNotEmpty() || secondaryAnnotated.isNotEmpty())) {
                firstResultLatency = (nowMs - startClockMs).coerceAtLeast(1L)
              }

              val statusesList = statusMap.values.toList()
              val hasMore = statusesList.any { it.hasMore }
              val totalHint = statusesList.mapNotNull { it.totalAvailable }.maxOrNull()

              val built =
                HybridSearchResponse(
                  query = trimmed,
                  isIsbnQuery = isIsbn,
                  isFromCache = false,
                  isOffline = false,
                  localMatches = localMatches,
                  workGroups = primaryAnnotated,
                  secondaryWorkGroups = secondaryAnnotated,
                  sourceStatuses = statusesList,
                  expandedSecondarySources = expandSecondary,
                  hasMoreResults = hasMore && !isIsbn,
                  isPartialResult = !isFinal,
                  totalFoundHint = totalHint,
                  firstResultLatencyMs = firstResultLatency,
                  totalLatencyMs = if (isFinal) (nowMs - startClockMs).coerceAtLeast(1L) else null,
                )
              latestResponse = built
              built
            }
          send(snapshot)
        }
      }
    }

    val finalResp = latestResponse
    if (finalResp != null) {
      synchronized(paginationLock) {
        paginationSessions[cacheKey] =
          PaginationSession(
            intent = intent,
            preferredLanguage = preferredLanguage,
            expandSecondary = expandSecondary,
            currentPage = 1,
            accumulatedCandidates = accumulatedCandidates.toList(),
            statuses = statusMap.toMap(),
          )
      }

      if (finalResp.workGroups.isNotEmpty() || finalResp.secondaryWorkGroups.isNotEmpty()) {
        synchronized(memoryCacheLock) { memoryCache[cacheKey] = finalResp }
        val ttlMillis = if (isIsbn) ISBN_CACHE_TTL_MS else QUERY_CACHE_TTL_MS
        val sourcesList =
          finalResp.sourceStatuses
            .filter { it.state == ProviderOutcomeState.SUCCESS }
            .joinToString(",") { it.source.name }
        val payloadObj =
          CachedSearchPayload(
            primaryGroups = finalResp.workGroups,
            secondaryGroups = finalResp.secondaryWorkGroups,
            totalFoundHint = finalResp.totalFoundHint,
            hasMoreResults = finalResp.hasMoreResults,
          )
        searchCacheDao.upsertEntry(
          SearchCacheEntity(
            cacheKey = cacheKey,
            queryNormalized = intent.normalizedQuery,
            queryType = if (isIsbn) "ISBN" else "QUERY",
            payloadJson = json.encodeToString(payloadObj),
            sourcesUsed = sourcesList,
            fetchedAtEpochMillis = startClockMs,
            expiresAtEpochMillis = startClockMs + ttlMillis,
            lastAccessedEpochMillis = startClockMs,
          )
        )
        searchCacheDao.trimBeyond(CACHE_MAX_ENTRIES)
      }
    }
  }

  suspend fun loadNextPage(
    currentResponse: HybridSearchResponse,
    preferredLanguage: String? = null,
    expandSecondary: Boolean = currentResponse.expandedSecondarySources,
  ): HybridSearchResponse {
    if (!connectivityChecker.isOnline()) return currentResponse
    val intent = QueryIntentParser.parse(currentResponse.query)
    if (intent.intentType == QueryIntentType.ISBN_EXACT) return currentResponse

    val cacheKey = buildCacheKey(intent, preferredLanguage, expandSecondary)
    val existingSession =
      synchronized(paginationLock) {
        paginationSessions[cacheKey]
          ?: PaginationSession(
            intent = intent,
            preferredLanguage = preferredLanguage,
            expandSecondary = expandSecondary,
            currentPage = 1,
            accumulatedCandidates = (currentResponse.workGroups + currentResponse.secondaryWorkGroups).flatMap { it.editions },
            statuses = currentResponse.sourceStatuses.associateBy { it.source },
          )
      }

    val nextPage = existingSession.currentPage + 1
    val allAdapters = selectAdapters(parsedIsbn = null, isIsbn = false, expandSecondary = expandSecondary)
    val paginatedAdapters =
      allAdapters.filter { adapter ->
        val prev = existingSession.statuses[adapter.source]
        prev == null || prev.hasMore
      }.ifEmpty {
        listOf(openLibraryAdapter, googleBooksAdapter)
      }

    val req =
      AdapterSearchRequest(
        rawQuery = intent.rawQuery,
        parsedIntent = intent,
        page = nextPage,
        pageSize = DEFAULT_PAGE_SIZE,
        expandSecondary = expandSecondary,
      )

    val nextResults =
      supervisorScope {
        paginatedAdapters.map { adapter ->
          async {
            runCatching {
                withTimeoutOrNull(ADAPTER_TIMEOUT_MS) { adapter.searchPaginated(req) }
                  ?: SourceAdapterResult(SourceQueryStatus(adapter.source, ProviderOutcomeState.ERROR, message = "Timeout"))
              }
              .getOrElse { err ->
                if (err is CancellationException) throw err
                SourceAdapterResult(SourceQueryStatus(adapter.source, ProviderOutcomeState.ERROR, message = err.message))
              }
          }
        }.awaitAll()
      }

    val combinedCandidates =
      (existingSession.accumulatedCandidates + nextResults.flatMap { it.candidates })
        .distinctBy { it.candidateId }

    val updatedStatusMap = existingSession.statuses.toMutableMap()
    nextResults.forEach { res ->
      val prev = updatedStatusMap[res.status.source]
      val cumulativeCount =
        if (prev != null && prev.state == ProviderOutcomeState.SUCCESS && res.status.state == ProviderOutcomeState.SUCCESS) {
          prev.count + res.status.count
        } else {
          res.status.count
        }
      updatedStatusMap[res.status.source] =
        res.status.copy(
          count = cumulativeCount,
          totalAvailable = res.status.totalAvailable ?: prev?.totalAvailable,
        )
    }

    val grouped =
      WorkGroupingEngine.groupSeparated(
        rawCandidates = combinedCandidates,
        preferredLanguage = preferredLanguage,
        parsedIntent = intent,
      )
    val allLocalBooks = libraryRepository.observeAllBooks().first()
    val primaryAnnotated = annotateGroups(grouped.primaryGroups, allLocalBooks)
    val secondaryAnnotated = annotateGroups(grouped.secondaryGroups, allLocalBooks)
    val statusList = updatedStatusMap.values.toList()
    val hasMore = statusList.any { it.hasMore } && nextResults.any { it.candidates.isNotEmpty() }

    synchronized(paginationLock) {
      paginationSessions[cacheKey] =
        existingSession.copy(
          currentPage = nextPage,
          accumulatedCandidates = combinedCandidates,
          statuses = updatedStatusMap,
        )
    }

    val nextResponse =
      currentResponse.copy(
        isFromCache = false,
        workGroups = primaryAnnotated,
        secondaryWorkGroups = secondaryAnnotated,
        sourceStatuses = statusList,
        hasMoreResults = hasMore,
        totalFoundHint = statusList.mapNotNull { it.totalAvailable }.maxOrNull() ?: currentResponse.totalFoundHint,
      )
    synchronized(memoryCacheLock) { memoryCache[cacheKey] = nextResponse }
    return nextResponse
  }

  suspend fun expandWorkEditions(
    workGroup: SearchWorkGroup,
    preferredLanguage: String? = null,
  ): SearchWorkGroup {
    val workKey = workGroup.externalWorkKey ?: return workGroup
    if (!connectivityChecker.isOnline()) return workGroup

    val extra =
      runCatching { openLibraryAdapter.fetchEditionsForWork(workKey) }.getOrNull()
        ?: return workGroup
    if (extra.candidates.isEmpty()) return workGroup

    val mergedGroups =
      WorkGroupingEngine.groupCandidates(
        rawCandidates = workGroup.editions + extra.candidates,
        preferredLanguage = preferredLanguage,
      )
    val combined =
      mergedGroups.firstOrNull() ?: return workGroup
    val allBooks = libraryRepository.observeAllBooks().first()
    return annotateGroups(
      listOf(
        combined.copy(
          groupId = workGroup.groupId,
          canonicalTitle = workGroup.canonicalTitle,
          externalWorkKey = workKey,
        )
      ),
      allBooks,
    ).first()
  }

  suspend fun fetchCandidatesForBookUpdate(
    title: String,
    author: String,
    isbns: List<String>,
    preferredLanguage: String?,
  ): List<SearchEditionCandidate> = coroutineScope {
    if (!connectivityChecker.isOnline()) return@coroutineScope emptyList()

    val primaryIsbn = isbns.firstNotNullOfOrNull { BibliographicMath.parseIsbn(it) }
    val results =
      supervisorScope {
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<SourceAdapterResult>>()
        if (primaryIsbn != null) {
          jobs.add(async { openLibraryAdapter.searchByIsbn(primaryIsbn.isbn13, primaryIsbn.isbn10, primaryIsbn.cleaned) })
          jobs.add(async { googleBooksAdapter.searchByIsbn(primaryIsbn.isbn13, primaryIsbn.isbn10, primaryIsbn.cleaned) })
          jobs.add(async { crossrefAdapter.searchByIsbn(primaryIsbn.isbn13, primaryIsbn.isbn10, primaryIsbn.cleaned) })
          jobs.add(async { wikidataAdapter.searchByIsbn(primaryIsbn.isbn13, primaryIsbn.isbn10, primaryIsbn.cleaned) })
          if (BibliographicMath.isBrazilianIsbn(primaryIsbn.isbn13 ?: primaryIsbn.isbn10)) {
            jobs.add(async { brasilApiAdapter.searchByIsbn(primaryIsbn.isbn13, primaryIsbn.isbn10, primaryIsbn.cleaned) })
          }
        } else {
          val q = listOf(title, author).filter { it.isNotBlank() }.joinToString(" ")
          jobs.add(async { openLibraryAdapter.searchByQuery(q) })
          jobs.add(async { googleBooksAdapter.searchByQuery(q) })
        }
        jobs.awaitAll()
      }
    WorkGroupingEngine.groupCandidates(results.flatMap { it.candidates }, preferredLanguage)
      .flatMap { it.editions }
  }

  private fun selectAdapters(
    parsedIsbn: com.example.core.model.ParsedIsbn?,
    isIsbn: Boolean,
    expandSecondary: Boolean,
  ): List<BibliographicSourceAdapter> =
    buildList {
      add(openLibraryAdapter)
      add(googleBooksAdapter)
      if (isIsbn || expandSecondary) {
        add(crossrefAdapter)
        add(wikidataAdapter)
      }
      val isBrIsbn = BibliographicMath.isBrazilianIsbn(parsedIsbn?.isbn13 ?: parsedIsbn?.isbn10)
      if (isBrIsbn || expandSecondary) {
        add(brasilApiAdapter)
      }
    }

  private fun buildCacheKey(
    intent: ParsedSearchIntent,
    preferredLanguage: String?,
    expandSecondary: Boolean,
  ): String {
    val langPart = BibliographicMath.normalizeLanguageCode(preferredLanguage).orEmpty()
    val isbn = intent.parsedIsbn
    return if (isbn != null) {
      "v2:isbn:${isbn.isbn13 ?: isbn.isbn10 ?: isbn.cleaned}:lang=$langPart"
    } else {
      "v2:q:${intent.normalizedQuery}:exl=${intent.explicitLanguageRequest.orEmpty()}:pref=$langPart:exp=$expandSecondary"
    }
  }

  private fun decodeCachedPayload(payloadJson: String): CachedSearchPayload? {
    runCatching { json.decodeFromString<CachedSearchPayload>(payloadJson) }
      .getOrNull()
      ?.let { return it }
    val legacyList =
      runCatching { json.decodeFromString<List<SearchWorkGroup>>(payloadJson) }.getOrNull()
        ?: return null
    return CachedSearchPayload(primaryGroups = legacyList)
  }

  private suspend fun findLocalMatches(
    rawQuery: String,
    isbn13: String?,
    isbn10: String?,
    cleanedIsbn: String?,
  ): List<CatalogBook> {
    if (rawQuery.isBlank()) return emptyList()
    if (isbn13 != null || isbn10 != null) {
      val editions = editionDao.findEditionsByIsbn(isbn13, isbn10, cleanedIsbn)
      val workIds = editions.map { it.workId }.distinct()
      if (workIds.isNotEmpty()) {
        return libraryRepository.observeAllBooks().first().filter { it.work.id in workIds }
      }
    }
    return libraryRepository.searchLocalLibrary(rawQuery)
  }

  private suspend fun annotateResponseWithLocalMatches(response: HybridSearchResponse): HybridSearchResponse {
    if (response.workGroups.isEmpty() && response.secondaryWorkGroups.isEmpty()) return response
    val allBooks = libraryRepository.observeAllBooks().first()
    return response.copy(
      workGroups = annotateGroups(response.workGroups, allBooks),
      secondaryWorkGroups = annotateGroups(response.secondaryWorkGroups, allBooks),
    )
  }

  private suspend fun annotateGroups(
    groups: List<SearchWorkGroup>,
    allBooks: List<CatalogBook>,
  ): List<SearchWorkGroup> {
    if (groups.isEmpty()) return groups
    val allIsbns =
      groups
        .flatMap { it.editions }
        .flatMap { listOfNotNull(it.isbn13, it.isbn10) }
        .distinct()

    val isbnToEdition = mutableMapOf<String, Pair<Long, Long>>()
    if (allIsbns.isNotEmpty()) {
      for (chunk in allIsbns.chunked(400)) {
        val matched = editionDao.findEditionsByIsbnIn(chunk)
        for (ed in matched) {
          ed.isbn13?.let { isbnToEdition.putIfAbsent(it, ed.id to ed.workId) }
          ed.isbn10?.let { isbnToEdition.putIfAbsent(it, ed.id to ed.workId) }
        }
      }
    }

    return groups.map { group ->
      val updatedEditions =
        group.editions.map { ed ->
          val match =
            ed.isbn13?.let { isbnToEdition[it] } ?: ed.isbn10?.let { isbnToEdition[it] }
          if (match != null) {
            ed.copy(localExistingEditionId = match.first, localExistingWorkId = match.second)
          } else {
            ed
          }
        }
      val matchedBook =
        updatedEditions.firstNotNullOfOrNull { ed ->
          ed.localExistingWorkId?.let { wid -> allBooks.firstOrNull { it.work.id == wid } }
        }
          ?: group.externalWorkKey?.let { key -> allBooks.firstOrNull { it.work.externalWorkKey == key } }
          ?: allBooks.firstOrNull { localBook ->
            val titleSim =
              BibliographicMath.tokenSimilarity(
                BibliographicMath.normalizeTitleCore(localBook.title),
                BibliographicMath.normalizeTitleCore(group.canonicalTitle),
              )
            val authorSim =
              BibliographicMath.tokenSimilarity(
                BibliographicMath.normalizeAuthorSurname(localBook.author),
                BibliographicMath.normalizeAuthorSurname(group.displayAuthors),
              )
            titleSim >= 0.88f && authorSim >= 0.78f
          }

      group.copy(
        editions = updatedEditions,
        localMatchedWorkId = matchedBook?.work?.id,
        localMatchedTitle = matchedBook?.title,
      )
    }
  }

  private companion object {
    const val DEFAULT_PAGE_SIZE = 30
    const val ADAPTER_TIMEOUT_MS = 7_500L
    const val QUERY_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    const val ISBN_CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1000L
    const val CACHE_MAX_ENTRIES = 120
    const val MEMORY_CACHE_MAX_ENTRIES = 24
  }
}
