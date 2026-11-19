package com.example.core.model

import java.util.Locale

enum class AppLanguage(val tag: String) {
  PORTUGUESE_BR("pt-BR"),
  ENGLISH("en");

  fun toLocale(): Locale =
    when (this) {
      PORTUGUESE_BR -> Locale.forLanguageTag("pt-BR")
      ENGLISH -> Locale.ENGLISH
    }

  companion object {
    fun fromTag(tag: String?): AppLanguage =
      entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
        ?: if (Locale.getDefault().language.equals("pt", ignoreCase = true)) {
          PORTUGUESE_BR
        } else {
          ENGLISH
        }
  }
}

enum class AppThemeMode {
  SYSTEM,
  PAPER,
  INK,
  SEPIA;

  companion object {
    fun fromName(name: String?): AppThemeMode =
      entries.firstOrNull { it.name == name } ?: PAPER
  }
}

enum class TypographyPreset {
  CLASSIC_EDITORIAL,
  LITERARY_PRESS,
  MODERN_ARCHIVE;

  companion object {
    fun fromName(name: String?): TypographyPreset =
      entries.firstOrNull { it.name == name } ?: CLASSIC_EDITORIAL
  }
}

enum class LibraryViewMode {
  GRID,
  LIST,
  SHELF,
  GALLERY;

  companion object {
    fun fromName(name: String?): LibraryViewMode =
      entries.firstOrNull { it.name == name } ?: GRID
  }
}

enum class LibrarySortOrder {
  RECENTLY_ADDED,
  TITLE_ASC,
  AUTHOR_ASC,
}

enum class ReadingStatus {
  UNREAD,
  READING,
  PAUSED,
  FINISHED,
  ABANDONED;

  companion object {
    fun fromName(name: String?): ReadingStatus =
      entries.firstOrNull { it.name == name } ?: UNREAD
  }
}

enum class CopyMediaType {
  PHYSICAL,
  DIGITAL;

  companion object {
    fun fromName(name: String?): CopyMediaType =
      entries.firstOrNull { it.name == name } ?: PHYSICAL
  }
}

enum class EditionFormat {
  HARDCOVER,
  PAPERBACK,
  POCKET,
  EBOOK,
  OTHER;

  companion object {
    fun fromName(name: String?): EditionFormat? =
      name?.let { value -> entries.firstOrNull { it.name == value } }
  }
}

enum class ShelfType {
  CUSTOM,
  SMART,
}

enum class ContributorRole {
  AUTHOR,
  CO_AUTHOR,
  EDITOR_ORGANIZER,
  TRANSLATOR,
  ILLUSTRATOR,
  PREFACE_BY,
  OTHER;

  val isPrimaryWorkRole: Boolean
    get() = this == AUTHOR || this == CO_AUTHOR || this == EDITOR_ORGANIZER

  val isEditionSpecificByDefault: Boolean
    get() = this == TRANSLATOR || this == ILLUSTRATOR || this == PREFACE_BY

  companion object {
    fun fromName(name: String?): ContributorRole =
      name?.let { value -> entries.firstOrNull { it.name == value } } ?: AUTHOR
  }
}

enum class CopyCondition {
  NEW,
  LIKE_NEW,
  GOOD,
  FAIR,
  WORN,
  DAMAGED;

  companion object {
    fun fromName(name: String?): CopyCondition? =
      name?.let { value -> entries.firstOrNull { it.name == value } }
  }
}

enum class CopyOrigin {
  PURCHASED,
  GIFT,
  BORROWED,
  EXCHANGE,
  INHERITED,
  OTHER;

  companion object {
    fun fromName(name: String?): CopyOrigin? =
      name?.let { value -> entries.firstOrNull { it.name == value } }
  }
}

enum class MetadataSource(val displayName: String) {
  OPEN_LIBRARY("Open Library"),
  GOOGLE_BOOKS("Google Books"),
  CROSSREF("Crossref"),
  WIKIDATA("Wikidata"),
  BRASIL_API("BrasilAPI / CBL"),
  MANUAL("Manual");

  companion object {
    fun fromName(name: String?): MetadataSource =
      name?.let { value -> entries.firstOrNull { it.name == value } } ?: MANUAL
  }
}

enum class MetadataSuggestionStatus {
  PENDING,
  ACCEPTED,
  REJECTED;

  companion object {
    fun fromName(name: String?): MetadataSuggestionStatus =
      name?.let { value -> entries.firstOrNull { it.name == value } } ?: PENDING
  }
}

enum class MetadataSuggestionScope {
  WORK,
  EDITION;

  companion object {
    fun fromName(name: String?): MetadataSuggestionScope =
      name?.let { value -> entries.firstOrNull { it.name == value } } ?: WORK
  }
}
