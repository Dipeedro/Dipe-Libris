package com.example.core.designsystem.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.InterFontFamily
import com.example.core.designsystem.JetBrainsMonoFontFamily
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.LoraFontFamily
import kotlin.math.max
import kotlin.math.min

@Immutable
internal data class BackArtworkRenderPalette(
  val topFieldColor: Color,
  val midFieldColor: Color,
  val bottomFieldColor: Color,
  val primaryShapeColor: Color,
  val secondaryShapeColor: Color,
  val accentColor: Color,
  val lightSupportColor: Color,
  val darkContrastColor: Color,
  val hairlineColor: Color,
  val boardEdgeColor: Color,
  val borderColor: Color,
  val textPrimaryColor: Color,
  val textSecondaryColor: Color,
  val textRuleColor: Color,
  val textCushionColor: Color,
  val textCushionAlpha: Float,
)

/**
 * Generates and caches a tiny 64x64 isotropic tileable fine-paper tooth texture once per process.
 * Zero horizontal row-scanline bias and zero dark black noise specks so turquoise, coral, ochre,
 * and pastel surfaces retain pure color clarity with ~16 KB memory overhead and zero per-frame allocations.
 */
internal object PaperGrainTextureProvider {
  private val tileShaderBrush: ShaderBrush? by lazy {
    runCatching {
      val size = 64
      val raw = IntArray(size * size)
      for (y in 0 until size) {
        for (x in 0 until size) {
          var h = (x * 0x27D4EB2D) xor (y * 0x165667B1)
          h = h xor (h ushr 15)
          h *= 0x85EBCA6B.toInt()
          h = h xor (h ushr 13)
          raw[y * size + x] = (h ushr 24) and 0xFF
        }
      }
      val pixels = IntArray(size * size)
      for (y in 0 until size) {
        val yNext = (y + 1) and (size - 1)
        for (x in 0 until size) {
          val xNext = (x + 1) and (size - 1)
          // Gentle 2x2 wrapped low-pass filter removes harsh salt-and-pepper noise
          val v =
            (raw[y * size + x] * 2 +
              raw[y * size + xNext] +
              raw[yNext * size + x] +
              raw[yNext * size + xNext]) / 5
          val centered = v - 128 // -128..127
          val isHighlight = centered >= 0
          val rgb = if (isHighlight) 252 else 218
          val alpha = ((kotlin.math.abs(centered) / 128f) * 9.5f).toInt().coerceIn(2, 10)
          pixels[y * size + x] = AndroidColor.argb(alpha, rgb, rgb, rgb)
        }
      }
      val bmp = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
      val imageBitmap: ImageBitmap = bmp.asImageBitmap()
      ShaderBrush(ImageShader(imageBitmap, TileMode.Repeated, TileMode.Repeated))
    }.getOrNull()
  }

  fun brush(): ShaderBrush? = tileShaderBrush
}

private fun contrastRatio(a: Color, b: Color): Float {
  val l1 = max(a.luminance(), b.luminance())
  val l2 = min(a.luminance(), b.luminance())
  return (l1 + 0.05f) / (l2 + 0.05f)
}

private fun colorSaturation(c: Color): Float {
  val hsv = FloatArray(3)
  AndroidColor.RGBToHSV(
    (c.red * 255f).toInt().coerceIn(0, 255),
    (c.green * 255f).toInt().coerceIn(0, 255),
    (c.blue * 255f).toInt().coerceIn(0, 255),
    hsv,
  )
  return hsv[1]
}

/**
 * Maps a [BookCoverVisualAnalysis] into a faithful, vivid-when-vivid / soft-when-pastel coated-board
 * artwork palette with clean 5-role hierarchy (`dominant`, `secondary`, `accent`, `lightSupport`,
 * `darkContrast`), zero gray muddying, and guaranteed upper-zone legibility.
 */
internal fun resolveBackArtworkRenderPalette(
  analysis: BookCoverVisualAnalysis,
  isDarkTheme: Boolean,
): BackArtworkRenderPalette {
  val dom = analysis.palette.dominant
  val sec = analysis.palette.secondary
  val acc = analysis.palette.accent
  val lightSupport = analysis.palette.lightSupport
  val darkContrast = analysis.palette.darkContrast
  val sp = analysis.spatial

  // Adaptive color preservation:
  // - Pure stark white (>0.84 lum & low sat) softens into hue-related paper tint (<0.78 lum)
  // - Crushed near-black (<0.032 lum) lifts gently into deep tinted charcoal (>0.008 lum)
  // - Pastels & vibrant covers keep their saturation and warmth without gray/beige muddying
  fun harmonizeSurfaceColor(raw: Color): Color {
    val lum = raw.luminance()
    val sat = colorSaturation(raw)
    var adjusted =
      when {
        lum > 0.84f && sat < 0.14f -> {
          val tint = if (analysis.mood.warmth >= -0.04f) Color(0xFFD6CEBE) else Color(0xFFCCD4DB)
          lerp(raw, tint, 0.66f)
        }
        lum > 0.86f -> lerp(raw, lightSupport, 0.24f)
        lum < 0.032f -> lerp(raw, lerp(darkContrast, Color(0xFF332E29), 0.52f), 0.68f)
        else -> raw
      }
    if (isDarkTheme && adjusted.luminance() > 0.68f && colorSaturation(adjusted) < 0.25f) {
      adjusted = lerp(adjusted, darkContrast, 0.16f)
    } else if (!isDarkTheme && adjusted.luminance() < 0.06f) {
      adjusted = lerp(adjusted, lerp(darkContrast, Color(0xFF2C2722), 0.35f), 0.22f)
    }
    return adjusted
  }

  val baseDominant = harmonizeSurfaceColor(dom)
  val topField = lerp(harmonizeSurfaceColor(sp.topBand), baseDominant, 0.16f)
  val midField = lerp(harmonizeSurfaceColor(sp.midBand), baseDominant, 0.24f)
  val bottomField = lerp(harmonizeSurfaceColor(sp.bottomBand), baseDominant, 0.16f)

  val rawPrimaryShape =
    when (analysis.motif) {
      BackArtworkMotif.CENTER_MEDALLION -> harmonizeSurfaceColor(sp.centerColor)
      BackArtworkMotif.DIAGONAL_SPLIT -> harmonizeSurfaceColor(sp.rightColor)
      BackArtworkMotif.HORIZON_BANDS -> harmonizeSurfaceColor(sp.bottomBand)
      BackArtworkMotif.EDITORIAL_FRAME -> harmonizeSurfaceColor(sec)
      BackArtworkMotif.ASYMMETRIC_FIELD -> harmonizeSurfaceColor(sec)
    }

  val primaryShapeColor =
    if (BookCoverAnalyzer.perceptualDistance(rawPrimaryShape, midField) < 0.11f) {
      if (midField.luminance() < 0.42f) lerp(midField, lightSupport, 0.24f)
      else lerp(midField, darkContrast, 0.22f)
    } else {
      rawPrimaryShape
    }

  val rawSecondaryShape = harmonizeSurfaceColor(sec)
  val secondaryShapeColor =
    if (BookCoverAnalyzer.perceptualDistance(rawSecondaryShape, primaryShapeColor) < 0.10f ||
      BookCoverAnalyzer.perceptualDistance(rawSecondaryShape, midField) < 0.09f
    ) {
      if (midField.luminance() < 0.42f) lerp(lightSupport, baseDominant, 0.34f)
      else lerp(darkContrast, baseDominant, 0.28f)
    } else {
      rawSecondaryShape
    }

  val accentColor =
    if (BookCoverAnalyzer.perceptualDistance(acc, midField) < 0.14f) {
      if (midField.luminance() < 0.45f) lerp(acc, lightSupport, 0.46f)
      else lerp(acc, darkContrast, 0.44f)
    } else {
      acc
    }

  val hairlineColor =
    if (midField.luminance() < 0.42f) {
      lerp(accentColor, lightSupport, 0.56f).copy(alpha = 0.44f)
    } else {
      lerp(accentColor, darkContrast, 0.52f).copy(alpha = 0.34f)
    }

  // Hardcover board rim color: cohesive darkened edition of cover board tone (never generic flat white/black)
  val edgeAnchor = lerp(analysis.palette.edgePalette.mid, bottomField, 0.28f)
  val boardEdgeColor =
    if (edgeAnchor.luminance() > 0.65f) {
      lerp(edgeAnchor, darkContrast, 0.34f)
    } else if (edgeAnchor.luminance() < 0.07f) {
      lerp(edgeAnchor, Color(0xFF453E36), 0.26f)
    } else {
      lerp(edgeAnchor, darkContrast, 0.22f)
    }
  val borderColor =
    if (isDarkTheme) lerp(boardEdgeColor, lightSupport, 0.20f).copy(alpha = 0.55f)
    else lerp(boardEdgeColor, darkContrast, 0.24f).copy(alpha = 0.42f)

  // Guarantee upper-zone typography legibility natively against topField (NEVER a dark mid-cover scrim!)
  val upperZoneApprox = lerp(topField, midField, 0.22f)
  val lightInk = lerp(lightSupport, Color(0xFFFAF6EE), 0.78f)
  val darkInk = lerp(darkContrast, Color(0xFF15120F), 0.72f)
  val lightC = contrastRatio(lightInk, upperZoneApprox)
  val darkC = contrastRatio(darkInk, upperZoneApprox)
  val useLightInk = lightC >= darkC
  val candidatePrimary = if (useLightInk) lightInk else darkInk
  val candidateSecondary =
    if (useLightInk) lerp(lightSupport, Color(0xFFEBE4D7), 0.60f).copy(alpha = 0.88f)
    else lerp(darkContrast, Color(0xFF332D26), 0.65f).copy(alpha = 0.86f)
  val rawContrast = max(lightC, darkC)
  val textCushionColor = lerp(topField, if (useLightInk) darkContrast else lightSupport, 0.55f)
  val textCushionAlpha = if (rawContrast < 4.5f) 0.14f else 0f
  val textRuleColor =
    if (useLightInk) {
      lerp(accentColor, lightInk, 0.48f).copy(alpha = 0.78f)
    } else {
      lerp(accentColor, darkInk, 0.42f).copy(alpha = 0.72f)
    }

  return BackArtworkRenderPalette(
    topFieldColor = topField,
    midFieldColor = midField,
    bottomFieldColor = bottomField,
    primaryShapeColor = primaryShapeColor,
    secondaryShapeColor = secondaryShapeColor,
    accentColor = accentColor,
    lightSupportColor = lightSupport,
    darkContrastColor = darkContrast,
    hairlineColor = hairlineColor,
    boardEdgeColor = boardEdgeColor,
    borderColor = borderColor,
    textPrimaryColor = candidatePrimary,
    textSecondaryColor = candidateSecondary,
    textRuleColor = textRuleColor,
    textCushionColor = textCushionColor,
    textCushionAlpha = textCushionAlpha,
  )
}

@Composable
internal fun BookBackCoverSurface(
  analysis: BookCoverVisualAnalysis,
  renderPalette: BackArtworkRenderPalette,
  publisherText: String?,
  yearValue: Int?,
  pagesText: String?,
  flipProgressProvider: () -> Float,
  reduceMotion: Boolean,
  shape: Shape,
  modifier: Modifier = Modifier,
  authorText: String? = null,
  editionSeedKey: String? = null,
) {
  val typography = LocalDipeTypography.current
  val isDarkSurface = renderPalette.midFieldColor.luminance() < 0.40f
  val grainBrush = remember { PaperGrainTextureProvider.brush() }
  val compositionSpec =
    remember(analysis, editionSeedKey) {
      BookCoverAnalyzer.resolveCompositionSpec(analysis = analysis, stableBookKey = editionSeedKey)
    }

  val artworkCacheModifier =
    remember(analysis, renderPalette, compositionSpec, isDarkSurface, grainBrush) {
      Modifier.drawWithCache {
          val w = size.width
          val h = size.height
          val dpPx = { dpVal: Float -> dpVal.dp.toPx() }

          val baseBrush =
            when {
              compositionSpec.family == BackArtworkDesignFamily.MONOCHROMATIC ->
                Brush.verticalGradient(
                  0.0f to lerp(renderPalette.midFieldColor, renderPalette.lightSupportColor, 0.12f),
                  0.58f to renderPalette.midFieldColor,
                  1.0f to lerp(renderPalette.midFieldColor, renderPalette.darkContrastColor, 0.10f),
                  startY = 0f,
                  endY = h,
                )
              analysis.spatial.leftVsRightContrast > analysis.spatial.topVsBottomContrast * 1.18f -> {
                val leftTone = lerp(renderPalette.topFieldColor, analysis.spatial.leftColor, 0.35f)
                val rightTone = lerp(renderPalette.bottomFieldColor, analysis.spatial.rightColor, 0.35f)
                Brush.linearGradient(
                  0.0f to leftTone,
                  0.55f to renderPalette.midFieldColor,
                  1.0f to rightTone,
                  start = Offset(0f, 0f),
                  end = Offset(w, h),
                )
              }
              else ->
                Brush.verticalGradient(
                  0.0f to renderPalette.topFieldColor,
                  0.52f to renderPalette.midFieldColor,
                  1.0f to renderPalette.bottomFieldColor,
                  startY = 0f,
                  endY = h,
                )
            }

          val prepared =
            BookBackDesignRenderer.prepareGeometry(
              w = w,
              h = h,
              dpToPx = dpPx,
              analysis = analysis,
              spec = compositionSpec,
              palette = renderPalette,
            )

          val primaryStroke = Stroke(width = 0.75.dp.toPx())
          val thinStroke = Stroke(width = 0.55.dp.toPx())

          // Only used when upper contrast is tight; strictly bounded to top 34% so middle band never darkens
          val upperCushionBrush =
            if (renderPalette.textCushionAlpha > 0.01f) {
              Brush.verticalGradient(
                0.0f to renderPalette.textCushionColor.copy(alpha = renderPalette.textCushionAlpha),
                0.28f to renderPalette.textCushionColor.copy(alpha = renderPalette.textCushionAlpha * 0.45f),
                0.38f to Color.Transparent,
                startY = 0f,
                endY = h,
              )
            } else {
              null
            }

          // Subtle narrow right-edge spine hinge crease (on flipped back cover, spine hinge is on right 3.5%)
          val spineX = w * 0.962f
          val spineCreaseBrush =
            Brush.horizontalGradient(
              0.955f to Color.Transparent,
              0.980f to renderPalette.darkContrastColor.copy(alpha = if (isDarkSurface) 0.16f else 0.08f),
              1.0f to renderPalette.darkContrastColor.copy(alpha = if (isDarkSurface) 0.22f else 0.12f),
              startX = 0f,
              endX = w,
            )
          val hingeLineColor = renderPalette.hairlineColor.copy(alpha = 0.38f)

          onDrawBehind {
            drawRect(brush = baseBrush)

            with(BookBackDesignRenderer) {
              renderPreparedGeometry(
                g = prepared,
                spec = compositionSpec,
                palette = renderPalette,
                strokeStyle = primaryStroke,
                thinStrokeStyle = thinStroke,
              )
            }

            if (upperCushionBrush != null) {
              drawRect(brush = upperCushionBrush)
            }

            if (grainBrush != null && compositionSpec.textureOpacity > 0.02f) {
              drawRect(brush = grainBrush, alpha = compositionSpec.textureOpacity)
            }

            drawRect(brush = spineCreaseBrush)
            drawLine(
              color = hingeLineColor,
              start = Offset(spineX, 4.dp.toPx()),
              end = Offset(spineX, h - 4.dp.toPx()),
              strokeWidth = thinStroke.width,
            )
          }
        }
    }

  val satinOverlayModifier =
    if (reduceMotion) {
      Modifier
    } else {
      Modifier.drawWithCache {
        val bandWidth = (size.width * 0.50f).coerceAtLeast(1f)
        val satinColor = Color(0xFFFFFAF0).copy(alpha = if (isDarkSurface) 0.065f else 0.09f)
        val satinBrush =
          Brush.linearGradient(
            0.0f to Color.Transparent,
            0.5f to satinColor,
            1.0f to Color.Transparent,
            start = Offset(-bandWidth * 0.5f, 0f),
            end = Offset(bandWidth * 0.5f, size.height * 0.72f),
          )
        onDrawBehind {
          val f = flipProgressProvider().coerceIn(0f, 1f)
          if (f <= 0.66f || f >= 0.995f) return@onDrawBehind
          val rise = smoothstep(0.66f, 0.85f, f)
          val fall = 1f - smoothstep(0.85f, 0.995f, f)
          val intensity = (rise * fall).coerceIn(0f, 1f)
          if (intensity <= 0.01f) return@onDrawBehind

          val sweepProgress = ((f - 0.66f) / 0.335f).coerceIn(0f, 1f)
          val centerX = -bandWidth * 0.35f + (size.width + bandWidth * 0.70f) * sweepProgress
          withTransform({ translate(left = centerX, top = 0f) }) {
            drawRect(
              brush = satinBrush,
              topLeft = Offset(-bandWidth * 0.6f, 0f),
              size = Size(bandWidth * 1.2f, size.height),
              alpha = intensity,
            )
          }
        }
      }
    }

  val primaryFont =
    when (compositionSpec.typographyStyle) {
      BackTypographyStyle.EDITORIAL_SERIF,
      BackTypographyStyle.DECORATIVE_BADGE,
      -> typography.displayFontFamily
      BackTypographyStyle.GEOMETRIC_SANS -> InterFontFamily
      BackTypographyStyle.MINIMAL_QUIET -> LoraFontFamily
    }
  val primaryLabelText = publisherText ?: authorText?.trim()?.takeIf { it.isNotEmpty() }
  val isMinimalText = compositionSpec.typographyStyle == BackTypographyStyle.MINIMAL_QUIET
  val showDecorativeHeader =
    publisherText != null &&
      (compositionSpec.typographyStyle == BackTypographyStyle.EDITORIAL_SERIF ||
        compositionSpec.typographyStyle == BackTypographyStyle.DECORATIVE_BADGE)

  Surface(
    color = renderPalette.midFieldColor,
    shape = shape,
    border = BorderStroke(0.65.dp, renderPalette.borderColor),
    shadowElevation = 0.dp,
    modifier = modifier.clip(shape),
  ) {
    Box(modifier = Modifier.fillMaxSize().clip(shape).then(artworkCacheModifier)) {
      // Editorial colophon metadata strictly in upper quiet zone (never overlaps lower art or thumb zone)
      val hasAnyMeta = primaryLabelText != null || yearValue != null || pagesText != null
      if (hasAnyMeta) {
        Column(
          modifier =
            Modifier.fillMaxSize()
              .padding(start = 10.dp, end = 12.dp, top = 11.dp, bottom = 10.dp),
          horizontalAlignment = Alignment.Start,
          verticalArrangement = Arrangement.Top,
        ) {
          if (showDecorativeHeader) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              if (compositionSpec.typographyStyle == BackTypographyStyle.DECORATIVE_BADGE) {
                Box(
                  modifier =
                    Modifier.size(4.dp)
                      .background(renderPalette.accentColor, shape = CircleShape),
                )
              }
              Box(
                modifier =
                  Modifier.width(if (isMinimalText) 12.dp else 16.dp)
                    .height(1.dp)
                    .background(renderPalette.textRuleColor, shape = RoundedCornerShape(1.dp)),
              )
            }
            Spacer(modifier = Modifier.height(5.dp))
          }

          if (primaryLabelText != null) {
            Text(
              text = primaryLabelText,
              style =
                TextStyle(
                  fontFamily = primaryFont,
                  fontWeight =
                    if (isMinimalText) FontWeight.Normal
                    else FontWeight.Medium,
                  fontSize = if (isMinimalText) 10.5.sp else 11.5.sp,
                  lineHeight = if (isMinimalText) 12.5.sp else 13.5.sp,
                  letterSpacing =
                    when (compositionSpec.typographyStyle) {
                      BackTypographyStyle.GEOMETRIC_SANS -> 0.1.sp
                      BackTypographyStyle.MINIMAL_QUIET -> 0.25.sp
                      else -> (-0.1).sp
                    },
                ),
              color = renderPalette.textPrimaryColor,
              textAlign = TextAlign.Start,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )

            if (yearValue != null) {
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = yearValue.toString(),
                style =
                  TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = if (isMinimalText) 9.5.sp else 10.sp,
                    lineHeight = 12.sp,
                    fontFeatureSettings = "tnum",
                  ),
                color = renderPalette.textSecondaryColor,
                textAlign = TextAlign.Start,
                maxLines = 1,
                softWrap = false,
              )
            }

            if (pagesText != null) {
              Spacer(modifier = Modifier.height(if (yearValue != null) 2.dp else 4.dp))
              Text(
                text = pagesText,
                style =
                  TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = if (isMinimalText) 9.sp else 9.5.sp,
                    lineHeight = 11.5.sp,
                    fontFeatureSettings = "tnum",
                  ),
                color =
                  renderPalette.textSecondaryColor.copy(
                    alpha = renderPalette.textSecondaryColor.alpha * 0.88f,
                  ),
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          } else if (yearValue != null) {
            Text(
              text = yearValue.toString(),
              style =
                TextStyle(
                  fontFamily = primaryFont,
                  fontWeight = FontWeight.Medium,
                  fontSize = 12.sp,
                  lineHeight = 14.sp,
                  fontFeatureSettings = "tnum",
                ),
              color = renderPalette.textPrimaryColor,
              textAlign = TextAlign.Start,
              maxLines = 1,
            )
            if (pagesText != null) {
              Spacer(modifier = Modifier.height(3.dp))
              Text(
                text = pagesText,
                style =
                  TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    fontFeatureSettings = "tnum",
                  ),
                color = renderPalette.textSecondaryColor,
                textAlign = TextAlign.Start,
                maxLines = 1,
              )
            }
          } else if (pagesText != null) {
            Text(
              text = pagesText,
              style =
                TextStyle(
                  fontFamily = JetBrainsMonoFontFamily,
                  fontWeight = FontWeight.Medium,
                  fontSize = 10.5.sp,
                  lineHeight = 13.sp,
                  fontFeatureSettings = "tnum",
                ),
              color = renderPalette.textPrimaryColor,
              textAlign = TextAlign.Start,
              maxLines = 1,
            )
          }
        }
      }

      // Brief, restrained satin sheen synced strictly to the end of the flip gesture (no loop, 0 when reduceMotion)
      if (!reduceMotion) {
        Box(modifier = Modifier.fillMaxSize().clip(shape).then(satinOverlayModifier))
      }
    }
  }
}
