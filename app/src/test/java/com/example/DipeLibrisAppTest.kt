package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.repository.RoomDiaryRepository
import com.example.core.data.repository.RoomLibraryRepository
import com.example.core.data.repository.RoomStatisticsRepository
import com.example.core.model.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DipeLibrisAppTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun navigateBetweenAllMainAreasAndSettings() {
    composeTestRule.waitForIdle()

    // Home is initial destination without redundant settings/shortcut buttons
    composeTestRule.onNodeWithTag("home_lazy_column").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_settings_button").assertDoesNotExist()

    // Navigate to Library
    composeTestRule.onNodeWithTag("nav_tab_library").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("library_add_book_button").assertIsDisplayed()

    // Navigate to Diary
    composeTestRule.onNodeWithTag("nav_tab_diary").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("diary_new_entry_button").assertIsDisplayed()

    // Navigate to Statistics
    composeTestRule.onNodeWithTag("nav_tab_statistics").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("0", substring = false)

    // Navigate to Profile -> Settings -> Switch to Portuguese (Brazil)
    composeTestRule.onNodeWithTag("nav_tab_profile").performClick()
    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithTag("profile_lazy_column")
      .performScrollToNode(hasTestTag("profile_settings_button"))
    composeTestRule.onNodeWithTag("profile_settings_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_language_portuguese_br").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Configurações")).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("Configurações").assertIsDisplayed()

    // Switch back to English
    composeTestRule.onNodeWithTag("settings_language_english").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Settings")).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("Settings").assertIsDisplayed()

    // Back to Profile
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithTag("profile_lazy_column")
      .performScrollToNode(hasTestTag("profile_edit_button"))
    composeTestRule.onNodeWithTag("profile_edit_button").assertIsDisplayed()
  }

  @Test
  fun catalogBookAndVerifyRoomPersistence() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db =
      Room.inMemoryDatabaseBuilder(context, DipeLibrisDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    runBlocking {
      val libraryRepo = RoomLibraryRepository(db.bookDao(), db.shelfDao())
      val diaryRepo = RoomDiaryRepository(db.diaryDao())
      val statsRepo = RoomStatisticsRepository(db.bookDao(), db.diaryDao())

      val workId =
        libraryRepo.addBook(
          title = "Grande Sertão: Veredas",
          author = "João Guimarães Rosa",
          totalPages = 608,
          publicationYear = 1956,
          editionNote = "Companhia das Letras",
          status = ReadingStatus.READING,
        )
      diaryRepo.addEntry("Travessia.", workId)

      val books = libraryRepo.observeAllBooks().first()
      assertEquals(1, books.size)
      assertEquals("Grande Sertão: Veredas", books.first().title)
      assertEquals(608, books.first().totalPages)

      val snapshot = statsRepo.observeArchiveSnapshot().first()
      assertEquals(1, snapshot.totalWorks)
      assertEquals(1, snapshot.readingCount)
      assertEquals(1, snapshot.diaryEntriesCount)
    }
    db.close()
  }

  @Test
  fun addBookThroughUiSheetDisplaysInLibrary() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_tab_library").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("library_add_book_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("add_book_title_input").performTextInput("Dom Casmurro")
    composeTestRule.onNodeWithTag("add_book_author_input").performTextInput("Machado de Assis")
    composeTestRule.onNodeWithTag("add_book_save_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Dom Casmurro").assertIsDisplayed()
  }
}
