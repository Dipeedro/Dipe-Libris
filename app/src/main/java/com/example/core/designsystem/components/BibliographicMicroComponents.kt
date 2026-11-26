package com.example.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.AppLanguage

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

fun shouldShowMinimalLanguageBadge(
  languageTag: String?,
  appLanguage: AppLanguage,
  hideIfMatchesAppLanguage: Boolean,
): Boolean {
  val code = formatMinimalLanguageCode(languageTag) ?: return false
  if (!hideIfMatchesAppLanguage) return true
  return when (appLanguage) {
    AppLanguage.PORTUGUESE_BR -> code != "PT" && code != "BR"
    AppLanguage.ENGLISH -> code != "EN"
  }
}

@Composable
fun MinimalLanguageBadge(
  languageTag: String?,
  modifier: Modifier = Modifier,
  hideIfMatchesAppLanguage: Boolean = false,
) {
  val appLanguage = LocalAppLanguage.current
  val shouldShow =
    remember(languageTag, appLanguage, hideIfMatchesAppLanguage) {
      shouldShowMinimalLanguageBadge(languageTag, appLanguage, hideIfMatchesAppLanguage)
    }
  if (!shouldShow) return
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
  leadingInsetPx: (() -> Float)? = null,
  trailingInsetPx: (() -> Float)? = null,
  placeholderOverflowClip: Boolean = false,
) {
  var isFocused by remember { mutableStateOf(false) }
  val motion = DipeTheme.motion
  val fillColor by
    animateColorAsState(
      targetValue =
        if (isFocused) MaterialTheme.colorScheme.surfaceContainerLowest
        else MaterialTheme.colorScheme.surfaceContainerLow,
      animationSpec = motion.standardTween(160),
      label = "search_pill_fill",
    )
  val borderColor by
    animateColorAsState(
      targetValue =
        if (isFocused) MaterialTheme.colorScheme.secondary.copy(alpha = 0.82f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f),
      animationSpec = motion.standardTween(160),
      label = "search_pill_border",
    )
  val borderStrokeWidth by
    animateDpAsState(
      targetValue = if (isFocused) 1.25.dp else 0.75.dp,
      animationSpec = motion.standardTween(160),
      label = "search_pill_border_width",
    )

  if (leadingInsetPx == null && trailingInsetPx == null) {
    Surface(
      shape = CircleShape,
      color = fillColor,
      border = BorderStroke(width = borderStrokeWidth, color = borderColor),
      modifier = modifier.heightIn(min = 48.dp),
    ) {
      CompactEditorialSearchPillContent(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = placeholder,
        clearContentDescription = clearContentDescription,
        inputTestTag = inputTestTag,
        isFocused = isFocused,
        onFocusChange = { isFocused = it },
        imeAction = imeAction,
        onSearchSubmit = onSearchSubmit,
        leadingInsetPx = null,
        trailingInsetPx = null,
        placeholderOverflowClip = placeholderOverflowClip,
      )
    }
  } else {
    Box(
      modifier =
        modifier
          .heightIn(min = 48.dp)
          .drawBehind {
            val leftInset = (leadingInsetPx?.invoke() ?: 0f).coerceIn(0f, size.width)
            val rightInset = (trailingInsetPx?.invoke() ?: 0f).coerceIn(0f, size.width - leftInset)
            val pillWidth = (size.width - leftInset - rightInset).coerceAtLeast(size.height)
            val radius = CornerRadius(size.height * 0.5f, size.height * 0.5f)
            drawRoundRect(
              color = fillColor,
              topLeft = Offset(leftInset, 0f),
              size = Size(pillWidth, size.height),
              cornerRadius = radius,
            )
            drawRoundRect(
              color = borderColor,
              topLeft = Offset(leftInset, 0f),
              size = Size(pillWidth, size.height),
              cornerRadius = radius,
              style = Stroke(width = borderStrokeWidth.toPx()),
            )
          }
          .drawWithContent {
            val leftInset = (leadingInsetPx?.invoke() ?: 0f).coerceIn(0f, size.width)
            val rightBound = (size.width - (trailingInsetPx?.invoke() ?: 0f)).coerceIn(leftInset, size.width)
            clipRect(left = leftInset, top = 0f, right = rightBound, bottom = size.height) {
              this@drawWithContent.drawContent()
            }
          },
      contentAlignment = Alignment.CenterStart,
    ) {
      CompactEditorialSearchPillContent(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = placeholder,
        clearContentDescription = clearContentDescription,
        inputTestTag = inputTestTag,
        isFocused = isFocused,
        onFocusChange = { isFocused = it },
        imeAction = imeAction,
        onSearchSubmit = onSearchSubmit,
        leadingInsetPx = leadingInsetPx,
        trailingInsetPx = trailingInsetPx,
        placeholderOverflowClip = placeholderOverflowClip,
      )
    }
  }
}

@Composable
private fun CompactEditorialSearchPillContent(
  query: String,
  onQueryChange: (String) -> Unit,
  placeholder: String,
  clearContentDescription: String,
  inputTestTag: String,
  isFocused: Boolean,
  onFocusChange: (Boolean) -> Unit,
  imeAction: ImeAction,
  onSearchSubmit: (() -> Unit)?,
  leadingInsetPx: (() -> Float)?,
  trailingInsetPx: (() -> Float)?,
  placeholderOverflowClip: Boolean,
) {
  val motion = DipeTheme.motion
  val searchIconTint by
    animateColorAsState(
      targetValue =
        if (isFocused) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
      animationSpec = motion.standardTween(160),
      label = "search_pill_icon_tint",
    )

  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 15.dp, end = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = Icons.Outlined.Search,
      contentDescription = null,
      tint = searchIconTint,
      modifier =
        Modifier.size(18.dp).graphicsLayer {
          translationX = leadingInsetPx?.invoke() ?: 0f
        },
    )
    Box(
      modifier =
        Modifier.weight(1f)
          .padding(vertical = 12.dp)
          .layout { measurable, constraints ->
            val leftInset = (leadingInsetPx?.invoke() ?: 0f).roundToInt().coerceAtLeast(0)
            val rightInset = (trailingInsetPx?.invoke() ?: 0f).roundToInt().coerceAtLeast(0)
            val availableWidth =
              (constraints.maxWidth - leftInset - rightInset).coerceAtLeast(0)
            val placeable =
              measurable.measure(constraints.copy(minWidth = 0, maxWidth = availableWidth))
            layout(constraints.maxWidth, placeable.height) {
              placeable.placeRelative(0, 0)
            }
          }
          .graphicsLayer {
            translationX = leadingInsetPx?.invoke() ?: 0f
          },
      contentAlignment = Alignment.CenterStart,
    ) {
      if (query.isEmpty()) {
        Text(
          text = placeholder,
          style = MaterialTheme.typography.bodyMedium,
          color =
            MaterialTheme.colorScheme.onSurfaceVariant.copy(
              alpha = if (isFocused) 0.62f else 0.82f,
            ),
          maxLines = 1,
          softWrap = false,
          overflow = if (placeholderOverflowClip) TextOverflow.Clip else TextOverflow.Ellipsis,
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
            .onFocusChanged { onFocusChange(it.isFocused) }
            .testTag(inputTestTag),
      )
    }
    AnimatedVisibility(
      visible = query.isNotEmpty(),
      enter =
        fadeIn(animationSpec = motion.standardTween(130)) +
          expandHorizontally(
            animationSpec = motion.standardTween(130),
            expandFrom = Alignment.End,
          ),
      exit =
        fadeOut(animationSpec = motion.standardTween(100)) +
          shrinkHorizontally(
            animationSpec = motion.standardTween(100),
            shrinkTowards = Alignment.End,
          ),
    ) {
      IconButton(
        onClick = { onQueryChange("") },
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .size(44.dp)
            .graphicsLayer { translationX = -(trailingInsetPx?.invoke() ?: 0f) }
            .testTag("${inputTestTag}_clear"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Clear,
          contentDescription = clearContentDescription,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
          modifier = Modifier.size(17.dp),
        )
      }
    }
  }
}
