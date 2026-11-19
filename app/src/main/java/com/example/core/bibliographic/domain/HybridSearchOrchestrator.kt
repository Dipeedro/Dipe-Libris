package com.example.core.bibliographic.domain

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

  suspend fun search(
    rawQuery: String,
    preferredLanguage: String? = null,
    expandSecondary: Boolean = false,
    forceRefresh: Boolean = false,
  ): HybridSearchResponse {
    val trimmed = rawQuery.trim()
    val parsedIsbn = BibliographicMath.parseIsbn(trimmed)
    val isIsbn = parsedIsbn != null
    val normalizedKey =
      if (parsedIsbn != null) {
        "isbn:${parsedIsbn.isbn13 ?: parsedIsbn.isbn10 ?: parsedIsbn.cleaned}"
      } else {
        "q:${BibliographicMath.normalizeText(trimmed)}:exp=$expandSecondary"
      }

    // 1. Local library matches (always available offline)
    val localMatches = findLocalMatches(trimmed, parsedIsbn?.isbn13, parsedIsbn?.isbn10, parsedIsbn?.cleaned)

    if (trimmed.isBlank()) {
      return HybridSearchResponse(
        query = trimmed,
        isIsbnQuery = false,
        isFromCache = false,
        isOffline = !connectivityChecker.isOnline(),
        localMatches = emptyList(),
        workGroups = emptyList(),
        sourceStatuses = emptyList(),
        expandedSecondarySources = expandSecondary,
      )
    }

    val now = System.currentTimeMillis()
    val online = connectivityChecker.isOnline()

    // 2. Check cache
    if (!forceRefresh) {
      val cached =
        if (online) searchCacheDao.getValidEntry(normalizedKey, now)
        else searchCacheDao.getAnyEntry(normalizedKey)
      if (cached != null) {
        val groups =
          runCatching { json.decodeFromString<List<SearchWorkGroup>>(cached.payloadJson) }
            .getOrNull()
        if (groups != null) {
          searchCacheDao.touchEntry(normalizedKey, now)
          val annotated = annotateWithLocalMatches(groups)
          val cachedStatuses =
            cached.sourcesUsed
              .split(",")
              .mapNotNull { name ->
                runCatching { MetadataSource.valueOf(name.trim()) }.getOrNull()?.let { src ->
                  SourceQueryStatus(src, ProviderOutcomeState.CACHED, count = groups.sumOf { it.editions.size })
                }
              }
          return HybridSearchResponse(
            query = trimmed,
            isIsbnQuery = isIsbn,
            isFromCache = true,
            isOffline = !online,
            localMatches = localMatches,
            workGroups = annotated,
            sourceStatuses = cachedStatuses,
            expandedSecondarySources = expandSecondary,
          )
        }
      }
    }

    // 3. Offline and no cache hit
    if (!online) {
      return HybridSearchResponse(
        query = trimmed,
        isIsbnQuery = isIsbn,
        isFromCache = false,
        isOffline = true,
        localMatches = localMatches,
        workGroups = emptyList(),
        sourceStatuses =
          listOf(
            SourceQueryStatus(MetadataSource.OPEN_LIBRARY, ProviderOutcomeState.OFFLINE),
            SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.OFFLINE),
          ),
        expandedSecondarySources = expandSecondary,
      )
    }

    // 4. Online parallel source orchestration
    val adapters =
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

    val results: List<SourceAdapterResult> =
      supervisorScope {
        adapters
          .map { adapter ->
            async {
              runCatching {
                  if (parsedIsbn != null) {
                    adapter.searchByIsbn(parsedIsbn.isbn13, parsedIsbn.isbn10, parsedIsbn.cleaned)
                  } else {
                    adapter.searchByQuery(trimmed)
                  }
                }
                .getOrElse { err ->
                  SourceAdapterResult(
                    status = SourceQueryStatus(adapter.source, ProviderOutcomeState.ERROR, message = err.message)
                  )
                }
            }
          }
          .awaitAll()
      }

    val rawCandidates = results.flatMap { it.candidates }
    val grouped = WorkGroupingEngine.groupCandidates(rawCandidates, preferredLanguage)
    val annotated = annotateWithLocalMatches(grouped)

    // 5. Persist cache if we got groups
    if (grouped.isNotEmpty()) {
      val ttlMillis = if (isIsbn) ISBN_CACHE_TTL_MS else QUERY_CACHE_TTL_MS
      val sourcesList =
        results
          .filter { it.status.state == ProviderOutcomeState.SUCCESS }
          .joinToString(",") { it.status.source.name }
      searchCacheDao.upsertEntry(
        SearchCacheEntity(
          cacheKey = normalizedKey,
          queryNormalized = BibliographicMath.normalizeText(trimmed),
          queryType = if (isIsbn) "ISBN" else "QUERY",
          payloadJson = json.encodeToString(grouped),
          sourcesUsed = sourcesList,
          fetchedAtEpochMillis = now,
          expiresAtEpochMillis = now + ttlMillis,
          lastAccessedEpochMillis = now,
        )
      )
      searchCacheDao.trimBeyond(CACHE_MAX_ENTRIES)
    }

    return HybridSearchResponse(
      query = trimmed,
      isIsbnQuery = isIsbn,
      isFromCache = false,
      isOffline = false,
      localMatches = localMatches,
      workGroups = annotated,
      sourceStatuses = results.map { it.status },
      expandedSecondarySources = expandSecondary,
    )
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
    return annotateWithLocalMatches(
      listOf(
        combined.copy(
          groupId = workGroup.groupId,
          canonicalTitle = workGroup.canonicalTitle,
          externalWorkKey = workKey,
        )
      )
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

  private suspend fun annotateWithLocalMatches(groups: List<SearchWorkGroup>): List<SearchWorkGroup> {
    if (groups.isEmpty()) return groups
    val allBooks = libraryRepository.observeAllBooks().first()
    val allIsbns =
      groups
        .flatMap { it.editions }
        .mapNotNull { it.isbn13 ?: it.isbn10 }
        .distinct()

    val isbnToEdition = mutableMapOf<String, Pair<Long, Long>>()
    for (isbn in allIsbns) {
      val parsed = BibliographicMath.parseIsbn(isbn)
      val found = editionDao.findEditionsByIsbn(parsed?.isbn13, parsed?.isbn10, parsed?.cleaned).firstOrNull()
      if (found != null) {
        isbnToEdition[isbn] = found.id to found.workId
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
    const val QUERY_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    const val ISBN_CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1000L
    const val CACHE_MAX_ENTRIES = 120
  }
}
