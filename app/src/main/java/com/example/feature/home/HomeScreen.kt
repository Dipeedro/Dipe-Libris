package com.example.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.EditorialMasthead
import com.example.core.designsystem.components.EditorialProgressBar
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.AppLanguage
import com.example.core.model.CatalogBook
import com.example.feature.library.QuickProgressSheet

@Composable
fun HomeScreen(
  uiState: HomeUiState,
  widthClass: WindowWidthClass,
  onOpenBook: (Long) -> Unit,
  modifier: Modifier = Modifier,
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
    if (uiState.snapshot.readingCount > 0) {
      "${LocaleManager.formatInteger(uiState.snapshot.readingCount, language)} ${
        pluralStringResource(
          R.plurals.home_overview_reading_label,
          if (uiState.snapshot.readingCount == 1) 1 else 2,
        )
      }"
    } else {
      stringResource(R.string.home_masthead_empty_subtitle)
    }

  Box(
    modifier = modifier.fillMaxSize().background(DipeTheme.colors.homeCanvas),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      state = listState,
      modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("home_lazy_column"),
      contentPadding =
        PaddingValues(
          start = horizontalPadding,
          end = horizontalPadding,
          top = DipeSpacing.md,
          bottom = DipeSpacing.xxl,
        ),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xl),
    ) {
      item(key = "home_masthead", contentType = "masthead") {
        EditorialMasthead(
          title = stringResource(R.string.home_masthead_title),
          subtitle = mastheadSubtitle,
        )
      }

      item(key = "home_currently_reading", contentType = "currently_reading") {
        if (featuredReading == null) {
          EditorialEmptyState(
            icon = Icons.Outlined.AutoStories,
            title = stringResource(R.string.home_empty_reading_title),
            body = stringResource(R.string.home_empty_reading_body),
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
          .padding(top = DipeSpacing.xs, bottom = DipeSpacing.md)
          .testTag("home_reading_book_${book.id}"),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      BookCoverArtwork(
        title = book.title,
        author = book.author,
        isFavorite = book.isFavorite,
        coverUri = book.coverUri,
        elevated = true,
        modifier =
          Modifier.width(if (widthClass == WindowWidthClass.COMPACT) 212.dp else 232.dp),
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
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
