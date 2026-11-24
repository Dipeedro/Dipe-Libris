package com.example.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import com.example.core.designsystem.components.EditorialModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.editionFormatLabel
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.EditionWithBookCopies

@Composable
fun EditionComparisonSheet(
  workTitle: String,
  workAuthor: String,
  editions: List<EditionWithBookCopies>,
  onSelectEdition: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  val language = LocalAppLanguage.current
  val unspecified = stringResource(R.string.book_detail_unspecified)

  EditorialModalBottomSheet(
    onDismissRequest = onDismiss,
  ) { requestDismiss ->
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(vertical = DipeSpacing.sm)
          .testTag("edition_comparison_sheet"),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DipeSpacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.edition_compare_title),
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(
          onClick = requestDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("compare_close_button"),
        ) {
          Text(stringResource(R.string.book_cancel_action))
        }
      }

      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = DipeSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
      ) {
        editions.forEach { group ->
          val edition = group.edition
          EditorialSurfaceCard(
            modifier = Modifier.width(210.dp).testTag("compare_edition_card_${edition.id}"),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentPadding = PaddingValues(DipeSpacing.md),
            onClick = {
              onSelectEdition(edition.id)
              onDismiss()
            },
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              BookCoverArtwork(
                title = workTitle,
                author = workAuthor,
                isFavorite = edition.isPrimary,
                coverUri = edition.coverUri,
                elevated = true,
                modifier = Modifier.width(96.dp),
              )

              Text(
                text = edition.publisherOrNote?.takeIf { it.isNotBlank() } ?: unspecified,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )

              HairlineDivider()

              ComparisonFieldRow(
                label = stringResource(R.string.book_detail_year_label),
                value = edition.publicationYear?.toString() ?: unspecified,
              )
              ComparisonFieldRow(
                label = stringResource(R.string.book_detail_pages_label),
                value =
                  edition.totalPages?.let { LocaleManager.formatInteger(it, language) }
                    ?: unspecified,
              )
              ComparisonFieldRow(
                label = stringResource(R.string.book_detail_format_label),
                value = edition.format?.let { editionFormatLabel(it) } ?: unspecified,
              )
              ComparisonFieldRow(
                label = stringResource(R.string.book_detail_language_label),
                value = edition.language?.takeIf { it.isNotBlank() } ?: unspecified,
              )
              ComparisonFieldRow(
                label = stringResource(R.string.book_detail_translator_label),
                value = edition.translator?.takeIf { it.isNotBlank() } ?: unspecified,
              )
              ComparisonFieldRow(
                label = stringResource(R.string.book_detail_isbn_label),
                value = edition.isbn?.takeIf { it.isNotBlank() } ?: unspecified,
              )
              ComparisonFieldRow(
                label = stringResource(R.string.edition_compare_copies),
                value = LocaleManager.formatInteger(group.copies.size, language),
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(DipeSpacing.lg))
    }
  }
}

@Composable
private fun ComparisonFieldRow(
  label: String,
  value: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      text = label,
      style = LocalDipeTypography.current.monoMetadata,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(end = DipeSpacing.xs),
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
