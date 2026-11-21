package com.example.core.bibliographic.network

import com.example.core.model.BibliographicMath
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.CoverCandidate
import com.example.core.model.EditionFormat
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SourceQueryStatus
import com.example.core.model.SynopsisCandidate
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class BrasilApiBookResponse(
  val isbn: String? = null,
  val title: String? = null,
  val subtitle: String? = null,
  val authors: List<String> = emptyList(),
  val publisher: String? = null,
  val synopsis: String? = null,
  val year: Int? = null,
  val format: String? = null,
  @SerialName("page_count") val pageCount: Int? = null,
  val subjects: List<String> = emptyList(),
  @SerialName("cover_url") val coverUrl: String? = null,
  val provider: String? = null,
)

class BrasilApiAdapter(
  private val client: OkHttpClient = BibliographicHttpClients.okHttpClient,
  private val baseUrl: String = "https://brasilapi.com.br/api/isbn/v1",
) : BibliographicSourceAdapter {

  override val source: MetadataSource = MetadataSource.BRASIL_API
  private val json = BibliographicHttpClients.json

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
      runCatching {
          val request = Request.Builder().url("$baseUrl/$target").get().build()
          val response = client.executeTextCall(request)
          if (response.code == 429) {
            return@withContext SourceAdapterResult(
              SourceQueryStatus(source, ProviderOutcomeState.RATE_LIMITED)
            )
          }
          if (response.code == 404) {
            return@withContext SourceAdapterResult(
              SourceQueryStatus(source, ProviderOutcomeState.EMPTY)
            )
          }
          val body = response.body
          if (!response.isSuccessful || body == null) {
            return@withContext SourceAdapterResult(
              SourceQueryStatus(source, ProviderOutcomeState.ERROR, message = "HTTP ${response.code}")
            )
          }
          val parsed = json.decodeFromString<BrasilApiBookResponse>(body)
            val title = parsed.title?.trim()?.takeIf { it.isNotEmpty() }
              ?: return@withContext SourceAdapterResult(
                SourceQueryStatus(source, ProviderOutcomeState.EMPTY)
              )
            val parsedIsbn = BibliographicMath.parseIsbn(parsed.isbn ?: target)
            val contributors =
              parsed.authors.mapIndexed { idx, name ->
                CandidateContributor(
                  name = name.trim(),
                  role = if (idx == 0) ContributorRole.AUTHOR else ContributorRole.CO_AUTHOR,
                )
              }
            val cleanSynopsis = BibliographicMath.stripHtmlTags(parsed.synopsis)
            val synopses =
              if (cleanSynopsis != null) {
                listOf(
                  SynopsisCandidate(
                    text = cleanSynopsis,
                    language = "pt",
                    source = source,
                    editionLabel = parsed.publisher ?: title,
                  )
                )
              } else {
                emptyList()
              }
            val covers =
              parsed.coverUrl
                ?.replace("http://", "https://")
                ?.takeIf { it.isNotBlank() }
                ?.let { url ->
                  listOf(
                    CoverCandidate(
                      imageUrl = url,
                      source = source,
                      matchesIsbn = true,
                      matchesPublisherYear = parsed.publisher != null && parsed.year != null,
                      matchesLanguage = true,
                    )
                  )
                }
                .orEmpty()

            val mappedFormat =
              parsed.format?.uppercase()?.let { fmt ->
                when {
                  "PHYSICAL" in fmt || "PAPERBACK" in fmt || "BROCHURA" in fmt -> EditionFormat.PAPERBACK
                  "HARDCOVER" in fmt || "CAPA DURA" in fmt -> EditionFormat.HARDCOVER
                  "DIGITAL" in fmt || "EBOOK" in fmt -> EditionFormat.EBOOK
                  else -> null
                }
              }

            val candidate =
              SearchEditionCandidate(
                candidateId = "br_${parsedIsbn?.isbn13 ?: target}",
                workTitle = title,
                subtitle = parsed.subtitle?.trim()?.takeIf { it.isNotEmpty() },
                contributors = contributors,
                publisher = parsed.publisher?.trim()?.takeIf { it.isNotEmpty() },
                publicationYear = parsed.year?.takeIf { it in 1400..2100 },
                totalPages = parsed.pageCount?.takeIf { it in 1..50_000 },
                isbn10 = parsedIsbn?.isbn10 ?: isbn10,
                isbn13 = parsedIsbn?.isbn13 ?: isbn13,
                language = "pt",
                format = mappedFormat,
                categories = parsed.subjects.take(8),
                coverCandidates = covers,
                synopsisCandidates = synopses,
                externalIds =
                  buildMap {
                    parsed.provider?.let { put("brasilapi_provider", it) }
                  },
                sources = listOf(source),
              )
            SourceAdapterResult(
              status = SourceQueryStatus(source, ProviderOutcomeState.SUCCESS, count = 1),
              candidates = listOf(candidate),
            )
        }
        .getOrElse { err ->
          if (err is CancellationException) throw err
          val msg = if (err is SocketTimeoutException) "Timeout: ${err.message ?: "socket read"}" else err.message
          SourceAdapterResult(
            SourceQueryStatus(source, ProviderOutcomeState.ERROR, message = msg)
          )
        }
    }

  override suspend fun searchByQuery(query: String): SourceAdapterResult =
    // BrasilAPI only supports ISBN lookup, never textual query or bulk crawling.
    SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
}
