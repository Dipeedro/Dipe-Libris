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

  /**
   * Para miniaturas compactas em listas/cards de pesquisa (<= 120.dp), prioriza variantes leves
   * (-M.jpg / zoom=1 / fife=w240 / 200x300bb) e preserva diversidade entre fontes (Google, Open Library,
   * BrasilAPI, Apple Books) para evitar que múltiplas variantes do mesmo provedor expulsem fallbacks.
   * Mantém a ordem HD original para telas de revisão e download offline (maxWidthDp > 120).
   */
  fun selectForDisplaySize(orderedUrls: List<String>, maxWidthDp: Int): List<String> {
    if (orderedUrls.isEmpty()) return emptyList()
    if (maxWidthDp > 120) return orderedUrls.distinct()

    val compactCandidates =
      orderedUrls
        .map { url -> toCompactThumbnailUrl(url) }
        .distinct()

    if (compactCandidates.size <= 2) {
      return prioritizeCompactGoogleOrder(compactCandidates)
    }

    val selected = mutableListOf<String>()
    val seenSources = mutableSetOf<String>()
    val orderedForCompact = prioritizeCompactGoogleOrder(compactCandidates)

    // Pass 1: preserve at most 1 compact URL per distinct source family so fallbacks survive
    for (url in orderedForCompact) {
      val srcKey = sourceFamilyKey(url)
      if (srcKey !in seenSources) {
        selected.add(url)
        seenSources.add(srcKey)
        if (selected.size == 2) return selected
      }
    }

    // Pass 2: if fewer than 2 distinct sources exist, fill up to 2 with remaining distinct variants
    for (url in orderedForCompact) {
      if (url !in selected) {
        selected.add(url)
        if (selected.size == 2) break
      }
    }
    return selected
  }

  private fun prioritizeCompactGoogleOrder(urls: List<String>): List<String> {
    val googleFrontcovers = mutableListOf<String>()
    val result = mutableListOf<String>()
    for (url in urls) {
      val lower = url.lowercase()
      if ("books.google" in lower && "/publisher/content/images/frontcovers/" in lower) {
        googleFrontcovers.add(url)
      } else {
        result.add(url)
      }
    }
    // Insert any /frontcovers/ FIFE URLs AFTER verified /books/content?zoom=1 URLs of the same source,
    // or at the end if other Google URLs exist, since /frontcovers/ often returns HTTP 200 placeholders.
    if (googleFrontcovers.isEmpty()) return urls
    val firstGoogleContentIdx = result.indexOfFirst { "books.google" in it.lowercase() }
    return if (firstGoogleContentIdx >= 0) {
      result.toMutableList().apply { addAll(firstGoogleContentIdx + 1, googleFrontcovers) }
    } else {
      urls
    }
  }

  private fun sourceFamilyKey(url: String): String {
    val lower = url.lowercase()
    return when {
      "books.google" in lower || "googleusercontent.com" in lower -> "google"
      "covers.openlibrary.org" in lower -> "openlibrary"
      "mzstatic.com" in lower -> "apple"
      "brasilapi" in lower -> "brasilapi"
      else -> lower.substringAfter("://").substringBefore('/')
    }
  }

  private fun toCompactThumbnailUrl(url: String): String {
    val trimmed = url.trim()
    val lower = trimmed.lowercase()
    return when {
      "books.google" in lower -> {
        if ("/publisher/content/images/frontcovers/" in lower) {
          // Never rewrite /frontcovers/...?fife=w800 to ?zoom=1 (invalid param for FIFE endpoint).
          // Keep a compact FIFE width so /frontcovers/ stays valid when used as secondary fallback.
          trimmed.replace(Regex("([?&])fife=w\\d+", RegexOption.IGNORE_CASE), "$1fife=w240")
        } else {
          var result = trimmed.replace(Regex("([?&])fife=w\\d+", RegexOption.IGNORE_CASE), "$1zoom=1")
          result = result.replace(Regex("([?&])zoom=0\\b", RegexOption.IGNORE_CASE), "$1zoom=1")
          result
        }
      }
      "covers.openlibrary.org" in lower ->
        trimmed.replace(Regex("-L\\.jpg(?=\\?|$)", RegexOption.IGNORE_CASE), "-M.jpg")
      "mzstatic.com" in lower ->
        trimmed.replace(Regex("\\d+x\\d+bb(?=\\.[a-zA-Z0-9]+(?:\\?.*)?$)", RegexOption.IGNORE_CASE), "200x300bb")
      else -> trimmed
    }
  }

  fun expandHdVariants(rawUrl: String): List<String> {
    val clean = normalizeCoverUrl(rawUrl) ?: return emptyList()
    val lower = clean.lowercase()
    return when {
      "books.google" in lower ->
        buildGoogleBooksCoverCandidates(
          rawUrl = clean,
          matchesIsbn = false,
          matchesPublisherYear = false,
          matchesLanguage = false,
        ).map { it.imageUrl }
      "covers.openlibrary.org" in lower ->
        listOf(
          clean.replace(Regex("-[SM]\\.jpg(?=\\?|$)", RegexOption.IGNORE_CASE), "-L.jpg"),
          clean,
        ).distinct()
      "mzstatic.com" in lower ->
        buildAppleBooksCoverCandidates(
          artworkUrl100 = clean,
          matchesIsbn = false,
          matchesPublisherYear = false,
          matchesLanguage = false,
        ).map { it.imageUrl }
      else -> listOf(clean)
    }
  }

  fun buildGoogleBooksCoverCandidates(
    rawUrl: String?,
    matchesIsbn: Boolean,
    matchesPublisherYear: Boolean,
    matchesLanguage: Boolean,
  ): List<CoverCandidate> =
    buildGoogleBooksCoverCandidates(
      rawUrls = listOf(rawUrl),
      matchesIsbn = matchesIsbn,
      matchesPublisherYear = matchesPublisherYear,
      matchesLanguage = matchesLanguage,
    )

  fun buildGoogleBooksCoverCandidates(
    rawUrls: List<String?>,
    matchesIsbn: Boolean,
    matchesPublisherYear: Boolean,
    matchesLanguage: Boolean,
  ): List<CoverCandidate> {
    val cleanUrls =
      rawUrls
        .mapNotNull { raw ->
          raw
            ?.trim()
            ?.replace("http://", "https://", ignoreCase = true)
            ?.replace("&edge=curl", "", ignoreCase = true)
            ?.takeIf { it.isNotBlank() }
        }
        .distinct()
    if (cleanUrls.isEmpty()) return emptyList()

    val candidates = mutableListOf<CoverCandidate>()
    var synthesizedForVolume = false

    for ((index, cleanBase) in cleanUrls.withIndex()) {
      val hasZoomParam = Regex("[?&]zoom=\\d+").containsMatchIn(cleanBase)
      val volumeId =
        Regex("[?&]id=([^&]+)").find(cleanBase)?.groupValues?.getOrNull(1)
          ?: Regex("/frontcovers/([^/?&]+)").find(cleanBase)?.groupValues?.getOrNull(1)

      if ("books.google" in cleanBase && !synthesizedForVolume) {
        synthesizedForVolume = true
        if (!volumeId.isNullOrBlank()) {
          val publisherFifeW800 =
            "https://books.google.com/books/publisher/content/images/frontcovers/$volumeId?fife=w800"
          candidates.add(
            CoverCandidate(
              imageUrl = publisherFifeW800,
              source = MetadataSource.GOOGLE_BOOKS,
              matchesIsbn = matchesIsbn,
              matchesPublisherYear = matchesPublisherYear,
              matchesLanguage = matchesLanguage,
              widthHint = 800,
              heightHint = 1200,
              isSynthetic = true,
            )
          )
        } else if (hasZoomParam) {
          val noZoomBase = cleanBase.replace(Regex("&zoom=\\d+"), "")
          val fifeW800 =
            if ("?" in noZoomBase) "$noZoomBase&fife=w800" else "$noZoomBase?fife=w800"
          candidates.add(
            CoverCandidate(
              imageUrl = fifeW800,
              source = MetadataSource.GOOGLE_BOOKS,
              matchesIsbn = matchesIsbn,
              matchesPublisherYear = matchesPublisherYear,
              matchesLanguage = matchesLanguage,
              widthHint = 800,
              heightHint = 1200,
              isSynthetic = true,
            )
          )
        }

        if (hasZoomParam) {
          val zoom0 = cleanBase.replace(Regex("([?&])zoom=\\d+"), "$1zoom=0")
          candidates.add(
            CoverCandidate(
              imageUrl = zoom0,
              source = MetadataSource.GOOGLE_BOOKS,
              matchesIsbn = matchesIsbn,
              matchesPublisherYear = matchesPublisherYear,
              matchesLanguage = matchesLanguage,
              widthHint = 900,
              heightHint = 1350,
              isSynthetic = true,
            )
          )
        }
      }

      val (wHint, hHint) =
        when {
          "fife=w800" in cleanBase -> 800 to 1200
          "zoom=0" in cleanBase -> 900 to 1350
          "zoom=2" in cleanBase || "zoom=3" in cleanBase -> 300 to 450
          "zoom=1" in cleanBase -> 128 to 192
          "zoom=5" in cleanBase -> 80 to 120
          index == 0 && cleanUrls.size > 1 -> 500 to 750
          else -> null to null
        }

      candidates.add(
        CoverCandidate(
          imageUrl = cleanBase,
          source = MetadataSource.GOOGLE_BOOKS,
          matchesIsbn = matchesIsbn,
          matchesPublisherYear = matchesPublisherYear,
          matchesLanguage = matchesLanguage,
          widthHint = wHint,
          heightHint = hHint,
          isSynthetic = false,
        )
      )
    }
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
    val appleDimRegex = Regex("\\d+x\\d+bb(?=\\.[a-zA-Z0-9]+(?:\\?.*)?$)")
    val upgraded1200 = cleanBase.replace(appleDimRegex, "1200x1800bb")
    if (upgraded1200 != cleanBase) {
      candidates.add(
        CoverCandidate(
          imageUrl = upgraded1200,
          source = MetadataSource.APPLE_BOOKS,
          matchesIsbn = matchesIsbn,
          matchesPublisherYear = matchesPublisherYear,
          matchesLanguage = matchesLanguage,
          widthHint = 1200,
          heightHint = 1800,
          isSynthetic = true,
        )
      )
    }
    val upgraded600 = cleanBase.replace(appleDimRegex, "600x900bb")
    if (upgraded600 != cleanBase && upgraded600 != upgraded1200) {
      candidates.add(
        CoverCandidate(
          imageUrl = upgraded600,
          source = MetadataSource.APPLE_BOOKS,
          matchesIsbn = matchesIsbn,
          matchesPublisherYear = matchesPublisherYear,
          matchesLanguage = matchesLanguage,
          widthHint = 600,
          heightHint = 900,
          isSynthetic = true,
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
        isSynthetic = false,
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
        MetadataSource.APPLE_BOOKS -> 6
      }
    // Real API-returned cover URLs are more reliable than speculative synthesized HD endpoints
    if (!c.isSynthetic) {
      score += 16
    }
    val w = c.widthHint ?: 0
    val h = c.heightHint ?: 0
    if (w >= 800 || h >= 1200) {
      score += if (c.isSynthetic) 20 else 24
    } else if (w >= 600 || h >= 900) {
      score += if (c.isSynthetic) 15 else 18
    } else if (w >= 333 || h >= 500) {
      score += 12
    } else if (w in 1..150 || h in 1..210) {
      score -= 14
    }
    val lowerUrl = c.imageUrl.lowercase()
    when {
      "publisher/content/images/frontcovers" in lowerUrl || "fife=w800" in lowerUrl || "fife=w1000" in lowerUrl ->
        score += if (c.isSynthetic) 18 else 22
      "zoom=0" in lowerUrl ->
        score += if (c.isSynthetic) 14 else 18
      "1200x1800bb" in lowerUrl -> score += 20
      "600x900bb" in lowerUrl -> score += 14
      "-l.jpg" in lowerUrl -> score += 12
      "zoom=1" in lowerUrl -> score -= 6
      "zoom=5" in lowerUrl || "-s.jpg" in lowerUrl || "100x100bb" in lowerUrl -> score -= 18
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
