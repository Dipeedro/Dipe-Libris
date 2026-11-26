package com.example.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.bibliographic.domain.CoverRanker
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.InterFontFamily
import com.example.core.designsystem.JetBrainsMonoFontFamily
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.designsystem.components.MicroMetadataItem
import com.example.core.designsystem.components.MinimalLanguageBadge
import com.example.core.designsystem.components.formatMinimalLanguageCode
import com.example.core.designsystem.components.shouldShowMinimalLanguageBadge
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.CatalogBook
import com.example.core.model.DuplicateAssessment
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import com.example.core.model.SourceQueryStatus
import java.util.Locale

private val MINOR_TITLE_CONNECTORS =
  setOf(
    "a", "as", "o", "os", "e", "em", "de", "da", "das", "do", "dos",
    "para", "com", "sem", "sob", "por", "um", "uma", "uns", "umas",
    "no", "na", "nos", "nas", "ao", "aos", "à", "às", "ou",
    "of", "the", "and", "in", "on", "at", "to", "for", "by", "with", "an",
  )

private val ROMAN_NUMERAL_REGEX =
  Regex("^(X{1,3}(IX|IV|V?I{0,3})|IX|IV|VI{0,3}|I{1,3})$")

private const val CARD_TRANSITION_MS = 220

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
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = RoundedCornerShape(12.dp),
    contentPadding = PaddingValues(12.dp),
    onClick = onClick,
    modifier = modifier.testTag(testTag),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      BookCoverArtwork(
        title = book.title,
        author = book.author,
        isFavorite = book.isFavorite,
        coverUri = book.coverUri,
        compact = true,
        widthHintDp = 42.dp,
        modifier = Modifier.width(42.dp),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = displayTitle,
          style =
            TextStyle(
              fontFamily = displayFontFamily,
              fontWeight = FontWeight.Medium,
              fontSize = 16.sp,
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
              fontSize = 13.sp,
              lineHeight = 18.sp,
            ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        MinimalLanguageBadge(languageTag = book.primaryEdition?.language)
        MicroMetadataItem(
          icon = Icons.Outlined.CheckCircle,
          text = stringResource(R.string.search_work_in_library_badge),
          tint = MaterialTheme.colorScheme.secondary,
          textColor = MaterialTheme.colorScheme.secondary,
          textStyle = searchSansMetadataStyle().copy(fontWeight = FontWeight.Medium),
        )
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
internal fun SearchWorkCard(
  group: SearchWorkGroup,
  groupIndex: Int,
  isExpanded: Boolean,
  displayedEditions: List<SearchEditionCandidate>,
  remainingEditions: Int,
  secondaryEditionsCount: Int,
  isSecondaryEditionsExpanded: Boolean,
  canLoadMoreRemoteEditions: Boolean,
  isLoadingMore: Boolean,
  selectedEditionId: String?,
  onToggleExpand: () -> Unit,
  onShowMoreLocalEditions: () -> Unit,
  onToggleSecondaryEditions: () -> Unit,
  onLoadMoreRemoteEditions: () -> Unit,
  onToggleEditionSelection: (SearchEditionCandidate) -> Unit,
  modifier: Modifier = Modifier,
) {
  val appLanguage = LocalAppLanguage.current
  val reduceMotion = DipeTheme.motion.reduceMotion
  val animDuration = if (reduceMotion) 0 else CARD_TRANSITION_MS

  val cardCovers =
    remember(group.groupId, group.previewCoverUrl, group.previewFallbackCoverUrls) {
      val ordered =
        buildList {
          group.previewCoverUrl?.let { add(it) }
          addAll(group.previewFallbackCoverUrls)
        }.distinct()
      CoverRanker.selectForDisplaySize(ordered, maxWidthDp = 64)
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

  val editionCount = group.editions.size
  val editionsCountText =
    pluralStringResource(R.plurals.search_work_editions_count, editionCount, editionCount)

  val primaryEdition = group.editions.firstOrNull()
  val rawLanguageTag = primaryEdition?.canonicalLanguageTag ?: primaryEdition?.language
  val languageSuffix =
    remember(rawLanguageTag, appLanguage) {
      if (shouldShowMinimalLanguageBadge(rawLanguageTag, appLanguage, hideIfMatchesAppLanguage = true)) {
        formatMinimalLanguageCode(rawLanguageTag)
      } else {
        null
      }
    }

  val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
  val terracottaColor = MaterialTheme.colorScheme.secondary
  val metadataLineText =
    remember(releaseYear, editionsCountText, languageSuffix, mutedColor, terracottaColor) {
      buildAnnotatedString {
        if (releaseYear != null) {
          withStyle(SpanStyle(color = mutedColor)) {
            append("$releaseYear · ")
          }
        }
        withStyle(SpanStyle(color = terracottaColor, fontWeight = FontWeight.Medium)) {
          append(editionsCountText)
        }
        if (!languageSuffix.isNullOrBlank()) {
          withStyle(SpanStyle(color = mutedColor)) {
            append(" · $languageSuffix")
          }
        }
      }
    }

  val displayFontFamily = LocalDipeTypography.current.displayFontFamily
  val monoMetaStyle =
    TextStyle(
      fontFamily = JetBrainsMonoFontFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 11.5.sp,
      lineHeight = 15.sp,
      fontFeatureSettings = "tnum",
    )
  val footerActionStyle =
    TextStyle(
      fontFamily = InterFontFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 13.sp,
      lineHeight = 18.sp,
    )

  val chevronRotation by
    animateFloatAsState(
      targetValue = if (isExpanded) 180f else 0f,
      animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
      label = "work_expand_chevron_rotation",
    )
  val cardShape = RoundedCornerShape(12.dp)
  val coverShape = RoundedCornerShape(8.dp)
  val expandLabel =
    stringResource(if (isExpanded) R.string.cd_collapse_section else R.string.cd_expand_section)

  val canTriggerMoreEditions =
    remainingEditions > 0 ||
      (!isSecondaryEditionsExpanded && secondaryEditionsCount > 0) ||
      canLoadMoreRemoteEditions

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shape = cardShape,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shadowElevation = 0.dp,
    modifier =
      modifier
        .fillMaxWidth()
        .padding(top = 12.dp)
        .testTag("search_work_card_$groupIndex"),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(cardShape)
            .clickable(
              role = Role.Button,
              onClickLabel = expandLabel,
              onClick = onToggleExpand,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AnimatedVisibility(
          visible = !isExpanded,
          enter =
            expandHorizontally(
              animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
              expandFrom = Alignment.Start,
            ) + fadeIn(animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing)),
          exit =
            shrinkHorizontally(
              animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
              shrinkTowards = Alignment.Start,
            ) + fadeOut(animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing)),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier =
                Modifier.size(width = 64.dp, height = 96.dp)
                  .clip(coverShape)
                  .background(MaterialTheme.colorScheme.surfaceContainerLow)
                  .testTag("search_work_cover_$groupIndex"),
              contentAlignment = Alignment.Center,
            ) {
              BookCoverArtwork(
                title = group.canonicalTitle,
                author = closedAuthorText,
                isFavorite = false,
                coverUri = cardCovers.firstOrNull(),
                fallbackCoverUris = cardCovers.drop(1),
                compact = true,
                elevated = false,
                widthHintDp = 64.dp,
                contentScale = ContentScale.Fit,
                shape = coverShape,
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = displayTitle,
            style =
              TextStyle(
                fontFamily = displayFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.5.sp,
                lineHeight = 20.5.sp,
                letterSpacing = (-0.15).sp,
              ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )

          if (closedAuthorText.isNotBlank()) {
            Text(
              text = closedAuthorText,
              style =
                TextStyle(
                  fontFamily = InterFontFamily,
                  fontWeight = FontWeight.Normal,
                  fontSize = 13.sp,
                  lineHeight = 17.sp,
                ),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Text(
            text = metadataLineText,
            style = monoMetaStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
          modifier =
            Modifier.size(28.dp)
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevronRotation },
          )
        }
      }

      AnimatedVisibility(
        visible = isExpanded,
        enter =
          expandVertically(
            animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
          ) + fadeIn(animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing)),
        exit =
          shrinkVertically(
            animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
          ) + fadeOut(animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing)),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
            modifier = Modifier.padding(horizontal = 12.dp),
          )

          if (displayedEditions.isNotEmpty()) {
            val editionRows = remember(displayedEditions) { displayedEditions.chunked(3) }
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              editionRows.forEachIndexed { rowIndex, rowEditions ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalAlignment = Alignment.Top,
                ) {
                  for (colIdx in 0 until 3) {
                    val edition = rowEditions.getOrNull(colIdx)
                    if (edition != null) {
                      val globalIdx = (rowIndex * 3) + colIdx
                      SearchEditionGalleryTile(
                        edition = edition,
                        isSelected = selectedEditionId == edition.candidateId,
                        testTag = "search_edition_select_${groupIndex}_$globalIdx",
                        onClick = { onToggleEditionSelection(edition) },
                        modifier = Modifier.weight(1f),
                      )
                    } else {
                      Spacer(modifier = Modifier.weight(1f))
                    }
                  }
                }
              }
            }
          }

          HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
            modifier = Modifier.padding(horizontal = 12.dp),
          )

          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (canTriggerMoreEditions) {
              val actionTag =
                when {
                  remainingEditions > 0 -> "search_edition_show_more_$groupIndex"
                  !isSecondaryEditionsExpanded && secondaryEditionsCount > 0 ->
                    "search_work_secondary_editions_toggle_$groupIndex"
                  else -> "search_edition_load_more_$groupIndex"
                }
              TextButton(
                onClick = {
                  when {
                    remainingEditions > 0 -> onShowMoreLocalEditions()
                    !isSecondaryEditionsExpanded && secondaryEditionsCount > 0 ->
                      onToggleSecondaryEditions()
                    else -> onLoadMoreRemoteEditions()
                  }
                },
                enabled = !isLoadingMore,
                modifier =
                  Modifier.heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag(actionTag),
              ) {
                if (isLoadingMore) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.6.dp,
                    color = MaterialTheme.colorScheme.secondary,
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                  text = stringResource(R.string.search_edition_load_more),
                  style = footerActionStyle,
                  color = MaterialTheme.colorScheme.secondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            } else {
              Spacer(modifier = Modifier.weight(1f))
            }

            TextButton(
              onClick = onToggleExpand,
              modifier =
                Modifier.heightIn(min = 48.dp)
                  .minimumInteractiveComponentSize()
                  .testTag("search_work_collapse_$groupIndex"),
            ) {
              Text(
                text = stringResource(R.string.search_work_collapse_action),
                style = footerActionStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
              )
            }
          }
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
  val compactEditionCovers =
    remember(edition.candidateId, edition.orderedCoverUrls) {
      CoverRanker.selectForDisplaySize(edition.orderedCoverUrls, maxWidthDp = 110)
    }
  val publisherText = remember(edition.publisher) { edition.publisher?.trim().orEmpty() }
  val pagesPart =
    edition.totalPages?.takeIf { it > 0 }?.let { pages ->
      stringResource(R.string.search_edition_pages_short, pages)
    }
  val yearPart = edition.publicationYear?.takeIf { it in 1400..2100 }?.toString()
  val pagesAndYearText =
    remember(pagesPart, yearPart) {
      listOfNotNull(pagesPart, yearPart).joinToString(" · ")
    }

  val selectLabel = stringResource(R.string.search_edition_select_action)
  val tileShape = RoundedCornerShape(8.dp)
  val terracotta = MaterialTheme.colorScheme.secondary

  val publisherStyle =
    TextStyle(
      fontFamily = InterFontFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 11.5.sp,
      lineHeight = 15.sp,
    )
  val monoMetaStyle =
    TextStyle(
      fontFamily = JetBrainsMonoFontFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 10.5.sp,
      lineHeight = 14.sp,
      fontFeatureSettings = "tnum",
    )

  Column(
    modifier =
      modifier
        .clip(tileShape)
        .clickable(onClickLabel = selectLabel, onClick = onClick)
        .testTag(testTag),
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .aspectRatio(2f / 3f)
          .clip(tileShape)
          .background(MaterialTheme.colorScheme.surfaceContainerLow),
      contentAlignment = Alignment.Center,
    ) {
      BookCoverArtwork(
        title = edition.workTitle,
        author = edition.primaryAuthorsText,
        isFavorite = false,
        coverUri = compactEditionCovers.firstOrNull(),
        fallbackCoverUris = compactEditionCovers.drop(1),
        compact = true,
        elevated = false,
        widthHintDp = 110.dp,
        contentScale = ContentScale.Fit,
        shape = tileShape,
      )

      if (isSelected) {
        Box(
          modifier =
            Modifier.matchParentSize()
              .border(width = 2.dp, color = terracotta, shape = tileShape),
        )
        Box(
          modifier =
            Modifier.align(Alignment.TopEnd)
              .padding(6.dp)
              .size(20.dp)
              .clip(CircleShape)
              .background(terracotta)
              .testTag("${testTag}_selected_badge"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.size(13.dp),
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = publisherText,
      style = publisherStyle,
      color = MaterialTheme.colorScheme.onSurface,
      minLines = 1,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(2.dp))

    Text(
      text = pagesAndYearText,
      style = monoMetaStyle,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      minLines = 1,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.fillMaxWidth(),
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
