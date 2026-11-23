package com.example.core.bibliographic.network

import com.example.core.model.BibliographicMath
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SourceQueryStatus
import java.net.SocketTimeoutException
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

@Serializable
private data class WbSearchEntitiesResponse(
  val search: List<WbSearchHit> = emptyList(),
)

@Serializable
private data class WbSearchHit(
  val id: String? = null,
  val label: String? = null,
  val description: String? = null,
)

class WikidataAdapter(
  private val client: OkHttpClient = BibliographicHttpClients.okHttpClient,
  private val endpointUrl: String = "https://query.wikidata.org/sparql",
  private val mediaWikiApiUrl: String = "https://www.wikidata.org/w/api.php",
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
    // SPARQL textual continua desativado; ponte bilíngue usa apenas MediaWiki API leve via fetchBilingualBridge().
    SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))

  /**
   * Ponte bilíngue leve via MediaWiki API (wbsearchentities + wbgetentities) limitada por timeout curto (~900ms),
   * conectando obras PT-BR <-> EN e retornando externalWorkKey (P648 Open Library) + wikidata_work (Q-ID).
   */
  suspend fun fetchBilingualBridge(query: String): SourceAdapterResult =
    withContext(Dispatchers.IO) {
      val trimmed = query.trim()
      if (trimmed.isEmpty()) {
        return@withContext SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
      }
      val startMs = System.currentTimeMillis()
      val outcome =
        withTimeoutOrNull(BRIDGE_TIMEOUT_MS) {
          runCatching {
              val encQ = URLEncoder.encode(trimmed, "UTF-8")
              val searchUrl =
                "$mediaWikiApiUrl?action=wbsearchentities&search=$encQ&language=pt&uselang=pt-br&type=item&limit=5&format=json"
              val searchBody = executeGet(searchUrl) ?: return@runCatching SourceAdapterResult(
                SourceQueryStatus(source, ProviderOutcomeState.EMPTY, durationMs = System.currentTimeMillis() - startMs)
              )
              val searchResp = json.decodeFromString<WbSearchEntitiesResponse>(searchBody)
              val qids = searchResp.search.mapNotNull { it.id?.takeIf { id -> id.startsWith("Q") } }.distinct().take(5)
              if (qids.isEmpty()) {
                return@runCatching SourceAdapterResult(
                  SourceQueryStatus(source, ProviderOutcomeState.EMPTY, durationMs = System.currentTimeMillis() - startMs)
                )
              }

              val entitiesUrl =
                "$mediaWikiApiUrl?action=wbgetentities&ids=${qids.joinToString("|")}&props=labels|claims&languages=pt-br|pt|en&format=json"
              val entitiesBody = executeGet(entitiesUrl) ?: return@runCatching SourceAdapterResult(
                SourceQueryStatus(source, ProviderOutcomeState.EMPTY, durationMs = System.currentTimeMillis() - startMs)
              )
              val root = json.parseToJsonElement(entitiesBody).jsonObject
              val entitiesObj = root["entities"]?.jsonObject ?: return@runCatching SourceAdapterResult(
                SourceQueryStatus(source, ProviderOutcomeState.EMPTY, durationMs = System.currentTimeMillis() - startMs)
              )

              data class BridgeDraft(
                val qid: String,
                val ptLabel: String?,
                val enLabel: String?,
                val olId: String?,
                val authorQids: List<String>,
                val seriesQid: String?,
              )

              val drafts = mutableListOf<BridgeDraft>()
              val refQidsToResolve = linkedSetOf<String>()

              for (qid in qids) {
                val entity = entitiesObj[qid]?.jsonObject ?: continue
                val labels = entity["labels"]?.jsonObject
                val ptLabel =
                  extractLangLabel(labels, "pt-br") ?: extractLangLabel(labels, "pt")
                val enLabel = extractLangLabel(labels, "en")
                if (ptLabel == null && enLabel == null) continue

                val claims = entity["claims"]?.jsonObject ?: JsonObject(emptyMap())
                val p31Qids = extractClaimEntityIds(claims, "P31")
                val olId = extractClaimStringValue(claims, "P648")
                val hasBookP31 = p31Qids.any { it in BOOK_ENTITY_P31_IDS }
                val hasNonBookP31Only = !hasBookP31 && p31Qids.any { it in NON_BOOK_ENTITY_P31_IDS }
                if (hasNonBookP31Only || (!hasBookP31 && olId == null && p31Qids.isNotEmpty())) {
                  continue
                }
                if (!hasBookP31 && olId == null) continue

                val authorQids = extractClaimEntityIds(claims, "P50").take(2)
                val seriesQid = extractClaimEntityIds(claims, "P179").firstOrNull()
                refQidsToResolve.addAll(authorQids)
                seriesQid?.let { refQidsToResolve.add(it) }
                drafts.add(BridgeDraft(qid, ptLabel, enLabel, olId, authorQids, seriesQid))
              }

              val resolvedLabels =
                if (refQidsToResolve.isNotEmpty()) {
                  val refsUrl =
                    "$mediaWikiApiUrl?action=wbgetentities&ids=${refQidsToResolve.take(10).joinToString("|")}&props=labels&languages=pt-br|pt|en&format=json"
                  executeGet(refsUrl)?.let { body ->
                    runCatching {
                        val refEntities = json.parseToJsonElement(body).jsonObject["entities"]?.jsonObject
                        buildMap {
                          refEntities?.entries?.forEach { (id, elem) ->
                            val lbls = elem.jsonObject["labels"]?.jsonObject
                            val label =
                              extractLangLabel(lbls, "pt-br")
                                ?: extractLangLabel(lbls, "pt")
                                ?: extractLangLabel(lbls, "en")
                            if (label != null) put(id, label)
                          }
                        }
                      }
                      .getOrNull()
                  }.orEmpty()
                } else {
                  emptyMap()
                }

              val candidates =
                drafts.mapNotNull { draft ->
                  val displayTitle = draft.ptLabel ?: draft.enLabel ?: return@mapNotNull null
                  val origTitle =
                    draft.enLabel?.takeIf {
                      BibliographicMath.normalizeBaseTitle(it) != BibliographicMath.normalizeBaseTitle(displayTitle)
                    }
                  val contributors =
                    draft.authorQids.mapIndexedNotNull { idx, aq ->
                      resolvedLabels[aq]?.let { name ->
                        CandidateContributor(
                          name = name,
                          role = if (idx == 0) ContributorRole.AUTHOR else ContributorRole.CO_AUTHOR,
                          externalId = aq,
                        )
                      }
                    }
                  val olWorkKey =
                    draft.olId?.let { rawOl ->
                      when {
                        rawOl.startsWith("/works/") -> rawOl
                        rawOl.endsWith("W") -> "/works/$rawOl"
                        else -> null
                      }
                    }
                  val lang = if (draft.ptLabel != null) "pt" else "en"
                  val langAssessment =
                    BibliographicMath.assessLanguage(
                      explicitLanguageRaw = if (draft.ptLabel != null) "pt-br" else "en",
                      editionTitle = displayTitle,
                    )

                  SearchEditionCandidate(
                    candidateId = "wd_bridge_${draft.qid}",
                    workTitle = displayTitle,
                    originalTitle = origTitle,
                    editionTitle = if (draft.ptLabel != null) draft.ptLabel else null,
                    contributors = contributors,
                    language = lang,
                    seriesName = draft.seriesQid?.let { resolvedLabels[it] },
                    externalWorkKey = olWorkKey ?: "wikidata:${draft.qid}",
                    externalIds =
                      buildMap {
                        put("wikidata_work", draft.qid)
                        olWorkKey?.let { put("openlibrary_work", it) }
                      },
                    sources = listOf(source),
                    canonicalLanguageTag = langAssessment.canonicalTag,
                    languageTierRank = langAssessment.tier.rank,
                    languageConfidenceName = langAssessment.confidence.name,
                    hasLanguageConflict = langAssessment.hasConflict,
                  )
                }

              SourceAdapterResult(
                status =
                  SourceQueryStatus(
                    source = source,
                    state = if (candidates.isEmpty()) ProviderOutcomeState.EMPTY else ProviderOutcomeState.SUCCESS,
                    count = candidates.size,
                    durationMs = System.currentTimeMillis() - startMs,
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
                  durationMs = System.currentTimeMillis() - startMs,
                )
              )
            }
        }
      outcome ?: SourceAdapterResult(
        SourceQueryStatus(
          source = source,
          state = ProviderOutcomeState.EMPTY,
          message = "Bridge timeout",
          durationMs = System.currentTimeMillis() - startMs,
        )
      )
    }

  private suspend fun executeGet(url: String): String? {
    val request = Request.Builder().url(url).get().build()
    val response = client.executeTextCall(request)
    if (!response.isSuccessful) return null
    return response.body
  }

  private fun extractLangLabel(labelsObj: JsonObject?, lang: String): String? =
    labelsObj?.get(lang)?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull?.let {
      BibliographicMath.sanitizeDisplayText(it)
    }

  private fun extractClaimEntityIds(claimsObj: JsonObject, prop: String): List<String> {
    val arr: JsonArray = claimsObj[prop]?.jsonArray ?: return emptyList()
    return arr.mapNotNull { item ->
      runCatching {
        item.jsonObject["mainsnak"]?.jsonObject
          ?.get("datavalue")?.jsonObject
          ?.get("value")?.jsonObject
          ?.get("id")?.jsonPrimitive?.contentOrNull
      }.getOrNull()
    }
  }

  private fun extractClaimStringValue(claimsObj: JsonObject, prop: String): String? {
    val arr: JsonArray = claimsObj[prop]?.jsonArray ?: return null
    for (item in arr) {
      val v =
        runCatching {
          item.jsonObject["mainsnak"]?.jsonObject
            ?.get("datavalue")?.jsonObject
            ?.get("value")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
      if (!v.isNullOrBlank()) return v.trim()
    }
    return null
  }

  private suspend fun executeSparql(
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
        val response = client.executeTextCall(request)
        if (response.code == 429) {
          return SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.RATE_LIMITED))
        }
        val body = response.body
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
      .getOrElse { err ->
        if (err is CancellationException) throw err
        val msg = if (err is SocketTimeoutException) "Timeout: ${err.message ?: "socket read"}" else err.message
        SourceAdapterResult(
          SourceQueryStatus(source, ProviderOutcomeState.ERROR, message = msg)
        )
      }
  }

  private fun isQid(value: String): Boolean = QID_REGEX.matches(value.trim())

  private fun formatHyphenated13Fallback(isbn13: String): String? {
    if (isbn13.length != 13) return null
    return null
  }

  private companion object {
    const val BRIDGE_TIMEOUT_MS = 2_200L
    val QID_REGEX = Regex("^Q\\d+$")
    val BOOK_ENTITY_P31_IDS =
      setOf(
        "Q7725634",  // literary work
        "Q571",      // book
        "Q47461344", // written work
        "Q8261",     // novel
        "Q25379",    // play
        "Q17518461", // novella
        "Q277759",   // book series
        "Q1667921",  // novel series
        "Q21198342", // manga series
        "Q1004",     // comics
        "Q725377",   // trilogy
        "Q3331189",  // version, edition or translation
      )
    val NON_BOOK_ENTITY_P31_IDS =
      setOf(
        "Q5",       // human
        "Q11424",   // film
        "Q5398426", // television series
        "Q7889",    // video game
        "Q482994",  // album
        "Q215380",  // musical group
      )
  }
}
