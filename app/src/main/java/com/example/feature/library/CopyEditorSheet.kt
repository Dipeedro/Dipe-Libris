package com.example.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import com.example.core.designsystem.components.EditorialModalBottomSheet
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.components.copyConditionLabel
import com.example.core.designsystem.components.copyMediaTypeLabel
import com.example.core.designsystem.components.copyOriginLabel
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.core.model.BookCopy
import com.example.core.model.CopyCondition
import com.example.core.model.CopyMediaType
import com.example.core.model.CopyOrigin

@Immutable
data class CopyEditorSubmission(
  val mediaType: CopyMediaType,
  val label: String?,
  val physicalFormat: String?,
  val locationOrPlatform: String?,
  val personalNote: String?,
  val setAsActiveReadingCopy: Boolean,
  val condition: CopyCondition? = null,
  val acquisitionOrigin: CopyOrigin? = null,
  val customCopyCode: String? = null,
  val pricePaidMinorUnits: Long? = null,
  val currencyCode: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CopyEditorSheet(
  initialCopy: BookCopy?,
  hasActiveJourney: Boolean,
  isCurrentlyActiveCopy: Boolean,
  onDismiss: () -> Unit,
  onSave: (CopyEditorSubmission) -> Unit,
  onDeleteCopy: (() -> Unit)? = null,
) {
  val isEditing = initialCopy != null

  var mediaType by remember(initialCopy) {
    mutableStateOf(initialCopy?.mediaType ?: CopyMediaType.PHYSICAL)
  }
  var label by remember(initialCopy) { mutableStateOf(initialCopy?.label.orEmpty()) }
  var physicalFormat by remember(initialCopy) { mutableStateOf(initialCopy?.physicalFormat.orEmpty()) }
  var location by remember(initialCopy) { mutableStateOf(initialCopy?.locationOrPlatform.orEmpty()) }
  var personalNote by remember(initialCopy) { mutableStateOf(initialCopy?.personalNote.orEmpty()) }
  var setAsActive by remember(initialCopy, isCurrentlyActiveCopy) {
    mutableStateOf(isCurrentlyActiveCopy)
  }
  var showCollectorExtras by remember(initialCopy) {
    mutableStateOf(
      initialCopy?.condition != null ||
        initialCopy?.acquisitionOrigin != null ||
        initialCopy?.customCopyCode != null ||
        initialCopy?.pricePaidMinorUnits != null
    )
  }
  var condition by remember(initialCopy) { mutableStateOf(initialCopy?.condition) }
  var acquisitionOrigin by remember(initialCopy) { mutableStateOf(initialCopy?.acquisitionOrigin) }
  var customCopyCode by remember(initialCopy) { mutableStateOf(initialCopy?.customCopyCode.orEmpty()) }
  var priceText by remember(initialCopy) {
    mutableStateOf(
      initialCopy?.pricePaidMinorUnits?.let { "%.2f".format(it / 100.0) }.orEmpty()
    )
  }
  var showDeleteConfirmation by remember { mutableStateOf(false) }

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

  EditorialModalBottomSheet(
    onDismissRequest = onDismiss,
    dismissOnBackPress = false,
  ) { requestDismiss ->
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm)
          .testTag("copy_editor_sheet"),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      Text(
        text = stringResource(if (isEditing) R.string.copy_edit_title else R.string.copy_add_title),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
        Text(
          text = stringResource(R.string.book_field_copy_type),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
          CopyMediaType.entries.forEach { option ->
            val selected = mediaType == option
            FilterChip(
              selected = selected,
              onClick = { mediaType = option },
              label = { Text(copyMediaTypeLabel(option)) },
              shape = CircleShape,
              colors = chipColors,
              border =
                FilterChipDefaults.filterChipBorder(
                  enabled = true,
                  selected = selected,
                  borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                  borderWidth = 0.5.dp,
                ),
              modifier = Modifier.testTag("copy_media_${option.name.lowercase()}"),
            )
          }
        }
      }

      OutlinedTextField(
        value = label,
        onValueChange = { label = it },
        label = { Text(stringResource(R.string.book_field_copy_label)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("copy_label_input"),
      )

      OutlinedTextField(
        value = location,
        onValueChange = { location = it },
        label = { Text(stringResource(R.string.book_field_copy_location)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("copy_location_input"),
      )

      OutlinedTextField(
        value = physicalFormat,
        onValueChange = { physicalFormat = it },
        label = { Text(stringResource(R.string.book_field_format)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("copy_format_input"),
      )

      OutlinedTextField(
        value = personalNote,
        onValueChange = { personalNote = it },
        label = { Text(stringResource(R.string.copy_field_note)) },
        maxLines = 3,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("copy_note_input"),
      )

      TextButton(
        onClick = { showCollectorExtras = !showCollectorExtras },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("copy_collector_toggle"),
      ) {
        Text(stringResource(R.string.review_copy_collector_toggle))
      }

      if (showCollectorExtras) {
        Text(
          text = stringResource(R.string.review_copy_condition),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.FlowRow(
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        ) {
          CopyCondition.entries.forEach { cond ->
            val selected = condition == cond
            FilterChip(
              selected = selected,
              onClick = { condition = if (selected) null else cond },
              label = { Text(copyConditionLabel(cond)) },
              shape = CircleShape,
              colors = chipColors,
            )
          }
        }
        Text(
          text = stringResource(R.string.review_copy_origin),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.FlowRow(
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        ) {
          CopyOrigin.entries.forEach { orig ->
            val selected = acquisitionOrigin == orig
            FilterChip(
              selected = selected,
              onClick = { acquisitionOrigin = if (selected) null else orig },
              label = { Text(copyOriginLabel(orig)) },
              shape = CircleShape,
              colors = chipColors,
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
        ) {
          OutlinedTextField(
            value = priceText,
            onValueChange = { priceText = it },
            label = { Text(stringResource(R.string.review_copy_price)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = textFieldColors,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = customCopyCode,
            onValueChange = { customCopyCode = it },
            label = { Text(stringResource(R.string.review_copy_code)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = textFieldColors,
            modifier = Modifier.weight(1f),
          )
        }
      }

      if (hasActiveJourney && !isCurrentlyActiveCopy) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.copy_field_active_toggle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
          Switch(
            checked = setAsActive,
            onCheckedChange = { setAsActive = it },
            modifier = Modifier.testTag("copy_active_switch"),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (isEditing && onDeleteCopy != null) {
          TextButton(
            onClick = { showDeleteConfirmation = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("copy_delete_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.DeleteOutline,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.copy_delete_action),
              color = MaterialTheme.colorScheme.error,
            )
          }
        } else {
          Spacer(modifier = Modifier.width(1.dp))
        }

        Row(horizontalArrangement = Arrangement.End) {
          TextButton(
            onClick = requestDismiss,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("copy_cancel_button"),
          ) {
            Text(stringResource(R.string.book_cancel_action))
          }
          Spacer(modifier = Modifier.width(DipeSpacing.sm))
          Button(
            onClick = {
              val parsedPrice =
                priceText.trim().replace(",", ".").toDoubleOrNull()?.let { Math.round(it * 100.0) }
              onSave(
                CopyEditorSubmission(
                  mediaType = mediaType,
                  label = label.trim().takeIf { it.isNotEmpty() },
                  physicalFormat = physicalFormat.trim().takeIf { it.isNotEmpty() },
                  locationOrPlatform = location.trim().takeIf { it.isNotEmpty() },
                  personalNote = personalNote.trim().takeIf { it.isNotEmpty() },
                  setAsActiveReadingCopy = setAsActive,
                  condition = condition,
                  acquisitionOrigin = acquisitionOrigin,
                  customCopyCode = customCopyCode.trim().takeIf { it.isNotEmpty() },
                  pricePaidMinorUnits = parsedPrice,
                  currencyCode = if (parsedPrice != null) (initialCopy?.currencyCode ?: "BRL") else null,
                )
              )
            },
            shape = CircleShape,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
              ),
            contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("copy_save_button"),
          ) {
            Text(stringResource(R.string.book_save_action))
          }
        }
      }

      Spacer(modifier = Modifier.height(DipeSpacing.lg))
    }
  }

  if (showDeleteConfirmation && onDeleteCopy != null) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirmation = false },
      shape = MaterialTheme.shapes.large,
      containerColor = MaterialTheme.colorScheme.surface,
      title = {
        Text(
          text = stringResource(R.string.copy_delete_confirm_title),
          style = MaterialTheme.typography.headlineSmall,
        )
      },
      text = {
        Text(
          text = stringResource(R.string.copy_delete_confirm_message),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showDeleteConfirmation = false
            onDeleteCopy()
          },
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("copy_delete_confirm_button"),
        ) {
          Text(
            text = stringResource(R.string.book_delete_confirm_action),
            color = MaterialTheme.colorScheme.error,
          )
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showDeleteConfirmation = false },
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Text(stringResource(R.string.book_cancel_action))
        }
      },
    )
  }
}
