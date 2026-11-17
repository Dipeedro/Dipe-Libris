package com.example.feature.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.EditorialPageHeader
import com.example.core.designsystem.components.EditorialPlannedBlock
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.ArchiveSnapshot

@Composable
fun StatisticsScreen(
  snapshot: ArchiveSnapshot,
  widthClass: WindowWidthClass,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 860.dp).fillMaxSize(),
      contentPadding =
        PaddingValues(
          horizontal = if (widthClass == WindowWidthClass.COMPACT) DipeSpacing.md else DipeSpacing.xl,
          vertical = DipeSpacing.lg,
        ),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xl),
    ) {
      item {
        EditorialPageHeader(
          kicker = stringResource(R.string.home_editorial_kicker),
          title = stringResource(R.string.statistics_title),
          subtitle = stringResource(R.string.statistics_subtitle),
        )
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.statistics_collection_summary))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
          ) {
            StatisticPlate(
              value = LocaleManager.formatInteger(snapshot.totalWorks, language),
              label = stringResource(R.string.statistics_metric_works),
              modifier = Modifier.weight(1f),
            )
            StatisticPlate(
              value = LocaleManager.formatInteger(snapshot.readingCount, language),
              label = stringResource(R.string.statistics_metric_reading),
              modifier = Modifier.weight(1f),
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
          ) {
            StatisticPlate(
              value = LocaleManager.formatInteger(snapshot.finishedCount, language),
              label = stringResource(R.string.statistics_metric_finished),
              modifier = Modifier.weight(1f),
            )
            StatisticPlate(
              value = LocaleManager.formatInteger(snapshot.diaryEntriesCount, language),
              label = stringResource(R.string.statistics_metric_diary),
              modifier = Modifier.weight(1f),
            )
          }
          Text(
            text = stringResource(R.string.statistics_empty_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.statistics_planned_section))
          EditorialPlannedBlock(
            title = stringResource(R.string.statistics_planned_cadence_title),
            description = stringResource(R.string.statistics_planned_cadence_body),
            icon = Icons.Outlined.AutoGraph,
          )
          EditorialPlannedBlock(
            title = stringResource(R.string.statistics_planned_authors_title),
            description = stringResource(R.string.statistics_planned_authors_body),
            icon = Icons.Outlined.Category,
          )
        }
      }
    }
  }
}

@Composable
private fun StatisticPlate(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = MaterialTheme.shapes.small,
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(DipeSpacing.md),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
