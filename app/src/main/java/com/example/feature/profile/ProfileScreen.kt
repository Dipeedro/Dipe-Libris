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
import com.example.core.designsystem.components.EditorialMasthead
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.EditorialSurfaceCard

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
  val horizontalPadding = DipeSpacing.horizontalMargin(widthClass)
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
      contentPadding =
        PaddingValues(
          start = horizontalPadding,
          end = horizontalPadding,
          top = DipeSpacing.md,
          bottom = DipeSpacing.xxl,
        ),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xl),
    ) {
      item(key = "profile_masthead", contentType = "masthead") {
        EditorialMasthead(
          title = stringResource(R.string.nav_profile),
          trailingContent = {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              border =
                BorderStroke(
                  0.5.dp,
                  MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
            ) {
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
          },
        )
      }

      item(key = "profile_identity", contentType = "identity_header") {
        EditorialSurfaceCard(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          contentPadding = PaddingValues(DipeSpacing.lg),
        ) {
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
                  Modifier.size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(0.75.dp, brand.badgeBorder, CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                if (initialLetter != null) {
                  Text(
                    text = initialLetter,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                  )
                } else {
                  Icon(
                    imageVector = Icons.Outlined.PersonOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp),
                  )
                }
              }

              OutlinedButton(
                onClick = onEditIdentity,
                shape = CircleShape,
                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("profile_edit_button"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Edit,
                  contentDescription = null,
                  modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = stringResource(R.string.profile_edit_identity),
                  style = MaterialTheme.typography.labelLarge,
                )
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
          }
        }
      }

      item(key = "profile_favorites", contentType = "favorite_books") {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.profile_section_favorites))
          if (uiState.favoriteBooks.isEmpty()) {
            EditorialSurfaceCard(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
              contentPadding = PaddingValues(DipeSpacing.lg),
            ) {
              Text(
                text = stringResource(R.string.profile_empty_favorites),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
              items(
                items = uiState.favoriteBooks,
                key = { it.id },
                contentType = { "favorite_book" },
              ) { book ->
                val onBookClick = remember(book.id, onOpenBook) { { onOpenBook(book.id) } }
                Column(
                  modifier =
                    Modifier.width(118.dp)
                      .clip(MaterialTheme.shapes.medium)
                      .clickable(onClick = onBookClick)
                      .padding(DipeSpacing.xs),
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
                ) {
                  BookCoverArtwork(
                    title = book.title,
                    author = book.author,
                    isFavorite = book.isFavorite,
                    elevated = true,
                    modifier = Modifier.fillMaxWidth(),
                  )
                  Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
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
            EditorialSurfaceCard(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
              contentPadding = PaddingValues(DipeSpacing.lg),
            ) {
              Text(
                text = stringResource(R.string.profile_empty_authors),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
              verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            ) {
              uiState.favoriteAuthors.forEach { author ->
                Surface(
                  color = MaterialTheme.colorScheme.surfaceContainerLowest,
                  shape = CircleShape,
                  border =
                    BorderStroke(
                      0.5.dp,
                      MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    ),
                ) {
                  Text(
                    text = author,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                      Modifier.padding(horizontal = DipeSpacing.md, vertical = DipeSpacing.sm),
                  )
                }
              }
            }
          }
        }
      }

      item(key = "profile_visual_preferences", contentType = "settings_shortcut") {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.profile_section_visual))
          EditorialSurfaceCard(
            modifier =
              Modifier.defaultMinSize(minHeight = 64.dp)
                .testTag("profile_visual_preferences_row"),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = 14.dp),
            onClick = onOpenSettings,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
                modifier = Modifier.weight(1f),
              ) {
                Box(
                  modifier =
                    Modifier.size(38.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.secondaryContainer),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs)) {
                  Text(
                    text = stringResource(R.string.profile_open_settings),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = stringResource(R.string.profile_open_settings_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
