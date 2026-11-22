package com.example

import com.example.core.bibliographic.domain.QueryIntentParser
import com.example.core.bibliographic.domain.WorkGroupingEngine
import com.example.core.bibliographic.network.AdapterSearchRequest
import com.example.core.bibliographic.network.AppleBooksAdapter
import com.example.core.bibliographic.network.GoogleBooksAdapter
import com.example.core.bibliographic.network.OpenLibraryAdapter
import com.example.core.model.BibliographicMath
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.CoverCandidate
import com.example.core.model.LanguageConfidence
import com.example.core.model.LanguageTier
import com.example.core.model.MetadataSource
import com.example.core.model.SearchEditionCandidate
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 1 — Suíte diagnóstica de regressão para classificação de idiomas e consolidação de edições.
 *
 * Os testes diag* verificam o comportamento bibliográfico esperado; falhas nesta etapa comprovam
 * exatamente as regressões descritas na auditoria antes de qualquer alteração no código de produção.
 * Os testes prot* protegem comportamentos corretos que devem ser preservados na Etapa 2.
 */
class LanguageClassificationAuditTest {

  @Test
  fun diag01_salamandraSpanishEditionWithoutLangAndEnglishTitleMustNotBePortuguese() = runBlocking {
    val directAssessment =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = null,
        isbn13Or10 = "9788478885190",
        publisher = "Salamandra",
        editionTitle = "Harry Potter and the Prisoner of Azkaban",
      )
    assertNotEquals(
      "Salamandra sem idioma explícito e título em inglês não pode virar pt",
      "pt",
      directAssessment.normalizedCode,
    )
    assertTrue(
      "Tier deveria ser > PT_UNCERTAIN (2), mas foi ${directAssessment.tier} (${directAssessment.canonicalTag})",
      directAssessment.tier.rank > LanguageTier.PT_UNCERTAIN.rank,
    )

    val mockClient =
      mockHttpClient { url ->
        when {
          url.contains("/works/OL82586W.json") ->
            """{"key":"/works/OL82586W","title":"Harry Potter and the Prisoner of Azkaban","authors":[{"author":{"key":"/authors/OL23919A"}}]}"""
          url.contains("/authors/OL23919A.json") ->
            """{"key":"/authors/OL23919A","name":"J. K. Rowling"}"""
          url.contains("/works/OL82586W/editions.json") ->
            """
            {
              "size": 1,
              "entries": [
                {
                  "key": "/books/OL_SALAMANDRA_EN_TITLE",
                  "title": "Harry Potter and the Prisoner of Azkaban",
                  "publishers": ["Salamandra"],
                  "isbn_13": ["9788478885190"],
                  "publish_date": "2000"
                }
              ]
            }
            """.trimIndent()
          else -> "{}"
        }
      }
    val olAdapter = OpenLibraryAdapter(client = mockClient, baseUrl = "https://openlibrary.test")
    val pageResult = olAdapter.fetchEditionsForWorkPage("/works/OL82586W", offset = 0, limit = 20)
    val ed = pageResult.candidates.single()
    assertNotEquals("pt", ed.language)
    assertTrue(ed.languageTierRank > LanguageTier.PT_UNCERTAIN.rank)
  }

  @Test
  fun diag02_salamandraSpanishEditionWithSpanishTitleWithoutLangMustNotBePortuguese() {
    val assessment =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = null,
        isbn13Or10 = "9788478885190",
        publisher = "Ediciones Salamandra",
        editionTitle = "Harry Potter y el prisionero de Azkaban",
      )
    assertNotEquals(
      "Edição espanhola da Salamandra com título em espanhol não pode ser classificada como pt (${assessment.canonicalTag})",
      "pt",
      assessment.normalizedCode,
    )
    assertTrue(
      "Tier esperado > PT_UNCERTAIN, atual=${assessment.tier}",
      assessment.tier.rank > LanguageTier.PT_UNCERTAIN.rank,
    )
  }

  @Test
  fun diag03_englishTitleWithBrazilianOrAmbiguousPublisherWithoutLangMustNotPromoteToPtBr() {
    val roccoEnglishTitle =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = null,
        isbn13Or10 = null,
        publisher = "Rocco",
        editionTitle = "Harry Potter and the Prisoner of Azkaban",
      )
    assertNotEquals(
      "Título claramente em inglês + editora brasileira sem idioma explícito não deve virar PT_BR",
      LanguageTier.PT_BR,
      roccoEnglishTitle.tier,
    )
    assertNotEquals("pt", roccoEnglishTitle.normalizedCode)

    val globalPublisher =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = null,
        isbn13Or10 = null,
        publisher = "Global Publishing",
        editionTitle = "Harry Potter and the Prisoner of Azkaban",
      )
    assertNotEquals(
      "Editora internacional contendo token 'Global' não pode virar PT_BR",
      LanguageTier.PT_BR,
      globalPublisher.tier,
    )
    assertNotEquals("pt", globalPublisher.normalizedCode)
  }

  @Test
  fun diag04_spanishTitlesWithSharedAccentsOrWordsMustNotBeClassifiedAsPortuguese() {
    val spanishCases =
      listOf(
        "Harry Potter: Edición Ilustrada",
        "Harry Potter: Libro dos",
        "La cámara secreta",
      )
    for (title in spanishCases) {
      val res =
        BibliographicMath.assessLanguage(
          explicitLanguageRaw = null,
          isbn13Or10 = null,
          publisher = null,
          editionTitle = title,
        )
      assertNotEquals(
        "Título em espanhol '$title' não pode virar 'pt' (${res.tier})",
        "pt",
        res.normalizedCode,
      )
      assertTrue(
        "Título em espanhol '$title' deveria ficar com tier > PT_UNCERTAIN, atual=${res.tier}",
        res.tier.rank > LanguageTier.PT_UNCERTAIN.rank,
      )
    }
  }

  @Test
  fun diag05_appleBooksSpanishOrAccentedEnglishMustNotBeConfirmedPortuguese() = runBlocking {
    val appleJson =
      """
      {
        "resultCount": 2,
        "results": [
          {
            "trackId": 1001,
            "trackName": "Harry Potter y el prisionero de Azkaban",
            "artistName": "J.K. Rowling",
            "description": "Edición especial ilustrada para jóvenes lectores de la saga.",
            "releaseDate": "2015-12-08T08:00:00Z",
            "genres": ["Books", "Fantasy"],
            "kind": "ebook"
          },
          {
            "trackId": 1002,
            "trackName": "Harry Potter and the Prisoner of Azkaban",
            "artistName": "J.K. Rowling",
            "description": "Illustrated English edition featuring café sketches and naïve art notes by José.",
            "releaseDate": "2017-10-03T08:00:00Z",
            "genres": ["Books", "Fantasy"],
            "kind": "ebook"
          }
        ]
      }
      """.trimIndent()

    val mockClient = mockHttpClient { appleJson }
    val adapter = AppleBooksAdapter(client = mockClient, baseUrl = "https://itunes.test")
    val result =
      adapter.searchPaginated(
        AdapterSearchRequest(
          rawQuery = "Harry Potter e o Prisioneiro de Azkaban",
          parsedIntent = QueryIntentParser.parse("Harry Potter e o Prisioneiro de Azkaban"),
        )
      )

    assertEquals(2, result.candidates.size)
    val spanishEbook = result.candidates.first { it.externalIds["apple_books_track_id"] == "1001" }
    assertNotEquals(
      "E-book da Apple Books em espanhol não pode virar pt-br (${spanishEbook.canonicalLanguageTag})",
      "pt",
      spanishEbook.language,
    )
    assertNotEquals(LanguageConfidence.CONFIRMED.name, spanishEbook.languageConfidenceName)

    val englishEbook = result.candidates.first { it.externalIds["apple_books_track_id"] == "1002" }
    assertNotEquals(
      "E-book da Apple Books em inglês com acentos em sinopse não pode virar pt-br",
      "pt",
      englishEbook.language,
    )
  }

  @Test
  fun diag06_brazilianIsbnWithExplicitForeignLanguageMustPreserveForeignLanguageWithConflict() {
    val assessment =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = "en",
        isbn13Or10 = "9788532512062",
        publisher = "Rocco",
        editionTitle = "Harry Potter and the Prisoner of Azkaban",
      )
    assertEquals(
      "Idioma explícito 'en' com título em inglês não deve ser sobrescrito para pt-br apenas por ISBN/editora BR",
      "en",
      assessment.canonicalTag,
    )
    assertEquals(LanguageTier.EN, assessment.tier)
    assertTrue("Conflito contextual de ISBN BR deve ficar sinalizado", assessment.hasConflict)
  }

  @Test
  fun diag07_confirmedForeignLanguageMustNotBeOverwrittenByUncertainPortugueseInDeduplication() {
    val intent = QueryIntentParser.parse("Harry Potter e o Prisioneiro de Azkaban")
    val sharedIsbn = "9788478885190"

    // Candidato B (Open Library) possui idioma PT incerto, porém ganha reliabilityScore maior por capa + bônus +18 de preferredLanguage
    val olUncertainPt =
      SearchEditionCandidate(
        candidateId = "ol_ed_uncertain_pt",
        workTitle = "Harry Potter and the Prisoner of Azkaban",
        contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Salamandra",
        publicationYear = 2000,
        isbn13 = sharedIsbn,
        language = "pt",
        canonicalLanguageTag = "pt-br",
        languageTierRank = LanguageTier.PT_BR.rank,
        languageConfidenceName = LanguageConfidence.UNCERTAIN.name,
        coverCandidates =
          listOf(
            CoverCandidate(
              imageUrl = "https://covers.openlibrary.org/b/isbn/$sharedIsbn-L.jpg",
              source = MetadataSource.OPEN_LIBRARY,
              matchesIsbn = true,
            )
          ),
        externalWorkKey = "/works/OL82586W",
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )

    // Candidato A (Google Books) possui idioma espanhol explicitamente CONFIRMADO
    val gbConfirmedEs =
      SearchEditionCandidate(
        candidateId = "gb_confirmed_es",
        workTitle = "Harry Potter y el prisionero de Azkaban",
        contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Salamandra",
        publicationYear = 2000,
        isbn13 = sharedIsbn,
        language = "es",
        canonicalLanguageTag = "es",
        languageTierRank = LanguageTier.PT_PT_OR_OTHER.rank,
        languageConfidenceName = LanguageConfidence.CONFIRMED.name,
        externalWorkKey = "/works/OL82586W",
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )

    val outcome =
      WorkGroupingEngine.groupSeparated(
        rawCandidates = listOf(olUncertainPt, gbConfirmedEs),
        preferredLanguage = "pt-BR",
        parsedIntent = intent,
      )
    val allEditions = (outcome.primaryGroups + outcome.secondaryGroups).flatMap { it.editions }
    assertEquals("Mesmo ISBN deve consolidar em 1 edição", 1, allEditions.size)

    val merged = allEditions.single()
    assertEquals(
      "Idioma 'es' CONFIRMADO do Google Books não pode ser sobrescrito por 'pt-br' INCERTO",
      "es",
      merged.canonicalLanguageTag,
    )
    assertEquals(LanguageTier.PT_PT_OR_OTHER.rank, merged.languageTierRank)
    assertTrue("Divergência de idioma entre fontes deve marcar hasLanguageConflict", merged.hasLanguageConflict)
  }

  @Test
  fun diag08_duplicateEditionsWithConflictingConfirmedLanguagesMustFlagConflict() {
    val intent = QueryIntentParser.parse("Harry Potter e o Prisioneiro de Azkaban")
    val sharedIsbn = "9788532512062"

    val olConfirmedPt =
      SearchEditionCandidate(
        candidateId = "ol_pt",
        workTitle = "Harry Potter e o Prisioneiro de Azkaban",
        contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Rocco",
        publicationYear = 2000,
        isbn13 = sharedIsbn,
        language = "pt",
        canonicalLanguageTag = "pt-br",
        languageTierRank = LanguageTier.PT_BR.rank,
        languageConfidenceName = LanguageConfidence.CONFIRMED.name,
        externalWorkKey = "/works/OL82586W",
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )
    val gbConfirmedEs =
      SearchEditionCandidate(
        candidateId = "gb_es",
        workTitle = "Harry Potter y el prisionero de Azkaban",
        contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Rocco",
        publicationYear = 2000,
        isbn13 = sharedIsbn,
        language = "es",
        canonicalLanguageTag = "es",
        languageTierRank = LanguageTier.PT_PT_OR_OTHER.rank,
        languageConfidenceName = LanguageConfidence.CONFIRMED.name,
        externalWorkKey = "/works/OL82586W",
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )

    val outcome =
      WorkGroupingEngine.groupSeparated(
        rawCandidates = listOf(olConfirmedPt, gbConfirmedEs),
        preferredLanguage = "pt-BR",
        parsedIntent = intent,
      )
    val merged = (outcome.primaryGroups + outcome.secondaryGroups).flatMap { it.editions }.single()
    assertTrue(
      "Duplicatas de fontes diferentes com idiomas conflitantes devem preservar hasLanguageConflict=true",
      merged.hasLanguageConflict,
    )
  }

  @Test
  fun diag09_endToEndT1ContaminationAcrossOpenLibraryGoogleBooksAndAppleBooks() = runBlocking {
    val olClient =
      mockHttpClient { url ->
        when {
          url.contains("/works/OL82586W.json") ->
            """{"key":"/works/OL82586W","title":"Harry Potter and the Prisoner of Azkaban","authors":[{"author":{"key":"/authors/OL23919A"}}]}"""
          url.contains("/authors/OL23919A.json") ->
            """{"key":"/authors/OL23919A","name":"J. K. Rowling"}"""
          url.contains("/works/OL82586W/editions.json") ->
            """
            {
              "size": 4,
              "entries": [
                {
                  "key": "/books/OL_PT_ROCCO",
                  "title": "Harry Potter e o Prisioneiro de Azkaban",
                  "publishers": ["Rocco"],
                  "languages": [{"key": "/languages/por"}],
                  "isbn_13": ["9788532512062"],
                  "publish_date": "2000"
                },
                {
                  "key": "/books/OL_SALAMANDRA_EN_TITLE",
                  "title": "Harry Potter and the Prisoner of Azkaban",
                  "publishers": ["Salamandra"],
                  "isbn_13": ["9788478885190"],
                  "publish_date": "2000"
                },
                {
                  "key": "/books/OL_ES_ACCENT_ONLY",
                  "title": "Harry Potter: Edición Ilustrada",
                  "publishers": ["Editorial Desconocida"],
                  "isbn_13": ["9788498389265"],
                  "publish_date": "2017"
                },
                {
                  "key": "/books/OL_EN_BLOOMSBURY",
                  "title": "Harry Potter and the Prisoner of Azkaban",
                  "publishers": ["Bloomsbury"],
                  "languages": [{"key": "/languages/eng"}],
                  "isbn_13": ["9780747542155"],
                  "publish_date": "1999"
                }
              ]
            }
            """.trimIndent()
          else -> "{}"
        }
      }
    val gbClient =
      mockHttpClient {
        """
        {
          "totalItems": 1,
          "items": [
            {
              "id": "gb_es_1",
              "volumeInfo": {
                "title": "Harry Potter y el prisionero de Azkaban",
                "authors": ["J.K. Rowling"],
                "publisher": "Salamandra",
                "publishedDate": "2000",
                "industryIdentifiers": [{"type": "ISBN_13", "identifier": "9788478885190"}],
                "language": "es"
              }
            }
          ]
        }
        """.trimIndent()
      }
    val abClient =
      mockHttpClient {
        """
        {
          "resultCount": 1,
          "results": [
            {
              "trackId": 9901,
              "trackName": "Harry Potter y el prisionero de Azkaban",
              "artistName": "J.K. Rowling",
              "description": "La edición en español para lectores jóvenes.",
              "releaseDate": "2015-12-08T08:00:00Z",
              "genres": ["Books"],
              "kind": "ebook"
            }
          ]
        }
        """.trimIndent()
      }

    val intent = QueryIntentParser.parse("Harry Potter e o Prisioneiro de Azkaban")
    val olPage = OpenLibraryAdapter(client = olClient, baseUrl = "https://openlibrary.test")
      .fetchEditionsForWorkPage("/works/OL82586W", offset = 0, limit = 20)
    val gbRes = GoogleBooksAdapter(client = gbClient, apiKeyProvider = { "k" }, baseUrl = "https://books.test")
      .searchPaginated(AdapterSearchRequest(intent.rawQuery, intent))
    val abRes = AppleBooksAdapter(client = abClient, baseUrl = "https://itunes.test")
      .searchPaginated(AdapterSearchRequest(intent.rawQuery, intent))

    val outcome =
      WorkGroupingEngine.groupSeparated(
        rawCandidates = olPage.candidates + gbRes.candidates + abRes.candidates,
        preferredLanguage = "pt-BR",
        parsedIntent = intent,
      )
    val group = outcome.primaryGroups.first()
    val ptEditions = group.editions.filter { it.languageTierRank <= LanguageTier.PT_UNCERTAIN.rank }
    val ptIsbns = ptEditions.mapNotNull { it.isbn13 }.toSet()

    assertEquals(
      "Apenas a edição brasileira real (9788532512062) deveria estar marcada como português, mas PT continha: ${ptEditions.map { "${it.candidateId}:${it.canonicalLanguageTag}:${it.publisher}:${it.isbn13}" }}",
      setOf("9788532512062"),
      ptIsbns,
    )
    assertEquals(1, ptEditions.size)
  }

  @Test
  fun diag10_trulyUndeterminableEditionMustRemainUnknown() {
    val neutral =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = null,
        isbn13Or10 = null,
        publisher = null,
        editionTitle = "Harry Potter",
      )
    assertNull(neutral.canonicalTag)
    assertNull(neutral.normalizedCode)
    assertEquals(LanguageTier.UNKNOWN, neutral.tier)
    assertEquals(LanguageConfidence.UNCERTAIN, neutral.confidence)
  }

  @Test
  fun diag11_portugueseTitleWithoutExplicitLangMustStillBeRecoveredAsPtUncertain() {
    val ptNoMeta =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = null,
        isbn13Or10 = null,
        publisher = null,
        editionTitle = "Harry Potter e o Prisioneiro de Azkaban",
      )
    assertEquals("pt", ptNoMeta.normalizedCode)
    assertEquals(LanguageTier.PT_UNCERTAIN, ptNoMeta.tier)
  }

  @Test
  fun prot01_unequivocalPortugueseTitleWithTildeAndBrazilianSignalsConfirmedAsPtBr() {
    val withExplicit =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = "pt-BR",
        isbn13Or10 = "9788532530783",
        publisher = "Rocco",
        editionTitle = "Harry Potter e a Câmara Secreta: Edição Comemorativa",
      )
    assertEquals("pt-br", withExplicit.canonicalTag)
    assertEquals(LanguageTier.PT_BR, withExplicit.tier)
    assertEquals(LanguageConfidence.CONFIRMED, withExplicit.confidence)
    assertFalse(withExplicit.hasConflict)

    val withGenericPor =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = "por",
        isbn13Or10 = "9788532530783",
        publisher = "Rocco",
        editionTitle = "O Alienista: Edição de Coleção",
      )
    assertEquals("pt-br", withGenericPor.canonicalTag)
    assertEquals(LanguageTier.PT_BR, withGenericPor.tier)
    assertEquals(LanguageConfidence.CONFIRMED, withGenericPor.confidence)
  }

  @Test
  fun prot02_reliableExplicitLanguagePreservedEvenWhenWorkTitleIsDifferentLanguage() {
    val ptEditionOfEnglishWork =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = "pt-BR",
        isbn13Or10 = "9788532512062",
        publisher = "Rocco",
        editionTitle = "Harry Potter e o Prisioneiro de Azkaban",
      )
    assertEquals("pt-br", ptEditionOfEnglishWork.canonicalTag)
    assertEquals(LanguageTier.PT_BR, ptEditionOfEnglishWork.tier)
    assertEquals(LanguageConfidence.CONFIRMED, ptEditionOfEnglishWork.confidence)

    val enEditionOfBrazilianWork =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = "en",
        isbn13Or10 = "9780199539536",
        publisher = "Oxford University Press",
        editionTitle = "Dom Casmurro",
      )
    assertEquals("en", enEditionOfBrazilianWork.canonicalTag)
    assertEquals(LanguageTier.EN, enEditionOfBrazilianWork.tier)
    assertEquals(LanguageConfidence.CONFIRMED, enEditionOfBrazilianWork.confidence)
  }

  @Test
  fun prot03_brazilianEditionWithEnglishTitleOnlyAndNoExplicitLangMustNotForceEnglishOrConfirmedPtBr() {
    val assessment =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = null,
        isbn13Or10 = "9788532512062",
        publisher = "Rocco",
        editionTitle = "Harry Potter and the Prisoner of Azkaban",
      )
    assertNotEquals(
      "Não deve assumir inglês automaticamente apenas pelo título original",
      LanguageTier.EN,
      assessment.tier,
    )
    assertNotEquals(
      "Não deve afirmar português confirmado sem evidência textual ou metadado explícito",
      LanguageConfidence.CONFIRMED,
      assessment.confidence,
    )
    assertNotEquals(
      "Não deve promover para PT_BR (rank 1) quando título da edição está em inglês e idioma é ausente",
      LanguageTier.PT_BR,
      assessment.tier,
    )
  }

  @Test
  fun prot04_confirmedPortugueseBeatsUnknownCandidateDuringDeduplication() {
    val intent = QueryIntentParser.parse("Harry Potter e o Prisioneiro de Azkaban")
    val sharedIsbn = "9788532512062"

    val unknownWithExtras =
      SearchEditionCandidate(
        candidateId = "ol_unknown",
        workTitle = "Harry Potter",
        contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Editora Qualquer",
        publicationYear = 2000,
        totalPages = 348,
        isbn13 = sharedIsbn,
        language = null,
        canonicalLanguageTag = null,
        languageTierRank = LanguageTier.UNKNOWN.rank,
        languageConfidenceName = LanguageConfidence.UNCERTAIN.name,
        coverCandidates =
          listOf(
            CoverCandidate(
              imageUrl = "https://covers.openlibrary.org/b/isbn/$sharedIsbn-L.jpg",
              source = MetadataSource.OPEN_LIBRARY,
              matchesIsbn = true,
            )
          ),
        externalWorkKey = "/works/OL82586W",
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )

    val confirmedPt =
      SearchEditionCandidate(
        candidateId = "gb_pt",
        workTitle = "Harry Potter e o Prisioneiro de Azkaban",
        contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Rocco",
        publicationYear = 2000,
        isbn13 = sharedIsbn,
        language = "pt",
        canonicalLanguageTag = "pt-br",
        languageTierRank = LanguageTier.PT_BR.rank,
        languageConfidenceName = LanguageConfidence.CONFIRMED.name,
        externalWorkKey = "/works/OL82586W",
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )

    val outcome =
      WorkGroupingEngine.groupSeparated(
        rawCandidates = listOf(unknownWithExtras, confirmedPt),
        preferredLanguage = "pt-BR",
        parsedIntent = intent,
      )
    val merged = outcome.primaryGroups.single().editions.single()
    assertEquals("pt-br", merged.canonicalLanguageTag)
    assertEquals(LanguageTier.PT_BR.rank, merged.languageTierRank)
    assertEquals(LanguageConfidence.CONFIRMED.name, merged.languageConfidenceName)
  }

  @Test
  fun prot05_portugueseTitlesWithoutExplicitLangRemainEligibleForPtUncertain() {
    val ptTitles =
      listOf(
        "O Senhor dos Anéis: A Sociedade do Anel",
        "Cem Anos de Solidão",
        "Dom Casmurro: Edição Especial",
        "A Câmara Secreta: Edição Ilustrada",
      )
    for (title in ptTitles) {
      val res =
        BibliographicMath.assessLanguage(
          explicitLanguageRaw = null,
          isbn13Or10 = null,
          publisher = null,
          editionTitle = title,
        )
      assertEquals("Título '$title' deveria ser 'pt'", "pt", res.normalizedCode)
      assertEquals("Título '$title' deveria ser PT_UNCERTAIN", LanguageTier.PT_UNCERTAIN, res.tier)
    }
  }

  @Test
  fun prot06_foreignIsbnIsContextualOnlyAndMustNotOverrideExplicitPortuguese() {
    // Pottermore Publishing utiliza prefixo ISBN global/inglês (978-1) em e-books oficiais em português
    val pottermorePt =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = "pt-BR",
        isbn13Or10 = "9781781103098",
        publisher = "Pottermore Publishing",
        editionTitle = "Harry Potter e o Prisioneiro de Azkaban",
      )
    assertEquals("pt-br", pottermorePt.canonicalTag)
    assertEquals(LanguageTier.PT_BR, pottermorePt.tier)
    assertEquals(LanguageConfidence.CONFIRMED, pottermorePt.confidence)
    assertFalse(pottermorePt.hasConflict)
  }

  @Test
  fun prot07_ambiguousPublishersAndSubstringBoundariesDoNotCauseFalsePositives() {
    val nonBrPublishers =
      listOf(
        "Salamandra",
        "Ediciones Salamandra",
        "La Galera",
        "Editorial Planeta",
        "Record Books",
        "Suma de Letras",
        "Global Publishing",
        "Informática Editorial",
      )
    for (pub in nonBrPublishers) {
      assertFalse(
        "Editora ambígua/internacional '$pub' não pode corresponder incondicionalmente como brasileira",
        BibliographicMath.isKnownBrazilianPublisher(pub),
      )
    }

    assertFalse(
      "'Casa da Palavra' não pode casar substring 'asa' como editora de Portugal",
      BibliographicMath.isKnownPortugalPublisher("Casa da Palavra"),
    )

    val genuineBrPublishers =
      listOf(
        "Rocco",
        "Intrínseca",
        "Companhia das Letras",
        "Editora Record",
        "Globo Livros",
        "Galera Record",
        "Editora Ática",
        "Planeta do Brasil",
        "Global Editora",
      )
    for (pub in genuineBrPublishers) {
      assertTrue(
        "Editora brasileira legítima '$pub' deve continuar sendo reconhecida",
        BibliographicMath.isKnownBrazilianPublisher(pub),
      )
    }

    // Editora curta ambígua ("Record") + ISBN BR + título neutro/PT recupera em PT_UNCERTAIN,
    // mas "Record" sem ISBN e com título em inglês permanece UNKNOWN.
    val recordBr =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = null,
        isbn13Or10 = "9788501059956",
        publisher = "Record",
        editionTitle = "Dom Casmurro",
      )
    assertEquals("pt", recordBr.normalizedCode)
    assertEquals(LanguageTier.PT_UNCERTAIN, recordBr.tier)

    val recordForeign =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = null,
        isbn13Or10 = null,
        publisher = "Record",
        editionTitle = "The Prisoner of Azkaban",
      )
    assertNull(recordForeign.normalizedCode)
    assertEquals(LanguageTier.UNKNOWN, recordForeign.tier)
  }

  @Test
  fun prot08_portugueseDasPorCamaraCaliceFenixRecoveredWithoutForeignCollisions() {
    val genuinePortuguese =
      listOf(
        "O Senhor das Moscas",
        "Por Quem os Sinos Dobram",
        "Harry Potter e a Câmara Secreta",
        "Harry Potter e o Cálice de Fogo",
        "Harry Potter e a Ordem da Fênix",
      )
    for (title in genuinePortuguese) {
      val res =
        BibliographicMath.assessLanguage(
          explicitLanguageRaw = null,
          isbn13Or10 = null,
          publisher = null,
          editionTitle = title,
        )
      assertEquals("Título PT '$title' deveria ser 'pt'", "pt", res.normalizedCode)
      assertEquals("Título PT '$title' deveria ficar em PT_UNCERTAIN", LanguageTier.PT_UNCERTAIN, res.tier)
    }

    val germanDas =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = null,
        isbn13Or10 = null,
        publisher = null,
        editionTitle = "Das Boot",
      )
    assertNull(germanDas.normalizedCode)
    assertEquals(LanguageTier.UNKNOWN, germanDas.tier)
  }

  @Test
  fun prot09_appleBooksBrazilianEbookRecoveredAndCompatiblePtMergedCleanly() = runBlocking {
    val appleJson =
      """
      {
        "resultCount": 2,
        "results": [
          {
            "trackId": 2001,
            "trackName": "Dom Casmurro",
            "artistName": "Machado de Assis",
            "description": "Edição digital integral do clássico romance brasileiro sobre memória e ciúme.",
            "releaseDate": "2018-05-01T08:00:00Z",
            "genres": ["Books"],
            "kind": "ebook"
          },
          {
            "trackId": 2002,
            "trackName": "Coraline (Edição em Português)",
            "artistName": "Neil Gaiman",
            "description": "Edição comemorativa.",
            "releaseDate": "2020-01-01T08:00:00Z",
            "genres": ["Books"],
            "kind": "ebook"
          }
        ]
      }
      """.trimIndent()

    val adapter = AppleBooksAdapter(client = mockHttpClient { appleJson }, baseUrl = "https://itunes.test")
    val res =
      adapter.searchPaginated(
        AdapterSearchRequest(
          rawQuery = "Dom Casmurro",
          parsedIntent = QueryIntentParser.parse("Dom Casmurro"),
        )
      )
    assertEquals(2, res.candidates.size)
    for (ebook in res.candidates) {
      assertEquals("pt", ebook.language)
      assertEquals(LanguageTier.PT_UNCERTAIN.rank, ebook.languageTierRank)
      assertEquals(LanguageConfidence.UNCERTAIN.name, ebook.languageConfidenceName)
    }

    // Deduplicação compatível ("pt" UNCERTAIN vs "pt-br" CONFIRMED) deve refinar para "pt-br" sem falso conflito
    val intent = QueryIntentParser.parse("Harry Potter e o Prisioneiro de Azkaban")
    val sharedIsbn = "9788532512062"
    val genericPt =
      SearchEditionCandidate(
        candidateId = "ol_generic_pt",
        workTitle = "Harry Potter e o Prisioneiro de Azkaban",
        contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Rocco",
        isbn13 = sharedIsbn,
        language = "pt",
        canonicalLanguageTag = "pt",
        languageTierRank = LanguageTier.PT_UNCERTAIN.rank,
        languageConfidenceName = LanguageConfidence.UNCERTAIN.name,
        coverCandidates =
          listOf(
            CoverCandidate(
              imageUrl = "https://covers.openlibrary.org/b/isbn/$sharedIsbn-L.jpg",
              source = MetadataSource.OPEN_LIBRARY,
              matchesIsbn = true,
            )
          ),
        externalWorkKey = "/works/OL82586W",
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )
    val confirmedPtBr =
      SearchEditionCandidate(
        candidateId = "gb_confirmed_ptbr",
        workTitle = "Harry Potter e o Prisioneiro de Azkaban",
        contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Rocco",
        isbn13 = sharedIsbn,
        language = "pt",
        canonicalLanguageTag = "pt-br",
        languageTierRank = LanguageTier.PT_BR.rank,
        languageConfidenceName = LanguageConfidence.CONFIRMED.name,
        externalWorkKey = "/works/OL82586W",
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )

    val merged =
      WorkGroupingEngine.groupSeparated(
        rawCandidates = listOf(genericPt, confirmedPtBr),
        preferredLanguage = "pt-BR",
        parsedIntent = intent,
      ).primaryGroups.single().editions.single()
    assertEquals("pt-br", merged.canonicalLanguageTag)
    assertEquals(LanguageTier.PT_BR.rank, merged.languageTierRank)
    assertEquals(LanguageConfidence.CONFIRMED.name, merged.languageConfidenceName)
    assertFalse(merged.hasLanguageConflict)
    assertTrue(merged.coverCandidates.isNotEmpty())
  }

  @Test
  fun prot10_explicitForeignSpanishPreservedWithConflictAndSharedAcuteAccentsNotPromoted() {
    val explicitEsWithBrIsbn =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = "es",
        isbn13Or10 = "9788532512062",
        publisher = "Rocco",
        editionTitle = "Harry Potter y el prisionero de Azkaban",
      )
    assertEquals("es", explicitEsWithBrIsbn.canonicalTag)
    assertEquals(LanguageTier.PT_PT_OR_OTHER, explicitEsWithBrIsbn.tier)
    assertTrue(explicitEsWithBrIsbn.hasConflict)

    val sharedAcuteOnly =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = null,
        isbn13Or10 = null,
        publisher = null,
        editionTitle = "Crónica Histórica: Edición Básica",
      )
    assertNull(sharedAcuteOnly.normalizedCode)
    assertEquals(LanguageTier.UNKNOWN, sharedAcuteOnly.tier)
  }

  private fun mockHttpClient(bodyForUrl: (String) -> String): OkHttpClient =
    OkHttpClient.Builder()
      .addInterceptor { chain ->
        val url = chain.request().url.toString()
        val body = bodyForUrl(url)
        Response.Builder()
          .request(chain.request())
          .protocol(Protocol.HTTP_1_1)
          .code(if (body == "{}") 404 else 200)
          .message("OK")
          .body(body.toResponseBody("application/json".toMediaType()))
          .build()
      }
      .build()
}
