package com.example.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.EditorialSubscreenTopBar
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.editionFormatLabel
import com.example.core.designsystem.components.metadataSourceLabel
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.DuplicateAssessment
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookSearchScreen(
  uiState: BookSearchUiState,
  widthClass: WindowWidthClass,
  onQueryChange: (String) -> Unit,
  onSubmitSearch: () -> Unit,
  onExpandSecondarySources: () -> Unit,
  onOpenScanner: () -> Unit,
  onManualCatalog: () -> Unit,
  onToggleGroupExpanded: (String) -> Unit,
  onLoadMoreEditions: (SearchWorkGroup) -> Unit,
  onSelectEdition: (SearchEditionCandidate) -> Unit,
  onOpenExistingBook: (Long) -> Unit,
  onDismissDuplicate: () -> Unit,
  onDuplicateAddCopy: (SearchEditionCandidate, editionId: Long, workId: Long) -> Unit,
  onDuplicateAddEdition: (SearchEditionCandidate, workId: Long) -> Unit,
  onDuplicateSaveSeparately: (SearchEditionCandidate) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val horizontalPadding = DipeSpacing.horizontalMargin(widthClass)
  val language = LocalAppLanguage.current
  val response = uiState.response

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier =
        Modifier.widthIn(max = 880.dp)
          .fillMaxSize()
          .testTag("book_search_screen"),
      contentPadding =
        PaddingValues(
          start = horizontalPadding,
          end = horizontalPadding,
          top = DipeSpacing.md,
          bottom = DipeSpacing.xxl,
        ),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      item(key = "search_header") {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSubscreenTopBar(
            title = stringResource(R.string.search_screen_title),
            backButtonTag = "search_back_button",
            onNavigateBack = onNavigateBack,
            showBottomDivider = true,
          )

          OutlinedTextField(
            value = uiState.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_query_placeholder)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
            shape = CircleShape,
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                unfocusedBorderColor = Color.Transparent,
              ),
            modifier = Modifier.fillMaxWidth().testTag("search_query_input"),
          )

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
          ) {
            Button(
              onClick = onSubmitSearch,
              enabled = uiState.query.isNotBlank() && !uiState.isSearching,
              shape = CircleShape,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.secondary,
                  contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("search_submit_button"),
            ) {
              Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(DipeSpacing.xs))
              Text(stringResource(R.string.search_action_submit))
            }

            OutlinedButton(
              onClick = onOpenScanner,
              shape = CircleShape,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("search_scan_isbn_button"),
            ) {
              Icon(
                Icons.Outlined.QrCodeScanner,
                contentDescription = stringResource(R.string.cd_scan_isbn),
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(DipeSpacing.xs))
              Text(stringResource(R.string.search_action_scan_isbn))
            }

            TextButton(
              onClick = onManualCatalog,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("search_manual_button"),
            ) {
              Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(DipeSpacing.xs))
              Text(stringResource(R.string.search_action_manual))
            }
          }
        }
      }

      if (uiState.isSearching) {
        item(key = "search_loading") {
          Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = DipeSpacing.xl),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(strokeWidth = 2.dp)
          }
        }
      }

      if (response != null) {
        if (response.isOffline) {
          item(key = "search_offline_banner") {
            EditorialSurfaceCard(
              containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
              contentPadding = PaddingValues(DipeSpacing.md),
              modifier = Modifier.testTag("search_offline_banner"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
              ) {
                Icon(
                  imageVector = Icons.Outlined.CloudOff,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                  text = stringResource(R.string.search_offline_banner),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }
          }
        } else if (response.isFromCache) {
          item(key = "search_cache_banner") {
            EditorialSurfaceCard(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              contentPadding = PaddingValues(DipeSpacing.md),
              modifier = Modifier.testTag("search_cache_banner"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
              ) {
                Icon(
                  imageVector = Icons.Outlined.History,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = stringResource(R.string.search_cache_banner),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        if (response.sourceStatuses.isNotEmpty()) {
          item(key = "search_source_statuses") {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
              verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
            ) {
              response.sourceStatuses.forEach { st ->
                val label =
                  when (st.state) {
                    ProviderOutcomeState.SUCCESS,
                    ProviderOutcomeState.CACHED ->
                      stringResource(
                        R.string.search_source_active,
                        metadataSourceLabel(st.source),
                        st.count,
                      )
                    ProviderOutcomeState.SKIPPED_NO_API_KEY ->
                      stringResource(R.string.search_source_no_key, metadataSourceLabel(st.source))
                    ProviderOutcomeState.OFFLINE ->
                      stringResource(R.string.search_source_offline, metadataSourceLabel(st.source))
                    ProviderOutcomeState.RATE_LIMITED ->
                      stringResource(
                        R.string.search_source_rate_limit,
                        metadataSourceLabel(st.source),
                      )
                    ProviderOutcomeState.EMPTY,
                    ProviderOutcomeState.ERROR -> "${metadataSourceLabel(st.source)} (${st.count})"
                  }
                Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                  Text(
                    text = label,
                    style = LocalDipeTypography.current.monoMetadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
              }
            }
          }
        }

        if (response.localMatches.isNotEmpty()) {
          item(key = "search_local_header") {
            EditorialSectionHeader(title = stringResource(R.string.search_local_matches_section))
          }
          itemsIndexed(
            items = response.localMatches,
            key = { _, book -> "local_${book.work.id}" },
          ) { index, book ->
            EditorialSurfaceCard(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
              border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
              onClick = { onOpenExistingBook(book.work.id) },
              modifier = Modifier.testTag("search_local_match_$index"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                BookCoverArtwork(
                  title = book.title,
                  author = book.author,
                  isFavorite = book.isFavorite,
                  coverUri = book.coverUri,
                  compact = true,
                  modifier = Modifier.width(46.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
                }
                Text(
                  text = stringResource(R.string.search_work_in_library_badge),
                  style = LocalDipeTypography.current.monoMetadata,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
            }
          }
        }

        if (response.workGroups.isNotEmpty()) {
          item(key = "search_works_header") {
            EditorialSectionHeader(title = stringResource(R.string.search_results_works_section))
          }

          itemsIndexed(
            items = response.workGroups,
            key = { _, group -> group.groupId },
          ) { groupIndex, group ->
            val isExpanded = group.groupId in uiState.expandedGroupIds
            val isLoadingMore = group.groupId in uiState.loadingMoreEditionsForGroups
            val genreName =
              group.suggestedGenreSlug?.let { uiState.genresBySlug[it]?.localizedName(language) }

            SearchWorkGroupCard(
              group = group,
              groupIndex = groupIndex,
              genreLabel = genreName,
              isExpanded = isExpanded,
              isLoadingMore = isLoadingMore,
              onToggleExpand = { onToggleGroupExpanded(group.groupId) },
              onLoadMoreEditions = { onLoadMoreEditions(group) },
              onSelectEdition = onSelectEdition,
            )
          }
        } else if (!uiState.isSearching && response.localMatches.isEmpty()) {
          item(key = "search_empty_results") {
            EditorialEmptyState(
              icon = Icons.AutoMirrored.Outlined.LibraryBooks,
              title = stringResource(R.string.search_no_results_title),
              body = stringResource(R.string.search_no_results_body),
              actionLabel = stringResource(R.string.search_action_manual),
              actionTestTag = "search_empty_manual_button",
              onAction = onManualCatalog,
            )
          }
        }

        if (!response.expandedSecondarySources && !response.isIsbnQuery && !response.isOffline && !uiState.isSearching) {
          item(key = "search_expand_sources") {
            OutlinedButton(
              onClick = onExpandSecondarySources,
              shape = CircleShape,
              modifier =
                Modifier.fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .testTag("search_expand_sources_button"),
            ) {
              Text(stringResource(R.string.search_action_expand_sources))
            }
          }
        }
      } else if (!uiState.hasSearched && !uiState.isSearching) {
        item(key = "search_initial_prompt") {
          EditorialEmptyState(
            icon = Icons.Outlined.Search,
            title = stringResource(R.string.search_empty_prompt_title),
            body = stringResource(R.string.search_empty_prompt_body),
            actionLabel = stringResource(R.string.search_action_manual),
            actionTestTag = "search_prompt_manual_button",
            onAction = onManualCatalog,
          )
        }
      }
    }
  }

  uiState.duplicatePrompt?.let { prompt ->
    DuplicateResolutionDialog(
      prompt = prompt,
      onDismiss = onDismissDuplicate,
      onOpenExistingBook = onOpenExistingBook,
      onAddCopy = onDuplicateAddCopy,
      onAddEdition = onDuplicateAddEdition,
      onSaveSeparately = onDuplicateSaveSeparately,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchWorkGroupCard(
  group: SearchWorkGroup,
  groupIndex: Int,
  genreLabel: String?,
  isExpanded: Boolean,
  isLoadingMore: Boolean,
  onToggleExpand: () -> Unit,
  onLoadMoreEditions: () -> Unit,
  onSelectEdition: (SearchEditionCandidate) -> Unit,
) {
  val editionCount = group.editions.size
  EditorialSurfaceCard(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentPadding = PaddingValues(DipeSpacing.md),
    modifier = Modifier.testTag("search_work_card_$groupIndex"),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BookCoverArtwork(
          title = group.canonicalTitle,
          author = group.displayAuthors,
          isFavorite = false,
          coverUri = group.previewCoverUrl,
          compact = true,
          elevated = true,
          modifier = Modifier.width(56.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = group.canonicalTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (group.displayAuthors.isNotBlank()) {
            Text(
              text = group.displayAuthors,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text =
                pluralStringResource(
                  R.plurals.search_work_editions_count,
                  if (editionCount == 1) 1 else 2,
                  editionCount,
                ),
              style = LocalDipeTypography.current.monoMetadata,
              color = MaterialTheme.colorScheme.secondary,
            )
            if (genreLabel != null) {
              Text(
                text = "· $genreLabel",
                style = LocalDipeTypography.current.monoMetadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (group.localMatchedWorkId != null) {
              Text(
                text = "· ${stringResource(R.string.search_work_in_library_badge)}",
                style = LocalDipeTypography.current.monoMetadata,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
          }
        }
        TextButton(
          onClick = onToggleExpand,
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("search_work_expand_$groupIndex"),
        ) {
          Icon(
            imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription =
              stringResource(
                if (isExpanded) R.string.cd_collapse_section else R.string.cd_expand_section
              ),
          )
        }
      }

      AnimatedVisibility(visible = isExpanded) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(top = DipeSpacing.xs),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
        ) {
          HairlineDivider()
          group.editions.forEachIndexed { editionIdx, edition ->
            val metaLine =
              listOfNotNull(
                  edition.publisher,
                  edition.publicationYear?.toString(),
                  edition.totalPages?.let { "$it p." },
                  edition.language,
                  edition.format?.let { editionFormatLabel(it) },
                )
                .joinToString(" · ")
            val isbnLine = edition.bestIsbn?.let { "ISBN $it" }
            val sourcesLine = edition.sources.joinToString(", ")

            EditorialSurfaceCard(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              shape = MaterialTheme.shapes.medium,
              contentPadding = PaddingValues(DipeSpacing.md),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                BookCoverArtwork(
                  title = edition.workTitle,
                  author = edition.primaryAuthorsText,
                  isFavorite = false,
                  coverUri = edition.bestCover?.imageUrl,
                  compact = true,
                  modifier = Modifier.width(42.dp),
                )
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  Text(
                    text =
                      edition.editionLabel?.takeIf { it.isNotBlank() }
                        ?: edition.publisher
                        ?: edition.workTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  if (metaLine.isNotEmpty()) {
                    Text(
                      text = metaLine,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  if (isbnLine != null || sourcesLine.isNotEmpty()) {
                    Text(
                      text = listOfNotNull(isbnLine, sourcesLine.takeIf { it.isNotEmpty() }).joinToString(" · "),
                      style = LocalDipeTypography.current.monoMetadata,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
                Button(
                  onClick = { onSelectEdition(edition) },
                  shape = CircleShape,
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.secondary,
                      contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                  contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.xs),
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("search_edition_select_${groupIndex}_$editionIdx"),
                ) {
                  Text(
                    text = stringResource(R.string.search_edition_select_action),
                    style = MaterialTheme.typography.labelMedium,
                  )
                }
              }
            }
          }

          if (group.externalWorkKey != null) {
            TextButton(
              onClick = onLoadMoreEditions,
              enabled = !isLoadingMore,
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              if (isLoadingMore) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(DipeSpacing.xs))
              }
              Text(stringResource(R.string.search_edition_load_more))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DuplicateResolutionDialog(
  prompt: PendingDuplicatePrompt,
  onDismiss: () -> Unit,
  onOpenExistingBook: (Long) -> Unit,
  onAddCopy: (SearchEditionCandidate, Long, Long) -> Unit,
  onAddEdition: (SearchEditionCandidate, Long) -> Unit,
  onSaveSeparately: (SearchEditionCandidate) -> Unit,
) {
  val candidate = prompt.candidate
  when (val assessment = prompt.assessment) {
    is DuplicateAssessment.ExactEditionMatch -> {
      AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.duplicate_exact_title)) },
        text = {
          Text(
            text =
              stringResource(
                R.string.duplicate_exact_body,
                assessment.existingTitle,
                assessment.matchedIsbn,
                assessment.existingCopiesCount,
              )
          )
        },
        confirmButton = {
          Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
            Button(
              onClick = {
                onAddCopy(candidate, assessment.existingEditionId, assessment.existingWorkId)
              },
              modifier = Modifier.fillMaxWidth().testTag("duplicate_add_copy_button"),
            ) {
              Text(stringResource(R.string.duplicate_add_copy_action))
            }
            OutlinedButton(
              onClick = {
                onDismiss()
                onOpenExistingBook(assessment.existingWorkId)
              },
              modifier = Modifier.fillMaxWidth().testTag("duplicate_open_existing_button"),
            ) {
              Text(stringResource(R.string.duplicate_open_existing_action))
            }
          }
        },
        dismissButton = {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.book_cancel_action)) }
        },
      )
    }

    is DuplicateAssessment.SameWorkDifferentEditionMatch -> {
      AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.duplicate_same_work_title)) },
        text = {
          Text(
            text =
              stringResource(
                R.string.duplicate_same_work_body,
                assessment.existingTitle,
                assessment.existingAuthor,
              )
          )
        },
        confirmButton = {
          Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
            Button(
              onClick = { onAddEdition(candidate, assessment.existingWorkId) },
              modifier = Modifier.fillMaxWidth().testTag("duplicate_add_edition_button"),
            ) {
              Text(stringResource(R.string.duplicate_add_edition_action))
            }
            OutlinedButton(
              onClick = {
                onDismiss()
                onOpenExistingBook(assessment.existingWorkId)
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(stringResource(R.string.duplicate_open_existing_action))
            }
            TextButton(
              onClick = { onSaveSeparately(candidate) },
              modifier = Modifier.fillMaxWidth().testTag("duplicate_save_separately_button"),
            ) {
              Text(stringResource(R.string.duplicate_save_as_new_work))
            }
          }
        },
        dismissButton = {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.book_cancel_action)) }
        },
      )
    }

    is DuplicateAssessment.UncertainWorkMatch -> {
      AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.duplicate_uncertain_title)) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
            Text(stringResource(R.string.duplicate_uncertain_body))
            assessment.candidates.take(3).forEach { similar ->
              TextButton(
                onClick = { onAddEdition(candidate, similar.work.id) },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("${similar.title} — ${similar.author}")
              }
            }
          }
        },
        confirmButton = {
          Button(
            onClick = { onSaveSeparately(candidate) },
            modifier = Modifier.testTag("duplicate_confirm_new_work_button"),
          ) {
            Text(stringResource(R.string.duplicate_save_as_new_work))
          }
        },
        dismissButton = {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.book_cancel_action)) }
        },
      )
    }

    DuplicateAssessment.NoMatch -> Unit
  }
}
