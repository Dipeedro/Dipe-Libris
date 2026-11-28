package com.example.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.core.bibliographic.domain.CoverRanker
import com.example.core.designsystem.DipeRadii
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.InterFontFamily
import com.example.core.designsystem.LocalDipeCoverTokens
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.BookCoverEdgePalette
import com.example.core.designsystem.components.EditorialSurfaceCard
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

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
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    border = null,
    shape = RoundedCornerShape(DipeRadii.surface),
    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
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
      targetValue = if (hasExpandedContentBelow) 0.dp else DipeRadii.surface,
      animationSpec = motion.standardTween(160),
      label = "work_header_bottom_radius",
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
      topStart = DipeRadii.surface,
      topEnd = DipeRadii.surface,
      bottomStart = bottomRadius,
      bottomEnd = bottomRadius,
    )
  val expandLabel =
    stringResource(if (isExpanded) R.string.cd_collapse_section else R.string.cd_expand_section)

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = headerShape,
    border = null,
    shadowElevation = 0.dp,
    modifier =
      modifier
        .fillMaxWidth()
        .padding(top = 10.dp)
        .clip(headerShape)
        .testTag("search_work_card_$groupIndex"),
  ) {
    BoxWithConstraints(
      modifier =
        Modifier.fillMaxWidth()
          .clip(headerShape)
          .clickable(onClick = onToggleExpand),
    ) {
      val coverSlotWidth =
        when {
          maxWidth < 310.dp -> 92.dp
          maxWidth < 480.dp -> 102.dp
          else -> 114.dp
        }
      val maxCoverHeight = coverSlotWidth * 1.50f

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
              Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = expandLabel,
                tint = expandChevronTint,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevronRotation },
              )
            }
          }
        }
      }
    }
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
  expansionProgressProvider: () -> Float,
  onSelectEdition: (SearchEditionCandidate) -> Unit,
  modifier: Modifier = Modifier,
) {
  val reduceMotion = DipeTheme.motion.reduceMotion
  val density = LocalDensity.current
  val glidePx = remember(density) { with(density) { (-5).dp.toPx() } }

  val itemShape =
    if (isLastItemInGroup) {
      RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = DipeRadii.surface,
        bottomEnd = DipeRadii.surface,
      )
    } else {
      RectangleShape
    }

  val rowBgColor = MaterialTheme.colorScheme.surfaceContainer
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .graphicsLayer {
          val p = if (reduceMotion) 1f else expansionProgressProvider().coerceIn(0f, 1f)
          clip = isLastItemInGroup || p < 0.999f
          shape = itemShape
        }
        .background(color = rowBgColor, shape = itemShape)
        .layout { measurable, constraints ->
          val placeable = measurable.measure(constraints)
          val p = if (reduceMotion) 1f else expansionProgressProvider().coerceIn(0f, 1f)
          val heightPx = (placeable.height * p).roundToInt().coerceAtLeast(0)
          layout(placeable.width, heightPx) {
            placeable.placeRelative(0, 0)
          }
        },
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .graphicsLayer {
            val p = if (reduceMotion) 1f else expansionProgressProvider().coerceIn(0f, 1f)
            val contentAlpha = smoothstep01(0.14f, 0.88f, p)
            alpha = contentAlpha
            translationY = (1f - p) * glidePx
          }
          .padding(
            start = 14.dp,
            end = 14.dp,
            top = if (isFirstGalleryRow) 10.dp else 6.dp,
            bottom = if (isLastItemInGroup) 14.dp else 6.dp,
          ),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.Bottom,
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

internal data class BookThicknessSpec(
  val thicknessDp: Dp,
  val normalizedThickness: Float,
)

private const val THICKNESS_MIN_DP = 11.5f
private const val THICKNESS_MAX_DP = 24.5f
private const val THICKNESS_DEFAULT_DP = 17.5f
private const val THICKNESS_MIN_PAGES = 60f
private const val THICKNESS_MAX_PAGES = 950f
private const val THICKNESS_MIN_WIDTH_RATIO = 0.135f
private const val THICKNESS_MAX_WIDTH_RATIO = 0.235f
private const val THICKNESS_BOUND_MIN_DP = 10.5f
private const val THICKNESS_BOUND_MAX_DP = 26.0f

/**
 * Centralized stylized miniature book thickness mapping from edition page count.
 * Delivers an expressive, chunky hardcover silhouette bounded cohesively (11.5..24.5 dp baseline)
 * with a mid-weight stylized fallback (17.5 dp) when page count is missing or non-positive.
 */
internal fun calculateBookThicknessSpec(totalPages: Int?): BookThicknessSpec {
  if (totalPages == null || totalPages <= 0) {
    val defaultNorm = (THICKNESS_DEFAULT_DP - THICKNESS_MIN_DP) / (THICKNESS_MAX_DP - THICKNESS_MIN_DP)
    return BookThicknessSpec(
      thicknessDp = THICKNESS_DEFAULT_DP.dp,
      normalizedThickness = defaultNorm.coerceIn(0f, 1f),
    )
  }
  val clampedPages = totalPages.toFloat().coerceIn(THICKNESS_MIN_PAGES, THICKNESS_MAX_PAGES)
  val scale = 140f
  val normalized =
    (ln(1f + (clampedPages - THICKNESS_MIN_PAGES) / scale) /
      ln(1f + (THICKNESS_MAX_PAGES - THICKNESS_MIN_PAGES) / scale))
      .coerceIn(0f, 1f)
  val thicknessDp = THICKNESS_MIN_DP + (THICKNESS_MAX_DP - THICKNESS_MIN_DP) * normalized
  return BookThicknessSpec(
    thicknessDp = thicknessDp.dp,
    normalizedThickness = normalized,
  )
}

/**
 * Computes stylized 3D book depth proportional to actual rendered cover width, while keeping
 * bounded minimum/maximum limits across compact phone grids and wider tablet layouts.
 */
internal fun calculateEffectiveBookThicknessPx(
  coverWidthPx: Float,
  fallbackThicknessPx: Float,
  normalizedThickness: Float,
  minThicknessPx: Float,
  maxThicknessPx: Float,
): Float {
  val safeMin = minThicknessPx.coerceAtLeast(1f)
  val safeMax = maxThicknessPx.coerceAtLeast(safeMin)
  if (coverWidthPx <= 0f) {
    return fallbackThicknessPx.coerceIn(safeMin, safeMax)
  }
  val norm = normalizedThickness.coerceIn(0f, 1f)
  val ratio = THICKNESS_MIN_WIDTH_RATIO + (THICKNESS_MAX_WIDTH_RATIO - THICKNESS_MIN_WIDTH_RATIO) * norm
  return (coverWidthPx * ratio).coerceIn(safeMin, safeMax)
}

internal data class Book3dProjection(
  val angleDeg: Float,
  val isShowingBack: Boolean,
  val edgeTurn: Float,
  val absCos: Float,
  val combinedScale: Float,
  val liftY: Float,
  val frontRotationY: Float,
  val frontTranslationX: Float,
  val frontAlpha: Float,
  val frontShadowFactor: Float,
  val backRotationY: Float,
  val backTranslationX: Float,
  val backAlpha: Float,
  val backShadowFactor: Float,
  val isSlabVisible: Boolean,
  val slabFrontX: Float,
  val slabBackX: Float,
  val slabWidthPx: Float,
  val slabTopY: Float,
  val slabBottomY: Float,
  val projectedEdgeTopY: Float,
  val projectedEdgeBottomY: Float,
  val boardOuterTopY: Float,
  val boardOuterBotY: Float,
  val slabCornerRadiusPx: Float,
  val foreEdgeScale: Float,
  val spineEdgeScale: Float,
  val frontBoardWidthPx: Float,
  val backBoardWidthPx: Float,
  val bookLeftX: Float,
  val bookRightX: Float,
  val shadowCenterX: Float,
  val shadowSpanPx: Float,
  val shellCornerRxEffective: Float,
  val shellCornerRy: Float,
  val scaledOverhangPx: Float,
  val frontProjectedWidthPx: Float,
  val backProjectedWidthPx: Float,
)

/**
 * Computes a single unified rigid-body 3D book projection shared identically across the Front
 * cover, the Swept Rounded Cover Shell + Recessed Paper-Block Canvas, and the Back cover ("Verso").
 *
 * Coordinate & rotation convention matches Android RenderNode / Compose Matrix.rotateY:
 * - Negative rotationY swings the right fore-edge (+X) toward the camera (-Z, larger scale).
 * - Front cover turns 0° -> -180° around Y; Verso turns +180° -> 0° around Y.
 * - Fore-edge volume spans [slabFrontX, slabBackX] continuously across 0°..180° so
 *   Front right edge == slabFrontX (0°..90°) and Verso left edge == slabBackX (90°..180°).
 */
internal fun calculateBook3dProjection(
  widthPx: Float,
  heightPx: Float,
  thicknessPx: Float,
  flipProgress: Float,
  pressProgress: Float,
  cameraDistPx: Float,
  liftOffsetMaxPx: Float,
  foreCornerRadiusPx: Float,
  boardOverhangPx: Float,
  reduceMotion: Boolean,
  frontBoardTargetPx: Float = thicknessPx * 0.21f,
  backBoardTargetPx: Float = thicknessPx * 0.17f,
): Book3dProjection {
  val f = flipProgress.coerceIn(0f, 1f)
  val hw = (widthPx * 0.5f).coerceAtLeast(0f)
  val hh = (heightPx * 0.5f).coerceAtLeast(0f)
  val cx = hw
  val cy = hh

  if (reduceMotion) {
    val isBack = f >= 0.5f
    return Book3dProjection(
      angleDeg = 0f,
      isShowingBack = isBack,
      edgeTurn = 0f,
      absCos = 1f,
      combinedScale = 1f,
      liftY = 0f,
      frontRotationY = 0f,
      frontTranslationX = 0f,
      frontAlpha = 1f - f,
      frontShadowFactor = if (isBack) 0f else 1f,
      backRotationY = 0f,
      backTranslationX = 0f,
      backAlpha = f,
      backShadowFactor = if (isBack) 1f else 0f,
      isSlabVisible = false,
      slabFrontX = widthPx,
      slabBackX = widthPx,
      slabWidthPx = 0f,
      slabTopY = 0f,
      slabBottomY = heightPx,
      projectedEdgeTopY = 0f,
      projectedEdgeBottomY = heightPx,
      boardOuterTopY = 0f,
      boardOuterBotY = heightPx,
      slabCornerRadiusPx = 0f,
      foreEdgeScale = 1f,
      spineEdgeScale = 1f,
      frontBoardWidthPx = 0f,
      backBoardWidthPx = 0f,
      bookLeftX = 0f,
      bookRightX = widthPx,
      shadowCenterX = cx,
      shadowSpanPx = widthPx,
      shellCornerRxEffective = foreCornerRadiusPx,
      shellCornerRy = foreCornerRadiusPx,
      scaledOverhangPx = 0f,
      frontProjectedWidthPx = widthPx,
      backProjectedWidthPx = widthPx,
    )
  }

  val angleDeg = f * 180f
  val isBack = f > 0.5f
  val rad = angleDeg * (PI / 180.0)
  val cosA = cos(rad).toFloat()
  val sinA = sin(rad).toFloat().coerceIn(0f, 1f)
  val absCos = abs(cosA)
  val edgeTurn = sinA

  val pressScale = 1f - (0.024f * pressProgress.coerceIn(0f, 1f))
  val turnLiftScale = 1f + (0.015f * edgeTurn) + (0.006f * smoothstep01(0.05f, 0.90f, f))
  val combinedScale = pressScale * turnLiftScale
  val liftY = -liftOffsetMaxPx * edgeTurn

  val halfT = (thicknessPx * 0.5f).coerceAtLeast(0f)
  val frontTranslationX = -halfT * sinA * combinedScale
  val backTranslationX = +halfT * sinA * combinedScale

  val shadowVisibility = smoothstep01(0.08f, 0.36f, absCos)

  val camZ = cameraDistPx.coerceAtLeast(widthPx * 1.5f).coerceAtLeast(1f)
  val foreEdgeZ = -hw * combinedScale * sinA
  val foreEdgeScale = camZ / (camZ + foreEdgeZ).coerceAtLeast(camZ * 0.35f)
  val spineZ = +hw * combinedScale * sinA
  val spineEdgeScale = camZ / (camZ + spineZ).coerceAtLeast(camZ * 0.35f)

  val seamCenterX = cx + (hw * combinedScale * cosA) * foreEdgeScale
  val slabFrontX = seamCenterX + frontTranslationX
  val slabBackX = seamCenterX + backTranslationX
  val slabWidthPx = (slabBackX - slabFrontX).coerceAtLeast(0f)

  val frontSpineX = cx - (hw * combinedScale * cosA) * spineEdgeScale + frontTranslationX
  val backSpineX = cx - (hw * combinedScale * cosA) * spineEdgeScale + backTranslationX
  val frontProjectedWidthPx = if (!isBack) (slabFrontX - frontSpineX).coerceAtLeast(0f) else 0f
  val backProjectedWidthPx = if (isBack) (backSpineX - slabBackX).coerceAtLeast(0f) else 0f

  // Continuous rigid-body horizontal footprint across 0°..180° for unified contact shadow
  val (bookLeftX, bookRightX) =
    if (!isBack) {
      minOf(frontSpineX, slabFrontX) to maxOf(slabBackX, frontSpineX)
    } else {
      minOf(slabFrontX, backSpineX) to maxOf(backSpineX, slabBackX)
    }
  val shadowSpanPx = (bookRightX - bookLeftX).coerceAtLeast(0f)
  val shadowCenterX = (bookLeftX + bookRightX) * 0.5f

  val edgeHalfHeight = hh * combinedScale * foreEdgeScale
  val edgeTopY = cy + liftY - edgeHalfHeight
  val edgeBotY = cy + liftY + edgeHalfHeight

  val scaledCornerR = foreCornerRadiusPx * combinedScale * foreEdgeScale
  val scaledOverhang = (boardOverhangPx * combinedScale * foreEdgeScale).coerceIn(0f, edgeHalfHeight * 0.20f)

  // Swept rounded-corner profile: matches Front/Back 6dp rounded corners at oblique angles and eases into soft hardcover corners near 90°
  val near90Blend = smoothstep01(0.0f, 0.38f, absCos)
  val capsuleCornerR = minOf(scaledCornerR * 0.44f, (slabWidthPx * 0.24f).coerceAtLeast(0f))
  val shellCornerRy = (capsuleCornerR + (scaledCornerR - capsuleCornerR) * near90Blend).coerceIn(0f, edgeHalfHeight * 0.25f)
  val shellCornerRxEffective = maxOf(scaledCornerR * absCos, capsuleCornerR * (1f - near90Blend))

  // Pages sit consistently recessed inside the overhanging top/bottom cover shell (zero vertical pop near 90°)
  val slabTopY = edgeTopY + scaledOverhang
  val slabBotY = edgeBotY - scaledOverhang
  val slabCornerRadiusPx =
    minOf(
      (shellCornerRy - scaledOverhang).coerceAtLeast(0.8f),
      (slabWidthPx * 0.30f).coerceAtLeast(0f),
      ((slabBotY - slabTopY) * 0.08f).coerceAtLeast(0f),
    ).coerceAtLeast(0f)

  val isSlabVisible =
    widthPx > 0f &&
      heightPx > 0f &&
      f > 0.005f &&
      f < 0.995f &&
      slabWidthPx > 0.35f &&
      (slabBotY - slabTopY) > 1f

  // Reserve prominent outer front shell, cohesive rear shell, and >=52% visible warm paper block
  val frontBoardWidthPx =
    if (!isSlabVisible) 0f
    else minOf(frontBoardTargetPx.coerceAtLeast(0f) * combinedScale, slabWidthPx * 0.27f)
  val backBoardWidthPx =
    if (!isSlabVisible) 0f
    else minOf(backBoardTargetPx.coerceAtLeast(0f) * combinedScale, slabWidthPx * 0.21f)

  return Book3dProjection(
    angleDeg = angleDeg,
    isShowingBack = isBack,
    edgeTurn = edgeTurn,
    absCos = absCos,
    combinedScale = combinedScale,
    liftY = liftY,
    frontRotationY = -angleDeg,
    frontTranslationX = frontTranslationX,
    frontAlpha = if (isBack) 0f else 1f,
    frontShadowFactor = if (isBack) 0f else shadowVisibility,
    backRotationY = 180f - angleDeg,
    backTranslationX = backTranslationX,
    backAlpha = if (isBack) 1f else 0f,
    backShadowFactor = if (isBack) shadowVisibility else 0f,
    isSlabVisible = isSlabVisible,
    slabFrontX = slabFrontX,
    slabBackX = slabBackX,
    slabWidthPx = slabWidthPx,
    slabTopY = slabTopY,
    slabBottomY = slabBotY,
    projectedEdgeTopY = edgeTopY,
    projectedEdgeBottomY = edgeBotY,
    boardOuterTopY = edgeTopY,
    boardOuterBotY = edgeBotY,
    slabCornerRadiusPx = slabCornerRadiusPx,
    foreEdgeScale = foreEdgeScale,
    spineEdgeScale = spineEdgeScale,
    frontBoardWidthPx = frontBoardWidthPx,
    backBoardWidthPx = backBoardWidthPx,
    bookLeftX = bookLeftX,
    bookRightX = bookRightX,
    shadowCenterX = shadowCenterX,
    shadowSpanPx = shadowSpanPx,
    shellCornerRxEffective = shellCornerRxEffective,
    shellCornerRy = shellCornerRy,
    scaledOverhangPx = scaledOverhang,
    frontProjectedWidthPx = frontProjectedWidthPx,
    backProjectedWidthPx = backProjectedWidthPx,
  )
}

private fun smoothstep01(edge0: Float, edge1: Float, x: Float): Float {
  if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
  val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

private val EditionFrontShape =
  RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 6.dp, bottomEnd = 6.dp)
private val EditionBackShape =
  RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 2.dp, bottomEnd = 2.dp)
private val EditionCoverShadowColor = Color(0xFF211A14)
private val EditionFlipRevealEasing = CubicBezierEasing(0.24f, 0.0f, 0.16f, 1.0f)
private val EditionFlipReturnEasing = CubicBezierEasing(0.20f, 0.0f, 0.14f, 1.0f)
private const val HOLD_TO_FLIP_MS = 190L
private const val FLIP_REVEAL_DURATION_MS = 420
private const val FLIP_RETURN_MAX_DURATION_MS = 250

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
  val density = LocalDensity.current
  val coverTokens = LocalDipeCoverTokens.current
  val hapticFeedback = LocalHapticFeedback.current
  val viewConfiguration = LocalViewConfiguration.current
  val windowInfo = LocalWindowInfo.current
  val interactionSource = remember(edition.candidateId) { MutableInteractionSource() }
  val currentOnClick by rememberUpdatedState(onClick)

  var isPressed by remember(edition.candidateId) { mutableStateOf(false) }
  var isHoldingFlip by remember(edition.candidateId) { mutableStateOf(false) }
  var accessibilityInspected by remember(edition.candidateId) { mutableStateOf(false) }
  var hasFiredHapticForCurrentFlip by remember(edition.candidateId) { mutableStateOf(false) }
  var measuredCoverSize by remember(edition.candidateId) { mutableStateOf(IntSize.Zero) }
  val flipProgress = remember(edition.candidateId) { Animatable(0f) }

  DisposableEffect(edition.candidateId) {
    onDispose {
      isPressed = false
      isHoldingFlip = false
      accessibilityInspected = false
      hasFiredHapticForCurrentFlip = false
    }
  }

  LaunchedEffect(windowInfo.isWindowFocused) {
    if (!windowInfo.isWindowFocused) {
      isPressed = false
      isHoldingFlip = false
    }
  }

  val targetFlipped = isHoldingFlip || accessibilityInspected
  LaunchedEffect(targetFlipped, reduceMotion, edition.candidateId) {
    if (targetFlipped) {
      flipProgress.animateTo(
        targetValue = 1f,
        animationSpec =
          tween(
            durationMillis = if (reduceMotion) 140 else FLIP_REVEAL_DURATION_MS,
            easing = EditionFlipRevealEasing,
          ),
      )
      if (isHoldingFlip && !hasFiredHapticForCurrentFlip && flipProgress.value >= 0.96f) {
        hasFiredHapticForCurrentFlip = true
        runCatching { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) }
      }
    } else {
      hasFiredHapticForCurrentFlip = false
      if (flipProgress.value > 0f) {
        val returnMs =
          if (reduceMotion) {
            120
          } else {
            (FLIP_RETURN_MAX_DURATION_MS * flipProgress.value)
              .roundToInt()
              .coerceIn(115, FLIP_RETURN_MAX_DURATION_MS)
          }
        flipProgress.animateTo(
          targetValue = 0f,
          animationSpec = tween(durationMillis = returnMs, easing = EditionFlipReturnEasing),
        )
      }
    }
  }

  val compactEditionCovers =
    remember(edition.candidateId, edition.orderedCoverUrls) {
      CoverRanker.selectForDisplaySize(edition.orderedCoverUrls, maxWidthDp = 120)
    }
  val thicknessSpec =
    remember(edition.totalPages) { calculateBookThicknessSpec(edition.totalPages) }
  val minThicknessPx = remember(density) { with(density) { THICKNESS_BOUND_MIN_DP.dp.toPx() } }
  val maxThicknessPx = remember(density) { with(density) { THICKNESS_BOUND_MAX_DP.dp.toPx() } }
  val fallbackThicknessPx = remember(thicknessSpec, density) { with(density) { thicknessSpec.thicknessDp.toPx() } }
  val effectiveThicknessPx =
    remember(measuredCoverSize.width, fallbackThicknessPx, thicknessSpec.normalizedThickness, minThicknessPx, maxThicknessPx) {
      calculateEffectiveBookThicknessPx(
        coverWidthPx = measuredCoverSize.width.toFloat(),
        fallbackThicknessPx = fallbackThicknessPx,
        normalizedThickness = thicknessSpec.normalizedThickness,
        minThicknessPx = minThicknessPx,
        maxThicknessPx = maxThicknessPx,
      )
    }
  val coverPalette =
    remember(edition.workTitle, edition.primaryAuthorsText, coverTokens) {
      coverTokens.paletteFor(title = edition.workTitle, author = edition.primaryAuthorsText)
    }

  val publisherText =
    remember(edition.publisher) { edition.publisher?.trim()?.takeIf { it.isNotEmpty() } }
  val yearValue =
    remember(edition.publicationYear) { edition.publicationYear?.takeIf { it in 1400..2100 } }
  val pagesValue = remember(edition.totalPages) { edition.totalPages?.takeIf { it > 0 } }
  val pagesText = pagesValue?.let { stringResource(R.string.search_edition_pages_value, it) }

  val selectLabel = stringResource(R.string.search_edition_select_action)
  val selectedCd = stringResource(R.string.cd_edition_selected)
  val inspectDetailsLabel = stringResource(R.string.cd_inspect_edition_details)
  val hideDetailsLabel = stringResource(R.string.cd_hide_edition_details)
  val coverCd = stringResource(R.string.cd_book_cover, edition.workTitle)
  val versoSummaryParts =
    remember(publisherText, yearValue, pagesText) {
      listOfNotNull(publisherText, yearValue?.toString(), pagesText)
    }
  val versoCd =
    if (versoSummaryParts.isNotEmpty()) {
      stringResource(R.string.cd_edition_verso_details, versoSummaryParts.joinToString(", "))
    } else {
      stringResource(R.string.cd_edition_verso_no_details)
    }

  // Only recompose on crossing threshold or interaction start/stop; per-frame motion runs in graphicsLayer/Canvas
  val isShowingBack by remember(reduceMotion, edition.candidateId) {
    derivedStateOf {
      val f = flipProgress.value.coerceIn(0f, 1f)
      if (reduceMotion) f >= 0.5f else f > 0.5f
    }
  }
  val isActivelyTurning by remember(edition.candidateId) {
    derivedStateOf { flipProgress.value > 0.001f }
  }
  val shouldMountBackFace =
    isShowingBack || (!reduceMotion && (isPressed || isHoldingFlip || isActivelyTurning || accessibilityInspected))

  val pressProgress by
    animateFloatAsState(
      targetValue = if (!reduceMotion && isPressed && !isHoldingFlip) 1f else 0f,
      animationSpec = tween(durationMillis = 75, easing = FastOutSlowInEasing),
      label = "edition_tile_press_progress",
    )

  val cameraDist = remember(density) { 12f * density.density }
  val cameraDistPx = remember(cameraDist) { cameraDist * 72f }
  val selectedBorderColor by
    animateColorAsState(
      targetValue =
        if (isSelected) MaterialTheme.colorScheme.secondary
        else Color.Transparent,
      animationSpec = motion.standardTween(150),
      label = "edition_selected_ring",
    )
  val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.25f
  val versoBackground =
    if (isDarkTheme) MaterialTheme.colorScheme.surfaceContainerHigh
    else MaterialTheme.colorScheme.surfaceContainerLowest
  val pageEdgeToneLight =
    if (isDarkTheme) Color(0xFFDED6C6)
    else Color(0xFFF6F1E6)
  val pageEdgeToneMid =
    if (isDarkTheme) Color(0xFFCBC1B0)
    else Color(0xFFEBE2D1)
  val pageEdgeToneShadow =
    if (isDarkTheme) Color(0xFFAEA391)
    else Color(0xFFD6CBB7)

  var coverEdgePalette by remember(edition.candidateId) { mutableStateOf<BookCoverEdgePalette?>(null) }
  val resolvedEdgePalette =
    remember(coverEdgePalette, coverPalette, isDarkTheme) {
      val base =
        coverEdgePalette
          ?: BookCoverEdgePalette(
            top = lerp(coverPalette.clothColor, Color.White, 0.08f),
            mid = coverPalette.clothColor,
            bottom = lerp(coverPalette.clothColor, Color.Black, 0.10f),
          )
      if (isDarkTheme) {
        fun liftIfTooDark(c: Color): Color =
          if (c.luminance() < 0.08f) lerp(c, Color(0xFF564E44), 0.36f) else c
        BookCoverEdgePalette(
          top = liftIfTooDark(base.top),
          mid = liftIfTooDark(base.mid),
          bottom = liftIfTooDark(base.bottom),
        )
      } else {
        base
      }
    }
  val frontBoardBrush =
    remember(resolvedEdgePalette) {
      Brush.verticalGradient(
        0.0f to lerp(resolvedEdgePalette.top, Color.White, 0.09f),
        0.48f to resolvedEdgePalette.mid,
        1.0f to lerp(resolvedEdgePalette.bottom, Color.Black, 0.11f),
      )
    }
  val backBoardTone =
    remember(versoBackground, resolvedEdgePalette, isDarkTheme) {
      if (isDarkTheme) lerp(versoBackground, resolvedEdgePalette.mid, 0.22f)
      else lerp(versoBackground, resolvedEdgePalette.mid, 0.18f)
    }
  val versoFrameColor =
    remember(versoBackground, resolvedEdgePalette, isDarkTheme) {
      if (isDarkTheme) lerp(Color(0xFF4F483F), resolvedEdgePalette.mid, 0.20f).copy(alpha = 0.78f)
      else lerp(versoBackground, resolvedEdgePalette.mid, 0.24f).copy(alpha = 0.74f)
    }

  val liftOffsetMaxPx = remember(density) { with(density) { 1.6.dp.toPx() } }
  val foreCornerRadiusPx = remember(density) { with(density) { 6.dp.toPx() } }
  val boardOverhangPx = remember(density) { with(density) { 2.2.dp.toPx() } }
  val frontBoardTargetPx =
    remember(density, thicknessSpec) {
      with(density) { (2.4f + 1.2f * thicknessSpec.normalizedThickness).dp.toPx() }
    }
  val backBoardTargetPx =
    remember(density, thicknessSpec) {
      with(density) { (2.0f + 1.0f * thicknessSpec.normalizedThickness).dp.toPx() }
    }
  val shellLipHighlightStroke = remember(density) { Stroke(width = with(density) { 0.55.dp.toPx() }) }
  val frontShellPath = remember { Path() }
  val backShellPath = remember { Path() }
  val slabPath = remember { Path() }
  val spineCreaseBrush =
    remember(isDarkTheme) {
      Brush.horizontalGradient(
        0.91f to Color.Transparent,
        0.955f to Color.White.copy(alpha = if (isDarkTheme) 0.04f else 0.12f),
        1.0f to Color.Black.copy(alpha = if (isDarkTheme) 0.16f else 0.055f),
      )
    }

  val projectionProvider: () -> Book3dProjection = {
    calculateBook3dProjection(
      widthPx = measuredCoverSize.width.toFloat(),
      heightPx = measuredCoverSize.height.toFloat(),
      thicknessPx = effectiveThicknessPx,
      flipProgress = flipProgress.value,
      pressProgress = pressProgress,
      cameraDistPx = cameraDistPx,
      liftOffsetMaxPx = liftOffsetMaxPx,
      foreCornerRadiusPx = foreCornerRadiusPx,
      boardOverhangPx = boardOverhangPx,
      reduceMotion = reduceMotion,
      frontBoardTargetPx = frontBoardTargetPx,
      backBoardTargetPx = backBoardTargetPx,
    )
  }

  val isElevatedTile = isPressed || isHoldingFlip || isActivelyTurning || accessibilityInspected

  Box(
    modifier =
      modifier
        .zIndex(if (isElevatedTile) 1f else 0f)
        .fillMaxWidth()
        .aspectRatio(2f / 3f)
        .minimumInteractiveComponentSize()
        .semantics {
          role = Role.Button
          contentDescription = if (isShowingBack) versoCd else coverCd
          onClick(label = selectLabel) {
            if (accessibilityInspected) {
              accessibilityInspected = false
            } else {
              currentOnClick()
            }
            true
          }
          customActions =
            listOf(
              CustomAccessibilityAction(
                label = if (accessibilityInspected) hideDetailsLabel else inspectDetailsLabel,
              ) {
                accessibilityInspected = !accessibilityInspected
                true
              }
            )
        }
        .pointerInput(edition.candidateId) {
          val touchSlop = viewConfiguration.touchSlop
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val downPos = down.position
            val pressInteraction = PressInteraction.Press(downPos)
            interactionSource.tryEmit(pressInteraction)
            isPressed = true

            var phase1Cancelled = false
            var releasedBeforeHold = false
            withTimeoutOrNull(HOLD_TO_FLIP_MS) {
              while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change == null || change.isConsumed) {
                  phase1Cancelled = true
                  break
                }
                if (!change.pressed) {
                  releasedBeforeHold = true
                  break
                }
                if ((change.position - downPos).getDistance() > touchSlop) {
                  phase1Cancelled = true
                  break
                }
              }
            }

            if (releasedBeforeHold) {
              isPressed = false
              interactionSource.tryEmit(PressInteraction.Release(pressInteraction))
              if (accessibilityInspected) {
                accessibilityInspected = false
              } else {
                currentOnClick()
              }
              return@awaitEachGesture
            }

            if (phase1Cancelled) {
              isPressed = false
              interactionSource.tryEmit(PressInteraction.Cancel(pressInteraction))
              return@awaitEachGesture
            }

            interactionSource.tryEmit(PressInteraction.Cancel(pressInteraction))
            accessibilityInspected = false
            isHoldingFlip = true
            try {
              while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change == null || !change.pressed || change.isConsumed) {
                  break
                }
                if ((change.position - downPos).getDistance() > touchSlop * 2f) {
                  break
                }
              }
            } finally {
              isHoldingFlip = false
              isPressed = false
            }
          }
        }
        .testTag(testTag),
    contentAlignment = Alignment.BottomCenter,
  ) {
    // Single Unified Shadow + Swept 3D Cover Shell & Recessed Pages Volume (zero stacked RenderNode 3D shadows)
    if (measuredCoverSize.width > 0 && measuredCoverSize.height > 0) {
      val slabWidthDp = with(density) { measuredCoverSize.width.toDp() }
      val slabHeightDp = with(density) { measuredCoverSize.height.toDp() }
      Canvas(modifier = Modifier.width(slabWidthDp).height(slabHeightDp)) {
        val p = projectionProvider()

        // 1. Single unified soft shadow: smooth handoff between flat rest card shadow and turning ground contact oval
        val turnWeight = if (reduceMotion) 0f else smoothstep01(0.01f, 0.28f, p.edgeTurn)
        val restWeight = 1f - turnWeight
        if (restWeight > 0.01f && size.width > 2f) {
          val restExpand = (if (isSelected) 1.5f else 1.0f).dp.toPx()
          val restOffsetY = 1.6.dp.toPx()
          val restCorner = CornerRadius(6.dp.toPx())
          val restAlpha = ((if (isDarkTheme) 0.14f else 0.065f) * restWeight)
          drawRoundRect(
            color = EditionCoverShadowColor.copy(alpha = restAlpha * 0.55f),
            topLeft = Offset(-restExpand, restOffsetY - restExpand * 0.4f),
            size = Size(size.width + restExpand * 2f, size.height + restExpand),
            cornerRadius = restCorner,
          )
          drawRoundRect(
            color = EditionCoverShadowColor.copy(alpha = restAlpha),
            topLeft = Offset(-restExpand * 0.35f, restOffsetY * 0.65f),
            size = Size(size.width + restExpand * 0.7f, size.height + restExpand * 0.35f),
            cornerRadius = restCorner,
          )
        }

        if (!reduceMotion && p.edgeTurn > 0.01f && size.width > 2f) {
          // Ground contact shadow stays comfortably within the 6dp row padding so sibling rows never clip it
          val shadowWidth = (p.shadowSpanPx + 4.dp.toPx() * p.edgeTurn).coerceAtLeast(14.dp.toPx())
          val outerH = 4.6.dp.toPx()
          val innerW = (shadowWidth * 0.72f).coerceAtLeast(8.dp.toPx())
          val innerH = 2.5.dp.toPx()
          val baseShadowY = size.height + 2.1.dp.toPx()
          val haloAlpha = (if (isDarkTheme) 0.14f else 0.065f) * p.edgeTurn
          val coreAlpha = (if (isDarkTheme) 0.17f else 0.088f) * p.edgeTurn

          drawOval(
            color = EditionCoverShadowColor.copy(alpha = haloAlpha),
            topLeft = Offset(p.shadowCenterX - shadowWidth * 0.5f, baseShadowY - outerH * 0.5f),
            size = Size(shadowWidth, outerH),
          )
          drawOval(
            color = EditionCoverShadowColor.copy(alpha = coreAlpha),
            topLeft = Offset(p.shadowCenterX - innerW * 0.5f, baseShadowY - innerH * 0.5f),
            size = Size(innerW, innerH),
          )
        }

        if (!reduceMotion && p.isSlabVisible) {
          val topOuterY = p.projectedEdgeTopY
          val botOuterY = p.projectedEdgeBottomY
          val topPageY = p.slabTopY
          val botPageY = p.slabBottomY
          val pageH = (botPageY - topPageY).coerceAtLeast(0f)
          val frontBoardW = p.frontBoardWidthPx
          val backBoardW = p.backBoardWidthPx
          val paperStartX = p.slabFrontX + frontBoardW
          val paperEndX = (p.slabBackX - backBoardW).coerceAtLeast(paperStartX)
          val paperSpan = (paperEndX - paperStartX).coerceAtLeast(0f)
          val shellRadius = CornerRadius(p.shellCornerRxEffective, p.shellCornerRy)
          val pageCornerRx = minOf(p.shellCornerRxEffective * 0.68f, (paperSpan * 0.42f).coerceAtLeast(0.8f)).coerceAtLeast(0.6f)
          val pageCornerRy = p.slabCornerRadiusPx.coerceAtLeast(0.8f)
          val pageRadius = CornerRadius(pageCornerRx, pageCornerRy)
          val paperBase = lerp(pageEdgeToneLight, pageEdgeToneMid, 0.36f)

          fun drawPagesBlock(roundLeftSide: Boolean, roundRightSide: Boolean) {
            if (paperSpan <= 0.25f || pageH <= 0.5f) return
            slabPath.rewind()
            slabPath.addRoundRect(
              RoundRect(
                left = paperStartX - 1.2f,
                top = topPageY,
                right = paperEndX + 1.2f,
                bottom = botPageY,
                topLeftCornerRadius = if (roundLeftSide) pageRadius else CornerRadius.Zero,
                bottomLeftCornerRadius = if (roundLeftSide) pageRadius else CornerRadius.Zero,
                topRightCornerRadius = if (roundRightSide) pageRadius else CornerRadius.Zero,
                bottomRightCornerRadius = if (roundRightSide) pageRadius else CornerRadius.Zero,
              )
            )
            drawPath(path = slabPath, color = paperBase)

            val safeTop = topPageY + pageCornerRy
            val safeH = (pageH - pageCornerRy * 2f).coerceAtLeast(0f)
            if (paperSpan > 1.0f && safeH > 0.5f) {
              val highlightW = (paperSpan * 0.38f).coerceAtLeast(0.6f)
              val shadowW = (paperSpan * 0.28f).coerceAtLeast(0.6f)
              drawRect(
                color = pageEdgeToneLight.copy(alpha = 0.70f * p.edgeTurn),
                topLeft = Offset(paperStartX, safeTop),
                size = Size(highlightW, safeH),
              )
              drawRect(
                color = pageEdgeToneShadow.copy(alpha = 0.45f * p.edgeTurn),
                topLeft = Offset((paperEndX - shadowW).coerceAtLeast(paperStartX), safeTop),
                size = Size(shadowW, safeH),
              )
            }

            // Subtle stacked-page texture hairlines (restrained, crisp, never noisy)
            val grainMinPx = 2.4.dp.toPx()
            if (paperSpan > grainMinPx) {
              val grainAlpha =
                (0.088f * p.edgeTurn * smoothstep01(grainMinPx, grainMinPx * 2.0f, paperSpan))
                  .coerceIn(0f, 0.095f)
              if (grainAlpha > 0.01f && safeH > 2.dp.toPx()) {
                val lineColor = pageEdgeToneShadow.copy(alpha = grainAlpha)
                val lineStroke = 0.35.dp.toPx()
                val lineTop = safeTop + 1.dp.toPx()
                val lineBot = (botPageY - pageCornerRy) - 1.dp.toPx()
                if (lineBot > lineTop) {
                  val count = if (paperSpan > 6.dp.toPx()) 4 else if (paperSpan > 3.5.dp.toPx()) 3 else 2
                  for (i in 1..count) {
                    val t = i.toFloat() / (count + 1).toFloat()
                    val lx = paperStartX + paperSpan * t
                    drawLine(
                      color = lineColor,
                      start = Offset(lx, lineTop),
                      end = Offset(lx, lineBot),
                      strokeWidth = lineStroke,
                    )
                  }
                }
              }
            }

            // Overhang shadow nestled right under the protruding top & bottom hardcover shells
            val overhangShadowH = 1.15.dp.toPx().coerceAtMost(pageH * 0.08f)
            if (overhangShadowH > 0.3f) {
              val overhangShade = Color.Black.copy(alpha = (if (isDarkTheme) 0.22f else 0.11f) * p.edgeTurn)
              val insetX = pageCornerRx * 0.65f
              val spanInner = (paperSpan - insetX).coerceAtLeast(0f)
              val startInner = if (roundLeftSide) paperStartX + insetX else paperStartX
              if (spanInner > 0.5f) {
                drawRect(
                  color = overhangShade,
                  topLeft = Offset(startInner, topPageY),
                  size = Size(spanInner, overhangShadowH),
                )
                drawRect(
                  color = overhangShade,
                  topLeft = Offset(startInner, botPageY - overhangShadowH),
                  size = Size(spanInner, overhangShadowH),
                )
              }
            }
          }

          if (!p.isShowingBack) {
            // 2A. Far-side Back Cover Shell wrapping cleanly behind pages with matching outer corners
            if (backBoardW > 0.25f) {
              val backAnchorLeft = maxOf(p.bookLeftX + 0.5f, p.slabBackX - p.shellCornerRxEffective - backBoardW - 2f)
              backShellPath.rewind()
              backShellPath.addRoundRect(
                RoundRect(
                  left = backAnchorLeft,
                  top = topOuterY,
                  right = p.slabBackX,
                  bottom = botOuterY,
                  topRightCornerRadius = shellRadius,
                  bottomRightCornerRadius = shellRadius,
                )
              )
              drawPath(path = backShellPath, color = backBoardTone)
            }

            // 2B. Warm paper block recessed inside overhanging cover shells
            drawPagesBlock(roundLeftSide = false, roundRightSide = true)

            // 2C. Inner crease where pages tuck behind back cover shell
            if (backBoardW > 0.25f && pageH > 2f) {
              drawLine(
                color = Color.Black.copy(alpha = (if (isDarkTheme) 0.24f else 0.11f) * p.edgeTurn),
                start = Offset(paperEndX, topPageY + pageCornerRy * 0.5f),
                end = Offset(paperEndX, botPageY - pageCornerRy * 0.5f),
                strokeWidth = 0.5.dp.toPx(),
              )
            }

            // 2D. Prominent Colored Front Cover Shell sweeping seamlessly behind Front's right rounded edge
            if (frontBoardW > 0.25f) {
              val tuckPx = minOf(p.shellCornerRxEffective + 1.5f, (p.frontProjectedWidthPx * 0.78f).coerceAtLeast(0f))
              frontShellPath.rewind()
              frontShellPath.addRoundRect(
                RoundRect(
                  left = p.slabFrontX - tuckPx,
                  top = topOuterY,
                  right = p.slabFrontX + frontBoardW,
                  bottom = botOuterY,
                  topRightCornerRadius = shellRadius,
                  bottomRightCornerRadius = shellRadius,
                )
              )
              drawPath(path = frontShellPath, brush = frontBoardBrush)
              drawPath(
                path = frontShellPath,
                color = Color.White.copy(alpha = (if (isDarkTheme) 0.12f else 0.18f) * p.edgeTurn),
                style = shellLipHighlightStroke,
              )
              if (pageH > 2f) {
                drawLine(
                  color = Color.Black.copy(alpha = (if (isDarkTheme) 0.28f else 0.15f) * p.edgeTurn),
                  start = Offset(paperStartX, topOuterY + p.shellCornerRy * 0.35f),
                  end = Offset(paperStartX, botOuterY - p.shellCornerRy * 0.35f),
                  strokeWidth = 0.55.dp.toPx(),
                )
              }
            }
          } else {
            // Mirror geometry past 90°: Front cover shell on far left, recessed pages in center, Back cover shell meeting Verso
            if (frontBoardW > 0.25f) {
              val frontAnchorRight = minOf(p.bookRightX - 0.5f, p.slabFrontX + p.shellCornerRxEffective + frontBoardW + 2f)
              frontShellPath.rewind()
              frontShellPath.addRoundRect(
                RoundRect(
                  left = p.slabFrontX,
                  top = topOuterY,
                  right = frontAnchorRight,
                  bottom = botOuterY,
                  topLeftCornerRadius = shellRadius,
                  bottomLeftCornerRadius = shellRadius,
                )
              )
              drawPath(path = frontShellPath, brush = frontBoardBrush)
              drawPath(
                path = frontShellPath,
                color = Color.White.copy(alpha = (if (isDarkTheme) 0.12f else 0.18f) * p.edgeTurn),
                style = shellLipHighlightStroke,
              )
            }

            drawPagesBlock(roundLeftSide = true, roundRightSide = false)

            if (frontBoardW > 0.25f && pageH > 2f) {
              drawLine(
                color = Color.Black.copy(alpha = (if (isDarkTheme) 0.28f else 0.15f) * p.edgeTurn),
                start = Offset(paperStartX, topPageY + pageCornerRy * 0.5f),
                end = Offset(paperStartX, botPageY - pageCornerRy * 0.5f),
                strokeWidth = 0.55.dp.toPx(),
              )
            }

            if (backBoardW > 0.25f) {
              val tuckPx = minOf(p.shellCornerRxEffective + 1.5f, (p.backProjectedWidthPx * 0.78f).coerceAtLeast(0f))
              backShellPath.rewind()
              backShellPath.addRoundRect(
                RoundRect(
                  left = p.slabBackX - backBoardW,
                  top = topOuterY,
                  right = p.slabBackX + tuckPx,
                  bottom = botOuterY,
                  topLeftCornerRadius = shellRadius,
                  bottomLeftCornerRadius = shellRadius,
                )
              )
              drawPath(path = backShellPath, color = backBoardTone)
              if (pageH > 2f) {
                drawLine(
                  color = Color.Black.copy(alpha = (if (isDarkTheme) 0.24f else 0.12f) * p.edgeTurn),
                  start = Offset(paperEndX, topOuterY + p.shellCornerRy * 0.35f),
                  end = Offset(paperEndX, botOuterY - p.shellCornerRy * 0.35f),
                  strokeWidth = 0.5.dp.toPx(),
                )
              }
            }
          }
        }
      }
    }

    // Front Cover Face (kept mounted during flip; shadowElevation = 0f avoids HWUI RenderNode 3D dark artifacts)
    Box(
      modifier =
        Modifier.graphicsLayer {
          val p = projectionProvider()
          scaleX = p.combinedScale
          scaleY = p.combinedScale
          rotationY = p.frontRotationY
          cameraDistance = cameraDist
          translationX = p.frontTranslationX
          translationY = p.liftY
          shadowElevation = 0f
          shape = EditionFrontShape
          clip = false
          alpha = p.frontAlpha
        },
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier =
          Modifier.onSizeChanged { measuredCoverSize = it }
            .clip(EditionFrontShape)
            .indication(interactionSource, ripple(bounded = true))
            .then(
              if (isSelected) {
                Modifier.border(1.5.dp, selectedBorderColor, EditionFrontShape)
              } else {
                Modifier
              }
            ),
      ) {
        BookCoverArtwork(
          title = edition.workTitle,
          author = edition.primaryAuthorsText,
          isFavorite = false,
          coverUri = compactEditionCovers.firstOrNull(),
          fallbackCoverUris = compactEditionCovers.drop(1),
          compact = true,
          elevated = false,
          widthHintDp = 120.dp,
          contentScale = ContentScale.Fit,
          shape = EditionFrontShape,
          onCoverEdgePaletteChanged = { coverEdgePalette = it },
        )

        AnimatedVisibility(
          visible = isSelected && !isShowingBack,
          enter = fadeIn(animationSpec = motion.standardTween(140)),
          exit = fadeOut(animationSpec = motion.standardTween(100)),
          modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
        ) {
          Box(
            modifier =
              Modifier.size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary)
                .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                .testTag("${testTag}_selected_badge"),
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
    }

    // Back Face ("Verso") — unmounted at rest; clean light paper surface framed as a bound board
    if (shouldMountBackFace) {
      val backWidthDp =
        if (measuredCoverSize.width > 0) with(density) { measuredCoverSize.width.toDp() }
        else Dp.Unspecified
      val backHeightDp =
        if (measuredCoverSize.height > 0) with(density) { measuredCoverSize.height.toDp() }
        else Dp.Unspecified
      val backSizeModifier =
        if (backWidthDp != Dp.Unspecified && backHeightDp != Dp.Unspecified) {
          Modifier.width(backWidthDp).height(backHeightDp)
        } else {
          Modifier.fillMaxSize()
        }

      Surface(
        color = versoBackground,
        shape = EditionBackShape,
        border = BorderStroke(0.75.dp, versoFrameColor),
        shadowElevation = 0.dp,
        modifier =
          backSizeModifier
            .graphicsLayer {
              val p = projectionProvider()
              scaleX = p.combinedScale
              scaleY = p.combinedScale
              rotationY = p.backRotationY
              cameraDistance = cameraDist
              translationX = p.backTranslationX
              translationY = p.liftY
              shadowElevation = 0f
              shape = EditionBackShape
              clip = true
              alpha = p.backAlpha
            }
            .testTag("${testTag}_back_face"),
      ) {
        Box(modifier = Modifier.fillMaxSize()) {
          // Upper editorial colophon layout so metadata clears the lower-center holding thumb
          Column(
            modifier =
              Modifier.fillMaxSize()
                .padding(start = 10.dp, end = 10.dp, top = 11.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top,
          ) {
            if (publisherText != null) {
              Text(
                text = publisherText,
                style =
                  TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    letterSpacing = (-0.05).sp,
                  ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }

            if (yearValue != null) {
              if (publisherText != null) {
                Spacer(modifier = Modifier.height(5.dp))
              }
              Text(
                text = yearValue.toString(),
                style =
                  TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    fontFeatureSettings = "tnum",
                  ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                maxLines = 1,
              )
            }

            if (pagesText != null) {
              if (yearValue != null) {
                Spacer(modifier = Modifier.height(2.dp))
              } else if (publisherText != null) {
                Spacer(modifier = Modifier.height(5.dp))
              }
              Text(
                text = pagesText,
                style =
                  TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    lineHeight = 13.5.sp,
                    fontFeatureSettings = "tnum",
                  ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                textAlign = TextAlign.Start,
                maxLines = 1,
              )
            }
          }

          // Delicate spine hinge crease exclusively along the rightmost spine edge
          Box(
            modifier =
              Modifier.fillMaxSize().drawBehind {
                drawRect(brush = spineCreaseBrush)
              }
          )
        }
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
  selectedEdition: SearchEditionCandidate?,
  expansionProgressProvider: () -> Float,
  onConfirmSelectedEdition: (SearchEditionCandidate) -> Unit,
  onShowMoreLocalEditions: () -> Unit,
  onToggleSecondaryEditions: () -> Unit,
  onLoadMoreRemoteEditions: () -> Unit,
  onCollapseGroup: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val motion = DipeTheme.motion
  val reduceMotion = motion.reduceMotion
  val density = LocalDensity.current
  val glideOffsetPx = remember(density) { with(density) { (-4).dp.toPx() } }

  val footerShape =
    RoundedCornerShape(
      topStart = 0.dp,
      topEnd = 0.dp,
      bottomStart = DipeRadii.surface,
      bottomEnd = DipeRadii.surface,
    )
  val actionPillTextStyle =
    TextStyle(
      fontFamily = InterFontFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 12.sp,
      lineHeight = 16.sp,
      fontFeatureSettings = "tnum",
    )
  val hasSecondaryControls =
    remainingEditions > 0 || canLoadMoreRemoteEditions || secondaryEditionsCount > 0 || showCollapseAction

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = footerShape,
    shadowElevation = 0.dp,
    modifier =
      modifier
        .fillMaxWidth()
        .clip(footerShape)
        .clipToBounds()
        .layout { measurable, constraints ->
          val placeable = measurable.measure(constraints)
          val p = if (reduceMotion) 1f else expansionProgressProvider().coerceIn(0f, 1f)
          val heightPx = (placeable.height * p).roundToInt().coerceAtLeast(0)
          layout(placeable.width, heightPx) {
            placeable.placeRelative(0, 0)
          }
        },
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .graphicsLayer {
            val p = if (reduceMotion) 1f else expansionProgressProvider().coerceIn(0f, 1f)
            alpha = smoothstep01(0.18f, 0.90f, p)
            translationY = (1f - p) * glideOffsetPx
          }
          .padding(
            start = 14.dp,
            end = 10.dp,
            top = 4.dp,
            bottom = if (hasSecondaryControls) 8.dp else 12.dp,
          ),
    ) {
      AnimatedVisibility(
        visible = selectedEdition != null,
        enter =
          fadeIn(animationSpec = motion.standardTween(150)) +
            expandVertically(animationSpec = motion.standardTween(160)),
        exit =
          fadeOut(animationSpec = motion.standardTween(110)) +
            shrinkVertically(animationSpec = motion.standardTween(120)),
      ) {
        selectedEdition?.let { edition ->
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .padding(end = 4.dp, bottom = if (hasSecondaryControls) 8.dp else 0.dp),
          ) {
            Button(
              onClick = { onConfirmSelectedEdition(edition) },
              shape = CircleShape,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.secondary,
                  contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
              contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("search_confirm_edition_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.search_edition_select_action),
                style =
                  TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                  ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      if (hasSecondaryControls) {
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
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
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
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
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
                      MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
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
  val actionLabel =
    stringResource(if (isExpanded) R.string.cd_collapse_section else R.string.cd_expand_section)

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(DipeRadii.control))
        .minimumInteractiveComponentSize()
        .clickable(
          role = Role.Button,
          onClickLabel = actionLabel,
          onClick = onToggle,
        )
        .padding(horizontal = 4.dp, vertical = 10.dp)
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

    Text(
      text = count.toString(),
      style =
        TextStyle(
          fontFamily = InterFontFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 12.sp,
          lineHeight = 14.sp,
          fontFeatureSettings = "tnum",
        ),
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
    )

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
