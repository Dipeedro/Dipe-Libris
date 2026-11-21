package com.example.core.bibliographic.domain

import com.example.core.model.BibliographicMath
import com.example.core.model.MatchTier
import com.example.core.model.MetadataSource
import com.example.core.model.SearchEditionCandidate
import kotlin.math.ceil

enum class MatchedTitleField {
  NONE,
  WORK_TITLE,
  EDITION_TITLE,
  ORIGINAL_TITLE,
  SERIES_NAME,
  AUTHOR_ONLY,
  WORK_BRIDGE,
}

data class CandidateMatchEvaluation(
  val accepted: Boolean,
  val queryMatchScore: Int,
  val titleMatchScore: Int,
  val authorMatchScore: Int,
  val significantTokenCoverage: Float,
  val matchTier: MatchTier = MatchTier.EXACT_WORK,
  val matchedField: MatchedTitleField = MatchedTitleField.NONE,
  val identityMatchBucket: Int = 0,
  val isAcademicOrDerivative: Boolean = false,
  val hasCanonicalAuthorConsensus: Boolean = false,
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

  private val hardGuideSpamRegex =
    Regex(
      "\\b(summary\\s*(?:&|and)\\s*analysis\\s*(?:of)?|study\\s+guide\\s*[:\\-]|analise\\s+do\\s+livro|resumo\\s+da\\s+serie|resumo\\s+(?:&|e)\\s+analise|trivia\\s+(?:&|and)\\s+quiz|unofficial\\s+(?:guide|companion))\\b",
      RegexOption.IGNORE_CASE,
    )

  private val derivativeSummaryPublishers =
    setOf("sparknotes", "bookrags", "brightsummaries", "quickread", "cliffsnotes", "instaread", "supersummary")

  private val academicSubtitleOrTitleRegex =
    Regex(
      "\\b(uma\\s+reflexao|reflexao\\s+sobre|o\\s+mundo\\s+contemporaneo|estudo\\s+critico|leitura\\s+critica|analise\\s+critica|ensaio(?:s)?\\s+sobre|abordagem\\s+pedagogica|dissertacao|tese\\s+de|critical\\s+essays?|philosophy\\s+and|perspectives\\s+on|companion\\s+to|critical\\s+guide|reading\\s+the)\\b",
      RegexOption.IGNORE_CASE,
    )

  private val academicCategoryTokens =
    setOf(
      "literary criticism",
      "criticism and interpretation",
      "study aids",
      "dissertations",
      "academic",
      "education",
      "literary theory",
      "philosophy",
      "social science",
    )

  private val editionOnlySubtitleRegex =
    Regex(
      "^(?:edicao(?:\\s+(?:especial|ilustrada|comemorativa|de\\s+luxo|definitiva|revista|ampliada|pocket|economica|bilingue|comentada|capa\\s+dura|integrada|unica))?|especial|ilustrada|comemorativa|capa\\s+dura|special\\s+edition|illustrated(?:\\s+edition)?|deluxe\\s+edition|anniversary\\s+edition|revised\\s+edition|volume\\s+unico|livro\\s+unico|livro\\s*\\d+|vol\\.?\\s*\\d+|part\\s*\\d+|parte\\s*\\d+)$",
      RegexOption.IGNORE_CASE,
    )

  private val benignSubtitlePrefixes =
    setOf("romance", "novela", "conto", "contos", "poemas", "fiction", "a novel", "livro", "vol", "volume", "parte", "part")

  /**
   * Avaliação contextual em lote (Etapas C + D):
   * 1. Valida título da obra, título da edição, título original, série e autor;
   * 2. Descobre consenso de autor canônico entre os acertos fortes;
   * 3. Rebaixa análises/estudos acadêmicos para T7 (DERIVATIVE_OR_ACADEMIC) sem contaminar T1;
   * 4. Propaga ponte bibliográfica (externalWorkKey / wikidata_work) para edições irmãs (ex: tradução OL).
   */
  fun classifyBatch(
    candidates: List<SearchEditionCandidate>,
    intent: ParsedSearchIntent?,
  ): List<Pair<SearchEditionCandidate, CandidateMatchEvaluation>> {
    if (candidates.isEmpty()) return emptyList()
    if (intent == null || intent.intentType == QueryIntentType.ISBN_EXACT) {
      return candidates.map {
        it to
          CandidateMatchEvaluation(
            accepted = true,
            queryMatchScore = 100,
            titleMatchScore = 100,
            authorMatchScore = 0,
            significantTokenCoverage = 1f,
            matchTier = MatchTier.EXACT_WORK,
            matchedField = MatchedTitleField.WORK_TITLE,
            identityMatchBucket = 100,
          )
      }
    }

    // Passo 1: Avaliação individual inicial
    val initial = candidates.map { it to evaluateCandidateInternal(it, intent, canonicalAuthorSurnames = emptySet()) }

    // Passo 2: Descobrir consenso de autor canônico entre acertos literários fortes
    val surnameWeights = linkedMapOf<String, Int>()
    val authorTitlePairCount =
      initial.count { (_, eval) -> eval.accepted && !eval.isAcademicOrDerivative && eval.titleMatchScore >= 80 }

    if (authorTitlePairCount >= 1) {
      for ((cand, eval) in initial) {
        if (!eval.accepted || eval.isAcademicOrDerivative) continue
        if (eval.titleMatchScore < 85 && cand.externalWorkKey == null) continue
        val surname = BibliographicMath.normalizeAuthorSurname(cand.primaryAuthorsText)
        if (surname.length >= 2) {
          val categoryNorm = BibliographicMath.normalizeText(cand.categories.joinToString(" "))
          val isAcademicCat = academicCategoryTokens.any { it in categoryNorm }
          if (!isAcademicCat) {
            val weight =
              when {
                eval.identityMatchBucket >= 90 -> 4
                eval.titleMatchScore >= 95 -> 3
                else -> 1
              } + (cand.sources.size.coerceAtMost(3)) + (if (cand.workEditionCount >= 5) 2 else 0)
            surnameWeights[surname] = (surnameWeights[surname] ?: 0) + weight
          }
        }
      }
    }
    val topWeight = surnameWeights.values.maxOrNull() ?: 0
    val canonicalSurnames =
      if (topWeight >= 4) {
        surnameWeights.filterValues { it >= (topWeight * 0.65f).toInt().coerceAtLeast(3) }.keys.toSet()
      } else {
        emptySet()
      }

    // Passo 3: Reavaliar lote aplicando consenso canônico + Work-Bridge de edições traduzidas/irmãs
    val validatedWorkKeys = linkedSetOf<String>()
    val validatedWikidataKeys = linkedSetOf<String>()
    for ((cand, eval) in initial) {
      if (eval.accepted && !eval.isAcademicOrDerivative && (eval.titleMatchScore >= 85 || eval.authorMatchScore >= 90)) {
        cand.externalWorkKey?.let { validatedWorkKeys.add(it) }
        cand.externalIds["wikidata_work"]?.let { validatedWikidataKeys.add(it) }
      }
    }

    val refined =
      candidates.map { cand ->
        val eval = evaluateCandidateInternal(cand, intent, canonicalSurnames)
        if (eval.accepted) {
          cand to eval
        } else {
          // Ponte bibliográfica de tradução/edição irmã via Work Key ou Wikidata
          val sharesWorkBridge =
            (cand.externalWorkKey != null && cand.externalWorkKey in validatedWorkKeys) ||
              (cand.externalIds["wikidata_work"] != null && cand.externalIds["wikidata_work"] in validatedWikidataKeys)
          if (sharesWorkBridge && !isHardSpam(cand, intent)) {
            cand to
              eval.copy(
                accepted = true,
                queryMatchScore = maxOf(eval.queryMatchScore, 75),
                matchTier = MatchTier.TRANSLATION_OR_ALT_TITLE,
                matchedField = MatchedTitleField.WORK_BRIDGE,
                identityMatchBucket = 70,
                hasCanonicalAuthorConsensus =
                  BibliographicMath.normalizeAuthorSurname(cand.primaryAuthorsText) in canonicalSurnames,
                rejectionReason = null,
              )
          } else {
            cand to eval
          }
        }
      }

    // Passo 4: Detectar se a pesquisa inteira é predominantemente de autor (ex.: "Machado de Assis")
    val acceptedOnly = refined.filter { it.second.accepted }
    val predominantlyAuthor = isPredominantlyAuthorQuery(acceptedOnly.map { it.second }, intent)
    if (!predominantlyAuthor) return refined

    val authorSurnames =
      acceptedOnly
        .filter { !it.second.isAcademicOrDerivative && it.second.authorMatchScore >= 85 }
        .mapNotNull { BibliographicMath.normalizeAuthorSurname(it.first.primaryAuthorsText).takeIf { s -> s.isNotEmpty() } }
        .toSet()

    return refined.map { (cand, eval) ->
      if (!eval.accepted) return@map cand to eval
      if (eval.authorMatchScore >= 85) {
        // Obra escrita PELO autor pesquisado (T5 autor)
        cand to
          eval.copy(
            matchTier = MatchTier.PROBABLE_TITLE_OR_AUTHOR_WORK,
            identityMatchBucket = maxOf(eval.identityMatchBucket, 80),
            hasCanonicalAuthorConsensus = true,
          )
      } else {
        // Título menciona o autor, mas quem escreveu foi outro pesquisador/ensaísta -> T7 (estudo/biografia sobre ele)
        val candSurname = BibliographicMath.normalizeAuthorSurname(cand.primaryAuthorsText)
        val authoredBySomeoneElse =
          candSurname.isNotEmpty() && authorSurnames.isNotEmpty() && candSurname !in authorSurnames
        if (authoredBySomeoneElse && eval.authorMatchScore < 40) {
          cand to
            eval.copy(
              matchTier = MatchTier.DERIVATIVE_OR_ACADEMIC,
              isAcademicOrDerivative = true,
              identityMatchBucket = 20,
            )
        } else {
          cand to eval
        }
      }
    }
  }

  fun evaluateCandidate(
    candidate: SearchEditionCandidate,
    intent: ParsedSearchIntent?,
  ): CandidateMatchEvaluation =
    evaluateCandidateInternal(candidate, intent, canonicalAuthorSurnames = emptySet())

  private fun isHardSpam(candidate: SearchEditionCandidate, intent: ParsedSearchIntent): Boolean {
    val combinedTitleText =
      listOfNotNull(candidate.workTitle, candidate.subtitle, candidate.editionTitle)
        .joinToString(" ")
        .trim()
    val synopsisRaw = candidate.synopsisCandidates.firstOrNull()?.text.orEmpty()
    val publisherNorm = BibliographicMath.normalizeText(candidate.publisher)
    val categoriesNorm = BibliographicMath.normalizeText(candidate.categories.joinToString(" "))

    if (!intent.allowColoringBooks && coloringCompoundRegex.containsMatchIn(combinedTitleText)) {
      return true
    }
    if (!intent.allowNotebooks) {
      val hasStrongStationeryPhrase = stationeryStrongRegex.containsMatchIn(combinedTitleText)
      val hasSecondaryStationeryCue =
        stationerySecondarySignals.containsMatchIn("$combinedTitleText $synopsisRaw $categoriesNorm")
      val endsWithNotebookOrJournal =
        Regex("\\b(notebook|journal)\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(candidate.workTitle)
      if (hasStrongStationeryPhrase || (endsWithNotebookOrJournal && (hasSecondaryStationeryCue || candidate.contributors.isEmpty()))) {
        return true
      }
    }
    if (!intent.allowGuidesOrAnalyses) {
      val hasHardSpamPhrase = hardGuideSpamRegex.containsMatchIn("$combinedTitleText ${synopsisRaw.take(180)}")
      val isSummaryPublisher = derivativeSummaryPublishers.any { it in publisherNorm }
      val startsWithResumoOrAnalise =
        Regex("^\\s*(resumo|analise|study\\s+guide|summary)\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE)
          .containsMatchIn(candidate.workTitle)
      if (hasHardSpamPhrase || isSummaryPublisher || startsWithResumoOrAnalise) {
        return true
      }
    }
    return false
  }

  private fun evaluateCandidateInternal(
    candidate: SearchEditionCandidate,
    intent: ParsedSearchIntent?,
    canonicalAuthorSurnames: Set<String>,
  ): CandidateMatchEvaluation {
    if (intent == null || intent.intentType == QueryIntentType.ISBN_EXACT) {
      return CandidateMatchEvaluation(
        accepted = true,
        queryMatchScore = 100,
        titleMatchScore = 100,
        authorMatchScore = 0,
        significantTokenCoverage = 1f,
        matchTier = MatchTier.EXACT_WORK,
        matchedField = MatchedTitleField.WORK_TITLE,
        identityMatchBucket = 100,
      )
    }

    val titleRaw = candidate.workTitle
    val editionTitleRaw = candidate.editionTitle?.takeIf { it.isNotBlank() }
    val originalTitleRaw = candidate.originalTitle?.takeIf { it.isNotBlank() }
    val subtitleRaw = candidate.subtitle.orEmpty()
    val combinedWorkText = "$titleRaw $subtitleRaw".trim()
    val combinedEditionText =
      editionTitleRaw?.let { ed ->
        if (subtitleRaw.isNotBlank() && !ed.contains(subtitleRaw, ignoreCase = true)) "$ed $subtitleRaw" else ed
      }

    val synopsisRaw = candidate.synopsisCandidates.firstOrNull()?.text.orEmpty()
    val publisherNorm = BibliographicMath.normalizeText(candidate.publisher)
    val categoriesNorm = BibliographicMath.normalizeText(candidate.categories.joinToString(" "))

    // 1. Filtro de papelaria / colorir / guias apócrifos comerciais
    if (!intent.allowColoringBooks && coloringCompoundRegex.containsMatchIn(combinedWorkText)) {
      return CandidateMatchEvaluation(false, 0, 0, 0, 0f, rejectionReason = "coloring_book")
    }
    if (!intent.allowNotebooks) {
      val hasStrongStationeryPhrase = stationeryStrongRegex.containsMatchIn(combinedWorkText)
      val hasSecondaryStationeryCue =
        stationerySecondarySignals.containsMatchIn("$combinedWorkText $synopsisRaw $categoriesNorm")
      val endsWithNotebookOrJournal =
        Regex("\\b(notebook|journal)\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(titleRaw)
      if (hasStrongStationeryPhrase || (endsWithNotebookOrJournal && (hasSecondaryStationeryCue || candidate.contributors.isEmpty()))) {
        return CandidateMatchEvaluation(false, 0, 0, 0, 0f, rejectionReason = "stationery_notebook")
      }
    }
    if (!intent.allowGuidesOrAnalyses) {
      val hasHardSpamPhrase = hardGuideSpamRegex.containsMatchIn("$combinedWorkText ${synopsisRaw.take(180)}")
      val isSummaryPublisher = derivativeSummaryPublishers.any { it in publisherNorm }
      val startsWithResumoOrAnalise =
        Regex("^\\s*(resumo|analise|study\\s+guide|summary)\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE)
          .containsMatchIn(titleRaw)
      if (hasHardSpamPhrase || isSummaryPublisher || startsWithResumoOrAnalise) {
        return CandidateMatchEvaluation(false, 0, 0, 0, 0f, rejectionReason = "derivative_summary_or_guide")
      }
    }

    // 2. Preparar campos normalizados (workTitle, editionTitle, originalTitle, series, authors)
    val qTargetTitle =
      if (intent.intentType == QueryIntentType.TITLE_AND_AUTHOR && !intent.titleQueryPart.isNullOrBlank()) {
        BibliographicMath.normalizeText(intent.titleQueryPart)
      } else {
        intent.normalizedQuery
      }
    val qTargetAuthor =
      intent.authorQueryPart?.let { BibliographicMath.normalizeText(it) }
    val sigTokensTitle =
      if (intent.intentType == QueryIntentType.TITLE_AND_AUTHOR && !intent.titleQueryPart.isNullOrBlank()) {
        qTargetTitle.split(' ').filter { it.length > 1 }.ifEmpty { intent.significantTokens }
      } else {
        intent.significantTokens
      }

    val titleForms =
      buildList {
        add(TitleForm(MatchedTitleField.WORK_TITLE, titleRaw, combinedWorkText, subtitleRaw))
        if (editionTitleRaw != null && BibliographicMath.normalizeText(editionTitleRaw) != BibliographicMath.normalizeText(titleRaw)) {
          add(TitleForm(MatchedTitleField.EDITION_TITLE, editionTitleRaw, combinedEditionText ?: editionTitleRaw, subtitleRaw))
        }
        if (originalTitleRaw != null && BibliographicMath.normalizeText(originalTitleRaw) != BibliographicMath.normalizeText(titleRaw)) {
          add(TitleForm(MatchedTitleField.ORIGINAL_TITLE, originalTitleRaw, originalTitleRaw, ""))
        }
      }

    val allowBilingualModifierSynonyms = intent.allowColoringBooks || intent.allowNotebooks
    val bestFormMatch =
      titleForms
        .map { form -> evaluateTitleForm(form, qTargetTitle, sigTokensTitle, allowBilingualModifierSynonyms) }
        .maxByOrNull { it.score }
        ?: TitleFormMatch(MatchedTitleField.NONE, 0, 0f, exactFull = false, exactBase = false, orderedContiguous = false, cleanSubtitle = true, fuzzyOnly = false)

    val normSeries = BibliographicMath.normalizeText(candidate.seriesName)
    val seriesWords = normSeries.split(' ').filter { it.isNotBlank() }.toSet()
    val seriesHits = countStrictTokenHits(sigTokensTitle, seriesWords, allowMorphological = true)
    val seriesCoverage = if (sigTokensTitle.isEmpty()) 0f else seriesHits.toFloat() / sigTokensTitle.size.toFloat()

    val normAuthors = BibliographicMath.normalizeText(candidate.primaryAuthorsText)
    val authorWords = normAuthors.split(' ').filter { it.isNotBlank() }.toSet()
    val authorTokens =
      if (intent.intentType == QueryIntentType.TITLE_AND_AUTHOR && qTargetAuthor != null) {
        qTargetAuthor.split(' ').filter { it.length > 1 }
      } else {
        intent.significantTokens
      }
    val authorSigHits = countStrictTokenHits(authorTokens, authorWords, allowMorphological = true)
    val authorCoverage = if (authorTokens.isEmpty()) 0f else authorSigHits.toFloat() / authorTokens.size.toFloat()
    val authorQueryString = qTargetAuthor ?: intent.normalizedQuery
    val exactAuthorMatch =
      normAuthors.isNotEmpty() &&
        (normAuthors == authorQueryString ||
          (authorTokens.size >= 2 && authorCoverage >= 1f) ||
          (normAuthors.contains(authorQueryString) && authorQueryString.length >= 6))

    val authorMatchScore =
      when {
        exactAuthorMatch -> 115
        authorCoverage >= 0.99f -> 98
        authorCoverage >= 0.60f && authorSigHits >= 1 -> (50 + (authorCoverage * 30)).toInt()
        else -> 0
      }

    val effectiveTitleField =
      if (seriesCoverage > bestFormMatch.coverage && seriesCoverage >= 0.99f && bestFormMatch.score < 85) {
        MatchedTitleField.SERIES_NAME
      } else {
        bestFormMatch.field
      }

    val effectiveTitleScore =
      maxOf(bestFormMatch.score, if (seriesCoverage >= 0.99f) 84 else 0)
    val effectiveTitleCoverage = maxOf(bestFormMatch.coverage, seriesCoverage)

    // Cobertura mínima rigorosa (sem fallback de Levenshtein de frase inteira que aprova "O Senhor dos Anais"!)
    val hasMinimumMatch =
      when {
        intent.intentType == QueryIntentType.EXPLICIT_AUTHOR ->
          authorCoverage >= 0.99f || exactAuthorMatch
        intent.intentType == QueryIntentType.TITLE_AND_AUTHOR ->
          effectiveTitleCoverage >= 0.99f && authorSigHits >= 1
        sigTokensTitle.size <= 1 ->
          effectiveTitleCoverage >= 0.99f || authorCoverage >= 0.99f
        sigTokensTitle.size == 2 ->
          // Para 2 tokens significativos (ex: "Senhor Aneis", "Jogos Vorazes"), exige AMBOS tokens!
          effectiveTitleCoverage >= 0.99f || authorCoverage >= 0.99f ||
            (effectiveTitleCoverage >= 0.5f && authorCoverage >= 0.5f && !intent.isExplicitTitleOnly())
        sigTokensTitle.size == 3 ->
          effectiveTitleCoverage >= 0.99f || authorCoverage >= 0.99f ||
            (effectiveTitleCoverage >= 0.66f && (bestFormMatch.orderedContiguous || authorSigHits >= 1))
        else ->
          effectiveTitleCoverage >= ceil(sigTokensTitle.size * 0.72f) / sigTokensTitle.size.toFloat() ||
            authorCoverage >= 0.99f
      }

    if (!hasMinimumMatch) {
      return CandidateMatchEvaluation(
        accepted = false,
        queryMatchScore = 0,
        titleMatchScore = effectiveTitleScore,
        authorMatchScore = authorMatchScore,
        significantTokenCoverage = maxOf(effectiveTitleCoverage, authorCoverage),
        rejectionReason = "low_bibliographic_relevance",
      )
    }

    // 3. Classificação contextual acadêmica / derivada (T7)
    val candSurname = BibliographicMath.normalizeAuthorSurname(candidate.primaryAuthorsText)
    val matchesCanonicalAuthor =
      candSurname.isNotEmpty() && candSurname in canonicalAuthorSurnames
    val conflictsWithCanonicalAuthor =
      canonicalAuthorSurnames.isNotEmpty() && candSurname.isNotEmpty() && !matchesCanonicalAuthor

    val rawFormForSubCheck =
      when (bestFormMatch.field) {
        MatchedTitleField.EDITION_TITLE -> editionTitleRaw ?: titleRaw
        else -> titleRaw
      }
    val struct = BibliographicMath.extractTitleStructure(rawFormForSubCheck, candidate.subtitle)
    val hasAcademicSubtitleSignal =
      academicSubtitleOrTitleRegex.containsMatchIn("$combinedWorkText ${synopsisRaw.take(180)}")
    val hasAcademicCategory =
      academicCategoryTokens.any { it in categoriesNorm } &&
        !Regex("\\b(fiction|ficcao|fantasy|fantasia|romance|juvenile)\\b").containsMatchIn(categoriesNorm)
    val hasExplanatoryNonEditionSubtitle =
      struct.subtitleNormalized.isNotEmpty() &&
        !isEditionOrVolumeSubtitle(struct.subtitleNormalized) &&
        !qTargetTitle.contains(struct.subtitleNormalized)

    val isAcademicDerivative =
      !intent.allowGuidesOrAnalyses &&
        effectiveTitleCoverage >= 0.5f &&
        authorMatchScore < 85 &&
        (
          hasAcademicSubtitleSignal ||
            (hasAcademicCategory && conflictsWithCanonicalAuthor) ||
            (hasAcademicCategory && hasExplanatoryNonEditionSubtitle) ||
            (conflictsWithCanonicalAuthor && hasExplanatoryNonEditionSubtitle && !bestFormMatch.exactFull)
        )

    // 4. Atribuição de MatchTier (T1..T7)
    val isCrossRefAcademic = MetadataSource.CROSSREF in candidate.sources && !isAcademicCategorySafe(categoriesNorm)
    val finalIsAcademic = isAcademicDerivative || (isCrossRefAcademic && !intent.allowGuidesOrAnalyses && conflictsWithCanonicalAuthor)

    val matchTier =
      when {
        finalIsAcademic -> MatchTier.DERIVATIVE_OR_ACADEMIC
        struct.bundleKind == com.example.core.model.TitleBundleKind.COLLECTION_OR_BOX ->
          MatchTier.COLLECTION_OR_BOX_SET
        struct.bundleKind == com.example.core.model.TitleBundleKind.SERIES_VOLUME &&
          !bestFormMatch.exactFull ->
          MatchTier.SERIES_VOLUME_MATCH
        bestFormMatch.exactFull || (bestFormMatch.exactBase && bestFormMatch.cleanSubtitle) ->
          if (bestFormMatch.field == MatchedTitleField.EDITION_TITLE || bestFormMatch.field == MatchedTitleField.ORIGINAL_TITLE) {
            MatchTier.TRANSLATION_OR_ALT_TITLE
          } else {
            MatchTier.EXACT_WORK
          }
        bestFormMatch.field == MatchedTitleField.EDITION_TITLE || bestFormMatch.field == MatchedTitleField.ORIGINAL_TITLE ->
          MatchTier.TRANSLATION_OR_ALT_TITLE
        bestFormMatch.exactBase && hasExplanatoryNonEditionSubtitle && !conflictsWithCanonicalAuthor ->
          // Subtítulo legítimo da própria série/autor (ex: "O Senhor dos Anéis: A Sociedade do Anel" por Tolkien)
          if (struct.bundleKind == com.example.core.model.TitleBundleKind.OMNIBUS_SINGLE_VOLUME) {
            MatchTier.EXACT_WORK
          } else {
            MatchTier.SERIES_VOLUME_MATCH
          }
        bestFormMatch.orderedContiguous && effectiveTitleCoverage >= 0.99f ->
          MatchTier.PROBABLE_TITLE_OR_AUTHOR_WORK
        authorMatchScore >= 95 && effectiveTitleScore < 70 ->
          MatchTier.PROBABLE_TITLE_OR_AUTHOR_WORK
        effectiveTitleCoverage >= 0.95f && !bestFormMatch.fuzzyOnly ->
          MatchTier.PROBABLE_TITLE_OR_AUTHOR_WORK
        else -> MatchTier.PARTIAL_OR_FUZZY_MATCH
      }

    val identityBucket =
      when {
        finalIsAcademic -> 10
        bestFormMatch.exactFull -> 100
        bestFormMatch.exactBase && bestFormMatch.cleanSubtitle -> 95
        bestFormMatch.orderedContiguous && effectiveTitleCoverage >= 0.99f -> 85
        effectiveTitleCoverage >= 0.99f -> 75
        authorMatchScore >= 95 -> 70
        else -> 40
      }

    val combinedScore =
      maxOf(effectiveTitleScore, authorMatchScore) +
        (if (effectiveTitleScore >= 50 && authorMatchScore >= 40) 25 else 0) +
        (if (matchesCanonicalAuthor) 12 else 0)

    return CandidateMatchEvaluation(
      accepted = true,
      queryMatchScore = combinedScore,
      titleMatchScore = effectiveTitleScore,
      authorMatchScore = authorMatchScore,
      significantTokenCoverage = maxOf(effectiveTitleCoverage, authorCoverage),
      matchTier = matchTier,
      matchedField =
        if (authorMatchScore > effectiveTitleScore && effectiveTitleScore < 50) {
          MatchedTitleField.AUTHOR_ONLY
        } else {
          effectiveTitleField
        },
      identityMatchBucket = identityBucket,
      isAcademicOrDerivative = finalIsAcademic,
      hasCanonicalAuthorConsensus = matchesCanonicalAuthor,
      rejectionReason = null,
    )
  }

  private fun ParsedSearchIntent.isExplicitTitleOnly(): Boolean =
    intentType == QueryIntentType.EXPLICIT_TITLE

  private fun isAcademicCategorySafe(categoriesNorm: String): Boolean =
    Regex("\\b(fiction|ficcao|fantasy|romance|literature)\\b").containsMatchIn(categoriesNorm)

  internal fun isEditionOrVolumeSubtitle(subtitleNorm: String): Boolean {
    if (subtitleNorm.isBlank()) return true
    if (subtitleNorm in benignSubtitlePrefixes) return true
    if (editionOnlySubtitleRegex.matches(subtitleNorm)) return true
    return Regex("^(?:livro|vol|volume|parte|part|tomo)\\s*[0-9ivx]+$").matches(subtitleNorm)
  }

  private data class TitleForm(
    val field: MatchedTitleField,
    val baseRaw: String,
    val fullRaw: String,
    val subtitleRaw: String,
  )

  private data class TitleFormMatch(
    val field: MatchedTitleField,
    val score: Int,
    val coverage: Float,
    val exactFull: Boolean,
    val exactBase: Boolean,
    val orderedContiguous: Boolean,
    val cleanSubtitle: Boolean,
    val fuzzyOnly: Boolean,
  )

  private fun evaluateTitleForm(
    form: TitleForm,
    qNorm: String,
    sigTokens: List<String>,
    allowBilingualModifierSynonyms: Boolean = false,
  ): TitleFormMatch {
    val normFull = BibliographicMath.normalizeText(form.fullRaw)
    val normBaseWithArticle = BibliographicMath.normalizeBaseTitle(form.baseRaw)
    val normCore = BibliographicMath.normalizeTitleCore(form.baseRaw)
    val struct = BibliographicMath.extractTitleStructure(form.baseRaw, form.subtitleRaw)

    val cleanSub = isEditionOrVolumeSubtitle(struct.subtitleNormalized)
    val exactFull =
      normFull == qNorm ||
        (normBaseWithArticle == qNorm && cleanSub) ||
        (normCore == qNorm && cleanSub)
    val exactBase = normBaseWithArticle == qNorm || normCore == qNorm

    val fullWords = normFull.split(' ').filter { it.isNotBlank() }
    val qWords = qNorm.split(' ').filter { it.isNotBlank() }
    val orderedContiguous = containsSubsequence(fullWords, qWords)

    val fullWordSet = fullWords.toSet()
    val strictHits =
      countStrictTokenHits(
        sigTokens,
        fullWordSet,
        allowMorphological = true,
        allowBilingualModifierSynonyms = allowBilingualModifierSynonyms,
      )
    val coverage = if (sigTokens.isEmpty()) 1f else strictHits.toFloat() / sigTokens.size.toFloat()

    // Fallback ortográfico restrito SOMENTE para tokens >= 6 chars (nunca substitui "aneis" por "anais")
    val fuzzyHits =
      if (coverage < 1f && sigTokens.all { it.length >= 6 }) {
        sigTokens.count { tok ->
          tok in fullWordSet || fullWordSet.any { w -> BibliographicMath.levenshteinSimilarity(w, tok) >= 0.85f }
        }
      } else {
        strictHits
      }
    val fuzzyCoverage = if (sigTokens.isEmpty()) 1f else fuzzyHits.toFloat() / sigTokens.size.toFloat()
    val usedFuzzy = fuzzyCoverage > coverage

    val score =
      when {
        exactFull -> 125
        exactBase && cleanSub -> 118
        orderedContiguous && coverage >= 0.99f -> 108
        exactBase -> 96
        normFull.startsWith(qNorm) && coverage >= 0.99f -> 92
        coverage >= 0.99f -> 86
        coverage >= 0.70f -> (58 + (coverage * 24)).toInt()
        usedFuzzy && fuzzyCoverage >= 0.99f -> 52
        else -> (maxOf(coverage, fuzzyCoverage) * 35).toInt()
      }

    return TitleFormMatch(
      field = form.field,
      score = score,
      coverage = maxOf(coverage, if (usedFuzzy) fuzzyCoverage else coverage),
      exactFull = exactFull,
      exactBase = exactBase,
      orderedContiguous = orderedContiguous,
      cleanSubtitle = cleanSub,
      fuzzyOnly = usedFuzzy,
    )
  }

  private fun countStrictTokenHits(
    queryTokens: List<String>,
    fieldWords: Set<String>,
    allowMorphological: Boolean,
    allowBilingualModifierSynonyms: Boolean = false,
  ): Int =
    queryTokens.count { tok ->
      val bilingualSynonyms =
        if (allowBilingualModifierSynonyms) {
          when (tok) {
            "livro" -> setOf("book", "books")
            "book" -> setOf("livro", "livros")
            "colorir" -> setOf("coloring", "colouring", "color")
            "coloring", "colouring" -> setOf("colorir")
            "caderno" -> setOf("notebook", "journal", "diary")
            "notebook" -> setOf("caderno")
            "diario" -> setOf("diary", "journal")
            else -> emptySet()
          }
        } else {
          emptySet()
        }
      when {
        tok in fieldWords -> true
        bilingualSynonyms.any { it in fieldWords } -> true
        allowMorphological && tok.length >= 4 ->
          fieldWords.any { w ->
            w.startsWith(tok) && w.length <= tok.length + 2
          }
        else -> false
      }
    }

  private fun containsSubsequence(haystack: List<String>, needle: List<String>): Boolean {
    if (needle.isEmpty()) return true
    if (needle.size > haystack.size) return false
    for (start in 0..(haystack.size - needle.size)) {
      var matched = true
      for (i in needle.indices) {
        if (haystack[start + i] != needle[i]) {
          matched = false
          break
        }
      }
      if (matched) return true
    }
    return false
  }

  /**
   * Detecta se uma consulta curta/ambígua comportou-se predominantemente como busca por autor
   * entre os candidatos literários aceitos (ex: "Machado de Assis" vs "Dom Casmurro").
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
    val hasExactLiteralTitleMatch =
      accepted.any {
        (it.matchTier == MatchTier.EXACT_WORK || it.matchTier == MatchTier.TRANSLATION_OR_ALT_TITLE) &&
          it.titleMatchScore >= 115 &&
          it.authorMatchScore < 40
      }
    if (hasExactLiteralTitleMatch) return false
    val strongAuthorMatches = accepted.count { it.authorMatchScore >= 85 && it.titleMatchScore < 85 }
    return strongAuthorMatches.toFloat() / accepted.size.toFloat() >= 0.55f
  }
}
