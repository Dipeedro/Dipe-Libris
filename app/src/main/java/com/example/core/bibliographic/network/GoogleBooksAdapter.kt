package com.example.core.bibliographic.network

import com.example.BuildConfig
import com.example.core.model.BibliographicMath
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.CoverCandidate
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SourceQueryStatus
import com.example.core.model.SynopsisCandidate
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class GoogleBooksResponse(
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
    return executeQuery("isbn:$target", maxResults = 10)
  }

  override suspend fun searchByQuery(query: String): SourceAdapterResult {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
      return SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
    }
    return executeQuery(trimmed, maxResults = 15)
  }

  private suspend fun executeQuery(qParam: String, maxResults: Int): SourceAdapterResult =
    withContext(Dispatchers.IO) {
      val key = apiKeyProvider().trim()
      if (key.isEmpty() || key == "MY_GOOGLE_BOOKS_API_KEY") {
        return@withContext SourceAdapterResult(
          status = SourceQueryStatus(source = source, state = ProviderOutcomeState.SKIPPED_NO_API_KEY),
          candidates = emptyList(),
        )
      }
      runCatching {
          val encodedQ = URLEncoder.encode(qParam, "UTF-8")
          val encodedKey = URLEncoder.encode(key, "UTF-8")
          val url = "$baseUrl/volumes?q=$encodedQ&maxResults=$maxResults&printType=books&key=$encodedKey"
          val request = Request.Builder().url(url).get().build()
          client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (
              response.code == 429 ||
                (response.code == 403 &&
                  (body.orEmpty().contains("limit", ignoreCase = true) ||
                    body.orEmpty().contains("quota", ignoreCase = true)))
            ) {
              return@withContext SourceAdapterResult(
                SourceQueryStatus(source, ProviderOutcomeState.RATE_LIMITED)
              )
            }
            if (!response.isSuccessful || body == null) {
              return@withContext SourceAdapterResult(
                SourceQueryStatus(source, ProviderOutcomeState.ERROR, message = "HTTP ${response.code}")
              )
            }
            val parsed = json.decodeFromString<GoogleBooksResponse>(body)
            val candidates =
              parsed.items.mapNotNull { item ->
                val info = item.volumeInfo ?: return@mapNotNull null
                val title = info.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val contributors =
                  info.authors.mapIndexed { idx, name ->
                    CandidateContributor(
                      name = name.trim(),
                      role = if (idx == 0) ContributorRole.AUTHOR else ContributorRole.CO_AUTHOR,
                    )
                  }
                val parsedIds =
                  info.industryIdentifiers.mapNotNull { BibliographicMath.parseIsbn(it.identifier) }
                val isbn13 = parsedIds.firstNotNullOfOrNull { it.isbn13 }
                val isbn10 = parsedIds.firstNotNullOfOrNull { it.isbn10 }
                val lang = BibliographicMath.normalizeLanguageCode(info.language)
                val pub = info.publisher?.trim()?.takeIf { it.isNotEmpty() }
                val year = extractYear(info.publishedDate)
                val cleanSynopsis = BibliographicMath.stripHtmlTags(info.description)
                val rawImage =
                  info.imageLinks?.large
                    ?: info.imageLinks?.medium
                    ?: info.imageLinks?.small
                    ?: info.imageLinks?.thumbnail
                    ?: info.imageLinks?.smallThumbnail
                val secureImage =
                  rawImage
                    ?.replace("http://", "https://")
                    ?.replace("&edge=curl", "")
                    ?.takeIf { it.isNotBlank() }

                val covers =
                  if (secureImage != null) {
                    listOf(
                      CoverCandidate(
                        imageUrl = secureImage,
                        source = source,
                        matchesIsbn = isbn13 != null || isbn10 != null,
                        matchesPublisherYear = pub != null && year != null,
                        matchesLanguage = lang != null,
                      )
                    )
                  } else {
                    emptyList()
                  }

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

                SearchEditionCandidate(
                  candidateId = "gb_${item.id ?: title.hashCode()}",
                  workTitle = title,
                  subtitle = info.subtitle?.trim()?.takeIf { it.isNotEmpty() },
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
                )
              }

            SourceAdapterResult(
              status =
                SourceQueryStatus(
                  source = source,
                  state = if (candidates.isEmpty()) ProviderOutcomeState.EMPTY else ProviderOutcomeState.SUCCESS,
                  count = candidates.size,
                ),
              candidates = candidates,
            )
          }
        }
        .getOrElse { err ->
          SourceAdapterResult(
            SourceQueryStatus(source, ProviderOutcomeState.ERROR, message = err.message)
          )
        }
    }
}
