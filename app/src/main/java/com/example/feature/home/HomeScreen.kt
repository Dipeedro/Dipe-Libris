package com.example.feature.home

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.EditorialMasthead
import com.example.core.designsystem.components.EditorialProgressBar
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.AppLanguage
import com.example.core.model.CatalogBook
import com.example.feature.library.QuickProgressSheet
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun HomeScreen(
  uiState: HomeUiState,
  widthClass: WindowWidthClass,
  onOpenBook: (Long) -> Unit,
  modifier: Modifier = Modifier,
  onChooseFromLibrary: () -> Unit = {},
  onCatalogBook: () -> Unit = {},
  onUpdateProgressByPage: (book: CatalogBook, page: Int, note: String?) -> Unit = { _, _, _ -> },
  onUpdateProgressByPageWithTotal:
    ((book: CatalogBook, page: Int, note: String?, totalPages: Int?) -> Unit)? = null,
  onUpdateProgressByPercent: (book: CatalogBook, percent: Float, note: String?) -> Unit = { _, _, _ -> },
  onCompleteReading: (CatalogBook) -> Unit = {},
  onCompleteReadingWithUndo: (book: CatalogBook, note: String?) -> Unit = { book, _ ->
    onCompleteReading(book)
  },
) {
  val language = LocalAppLanguage.current
  val listState = rememberLazyListState()
  val horizontalPadding = DipeSpacing.horizontalMargin(widthClass)
  val featuredReading = uiState.currentlyReading.firstOrNull()
  val secondaryReading =
    remember(uiState.currentlyReading) {
      if (uiState.currentlyReading.size > 1) uiState.currentlyReading.drop(1) else emptyList()
    }
  var progressSheetBook by remember { mutableStateOf<CatalogBook?>(null) }
  var progressSheetFocusTotal by remember { mutableStateOf(false) }

  val mastheadSubtitle =
    when {
      uiState.snapshot.readingCount > 0 ->
        "${LocaleManager.formatInteger(uiState.snapshot.readingCount, language)} ${
          pluralStringResource(
            R.plurals.home_overview_reading_label,
            if (uiState.snapshot.readingCount == 1) 1 else 2,
          )
        }"
      uiState.snapshot.pausedCount > 0 ->
        pluralStringResource(
          R.plurals.home_empty_subtitle_paused,
          if (uiState.snapshot.pausedCount == 1) 1 else 2,
          uiState.snapshot.pausedCount,
        )
      uiState.snapshot.totalWorks > 0 ->
        pluralStringResource(
          R.plurals.home_empty_subtitle_cataloged,
          if (uiState.snapshot.totalWorks == 1) 1 else 2,
          uiState.snapshot.totalWorks,
        )
      else -> stringResource(R.string.home_masthead_empty_subtitle)
    }

  if (uiState.isLoading) {
    Box(modifier = modifier.fillMaxSize())
    return
  }

  val centerPrimaryStage = secondaryReading.isEmpty()
  val configuration = LocalConfiguration.current
  val density = LocalDensity.current
  var measuredMastheadHeightPx by remember { mutableIntStateOf(0) }

  BoxWithConstraints(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    val viewportMaxHeight = maxHeight
    val isLandscape =
      (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && maxWidth >= 380.dp) ||
        (maxWidth > maxHeight && maxWidth >= 440.dp)
    val isCompactHeight = maxHeight < 480.dp
    val topContentPadding =
      when {
        isLandscape && centerPrimaryStage -> 0.dp
        isCompactHeight -> DipeSpacing.xs
        else -> DipeSpacing.md
      }
    val bottomContentPadding =
      when {
        isLandscape && centerPrimaryStage -> DipeSpacing.xs
        centerPrimaryStage && isCompactHeight -> DipeSpacing.sm
        centerPrimaryStage -> DipeSpacing.md
        else -> DipeSpacing.xl
      }
    val defaultMastheadReserve = if (isCompactHeight) 84.dp else 116.dp
    val mastheadHeightDp =
      if (measuredMastheadHeightPx > 0) with(density) { measuredMastheadHeightPx.toDp() }
      else defaultMastheadReserve
    val minStageHeight =
      (maxHeight - mastheadHeightDp - topContentPadding - bottomContentPadding).coerceAtLeast(160.dp)
    val maxContentWidth = if (widthClass == WindowWidthClass.COMPACT) 760.dp else 880.dp

    LazyColumn(
      state = listState,
      modifier = Modifier.widthIn(max = maxContentWidth).fillMaxSize().testTag("home_lazy_column"),
      contentPadding =
        PaddingValues(
          start = horizontalPadding,
          end = horizontalPadding,
          top = topContentPadding,
          bottom = bottomContentPadding,
        ),
      verticalArrangement =
        if (centerPrimaryStage) Arrangement.Top else Arrangement.spacedBy(DipeSpacing.lg),
    ) {
      item(key = "home_masthead", contentType = "masthead") {
        EditorialMasthead(
          title = stringResource(R.string.home_masthead_title),
          subtitle = mastheadSubtitle,
          modifier = Modifier.onSizeChanged { measuredMastheadHeightPx = it.height },
        )
      }

      item(key = "home_currently_reading", contentType = "currently_reading") {
        Box(
          modifier =
            if (centerPrimaryStage) {
              Modifier.fillMaxWidth().heightIn(min = minStageHeight)
            } else {
              Modifier.fillMaxWidth()
            },
          contentAlignment = Alignment.Center,
        ) {
          if (featuredReading == null) {
            HomeEmptyStage(
              onChooseFromLibrary = onChooseFromLibrary,
              onCatalogBook = onCatalogBook,
              modifier = Modifier.testTag("home_empty_stage"),
            )
          } else {
            val onFeaturedClick =
              remember(featuredReading.id, onOpenBook) { { onOpenBook(featuredReading.id) } }
            FeaturedReadingShowcase(
              book = featuredReading,
              widthClass = widthClass,
              isLandscape = isLandscape,
              viewportMaxHeight = viewportMaxHeight,
              stageAvailableHeight = minStageHeight,
              language = language,
              onClick = onFeaturedClick,
              onUpdateProgress = {
                progressSheetFocusTotal = false
                progressSheetBook = featuredReading
              },
              onDefineTotal = {
                progressSheetFocusTotal = true
                progressSheetBook = featuredReading
              },
            )
          }
        }
      }

      if (secondaryReading.isNotEmpty()) {
        item(key = "home_secondary_reading", contentType = "secondary_reading") {
          Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
            EditorialSectionHeader(title = stringResource(R.string.home_section_also_reading))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
              items(
                items = secondaryReading,
                key = { it.id },
                contentType = { "reading_plate" },
              ) { book ->
                val onBookClick = remember(book.id, onOpenBook) { { onOpenBook(book.id) } }
                CurrentlyReadingPlate(book = book, language = language, onClick = onBookClick)
              }
            }
          }
        }
      }
    }
  }

  progressSheetBook?.let { target ->
    QuickProgressSheet(
      bookTitle = target.title,
      initialPage = target.activeCurrentPage ?: target.primaryCopy?.currentPage ?: 0,
      totalPages = target.totalPages,
      initialPercent = target.activeProgressPercent ?: 0f,
      focusTotalPagesOnOpen = progressSheetFocusTotal,
      requireTotalPagesIfMissing = progressSheetFocusTotal,
      onDismiss = { progressSheetBook = null },
      onSavePage = { page, note ->
        onUpdateProgressByPage(target, page, note)
        progressSheetBook = null
      },
      onSavePageWithTotal = { page, note, enteredTotal ->
        if (onUpdateProgressByPageWithTotal != null) {
          onUpdateProgressByPageWithTotal(target, page, note, enteredTotal)
        } else {
          onUpdateProgressByPage(target, page, note)
        }
        progressSheetBook = null
      },
      onSavePercent = { percent, note ->
        onUpdateProgressByPercent(target, percent, note)
        progressSheetBook = null
      },
      onMarkFinished = { note ->
        onCompleteReadingWithUndo(target, note)
        progressSheetBook = null
      },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeaturedReadingShowcase(
  book: CatalogBook,
  widthClass: WindowWidthClass,
  isLandscape: Boolean,
  viewportMaxHeight: Dp,
  stageAvailableHeight: Dp,
  language: AppLanguage,
  onClick: () -> Unit,
  onUpdateProgress: () -> Unit,
  onDefineTotal: () -> Unit,
) {
  val progressPercent = book.activeProgressPercent
  val currentPage = book.activeCurrentPage
  val isSideBySide = isLandscape || widthClass == WindowWidthClass.EXPANDED
  val showTotalInProgress = progressPercent != null && currentPage != null && book.totalPages != null

  val metadataParts =
    remember(book.totalPages, book.publicationYear, book.editionNote, language, showTotalInProgress) {
      buildList {
        if (!showTotalInProgress) {
          book.totalPages?.let { add(LocaleManager.formatInteger(it, language) + " p.") }
        }
        book.publicationYear?.let { add(it.toString()) }
        book.editionNote?.takeIf { it.isNotBlank() }?.let { add(it) }
      }
    }

  val reduceMotion = DipeTheme.motion.reduceMotion
  val context = LocalContext.current
  val density = LocalDensity.current
  val coverReady = remember(book.id, reduceMotion) { mutableStateOf(reduceMotion || book.coverUri.isNullOrBlank()) }
  val coverProgress = remember(book.id, reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }
  val coverShadowProgress = remember(book.id, reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }
  val titleMetaProgress = remember(book.id, reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }
  val progressStageProgress = remember(book.id, reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }
  val buttonsProgress = remember(book.id, reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }

  if (!reduceMotion) {
    LaunchedEffect(book.id, book.coverUri) {
      val rawUri = book.coverUri?.takeIf { it.isNotBlank() }
      if (rawUri != null && !coverReady.value) {
        val data: Any =
          when {
            rawUri.startsWith("http://") || rawUri.startsWith("https://") -> rawUri
            rawUri.startsWith("content://") || rawUri.startsWith("file://") ->
              android.net.Uri.parse(rawUri)
            rawUri.startsWith("/") -> File(rawUri)
            else -> rawUri
          }
        val coverWidthHint =
          if (isSideBySide) {
            ((viewportMaxHeight * 0.64f).coerceAtLeast(140.dp) * 0.72f).coerceIn(132.dp, 236.dp)
          } else {
            if (widthClass == WindowWidthClass.COMPACT) 204.dp else 224.dp
          }
        val preloadWidthPx = with(density) { coverWidthHint.roundToPx() }.coerceIn(64, 1600)
        val preloadHeightPx = (preloadWidthPx * 3) / 2
        withTimeoutOrNull(350L) {
          runCatching {
            context.imageLoader.execute(
              ImageRequest.Builder(context)
                .data(data)
                .size(preloadWidthPx, preloadHeightPx)
                .precision(Precision.INEXACT)
                .build()
            )
          }
        }
      }
      coverReady.value = true
    }

    LaunchedEffect(book.id, coverReady.value) {
      if (!coverReady.value) return@LaunchedEffect
      launch {
        coverProgress.animateTo(1f, tween(durationMillis = 360, easing = FastOutSlowInEasing))
      }
      launch {
        delay(250L)
        coverShadowProgress.animateTo(1f, tween(durationMillis = 400, easing = EaseOut))
      }
      launch {
        delay(70L)
        titleMetaProgress.animateTo(1f, tween(durationMillis = 340, easing = FastOutSlowInEasing))
      }
      launch {
        delay(140L)
        progressStageProgress.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
      }
      launch {
        delay(210L)
        buttonsProgress.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
      }
    }
  }

  val risePx = with(density) { 14.dp.toPx() }
  val coverCascadeModifier =
    Modifier.graphicsLayer {
      val p = coverProgress.value
      alpha = p
      translationY = (1f - p) * risePx
    }
  val titleMetaCascadeModifier =
    Modifier.graphicsLayer {
      val p = titleMetaProgress.value
      alpha = p
      translationY = (1f - p) * risePx
    }
  val progressCascadeModifier =
    Modifier.graphicsLayer {
      val p = progressStageProgress.value
      alpha = p
      translationY = (1f - p) * risePx
    }
  val buttonsCascadeModifier =
    Modifier.graphicsLayer {
      val p = buttonsProgress.value
      alpha = p
      translationY = (1f - p) * risePx
    }

  val interactionSource = remember { MutableInteractionSource() }

  if (isSideBySide) {
    val maxCoverHeight =
      if (isLandscape) {
        minOf(
          viewportMaxHeight * 0.64f,
          (stageAvailableHeight - 28.dp).coerceAtLeast(140.dp),
        ).coerceAtLeast(140.dp)
      } else {
        (viewportMaxHeight * 0.64f).coerceAtLeast(150.dp)
      }
    val maxCoverWidthHint = (maxCoverHeight * 0.72f).coerceIn(132.dp, 236.dp)
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
          .padding(horizontal = DipeSpacing.xs, vertical = DipeSpacing.xs)
          .testTag("home_reading_book_${book.id}"),
      horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xl, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = coverCascadeModifier.padding(14.dp),
        contentAlignment = Alignment.Center,
      ) {
        BookCoverArtwork(
          title = book.title,
          author = book.author,
          isFavorite = book.isFavorite,
          coverUri = book.coverUri,
          elevated = true,
          shadowAlpha = { coverShadowProgress.value },
          widthHintDp = maxCoverWidthHint,
          contentScale = ContentScale.Fit,
          modifier = Modifier.heightIn(max = maxCoverHeight).widthIn(max = 260.dp),
        )
      }
      Column(
        modifier = Modifier.weight(1f).widthIn(max = 440.dp),
        horizontalAlignment = Alignment.Start,
      ) {
        FeaturedBookDetails(
          book = book,
          metadataParts = metadataParts,
          progressPercent = progressPercent,
          currentPage = currentPage,
          language = language,
          centered = false,
          isLandscape = isLandscape,
          titleMetaModifier = titleMetaCascadeModifier,
          progressModifier = progressCascadeModifier,
          buttonsModifier = buttonsCascadeModifier,
          onOpenBook = onClick,
          onUpdateProgress = onUpdateProgress,
          onDefineTotal = onDefineTotal,
        )
      }
    }
  } else {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
          .padding(horizontal = DipeSpacing.xs, vertical = DipeSpacing.sm)
          .testTag("home_reading_book_${book.id}"),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      val featuredWidth = if (widthClass == WindowWidthClass.COMPACT) 204.dp else 224.dp
      BookCoverArtwork(
        title = book.title,
        author = book.author,
        isFavorite = book.isFavorite,
        coverUri = book.coverUri,
        elevated = true,
        shadowAlpha = { coverShadowProgress.value },
        widthHintDp = featuredWidth,
        modifier = coverCascadeModifier.width(featuredWidth),
      )

      Spacer(modifier = Modifier.height(DipeSpacing.lg))

      FeaturedBookDetails(
        book = book,
        metadataParts = metadataParts,
        progressPercent = progressPercent,
        currentPage = currentPage,
        language = language,
        centered = true,
        isLandscape = false,
        titleMetaModifier = titleMetaCascadeModifier,
        progressModifier = progressCascadeModifier,
        buttonsModifier = buttonsCascadeModifier,
        onOpenBook = onClick,
        onUpdateProgress = onUpdateProgress,
        onDefineTotal = onDefineTotal,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeaturedBookDetails(
  book: CatalogBook,
  metadataParts: List<String>,
  progressPercent: Float?,
  currentPage: Int?,
  language: AppLanguage,
  centered: Boolean,
  isLandscape: Boolean,
  titleMetaModifier: Modifier = Modifier,
  progressModifier: Modifier = Modifier,
  buttonsModifier: Modifier = Modifier,
  onOpenBook: () -> Unit,
  onUpdateProgress: () -> Unit,
  onDefineTotal: () -> Unit,
) {
  val textAlign = if (centered) TextAlign.Center else TextAlign.Start
  val columnAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
  val hasKnownTotal = (book.totalPages ?: 0) > 0

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = columnAlignment,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().then(titleMetaModifier),
      horizontalAlignment = columnAlignment,
    ) {
      Text(
        text = book.title,
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = textAlign,
        maxLines = if (isLandscape) 2 else 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
      )

      Spacer(modifier = Modifier.height(DipeSpacing.xs))

      if (isLandscape) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.Start,
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs),
        ) {
          Text(
            text = book.author,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (metadataParts.isNotEmpty()) {
            Text(
              text = metadataParts.joinToString(" · "),
              style = DipeTheme.typography.monoMetadata,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Start,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      } else {
        FlowRow(
          horizontalArrangement =
            Arrangement.spacedBy(
              DipeSpacing.compact,
              if (centered) Alignment.CenterHorizontally else Alignment.Start,
            ),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs),
        ) {
          Text(
            text = book.author,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterVertically),
          )
          if (metadataParts.isNotEmpty()) {
            Text(
              text = metadataParts.joinToString(" · "),
              style = DipeTheme.typography.monoMetadata,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = textAlign,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.align(Alignment.CenterVertically),
            )
          }
        }
      }
    }

    if (progressPercent != null || book.activeSessionId != null) {
      Spacer(modifier = Modifier.height(if (hasKnownTotal) DipeSpacing.compact else DipeSpacing.xs))
      if (hasKnownTotal) {
        val progressParts =
          buildList {
            if (book.activeRereadNumber != null && book.activeRereadNumber > 1) {
              add(stringResource(R.string.journey_reread_label, book.activeRereadNumber))
            }
            if (currentPage != null && book.totalPages != null) {
              add(
                stringResource(
                  R.string.journey_page_of_total,
                  LocaleManager.formatInteger(currentPage, language),
                  LocaleManager.formatInteger(book.totalPages!!, language),
                )
              )
            } else if (currentPage != null && currentPage > 0) {
              add(
                stringResource(
                  R.string.journey_page_only,
                  LocaleManager.formatInteger(currentPage, language),
                )
              )
            }
            add(LocaleManager.formatPercent(progressPercent ?: 0f, language))
          }

        Column(
          modifier =
            (if (isLandscape) Modifier.fillMaxWidth() else Modifier.widthIn(max = 264.dp).fillMaxWidth())
              .then(progressModifier),
          horizontalAlignment = columnAlignment,
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
        ) {
          EditorialProgressBar(progressPercent = progressPercent ?: 0f, height = 3.dp)
          Text(
            text = progressParts.joinToString(" · "),
            style = DipeTheme.typography.monoMetadata,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = textAlign,
            modifier = Modifier.testTag("home_featured_progress_label"),
          )
        }
      } else {
        val safePage = (currentPage ?: book.primaryCopy?.currentPage ?: 0).coerceAtLeast(0)
        val pageUnknownBase =
          stringResource(
            R.string.journey_page_total_unknown,
            LocaleManager.formatInteger(safePage, language),
          )
        val pageUnknownLabel =
          if (book.activeRereadNumber != null && book.activeRereadNumber > 1) {
            "${stringResource(R.string.journey_reread_label, book.activeRereadNumber)} · $pageUnknownBase"
          } else {
            pageUnknownBase
          }

        FlowRow(
          modifier =
            (if (isLandscape) Modifier.fillMaxWidth() else Modifier.widthIn(max = 320.dp).fillMaxWidth())
              .then(progressModifier),
          horizontalArrangement =
            Arrangement.spacedBy(
              DipeSpacing.sm,
              if (centered) Alignment.CenterHorizontally else Alignment.Start,
            ),
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            text = pageUnknownLabel,
            style = DipeTheme.typography.monoMetadata,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = textAlign,
            modifier = Modifier.align(Alignment.CenterVertically).testTag("home_featured_progress_label"),
          )
          Box(
            modifier =
              Modifier.align(Alignment.CenterVertically)
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(CircleShape)
                .clickable(onClick = onDefineTotal)
                .padding(horizontal = DipeSpacing.xs)
                .testTag("home_define_total_link"),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = stringResource(R.string.journey_define_total_action),
              style = DipeTheme.typography.monoMetadata.copy(textDecoration = TextDecoration.Underline),
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        }
      }
    }

    Spacer(
      modifier =
        Modifier.height(
          when {
            !hasKnownTotal -> DipeSpacing.xs
            isLandscape -> DipeSpacing.md
            else -> DipeSpacing.lg
          }
        )
    )

    if (isLandscape) {
      BoxWithConstraints(modifier = Modifier.fillMaxWidth().then(buttonsModifier)) {
        val sideBySideButtons = maxWidth >= 320.dp
        if (sideBySideButtons) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Button(
              onClick = onOpenBook,
              shape = CircleShape,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.secondary,
                  contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
              contentPadding = PaddingValues(horizontal = DipeSpacing.compact, vertical = DipeSpacing.compact),
              modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
              Text(
                text = stringResource(R.string.home_open_book),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
              )
            }

            OutlinedButton(
              onClick = onUpdateProgress,
              shape = CircleShape,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              colors =
                ButtonDefaults.outlinedButtonColors(
                  containerColor = Color.Transparent,
                  contentColor = MaterialTheme.colorScheme.onBackground,
                ),
              contentPadding = PaddingValues(horizontal = DipeSpacing.compact, vertical = DipeSpacing.compact),
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("home_update_progress_button"),
            ) {
              Text(
                text = stringResource(R.string.journey_update_progress),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        } else {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          ) {
            Button(
              onClick = onOpenBook,
              shape = CircleShape,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.secondary,
                  contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
              contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.compact),
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
              Text(
                text = stringResource(R.string.home_open_book),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
              )
            }

            OutlinedButton(
              onClick = onUpdateProgress,
              shape = CircleShape,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              colors =
                ButtonDefaults.outlinedButtonColors(
                  containerColor = Color.Transparent,
                  contentColor = MaterialTheme.colorScheme.onBackground,
                ),
              contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.compact),
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("home_update_progress_button"),
            ) {
              Text(
                text = stringResource(R.string.journey_update_progress),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
              )
            }
          }
        }
      }
    } else {
      FlowRow(
        modifier = buttonsModifier,
        horizontalArrangement =
          Arrangement.spacedBy(
            DipeSpacing.compact,
            if (centered) Alignment.CenterHorizontally else Alignment.Start,
          ),
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
      ) {
        Button(
          onClick = onOpenBook,
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
          contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.compact),
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text(
            text = stringResource(R.string.home_open_book),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
          )
        }

        OutlinedButton(
          onClick = onUpdateProgress,
          shape = CircleShape,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          colors =
            ButtonDefaults.outlinedButtonColors(
              containerColor = Color.Transparent,
              contentColor = MaterialTheme.colorScheme.onBackground,
            ),
          contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.compact),
          modifier = Modifier.heightIn(min = 48.dp).testTag("home_update_progress_button"),
        ) {
          Text(
            text = stringResource(R.string.journey_update_progress),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
private fun CurrentlyReadingPlate(
  book: CatalogBook,
  language: AppLanguage,
  onClick: () -> Unit,
) {
  Column(
    modifier =
      Modifier.width(120.dp)
        .clickable(onClick = onClick)
        .padding(DipeSpacing.xs)
        .testTag("home_reading_book_${book.id}"),
    verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
  ) {
    BookCoverArtwork(
      title = book.title,
      author = book.author,
      isFavorite = book.isFavorite,
      coverUri = book.coverUri,
      elevated = true,
      widthHintDp = 120.dp,
      modifier = Modifier.fillMaxWidth(),
    )
    book.activeProgressPercent?.let { pct ->
      EditorialProgressBar(progressPercent = pct, height = 2.dp)
    }
    Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs)) {
      Text(
        text = book.title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = book.author,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      book.activeProgressPercent?.let { pct ->
        Text(
          text = LocaleManager.formatPercent(pct, language),
          style = DipeTheme.typography.monoMetadata,
          color = MaterialTheme.colorScheme.secondary,
          maxLines = 1,
        )
      }
    }
  }
}

private data class HomeEmptyCopyVariant(
  val titleRes: Int,
  val bodyRes: Int,
)

private val HomeEmptyCopyVariants =
  listOf(
    HomeEmptyCopyVariant(
      titleRes = R.string.home_empty_variant_1_title,
      bodyRes = R.string.home_empty_variant_1_body,
    ),
    HomeEmptyCopyVariant(
      titleRes = R.string.home_empty_variant_2_title,
      bodyRes = R.string.home_empty_variant_2_body,
    ),
    HomeEmptyCopyVariant(
      titleRes = R.string.home_empty_variant_3_title,
      bodyRes = R.string.home_empty_variant_3_body,
    ),
    HomeEmptyCopyVariant(
      titleRes = R.string.home_empty_variant_4_title,
      bodyRes = R.string.home_empty_variant_4_body,
    ),
  )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeEmptyStage(
  onChooseFromLibrary: () -> Unit,
  onCatalogBook: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val reduceMotion = DipeTheme.motion.reduceMotion
  val copyVariant = remember { HomeEmptyCopyVariants.random() }
  val titleText = stringResource(copyVariant.titleRes)
  val bodyText = stringResource(copyVariant.bodyRes)

  // Staggered entrance values read strictly during draw/graphicsLayer phase (zero recomposition)
  val illustrationProgress = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }
  val textProgress = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }
  val primaryButtonProgress = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }
  val secondaryButtonProgress = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }

  if (!reduceMotion) {
    LaunchedEffect(Unit) {
      launch {
        illustrationProgress.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        )
      }
      launch {
        delay(80L)
        textProgress.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        )
      }
      launch {
        delay(170L)
        primaryButtonProgress.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
        )
      }
      launch {
        delay(250L)
        secondaryButtonProgress.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
        )
      }
    }
  }

  val idlePhaseProvider: () -> Float =
    if (!reduceMotion) {
      val transition = rememberInfiniteTransition(label = "home_empty_emblem_idle")
      val phaseState =
        transition.animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec =
            infiniteRepeatable(
              animation = tween(durationMillis = 4200, easing = LinearEasing),
              repeatMode = RepeatMode.Reverse,
            ),
          label = "home_empty_emblem_breath",
        )
      val provider: () -> Float = { phaseState.value }
      provider
    } else {
      val staticProvider: () -> Float = { 0.5f }
      staticProvider
    }

  val density = LocalDensity.current
  val riseOffsetPx = with(density) { 18.dp.toPx() }
  val buttonRisePx = with(density) { 14.dp.toPx() }

  BoxWithConstraints(
    modifier = modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center,
  ) {
    val useTwoColumns = maxWidth >= 520.dp

    if (useTwoColumns) {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(vertical = DipeSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xl, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        HomeEmptyBookEmblem(
          circleSize = 132.dp,
          reduceMotion = reduceMotion,
          idlePhaseProvider = idlePhaseProvider,
          modifier =
            Modifier.graphicsLayer {
              val p = illustrationProgress.value
              alpha = p
              translationY = (1f - p) * riseOffsetPx
            },
        )

        Column(
          modifier = Modifier.weight(1f, fill = false).widthIn(max = 480.dp),
          horizontalAlignment = Alignment.Start,
        ) {
          Column(
            modifier =
              Modifier.graphicsLayer {
                val p = textProgress.value
                alpha = p
                translationY = (1f - p) * buttonRisePx
              },
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
          ) {
            Text(
              text = titleText,
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.onBackground,
              textAlign = TextAlign.Start,
            )
            Text(
              text = bodyText,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Start,
            )
          }

          Spacer(modifier = Modifier.height(DipeSpacing.md))

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          ) {
            HomeEmptyPrimaryActionButton(
              onClick = onChooseFromLibrary,
              modifier =
                Modifier.graphicsLayer {
                  val p = primaryButtonProgress.value
                  alpha = p
                  translationY = (1f - p) * buttonRisePx
                },
            )
            HomeEmptySecondaryActionButton(
              onClick = onCatalogBook,
              modifier =
                Modifier.graphicsLayer {
                  val p = secondaryButtonProgress.value
                  alpha = p
                  translationY = (1f - p) * buttonRisePx
                },
            )
          }
        }
      }
    } else {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp).padding(vertical = DipeSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        HomeEmptyBookEmblem(
          circleSize = 112.dp,
          reduceMotion = reduceMotion,
          idlePhaseProvider = idlePhaseProvider,
          modifier =
            Modifier.graphicsLayer {
              val p = illustrationProgress.value
              alpha = p
              translationY = (1f - p) * riseOffsetPx
            },
        )

        Spacer(modifier = Modifier.height(DipeSpacing.lg))

        Column(
          modifier =
            Modifier.fillMaxWidth().graphicsLayer {
              val p = textProgress.value
              alpha = p
              translationY = (1f - p) * buttonRisePx
            },
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        ) {
          Text(
            text = titleText,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
          )
          Text(
            text = bodyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }

        Spacer(modifier = Modifier.height(DipeSpacing.lg))

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
        ) {
          HomeEmptyPrimaryActionButton(
            onClick = onChooseFromLibrary,
            modifier =
              Modifier.graphicsLayer {
                val p = primaryButtonProgress.value
                alpha = p
                translationY = (1f - p) * buttonRisePx
              },
          )
          HomeEmptySecondaryActionButton(
            onClick = onCatalogBook,
            modifier =
              Modifier.graphicsLayer {
                val p = secondaryButtonProgress.value
                alpha = p
                translationY = (1f - p) * buttonRisePx
              },
          )
        }
      }
    }
  }
}

@Composable
private fun HomeEmptyPrimaryActionButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Button(
    onClick = onClick,
    shape = CircleShape,
    colors =
      ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
      ),
    contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.compact),
    modifier = modifier.heightIn(min = 48.dp).testTag("home_empty_choose_library_button"),
  ) {
    Text(
      text = stringResource(R.string.home_empty_choose_library),
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
    )
  }
}

@Composable
private fun HomeEmptySecondaryActionButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  OutlinedButton(
    onClick = onClick,
    shape = CircleShape,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    colors =
      ButtonDefaults.outlinedButtonColors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
      ),
    contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.compact),
    modifier = modifier.heightIn(min = 48.dp).testTag("home_empty_catalog_book_button"),
  ) {
    Text(
      text = stringResource(R.string.home_shortcut_add_volume),
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
    )
  }
}

@Composable
private fun HomeEmptyBookEmblem(
  circleSize: Dp,
  reduceMotion: Boolean,
  idlePhaseProvider: () -> Float,
  modifier: Modifier = Modifier,
) {
  val discColor = MaterialTheme.colorScheme.secondaryContainer
  val accentColor = MaterialTheme.colorScheme.secondary
  val pageHighlightColor = MaterialTheme.colorScheme.surfaceContainerLowest

  Box(
    modifier = modifier.size(circleSize),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val t = if (reduceMotion) 0.5f else idlePhaseProvider()
      // Smooth sine wave in [0f, 1f]
      val wave = ((1f - kotlin.math.cos(t * kotlin.math.PI.toFloat())) * 0.5f)
      val floatShiftY = if (reduceMotion) 0f else (0.5f - wave) * 4.5.dp.toPx()
      val radius = minOf(size.width, size.height) * 0.5f
      val centerX = size.width * 0.5f
      val centerY = size.height * 0.5f

      // Calm outer editorial halo ring
      drawCircle(
        color = accentColor.copy(alpha = if (reduceMotion) 0.14f else 0.10f + (0.10f * wave)),
        radius = radius - 1.dp.toPx(),
        style = Stroke(width = 1.dp.toPx()),
      )

      // Tonal medallion disc
      drawCircle(
        color = discColor,
        radius = radius - 6.dp.toPx(),
      )

      // Prominent open book symbol scaled for clear visual hierarchy inside the medallion
      val bookWidth = (radius * 2f) * 0.58f
      val bookHeight = bookWidth * 0.72f
      val leftX = centerX - (bookWidth * 0.5f)
      val rightX = centerX + (bookWidth * 0.5f)
      val topY = centerY - (bookHeight * 0.5f) + floatShiftY
      val bottomY = centerY + (bookHeight * 0.5f) + floatShiftY
      val pageLift = if (reduceMotion) 0f else (wave - 0.5f) * 1.4.dp.toPx()
      val spineDip = bookHeight * 0.14f
      val strokeMain = (bookWidth * 0.055f).coerceAtLeast(2.2.dp.toPx())
      val strokeThin = (bookWidth * 0.036f).coerceAtLeast(1.4.dp.toPx())

      val leftPagePath =
        Path().apply {
          moveTo(centerX, topY + spineDip)
          cubicTo(
            centerX - bookWidth * 0.16f,
            topY - pageLift,
            leftX + bookWidth * 0.12f,
            topY - pageLift,
            leftX,
            topY + bookHeight * 0.07f,
          )
          lineTo(leftX, bottomY - bookHeight * 0.05f)
          cubicTo(
            leftX + bookWidth * 0.14f,
            bottomY - bookHeight * 0.09f,
            centerX - bookWidth * 0.16f,
            bottomY - bookHeight * 0.09f,
            centerX,
            bottomY + spineDip * 0.45f,
          )
          close()
        }

      val rightPagePath =
        Path().apply {
          moveTo(centerX, topY + spineDip)
          cubicTo(
            centerX + bookWidth * 0.16f,
            topY - pageLift,
            rightX - bookWidth * 0.12f,
            topY - pageLift,
            rightX,
            topY + bookHeight * 0.07f,
          )
          lineTo(rightX, bottomY - bookHeight * 0.05f)
          cubicTo(
            rightX - bookWidth * 0.14f,
            bottomY - bookHeight * 0.09f,
            centerX + bookWidth * 0.16f,
            bottomY - bookHeight * 0.09f,
            centerX,
            bottomY + spineDip * 0.45f,
          )
          close()
        }

      drawPath(path = leftPagePath, color = pageHighlightColor.copy(alpha = 0.72f))
      drawPath(path = rightPagePath, color = pageHighlightColor.copy(alpha = 0.88f))

      drawPath(
        path = leftPagePath,
        color = accentColor,
        style = Stroke(width = strokeMain, cap = StrokeCap.Round, join = StrokeJoin.Round),
      )
      drawPath(
        path = rightPagePath,
        color = accentColor,
        style = Stroke(width = strokeMain, cap = StrokeCap.Round, join = StrokeJoin.Round),
      )

      // Central spine crease
      drawLine(
        color = accentColor,
        start = Offset(centerX, topY + spineDip),
        end = Offset(centerX, bottomY + spineDip * 0.45f),
        strokeWidth = strokeThin,
        cap = StrokeCap.Round,
      )

      // Subtle typographic lines on both open pages
      val lineY1 = topY + bookHeight * 0.36f
      val lineY2 = topY + bookHeight * 0.56f
      drawLine(
        color = accentColor.copy(alpha = 0.52f),
        start = Offset(leftX + bookWidth * 0.11f, lineY1),
        end = Offset(centerX - bookWidth * 0.10f, lineY1 - bookHeight * 0.03f),
        strokeWidth = strokeThin,
        cap = StrokeCap.Round,
      )
      drawLine(
        color = accentColor.copy(alpha = 0.42f),
        start = Offset(leftX + bookWidth * 0.11f, lineY2),
        end = Offset(centerX - bookWidth * 0.14f, lineY2 - bookHeight * 0.03f),
        strokeWidth = strokeThin,
        cap = StrokeCap.Round,
      )
      drawLine(
        color = accentColor.copy(alpha = 0.52f),
        start = Offset(centerX + bookWidth * 0.10f, lineY1 - bookHeight * 0.03f),
        end = Offset(rightX - bookWidth * 0.11f, lineY1),
        strokeWidth = strokeThin,
        cap = StrokeCap.Round,
      )
      drawLine(
        color = accentColor.copy(alpha = 0.42f),
        start = Offset(centerX + bookWidth * 0.10f, lineY2 - bookHeight * 0.03f),
        end = Offset(rightX - bookWidth * 0.16f, lineY2),
        strokeWidth = strokeThin,
        cap = StrokeCap.Round,
      )

      // Bookmark ribbon extending softly below the spine
      val ribbonX = centerX + bookWidth * 0.09f
      val ribbonTop = bottomY - bookHeight * 0.02f
      val ribbonBottom = bottomY + bookHeight * 0.24f
      val ribbonWidth = bookWidth * 0.09f
      val ribbonPath =
        Path().apply {
          moveTo(ribbonX - ribbonWidth * 0.5f, ribbonTop)
          lineTo(ribbonX + ribbonWidth * 0.5f, ribbonTop)
          lineTo(ribbonX + ribbonWidth * 0.5f, ribbonBottom)
          lineTo(ribbonX, ribbonBottom - ribbonWidth * 0.48f)
          lineTo(ribbonX - ribbonWidth * 0.5f, ribbonBottom)
          close()
        }
      drawPath(path = ribbonPath, color = accentColor)
    }
  }
}

