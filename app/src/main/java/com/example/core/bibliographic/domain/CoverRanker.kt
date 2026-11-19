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

  private fun scoreCandidate(c: CoverCandidate): Int {
    var score = 0
    if (c.matchesIsbn) score += 50
    if (c.matchesPublisherYear) score += 20
    if (c.matchesLanguage) score += 15
    score +=
      when (c.source) {
        MetadataSource.MANUAL -> 30
        MetadataSource.OPEN_LIBRARY -> 12
        MetadataSource.GOOGLE_BOOKS -> 10
        MetadataSource.BRASIL_API -> 9
        MetadataSource.WIKIDATA -> 8
        MetadataSource.CROSSREF -> 4
      }
    val w = c.widthHint ?: 0
    val h = c.heightHint ?: 0
    if (w >= 400 || h >= 600) {
      score += 14
    } else if (w in 1..119 || h in 1..159) {
      score -= 15
    }
    if (c.imageUrl.contains("-L.jpg", ignoreCase = true) || c.imageUrl.contains("zoom=1", ignoreCase = true)) {
      score += 10
    } else if (c.imageUrl.contains("zoom=5", ignoreCase = true) || c.imageUrl.contains("-S.jpg", ignoreCase = true)) {
      score -= 12
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
