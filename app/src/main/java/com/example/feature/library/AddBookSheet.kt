package com.example.feature.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.copyMediaTypeLabel
import com.example.core.designsystem.components.editionFormatLabel
import com.example.core.designsystem.components.readingStatusLabel
import com.example.core.designsystem.components.rememberCoverSourcePickerState
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddBookSheet(
  onDismiss: () -> Unit,
  onSave: (AddBookSubmission) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var author by remember { mutableStateOf("") }
  var pagesText by remember { mutableStateOf("") }
  var yearText by remember { mutableStateOf("") }
  var editionNote by remember { mutableStateOf("") }
  var selectedStatus by remember { mutableStateOf(ReadingStatus.UNREAD) }
  var pickedCoverUri by remember { mutableStateOf<Uri?>(null) }
  var coverErrorRes by remember { mutableStateOf<Int?>(null) }

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
  var showDiscardConfirm by remember { mutableStateOf(false) }

  val hasUnsavedChanges =
    title.isNotBlank() ||
      author.isNotBlank() ||
      pagesText.isNotBlank() ||
      yearText.isNotBlank() ||
      editionNote.isNotBlank() ||
      isbnText.isNotBlank() ||
      languageText.isNotBlank() ||
      translatorText.isNotBlank() ||
      copyLabelText.isNotBlank() ||
      copyLocationText.isNotBlank() ||
      pickedCoverUri != null ||
      selectedStatus != ReadingStatus.UNREAD ||
      selectedFormat != null

  val requestSafeClose = {
    if (hasUnsavedChanges) {
      showDiscardConfirm = true
    } else {
      onDismiss()
    }
  }

  // Hosted inside the main window so Android natively routes Back to the IME first
  // while the keyboard is open; once closed, Back guards unsaved input.
  BackHandler(onBack = requestSafeClose)

  val coverPickerState =
    rememberCoverSourcePickerState(
      hasCover = pickedCoverUri != null,
      onUriPicked = { uri ->
        pickedCoverUri = uri
        coverErrorRes = null
      },
      onRemoveCover = {
        pickedCoverUri = null
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

  Surface(
    modifier = Modifier.fillMaxSize().testTag("add_book_screen"),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
          )
          .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
      // Whisper-quiet top bar: close action only, keeping the hero cover focal
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = DipeSpacing.sm, vertical = DipeSpacing.xxs),
      ) {
        IconButton(
          onClick = requestSafeClose,
          modifier =
            Modifier.align(Alignment.CenterEnd)
              .minimumInteractiveComponentSize()
              .testTag("add_book_close_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.cd_close),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Scrollable editorial catalog form
      Column(
        modifier =
          Modifier.weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DipeSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
      ) {
        // Centered hero book cover with discrete photo badge
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        ) {
          Box(contentAlignment = Alignment.BottomEnd) {
            BookCoverArtwork(
              title = title.ifBlank { stringResource(R.string.app_name) },
              author = author.ifBlank { stringResource(R.string.profile_default_name) },
              isFavorite = false,
              coverUri = pickedCoverUri?.toString(),
              compact = title.isBlank() && pickedCoverUri == null,
              elevated = true,
              onImageLoadError = {
                pickedCoverUri = null
                coverErrorRes = R.string.cover_import_error
              },
              modifier =
                Modifier.width(136.dp)
                  .clip(MaterialTheme.shapes.small)
                  .clickable(onClick = { coverPickerState.openChooser() })
                  .testTag("add_book_pick_cover_button"),
            )

            Surface(
              onClick = { coverPickerState.openChooser() },
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surfaceContainerLowest,
              shadowElevation = 4.dp,
              border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier =
                Modifier.offset(x = 8.dp, y = 8.dp)
                  .minimumInteractiveComponentSize()
                  .size(38.dp)
                  .testTag("add_book_cover_badge"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector =
                    if (pickedCoverUri == null) Icons.Outlined.AddPhotoAlternate
                    else Icons.Outlined.Edit,
                  contentDescription = stringResource(R.string.cd_cover_badge_action),
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }

          if (pickedCoverUri != null) {
            TextButton(
              onClick = {
                pickedCoverUri = null
                coverErrorRes = null
              },
              contentPadding = PaddingValues(horizontal = DipeSpacing.sm, vertical = 2.dp),
              modifier = Modifier.testTag("add_book_remove_cover_button"),
            ) {
              Text(
                text = stringResource(R.string.cover_remove_photo),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          coverErrorRes?.let { errorRes ->
            Text(
              text = stringResource(errorRes),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth().testTag("add_book_cover_error"),
            )
          }
        }

        Spacer(modifier = Modifier.height(DipeSpacing.xxs))

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
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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
            keyboardOptions =
              KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
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
            keyboardOptions =
              KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f).testTag("add_book_year_input"),
          )
        }

        OutlinedTextField(
          value = editionNote,
          onValueChange = { editionNote = it },
          label = { Text(stringResource(R.string.book_field_edition_note)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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
                    modifier =
                      Modifier.testTag("add_book_copy_media_${mediaType.name.lowercase()}"),
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

        Spacer(modifier = Modifier.height(DipeSpacing.lg))
      }

      // Pinned bottom action bar — always visible above keyboard or navigation bar
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          HairlineDivider()
          validationErrorRes?.let { errorRes ->
            Text(
              text = stringResource(errorRes),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.xs)
                  .testTag("add_book_validation_error"),
            )
          }
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TextButton(
              onClick = onDismiss,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("add_book_cancel_button"),
            ) {
              Text(stringResource(R.string.book_cancel_action))
            }
            Spacer(modifier = Modifier.width(DipeSpacing.sm))
            Button(
              onClick = {
                val parsedPages =
                  if (pagesText.isBlank()) null
                  else pagesText.toIntOrNull()?.takeIf { it in 1..50_000 }
                val parsedYear =
                  if (yearText.isBlank()) null
                  else yearText.toIntOrNull()?.takeIf { it in 1..2100 }
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
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("add_book_save_button"),
            ) {
              Text(stringResource(R.string.book_save_action))
            }
          }
        }
      }
    }
  }

  if (showDiscardConfirm) {
    AlertDialog(
      onDismissRequest = { showDiscardConfirm = false },
      shape = MaterialTheme.shapes.large,
      containerColor = MaterialTheme.colorScheme.surface,
      title = {
        Text(
          text = stringResource(R.string.book_discard_title),
          style = MaterialTheme.typography.headlineSmall,
        )
      },
      text = {
        Text(
          text = stringResource(R.string.book_discard_message),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showDiscardConfirm = false
            onDismiss()
          },
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("add_book_discard_confirm"),
        ) {
          Text(
            text = stringResource(R.string.book_discard_action),
            color = MaterialTheme.colorScheme.error,
          )
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showDiscardConfirm = false },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("add_book_discard_keep"),
        ) {
          Text(stringResource(R.string.book_keep_editing_action))
        }
      },
    )
  }
}
