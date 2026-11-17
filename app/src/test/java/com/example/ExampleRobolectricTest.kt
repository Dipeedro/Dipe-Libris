package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.localization.LocaleManager
import com.example.core.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app_name remains Dipe Libris in both locales`() {
    val base = ApplicationProvider.getApplicationContext<Context>()
    val enContext = LocaleManager.createLocalizedContext(base, AppLanguage.ENGLISH)
    val ptContext = LocaleManager.createLocalizedContext(base, AppLanguage.PORTUGUESE_BR)

    assertEquals("Dipe Libris", enContext.getString(R.string.app_name))
    assertEquals("Dipe Libris", ptContext.getString(R.string.app_name))
    assertEquals("Library", enContext.getString(R.string.nav_library))
    assertEquals("Biblioteca", ptContext.getString(R.string.nav_library))
  }

  @Test
  fun `plurals render singular and plural cleanly in English and Portuguese`() {
    val base = ApplicationProvider.getApplicationContext<Context>()
    val enContext = LocaleManager.createLocalizedContext(base, AppLanguage.ENGLISH)
    val ptContext = LocaleManager.createLocalizedContext(base, AppLanguage.PORTUGUESE_BR)

    assertEquals(
      "cataloged work",
      enContext.resources.getQuantityString(R.plurals.home_overview_works_label, 1),
    )
    assertEquals(
      "cataloged works",
      enContext.resources.getQuantityString(R.plurals.home_overview_works_label, 2),
    )
    assertEquals(
      "obra catalogada",
      ptContext.resources.getQuantityString(R.plurals.home_overview_works_label, 1),
    )
    assertEquals(
      "obras catalogadas",
      ptContext.resources.getQuantityString(R.plurals.home_overview_works_label, 2),
    )
    assertEquals(
      "1 página",
      ptContext.resources.getQuantityString(R.plurals.pages_count, 1, 1),
    )
    assertEquals(
      "240 páginas",
      ptContext.resources.getQuantityString(R.plurals.pages_count, 2, 240),
    )
    assertEquals(
      "1 page",
      enContext.resources.getQuantityString(R.plurals.pages_count, 1, 1),
    )
    assertEquals(
      "240 pages",
      enContext.resources.getQuantityString(R.plurals.pages_count, 2, 240),
    )
  }
}
