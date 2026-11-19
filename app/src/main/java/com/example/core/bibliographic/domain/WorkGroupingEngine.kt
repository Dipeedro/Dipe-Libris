package com.example.core.bibliographic.domain

import com.example.core.model.BibliographicMath
import com.example.core.model.CandidateContributor
import com.example.core.model.EditionFormat
import com.example.core.model.MetadataSource
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import java.util.Locale

object WorkGroupingEngine {

  fun groupCandidates(
    rawCandidates: List<SearchEditionCandidate>,
    preferredLanguage: String? = null,
  ): List<SearchWorkGroup> {
    if (rawCandidates.isEmpty()) return emptyList()

    val normalizedCandidates =
      rawCandidates.mapNotNull { candidate ->
        val cleanTitle = candidate.workTitle.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val parsed13 = BibliographicMath.parseIsbn(candidate.isbn13)
        val parsed10 = BibliographicMath.parseIsbn(candidate.isbn10)
        val isbn13 = parsed13?.isbn13 ?: parsed10?.isbn13
        val isbn10 = parsed10?.isbn10 ?: parsed13?.isbn10
        val normLang = BibliographicMath.normalizeLanguageCode(candidate.language)
        val covers = CoverRanker.rankAndDeduplicate(candidate.coverCandidates)
        val synopses = SynopsisSelector.rankAndDeduplicate(candidate.synopsisCandidates, preferredLanguage, normLang)
        val cleaned =
          candidate.copy(
            workTitle = cleanTitle,
            isbn13 = isbn13,
            isbn10 = isbn10,
            language = normLang,
            coverCandidates = covers,
            synopsisCandidates = synopses,
          )
        cleaned.copy(reliabilityScore = computeEditionReliability(cleaned, preferredLanguage))
      }

    // Step 1: Merge candidates representing the same Edition (by ISBN or publisher+year+title)
    val mergedEditions = mutableListOf<SearchEditionCandidate>()
    for (candidate in normalizedCandidates) {
      val matchIdx =
        mergedEditions.indexOfFirst { existing -> isSameEdition(existing, candidate) }
      if (matchIdx >= 0) {
        mergedEditions[matchIdx] =
          mergeEditions(mergedEditions[matchIdx], candidate, preferredLanguage)
      } else {
        mergedEditions.add(candidate)
      }
    }

    // Step 2: Group distinct editions into Works
    val workBuckets = mutableListOf<MutableList<SearchEditionCandidate>>()
    for (edition in mergedEditions) {
      val bucketIdx =
        workBuckets.indexOfFirst { bucket ->
          bucket.any { member -> belongsToSameWork(member, edition) }
        }
      if (bucketIdx >= 0) {
        workBuckets[bucketIdx].add(edition)
      } else {
        workBuckets.add(mutableListOf(edition))
      }
    }

    return workBuckets
      .mapIndexed { idx, editions ->
        val sortedEditions = editions.sortedByDescending { it.reliabilityScore }
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
              sortedEditions.flatMap { it.contributors }.distinctBy { BibliographicMath.normalizeText(it.name) }.take(2)
            }
        val allCategories = sortedEditions.flatMap { it.categories }.distinct()
        val suggestedGenreSlug = GenreSuggester.suggestSlug(allCategories, canonicalTitle)
        val allSources = sortedEditions.flatMap { it.sources }.distinct()
        val groupKey =
          externalWorkKey
            ?: "${BibliographicMath.normalizeTitleCore(canonicalTitle)}_${BibliographicMath.normalizeAuthorSurname(primaryAuthors.firstOrNull()?.name)}_$idx"

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
        )
      }
      .sortedByDescending { group ->
        val topScore = group.editions.maxOfOrNull { it.reliabilityScore } ?: 0
        topScore + (group.sources.size * 8)
      }
  }

  private fun isSameEdition(a: SearchEditionCandidate, b: SearchEditionCandidate): Boolean {
    if (a.isbn13 != null && b.isbn13 != null) {
      return a.isbn13 == b.isbn13
    }
    if (a.isbn10 != null && b.isbn10 != null) {
      return a.isbn10 == b.isbn10
    }
    // Without ISBN, only merge if publisher + publicationYear + workTitle strongly match
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
    return pubSim >= 0.85f && titleSim >= 0.88f && a.language == b.language
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
    val mergedLanguage = primary.language ?: secondary.language
    val mergedSynopses =
      SynopsisSelector.rankAndDeduplicate(
        primary.synopsisCandidates + secondary.synopsisCandidates,
        preferredLanguage,
        mergedLanguage,
      )
    val mergedSources = (primary.sources + secondary.sources).distinct()
    val mergedExternalIds = secondary.externalIds + primary.externalIds
    val mergedCategories = (primary.categories + secondary.categories).distinct()

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
      )
    return merged.copy(reliabilityScore = computeEditionReliability(merged, preferredLanguage))
  }

  private fun belongsToSameWork(a: SearchEditionCandidate, b: SearchEditionCandidate): Boolean {
    if (a.externalWorkKey != null && b.externalWorkKey != null && a.externalWorkKey == b.externalWorkKey) {
      return true
    }
    val wdA = a.externalIds["wikidata_work"]
    val wdB = b.externalIds["wikidata_work"]
    if (wdA != null && wdB != null && wdA == wdB) {
      return true
    }

    val coreA = BibliographicMath.normalizeTitleCore(a.workTitle)
    val coreB = BibliographicMath.normalizeTitleCore(b.workTitle)
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
    return editions
      .map { it.workTitle }
      .maxByOrNull { title ->
        var score = 0
        if (title != title.uppercase(Locale.ROOT)) score += 10
        if (':' !in title) score += 3
        score - (title.length / 40)
      }
      ?: editions.first().workTitle
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

    score += (candidate.sources.size - 1).coerceAtLeast(0) * 10
    if (MetadataSource.OPEN_LIBRARY in candidate.sources) score += 5
    if (MetadataSource.GOOGLE_BOOKS in candidate.sources) score += 5
    if (MetadataSource.BRASIL_API in candidate.sources) score += 6
    return score
  }
}
