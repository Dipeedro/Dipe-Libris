package com.example.feature.settings

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.core.data.local.preferences.UserPreferences
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.EditorialPlannedBlock
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.model.AppLanguage
import com.example.core.model.AppThemeMode
import com.example.core.model.LibraryViewMode
import com.example.core.model.TypographyPreset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  preferences: UserPreferences,
  widthClass: WindowWidthClass,
  onSelectLanguage: (AppLanguage) -> Unit,
  onSelectTheme: (AppThemeMode) -> Unit,
  onSelectTypography: (TypographyPreset) -> Unit,
  onSelectDefaultLibraryView: (LibraryViewMode) -> Unit,
  onToggleReduceMotion: (Boolean) -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 820.dp).fillMaxSize(),
      contentPadding =
        PaddingValues(
          horizontal = if (widthClass == WindowWidthClass.COMPACT) DipeSpacing.md else DipeSpacing.xl,
          vertical = DipeSpacing.lg,
        ),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xl),
    ) {
      item {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
              )
            }
            Spacer(modifier = Modifier.width(DipeSpacing.xs))
            Text(
              text = stringResource(R.string.settings_title),
              style = MaterialTheme.typography.displayMedium,
              color = MaterialTheme.colorScheme.onBackground,
            )
          }
          Spacer(modifier = Modifier.height(DipeSpacing.xs))
          Text(
            text = stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(DipeSpacing.md))
          HairlineDivider()
        }
      }

      // Language Selection
      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.settings_section_language))
          Text(
            text = stringResource(R.string.settings_language_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          ) {
            AppLanguage.entries.forEach { language ->
              val selected = preferences.language == language
              FilterChip(
                selected = selected,
                onClick = { onSelectLanguage(language) },
                leadingIcon =
                  if (selected) {
                    { Icon(Icons.Outlined.Check, contentDescription = null) }
                  } else null,
                label = {
                  Text(
                    when (language) {
                      AppLanguage.PORTUGUESE_BR -> stringResource(R.string.settings_lang_pt_br)
                      AppLanguage.ENGLISH -> stringResource(R.string.settings_lang_en)
                    }
                  )
                },
                modifier = Modifier.testTag("settings_language_${language.name.lowercase()}"),
              )
            }
          }
        }
      }

      // Theme Selection
      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.settings_section_theme))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          ) {
            AppThemeMode.entries.forEach { mode ->
              val selected = preferences.themeMode == mode
              FilterChip(
                selected = selected,
                onClick = { onSelectTheme(mode) },
                leadingIcon =
                  if (selected) {
                    { Icon(Icons.Outlined.Check, contentDescription = null) }
                  } else null,
                label = {
                  Text(
                    when (mode) {
                      AppThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                      AppThemeMode.PAPER -> stringResource(R.string.settings_theme_paper)
                      AppThemeMode.INK -> stringResource(R.string.settings_theme_ink)
                      AppThemeMode.SEPIA -> stringResource(R.string.settings_theme_sepia)
                    }
                  )
                },
                modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}"),
              )
            }
          }
        }
      }

      // Typography Preset Selection
      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.settings_section_typography))
          Text(
            text = stringResource(R.string.settings_typography_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
            TypographyPreset.entries.forEach { preset ->
              val selected = preferences.typographyPreset == preset
              val (titleRes, descRes) =
                when (preset) {
                  TypographyPreset.CLASSIC_EDITORIAL ->
                    R.string.settings_typo_classic to R.string.settings_typo_classic_desc
                  TypographyPreset.LITERARY_PRESS ->
                    R.string.settings_typo_literary to R.string.settings_typo_literary_desc
                  TypographyPreset.MODERN_ARCHIVE ->
                    R.string.settings_typo_modern to R.string.settings_typo_modern_desc
                }
              Surface(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable { onSelectTypography(preset) }
                    .minimumInteractiveComponentSize()
                    .testTag("settings_typography_${preset.name.lowercase()}"),
                color =
                  if (selected) MaterialTheme.colorScheme.surfaceContainer
                  else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.small,
                border =
                  BorderStroke(
                    width = if (selected) 1.dp else 0.5.dp,
                    color =
                      if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant,
                  ),
              ) {
                Row(
                  modifier = Modifier.padding(DipeSpacing.md),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs),
                  ) {
                    Text(
                      text = stringResource(titleRes),
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                      text = stringResource(descRes),
                      style = LocalDipeTypography.current.monoMetadata,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  if (selected) {
                    Icon(
                      imageVector = Icons.Outlined.Check,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // General Preferences
      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.settings_section_general))
          Text(
            text = stringResource(R.string.settings_default_library_view),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          ) {
            LibraryViewMode.entries.forEach { mode ->
              val selected = preferences.defaultLibraryViewMode == mode
              FilterChip(
                selected = selected,
                onClick = { onSelectDefaultLibraryView(mode) },
                label = {
                  Text(
                    when (mode) {
                      LibraryViewMode.GRID -> stringResource(R.string.library_view_grid)
                      LibraryViewMode.LIST -> stringResource(R.string.library_view_list)
                      LibraryViewMode.SHELF -> stringResource(R.string.library_view_shelf)
                      LibraryViewMode.GALLERY -> stringResource(R.string.library_view_gallery)
                    }
                  )
                },
                modifier = Modifier.testTag("settings_default_view_${mode.name.lowercase()}"),
              )
            }
          }

          HairlineDivider()

          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable { onToggleReduceMotion(!preferences.reduceMotion) }
                .padding(vertical = DipeSpacing.xs)
                .minimumInteractiveComponentSize()
                .testTag("settings_reduce_motion_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(
              modifier = Modifier.weight(1f).padding(end = DipeSpacing.md),
              verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs),
            ) {
              Text(
                text = stringResource(R.string.settings_reduce_motion),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = stringResource(R.string.settings_reduce_motion_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = preferences.reduceMotion,
              onCheckedChange = onToggleReduceMotion,
              modifier = Modifier.testTag("settings_reduce_motion_switch"),
            )
          }
        }
      }

      // About Dipe Libris
      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.settings_section_about))
          Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = stringResource(R.string.settings_about_phase),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.secondary,
            )
            Text(
              text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
              style = LocalDipeTypography.current.monoMetadata,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(DipeSpacing.xs))
            Text(
              text = stringResource(R.string.settings_about_privacy),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          EditorialPlannedBlock(
            title = stringResource(R.string.settings_future_modules_title),
            description = stringResource(R.string.settings_future_modules_body),
            icon = Icons.Outlined.Info,
          )
        }
      }
    }
  }
}
