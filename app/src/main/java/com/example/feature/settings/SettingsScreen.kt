package com.example.feature.settings

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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.core.data.local.preferences.UserPreferences
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.InkColorScheme
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.PaperColorScheme
import com.example.core.designsystem.SepiaColorScheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.EditorialSubscreenTopBar
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.displayFontFamilyFor
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
  val listState = rememberLazyListState()
  val horizontalPadding =
    if (widthClass == WindowWidthClass.COMPACT) DipeSpacing.md else DipeSpacing.xl

  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = MaterialTheme.colorScheme.primary,
      selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
      selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
      containerColor = MaterialTheme.colorScheme.surface,
      labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      state = listState,
      modifier = Modifier.widthIn(max = 820.dp).fillMaxSize().testTag("settings_lazy_column"),
      contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = DipeSpacing.md),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xl),
    ) {
      item(key = "settings_top_bar", contentType = "top_bar") {
        EditorialSubscreenTopBar(
          title = stringResource(R.string.settings_title),
          backButtonTag = "settings_back_button",
          onNavigateBack = onNavigateBack,
        )
      }

      // Language Selection
      item(key = "settings_language", contentType = "chip_section") {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.settings_section_language))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          ) {
            AppLanguage.entries.forEach { language ->
              val selected = preferences.language == language
              FilterChip(
                selected = selected,
                onClick = { onSelectLanguage(language) },
                shape = CircleShape,
                colors = chipColors,
                border =
                  FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    borderWidth = 0.75.dp,
                  ),
                leadingIcon =
                  if (selected) {
                    { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
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

      // Theme Selection with tactile color circle swatches
      item(key = "settings_theme", contentType = "theme_section") {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.settings_section_theme))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          ) {
            AppThemeMode.entries.forEach { mode ->
              val selected = preferences.themeMode == mode
              ThemeSwatchOption(
                mode = mode,
                label =
                  when (mode) {
                    AppThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                    AppThemeMode.PAPER -> stringResource(R.string.settings_theme_paper)
                    AppThemeMode.INK -> stringResource(R.string.settings_theme_ink)
                    AppThemeMode.SEPIA -> stringResource(R.string.settings_theme_sepia)
                  },
                selected = selected,
                onClick = { onSelectTheme(mode) },
                modifier =
                  Modifier.weight(1f)
                    .testTag("settings_theme_${mode.name.lowercase()}"),
              )
            }
          }
        }
      }

      // Typography Preset Selection with live serif "Aa" specimen
      item(key = "settings_typography", contentType = "typography_section") {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
          EditorialSectionHeader(title = stringResource(R.string.settings_section_typography))
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
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .defaultMinSize(minHeight = 60.dp)
                  .clickable { onSelectTypography(preset) }
                  .padding(vertical = DipeSpacing.sm)
                  .testTag("settings_typography_${preset.name.lowercase()}"),
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                      if (selected) MaterialTheme.colorScheme.secondaryContainer
                      else MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                    .border(
                      width = 0.75.dp,
                      color =
                        if (selected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.outlineVariant,
                      shape = MaterialTheme.shapes.small,
                    ),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "Aa",
                  fontFamily = displayFontFamilyFor(preset),
                  fontSize = 18.sp,
                  color =
                    if (selected) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurface,
                )
              }

              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs),
              ) {
                Text(
                  text = stringResource(titleRes),
                  style = MaterialTheme.typography.titleMedium,
                  color =
                    if (selected) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurface,
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
                  tint = MaterialTheme.colorScheme.secondary,
                )
              }
            }
            HairlineDivider()
          }
        }
      }

      // General Preferences
      item(key = "settings_general", contentType = "general_section") {
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
                shape = CircleShape,
                colors = chipColors,
                border =
                  FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    borderWidth = 0.75.dp,
                  ),
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
                .defaultMinSize(minHeight = 52.dp)
                .clickable { onToggleReduceMotion(!preferences.reduceMotion) }
                .padding(vertical = DipeSpacing.xs)
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

      // Application Info (Version 1.2)
      item(key = "settings_app_info", contentType = "app_info") {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
          EditorialSectionHeader(title = stringResource(R.string.settings_section_app_info))
          Text(
            text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
            style = LocalDipeTypography.current.monoMetadata,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("settings_app_version"),
          )
          Text(
            text = stringResource(R.string.settings_about_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun ThemeSwatchOption(
  mode: AppThemeMode,
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .defaultMinSize(minHeight = 48.dp)
        .clickable(onClick = onClick)
        .padding(vertical = DipeSpacing.xs),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
  ) {
    val ringColor =
      if (selected) MaterialTheme.colorScheme.secondary
      else MaterialTheme.colorScheme.outlineVariant
    val ringWidth = if (selected) 2.dp else 0.75.dp

    Box(
      modifier =
        Modifier.size(42.dp)
          .clip(CircleShape)
          .border(ringWidth, ringColor, CircleShape)
          .padding(3.dp)
          .clip(CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      when (mode) {
        AppThemeMode.PAPER -> {
          Box(modifier = Modifier.fillMaxSize().background(PaperColorScheme.background))
        }
        AppThemeMode.INK -> {
          Box(modifier = Modifier.fillMaxSize().background(InkColorScheme.background))
        }
        AppThemeMode.SEPIA -> {
          Box(modifier = Modifier.fillMaxSize().background(SepiaColorScheme.background))
        }
        AppThemeMode.SYSTEM -> {
          Row(modifier = Modifier.fillMaxSize()) {
            Box(
              modifier = Modifier.weight(1f).fillMaxHeight().background(PaperColorScheme.background)
            )
            Box(
              modifier = Modifier.weight(1f).fillMaxHeight().background(InkColorScheme.background)
            )
          }
        }
      }
    }

    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color =
        if (selected) MaterialTheme.colorScheme.onBackground
        else MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
    )
  }
}
