package com.example.core.bibliographic.domain

import com.example.core.bibliographic.network.AdapterSearchRequest
import com.example.core.bibliographic.network.BibliographicSourceAdapter
import com.example.core.bibliographic.network.ConnectivityChecker
import com.example.core.bibliographic.network.SourceAdapterResult
import com.example.core.bibliographic.network.WikidataAdapter
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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class SourceQueryStatusSnapshot(
  val source: MetadataSource,
  val state: ProviderOutcomeState,
  val count: Int = 0,
  val totalAvailable: Int? = null,
  val hasMore: Boolean = false,
)

@Serializable
private data class CachedSearchPayload(
  val candidates: List<SearchEditionCandidate> = emptyList(),
  val statuses: List<SourceQueryStatusSnapshot> = emptyList(),
  val primaryGroups: List<SearchWorkGroup> = emptyList(),
  val secondaryGroups: List<SearchWorkGroup> = emptyList(),
  val derivativeGroups: List<SearchWorkGroup> = emptyList(),
  val totalFoundHint: Int? = null,
  val hasMoreResults: Boolean = false,
  val nextPage: Int = 2,
)

private data class PaginationSession(
  val intent: ParsedSearchIntent,
  val preferredLanguage: String?,
  val expandSecondary: Boolean,
  val currentPage: Int,
  val accumulatedCandidates: List<SearchEditionCandidate>,
  val statuses: Map<MetadataSource, SourceQueryStatus>,
)

data class ExpandedWorkResult(
  val group: SearchWorkGroup,
  val hasMoreToLoad: Boolean,
)

private data class WorkExpansionCursor(
  val nextOlOffset: Int = 0,
  val olHasMore: Boolean = true,
  val gbFetched: Boolean = false,
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
  private val appleBooksAdapter: BibliographicSourceAdapter? = null,
) {

  private val memoryCacheLock = Any()
  private val memoryCache =
    object : LinkedHashMap<String, HybridSearchResponse>(MEMORY_CACHE_MAX_ENTRIES, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HybridSearchResponse>?): Boolean =
        size > MEMORY_CACHE_MAX_ENTRIES
    }

  private val paginationLock = Any()
  private val paginationSessions = mutableMapOf<String, PaginationSession>()

  private val expansionLock = Any()
  private val expansionCursors = mutableMapOf<String, WorkExpansionCursor>()

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
          derivativeWorkGroups = emptyList(),
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

    // 2. Check persistent Room cache (v3 stores candidates & source pagination state)
    if (!forceRefresh) {
      val cached =
        if (online) searchCacheDao.getValidEntry(cacheKey, startClockMs)
        else searchCacheDao.getAnyEntry(cacheKey)
      if (cached != null) {
        val decodedPayload = decodeCachedPayload(cached.payloadJson)
        if (decodedPayload != null) {
          searchCacheDao.touchEntry(cacheKey, startClockMs)
          val allLocalBooks = libraryRepository.observeAllBooks().first()
          val groupedOutcome =
            if (decodedPayload.candidates.isNotEmpty()) {
              WorkGroupingEngine.groupSeparated(
                rawCandidates = decodedPayload.candidates,
                preferredLanguage = preferredLanguage,
                parsedIntent = intent,
              )
            } else {
              GroupedSearchOutcome(
                primaryGroups = decodedPayload.primaryGroups,
                secondaryGroups = decodedPayload.secondaryGroups,
                derivativeGroups = decodedPayload.derivativeGroups,
              )
            }
          val primaryAnnotated = annotateGroups(groupedOutcome.primaryGroups, allLocalBooks)
          val secondaryAnnotated = annotateGroups(groupedOutcome.secondaryGroups, allLocalBooks)
          val derivativeAnnotated = annotateGroups(groupedOutcome.derivativeGroups, allLocalBooks)
          val cachedCount = (primaryAnnotated + secondaryAnnotated).sumOf { it.editions.size }
          val cachedStatuses =
            if (decodedPayload.statuses.isNotEmpty()) {
              decodedPayload.statuses.map { snap ->
                SourceQueryStatus(
                  source = snap.source,
                  state = if (snap.state == ProviderOutcomeState.SUCCESS) ProviderOutcomeState.CACHED else snap.state,
                  count = snap.count,
                  totalAvailable = snap.totalAvailable,
                  hasMore = snap.hasMore,
                )
              }
            } else {
              cached.sourcesUsed
                .split(",")
                .mapNotNull { name ->
                  runCatching { MetadataSource.valueOf(name.trim()) }.getOrNull()?.let { src ->
                    SourceQueryStatus(src, ProviderOutcomeState.CACHED, count = cachedCount)
                  }
                }
            }

          if (decodedPayload.candidates.isNotEmpty()) {
            synchronized(paginationLock) {
              paginationSessions[cacheKey] =
                PaginationSession(
                  intent = intent,
                  preferredLanguage = preferredLanguage,
                  expandSecondary = expandSecondary,
                  currentPage = (decodedPayload.nextPage - 1).coerceAtLeast(1),
                  accumulatedCandidates = decodedPayload.candidates,
                  statuses = cachedStatuses.associateBy { it.source },
                )
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
              derivativeWorkGroups = derivativeAnnotated,
              sourceStatuses = cachedStatuses,
              expandedSecondarySources = expandSecondary,
              hasMoreResults = decodedPayload.hasMoreResults && !isIsbn,
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
          derivativeWorkGroups = emptyList(),
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

    // 4. Online parallel source batch with bounded deadline before final sort (zero list-jumping)
    val adapters = selectAdapters(intent = intent, parsedIsbn = parsedIsbn, isIsbn = isIsbn)
    val runWikidataBridge =
      !isIsbn &&
        QueryPlanner.shouldRunWikidataBridge(intent, expandSecondary) &&
        wikidataAdapter is WikidataAdapter

    val initialStatuses = linkedMapOf<MetadataSource, SourceQueryStatus>()
    adapters.forEach { adapter ->
      initialStatuses[adapter.source] = SourceQueryStatus(adapter.source, ProviderOutcomeState.LOADING)
    }

    // Emission 1: Immediate loading state + local matches while waiting for bounded batch
    send(
      HybridSearchResponse(
        query = trimmed,
        isIsbnQuery = isIsbn,
        isFromCache = false,
        isOffline = false,
        localMatches = localMatches,
        workGroups = emptyList(),
        secondaryWorkGroups = emptyList(),
        derivativeWorkGroups = emptyList(),
        sourceStatuses = initialStatuses.values.toList(),
        expandedSecondarySources = expandSecondary,
        isPartialResult = true,
        firstResultLatencyMs = if (localMatches.isNotEmpty()) System.currentTimeMillis() - startClockMs else null,
      )
    )

    val allLocalBooks = libraryRepository.observeAllBooks().first()
    val request =
      AdapterSearchRequest(
        rawQuery = trimmed,
        parsedIntent = intent,
        page = 1,
        pageSize = DEFAULT_PAGE_SIZE,
        expandSecondary = expandSecondary,
      )

    val completedResults = ConcurrentHashMap<MetadataSource, SourceAdapterResult>()
    var bridgeResult: SourceAdapterResult? = null

    withTimeoutOrNull(PRIMARY_BATCH_DEADLINE_MS) {
      supervisorScope {
        val jobs =
          adapters.map { adapter ->
            launch {
              val adapterStart = System.currentTimeMillis()
              val timeoutMs = timeoutForAdapter(adapter.source, isIsbn)
              val res =
                try {
                  withTimeoutOrNull(timeoutMs) {
                    adapter.searchPaginated(request)
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
              completedResults[adapter.source] = res
            }
          }

        val bridgeJob =
          if (runWikidataBridge) {
            launch {
              bridgeResult =
                runCatching {
                    withTimeoutOrNull(WIKIDATA_BRIDGE_DEADLINE_MS) {
                      (wikidataAdapter as WikidataAdapter).fetchBilingualBridge(intent.subjectQueryPart ?: intent.cleanQuery)
                    }
                  }
                  .getOrNull()
            }
          } else {
            null
          }

        jobs.joinAll()
        bridgeJob?.join()
      }
    }

    val statusMap = linkedMapOf<MetadataSource, SourceQueryStatus>()
    val accumulatedCandidates = mutableListOf<SearchEditionCandidate>()
    val deadlineElapsed = (System.currentTimeMillis() - startClockMs).coerceAtLeast(1L)

    adapters.forEach { adapter ->
      val res = completedResults[adapter.source]
      if (res != null) {
        statusMap[adapter.source] = res.status
        accumulatedCandidates.addAll(res.candidates)
      } else {
        statusMap[adapter.source] =
          SourceQueryStatus(
            source = adapter.source,
            state = ProviderOutcomeState.ERROR,
            message = "Batch deadline exceeded",
            durationMs = deadlineElapsed,
          )
      }
    }

    bridgeResult?.let { bridge ->
      if (bridge.candidates.isNotEmpty()) {
        accumulatedCandidates.addAll(bridge.candidates)
      }
    }

    val grouped =
      WorkGroupingEngine.groupSeparated(
        rawCandidates = accumulatedCandidates,
        preferredLanguage = preferredLanguage,
        parsedIntent = intent,
      )
    val primaryAnnotated = annotateGroups(grouped.primaryGroups, allLocalBooks)
    val secondaryAnnotated = annotateGroups(grouped.secondaryGroups, allLocalBooks)
    val derivativeAnnotated = annotateGroups(grouped.derivativeGroups, allLocalBooks)

    val statusesList = statusMap.values.toList()
    val hasMore = statusesList.any { it.hasMore }
    val totalHint = statusesList.mapNotNull { it.totalAvailable }.maxOrNull()
    val nowMs = System.currentTimeMillis()
    val latency = (nowMs - startClockMs).coerceAtLeast(1L)

    val finalResp =
      HybridSearchResponse(
        query = trimmed,
        isIsbnQuery = isIsbn,
        isFromCache = false,
        isOffline = false,
        localMatches = localMatches,
        workGroups = primaryAnnotated,
        secondaryWorkGroups = secondaryAnnotated,
        derivativeWorkGroups = derivativeAnnotated,
        sourceStatuses = statusesList,
        expandedSecondarySources = expandSecondary,
        hasMoreResults = hasMore && !isIsbn,
        isPartialResult = false,
        totalFoundHint = totalHint,
        firstResultLatencyMs = latency,
        totalLatencyMs = latency,
      )

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

    val hasAnyLiteraryResults =
      finalResp.workGroups.isNotEmpty() || finalResp.secondaryWorkGroups.isNotEmpty()
    val allAdaptersErrored =
      statusesList.isNotEmpty() &&
        statusesList.none { it.state == ProviderOutcomeState.SUCCESS || it.state == ProviderOutcomeState.CACHED }
    val essentialSourceDegraded =
      statusesList.any { st ->
        val isCorePrimary =
          st.source == MetadataSource.OPEN_LIBRARY ||
            st.source == MetadataSource.GOOGLE_BOOKS ||
            (appleBooksAdapter != null && st.source == MetadataSource.APPLE_BOOKS)
        isCorePrimary &&
          (st.state == ProviderOutcomeState.ERROR || st.state == ProviderOutcomeState.RATE_LIMITED)
      }
    if (hasAnyLiteraryResults && !allAdaptersErrored && !essentialSourceDegraded) {
      synchronized(memoryCacheLock) { memoryCache[cacheKey] = finalResp }
      val ttlMillis = if (isIsbn) ISBN_CACHE_TTL_MS else QUERY_CACHE_TTL_MS
      val sourcesList =
        finalResp.sourceStatuses
          .filter { it.state == ProviderOutcomeState.SUCCESS }
          .joinToString(",") { it.source.name }
      val payloadObj =
        CachedSearchPayload(
          candidates = accumulatedCandidates.toList(),
          statuses =
            statusesList.map { st ->
              SourceQueryStatusSnapshot(
                source = st.source,
                state = st.state,
                count = st.count,
                totalAvailable = st.totalAvailable,
                hasMore = st.hasMore,
              )
            },
          primaryGroups = finalResp.workGroups,
          secondaryGroups = finalResp.secondaryWorkGroups,
          derivativeGroups = finalResp.derivativeWorkGroups,
          totalFoundHint = finalResp.totalFoundHint,
          hasMoreResults = finalResp.hasMoreResults,
          nextPage = 2,
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

    send(finalResp)
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
            accumulatedCandidates =
              (currentResponse.workGroups + currentResponse.secondaryWorkGroups + currentResponse.derivativeWorkGroups)
                .flatMap { it.editions },
            statuses = currentResponse.sourceStatuses.associateBy { it.source },
          )
      }

    val nextPage = existingSession.currentPage + 1
    val allAdapters = selectAdapters(intent = intent, parsedIsbn = null, isIsbn = false)
    val paginatedAdapters =
      allAdapters.filter { adapter ->
        val prev = existingSession.statuses[adapter.source]
        prev == null || prev.hasMore
      }

    if (paginatedAdapters.isEmpty()) {
      return currentResponse.copy(hasMoreResults = false)
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
                withTimeoutOrNull(PRIMARY_BATCH_DEADLINE_MS) { adapter.searchPaginated(req) }
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
    val derivativeAnnotated = annotateGroups(grouped.derivativeGroups, allLocalBooks)
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
        derivativeWorkGroups = derivativeAnnotated,
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
    originalQuery: String? = null,
  ): SearchWorkGroup =
    expandWorkEditionsDetailed(
      workGroup = workGroup,
      preferredLanguage = preferredLanguage,
      originalQuery = originalQuery,
    ).group

  suspend fun expandWorkEditionsDetailed(
    workGroup: SearchWorkGroup,
    preferredLanguage: String? = null,
    originalQuery: String? = null,
  ): ExpandedWorkResult {
    if (!connectivityChecker.isOnline()) {
      return ExpandedWorkResult(workGroup, hasMoreToLoad = false)
    }

    val workKey = workGroup.externalWorkKey
    val cursorKey = "${workGroup.groupId}|${workKey.orEmpty()}"
    val currentCursor =
      synchronized(expansionLock) {
        expansionCursors[cursorKey] ?: WorkExpansionCursor(
          nextOlOffset = 0,
          olHasMore = workKey?.startsWith("/works/") == true,
          gbFetched = false,
        )
      }

    val intentForExpansion = originalQuery?.takeIf { it.isNotBlank() }?.let { QueryIntentParser.parse(it) }
    val primaryAuthor = workGroup.primaryAuthors.firstOrNull()?.name?.takeIf { it.isNotBlank() }
    val gbTargetQuery =
      listOfNotNull(workGroup.canonicalTitle.takeIf { it.isNotBlank() }, primaryAuthor)
        .joinToString(" - ")

    val collectedExtras = mutableListOf<SearchEditionCandidate>()
    var reportedTotal: Int? = workGroup.totalAvailableEditionsHint
    var nextOlOffset = currentCursor.nextOlOffset
    var olHasMore = currentCursor.olHasMore && workKey?.startsWith("/works/") == true
    var gbFetched = currentCursor.gbFetched

    supervisorScope {
      val olJob =
        if (olHasMore && workKey != null) {
          async {
            runCatching {
              withTimeoutOrNull(WORK_EXPANSION_TIMEOUT_MS) {
                val hintCount = workGroup.totalAvailableEditionsHint ?: 0
                if (nextOlOffset == 0 && hintCount >= 100) {
                  val (p1, p2) =
                    coroutineScope {
                      val d1 = async { openLibraryAdapter.fetchEditionsForWorkPage(workKey, offset = 0, limit = 100) }
                      val d2 = async { openLibraryAdapter.fetchEditionsForWorkPage(workKey, offset = 100, limit = 100) }
                      d1.await() to d2.await()
                    }
                  val combined = p1.candidates + p2.candidates
                  val maxTotal = listOfNotNull(p1.status.totalAvailable, p2.status.totalAvailable).maxOrNull()
                  val stillMore =
                    if (p2.status.state == ProviderOutcomeState.EMPTY && p2.candidates.isEmpty()) {
                      false
                    } else {
                      p2.status.hasMore
                    }
                  Triple(combined, 200 to stillMore, maxTotal)
                } else {
                  val p1 = openLibraryAdapter.fetchEditionsForWorkPage(workKey, offset = nextOlOffset, limit = 100)
                  val page1PtCount = p1.candidates.count { it.languageTierRank <= com.example.core.model.LanguageTier.PT_UNCERTAIN.rank }
                  if (p1.status.hasMore && page1PtCount < 10) {
                    val p2 = openLibraryAdapter.fetchEditionsForWorkPage(workKey, offset = nextOlOffset + 100, limit = 100)
                    val combined = p1.candidates + p2.candidates
                    val maxTotal = listOfNotNull(p1.status.totalAvailable, p2.status.totalAvailable).maxOrNull()
                    val stillMore =
                      if (p2.status.state == ProviderOutcomeState.EMPTY && p2.candidates.isEmpty()) false
                      else p2.status.hasMore
                    Triple(combined, (nextOlOffset + 200) to stillMore, maxTotal)
                  } else {
                    Triple(p1.candidates, (nextOlOffset + 100) to p1.status.hasMore, p1.status.totalAvailable)
                  }
                }
              }
            }.getOrNull()
          }
        } else {
          null
        }

      val gbJob =
        if (!gbFetched && gbTargetQuery.isNotBlank()) {
          async<List<SearchEditionCandidate>> {
            runCatching {
              withTimeoutOrNull(WORK_EXPANSION_TIMEOUT_MS) {
                val targetIntent = QueryIntentParser.parse(gbTargetQuery)
                val req1 =
                  AdapterSearchRequest(
                    rawQuery = gbTargetQuery,
                    parsedIntent = targetIntent,
                    page = 1,
                    pageSize = 40,
                    expandSecondary = true,
                  )
                val p1 = googleBooksAdapter.searchPaginated(req1)
                val p2Candidates =
                  if (p1.status.hasMore && p1.candidates.size >= 10) {
                    googleBooksAdapter.searchPaginated(req1.copy(page = 2)).candidates
                  } else {
                    emptyList()
                  }
                p1.candidates + p2Candidates
              }.orEmpty()
            }.getOrDefault(emptyList<SearchEditionCandidate>())
          }
        } else {
          null
        }

      olJob?.await()?.let { (olCands, offsetAndMore, maxTotal) ->
        collectedExtras.addAll(olCands)
        nextOlOffset = offsetAndMore.first
        olHasMore = offsetAndMore.second
        if (maxTotal != null) {
          reportedTotal = listOfNotNull(reportedTotal, maxTotal).maxOrNull()
        }
      }
      gbJob?.await()?.let { gbCands ->
        collectedExtras.addAll(gbCands)
        gbFetched = true
      }
    }

    synchronized(expansionLock) {
      expansionCursors[cursorKey] =
        WorkExpansionCursor(
          nextOlOffset = nextOlOffset,
          olHasMore = olHasMore,
          gbFetched = gbFetched,
        )
    }

    val mergedGroup =
      WorkGroupingEngine.mergeExpandedEditionsIntoGroup(
        workGroup = workGroup,
        extraCandidates = collectedExtras,
        preferredLanguage = preferredLanguage,
        parsedIntent = intentForExpansion,
        reportedTotalEditions = reportedTotal,
      )
    val allBooks = libraryRepository.observeAllBooks().first()
    val annotated = annotateGroups(listOf(mergedGroup), allBooks).first()

    synchronized(memoryCacheLock) {
      val keys = memoryCache.keys.toList()
      for (k in keys) {
        val cached = memoryCache[k] ?: continue
        var changed = false
        val newPrimary =
          cached.workGroups.map { g ->
            if (g.groupId == annotated.groupId) {
              changed = true
              annotated
            } else g
          }
        val newSecondary =
          cached.secondaryWorkGroups.map { g ->
            if (g.groupId == annotated.groupId) {
              changed = true
              annotated
            } else g
          }
        val newDeriv =
          cached.derivativeWorkGroups.map { g ->
            if (g.groupId == annotated.groupId) {
              changed = true
              annotated
            } else g
          }
        if (changed) {
          memoryCache[k] =
            cached.copy(
              workGroups = newPrimary,
              secondaryWorkGroups = newSecondary,
              derivativeWorkGroups = newDeriv,
            )
        }
      }
    }

    return ExpandedWorkResult(group = annotated, hasMoreToLoad = olHasMore)
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
    intent: ParsedSearchIntent,
    parsedIsbn: com.example.core.model.ParsedIsbn?,
    isIsbn: Boolean,
  ): List<BibliographicSourceAdapter> =
    buildList {
      add(openLibraryAdapter)
      add(googleBooksAdapter)
      appleBooksAdapter?.let { add(it) }

      val isExplicitDoi = QueryIntentParser.isExplicitDoi(intent.rawQuery)
      if (isIsbn || isExplicitDoi) {
        add(crossrefAdapter)
      }
      if (isIsbn) {
        add(wikidataAdapter)
        val isBrIsbn = BibliographicMath.isBrazilianIsbn(parsedIsbn?.isbn13 ?: parsedIsbn?.isbn10)
        if (isBrIsbn) {
          add(brasilApiAdapter)
        }
      }
    }

  private fun timeoutForAdapter(source: MetadataSource, isIsbn: Boolean): Long =
    when (source) {
      MetadataSource.APPLE_BOOKS -> APPLE_BOOKS_TIMEOUT_MS
      MetadataSource.GOOGLE_BOOKS -> if (isIsbn) PRIMARY_BATCH_DEADLINE_MS else GOOGLE_BOOKS_TIMEOUT_MS
      MetadataSource.OPEN_LIBRARY -> OPEN_LIBRARY_TIMEOUT_MS
      else -> PRIMARY_BATCH_DEADLINE_MS
    }

  private fun buildCacheKey(
    intent: ParsedSearchIntent,
    preferredLanguage: String?,
    expandSecondary: Boolean,
  ): String {
    val langPart = BibliographicMath.normalizeLanguageCode(preferredLanguage).orEmpty()
    val isbn = intent.parsedIsbn
    return if (isbn != null) {
      "v5:isbn:${isbn.isbn13 ?: isbn.cleaned}:lang=$langPart"
    } else {
      "v5:q:${intent.normalizedQuery}:exl=${intent.explicitLanguageRequest.orEmpty()}:pref=$langPart:exp=$expandSecondary"
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
    if (
      response.workGroups.isEmpty() &&
      response.secondaryWorkGroups.isEmpty() &&
      response.derivativeWorkGroups.isEmpty()
    ) {
      return response
    }
    val allBooks = libraryRepository.observeAllBooks().first()
    return response.copy(
      workGroups = annotateGroups(response.workGroups, allBooks),
      secondaryWorkGroups = annotateGroups(response.secondaryWorkGroups, allBooks),
      derivativeWorkGroups = annotateGroups(response.derivativeWorkGroups, allBooks),
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
    const val PRIMARY_BATCH_DEADLINE_MS = 3_200L
    const val OPEN_LIBRARY_TIMEOUT_MS = 3_150L
    const val GOOGLE_BOOKS_TIMEOUT_MS = 2_800L
    const val APPLE_BOOKS_TIMEOUT_MS = 2_200L
    const val WIKIDATA_BRIDGE_DEADLINE_MS = 900L
    const val WORK_EXPANSION_TIMEOUT_MS = 4_500L
    const val QUERY_CACHE_TTL_MS = 3L * 24L * 60L * 60L * 1000L
    const val ISBN_CACHE_TTL_MS = 15L * 24L * 60L * 60L * 1000L
    const val CACHE_MAX_ENTRIES = 120
    const val MEMORY_CACHE_MAX_ENTRIES = 24
  }
}
