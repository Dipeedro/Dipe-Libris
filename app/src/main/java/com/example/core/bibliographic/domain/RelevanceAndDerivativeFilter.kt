package com.example.core.bibliographic.domain

import com.example.core.model.BibliographicMath
import com.example.core.model.MatchTier
import com.example.core.model.MetadataSource
import com.example.core.model.SearchEditionCandidate
import java.text.Normalizer
import java.util.Locale
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

  private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")

  private fun stripAccentsLower(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val decomposed = Normalizer.normalize(raw.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
    return diacriticsRegex
      .replace(decomposed, "")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

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
      "\\b(" +
        "summary\\s*(?:&|and)\\s*analysis\\s*(?:of)?|" +
        "(?:resumo|analise)\\s+(?:completo|completa|detalhado|detalhada|rapido|rapida)?\\s*(?:&|e)\\s*(?:analise|resumo)|" +
        "analise\\s+(?:do\\s+livro|da\\s+obra)|" +
        "resumo\\s+(?:da\\s+serie|da\\s+obra|do\\s+livro)|" +
        "fichamento\\b|" +
        "perguntas\\s*(?:&|e)\\s*respostas|" +
        "study\\s+guide\\s*[:\\-]|" +
        "complete\\s+study\\s+guide|" +
        "trivia\\s*(?:&|and)\\s*quiz|" +
        "quiz\\s+book\\b|" +
        "unofficial\\s+(?:guide|companion|trivia)|" +
        "guia\\s+nao\\s+oficial|" +
        "guia\\s+de\\s+estudo" +
        ")\\b",
      RegexOption.IGNORE_CASE,
    )

  private val derivativeSummaryPublishers =
    setOf(
      "sparknotes",
      "bookrags",
      "brightsummaries",
      "quickread",
      "cliffsnotes",
      "instaread",
      "supersummary",
      "resumocast",
      "12min",
    )

  private val academicSubtitleOrTitleRegex =
    Regex(
      "\\b(" +
        "uma\\s+reflexao|" +
        "reflexao\\s+sobre|" +
        "o\\s+mundo\\s+contemporaneo|" +
        "estudo\\s+critico|" +
        "estudos?\\s+(?:sobre|criticos?|literarios?)|" +
        "leitura\\s+critica|" +
        "analise\\s+(?:critica|literaria|filosofica)|" +
        "ensaio(?:s)?\\s+sobre|" +
        "abordagem\\s+pedagogica|" +
        "dissertacao|" +
        "tese\\s+de|" +
        "monografia|" +
        "(?:e|and)\\s+a\\s+(?:filosofia|psicologia|ciencia|politica|historia|educacao|sociedade|etica|religiao)|" +
        "(?:e|and)\\s+o\\s+direito|" +
        "filosofia\\s+(?:e|de|em|na|no)\\b|" +
        "philosophy\\s+and|" +
        "and\\s+philosophy\\b|" +
        "na\\s+sala\\s+de\\s+aula|" +
        "perspectivas?\\s+(?:sobre|criticas?)|" +
        "aspectos?\\s+(?:filosoficos|sociais|politicos|psicologicos)|" +
        "guia\\s+de\\s+leitura|" +
        "guia\\s+do\\s+leitor|" +
        "critical\\s+(?:essays?|guide|reading)|" +
        "perspectives\\s+on|" +
        "companion\\s+to|" +
        "reading\\s+the" +
        ")\\b",
      RegexOption.IGNORE_CASE,
    )

  private val explanatoryCompanionOrAcademicCueRegex =
    Regex(
      "\\b(" +
        "guia|guide|manual|handbook|companion|" +
        "analise|analysis|resumo|summary|reflexao|estudo|study|" +
        "filosofia|philosophy|psicologia|tributo|tribute|" +
        "ilustrado|illustrated|bastidores|making\\s+of|por\\s+tras|behind\\s+the|segredos|secrets|" +
        "receitas|cookbook|trivia|quiz|fichamento|dissertacao|tese|monografia|critico|critica" +
        ")\\b",
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
      "^(?:edicao(?:\\s+(?:especial|ilustrada|comemorativa|de\\s+luxo|definitiva|revista|ampliada|pocket|economica|bilingue|comentada|critica|anotada|bolso|capa\\s+dura|integrada|unica))?|" +
        "especial|ilustrada|comemorativa|critica|anotada|capa\\s+dura|texto\\s+integral|" +
        "com\\s+(?:ilustracoes|aquarelas)(?:\\s+do\\s+autor)?|" +
        "(?:special|illustrated|deluxe|anniversary|revised|collectors?|standard|complete|unabridged)\\s+edition|" +
        "illustrated|unabridged|volume\\s+unico|livro\\s+unico|livro\\s*\\d+|vol\\.?\\s*\\d+|part\\s*\\d+|parte\\s*\\d+)$",
      RegexOption.IGNORE_CASE,
    )

  private val benignSubtitlePrefixes =
    setOf(
      "romance",
      "novela",
      "conto",
      "contos",
      "poemas",
      "fiction",
      "a novel",
      "livro",
      "vol",
      "volume",
      "parte",
      "part",
      "texto integral",
    )

  private val guideCueKeywords =
    setOf(
      "guia",
      "guide",
      "manual",
      "handbook",
      "companion",
      "tribute",
      "tributos",
      "ilustrado",
      "illustrated",
      "analise",
      "analysis",
      "resumo",
      "summary",
      "estudo",
      "study",
      "fichamento",
    )

  internal fun hasExplanatoryCompanionOrAcademicCue(text: String): Boolean {
    val norm = stripAccentsLower(text)
    if (norm.isBlank()) return false
    return academicSubtitleOrTitleRegex.containsMatchIn(norm) ||
      hardGuideSpamRegex.containsMatchIn(norm) ||
      explanatoryCompanionOrAcademicCueRegex.containsMatchIn(norm)
  }

  /**
   * Avaliação contextual em lote (Etapas C + D):
   * 1. Valida título da obra, título da edição, título original, série, autor e cruzamento Título+Autor;
   * 2. Descobre consenso de autor canônico entre os acertos fortes;
   * 3. Rebaixa análises/estudos acadêmicos para T7 (DERIVATIVE_OR_ACADEMIC) sem contaminar T1;
   * 4. Propaga ponte bibliográfica (externalWorkKey / wikidata_work / sinônimo bilíngue de assunto) para edições irmãs.
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

    // Coletar chaves de obra e equivalentes bilíngues validados para ponte segura
    val validatedWorkKeys = linkedSetOf<String>()
    val validatedWikidataKeys = linkedSetOf<String>()
    val bilingualSubjectTitles = linkedSetOf<String>()
    for ((cand, eval) in initial) {
      if (eval.accepted && !eval.isAcademicOrDerivative && (eval.titleMatchScore >= 85 || eval.authorMatchScore >= 90)) {
        cand.externalWorkKey?.let { validatedWorkKeys.add(it) }
        cand.externalIds["wikidata_work"]?.let { validatedWikidataKeys.add(it) }
        if (intent.subjectQueryPart != null && intent.allowGuidesOrAnalyses) {
          cand.originalTitle?.let { BibliographicMath.normalizeBaseTitle(it) }?.takeIf { it.isNotBlank() }?.let {
            bilingualSubjectTitles.add(it)
          }
          cand.workTitle.let { BibliographicMath.normalizeBaseTitle(it) }.takeIf { it.isNotBlank() }?.let {
            bilingualSubjectTitles.add(it)
          }
        }
      }
    }

    // Passo 3: Reavaliar lote aplicando consenso canônico + Work-Bridge de edições traduzidas/guias equivalentes
    val refined =
      candidates.map { cand ->
        val eval = evaluateCandidateInternal(cand, intent, canonicalSurnames)
        if (eval.accepted) {
          cand to eval
        } else {
          val sharesWorkBridge =
            (cand.externalWorkKey != null && cand.externalWorkKey in validatedWorkKeys) ||
              (cand.externalIds["wikidata_work"] != null && cand.externalIds["wikidata_work"] in validatedWikidataKeys)

          // Ponte bilíngue específica para guias/manuais quando o usuário buscou "<assunto> guia"
          val matchesBilingualGuideBridge =
            intent.subjectQueryPart != null &&
              intent.allowGuidesOrAnalyses &&
              bilingualSubjectTitles.isNotEmpty() &&
              !isHardSpam(cand, intent) &&
              run {
                val candFullNorm =
                  BibliographicMath.normalizeText("${cand.workTitle} ${cand.subtitle.orEmpty()} ${cand.editionTitle.orEmpty()}")
                val candWords = candFullNorm.split(' ').filter { it.isNotBlank() }.toSet()
                val hasGuideMarker = candWords.any { it in guideCueKeywords }
                val hitsAltSubject =
                  bilingualSubjectTitles.any { altSubj ->
                    val altTokens = altSubj.split(' ').filter { it.length > 1 }
                    altTokens.isNotEmpty() &&
                      countStrictTokenHits(altTokens, candWords, allowMorphological = true) >= altTokens.size
                  }
                hasGuideMarker && hitsAltSubject
              }

          if (matchesBilingualGuideBridge) {
            cand to
              eval.copy(
                accepted = true,
                queryMatchScore = maxOf(eval.queryMatchScore, 112),
                titleMatchScore = maxOf(eval.titleMatchScore, 96),
                significantTokenCoverage = 1f,
                matchTier = MatchTier.TRANSLATION_OR_ALT_TITLE,
                matchedField = MatchedTitleField.ORIGINAL_TITLE,
                identityMatchBucket = 96,
                hasCanonicalAuthorConsensus =
                  BibliographicMath.normalizeAuthorSurname(cand.primaryAuthorsText) in canonicalSurnames,
                rejectionReason = null,
              )
          } else if (sharesWorkBridge && !isHardSpam(cand, intent)) {
            val struct = BibliographicMath.extractTitleStructure(cand.workTitle, cand.subtitle)
            val bridgeCombinedNorm = stripAccentsLower("${cand.workTitle} ${cand.subtitle.orEmpty()} ${cand.synopsisCandidates.firstOrNull()?.text.orEmpty().take(180)}")
            val bridgeHasAcademicSignal =
              !intent.allowGuidesOrAnalyses &&
                (academicSubtitleOrTitleRegex.containsMatchIn(bridgeCombinedNorm) ||
                  (struct.subtitleNormalized.isNotEmpty() &&
                    !isEditionOrVolumeSubtitle(struct.subtitleNormalized) &&
                    BibliographicMath.normalizeAuthorSurname(cand.primaryAuthorsText) !in canonicalSurnames))
            if (bridgeHasAcademicSignal) {
              cand to eval
            } else {
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
            }
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
    val titleNormPunct = stripAccentsLower(candidate.workTitle)
    val combinedTitleNormPunct = stripAccentsLower(combinedTitleText)
    val synopsisNormPunct = stripAccentsLower(candidate.synopsisCandidates.firstOrNull()?.text.orEmpty())
    val publisherNorm = BibliographicMath.normalizeText(candidate.publisher)
    val categoriesNorm = BibliographicMath.normalizeText(candidate.categories.joinToString(" "))

    if (!intent.allowColoringBooks && coloringCompoundRegex.containsMatchIn(combinedTitleNormPunct)) {
      return true
    }
    if (!intent.allowNotebooks) {
      val hasStrongStationeryPhrase = stationeryStrongRegex.containsMatchIn(combinedTitleNormPunct)
      val hasSecondaryStationeryCue =
        stationerySecondarySignals.containsMatchIn("$combinedTitleNormPunct $synopsisNormPunct $categoriesNorm")
      val endsWithNotebookOrJournal =
        Regex("\\b(notebook|journal)\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(titleNormPunct)
      if (hasStrongStationeryPhrase || (endsWithNotebookOrJournal && (hasSecondaryStationeryCue || candidate.contributors.isEmpty()))) {
        return true
      }
    }
    if (!intent.allowGuidesOrAnalyses) {
      val authorNormSafe = BibliographicMath.normalizeText(candidate.primaryAuthorsText)
      val hasHardSpamPhrase = hardGuideSpamRegex.containsMatchIn("$combinedTitleNormPunct ${synopsisNormPunct.take(200)}")
      val isSummaryPublisher = derivativeSummaryPublishers.any { it in publisherNorm || it in authorNormSafe }
      val startsWithResumoOrAnalise =
        Regex(
          "^\\s*(?:resumo|analise|fichamento|study\\s+guide|summary)(?:\\s+(?:completo|completa|detalhado|detalhada|rapido|rapida|essencial|pratico|pratica))?\\s*[:\\-]\\s*",
          RegexOption.IGNORE_CASE,
        ).containsMatchIn(titleNormPunct)
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

    val titleNormPunct = stripAccentsLower(titleRaw)
    val combinedWorkNormPunct = stripAccentsLower(combinedWorkText)
    val synopsisNormPunct = stripAccentsLower(candidate.synopsisCandidates.firstOrNull()?.text.orEmpty())
    val publisherNorm = BibliographicMath.normalizeText(candidate.publisher)
    val categoriesNorm = BibliographicMath.normalizeText(candidate.categories.joinToString(" "))
    val normAuthors = BibliographicMath.normalizeText(candidate.primaryAuthorsText)

    // 1. Filtro de papelaria / colorir / guias apócrifos comerciais (em texto normalizado sem diacríticos)
    if (!intent.allowColoringBooks && coloringCompoundRegex.containsMatchIn(combinedWorkNormPunct)) {
      return CandidateMatchEvaluation(false, 0, 0, 0, 0f, rejectionReason = "coloring_book")
    }
    if (!intent.allowNotebooks) {
      val hasStrongStationeryPhrase = stationeryStrongRegex.containsMatchIn(combinedWorkNormPunct)
      val hasSecondaryStationeryCue =
        stationerySecondarySignals.containsMatchIn("$combinedWorkNormPunct $synopsisNormPunct $categoriesNorm")
      val endsWithNotebookOrJournal =
        Regex("\\b(notebook|journal)\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(titleNormPunct)
      if (hasStrongStationeryPhrase || (endsWithNotebookOrJournal && (hasSecondaryStationeryCue || candidate.contributors.isEmpty()))) {
        return CandidateMatchEvaluation(false, 0, 0, 0, 0f, rejectionReason = "stationery_notebook")
      }
    }
    if (!intent.allowGuidesOrAnalyses) {
      val hasHardSpamPhrase = hardGuideSpamRegex.containsMatchIn("$combinedWorkNormPunct ${synopsisNormPunct.take(200)}")
      val isSummaryPublisher = derivativeSummaryPublishers.any { it in publisherNorm || it in normAuthors }
      val startsWithResumoOrAnalise =
        Regex(
          "^\\s*(?:resumo|analise|fichamento|study\\s+guide|summary)(?:\\s+(?:completo|completa|detalhado|detalhada|rapido|rapida|essencial|pratico|pratica))?\\s*[:\\-]\\s*",
          RegexOption.IGNORE_CASE,
        ).containsMatchIn(titleNormPunct)
      if (hasHardSpamPhrase || isSummaryPublisher || startsWithResumoOrAnalise) {
        return CandidateMatchEvaluation(false, 0, 0, 0, 0f, rejectionReason = "derivative_summary_or_guide")
      }
    }

    // 2. Preparar campos normalizados (workTitle, editionTitle, originalTitle, series, authors)
    val subjectNorm = intent.subjectQueryPart?.let { BibliographicMath.normalizeText(it) }?.takeIf { it.isNotBlank() }
    val qTargetTitle =
      when {
        intent.intentType == QueryIntentType.TITLE_AND_AUTHOR && !intent.titleQueryPart.isNullOrBlank() ->
          BibliographicMath.normalizeText(intent.titleQueryPart)
        subjectNorm != null -> subjectNorm
        else -> intent.normalizedQuery
      }
    val qTargetAuthor =
      intent.authorQueryPart?.let { BibliographicMath.normalizeText(it) }
    val sigTokensTitle =
      when {
        intent.intentType == QueryIntentType.TITLE_AND_AUTHOR && !intent.titleQueryPart.isNullOrBlank() ->
          qTargetTitle.split(' ').filter { it.length > 1 }.ifEmpty { intent.significantTokens }
        subjectNorm != null ->
          subjectNorm.split(' ').filter { it.length > 1 && it !in queryStopwords }.ifEmpty { intent.significantTokens }
        else -> intent.significantTokens
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

    val allowBilingualModifierSynonyms =
      intent.allowColoringBooks || intent.allowNotebooks || intent.allowGuidesOrAnalyses
    val bestFormMatch =
      titleForms
        .map { form -> evaluateTitleForm(form, qTargetTitle, sigTokensTitle, allowBilingualModifierSynonyms) }
        .maxByOrNull { it.score }
        ?: TitleFormMatch(MatchedTitleField.NONE, 0, 0f, exactFull = false, exactBase = false, orderedContiguous = false, cleanSubtitle = true, fuzzyOnly = false, matchedWordSet = emptySet(), baseNonStopwords = emptyList())

    val normSeries = BibliographicMath.normalizeText(candidate.seriesName)
    val seriesWords = normSeries.split(' ').filter { it.isNotBlank() }.toSet()
    val seriesHits = countStrictTokenHits(sigTokensTitle, seriesWords, allowMorphological = true)
    val seriesCoverage = if (sigTokensTitle.isEmpty()) 0f else seriesHits.toFloat() / sigTokensTitle.size.toFloat()

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

    // 2b. Detecção estruturada de consulta cruzada Título + Autor sem delimitador explícito (ex.: "Coraline Neil Gaiman")
    val crossFieldEval =
      if (intent.intentType !in setOf(QueryIntentType.EXPLICIT_AUTHOR, QueryIntentType.EXPLICIT_TITLE, QueryIntentType.TITLE_AND_AUTHOR) &&
        intent.subjectQueryPart == null &&
        intent.significantTokens.size >= 2 &&
        bestFormMatch.field != MatchedTitleField.NONE &&
        authorWords.isNotEmpty()
      ) {
        evaluateCrossFieldTitleAndAuthor(
          queryTokens = intent.significantTokens,
          titleForm = bestFormMatch,
          authorWords = authorWords,
          candidateSurname = BibliographicMath.normalizeAuthorSurname(candidate.primaryAuthorsText),
          allowBilingualModifierSynonyms = allowBilingualModifierSynonyms,
        )
      } else {
        null
      }

    // 2c. Verificar correspondência de modificador destacado (ex.: "Jogos Vorazes guia")
    val guideMetadataNorm =
      BibliographicMath.normalizeText("$combinedWorkText ${editionTitleRaw.orEmpty()} $categoriesNorm ${synopsisNormPunct.take(180)}")
        .split(' ')
        .filter { it.isNotBlank() }
        .toSet()
    val matchesDetachedGuideModifier =
      intent.allowGuidesOrAnalyses &&
        intent.detachedModifierTokens.isNotEmpty() &&
        countStrictTokenHits(
          intent.detachedModifierTokens,
          guideMetadataNorm,
          allowMorphological = true,
          allowBilingualModifierSynonyms = true,
        ) > 0

    val authorMatchScore =
      when {
        crossFieldEval != null -> maxOf(if (crossFieldEval.authorTokenHits >= 2) 110 else 96, if (exactAuthorMatch) 115 else 0)
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

    var effectiveTitleScore =
      maxOf(bestFormMatch.score, if (seriesCoverage >= 0.99f) 84 else 0)
    var effectiveTitleCoverage = maxOf(bestFormMatch.coverage, seriesCoverage)
    if (crossFieldEval != null) {
      effectiveTitleScore = maxOf(effectiveTitleScore, crossFieldEval.boostedTitleScore)
      effectiveTitleCoverage = maxOf(effectiveTitleCoverage, crossFieldEval.candidateTitleCompleteness)
    }

    // Cobertura mínima rigorosa (sem fallback de Levenshtein de frase inteira que aprova "O Senhor dos Anais"!)
    val hasMinimumMatch =
      when {
        intent.intentType == QueryIntentType.EXPLICIT_AUTHOR ->
          authorCoverage >= 0.99f || exactAuthorMatch
        intent.intentType == QueryIntentType.TITLE_AND_AUTHOR ->
          effectiveTitleCoverage >= 0.99f && authorSigHits >= 1
        crossFieldEval != null -> true
        intent.subjectQueryPart != null && intent.detachedModifierTokens.isNotEmpty() ->
          effectiveTitleCoverage >= 0.99f &&
            (matchesDetachedGuideModifier || !intent.allowGuidesOrAnalyses || bestFormMatch.exactBase)
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
      academicSubtitleOrTitleRegex.containsMatchIn("$combinedWorkNormPunct ${synopsisNormPunct.take(200)}")
    val hasAcademicCategory =
      academicCategoryTokens.any { it in categoriesNorm } &&
        !Regex("\\b(fiction|ficcao|fantasy|fantasia|romance|juvenile)\\b").containsMatchIn(categoriesNorm)
    val hasExplanatoryNonEditionSubtitle =
      struct.subtitleNormalized.isNotEmpty() &&
        !isEditionOrVolumeSubtitle(struct.subtitleNormalized) &&
        !qTargetTitle.contains(struct.subtitleNormalized)

    val hasCompanionTieInSubtitle =
      hasExplanatoryNonEditionSubtitle &&
        hasExplanatoryCompanionOrAcademicCue(struct.subtitleNormalized)

    val isAcademicDerivative =
      !intent.allowGuidesOrAnalyses &&
        effectiveTitleCoverage >= 0.5f &&
        (
          hasAcademicSubtitleSignal ||
            hasCompanionTieInSubtitle ||
            (authorMatchScore < 85 &&
              ((hasAcademicCategory && conflictsWithCanonicalAuthor) ||
                (hasAcademicCategory && hasExplanatoryNonEditionSubtitle) ||
                (conflictsWithCanonicalAuthor && hasExplanatoryNonEditionSubtitle && !bestFormMatch.exactFull)))
          )

    // 4. Atribuição de MatchTier (T1..T7)
    val isCrossRefAcademic = MetadataSource.CROSSREF in candidate.sources && !isAcademicCategorySafe(categoriesNorm)
    val finalIsAcademic = isAcademicDerivative || (isCrossRefAcademic && !intent.allowGuidesOrAnalyses && conflictsWithCanonicalAuthor)

    val effectiveExactBase = bestFormMatch.exactBase || (crossFieldEval?.isExactBaseTitle == true)
    val effectiveExactFull = bestFormMatch.exactFull || (crossFieldEval?.isExactBaseTitle == true && bestFormMatch.cleanSubtitle)

    val matchTier =
      when {
        finalIsAcademic -> MatchTier.DERIVATIVE_OR_ACADEMIC
        struct.bundleKind == com.example.core.model.TitleBundleKind.COLLECTION_OR_BOX ->
          MatchTier.COLLECTION_OR_BOX_SET
        struct.bundleKind == com.example.core.model.TitleBundleKind.SERIES_VOLUME &&
          !effectiveExactFull ->
          MatchTier.SERIES_VOLUME_MATCH
        effectiveExactFull || (effectiveExactBase && bestFormMatch.cleanSubtitle) ->
          if (bestFormMatch.field == MatchedTitleField.EDITION_TITLE || bestFormMatch.field == MatchedTitleField.ORIGINAL_TITLE) {
            MatchTier.TRANSLATION_OR_ALT_TITLE
          } else {
            MatchTier.EXACT_WORK
          }
        bestFormMatch.field == MatchedTitleField.EDITION_TITLE || bestFormMatch.field == MatchedTitleField.ORIGINAL_TITLE ->
          MatchTier.TRANSLATION_OR_ALT_TITLE
        effectiveExactBase && hasExplanatoryNonEditionSubtitle && !conflictsWithCanonicalAuthor ->
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

    var identityBucket =
      when {
        finalIsAcademic -> 10
        effectiveExactFull -> 100
        effectiveExactBase && bestFormMatch.cleanSubtitle -> 95
        crossFieldEval != null -> 90
        bestFormMatch.orderedContiguous && effectiveTitleCoverage >= 0.99f -> 85
        effectiveTitleCoverage >= 0.99f -> 75
        authorMatchScore >= 95 -> 70
        else -> 40
      }

    // Quando o usuário explicitamente pediu guia/análise destacada (ex: "Jogos Vorazes guia"),
    // guias reais sobem acima das edições puras do romance para atender a intenção do leitor.
    val guideIntentBonus =
      if (intent.allowGuidesOrAnalyses && intent.detachedModifierTokens.isNotEmpty()) {
        if (matchesDetachedGuideModifier) {
          identityBucket = maxOf(identityBucket, 98)
          35
        } else {
          identityBucket = minOf(identityBucket, 80)
          0
        }
      } else {
        0
      }

    val combinedScore =
      maxOf(effectiveTitleScore, authorMatchScore) +
        (if (effectiveTitleScore >= 50 && authorMatchScore >= 40) 25 else 0) +
        (if (matchesCanonicalAuthor) 12 else 0) +
        guideIntentBonus

    return CandidateMatchEvaluation(
      accepted = true,
      queryMatchScore = combinedScore,
      titleMatchScore = effectiveTitleScore,
      authorMatchScore = authorMatchScore,
      significantTokenCoverage = maxOf(effectiveTitleCoverage, authorCoverage),
      matchTier = matchTier,
      matchedField =
        if (crossFieldEval == null && authorMatchScore > effectiveTitleScore && effectiveTitleScore < 50) {
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

  private data class CrossFieldMatch(
    val titleTokenHits: Int,
    val authorTokenHits: Int,
    val boostedTitleScore: Int,
    val candidateTitleCompleteness: Float,
    val isExactBaseTitle: Boolean,
  )

  private fun evaluateCrossFieldTitleAndAuthor(
    queryTokens: List<String>,
    titleForm: TitleFormMatch,
    authorWords: Set<String>,
    candidateSurname: String,
    allowBilingualModifierSynonyms: Boolean,
  ): CrossFieldMatch? {
    val inTitle =
      queryTokens.filter { tok ->
        countStrictTokenHits(listOf(tok), titleForm.matchedWordSet, allowMorphological = true, allowBilingualModifierSynonyms) > 0
      }
    val inAuthor =
      queryTokens.filter { tok ->
        countStrictTokenHits(listOf(tok), authorWords, allowMorphological = true) > 0
      }
    if (inTitle.isEmpty() || inAuthor.isEmpty()) return null
    val authorExplainsMissingTitleToken = inAuthor.any { it !in inTitle }
    if (!authorExplainsMissingTitleToken) return null

    val unionExplained = queryTokens.count { it in inTitle || it in inAuthor }
    val unionCoverage = unionExplained.toFloat() / queryTokens.size.toFloat()
    val requiredUnionCoverage = if (queryTokens.size <= 5) 0.99f else 0.85f
    if (unionCoverage < requiredUnionCoverage) return null

    val baseWords = titleForm.baseNonStopwords
    if (baseWords.isEmpty()) return null
    val matchedBaseWords =
      baseWords.count { bw ->
        inTitle.any { qTok -> bw == qTok || (qTok.length >= 4 && bw.startsWith(qTok) && bw.length <= qTok.length + 2) }
      }
    val candidateTitleCompleteness = matchedBaseWords.toFloat() / baseWords.size.toFloat()
    if (candidateTitleCompleteness < 0.65f) return null

    // Autor precisa bater sobrenome ou >= 2 tokens para evitar colisões acidentais de prenome
    val surnameMatched =
      candidateSurname.isNotEmpty() &&
        inAuthor.any { qTok -> qTok == candidateSurname || BibliographicMath.tokenSimilarity(qTok, candidateSurname) >= 0.85f }
    if (!surnameMatched && inAuthor.size < 2) return null

    val isExactBase = matchedBaseWords == baseWords.size
    val boostedTitleScore =
      when {
        isExactBase && titleForm.cleanSubtitle -> 118
        isExactBase -> 102
        else -> 88
      }
    return CrossFieldMatch(
      titleTokenHits = inTitle.size,
      authorTokenHits = inAuthor.size,
      boostedTitleScore = boostedTitleScore,
      candidateTitleCompleteness = candidateTitleCompleteness,
      isExactBaseTitle = isExactBase,
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

  private val queryStopwords =
    setOf(
      "a", "o", "as", "os", "um", "uma", "de", "da", "do", "das", "dos", "em", "no", "na", "e", "ou",
      "the", "an", "and", "or", "of", "in", "on", "to", "for",
    )

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
    val matchedWordSet: Set<String> = emptySet(),
    val baseNonStopwords: List<String> = emptyList(),
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

    val baseWords =
      normBaseWithArticle
        .split(' ')
        .filter { it.isNotBlank() && it !in queryStopwords }
        .ifEmpty { normBaseWithArticle.split(' ').filter { it.isNotBlank() } }

    return TitleFormMatch(
      field = form.field,
      score = score,
      coverage = maxOf(coverage, if (usedFuzzy) fuzzyCoverage else coverage),
      exactFull = exactFull,
      exactBase = exactBase,
      orderedContiguous = orderedContiguous,
      cleanSubtitle = cleanSub,
      fuzzyOnly = usedFuzzy,
      matchedWordSet = fullWordSet,
      baseNonStopwords = baseWords,
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
            "guia" -> setOf("guide", "manual", "handbook", "companion")
            "guide" -> setOf("guia", "manual", "handbook", "companion")
            "manual" -> setOf("guia", "guide", "handbook")
            "handbook" -> setOf("guia", "guide", "manual")
            "companion" -> setOf("guia", "guide")
            "oficial" -> setOf("official")
            "official" -> setOf("oficial")
            "ilustrado" -> setOf("illustrated")
            "illustrated" -> setOf("ilustrado")
            "filme" -> setOf("movie", "film")
            "movie" -> setOf("filme")
            "tributos" -> setOf("tribute", "tributes")
            "tribute" -> setOf("tributos", "tributo")
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
   * entre os candidatos literários aceitos (ex: "Machado de Assis" vs "Dom Casmurro" vs "Coraline Neil Gaiman").
   */
  fun isPredominantlyAuthorQuery(
    evaluations: List<CandidateMatchEvaluation>,
    intent: ParsedSearchIntent?,
  ): Boolean {
    if (intent == null) return false
    if (intent.intentType == QueryIntentType.EXPLICIT_AUTHOR) return true
    if (intent.intentType != QueryIntentType.AMBIGUOUS_OR_SHORT) return false
    if (intent.detachedModifierTokens.isNotEmpty() || intent.allowGuidesOrAnalyses || intent.wantsBoxOrCollection) {
      return false
    }
    val accepted = evaluations.filter { it.accepted }
    if (accepted.size < 2) return false
    val hasExactLiteralTitleMatch =
      accepted.any {
        (it.matchTier == MatchTier.EXACT_WORK || it.matchTier == MatchTier.TRANSLATION_OR_ALT_TITLE) &&
          it.titleMatchScore >= 115 &&
          (it.authorMatchScore < 40 || (it.authorMatchScore >= 90 && it.titleMatchScore >= 115))
      }
    if (hasExactLiteralTitleMatch) return false
    val strongAuthorMatches = accepted.count { it.authorMatchScore >= 85 && it.titleMatchScore < 85 }
    return strongAuthorMatches.toFloat() / accepted.size.toFloat() >= 0.55f
  }
}
