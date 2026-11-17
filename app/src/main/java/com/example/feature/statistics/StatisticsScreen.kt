package com.example.feature.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.readingStatusAccentColor
import com.example.core.designsystem.components.readingStatusLabel
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.ArchiveSnapshot
import com.example.core.model.ReadingStatus
import kotlin.math.roundToInt

@Composable
fun StatisticsScreen(
  snapshot: ArchiveSnapshot,
  widthClass: WindowWidthClass,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  val listState = rememberLazyListState()
  val horizontalPadding =
    if (widthClass == WindowWidthClass.COMPACT) DipeSpacing.md else DipeSpacing.xl
  val completionRatio =
    if (snapshot.totalWorks > 0) {
      (snapshot.finishedCount.toFloat() / snapshot.totalWorks.toFloat()).coerceIn(0f, 1f)
    } else {
      0f
    }
  val completionPercent = (completionRatio * 100f).roundToInt()

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      state = listState,
      modifier = Modifier.widthIn(max = 820.dp).fillMaxSize().testTag("statistics_lazy_column"),
      contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = DipeSpacing.md),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xl),
    ) {
      item(key = "statistics_figures", contentType = "figures_grid") {
        Column(modifier = Modifier.fillMaxWidth()) {
          EditorialSectionHeader(title = stringResource(R.string.statistics_collection_summary))
          Spacer(modifier = Modifier.height(DipeSpacing.sm))

          Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = DipeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            StatisticFigure(
              value = LocaleManager.formatInteger(snapshot.totalWorks, language),
              label = stringResource(R.string.statistics_metric_works),
              modifier = Modifier.weight(1f),
            )
            VerticalDivider(
              modifier = Modifier.fillMaxHeight().padding(vertical = DipeSpacing.xs),
              thickness = 0.75.dp,
              color = MaterialTheme.colorScheme.outlineVariant,
            )
            StatisticFigure(
              value = LocaleManager.formatInteger(snapshot.readingCount, language),
              label = stringResource(R.string.statistics_metric_reading),
              modifier = Modifier.weight(1f).padding(start = DipeSpacing.md),
            )
          }

          HairlineDivider()

          Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = DipeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            StatisticFigure(
              value = LocaleManager.formatInteger(snapshot.finishedCount, language),
              label = stringResource(R.string.statistics_metric_finished),
              modifier = Modifier.weight(1f),
            )
            VerticalDivider(
              modifier = Modifier.fillMaxHeight().padding(vertical = DipeSpacing.xs),
              thickness = 0.75.dp,
              color = MaterialTheme.colorScheme.outlineVariant,
            )
            StatisticFigure(
              value = LocaleManager.formatInteger(snapshot.diaryEntriesCount, language),
              label = stringResource(R.string.statistics_metric_diary),
              modifier = Modifier.weight(1f).padding(start = DipeSpacing.md),
            )
          }

          HairlineDivider()
        }
      }

      item(key = "statistics_completion", contentType = "completion_bar") {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = stringResource(R.string.statistics_completion_label),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "${LocaleManager.formatInteger(completionPercent, language)}%",
              style = LocalDipeTypography.current.monoMetadata,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
          ) {
            if (completionRatio > 0f) {
              Box(
                modifier =
                  Modifier.fillMaxWidth(completionRatio)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
              )
            }
          }
        }
      }

      item(key = "statistics_distribution", contentType = "status_distribution") {
        val statusItems =
          listOf(
            ReadingStatus.READING to snapshot.readingCount,
            ReadingStatus.FINISHED to snapshot.finishedCount,
            ReadingStatus.UNREAD to snapshot.unreadCount,
            ReadingStatus.PAUSED to snapshot.pausedCount,
            ReadingStatus.ABANDONED to snapshot.abandonedCount,
          )
        val totalStatusCount = statusItems.sumOf { it.second }.coerceAtLeast(1)

        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.statistics_status_distribution))

          if (snapshot.totalWorks > 0) {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .height(6.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surfaceContainerHigh),
              horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
              statusItems.forEach { (status, count) ->
                if (count > 0) {
                  val weight = count.toFloat() / totalStatusCount.toFloat()
                  Box(
                    modifier =
                      Modifier.weight(weight)
                        .fillMaxHeight()
                        .background(readingStatusAccentColor(status)),
                  )
                }
              }
            }
          }

          Column {
            statusItems.forEach { (status, count) ->
              StatusDistributionRow(
                label = readingStatusLabel(status),
                count = LocaleManager.formatInteger(count, language),
                dotColor = readingStatusAccentColor(status),
              )
              HairlineDivider()
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StatisticFigure(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(vertical = DipeSpacing.xs),
    verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.displayLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun StatusDistributionRow(
  label: String,
  count: String,
  dotColor: Color,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = DipeSpacing.sm),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
    ) {
      Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dotColor))
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    Text(
      text = count,
      style = LocalDipeTypography.current.monoMetadata,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
