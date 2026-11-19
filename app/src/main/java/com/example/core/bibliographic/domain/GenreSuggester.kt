package com.example.core.bibliographic.domain

import com.example.core.model.BibliographicMath
import com.example.core.model.Genre

object GenreSuggester {

  private val keywordToSlug: List<Pair<List<String>, String>> =
    listOf(
      listOf("graphic novel", "comics", "manga", "quadrinho", "hq", "bande dessinee") to "graphic-novels",
      listOf("poetry", "poesia", "poems", "poemas", "verse", "lyrik") to "poetry",
      listOf("drama", "plays", "theater", "theatre", "teatro", "dramaturgia", "tragedy", "comedy play") to "drama",
      listOf("science fiction", "sci-fi", "fantasy", "fantasia", "ficcao cientifica", "dystopia", "speculative") to "fantasy-scifi",
      listOf("mystery", "thriller", "detective", "crime fiction", "suspense", "misterio", "policial", "noir") to "mystery-thriller",
      listOf("romance", "love stories", "romantic fiction") to "romance",
      listOf("classic", "classics", "classico", "classicos", "world literature") to "classics",
      listOf("biography", "autobiography", "memoir", "biografia", "memorias", "diaries", "letters") to "biography-memoir",
      listOf("philosophy", "filosofia", "ethics", "metaphysics", "epistemology", "logic") to "philosophy",
      listOf("history", "historia", "historical", "civilization", "ancient world", "war history") to "history",
      listOf("psychology", "sociology", "anthropology", "social science", "politics", "psicologia", "sociedade", "politica") to "psychology-society",
      listOf("science", "technology", "mathematics", "physics", "biology", "computer", "programming", "software", "ciencia", "tecnologia", "engenharia") to "science-technology",
      listOf("art", "architecture", "music", "cinema", "photography", "design", "artes", "arquitetura", "musica") to "arts-architecture",
      listOf("religion", "theology", "mythology", "spirituality", "religiao", "mitologia", "teologia") to "religion-mythology",
      listOf("essay", "criticism", "literary criticism", "ensaio", "ensaios", "critica", "rhetoric") to "essays-criticism",
      listOf("education", "study", "reference", "textbook", "academic", "law", "economics", "business", "medical", "academico", "direito", "economia") to "academic-reference",
      listOf("fiction", "novel", "literature", "ficcao", "literatura", "short stories", "contos") to "literary-fiction",
    )

  fun suggestSlug(categories: List<String>, titleHint: String? = null): String? {
    val combined =
      (categories + listOfNotNull(titleHint))
        .joinToString(" ")
        .let { BibliographicMath.normalizeText(it) }
    if (combined.isBlank()) return null

    for ((keywords, slug) in keywordToSlug) {
      if (keywords.any { kw -> kw in combined }) {
        return slug
      }
    }
    return null
  }

  fun matchGenre(categories: List<String>, availableGenres: List<Genre>, titleHint: String? = null): Genre? {
    if (availableGenres.isEmpty()) return null
    val normalizedCats = categories.map { BibliographicMath.normalizeText(it) }.filter { it.isNotEmpty() }

    // First check custom or direct genre name matches
    availableGenres.firstOrNull { genre ->
      val pt = BibliographicMath.normalizeText(genre.namePt)
      val en = BibliographicMath.normalizeText(genre.nameEn)
      normalizedCats.any { cat -> cat == pt || cat == en || (genre.isCustom && (pt in cat || cat in pt)) }
    }?.let { return it }

    val slug = suggestSlug(categories, titleHint) ?: return null
    return availableGenres.firstOrNull { it.slug == slug }
  }
}
