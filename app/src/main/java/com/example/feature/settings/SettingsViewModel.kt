package com.example.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.local.preferences.UserPreferences
import com.example.core.model.AppLanguage
import com.example.core.model.AppThemeMode
import com.example.core.model.LibraryViewMode
import com.example.core.model.TypographyPreset
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
  private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

  val preferences: StateFlow<UserPreferences> =
    preferencesRepository.preferences.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = UserPreferences(),
    )

  fun setLanguage(language: AppLanguage) {
    viewModelScope.launch { preferencesRepository.setLanguage(language) }
  }

  fun setThemeMode(themeMode: AppThemeMode) {
    viewModelScope.launch { preferencesRepository.setThemeMode(themeMode) }
  }

  fun setTypographyPreset(preset: TypographyPreset) {
    viewModelScope.launch { preferencesRepository.setTypographyPreset(preset) }
  }

  fun setDefaultLibraryViewMode(viewMode: LibraryViewMode) {
    viewModelScope.launch { preferencesRepository.setDefaultLibraryViewMode(viewMode) }
  }

  fun setReduceMotion(reduceMotion: Boolean) {
    viewModelScope.launch { preferencesRepository.setReduceMotion(reduceMotion) }
  }
}
