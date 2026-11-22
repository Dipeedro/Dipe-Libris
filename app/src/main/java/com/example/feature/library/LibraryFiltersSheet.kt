package com.example.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.Genre
import com.example.core.model.LibrarySortOrder

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LibraryFiltersSheet(
  totalCatalogCount: Int,
  genreFilter: Long?,
  availableGenres: List<Genre>,
  sortOrder: LibrarySortOrder,
  isCheckingUpdates: Boolean,
  totalPendingSuggestionsCount: Int,
  onGenreFilterChange: (Long?) -> Unit,
  onSortOrderChange: (LibrarySortOrder) -> Unit,
  onCheckUpdates: () -> Unit,
  onClearFilters: () -> Unit,
  onDismiss: () -> Unit,
) {
  val language = LocalAppLanguage.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val hasCustomFilterOrSort = genreFilter != null || sortOrder != LibrarySortOrder.RECENTLY_ADDED

  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = MaterialTheme.colorScheme.primary,
      selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
      labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    modifier = Modifier.testTag("library_filters_sheet"),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = DipeSpacing.lg)
          .padding(bottom = DipeSpacing.lg),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.library_filters_sheet_title),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        if (hasCustomFilterOrSort) {
          TextButton(
            onClick = onClearFilters,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("library_filters_clear_button"),
          ) {
            Text(
              text = stringResource(R.string.library_filters_clear),
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        }
      }

      if (availableGenres.isNotEmpty()) {
        EditorialSectionHeader(title = stringResource(R.string.library_filters_genre_section))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        ) {
          FilterChip(
            selected = genreFilter == null,
            onClick = { onGenreFilterChange(null) },
            label = { Text(stringResource(R.string.library_filters_all_genres)) },
            shape = CircleShape,
            colors = chipColors,
            border = null,
            modifier = Modifier.testTag("library_genre_filter_all"),
          )
          availableGenres.forEach { genre ->
            val selected = genreFilter == genre.id
            FilterChip(
              selected = selected,
              onClick = { onGenreFilterChange(if (selected) null else genre.id) },
              label = { Text(genre.localizedName(language)) },
              shape = CircleShape,
              colors = chipColors,
              border = null,
              modifier = Modifier.testTag("library_genre_filter_${genre.slug}"),
            )
          }
        }
        HairlineDivider()
      }

      EditorialSectionHeader(title = stringResource(R.string.library_filters_sort_section))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
      ) {
        LibrarySortOrder.entries.forEach { order ->
          val selected = sortOrder == order
          val label =
            when (order) {
              LibrarySortOrder.RECENTLY_ADDED -> stringResource(R.string.library_sort_recent)
              LibrarySortOrder.TITLE_ASC -> stringResource(R.string.library_sort_title)
              LibrarySortOrder.AUTHOR_ASC -> stringResource(R.string.library_sort_author)
            }
          FilterChip(
            selected = selected,
            onClick = { onSortOrderChange(order) },
            leadingIcon =
              if (selected) {
                {
                  Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                }
              } else {
                null
              },
            label = { Text(label) },
            shape = CircleShape,
            colors = chipColors,
            border = null,
            modifier = Modifier.testTag("library_sort_option_${order.name.lowercase()}"),
          )
        }
      }

      if (totalCatalogCount > 0) {
        HairlineDivider()
        EditorialSectionHeader(title = stringResource(R.string.library_filters_tools_section))
        EditorialSurfaceCard(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          shape = MaterialTheme.shapes.medium,
          contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.compact),
          onClick = if (isCheckingUpdates) null else onCheckUpdates,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("library_check_updates_button"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (isCheckingUpdates) {
                CircularProgressIndicator(
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(18.dp),
                )
              } else {
                Icon(
                  imageVector = Icons.Outlined.Sync,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(18.dp),
                )
              }
              Text(
                text = stringResource(R.string.library_check_updates),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            if (totalPendingSuggestionsCount > 0) {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
              ) {
                Text(
                  text = "+$totalPendingSuggestionsCount",
                  style = LocalDipeTypography.current.monoMetadata,
                  color = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(DipeSpacing.xs))
    }
  }
}
