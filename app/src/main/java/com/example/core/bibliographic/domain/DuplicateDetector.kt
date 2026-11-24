package com.example.core.bibliographic.domain

import com.example.core.data.local.db.EditionEntity
import com.example.core.model.BibliographicMath
import com.example.core.model.CatalogBook
import com.example.core.model.DuplicateAssessment
import com.example.core.model.SearchEditionCandidate

object DuplicateDetector {

  fun assessCandidate(
    candidate: SearchEditionCandidate,
    localCatalog: List<CatalogBook>,
    matchedIsbnEditions: List<EditionEntity>,
    copyCountsByEditionId: Map<Long, Int>,
  ): DuplicateAssessment {
    val primaryTitle = candidate.editionTitle?.takeIf { it.isNotBlank() } ?: candidate.workTitle
    val altTitle = candidate.originalTitle?.takeIf { it.isNotBlank() } ?: candidate.workTitle
    return assessRaw(
      isbn13 = candidate.isbn13,
      isbn10 = candidate.isbn10,
      externalWorkKey = candidate.externalWorkKey,
      title = primaryTitle,
      author = candidate.primaryAuthorsText,
      localCatalog = localCatalog,
      matchedIsbnEditions = matchedIsbnEditions,
      copyCountsByEditionId = copyCountsByEditionId,
      alternateTitle = altTitle.takeIf { it != primaryTitle },
    )
  }

  fun assessRaw(
    isbn13: String?,
    isbn10: String?,
    externalWorkKey: String?,
    title: String,
    author: String,
    localCatalog: List<CatalogBook>,
    matchedIsbnEditions: List<EditionEntity>,
    copyCountsByEditionId: Map<Long, Int>,
    alternateTitle: String? = null,
  ): DuplicateAssessment {
    // 1. Exact edition match by ISBN
    if (matchedIsbnEditions.isNotEmpty()) {
      val exactEdition = matchedIsbnEditions.first()
      val book = localCatalog.firstOrNull { it.work.id == exactEdition.workId }
      val matchedIsbn = exactEdition.isbn13 ?: exactEdition.isbn10 ?: exactEdition.isbn ?: isbn13 ?: isbn10.orEmpty()
      return DuplicateAssessment.ExactEditionMatch(
        existingWorkId = exactEdition.workId,
        existingEditionId = exactEdition.id,
        existingTitle = book?.title ?: title,
        existingAuthor = book?.author ?: author,
        matchedIsbn = matchedIsbn,
        existingCopiesCount = copyCountsByEditionId[exactEdition.id] ?: book?.copyCount ?: 1,
      )
    }

    if (localCatalog.isEmpty()) return DuplicateAssessment.NoMatch

    // 2. Exact external work key match -> SameWorkDifferentEditionMatch
    if (!externalWorkKey.isNullOrBlank()) {
      localCatalog.firstOrNull { it.work.externalWorkKey == externalWorkKey }?.let { book ->
        return DuplicateAssessment.SameWorkDifferentEditionMatch(
          existingWorkId = book.work.id,
          existingTitle = book.title,
          existingAuthor = book.author,
          confidence = 0.98f,
          matchReason = "external_work_key",
        )
      }
    }

    // 3. Title + author similarity comparison (checking both edition and original work titles)
    val candidateTitleCores =
      listOfNotNull(
        BibliographicMath.normalizeTitleCore(title).takeIf { it.isNotEmpty() },
        alternateTitle?.let { BibliographicMath.normalizeTitleCore(it) }?.takeIf { it.isNotEmpty() },
      ).distinct()
    val candidateAuthorSurname = BibliographicMath.normalizeAuthorSurname(author)
    if (candidateTitleCores.isEmpty()) return DuplicateAssessment.NoMatch

    val scoredMatches =
      localCatalog
        .mapNotNull { book ->
          val localTitleCores =
            listOfNotNull(
              BibliographicMath.normalizeTitleCore(book.title).takeIf { it.isNotEmpty() },
              book.work.originalTitle?.let { BibliographicMath.normalizeTitleCore(it) }?.takeIf { it.isNotEmpty() },
            ).distinct()
          var titleSim = 0f
          for (candCore in candidateTitleCores) {
            for (locCore in localTitleCores) {
              val sim = BibliographicMath.tokenSimilarity(candCore, locCore)
              if (sim > titleSim) titleSim = sim
            }
          }
          if (titleSim < 0.72f) return@mapNotNull null

          val localSurname = BibliographicMath.normalizeAuthorSurname(book.author)
          val authorSim =
            if (candidateAuthorSurname.isNotEmpty() && localSurname.isNotEmpty()) {
              BibliographicMath.tokenSimilarity(candidateAuthorSurname, localSurname)
            } else {
              0.5f
            }

          Triple(book, titleSim, authorSim)
        }
        .sortedByDescending { (_, t, a) -> (t * 0.65f) + (a * 0.35f) }

    val best = scoredMatches.firstOrNull() ?: return DuplicateAssessment.NoMatch
    val (bestBook, bestTitleSim, bestAuthorSim) = best

    if (bestTitleSim >= 0.88f && bestAuthorSim >= 0.78f) {
      return DuplicateAssessment.SameWorkDifferentEditionMatch(
        existingWorkId = bestBook.work.id,
        existingTitle = bestBook.title,
        existingAuthor = bestBook.author,
        confidence = (bestTitleSim * 0.6f) + (bestAuthorSim * 0.4f),
        matchReason = "title_author_similarity",
      )
    }

    val uncertain =
      scoredMatches
        .filter { (_, tSim, aSim) ->
          tSim >= 0.90f || (tSim >= 0.80f && aSim >= 0.40f) || (tSim >= 0.74f && aSim >= 0.68f)
        }
        .map { it.first }
        .take(4)

    return if (uncertain.isNotEmpty()) {
      DuplicateAssessment.UncertainWorkMatch(candidates = uncertain)
    } else {
      DuplicateAssessment.NoMatch
    }
  }
}
