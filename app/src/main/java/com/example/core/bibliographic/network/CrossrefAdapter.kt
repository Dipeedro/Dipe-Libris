package com.example.core.bibliographic.network

import com.example.core.model.BibliographicMath
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SourceQueryStatus
import com.example.core.model.SynopsisCandidate
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class CrossrefWorksResponse(
  val message: CrossrefMessage? = null,
)

@Serializable
private data class CrossrefMessage(
  val items: List<CrossrefWorkItem> = emptyList(),
)

@Serializable
private data class CrossrefWorkItem(
  @SerialName("DOI") val doi: String? = null,
  val type: String? = null,
  val title: List<String> = emptyList(),
  val subtitle: List<String> = emptyList(),
  val publisher: String? = null,
  @SerialName("ISBN") val isbn: List<String> = emptyList(),
  val author: List<CrossrefPerson> = emptyList(),
  val editor: List<CrossrefPerson> = emptyList(),
  val translator: List<CrossrefPerson> = emptyList(),
  @SerialName("abstract") val abstractText: String? = null,
  val subject: List<String> = emptyList(),
  val language: String? = null,
  @SerialName("published-print") val publishedPrint: CrossrefDateParts? = null,
  @SerialName("published-online") val publishedOnline: CrossrefDateParts? = null,
  val issued: CrossrefDateParts? = null,
)

@Serializable
private data class CrossrefPerson(
  val given: String? = null,
  val family: String? = null,
  val name: String? = null,
)

@Serializable
private data class CrossrefDateParts(
  @SerialName("date-parts") val dateParts: List<List<Int>> = emptyList(),
)

class CrossrefAdapter(
  private val client: OkHttpClient = BibliographicHttpClients.okHttpClient,
  private val baseUrl: String = "https://api.crossref.org",
) : BibliographicSourceAdapter {

  override val source: MetadataSource = MetadataSource.CROSSREF
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
    val filters =
      listOfNotNull(isbn13, isbn10)
        .distinct()
        .joinToString(",") { "isbn:$it" }
        .ifBlank { "isbn:$target" }
    val url =
      "$baseUrl/works?filter=$filters&mailto=${URLEncoder.encode(BibliographicHttpClients.CONTACT_EMAIL, "UTF-8")}&rows=6"
    return executeRequest(url)
  }

  override suspend fun searchByQuery(query: String): SourceAdapterResult {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
      return SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
    }
    val encoded = URLEncoder.encode(trimmed, "UTF-8")
    val url =
      "$baseUrl/works?query.bibliographic=$encoded&filter=type:book,type:monograph,type:edited-book" +
        "&mailto=${URLEncoder.encode(BibliographicHttpClients.CONTACT_EMAIL, "UTF-8")}&rows=8"
    return executeRequest(url)
  }

  private suspend fun executeRequest(url: String): SourceAdapterResult =
    withContext(Dispatchers.IO) {
      runCatching {
          val request = Request.Builder().url(url).get().build()
          client.newCall(request).execute().use { response ->
            if (response.code == 429) {
              return@withContext SourceAdapterResult(
                SourceQueryStatus(source, ProviderOutcomeState.RATE_LIMITED)
              )
            }
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) {
              return@withContext SourceAdapterResult(
                SourceQueryStatus(source, ProviderOutcomeState.ERROR, message = "HTTP ${response.code}")
              )
            }
            val parsed = json.decodeFromString<CrossrefWorksResponse>(body)
            val candidates =
              parsed.message?.items.orEmpty().mapNotNull { item ->
                val title =
                  item.title.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val contributors = buildList {
                  item.author.forEachIndexed { idx, p ->
                    formatPerson(p)?.let { name ->
                      add(
                        CandidateContributor(
                          name = name,
                          role = if (idx == 0) ContributorRole.AUTHOR else ContributorRole.CO_AUTHOR,
                        )
                      )
                    }
                  }
                  item.editor.forEach { p ->
                    formatPerson(p)?.let { name ->
                      add(CandidateContributor(name = name, role = ContributorRole.EDITOR_ORGANIZER))
                    }
                  }
                  item.translator.forEach { p ->
                    formatPerson(p)?.let { name ->
                      add(CandidateContributor(name = name, role = ContributorRole.TRANSLATOR))
                    }
                  }
                }
                val parsedIds = item.isbn.mapNotNull { BibliographicMath.parseIsbn(it) }
                val isbn13 = parsedIds.firstNotNullOfOrNull { it.isbn13 }
                val isbn10 = parsedIds.firstNotNullOfOrNull { it.isbn10 }
                val year =
                  item.publishedPrint?.dateParts?.firstOrNull()?.firstOrNull()
                    ?: item.publishedOnline?.dateParts?.firstOrNull()?.firstOrNull()
                    ?: item.issued?.dateParts?.firstOrNull()?.firstOrNull()
                val lang = BibliographicMath.normalizeLanguageCode(item.language)
                val cleanAbstract = BibliographicMath.stripHtmlTags(item.abstractText)
                val synopses =
                  if (cleanAbstract != null) {
                    listOf(
                      SynopsisCandidate(
                        text = cleanAbstract,
                        language = lang,
                        source = source,
                        editionLabel = item.publisher ?: title,
                      )
                    )
                  } else {
                    emptyList()
                  }

                SearchEditionCandidate(
                  candidateId = "cr_${item.doi ?: title.hashCode()}",
                  workTitle = title,
                  subtitle = item.subtitle.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
                  contributors = contributors,
                  publisher = item.publisher?.trim()?.takeIf { it.isNotEmpty() },
                  publicationYear = year?.takeIf { it in 1400..2100 },
                  isbn10 = isbn10,
                  isbn13 = isbn13,
                  language = lang,
                  categories = item.subject.take(8),
                  synopsisCandidates = synopses,
                  externalIds =
                    buildMap {
                      item.doi?.let { put("doi", it) }
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

  private fun formatPerson(person: CrossrefPerson): String? {
    val full = listOfNotNull(person.given?.trim(), person.family?.trim()).joinToString(" ").trim()
    return full.ifEmpty { person.name?.trim().orEmpty() }.takeIf { it.isNotEmpty() }
  }
}
