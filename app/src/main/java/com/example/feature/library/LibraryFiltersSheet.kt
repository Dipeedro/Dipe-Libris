package com.example.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.components.EditorialModalBottomSheet
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.Genre
import com.example.core.model.LibrarySortOrder

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryFiltersSheet(
  filteredCount: Int,
  genreFilter: Long?,
  availableGenres: List<Genre>,
  sortOrder: LibrarySortOrder,
  onGenreFilterChange: (Long?) -> Unit,
  onSortOrderChange: (LibrarySortOrder) -> Unit,
  onClearFilters: () -> Unit,
  onDismiss: () -> Unit,
) {
  val language = LocalAppLanguage.current
  val selectedIconTint =
    if (MaterialTheme.colorScheme.primary.luminance() < 0.45f) Color.White
    else MaterialTheme.colorScheme.onPrimary

  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = MaterialTheme.colorScheme.primary,
      selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
      selectedLeadingIconColor = selectedIconTint,
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
      labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

  EditorialModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetTestTag = "library_filters_sheet",
    scrollableContent = true,
    footer = { requestDismiss ->
      Column(modifier = Modifier.fillMaxWidth()) {
        HairlineDivider()
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.md),
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = onClearFilters,
            shape = CircleShape,
            border = BorderStroke(1.dp, DipeTheme.colors.controlBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = DipeTheme.colors.textPrimary,
              ),
            contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.compact),
            modifier =
              Modifier.heightIn(min = 48.dp).testTag("library_filters_clear_button"),
          ) {
            Text(
              text = stringResource(R.string.library_filters_clear_short),
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
            )
          }

          Button(
            onClick = requestDismiss,
            shape = CircleShape,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
              ),
            contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.compact),
            modifier =
              Modifier.weight(1f)
                .heightIn(min = 48.dp)
                .testTag("library_filters_show_button"),
          ) {
            Text(
              text =
                pluralStringResource(
                  R.plurals.library_filters_show_books,
                  if (filteredCount == 1) 1 else 2,
                  filteredCount,
                ),
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    },
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = DipeSpacing.lg)
          .padding(bottom = DipeSpacing.lg),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      Text(
        text = stringResource(R.string.library_filters_sheet_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )

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
              LibrarySortOrder.PROGRESS_DESC -> stringResource(R.string.library_sort_progress)
              LibrarySortOrder.PAGES_DESC -> stringResource(R.string.library_sort_pages)
              LibrarySortOrder.YEAR_DESC -> stringResource(R.string.library_sort_year)
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
                    tint = selectedIconTint,
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
    }
  }
}
