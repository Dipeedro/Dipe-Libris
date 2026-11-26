package com.example.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.bibliographic.domain.CoverRanker
import com.example.core.designsystem.DipeRadii
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.InterFontFamily
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.MicroMetadataItem
import com.example.core.designsystem.components.MinimalLanguageBadge
import com.example.core.model.CatalogBook
import com.example.core.model.DuplicateAssessment
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import com.example.core.model.SourceQueryStatus
import java.util.Locale

private val GENRE_SEPARATOR_REGEX =
  Regex("""\s+(?:e|and|&|-)\s+|[,/;·|—]""", RegexOption.IGNORE_CASE)

private val MINOR_TITLE_CONNECTORS =
  setOf(
    "a", "as", "o", "os", "e", "em", "de", "da", "das", "do", "dos",
    "para", "com", "sem", "sob", "por", "um", "uma", "uns", "umas",
    "no", "na", "nos", "nas", "ao", "aos", "à", "às", "ou",
    "of", "the", "and", "in", "on", "at", "to", "for", "by", "with", "an",
  )

private val ROMAN_NUMERAL_REGEX =
  Regex("^(X{1,3}(IX|IV|V?I{0,3})|IX|IV|VI{0,3}|I{1,3})$")

/**
 * Normalizes ugly ALL-CAPS or all-lowercase bibliographic titles/subtitles strictly for display,
 * without touching underlying bibliographic models or mixed-case curated titles.
 */
internal fun formatEditorialDisplayTitle(raw: String): String {
  val trimmed = raw.trim()
  if (trimmed.isEmpty()) return raw

  var upperCount = 0
  var lowerCount = 0
  for (ch in trimmed) {
    if (ch.isLetter()) {
      if (ch.isUpperCase()) upperCount++ else if (ch.isLowerCase()) lowerCount++
    }
  }
  val needsNormalization =
    (lowerCount == 0 && upperCount >= 4) || (upperCount == 0 && lowerCount >= 2)
  if (!needsNormalization) return raw

  val locale = Locale.getDefault()
  val tokens = trimmed.split(Regex("\\s+"))
  var forceCapitalizeNext = true
  return tokens.mapIndexed { idx, token ->
    val cleanAlpha = token.trim { !it.isLetterOrDigit() }
    val endsBoundary =
      token.endsWith(":") || token.endsWith("—") || token.endsWith("-") ||
        token.endsWith("?") || token.endsWith("!") || token.endsWith(".")

    val formatted =
      if (cleanAlpha.isNotEmpty() && ROMAN_NUMERAL_REGEX.matches(cleanAlpha.uppercase(Locale.US)) && upperCount > 0 && lowerCount == 0) {
        token.uppercase(Locale.US)
      } else {
        val lowerToken = token.lowercase(locale)
        val shouldKeepLower =
          !forceCapitalizeNext && idx > 0 && idx < tokens.lastIndex &&
            lowerToken.trim { !it.isLetter() } in MINOR_TITLE_CONNECTORS
        if (shouldKeepLower) {
          lowerToken
        } else {
          capitalizeFirstLetterPreservingPrefix(lowerToken, locale)
        }
      }
    forceCapitalizeNext = endsBoundary || token == ":" || token == "—" || token == "-"
    formatted
  }.joinToString(" ")
}

private fun capitalizeFirstLetterPreservingPrefix(token: String, locale: Locale): String {
  val firstLetterIdx = token.indexOfFirst { it.isLetter() }
  if (firstLetterIdx < 0) return token
  val sb = StringBuilder(token.length)
  sb.append(token, 0, firstLetterIdx)
  sb.append(token[firstLetterIdx].toString().uppercase(locale))
  if (firstLetterIdx + 1 < token.length) {
    sb.append(token, firstLetterIdx + 1, token.length)
  }
  return sb.toString()
}

@Composable
internal fun searchSansMetadataStyle(): TextStyle =
  TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.05.sp,
    fontFeatureSettings = "tnum",
  )

@Composable
internal fun SearchLocalMatchCard(
  book: CatalogBook,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val displayFontFamily = LocalDipeTypography.current.displayFontFamily
  val displayTitle = remember(book.title) { formatEditorialDisplayTitle(book.title) }

  EditorialSurfaceCard(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)),
    shape = MaterialTheme.shapes.medium,
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    onClick = onClick,
    modifier = modifier.testTag(testTag),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      BookCoverArtwork(
        title = book.title,
        author = book.author,
        isFavorite = book.isFavorite,
        coverUri = book.coverUri,
        compact = true,
        widthHintDp = 40.dp,
        modifier = Modifier.width(40.dp),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = displayTitle,
          style =
            TextStyle(
              fontFamily = displayFontFamily,
              fontWeight = FontWeight.Medium,
              fontSize = 15.5.sp,
              lineHeight = 20.sp,
              letterSpacing = (-0.1).sp,
            ),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = book.author,
          style =
            TextStyle(
              fontFamily = InterFontFamily,
              fontWeight = FontWeight.Normal,
              fontSize = 12.5.sp,
              lineHeight = 17.sp,
            ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          modifier = Modifier.padding(top = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          MicroMetadataItem(
            icon = Icons.Outlined.CheckCircle,
            text = stringResource(R.string.search_work_in_library_badge),
            tint = MaterialTheme.colorScheme.secondary,
            textColor = MaterialTheme.colorScheme.secondary,
            textStyle = searchSansMetadataStyle().copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
          )
          MinimalLanguageBadge(languageTag = book.primaryEdition?.language)
        }
      }
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
internal fun SearchWorkHeaderItem(
  group: SearchWorkGroup,
  groupIndex: Int,
  genreLabel: String?,
  isExpanded: Boolean,
  hasExpandedContentBelow: Boolean,
  onToggleExpand: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val cardCovers =
    remember(group.groupId, group.previewCoverUrl, group.previewFallbackCoverUrls) {
      val ordered =
        buildList {
          group.previewCoverUrl?.let { add(it) }
          addAll(group.previewFallbackCoverUrls)
        }.distinct()
      CoverRanker.selectForDisplaySize(ordered, maxWidthDp = 128)
    }

  val displayTitle =
    remember(group.canonicalTitle) { formatEditorialDisplayTitle(group.canonicalTitle) }
  val closedAuthorText =
    remember(group.primaryAuthors, group.displayAuthors) {
      group.primaryAuthors
        .take(2)
        .joinToString(", ") { it.name }
        .ifBlank { group.displayAuthors }
    }
  val releaseYear =
    remember(group.groupId, group.editions) {
      group.editions
        .mapNotNull { it.publicationYear?.takeIf { year -> year in 1400..2100 } }
        .minOrNull()
        ?: group.editions.firstNotNullOfOrNull { it.publicationYear }
    }
  val primaryGenreLabel =
    remember(genreLabel) {
      genreLabel
        ?.split(GENRE_SEPARATOR_REGEX, limit = 2)
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    }

  val motion = DipeTheme.motion
  val displayFontFamily = LocalDipeTypography.current.displayFontFamily
  val closedMetaStyle =
    TextStyle(
      fontFamily = InterFontFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 12.sp,
      lineHeight = 16.sp,
      fontFeatureSettings = "tnum",
    )

  val chevronRotation by
    animateFloatAsState(
      targetValue = if (isExpanded) 180f else 0f,
      animationSpec = motion.standardTween(200),
      label = "work_expand_chevron_rotation",
    )
  val bottomRadius by
    animateDpAsState(
      targetValue = if (hasExpandedContentBelow) 0.dp else DipeRadii.control,
      animationSpec = motion.standardTween(160),
      label = "work_header_bottom_radius",
    )
  val expandBadgeBg by
    animateColorAsState(
      targetValue =
        if (isExpanded) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
      animationSpec = motion.standardTween(160),
      label = "work_expand_badge_bg",
    )
  val expandChevronTint by
    animateColorAsState(
      targetValue =
        if (isExpanded) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
      animationSpec = motion.standardTween(160),
      label = "work_expand_chevron_tint",
    )

  val headerShape =
    RoundedCornerShape(
      topStart = DipeRadii.control,
      topEnd = DipeRadii.control,
      bottomStart = bottomRadius,
      bottomEnd = bottomRadius,
    )
  val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
  val expandLabel =
    stringResource(if (isExpanded) R.string.cd_collapse_section else R.string.cd_expand_section)

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shape = headerShape,
    border = if (hasExpandedContentBelow) null else BorderStroke(0.75.dp, borderColor),
    shadowElevation = 0.dp,
    modifier =
      modifier
        .fillMaxWidth()
        .padding(top = 10.dp)
        .testTag("search_work_card_$groupIndex"),
  ) {
    BoxWithConstraints(
      modifier =
        Modifier.fillMaxWidth()
          .clip(headerShape)
          .then(
            if (hasExpandedContentBelow) {
              Modifier.drawBehind {
                drawExpandedCardHeaderBorders(
                  color = borderColor,
                  strokeWidthPx = 0.75.dp.toPx(),
                  cornerRadiusPx = DipeRadii.control.toPx(),
                )
              }
            } else {
              Modifier
            }
          )
          .clickable(onClick = onToggleExpand),
    ) {
      val coverSlotWidth =
        when {
          maxWidth < 310.dp -> 92.dp
          maxWidth < 480.dp -> 102.dp
          else -> 114.dp
        }
      val maxCoverHeight = coverSlotWidth * 1.50f

      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .height(IntrinsicSize.Min)
              .padding(horizontal = 14.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Box(
            modifier =
              Modifier.width(coverSlotWidth)
                .heightIn(max = maxCoverHeight)
                .testTag("search_work_cover_$groupIndex"),
            contentAlignment = Alignment.TopCenter,
          ) {
            BookCoverArtwork(
              title = group.canonicalTitle,
              author = closedAuthorText,
              isFavorite = false,
              coverUri = cardCovers.firstOrNull(),
              fallbackCoverUris = cardCovers.drop(1),
              compact = false,
              elevated = true,
              shadowElevation = 3.dp,
              widthHintDp = coverSlotWidth,
              contentScale = ContentScale.Fit,
              modifier = Modifier.widthIn(max = coverSlotWidth).heightIn(max = maxCoverHeight),
            )
          }

          Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              Text(
                text = displayTitle,
                style =
                  TextStyle(
                    fontFamily = displayFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    letterSpacing = (-0.15).sp,
                  ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
              )

              if (closedAuthorText.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                  text = closedAuthorText,
                  style =
                    TextStyle(
                      fontFamily = InterFontFamily,
                      fontWeight = FontWeight.Normal,
                      fontSize = 13.sp,
                      lineHeight = 17.5.sp,
                    ),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              if (releaseYear != null || primaryGenreLabel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  if (releaseYear != null) {
                    Text(
                      text = releaseYear.toString(),
                      style = closedMetaStyle,
                      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                      maxLines = 1,
                      softWrap = false,
                    )
                  }
                  if (releaseYear != null && primaryGenreLabel != null) {
                    Text(
                      text = "·",
                      style = closedMetaStyle,
                      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    )
                  }
                  if (primaryGenreLabel != null) {
                    Text(
                      text = primaryGenreLabel,
                      style = closedMetaStyle,
                      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f, fill = false),
                    )
                  }
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
              horizontalArrangement = Arrangement.End,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                      role = Role.Button,
                      onClickLabel = expandLabel,
                      onClick = onToggleExpand,
                    )
                    .testTag("search_work_expand_$groupIndex"),
                contentAlignment = Alignment.Center,
              ) {
                Box(
                  modifier =
                    Modifier.size(28.dp)
                      .clip(CircleShape)
                      .background(expandBadgeBg),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = expandLabel,
                    tint = expandChevronTint,
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = chevronRotation },
                  )
                }
              }
            }
          }
        }

        if (hasExpandedContentBelow) {
          HairlineDivider(
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

private fun DrawScope.drawExpandedCardHeaderBorders(
  color: Color,
  strokeWidthPx: Float,
  cornerRadiusPx: Float,
) {
  val halfStroke = strokeWidthPx * 0.5f
  val rightX = size.width - halfStroke
  val r = cornerRadiusPx.coerceAtMost(size.width * 0.5f).coerceAtMost(size.height)
  val path =
    Path().apply {
      moveTo(halfStroke, size.height)
      lineTo(halfStroke, halfStroke + r)
      quadraticTo(halfStroke, halfStroke, halfStroke + r, halfStroke)
      lineTo(rightX - r, halfStroke)
      quadraticTo(rightX, halfStroke, rightX, halfStroke + r)
      lineTo(rightX, size.height)
    }
  drawPath(path = path, color = color, style = Stroke(width = strokeWidthPx))
}

private fun DrawScope.drawExpandedCardSegmentBorders(
  color: Color,
  strokeWidthPx: Float,
  roundBottom: Boolean,
  cornerRadiusPx: Float,
) {
  val halfStroke = strokeWidthPx * 0.5f
  val rightX = size.width - halfStroke
  if (!roundBottom) {
    drawLine(
      color = color,
      start = Offset(halfStroke, 0f),
      end = Offset(halfStroke, size.height),
      strokeWidth = strokeWidthPx,
    )
    drawLine(
      color = color,
      start = Offset(rightX, 0f),
      end = Offset(rightX, size.height),
      strokeWidth = strokeWidthPx,
    )
  } else {
    val bottomY = size.height - halfStroke
    val r = cornerRadiusPx.coerceAtMost(size.width * 0.5f).coerceAtMost(size.height)
    val path =
      Path().apply {
        moveTo(halfStroke, 0f)
        lineTo(halfStroke, bottomY - r)
        quadraticTo(halfStroke, bottomY, halfStroke + r, bottomY)
        lineTo(rightX - r, bottomY)
        quadraticTo(rightX, bottomY, rightX, bottomY - r)
        lineTo(rightX, 0f)
      }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidthPx))
  }
}

@Composable
internal fun SearchEditionGalleryRowItem(
  editions: List<SearchEditionCandidate>,
  columnsCount: Int,
  groupIndex: Int,
  rowIndex: Int,
  rowStartIndex: Int,
  isFirstGalleryRow: Boolean,
  isLastItemInGroup: Boolean,
  selectedEditionId: String?,
  shouldAnimateEntrance: Boolean,
  onEntranceFinished: () -> Unit,
  onSelectEdition: (SearchEditionCandidate) -> Unit,
  modifier: Modifier = Modifier,
) {
  val reduceMotion = DipeTheme.motion.reduceMotion
  val density = LocalDensity.current
  val glidePx = remember(density) { with(density) { (-6).dp.toPx() } }
  val revealProgress =
    remember(groupIndex, rowIndex) {
      Animatable(if (reduceMotion || !shouldAnimateEntrance) 1f else 0f)
    }

  LaunchedEffect(shouldAnimateEntrance, reduceMotion) {
    if (reduceMotion || !shouldAnimateEntrance) {
      revealProgress.snapTo(1f)
    } else if (revealProgress.value < 1f) {
      revealProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
      )
      onEntranceFinished()
    }
  }

  val itemShape =
    if (isLastItemInGroup) {
      RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = DipeRadii.control,
        bottomEnd = DipeRadii.control,
      )
    } else {
      RectangleShape
    }
  val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = itemShape,
    shadowElevation = 0.dp,
    modifier =
      modifier
        .fillMaxWidth()
        .graphicsLayer {
          val p = if (reduceMotion) 1f else revealProgress.value
          alpha = p
          translationY = (1f - p) * glidePx
        },
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .drawBehind {
            drawExpandedCardSegmentBorders(
              color = borderColor,
              strokeWidthPx = 0.75.dp.toPx(),
              roundBottom = isLastItemInGroup,
              cornerRadiusPx = DipeRadii.control.toPx(),
            )
          }
          .padding(
            start = 8.dp,
            end = 8.dp,
            top = if (isFirstGalleryRow) 10.dp else 4.dp,
            bottom = if (isLastItemInGroup) 10.dp else 4.dp,
          ),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.Top,
    ) {
      for (colIdx in 0 until columnsCount) {
        val edition = editions.getOrNull(colIdx)
        if (edition != null) {
          val globalIdx = rowStartIndex + colIdx
          SearchEditionGalleryTile(
            edition = edition,
            isSelected = selectedEditionId == edition.candidateId,
            testTag = "search_edition_select_${groupIndex}_$globalIdx",
            onClick = { onSelectEdition(edition) },
            modifier = Modifier.weight(1f),
          )
        } else {
          Spacer(modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun SearchEditionGalleryTile(
  edition: SearchEditionCandidate,
  isSelected: Boolean,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val motion = DipeTheme.motion
  val reduceMotion = motion.reduceMotion
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()

  val pressScale by
    animateFloatAsState(
      targetValue = if (isPressed && !reduceMotion) 0.975f else 1f,
      animationSpec = motion.standardTween(90),
      label = "edition_tile_press_scale",
    )
  val tileBackground by
    animateColorAsState(
      targetValue =
        when {
          isSelected -> MaterialTheme.colorScheme.surfaceContainerLowest
          isPressed -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.65f)
          else -> Color.Transparent
        },
      animationSpec = motion.standardTween(150),
      label = "edition_tile_bg",
    )
  val tileBorderColor by
    animateColorAsState(
      targetValue =
        if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.68f)
        else Color.Transparent,
      animationSpec = motion.standardTween(150),
      label = "edition_tile_border",
    )

  val compactEditionCovers =
    remember(edition.candidateId, edition.orderedCoverUrls) {
      CoverRanker.selectForDisplaySize(edition.orderedCoverUrls, maxWidthDp = 104)
    }
  val missingLabel = stringResource(R.string.search_metadata_missing_placeholder)
  val publisherText =
    remember(edition.publisher, missingLabel) {
      edition.publisher?.trim()?.takeIf { it.isNotEmpty() } ?: missingLabel
    }
  val yearText =
    remember(edition.publicationYear, missingLabel) {
      edition.publicationYear?.takeIf { it in 1400..2100 }?.toString() ?: missingLabel
    }
  val pagesText =
    remember(edition.totalPages, missingLabel) {
      edition.totalPages?.takeIf { it > 0 }?.toString() ?: missingLabel
    }
  val selectLabel = stringResource(R.string.search_edition_select_action)
  val selectedCd = stringResource(R.string.cd_edition_selected)

  val publisherStyle =
    TextStyle(
      fontFamily = InterFontFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 11.5.sp,
      lineHeight = 14.5.sp,
    )
  val metaSecondaryStyle =
    TextStyle(
      fontFamily = InterFontFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 10.5.sp,
      lineHeight = 14.sp,
      fontFeatureSettings = "tnum",
    )

  val tileShape = RoundedCornerShape(8.dp)

  Column(
    modifier =
      modifier
        .graphicsLayer {
          scaleX = pressScale
          scaleY = pressScale
        }
        .clip(tileShape)
        .background(tileBackground, tileShape)
        .then(
          if (isSelected) {
            Modifier.border(1.dp, tileBorderColor, tileShape)
          } else {
            Modifier
          }
        )
        .clickable(
          interactionSource = interactionSource,
          indication = androidx.compose.material3.ripple(),
          onClickLabel = selectLabel,
          onClick = onClick,
        )
        .padding(6.dp)
        .testTag(testTag),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
      contentAlignment = Alignment.BottomCenter,
    ) {
      BookCoverArtwork(
        title = edition.workTitle,
        author = edition.primaryAuthorsText,
        isFavorite = false,
        coverUri = compactEditionCovers.firstOrNull(),
        fallbackCoverUris = compactEditionCovers.drop(1),
        compact = true,
        elevated = true,
        shadowElevation = if (isSelected) 4.dp else 2.dp,
        widthHintDp = 104.dp,
        contentScale = ContentScale.Fit,
      )

      androidx.compose.animation.AnimatedVisibility(
        visible = isSelected,
        enter = fadeIn(animationSpec = motion.standardTween(140)),
        exit = fadeOut(animationSpec = motion.standardTween(100)),
        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
      ) {
        Box(
          modifier =
            Modifier.size(18.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.secondary)
              .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = selectedCd,
            tint = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.size(11.dp),
          )
        }
      }
    }

    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = publisherText,
        style = publisherStyle,
        color =
          when {
            publisherText == missingLabel -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
            isSelected -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
          },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(
          text = yearText,
          style = metaSecondaryStyle,
          color =
            MaterialTheme.colorScheme.onSurfaceVariant.copy(
              alpha = if (yearText == missingLabel) 0.48f else 0.84f,
            ),
          maxLines = 1,
          softWrap = false,
        )
        Text(
          text = "·",
          style = metaSecondaryStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Text(
          text = pagesText,
          style = metaSecondaryStyle,
          color =
            MaterialTheme.colorScheme.onSurfaceVariant.copy(
              alpha = if (pagesText == missingLabel) 0.48f else 0.78f,
            ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
internal fun SearchWorkActionsFooterItem(
  groupIndex: Int,
  remainingEditions: Int,
  secondaryEditionsCount: Int,
  isSecondaryEditionsExpanded: Boolean,
  canLoadMoreRemoteEditions: Boolean,
  isLoadingMore: Boolean,
  showCollapseAction: Boolean,
  shouldAnimateEntrance: Boolean,
  onEntranceFinished: () -> Unit,
  onShowMoreLocalEditions: () -> Unit,
  onToggleSecondaryEditions: () -> Unit,
  onLoadMoreRemoteEditions: () -> Unit,
  onCollapseGroup: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val motion = DipeTheme.motion
  val reduceMotion = motion.reduceMotion
  val density = LocalDensity.current
  val glideOffsetPx = remember(density) { with(density) { (-5).dp.toPx() } }
  val revealProgress =
    remember(groupIndex) {
      Animatable(if (reduceMotion || !shouldAnimateEntrance) 1f else 0f)
    }

  LaunchedEffect(shouldAnimateEntrance, reduceMotion) {
    if (reduceMotion || !shouldAnimateEntrance) {
      revealProgress.snapTo(1f)
    } else if (revealProgress.value < 1f) {
      revealProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
      )
      onEntranceFinished()
    }
  }

  val footerShape =
    RoundedCornerShape(
      topStart = 0.dp,
      topEnd = 0.dp,
      bottomStart = DipeRadii.control,
      bottomEnd = DipeRadii.control,
    )
  val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
  val actionPillTextStyle =
    TextStyle(
      fontFamily = InterFontFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 12.sp,
      lineHeight = 16.sp,
      fontFeatureSettings = "tnum",
    )

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = footerShape,
    shadowElevation = 0.dp,
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .drawBehind {
            drawExpandedCardSegmentBorders(
              color = borderColor,
              strokeWidthPx = 0.75.dp.toPx(),
              roundBottom = true,
              cornerRadiusPx = DipeRadii.control.toPx(),
            )
          }
          .graphicsLayer {
            val p = if (reduceMotion) 1f else revealProgress.value
            alpha = p
            translationY = (1f - p) * glideOffsetPx
          }
          .padding(start = 12.dp, end = 6.dp, bottom = 6.dp),
    ) {
      HairlineDivider(modifier = Modifier.padding(end = 6.dp, bottom = 4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (remainingEditions > 0) {
            val showMoreDesc =
              stringResource(R.string.search_edition_show_more_local, remainingEditions)
            Surface(
              onClick = onShowMoreLocalEditions,
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
              contentColor = MaterialTheme.colorScheme.onSurface,
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .semantics { contentDescription = showMoreDesc }
                  .testTag("search_edition_show_more_$groupIndex"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Add,
                  contentDescription = null,
                  modifier = Modifier.size(15.dp),
                )
                Text(
                  text = stringResource(R.string.search_edition_more_local_short, remainingEditions),
                  style = actionPillTextStyle,
                  maxLines = 1,
                )
              }
            }
          } else if (canLoadMoreRemoteEditions) {
            val loadMoreDesc = stringResource(R.string.search_edition_load_more)
            Surface(
              onClick = onLoadMoreRemoteEditions,
              enabled = !isLoadingMore,
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
              contentColor = MaterialTheme.colorScheme.onSurface,
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .semantics { contentDescription = loadMoreDesc }
                  .testTag("search_edition_load_more_$groupIndex"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                if (isLoadingMore) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.6.dp,
                    color = MaterialTheme.colorScheme.secondary,
                  )
                } else {
                  Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                  )
                }
                Text(
                  text = stringResource(R.string.search_edition_load_more_short),
                  style = actionPillTextStyle,
                  maxLines = 1,
                )
              }
            }
          }

          if (secondaryEditionsCount > 0) {
            val secondaryDesc =
              if (isSecondaryEditionsExpanded) {
                stringResource(R.string.search_edition_hide_secondary_langs)
              } else {
                stringResource(
                  R.string.search_edition_show_secondary_langs,
                  secondaryEditionsCount,
                )
              }
            val langBg by
              animateColorAsState(
                targetValue =
                  if (isSecondaryEditionsExpanded) {
                    MaterialTheme.colorScheme.secondaryContainer
                  } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
                  },
                animationSpec = motion.standardTween(150),
                label = "secondary_lang_pill_bg",
              )
            val langTint by
              animateColorAsState(
                targetValue =
                  if (isSecondaryEditionsExpanded) {
                    MaterialTheme.colorScheme.secondary
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  },
                animationSpec = motion.standardTween(150),
                label = "secondary_lang_pill_tint",
              )
            Surface(
              onClick = onToggleSecondaryEditions,
              shape = CircleShape,
              color = langBg,
              contentColor = langTint,
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .semantics { contentDescription = secondaryDesc }
                  .testTag("search_work_secondary_editions_toggle_$groupIndex"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Language,
                  contentDescription = null,
                  modifier = Modifier.size(15.dp),
                )
                Text(
                  text = secondaryEditionsCount.toString(),
                  style = actionPillTextStyle,
                  maxLines = 1,
                )
              }
            }
          }
        }

        if (showCollapseAction) {
          IconButton(
            onClick = onCollapseGroup,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("search_work_collapse_$groupIndex"),
          ) {
            Icon(
              imageVector = Icons.Outlined.ExpandLess,
              contentDescription = stringResource(R.string.search_work_collapse_action),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(19.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun SearchContextualDrawerHeader(
  title: String,
  count: Int,
  isExpanded: Boolean,
  testTag: String,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val motion = DipeTheme.motion
  val chevronRotation by
    animateFloatAsState(
      targetValue = if (isExpanded) 180f else 0f,
      animationSpec = motion.standardTween(200),
      label = "contextual_drawer_chevron",
    )
  val titleColor by
    animateColorAsState(
      targetValue =
        if (isExpanded) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
      animationSpec = motion.standardTween(160),
      label = "contextual_drawer_title_color",
    )
  val rowBg by
    animateColorAsState(
      targetValue =
        if (isExpanded) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
        else Color.Transparent,
      animationSpec = motion.standardTween(160),
      label = "contextual_drawer_bg",
    )
  val actionLabel =
    stringResource(if (isExpanded) R.string.cd_collapse_section else R.string.cd_expand_section)

  Column(modifier = modifier.fillMaxWidth()) {
    HairlineDivider()
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(top = 4.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(rowBg)
          .minimumInteractiveComponentSize()
          .clickable(
            role = Role.Button,
            onClickLabel = actionLabel,
            onClick = onToggle,
          )
          .padding(horizontal = 6.dp, vertical = 10.dp)
          .testTag(testTag),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = title,
        style =
          TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
          ),
        color = titleColor,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )

      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
      ) {
        Text(
          text = count.toString(),
          style =
            TextStyle(
              fontFamily = InterFontFamily,
              fontWeight = FontWeight.Medium,
              fontSize = 11.5.sp,
              lineHeight = 14.sp,
              fontFeatureSettings = "tnum",
            ),
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
      }

      Icon(
        imageVector = Icons.Outlined.ExpandMore,
        contentDescription = actionLabel,
        tint =
          if (isExpanded) MaterialTheme.colorScheme.secondary
          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = chevronRotation },
      )
    }
  }
}

private enum class SourceVisualOutcome {
  SUCCESS,
  EMPTY,
  TIMEOUT,
  ERROR,
  OFFLINE,
  LOADING,
}

private fun classifySourceOutcome(
  status: SourceQueryStatus,
  isSearching: Boolean,
): SourceVisualOutcome {
  if (status.state == ProviderOutcomeState.LOADING && isSearching) {
    return SourceVisualOutcome.LOADING
  }
  return when (status.state) {
    ProviderOutcomeState.SUCCESS,
    ProviderOutcomeState.CACHED ->
      if (status.count > 0) SourceVisualOutcome.SUCCESS else SourceVisualOutcome.EMPTY
    ProviderOutcomeState.EMPTY -> SourceVisualOutcome.EMPTY
    ProviderOutcomeState.OFFLINE -> SourceVisualOutcome.OFFLINE
    ProviderOutcomeState.LOADING -> SourceVisualOutcome.EMPTY
    ProviderOutcomeState.ERROR -> {
      val msg = status.message.orEmpty()
      val isTimeout =
        msg.contains("timeout", ignoreCase = true) ||
          msg.contains("tempo", ignoreCase = true) ||
          msg.contains("deadline", ignoreCase = true)
      if (isTimeout) SourceVisualOutcome.TIMEOUT else SourceVisualOutcome.ERROR
    }
    ProviderOutcomeState.RATE_LIMITED,
    ProviderOutcomeState.SKIPPED_NO_API_KEY -> SourceVisualOutcome.ERROR
  }
}

@Composable
private fun SourceLogoMark(
  source: MetadataSource,
  size: Dp = 15.dp,
  modifier: Modifier = Modifier,
) {
  when (source) {
    MetadataSource.GOOGLE_BOOKS ->
      Image(
        painter = painterResource(R.drawable.ic_source_google_books),
        contentDescription = null,
        modifier = modifier.size(size),
      )
    MetadataSource.APPLE_BOOKS ->
      Icon(
        painter = painterResource(R.drawable.ic_source_apple_books),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
        modifier = modifier.size(size),
      )
    MetadataSource.OPEN_LIBRARY ->
      Icon(
        painter = painterResource(R.drawable.ic_source_open_library),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
        modifier = modifier.size(size),
      )
    MetadataSource.BRASIL_API ->
      Image(
        painter = painterResource(R.drawable.ic_source_brasil_api),
        contentDescription = null,
        modifier = modifier.size(size),
      )
    MetadataSource.CROSSREF ->
      Icon(
        imageVector = Icons.Outlined.AutoStories,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(size),
      )
    MetadataSource.WIKIDATA,
    MetadataSource.MANUAL ->
      Icon(
        imageVector = Icons.Outlined.Public,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(size),
      )
  }
}

@Composable
private fun SourceStatusIndicator(
  outcome: SourceVisualOutcome,
  size: Dp = 12.dp,
) {
  when (outcome) {
    SourceVisualOutcome.SUCCESS ->
      Icon(
        imageVector = Icons.Outlined.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.size(size),
      )
    SourceVisualOutcome.TIMEOUT ->
      Icon(
        imageVector = Icons.Outlined.Schedule,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
        modifier = Modifier.size(size),
      )
    SourceVisualOutcome.EMPTY ->
      Icon(
        imageVector = Icons.Outlined.Close,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
        modifier = Modifier.size(size),
      )
    SourceVisualOutcome.ERROR,
    SourceVisualOutcome.OFFLINE ->
      Icon(
        imageVector = Icons.Outlined.ErrorOutline,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.82f),
        modifier = Modifier.size(size),
      )
    SourceVisualOutcome.LOADING ->
      CircularProgressIndicator(
        modifier = Modifier.size(size - 1.dp),
        strokeWidth = 1.4.dp,
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.78f),
      )
  }
}

/**
 * Discreet, subordinate bottom strip showing consulted bibliographic sources via crisp local
 * SVG identities + separate minimal status marks, expandable for human-friendly per-source summaries.
 * Never displays technical strings, monospaced badges, or redundant "Status das fontes" headers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchBottomSourceStatusBar(
  sourceStatuses: List<SourceQueryStatus>,
  isSearching: Boolean,
  onRetrySearch: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (sourceStatuses.isEmpty()) return

  var expanded by rememberSaveable { mutableStateOf(false) }
  val chevronRotation by
    animateFloatAsState(
      targetValue = if (expanded) 180f else 0f,
      animationSpec = DipeTheme.motion.standardTween(200),
      label = "source_status_chevron_rotation",
    )

  val outcomes =
    remember(sourceStatuses, isSearching) {
      sourceStatuses.map { status -> status to classifySourceOutcome(status, isSearching) }
    }
  val hasTimeout = outcomes.any { it.second == SourceVisualOutcome.TIMEOUT }
  val hasError = outcomes.any { it.second == SourceVisualOutcome.ERROR }
  val toggleDescription =
    stringResource(if (expanded) R.string.cd_source_status_collapse else R.string.cd_source_status_expand)

  Column(
    modifier = modifier.fillMaxWidth().padding(top = DipeSpacing.xs, bottom = DipeSpacing.sm),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
  ) {
    Box(
      modifier =
        Modifier.clip(CircleShape)
          .clickable(
            onClickLabel = toggleDescription,
            role = Role.Button,
            onClick = { expanded = !expanded },
          )
          .minimumInteractiveComponentSize()
          .padding(horizontal = 12.dp, vertical = 6.dp)
          .testTag("search_sources_status_toggle"),
      contentAlignment = Alignment.Center,
    ) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        outcomes.forEach { (status, outcome) ->
          Row(
            modifier =
              Modifier
                .align(Alignment.CenterVertically)
                .testTag("search_bottom_source_${status.source.name.lowercase(Locale.US)}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            SourceLogoMark(source = status.source, size = 15.dp)
            SourceStatusIndicator(outcome = outcome, size = 12.dp)
          }
        }
        Icon(
          imageVector = Icons.Outlined.ExpandMore,
          contentDescription = toggleDescription,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
          modifier =
            Modifier
              .align(Alignment.CenterVertically)
              .size(14.dp)
              .graphicsLayer { rotationZ = chevronRotation },
        )
      }
    }

    if (!isSearching && (hasTimeout || hasError)) {
      val promptText =
        stringResource(
          if (hasTimeout) R.string.search_sources_slow_retry else R.string.search_sources_issue_retry
        )
      TextButton(
        onClick = onRetrySearch,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier =
          Modifier.minimumInteractiveComponentSize().testTag("search_sources_retry_hint"),
      ) {
        Text(
          text = promptText,
          style =
            TextStyle(
              fontFamily = InterFontFamily,
              fontWeight = FontWeight.Normal,
              fontSize = 12.sp,
              lineHeight = 16.sp,
            ),
          color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
          textAlign = TextAlign.Center,
        )
      }
    }

    AnimatedVisibility(
      visible = expanded,
      enter = fadeIn(animationSpec = DipeTheme.motion.standardTween(180)) +
        expandVertically(animationSpec = DipeTheme.motion.editorialSpring()),
      exit = fadeOut(animationSpec = DipeTheme.motion.standardTween(150)) +
        shrinkVertically(animationSpec = DipeTheme.motion.editorialSpring()),
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth().testTag("search_sources_status_details"),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          outcomes.forEach { (status, outcome) ->
            val outcomeSummary =
              when (outcome) {
                SourceVisualOutcome.SUCCESS ->
                  pluralStringResource(
                    R.plurals.search_source_detail_found,
                    if (status.count == 1) 1 else 2,
                    status.count,
                  )
                SourceVisualOutcome.EMPTY -> stringResource(R.string.search_source_detail_empty)
                SourceVisualOutcome.TIMEOUT -> stringResource(R.string.search_source_detail_timeout)
                SourceVisualOutcome.ERROR -> stringResource(R.string.search_source_detail_error)
                SourceVisualOutcome.OFFLINE -> stringResource(R.string.search_source_detail_offline)
                SourceVisualOutcome.LOADING -> stringResource(R.string.search_source_detail_loading)
              }
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              SourceLogoMark(source = status.source, size = 16.dp)
              Text(
                text = status.source.displayName,
                style =
                  TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                  ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = outcomeSummary,
                style =
                  TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                  ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
              )
              SourceStatusIndicator(outcome = outcome, size = 13.dp)
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun DuplicateResolutionDialog(
  prompt: PendingDuplicatePrompt,
  onDismiss: () -> Unit,
  onOpenExistingBook: (Long) -> Unit,
  onAddCopy: (SearchEditionCandidate, Long, Long) -> Unit,
  onAddEdition: (SearchEditionCandidate, Long) -> Unit,
  onSaveSeparately: (SearchEditionCandidate) -> Unit,
) {
  val candidate = prompt.candidate
  when (val assessment = prompt.assessment) {
    is DuplicateAssessment.ExactEditionMatch -> {
      AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.duplicate_exact_title)) },
        text = {
          Text(
            text =
              stringResource(
                R.string.duplicate_exact_body,
                assessment.existingTitle,
                assessment.matchedIsbn,
                assessment.existingCopiesCount,
              )
          )
        },
        confirmButton = {
          Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
            Button(
              onClick = {
                onAddCopy(candidate, assessment.existingEditionId, assessment.existingWorkId)
              },
              modifier = Modifier.fillMaxWidth().testTag("duplicate_add_copy_button"),
            ) {
              Text(stringResource(R.string.duplicate_add_copy_action))
            }
            OutlinedButton(
              onClick = {
                onDismiss()
                onOpenExistingBook(assessment.existingWorkId)
              },
              modifier = Modifier.fillMaxWidth().testTag("duplicate_open_existing_button"),
            ) {
              Text(stringResource(R.string.duplicate_open_existing_action))
            }
          }
        },
        dismissButton = {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.book_cancel_action)) }
        },
      )
    }

    is DuplicateAssessment.SameWorkDifferentEditionMatch -> {
      AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.duplicate_same_work_title)) },
        text = {
          Text(
            text =
              stringResource(
                R.string.duplicate_same_work_body,
                assessment.existingTitle,
                assessment.existingAuthor,
              )
          )
        },
        confirmButton = {
          Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
            Button(
              onClick = { onAddEdition(candidate, assessment.existingWorkId) },
              modifier = Modifier.fillMaxWidth().testTag("duplicate_add_edition_button"),
            ) {
              Text(stringResource(R.string.duplicate_add_edition_action))
            }
            OutlinedButton(
              onClick = {
                onDismiss()
                onOpenExistingBook(assessment.existingWorkId)
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(stringResource(R.string.duplicate_open_existing_action))
            }
            TextButton(
              onClick = { onSaveSeparately(candidate) },
              modifier = Modifier.fillMaxWidth().testTag("duplicate_save_separately_button"),
            ) {
              Text(stringResource(R.string.duplicate_save_as_new_work))
            }
          }
        },
        dismissButton = {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.book_cancel_action)) }
        },
      )
    }

    is DuplicateAssessment.UncertainWorkMatch -> {
      AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.duplicate_uncertain_title)) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
            Text(stringResource(R.string.duplicate_uncertain_body))
            assessment.candidates.take(3).forEach { similar ->
              TextButton(
                onClick = { onAddEdition(candidate, similar.work.id) },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("${similar.title} — ${similar.author}")
              }
            }
          }
        },
        confirmButton = {
          Button(
            onClick = { onSaveSeparately(candidate) },
            modifier = Modifier.testTag("duplicate_confirm_new_work_button"),
          ) {
            Text(stringResource(R.string.duplicate_save_as_new_work))
          }
        },
        dismissButton = {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.book_cancel_action)) }
        },
      )
    }

    DuplicateAssessment.NoMatch -> Unit
  }
}

@Composable
internal fun SearchTopProgressHairline(
  progressProvider: () -> Float,
  reduceMotion: Boolean,
  modifier: Modifier = Modifier,
) {
  val trackColor = MaterialTheme.colorScheme.surfaceContainerLow
  val indicatorColor = MaterialTheme.colorScheme.secondary
  Box(
    modifier =
      modifier.drawBehind {
        drawRect(color = trackColor)
        if (reduceMotion) {
          drawRect(color = indicatorColor.copy(alpha = 0.72f))
        } else {
          val t = progressProvider().coerceIn(0f, 1f)
          val barWidth = size.width * 0.34f
          val travel = size.width + barWidth
          val left = -barWidth + (travel * t)
          val clampedLeft = left.coerceIn(0f, size.width)
          val clampedRight = (left + barWidth).coerceIn(0f, size.width)
          val visibleWidth = clampedRight - clampedLeft
          if (visibleWidth > 0f) {
            drawRect(
              color = indicatorColor,
              topLeft = Offset(clampedLeft, 0f),
              size = Size(visibleWidth, size.height),
            )
          }
        }
      }
  )
}

/**
 * Calm, understated editorial loading indicator used only when no results are on screen yet.
 * Uses gentle opacity modulation without chunky dashboard skeleton cards.
 */
@Composable
internal fun SearchEditorialLoadingState(
  pulseProvider: () -> Float,
  reduceMotion: Boolean,
  modifier: Modifier = Modifier,
) {
  val loadingMessage = stringResource(R.string.search_loading_progress)
  val accentColor = MaterialTheme.colorScheme.secondary
  val placeholderLineColor = MaterialTheme.colorScheme.surfaceContainerHigh
  val spineCreaseColor = MaterialTheme.colorScheme.outlineVariant

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(top = DipeSpacing.lg, bottom = DipeSpacing.md)
        .semantics(mergeDescendants = true) {
          liveRegion = LiveRegionMode.Polite
        }
        .testTag("search_loading_indicator"),
    verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = DipeSpacing.xs),
      horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Canvas(modifier = Modifier.size(width = 16.dp, height = 13.dp)) {
        val barWidth = 2.dp.toPx()
        val gap = 2.5.dp.toPx()
        val totalBarsWidth = (barWidth * 3f) + (gap * 2f)
        val startX = (size.width - totalBarsWidth) * 0.5f
        val p = if (reduceMotion) 0.5f else pulseProvider().coerceIn(0f, 1f)
        val corner = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)
        for (i in 0 until 3) {
          val phase = ((p + i * 0.33f) % 1f)
          val wave = 1f - kotlin.math.abs(phase * 2f - 1f)
          val factor = if (reduceMotion) 0.72f else 0.42f + (0.58f * wave)
          val barHeight = size.height * factor
          val top = (size.height - barHeight) * 0.5f
          val x = startX + i * (barWidth + gap)
          drawRoundRect(
            color = accentColor.copy(alpha = if (reduceMotion) 0.8f else 0.45f + (0.55f * wave)),
            topLeft = Offset(x, top),
            size = Size(barWidth, barHeight),
            cornerRadius = corner,
          )
        }
      }
      Text(
        text = loadingMessage,
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

    // Whisper-light single editorial book placeholder (avoid heavy fake dashboard cards)
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = DipeSpacing.xs, vertical = DipeSpacing.xs),
      horizontalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier
            .width(44.dp)
            .height(64.dp)
            .drawBehind {
              val p = if (reduceMotion) 0.5f else pulseProvider().coerceIn(0f, 1f)
              val wave = 1f - kotlin.math.abs(p * 2f - 1f)
              val alpha = if (reduceMotion) 0.45f else 0.32f + (0.28f * wave)
              drawRoundRect(
                color = placeholderLineColor.copy(alpha = alpha),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
              )
              drawLine(
                color = spineCreaseColor.copy(alpha = alpha * 0.7f),
                start = Offset(5.dp.toPx(), 4.dp.toPx()),
                end = Offset(5.dp.toPx(), size.height - 4.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
              )
            }
      )

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth(0.72f)
              .height(12.dp)
              .drawBehind {
                val p = if (reduceMotion) 0.5f else pulseProvider().coerceIn(0f, 1f)
                val wave = 1f - kotlin.math.abs(p * 2f - 1f)
                val alpha = if (reduceMotion) 0.45f else 0.32f + (0.28f * wave)
                drawRoundRect(
                  color = placeholderLineColor.copy(alpha = alpha),
                  cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
              }
        )
        Box(
          modifier =
            Modifier
              .fillMaxWidth(0.46f)
              .height(10.dp)
              .drawBehind {
                val p = if (reduceMotion) 0.5f else pulseProvider().coerceIn(0f, 1f)
                val wave = 1f - kotlin.math.abs(((p + 0.18f) % 1f) * 2f - 1f)
                val alpha = if (reduceMotion) 0.38f else 0.24f + (0.24f * wave)
                drawRoundRect(
                  color = placeholderLineColor.copy(alpha = alpha),
                  cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
              }
        )
      }
    }
  }
}
