package com.example.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.model.ReadingStatus

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
  HorizontalDivider(
    modifier = modifier,
    thickness = 0.5.dp,
    color = DipeTheme.colors.dividerHairline,
  )
}

/**
 * Circular monochrome colophon seal combining a capital 'D' with a bound book & open page.
 * Strictly monochrome via DipeBrandTokens, independent from Paper/Ink/Sepia reading themes.
 */
@Composable
fun DipeBrandSeal(
  modifier: Modifier = Modifier,
  size: Dp = 36.dp,
  contentDescription: String? = null,
) {
  val brand = DipeTheme.brand
  val cdModifier =
    if (contentDescription != null) {
      Modifier.semantics {
        this.contentDescription = contentDescription
        this.role = Role.Image
      }
    } else {
      Modifier
    }

  Canvas(modifier = modifier.size(size).then(cdModifier)) {
    val w = this.size.width
    val h = this.size.height
    val radius = minOf(w, h) * 0.5f

    // Monochrome circular disc + hairline perimeter ring
    drawCircle(color = brand.badgeContainer, radius = radius)
    drawCircle(
      color = brand.badgeBorder,
      radius = radius - 0.5.dp.toPx(),
      style = Stroke(width = 1.dp.toPx()),
    )

    // Normalize 24x24 vector space inside the seal
    val unit = minOf(w, h) / 24f
    val strokeW = (1.65f * unit).coerceAtLeast(1.2.dp.toPx())
    val thinStrokeW = (1.15f * unit).coerceAtLeast(0.9.dp.toPx())

    // Book-D silhouette: left bound spine + D-curved outer cover
    val dBookPath =
      Path().apply {
        moveTo(7.4f * unit, 6.4f * unit)
        lineTo(11.6f * unit, 6.4f * unit)
        cubicTo(
          15.7f * unit,
          6.4f * unit,
          17.8f * unit,
          8.7f * unit,
          17.8f * unit,
          12.0f * unit,
        )
        cubicTo(
          17.8f * unit,
          15.3f * unit,
          15.7f * unit,
          17.6f * unit,
          11.6f * unit,
          17.6f * unit,
        )
        lineTo(7.4f * unit, 17.6f * unit)
        close()
      }

    drawPath(
      path = dBookPath,
      color = brand.badgeContent,
      style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    // Spine crease + turning inner page line referencing a bound book
    drawLine(
      color = brand.badgeContent,
      start = Offset(9.8f * unit, 6.7f * unit),
      end = Offset(9.8f * unit, 17.3f * unit),
      strokeWidth = thinStrokeW,
      cap = StrokeCap.Round,
    )
    val pageLeafPath =
      Path().apply {
        moveTo(9.8f * unit, 8.6f * unit)
        lineTo(13.6f * unit, 9.6f * unit)
        lineTo(13.6f * unit, 14.4f * unit)
        lineTo(9.8f * unit, 15.4f * unit)
      }
    drawPath(
      path = pageLeafPath,
      color = brand.badgeContent,
      style = Stroke(width = thinStrokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  }
}

/**
 * Editorial display masthead for top-level screens: commanding serif title,
 * optional muted subtitle/counter, and aligned trailing action.
 */
@Composable
fun EditorialMasthead(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  trailingContent: @Composable (RowScope.() -> Unit)? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth().padding(top = DipeSpacing.md, bottom = DipeSpacing.xs),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top,
  ) {
    Column(
      modifier = Modifier.weight(1f).padding(end = DipeSpacing.md),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
    ) {
      Text(
        text = title,
        style = LocalDipeTypography.current.editorialMasthead,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (trailingContent != null) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        content = trailingContent,
      )
    }
  }
}

/**
 * Borderless tonal surface card that creates depth through subtle color stepping
 * and generous internal padding rather than boxed outlines.
 */
@Composable
fun EditorialSurfaceCard(
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
  border: BorderStroke? = null,
  shape: Shape = MaterialTheme.shapes.large,
  contentPadding: PaddingValues = PaddingValues(DipeSpacing.lg),
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier =
      modifier.fillMaxWidth().let { base ->
        if (onClick != null) base.clip(shape).clickable(onClick = onClick) else base
      },
    shape = shape,
    color = containerColor,
    border = border,
  ) {
    Column(
      modifier = Modifier.padding(contentPadding),
      content = content,
    )
  }
}

@Composable
fun EditorialSubscreenTopBar(
  title: String?,
  backButtonTag: String,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  showBottomDivider: Boolean = false,
  floatingButtonChrome: Boolean = false,
  trailingContent: @Composable (RowScope.() -> Unit)? = null,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = DipeSpacing.xs),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (floatingButtonChrome) {
          Surface(
            onClick = onNavigateBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
            modifier = Modifier.size(44.dp).minimumInteractiveComponentSize().testTag(backButtonTag),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                modifier = Modifier.size(20.dp),
              )
            }
          }
        } else {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag(backButtonTag),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
        }
        if (!title.isNullOrBlank()) {
          Spacer(modifier = Modifier.width(DipeSpacing.sm))
          Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (trailingContent != null) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
          content = trailingContent,
        )
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
  showDivider: Boolean = false,
  onAction: (() -> Unit)? = null,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
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
          contentPadding = PaddingValues(horizontal = DipeSpacing.sm, vertical = DipeSpacing.xs),
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
    if (showDivider) {
      Spacer(modifier = Modifier.height(DipeSpacing.xs))
      HairlineDivider()
    }
  }
}

/**
 * Clean vertical-rule-separated metadata strip inspired by editorial catalog plates.
 * Allows multi-line values so edition notes and longer strings never disappear.
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
          modifier = Modifier.fillMaxHeight().padding(vertical = DipeSpacing.xs),
          thickness = 0.5.dp,
          color = MaterialTheme.colorScheme.outlineVariant,
        )
      }
      Column(
        modifier = Modifier.weight(1f).padding(horizontal = DipeSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
      ) {
        Text(
          text = label.uppercase(),
          style = LocalDipeTypography.current.editorialKicker,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = value,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center,
          maxLines = 2,
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
  EditorialSurfaceCard(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
    contentPadding = PaddingValues(DipeSpacing.lg),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.size(20.dp),
        )
      }
      Spacer(modifier = Modifier.height(DipeSpacing.xxs))
      Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (body.isNotBlank()) {
        Text(
          text = body,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (actionLabel != null && onAction != null) {
        Spacer(modifier = Modifier.height(DipeSpacing.xs))
        OutlinedButton(
          onClick = onAction,
          shape = CircleShape,
          border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline),
          contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.sm),
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

@Composable
fun EditorialProgressBar(
  progressPercent: Float,
  modifier: Modifier = Modifier,
  height: Dp = 3.dp,
  fillColor: Color = DipeTheme.colors.readingProgress,
  trackColor: Color = DipeTheme.colors.readingProgressTrack,
) {
  val targetFraction = (progressPercent / 100f).coerceIn(0f, 1f)
  val animatedFraction by
    animateFloatAsState(
      targetValue = targetFraction,
      animationSpec = DipeTheme.motion.standardTween(200),
      label = "editorial_progress_fraction",
    )
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(height)
        .clip(CircleShape)
        .background(trackColor),
  ) {
    if (animatedFraction > 0f) {
      Box(
        modifier =
          Modifier.fillMaxWidth(animatedFraction)
            .fillMaxHeight()
            .clip(CircleShape)
            .background(fillColor),
      )
    }
  }
}

@Composable
fun editionFormatLabel(format: com.example.core.model.EditionFormat): String =
  when (format) {
    com.example.core.model.EditionFormat.HARDCOVER -> stringResource(R.string.edition_format_hardcover)
    com.example.core.model.EditionFormat.PAPERBACK -> stringResource(R.string.edition_format_paperback)
    com.example.core.model.EditionFormat.POCKET -> stringResource(R.string.edition_format_pocket)
    com.example.core.model.EditionFormat.EBOOK -> stringResource(R.string.edition_format_ebook)
    com.example.core.model.EditionFormat.OTHER -> stringResource(R.string.edition_format_other)
  }

@Composable
fun copyMediaTypeLabel(mediaType: com.example.core.model.CopyMediaType): String =
  when (mediaType) {
    com.example.core.model.CopyMediaType.PHYSICAL -> stringResource(R.string.copy_media_physical)
    com.example.core.model.CopyMediaType.DIGITAL -> stringResource(R.string.copy_media_digital)
  }

@Composable
fun rememberDirectGalleryCoverPicker(
  onUriPicked: (android.net.Uri) -> Unit,
  onPickerUnavailable: () -> Unit = {},
): () -> Unit {
  val context = androidx.compose.ui.platform.LocalContext.current
  val viewContext = androidx.compose.ui.platform.LocalView.current.context
  val owner =
    androidx.activity.compose.LocalActivityResultRegistryOwner.current
      ?: androidx.compose.runtime.remember(context, viewContext) {
        context.findActivityResultRegistryOwner() ?: viewContext.findActivityResultRegistryOwner()
      }

  val latestOnUriPicked by androidx.compose.runtime.rememberUpdatedState(onUriPicked)
  val latestOnUnavailable by androidx.compose.runtime.rememberUpdatedState(onPickerUnavailable)
  val actionHolder =
    androidx.compose.runtime.remember {
      object {
        var action: () -> Unit = {}
      }
    }

  if (owner != null) {
    androidx.compose.runtime.CompositionLocalProvider(
      androidx.activity.compose.LocalActivityResultRegistryOwner provides owner
    ) {
      val handlePickedUri: (android.net.Uri?) -> Unit = { uri ->
        if (uri != null) {
          runCatching {
            context.contentResolver.takePersistableUriPermission(
              uri,
              android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
          }
          latestOnUriPicked(uri)
        }
      }

      val galleryLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
          androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
          if (result.resultCode == android.app.Activity.RESULT_OK) {
            handlePickedUri(result.data?.data)
          }
        }

      val photoPickerLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
          androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
          onResult = handlePickedUri,
        )

      val getContentLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
          androidx.activity.result.contract.ActivityResultContracts.GetContent(),
          onResult = handlePickedUri,
        )

      androidx.compose.runtime.SideEffect {
        actionHolder.action = {
          val galleryIntent =
            android.content.Intent(android.content.Intent.ACTION_PICK).apply {
              setDataAndType(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "image/*",
              )
            }
          val launchedGallery = runCatching { galleryLauncher.launch(galleryIntent) }.isSuccess
          if (!launchedGallery) {
            val launchedPhotoPicker =
              runCatching {
                  photoPickerLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                      androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                  )
                }
                .isSuccess
            if (!launchedPhotoPicker) {
              val launchedGetContent =
                runCatching { getContentLauncher.launch("image/*") }.isSuccess
              if (!launchedGetContent) {
                latestOnUnavailable()
              }
            }
          }
        }
      }
    }
  } else {
    androidx.compose.runtime.SideEffect {
      actionHolder.action = { latestOnUnavailable() }
    }
  }

  return androidx.compose.runtime.remember(actionHolder) { { actionHolder.action() } }
}

@Composable
fun rememberSafeCoverPicker(onUriPicked: (android.net.Uri) -> Unit): () -> Unit =
  rememberDirectGalleryCoverPicker(onUriPicked = onUriPicked)

private tailrec fun android.content.Context.findActivityResultRegistryOwner():
  androidx.activity.result.ActivityResultRegistryOwner? =
  when (this) {
    is androidx.activity.result.ActivityResultRegistryOwner -> this
    is android.content.ContextWrapper -> baseContext.findActivityResultRegistryOwner()
    else -> null
  }

