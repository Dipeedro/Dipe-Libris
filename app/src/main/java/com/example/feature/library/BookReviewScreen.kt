package com.example.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.EditorialSubscreenTopBar
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.contributorRoleLabel
import com.example.core.designsystem.components.copyConditionLabel
import com.example.core.designsystem.components.copyMediaTypeLabel
import com.example.core.designsystem.components.copyOriginLabel
import com.example.core.designsystem.components.editionFormatLabel
import com.example.core.designsystem.components.readingStatusLabel
import com.example.core.designsystem.components.rememberDirectGalleryCoverPicker
import com.example.core.di.BookReviewMode
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.ContributorRole
import com.example.core.model.CopyCondition
import com.example.core.model.CopyMediaType
import com.example.core.model.CopyOrigin
import com.example.core.model.EditionFormat
import com.example.core.model.ReadingStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookReviewScreen(
  uiState: BookReviewUiState,
  widthClass: WindowWidthClass,
  viewModel: BookReviewViewModel,
  onNavigateBack: () -> Unit,
  onSavedWork: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  val horizontalPadding = DipeSpacing.horizontalMargin(widthClass)
  var showCustomGenreDialog by remember { mutableStateOf(false) }
  var showSynopsisPickerDialog by remember { mutableStateOf(false) }
  var coverPickerError by remember { mutableStateOf(false) }

  val launchGalleryPicker =
    rememberDirectGalleryCoverPicker(
      onUriPicked = { uri ->
        coverPickerError = false
        viewModel.selectLocalCover(uri)
      },
      onPickerUnavailable = { coverPickerError = true },
    )

  val screenTitle =
    when (uiState.mode) {
      BookReviewMode.IMPORTED -> stringResource(R.string.review_screen_title_imported)
      BookReviewMode.MANUAL -> stringResource(R.string.review_screen_title_manual)
      BookReviewMode.ADD_EDITION_TO_WORK,
      BookReviewMode.ADD_COPY_TO_EDITION -> stringResource(R.string.review_screen_title_new_edition)
    }

  val textFieldColors =
    OutlinedTextFieldDefaults.colors(
      focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
      unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
      focusedBorderColor = MaterialTheme.colorScheme.secondary,
    )

  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = MaterialTheme.colorScheme.primary,
      selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
      containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

  val previewCoverUri: String? =
    when {
      uiState.useTypographicCover -> null
      uiState.selectedLocalCoverUri != null -> uiState.selectedLocalCoverUri.toString()
      else -> uiState.selectedRemoteCoverUrl
    }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier = Modifier.widthIn(max = 880.dp).fillMaxSize().imePadding().testTag("book_review_screen")
    ) {
      LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("book_review_lazy_column"),
        contentPadding =
          PaddingValues(
            start = horizontalPadding,
            end = horizontalPadding,
            top = DipeSpacing.md,
            bottom = DipeSpacing.xl,
          ),
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
      ) {
        item(key = "review_top_bar") {
          EditorialSubscreenTopBar(
            title = screenTitle,
            backButtonTag = "review_back_button",
            onNavigateBack = onNavigateBack,
            showBottomDivider = true,
          )
        }

        uiState.validationErrorRes?.let { errorRes ->
          item(key = "review_validation_error") {
            EditorialSurfaceCard(
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentPadding = PaddingValues(DipeSpacing.md),
              modifier = Modifier.testTag("review_validation_error"),
            ) {
              Text(
                text = stringResource(errorRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
            }
          }
        }

        // 1. Work & Contributors
        item(key = "review_section_work") {
          val isExpanded = ReviewSection.WORK_CONTRIBUTORS in uiState.expandedSections
          ReviewAccordionCard(
            title = stringResource(R.string.review_section_work_contributors),
            summary =
              listOfNotNull(
                  uiState.title.takeIf { it.isNotBlank() },
                  uiState.contributors.firstOrNull()?.name?.takeIf { it.isNotBlank() },
                )
                .joinToString(" — ")
                .ifBlank { null },
            expanded = isExpanded,
            headerTag = "review_section_header_work",
            onToggle = { viewModel.toggleSection(ReviewSection.WORK_CONTRIBUTORS) },
          ) {
            OutlinedTextField(
              value = uiState.title,
              onValueChange = viewModel::setTitle,
              label = { Text(stringResource(R.string.book_field_title)) },
              singleLine = true,
              shape = MaterialTheme.shapes.medium,
              colors = textFieldColors,
              modifier = Modifier.fillMaxWidth().testTag("review_title_input"),
            )
            OutlinedTextField(
              value = uiState.subtitle,
              onValueChange = viewModel::setSubtitle,
              label = { Text(stringResource(R.string.review_field_subtitle)) },
              singleLine = true,
              shape = MaterialTheme.shapes.medium,
              colors = textFieldColors,
              modifier = Modifier.fillMaxWidth().testTag("review_subtitle_input"),
            )
            OutlinedTextField(
              value = uiState.originalTitle,
              onValueChange = viewModel::setOriginalTitle,
              label = { Text(stringResource(R.string.review_field_original_title)) },
              singleLine = true,
              shape = MaterialTheme.shapes.medium,
              colors = textFieldColors,
              modifier = Modifier.fillMaxWidth().testTag("review_original_title_input"),
            )

            Text(
              text = stringResource(R.string.review_contributors_label),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            uiState.contributors.forEachIndexed { idx, row ->
              ContributorEditorRow(
                row = row,
                index = idx,
                canRemove = uiState.contributors.size > 1,
                textFieldColors = textFieldColors,
                onNameChange = { viewModel.updateContributorName(row.localKey, it) },
                onRoleChange = { viewModel.updateContributorRole(row.localKey, it) },
                onRemove = { viewModel.removeContributorRow(row.localKey) },
              )
            }

            TextButton(
              onClick = viewModel::addContributorRow,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("review_add_contributor_button"),
            ) {
              Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.review_add_contributor))
            }
          }
        }

        // 2. Cover
        item(key = "review_section_cover") {
          val isExpanded = ReviewSection.COVER in uiState.expandedSections
          ReviewAccordionCard(
            title = stringResource(R.string.review_section_cover),
            summary =
              when {
                uiState.useTypographicCover -> stringResource(R.string.review_cover_no_photo)
                uiState.selectedLocalCoverUri != null -> stringResource(R.string.cover_pick_photo)
                uiState.selectedRemoteCoverUrl != null -> uiState.coverSource
                else -> null
              },
            expanded = isExpanded,
            headerTag = "review_section_header_cover",
            onToggle = { viewModel.toggleSection(ReviewSection.COVER) },
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              BookCoverArtwork(
                title = uiState.title.ifBlank { "Dipe Libris" },
                author = uiState.contributors.firstOrNull()?.name.orEmpty(),
                isFavorite = false,
                coverUri = previewCoverUri,
                compact = previewCoverUri == null,
                elevated = true,
                modifier = Modifier.width(84.dp),
              )
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
              ) {
                OutlinedButton(
                  onClick = launchGalleryPicker,
                  shape = CircleShape,
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("review_pick_cover_button"),
                ) {
                  Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(DipeSpacing.xs))
                  Text(stringResource(R.string.cover_pick_photo))
                }
                TextButton(
                  onClick = viewModel::chooseTypographicCover,
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("review_no_cover_button"),
                ) {
                  Text(stringResource(R.string.review_cover_no_photo))
                }
                if (coverPickerError) {
                  Text(
                    text = stringResource(R.string.cover_picker_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }

            if (uiState.coverCandidates.isNotEmpty()) {
              Text(
                text = stringResource(R.string.review_cover_alternatives),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              LazyRow(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
                itemsIndexed(
                  items = uiState.coverCandidates,
                  key = { idx, c -> "${c.imageUrl}_$idx" },
                ) { idx, candidate ->
                  val isSelected =
                    !uiState.useTypographicCover &&
                      uiState.selectedLocalCoverUri == null &&
                      uiState.selectedRemoteCoverUrl == candidate.imageUrl
                  Surface(
                    onClick = { viewModel.selectRemoteCover(candidate) },
                    shape = MaterialTheme.shapes.medium,
                    border =
                      BorderStroke(
                        width = if (isSelected) 2.dp else 0.5.dp,
                        color =
                          if (isSelected) MaterialTheme.colorScheme.secondary
                          else MaterialTheme.colorScheme.outlineVariant,
                      ),
                    modifier = Modifier.testTag("review_cover_candidate_$idx"),
                  ) {
                    Column(
                      modifier = Modifier.padding(DipeSpacing.xs),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      BookCoverArtwork(
                        title = uiState.title.ifBlank { "Cover" },
                        author = "",
                        isFavorite = false,
                        coverUri = candidate.imageUrl,
                        compact = true,
                        modifier = Modifier.width(58.dp),
                      )
                      if (candidate.matchesIsbn) {
                        Text(
                          text = stringResource(R.string.review_cover_badge_isbn),
                          style = LocalDipeTypography.current.monoMetadata,
                          color = MaterialTheme.colorScheme.secondary,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // 3. Edition
        item(key = "review_section_edition") {
          val isExpanded = ReviewSection.EDITION in uiState.expandedSections
          ReviewAccordionCard(
            title = stringResource(R.string.review_section_edition),
            summary =
              listOfNotNull(
                  uiState.publisher.takeIf { it.isNotBlank() },
                  uiState.publicationYear.takeIf { it.isNotBlank() },
                  uiState.isbn13.takeIf { it.isNotBlank() },
                )
                .joinToString(" · ")
                .ifBlank { null },
            expanded = isExpanded,
            headerTag = "review_section_header_edition",
            onToggle = { viewModel.toggleSection(ReviewSection.EDITION) },
          ) {
            OutlinedTextField(
              value = uiState.publisher,
              onValueChange = viewModel::setPublisher,
              label = { Text(stringResource(R.string.review_field_publisher)) },
              singleLine = true,
              shape = MaterialTheme.shapes.medium,
              colors = textFieldColors,
              modifier = Modifier.fillMaxWidth().testTag("review_publisher_input"),
            )
            OutlinedTextField(
              value = uiState.editionLabel,
              onValueChange = viewModel::setEditionLabel,
              label = { Text(stringResource(R.string.review_field_edition_label)) },
              singleLine = true,
              shape = MaterialTheme.shapes.medium,
              colors = textFieldColors,
              modifier = Modifier.fillMaxWidth().testTag("review_edition_label_input"),
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            ) {
              OutlinedTextField(
                value = uiState.publicationYear,
                onValueChange = viewModel::setPublicationYear,
                label = { Text(stringResource(R.string.book_field_year)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.medium,
                colors = textFieldColors,
                modifier = Modifier.weight(1f).testTag("review_year_input"),
              )
              OutlinedTextField(
                value = uiState.totalPages,
                onValueChange = viewModel::setTotalPages,
                label = { Text(stringResource(R.string.book_field_pages)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.medium,
                colors = textFieldColors,
                modifier = Modifier.weight(1f).testTag("review_pages_input"),
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            ) {
              OutlinedTextField(
                value = uiState.isbn13,
                onValueChange = viewModel::setIsbn13,
                label = { Text(stringResource(R.string.review_field_isbn13)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = textFieldColors,
                modifier = Modifier.weight(1f).testTag("review_isbn13_input"),
              )
              OutlinedTextField(
                value = uiState.isbn10,
                onValueChange = viewModel::setIsbn10,
                label = { Text(stringResource(R.string.review_field_isbn10)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = textFieldColors,
                modifier = Modifier.weight(1f).testTag("review_isbn10_input"),
              )
            }
            OutlinedTextField(
              value = uiState.language,
              onValueChange = viewModel::setLanguage,
              label = { Text(stringResource(R.string.book_field_language)) },
              singleLine = true,
              shape = MaterialTheme.shapes.medium,
              colors = textFieldColors,
              modifier = Modifier.fillMaxWidth().testTag("review_language_input"),
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            ) {
              OutlinedTextField(
                value = uiState.seriesName,
                onValueChange = viewModel::setSeriesName,
                label = { Text(stringResource(R.string.review_field_series)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = textFieldColors,
                modifier = Modifier.weight(2f).testTag("review_series_input"),
              )
              OutlinedTextField(
                value = uiState.seriesVolume,
                onValueChange = viewModel::setSeriesVolume,
                label = { Text(stringResource(R.string.review_field_volume)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = textFieldColors,
                modifier = Modifier.weight(1f).testTag("review_volume_input"),
              )
            }
            Text(
              text = stringResource(R.string.book_field_format),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
              verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
            ) {
              EditionFormat.entries.forEach { format ->
                val selected = uiState.format == format
                FilterChip(
                  selected = selected,
                  onClick = { viewModel.toggleFormat(format) },
                  label = { Text(editionFormatLabel(format)) },
                  shape = CircleShape,
                  colors = chipColors,
                  modifier = Modifier.testTag("review_format_${format.name.lowercase()}"),
                )
              }
            }
          }
        }

        // 4. Genre & Categories
        item(key = "review_section_genre") {
          val isExpanded = ReviewSection.GENRE in uiState.expandedSections
          val selectedGenreName =
            uiState.availableGenres
              .firstOrNull { it.id == uiState.selectedGenreId }
              ?.localizedName(language)
          ReviewAccordionCard(
            title = stringResource(R.string.review_section_genre),
            summary = selectedGenreName,
            expanded = isExpanded,
            headerTag = "review_section_header_genre",
            onToggle = { viewModel.toggleSection(ReviewSection.GENRE) },
          ) {
            Text(
              text = stringResource(R.string.review_genre_primary_label),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
              verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
            ) {
              uiState.availableGenres.forEach { genre ->
                val selected = uiState.selectedGenreId == genre.id
                val isSuggested = uiState.suggestedGenreId == genre.id
                FilterChip(
                  selected = selected,
                  onClick = { viewModel.selectPrimaryGenre(genre.id) },
                  label = {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      if (isSuggested) {
                        Icon(
                          Icons.Outlined.AutoAwesome,
                          contentDescription = stringResource(R.string.review_genre_suggested),
                          modifier = Modifier.size(14.dp),
                        )
                      }
                      Text(genre.localizedName(language))
                    }
                  },
                  shape = CircleShape,
                  colors = chipColors,
                  modifier = Modifier.testTag("review_genre_${genre.slug}"),
                )
              }
              OutlinedButton(
                onClick = { showCustomGenreDialog = true },
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("review_create_genre_button"),
              ) {
                Text(stringResource(R.string.review_genre_create_new))
              }
            }

            if (uiState.secondaryCategories.isNotEmpty()) {
              Text(
                text = stringResource(R.string.review_categories_secondary),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = uiState.secondaryCategories.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // 5. Synopsis
        item(key = "review_section_synopsis") {
          val isExpanded = ReviewSection.SYNOPSIS in uiState.expandedSections
          ReviewAccordionCard(
            title = stringResource(R.string.review_section_synopsis),
            summary =
              uiState.synopsis.takeIf { it.isNotBlank() }?.take(64)?.let {
                if (uiState.synopsis.length > 64) "$it…" else it
              },
            expanded = isExpanded,
            headerTag = "review_section_header_synopsis",
            onToggle = { viewModel.toggleSection(ReviewSection.SYNOPSIS) },
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
                uiState.synopsisSource?.let { src ->
                  Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Text(
                      text = stringResource(R.string.review_synopsis_source_tag, src),
                      style = LocalDipeTypography.current.monoMetadata,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                  }
                }
                if (uiState.isSynopsisCustomized) {
                  Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                      text = stringResource(R.string.review_synopsis_customized),
                      style = LocalDipeTypography.current.monoMetadata,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                  }
                }
              }
              if (uiState.synopsisCandidates.size > 1) {
                TextButton(
                  onClick = { showSynopsisPickerDialog = true },
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("review_synopsis_alternatives_button"),
                ) {
                  Text(
                    stringResource(
                      R.string.review_synopsis_alternatives,
                      uiState.synopsisCandidates.size,
                    )
                  )
                }
              }
            }

            OutlinedTextField(
              value = uiState.synopsis,
              onValueChange = viewModel::setSynopsis,
              label = { Text(stringResource(R.string.review_synopsis_label)) },
              minLines = 3,
              maxLines = 8,
              shape = MaterialTheme.shapes.medium,
              colors = textFieldColors,
              modifier = Modifier.fillMaxWidth().testTag("review_synopsis_input"),
            )
          }
        }

        // 6. Copy
        item(key = "review_section_copy") {
          val isExpanded = ReviewSection.COPY in uiState.expandedSections
          ReviewAccordionCard(
            title = stringResource(R.string.review_section_copy),
            summary =
              listOfNotNull(
                  copyMediaTypeLabel(uiState.copyMediaType),
                  readingStatusLabel(uiState.readingStatus),
                  uiState.copyLocation.takeIf { it.isNotBlank() },
                )
                .joinToString(" · "),
            expanded = isExpanded,
            headerTag = "review_section_header_copy",
            onToggle = { viewModel.toggleSection(ReviewSection.COPY) },
          ) {
            Text(
              text = stringResource(R.string.book_field_copy_type),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
              CopyMediaType.entries.forEach { mediaType ->
                val selected = uiState.copyMediaType == mediaType
                FilterChip(
                  selected = selected,
                  onClick = { viewModel.setCopyMediaType(mediaType) },
                  label = { Text(copyMediaTypeLabel(mediaType)) },
                  shape = CircleShape,
                  colors = chipColors,
                  modifier = Modifier.testTag("review_copy_media_${mediaType.name.lowercase()}"),
                )
              }
            }

            if (uiState.mode != BookReviewMode.ADD_COPY_TO_EDITION && uiState.mode != BookReviewMode.ADD_EDITION_TO_WORK) {
              Text(
                text = stringResource(R.string.book_field_status),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
              ) {
                ReadingStatus.entries.forEach { status ->
                  val selected = uiState.readingStatus == status
                  FilterChip(
                    selected = selected,
                    onClick = { viewModel.setReadingStatus(status) },
                    label = { Text(readingStatusLabel(status)) },
                    shape = CircleShape,
                    colors = chipColors,
                    modifier = Modifier.testTag("review_status_${status.name.lowercase()}"),
                  )
                }
              }
            }

            OutlinedTextField(
              value = uiState.copyLabel,
              onValueChange = viewModel::setCopyLabel,
              label = { Text(stringResource(R.string.book_field_copy_label)) },
              singleLine = true,
              shape = MaterialTheme.shapes.medium,
              colors = textFieldColors,
              modifier = Modifier.fillMaxWidth().testTag("review_copy_label_input"),
            )
            OutlinedTextField(
              value = uiState.copyLocation,
              onValueChange = viewModel::setCopyLocation,
              label = { Text(stringResource(R.string.book_field_copy_location)) },
              singleLine = true,
              shape = MaterialTheme.shapes.medium,
              colors = textFieldColors,
              modifier = Modifier.fillMaxWidth().testTag("review_copy_location_input"),
            )

            // Collapsible Acquisition & Collection block
            TextButton(
              onClick = viewModel::toggleCollectorDetails,
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("review_collector_details_toggle"),
            ) {
              Icon(
                imageVector =
                  if (uiState.collectorDetailsExpanded) Icons.Outlined.ExpandLess
                  else Icons.Outlined.ExpandMore,
                contentDescription = null,
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.review_copy_collector_toggle))
            }

            AnimatedVisibility(visible = uiState.collectorDetailsExpanded) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
              ) {
                Text(
                  text = stringResource(R.string.review_copy_condition),
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                ) {
                  CopyCondition.entries.forEach { cond ->
                    val selected = uiState.copyCondition == cond
                    FilterChip(
                      selected = selected,
                      onClick = { viewModel.toggleCopyCondition(cond) },
                      label = { Text(copyConditionLabel(cond)) },
                      shape = CircleShape,
                      colors = chipColors,
                    )
                  }
                }

                Text(
                  text = stringResource(R.string.review_copy_origin),
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                ) {
                  CopyOrigin.entries.forEach { origin ->
                    val selected = uiState.copyOrigin == origin
                    FilterChip(
                      selected = selected,
                      onClick = { viewModel.toggleCopyOrigin(origin) },
                      label = { Text(copyOriginLabel(origin)) },
                      shape = CircleShape,
                      colors = chipColors,
                    )
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
                ) {
                  OutlinedTextField(
                    value = uiState.copyPriceText,
                    onValueChange = viewModel::setCopyPriceText,
                    label = { Text(stringResource(R.string.review_copy_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = textFieldColors,
                    modifier = Modifier.weight(1f).testTag("review_copy_price_input"),
                  )
                  OutlinedTextField(
                    value = uiState.copyCustomCode,
                    onValueChange = viewModel::setCopyCustomCode,
                    label = { Text(stringResource(R.string.review_copy_code)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = textFieldColors,
                    modifier = Modifier.weight(1f).testTag("review_copy_code_input"),
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  val dateText =
                    uiState.copyAcquisitionEpochMillis?.let {
                      LocaleManager.formatShortDate(it, language)
                    }
                  Text(
                    text = dateText ?: stringResource(R.string.book_detail_unspecified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (uiState.copyAcquisitionEpochMillis == null) {
                    TextButton(onClick = viewModel::setCopyAcquiredToday) {
                      Text(stringResource(R.string.review_copy_acquired_today))
                    }
                  } else {
                    TextButton(onClick = viewModel::clearCopyAcquiredDate) {
                      Text(stringResource(R.string.review_copy_acquired_clear))
                    }
                  }
                }

                OutlinedTextField(
                  value = uiState.copyPersonalNote,
                  onValueChange = viewModel::setCopyPersonalNote,
                  label = { Text(stringResource(R.string.copy_field_note)) },
                  maxLines = 3,
                  shape = MaterialTheme.shapes.medium,
                  colors = textFieldColors,
                  modifier = Modifier.fillMaxWidth().testTag("review_copy_note_input"),
                )
              }
            }
          }
        }
      }

      HairlineDivider()
      Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = horizontalPadding, vertical = DipeSpacing.md),
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(
            onClick = onNavigateBack,
            enabled = !uiState.isSaving,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("review_cancel_button"),
          ) {
            Text(stringResource(R.string.book_cancel_action))
          }
          Spacer(modifier = Modifier.weight(1f))
          Button(
            onClick = { viewModel.save(onSavedWork) },
            enabled = !uiState.isSaving,
            shape = CircleShape,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
              ),
            contentPadding = PaddingValues(horizontal = DipeSpacing.xl, vertical = DipeSpacing.sm),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("review_save_button"),
          ) {
            if (uiState.isSaving) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSecondary,
              )
              Spacer(modifier = Modifier.width(DipeSpacing.xs))
            }
            Text(stringResource(R.string.review_save_catalog))
          }
        }
      }
    }
  }

  if (showCustomGenreDialog) {
    CustomGenreDialog(
      onDismiss = { showCustomGenreDialog = false },
      onCreate = { name ->
        viewModel.createCustomGenre(name)
        showCustomGenreDialog = false
      },
    )
  }

  if (showSynopsisPickerDialog) {
    AlertDialog(
      onDismissRequest = { showSynopsisPickerDialog = false },
      shape = MaterialTheme.shapes.large,
      containerColor = MaterialTheme.colorScheme.surface,
      title = { Text(stringResource(R.string.review_synopsis_label)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
          uiState.synopsisCandidates.forEachIndexed { idx, candidate ->
            EditorialSurfaceCard(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              onClick = {
                viewModel.selectAlternativeSynopsis(candidate)
                showSynopsisPickerDialog = false
              },
              modifier = Modifier.testTag("review_synopsis_candidate_$idx"),
            ) {
              Text(
                text =
                  listOfNotNull(
                      candidate.source.name,
                      candidate.language,
                      candidate.editionLabel,
                    )
                    .joinToString(" · "),
                style = LocalDipeTypography.current.monoMetadata,
                color = MaterialTheme.colorScheme.secondary,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = candidate.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showSynopsisPickerDialog = false }) {
          Text(stringResource(R.string.book_cancel_action))
        }
      },
    )
  }
}

@Composable
private fun ReviewAccordionCard(
  title: String,
  summary: String?,
  expanded: Boolean,
  headerTag: String,
  onToggle: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  EditorialSurfaceCard(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentPadding = PaddingValues(DipeSpacing.md),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clip(MaterialTheme.shapes.medium)
          .clickable(onClick = onToggle)
          .padding(vertical = 4.dp)
          .testTag(headerTag),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (!expanded && !summary.isNullOrBlank()) {
          Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Icon(
        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription =
          stringResource(if (expanded) R.string.cd_collapse_section else R.string.cd_expand_section),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    AnimatedVisibility(visible = expanded) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(top = DipeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
        content = content,
      )
    }
  }
}

@Composable
private fun ContributorEditorRow(
  row: EditableContributorRow,
  index: Int,
  canRemove: Boolean,
  textFieldColors: androidx.compose.material3.TextFieldColors,
  onNameChange: (String) -> Unit,
  onRoleChange: (ContributorRole) -> Unit,
  onRemove: () -> Unit,
) {
  var roleMenuOpen by remember { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = row.name,
        onValueChange = onNameChange,
        placeholder = { Text(stringResource(R.string.review_contributor_name_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.weight(1f).testTag("review_contributor_name_$index"),
      )

      Box {
        OutlinedButton(
          onClick = { roleMenuOpen = true },
          shape = CircleShape,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("review_contributor_role_$index"),
        ) {
          Text(
            text = contributorRoleLabel(row.role),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
          )
        }
        DropdownMenu(
          expanded = roleMenuOpen,
          onDismissRequest = { roleMenuOpen = false },
        ) {
          ContributorRole.entries.forEach { role ->
            DropdownMenuItem(
              text = { Text(contributorRoleLabel(role)) },
              onClick = {
                onRoleChange(role)
                roleMenuOpen = false
              },
            )
          }
        }
      }

      if (canRemove) {
        IconButton(
          onClick = onRemove,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = stringResource(R.string.copy_delete_action),
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}

@Composable
private fun CustomGenreDialog(
  onDismiss: () -> Unit,
  onCreate: (String) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    shape = MaterialTheme.shapes.large,
    containerColor = MaterialTheme.colorScheme.surface,
    title = { Text(stringResource(R.string.review_genre_custom_dialog_title)) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.review_genre_custom_name_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("review_custom_genre_input"),
      )
    },
    confirmButton = {
      Button(
        onClick = { onCreate(name) },
        enabled = name.isNotBlank(),
        modifier = Modifier.testTag("review_custom_genre_confirm"),
      ) {
        Text(stringResource(R.string.book_save_action))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.book_cancel_action)) }
    },
  )
}
