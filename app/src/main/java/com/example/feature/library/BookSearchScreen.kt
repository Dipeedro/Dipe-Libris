package com.example.feature.library

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.CompactEditorialSearchPill
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.metadataSourceLabel
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.AppLanguage
import com.example.core.model.Genre
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup

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
  onShowMoreLocalEditions: ((String) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val horizontalPadding = DipeSpacing.horizontalMargin(widthClass)
  val language = LocalAppLanguage.current
  val reduceMotion = DipeTheme.motion.reduceMotion
  val (sweepProvider, pulseProvider) =
    if (uiState.isSearching && !reduceMotion) {
      val transition = rememberInfiniteTransition(label = "search_loading_transition")
      val sweepState =
        transition.animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec =
            infiniteRepeatable(
              animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
              repeatMode = RepeatMode.Restart,
            ),
          label = "search_hairline_sweep",
        )
      val pulseState =
        transition.animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec =
            infiniteRepeatable(
              animation = tween(durationMillis = 1350, easing = LinearEasing),
              repeatMode = RepeatMode.Restart,
            ),
          label = "search_pulse_wave",
        )
      Pair({ sweepState.value }, { pulseState.value })
    } else {
      Pair({ 0.5f }, { 0.5f })
    }
  val response = uiState.response
  val hasAnyResponseWorks =
    response != null && (
      response.workGroups.isNotEmpty() ||
        response.secondaryWorkGroups.isNotEmpty() ||
        response.derivativeWorkGroups.isNotEmpty() ||
        response.localMatches.isNotEmpty()
      )
  val hasLiteraryWorks =
    response != null && (
      response.workGroups.isNotEmpty() ||
        response.secondaryWorkGroups.isNotEmpty() ||
        response.localMatches.isNotEmpty()
      )
  val displayedStatuses =
    remember(response?.sourceStatuses, hasLiteraryWorks) {
      response?.sourceStatuses.orEmpty().filter { st ->
        if (!hasLiteraryWorks) {
          true
        } else {
          val isPrimarySource =
            st.source == MetadataSource.OPEN_LIBRARY || st.source == MetadataSource.GOOGLE_BOOKS
          val isDiscreetFailure =
            st.state == ProviderOutcomeState.ERROR ||
              st.state == ProviderOutcomeState.RATE_LIMITED ||
              st.state == ProviderOutcomeState.SKIPPED_NO_API_KEY
          isPrimarySource || !isDiscreetFailure
        }
      }
    }

  val fallbackEditionWindowLimits =
    remember(response?.query, response?.isFromCache) { mutableStateMapOf<String, Int>() }
  val windowLimitFor: (String) -> Int = { groupId ->
    uiState.editionWindowLimits[groupId]
      ?: fallbackEditionWindowLimits[groupId]
      ?: BookSearchViewModel.DEFAULT_EDITION_WINDOW_SIZE
  }
  val handleShowMoreLocalEditions: (String) -> Unit = { groupId ->
    if (onShowMoreLocalEditions != null) {
      onShowMoreLocalEditions(groupId)
    } else {
      fallbackEditionWindowLimits[groupId] =
        windowLimitFor(groupId) + BookSearchViewModel.EDITION_WINDOW_INCREMENT
    }
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.widthIn(max = 880.dp).fillMaxSize()) {
      // Fixed, compact editorial search bar with camera pill (no redundant Search button)
      Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .padding(
                  start = (horizontalPadding - 12.dp).coerceAtLeast(DipeSpacing.xs),
                  end = horizontalPadding,
                  top = DipeSpacing.sm,
                  bottom = DipeSpacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          ) {
            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("search_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = MaterialTheme.colorScheme.onBackground,
              )
            }

            CompactEditorialSearchPill(
              query = uiState.query,
              onQueryChange = onQueryChange,
              placeholder = stringResource(R.string.search_query_placeholder),
              clearContentDescription = stringResource(R.string.search_clear_query),
              inputTestTag = "search_query_input",
              imeAction = ImeAction.Search,
              onSearchSubmit = {
                if (uiState.query.isNotBlank() && !uiState.isSearching) {
                  onSubmitSearch()
                }
              },
              modifier = Modifier.weight(1f),
            )

            Surface(
              onClick = onOpenScanner,
              shape = CircleShape,
              color = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.secondary,
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .size(44.dp)
                  .testTag("search_scan_isbn_button"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Outlined.PhotoCamera,
                  contentDescription = stringResource(R.string.cd_scan_isbn),
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }

          if (uiState.isSearching) {
            SearchTopProgressHairline(
              progressProvider = sweepProvider,
              reduceMotion = reduceMotion,
              modifier = Modifier.fillMaxWidth().height(2.dp).testTag("search_progressive_bar"),
            )
          } else {
            HairlineDivider()
          }
        }
      }

      LazyColumn(
        modifier =
          Modifier.weight(1f)
            .fillMaxWidth()
            .testTag("book_search_screen"),
        contentPadding =
          PaddingValues(
            start = horizontalPadding,
            end = horizontalPadding,
            top = DipeSpacing.xxs,
            bottom = DipeSpacing.xxl,
          ),
        verticalArrangement = Arrangement.Top,
      ) {
        // Live local library matches while typing (before user submits search)
        if (!uiState.hasSearched && uiState.localLiveMatches.isNotEmpty() && !uiState.isSearching) {
          item(key = "search_live_local_header", contentType = "section_header") {
            EditorialSectionHeader(
              title = stringResource(R.string.search_live_local_matches_section),
              modifier = Modifier.padding(top = DipeSpacing.compact),
            )
          }
          itemsIndexed(
            items = uiState.localLiveMatches,
            key = { _, book -> "live_local_${book.work.id}" },
            contentType = { _, _ -> "local_match" },
          ) { index, book ->
            SearchLocalMatchCard(
              book = book,
              testTag = "search_live_local_match_$index",
              onClick = { onOpenExistingBook(book.work.id) },
              modifier = Modifier.padding(top = DipeSpacing.compact),
            )
          }
        }

        if (uiState.isSearching && !hasAnyResponseWorks) {
          item(key = "search_loading_indicator", contentType = "loading_indicator") {
            SearchEditorialLoadingState(
              pulseProvider = pulseProvider,
              reduceMotion = reduceMotion,
            )
          }
        }

        if (response != null) {
          if (response.isOffline) {
            item(key = "search_offline_banner", contentType = "offline_banner") {
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(top = DipeSpacing.compact)
                    .testTag("search_offline_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
              ) {
                Icon(
                  imageVector = Icons.Outlined.CloudOff,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = stringResource(R.string.search_offline_short),
                  style = LocalDipeTypography.current.monoMetadata.copy(fontSize = 11.5.sp),
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
            }
          }

          if (displayedStatuses.isNotEmpty() || (!response.isOffline && response.isFromCache)) {
            item(key = "search_source_statuses", contentType = "source_statuses") {
              LazyRow(
                modifier = Modifier.padding(top = DipeSpacing.compact),
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (!response.isOffline && response.isFromCache) {
                  item(key = "cache_pill", contentType = "status_pill") {
                    Surface(
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.surfaceContainerLow,
                      modifier = Modifier.testTag("search_cache_banner"),
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.History,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.onSurfaceVariant,
                          modifier = Modifier.size(12.dp),
                        )
                        Text(
                          text = stringResource(R.string.search_cache_short),
                          style = LocalDipeTypography.current.monoMetadata.copy(fontSize = 11.sp),
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                  }
                }

                items(
                  items = displayedStatuses,
                  key = { it.source.name },
                  contentType = { "status_pill" },
                ) { st ->
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
                      ProviderOutcomeState.ERROR -> {
                        val isTimeout =
                          st.message?.contains("timeout", ignoreCase = true) == true ||
                            st.message?.contains("deadline", ignoreCase = true) == true
                        if (isTimeout) {
                          stringResource(R.string.search_source_timeout, srcLabel)
                        } else {
                          stringResource(R.string.search_source_error, srcLabel)
                        }
                      }
                    }
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                      if (st.state == ProviderOutcomeState.LOADING && uiState.isSearching) {
                        SearchSourceLoadingDot(
                          pulseProvider = pulseProvider,
                          reduceMotion = reduceMotion,
                        )
                      }
                      Text(
                        text = label,
                        style = LocalDipeTypography.current.monoMetadata.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                      )
                    }
                  }
                }
              }
            }
          }

          if (response.localMatches.isNotEmpty()) {
            item(key = "search_local_header", contentType = "section_header") {
              EditorialSectionHeader(
                title = stringResource(R.string.search_local_matches_section),
                modifier = Modifier.padding(top = DipeSpacing.compact),
              )
            }
            itemsIndexed(
              items = response.localMatches,
              key = { _, book -> "local_${book.work.id}" },
              contentType = { _, _ -> "local_match" },
            ) { index, book ->
              SearchLocalMatchCard(
                book = book,
                testTag = "search_local_match_$index",
                onClick = { onOpenExistingBook(book.work.id) },
                modifier = Modifier.padding(top = DipeSpacing.compact),
              )
            }
          }

          if (response.workGroups.isNotEmpty()) {
            item(key = "search_works_header", contentType = "section_header") {
              Row(
                modifier = Modifier.fillMaxWidth().padding(top = DipeSpacing.compact),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                EditorialSectionHeader(
                  title = stringResource(R.string.search_results_works_section),
                  modifier = Modifier.weight(1f),
                )
                Text(
                  text = stringResource(R.string.search_results_count_summary, response.workGroups.size),
                  style = LocalDipeTypography.current.monoMetadata.copy(fontSize = 11.5.sp),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            flattenedWorkGroupItems(
              sectionPrefix = "pri",
              groups = response.workGroups,
              indexOffset = 0,
              genresBySlug = uiState.genresBySlug,
              language = language,
              expandedGroupIds = uiState.expandedGroupIds,
              expandedSecondaryEditionGroupIds = uiState.expandedSecondaryEditionGroupIds,
              loadingMoreEditionsForGroups = uiState.loadingMoreEditionsForGroups,
              exhaustedEditionGroupIds = uiState.exhaustedEditionGroupIds,
              windowLimitFor = windowLimitFor,
              onToggleExpand = onToggleGroupExpanded,
              onShowMoreLocalEditions = handleShowMoreLocalEditions,
              onToggleSecondaryEditions = onToggleGroupSecondaryEditions,
              onLoadMoreEditions = onLoadMoreEditions,
              onSelectEdition = onSelectEdition,
            )
          }

          // Secondary languages (pt-PT & other foreign works)
          if (response.secondaryWorkGroups.isNotEmpty()) {
            val hasPrimary = response.workGroups.isNotEmpty()
            val showSecondaryList = !hasPrimary || uiState.isSecondaryLanguagesExpanded
            val indexOffset = response.workGroups.size

            item(key = "search_secondary_languages_header", contentType = "drawer_toggle") {
              Box(modifier = Modifier.fillMaxWidth().padding(top = DipeSpacing.compact)) {
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
                    contentPadding = PaddingValues(DipeSpacing.compact),
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
            }

            if (showSecondaryList) {
              flattenedWorkGroupItems(
                sectionPrefix = "sec",
                groups = response.secondaryWorkGroups,
                indexOffset = indexOffset,
                genresBySlug = uiState.genresBySlug,
                language = language,
                expandedGroupIds = uiState.expandedGroupIds,
                expandedSecondaryEditionGroupIds = uiState.expandedSecondaryEditionGroupIds,
                loadingMoreEditionsForGroups = uiState.loadingMoreEditionsForGroups,
                exhaustedEditionGroupIds = uiState.exhaustedEditionGroupIds,
                windowLimitFor = windowLimitFor,
                onToggleExpand = onToggleGroupExpanded,
                onShowMoreLocalEditions = handleShowMoreLocalEditions,
                onToggleSecondaryEditions = onToggleGroupSecondaryEditions,
                onLoadMoreEditions = onLoadMoreEditions,
                onSelectEdition = onSelectEdition,
              )
            }
          } else if (
            response.workGroups.isEmpty() &&
              response.derivativeWorkGroups.isEmpty() &&
              !uiState.isSearching &&
              response.localMatches.isEmpty()
          ) {
            item(key = "search_empty_results", contentType = "empty_state") {
              EditorialEmptyState(
                icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                title = stringResource(R.string.search_no_results_title),
                body = stringResource(R.string.search_no_results_body),
                actionLabel = stringResource(R.string.search_action_manual),
                actionTestTag = "search_empty_manual_button",
                onAction = onManualCatalog,
                modifier = Modifier.padding(top = DipeSpacing.compact),
              )
            }
          }

          // Academic studies, literary analyses & derivative editions
          if (response.derivativeWorkGroups.isNotEmpty()) {
            val hasLiterary = response.workGroups.isNotEmpty() || response.secondaryWorkGroups.isNotEmpty()
            val showDerivatives = !hasLiterary || uiState.isDerivativeWorksExpanded
            val derivOffset = response.workGroups.size + response.secondaryWorkGroups.size

            item(key = "search_derivative_works_header", contentType = "drawer_toggle") {
              Box(modifier = Modifier.fillMaxWidth().padding(top = DipeSpacing.compact)) {
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
                    contentPadding = PaddingValues(DipeSpacing.compact),
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
            }

            if (showDerivatives) {
              flattenedWorkGroupItems(
                sectionPrefix = "deriv",
                groups = response.derivativeWorkGroups,
                indexOffset = derivOffset,
                genresBySlug = uiState.genresBySlug,
                language = language,
                expandedGroupIds = uiState.expandedGroupIds,
                expandedSecondaryEditionGroupIds = uiState.expandedSecondaryEditionGroupIds,
                loadingMoreEditionsForGroups = uiState.loadingMoreEditionsForGroups,
                exhaustedEditionGroupIds = uiState.exhaustedEditionGroupIds,
                windowLimitFor = windowLimitFor,
                onToggleExpand = onToggleGroupExpanded,
                onShowMoreLocalEditions = handleShowMoreLocalEditions,
                onToggleSecondaryEditions = onToggleGroupSecondaryEditions,
                onLoadMoreEditions = onLoadMoreEditions,
                onSelectEdition = onSelectEdition,
              )
            }
          }

          // Manual Pagination Footer
          if (response.hasMoreResults && !response.isOffline && !uiState.isSearching) {
            item(key = "search_load_more_results", contentType = "footer_button") {
              OutlinedButton(
                onClick = onLoadMoreResults,
                enabled = !uiState.isLoadingMoreResults,
                shape = CircleShape,
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(top = DipeSpacing.compact)
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
            item(key = "search_expand_sources", contentType = "footer_button") {
              OutlinedButton(
                onClick = onExpandSecondarySources,
                shape = CircleShape,
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(top = DipeSpacing.compact)
                    .minimumInteractiveComponentSize()
                    .testTag("search_expand_sources_button"),
              ) {
                Text(stringResource(R.string.search_action_expand_sources))
              }
            }
          }

          // Subtle manual registration footer below results
          if (hasAnyResponseWorks && !uiState.isSearching) {
            item(key = "search_manual_footer", contentType = "manual_footer") {
              Column(
                modifier = Modifier.fillMaxWidth().padding(top = DipeSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
              ) {
                HairlineDivider()
                Spacer(modifier = Modifier.height(DipeSpacing.xs))
                Text(
                  text = stringResource(R.string.search_manual_footer_hint),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                  onClick = onManualCatalog,
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("search_manual_button"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(DipeSpacing.xs))
                  Text(
                    text = stringResource(R.string.search_action_manual),
                    color = MaterialTheme.colorScheme.secondary,
                  )
                }
              }
            }
          }
        } else if (!uiState.hasSearched && !uiState.isSearching && uiState.localLiveMatches.isEmpty()) {
          item(key = "search_initial_prompt", contentType = "empty_state") {
            EditorialEmptyState(
              icon = Icons.Outlined.Search,
              title = stringResource(R.string.search_empty_prompt_title),
              body = stringResource(R.string.search_empty_prompt_body),
              actionLabel = stringResource(R.string.search_action_manual),
              actionTestTag = "search_manual_button",
              onAction = onManualCatalog,
              modifier = Modifier.padding(top = DipeSpacing.compact),
            )
          }
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

private fun LazyListScope.flattenedWorkGroupItems(
  sectionPrefix: String,
  groups: List<SearchWorkGroup>,
  indexOffset: Int,
  genresBySlug: Map<String, Genre>,
  language: AppLanguage,
  expandedGroupIds: Set<String>,
  expandedSecondaryEditionGroupIds: Set<String>,
  loadingMoreEditionsForGroups: Set<String>,
  exhaustedEditionGroupIds: Set<String>,
  windowLimitFor: (String) -> Int,
  onToggleExpand: (String) -> Unit,
  onShowMoreLocalEditions: (String) -> Unit,
  onToggleSecondaryEditions: (String) -> Unit,
  onLoadMoreEditions: (SearchWorkGroup) -> Unit,
  onSelectEdition: (SearchEditionCandidate) -> Unit,
) {
  groups.forEachIndexed { localIdx, group ->
    val groupIndex = indexOffset + localIdx
    val isExpanded = group.groupId in expandedGroupIds
    val isSecondaryEditionsExpanded = group.groupId in expandedSecondaryEditionGroupIds
    val isLoadingMore = group.groupId in loadingMoreEditionsForGroups
    val isExpansionExhausted = group.groupId in exhaustedEditionGroupIds
    val visibleEditions =
      if (isSecondaryEditionsExpanded) {
        group.primaryTierEditions + group.secondaryTierEditions
      } else {
        group.primaryTierEditions
      }
    val editionLimit = windowLimitFor(group.groupId)
    val displayedEditions = if (isExpanded) visibleEditions.take(editionLimit) else emptyList()
    val remainingEditions =
      if (isExpanded) (visibleEditions.size - displayedEditions.size).coerceAtLeast(0) else 0
    val canLoadMoreRemote =
      isExpanded &&
        (group.externalWorkKey != null || group.displayAuthors.isNotBlank()) &&
        !isExpansionExhausted
    val showActionsFooter =
      isExpanded &&
        (remainingEditions > 0 || group.secondaryTierEditions.isNotEmpty() || canLoadMoreRemote)
    val hasExpandedContentBelow =
      isExpanded && (displayedEditions.isNotEmpty() || showActionsFooter)
    val genreName = group.suggestedGenreSlug?.let { genresBySlug[it]?.localizedName(language) }

    item(
      key = "${sectionPrefix}_hdr_${group.groupId}",
      contentType = "search_work_header",
    ) {
      SearchWorkHeaderItem(
        group = group,
        groupIndex = groupIndex,
        genreLabel = genreName,
        isExpanded = isExpanded,
        hasExpandedContentBelow = hasExpandedContentBelow,
        onToggleExpand = { onToggleExpand(group.groupId) },
      )
    }

    if (displayedEditions.isNotEmpty()) {
      itemsIndexed(
        items = displayedEditions,
        key = { edIdx, edition -> "${sectionPrefix}_ed_${group.groupId}_${edition.candidateId}_$edIdx" },
        contentType = { _, _ -> "search_edition_row" },
      ) { editionIdx, edition ->
        val isLastItemInGroup = !showActionsFooter && editionIdx == displayedEditions.lastIndex
        SearchEditionRowItem(
          edition = edition,
          testTag = "search_edition_select_${groupIndex}_$editionIdx",
          isLastItemInGroup = isLastItemInGroup,
          onClick = { onSelectEdition(edition) },
        )
      }
    }

    if (showActionsFooter) {
      item(
        key = "${sectionPrefix}_act_${group.groupId}",
        contentType = "search_work_actions",
      ) {
        SearchWorkActionsFooterItem(
          groupIndex = groupIndex,
          remainingEditions = remainingEditions,
          secondaryEditionsCount = group.secondaryTierEditions.size,
          isSecondaryEditionsExpanded = isSecondaryEditionsExpanded,
          canLoadMoreRemoteEditions = canLoadMoreRemote,
          isLoadingMore = isLoadingMore,
          onShowMoreLocalEditions = { onShowMoreLocalEditions(group.groupId) },
          onToggleSecondaryEditions = { onToggleSecondaryEditions(group.groupId) },
          onLoadMoreRemoteEditions = { onLoadMoreEditions(group) },
        )
      }
    }
  }
}
