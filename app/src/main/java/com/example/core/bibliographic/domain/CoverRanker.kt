package com.example.core.bibliographic.domain

import com.example.core.model.CoverCandidate
import com.example.core.model.MetadataSource

object CoverRanker {

  fun rankAndDeduplicate(candidates: List<CoverCandidate>): List<CoverCandidate> {
    if (candidates.isEmpty()) return emptyList()
    val filtered =
      candidates
        .mapNotNull { candidate ->
          val normalizedUrl = normalizeCoverUrl(candidate.imageUrl) ?: return@mapNotNull null
          if (isLikelyPlaceholder(normalizedUrl)) return@mapNotNull null
          val scored = scoreCandidate(candidate.copy(imageUrl = normalizedUrl))
          candidate.copy(imageUrl = normalizedUrl, score = scored)
        }
        .groupBy { it.imageUrl }
        .values
        .map { sameUrlGroup ->
          sameUrlGroup.maxByOrNull { it.score }!!
        }
        .sortedByDescending { it.score }
    return filtered
  }

  fun buildGoogleBooksCoverCandidates(
    rawUrl: String?,
    matchesIsbn: Boolean,
    matchesPublisherYear: Boolean,
    matchesLanguage: Boolean,
  ): List<CoverCandidate> {
    val cleanBase =
      rawUrl
        ?.trim()
        ?.replace("http://", "https://", ignoreCase = true)
        ?.replace("&edge=curl", "", ignoreCase = true)
        ?.takeIf { it.isNotBlank() }
        ?: return emptyList()

    val candidates = mutableListOf<CoverCandidate>()
    val hasZoomParam = Regex("[?&]zoom=\\d+").containsMatchIn(cleanBase)
    if (hasZoomParam && "books.google" in cleanBase) {
      val noZoomBase = cleanBase.replace(Regex("&zoom=\\d+"), "")
      val fifeW800 =
        if ("?" in noZoomBase) "$noZoomBase&fife=w800" else "$noZoomBase?fife=w800"
      val zoom0 = cleanBase.replace(Regex("([?&])zoom=\\d+"), "$1zoom=0")
      candidates.add(
        CoverCandidate(
          imageUrl = fifeW800,
          source = MetadataSource.GOOGLE_BOOKS,
          matchesIsbn = matchesIsbn,
          matchesPublisherYear = matchesPublisherYear,
          matchesLanguage = matchesLanguage,
          widthHint = 800,
          heightHint = 1200,
        )
      )
      candidates.add(
        CoverCandidate(
          imageUrl = zoom0,
          source = MetadataSource.GOOGLE_BOOKS,
          matchesIsbn = matchesIsbn,
          matchesPublisherYear = matchesPublisherYear,
          matchesLanguage = matchesLanguage,
          widthHint = 900,
          heightHint = 1350,
        )
      )
    }

    candidates.add(
      CoverCandidate(
        imageUrl = cleanBase,
        source = MetadataSource.GOOGLE_BOOKS,
        matchesIsbn = matchesIsbn,
        matchesPublisherYear = matchesPublisherYear,
        matchesLanguage = matchesLanguage,
        widthHint = if ("zoom=1" in cleanBase) 128 else null,
        heightHint = if ("zoom=1" in cleanBase) 192 else null,
      )
    )
    return rankAndDeduplicate(candidates)
  }

  fun buildAppleBooksCoverCandidates(
    artworkUrl100: String?,
    matchesIsbn: Boolean,
    matchesPublisherYear: Boolean,
    matchesLanguage: Boolean,
  ): List<CoverCandidate> {
    val cleanBase =
      artworkUrl100
        ?.trim()
        ?.replace("http://", "https://", ignoreCase = true)
        ?.takeIf { it.isNotBlank() }
        ?: return emptyList()

    val candidates = mutableListOf<CoverCandidate>()
    val upgraded600 =
      cleanBase.replace(Regex("\\d+x\\d+bb(?=\\.[a-zA-Z0-9]+$)"), "600x900bb")
    if (upgraded600 != cleanBase) {
      candidates.add(
        CoverCandidate(
          imageUrl = upgraded600,
          source = MetadataSource.APPLE_BOOKS,
          matchesIsbn = matchesIsbn,
          matchesPublisherYear = matchesPublisherYear,
          matchesLanguage = matchesLanguage,
          widthHint = 600,
          heightHint = 900,
        )
      )
    }
    candidates.add(
      CoverCandidate(
        imageUrl = cleanBase,
        source = MetadataSource.APPLE_BOOKS,
        matchesIsbn = matchesIsbn,
        matchesPublisherYear = matchesPublisherYear,
        matchesLanguage = matchesLanguage,
        widthHint = 100,
        heightHint = 100,
      )
    )
    return rankAndDeduplicate(candidates)
  }

  private fun scoreCandidate(c: CoverCandidate): Int {
    var score = 0
    if (c.matchesIsbn) score += 50
    if (c.matchesPublisherYear) score += 20
    if (c.matchesLanguage) score += 15
    score +=
      when (c.source) {
        MetadataSource.MANUAL -> 40
        // Fontes abertas/diretas têm prioridade sobre Apple Books para persistência offline segura
        MetadataSource.OPEN_LIBRARY -> 24
        MetadataSource.GOOGLE_BOOKS -> 22
        MetadataSource.BRASIL_API -> 21
        MetadataSource.WIKIDATA -> 18
        MetadataSource.CROSSREF -> 10
        MetadataSource.APPLE_BOOKS -> 2
      }
    val w = c.widthHint ?: 0
    val h = c.heightHint ?: 0
    if (w >= 600 || h >= 900) {
      score += 18
    } else if (w >= 400 || h >= 600) {
      score += 12
    } else if (w in 1..130 || h in 1..195) {
      score -= 8
    }
    val lowerUrl = c.imageUrl.lowercase()
    when {
      "fife=w800" in lowerUrl || "fife=w1000" in lowerUrl -> score += 18
      "zoom=0" in lowerUrl -> score += 15
      "-l.jpg" in lowerUrl -> score += 12
      "zoom=1" in lowerUrl -> score += 4
      "zoom=5" in lowerUrl || "-s.jpg" in lowerUrl -> score -= 12
    }
    return score
  }

  private fun normalizeCoverUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    val https =
      if (trimmed.startsWith("http://", ignoreCase = true)) {
        "https://" + trimmed.removePrefix("http://")
      } else {
        trimmed
      }
    return https.takeIf { it.startsWith("https://") || it.startsWith("file://") || it.startsWith("/") }
  }

  private fun isLikelyPlaceholder(url: String): Boolean {
    val lower = url.lowercase()
    return "no_cover" in lower ||
      "nocover" in lower ||
      "placeholder" in lower ||
      "image_not_available" in lower
  }
}
