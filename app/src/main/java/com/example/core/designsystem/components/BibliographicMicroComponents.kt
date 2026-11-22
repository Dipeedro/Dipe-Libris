package com.example.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography

/**
 * Normalizes raw bibliographic language tags (e.g., "pt-br", "por", "en-US") into
 * a compact 2-letter editorial code ("BR", "PT", "EN", "ES", etc.).
 */
fun formatMinimalLanguageCode(rawLanguage: String?): String? {
  val trimmed = rawLanguage?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
  val normalized = trimmed.replace('_', '-')
  return when {
    normalized == "pt-br" || normalized == "por-br" || normalized.contains("brazil") || normalized.contains("brasil") -> "BR"
    normalized == "pt-pt" || normalized == "por-pt" || normalized.contains("portugal") -> "PT"
    normalized == "pt" || normalized == "por" || normalized.startsWith("portug") -> "PT"
    normalized == "en" || normalized.startsWith("en-") || normalized == "eng" || normalized.contains("english") || normalized.contains("ingl") -> "EN"
    normalized == "es" || normalized.startsWith("es-") || normalized == "spa" || normalized.contains("span") || normalized.contains("espanh") -> "ES"
    normalized == "fr" || normalized.startsWith("fr-") || normalized == "fra" || normalized == "fre" || normalized.contains("french") || normalized.contains("franc") -> "FR"
    normalized == "de" || normalized.startsWith("de-") || normalized == "deu" || normalized == "ger" || normalized.contains("german") || normalized.contains("alem") -> "DE"
    normalized == "it" || normalized.startsWith("it-") || normalized == "ita" || normalized.contains("ital") -> "IT"
    normalized == "ja" || normalized.startsWith("ja-") || normalized == "jpn" || normalized.contains("japan") || normalized.contains("japon") -> "JA"
    else -> {
      val primarySubtag = normalized.substringBefore('-')
      when {
        primarySubtag.length == 2 && primarySubtag.all { it.isLetter() } -> primarySubtag.uppercase()
        primarySubtag.length == 3 && primarySubtag.all { it.isLetter() } -> primarySubtag.take(2).uppercase()
        else -> null
      }
    }
  }
}

fun isBrazilianPortugueseTag(rawLanguage: String?): Boolean {
  val normalized = rawLanguage?.trim()?.lowercase()?.replace('_', '-') ?: return false
  return normalized == "pt-br" || normalized == "por-br" || normalized.contains("brazil") || normalized.contains("brasil")
}

@Composable
fun MinimalLanguageBadge(
  languageTag: String?,
  modifier: Modifier = Modifier,
) {
  val code = remember(languageTag) { formatMinimalLanguageCode(languageTag) } ?: return
  val isPtBr = remember(languageTag) { isBrazilianPortugueseTag(languageTag) }
  val cd = stringResource(R.string.cd_language_badge, code)

  val containerColor =
    if (isPtBr) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
    else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
  val contentColor =
    if (isPtBr) MaterialTheme.colorScheme.secondary
    else MaterialTheme.colorScheme.onSurfaceVariant

  Surface(
    shape = CircleShape,
    color = containerColor,
    modifier = modifier.semantics(mergeDescendants = true) { contentDescription = cd },
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.Language,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(11.dp),
      )
      Text(
        text = code,
        style = LocalDipeTypography.current.monoMetadata.copy(fontSize = 10.5.sp, lineHeight = 13.sp),
        color = contentColor,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}

@Composable
fun MicroMetadataItem(
  icon: ImageVector,
  text: String,
  modifier: Modifier = Modifier,
  tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  textStyle: TextStyle = LocalDipeTypography.current.monoMetadata.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(13.dp),
    )
    Text(
      text = text,
      style = textStyle,
      color = textColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun CompactEditorialSearchPill(
  query: String,
  onQueryChange: (String) -> Unit,
  placeholder: String,
  clearContentDescription: String,
  inputTestTag: String,
  modifier: Modifier = Modifier,
  imeAction: ImeAction = ImeAction.Search,
  onSearchSubmit: (() -> Unit)? = null,
) {
  var isFocused by remember { mutableStateOf(false) }

  Surface(
    shape = CircleShape,
    color =
      if (isFocused) MaterialTheme.colorScheme.surfaceContainerLowest
      else MaterialTheme.colorScheme.surfaceContainerLow,
    border =
      BorderStroke(
        width = if (isFocused) 1.dp else 0.5.dp,
        color =
          if (isFocused) MaterialTheme.colorScheme.secondary
          else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
      ),
    modifier = modifier.heightIn(min = 48.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
    ) {
      Icon(
        imageVector = Icons.Outlined.Search,
        contentDescription = null,
        tint =
          if (isFocused) MaterialTheme.colorScheme.secondary
          else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
      Box(
        modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        if (query.isEmpty()) {
          Text(
            text = placeholder,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        BasicTextField(
          value = query,
          onValueChange = onQueryChange,
          singleLine = true,
          textStyle =
            MaterialTheme.typography.bodyMedium.copy(
              color = MaterialTheme.colorScheme.onSurface,
            ),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
          keyboardOptions = KeyboardOptions(imeAction = imeAction),
          keyboardActions =
            KeyboardActions(
              onSearch = { onSearchSubmit?.invoke() },
            ),
          modifier =
            Modifier.fillMaxWidth()
              .onFocusChanged { isFocused = it.isFocused }
              .testTag(inputTestTag),
        )
      }
      if (query.isNotEmpty()) {
        IconButton(
          onClick = { onQueryChange("") },
          modifier = Modifier.size(48.dp).testTag("${inputTestTag}_clear"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Clear,
            contentDescription = clearContentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}
