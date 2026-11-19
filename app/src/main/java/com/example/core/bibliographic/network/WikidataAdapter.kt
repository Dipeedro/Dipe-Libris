package com.example.core.bibliographic.network

import com.example.core.model.BibliographicMath
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SourceQueryStatus
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class WikidataSparqlResponse(
  val results: WikidataResults? = null,
)

@Serializable
private data class WikidataResults(
  val bindings: List<Map<String, WikidataBindingValue>> = emptyList(),
)

@Serializable
private data class WikidataBindingValue(
  val value: String = "",
)

class WikidataAdapter(
  private val client: OkHttpClient = BibliographicHttpClients.okHttpClient,
  private val endpointUrl: String = "https://query.wikidata.org/sparql",
) : BibliographicSourceAdapter {

  override val source: MetadataSource = MetadataSource.WIKIDATA
  private val json = BibliographicHttpClients.json

  override suspend fun searchByIsbn(
    isbn13: String?,
    isbn10: String?,
    rawIsbn: String,
  ): SourceAdapterResult =
    withContext(Dispatchers.IO) {
      val candidatesIsbn =
        listOfNotNull(
            isbn13,
            isbn13?.let { formatHyphenated13Fallback(it) },
            isbn10,
            rawIsbn.trim().takeIf { it.isNotBlank() },
          )
          .distinct()
      if (candidatesIsbn.isEmpty()) {
        return@withContext SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
      }
      val valuesClause = candidatesIsbn.joinToString(" ") { "\"${it.replace("\"", "")}\"" }
      val sparql =
        """
        SELECT ?edition ?editionLabel ?work ?workLabel ?authorLabel ?translatorLabel ?illustratorLabel ?publisherLabel ?genreLabel ?seriesLabel ?pubDate ?pages ?isbn13 ?isbn10 WHERE {
          VALUES ?isbnQuery { $valuesClause }
          { ?edition wdt:P212 ?isbnQuery . } UNION { ?edition wdt:P957 ?isbnQuery . }
          OPTIONAL { ?edition wdt:P212 ?isbn13 . }
          OPTIONAL { ?edition wdt:P957 ?isbn10 . }
          OPTIONAL { ?edition wdt:P629 ?work . }
          OPTIONAL { ?edition wdt:P50 ?edAuthor . }
          OPTIONAL { ?work wdt:P50 ?workAuthor . }
          BIND(COALESCE(?workAuthor, ?edAuthor) AS ?author)
          OPTIONAL { ?edition wdt:P655 ?translator . }
          OPTIONAL { ?edition wdt:P110 ?illustrator . }
          OPTIONAL { ?edition wdt:P123 ?publisher . }
          OPTIONAL { ?work wdt:P136 ?workGenre . }
          OPTIONAL { ?edition wdt:P136 ?edGenre . }
          BIND(COALESCE(?workGenre, ?edGenre) AS ?genre)
          OPTIONAL { ?work wdt:P179 ?series . }
          OPTIONAL { ?edition wdt:P577 ?pubDate . }
          OPTIONAL { ?edition wdt:P1104 ?pages . }
          SERVICE wikibase:label { bd:serviceParam wikibase:language "pt,en". }
        }
        LIMIT 12
        """.trimIndent()

      executeSparql(sparql, isbn13, isbn10)
    }

  override suspend fun searchByQuery(query: String): SourceAdapterResult =
    // Wikidata SPARQL text search is expensive and timeout-prone; we reserve Wikidata for
    // exact ISBN / canonical work-linking so we never hit 60s timeouts on general searches.
    SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))

  private fun executeSparql(
    sparql: String,
    fallbackIsbn13: String?,
    fallbackIsbn10: String?,
  ): SourceAdapterResult {
    return runCatching {
        val url = "$endpointUrl?format=json&query=${URLEncoder.encode(sparql, "UTF-8")}"
        val request =
          Request.Builder()
            .url(url)
            .header("Accept", "application/sparql-results+json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
          if (response.code == 429) {
            return SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.RATE_LIMITED))
          }
          val body = response.body?.string()
          if (!response.isSuccessful || body == null) {
            return SourceAdapterResult(
              SourceQueryStatus(source, ProviderOutcomeState.ERROR, message = "HTTP ${response.code}")
            )
          }
          val parsed = json.decodeFromString<WikidataSparqlResponse>(body)
          val bindings = parsed.results?.bindings.orEmpty()
          if (bindings.isEmpty()) {
            return SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
          }

          val groupedByEdition = bindings.groupBy { it["edition"]?.value ?: "unknown" }
          val candidates =
            groupedByEdition.mapNotNull { (editionUri, rows) ->
              val first = rows.firstOrNull() ?: return@mapNotNull null
              val title =
                first["workLabel"]?.value?.takeIf { !isQid(it) }
                  ?: first["editionLabel"]?.value?.takeIf { !isQid(it) }
                  ?: return@mapNotNull null

              val contributors = buildList {
                rows.mapNotNull { it["authorLabel"]?.value?.takeIf { s -> !isQid(s) } }.distinct().forEachIndexed { idx, name ->
                  add(
                    CandidateContributor(
                      name = name,
                      role = if (idx == 0) ContributorRole.AUTHOR else ContributorRole.CO_AUTHOR,
                    )
                  )
                }
                rows.mapNotNull { it["translatorLabel"]?.value?.takeIf { s -> !isQid(s) } }.distinct().forEach { name ->
                  add(CandidateContributor(name = name, role = ContributorRole.TRANSLATOR))
                }
                rows.mapNotNull { it["illustratorLabel"]?.value?.takeIf { s -> !isQid(s) } }.distinct().forEach { name ->
                  add(CandidateContributor(name = name, role = ContributorRole.ILLUSTRATOR))
                }
              }

              val parsed13 =
                BibliographicMath.parseIsbn(first["isbn13"]?.value ?: fallbackIsbn13)?.isbn13
              val parsed10 =
                BibliographicMath.parseIsbn(first["isbn10"]?.value ?: fallbackIsbn10)?.isbn10
              val workQid = first["work"]?.value?.substringAfterLast("/")?.takeIf { it.startsWith("Q") }
              val editionQid = editionUri.substringAfterLast("/").takeIf { it.startsWith("Q") }
              val genres = rows.mapNotNull { it["genreLabel"]?.value?.takeIf { s -> !isQid(s) } }.distinct()

              SearchEditionCandidate(
                candidateId = "wd_${editionQid ?: title.hashCode()}",
                workTitle = title,
                editionTitle = first["editionLabel"]?.value?.takeIf { !isQid(it) },
                contributors = contributors,
                publisher = first["publisherLabel"]?.value?.takeIf { !isQid(it) },
                publicationYear = extractYear(first["pubDate"]?.value),
                totalPages = first["pages"]?.value?.toDoubleOrNull()?.toInt()?.takeIf { it in 1..50_000 },
                isbn10 = parsed10,
                isbn13 = parsed13,
                seriesName = first["seriesLabel"]?.value?.takeIf { !isQid(it) },
                categories = genres,
                externalWorkKey = workQid?.let { "wikidata:$it" },
                externalIds =
                  buildMap {
                    workQid?.let { put("wikidata_work", it) }
                    editionQid?.let { put("wikidata_edition", it) }
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

  private fun isQid(value: String): Boolean = Regex("^Q\\d+$").matches(value.trim())

  private fun formatHyphenated13Fallback(isbn13: String): String? {
    if (isbn13.length != 13) return null
    // Common Portuguese/Brazilian 978-85 / 978-65 formatting won't always match exact registrant split,
    // so keep unhyphenated primary + standard 978-85 prefix attempt if needed.
    return null
  }
}
