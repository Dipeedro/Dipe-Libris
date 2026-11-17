package com.example.core.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.AppLanguage
import com.example.core.model.AppThemeMode
import com.example.core.model.LibraryViewMode
import com.example.core.model.TypographyPreset
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dipe_libris_preferences")

interface PreferencesRepository {
  val preferences: Flow<UserPreferences>

  suspend fun setLanguage(language: AppLanguage)

  suspend fun setThemeMode(themeMode: AppThemeMode)

  suspend fun setTypographyPreset(preset: TypographyPreset)

  suspend fun setDefaultLibraryViewMode(viewMode: LibraryViewMode)

  suspend fun setReduceMotion(reduceMotion: Boolean)

  suspend fun setReaderIdentity(name: String, bio: String)
}

class DataStorePreferencesRepository(private val context: Context) : PreferencesRepository {

  private object Keys {
    val LANGUAGE = stringPreferencesKey("language_tag")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val TYPOGRAPHY_PRESET = stringPreferencesKey("typography_preset")
    val DEFAULT_LIBRARY_VIEW = stringPreferencesKey("default_library_view")
    val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    val READER_NAME = stringPreferencesKey("reader_name")
    val READER_BIO = stringPreferencesKey("reader_bio")
  }

  override val preferences: Flow<UserPreferences> =
    context.dataStore.data
      .catch { exception ->
        if (exception is IOException) {
          emit(emptyPreferences())
        } else {
          throw exception
        }
      }
      .map { prefs ->
        UserPreferences(
          language = AppLanguage.fromTag(prefs[Keys.LANGUAGE]),
          themeMode = AppThemeMode.fromName(prefs[Keys.THEME_MODE]),
          typographyPreset = TypographyPreset.fromName(prefs[Keys.TYPOGRAPHY_PRESET]),
          defaultLibraryViewMode = LibraryViewMode.fromName(prefs[Keys.DEFAULT_LIBRARY_VIEW]),
          reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
          readerName = prefs[Keys.READER_NAME] ?: "",
          readerBio = prefs[Keys.READER_BIO] ?: "",
        )
      }

  override suspend fun setLanguage(language: AppLanguage) {
    context.dataStore.edit { it[Keys.LANGUAGE] = language.tag }
  }

  override suspend fun setThemeMode(themeMode: AppThemeMode) {
    context.dataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
  }

  override suspend fun setTypographyPreset(preset: TypographyPreset) {
    context.dataStore.edit { it[Keys.TYPOGRAPHY_PRESET] = preset.name }
  }

  override suspend fun setDefaultLibraryViewMode(viewMode: LibraryViewMode) {
    context.dataStore.edit { it[Keys.DEFAULT_LIBRARY_VIEW] = viewMode.name }
  }

  override suspend fun setReduceMotion(reduceMotion: Boolean) {
    context.dataStore.edit { it[Keys.REDUCE_MOTION] = reduceMotion }
  }

  override suspend fun setReaderIdentity(name: String, bio: String) {
    context.dataStore.edit {
      it[Keys.READER_NAME] = name.trim()
      it[Keys.READER_BIO] = bio.trim()
    }
  }
}
