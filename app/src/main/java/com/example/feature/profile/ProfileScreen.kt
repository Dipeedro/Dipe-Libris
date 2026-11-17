package com.example.feature.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeBrandTokens
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.HairlineDivider

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
  uiState: ProfileUiState,
  widthClass: WindowWidthClass,
  onEditIdentity: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenBook: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val brand = LocalDipeBrandTokens.current
  val listState = rememberLazyListState()
  val horizontalPadding =
    if (widthClass == WindowWidthClass.COMPACT) DipeSpacing.md else DipeSpacing.xl
  val customName = uiState.preferences.readerName.trim()
  val displayName = customName.ifBlank { stringResource(R.string.profile_default_name) }
  val displayBio =
    uiState.preferences.readerBio.ifBlank { stringResource(R.string.profile_default_bio) }
  val initialLetter =
    remember(customName) { customName.firstOrNull()?.uppercase() }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      state = listState,
      modifier = Modifier.widthIn(max = 820.dp).fillMaxSize().testTag("profile_lazy_column"),
      contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = DipeSpacing.md),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xl),
    ) {
      item(key = "profile_identity", contentType = "identity_header") {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.size(58.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surfaceContainerLow)
                  .border(0.75.dp, brand.badgeBorder, CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              if (initialLetter != null) {
                Text(
                  text = initialLetter,
                  style = MaterialTheme.typography.headlineMedium,
                  color = MaterialTheme.colorScheme.onBackground,
                )
              } else {
                Icon(
                  imageVector = Icons.Outlined.PersonOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(26.dp),
                )
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
            ) {
              OutlinedButton(
                onClick = onEditIdentity,
                shape = CircleShape,
                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.xs),
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("profile_edit_button"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Edit,
                  contentDescription = null,
                  modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(DipeSpacing.xs))
                Text(stringResource(R.string.profile_edit_identity))
              }
              IconButton(
                onClick = onOpenSettings,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("profile_settings_button"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Settings,
                  contentDescription = stringResource(R.string.cd_open_settings),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
            Text(
              text = displayName,
              style = MaterialTheme.typography.headlineLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = displayBio,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          HairlineDivider()
        }
      }

      item(key = "profile_favorites", contentType = "favorite_books") {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.profile_section_favorites))
          if (uiState.favoriteBooks.isEmpty()) {
            Text(
              text = stringResource(R.string.profile_empty_favorites),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
              items(
                items = uiState.favoriteBooks,
                key = { it.id },
                contentType = { "favorite_book" },
              ) { book ->
                val onBookClick = remember(book.id, onOpenBook) { { onOpenBook(book.id) } }
                Column(
                  modifier = Modifier.width(114.dp).clickable(onClick = onBookClick),
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                ) {
                  BookCoverArtwork(
                    title = book.title,
                    author = book.author,
                    isFavorite = book.isFavorite,
                    compact = true,
                    elevated = true,
                    modifier = Modifier.fillMaxWidth(),
                  )
                  Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      }

      item(key = "profile_authors", contentType = "favorite_authors") {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.profile_section_authors))
          if (uiState.favoriteAuthors.isEmpty()) {
            Text(
              text = stringResource(R.string.profile_empty_authors),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
              verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            ) {
              uiState.favoriteAuthors.forEach { author ->
                Surface(
                  color = MaterialTheme.colorScheme.surface,
                  shape = CircleShape,
                  border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                  Text(
                    text = author,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                      Modifier.padding(horizontal = DipeSpacing.md, vertical = DipeSpacing.xs),
                  )
                }
              }
            }
          }
        }
      }

      item(key = "profile_visual_preferences", contentType = "settings_shortcut") {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
          EditorialSectionHeader(title = stringResource(R.string.profile_section_visual))
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clickable(onClick = onOpenSettings)
                .padding(vertical = DipeSpacing.sm)
                .testTag("profile_visual_preferences_row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = stringResource(R.string.profile_open_settings),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          HairlineDivider()
        }
      }
    }
  }
}
