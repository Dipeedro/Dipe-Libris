package com.example.feature.home

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
  uiState: HomeUiState,
  widthClass: WindowWidthClass,
  onOpenBook: (Long) -> Unit,
  modifier: Modifier = Modifier,
  onChooseFromLibrary: () -> Unit = {},
  onCatalogBook: () -> Unit = {},
  onUpdateProgressByPage: (book: CatalogBook, page: Int, note: String?) -> Unit = { _, _, _ -> },
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

  val centerPrimaryStage = secondaryReading.isEmpty()

  BoxWithConstraints(
    modifier = modifier.fillMaxSize().background(DipeTheme.colors.homeCanvas),
    contentAlignment = Alignment.TopCenter,
  ) {
    val isCompactHeight = maxHeight < 480.dp
    val topContentPadding = if (isCompactHeight) DipeSpacing.xs else DipeSpacing.md
    val bottomContentPadding =
      if (centerPrimaryStage) {
        if (isCompactHeight) DipeSpacing.sm else DipeSpacing.md
      } else {
        DipeSpacing.xl
      }
    val mastheadReserve = if (isCompactHeight) 92.dp else 124.dp
    val minStageHeight = (maxHeight - mastheadReserve).coerceAtLeast(180.dp)
    val maxContentWidth = if (widthClass == WindowWidthClass.COMPACT) 760.dp else 840.dp

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
              language = language,
              onClick = onFeaturedClick,
              onUpdateProgress = { progressSheetBook = featuredReading },
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
      onDismiss = { progressSheetBook = null },
      onSavePage = { page, note ->
        onUpdateProgressByPage(target, page, note)
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
  language: AppLanguage,
  onClick: () -> Unit,
  onUpdateProgress: () -> Unit,
) {
  val progressPercent = book.activeProgressPercent
  val currentPage = book.activeCurrentPage
  val isExpanded = widthClass == WindowWidthClass.EXPANDED
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

  if (isExpanded) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clip(MaterialTheme.shapes.large)
          .clickable(onClick = onClick)
          .padding(vertical = DipeSpacing.md)
          .testTag("home_reading_book_${book.id}"),
      horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xl),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      BookCoverArtwork(
        title = book.title,
        author = book.author,
        isFavorite = book.isFavorite,
        coverUri = book.coverUri,
        elevated = true,
        widthHintDp = 232.dp,
        modifier = Modifier.width(232.dp),
      )
      Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.Start,
      ) {
        FeaturedBookDetails(
          book = book,
          metadataParts = metadataParts,
          progressPercent = progressPercent,
          currentPage = currentPage,
          language = language,
          centered = false,
          onOpenBook = onClick,
          onUpdateProgress = onUpdateProgress,
        )
      }
    }
  } else {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .clip(MaterialTheme.shapes.large)
          .clickable(onClick = onClick)
          .padding(vertical = DipeSpacing.sm)
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
        widthHintDp = featuredWidth,
        modifier = Modifier.width(featuredWidth),
      )

      Spacer(modifier = Modifier.height(DipeSpacing.md))

      FeaturedBookDetails(
        book = book,
        metadataParts = metadataParts,
        progressPercent = progressPercent,
        currentPage = currentPage,
        language = language,
        centered = true,
        onOpenBook = onClick,
        onUpdateProgress = onUpdateProgress,
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
  onOpenBook: () -> Unit,
  onUpdateProgress: () -> Unit,
) {
  val textAlign = if (centered) TextAlign.Center else TextAlign.Start
  val columnAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = columnAlignment,
  ) {
    Text(
      text = book.title,
      style = MaterialTheme.typography.displayMedium,
      color = MaterialTheme.colorScheme.onBackground,
      textAlign = textAlign,
      maxLines = 3,
      overflow = TextOverflow.Ellipsis,
    )

    Spacer(modifier = Modifier.height(DipeSpacing.xs))

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

    if (progressPercent != null) {
      Spacer(modifier = Modifier.height(DipeSpacing.compact))
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
          add(LocaleManager.formatPercent(progressPercent, language))
        }

      Column(
        modifier = Modifier.widthIn(max = 264.dp).fillMaxWidth(),
        horizontalAlignment = columnAlignment,
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
      ) {
        EditorialProgressBar(progressPercent = progressPercent, height = 3.dp)
        Text(
          text = progressParts.joinToString(" · "),
          style = DipeTheme.typography.monoMetadata,
          color = MaterialTheme.colorScheme.secondary,
          textAlign = textAlign,
          modifier = Modifier.testTag("home_featured_progress_label"),
        )
      }
    }

    Spacer(modifier = Modifier.height(DipeSpacing.lg))

    FlowRow(
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

@Composable
private fun CurrentlyReadingPlate(
  book: CatalogBook,
  language: AppLanguage,
  onClick: () -> Unit,
) {
  Column(
    modifier =
      Modifier.width(120.dp)
        .clip(MaterialTheme.shapes.medium)
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

