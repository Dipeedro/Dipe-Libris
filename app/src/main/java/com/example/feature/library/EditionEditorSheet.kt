package com.example.feature.library

import android.net.Uri
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.copyMediaTypeLabel
import com.example.core.designsystem.components.editionFormatLabel
import com.example.core.designsystem.components.rememberCoverSourcePickerState
import com.example.core.model.CopyMediaType
import com.example.core.model.Edition
import com.example.core.model.EditionFormat
import com.example.core.model.ReadingProgressMath

@Immutable
data class EditionEditorSubmission(
  val publisherOrNote: String?,
  val publicationYear: Int?,
  val totalPages: Int?,
  val isbn: String?,
  val language: String?,
  val format: EditionFormat?,
  val translator: String?,
  val isPrimary: Boolean,
  val pickedCoverUri: Uri?,
  val removeCover: Boolean,
  val initialCopyMediaType: CopyMediaType,
  val initialCopyLabel: String?,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditionEditorSheet(
  workTitle: String,
  workAuthor: String,
  initialEdition: Edition?,
  onDismiss: () -> Unit,
  onSave: (EditionEditorSubmission) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val isEditing = initialEdition != null

  var publisherText by remember(initialEdition) { mutableStateOf(initialEdition?.publisherOrNote.orEmpty()) }
  var yearText by remember(initialEdition) { mutableStateOf(initialEdition?.publicationYear?.toString().orEmpty()) }
  var pagesText by remember(initialEdition) { mutableStateOf(initialEdition?.totalPages?.toString().orEmpty()) }
  var isbnText by remember(initialEdition) { mutableStateOf(initialEdition?.isbn.orEmpty()) }
  var languageText by remember(initialEdition) { mutableStateOf(initialEdition?.language.orEmpty()) }
  var selectedFormat by remember(initialEdition) { mutableStateOf(initialEdition?.format) }
  var translatorText by remember(initialEdition) { mutableStateOf(initialEdition?.translator.orEmpty()) }
  var isPrimary by remember(initialEdition) { mutableStateOf(initialEdition?.isPrimary == true) }
  var pickedCoverUri by remember(initialEdition) { mutableStateOf<Uri?>(null) }
  var removeExistingCover by remember(initialEdition) { mutableStateOf(false) }
  var coverErrorRes by remember { mutableStateOf<Int?>(null) }

  var initialCopyMedia by remember { mutableStateOf(CopyMediaType.PHYSICAL) }
  var initialCopyLabel by remember { mutableStateOf("") }

  var validationErrorRes by remember { mutableStateOf<Int?>(null) }
  var pagesError by remember { mutableStateOf(false) }
  var yearError by remember { mutableStateOf(false) }
  var isbnError by remember { mutableStateOf(false) }

  val previewCoverUri =
    when {
      pickedCoverUri != null -> pickedCoverUri.toString()
      removeExistingCover -> null
      else -> initialEdition?.coverUri
    }

  val coverPickerState =
    rememberCoverSourcePickerState(
      hasCover = previewCoverUri != null,
      onUriPicked = { uri ->
        pickedCoverUri = uri
        removeExistingCover = false
        coverErrorRes = null
      },
      onRemoveCover = {
        pickedCoverUri = null
        removeExistingCover = true
        coverErrorRes = null
      },
      onPickerUnavailable = {
        coverErrorRes = R.string.cover_picker_unavailable
      },
    )

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
    properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm)
          .testTag("edition_editor_sheet"),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      Text(
        text = stringResource(if (isEditing) R.string.edition_edit_title else R.string.edition_add_title),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BookCoverArtwork(
          title = workTitle,
          author = workAuthor,
          isFavorite = false,
          coverUri = previewCoverUri,
          compact = previewCoverUri == null,
          elevated = true,
          onImageLoadError = {
            pickedCoverUri = null
            coverErrorRes = R.string.cover_import_error
          },
          modifier = Modifier.width(64.dp),
        )
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        ) {
          OutlinedButton(
            onClick = { coverPickerState.openChooser() },
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.xs),
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("edition_pick_cover_button"),
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
                  if (previewCoverUri == null) R.string.cover_pick_photo
                  else R.string.cover_change_photo
                ),
              style = MaterialTheme.typography.labelLarge,
            )
          }
          if (previewCoverUri != null) {
            TextButton(
              onClick = {
                pickedCoverUri = null
                removeExistingCover = true
                coverErrorRes = null
              },
              contentPadding = PaddingValues(horizontal = DipeSpacing.xs, vertical = 2.dp),
              modifier = Modifier.testTag("edition_remove_cover_button"),
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
          coverErrorRes?.let { errorRes ->
            Text(
              text = stringResource(errorRes),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag("edition_cover_error"),
            )
          }
        }
      }

      OutlinedTextField(
        value = publisherText,
        onValueChange = { publisherText = it },
        label = { Text(stringResource(R.string.book_field_edition_note)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("edition_publisher_input"),
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
          modifier = Modifier.weight(1f).testTag("edition_pages_input"),
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
          modifier = Modifier.weight(1f).testTag("edition_year_input"),
        )
      }

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
          modifier = Modifier.weight(1f).testTag("edition_isbn_input"),
        )
        OutlinedTextField(
          value = languageText,
          onValueChange = { languageText = it },
          label = { Text(stringResource(R.string.book_field_language)) },
          singleLine = true,
          shape = MaterialTheme.shapes.medium,
          colors = textFieldColors,
          modifier = Modifier.weight(1f).testTag("edition_language_input"),
        )
      }

      OutlinedTextField(
        value = translatorText,
        onValueChange = { translatorText = it },
        label = { Text(stringResource(R.string.book_field_translator)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("edition_translator_input"),
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
              modifier = Modifier.testTag("edition_format_${format.name.lowercase()}"),
            )
          }
        }
      }

      if (!isEditing) {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
          Text(
            text = stringResource(R.string.book_field_copy_type),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
            CopyMediaType.entries.forEach { mediaType ->
              val selected = initialCopyMedia == mediaType
              FilterChip(
                selected = selected,
                onClick = { initialCopyMedia = mediaType },
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
                modifier = Modifier.testTag("edition_copy_media_${mediaType.name.lowercase()}"),
              )
            }
          }
          OutlinedTextField(
            value = initialCopyLabel,
            onValueChange = { initialCopyLabel = it },
            label = { Text(stringResource(R.string.book_field_copy_label)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth().testTag("edition_copy_label_input"),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.edition_set_primary_toggle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
          checked = isPrimary,
          onCheckedChange = { isPrimary = it },
          modifier = Modifier.testTag("edition_primary_switch"),
        )
      }

      validationErrorRes?.let { errorRes ->
        Text(
          text = stringResource(errorRes),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag("edition_validation_error"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("edition_cancel_button"),
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
              pagesText.isNotBlank() && parsedPages == null -> {
                pagesError = true
                validationErrorRes = R.string.book_validation_pages
              }
              yearText.isNotBlank() && parsedYear == null -> {
                yearError = true
                validationErrorRes = R.string.book_validation_year
              }
              !isbnValid -> {
                isbnError = true
                validationErrorRes = R.string.book_validation_isbn
              }
              else -> {
                onSave(
                  EditionEditorSubmission(
                    publisherOrNote = publisherText.trim().takeIf { it.isNotEmpty() },
                    publicationYear = parsedYear,
                    totalPages = parsedPages,
                    isbn = ReadingProgressMath.normalizeIsbn(isbnText),
                    language = languageText.trim().takeIf { it.isNotEmpty() },
                    format = selectedFormat,
                    translator = translatorText.trim().takeIf { it.isNotEmpty() },
                    isPrimary = isPrimary,
                    pickedCoverUri = pickedCoverUri,
                    removeCover = removeExistingCover,
                    initialCopyMediaType = initialCopyMedia,
                    initialCopyLabel = initialCopyLabel.trim().takeIf { it.isNotEmpty() },
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
          modifier = Modifier.minimumInteractiveComponentSize().testTag("edition_save_button"),
        ) {
          Text(stringResource(R.string.book_save_action))
        }
      }

      Spacer(modifier = Modifier.height(DipeSpacing.lg))
    }
  }
}
