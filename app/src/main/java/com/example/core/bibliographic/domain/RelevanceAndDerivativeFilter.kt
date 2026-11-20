package com.example.core.bibliographic.domain

import com.example.core.model.BibliographicMath
import com.example.core.model.SearchEditionCandidate
import java.util.Locale

data class CandidateMatchEvaluation(
  val accepted: Boolean,
  val queryMatchScore: Int,
  val titleMatchScore: Int,
  val authorMatchScore: Int,
  val significantTokenCoverage: Float,
  val rejectionReason: String? = null,
)

object RelevanceAndDerivativeFilter {

  private val coloringCompoundRegex =
    Regex(
      "\\b(coloring\\s+book|colouring\\s+book|livro\\s+de\\s+colorir|caderno\\s+de\\s+colorir|colorir\\s+para\\s+adultos)\\b",
      RegexOption.IGNORE_CASE,
    )

  private val stationeryStrongRegex =
    Regex(
      "\\b(composition\\s+notebook|blank\\s+lined\\s+(?:journal|notebook)|lined\\s+(?:journal|notebook|diary)|college\\s+ruled\\s+notebook|dot\\s+grid\\s+(?:journal|notebook)|daily\\s+planner|sketchbook\\s+for)\\b",
      RegexOption.IGNORE_CASE,
    )

  private val stationerySecondarySignals =
    Regex(
      "\\b(100\\s+pages|110\\s+pages|120\\s+pages|6\\s*x\\s*9|lined\\s+pages|gift\\s+for\\s+fans|stationery|diaries\\s*&\\s*journals)\\b",
      RegexOption.IGNORE_CASE,
    )

  private val derivativeGuideStrongRegex =
    Regex(
      "\\b(summary\\s*(?:&|and)\\s*analysis\\s+of|study\\s+guide\\s*[:\\-]|analise\\s+do\\s+livro|resumo\\s+da\\s+serie|resumo\\s+(?:&|e)\\s+analise|trivia\\s+(?:&|and)\\s+quiz|unofficial\\s+(?:guide|companion))\\b",
      RegexOption.IGNORE_CASE,
    )

  private val derivativeSummaryPublishers =
    setOf("sparknotes", "bookrags", "brightsummaries", "quickread", "cliffsnotes", "instaread", "supersummary")

  fun evaluateCandidate(
    candidate: SearchEditionCandidate,
    intent: ParsedSearchIntent?,
  ): CandidateMatchEvaluation {
    if (intent == null || intent.intentType == QueryIntentType.ISBN_EXACT) {
      return CandidateMatchEvaluation(
        accepted = true,
        queryMatchScore = 100,
        titleMatchScore = 100,
        authorMatchScore = 0,
        significantTokenCoverage = 1f,
      )
    }

    val titleRaw = candidate.workTitle
    val subtitleRaw = candidate.subtitle.orEmpty()
    val combinedTitleText = "$titleRaw $subtitleRaw".trim()
    val synopsisRaw = candidate.synopsisCandidates.firstOrNull()?.text.orEmpty()
    val publisherNorm = BibliographicMath.normalizeText(candidate.publisher)
    val categoriesNorm = BibliographicMath.normalizeText(candidate.categories.joinToString(" "))

    // 1. Multi-signal contextual filtering for coloring books (never single-word blacklisting)
    if (!intent.allowColoringBooks && coloringCompoundRegex.containsMatchIn(combinedTitleText)) {
      return CandidateMatchEvaluation(false, 0, 0, 0, 0f, "coloring_book")
    }

    // 2. Multi-signal contextual filtering for stationery / blank notebooks
    // Preserves legitimate literary titles like "Caderno de Memórias Coloniais"
    if (!intent.allowNotebooks) {
      val hasStrongStationeryPhrase = stationeryStrongRegex.containsMatchIn(combinedTitleText)
      val hasSecondaryStationeryCue =
        stationerySecondarySignals.containsMatchIn("$combinedTitleText $synopsisRaw $categoriesNorm")
      val endsWithNotebookOrJournal =
        Regex("\\b(notebook|journal)\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(titleRaw)
      if (hasStrongStationeryPhrase || (endsWithNotebookOrJournal && (hasSecondaryStationeryCue || candidate.contributors.isEmpty()))) {
        return CandidateMatchEvaluation(false, 0, 0, 0, 0f, "stationery_notebook")
      }
    }

    // 3. Multi-signal contextual filtering for apocryphal study guides / summaries
    // Preserves legitimate novels/titles like "O Guia do Mochileiro das Galáxias"
    if (!intent.allowGuidesOrAnalyses) {
      val hasStrongDerivativePhrase = derivativeGuideStrongRegex.containsMatchIn("$combinedTitleText ${synopsisRaw.take(180)}")
      val isSummaryPublisher = derivativeSummaryPublishers.any { it in publisherNorm }
      val startsWithResumoOrAnalise =
        Regex("^\\s*(resumo|analise|study\\s+guide|summary)\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE)
          .containsMatchIn(titleRaw)
      if (hasStrongDerivativePhrase || isSummaryPublisher || startsWithResumoOrAnalise) {
        return CandidateMatchEvaluation(false, 0, 0, 0, 0f, "derivative_summary_or_guide")
      }
    }

    // 4. Bibliographic match & anti-stopword/off-topic floor
    val normTitleFull = BibliographicMath.normalizeText(combinedTitleText)
    val normCoreTitle = BibliographicMath.normalizeTitleCore(titleRaw)
    val normAuthors = BibliographicMath.normalizeText(candidate.primaryAuthorsText)
    val normSeries = BibliographicMath.normalizeText(candidate.seriesName)
    val normOriginal = BibliographicMath.normalizeText(candidate.originalTitle)

    val titleWords = normTitleFull.split(' ').filter { it.isNotBlank() }.toSet()
    val authorWords = normAuthors.split(' ').filter { it.isNotBlank() }.toSet()
    val metaWords =
      (titleWords + authorWords +
        normSeries.split(' ').filter { it.isNotBlank() } +
        normOriginal.split(' ').filter { it.isNotBlank() })

    val sigTokens = intent.significantTokens
    val titleSigHits = sigTokens.count { tok -> tok in titleWords || titleWords.any { w -> w.startsWith(tok) || (tok.length >= 4 && BibliographicMath.levenshteinSimilarity(w, tok) >= 0.82f) } }
    val authorSigHits = sigTokens.count { tok -> tok in authorWords || authorWords.any { w -> w.startsWith(tok) || (tok.length >= 4 && BibliographicMath.levenshteinSimilarity(w, tok) >= 0.84f) } }
    val overallSigHits = sigTokens.count { tok -> tok in metaWords || metaWords.any { w -> w.startsWith(tok) || (tok.length >= 4 && BibliographicMath.levenshteinSimilarity(w, tok) >= 0.82f) } }

    val sigCoverage = if (sigTokens.isEmpty()) 1f else overallSigHits.toFloat() / sigTokens.size.toFloat()
    val titleCoverage = if (sigTokens.isEmpty()) 0f else titleSigHits.toFloat() / sigTokens.size.toFloat()
    val authorCoverage = if (sigTokens.isEmpty()) 0f else authorSigHits.toFloat() / sigTokens.size.toFloat()

    val qNorm = intent.normalizedQuery
    val exactTitleMatch = normTitleFull == qNorm || normCoreTitle == qNorm || normOriginal == qNorm
    val titlePrefixMatch = normTitleFull.startsWith(qNorm) || normCoreTitle.startsWith(qNorm)
    val exactAuthorMatch = normAuthors == qNorm || normAuthors.contains(qNorm)

    val titleSim = BibliographicMath.tokenSimilarity(normCoreTitle.ifEmpty { normTitleFull }, qNorm)
    val authorSim = if (normAuthors.isNotEmpty()) BibliographicMath.tokenSimilarity(normAuthors, qNorm) else 0f

    val titleMatchScore =
      when {
        exactTitleMatch -> 120
        titlePrefixMatch -> 102
        titleCoverage >= 0.99f -> 88
        titleSim >= 0.80f -> 74
        titleCoverage >= 0.50f -> (42 + (titleCoverage * 30)).toInt()
        else -> (titleSim * 35).toInt()
      }

    val authorMatchScore =
      when {
        exactAuthorMatch -> 110
        authorCoverage >= 0.99f -> 95
        authorSim >= 0.80f -> 78
        authorCoverage >= 0.50f -> (40 + (authorCoverage * 30)).toInt()
        else -> (authorSim * 30).toInt()
      }

    val combinedScore = maxOf(titleMatchScore, authorMatchScore) +
      (if (titleMatchScore >= 50 && authorMatchScore >= 40) 25 else 0)

    // Anti-off-topic / stopword-only floor:
    // Rejects full-text noise ("Oracle DBA", random religious books matching "que", "tal", "um")
    val hasMinimumMatch =
      when {
        sigTokens.size == 1 -> sigCoverage >= 1f || titleSim >= 0.72f || authorSim >= 0.72f
        sigTokens.size == 2 -> sigCoverage >= 0.5f || titleSim >= 0.62f || authorSim >= 0.62f
        else -> sigCoverage >= 0.45f || titleSim >= 0.55f || authorSim >= 0.55f
      }

    return CandidateMatchEvaluation(
      accepted = hasMinimumMatch,
      queryMatchScore = combinedScore,
      titleMatchScore = titleMatchScore,
      authorMatchScore = authorMatchScore,
      significantTokenCoverage = sigCoverage,
      rejectionReason = if (hasMinimumMatch) null else "low_bibliographic_relevance",
    )
  }

  /**
   * Computes whether an ambiguous/short query behaved predominantly like an author search
   * across the accepted candidates (e.g., "Machado de Assis" -> 80% matched author name)
   * without breaking short titles (e.g., "Dom Casmurro" or "Coraline").
   */
  fun isPredominantlyAuthorQuery(
    evaluations: List<CandidateMatchEvaluation>,
    intent: ParsedSearchIntent?,
  ): Boolean {
    if (intent == null) return false
    if (intent.intentType == QueryIntentType.EXPLICIT_AUTHOR) return true
    if (intent.intentType != QueryIntentType.AMBIGUOUS_OR_SHORT) return false
    val accepted = evaluations.filter { it.accepted }
    if (accepted.size < 2) return false
    val strongAuthorMatches = accepted.count { it.authorMatchScore >= 80 && it.titleMatchScore < 85 }
    return strongAuthorMatches.toFloat() / accepted.size.toFloat() >= 0.55f
  }
}
