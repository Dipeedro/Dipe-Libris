package com.example.feature.library

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTypographyScope
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.EditorialMetadataStrip
import com.example.core.designsystem.components.EditorialProgressBar
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.EditorialSubscreenTopBar
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.designsystem.components.contributorRoleLabel
import com.example.core.designsystem.components.copyMediaTypeLabel
import com.example.core.designsystem.components.editionFormatLabel
import com.example.core.designsystem.components.readingStatusAccentColor
import com.example.core.designsystem.components.readingStatusLabel
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.BibliographicMath
import com.example.core.model.BookCopy
import com.example.core.model.CopyMediaType
import com.example.core.model.Edition
import com.example.core.model.EditionFormat
import com.example.core.model.EditionWithBookCopies
import com.example.core.model.ReadingJourneySummary
import com.example.core.model.ReadingProgressMath
import com.example.core.model.ReadingStatus
import com.example.core.model.Work
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
  uiState: BookDetailUiState,
  widthClass: WindowWidthClass,
  onToggleFavorite: (Boolean) -> Unit,
  onUpdateStatus: (ReadingStatus) -> Unit,
  onOpenDiary: () -> Unit,
  onConfirmDelete: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  onSelectEdition: (Long) -> Unit = {},
  onUpdateWorkMetadata: (title: String, author: String) -> Unit = { _, _ -> },
  onAddEdition: (EditionEditorSubmission) -> Unit = {},
  onUpdateEdition: (edition: Edition, pickedCoverUri: Uri?, removeCover: Boolean) -> Unit = { _, _, _ -> },
  onSetPrimaryEdition: (Long) -> Unit = {},
  onDeleteEdition: (Long) -> Unit = {},
  onAddCopy: (editionId: Long, CopyEditorSubmission) -> Unit = { _, _ -> },
  onUpdateCopy: (BookCopy, setAsActiveReadingCopy: Boolean) -> Unit = { _, _ -> },
  onDeleteCopy: (Long) -> Unit = {},
  onStartJourney: (copyId: Long?, note: String?) -> Unit = { _, _ -> },
  onStartReread: (copyId: Long?, note: String?) -> Unit = { _, _ -> },
  onUpdateProgressByPage: (sessionId: Long, page: Int, note: String?) -> Unit = { _, _, _ -> },
  onUpdateProgressByPercent: (sessionId: Long, percent: Float, note: String?) -> Unit = { _, _, _ -> },
  onPauseJourney: (Long) -> Unit = {},
  onResumeJourney: (Long) -> Unit = {},
  onCompleteJourney: (Long) -> Unit = {},
  onCompleteJourneyWithUndo: (sessionId: Long, note: String?) -> Unit = { id, _ ->
    onCompleteJourney(id)
  },
  onProgressUpdated: () -> Unit = {},
  onSwitchActiveCopy: (sessionId: Long, copyId: Long) -> Unit = { _, _ -> },
  onCheckMetadataUpdates: () -> Unit = {},
  onAcceptSuggestion: (Long) -> Unit = {},
  onRejectSuggestion: (Long) -> Unit = {},
  onUpdateWorkDetails: (Work) -> Unit = {},
) {
  val language = LocalAppLanguage.current
  val resources = LocalContext.current.resources
  val horizontalPadding = DipeSpacing.horizontalMargin(widthClass)
  val scope = rememberCoroutineScope()

  var showDeleteConfirmation by remember { mutableStateOf(false) }
  var editionPendingDelete by remember { mutableStateOf<Edition?>(null) }
  var showWorkEditSheet by remember { mutableStateOf(false) }
  var showAddEditionSheet by remember { mutableStateOf(false) }
  var editingEdition by remember { mutableStateOf<Edition?>(null) }
  var addingCopyForEditionId by remember { mutableStateOf<Long?>(null) }
  var editingCopy by remember { mutableStateOf<BookCopy?>(null) }
  var showQuickProgressSheet by remember { mutableStateOf(false) }
  var startJourneyModeIsReread by remember { mutableStateOf<Boolean?>(null) }
  var showComparisonSheet by remember { mutableStateOf(false) }
  var showSuggestionsSheet by remember { mutableStateOf(false) }

  when (uiState) {
    BookDetailUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
          modifier =
            Modifier.widthIn(max = 820.dp)
              .fillMaxSize()
              .padding(horizontal = horizontalPadding, vertical = DipeSpacing.md),
        ) {
          EditorialSubscreenTopBar(
            title = null,
            backButtonTag = "book_detail_back_button",
            onNavigateBack = onNavigateBack,
            floatingButtonChrome = true,
          )
          Box(
            modifier = Modifier.fillMaxSize().testTag("book_detail_loading"),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
            ) {
              CircularProgressIndicator(strokeWidth = 2.dp)
              Text(
                text = stringResource(R.string.book_detail_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    BookDetailUiState.NotFound -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
          modifier =
            Modifier.widthIn(max = 820.dp)
              .fillMaxSize()
              .padding(horizontal = horizontalPadding, vertical = DipeSpacing.md),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.lg),
        ) {
          EditorialSubscreenTopBar(
            title = null,
            backButtonTag = "book_detail_back_button",
            onNavigateBack = onNavigateBack,
            floatingButtonChrome = true,
          )
          EditorialEmptyState(
            icon = Icons.AutoMirrored.Outlined.LibraryBooks,
            title = stringResource(R.string.book_detail_not_found_title),
            body = stringResource(R.string.book_detail_not_found_body),
            actionLabel = stringResource(R.string.book_detail_not_found_action),
            actionTestTag = "book_detail_not_found_back_button",
            onAction = onNavigateBack,
            modifier = Modifier.testTag("book_detail_not_found"),
          )
        }
      }
    }

    is BookDetailUiState.Loaded -> {
      val detail = uiState.detail
      val work = detail.work
      val editions = detail.editions
      val diaryNotes = uiState.diaryNotes
      val activeJourney = detail.activeJourney
      val focusedIndex =
        remember(editions, uiState.focusedEditionId) {
          editions.indexOfFirst { it.edition.id == uiState.focusedEditionId }.takeIf { it >= 0 } ?: 0
        }
      val focusedEditionWithCopies: EditionWithBookCopies? = editions.getOrNull(focusedIndex)
      val focusedEdition = focusedEditionWithCopies?.edition

      val pagerState =
        rememberPagerState(
          initialPage = focusedIndex,
          pageCount = { editions.size.coerceAtLeast(1) },
        )

      LaunchedEffect(pagerState.settledPage, editions) {
        val settledEdition = editions.getOrNull(pagerState.settledPage)?.edition
        if (settledEdition != null && settledEdition.id != uiState.focusedEditionId) {
          onSelectEdition(settledEdition.id)
        }
      }

      LaunchedEffect(focusedIndex, editions.size) {
        if (editions.isNotEmpty() && pagerState.currentPage != focusedIndex) {
          pagerState.animateScrollToPage(focusedIndex)
        }
      }

      val unspecified = stringResource(R.string.book_detail_unspecified)
      val pagesDisplay =
        remember(focusedEdition?.totalPages, language, unspecified) {
          focusedEdition?.totalPages?.let { LocaleManager.formatInteger(it, language) } ?: unspecified
        }
      val yearDisplay =
        remember(focusedEdition?.publicationYear, unspecified) {
          focusedEdition?.publicationYear?.toString() ?: unspecified
        }
      val editionDisplay =
        remember(focusedEdition?.publisherOrNote, unspecified) {
          focusedEdition?.publisherOrNote?.takeIf { it.isNotBlank() } ?: unspecified
        }

      val seriesDisplay =
        remember(focusedEdition?.seriesName, focusedEdition?.seriesVolume) {
          focusedEdition?.seriesName?.takeIf { it.isNotBlank() }?.let { series ->
            val vol = focusedEdition.seriesVolume?.takeIf { it.isNotBlank() }
            if (vol != null) "$series #$vol" else series
          }
        }

      val formatLabel = focusedEdition?.format?.let { editionFormatLabel(it) }
      val secondaryMetaLine =
        remember(focusedEdition, formatLabel, seriesDisplay) {
          listOfNotNull(
              focusedEdition?.editionLabel?.takeIf { it.isNotBlank() },
              formatLabel,
              focusedEdition?.language?.takeIf { it.isNotBlank() },
              focusedEdition?.translator?.takeIf { it.isNotBlank() },
              seriesDisplay,
              (focusedEdition?.isbn13 ?: focusedEdition?.isbn)?.takeIf { it.isNotBlank() }?.let { "ISBN $it" },
            )
            .joinToString(" · ")
        }

      val chipColors =
        FilterChipDefaults.filterChipColors(
          selectedContainerColor = MaterialTheme.colorScheme.primary,
          selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
          containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
          labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

      DipeTypographyScope(presetOverride = work.typographyPresetOverride) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
          LazyColumn(
            modifier =
              Modifier.widthIn(max = 820.dp).fillMaxSize().testTag("book_detail_lazy_column"),
            contentPadding = PaddingValues(bottom = DipeSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.xl),
          ) {
            // Soft-tinted full-bleed showcase stage with swipeable Editions HorizontalPager
            item(key = "detail_hero", contentType = "hero") {
              Column(
                modifier =
                  Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
              ) {
                Column(
                  modifier =
                    Modifier.fillMaxWidth()
                      .padding(
                        top = DipeSpacing.md,
                        bottom = DipeSpacing.lg,
                      ),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  EditorialSubscreenTopBar(
                    title = null,
                    backButtonTag = "book_detail_back_button",
                    onNavigateBack = onNavigateBack,
                    floatingButtonChrome = true,
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                    trailingContent = {
                      Surface(
                        onClick = { showWorkEditSheet = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
                        modifier =
                          Modifier.size(44.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("book_detail_edit_work_button"),
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.book_detail_edit_work),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                          )
                        }
                      }
                      Surface(
                        onClick = { onToggleFavorite(work.isFavorite) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
                        modifier =
                          Modifier.size(44.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("book_detail_favorite_button"),
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Icon(
                            imageVector =
                              if (work.isFavorite) Icons.Filled.Bookmark
                              else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(R.string.cd_toggle_favorite),
                            tint =
                              if (work.isFavorite) MaterialTheme.colorScheme.secondary
                              else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                          )
                        }
                      }
                    },
                  )

                  Spacer(modifier = Modifier.height(DipeSpacing.md))

                  val coverWidth = if (widthClass == WindowWidthClass.COMPACT) 168.dp else 196.dp
                  if (editions.size > 1) {
                    HorizontalPager(
                      state = pagerState,
                      contentPadding =
                        PaddingValues(
                          horizontal =
                            if (widthClass == WindowWidthClass.COMPACT) 88.dp else 160.dp
                        ),
                      pageSpacing = DipeSpacing.md,
                      modifier = Modifier.fillMaxWidth().testTag("book_detail_edition_pager"),
                    ) { page ->
                      val pageEdition = editions[page].edition
                      Box(
                        modifier =
                          Modifier.fillMaxWidth()
                            .testTag("book_detail_edition_cover_${pageEdition.id}"),
                        contentAlignment = Alignment.Center,
                      ) {
                        BookCoverArtwork(
                          title = work.title,
                          author = work.author,
                          isFavorite = work.isFavorite,
                          coverUri = pageEdition.coverUri,
                          elevated = true,
                          widthHintDp = coverWidth,
                          modifier = Modifier.width(coverWidth),
                        )
                      }
                    }
                  } else {
                    BookCoverArtwork(
                      title = work.title,
                      author = work.author,
                      isFavorite = work.isFavorite,
                      coverUri = focusedEdition?.coverUri,
                      elevated = true,
                      widthHintDp = coverWidth,
                      modifier = Modifier.width(coverWidth),
                    )
                  }

                  Spacer(modifier = Modifier.height(DipeSpacing.md))

                  // Quiet edition selector & actions bar
                  FlowRow(
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
                    verticalArrangement = Arrangement.Center,
                  ) {
                    if (editions.size > 1) {
                      Text(
                        text =
                          stringResource(
                            R.string.book_detail_editions_pager_indicator,
                            pagerState.currentPage + 1,
                            editions.size,
                          ),
                        style = LocalDipeTypography.current.monoMetadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                          Modifier.align(Alignment.CenterVertically)
                            .testTag("book_detail_edition_indicator"),
                      )
                    }
                    if (focusedEdition?.isPrimary == true && editions.size > 1) {
                      Text(
                        text = "· ${stringResource(R.string.book_detail_primary_edition_badge)}",
                        style = LocalDipeTypography.current.monoMetadata,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.align(Alignment.CenterVertically),
                      )
                    }
                  }

                  FlowRow(
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                    verticalArrangement = Arrangement.Center,
                  ) {
                    if (focusedEdition != null) {
                      TextButton(
                        onClick = { editingEdition = focusedEdition },
                        modifier =
                          Modifier.minimumInteractiveComponentSize()
                            .testTag("book_detail_edit_edition_button"),
                      ) {
                        Text(
                          text = stringResource(R.string.book_detail_edit_edition),
                          style = MaterialTheme.typography.labelLarge,
                        )
                      }
                      if (!focusedEdition.isPrimary && editions.size > 1) {
                        TextButton(
                          onClick = { onSetPrimaryEdition(focusedEdition.id) },
                          modifier =
                            Modifier.minimumInteractiveComponentSize()
                              .testTag("book_detail_set_primary_edition_button"),
                        ) {
                          Text(
                            text = stringResource(R.string.book_detail_set_primary_edition),
                            style = MaterialTheme.typography.labelLarge,
                          )
                        }
                      }
                    }
                    TextButton(
                      onClick = { showAddEditionSheet = true },
                      modifier =
                        Modifier.minimumInteractiveComponentSize()
                          .testTag("book_detail_add_edition_button"),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = stringResource(R.string.book_detail_add_edition),
                        style = MaterialTheme.typography.labelLarge,
                      )
                    }
                    if (editions.size >= 2) {
                      TextButton(
                        onClick = { showComparisonSheet = true },
                        modifier =
                          Modifier.minimumInteractiveComponentSize()
                            .testTag("book_detail_compare_editions_button"),
                      ) {
                        Icon(
                          imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
                          contentDescription = null,
                          modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                          text = stringResource(R.string.book_detail_compare_editions),
                          style = MaterialTheme.typography.labelLarge,
                        )
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(DipeSpacing.sm))

                  Text(
                    text = work.title,
                    style = LocalDipeTypography.current.editorialMasthead,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                  )
                  work.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = sub,
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      textAlign = TextAlign.Center,
                      modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                  }
                  work.originalTitle?.trim()?.takeIf { orig ->
                    orig.isNotEmpty() &&
                      BibliographicMath.normalizeBaseTitle(orig) !=
                        BibliographicMath.normalizeBaseTitle(work.title)
                  }?.let { orig ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = stringResource(R.string.book_detail_original_title, orig),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      textAlign = TextAlign.Center,
                      modifier =
                        Modifier.padding(horizontal = horizontalPadding)
                          .testTag("book_detail_original_title"),
                    )
                  }
                  Spacer(modifier = Modifier.height(DipeSpacing.xs))
                  Text(
                    text = work.author,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                  )
                  work.primaryGenre?.let { genre ->
                    Spacer(modifier = Modifier.height(DipeSpacing.sm))
                    Surface(
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                      Text(
                        text = genre.localizedName(language),
                        style = LocalDipeTypography.current.monoMetadata,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(DipeSpacing.lg))

                  EditorialMetadataStrip(
                    items =
                      listOf(
                        stringResource(R.string.book_detail_pages_label) to pagesDisplay,
                        stringResource(R.string.book_detail_year_label) to yearDisplay,
                        stringResource(R.string.book_detail_edition_label) to editionDisplay,
                      ),
                    modifier = Modifier.padding(horizontal = horizontalPadding).widthIn(max = 540.dp),
                  )

                  if (secondaryMetaLine.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(DipeSpacing.sm))
                    Text(
                      text = secondaryMetaLine,
                      style = LocalDipeTypography.current.monoMetadata,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      textAlign = TextAlign.Center,
                      modifier =
                        Modifier.padding(horizontal = horizontalPadding)
                          .testTag("book_detail_edition_secondary_meta"),
                    )
                  }

                  if (focusedEdition != null && editions.size > 1) {
                    Spacer(modifier = Modifier.height(DipeSpacing.xs))
                    TextButton(
                      onClick = { editionPendingDelete = focusedEdition },
                      modifier =
                        Modifier.minimumInteractiveComponentSize()
                          .testTag("book_detail_delete_edition_button"),
                    ) {
                      Text(
                        text = stringResource(R.string.book_detail_delete_edition),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(DipeSpacing.xs))
                  TextButton(
                    onClick = onCheckMetadataUpdates,
                    enabled = !uiState.isCheckingUpdates,
                    modifier =
                      Modifier.minimumInteractiveComponentSize()
                        .testTag("book_detail_check_updates_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.AutoAwesome,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text =
                        if (uiState.isCheckingUpdates) "…"
                        else stringResource(R.string.detail_check_updates),
                      style = MaterialTheme.typography.labelLarge,
                    )
                  }
                }
              }
            }

            if (detail.pendingSuggestions.isNotEmpty()) {
              item(key = "detail_pending_suggestions", contentType = "suggestions_banner") {
                EditorialSurfaceCard(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                  contentPadding = PaddingValues(DipeSpacing.md),
                  modifier =
                    Modifier.padding(horizontal = horizontalPadding)
                      .testTag("book_detail_suggestions_banner"),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text =
                        stringResource(
                          R.string.detail_suggestions_banner,
                          detail.pendingSuggestions.size,
                        ),
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.weight(1f),
                    )
                    Button(
                      onClick = { showSuggestionsSheet = true },
                      shape = CircleShape,
                      colors =
                        ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.secondary,
                          contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                      modifier = Modifier.testTag("book_detail_review_suggestions_button"),
                    ) {
                      Text(stringResource(R.string.detail_review_suggestions))
                    }
                  }
                }
              }
            }

            // Current reading progress & actions
            item(key = "detail_reading_journey", contentType = "reading_journey") {
              CurrentReadingJourneySection(
                activeJourney = activeJourney,
                hasPastJourneys = detail.journeys.isNotEmpty(),
                workStatus = work.status,
                focusedEditionId = focusedEdition?.id,
                onOpenProgress = { showQuickProgressSheet = true },
                onStartReading = { startJourneyModeIsReread = false },
                onStartReread = { startJourneyModeIsReread = true },
                onPause = { activeJourney?.let { onPauseJourney(it.session.id) } },
                onResume = { activeJourney?.let { onResumeJourney(it.session.id) } },
                onFinish = { activeJourney?.let { onCompleteJourney(it.session.id) } },
                onJumpToReadingEdition = { targetEditionId ->
                  val targetIdx = editions.indexOfFirst { it.edition.id == targetEditionId }
                  if (targetIdx >= 0) {
                    onSelectEdition(targetEditionId)
                    scope.launch { pagerState.animateScrollToPage(targetIdx) }
                  }
                },
                modifier = Modifier.padding(horizontal = horizontalPadding),
              )
            }

            // Reading status selector
            item(key = "detail_status", contentType = "status_selector") {
              Column(
                modifier = Modifier.padding(horizontal = horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
              ) {
                EditorialSectionHeader(title = stringResource(R.string.book_detail_status_label))
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
                ) {
                  ReadingStatus.entries.forEach { status ->
                    val selected = work.status == status
                    FilterChip(
                      selected = selected,
                      onClick = { onUpdateStatus(status) },
                      label = { Text(readingStatusLabel(status)) },
                      shape = CircleShape,
                      colors = chipColors,
                      border = null,
                      modifier = Modifier.testTag("book_detail_status_${status.name.lowercase()}"),
                    )
                  }
                }
              }
            }

            // Copies for focused edition
            if (focusedEdition != null) {
              item(key = "detail_copies", contentType = "copies_section") {
                val editionCopies = focusedEditionWithCopies.copies
                Column(
                  modifier = Modifier.padding(horizontal = horizontalPadding),
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
                ) {
                  EditorialSectionHeader(
                    title = stringResource(R.string.book_detail_copies_section),
                    actionLabel = stringResource(R.string.book_detail_add_copy),
                    actionTag = "book_detail_add_copy_button",
                    onAction = { addingCopyForEditionId = focusedEdition.id },
                  )

                  if (editionCopies.isEmpty()) {
                    Text(
                      text = stringResource(R.string.book_detail_no_copies),
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  } else {
                    Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
                      editionCopies.forEach { copy ->
                        val isActiveCopy = activeJourney?.copy?.id == copy.id
                        CopySummaryCard(
                          copy = copy,
                          isActiveReadingCopy = isActiveCopy,
                          canSwitchActiveCopy = activeJourney != null && !isActiveCopy,
                          onClick = { editingCopy = copy },
                          onSwitchToCopy = {
                            activeJourney?.let { onSwitchActiveCopy(it.session.id, copy.id) }
                          },
                        )
                      }
                    }
                  }
                }
              }
            }

            if (!work.activeSynopsis.isNullOrBlank()) {
              item(key = "detail_synopsis", contentType = "synopsis_section") {
                Column(
                  modifier =
                    Modifier.padding(horizontal = horizontalPadding)
                      .testTag("book_detail_synopsis_section"),
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
                ) {
                  EditorialSectionHeader(title = stringResource(R.string.detail_synopsis_section))
                  EditorialSurfaceCard(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentPadding = PaddingValues(DipeSpacing.md),
                  ) {
                    Text(
                      text = work.activeSynopsis,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              }
            }

            if (detail.contributors.isNotEmpty()) {
              item(key = "detail_contributors", contentType = "contributors_section") {
                Column(
                  modifier =
                    Modifier.padding(horizontal = horizontalPadding)
                      .testTag("book_detail_contributors_section"),
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
                ) {
                  EditorialSectionHeader(title = stringResource(R.string.detail_contributors_section))
                  FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                  ) {
                    detail.contributors.forEach { contrib ->
                      Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                      ) {
                        Text(
                          text = "${contrib.name} · ${contributorRoleLabel(contrib.role)}",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurface,
                          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                      }
                    }
                  }
                }
              }
            }

            // Diary notes preview (strictly book-scoped)
            item(key = "detail_notes_header", contentType = "section_header") {
              EditorialSectionHeader(
                title = stringResource(R.string.book_detail_notes_section),
                modifier = Modifier.padding(horizontal = horizontalPadding),
              )
            }

            if (diaryNotes.isEmpty()) {
              item(key = "detail_no_notes", contentType = "empty_notes") {
                Text(
                  text = stringResource(R.string.book_detail_no_notes),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = horizontalPadding),
                )
              }
            } else {
              val previewNotes = diaryNotes.take(3)
              items(
                items = previewNotes,
                key = { it.id },
                contentType = { "detail_note" },
              ) { note ->
                val formattedDate =
                  remember(note.createdAtEpochMillis, language) {
                    LocaleManager.formatShortDate(note.createdAtEpochMillis, language)
                  }
                EditorialSurfaceCard(
                  modifier = Modifier.padding(horizontal = horizontalPadding),
                  containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                  shape = MaterialTheme.shapes.medium,
                  contentPadding = PaddingValues(DipeSpacing.md),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
                  ) {
                    Box(
                      modifier =
                        Modifier.width(3.dp)
                          .fillMaxHeight()
                          .clip(CircleShape)
                          .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)),
                    )
                    Column(
                      modifier = Modifier.weight(1f),
                      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                    ) {
                      Text(
                        text = formattedDate,
                        style = LocalDipeTypography.current.monoMetadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                }
              }
            }

            item(key = "detail_open_diary_action", contentType = "diary_action") {
              Box(modifier = Modifier.padding(horizontal = horizontalPadding)) {
                Button(
                  onClick = onOpenDiary,
                  shape = CircleShape,
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.secondaryContainer,
                      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                  contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = 10.dp),
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("book_detail_open_diary_button"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(DipeSpacing.sm))
                  Text(
                    text = stringResource(R.string.book_detail_open_diary),
                    style = MaterialTheme.typography.labelLarge,
                  )
                }
              }
            }

            // Reading history & rereads timeline
            item(key = "detail_history", contentType = "reading_history") {
              Column(
                modifier = Modifier.padding(horizontal = horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
              ) {
                EditorialSectionHeader(title = stringResource(R.string.journey_section_history))
                if (detail.journeys.isEmpty()) {
                  Text(
                    text = stringResource(R.string.journey_no_history),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                } else {
                  Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
                    detail.journeys.forEach { journey ->
                      JourneyHistoryCard(
                        journey = journey,
                        language = language,
                        durationText =
                          LocaleManager.formatDuration(
                            ReadingProgressMath.effectiveDurationMillis(
                              startEpochMillis = journey.session.startEpochMillis,
                              endEpochMillis = journey.session.endEpochMillis,
                              pausedAtEpochMillis = journey.session.pausedAtEpochMillis,
                              accumulatedPausedMillis = journey.session.accumulatedPausedMillis,
                            ),
                            resources,
                          ),
                      )
                    }
                  }
                }
              }
            }

            item(key = "detail_delete", contentType = "delete_action") {
              Column(
                modifier = Modifier.padding(horizontal = horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
              ) {
                Spacer(modifier = Modifier.height(DipeSpacing.sm))
                OutlinedButton(
                  onClick = { showDeleteConfirmation = true },
                  enabled = !uiState.isDeleting,
                  shape = CircleShape,
                  border =
                    BorderStroke(
                      0.75.dp,
                      MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                    ),
                  contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm),
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("book_detail_delete_button"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(DipeSpacing.sm))
                  Text(
                    text = stringResource(R.string.book_detail_delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }
      }

      if (showWorkEditSheet) {
        WorkEditSheet(
          initialTitle = work.title,
          initialAuthor = work.author,
          initialOriginalTitle = work.originalTitle,
          onDismiss = { showWorkEditSheet = false },
          onSave = { newTitle, newAuthor ->
            onUpdateWorkMetadata(newTitle, newAuthor)
            showWorkEditSheet = false
          },
          onSaveWithOriginalTitle = { newTitle, newAuthor, newOriginalTitle ->
            val prevOrig = work.originalTitle?.trim()?.ifEmpty { null }
            val nextOrig = newOriginalTitle?.trim()?.ifEmpty { null }
            if (prevOrig != nextOrig) {
              onUpdateWorkDetails(
                work.copy(
                  title = newTitle,
                  author = newAuthor,
                  originalTitle = nextOrig,
                )
              )
            } else {
              onUpdateWorkMetadata(newTitle, newAuthor)
            }
            showWorkEditSheet = false
          },
        )
      }

      if (showAddEditionSheet) {
        EditionEditorSheet(
          workTitle = work.title,
          workAuthor = work.author,
          initialEdition = null,
          onDismiss = { showAddEditionSheet = false },
          onSave = { submission ->
            onAddEdition(submission)
            showAddEditionSheet = false
          },
        )
      }

      editingEdition?.let { targetEdition ->
        EditionEditorSheet(
          workTitle = work.title,
          workAuthor = work.author,
          initialEdition = targetEdition,
          onDismiss = { editingEdition = null },
          onSave = { submission ->
            onUpdateEdition(
              targetEdition.copy(
                publisherOrNote = submission.publisherOrNote,
                publicationYear = submission.publicationYear,
                totalPages = submission.totalPages,
                isbn = submission.isbn,
                language = submission.language,
                format = submission.format,
                translator = submission.translator,
                isPrimary = submission.isPrimary,
              ),
              submission.pickedCoverUri,
              submission.removeCover,
            )
            editingEdition = null
          },
        )
      }

      addingCopyForEditionId?.let { editionId ->
        CopyEditorSheet(
          initialCopy = null,
          hasActiveJourney = activeJourney != null,
          isCurrentlyActiveCopy = false,
          onDismiss = { addingCopyForEditionId = null },
          onSave = { submission ->
            onAddCopy(editionId, submission)
            addingCopyForEditionId = null
          },
        )
      }

      editingCopy?.let { targetCopy ->
        val isCurrentlyActive = activeJourney?.copy?.id == targetCopy.id
        CopyEditorSheet(
          initialCopy = targetCopy,
          hasActiveJourney = activeJourney != null,
          isCurrentlyActiveCopy = isCurrentlyActive,
          onDismiss = { editingCopy = null },
          onSave = { submission ->
            onUpdateCopy(
              targetCopy.copy(
                mediaType = submission.mediaType,
                label = submission.label,
                physicalFormat = submission.physicalFormat,
                locationOrPlatform = submission.locationOrPlatform,
                personalNote = submission.personalNote,
                condition = submission.condition,
                acquisitionOrigin = submission.acquisitionOrigin,
                customCopyCode = submission.customCopyCode,
                pricePaidMinorUnits = submission.pricePaidMinorUnits,
                currencyCode = submission.currencyCode,
              ),
              submission.setAsActiveReadingCopy,
            )
            editingCopy = null
          },
          onDeleteCopy = {
            onDeleteCopy(targetCopy.id)
            editingCopy = null
          },
        )
      }

      if (showQuickProgressSheet && activeJourney != null) {
        val totalPages =
          ReadingProgressMath.effectiveTotalPages(
            activeJourney.edition?.totalPages,
            activeJourney.session.totalPagesSnapshot,
          )
        QuickProgressSheet(
          bookTitle = work.title,
          initialPage = activeJourney.session.currentPage,
          totalPages = totalPages,
          initialPercent = activeJourney.session.progressPercent,
          onDismiss = { showQuickProgressSheet = false },
          onSavePage = { page, note ->
            onUpdateProgressByPage(activeJourney.session.id, page, note)
            showQuickProgressSheet = false
            onProgressUpdated()
          },
          onSavePercent = { percent, note ->
            onUpdateProgressByPercent(activeJourney.session.id, percent, note)
            showQuickProgressSheet = false
            onProgressUpdated()
          },
          onMarkFinished = { note ->
            onCompleteJourneyWithUndo(activeJourney.session.id, note)
            showQuickProgressSheet = false
          },
        )
      }

      startJourneyModeIsReread?.let { isReread ->
        StartReadingJourneySheet(
          isReread = isReread,
          editions = editions,
          preferredEditionId = focusedEdition?.id,
          onDismiss = { startJourneyModeIsReread = null },
          onStart = { copyId, note ->
            if (isReread) {
              onStartReread(copyId, note)
            } else {
              onStartJourney(copyId, note)
            }
            startJourneyModeIsReread = null
          },
        )
      }

      if (showComparisonSheet && editions.size >= 2) {
        EditionComparisonSheet(
          workTitle = work.title,
          workAuthor = work.author,
          editions = editions,
          onSelectEdition = { targetEditionId ->
            onSelectEdition(targetEditionId)
            val idx = editions.indexOfFirst { it.edition.id == targetEditionId }
            if (idx >= 0) {
              scope.launch { pagerState.animateScrollToPage(idx) }
            }
          },
          onDismiss = { showComparisonSheet = false },
        )
      }

      editionPendingDelete?.let { target ->
        AlertDialog(
          onDismissRequest = { editionPendingDelete = null },
          shape = MaterialTheme.shapes.large,
          containerColor = MaterialTheme.colorScheme.surface,
          title = {
            Text(
              text = stringResource(R.string.book_detail_delete_edition_confirm_title),
              style = MaterialTheme.typography.headlineSmall,
            )
          },
          text = {
            Text(
              text = stringResource(R.string.book_detail_delete_edition_confirm_message),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          confirmButton = {
            TextButton(
              onClick = {
                onDeleteEdition(target.id)
                editionPendingDelete = null
              },
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("edition_delete_confirm_button"),
            ) {
              Text(
                text = stringResource(R.string.book_delete_confirm_action),
                color = MaterialTheme.colorScheme.error,
              )
            }
          },
          dismissButton = {
            TextButton(
              onClick = { editionPendingDelete = null },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Text(stringResource(R.string.book_cancel_action))
            }
          },
        )
      }

      if (showDeleteConfirmation) {
        AlertDialog(
          onDismissRequest = { showDeleteConfirmation = false },
          shape = MaterialTheme.shapes.large,
          containerColor = MaterialTheme.colorScheme.surface,
          title = {
            Text(
              text = stringResource(R.string.book_delete_confirm_title),
              style = MaterialTheme.typography.headlineSmall,
            )
          },
          text = {
            Text(
              text = stringResource(R.string.book_delete_confirm_message),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          confirmButton = {
            TextButton(
              onClick = {
                showDeleteConfirmation = false
                onConfirmDelete()
              },
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("book_delete_confirm_button"),
            ) {
              Text(
                text = stringResource(R.string.book_delete_confirm_action),
                color = MaterialTheme.colorScheme.error,
              )
            }
          },
          dismissButton = {
            TextButton(
              onClick = { showDeleteConfirmation = false },
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("book_delete_cancel_button"),
            ) {
              Text(stringResource(R.string.book_cancel_action))
            }
          },
          modifier = Modifier.testTag("book_delete_confirm_dialog"),
        )
      }

      if (showSuggestionsSheet && detail.pendingSuggestions.isNotEmpty()) {
        MetadataSuggestionsSheet(
          suggestions = detail.pendingSuggestions,
          onAccept = onAcceptSuggestion,
          onReject = onRejectSuggestion,
          onDismiss = { showSuggestionsSheet = false },
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrentReadingJourneySection(
  activeJourney: ReadingJourneySummary?,
  hasPastJourneys: Boolean,
  workStatus: ReadingStatus,
  focusedEditionId: Long?,
  onOpenProgress: () -> Unit,
  onStartReading: () -> Unit,
  onStartReread: () -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onFinish: () -> Unit,
  onJumpToReadingEdition: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
  ) {
    EditorialSectionHeader(title = stringResource(R.string.journey_section_current))

    if (activeJourney != null) {
      val session = activeJourney.session
      val totalPages =
        ReadingProgressMath.effectiveTotalPages(
          activeJourney.edition?.totalPages,
          session.totalPagesSnapshot,
        )
      val progressText =
        if (totalPages != null) {
          stringResource(
            R.string.journey_page_of_total,
            LocaleManager.formatInteger(session.currentPage, language),
            LocaleManager.formatInteger(totalPages, language),
          )
        } else {
          stringResource(
            R.string.journey_page_only,
            LocaleManager.formatInteger(session.currentPage, language),
          )
        }

      EditorialSurfaceCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentPadding = PaddingValues(DipeSpacing.md),
        modifier = Modifier.testTag("book_detail_active_journey_card"),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = readingStatusLabel(session.status),
                style = MaterialTheme.typography.titleSmall,
                color = readingStatusAccentColor(session.status),
              )
              if (session.rereadNumber > 1) {
                Text(
                  text =
                    "· ${stringResource(R.string.journey_reread_label, session.rereadNumber)}",
                  style = LocalDipeTypography.current.monoMetadata,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
            }
            Text(
              text = LocaleManager.formatPercent(session.progressPercent, language),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.secondary,
            )
          }

          EditorialProgressBar(progressPercent = session.progressPercent, height = 4.dp)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = progressText,
              style = LocalDipeTypography.current.monoMetadata,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            activeJourney.copy?.let { copy ->
              val copyLabel =
                copy.label?.takeIf { it.isNotBlank() } ?: copyMediaTypeLabel(copy.mediaType)
              Text(
                text = copyLabel,
                style = LocalDipeTypography.current.monoMetadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          val readingEdition = activeJourney.edition
          if (readingEdition != null && focusedEditionId != null && readingEdition.id != focusedEditionId) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text =
                  stringResource(
                    R.string.journey_other_edition_notice,
                    readingEdition.publisherOrNote?.takeIf { it.isNotBlank() }
                      ?: readingEdition.publicationYear?.toString()
                      ?: stringResource(R.string.book_detail_edition_label),
                  ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
              )
              TextButton(
                onClick = { onJumpToReadingEdition(readingEdition.id) },
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("book_detail_jump_reading_edition_button"),
              ) {
                Text(stringResource(R.string.book_detail_jump_to_reading_edition))
              }
            }
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
          ) {
            Button(
              onClick = onOpenProgress,
              shape = CircleShape,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.secondary,
                  contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
              contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.xs),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("book_detail_update_progress_button"),
            ) {
              Text(
                text = stringResource(R.string.journey_update_progress),
                style = MaterialTheme.typography.labelLarge,
              )
            }

            if (session.status == ReadingStatus.READING) {
              OutlinedButton(
                onClick = onPause,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.xs),
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("book_detail_pause_button"),
              ) {
                Text(stringResource(R.string.journey_pause))
              }
            } else if (session.status == ReadingStatus.PAUSED) {
              OutlinedButton(
                onClick = onResume,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.xs),
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("book_detail_resume_button"),
              ) {
                Text(stringResource(R.string.journey_resume))
              }
            }

            OutlinedButton(
              onClick = onFinish,
              shape = CircleShape,
              contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.xs),
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("book_detail_finish_button"),
            ) {
              Text(stringResource(R.string.journey_finish))
            }
          }
        }
      }
    } else {
      val showReread = hasPastJourneys || workStatus == ReadingStatus.FINISHED
      Row(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
        Button(
          onClick = if (showReread) onStartReread else onStartReading,
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
          contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = 10.dp),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag(
                if (showReread) "book_detail_start_reread_button"
                else "book_detail_start_reading_button"
              ),
        ) {
          Text(
            text =
              stringResource(
                if (showReread) R.string.journey_start_reread else R.string.journey_start_reading
              ),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

@Composable
private fun CopySummaryCard(
  copy: BookCopy,
  isActiveReadingCopy: Boolean,
  canSwitchActiveCopy: Boolean,
  onClick: () -> Unit,
  onSwitchToCopy: () -> Unit,
) {
  val title = copy.label?.takeIf { it.isNotBlank() } ?: copyMediaTypeLabel(copy.mediaType)
  val metaParts =
    listOfNotNull(
      copyMediaTypeLabel(copy.mediaType),
      copy.locationOrPlatform?.takeIf { it.isNotBlank() },
      copy.physicalFormat?.takeIf { it.isNotBlank() },
    )
  EditorialSurfaceCard(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    shape = MaterialTheme.shapes.medium,
    contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = 12.dp),
    onClick = onClick,
    modifier = Modifier.testTag("book_detail_copy_${copy.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector =
          if (copy.mediaType == CopyMediaType.DIGITAL) Icons.Outlined.TabletAndroid
          else Icons.AutoMirrored.Outlined.MenuBook,
        contentDescription = null,
        tint =
          if (isActiveReadingCopy) MaterialTheme.colorScheme.secondary
          else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = metaParts.joinToString(" · "),
          style = LocalDipeTypography.current.monoMetadata,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        copy.personalNote?.takeIf { it.isNotBlank() }?.let { note ->
          Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (isActiveReadingCopy) {
        Text(
          text = stringResource(R.string.copy_active_reading_badge),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.testTag("book_detail_active_copy_badge_${copy.id}"),
        )
      } else if (canSwitchActiveCopy) {
        TextButton(
          onClick = onSwitchToCopy,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("book_detail_read_copy_${copy.id}"),
        ) {
          Text(stringResource(R.string.copy_set_active_reading))
        }
      }
    }
  }
}

@Composable
private fun JourneyHistoryCard(
  journey: ReadingJourneySummary,
  language: com.example.core.model.AppLanguage,
  durationText: String,
) {
  val session = journey.session
  val startStr = remember(session.startEpochMillis, language) {
    LocaleManager.formatShortDate(session.startEpochMillis, language)
  }
  val endStr = remember(session.endEpochMillis, language) {
    session.endEpochMillis?.let { LocaleManager.formatShortDate(it, language) }
  }
  val dateSpan =
    if (endStr != null) stringResource(R.string.journey_finished_span, startStr, endStr)
    else stringResource(R.string.journey_started_on, startStr)

  val copyDesc =
    journey.copy?.let { copy ->
      val label = copy.label?.takeIf { it.isNotBlank() } ?: copyMediaTypeLabel(copy.mediaType)
      val ed = journey.edition?.publisherOrNote?.takeIf { it.isNotBlank() }
      if (ed != null) "$label · $ed" else label
    } ?: stringResource(R.string.copy_archived_fallback)

  EditorialSurfaceCard(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    shape = MaterialTheme.shapes.medium,
    contentPadding = PaddingValues(DipeSpacing.md),
    modifier = Modifier.testTag("book_detail_journey_${session.id}"),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            "${stringResource(R.string.journey_reread_label, session.rereadNumber)} · ${readingStatusLabel(session.status)}",
          style = MaterialTheme.typography.titleSmall,
          color = readingStatusAccentColor(session.status),
        )
        Text(
          text = LocaleManager.formatPercent(session.progressPercent, language),
          style = LocalDipeTypography.current.monoMetadata,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Text(
        text = "$dateSpan · $durationText",
        style = LocalDipeTypography.current.monoMetadata,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Text(
        text = copyDesc,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (journey.logs.isNotEmpty()) {
        Spacer(modifier = Modifier.height(DipeSpacing.xxs))
        val updatesLabel =
          pluralStringResource(
            R.plurals.progress_updates_count,
            journey.logs.size,
            journey.logs.size,
          )
        Text(
          text = updatesLabel,
          style = LocalDipeTypography.current.monoMetadata,
          color = MaterialTheme.colorScheme.secondary,
        )
        journey.logs.take(3).forEach { log ->
          val logDate = LocaleManager.formatShortDate(log.recordedAtEpochMillis, language)
          val logPage = log.pageNumber?.let { "p. ${LocaleManager.formatInteger(it, language)}" }
          val logPct = LocaleManager.formatPercent(log.progressPercent, language)
          val line =
            listOfNotNull(logDate, logPage, logPct, log.note?.takeIf { it.isNotBlank() })
              .joinToString(" · ")
          Text(
            text = line,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
