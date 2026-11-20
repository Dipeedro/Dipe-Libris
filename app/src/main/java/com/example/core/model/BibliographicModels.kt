package com.example.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class CandidateContributor(
  val name: String,
  val role: ContributorRole = ContributorRole.AUTHOR,
  val externalId: String? = null,
)

@Immutable
@Serializable
data class CoverCandidate(
  val imageUrl: String,
  val source: MetadataSource,
  val score: Int = 0,
  val matchesIsbn: Boolean = false,
  val matchesPublisherYear: Boolean = false,
  val matchesLanguage: Boolean = false,
  val widthHint: Int? = null,
  val heightHint: Int? = null,
)

@Immutable
@Serializable
data class SynopsisCandidate(
  val text: String,
  val language: String? = null,
  val source: MetadataSource,
  val editionLabel: String? = null,
  val score: Int = 0,
)

@Immutable
@Serializable
data class SearchEditionCandidate(
  val candidateId: String,
  val workTitle: String,
  val subtitle: String? = null,
  val originalTitle: String? = null,
  val editionTitle: String? = null,
  val contributors: List<CandidateContributor> = emptyList(),
  val publisher: String? = null,
  val publicationYear: Int? = null,
  val totalPages: Int? = null,
  val isbn10: String? = null,
  val isbn13: String? = null,
  val language: String? = null,
  val format: EditionFormat? = null,
  val editionLabel: String? = null,
  val seriesName: String? = null,
  val seriesVolume: String? = null,
  val categories: List<String> = emptyList(),
  val coverCandidates: List<CoverCandidate> = emptyList(),
  val synopsisCandidates: List<SynopsisCandidate> = emptyList(),
  val externalWorkKey: String? = null,
  val externalIds: Map<String, String> = emptyMap(),
  val sources: List<MetadataSource> = emptyList(),
  val reliabilityScore: Int = 0,
  val localExistingEditionId: Long? = null,
  val localExistingWorkId: Long? = null,
  val canonicalLanguageTag: String? = null,
  val languageTierRank: Int = LanguageTier.UNKNOWN.rank,
  val languageConfidenceName: String? = null,
  val hasLanguageConflict: Boolean = false,
  val queryMatchScore: Int = 0,
  val workEditionCount: Int = 0,
  val workReadingLogCount: Int = 0,
  val workRatingsCount: Int = 0,
  val availableWorkLanguages: List<String> = emptyList(),
) {
  val bestIsbn: String?
    get() = isbn13 ?: isbn10

  val bestCover: CoverCandidate?
    get() = coverCandidates.maxByOrNull { it.score }

  val orderedCoverUrls: List<String>
    get() = coverCandidates.sortedByDescending { it.score }.map { it.imageUrl }.distinct()

  val bestSynopsis: SynopsisCandidate?
    get() = synopsisCandidates.maxByOrNull { it.score }

  val effectiveLanguageTier: LanguageTier
    get() = LanguageTier.entries.firstOrNull { it.rank == languageTierRank } ?: LanguageTier.UNKNOWN

  val primaryAuthorsText: String
    get() =
      contributors
        .filter { it.role.isPrimaryWorkRole }
        .joinToString(", ") { it.name }
        .ifBlank { contributors.firstOrNull()?.name.orEmpty() }

  val translatorsText: String?
    get() =
      contributors
        .filter { it.role == ContributorRole.TRANSLATOR }
        .joinToString(", ") { it.name }
        .takeIf { it.isNotBlank() }
}

@Immutable
@Serializable
data class SearchWorkGroup(
  val groupId: String,
  val canonicalTitle: String,
  val subtitle: String? = null,
  val originalTitle: String? = null,
  val primaryAuthors: List<CandidateContributor> = emptyList(),
  val externalWorkKey: String? = null,
  val suggestedGenreSlug: String? = null,
  val editions: List<SearchEditionCandidate> = emptyList(),
  val sources: List<MetadataSource> = emptyList(),
  val localMatchedWorkId: Long? = null,
  val localMatchedTitle: String? = null,
  val workPopularityScore: Int = 0,
  val queryRelevanceScore: Int = 0,
  val primaryLanguageTierRank: Int = LanguageTier.UNKNOWN.rank,
  val totalAvailableEditionsHint: Int? = null,
) {
  val displayAuthors: String
    get() = primaryAuthors.joinToString(", ") { it.name }

  val previewCoverUrl: String?
    get() = editions.firstNotNullOfOrNull { it.bestCover?.imageUrl }

  val previewFallbackCoverUrls: List<String>
    get() = editions.flatMap { it.orderedCoverUrls }.distinct().drop(1)

  val primaryTierEditions: List<SearchEditionCandidate>
    get() {
      val primary = editions.filter { it.languageTierRank <= LanguageTier.PT_UNCERTAIN.rank }
      return primary.ifEmpty { editions }
    }

  val secondaryTierEditions: List<SearchEditionCandidate>
    get() {
      val primary = editions.filter { it.languageTierRank <= LanguageTier.PT_UNCERTAIN.rank }
      return if (primary.isEmpty()) emptyList() else editions.filter { it.languageTierRank > LanguageTier.PT_UNCERTAIN.rank }
    }
}

enum class ProviderOutcomeState {
  SUCCESS,
  EMPTY,
  CACHED,
  SKIPPED_NO_API_KEY,
  RATE_LIMITED,
  OFFLINE,
  ERROR,
  LOADING,
}

@Immutable
data class SourceQueryStatus(
  val source: MetadataSource,
  val state: ProviderOutcomeState,
  val count: Int = 0,
  val message: String? = null,
  val totalAvailable: Int? = null,
  val hasMore: Boolean = false,
  val durationMs: Long = 0L,
)

@Immutable
data class HybridSearchResponse(
  val query: String,
  val isIsbnQuery: Boolean,
  val isFromCache: Boolean,
  val isOffline: Boolean,
  val localMatches: List<CatalogBook> = emptyList(),
  val workGroups: List<SearchWorkGroup> = emptyList(),
  val secondaryWorkGroups: List<SearchWorkGroup> = emptyList(),
  val sourceStatuses: List<SourceQueryStatus> = emptyList(),
  val expandedSecondarySources: Boolean = false,
  val hasMoreResults: Boolean = false,
  val isPartialResult: Boolean = false,
  val totalFoundHint: Int? = null,
  val firstResultLatencyMs: Long? = null,
  val totalLatencyMs: Long? = null,
)

@Immutable
sealed interface DuplicateAssessment {
  @Immutable
  data object NoMatch : DuplicateAssessment

  @Immutable
  data class ExactEditionMatch(
    val existingWorkId: Long,
    val existingEditionId: Long,
    val existingTitle: String,
    val existingAuthor: String,
    val matchedIsbn: String,
    val existingCopiesCount: Int,
  ) : DuplicateAssessment

  @Immutable
  data class SameWorkDifferentEditionMatch(
    val existingWorkId: Long,
    val existingTitle: String,
    val existingAuthor: String,
    val confidence: Float,
    val matchReason: String,
  ) : DuplicateAssessment

  @Immutable
  data class UncertainWorkMatch(
    val candidates: List<CatalogBook>,
  ) : DuplicateAssessment
}

@Immutable
data class BookReviewSubmission(
  val existingWorkId: Long? = null,
  val title: String,
  val subtitle: String? = null,
  val originalTitle: String? = null,
  val contributors: List<CandidateContributor>,
  val primaryGenreId: Long? = null,
  val activeSynopsis: String? = null,
  val synopsisLanguage: String? = null,
  val synopsisSource: String? = null,
  val isSynopsisCustomized: Boolean = false,
  val externalWorkKey: String? = null,
  val status: ReadingStatus = ReadingStatus.UNREAD,
  val publisherOrNote: String? = null,
  val publicationYear: Int? = null,
  val totalPages: Int? = null,
  val isbn10: String? = null,
  val isbn13: String? = null,
  val language: String? = null,
  val format: EditionFormat? = null,
  val editionLabel: String? = null,
  val seriesName: String? = null,
  val seriesVolume: String? = null,
  val categories: List<String> = emptyList(),
  val externalIdsJson: String? = null,
  val selectedRemoteCoverUrl: String? = null,
  val fallbackRemoteCoverUrls: List<String> = emptyList(),
  val selectedLocalCoverUri: String? = null,
  val coverSource: String? = null,
  val isCoverCustomized: Boolean = false,
  val copyMediaType: CopyMediaType = CopyMediaType.PHYSICAL,
  val copyLabel: String? = null,
  val copyPhysicalFormat: String? = null,
  val copyLocationOrPlatform: String? = null,
  val copyCondition: CopyCondition? = null,
  val copyOrigin: CopyOrigin? = null,
  val copyAcquisitionEpochMillis: Long? = null,
  val copyPriceMinorUnits: Long? = null,
  val copyCurrencyCode: String? = null,
  val copyCustomCode: String? = null,
  val copyPersonalNote: String? = null,
)
