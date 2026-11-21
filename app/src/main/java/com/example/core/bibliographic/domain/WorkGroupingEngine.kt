package com.example.core.bibliographic.domain

import com.example.core.model.BibliographicMath
import com.example.core.model.EditionFormat
import com.example.core.model.LanguageConfidence
import com.example.core.model.LanguageTier
import com.example.core.model.MatchTier
import com.example.core.model.MetadataSource
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import com.example.core.model.TitleBundleKind
import com.example.core.model.TitleStructure
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

data class GroupedSearchOutcome(
  val primaryGroups: List<SearchWorkGroup>,
  val secondaryGroups: List<SearchWorkGroup>,
  val derivativeGroups: List<SearchWorkGroup> = emptyList(),
)

object WorkGroupingEngine {

  private data class EvaluatedCandidate(
    val candidate: SearchEditionCandidate,
    val evaluation: CandidateMatchEvaluation,
    val titleStructure: TitleStructure,
  )

  private data class RankedGroupKey(
    val group: SearchWorkGroup,
    val matchTierRank: Int,
    val identityMatchBucket: Int,
    val hasCanonicalAuthorConsensus: Boolean,
    val independentSourcesCount: Int,
    val bundleRankOrder: Int,
    val volumeNumber: Int,
    val languageTierRank: Int,
    val workPopularityBucket: Int,
    val maxAuthorScore: Int,
    val bestEditionQualityScore: Int,
  )

  fun groupCandidates(
    rawCandidates: List<SearchEditionCandidate>,
    preferredLanguage: String? = null,
    parsedIntent: ParsedSearchIntent? = null,
  ): List<SearchWorkGroup> {
    val separated = groupSeparated(rawCandidates, preferredLanguage, parsedIntent)
    return separated.primaryGroups + separated.secondaryGroups + separated.derivativeGroups
  }

  fun groupSeparated(
    rawCandidates: List<SearchEditionCandidate>,
    preferredLanguage: String? = null,
    parsedIntent: ParsedSearchIntent? = null,
  ): GroupedSearchOutcome {
    if (rawCandidates.isEmpty()) return GroupedSearchOutcome(emptyList(), emptyList(), emptyList())

    val explicitRequestedLang = parsedIntent?.explicitLanguageRequest
    val sanitizedCandidates = mutableListOf<SearchEditionCandidate>()

    for (raw in rawCandidates) {
      val cleanTitle = BibliographicMath.sanitizeDisplayText(raw.workTitle) ?: continue
      val parsed13 = BibliographicMath.parseIsbn(raw.isbn13)
      val parsed10 = BibliographicMath.parseIsbn(raw.isbn10)
      val isbn13 = parsed13?.isbn13 ?: parsed10?.isbn13
      val isbn10 = parsed10?.isbn10 ?: parsed13?.isbn10

      val langAssessment =
        if (raw.languageConfidenceName == null || explicitRequestedLang != null) {
          BibliographicMath.assessLanguage(
            explicitLanguageRaw = raw.canonicalLanguageTag ?: raw.language,
            isbn13Or10 = isbn13 ?: isbn10,
            publisher = raw.publisher,
            editionTitle = raw.editionTitle ?: cleanTitle,
            explicitRequestedLanguage = explicitRequestedLang,
          )
        } else {
          null
        }

      val normLang = langAssessment?.normalizedCode ?: BibliographicMath.normalizeLanguageCode(raw.language)
      val canonicalLang = langAssessment?.canonicalTag ?: raw.canonicalLanguageTag ?: normLang
      val tierRank = langAssessment?.tier?.rank ?: raw.languageTierRank
      val confName = langAssessment?.confidence?.name ?: raw.languageConfidenceName ?: LanguageConfidence.UNCERTAIN.name
      val hasConflict = langAssessment?.hasConflict ?: raw.hasLanguageConflict

      val covers = CoverRanker.rankAndDeduplicate(raw.coverCandidates)
      val synopses = SynopsisSelector.rankAndDeduplicate(raw.synopsisCandidates, preferredLanguage, normLang)

      val cleaned =
        raw.copy(
          workTitle = cleanTitle,
          subtitle = BibliographicMath.sanitizeDisplayText(raw.subtitle),
          originalTitle = BibliographicMath.sanitizeDisplayText(raw.originalTitle),
          editionTitle = BibliographicMath.sanitizeDisplayText(raw.editionTitle),
          publisher = BibliographicMath.sanitizeDisplayText(raw.publisher),
          isbn13 = isbn13,
          isbn10 = isbn10,
          language = normLang,
          canonicalLanguageTag = canonicalLang,
          languageTierRank = tierRank,
          languageConfidenceName = confName,
          hasLanguageConflict = hasConflict,
          coverCandidates = covers,
          synopsisCandidates = synopses,
        )
      sanitizedCandidates.add(cleaned)
    }

    if (sanitizedCandidates.isEmpty()) return GroupedSearchOutcome(emptyList(), emptyList(), emptyList())

    // Etapas C & D: Avaliação em lote com consenso de autor canônico e ponte bilíngue
    val classified = RelevanceAndDerivativeFilter.classifyBatch(sanitizedCandidates, parsedIntent)
    val acceptedItems =
      classified.mapNotNull { (cand, eval) ->
        if (!eval.accepted) return@mapNotNull null
        val enriched =
          cand.copy(
            queryMatchScore = maxOf(cand.queryMatchScore, eval.queryMatchScore),
            reliabilityScore = computeEditionReliability(cand, preferredLanguage),
          )
        val primaryDisplayForStruct =
          enriched.editionTitle?.takeIf {
            eval.matchedField == MatchedTitleField.EDITION_TITLE &&
              enriched.languageTierRank <= LanguageTier.PT_UNCERTAIN.rank
          } ?: enriched.workTitle
        val struct = BibliographicMath.extractTitleStructure(primaryDisplayForStruct, enriched.subtitle)
        EvaluatedCandidate(enriched, eval, struct)
      }

    if (acceptedItems.isEmpty()) return GroupedSearchOutcome(emptyList(), emptyList(), emptyList())

    val isPredominantlyAuthor =
      RelevanceAndDerivativeFilter.isPredominantlyAuthorQuery(
        evaluations = acceptedItems.map { it.evaluation },
        intent = parsedIntent,
      )

    // Passo E1: Deduplicação de Editions (isolando literárias vs derivadas)
    val literaryMerged = deduplicateEditions(acceptedItems.filter { !it.evaluation.isAcademicOrDerivative && it.evaluation.matchTier != MatchTier.DERIVATIVE_OR_ACADEMIC }, preferredLanguage)
    val derivativeMerged = deduplicateEditions(acceptedItems.filter { it.evaluation.isAcademicOrDerivative || it.evaluation.matchTier == MatchTier.DERIVATIVE_OR_ACADEMIC }, preferredLanguage)

    // Passo E2 & F: Consolidação em Works com barreiras estruturais e ordenação lexicográfica
    val literaryRanked =
      buildAndRankWorkBuckets(
        items = literaryMerged,
        preferredLanguage = preferredLanguage,
        parsedIntent = parsedIntent,
        isPredominantlyAuthor = isPredominantlyAuthor,
        isDerivativeClass = false,
      )
    val derivativeRanked =
      buildAndRankWorkBuckets(
        items = derivativeMerged,
        preferredLanguage = preferredLanguage,
        parsedIntent = parsedIntent,
        isPredominantlyAuthor = isPredominantlyAuthor,
        isDerivativeClass = true,
      )

    val primaryCutoffRank = LanguageTier.EN.rank
    val primaryGroups = literaryRanked.filter { it.primaryLanguageTierRank <= primaryCutoffRank }
    val secondaryGroups = literaryRanked.filter { it.primaryLanguageTierRank > primaryCutoffRank }

    return GroupedSearchOutcome(
      primaryGroups = primaryGroups,
      secondaryGroups = secondaryGroups,
      derivativeGroups = derivativeRanked,
    )
  }

  private fun deduplicateEditions(
    items: List<EvaluatedCandidate>,
    preferredLanguage: String?,
  ): List<EvaluatedCandidate> {
    if (items.isEmpty()) return emptyList()
    val mergedEditions = mutableListOf<EvaluatedCandidate>()
    val isbn13Index = HashMap<String, Int>()
    val isbn10Index = HashMap<String, Int>()
    val extIdIndex = HashMap<String, Int>()
    val noIsbnBuckets = HashMap<String, MutableList<Int>>()

    for (item in items) {
      val cand = item.candidate
      var matchIdx = -1
      if (cand.isbn13 != null) {
        matchIdx = isbn13Index[cand.isbn13] ?: -1
      }
      if (matchIdx < 0 && cand.isbn10 != null) {
        matchIdx = isbn10Index[cand.isbn10] ?: -1
      }
      if (matchIdx < 0) {
        for (idKey in listOf("openlibrary_edition", "google_books_id", "apple_books_track_id")) {
          val extVal = cand.externalIds[idKey] ?: continue
          val hit = extIdIndex["$idKey:$extVal"]
          if (hit != null) {
            matchIdx = hit
            break
          }
        }
      }
      if (matchIdx < 0 && cand.isbn13 == null && cand.isbn10 == null && cand.publicationYear != null && !cand.publisher.isNullOrBlank()) {
        val bucketKey = "${item.titleStructure.coreNormalized}|${cand.publicationYear}"
        val bucket = noIsbnBuckets[bucketKey]
        if (bucket != null) {
          matchIdx = bucket.firstOrNull { idx -> isSameEditionWithoutIsbn(mergedEditions[idx].candidate, cand) } ?: -1
        }
      }

      if (matchIdx >= 0) {
        val prev = mergedEditions[matchIdx]
        val mergedCand = mergeEditions(prev.candidate, cand, preferredLanguage)
        val mergedEval = combineEvaluations(prev.evaluation, item.evaluation)
        val mergedStruct =
          if (prev.evaluation.matchTier.rank <= item.evaluation.matchTier.rank) prev.titleStructure else item.titleStructure
        mergedEditions[matchIdx] = EvaluatedCandidate(mergedCand, mergedEval, mergedStruct)
        mergedCand.isbn13?.let { isbn13Index.putIfAbsent(it, matchIdx) }
        mergedCand.isbn10?.let { isbn10Index.putIfAbsent(it, matchIdx) }
        for (idKey in listOf("openlibrary_edition", "google_books_id", "apple_books_track_id")) {
          mergedCand.externalIds[idKey]?.let { extIdIndex.putIfAbsent("$idKey:$it", matchIdx) }
        }
      } else {
        val newIdx = mergedEditions.size
        mergedEditions.add(item)
        cand.isbn13?.let { isbn13Index[it] = newIdx }
        cand.isbn10?.let { isbn10Index[it] = newIdx }
        for (idKey in listOf("openlibrary_edition", "google_books_id", "apple_books_track_id")) {
          cand.externalIds[idKey]?.let { extIdIndex["$idKey:$it"] = newIdx }
        }
        if (cand.isbn13 == null && cand.isbn10 == null && cand.publicationYear != null && !cand.publisher.isNullOrBlank()) {
          val bucketKey = "${item.titleStructure.coreNormalized}|${cand.publicationYear}"
          noIsbnBuckets.getOrPut(bucketKey) { mutableListOf() }.add(newIdx)
        }
      }
    }
    return mergedEditions
  }

  private fun buildAndRankWorkBuckets(
    items: List<EvaluatedCandidate>,
    preferredLanguage: String?,
    parsedIntent: ParsedSearchIntent?,
    isPredominantlyAuthor: Boolean,
    isDerivativeClass: Boolean,
  ): List<SearchWorkGroup> {
    if (items.isEmpty()) return emptyList()

    val workBuckets = mutableListOf<MutableList<EvaluatedCandidate>>()
    val workKeyToBucket = HashMap<String, MutableList<Int>>()
    val wikidataToBucket = HashMap<String, MutableList<Int>>()
    val coreTitleToBuckets = HashMap<String, MutableList<Int>>()
    val surnameToBuckets = HashMap<String, MutableList<Int>>()

    for (item in items) {
      val cand = item.candidate
      var matchedBucket = -1
      val checked = HashSet<Int>()

      val wKey = cand.externalWorkKey
      if (wKey != null) {
        val buckets = workKeyToBucket[wKey].orEmpty()
        matchedBucket =
          buckets.firstOrNull { bIdx ->
            checked.add(bIdx)
            workBuckets[bIdx].any { member -> belongsToSameWork(member, item) }
          } ?: -1
      }

      val wdKey = cand.externalIds["wikidata_work"]
      if (matchedBucket < 0 && wdKey != null) {
        val buckets = wikidataToBucket[wdKey].orEmpty()
        matchedBucket =
          buckets.firstOrNull { bIdx ->
            if (!checked.add(bIdx)) false
            else workBuckets[bIdx].any { member -> belongsToSameWork(member, item) }
          } ?: -1
      }

      if (matchedBucket < 0) {
        val candidateIndices = linkedSetOf<Int>()
        extractTitleLookupKeys(cand, item.titleStructure).forEach { key ->
          coreTitleToBuckets[key]?.let { candidateIndices.addAll(it) }
        }
        val surname = BibliographicMath.normalizeAuthorSurname(cand.primaryAuthorsText)
        if (surname.isNotEmpty()) {
          surnameToBuckets[surname]?.let { candidateIndices.addAll(it) }
        }
        matchedBucket =
          candidateIndices.firstOrNull { bIdx ->
            if (!checked.add(bIdx)) false
            else workBuckets[bIdx].any { member -> belongsToSameWork(member, item) }
          } ?: -1
      }

      val targetIdx =
        if (matchedBucket >= 0) {
          workBuckets[matchedBucket].add(item)
          matchedBucket
        } else {
          val newIdx = workBuckets.size
          workBuckets.add(mutableListOf(item))
          newIdx
        }

      if (wKey != null) workKeyToBucket.getOrPut(wKey) { mutableListOf() }.addDistinct(targetIdx)
      if (wdKey != null) wikidataToBucket.getOrPut(wdKey) { mutableListOf() }.addDistinct(targetIdx)
      extractTitleLookupKeys(cand, item.titleStructure).forEach { key ->
        coreTitleToBuckets.getOrPut(key) { mutableListOf() }.addDistinct(targetIdx)
      }
      val surname = BibliographicMath.normalizeAuthorSurname(cand.primaryAuthorsText)
      if (surname.isNotEmpty()) {
        surnameToBuckets.getOrPut(surname) { mutableListOf() }.addDistinct(targetIdx)
      }
    }

    val rankedKeys =
      workBuckets.map { bucketItems ->
        val sortedItems =
          bucketItems.sortedWith(
            compareBy<EvaluatedCandidate> { it.candidate.languageTierRank }
              .thenByDescending { editionSelectionScore(it.candidate, it.evaluation) }
          )
        val sortedEditions = sortedItems.map { it.candidate }
        val primary = sortedEditions.first()
        val bestTier =
          sortedItems.minByOrNull { it.evaluation.matchTier.rank }?.evaluation?.matchTier
            ?: MatchTier.EXACT_WORK
        val bestBundleKind =
          sortedItems
            .groupingBy { it.titleStructure.bundleKind }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: TitleBundleKind.SINGLE

        val canonicalTitle = pickCanonicalTitle(sortedEditions, preferredLanguage)
        val canonicalNorm = BibliographicMath.normalizeBaseTitle(canonicalTitle)
        val subtitle =
          sortedEditions.firstNotNullOfOrNull { ed ->
            ed.subtitle?.takeIf { s -> s.isNotBlank() && BibliographicMath.normalizeBaseTitle(s) != canonicalNorm }
          }
        val originalTitle = pickOriginalTitle(sortedEditions, canonicalTitle)
        val externalWorkKey = sortedEditions.firstNotNullOfOrNull { it.externalWorkKey }
        val primaryAuthors =
          sortedEditions
            .flatMap { it.contributors }
            .filter { it.role.isPrimaryWorkRole }
            .distinctBy { BibliographicMath.normalizeText(it.name) }
            .ifEmpty {
              sortedEditions
                .flatMap { it.contributors }
                .distinctBy { BibliographicMath.normalizeText(it.name) }
                .take(2)
            }
        val allCategories = sortedEditions.flatMap { it.categories }.distinct()
        val suggestedGenreSlug = GenreSuggester.suggestSlug(allCategories, canonicalTitle)
        val allSources = sortedEditions.flatMap { it.sources }.distinct()
        val workPopScore = computeWorkPopularityScore(sortedEditions, allSources)
        val bestEval = sortedItems.maxByOrNull { it.evaluation.queryMatchScore }?.evaluation
        val queryRelevance =
          if (isPredominantlyAuthor) {
            bestEval?.authorMatchScore ?: 0
          } else {
            bestEval?.queryMatchScore ?: 0
          }
        val bestTierRank = sortedEditions.minOfOrNull { it.languageTierRank } ?: LanguageTier.UNKNOWN.rank
        val totalHints =
          sortedEditions.maxOfOrNull { it.workEditionCount }?.takeIf { it > sortedEditions.size }
        val repStruct = sortedItems.first().titleStructure

        val deterministicGroupId =
          buildDeterministicGroupId(
            externalWorkKey = externalWorkKey,
            canonicalTitle = canonicalTitle,
            subtitle = subtitle,
            authorName = primaryAuthors.firstOrNull()?.name,
            volumeNumber = repStruct.volumeNumber,
            bundleKind = bestBundleKind,
            isDerivative = isDerivativeClass,
          )

        val group =
          SearchWorkGroup(
            groupId = deterministicGroupId,
            canonicalTitle = canonicalTitle,
            subtitle = subtitle,
            originalTitle = originalTitle,
            primaryAuthors = primaryAuthors,
            externalWorkKey = externalWorkKey,
            suggestedGenreSlug = suggestedGenreSlug,
            editions = sortedEditions,
            sources = allSources,
            localMatchedWorkId = primary.localExistingWorkId,
            workPopularityScore = workPopScore,
            queryRelevanceScore = queryRelevance,
            primaryLanguageTierRank = bestTierRank,
            totalAvailableEditionsHint = totalHints,
            matchTierName = bestTier.name,
            bundleKindName = bestBundleKind.name,
          )

        val bundleOrder =
          when (bestBundleKind) {
            TitleBundleKind.SINGLE -> 0
            TitleBundleKind.OMNIBUS_SINGLE_VOLUME -> 1
            TitleBundleKind.SERIES_VOLUME -> 2
            TitleBundleKind.COLLECTION_OR_BOX -> 3
          }
        val popBucket = computePopularityBucket(workPopScore)
        val bestEditionQuality = sortedItems.maxOfOrNull { editionSelectionScore(it.candidate, it.evaluation) } ?: 0

        RankedGroupKey(
          group = group,
          matchTierRank = bestTier.rank,
          identityMatchBucket = sortedItems.maxOfOrNull { it.evaluation.identityMatchBucket } ?: 0,
          hasCanonicalAuthorConsensus = sortedItems.any { it.evaluation.hasCanonicalAuthorConsensus },
          independentSourcesCount = allSources.distinct().size.coerceAtMost(3),
          bundleRankOrder = bundleOrder,
          volumeNumber = repStruct.volumeNumber ?: 0,
          languageTierRank = bestTierRank,
          workPopularityBucket = popBucket,
          maxAuthorScore = sortedItems.maxOfOrNull { it.evaluation.authorMatchScore } ?: 0,
          bestEditionQualityScore = bestEditionQuality,
        )
      }

    // Passo F: Comparador Lexicográfico Hierárquico (Identidade e Autor Canônico > Popularidade/Completude)
    val comparator =
      if (isPredominantlyAuthor) {
        Comparator<RankedGroupKey> { a, b ->
          compareValues(a.matchTierRank, b.matchTierRank).takeIf { it != 0 }?.let { return@Comparator it }
          compareValues(b.maxAuthorScore / 15, a.maxAuthorScore / 15).takeIf { it != 0 }?.let { return@Comparator it }
          compareValues(b.workPopularityBucket, a.workPopularityBucket).takeIf { it != 0 }?.let { return@Comparator it }
          compareValues(a.languageTierRank, b.languageTierRank).takeIf { it != 0 }?.let { return@Comparator it }
          compareValues(b.bestEditionQualityScore, a.bestEditionQualityScore)
        }
      } else {
        Comparator<RankedGroupKey> { a, b ->
          compareValues(a.matchTierRank, b.matchTierRank).takeIf { it != 0 }?.let { return@Comparator it }
          compareValues(b.identityMatchBucket, a.identityMatchBucket).takeIf { it != 0 }?.let { return@Comparator it }
          compareValues(b.hasCanonicalAuthorConsensus, a.hasCanonicalAuthorConsensus).takeIf { it != 0 }?.let { return@Comparator it }
          compareValues(b.independentSourcesCount, a.independentSourcesCount).takeIf { it != 0 }?.let { return@Comparator it }
          compareValues(a.bundleRankOrder, b.bundleRankOrder).takeIf { it != 0 }?.let { return@Comparator it }
          compareValues(a.volumeNumber, b.volumeNumber).takeIf { it != 0 }?.let { return@Comparator it }
          compareValues(a.languageTierRank, b.languageTierRank).takeIf { it != 0 }?.let { return@Comparator it }
          compareValues(b.workPopularityBucket, a.workPopularityBucket).takeIf { it != 0 }?.let { return@Comparator it }
          compareValues(b.bestEditionQualityScore, a.bestEditionQualityScore)
        }
      }

    return rankedKeys.sortedWith(comparator).map { it.group }
  }

  private fun MutableList<Int>.addDistinct(value: Int) {
    if (value !in this) add(value)
  }

  private fun extractTitleLookupKeys(cand: SearchEditionCandidate, struct: TitleStructure): Set<String> =
    buildSet {
      if (struct.coreNormalized.isNotEmpty()) add(struct.coreNormalized)
      cand.editionTitle?.let { BibliographicMath.normalizeTitleCore(it) }?.takeIf { it.isNotEmpty() }?.let { add(it) }
      cand.originalTitle?.let { BibliographicMath.normalizeTitleCore(it) }?.takeIf { it.isNotEmpty() }?.let { add(it) }
      BibliographicMath.normalizeTitleCore(cand.workTitle).takeIf { it.isNotEmpty() }?.let { add(it) }
    }

  private fun combineEvaluations(a: CandidateMatchEvaluation, b: CandidateMatchEvaluation): CandidateMatchEvaluation {
    val bestTier = if (a.matchTier.rank <= b.matchTier.rank) a.matchTier else b.matchTier
    return a.copy(
      queryMatchScore = maxOf(a.queryMatchScore, b.queryMatchScore),
      titleMatchScore = maxOf(a.titleMatchScore, b.titleMatchScore),
      authorMatchScore = maxOf(a.authorMatchScore, b.authorMatchScore),
      significantTokenCoverage = maxOf(a.significantTokenCoverage, b.significantTokenCoverage),
      matchTier = bestTier,
      identityMatchBucket = maxOf(a.identityMatchBucket, b.identityMatchBucket),
      isAcademicOrDerivative = a.isAcademicOrDerivative || b.isAcademicOrDerivative,
      hasCanonicalAuthorConsensus = a.hasCanonicalAuthorConsensus || b.hasCanonicalAuthorConsensus,
    )
  }

  private fun buildDeterministicGroupId(
    externalWorkKey: String?,
    canonicalTitle: String,
    subtitle: String?,
    authorName: String?,
    volumeNumber: Int?,
    bundleKind: TitleBundleKind,
    isDerivative: Boolean,
  ): String {
    val rawKey =
      buildString {
        append(externalWorkKey ?: BibliographicMath.normalizeBaseTitle(canonicalTitle))
        append('|')
        append(BibliographicMath.normalizeText(subtitle).take(40))
        append('|')
        append(BibliographicMath.normalizeAuthorSurname(authorName))
        append('|')
        append(volumeNumber ?: 0)
        append('|')
        append(bundleKind.name)
        append('|')
        append(if (isDerivative) "T7" else "W")
      }
    val digest = MessageDigest.getInstance("SHA-1").digest(rawKey.toByteArray(Charsets.UTF_8))
    val shortHex = digest.take(8).joinToString("") { "%02x".format(it) }
    val cleanSlug = BibliographicMath.normalizeTitleCore(canonicalTitle).replace(' ', '_').take(24)
    return "wg_${cleanSlug}_$shortHex"
  }

  private fun isSameEditionWithoutIsbn(a: SearchEditionCandidate, b: SearchEditionCandidate): Boolean {
    if (a.publisher.isNullOrBlank() || b.publisher.isNullOrBlank()) return false
    if (a.publicationYear == null || b.publicationYear == null || a.publicationYear != b.publicationYear) {
      return false
    }
    if (a.totalPages != null && b.totalPages != null) {
      val maxP = maxOf(a.totalPages, b.totalPages).toFloat()
      if (maxP > 0f && abs(a.totalPages - b.totalPages).toFloat() / maxP > 0.08f) return false
    }
    val pubSim = BibliographicMath.tokenSimilarity(a.publisher, b.publisher)
    val titleSim =
      BibliographicMath.tokenSimilarity(
        BibliographicMath.normalizeBaseTitle(a.editionTitle ?: a.workTitle),
        BibliographicMath.normalizeBaseTitle(b.editionTitle ?: b.workTitle),
      )
    val sameLang = (a.canonicalLanguageTag ?: a.language) == (b.canonicalLanguageTag ?: b.language)
    return pubSim >= 0.85f && titleSim >= 0.90f && sameLang
  }

  private fun mergeEditions(
    a: SearchEditionCandidate,
    b: SearchEditionCandidate,
    preferredLanguage: String?,
  ): SearchEditionCandidate {
    val primary = if (a.reliabilityScore >= b.reliabilityScore) a else b
    val secondary = if (primary === a) b else a

    val mergedContributors =
      (primary.contributors + secondary.contributors).distinctBy {
        "${BibliographicMath.normalizeText(it.name)}_${it.role}"
      }
    val mergedCovers = CoverRanker.rankAndDeduplicate(primary.coverCandidates + secondary.coverCandidates)
    val primaryHasReliableLang =
      primary.languageConfidenceName == LanguageConfidence.CONFIRMED.name && !primary.hasLanguageConflict
    val langDonor =
      if (primaryHasReliableLang || primary.language != null) primary else secondary
    val mergedLanguage = langDonor.language ?: secondary.language
    val mergedSynopses =
      SynopsisSelector.rankAndDeduplicate(
        primary.synopsisCandidates + secondary.synopsisCandidates,
        preferredLanguage,
        mergedLanguage,
      )
    val mergedSources = (primary.sources + secondary.sources).distinct()
    val mergedExternalIds = secondary.externalIds + primary.externalIds
    val mergedCategories = (primary.categories + secondary.categories).distinct()
    val mergedWorkLangs = (primary.availableWorkLanguages + secondary.availableWorkLanguages).distinct()

    val merged =
      primary.copy(
        subtitle = primary.subtitle ?: secondary.subtitle,
        originalTitle = primary.originalTitle ?: secondary.originalTitle,
        editionTitle = primary.editionTitle ?: secondary.editionTitle,
        contributors = mergedContributors,
        publisher = primary.publisher ?: secondary.publisher,
        publicationYear = primary.publicationYear ?: secondary.publicationYear,
        totalPages = primary.totalPages ?: secondary.totalPages,
        isbn10 = primary.isbn10 ?: secondary.isbn10,
        isbn13 = primary.isbn13 ?: secondary.isbn13,
        language = mergedLanguage,
        canonicalLanguageTag = langDonor.canonicalLanguageTag ?: secondary.canonicalLanguageTag,
        languageTierRank = minOf(primary.languageTierRank, secondary.languageTierRank).let {
          if (primaryHasReliableLang) primary.languageTierRank else langDonor.languageTierRank
        },
        languageConfidenceName = langDonor.languageConfidenceName ?: secondary.languageConfidenceName,
        hasLanguageConflict = if (primaryHasReliableLang) primary.hasLanguageConflict else langDonor.hasLanguageConflict,
        format = primary.format ?: secondary.format,
        editionLabel = primary.editionLabel ?: secondary.editionLabel,
        seriesName = primary.seriesName ?: secondary.seriesName,
        seriesVolume = primary.seriesVolume ?: secondary.seriesVolume,
        categories = mergedCategories,
        coverCandidates = mergedCovers,
        synopsisCandidates = mergedSynopses,
        externalWorkKey = primary.externalWorkKey ?: secondary.externalWorkKey,
        externalIds = mergedExternalIds,
        sources = mergedSources,
        queryMatchScore = maxOf(primary.queryMatchScore, secondary.queryMatchScore),
        workEditionCount = maxOf(primary.workEditionCount, secondary.workEditionCount),
        workReadingLogCount = maxOf(primary.workReadingLogCount, secondary.workReadingLogCount),
        workRatingsCount = maxOf(primary.workRatingsCount, secondary.workRatingsCount),
        availableWorkLanguages = mergedWorkLangs,
      )
    return merged.copy(reliabilityScore = computeEditionReliability(merged, preferredLanguage))
  }

  private fun belongsToSameWork(itemA: EvaluatedCandidate, itemB: EvaluatedCandidate): Boolean {
    val structA = itemA.titleStructure
    val structB = itemB.titleStructure

    // Barreira #1: Derivado/Acadêmico jamais se funde à obra literária
    if (itemA.evaluation.isAcademicOrDerivative != itemB.evaluation.isAcademicOrDerivative) return false

    // Barreira #2: Pacote estrutural (SINGLE vs SERIES_VOLUME vs OMNIBUS_SINGLE_VOLUME vs COLLECTION_OR_BOX)
    if (structA.bundleKind != structB.bundleKind) return false

    // Barreira #3: Volume numérico e Subtítulo temático distinto em séries
    if (structA.volumeNumber != null && structB.volumeNumber != null && structA.volumeNumber != structB.volumeNumber) {
      return false
    }
    if ((structA.volumeNumber ?: 1) > 1 && structB.volumeNumber == null && structA.coreNormalized != structB.coreNormalized) {
      return false
    }
    if ((structB.volumeNumber ?: 1) > 1 && structA.volumeNumber == null && structA.coreNormalized != structB.coreNormalized) {
      return false
    }
    if (structA.subtitleNormalized.isNotEmpty() && structB.subtitleNormalized.isNotEmpty()) {
      val edA = RelevanceAndDerivativeFilter.isEditionOrVolumeSubtitle(structA.subtitleNormalized)
      val edB = RelevanceAndDerivativeFilter.isEditionOrVolumeSubtitle(structB.subtitleNormalized)
      if (!edA && !edB) {
        val subSim = BibliographicMath.tokenSimilarity(structA.subtitleNormalized, structB.subtitleNormalized)
        if (subSim < 0.70f) return false
      }
    }

    val a = itemA.candidate
    val b = itemB.candidate

    // Barreira #4: Autor principal (impede fusão de homônimos como "Coraline" de autores distintos)
    val surnameA = BibliographicMath.normalizeAuthorSurname(a.primaryAuthorsText)
    val surnameB = BibliographicMath.normalizeAuthorSurname(b.primaryAuthorsText)
    val authorCompat =
      if (surnameA.isNotEmpty() && surnameB.isNotEmpty()) {
        BibliographicMath.tokenSimilarity(surnameA, surnameB) >= 0.70f
      } else {
        true
      }
    if (!authorCompat) return false

    // Evidência forte por chave de obra / Wikidata (após passar nas 4 barreiras!)
    if (a.externalWorkKey != null && b.externalWorkKey != null && a.externalWorkKey == b.externalWorkKey) {
      return true
    }
    val wdA = a.externalIds["wikidata_work"]
    val wdB = b.externalIds["wikidata_work"]
    if (wdA != null && wdB != null && wdA == wdB) {
      return true
    }

    // Comparação cruzada considerando título da obra, título traduzido da edição e título original
    val formsA =
      listOfNotNull(
          structA.baseNormalizedWithArticle.takeIf { it.isNotBlank() },
          structA.coreNormalized.takeIf { it.isNotBlank() },
          a.editionTitle?.let { BibliographicMath.normalizeBaseTitle(it) }?.takeIf { it.isNotBlank() },
          a.originalTitle?.let { BibliographicMath.normalizeBaseTitle(it) }?.takeIf { it.isNotBlank() },
        )
        .distinct()
    val formsB =
      listOfNotNull(
          structB.baseNormalizedWithArticle.takeIf { it.isNotBlank() },
          structB.coreNormalized.takeIf { it.isNotBlank() },
          b.editionTitle?.let { BibliographicMath.normalizeBaseTitle(it) }?.takeIf { it.isNotBlank() },
          b.originalTitle?.let { BibliographicMath.normalizeBaseTitle(it) }?.takeIf { it.isNotBlank() },
        )
        .distinct()

    val maxFormSim =
      formsA.maxOfOrNull { fa ->
        formsB.maxOfOrNull { fb -> BibliographicMath.tokenSimilarity(fa, fb) } ?: 0f
      } ?: 0f

    if (surnameA.isEmpty() || surnameB.isEmpty()) {
      return maxFormSim >= 0.94f
    }
    return maxFormSim >= 0.86f
  }

  private fun pickCanonicalTitle(
    editions: List<SearchEditionCandidate>,
    preferredLanguage: String?,
  ): String {
    val wantsPt = BibliographicMath.normalizeLanguageCode(preferredLanguage) != "en"
    val ptEditions =
      editions.filter { it.languageTierRank <= LanguageTier.PT_UNCERTAIN.rank }
    if (wantsPt && ptEditions.isNotEmpty()) {
      // Se alguma edição PT já traz um título localizado em editionTitle (ex: workTitle="The Hunger Games", editionTitle="Jogos Vorazes")
      val ptDisplayCandidates =
        ptEditions.flatMap { ed ->
          val edT = ed.editionTitle?.takeIf { it.isNotBlank() }
          val wT = ed.workTitle
          val orig = ed.originalTitle
          when {
            edT != null && orig != null && BibliographicMath.normalizeBaseTitle(wT) == BibliographicMath.normalizeBaseTitle(orig) ->
              listOf(edT)
            edT != null && BibliographicMath.normalizeBaseTitle(edT) != BibliographicMath.normalizeBaseTitle(wT) &&
              looksPortugueseLike(edT) && !looksPortugueseLike(wT) ->
              listOf(edT)
            else -> listOfNotNull(wT, edT)
          }
        }
      ptDisplayCandidates
        .maxByOrNull { scoreDisplayTitle(it) }
        ?.let { return it }
    }

    val primaryTier =
      ptEditions
        .ifEmpty { editions.filter { it.languageTierRank <= LanguageTier.EN.rank } }
        .ifEmpty { editions }
    return primaryTier
      .map { it.workTitle }
      .maxByOrNull { scoreDisplayTitle(it) }
      ?: editions.first().workTitle
  }

  private fun pickOriginalTitle(
    editions: List<SearchEditionCandidate>,
    canonicalTitle: String,
  ): String? {
    val canonicalNorm = BibliographicMath.normalizeBaseTitle(canonicalTitle)
    editions
      .firstNotNullOfOrNull { it.originalTitle?.takeIf { o -> o.isNotBlank() && BibliographicMath.normalizeBaseTitle(o) != canonicalNorm } }
      ?.let { return it }

    return editions
      .firstOrNull { ed ->
        val wNorm = BibliographicMath.normalizeBaseTitle(ed.workTitle)
        wNorm.isNotBlank() && wNorm != canonicalNorm &&
          (ed.language == "en" || ed.language == "fr" || ed.language == "es" || !looksPortugueseLike(ed.workTitle))
      }
      ?.workTitle
  }

  private fun scoreDisplayTitle(title: String): Int {
    var score = 0
    if (title != title.uppercase(Locale.ROOT)) score += 10
    if (':' !in title) score += 3
    if (looksPortugueseLike(title)) score += 6
    score -= (title.length / 45)
    return score
  }

  private fun looksPortugueseLike(title: String): Boolean {
    val lower = title.lowercase(Locale.ROOT)
    return Regex("[ãõçáéíóúâêôà]").containsMatchIn(lower) ||
      Regex("\\b(dos|das|uma|um|jogos|vorazes|senhor|aneis|principe|pequeno|gato|memorias|casmurro)\\b")
        .containsMatchIn(BibliographicMath.normalizeText(lower))
  }

  private fun editionSelectionScore(
    cand: SearchEditionCandidate,
    eval: CandidateMatchEvaluation,
  ): Int {
    var score = cand.reliabilityScore
    score += eval.queryMatchScore
    val isbn = cand.isbn13 ?: cand.isbn10
    if (BibliographicMath.isBrazilianIsbn(isbn)) score += 24
    if (BibliographicMath.isKnownBrazilianPublisher(cand.publisher)) score += 18
    if (cand.bestCover != null) {
      score += 10
      if (cand.bestCover!!.matchesIsbn) score += 8
    }
    if (cand.hasLanguageConflict) score -= 18
    return score
  }

  private fun computeWorkPopularityScore(
    editions: List<SearchEditionCandidate>,
    allSources: List<MetadataSource>,
  ): Int {
    val maxEdCount = editions.maxOfOrNull { it.workEditionCount } ?: 0
    val maxReadingLog = editions.maxOfOrNull { it.workReadingLogCount } ?: 0
    val maxRatings = editions.maxOfOrNull { it.workRatingsCount } ?: 0
    val editionSignals = (ln((maxEdCount + 1).toDouble()) * 8.0).toInt().coerceIn(0, 42)
    val readingLogSignals = (ln((maxReadingLog + 1).toDouble()) * 7.5).toInt().coerceIn(0, 48)
    val ratingSignals = (ln((maxRatings + 1).toDouble()) * 4.0).toInt().coerceIn(0, 18)
    val groupedBonus = (editions.size.coerceAtMost(8) * 3) + ((allSources.size - 1).coerceAtLeast(0) * 14)
    return editionSignals + readingLogSignals + ratingSignals + groupedBonus
  }

  private fun computePopularityBucket(workPopularityScore: Int): Int =
    when {
      workPopularityScore >= 85 -> 6
      workPopularityScore >= 60 -> 5
      workPopularityScore >= 40 -> 4
      workPopularityScore >= 24 -> 3
      workPopularityScore >= 12 -> 2
      workPopularityScore >= 4 -> 1
      else -> 0
    }

  fun computeEditionReliability(
    candidate: SearchEditionCandidate,
    preferredLanguage: String?,
  ): Int {
    var score = 0
    if (candidate.isbn13 != null) score += 25
    else if (candidate.isbn10 != null) score += 18
    if (!candidate.publisher.isNullOrBlank()) score += 10
    if (candidate.publicationYear != null) score += 8
    if (candidate.totalPages != null) score += 8
    if (candidate.contributors.isNotEmpty()) score += 12
    if (candidate.coverCandidates.isNotEmpty()) score += 14
    if (candidate.synopsisCandidates.isNotEmpty()) score += 12
    if (!candidate.editionLabel.isNullOrBlank()) score += 5
    if (candidate.format != null && candidate.format != EditionFormat.OTHER) score += 4

    val normPref = BibliographicMath.normalizeLanguageCode(preferredLanguage)
    if (normPref != null && candidate.language == normPref) {
      score += 18
    }

    score += (candidate.sources.size - 1).coerceAtLeast(0) * 14
    if (MetadataSource.OPEN_LIBRARY in candidate.sources) score += 5
    if (MetadataSource.GOOGLE_BOOKS in candidate.sources) score += 5
    if (MetadataSource.BRASIL_API in candidate.sources) score += 6
    if (MetadataSource.APPLE_BOOKS in candidate.sources) score += 4
    return score
  }
}
