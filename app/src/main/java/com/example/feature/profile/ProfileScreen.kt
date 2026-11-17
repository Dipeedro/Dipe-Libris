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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.EditorialPageHeader
import com.example.core.designsystem.components.EditorialPlannedBlock
import com.example.core.designsystem.components.EditorialSectionHeader

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
  val displayName =
    uiState.preferences.readerName.ifBlank { stringResource(R.string.profile_default_name) }
  val displayBio =
    uiState.preferences.readerBio.ifBlank { stringResource(R.string.profile_default_bio) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 860.dp).fillMaxSize(),
      contentPadding =
        PaddingValues(
          horizontal = if (widthClass == WindowWidthClass.COMPACT) DipeSpacing.md else DipeSpacing.xl,
          vertical = DipeSpacing.lg,
        ),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xl),
    ) {
      item {
        EditorialPageHeader(
          kicker = stringResource(R.string.home_editorial_kicker),
          title = stringResource(R.string.profile_title),
          subtitle = stringResource(R.string.profile_subtitle),
          trailingContent = {
            IconButton(
              onClick = onOpenSettings,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("profile_settings_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.cd_open_settings),
              )
            }
          },
        )
      }

      // Editorial Banner + Avatar + Personal Identity
      item {
        Column(modifier = Modifier.fillMaxWidth()) {
          // Prepared Banner Space (clean monochrome architectural canvas)
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(92.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                  0.5.dp,
                  MaterialTheme.colorScheme.outlineVariant,
                  MaterialTheme.shapes.small,
                ),
            contentAlignment = Alignment.BottomEnd,
          ) {
            Text(
              text = stringResource(R.string.brand_wordmark),
              style = LocalDipeTypography.current.editorialKicker,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
              modifier = Modifier.padding(DipeSpacing.md),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DipeSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
          ) {
            // Prepared Avatar Frame overlapping banner asymmetrically
            Box(
              modifier =
                Modifier.offset(y = (-24).dp)
                  .size(64.dp)
                  .clip(CircleShape)
                  .background(brand.badgeContainer)
                  .border(1.5.dp, brand.avatarRing, CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.PersonOutline,
                contentDescription = null,
                tint = brand.badgeContent,
                modifier = Modifier.size(28.dp),
              )
            }

            OutlinedButton(
              onClick = onEditIdentity,
              shape = MaterialTheme.shapes.small,
              border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("profile_edit_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(DipeSpacing.xs))
              Text(stringResource(R.string.profile_edit_identity))
            }
          }

          Column(
            modifier = Modifier.padding(top = 0.dp),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
          ) {
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
            Spacer(modifier = Modifier.height(DipeSpacing.xs))
            Text(
              text = stringResource(R.string.profile_avatar_banner_note),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
          }
        }
      }

      // Favorite books
      item {
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
              items(uiState.favoriteBooks, key = { it.id }) { book ->
                Column(
                  modifier = Modifier.width(116.dp).clickable { onOpenBook(book.id) },
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                ) {
                  BookCoverArtwork(
                    title = book.title,
                    author = book.author,
                    isFavorite = book.isFavorite,
                    compact = true,
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

      // Favorite authors
      item {
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
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  shape = MaterialTheme.shapes.small,
                  border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                  Text(
                    text = author,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = DipeSpacing.md, vertical = DipeSpacing.sm),
                  )
                }
              }
            }
          }
        }
      }

      // Featured shelves prepared
      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.profile_section_shelves))
          EditorialPlannedBlock(
            title = stringResource(R.string.profile_section_shelves),
            description = stringResource(R.string.profile_empty_shelves),
            icon = Icons.Outlined.BookmarkBorder,
          )
        }
      }

      // Visual preferences entry
      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.profile_section_visual))
          Surface(
            modifier =
              Modifier.fillMaxWidth()
                .clickable(onClick = onOpenSettings)
                .minimumInteractiveComponentSize()
                .testTag("profile_visual_preferences_row"),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Row(
              modifier = Modifier.padding(DipeSpacing.md),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
                modifier = Modifier.weight(1f),
              ) {
                Icon(imageVector = Icons.Outlined.Tune, contentDescription = null)
                Text(
                  text = stringResource(R.string.profile_open_settings),
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
              )
            }
          }
        }
      }
    }
  }
}
