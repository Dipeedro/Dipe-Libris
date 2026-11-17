package com.example.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.components.HairlineDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileSheet(
  initialName: String,
  initialBio: String,
  onDismiss: () -> Unit,
  onSave: (name: String, bio: String) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var name by remember { mutableStateOf(initialName) }
  var bio by remember { mutableStateOf(initialBio) }

  val textFieldColors =
    OutlinedTextFieldDefaults.colors(
      unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
      focusedBorderColor = MaterialTheme.colorScheme.primary,
    )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth().padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
        Text(
          text = stringResource(R.string.profile_edit_sheet_title),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        HairlineDivider()
      }

      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.profile_field_name)) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
      )
      OutlinedTextField(
        value = bio,
        onValueChange = { bio = it },
        label = { Text(stringResource(R.string.profile_field_bio)) },
        minLines = 3,
        maxLines = 5,
        shape = MaterialTheme.shapes.small,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("profile_bio_input"),
      )

      HairlineDivider()

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Text(stringResource(R.string.book_cancel_action))
        }
        Button(
          onClick = { onSave(name, bio) },
          shape = CircleShape,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("profile_save_button"),
        ) {
          Text(stringResource(R.string.profile_save_identity))
        }
      }
      Spacer(modifier = Modifier.height(DipeSpacing.lg))
    }
  }
}
