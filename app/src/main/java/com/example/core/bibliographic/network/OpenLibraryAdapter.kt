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
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
  val docs: List<OlSearchDoc> = emptyList(),
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
  val language: List<String> = emptyList(),
  val subject: List<String> = emptyList(),
  @SerialName("cover_i") val coverId: Long? = null,
  @SerialName("number_of_pages_median") val medianPages: Int? = null,
  val publisher: List<String> = emptyList(),
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
              SourceQueryStatus(source, ProviderOutcomeState.RATE_LIMITED)
            )
          }
          val editionBody = editionResp.body
          if (editionResp.code == 200 && editionBody != null) {
            val edition = json.decodeFromString<OlEditionRecord>(editionBody)
            val workKey = edition.works.firstOrNull()?.key
            val work =
              workKey?.let { wk ->
                val workResp = fetchJson("$baseUrl$wk.json")
                workResp.body?.takeIf { workResp.code == 200 }?.let {
                  runCatching { json.decodeFromString<OlWorkRecord>(it) }.getOrNull()
                }
              }

            val authorKeys =
              (edition.authors.mapNotNull { it.key } + work?.authors?.mapNotNull { it.author?.key }.orEmpty())
                .distinct()
                .take(4)
            val resolvedAuthors =
              authorKeys.mapNotNull { aKey ->
                val resp = fetchJson("$baseUrl$aKey.json")
                resp.body?.takeIf { resp.code == 200 }?.let { body ->
                  runCatching { json.decodeFromString<OlAuthorRecord>(body) }.getOrNull()?.name
                }?.let { authorName ->
                  CandidateContributor(
                    name = authorName,
                    role = ContributorRole.AUTHOR,
                    externalId = aKey,
                  )
                }
              }

            val candidate = buildCandidateFromEdition(edition, work, resolvedAuthors)
            return@withContext SourceAdapterResult(
              status = SourceQueryStatus(source, ProviderOutcomeState.SUCCESS, count = 1),
              candidates = listOf(candidate),
            )
          }

          // Fallback to search.json?isbn=...
          val searchUrl =
            "$baseUrl/search.json?isbn=${URLEncoder.encode(target, "UTF-8")}" +
              "&fields=$SEARCH_FIELDS&limit=8"
          executeSearchUrl(searchUrl)
        }
        .getOrElse { err ->
          SourceAdapterResult(
            SourceQueryStatus(source, ProviderOutcomeState.ERROR, message = err.message)
          )
        }
    }

  override suspend fun searchByQuery(query: String): SourceAdapterResult =
    withContext(Dispatchers.IO) {
      val trimmed = query.trim()
      if (trimmed.isEmpty()) {
        return@withContext SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
      }
      runCatching {
          val encoded = URLEncoder.encode(trimmed, "UTF-8")
          val url = "$baseUrl/search.json?q=$encoded&fields=$SEARCH_FIELDS&limit=15"
          executeSearchUrl(url)
        }
        .getOrElse { err ->
          SourceAdapterResult(
            SourceQueryStatus(source, ProviderOutcomeState.ERROR, message = err.message)
          )
        }
    }

  override suspend fun fetchEditionsForWork(externalWorkKey: String): SourceAdapterResult =
    withContext(Dispatchers.IO) {
      if (!externalWorkKey.startsWith("/works/")) {
        return@withContext SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
      }
      runCatching {
          val workResp = fetchJson("$baseUrl$externalWorkKey.json")
          val work =
            workResp.body?.takeIf { workResp.code == 200 }?.let {
              runCatching { json.decodeFromString<OlWorkRecord>(it) }.getOrNull()
            }
          val editionsResp = fetchJson("$baseUrl$externalWorkKey/editions.json?limit=25")
          if (editionsResp.code == 429) {
            return@withContext SourceAdapterResult(
              SourceQueryStatus(source, ProviderOutcomeState.RATE_LIMITED)
            )
          }
          val body = editionsResp.body
          if (editionsResp.code != 200 || body == null) {
            return@withContext SourceAdapterResult(
              SourceQueryStatus(source, ProviderOutcomeState.EMPTY)
            )
          }
          val parsed = json.decodeFromString<OlWorkEditionsResponse>(body)
          val authorNames =
            work?.authors?.mapNotNull { it.author?.key }?.take(3)?.mapNotNull { aKey ->
              val resp = fetchJson("$baseUrl$aKey.json")
              resp.body?.takeIf { resp.code == 200 }?.let { aBody ->
                runCatching { json.decodeFromString<OlAuthorRecord>(aBody) }.getOrNull()?.name
              }?.let { CandidateContributor(it, ContributorRole.AUTHOR, aKey) }
            }.orEmpty()

          val candidates =
            parsed.entries
              .filter { !it.title.isNullOrBlank() || work?.title != null }
              .map { ed -> buildCandidateFromEdition(ed, work, authorNames) }
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
        .getOrElse { err ->
          SourceAdapterResult(
            SourceQueryStatus(source, ProviderOutcomeState.ERROR, message = err.message)
          )
        }
    }

  private fun executeSearchUrl(url: String): SourceAdapterResult {
    val resp = fetchJson(url)
    if (resp.code == 429) {
      return SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.RATE_LIMITED))
    }
    val body = resp.body
    if (resp.code != 200 || body == null) {
      return SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
    }
    val parsed = json.decodeFromString<OlSearchResponse>(body)
    val candidates =
      parsed.docs.mapNotNull { doc ->
        val title = doc.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val contributors =
          doc.authorNames.mapIndexed { idx, name ->
            CandidateContributor(
              name = name.trim(),
              role = if (idx == 0) ContributorRole.AUTHOR else ContributorRole.CO_AUTHOR,
              externalId = doc.authorKeys.getOrNull(idx),
            )
          }
        val parsedIsbns = doc.isbn.mapNotNull { BibliographicMath.parseIsbn(it) }
        val isbn13 = parsedIsbns.firstNotNullOfOrNull { it.isbn13 }
        val isbn10 = parsedIsbns.firstNotNullOfOrNull { it.isbn10 }
        val lang = doc.language.firstOrNull()?.let { BibliographicMath.normalizeLanguageCode(it) }
        val covers = buildList {
          doc.coverId?.takeIf { it > 0 }?.let { cid ->
            add(
              CoverCandidate(
                imageUrl = "https://covers.openlibrary.org/b/id/$cid-L.jpg?default=false",
                source = source,
                matchesLanguage = lang != null,
                matchesPublisherYear = doc.firstPublishYear != null,
              )
            )
          }
          isbn13?.let { code ->
            add(
              CoverCandidate(
                imageUrl = "https://covers.openlibrary.org/b/isbn/$code-L.jpg?default=false",
                source = source,
                matchesIsbn = true,
              )
            )
          }
        }
        SearchEditionCandidate(
          candidateId = "ol_${doc.key ?: title.hashCode()}_${isbn13 ?: doc.editionKeys.firstOrNull().orEmpty()}",
          workTitle = title,
          subtitle = doc.subtitle?.trim()?.takeIf { it.isNotEmpty() },
          contributors = contributors,
          publisher = doc.publisher.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
          publicationYear = doc.firstPublishYear?.takeIf { it in 1400..2100 },
          totalPages = doc.medianPages?.takeIf { it in 1..50_000 },
          isbn10 = isbn10,
          isbn13 = isbn13,
          language = lang,
          categories = doc.subject.take(8),
          coverCandidates = covers,
          externalWorkKey = doc.key,
          externalIds =
            buildMap {
              doc.key?.let { put("openlibrary_work", it) }
              doc.editionKeys.firstOrNull()?.let { put("openlibrary_edition", "/books/$it") }
            },
          sources = listOf(source),
        )
      }
    return SourceAdapterResult(
      status =
        SourceQueryStatus(
          source = source,
          state = if (candidates.isEmpty()) ProviderOutcomeState.EMPTY else ProviderOutcomeState.SUCCESS,
          count = candidates.size,
        ),
      candidates = candidates,
    )
  }

  private fun buildCandidateFromEdition(
    edition: OlEditionRecord,
    work: OlWorkRecord?,
    resolvedWorkAuthors: List<CandidateContributor>,
  ): SearchEditionCandidate {
    val parsed13 = edition.isbn13.firstNotNullOfOrNull { BibliographicMath.parseIsbn(it) }
    val parsed10 = edition.isbn10.firstNotNullOfOrNull { BibliographicMath.parseIsbn(it) }
    val final13 = parsed13?.isbn13 ?: parsed10?.isbn13
    val final10 = parsed10?.isbn10 ?: parsed13?.isbn10
    val lang =
      edition.languages
        .firstOrNull()
        ?.key
        ?.substringAfterLast("/")
        ?.let { BibliographicMath.normalizeLanguageCode(it) }

    val editionContributors =
      edition.contributors.mapNotNull { c ->
        val name = c.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
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
            editionLabel = edition.editionName ?: edition.publishers.firstOrNull(),
          )
        )
      }
      if (workDesc != null && workDesc != editionDesc) {
        add(
          SynopsisCandidate(
            text = workDesc,
            language = null,
            source = source,
            editionLabel = work?.title,
          )
        )
      }
    }

    val pubYear = extractYear(edition.publishDate)
    val pub = edition.publishers.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    val covers = buildList {
      edition.covers.filter { it > 0 }.take(3).forEach { cid ->
        add(
          CoverCandidate(
            imageUrl = "https://covers.openlibrary.org/b/id/$cid-L.jpg?default=false",
            source = source,
            matchesIsbn = final13 != null || final10 != null,
            matchesPublisherYear = pub != null && pubYear != null,
            matchesLanguage = lang != null,
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

    return SearchEditionCandidate(
      candidateId = "ol_ed_${edition.key ?: final13 ?: final10 ?: edition.title.hashCode()}",
      workTitle = work?.title?.trim()?.takeIf { it.isNotEmpty() }
        ?: edition.title?.trim()
        ?: "",
      subtitle = edition.subtitle?.trim() ?: work?.subtitle?.trim(),
      editionTitle = edition.title?.trim(),
      contributors = allContributors,
      publisher = pub,
      publicationYear = pubYear,
      totalPages = edition.numberOfPages?.takeIf { it in 1..50_000 },
      isbn10 = final10,
      isbn13 = final13,
      language = lang,
      format = format,
      editionLabel = edition.editionName?.trim()?.takeIf { it.isNotEmpty() },
      seriesName = edition.series.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
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

  private data class HttpResponseSnapshot(val code: Int, val body: String?)

  private fun fetchJson(url: String): HttpResponseSnapshot {
    val request = Request.Builder().url(url).get().build()
    client.newCall(request).execute().use { response ->
      return HttpResponseSnapshot(response.code, response.body?.string())
    }
  }

  private companion object {
    const val SEARCH_FIELDS =
      "key,title,subtitle,author_name,author_key,first_publish_year,isbn,edition_key,language,subject,cover_i,number_of_pages_median,publisher"
  }
}
