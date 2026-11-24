package com.example.core.data.local.media

import android.content.Context
import com.example.core.bibliographic.domain.CoverRanker
import com.example.core.bibliographic.domain.HybridSearchOrchestrator
import com.example.core.bibliographic.network.ConnectivityChecker
import com.example.core.data.local.db.BookDao
import com.example.core.data.local.db.EditionDao
import com.example.core.data.local.db.EditionEntity
import com.example.core.data.local.preferences.PreferencesRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CoverUpgradeCoordinator(
  private val context: Context,
  private val bookDao: BookDao,
  private val editionDao: EditionDao,
  private val coverStorageManager: CoverStorageManager,
  private val orchestrator: HybridSearchOrchestrator,
  private val preferencesRepository: PreferencesRepository,
  private val connectivityChecker: ConnectivityChecker,
) {
  private val hasStartedSession = AtomicBoolean(false)

  suspend fun upgradeLowResCoversIfNeeded() {
    if (!connectivityChecker.isOnline()) return
    if (!hasStartedSession.compareAndSet(false, true)) return
    withContext(Dispatchers.IO) {
      if (!connectivityChecker.isOnline()) {
        hasStartedSession.set(false)
        return@withContext
      }
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      val preferredLang =
        runCatching { preferencesRepository.preferences.first().language.tag }.getOrNull()
      val editions = runCatching { editionDao.getAutoManagedCoverEditions() }.getOrElse { return@withContext }
      val now = System.currentTimeMillis()

      for (edition in editions) {
        if (!connectivityChecker.isOnline()) break
        val attemptKey = "edition_${edition.id}"
        try {
          val lastAttempt = prefs.getLong(attemptKey, 0L)
          if (lastAttempt > 0L && now - lastAttempt < RETRY_COOLDOWN_MS) continue

          val existingUri = edition.coverUri ?: continue
          val isRemote = existingUri.startsWith("http://") || existingUri.startsWith("https://")
          val currentDims =
            if (isRemote) null else coverStorageManager.getLocalCoverDimensions(existingUri)
          val currentWidth =
            when {
              isRemote -> 0
              currentDims == null -> continue
              currentDims.first >= TARGET_MIN_WIDTH_PX -> continue
              else -> currentDims.first
            }

          val work = bookDao.getWorkById(edition.workId) ?: continue
          val isbns = listOfNotNull(edition.isbn13, edition.isbn10, edition.isbn).distinct()

          val directUrlCandidates =
            buildList {
              if (isRemote) addAll(CoverRanker.expandHdVariants(existingUri))
              extractGoogleVolumeId(edition.externalIdsJson)?.let { volId ->
                add("https://books.google.com/books/publisher/content/images/frontcovers/$volId?fife=w800")
                add("https://books.google.com/books/content?id=$volId&printsec=frontcover&img=1&zoom=0")
              }
            }

          val fetchedCandidates =
            buildList {
              addAll(
                orchestrator.fetchCandidatesForBookUpdate(
                  title = work.title,
                  author = work.author,
                  isbns = isbns,
                  preferredLanguage = preferredLang,
                )
              )
              if (isEmpty() && isbns.isEmpty() && !work.originalTitle.isNullOrBlank() && work.originalTitle != work.title) {
                addAll(
                  orchestrator.fetchCandidatesForBookUpdate(
                    title = work.originalTitle,
                    author = work.author,
                    isbns = emptyList(),
                    preferredLanguage = preferredLang,
                  )
                )
              }
            }

          val rankedRemoteUrls =
            CoverRanker.rankAndDeduplicate(fetchedCandidates.flatMap { it.coverCandidates })
              .flatMap { CoverRanker.expandHdVariants(it.imageUrl) }

          val allCandidateUrls =
            (directUrlCandidates + rankedRemoteUrls)
              .map { it.trim() }
              .filter { it.startsWith("https://") || it.startsWith("http://") }
              .distinct()

          if (allCandidateUrls.isEmpty()) {
            // Short cooldown when no candidate URLs were returned (e.g., transient API timeout)
            prefs.edit().putLong(attemptKey, now - RETRY_COOLDOWN_MS + TRANSIENT_RETRY_COOLDOWN_MS).apply()
            continue
          }

          val requiredWidth = maxOf(TARGET_MIN_WIDTH_PX, currentWidth + 60)
          val upgradedPath =
            coverStorageManager.downloadBestAvailableCover(
              imageUrls = allCandidateUrls,
              minPreferredWidth = requiredWidth,
            )

          if (upgradedPath == null) {
            if (connectivityChecker.isOnline()) {
              prefs.edit().putLong(attemptKey, now).apply()
            }
            continue
          }

          val newDims = coverStorageManager.getLocalCoverDimensions(upgradedPath)
          val isBetter =
            newDims != null &&
              newDims.first >= TARGET_MIN_WIDTH_PX &&
              newDims.first > currentWidth

          if (!isBetter) {
            coverStorageManager.deleteCoverIfManaged(upgradedPath)
            prefs.edit().putLong(attemptKey, now).apply()
            continue
          }

          val latest: EditionEntity? = editionDao.getEditionById(edition.id)
          if (latest == null || latest.isCoverCustomized) {
            coverStorageManager.deleteCoverIfManaged(upgradedPath)
            continue
          }

          editionDao.updateEdition(latest.copy(coverUri = upgradedPath))
          prefs.edit().putLong(attemptKey, now).apply()
          if (!isRemote && existingUri != upgradedPath) {
            coverStorageManager.deleteCoverIfManaged(existingUri)
          }
        } catch (ce: CancellationException) {
          throw ce
        } catch (_: Throwable) {
          // Best-effort background cover upgrade; do not lock 7-day cooldown on unexpected error
        }
      }
    }
  }

  private fun extractGoogleVolumeId(externalIdsJson: String?): String? {
    if (externalIdsJson.isNullOrBlank()) return null
    return runCatching {
      val obj = JSONObject(externalIdsJson)
      obj.optString("googleBooksVolumeId").takeIf { it.isNotBlank() }
        ?: obj.optString("google_books_id").takeIf { it.isNotBlank() }
    }.getOrNull()
  }

  private companion object {
    const val PREFS_NAME = "dipe_cover_upgrade_prefs"
    const val TARGET_MIN_WIDTH_PX = 320
    const val RETRY_COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000
    const val TRANSIENT_RETRY_COOLDOWN_MS = 6L * 60 * 60 * 1000
  }
}
