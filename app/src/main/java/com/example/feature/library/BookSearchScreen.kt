package com.example.feature.library

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.EditorialSubscreenTopBar
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.designsystem.components.metadataSourceLabel
import com.example.core.localization.LocalAppLanguage
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
  onLoadMoreResults: () -> Unit = {},
  onToggleSecondaryLanguages: () -> Unit = {},
  onToggleDerivativeWorks: () -> Unit = {},
  onToggleGroupSecondaryEditions: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val horizontalPadding = DipeSpacing.horizontalMargin(widthClass)
  val language = LocalAppLanguage.current
  val response = uiState.response
  val hasAnyResponseWorks =
    response != null && (
      response.workGroups.isNotEmpty() ||
        response.secondaryWorkGroups.isNotEmpty() ||
        response.derivativeWorkGroups.isNotEmpty() ||
        response.localMatches.isNotEmpty()
      )

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

      // Live local library matches while typing (before user presses Search or while editing)
      if (!uiState.hasSearched && uiState.localLiveMatches.isNotEmpty() && !uiState.isSearching) {
        item(key = "search_live_local_header") {
          EditorialSectionHeader(title = stringResource(R.string.search_live_local_matches_section))
        }
        itemsIndexed(
          items = uiState.localLiveMatches,
          key = { _, book -> "live_local_${book.work.id}" },
        ) { index, book ->
          SearchLocalMatchCard(
            book = book,
            testTag = "search_live_local_match_$index",
            onClick = { onOpenExistingBook(book.work.id) },
          )
        }
      }

      if (uiState.isSearching) {
        item(key = "search_loading") {
          if (hasAnyResponseWorks) {
            LinearProgressIndicator(
              modifier = Modifier.fillMaxWidth().testTag("search_progressive_bar"),
              color = MaterialTheme.colorScheme.secondary,
            )
          } else {
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = DipeSpacing.md),
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm, Alignment.CenterHorizontally),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
              Text(
                text = stringResource(R.string.search_loading_progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
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
                val srcLabel = metadataSourceLabel(st.source)
                val label =
                  when (st.state) {
                    ProviderOutcomeState.LOADING ->
                      stringResource(R.string.search_source_loading, srcLabel)
                    ProviderOutcomeState.SUCCESS,
                    ProviderOutcomeState.CACHED -> {
                      val total = st.totalAvailable
                      if (total != null && total > st.count) {
                        stringResource(R.string.search_source_active_total, srcLabel, st.count, total)
                      } else {
                        stringResource(R.string.search_source_active, srcLabel, st.count)
                      }
                    }
                    ProviderOutcomeState.SKIPPED_NO_API_KEY ->
                      stringResource(R.string.search_source_no_key, srcLabel)
                    ProviderOutcomeState.OFFLINE ->
                      stringResource(R.string.search_source_offline, srcLabel)
                    ProviderOutcomeState.RATE_LIMITED ->
                      stringResource(R.string.search_source_rate_limit, srcLabel)
                    ProviderOutcomeState.EMPTY -> "$srcLabel (${st.count})"
                    ProviderOutcomeState.ERROR ->
                      stringResource(R.string.search_source_error, srcLabel)
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
            SearchLocalMatchCard(
              book = book,
              testTag = "search_local_match_$index",
              onClick = { onOpenExistingBook(book.work.id) },
            )
          }
        }

        if (response.workGroups.isNotEmpty()) {
          item(key = "search_works_header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              EditorialSectionHeader(title = stringResource(R.string.search_results_works_section))
              Text(
                text = stringResource(R.string.search_results_count_summary, response.workGroups.size),
                style = LocalDipeTypography.current.monoMetadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          itemsIndexed(
            items = response.workGroups,
            key = { _, group -> group.groupId },
          ) { groupIndex, group ->
            val genreName =
              group.suggestedGenreSlug?.let { uiState.genresBySlug[it]?.localizedName(language) }
            SearchWorkGroupCard(
              group = group,
              groupIndex = groupIndex,
              genreLabel = genreName,
              isExpanded = group.groupId in uiState.expandedGroupIds,
              isSecondaryEditionsExpanded = group.groupId in uiState.expandedSecondaryEditionGroupIds,
              isLoadingMore = group.groupId in uiState.loadingMoreEditionsForGroups,
              onToggleExpand = { onToggleGroupExpanded(group.groupId) },
              onToggleSecondaryEditions = { onToggleGroupSecondaryEditions(group.groupId) },
              onLoadMoreEditions = { onLoadMoreEditions(group) },
              onSelectEdition = onSelectEdition,
            )
          }
        }

        // Secondary languages (pt-PT & other foreign works):
        // Collapsible drawer when primary results exist; auto-displayed when primary is empty.
        if (response.secondaryWorkGroups.isNotEmpty()) {
          val hasPrimary = response.workGroups.isNotEmpty()
          val showSecondaryList = !hasPrimary || uiState.isSecondaryLanguagesExpanded
          val indexOffset = response.workGroups.size

          item(key = "search_secondary_languages_header") {
            if (hasPrimary) {
              OutlinedButton(
                onClick = onToggleSecondaryLanguages,
                shape = CircleShape,
                modifier =
                  Modifier.fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("search_secondary_languages_toggle"),
              ) {
                Text(
                  text =
                    stringResource(
                      R.string.search_secondary_languages_toggle,
                      response.secondaryWorkGroups.size,
                    ),
                  modifier = Modifier.weight(1f),
                )
                Icon(
                  imageVector =
                    if (uiState.isSecondaryLanguagesExpanded) Icons.Outlined.ExpandLess
                    else Icons.Outlined.ExpandMore,
                  contentDescription = null,
                )
              }
            } else {
              EditorialSurfaceCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentPadding = PaddingValues(DipeSpacing.md),
                modifier = Modifier.testTag("search_secondary_only_notice"),
              ) {
                Text(
                  text = stringResource(R.string.search_secondary_languages_only_notice),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          if (showSecondaryList) {
            itemsIndexed(
              items = response.secondaryWorkGroups,
              key = { _, group -> "sec_${group.groupId}" },
            ) { secIdx, group ->
              val groupIndex = indexOffset + secIdx
              val genreName =
                group.suggestedGenreSlug?.let { uiState.genresBySlug[it]?.localizedName(language) }
              SearchWorkGroupCard(
                group = group,
                groupIndex = groupIndex,
                genreLabel = genreName,
                isExpanded = group.groupId in uiState.expandedGroupIds,
                isSecondaryEditionsExpanded = group.groupId in uiState.expandedSecondaryEditionGroupIds,
                isLoadingMore = group.groupId in uiState.loadingMoreEditionsForGroups,
                onToggleExpand = { onToggleGroupExpanded(group.groupId) },
                onToggleSecondaryEditions = { onToggleGroupSecondaryEditions(group.groupId) },
                onLoadMoreEditions = { onLoadMoreEditions(group) },
                onSelectEdition = onSelectEdition,
              )
            }
          }
        } else if (
          response.workGroups.isEmpty() &&
            response.derivativeWorkGroups.isEmpty() &&
            !uiState.isSearching &&
            response.localMatches.isEmpty()
        ) {
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

        // Academic studies, literary analyses & derivative editions (T7):
        // Always collapsed into bottom drawer when literary results exist.
        if (response.derivativeWorkGroups.isNotEmpty()) {
          val hasLiterary = response.workGroups.isNotEmpty() || response.secondaryWorkGroups.isNotEmpty()
          val showDerivatives = !hasLiterary || uiState.isDerivativeWorksExpanded
          val derivOffset = response.workGroups.size + response.secondaryWorkGroups.size

          item(key = "search_derivative_works_header") {
            if (hasLiterary) {
              OutlinedButton(
                onClick = onToggleDerivativeWorks,
                shape = CircleShape,
                modifier =
                  Modifier.fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("search_derivative_works_toggle"),
              ) {
                Text(
                  text =
                    stringResource(
                      R.string.search_derivative_drawer_toggle,
                      response.derivativeWorkGroups.size,
                    ),
                  modifier = Modifier.weight(1f),
                )
                Icon(
                  imageVector =
                    if (uiState.isDerivativeWorksExpanded) Icons.Outlined.ExpandLess
                    else Icons.Outlined.ExpandMore,
                  contentDescription = null,
                )
              }
            } else {
              EditorialSurfaceCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentPadding = PaddingValues(DipeSpacing.md),
                modifier = Modifier.testTag("search_derivative_only_notice"),
              ) {
                Text(
                  text = stringResource(R.string.search_derivative_only_notice),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          if (showDerivatives) {
            itemsIndexed(
              items = response.derivativeWorkGroups,
              key = { _, group -> "deriv_${group.groupId}" },
            ) { derivIdx, group ->
              val groupIndex = derivOffset + derivIdx
              val genreName =
                group.suggestedGenreSlug?.let { uiState.genresBySlug[it]?.localizedName(language) }
              SearchWorkGroupCard(
                group = group,
                groupIndex = groupIndex,
                genreLabel = genreName,
                isExpanded = group.groupId in uiState.expandedGroupIds,
                isSecondaryEditionsExpanded = group.groupId in uiState.expandedSecondaryEditionGroupIds,
                isLoadingMore = group.groupId in uiState.loadingMoreEditionsForGroups,
                onToggleExpand = { onToggleGroupExpanded(group.groupId) },
                onToggleSecondaryEditions = { onToggleGroupSecondaryEditions(group.groupId) },
                onLoadMoreEditions = { onLoadMoreEditions(group) },
                onSelectEdition = onSelectEdition,
              )
            }
          }
        }

        // Manual Pagination Footer
        if (response.hasMoreResults && !response.isOffline && !uiState.isSearching) {
          item(key = "search_load_more_results") {
            OutlinedButton(
              onClick = onLoadMoreResults,
              enabled = !uiState.isLoadingMoreResults,
              shape = CircleShape,
              modifier =
                Modifier.fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .testTag("search_load_more_results_button"),
            ) {
              if (uiState.isLoadingMoreResults) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(DipeSpacing.xs))
                Text(stringResource(R.string.search_loading_more_results))
              } else {
                Text(stringResource(R.string.search_action_load_more_results))
              }
            }
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
      } else if (!uiState.hasSearched && !uiState.isSearching && uiState.localLiveMatches.isEmpty()) {
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
