package com.example.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.model.ReadingStatus

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
  HorizontalDivider(
    modifier = modifier,
    thickness = 0.75.dp,
    color = MaterialTheme.colorScheme.outlineVariant,
  )
}

@Composable
fun EditorialSubscreenTopBar(
  title: String?,
  backButtonTag: String,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  showBottomDivider: Boolean = true,
  trailingContent: @Composable (RowScope.() -> Unit)? = null,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onNavigateBack,
          modifier = Modifier.minimumInteractiveComponentSize().testTag(backButtonTag),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
          )
        }
        if (!title.isNullOrBlank()) {
          Spacer(modifier = Modifier.width(DipeSpacing.xs))
          Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (trailingContent != null) {
        Row(verticalAlignment = Alignment.CenterVertically, content = trailingContent)
      }
    }
    if (showBottomDivider) {
      HairlineDivider()
    }
  }
}

@Composable
fun EditorialSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  actionTag: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = DipeSpacing.xs),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title.uppercase(),
        style = LocalDipeTypography.current.editorialKicker,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (actionLabel != null && onAction != null) {
        TextButton(
          onClick = onAction,
          modifier =
            Modifier.minimumInteractiveComponentSize().let { base ->
              if (actionTag != null) base.testTag(actionTag) else base
            },
        ) {
          Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    }
    HairlineDivider()
  }
}

/**
 * Clean vertical-rule-separated metadata strip inspired by editorial catalog plates.
 */
@Composable
fun EditorialMetadataStrip(
  items: List<Pair<String, String>>,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    items.forEachIndexed { index, (label, value) ->
      if (index > 0) {
        VerticalDivider(
          modifier = Modifier.fillMaxHeight().padding(vertical = DipeSpacing.xxs),
          thickness = 0.75.dp,
          color = MaterialTheme.colorScheme.outlineVariant,
        )
      }
      Column(
        modifier = Modifier.weight(1f).padding(horizontal = DipeSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs),
      ) {
        Text(
          text = label.uppercase(),
          style = LocalDipeTypography.current.editorialKicker,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = value,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun EditorialEmptyState(
  icon: ImageVector,
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  actionTestTag: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = DipeSpacing.lg),
    horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
  ) {
    Box(
      modifier =
        Modifier.width(2.dp)
          .fillMaxHeight()
          .background(MaterialTheme.colorScheme.outlineVariant),
    )
    Column(
      modifier = Modifier.weight(1f),
      horizontalAlignment = Alignment.Start,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.height(DipeSpacing.sm))
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (body.isNotBlank()) {
        Spacer(modifier = Modifier.height(DipeSpacing.xs))
        Text(
          text = body,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (actionLabel != null && onAction != null) {
        Spacer(modifier = Modifier.height(DipeSpacing.md))
        OutlinedButton(
          onClick = onAction,
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline),
          modifier =
            Modifier.minimumInteractiveComponentSize().let { base ->
              if (actionTestTag != null) base.testTag(actionTestTag) else base
            },
        ) {
          Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}

@Composable
fun readingStatusLabel(status: ReadingStatus): String =
  when (status) {
    ReadingStatus.UNREAD -> stringResource(R.string.status_unread)
    ReadingStatus.READING -> stringResource(R.string.status_reading)
    ReadingStatus.PAUSED -> stringResource(R.string.status_paused)
    ReadingStatus.FINISHED -> stringResource(R.string.status_finished)
    ReadingStatus.ABANDONED -> stringResource(R.string.status_abandoned)
  }

@Composable
fun readingStatusAccentColor(status: ReadingStatus): Color =
  when (status) {
    ReadingStatus.READING -> MaterialTheme.colorScheme.secondary
    ReadingStatus.FINISHED -> MaterialTheme.colorScheme.tertiary
    ReadingStatus.PAUSED -> MaterialTheme.colorScheme.outline
    ReadingStatus.UNREAD -> MaterialTheme.colorScheme.onSurfaceVariant
    ReadingStatus.ABANDONED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
  }
