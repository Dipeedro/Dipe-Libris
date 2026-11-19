package com.example.core.bibliographic.domain

import com.example.core.data.local.db.MetadataSuggestionEntity
import com.example.core.model.BibliographicMath
import com.example.core.model.BookDetailAggregate
import com.example.core.model.Edition
import com.example.core.model.MetadataSuggestionScope
import com.example.core.model.MetadataSuggestionStatus
import com.example.core.model.SearchEditionCandidate

object WorkEditMask {
  const val TITLE = 1L shl 0
  const val AUTHOR = 1L shl 1
  const val SUBTITLE = 1L shl 2
  const val ORIGINAL_TITLE = 1L shl 3
  const val GENRE = 1L shl 4
  const val SYNOPSIS = 1L shl 5

  fun isLocked(mask: Long, flag: Long): Boolean = (mask and flag) != 0L
}

object MetadataDiffAnalyzer {

  fun analyze(
    book: BookDetailAggregate,
    candidates: List<SearchEditionCandidate>,
    existingSuggestions: List<MetadataSuggestionEntity>,
    preferredLanguage: String? = null,
  ): List<MetadataSuggestionEntity> {
    if (candidates.isEmpty()) return emptyList()
    val work = book.work
    val primaryEdition =
      book.editions.firstOrNull { it.edition.isPrimary }?.edition ?: book.editions.firstOrNull()?.edition
    val rejectedKeys =
      existingSuggestions
        .filter { it.status == MetadataSuggestionStatus.REJECTED || it.status == MetadataSuggestionStatus.PENDING }
        .map { "${it.scope.name}|${it.editionId ?: 0L}|${it.fieldName}|${it.suggestedValue}" }
        .toSet()

    val suggestions = mutableListOf<MetadataSuggestionEntity>()
    val now = System.currentTimeMillis()

    fun tryAdd(
      scope: MetadataSuggestionScope,
      editionId: Long?,
      fieldName: String,
      currentValue: String?,
      suggestedValue: String?,
      sourceName: String,
      reason: String,
      confidenceScore: Int,
    ) {
      val cleanSuggested = suggestedValue?.trim()?.takeIf { it.isNotEmpty() } ?: return
      val cleanCurrent = currentValue?.trim()?.takeIf { it.isNotEmpty() }
      if (cleanCurrent != null && BibliographicMath.normalizeText(cleanCurrent) == BibliographicMath.normalizeText(cleanSuggested)) {
        return
      }
      val dedupeKey = "${scope.name}|${editionId ?: 0L}|$fieldName|$cleanSuggested"
      if (dedupeKey in rejectedKeys) return
      val alreadyAdded = suggestions.any { it.scope == scope && it.editionId == editionId && it.fieldName == fieldName }
      if (alreadyAdded) return

      suggestions.add(
        MetadataSuggestionEntity(
          workId = work.id,
          editionId = editionId,
          scope = scope,
          fieldName = fieldName,
          currentValue = cleanCurrent,
          suggestedValue = cleanSuggested,
          sourceName = sourceName,
          reason = reason,
          confidenceScore = confidenceScore,
          status = MetadataSuggestionStatus.PENDING,
          createdAtEpochMillis = now,
        )
      )
    }

    // Match best candidate per existing edition (by ISBN first, else top candidate for work)
    for (edWithCopies in book.editions) {
      val edition = edWithCopies.edition
      val matchingCandidate = findBestCandidateForEdition(edition, candidates) ?: continue
      val sourceLabel = matchingCandidate.sources.joinToString(" + ") { it.displayName }

      if (edition.isbn13.isNullOrBlank() && !matchingCandidate.isbn13.isNullOrBlank()) {
        tryAdd(
          scope = MetadataSuggestionScope.EDITION,
          editionId = edition.id,
          fieldName = FIELD_ISBN13,
          currentValue = edition.isbn13 ?: edition.isbn,
          suggestedValue = matchingCandidate.isbn13,
          sourceName = sourceLabel,
          reason = REASON_MISSING_FIELD,
          confidenceScore = matchingCandidate.reliabilityScore,
        )
      }

      if (edition.publisherOrNote.isNullOrBlank() && !matchingCandidate.publisher.isNullOrBlank()) {
        tryAdd(
          scope = MetadataSuggestionScope.EDITION,
          editionId = edition.id,
          fieldName = FIELD_PUBLISHER,
          currentValue = edition.publisherOrNote,
          suggestedValue = matchingCandidate.publisher,
          sourceName = sourceLabel,
          reason = REASON_MISSING_FIELD,
          confidenceScore = matchingCandidate.reliabilityScore,
        )
      }

      if (edition.publicationYear == null && matchingCandidate.publicationYear != null) {
        tryAdd(
          scope = MetadataSuggestionScope.EDITION,
          editionId = edition.id,
          fieldName = FIELD_PUBLICATION_YEAR,
          currentValue = edition.publicationYear?.toString(),
          suggestedValue = matchingCandidate.publicationYear.toString(),
          sourceName = sourceLabel,
          reason = REASON_MISSING_FIELD,
          confidenceScore = matchingCandidate.reliabilityScore,
        )
      }

      if (edition.totalPages == null && matchingCandidate.totalPages != null) {
        tryAdd(
          scope = MetadataSuggestionScope.EDITION,
          editionId = edition.id,
          fieldName = FIELD_TOTAL_PAGES,
          currentValue = edition.totalPages?.toString(),
          suggestedValue = matchingCandidate.totalPages.toString(),
          sourceName = sourceLabel,
          reason = REASON_MISSING_FIELD,
          confidenceScore = matchingCandidate.reliabilityScore,
        )
      }

      if (edition.seriesName.isNullOrBlank() && !matchingCandidate.seriesName.isNullOrBlank()) {
        val seriesDisplay =
          listOfNotNull(matchingCandidate.seriesName, matchingCandidate.seriesVolume?.let { "#$it" })
            .joinToString(" ")
        tryAdd(
          scope = MetadataSuggestionScope.EDITION,
          editionId = edition.id,
          fieldName = FIELD_SERIES_NAME,
          currentValue = edition.seriesName,
          suggestedValue = seriesDisplay,
          sourceName = sourceLabel,
          reason = REASON_SERIES_IDENTIFIED,
          confidenceScore = matchingCandidate.reliabilityScore,
        )
      }

      if (edition.translator.isNullOrBlank() && !matchingCandidate.translatorsText.isNullOrBlank()) {
        tryAdd(
          scope = MetadataSuggestionScope.EDITION,
          editionId = edition.id,
          fieldName = FIELD_TRANSLATOR,
          currentValue = edition.translator,
          suggestedValue = matchingCandidate.translatorsText,
          sourceName = sourceLabel,
          reason = REASON_MISSING_FIELD,
          confidenceScore = matchingCandidate.reliabilityScore,
        )
      }

      if (!edition.isCoverCustomized && edition.coverUri.isNullOrBlank()) {
        val bestCover = matchingCandidate.bestCover
        if (bestCover != null) {
          tryAdd(
            scope = MetadataSuggestionScope.EDITION,
            editionId = edition.id,
            fieldName = FIELD_COVER_URL,
            currentValue = edition.coverUri,
            suggestedValue = bestCover.imageUrl,
            sourceName = bestCover.source.displayName,
            reason = REASON_COVER_FOUND,
            confidenceScore = bestCover.score,
          )
        }
      }
    }

    // Work-level suggestions using top candidate overall
    val topCandidate =
      candidates.firstOrNull { cand ->
        primaryEdition?.isbn13 != null && cand.isbn13 == primaryEdition.isbn13
      } ?: candidates.maxByOrNull { it.reliabilityScore }

    if (topCandidate != null) {
      val sourceLabel = topCandidate.sources.joinToString(" + ") { it.displayName }

      if (!WorkEditMask.isLocked(work.userEditedMask, WorkEditMask.SUBTITLE) &&
        work.subtitle.isNullOrBlank() &&
        !topCandidate.subtitle.isNullOrBlank()
      ) {
        tryAdd(
          scope = MetadataSuggestionScope.WORK,
          editionId = null,
          fieldName = FIELD_SUBTITLE,
          currentValue = work.subtitle,
          suggestedValue = topCandidate.subtitle,
          sourceName = sourceLabel,
          reason = REASON_MISSING_FIELD,
          confidenceScore = topCandidate.reliabilityScore,
        )
      }

      if (!work.isSynopsisCustomized && !WorkEditMask.isLocked(work.userEditedMask, WorkEditMask.SYNOPSIS)) {
        val rankedSynopses =
          SynopsisSelector.rankAndDeduplicate(
            candidates = candidates.flatMap { it.synopsisCandidates },
            preferredLanguage = preferredLanguage,
            editionLanguage = primaryEdition?.language,
          )
        val bestSynopsis = rankedSynopses.firstOrNull()
        if (bestSynopsis != null) {
          val currentLen = work.activeSynopsis?.length ?: 0
          val isSignificantlyBetter = work.activeSynopsis.isNullOrBlank() || (bestSynopsis.text.length > currentLen + 120 && currentLen < 120)
          if (isSignificantlyBetter) {
            tryAdd(
              scope = MetadataSuggestionScope.WORK,
              editionId = null,
              fieldName = FIELD_SYNOPSIS,
              currentValue = work.activeSynopsis,
              suggestedValue = bestSynopsis.text,
              sourceName = bestSynopsis.source.displayName,
              reason = if (work.activeSynopsis.isNullOrBlank()) REASON_MISSING_FIELD else REASON_RICHER_SYNOPSIS,
              confidenceScore = bestSynopsis.score,
            )
          }
        }
      }
    }

    return suggestions
  }

  private fun findBestCandidateForEdition(
    edition: Edition,
    candidates: List<SearchEditionCandidate>,
  ): SearchEditionCandidate? {
    val ed13 = edition.isbn13 ?: BibliographicMath.parseIsbn(edition.isbn)?.isbn13
    val ed10 = edition.isbn10 ?: BibliographicMath.parseIsbn(edition.isbn)?.isbn10
    if (ed13 != null) {
      candidates.firstOrNull { it.isbn13 == ed13 }?.let { return it }
    }
    if (ed10 != null) {
      candidates.firstOrNull { it.isbn10 == ed10 }?.let { return it }
    }
    return candidates
      .filter { cand ->
        val edLang = BibliographicMath.normalizeLanguageCode(edition.language)
        edLang == null || cand.language == null || cand.language == edLang
      }
      .maxByOrNull { it.reliabilityScore }
  }

  const val FIELD_SUBTITLE = "subtitle"
  const val FIELD_SYNOPSIS = "activeSynopsis"
  const val FIELD_ISBN13 = "isbn13"
  const val FIELD_PUBLISHER = "publisherOrNote"
  const val FIELD_PUBLICATION_YEAR = "publicationYear"
  const val FIELD_TOTAL_PAGES = "totalPages"
  const val FIELD_SERIES_NAME = "seriesName"
  const val FIELD_TRANSLATOR = "translator"
  const val FIELD_COVER_URL = "coverUrl"

  const val REASON_MISSING_FIELD = "missing_field"
  const val REASON_RICHER_SYNOPSIS = "richer_synopsis"
  const val REASON_COVER_FOUND = "cover_found"
  const val REASON_SERIES_IDENTIFIED = "series_identified"
}
