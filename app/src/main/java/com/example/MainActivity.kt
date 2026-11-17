package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.local.preferences.UserPreferences
import com.example.core.designsystem.DipeLibrisTheme
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.ui.dipeFactory
import com.example.navigation.DipeNavigationShell
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MainUiState {
  data object Loading : MainUiState

  data class Ready(val preferences: UserPreferences) : MainUiState
}

class MainViewModel(
  preferencesRepository: PreferencesRepository,
) : ViewModel() {
  val uiState: StateFlow<MainUiState> =
    preferencesRepository.preferences
      .map<UserPreferences, MainUiState> { MainUiState.Ready(it) }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MainUiState.Loading,
      )
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val app = application as DipeLibrisApplication
    val container = app.container

    setContent {
      val mainViewModel: MainViewModel =
        viewModel(factory = dipeFactory(container) { MainViewModel(it.preferencesRepository) })
      val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()

      when (val state = uiState) {
        MainUiState.Loading -> {
          DipeLibrisTheme {
            Box(
              modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            )
          }
        }
        is MainUiState.Ready -> {
          val prefs = state.preferences
          val baseContext = LocalContext.current
          val localizedContext =
            remember(baseContext, prefs.language) {
              LocaleManager.createLocalizedContext(baseContext, prefs.language)
            }
          val localizedConfig =
            remember(localizedContext) { localizedContext.resources.configuration }

          SideEffect { LocaleManager.applyDefaultLocale(prefs.language) }

          CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedConfig,
            LocalAppLanguage provides prefs.language,
          ) {
            DipeLibrisTheme(
              themeMode = prefs.themeMode,
              typographyPreset = prefs.typographyPreset,
              userReduceMotion = prefs.reduceMotion,
            ) {
              DipeNavigationShell(container = container)
            }
          }
        }
      }
    }
  }
}
