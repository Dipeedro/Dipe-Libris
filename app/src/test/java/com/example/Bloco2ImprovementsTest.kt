package com.example

import com.example.core.bibliographic.domain.CoverRanker
import com.example.core.bibliographic.domain.DuplicateDetector
import com.example.core.bibliographic.domain.QueryIntentParser
import com.example.core.bibliographic.domain.WorkGroupingEngine
import com.example.core.designsystem.components.shouldShowMinimalLanguageBadge
import com.example.core.di.BookReviewMode
import com.example.core.di.ReviewDraftPayload
import com.example.core.model.AppLanguage
import com.example.core.model.CandidateContributor
import com.example.core.model.CatalogBook
import com.example.core.model.ContributorRole
import com.example.core.model.CoverCandidate
import com.example.core.model.DuplicateAssessment
import com.example.core.model.Edition
import com.example.core.model.LanguageConfidence
import com.example.core.model.LanguageTier
import com.example.core.model.MetadataSource
import com.example.core.model.ReadingStatus
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.Work
import com.example.feature.library.buildInitialReviewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Bloco2ImprovementsTest {

  @Test
  fun coverRanker_prefersTrueHighResolutionGoogleAndAppleUrls() {
    val googleCandidates =
      CoverRanker.buildGoogleBooksCoverCandidates(
        rawUrl = "http://books.google.com/books/content?id=abc123XYZ&printsec=frontcover&img=1&zoom=1&edge=curl",
        matchesIsbn = true,
        matchesPublisherYear = false,
        matchesLanguage = true,
      )
    assertTrue(googleCandidates.size >= 3)
    assertEquals(
      "https://books.google.com/books/publisher/content/images/frontcovers/abc123XYZ?fife=w800",
      googleCandidates[0].imageUrl,
    )
    assertTrue(googleCandidates[1].imageUrl.contains("zoom=0"))
    assertTrue(googleCandidates[2].imageUrl.contains("zoom=1"))

    val appleCandidates =
      CoverRanker.buildAppleBooksCoverCandidates(
        artworkUrl100 = "http://is1-ssl.mzstatic.com/image/thumb/Publication/v4/aa/bb/cover.jpg/100x100bb.jpg",
        matchesIsbn = false,
        matchesPublisherYear = true,
        matchesLanguage = true,
      )
    assertEquals(3, appleCandidates.size)
    assertTrue(appleCandidates[0].imageUrl.contains("1200x1800bb"))
    assertTrue(appleCandidates[1].imageUrl.contains("600x900bb"))
    assertTrue(appleCandidates[2].imageUrl.contains("100x100bb"))

    val compactUrls =
      CoverRanker.selectForDisplaySize(
        orderedUrls = googleCandidates.map { it.imageUrl },
        maxWidthDp = 56,
      )
    assertEquals(2, compactUrls.size)
    assertFalse(compactUrls.any { "fife=w800" in it || "zoom=0" in it })
    assertFalse(compactUrls.any { "/frontcovers/" in it && "zoom=1" in it })

    val expandedOl =
      CoverRanker.expandHdVariants("http://covers.openlibrary.org/b/id/12345-M.jpg")
    assertEquals("https://covers.openlibrary.org/b/id/12345-L.jpg", expandedOl.first())
  }

  @Test
  fun coverRanker_preservesOpenLibraryFallbackAndNeverRewritesFrontcoversToZoom1() {
    val googleCandidates =
      CoverRanker.buildGoogleBooksCoverCandidates(
        rawUrl = "http://books.google.com/books/content?id=VolXYZ&printsec=frontcover&img=1&zoom=1&edge=curl",
        matchesIsbn = true,
        matchesPublisherYear = true,
        matchesLanguage = true,
      )
    val openLibraryCover =
      CoverCandidate(
        imageUrl = "https://covers.openlibrary.org/b/id/555123-L.jpg?default=false",
        source = MetadataSource.OPEN_LIBRARY,
        matchesIsbn = true,
        matchesPublisherYear = true,
        matchesLanguage = true,
        widthHint = 333,
        heightHint = 500,
      )

    val combined = CoverRanker.rankAndDeduplicate(googleCandidates + openLibraryCover)
    assertEquals(MetadataSource.OPEN_LIBRARY, combined.first().source)

    val compactFromGoogleFirst =
      CoverRanker.selectForDisplaySize(
        orderedUrls = googleCandidates.map { it.imageUrl } + openLibraryCover.imageUrl,
        maxWidthDp = 52,
      )
    assertEquals(2, compactFromGoogleFirst.size)
    assertFalse(compactFromGoogleFirst.any { "/frontcovers/" in it && "zoom=1" in it })
    assertTrue(compactFromGoogleFirst.any { "books.google.com/books/content" in it && "zoom=1" in it })
    assertTrue(compactFromGoogleFirst.any { "covers.openlibrary.org" in it && "-M.jpg" in it })

    val appleQueryCandidates =
      CoverRanker.buildAppleBooksCoverCandidates(
        artworkUrl100 = "https://is1-ssl.mzstatic.com/image/thumb/Pub/100x100bb.jpg?v=2",
        matchesIsbn = false,
        matchesPublisherYear = false,
        matchesLanguage = false,
      )
    assertTrue(appleQueryCandidates.any { "1200x1800bb.jpg?v=2" in it.imageUrl })
    val compactApple = CoverRanker.selectForDisplaySize(appleQueryCandidates.map { it.imageUrl }, 44)
    assertTrue(compactApple.first().contains("200x300bb.jpg?v=2"))

    assertTrue(
      com.example.core.data.local.media.CoverPlaceholderDetector.isGoogleBooksHost(
        "https://books.google.com/books/publisher/content/images/frontcovers/VolXYZ?fife=w800"
      )
    )
    assertFalse(
      com.example.core.data.local.media.CoverPlaceholderDetector.isValidImageContentType(
        "text/html; charset=utf-8"
      )
    )
    assertTrue(
      com.example.core.data.local.media.CoverPlaceholderDetector.isValidImageContentType(
        "image/png"
      )
    )
  }

  @Test
  fun localizedEditionMergesAndCatalogsEditionTitleWithOriginalTitlePreserved() {
    val intent = QueryIntentParser.parse("Harry Potter e a Pedra Filosofal")
    val sharedIsbn = "9788532511010"

    val openLibraryEnglishWork =
      SearchEditionCandidate(
        candidateId = "ol_rocco",
        workTitle = "Harry Potter and the Philosopher's Stone",
        editionTitle = "Harry Potter e a Pedra Filosofal",
        contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Rocco",
        publicationYear = 2000,
        isbn13 = sharedIsbn,
        language = "pt",
        canonicalLanguageTag = "pt-br",
        languageTierRank = LanguageTier.PT_BR.rank,
        languageConfidenceName = LanguageConfidence.CONFIRMED.name,
        externalWorkKey = "/works/OL82563W",
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )

    val googleBooksPt =
      SearchEditionCandidate(
        candidateId = "gb_rocco",
        workTitle = "Harry Potter e a Pedra Filosofal",
        editionTitle = "Harry Potter e a Pedra Filosofal",
        contributors = listOf(CandidateContributor("J.K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Rocco",
        publicationYear = 2000,
        totalPages = 224,
        isbn13 = sharedIsbn,
        language = "pt",
        canonicalLanguageTag = "pt-br",
        languageTierRank = LanguageTier.PT_BR.rank,
        languageConfidenceName = LanguageConfidence.CONFIRMED.name,
        coverCandidates =
          listOf(
            CoverCandidate(
              imageUrl = "https://books.google.com/books/publisher/content/images/frontcovers/rocco1?fife=w800",
              source = MetadataSource.GOOGLE_BOOKS,
              matchesIsbn = true,
            )
          ),
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )

    val grouped =
      WorkGroupingEngine.groupSeparated(
        rawCandidates = listOf(openLibraryEnglishWork, googleBooksPt),
        preferredLanguage = "pt-BR",
        parsedIntent = intent,
      )
    val selectedEdition = grouped.primaryGroups.first().editions.single()
    assertEquals("Harry Potter e a Pedra Filosofal", selectedEdition.editionTitle)
    assertEquals("Harry Potter and the Philosopher's Stone", selectedEdition.originalTitle)

    val reviewState =
      buildInitialReviewState(
        ReviewDraftPayload(
          mode = BookReviewMode.IMPORTED,
          candidate = selectedEdition,
        )
      )
    assertEquals("Harry Potter e a Pedra Filosofal", reviewState.title)
    assertEquals("Harry Potter and the Philosopher's Stone", reviewState.originalTitle)
  }

  @Test
  fun duplicateDetector_matchesCrossLanguageEditionAndOriginalTitle() {
    val existingBook =
      CatalogBook(
        work =
          Work(
            id = 42L,
            title = "Harry Potter e a Pedra Filosofal",
            originalTitle = "Harry Potter and the Philosopher's Stone",
            author = "J. K. Rowling",
            status = ReadingStatus.READING,
          ),
        primaryEdition =
          Edition(
            id = 7L,
            workId = 42L,
            publisherOrNote = "Rocco",
            isbn13 = "9788532511010",
            language = "pt-br",
            isPrimary = true,
          ),
        primaryCopy = null,
      )

    val englishEditionCandidate =
      SearchEditionCandidate(
        candidateId = "ol_bloomsbury",
        workTitle = "Harry Potter and the Philosopher's Stone",
        editionTitle = "Harry Potter and the Philosopher's Stone",
        contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Bloomsbury",
        isbn13 = "9780747532699",
        language = "en",
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )

    val assessment =
      DuplicateDetector.assessCandidate(
        candidate = englishEditionCandidate,
        localCatalog = listOf(existingBook),
        matchedIsbnEditions = emptyList(),
        copyCountsByEditionId = emptyMap(),
      )
    assertTrue(assessment is DuplicateAssessment.SameWorkDifferentEditionMatch)
    assertEquals(42L, (assessment as DuplicateAssessment.SameWorkDifferentEditionMatch).existingWorkId)
  }

  @Test
  fun languageBadgeVisibility_hidesDefaultAppLanguageInLibraryButKeepsInSearch() {
    assertFalse(
      shouldShowMinimalLanguageBadge(
        languageTag = "pt-BR",
        appLanguage = AppLanguage.PORTUGUESE_BR,
        hideIfMatchesAppLanguage = true,
      )
    )
    assertFalse(
      shouldShowMinimalLanguageBadge(
        languageTag = "pt",
        appLanguage = AppLanguage.PORTUGUESE_BR,
        hideIfMatchesAppLanguage = true,
      )
    )
    assertTrue(
      shouldShowMinimalLanguageBadge(
        languageTag = "en",
        appLanguage = AppLanguage.PORTUGUESE_BR,
        hideIfMatchesAppLanguage = true,
      )
    )
    assertTrue(
      shouldShowMinimalLanguageBadge(
        languageTag = "pt-BR",
        appLanguage = AppLanguage.PORTUGUESE_BR,
        hideIfMatchesAppLanguage = false,
      )
    )
    assertFalse(
      shouldShowMinimalLanguageBadge(
        languageTag = "en-US",
        appLanguage = AppLanguage.ENGLISH,
        hideIfMatchesAppLanguage = true,
      )
    )
    assertTrue(
      shouldShowMinimalLanguageBadge(
        languageTag = "pt-BR",
        appLanguage = AppLanguage.ENGLISH,
        hideIfMatchesAppLanguage = true,
      )
    )
  }
}
