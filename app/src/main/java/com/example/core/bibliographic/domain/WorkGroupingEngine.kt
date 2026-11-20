package com.example.core.bibliographic.domain

import com.example.core.model.BibliographicMath
import com.example.core.model.EditionFormat
import com.example.core.model.LanguageConfidence
import com.example.core.model.LanguageTier
import com.example.core.model.MetadataSource
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import com.example.core.model.TitleStructure
import java.util.Locale
import kotlin.math.ln

data class GroupedSearchOutcome(
  val primaryGroups: List<SearchWorkGroup>,
  val secondaryGroups: List<SearchWorkGroup>,
)

object WorkGroupingEngine {

  private data class EvaluatedCandidate(
    val candidate: SearchEditionCandidate,
    val evaluation: CandidateMatchEvaluation,
    val titleStructure: TitleStructure,
  )

  fun groupCandidates(
    rawCandidates: List<SearchEditionCandidate>,
    preferredLanguage: String? = null,
    parsedIntent: ParsedSearchIntent? = null,
  ): List<SearchWorkGroup> {
    val separated = groupSeparated(rawCandidates, preferredLanguage, parsedIntent)
    return separated.primaryGroups + separated.secondaryGroups
  }

  fun groupSeparated(
    rawCandidates: List<SearchEditionCandidate>,
    preferredLanguage: String? = null,
    parsedIntent: ParsedSearchIntent? = null,
  ): GroupedSearchOutcome {
    if (rawCandidates.isEmpty()) return GroupedSearchOutcome(emptyList(), emptyList())

    val explicitRequestedLang = parsedIntent?.explicitLanguageRequest
    val evaluatedList = mutableListOf<EvaluatedCandidate>()

    for (raw in rawCandidates) {
      val cleanTitle = BibliographicMath.sanitizeDisplayText(raw.workTitle) ?: continue
      val parsed13 = BibliographicMath.parseIsbn(raw.isbn13)
      val parsed10 = BibliographicMath.parseIsbn(raw.isbn10)
      val isbn13 = parsed13?.isbn13 ?: parsed10?.isbn13
      val isbn10 = parsed10?.isbn10 ?: parsed13?.isbn10

      // Preserve adapter language classification when already assessed, or re-assess for synthetic/legacy inputs or explicit language request
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

      var cleaned =
        raw.copy(
          workTitle = cleanTitle,
          subtitle = BibliographicMath.sanitizeDisplayText(raw.subtitle),
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

      val eval = RelevanceAndDerivativeFilter.evaluateCandidate(cleaned, parsedIntent)
      if (!eval.accepted) continue

      cleaned =
        cleaned.copy(
          queryMatchScore = maxOf(cleaned.queryMatchScore, eval.queryMatchScore),
          reliabilityScore = computeEditionReliability(cleaned, preferredLanguage),
        )
      val struct = BibliographicMath.extractTitleStructure(cleanTitle, cleaned.subtitle)
      evaluatedList.add(EvaluatedCandidate(cleaned, eval, struct))
    }

    if (evaluatedList.isEmpty()) return GroupedSearchOutcome(emptyList(), emptyList())

    val isPredominantlyAuthor =
      RelevanceAndDerivativeFilter.isPredominantlyAuthorQuery(
        evaluations = evaluatedList.map { it.evaluation },
        intent = parsedIntent,
      )

    // Step 1: O(1) indexed deduplication of identical Editions
    val mergedEditions = mutableListOf<EvaluatedCandidate>()
    val isbn13Index = HashMap<String, Int>()
    val isbn10Index = HashMap<String, Int>()
    val noIsbnBuckets = HashMap<String, MutableList<Int>>()

    for (item in evaluatedList) {
      val cand = item.candidate
      var matchIdx = -1
      if (cand.isbn13 != null) {
        matchIdx = isbn13Index[cand.isbn13] ?: -1
      }
      if (matchIdx < 0 && cand.isbn10 != null) {
        matchIdx = isbn10Index[cand.isbn10] ?: -1
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
        val mergedEval =
          if (prev.evaluation.queryMatchScore >= item.evaluation.queryMatchScore) prev.evaluation else item.evaluation
        val mergedItem = prev.copy(candidate = mergedCand, evaluation = mergedEval)
        mergedEditions[matchIdx] = mergedItem
        mergedCand.isbn13?.let { isbn13Index.putIfAbsent(it, matchIdx) }
        mergedCand.isbn10?.let { isbn10Index.putIfAbsent(it, matchIdx) }
      } else {
        val newIdx = mergedEditions.size
        mergedEditions.add(item)
        cand.isbn13?.let { isbn13Index[it] = newIdx }
        cand.isbn10?.let { isbn10Index[it] = newIdx }
        if (cand.isbn13 == null && cand.isbn10 == null && cand.publicationYear != null && !cand.publisher.isNullOrBlank()) {
          val bucketKey = "${item.titleStructure.coreNormalized}|${cand.publicationYear}"
          noIsbnBuckets.getOrPut(bucketKey) { mutableListOf() }.add(newIdx)
        }
      }
    }

    // Step 2: Indexed Work grouping with strict Volume & Collection/Box separation guard
    val workBuckets = mutableListOf<MutableList<EvaluatedCandidate>>()
    val workKeyToBucket = HashMap<String, Int>()
    val wikidataToBucket = HashMap<String, Int>()
    val coreTitleToBuckets = HashMap<String, MutableList<Int>>()
    val surnameToBuckets = HashMap<String, MutableList<Int>>()

    for (item in mergedEditions) {
      val cand = item.candidate
      var matchedBucket = -1

      // Check work key index with volume/bundle guard
      val wKey = cand.externalWorkKey
      if (wKey != null) {
        val candidateBucketIdx = workKeyToBucket[wKey]
        if (candidateBucketIdx != null &&
          workBuckets[candidateBucketIdx].any { member -> belongsToSameWork(member, item) }
        ) {
          matchedBucket = candidateBucketIdx
        }
      }

      // Check Wikidata index with guard
      val wdKey = cand.externalIds["wikidata_work"]
      if (matchedBucket < 0 && wdKey != null) {
        val candidateBucketIdx = wikidataToBucket[wdKey]
        if (candidateBucketIdx != null &&
          workBuckets[candidateBucketIdx].any { member -> belongsToSameWork(member, item) }
        ) {
          matchedBucket = candidateBucketIdx
        }
      }

      // Check small candidate buckets sharing normalized title core or first significant word or author surname
      if (matchedBucket < 0) {
        val candidateIndices = linkedSetOf<Int>()
        coreTitleToBuckets[item.titleStructure.coreNormalized]?.let { candidateIndices.addAll(it) }
        val surname = BibliographicMath.normalizeAuthorSurname(cand.primaryAuthorsText)
        if (surname.isNotEmpty()) {
          surnameToBuckets[surname]?.let { candidateIndices.addAll(it) }
        }
        matchedBucket =
          candidateIndices.firstOrNull { bIdx ->
            workBuckets[bIdx].any { member -> belongsToSameWork(member, item) }
          } ?: -1
      }

      if (matchedBucket >= 0) {
        workBuckets[matchedBucket].add(item)
        if (wKey != null) workKeyToBucket.putIfAbsent(wKey, matchedBucket)
        if (wdKey != null) wikidataToBucket.putIfAbsent(wdKey, matchedBucket)
        val surname = BibliographicMath.normalizeAuthorSurname(cand.primaryAuthorsText)
        if (surname.isNotEmpty()) {
          surnameToBuckets.getOrPut(surname) { mutableListOf() }.add(matchedBucket)
        }
        coreTitleToBuckets.getOrPut(item.titleStructure.coreNormalized) { mutableListOf() }.add(matchedBucket)
      } else {
        val newBucketIdx = workBuckets.size
        workBuckets.add(mutableListOf(item))
        if (wKey != null) workKeyToBucket[wKey] = newBucketIdx
        if (wdKey != null) wikidataToBucket[wdKey] = newBucketIdx
        coreTitleToBuckets.getOrPut(item.titleStructure.coreNormalized) { mutableListOf() }.add(newBucketIdx)
        val surname = BibliographicMath.normalizeAuthorSurname(cand.primaryAuthorsText)
        if (surname.isNotEmpty()) {
          surnameToBuckets.getOrPut(surname) { mutableListOf() }.add(newBucketIdx)
        }
      }
    }

    // Step 3: Build WorkGroups keeping Work popularity strictly separated from Edition selection
    val builtGroups =
      workBuckets.mapIndexed { idx, bucketItems ->
        val sortedItems =
          bucketItems.sortedWith(
            compareBy<EvaluatedCandidate> { it.candidate.languageTierRank }
              .thenByDescending { editionSelectionScore(it.candidate, it.evaluation) }
          )
        val sortedEditions = sortedItems.map { it.candidate }
        val primary = sortedEditions.first()
        val canonicalTitle = pickCanonicalTitle(sortedEditions)
        val subtitle = sortedEditions.firstNotNullOfOrNull { it.subtitle?.takeIf { s -> s.isNotBlank() } }
        val originalTitle = sortedEditions.firstNotNullOfOrNull { it.originalTitle?.takeIf { s -> s.isNotBlank() } }
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
        val groupKey =
          externalWorkKey?.let { "$it:${sortedItems.first().titleStructure.volumeNumber ?: 0}:${sortedItems.first().titleStructure.bundleKind.name}" }
            ?: "${BibliographicMath.normalizeTitleCore(canonicalTitle)}_${BibliographicMath.normalizeAuthorSurname(primaryAuthors.firstOrNull()?.name)}_$idx"

        val group =
          SearchWorkGroup(
            groupId = groupKey,
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
          )
        group to computeWorkRankScore(group, isPredominantlyAuthor, sortedItems)
      }

    val rankedGroups =
      builtGroups
        .sortedByDescending { it.second }
        .map { it.first }

    val primaryCutoffRank = LanguageTier.PT_UNCERTAIN.rank
    val primaryGroups = rankedGroups.filter { it.primaryLanguageTierRank <= primaryCutoffRank }
    val secondaryGroups = rankedGroups.filter { it.primaryLanguageTierRank > primaryCutoffRank }

    return GroupedSearchOutcome(
      primaryGroups = primaryGroups,
      secondaryGroups = secondaryGroups,
    )
  }

  private fun isSameEditionWithoutIsbn(a: SearchEditionCandidate, b: SearchEditionCandidate): Boolean {
    if (a.publisher.isNullOrBlank() || b.publisher.isNullOrBlank()) return false
    if (a.publicationYear == null || b.publicationYear == null || a.publicationYear != b.publicationYear) {
      return false
    }
    val pubSim = BibliographicMath.tokenSimilarity(a.publisher, b.publisher)
    val titleSim =
      BibliographicMath.tokenSimilarity(
        BibliographicMath.normalizeTitleCore(a.workTitle),
        BibliographicMath.normalizeTitleCore(b.workTitle),
      )
    val sameLang = (a.canonicalLanguageTag ?: a.language) == (b.canonicalLanguageTag ?: b.language)
    return pubSim >= 0.85f && titleSim >= 0.88f && sameLang
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

    // Strict structural guards: never merge Box/Trilogy into single volume or Volume 1 into Volume 2
    if (structA.bundleKind != structB.bundleKind) return false
    if (structA.volumeNumber != null && structB.volumeNumber != null && structA.volumeNumber != structB.volumeNumber) {
      return false
    }
    if ((structA.volumeNumber ?: 1) > 1 && structB.volumeNumber == null && structA.coreNormalized != structB.coreNormalized) {
      return false
    }
    if ((structB.volumeNumber ?: 1) > 1 && structA.volumeNumber == null && structA.coreNormalized != structB.coreNormalized) {
      return false
    }

    val a = itemA.candidate
    val b = itemB.candidate
    if (a.externalWorkKey != null && b.externalWorkKey != null && a.externalWorkKey == b.externalWorkKey) {
      return true
    }
    val wdA = a.externalIds["wikidata_work"]
    val wdB = b.externalIds["wikidata_work"]
    if (wdA != null && wdB != null && wdA == wdB) {
      return true
    }

    val coreA = structA.coreNormalized
    val coreB = structB.coreNormalized
    if (coreA.isEmpty() || coreB.isEmpty()) return false
    val titleSim = BibliographicMath.tokenSimilarity(coreA, coreB)
    if (titleSim < 0.84f) return false

    val surnameA = BibliographicMath.normalizeAuthorSurname(a.primaryAuthorsText)
    val surnameB = BibliographicMath.normalizeAuthorSurname(b.primaryAuthorsText)
    if (surnameA.isEmpty() || surnameB.isEmpty()) {
      return titleSim >= 0.94f
    }
    val authorSim = BibliographicMath.tokenSimilarity(surnameA, surnameB)
    return authorSim >= 0.72f
  }

  private fun pickCanonicalTitle(editions: List<SearchEditionCandidate>): String {
    val primaryTier =
      editions.filter { it.languageTierRank <= LanguageTier.PT_BR.rank }
        .ifEmpty { editions.filter { it.languageTierRank <= LanguageTier.EN.rank } }
        .ifEmpty { editions }
    return primaryTier
      .map { it.workTitle }
      .maxByOrNull { title ->
        var score = 0
        if (title != title.uppercase(Locale.ROOT)) score += 10
        if (':' !in title) score += 3
        score - (title.length / 45)
      }
      ?: editions.first().workTitle
  }

  /**
   * Pontuação estrita de seleção da edição principal DENTRO de uma obra.
   * Deliberadamente NÃO utiliza métricas de popularidade da obra como se fossem da edição;
   * prioriza correspondência com a consulta, idioma (pt-BR > en > pt incerto > outros),
   * relevância editorial brasileira, qualidade bibliográfica e capa verificada.
   */
  private fun editionSelectionScore(
    cand: SearchEditionCandidate,
    eval: CandidateMatchEvaluation,
  ): Int {
    var score = cand.reliabilityScore
    score += eval.queryMatchScore
    val isbn = cand.isbn13 ?: cand.isbn10
    if (BibliographicMath.isBrazilianIsbn(isbn)) score += 22
    if (BibliographicMath.isKnownBrazilianPublisher(cand.publisher)) score += 16
    if (cand.bestCover != null) {
      score += 10
      if (cand.bestCover!!.matchesIsbn) score += 8
    }
    if (cand.hasLanguageConflict) score -= 18
    return score
  }

  /**
   * Popularidade da OBRA (editions_count, readinglog_count, ratings_count, fontes concordantes).
   * Usada exclusivamente para ranquear obras entre si, nunca como evidência da edição individual.
   */
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

  private fun computeWorkRankScore(
    group: SearchWorkGroup,
    isPredominantlyAuthor: Boolean,
    items: List<EvaluatedCandidate>,
  ): Int {
    val topEdition = group.editions.firstOrNull()
    val langBonus =
      when {
        group.primaryLanguageTierRank == LanguageTier.EXPLICIT_MATCH.rank -> 55
        group.primaryLanguageTierRank == LanguageTier.PT_BR.rank -> 38
        group.primaryLanguageTierRank == LanguageTier.EN.rank -> 24
        group.primaryLanguageTierRank == LanguageTier.PT_UNCERTAIN.rank -> 14
        else -> 0
      }
    val hasCoverBonus = if (group.previewCoverUrl != null) 12 else 0
    val singleBundleBonus =
      if (items.firstOrNull()?.titleStructure?.bundleKind == com.example.core.model.TitleBundleKind.SINGLE) 8 else 0

    return if (isPredominantlyAuthor) {
      val maxAuthorScore = items.maxOfOrNull { it.evaluation.authorMatchScore } ?: 0
      maxAuthorScore + (group.workPopularityScore * 2) + langBonus + hasCoverBonus + ((topEdition?.reliabilityScore ?: 0) / 3)
    } else {
      group.queryRelevanceScore * 2 +
        group.workPopularityScore +
        langBonus +
        hasCoverBonus +
        singleBundleBonus +
        ((topEdition?.reliabilityScore ?: 0) / 2)
    }
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
    return score
  }
}
