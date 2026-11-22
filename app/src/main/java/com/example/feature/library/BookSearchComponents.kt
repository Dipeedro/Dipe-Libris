package com.example.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.bibliographic.domain.CoverRanker
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.DipeIcons
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.MicroMetadataItem
import com.example.core.designsystem.components.MinimalLanguageBadge
import com.example.core.designsystem.components.editionFormatLabel
import com.example.core.model.CatalogBook
import com.example.core.model.DuplicateAssessment
import com.example.core.model.MetadataSource
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import com.example.core.model.TitleBundleKind

@Composable
internal fun SearchLocalMatchCard(
  book: CatalogBook,
  testTag: String,
  onClick: () -> Unit,
) {
  EditorialSurfaceCard(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
    contentPadding = PaddingValues(horizontal = DipeSpacing.compact, vertical = DipeSpacing.compact),
    onClick = onClick,
    modifier = Modifier.testTag(testTag),
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
        modifier = Modifier.width(44.dp),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = book.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = book.author,
          style = MaterialTheme.typography.bodySmall,
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
        )
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchWorkGroupCard(
  group: SearchWorkGroup,
  groupIndex: Int,
  genreLabel: String?,
  isExpanded: Boolean,
  isSecondaryEditionsExpanded: Boolean,
  isLoadingMore: Boolean,
  onToggleExpand: () -> Unit,
  onToggleSecondaryEditions: () -> Unit,
  onLoadMoreEditions: () -> Unit,
  onSelectEdition: (SearchEditionCandidate) -> Unit,
  isExpansionExhausted: Boolean = false,
) {
  val editionCount = group.editions.size
  val visibleEditions =
    if (isSecondaryEditionsExpanded) {
      group.primaryTierEditions + group.secondaryTierEditions
    } else {
      group.primaryTierEditions
    }
  val bundleKind = group.effectiveBundleKind
  val bundleBadgeLabel =
    when (bundleKind) {
      TitleBundleKind.COLLECTION_OR_BOX -> stringResource(R.string.search_bundle_box_badge)
      TitleBundleKind.OMNIBUS_SINGLE_VOLUME -> stringResource(R.string.search_bundle_omnibus_badge)
      else -> null
    }
  val previewCoverSource = group.editions.firstNotNullOfOrNull { it.bestCover }?.source
  val compactGroupCovers =
    remember(group.groupId, group.editions) {
      val ordered =
        buildList {
          group.previewCoverUrl?.let { add(it) }
          addAll(group.previewFallbackCoverUrls)
        }.distinct()
      CoverRanker.selectForDisplaySize(ordered, maxWidthDp = 52)
    }

  EditorialSurfaceCard(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentPadding = PaddingValues(DipeSpacing.compact),
    modifier = Modifier.testTag("search_work_card_$groupIndex"),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggleExpand),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BookCoverArtwork(
          title = group.canonicalTitle,
          author = group.displayAuthors,
          isFavorite = false,
          coverUri = compactGroupCovers.firstOrNull(),
          fallbackCoverUris = compactGroupCovers.drop(1),
          compact = true,
          elevated = true,
          modifier = Modifier.width(52.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(
            text = group.canonicalTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          group.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
            Text(
              text = sub,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (group.displayAuthors.isNotBlank()) {
            Text(
              text = group.displayAuthors,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            MicroMetadataItem(
              icon = Icons.Outlined.Layers,
              text =
                pluralStringResource(
                  R.plurals.search_work_editions_count,
                  if (editionCount == 1) 1 else 2,
                  editionCount,
                ),
              tint = MaterialTheme.colorScheme.secondary,
              textColor = MaterialTheme.colorScheme.secondary,
            )
            if (bundleBadgeLabel != null) {
              Text(
                text = bundleBadgeLabel,
                style = LocalDipeTypography.current.monoMetadata.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.tertiary,
              )
            }
            if (genreLabel != null) {
              Text(
                text = genreLabel,
                style = LocalDipeTypography.current.monoMetadata.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            if (group.localMatchedWorkId != null) {
              MicroMetadataItem(
                icon = Icons.Outlined.CheckCircle,
                text = stringResource(R.string.search_work_in_library_badge),
                tint = MaterialTheme.colorScheme.secondary,
                textColor = MaterialTheme.colorScheme.secondary,
              )
            }
            if (previewCoverSource == MetadataSource.APPLE_BOOKS) {
              Text(
                text = stringResource(R.string.search_source_attribution_apple),
                style = LocalDipeTypography.current.monoMetadata.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        IconButton(
          onClick = onToggleExpand,
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("search_work_expand_$groupIndex"),
        ) {
          Icon(
            imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription =
              stringResource(
                if (isExpanded) R.string.cd_collapse_section else R.string.cd_expand_section
              ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      AnimatedVisibility(visible = isExpanded) {
        var editionWindowLimit by remember(group.groupId, isExpanded) { mutableIntStateOf(6) }
        var prevVisibleEditionCount by remember(group.groupId, isExpanded) { mutableIntStateOf(visibleEditions.size) }
        LaunchedEffect(visibleEditions.size, isExpanded) {
          if (isExpanded && visibleEditions.size > prevVisibleEditionCount) {
            editionWindowLimit = maxOf(editionWindowLimit, visibleEditions.size)
          }
          prevVisibleEditionCount = visibleEditions.size
        }
        val displayedEditions = visibleEditions.take(editionWindowLimit)
        val remainingEditions = (visibleEditions.size - displayedEditions.size).coerceAtLeast(0)

        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          displayedEditions.forEachIndexed { editionIdx, edition ->
            HairlineDivider()
            SearchEditionRow(
              edition = edition,
              testTag = "search_edition_select_${groupIndex}_$editionIdx",
              onClick = { onSelectEdition(edition) },
            )
          }

          if (remainingEditions > 0 || group.secondaryTierEditions.isNotEmpty() || ((group.externalWorkKey != null || group.displayAuthors.isNotBlank()) && !isExpansionExhausted)) {
            HairlineDivider()
          }

          if (remainingEditions > 0) {
            TextButton(
              onClick = { editionWindowLimit += 8 },
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("search_edition_show_more_$groupIndex"),
            ) {
              Text(stringResource(R.string.search_edition_show_more_local, remainingEditions))
            }
          }

          if (group.secondaryTierEditions.isNotEmpty()) {
            TextButton(
              onClick = onToggleSecondaryEditions,
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("search_work_secondary_editions_toggle_$groupIndex"),
            ) {
              Text(
                text =
                  if (isSecondaryEditionsExpanded) {
                    stringResource(R.string.search_edition_hide_secondary_langs)
                  } else {
                    stringResource(
                      R.string.search_edition_show_secondary_langs,
                      group.secondaryTierEditions.size,
                    )
                  }
              )
            }
          }

          if ((group.externalWorkKey != null || group.displayAuthors.isNotBlank()) && !isExpansionExhausted) {
            TextButton(
              onClick = onLoadMoreEditions,
              enabled = !isLoadingMore,
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("search_edition_load_more_$groupIndex"),
            ) {
              if (isLoadingMore) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(DipeSpacing.xs))
              }
              Text(stringResource(R.string.search_edition_load_more))
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchEditionRow(
  edition: SearchEditionCandidate,
  testTag: String,
  onClick: () -> Unit,
) {
  val langBadge = edition.canonicalLanguageTag ?: edition.language
  val compactEditionCovers =
    remember(edition.candidateId, edition.orderedCoverUrls) {
      CoverRanker.selectForDisplaySize(edition.orderedCoverUrls, maxWidthDp = 44)
    }

  val primaryHeading =
    remember(edition.publisher, edition.editionLabel, edition.editionTitle, edition.workTitle) {
      val publisherClean = edition.publisher?.trim()?.takeIf { it.isNotEmpty() }
      val labelClean =
        edition.editionLabel?.trim()?.takeIf {
          it.isNotEmpty() && !it.equals(publisherClean, ignoreCase = true)
        }
      listOfNotNull(publisherClean, labelClean)
        .joinToString(" · ")
        .ifBlank {
          edition.editionTitle?.takeIf { it.isNotBlank() } ?: edition.workTitle
        }
    }

  val sourcesLine =
    remember(edition.sources) {
      edition.sources.joinToString(", ") { it.displayName }
    }

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.small)
        .clickable(onClick = onClick)
        .padding(horizontal = DipeSpacing.xs, vertical = DipeSpacing.compact)
        .testTag(testTag),
    horizontalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BookCoverArtwork(
      title = edition.workTitle,
      author = edition.primaryAuthorsText,
      isFavorite = false,
      coverUri = compactEditionCovers.firstOrNull(),
      fallbackCoverUris = compactEditionCovers.drop(1),
      compact = true,
      modifier = Modifier.width(44.dp),
    )

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      // Line 1: Publisher / Edition name + Minimalist Language Seal
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = primaryHeading,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        MinimalLanguageBadge(languageTag = langBadge)
      }

      // Line 2: Pages · Year · Format via minimalist icons
      if (edition.totalPages != null || edition.publicationYear != null || edition.format != null) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          edition.totalPages?.let { pages ->
            MicroMetadataItem(
              icon = Icons.AutoMirrored.Outlined.MenuBook,
              text = "$pages p.",
            )
          }
          edition.publicationYear?.let { year ->
            MicroMetadataItem(
              icon = Icons.Outlined.CalendarToday,
              text = year.toString(),
            )
          }
          edition.format?.let { format ->
            MicroMetadataItem(
              icon = Icons.Outlined.Style,
              text = editionFormatLabel(format),
            )
          }
        }
      }

      // Line 3: Full ISBN (untruncated) + Provenance source
      if (edition.bestIsbn != null || sourcesLine.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          edition.bestIsbn?.let { isbn ->
            MicroMetadataItem(
              icon = DipeIcons.Barcode,
              text = isbn,
            )
          }
          if (sourcesLine.isNotEmpty()) {
            Text(
              text = sourcesLine,
              style = LocalDipeTypography.current.monoMetadata.copy(fontSize = 11.sp, lineHeight = 14.sp),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    Icon(
      imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
      contentDescription = stringResource(R.string.search_edition_select_action),
      tint = MaterialTheme.colorScheme.secondary,
      modifier = Modifier.size(20.dp),
    )
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
