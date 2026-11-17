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
