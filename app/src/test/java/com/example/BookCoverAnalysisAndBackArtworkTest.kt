package com.example

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.DefaultCoverTokens
import com.example.core.designsystem.DipeLibrisTheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.BackArtworkMotif
import com.example.core.designsystem.components.BookBackCoverSurface
import com.example.core.designsystem.components.BookCoverAnalyzer
import com.example.core.designsystem.components.PaperGrainTextureProvider
import com.example.core.designsystem.components.resolveBackArtworkRenderPalette
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.AppLanguage
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.HybridSearchResponse
import com.example.core.model.LanguageTier
import com.example.core.model.MatchTier
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import com.example.core.model.SourceQueryStatus
import com.example.feature.library.BookSearchScreen
import com.example.feature.library.BookSearchUiState
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookCoverAnalysisAndBackArtworkTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @After
  fun tearDown() {
    BookCoverAnalyzer.clearCache()
  }

  private fun buildPixelBitmap(
    width: Int,
    height: Int,
    pixelColor: (x: Int, y: Int) -> Int,
  ): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (y in 0 until height) {
      for (x in 0 until width) {
        bmp.setPixel(x, y, pixelColor(x, y))
      }
    }
    return bmp
  }

  private fun contrastRatio(a: Color, b: Color): Float {
    val l1 = max(a.luminance(), b.luminance())
    val l2 = min(a.luminance(), b.luminance())
    return (l1 + 0.05f) / (l2 + 0.05f)
  }

  @Test
  fun paletteAndSpatialAnalysis_extractsDistinctColorsAndPreservesSpatialDistribution() {
    // Horizon artwork: deep midnight top, warm terracotta horizon/lower half, gold sun accent
    val horizonBmp =
      buildPixelBitmap(32, 48) { x, y ->
        when {
          y < 20 -> AndroidColor.rgb(20, 38, 68)
          x in 11..20 && y in 16..24 -> AndroidColor.rgb(218, 162, 58)
          else -> AndroidColor.rgb(176, 72, 48)
        }
      }
    val horizonAnalysis = BookCoverAnalyzer.analyzeSoftwareBitmap(horizonBmp)
    horizonBmp.recycle()

    assertNotNull(horizonAnalysis)
    horizonAnalysis!!
    assertTrue(
      "Dominant and secondary should be perceptually distinct",
      BookCoverAnalyzer.perceptualDistance(
        horizonAnalysis.palette.dominant,
        horizonAnalysis.palette.secondary,
      ) >= 0.14f,
    )
    assertTrue(
      "Top band should preserve cool navy blue dominance",
      horizonAnalysis.spatial.topBand.blue > horizonAnalysis.spatial.topBand.red + 0.10f,
    )
    assertTrue(
      "Bottom band should preserve warm terracotta red dominance",
      horizonAnalysis.spatial.bottomBand.red > horizonAnalysis.spatial.bottomBand.blue + 0.25f,
    )
    assertTrue(
      "Top-vs-bottom contrast should be substantial",
      horizonAnalysis.spatial.topVsBottomContrast >= 0.20f,
    )
    assertEquals(BackArtworkMotif.HORIZON_BANDS, horizonAnalysis.motif)

    // Center medallion artwork: calm slate perimeter with vibrant emerald focal center
    val medallionBmp =
      buildPixelBitmap(32, 48) { x, y ->
        if (x in 9..22 && y in 12..34) {
          AndroidColor.rgb(32, 138, 96)
        } else {
          AndroidColor.rgb(44, 48, 56)
        }
      }
    val medallionAnalysis = BookCoverAnalyzer.analyzeSoftwareBitmap(medallionBmp)
    medallionBmp.recycle()

    assertNotNull(medallionAnalysis)
    medallionAnalysis!!
    assertEquals(BackArtworkMotif.CENTER_MEDALLION, medallionAnalysis.motif)
    assertTrue(medallionAnalysis.spatial.centerProminence > medallionAnalysis.spatial.topVsBottomContrast)

    // Left-vs-right chromatic split
    val splitBmp =
      buildPixelBitmap(32, 48) { x, _ ->
        if (x < 16) AndroidColor.rgb(154, 48, 42) else AndroidColor.rgb(32, 82, 128)
      }
    val splitAnalysis = BookCoverAnalyzer.analyzeSoftwareBitmap(splitBmp)
    splitBmp.recycle()

    assertNotNull(splitAnalysis)
    splitAnalysis!!
    assertEquals(BackArtworkMotif.DIAGONAL_SPLIT, splitAnalysis.motif)
    assertTrue(splitAnalysis.spatial.leftColor.red > splitAnalysis.spatial.leftColor.blue + 0.20f)
    assertTrue(splitAnalysis.spatial.rightColor.blue > splitAnalysis.spatial.rightColor.red + 0.20f)
  }

  @Test
  fun analyzer_isDeterministicAndDifferentCoversProduceDifferentArtworks() {
    val coverA =
      buildPixelBitmap(30, 45) { _, y ->
        if (y < 22) AndroidColor.rgb(34, 62, 102) else AndroidColor.rgb(212, 184, 124)
      }
    val coverB =
      buildPixelBitmap(30, 45) { x, y ->
        if (x < 15 && y < 30) AndroidColor.rgb(144, 46, 38) else AndroidColor.rgb(232, 224, 210)
      }

    val firstRunA = BookCoverAnalyzer.analyzeSoftwareBitmap(coverA)
    val secondRunA = BookCoverAnalyzer.analyzeSoftwareBitmap(coverA)
    val runB = BookCoverAnalyzer.analyzeSoftwareBitmap(coverB)
    coverA.recycle()
    coverB.recycle()

    assertEquals(firstRunA, secondRunA)
    assertNotNull(firstRunA)
    assertNotNull(runB)
    assertNotEquals(firstRunA!!.compositionSeed, runB!!.compositionSeed)
    assertNotEquals(firstRunA.palette.dominant, runB.palette.dominant)
  }

  @Test
  fun analyzer_handlesEmptyInvalidRecycledAndTinyBitmapsSafely() {
    // 1x1 too small
    val tiny1x1 = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    assertNull(BookCoverAnalyzer.analyzeSoftwareBitmap(tiny1x1))
    tiny1x1.recycle()

    // Recycled bitmap
    val recycled = Bitmap.createBitmap(16, 24, Bitmap.Config.ARGB_8888)
    recycled.recycle()
    assertNull(BookCoverAnalyzer.analyzeSoftwareBitmap(recycled))

    // Transparent bitmap
    val transparent =
      buildPixelBitmap(16, 24) { _, _ -> AndroidColor.TRANSPARENT }
    assertNull(BookCoverAnalyzer.analyzeSoftwareBitmap(transparent))
    transparent.recycle()

    // Very small valid 2x2 and 3x3 bitmaps analyze safely without out-of-bounds
    val tiny2x2 =
      buildPixelBitmap(2, 2) { x, y ->
        if (x == 0 && y == 0) AndroidColor.rgb(160, 52, 42) else AndroidColor.rgb(32, 54, 88)
      }
    val tiny2x2Analysis = BookCoverAnalyzer.analyzeSoftwareBitmap(tiny2x2)
    tiny2x2.recycle()
    assertNotNull(tiny2x2Analysis)

    val tiny3x3 =
      buildPixelBitmap(3, 3) { _, y ->
        if (y == 0) AndroidColor.rgb(218, 178, 72) else AndroidColor.rgb(42, 66, 58)
      }
    val tiny3x3Analysis = BookCoverAnalyzer.analyzeSoftwareBitmap(tiny3x3)
    tiny3x3.recycle()
    assertNotNull(tiny3x3Analysis)
  }

  @Test
  fun edgeCases_monochromeDarkBrightAndHighlySaturatedCoversStayRestrainedAndLegible() {
    // 1. Mostly monochromatic warm slate cover
    val monoBmp =
      buildPixelBitmap(24, 36) { _, y ->
        val shade = 84 + (y / 3)
        AndroidColor.rgb(shade, shade + 2, shade + 6)
      }
    val monoAnalysis = BookCoverAnalyzer.analyzeSoftwareBitmap(monoBmp)!!
    monoBmp.recycle()
    assertEquals(BackArtworkMotif.EDITORIAL_FRAME, monoAnalysis.motif)
    val monoRender = resolveBackArtworkRenderPalette(monoAnalysis, isDarkTheme = false)
    assertTrue(
      "Monochrome back surface must maintain visual shape separation",
      BookCoverAnalyzer.perceptualDistance(monoRender.midFieldColor, monoRender.primaryShapeColor) >= 0.08f,
    )

    // 2. Pitch-black dark cover
    val darkBmp = buildPixelBitmap(24, 36) { _, _ -> AndroidColor.rgb(3, 3, 4) }
    val darkAnalysis = BookCoverAnalyzer.analyzeSoftwareBitmap(darkBmp)!!
    darkBmp.recycle()
    assertTrue(darkAnalysis.mood.isDark)
    val darkRender = resolveBackArtworkRenderPalette(darkAnalysis, isDarkTheme = true)
    assertTrue(
      "Pitch-black cover back surface should lift slightly off #000000 for material depth",
      darkRender.midFieldColor.luminance() > 0.008f,
    )
    assertTrue(
      "Light ink on dark back artwork must remain readable",
      contrastRatio(darkRender.textPrimaryColor, darkRender.midFieldColor) >= 4.5f,
    )

    // 3. Stark pure-white bright cover -> must NOT yield a plain white back face
    val brightBmp = buildPixelBitmap(24, 36) { _, _ -> AndroidColor.WHITE }
    val brightAnalysis = BookCoverAnalyzer.analyzeSoftwareBitmap(brightBmp)!!
    brightBmp.recycle()
    assertTrue(brightAnalysis.mood.isLight)
    val brightRender = resolveBackArtworkRenderPalette(brightAnalysis, isDarkTheme = false)
    assertTrue(
      "Bright/white cover back artwork must be toned below pure white (${brightRender.midFieldColor.luminance()})",
      brightRender.midFieldColor.luminance() < 0.78f,
    )
    assertTrue(
      "Dark ink on toned bright cover must remain readable",
      contrastRatio(brightRender.textPrimaryColor, brightRender.midFieldColor) >= 4.5f,
    )

    // 4. Hyper-saturated neon cover -> back cover tames excessive chroma cleanly
    val neonBmp =
      buildPixelBitmap(24, 36) { x, _ ->
        if (x < 12) AndroidColor.rgb(0, 255, 64) else AndroidColor.rgb(255, 0, 220)
      }
    val neonAnalysis = BookCoverAnalyzer.analyzeSoftwareBitmap(neonBmp)!!
    neonBmp.recycle()
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(
      (neonAnalysis.palette.dominant.red * 255f).toInt(),
      (neonAnalysis.palette.dominant.green * 255f).toInt(),
      (neonAnalysis.palette.dominant.blue * 255f).toInt(),
      hsv,
    )
    assertTrue("Neon saturation should be tempered below raw 1.0 (${hsv[1]})", hsv[1] < 0.85f)
    val neonRender = resolveBackArtworkRenderPalette(neonAnalysis, isDarkTheme = false)
    assertTrue(
      "Board edge should derive richly from cover palette rather than neutral white",
      neonRender.boardEdgeColor != Color.White && neonRender.boardEdgeColor.luminance() < 0.65f,
    )
  }

  @Test
  fun cacheReuse_invalidation_andBoundedLazyAnalysisBehaveProperly() = runTest {
    val bmp1 = buildPixelBitmap(24, 36) { _, _ -> AndroidColor.rgb(168, 52, 40) }
    val drawable1 = BitmapDrawable(null, bmp1)
    val uri = "https://example.org/cover_cache_test.jpg"

    val first = BookCoverAnalyzer.extractFromDrawableSync(uri, drawable1)
    assertNotNull(first)
    val cachedHit = BookCoverAnalyzer.getCached(uri, BookCoverAnalyzer.signatureForDrawable(drawable1))
    assertEquals(first, cachedHit)

    // Replacing bitmap at same URI automatically invalidates stale signature
    val bmp2 = buildPixelBitmap(24, 36) { _, _ -> AndroidColor.rgb(28, 86, 134) }
    val drawable2 = BitmapDrawable(null, bmp2)
    assertNull(BookCoverAnalyzer.getCached(uri, BookCoverAnalyzer.signatureForDrawable(drawable2)))
    val refreshed = BookCoverAnalyzer.extractFromDrawableSync(uri, drawable2)
    assertNotNull(refreshed)
    assertNotEquals(first!!.palette.dominant, refreshed!!.palette.dominant)

    // Explicit invalidation clears cached entry
    BookCoverAnalyzer.invalidate(uri)
    assertNull(BookCoverAnalyzer.getCached(uri))

    // Priority + bounded background analysis executes cleanly across multiple concurrent covers
    val concurrentResults =
      (0 until 6)
        .map { idx ->
          async {
            val b = buildPixelBitmap(18, 27) { _, _ -> AndroidColor.rgb(30 + idx * 30, 60, 120) }
            val d = BitmapDrawable(null, b)
            val res =
              BookCoverAnalyzer.analyzeDrawableBounded(
                cacheKey = "lazy://cover_$idx",
                drawable = d,
                prioritize = idx == 0,
                lazyStaggerMs = if (idx == 0) 0L else 12L * idx,
              )
            b.recycle()
            res
          }
        }
        .awaitAll()
    assertTrue(concurrentResults.all { it != null })

    bmp1.recycle()
    bmp2.recycle()
  }

  @Test
  fun paperGrainTextureAndTypographicCoverFallback_remainDeterministic() {
    assertNotNull(PaperGrainTextureProvider.brush())

    val entry = DefaultCoverTokens.paletteFor(title = "Grande Sertão: Veredas", author = "Guimarães Rosa")
    val analysisA = BookCoverAnalyzer.analyzeTypographicCover(entry, "Grande Sertão: Veredas", "Guimarães Rosa")
    val analysisB = BookCoverAnalyzer.analyzeTypographicCover(entry, "Grande Sertão: Veredas", "Guimarães Rosa")
    assertEquals(analysisA, analysisB)

    val renderPalette = resolveBackArtworkRenderPalette(analysisA, isDarkTheme = false)
    assertTrue(renderPalette.midFieldColor.luminance() < 0.85f)
  }

  @Test
  fun backArtworkSurface_rendersEditorialHierarchyAndRespectsReduceMotion() {
    val coverBmp =
      buildPixelBitmap(24, 36) { _, y ->
        if (y < 18) AndroidColor.rgb(28, 48, 84) else AndroidColor.rgb(186, 86, 52)
      }
    val analysis = BookCoverAnalyzer.analyzeSoftwareBitmap(coverBmp)!!
    coverBmp.recycle()
    val renderPalette = resolveBackArtworkRenderPalette(analysis, isDarkTheme = false)
    var flipValue by mutableStateOf(0.84f)
    var reduceMotion by mutableStateOf(false)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme(userReduceMotion = reduceMotion) {
          BookBackCoverSurface(
            analysis = analysis,
            renderPalette = renderPalette,
            publisherText = "Companhia das Letras",
            yearValue = 2019,
            pagesText = "424 p.",
            flipProgressProvider = { flipValue },
            reduceMotion = reduceMotion,
            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 2.dp, bottomEnd = 2.dp),
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Companhia das Letras").assertIsDisplayed()
    composeTestRule.onNodeWithText("2019").assertIsDisplayed()
    composeTestRule.onNodeWithText("424 p.").assertIsDisplayed()

    // Toggle reduceMotion mid-flip cleanly without crash or metadata loss
    reduceMotion = true
    flipValue = 1.0f
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Companhia das Letras").assertIsDisplayed()
  }

  @Test
  fun searchGalleryHold_preservesFlipBehaviorAndEditorialBackArtworkUnderTypographicFallback() {
    val edition =
      SearchEditionCandidate(
        candidateId = "ed_art_1",
        workTitle = "Memórias Póstumas de Brás Cubas",
        contributors = listOf(CandidateContributor("Machado de Assis", ContributorRole.AUTHOR)),
        publisher = "Penguin-Companhia",
        publicationYear = 2014,
        totalPages = 368,
        isbn13 = "9788563560957",
        language = "pt",
        canonicalLanguageTag = "pt-br",
        languageTierRank = LanguageTier.PT_BR.rank,
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )
    val group =
      SearchWorkGroup(
        groupId = "w_art_1",
        canonicalTitle = edition.workTitle,
        primaryAuthors = edition.contributors,
        editions = listOf(edition),
        sources = listOf(MetadataSource.OPEN_LIBRARY),
        matchTierName = MatchTier.EXACT_WORK.name,
      )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme(userReduceMotion = false) {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Memórias Póstumas",
                hasSearched = true,
                expandedGroupIds = setOf("w_art_1"),
                response =
                  HybridSearchResponse(
                    query = "Memórias Póstumas",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(group),
                    sourceStatuses = listOf(SourceQueryStatus(MetadataSource.OPEN_LIBRARY, ProviderOutcomeState.SUCCESS, 1)),
                  ),
              ),
            widthClass = WindowWidthClass.COMPACT,
            onQueryChange = {},
            onSubmitSearch = {},
            onExpandSecondarySources = {},
            onOpenScanner = {},
            onManualCatalog = {},
            onToggleGroupExpanded = {},
            onLoadMoreEditions = {},
            onToggleEditionSelection = {},
            onSelectEdition = {},
            onOpenExistingBook = {},
            onDismissDuplicate = {},
            onDuplicateAddCopy = { _, _, _ -> },
            onDuplicateAddEdition = { _, _ -> },
            onDuplicateSaveSeparately = {},
            onNavigateBack = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0_back_face").assertDoesNotExist()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performTouchInput { down(center) }
    composeTestRule.mainClock.advanceTimeBy(700)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0_back_face", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule
      .onNode(hasText("Penguin-Companhia") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("2014") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("368 p.") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performTouchInput { up() }
    composeTestRule.mainClock.advanceTimeBy(400)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0_back_face").assertDoesNotExist()
  }
}
