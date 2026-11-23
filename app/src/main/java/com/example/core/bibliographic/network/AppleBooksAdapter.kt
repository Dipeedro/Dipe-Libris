package com.example.core.bibliographic.network

import com.example.core.bibliographic.domain.CoverRanker
import com.example.core.bibliographic.domain.QueryIntentType
import com.example.core.bibliographic.domain.QueryPlanner
import com.example.core.model.BibliographicMath
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.LanguageAssessment
import com.example.core.model.LanguageConfidence
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SourceQueryStatus
import com.example.core.model.SynopsisCandidate
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class AppleBooksResponse(
  val resultCount: Int = 0,
  val results: List<AppleBooksItem> = emptyList(),
)

@Serializable
private data class AppleBooksItem(
  val trackId: Long? = null,
  val trackName: String? = null,
  val trackCensoredName: String? = null,
  val artistName: String? = null,
  val artworkUrl100: String? = null,
  val artworkUrl60: String? = null,
  val description: String? = null,
  val releaseDate: String? = null,
  val genres: List<String> = emptyList(),
  val trackViewUrl: String? = null,
  val kind: String? = null,
  val wrapperType: String? = null,
)

/**
 * Adaptador experimental e isolado para o catálogo Apple Books (iTunes Search API country=br).
 * Mantém circuito de cooldown em 403/429, limite de 1 chamada na página 1 e atribuição
 * preservada para visualização, nunca sobrepondo fontes abertas no CoverRanker.
 */
class AppleBooksAdapter(
  private val client: OkHttpClient = BibliographicHttpClients.okHttpClient,
  private val baseUrl: String = "https://itunes.apple.com",
  private val clockMs: () -> Long = { System.currentTimeMillis() },
) : BibliographicSourceAdapter {

  override val source: MetadataSource = MetadataSource.APPLE_BOOKS
  private val json = BibliographicHttpClients.json
  private val cooldownUntilMs = AtomicLong(0L)

  override suspend fun searchByIsbn(
    isbn13: String?,
    isbn10: String?,
    rawIsbn: String,
  ): SourceAdapterResult =
    withContext(Dispatchers.IO) {
      val target = isbn13 ?: isbn10 ?: BibliographicMath.cleanIsbn(rawIsbn)
      if (target.isBlank()) {
        return@withContext SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
      }
      val url = "$baseUrl/lookup?isbn=${URLEncoder.encode(target, "UTF-8")}&country=br&entity=ebook"
      executeRequest(url, explicitRequestedLang = null)
    }

  override suspend fun searchByQuery(query: String): SourceAdapterResult =
    withContext(Dispatchers.IO) {
      val trimmed = query.trim()
      if (trimmed.isEmpty()) {
        return@withContext SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
      }
      val enc = URLEncoder.encode(trimmed, "UTF-8")
      val url = "$baseUrl/search?term=$enc&country=br&media=ebook&entity=ebook&limit=20"
      executeRequest(url, explicitRequestedLang = null)
    }

  override suspend fun searchPaginated(request: AdapterSearchRequest): SourceAdapterResult =
    withContext(Dispatchers.IO) {
      val intent = request.parsedIntent
      if (request.page > 1) {
        return@withContext SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
      }
      if (intent.intentType == QueryIntentType.ISBN_EXACT && intent.parsedIsbn != null) {
        return@withContext searchByIsbn(
          isbn13 = intent.parsedIsbn.isbn13,
          isbn10 = intent.parsedIsbn.isbn10,
          rawIsbn = intent.parsedIsbn.cleaned,
        )
      }
      val plan = QueryPlanner.planAppleBooks(intent)
      if (plan.term.isBlank()) {
        return@withContext SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
      }
      val url = buildString {
        append("$baseUrl/search?term=${URLEncoder.encode(plan.term, "UTF-8")}")
        append("&country=br&media=ebook&entity=ebook&limit=${plan.limit}")
        plan.attribute?.let { attr ->
          append("&attribute=${URLEncoder.encode(attr, "UTF-8")}")
        }
      }
      executeRequest(url, explicitRequestedLang = intent.explicitLanguageRequest)
    }

  private suspend fun executeRequest(
    url: String,
    explicitRequestedLang: String?,
  ): SourceAdapterResult {
    val startMs = clockMs()
    if (startMs < cooldownUntilMs.get()) {
      return SourceAdapterResult(
        SourceQueryStatus(
          source = source,
          state = ProviderOutcomeState.RATE_LIMITED,
          message = "Cooldown active",
          durationMs = 0L,
        )
      )
    }

    return runCatching {
        val request = Request.Builder().url(url).get().build()
        val response = client.executeTextCall(request)
        val elapsed = (clockMs() - startMs).coerceAtLeast(0L)
        if (response.code == 429 || response.code == 403) {
          cooldownUntilMs.set(clockMs() + COOLDOWN_DURATION_MS)
          return SourceAdapterResult(
            SourceQueryStatus(
              source = source,
              state = ProviderOutcomeState.RATE_LIMITED,
              message = "HTTP ${response.code}",
              durationMs = elapsed,
            )
          )
        }
        val body = response.body
        if (!response.isSuccessful || body == null) {
          return SourceAdapterResult(
            SourceQueryStatus(
              source = source,
              state = ProviderOutcomeState.ERROR,
              message = "HTTP ${response.code}",
              durationMs = elapsed,
            )
          )
        }

        val parsed = json.decodeFromString<AppleBooksResponse>(body)
        val candidates =
          parsed.results
            .filter { it.kind == null || it.kind == "ebook" }
            .mapNotNull { item -> mapItemToCandidate(item, explicitRequestedLang) }

        SourceAdapterResult(
          status =
            SourceQueryStatus(
              source = source,
              state = if (candidates.isEmpty()) ProviderOutcomeState.EMPTY else ProviderOutcomeState.SUCCESS,
              count = candidates.size,
              hasMore = false,
              durationMs = elapsed,
            ),
          candidates = candidates,
        )
      }
      .getOrElse { err ->
        if (err is CancellationException) throw err
        val msg = if (err is SocketTimeoutException) "Timeout: ${err.message ?: "socket read"}" else err.message
        SourceAdapterResult(
          SourceQueryStatus(
            source = source,
            state = ProviderOutcomeState.ERROR,
            message = msg,
            durationMs = (clockMs() - startMs).coerceAtLeast(0L),
          )
        )
      }
  }

  private fun mapItemToCandidate(
    item: AppleBooksItem,
    explicitRequestedLang: String?,
  ): SearchEditionCandidate? {
    val rawTitle = BibliographicMath.sanitizeDisplayText(item.trackName ?: item.trackCensoredName)
      ?: return null
    val cleanTitle = cleanAppleTitle(rawTitle)
    val hasExplicitPtSuffix = cleanTitle != rawTitle
    val contributors = parseArtists(item.artistName)
    val year = extractYear(item.releaseDate)
    val synopsis = BibliographicMath.stripHtmlTags(item.description)
    val langAssessment =
      assessAppleBooksLanguage(
        cleanTitle = cleanTitle,
        hasExplicitPtSuffix = hasExplicitPtSuffix,
        synopsis = synopsis,
        explicitRequestedLang = explicitRequestedLang,
      )
    val normLang = langAssessment.normalizedCode

    val covers =
      CoverRanker.buildAppleBooksCoverCandidates(
        artworkUrl100 = item.artworkUrl100 ?: item.artworkUrl60,
        matchesIsbn = false,
        matchesPublisherYear = year != null,
        matchesLanguage = normLang != null,
      )

    val synopses =
      if (synopsis != null) {
        listOf(
          SynopsisCandidate(
            text = synopsis,
            language = normLang,
            source = source,
            editionLabel = "Apple Books",
          )
        )
      } else {
        emptyList()
      }

    val categories =
      item.genres
        .filterNot { it.equals("Books", ignoreCase = true) || it.equals("Livros", ignoreCase = true) }
        .take(6)

    return SearchEditionCandidate(
      candidateId = "ab_${item.trackId ?: cleanTitle.hashCode()}",
      workTitle = cleanTitle,
      contributors = contributors,
      publicationYear = year,
      language = normLang,
      categories = categories,
      coverCandidates = covers,
      synopsisCandidates = synopses,
      externalIds =
        buildMap {
          item.trackId?.let { put("apple_books_track_id", it.toString()) }
          item.trackViewUrl?.takeIf { it.isNotBlank() }?.let { put("apple_books_url", it) }
        },
      sources = listOf(source),
      canonicalLanguageTag = langAssessment.canonicalTag,
      languageTierRank = langAssessment.tier.rank,
      languageConfidenceName = langAssessment.confidence.name,
      hasLanguageConflict = langAssessment.hasConflict,
    )
  }

  private fun parseArtists(rawArtist: String?): List<CandidateContributor> {
    val clean = BibliographicMath.sanitizeDisplayText(rawArtist) ?: return emptyList()
    val parts = clean.split(ARTIST_SPLIT_REGEX).mapNotNull { BibliographicMath.sanitizeDisplayText(it) }
    return parts.mapIndexed { idx, name ->
      CandidateContributor(
        name = name,
        role = if (idx == 0) ContributorRole.AUTHOR else ContributorRole.CO_AUTHOR,
      )
    }
  }

  private fun cleanAppleTitle(raw: String): String =
    raw
      .replace(PT_EDITION_SUFFIX_REGEX, "")
      .trim()
      .ifBlank { raw }

  private fun assessAppleBooksLanguage(
    cleanTitle: String,
    hasExplicitPtSuffix: Boolean,
    synopsis: String?,
    explicitRequestedLang: String?,
  ): LanguageAssessment {
    val base =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = null,
        isbn13Or10 = null,
        publisher = null,
        editionTitle = cleanTitle,
        explicitRequestedLanguage = explicitRequestedLang,
      )
    if (base.canonicalTag != null || BibliographicMath.hasNonLatinScript(cleanTitle)) {
      return base
    }

    val titleForeignLang = BibliographicMath.detectClearForeignLatinLanguage(cleanTitle)
    if (titleForeignLang != null) {
      val norm = BibliographicMath.normalizeLanguageCode(titleForeignLang) ?: titleForeignLang
      return LanguageAssessment(
        normalizedCode = norm,
        canonicalTag = norm,
        tier =
          BibliographicMath.computeTierForCanonical(
            canonicalTag = norm,
            confidence = LanguageConfidence.UNCERTAIN,
            explicitRequestedLanguage = explicitRequestedLang,
          ),
        confidence = LanguageConfidence.UNCERTAIN,
        hasConflict = false,
      )
    }

    val synopsisSample = synopsis?.take(260)
    val synopsisForeignLang = BibliographicMath.detectClearForeignLatinLanguage(synopsisSample)
    if (hasExplicitPtSuffix && synopsisForeignLang == null) {
      return LanguageAssessment(
        normalizedCode = "pt",
        canonicalTag = "pt",
        tier =
          BibliographicMath.computeTierForCanonical(
            canonicalTag = "pt",
            confidence = LanguageConfidence.UNCERTAIN,
            explicitRequestedLanguage = explicitRequestedLang,
          ),
        confidence = LanguageConfidence.UNCERTAIN,
        hasConflict = false,
      )
    }

    if (BibliographicMath.looksPortugueseEditionTitle(synopsisSample)) {
      return LanguageAssessment(
        normalizedCode = "pt",
        canonicalTag = "pt",
        tier =
          BibliographicMath.computeTierForCanonical(
            canonicalTag = "pt",
            confidence = LanguageConfidence.UNCERTAIN,
            explicitRequestedLanguage = explicitRequestedLang,
          ),
        confidence = LanguageConfidence.UNCERTAIN,
        hasConflict = false,
      )
    }

    if (synopsisForeignLang != null) {
      val norm = BibliographicMath.normalizeLanguageCode(synopsisForeignLang) ?: synopsisForeignLang
      return LanguageAssessment(
        normalizedCode = norm,
        canonicalTag = norm,
        tier =
          BibliographicMath.computeTierForCanonical(
            canonicalTag = norm,
            confidence = LanguageConfidence.UNCERTAIN,
            explicitRequestedLanguage = explicitRequestedLang,
          ),
        confidence = LanguageConfidence.UNCERTAIN,
        hasConflict = hasExplicitPtSuffix,
      )
    }

    return base
  }

  private companion object {
    const val COOLDOWN_DURATION_MS = 60_000L
    val ARTIST_SPLIT_REGEX = Regex("\\s+&\\s+|\\s*;\\s*")
    val PT_EDITION_SUFFIX_REGEX =
      Regex("\\s*\\((?:Portuguese Edition|Edi[çc][ãa]o em Portugu[êe]s)\\)\\s*$", RegexOption.IGNORE_CASE)
  }
}
