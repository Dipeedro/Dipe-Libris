package com.example.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.local.preferences.UserPreferences
import com.example.core.data.repository.LibraryRepository
import com.example.core.model.CatalogBook
import com.example.core.model.Shelf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
  val preferences: UserPreferences = UserPreferences(),
  val favoriteBooks: List<CatalogBook> = emptyList(),
  val favoriteAuthors: List<String> = emptyList(),
  val featuredShelves: List<Shelf> = emptyList(),
)

class ProfileViewModel(
  private val preferencesRepository: PreferencesRepository,
  libraryRepository: LibraryRepository,
) : ViewModel() {

  val uiState: StateFlow<ProfileUiState> =
    combine(
      preferencesRepository.preferences,
      libraryRepository.observeFavoriteBooks(),
      libraryRepository.observeShelves(),
    ) { prefs, favorites, shelves ->
      ProfileUiState(
        preferences = prefs,
        favoriteBooks = favorites,
        favoriteAuthors = favorites.map { it.author }.distinct(),
        featuredShelves = shelves.filter { it.isFeatured },
      )
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(),
      )

  fun updateIdentity(name: String, bio: String, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      preferencesRepository.setReaderIdentity(name = name, bio = bio)
      onDone()
    }
  }
}
