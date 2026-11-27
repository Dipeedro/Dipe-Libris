package com.example.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.InterFontFamily
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.CompactEditorialSearchPill
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.AppLanguage
import com.example.core.model.Genre
import com.example.core.model.HybridSearchResponse
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup

private fun hasAnyWorks(response: HybridSearchResponse?): Boolean =
  response != null && (
    response.workGroups.isNotEmpty() ||
      response.secondaryWorkGroups.isNotEmpty() ||
      response.derivativeWorkGroups.isNotEmpty() ||
      response.localMatches.isNotEmpty()
    )

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
  onToggleEditionSelection: ((SearchEditionCandidate) -> Unit)? = null,
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
  val rawHasWorks = hasAnyWorks(response)

  // Preserve previous results softly during follow-up queries/refresh so results never flash empty.
  var preservedResponse by remember { mutableStateOf<HybridSearchResponse?>(null) }
  LaunchedEffect(response, uiState.isSearching, uiState.hasSearched, uiState.hasSearchError) {
    if (!uiState.hasSearched || (uiState.hasSearchError && !rawHasWorks)) {
      preservedResponse = null
    } else if (rawHasWorks) {
      preservedResponse = response
    } else if (!uiState.isSearching) {
      preservedResponse = null
    }
  }
  val isRefreshingExistingResults =
    uiState.isSearching && !rawHasWorks && preservedResponse != null
  val displayResponse = if (isRefreshingExistingResults) preservedResponse else response
  val hasAnyDisplayWorks = hasAnyWorks(displayResponse)

  val resultsAlpha by
    animateFloatAsState(
      targetValue = if (isRefreshingExistingResults) 0.58f else 1f,
      animationSpec = DipeTheme.motion.standardTween(180),
      label = "search_results_refresh_alpha",
    )

  val fallbackEditionWindowLimits =
    remember(displayResponse?.query, displayResponse?.isFromCache) {
      mutableStateMapOf<String, Int>()
    }
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
  var localSelectedEditionId by remember(displayResponse?.query) { mutableStateOf<String?>(null) }
  val effectiveSelectedEditionId = uiState.selectedEditionId ?: localSelectedEditionId
  val handleToggleEditionSelection: (SearchEditionCandidate) -> Unit = { candidate ->
    if (onToggleEditionSelection != null) {
      onToggleEditionSelection(candidate)
    } else {
      localSelectedEditionId =
        if (localSelectedEditionId == candidate.candidateId) null else candidate.candidateId
    }
  }

  val editionColumns =
    when (widthClass) {
      WindowWidthClass.COMPACT -> 3
      WindowWidthClass.MEDIUM -> 4
      WindowWidthClass.EXPANDED -> 5
    }

  val revealedGalleryPartsByGroup =
    remember(displayResponse?.query, displayResponse?.isFromCache) {
      mutableStateMapOf<String, Set<String>>()
    }
  LaunchedEffect(uiState.expandedGroupIds) {
    val collapsedGroupIds =
      revealedGalleryPartsByGroup.keys.filter { it !in uiState.expandedGroupIds }
    collapsedGroupIds.forEach { revealedGalleryPartsByGroup.remove(it) }
  }

  val secondaryButtonTextStyle =
    TextStyle(
      fontFamily = InterFontFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 13.5.sp,
      lineHeight = 18.sp,
    )

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.widthIn(max = 880.dp).fillMaxSize()) {
      // Fixed, compact editorial search bar with camera pill (clean, uncluttered top region)
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

            val cameraInteractionSource = remember { MutableInteractionSource() }
            val cameraPressed by cameraInteractionSource.collectIsPressedAsState()
            val cameraBg by
              animateColorAsState(
                targetValue =
                  if (cameraPressed) MaterialTheme.colorScheme.surfaceContainer
                  else MaterialTheme.colorScheme.surfaceContainerLow,
                animationSpec = DipeTheme.motion.standardTween(120),
                label = "search_camera_bg",
              )
            val cameraTint by
              animateColorAsState(
                targetValue =
                  if (cameraPressed) MaterialTheme.colorScheme.secondary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = DipeTheme.motion.standardTween(120),
                label = "search_camera_tint",
              )

            Surface(
              onClick = onOpenScanner,
              interactionSource = cameraInteractionSource,
              shape = CircleShape,
              color = cameraBg,
              contentColor = cameraTint,
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .size(48.dp)
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

      if (uiState.hasPartialFailureWarning && hasAnyDisplayWorks) {
        EditorialSurfaceCard(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          contentPadding =
            PaddingValues(
              horizontal = DipeSpacing.compact,
              vertical = DipeSpacing.xs,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = DipeSpacing.compact,
              )
              .testTag("search_partial_warning_banner"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
          ) {
            Icon(
              imageVector = Icons.Outlined.CloudOff,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = stringResource(R.string.search_partial_failure_warning),
              style =
                TextStyle(
                  fontFamily = InterFontFamily,
                  fontWeight = FontWeight.Normal,
                  fontSize = 12.5.sp,
                  lineHeight = 17.sp,
                ),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
            TextButton(
              onClick = onSubmitSearch,
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("search_partial_warning_retry"),
            ) {
              Text(
                text = stringResource(R.string.search_action_retry),
                style = secondaryButtonTextStyle,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
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

        if (uiState.isSearching && !hasAnyDisplayWorks) {
          item(key = "search_loading_indicator", contentType = "loading_indicator") {
            SearchEditorialLoadingState(
              pulseProvider = pulseProvider,
              reduceMotion = reduceMotion,
            )
          }
        }

        if (displayResponse != null) {
          if (displayResponse.isOffline) {
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
                  style = searchSansMetadataStyle(),
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
            }
          }

          if (displayResponse.localMatches.isNotEmpty()) {
            item(key = "search_local_header", contentType = "section_header") {
              EditorialSectionHeader(
                title = stringResource(R.string.search_local_matches_section),
                modifier = Modifier.padding(top = DipeSpacing.compact),
              )
            }
            itemsIndexed(
              items = displayResponse.localMatches,
              key = { _, book -> "local_${book.work.id}" },
              contentType = { _, _ -> "local_match" },
            ) { index, book ->
              SearchLocalMatchCard(
                book = book,
                testTag = "search_local_match_$index",
                onClick = { onOpenExistingBook(book.work.id) },
                modifier =
                  Modifier.padding(top = DipeSpacing.compact)
                    .graphicsLayer { alpha = resultsAlpha },
              )
            }
          }

          if (displayResponse.workGroups.isNotEmpty()) {
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
                  text =
                    pluralStringResource(
                      R.plurals.search_results_count_summary,
                      displayResponse.workGroups.size,
                      displayResponse.workGroups.size,
                    ),
                  style = searchSansMetadataStyle(),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            flattenedWorkGroupItems(
              sectionPrefix = "pri",
              groups = displayResponse.workGroups,
              indexOffset = 0,
              editionColumns = editionColumns,
              genresBySlug = uiState.genresBySlug,
              language = language,
              expandedGroupIds = uiState.expandedGroupIds,
              expandedSecondaryEditionGroupIds = uiState.expandedSecondaryEditionGroupIds,
              loadingMoreEditionsForGroups = uiState.loadingMoreEditionsForGroups,
              exhaustedEditionGroupIds = uiState.exhaustedEditionGroupIds,
              selectedEditionId = effectiveSelectedEditionId,
              revealedGalleryPartsByGroup = revealedGalleryPartsByGroup,
              reduceMotion = reduceMotion,
              resultsAlphaProvider = { resultsAlpha },
              windowLimitFor = windowLimitFor,
              onToggleExpand = onToggleGroupExpanded,
              onShowMoreLocalEditions = handleShowMoreLocalEditions,
              onToggleSecondaryEditions = onToggleGroupSecondaryEditions,
              onLoadMoreEditions = onLoadMoreEditions,
              onToggleEdition = handleToggleEditionSelection,
              onConfirmSelectedEdition = onSelectEdition,
            )
          }

          // Secondary languages (pt-PT & other foreign works)
          if (displayResponse.secondaryWorkGroups.isNotEmpty()) {
            val hasPrimary = displayResponse.workGroups.isNotEmpty()
            val showSecondaryList = !hasPrimary || uiState.isSecondaryLanguagesExpanded
            val indexOffset = displayResponse.workGroups.size

            item(key = "search_secondary_languages_header", contentType = "drawer_toggle") {
              Box(modifier = Modifier.fillMaxWidth().padding(top = DipeSpacing.md)) {
                if (hasPrimary) {
                  SearchContextualDrawerHeader(
                    title = stringResource(R.string.search_secondary_languages_section_title),
                    count = displayResponse.secondaryWorkGroups.size,
                    isExpanded = uiState.isSecondaryLanguagesExpanded,
                    testTag = "search_secondary_languages_toggle",
                    onToggle = onToggleSecondaryLanguages,
                  )
                } else {
                  EditorialSurfaceCard(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentPadding = PaddingValues(DipeSpacing.compact),
                    modifier = Modifier.testTag("search_secondary_only_notice"),
                  ) {
                    Text(
                      text = stringResource(R.string.search_secondary_languages_only_notice),
                      style =
                        TextStyle(
                          fontFamily = InterFontFamily,
                          fontWeight = FontWeight.Normal,
                          fontSize = 12.5.sp,
                          lineHeight = 17.sp,
                        ),
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }

            if (showSecondaryList) {
              flattenedWorkGroupItems(
                sectionPrefix = "sec",
                groups = displayResponse.secondaryWorkGroups,
                indexOffset = indexOffset,
                editionColumns = editionColumns,
                genresBySlug = uiState.genresBySlug,
                language = language,
                expandedGroupIds = uiState.expandedGroupIds,
                expandedSecondaryEditionGroupIds = uiState.expandedSecondaryEditionGroupIds,
                loadingMoreEditionsForGroups = uiState.loadingMoreEditionsForGroups,
                exhaustedEditionGroupIds = uiState.exhaustedEditionGroupIds,
                selectedEditionId = effectiveSelectedEditionId,
                revealedGalleryPartsByGroup = revealedGalleryPartsByGroup,
                reduceMotion = reduceMotion,
                resultsAlphaProvider = { resultsAlpha },
                windowLimitFor = windowLimitFor,
                onToggleExpand = onToggleGroupExpanded,
                onShowMoreLocalEditions = handleShowMoreLocalEditions,
                onToggleSecondaryEditions = onToggleGroupSecondaryEditions,
                onLoadMoreEditions = onLoadMoreEditions,
                onToggleEdition = handleToggleEditionSelection,
                onConfirmSelectedEdition = onSelectEdition,
              )
            }
          } else if (
            displayResponse.workGroups.isEmpty() &&
              displayResponse.derivativeWorkGroups.isEmpty() &&
              !uiState.isSearching &&
              !uiState.hasSearchError &&
              displayResponse.localMatches.isEmpty()
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
          if (displayResponse.derivativeWorkGroups.isNotEmpty()) {
            val hasLiterary =
              displayResponse.workGroups.isNotEmpty() ||
                displayResponse.secondaryWorkGroups.isNotEmpty()
            val showDerivatives = !hasLiterary || uiState.isDerivativeWorksExpanded
            val derivOffset =
              displayResponse.workGroups.size + displayResponse.secondaryWorkGroups.size

            item(key = "search_derivative_works_header", contentType = "drawer_toggle") {
              Box(modifier = Modifier.fillMaxWidth().padding(top = DipeSpacing.md)) {
                if (hasLiterary) {
                  SearchContextualDrawerHeader(
                    title = stringResource(R.string.search_derivative_section_title),
                    count = displayResponse.derivativeWorkGroups.size,
                    isExpanded = uiState.isDerivativeWorksExpanded,
                    testTag = "search_derivative_works_toggle",
                    onToggle = onToggleDerivativeWorks,
                  )
                } else {
                  EditorialSurfaceCard(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentPadding = PaddingValues(DipeSpacing.compact),
                    modifier = Modifier.testTag("search_derivative_only_notice"),
                  ) {
                    Text(
                      text = stringResource(R.string.search_derivative_only_notice),
                      style =
                        TextStyle(
                          fontFamily = InterFontFamily,
                          fontWeight = FontWeight.Normal,
                          fontSize = 12.5.sp,
                          lineHeight = 17.sp,
                        ),
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }

            if (showDerivatives) {
              flattenedWorkGroupItems(
                sectionPrefix = "deriv",
                groups = displayResponse.derivativeWorkGroups,
                indexOffset = derivOffset,
                editionColumns = editionColumns,
                genresBySlug = uiState.genresBySlug,
                language = language,
                expandedGroupIds = uiState.expandedGroupIds,
                expandedSecondaryEditionGroupIds = uiState.expandedSecondaryEditionGroupIds,
                loadingMoreEditionsForGroups = uiState.loadingMoreEditionsForGroups,
                exhaustedEditionGroupIds = uiState.exhaustedEditionGroupIds,
                selectedEditionId = effectiveSelectedEditionId,
                revealedGalleryPartsByGroup = revealedGalleryPartsByGroup,
                reduceMotion = reduceMotion,
                resultsAlphaProvider = { resultsAlpha },
                windowLimitFor = windowLimitFor,
                onToggleExpand = onToggleGroupExpanded,
                onShowMoreLocalEditions = handleShowMoreLocalEditions,
                onToggleSecondaryEditions = onToggleGroupSecondaryEditions,
                onLoadMoreEditions = onLoadMoreEditions,
                onToggleEdition = handleToggleEditionSelection,
                onConfirmSelectedEdition = onSelectEdition,
              )
            }
          }

          // Natural list-continuation load-more action
          if (displayResponse.hasMoreResults && !displayResponse.isOffline && !uiState.isSearching) {
            item(key = "search_load_more_results", contentType = "footer_button") {
              Surface(
                onClick = onLoadMoreResults,
                enabled = !uiState.isLoadingMoreResults,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(top = DipeSpacing.md)
                    .minimumInteractiveComponentSize()
                    .testTag("search_load_more_results_button"),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                  horizontalArrangement = Arrangement.Center,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  if (uiState.isLoadingMoreResults) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(15.dp),
                      strokeWidth = 1.8.dp,
                      color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(modifier = Modifier.width(DipeSpacing.xs))
                    Text(
                      text = stringResource(R.string.search_loading_more_results),
                      style = secondaryButtonTextStyle,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  } else {
                    Text(
                      text = stringResource(R.string.search_action_load_more_results),
                      style = secondaryButtonTextStyle,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              }
            }
          }

          if (
            !displayResponse.expandedSecondarySources &&
              !displayResponse.isIsbnQuery &&
              !displayResponse.isOffline &&
              !uiState.isSearching
          ) {
            item(key = "search_expand_sources", contentType = "footer_button") {
              Box(
                modifier = Modifier.fillMaxWidth().padding(top = DipeSpacing.sm),
                contentAlignment = Alignment.Center,
              ) {
                TextButton(
                  onClick = onExpandSecondarySources,
                  shape = CircleShape,
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("search_expand_sources_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ManageSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.88f),
                    modifier = Modifier.size(17.dp),
                  )
                  Spacer(modifier = Modifier.width(7.dp))
                  Text(
                    text = stringResource(R.string.search_action_expand_sources),
                    style =
                      TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                      ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }

          // Clear, understated manual registration footer below results
          if (hasAnyDisplayWorks && !uiState.isSearching) {
            item(key = "search_manual_footer", contentType = "manual_footer") {
              Column(
                modifier = Modifier.fillMaxWidth().padding(top = DipeSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
              ) {
                Text(
                  text = stringResource(R.string.search_manual_footer_hint),
                  style =
                    TextStyle(
                      fontFamily = InterFontFamily,
                      fontWeight = FontWeight.Normal,
                      fontSize = 13.sp,
                      lineHeight = 18.sp,
                    ),
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
                    style = secondaryButtonTextStyle,
                    color = MaterialTheme.colorScheme.secondary,
                  )
                }
              }
            }
          }

          if (uiState.hasSearchError && !hasAnyDisplayWorks && !uiState.isSearching) {
            item(key = "search_response_error_state", contentType = "empty_state") {
              EditorialEmptyState(
                icon = Icons.Outlined.CloudOff,
                title = stringResource(R.string.search_error_title),
                body = stringResource(R.string.search_error_body),
                actionLabel = stringResource(R.string.search_action_retry),
                actionTestTag = "search_error_retry_button",
                onAction = onSubmitSearch,
                modifier = Modifier.padding(top = DipeSpacing.compact),
              )
            }
          }

          // Subordinate, discreet source status strip at the bottom (below manual registration)
          val activeStatuses = response?.sourceStatuses.orEmpty()
          val bottomSourceStatuses =
            if (isRefreshingExistingResults && activeStatuses.isNotEmpty()) {
              activeStatuses
            } else {
              displayResponse.sourceStatuses
            }
          if (bottomSourceStatuses.isNotEmpty() && (hasAnyDisplayWorks || !uiState.isSearching)) {
            item(key = "search_bottom_source_status_bar", contentType = "bottom_source_status") {
              SearchBottomSourceStatusBar(
                sourceStatuses = bottomSourceStatuses,
                isSearching = uiState.isSearching,
                onRetrySearch = onSubmitSearch,
                modifier = Modifier.padding(top = DipeSpacing.xs),
              )
            }
          }
        } else if (uiState.hasSearchError && !uiState.isSearching && uiState.localLiveMatches.isEmpty()) {
          item(key = "search_error_state", contentType = "empty_state") {
            EditorialEmptyState(
              icon = Icons.Outlined.CloudOff,
              title = stringResource(R.string.search_error_title),
              body = stringResource(R.string.search_error_body),
              actionLabel = stringResource(R.string.search_action_retry),
              actionTestTag = "search_error_retry_button",
              onAction = onSubmitSearch,
              modifier = Modifier.padding(top = DipeSpacing.compact),
            )
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
  editionColumns: Int,
  genresBySlug: Map<String, Genre>,
  language: AppLanguage,
  expandedGroupIds: Set<String>,
  expandedSecondaryEditionGroupIds: Set<String>,
  loadingMoreEditionsForGroups: Set<String>,
  exhaustedEditionGroupIds: Set<String>,
  selectedEditionId: String?,
  revealedGalleryPartsByGroup: MutableMap<String, Set<String>>,
  reduceMotion: Boolean,
  resultsAlphaProvider: () -> Float,
  windowLimitFor: (String) -> Int,
  onToggleExpand: (String) -> Unit,
  onShowMoreLocalEditions: (String) -> Unit,
  onToggleSecondaryEditions: (String) -> Unit,
  onLoadMoreEditions: (SearchWorkGroup) -> Unit,
  onToggleEdition: (SearchEditionCandidate) -> Unit,
  onConfirmSelectedEdition: (SearchEditionCandidate) -> Unit,
) {
  val seenGroupIds = HashMap<String, Int>(groups.size)
  groups.forEachIndexed { localIdx, group ->
    val occurrence = (seenGroupIds[group.groupId] ?: 0) + 1
    seenGroupIds[group.groupId] = occurrence
    val uniqueKeySuffix = if (occurrence == 1) group.groupId else "${group.groupId}__dup$occurrence"
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
    val selectedEditionInGroup =
      if (isExpanded && selectedEditionId != null) {
        displayedEditions.firstOrNull { it.candidateId == selectedEditionId }
      } else {
        null
      }
    val showActionsFooter =
      isExpanded &&
        (selectedEditionInGroup != null ||
          remainingEditions > 0 ||
          group.secondaryTierEditions.isNotEmpty() ||
          canLoadMoreRemote ||
          displayedEditions.size > editionColumns)
    val hasExpandedContentBelow =
      isExpanded && (displayedEditions.isNotEmpty() || showActionsFooter)
    val genreName = group.suggestedGenreSlug?.let { genresBySlug[it]?.localizedName(language) }

    item(
      key = "${sectionPrefix}_hdr_$uniqueKeySuffix",
      contentType = "search_work_header",
    ) {
      val itemMotionModifier =
        if (reduceMotion) {
          Modifier
        } else {
          Modifier.animateItem(
            fadeInSpec = DipeTheme.motion.standardTween(180),
            fadeOutSpec = DipeTheme.motion.standardTween(130),
            placementSpec = DipeTheme.motion.editorialSpring(),
          )
        }
      SearchWorkHeaderItem(
        group = group,
        groupIndex = groupIndex,
        genreLabel = genreName,
        isExpanded = isExpanded,
        hasExpandedContentBelow = hasExpandedContentBelow,
        onToggleExpand = { onToggleExpand(group.groupId) },
        modifier = itemMotionModifier.graphicsLayer { alpha = resultsAlphaProvider() },
      )
    }

    if (displayedEditions.isNotEmpty()) {
      val editionRows = displayedEditions.chunked(editionColumns)
      itemsIndexed(
        items = editionRows,
        key = { rowIdx, rowEds ->
          "${sectionPrefix}_edrow_${uniqueKeySuffix}_${rowIdx}_${rowEds.firstOrNull()?.candidateId.orEmpty()}"
        },
        contentType = { _, _ -> "search_edition_gallery_row" },
      ) { rowIdx, rowEditions ->
        val isLastItemInGroup = !showActionsFooter && rowIdx == editionRows.lastIndex
        val rowPartKey = "${sectionPrefix}_row_${uniqueKeySuffix}_$rowIdx"
        val alreadyRevealed = rowPartKey in revealedGalleryPartsByGroup[group.groupId].orEmpty()
        val itemMotionModifier =
          if (reduceMotion) {
            Modifier
          } else {
            Modifier.animateItem(
              fadeInSpec = DipeTheme.motion.standardTween(180),
              fadeOutSpec = DipeTheme.motion.standardTween(130),
              placementSpec = DipeTheme.motion.editorialSpring(),
            )
          }
        SearchEditionGalleryRowItem(
          editions = rowEditions,
          columnsCount = editionColumns,
          groupIndex = groupIndex,
          rowIndex = rowIdx,
          rowStartIndex = rowIdx * editionColumns,
          isFirstGalleryRow = rowIdx == 0,
          isLastItemInGroup = isLastItemInGroup,
          selectedEditionId = selectedEditionId,
          shouldAnimateEntrance = !reduceMotion && !alreadyRevealed,
          onEntranceFinished = {
            revealedGalleryPartsByGroup[group.groupId] =
              revealedGalleryPartsByGroup[group.groupId].orEmpty() + rowPartKey
          },
          onSelectEdition = onToggleEdition,
          modifier = itemMotionModifier.graphicsLayer { alpha = resultsAlphaProvider() },
        )
      }
    }

    if (showActionsFooter) {
      item(
        key = "${sectionPrefix}_act_$uniqueKeySuffix",
        contentType = "search_work_actions",
      ) {
        val footerPartKey = "${sectionPrefix}_footer_$uniqueKeySuffix"
        val footerAlreadyRevealed =
          footerPartKey in revealedGalleryPartsByGroup[group.groupId].orEmpty()
        val itemMotionModifier =
          if (reduceMotion) {
            Modifier
          } else {
            Modifier.animateItem(
              fadeInSpec = DipeTheme.motion.standardTween(180),
              fadeOutSpec = DipeTheme.motion.standardTween(130),
              placementSpec = DipeTheme.motion.editorialSpring(),
            )
          }
        SearchWorkActionsFooterItem(
          groupIndex = groupIndex,
          remainingEditions = remainingEditions,
          secondaryEditionsCount = group.secondaryTierEditions.size,
          isSecondaryEditionsExpanded = isSecondaryEditionsExpanded,
          canLoadMoreRemoteEditions = canLoadMoreRemote,
          isLoadingMore = isLoadingMore,
          showCollapseAction = displayedEditions.size > editionColumns,
          selectedEdition = selectedEditionInGroup,
          shouldAnimateEntrance = !reduceMotion && !footerAlreadyRevealed,
          onEntranceFinished = {
            revealedGalleryPartsByGroup[group.groupId] =
              revealedGalleryPartsByGroup[group.groupId].orEmpty() + footerPartKey
          },
          onConfirmSelectedEdition = onConfirmSelectedEdition,
          onShowMoreLocalEditions = { onShowMoreLocalEditions(group.groupId) },
          onToggleSecondaryEditions = { onToggleSecondaryEditions(group.groupId) },
          onLoadMoreRemoteEditions = { onLoadMoreEditions(group) },
          onCollapseGroup = { onToggleExpand(group.groupId) },
          modifier = itemMotionModifier.graphicsLayer { alpha = resultsAlphaProvider() },
        )
      }
    }
  }
}
