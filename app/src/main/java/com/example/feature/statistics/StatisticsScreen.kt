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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.EditorialMasthead
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.EditorialSurfaceCard
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
  val horizontalPadding = DipeSpacing.horizontalMargin(widthClass)
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
      contentPadding =
        PaddingValues(
          start = horizontalPadding,
          end = horizontalPadding,
          top = DipeSpacing.md,
          bottom = DipeSpacing.xxl,
        ),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xl),
    ) {
      item(key = "statistics_masthead", contentType = "masthead") {
        EditorialMasthead(
          title = stringResource(R.string.nav_statistics),
          subtitle = stringResource(R.string.statistics_masthead_subtitle),
        )
      }

      // Hero completion card
      item(key = "statistics_completion", contentType = "completion_bar") {
        EditorialSurfaceCard(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          contentPadding = PaddingValues(DipeSpacing.lg),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Bottom,
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
                Text(
                  text = stringResource(R.string.statistics_completion_label).uppercase(),
                  style = LocalDipeTypography.current.editorialKicker,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text =
                    stringResource(
                      R.string.statistics_completion_detail,
                      LocaleManager.formatInteger(snapshot.finishedCount, language),
                      LocaleManager.formatInteger(snapshot.totalWorks, language),
                    ),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Text(
                text = "${LocaleManager.formatInteger(completionPercent, language)}%",
                style =
                  MaterialTheme.typography.displayLarge.copy(
                    fontSize = 38.sp,
                    lineHeight = 42.sp,
                  ),
                color = MaterialTheme.colorScheme.onBackground,
              )
            }

            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(10.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
              if (completionRatio > 0f) {
                Box(
                  modifier =
                    Modifier.fillMaxWidth(completionRatio)
                      .height(10.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.secondary),
                )
              }
            }
          }
        }
      }

      // 2x2 tonal metric cards
      item(key = "statistics_figures", contentType = "figures_grid") {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
        ) {
          EditorialSectionHeader(title = stringResource(R.string.statistics_collection_summary))

          Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            StatisticMetricCard(
              icon = Icons.AutoMirrored.Outlined.LibraryBooks,
              value = LocaleManager.formatInteger(snapshot.totalWorks, language),
              label = stringResource(R.string.statistics_metric_works),
              modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatisticMetricCard(
              icon = Icons.Outlined.AutoStories,
              value = LocaleManager.formatInteger(snapshot.readingCount, language),
              label = stringResource(R.string.statistics_metric_reading),
              modifier = Modifier.weight(1f).fillMaxHeight(),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            StatisticMetricCard(
              icon = Icons.Outlined.CheckCircle,
              value = LocaleManager.formatInteger(snapshot.finishedCount, language),
              label = stringResource(R.string.statistics_metric_finished),
              modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatisticMetricCard(
              icon = Icons.Outlined.EditNote,
              value = LocaleManager.formatInteger(snapshot.diaryEntriesCount, language),
              label = stringResource(R.string.statistics_metric_diary),
              modifier = Modifier.weight(1f).fillMaxHeight(),
            )
          }
        }
      }

      // Status distribution card
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

          EditorialSurfaceCard(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentPadding = PaddingValues(DipeSpacing.lg),
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
              if (snapshot.totalWorks > 0) {
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .height(10.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                  horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                statusItems.forEachIndexed { index, (status, count) ->
                  val share =
                    if (snapshot.totalWorks > 0) {
                      ((count.toFloat() / snapshot.totalWorks.toFloat()) * 100f).roundToInt()
                    } else {
                      0
                    }
                  StatusDistributionRow(
                    label = readingStatusLabel(status),
                    count = LocaleManager.formatInteger(count, language),
                    percent = "${LocaleManager.formatInteger(share, language)}%",
                    dotColor = readingStatusAccentColor(status),
                  )
                  if (index < statusItems.lastIndex) {
                    HairlineDivider()
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StatisticMetricCard(
  icon: ImageVector,
  value: String,
  label: String,
  modifier: Modifier = Modifier,
) {
  EditorialSurfaceCard(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentPadding = PaddingValues(DipeSpacing.md),
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Box(
        modifier =
          Modifier.size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.size(16.dp),
        )
      }
      Spacer(modifier = Modifier.height(DipeSpacing.md))
      Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs)) {
        Text(
          text = value,
          style = MaterialTheme.typography.displayMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = label,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun StatusDistributionRow(
  label: String,
  count: String,
  percent: String,
  dotColor: Color,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
    ) {
      Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
    ) {
      Text(
        text = percent,
        style = LocalDipeTypography.current.monoMetadata,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
      )
      Text(
        text = count,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
