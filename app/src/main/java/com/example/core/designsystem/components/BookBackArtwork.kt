package com.example.core.designsystem.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
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
import com.example.core.designsystem.JetBrainsMonoFontFamily
import com.example.core.designsystem.LocalDipeTypography
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
 * Generates and caches a tiny 64x64 tileable monochrome matte-paper grain texture once per process.
 * Zero per-frame allocations, ~16 KB memory total, subtle tactile fiber tooth without visual noise.
 */
internal object PaperGrainTextureProvider {
  private val tileShaderBrush: ShaderBrush? by lazy {
    runCatching {
      val size = 64
      val pixels = IntArray(size * size)
      var state = 0x5A17C39B
      for (y in 0 until size) {
        // Subtle horizontal paper cellulose bias per row
        state = state xor (state shl 13)
        state = state xor (state ushr 17)
        state = state xor (state shl 5)
        val rowFiber = ((state and 0xF) - 8)
        for (x in 0 until size) {
          state = state xor (state shl 13)
          state = state xor (state ushr 17)
          state = state xor (state shl 5)
          val fine = ((state ushr 8) and 0x1F) - 16
          val delta = (rowFiber + fine).coerceIn(-24, 24)
          val isLightSpeck = delta >= 0
          val rgb = if (isLightSpeck) 244 else 26
          val alpha = (kotlin.math.abs(delta) * 1.15f).toInt().coerceIn(4, 28)
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

/**
 * Maps a [BookCoverVisualAnalysis] into a restrained, coated-board back artwork palette that
 * never appears brighter or harsher than the front cover, preserves spatial color flow, and
 * ensures guaranteed typography legibility and cohesive hardcover board edges.
 */
internal fun resolveBackArtworkRenderPalette(
  analysis: BookCoverVisualAnalysis,
  isDarkTheme: Boolean,
): BackArtworkRenderPalette {
  val dom = analysis.palette.dominant
  val sec = analysis.palette.secondary
  val acc = analysis.palette.accent
  val sp = analysis.spatial

  // Prevent blank-white or crushed-black back surfaces while retaining original hue identity
  fun stabilizeFieldColor(raw: Color, warmth: Float): Color {
    val warmMatte = if (warmth >= -0.04f) Color(0xFFD8CCB6) else Color(0xFFCCD3D9)
    val deepMatte = if (warmth >= -0.04f) Color(0xFF28231E) else Color(0xFF21272E)
    var adjusted =
      when {
        raw.luminance() > 0.80f -> lerp(raw, warmMatte, 0.66f)
        raw.luminance() < 0.045f -> lerp(raw, deepMatte, 0.68f)
        else -> lerp(raw, if (raw.luminance() > 0.45f) warmMatte else deepMatte, 0.16f)
      }
    if (isDarkTheme && adjusted.luminance() > 0.62f) {
      adjusted = lerp(adjusted, deepMatte, 0.28f)
    } else if (!isDarkTheme && adjusted.luminance() < 0.07f) {
      adjusted = lerp(adjusted, deepMatte, 0.25f)
    }
    return adjusted
  }

  val baseDominant = stabilizeFieldColor(dom, analysis.mood.warmth)
  val topField =
    lerp(stabilizeFieldColor(sp.topBand, analysis.mood.warmth), baseDominant, 0.26f)
  val midField =
    lerp(stabilizeFieldColor(sp.midBand, analysis.mood.warmth), baseDominant, 0.34f)
  val bottomField =
    lerp(stabilizeFieldColor(sp.bottomBand, analysis.mood.warmth), baseDominant, 0.26f)

  val rawPrimaryShape =
    when (analysis.motif) {
      BackArtworkMotif.CENTER_MEDALLION -> stabilizeFieldColor(sp.centerColor, analysis.mood.warmth)
      BackArtworkMotif.DIAGONAL_SPLIT -> stabilizeFieldColor(sp.rightColor, analysis.mood.warmth)
      BackArtworkMotif.HORIZON_BANDS -> stabilizeFieldColor(sp.bottomBand, analysis.mood.warmth)
      BackArtworkMotif.EDITORIAL_FRAME -> stabilizeFieldColor(sec, analysis.mood.warmth)
      BackArtworkMotif.ASYMMETRIC_FIELD -> stabilizeFieldColor(sec, analysis.mood.warmth)
    }
  // Guarantee meaningful tonal/chromatic separation between field and primary geometric form
  val primaryShapeColor =
    if (BookCoverAnalyzer.perceptualDistance(rawPrimaryShape, midField) < 0.11f) {
      if (midField.luminance() < 0.42f) lerp(midField, Color(0xFFE6DEC9), 0.20f)
      else lerp(midField, Color(0xFF28221D), 0.18f)
    } else {
      rawPrimaryShape
    }

  val secondaryShapeColor =
    lerp(
      stabilizeFieldColor(sec, analysis.mood.warmth),
      if (midField.luminance() < 0.42f) Color(0xFFEDE4D2) else Color(0xFF251F1A),
      0.16f,
    )

  val accentColor =
    if (BookCoverAnalyzer.perceptualDistance(acc, midField) < 0.14f) {
      if (midField.luminance() < 0.45f) lerp(acc, Color(0xFFE0BA7A), 0.42f)
      else lerp(acc, Color(0xFF8E3F2C), 0.40f)
    } else {
      acc
    }

  val hairlineColor =
    if (midField.luminance() < 0.42f) {
      lerp(accentColor, Color(0xFFF3ECE0), 0.45f).copy(alpha = 0.42f)
    } else {
      lerp(accentColor, Color(0xFF221D18), 0.45f).copy(alpha = 0.34f)
    }

  // Hardcover board rim color: richly deepened cover board tone wrapping the back cover
  val edgeAnchor = lerp(analysis.palette.edgePalette.mid, bottomField, 0.35f)
  val boardEdgeColor =
    if (edgeAnchor.luminance() > 0.65f) {
      lerp(edgeAnchor, Color(0xFF4B4236), 0.36f)
    } else if (edgeAnchor.luminance() < 0.08f) {
      lerp(edgeAnchor, Color(0xFF453E36), 0.30f)
    } else {
      lerp(edgeAnchor, Color(0xFF1E1915), 0.24f)
    }
  val borderColor =
    if (isDarkTheme) lerp(boardEdgeColor, Color(0xFFE8DFCE), 0.16f).copy(alpha = 0.65f)
    else lerp(boardEdgeColor, Color(0xFF1E1915), 0.20f).copy(alpha = 0.52f)

  // Typography readability anchored to upper artwork field
  val upperZoneApprox = lerp(topField, midField, 0.28f)
  val useLightInk = upperZoneApprox.luminance() < 0.43f
  val candidatePrimary =
    if (useLightInk) Color(0xFFF5EFE4) else Color(0xFF1A1612)
  val candidateSecondary =
    if (useLightInk) Color(0xFFE3DACB).copy(alpha = 0.88f)
    else Color(0xFF332C25).copy(alpha = 0.86f)
  val rawContrast = contrastRatio(candidatePrimary, upperZoneApprox)
  val textCushionColor = if (useLightInk) Color(0xFF171310) else Color(0xFFF1E9DA)
  val textCushionAlpha =
    when {
      rawContrast < 4.5f -> 0.34f
      rawContrast < 6.5f -> 0.16f
      else -> 0.06f
    }
  val textRuleColor =
    if (useLightInk) {
      lerp(accentColor, Color(0xFFF5EFE4), 0.48f).copy(alpha = 0.78f)
    } else {
      lerp(accentColor, Color(0xFF282019), 0.42f).copy(alpha = 0.72f)
    }

  return BackArtworkRenderPalette(
    topFieldColor = topField,
    midFieldColor = midField,
    bottomFieldColor = bottomField,
    primaryShapeColor = primaryShapeColor,
    secondaryShapeColor = secondaryShapeColor,
    accentColor = accentColor,
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
) {
  val typography = LocalDipeTypography.current
  val isDarkTheme = renderPalette.midFieldColor.luminance() < 0.40f
  val grainBrush = remember { PaperGrainTextureProvider.brush() }

  val artworkCacheModifier =
    remember(analysis, renderPalette, isDarkTheme, grainBrush) {
      Modifier.drawWithCache {
          val w = size.width
          val h = size.height
          val seed = analysis.compositionSeed
          val mirrorH = (seed and 1) == 1

          val baseBrush =
            if (analysis.spatial.leftVsRightContrast > analysis.spatial.topVsBottomContrast * 1.18f) {
              val leftTone =
                lerp(
                  renderPalette.topFieldColor,
                  lerp(analysis.spatial.leftColor, renderPalette.midFieldColor, 0.35f),
                  0.65f,
                )
              val rightTone =
                lerp(
                  renderPalette.bottomFieldColor,
                  lerp(analysis.spatial.rightColor, renderPalette.midFieldColor, 0.35f),
                  0.65f,
                )
              Brush.linearGradient(
                0.0f to leftTone,
                0.55f to renderPalette.midFieldColor,
                1.0f to rightTone,
                start = Offset(0f, 0f),
                end = Offset(w, h),
              )
            } else {
              Brush.verticalGradient(
                0.0f to renderPalette.topFieldColor,
                0.52f to renderPalette.midFieldColor,
                1.0f to renderPalette.bottomFieldColor,
                startY = 0f,
                endY = h,
              )
            }

          val primaryStroke = Stroke(width = 0.65.dp.toPx())
          val primaryPath = Path()
          val secondaryPath = Path()

          when (analysis.motif) {
            BackArtworkMotif.HORIZON_BANDS -> {
              val crestY = h * (0.46f + ((seed % 7) * 0.015f))
              val lowerY = h * 0.72f
              primaryPath.apply {
                moveTo(0f, crestY)
                cubicTo(
                  w * 0.34f,
                  crestY - h * 0.065f,
                  w * 0.68f,
                  crestY + h * 0.055f,
                  w,
                  crestY - h * 0.015f,
                )
                lineTo(w, h)
                lineTo(0f, h)
                close()
              }
              secondaryPath.apply {
                moveTo(0f, lowerY)
                cubicTo(
                  w * 0.38f,
                  lowerY + h * 0.045f,
                  w * 0.72f,
                  lowerY - h * 0.055f,
                  w,
                  lowerY + h * 0.01f,
                )
                lineTo(w, h)
                lineTo(0f, h)
                close()
              }
            }

            BackArtworkMotif.CENTER_MEDALLION -> {
              val medW = w * 0.56f
              val medH = h * 0.44f
              val left = (w - medW) * 0.5f - (w * 0.03f)
              val top = h * 0.41f
              val archR = CornerRadius(medW * 0.48f, medW * 0.48f)
              val baseR = CornerRadius(6.dp.toPx(), 6.dp.toPx())
              primaryPath.addRoundRect(
                RoundRect(
                  left = left,
                  top = top,
                  right = left + medW,
                  bottom = top + medH,
                  topLeftCornerRadius = archR,
                  topRightCornerRadius = archR,
                  bottomLeftCornerRadius = baseR,
                  bottomRightCornerRadius = baseR,
                )
              )
              val inset = 4.dp.toPx()
              secondaryPath.addRoundRect(
                RoundRect(
                  left = left + inset,
                  top = top + inset,
                  right = left + medW - inset,
                  bottom = top + medH - inset,
                  topLeftCornerRadius = archR,
                  topRightCornerRadius = archR,
                  bottomLeftCornerRadius = baseR,
                  bottomRightCornerRadius = baseR,
                )
              )
            }

            BackArtworkMotif.DIAGONAL_SPLIT -> {
              if (!mirrorH) {
                primaryPath.apply {
                  moveTo(0f, h * 0.58f)
                  lineTo(w, h * 0.34f)
                  lineTo(w, h)
                  lineTo(0f, h)
                  close()
                }
                secondaryPath.apply {
                  moveTo(0f, h * 0.78f)
                  lineTo(w * 0.86f, h * 0.56f)
                  lineTo(w * 0.86f, h)
                  lineTo(0f, h)
                  close()
                }
              } else {
                primaryPath.apply {
                  moveTo(0f, h * 0.38f)
                  lineTo(w, h * 0.60f)
                  lineTo(w, h)
                  lineTo(0f, h)
                  close()
                }
                secondaryPath.apply {
                  moveTo(w * 0.14f, h * 0.62f)
                  lineTo(w, h * 0.76f)
                  lineTo(w, h)
                  lineTo(w * 0.14f, h)
                  close()
                }
              }
            }

            BackArtworkMotif.EDITORIAL_FRAME -> {
              val pad = 8.dp.toPx()
              val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())
              primaryPath.addRoundRect(
                RoundRect(
                  rect = Rect(pad, h * 0.40f, w - pad - 2.dp.toPx(), h - pad),
                  cornerRadius = corner,
                )
              )
              secondaryPath.addRoundRect(
                RoundRect(
                  rect = Rect(pad * 1.4f, pad, w - pad - 2.dp.toPx(), h - pad),
                  cornerRadius = corner,
                )
              )
            }

            BackArtworkMotif.ASYMMETRIC_FIELD -> {
              val anchorRight =
                analysis.spatial.heaviestQuadrant == SpatialQuadrant.TOP_RIGHT ||
                  analysis.spatial.heaviestQuadrant == SpatialQuadrant.BOTTOM_RIGHT
              val monolithW = w * 0.62f
              val monolithH = h * 0.50f
              val mLeft = if (anchorRight) w - monolithW + w * 0.08f else -w * 0.08f
              val mTop = h * 0.44f
              val bigR = CornerRadius(monolithW * 0.46f, monolithW * 0.46f)
              primaryPath.addRoundRect(
                RoundRect(
                  rect = Rect(mLeft, mTop, mLeft + monolithW, mTop + monolithH + h * 0.12f),
                  cornerRadius = bigR,
                )
              )
              val secLeft = if (anchorRight) w * 0.12f else w * 0.34f
              val secW = w * 0.44f
              secondaryPath.addRoundRect(
                RoundRect(
                  rect = Rect(secLeft, h * 0.60f, secLeft + secW, h * 0.94f),
                  cornerRadius = CornerRadius(secW * 0.44f, secW * 0.44f),
                )
              )
            }
          }

          val textCushionBrush =
            if (renderPalette.textCushionAlpha > 0.01f) {
              Brush.verticalGradient(
                0.0f to renderPalette.textCushionColor.copy(alpha = renderPalette.textCushionAlpha),
                0.42f to renderPalette.textCushionColor.copy(alpha = renderPalette.textCushionAlpha * 0.55f),
                0.68f to Color.Transparent,
                startY = 0f,
                endY = h,
              )
            } else {
              null
            }

          // Restrained vignette & physical right-edge spine hinge crease (on flipped back cover, spine is on right)
          val edgeVignetteBrush =
            Brush.verticalGradient(
              0.0f to Color.Black.copy(alpha = 0.04f),
              0.15f to Color.Transparent,
              0.84f to Color.Transparent,
              1.0f to Color.Black.copy(alpha = if (isDarkTheme) 0.14f else 0.075f),
              startY = 0f,
              endY = h,
            )
          val spineCreaseBrush =
            Brush.horizontalGradient(
              0.90f to Color.Transparent,
              0.945f to Color.White.copy(alpha = if (isDarkTheme) 0.055f else 0.16f),
              0.985f to Color.Black.copy(alpha = if (isDarkTheme) 0.24f else 0.095f),
              1.0f to Color.Black.copy(alpha = if (isDarkTheme) 0.30f else 0.12f),
              startX = 0f,
              endX = w,
            )

          val accentDotCenter =
            when (analysis.motif) {
              BackArtworkMotif.CENTER_MEDALLION -> Offset(w * 0.50f - w * 0.03f, h * 0.78f)
              BackArtworkMotif.EDITORIAL_FRAME -> Offset(w - 16.dp.toPx(), h - 16.dp.toPx())
              BackArtworkMotif.DIAGONAL_SPLIT ->
                Offset(if (mirrorH) w * 0.24f else w * 0.76f, h * 0.74f)
              BackArtworkMotif.HORIZON_BANDS -> Offset(w * 0.22f, h * 0.64f)
              BackArtworkMotif.ASYMMETRIC_FIELD -> Offset(w * 0.74f, h * 0.58f)
            }
          val accentDotRadius = 3.2.dp.toPx()

          onDrawBehind {
            drawRect(brush = baseBrush)

            when (analysis.motif) {
              BackArtworkMotif.EDITORIAL_FRAME -> {
                drawPath(
                  path = primaryPath,
                  color = renderPalette.primaryShapeColor.copy(alpha = 0.32f),
                )
                drawPath(
                  path = secondaryPath,
                  color = renderPalette.hairlineColor,
                  style = primaryStroke,
                )
                drawCircle(
                  color = renderPalette.accentColor.copy(alpha = 0.80f),
                  radius = accentDotRadius,
                  center = accentDotCenter,
                )
              }

              BackArtworkMotif.CENTER_MEDALLION -> {
                drawPath(
                  path = primaryPath,
                  color = renderPalette.primaryShapeColor.copy(alpha = 0.88f),
                )
                drawPath(
                  path = secondaryPath,
                  color = renderPalette.hairlineColor,
                  style = primaryStroke,
                )
                drawCircle(
                  color = renderPalette.accentColor.copy(alpha = 0.85f),
                  radius = accentDotRadius * 0.9f,
                  center = accentDotCenter,
                )
              }

              else -> {
                drawPath(
                  path = primaryPath,
                  color = renderPalette.primaryShapeColor.copy(alpha = 0.88f),
                )
                drawPath(
                  path = secondaryPath,
                  color = renderPalette.secondaryShapeColor.copy(alpha = 0.56f),
                )
                drawCircle(
                  color = renderPalette.accentColor.copy(alpha = 0.84f),
                  radius = accentDotRadius,
                  center = accentDotCenter,
                )
              }
            }

            if (textCushionBrush != null) {
              drawRect(brush = textCushionBrush)
            }

            // Ultra-subtle fibrous matte paper tooth
            if (grainBrush != null) {
              drawRect(brush = grainBrush, alpha = if (isDarkTheme) 0.55f else 0.70f)
            }

            drawRect(brush = edgeVignetteBrush)
            drawRect(brush = spineCreaseBrush)
          }
        }
    }

  val satinOverlayModifier =
    if (reduceMotion) {
      Modifier
    } else {
      Modifier.drawWithCache {
        val bandWidth = (size.width * 0.56f).coerceAtLeast(1f)
        val satinColor = Color(0xFFFFF8EC).copy(alpha = 0.11f)
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

  Surface(
    color = renderPalette.midFieldColor,
    shape = shape,
    border = BorderStroke(0.75.dp, renderPalette.borderColor),
    shadowElevation = 0.dp,
    modifier = modifier,
  ) {
    Box(modifier = Modifier.fillMaxSize().then(artworkCacheModifier)) {
      // Editorial colophon metadata in upper quiet zone (clears lower thumb hold area & right spine hinge)
      val hasAnyMeta = publisherText != null || yearValue != null || pagesText != null
      if (hasAnyMeta) {
        Column(
          modifier =
            Modifier.fillMaxSize()
              .padding(start = 10.dp, end = 12.dp, top = 11.dp, bottom = 10.dp),
          horizontalAlignment = Alignment.Start,
          verticalArrangement = Arrangement.Top,
        ) {
          Box(
            modifier =
              Modifier.width(16.dp)
                .height(1.2.dp)
                .background(renderPalette.textRuleColor, shape = RoundedCornerShape(1.dp)),
          )
          Spacer(modifier = Modifier.height(6.dp))

          if (publisherText != null) {
            Text(
              text = publisherText,
              style =
                TextStyle(
                  fontFamily = typography.displayFontFamily,
                  fontWeight = FontWeight.Medium,
                  fontSize = 11.5.sp,
                  lineHeight = 13.5.sp,
                  letterSpacing = (-0.1).sp,
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
                    fontSize = 10.sp,
                    lineHeight = 12.5.sp,
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
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    fontFeatureSettings = "tnum",
                  ),
                color = renderPalette.textSecondaryColor.copy(alpha = renderPalette.textSecondaryColor.alpha * 0.88f),
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
                  fontFamily = typography.displayFontFamily,
                  fontWeight = FontWeight.Medium,
                  fontSize = 12.5.sp,
                  lineHeight = 14.5.sp,
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
        Box(modifier = Modifier.fillMaxSize().then(satinOverlayModifier))
      }
    }
  }
}
