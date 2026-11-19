package com.example.core.bibliographic.domain

import com.example.core.model.BibliographicMath
import com.example.core.model.MetadataSource
import com.example.core.model.SynopsisCandidate

object SynopsisSelector {

  fun rankAndDeduplicate(
    candidates: List<SynopsisCandidate>,
    preferredLanguage: String? = null,
    editionLanguage: String? = null,
  ): List<SynopsisCandidate> {
    if (candidates.isEmpty()) return emptyList()
    val normPref = BibliographicMath.normalizeLanguageCode(preferredLanguage)
    val normEditionLang = BibliographicMath.normalizeLanguageCode(editionLanguage)

    val scored =
      candidates
        .mapNotNull { candidate ->
          val cleanText = BibliographicMath.stripHtmlTags(candidate.text) ?: return@mapNotNull null
          if (cleanText.length < 25) return@mapNotNull null
          val candLang = BibliographicMath.normalizeLanguageCode(candidate.language)
          val score = scoreSynopsis(cleanText, candLang, candidate.source, normPref, normEditionLang)
          candidate.copy(text = cleanText, language = candLang ?: candidate.language, score = score)
        }
        .sortedByDescending { it.score }

    val unique = mutableListOf<SynopsisCandidate>()
    for (item in scored) {
      val isDup =
        unique.any { existing ->
          val sim = BibliographicMath.tokenSimilarity(existing.text.take(280), item.text.take(280))
          sim >= 0.88f
        }
      if (!isDup) {
        unique.add(item)
      }
    }
    return unique
  }

  private fun scoreSynopsis(
    text: String,
    candidateLang: String?,
    source: MetadataSource,
    preferredLang: String?,
    editionLang: String?,
  ): Int {
    var score = 0
    if (preferredLang != null && candidateLang != null) {
      score += if (candidateLang == preferredLang) 45 else -25
    }
    if (editionLang != null && candidateLang != null && candidateLang == editionLang) {
      score += 18
    }

    score +=
      when (text.length) {
        in 140..2200 -> 26
        in 80..139 -> 14
        in 2201..4500 -> 18
        else -> -12
      }

    val trimmed = text.trimEnd()
    if (trimmed.endsWith("...") || trimmed.endsWith("…")) {
      score -= 14
    }
    val lower = trimmed.lowercase()
    if ("read more" in lower || "leia mais" in lower || "click here" in lower) {
      score -= 12
    }

    score +=
      when (source) {
        MetadataSource.GOOGLE_BOOKS -> 12
        MetadataSource.BRASIL_API -> 11
        MetadataSource.OPEN_LIBRARY -> 10
        MetadataSource.WIKIDATA -> 8
        MetadataSource.CROSSREF -> 6
        MetadataSource.MANUAL -> 20
      }

    return score
  }
}
