package com.example.feature.library

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.copyMediaTypeLabel
import com.example.core.designsystem.components.editionFormatLabel
import com.example.core.designsystem.components.readingStatusLabel
import com.example.core.designsystem.components.rememberSafeCoverPicker
import com.example.core.model.CopyMediaType
import com.example.core.model.EditionFormat
import com.example.core.model.ReadingProgressMath
import com.example.core.model.ReadingStatus

@Immutable
data class AddBookSubmission(
  val title: String,
  val author: String,
  val totalPages: Int?,
  val publicationYear: Int?,
  val editionNote: String?,
  val status: ReadingStatus,
  val isbn: String? = null,
  val pickedCoverUri: Uri? = null,
  val language: String? = null,
  val format: EditionFormat? = null,
  val translator: String? = null,
  val copyMediaType: CopyMediaType = CopyMediaType.PHYSICAL,
  val copyLabel: String? = null,
  val copyLocation: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddBookSheet(
  onDismiss: () -> Unit,
  onSave: (AddBookSubmission) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var title by remember { mutableStateOf("") }
  var author by remember { mutableStateOf("") }
  var pagesText by remember { mutableStateOf("") }
  var yearText by remember { mutableStateOf("") }
  var editionNote by remember { mutableStateOf("") }
  var selectedStatus by remember { mutableStateOf(ReadingStatus.UNREAD) }
  var pickedCoverUri by remember { mutableStateOf<Uri?>(null) }

  // Progressive details accordion
  var showMoreDetails by remember { mutableStateOf(false) }
  var isbnText by remember { mutableStateOf("") }
  var languageText by remember { mutableStateOf("") }
  var selectedFormat by remember { mutableStateOf<EditionFormat?>(null) }
  var translatorText by remember { mutableStateOf("") }
  var selectedCopyMedia by remember { mutableStateOf(CopyMediaType.PHYSICAL) }
  var copyLabelText by remember { mutableStateOf("") }
  var copyLocationText by remember { mutableStateOf("") }

  var validationErrorRes by remember { mutableStateOf<Int?>(null) }
  var titleAuthorError by remember { mutableStateOf(false) }
  var pagesError by remember { mutableStateOf(false) }
  var yearError by remember { mutableStateOf(false) }
  var isbnError by remember { mutableStateOf(false) }

  val launchCoverPicker = rememberSafeCoverPicker { uri -> pickedCoverUri = uri }

  val textFieldColors =
    OutlinedTextFieldDefaults.colors(
      focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
      unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
      focusedBorderColor = MaterialTheme.colorScheme.secondary,
    )

  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = MaterialTheme.colorScheme.primary,
      selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
      containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      Text(
        text = stringResource(R.string.book_add_sheet_title),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )

      // Compact cover preview & Photo Picker row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BookCoverArtwork(
          title = title.ifBlank { stringResource(R.string.app_name) },
          author = author.ifBlank { stringResource(R.string.profile_default_name) },
          isFavorite = false,
          coverUri = pickedCoverUri?.toString(),
          compact = pickedCoverUri == null,
          elevated = true,
          modifier = Modifier.width(56.dp),
        )
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        ) {
          OutlinedButton(
            onClick = launchCoverPicker,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.xs),
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("add_book_pick_cover_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.AddPhotoAlternate,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(DipeSpacing.xs))
            Text(
              text =
                stringResource(
                  if (pickedCoverUri == null) R.string.cover_pick_photo
                  else R.string.cover_change_photo
                ),
              style = MaterialTheme.typography.labelLarge,
            )
          }
          if (pickedCoverUri != null) {
            TextButton(
              onClick = { pickedCoverUri = null },
              contentPadding = PaddingValues(horizontal = DipeSpacing.xs, vertical = 2.dp),
              modifier = Modifier.testTag("add_book_remove_cover_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = stringResource(R.string.cover_remove_photo),
                style = MaterialTheme.typography.labelMedium,
              )
            }
          }
        }
      }

      OutlinedTextField(
        value = title,
        onValueChange = {
          title = it
          titleAuthorError = false
          validationErrorRes = null
        },
        label = { Text(stringResource(R.string.book_field_title)) },
        isError = titleAuthorError && title.isBlank(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("add_book_title_input"),
      )

      OutlinedTextField(
        value = author,
        onValueChange = {
          author = it
          titleAuthorError = false
          validationErrorRes = null
        },
        label = { Text(stringResource(R.string.book_field_author)) },
        isError = titleAuthorError && author.isBlank(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("add_book_author_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
      ) {
        OutlinedTextField(
          value = pagesText,
          onValueChange = { input ->
            pagesText = input.filter { it.isDigit() }.take(6)
            pagesError = false
            validationErrorRes = null
          },
          label = { Text(stringResource(R.string.book_field_pages)) },
          isError = pagesError,
          singleLine = true,
          shape = MaterialTheme.shapes.medium,
          colors = textFieldColors,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.weight(1f).testTag("add_book_pages_input"),
        )
        OutlinedTextField(
          value = yearText,
          onValueChange = { input ->
            yearText = input.filter { it.isDigit() }.take(4)
            yearError = false
            validationErrorRes = null
          },
          label = { Text(stringResource(R.string.book_field_year)) },
          isError = yearError,
          singleLine = true,
          shape = MaterialTheme.shapes.medium,
          colors = textFieldColors,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.weight(1f).testTag("add_book_year_input"),
        )
      }

      OutlinedTextField(
        value = editionNote,
        onValueChange = { editionNote = it },
        label = { Text(stringResource(R.string.book_field_edition_note)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("add_book_edition_input"),
      )

      Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
        Text(
          text = stringResource(R.string.book_field_status),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        ) {
          ReadingStatus.entries.forEach { status ->
            val selected = selectedStatus == status
            FilterChip(
              selected = selected,
              onClick = { selectedStatus = status },
              label = { Text(readingStatusLabel(status)) },
              shape = CircleShape,
              colors = chipColors,
              border =
                FilterChipDefaults.filterChipBorder(
                  enabled = true,
                  selected = selected,
                  borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                  borderWidth = 0.5.dp,
                ),
              modifier = Modifier.testTag("add_book_status_${status.name.lowercase()}"),
            )
          }
        }
      }

      // Progressive disclosure for edition & first copy details
      HairlineDivider()
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { showMoreDetails = !showMoreDetails }
            .minimumInteractiveComponentSize()
            .padding(vertical = DipeSpacing.xs)
            .testTag("add_book_more_details_toggle"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.book_more_edition_details),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
          imageVector =
            if (showMoreDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      AnimatedVisibility(visible = showMoreDetails) {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          ) {
            OutlinedTextField(
              value = isbnText,
              onValueChange = {
                isbnText = it
                isbnError = false
                validationErrorRes = null
              },
              label = { Text(stringResource(R.string.book_field_isbn)) },
              isError = isbnError,
              singleLine = true,
              shape = MaterialTheme.shapes.medium,
              colors = textFieldColors,
              modifier = Modifier.weight(1f).testTag("add_book_isbn_input"),
            )
            OutlinedTextField(
              value = languageText,
              onValueChange = { languageText = it },
              label = { Text(stringResource(R.string.book_field_language)) },
              singleLine = true,
              shape = MaterialTheme.shapes.medium,
              colors = textFieldColors,
              modifier = Modifier.weight(1f).testTag("add_book_language_input"),
            )
          }

          OutlinedTextField(
            value = translatorText,
            onValueChange = { translatorText = it },
            label = { Text(stringResource(R.string.book_field_translator)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth().testTag("add_book_translator_input"),
          )

          Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
            Text(
              text = stringResource(R.string.book_field_format),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
              verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
            ) {
              EditionFormat.entries.forEach { format ->
                val selected = selectedFormat == format
                FilterChip(
                  selected = selected,
                  onClick = { selectedFormat = if (selected) null else format },
                  label = { Text(editionFormatLabel(format)) },
                  shape = CircleShape,
                  colors = chipColors,
                  border =
                    FilterChipDefaults.filterChipBorder(
                      enabled = true,
                      selected = selected,
                      borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                      borderWidth = 0.5.dp,
                    ),
                  modifier = Modifier.testTag("add_book_format_${format.name.lowercase()}"),
                )
              }
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
            Text(
              text = stringResource(R.string.book_field_copy_type),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
              CopyMediaType.entries.forEach { mediaType ->
                val selected = selectedCopyMedia == mediaType
                FilterChip(
                  selected = selected,
                  onClick = { selectedCopyMedia = mediaType },
                  label = { Text(copyMediaTypeLabel(mediaType)) },
                  shape = CircleShape,
                  colors = chipColors,
                  border =
                    FilterChipDefaults.filterChipBorder(
                      enabled = true,
                      selected = selected,
                      borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                      borderWidth = 0.5.dp,
                    ),
                  modifier = Modifier.testTag("add_book_copy_media_${mediaType.name.lowercase()}"),
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          ) {
            OutlinedTextField(
              value = copyLabelText,
              onValueChange = { copyLabelText = it },
              label = { Text(stringResource(R.string.book_field_copy_label)) },
              singleLine = true,
              shape = MaterialTheme.shapes.medium,
              colors = textFieldColors,
              modifier = Modifier.weight(1f).testTag("add_book_copy_label_input"),
            )
            OutlinedTextField(
              value = copyLocationText,
              onValueChange = { copyLocationText = it },
              label = { Text(stringResource(R.string.book_field_copy_location)) },
              singleLine = true,
              shape = MaterialTheme.shapes.medium,
              colors = textFieldColors,
              modifier = Modifier.weight(1f).testTag("add_book_copy_location_input"),
            )
          }
        }
      }

      validationErrorRes?.let { errorRes ->
        Text(
          text = stringResource(errorRes),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag("add_book_validation_error"),
        )
      }

      Spacer(modifier = Modifier.height(DipeSpacing.xs))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("add_book_cancel_button"),
        ) {
          Text(stringResource(R.string.book_cancel_action))
        }
        Spacer(modifier = Modifier.width(DipeSpacing.sm))
        Button(
          onClick = {
            val parsedPages =
              if (pagesText.isBlank()) null else pagesText.toIntOrNull()?.takeIf { it in 1..50_000 }
            val parsedYear =
              if (yearText.isBlank()) null else yearText.toIntOrNull()?.takeIf { it in 1..2100 }
            val isbnValid = ReadingProgressMath.isValidIsbn(isbnText)

            when {
              title.isBlank() || author.isBlank() -> {
                titleAuthorError = true
                validationErrorRes = R.string.book_validation_required
              }
              pagesText.isNotBlank() && parsedPages == null -> {
                pagesError = true
                validationErrorRes = R.string.book_validation_pages
              }
              yearText.isNotBlank() && parsedYear == null -> {
                yearError = true
                validationErrorRes = R.string.book_validation_year
              }
              !isbnValid -> {
                showMoreDetails = true
                isbnError = true
                validationErrorRes = R.string.book_validation_isbn
              }
              else -> {
                onSave(
                  AddBookSubmission(
                    title = title.trim(),
                    author = author.trim(),
                    totalPages = parsedPages,
                    publicationYear = parsedYear,
                    editionNote = editionNote.trim().takeIf { it.isNotEmpty() },
                    status = selectedStatus,
                    isbn = ReadingProgressMath.normalizeIsbn(isbnText),
                    pickedCoverUri = pickedCoverUri,
                    language = languageText.trim().takeIf { it.isNotEmpty() },
                    format = selectedFormat,
                    translator = translatorText.trim().takeIf { it.isNotEmpty() },
                    copyMediaType = selectedCopyMedia,
                    copyLabel = copyLabelText.trim().takeIf { it.isNotEmpty() },
                    copyLocation = copyLocationText.trim().takeIf { it.isNotEmpty() },
                  )
                )
              }
            }
          },
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
          contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("add_book_save_button"),
        ) {
          Text(stringResource(R.string.book_save_action))
        }
      }

      Spacer(modifier = Modifier.height(DipeSpacing.lg))
    }
  }
}
