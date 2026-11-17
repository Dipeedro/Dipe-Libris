package com.example.core.data.local.preferences

import com.example.core.model.AppLanguage
import com.example.core.model.AppThemeMode
import com.example.core.model.LibraryViewMode
import com.example.core.model.TypographyPreset

data class UserPreferences(
  val language: AppLanguage = AppLanguage.fromTag(null),
  val themeMode: AppThemeMode = AppThemeMode.PAPER,
  val typographyPreset: TypographyPreset = TypographyPreset.CLASSIC_EDITORIAL,
  val defaultLibraryViewMode: LibraryViewMode = LibraryViewMode.GRID,
  val reduceMotion: Boolean = false,
  val readerName: String = "",
  val readerBio: String = "",
)
