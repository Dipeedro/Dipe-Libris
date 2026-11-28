package com.example.core.designsystem.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class BackArtworkPreparedGeometry(
  val primaryPath: Path = Path(),
  val secondaryPath: Path = Path(),
  val tertiaryPath: Path = Path(),
  val strokePath: Path = Path(),
  val accentPath: Path = Path(),
  var atmosphericBrush: Brush? = null,
  val primaryCircles: MutableList<Pair<Offset, Float>> = ArrayList(4),
  val accentDots: MutableList<Pair<Offset, Float>> = ArrayList(8),
  val lines: MutableList<Pair<Offset, Offset>> = ArrayList(14),
  val patternDots: MutableList<Pair<Offset, Float>> = ArrayList(36),
  val monoShadeColor: Color = Color.Transparent,
  val monoTintColor: Color = Color.Transparent,
  val glowColor: Color = Color.Transparent,
)

internal object BookBackDesignRenderer {

  fun prepareGeometry(
    w: Float,
    h: Float,
    dpToPx: (Float) -> Float,
    analysis: BookCoverVisualAnalysis,
    spec: BackArtworkCompositionSpec,
    palette: BackArtworkRenderPalette,
  ): BackArtworkPreparedGeometry {
    val g =
      BackArtworkPreparedGeometry(
        monoShadeColor = lerp(palette.midFieldColor, palette.darkContrastColor, 0.28f),
        monoTintColor = lerp(palette.midFieldColor, palette.lightSupportColor, 0.34f),
        glowColor = lerp(palette.accentColor, palette.lightSupportColor, 0.45f),
      )
    val mirror = spec.mirrorHorizontal
    val v = spec.variantIndex
    val scale = spec.scaleFactor
    val count = spec.elementCount

    when (spec.family) {
      BackArtworkDesignFamily.GEOMETRIC ->
        prepareGeometric(w, h, dpToPx, v, scale, mirror, spec, g)
      BackArtworkDesignFamily.ORGANIC ->
        prepareOrganic(w, h, dpToPx, v, scale, mirror, g)
      BackArtworkDesignFamily.SYMBOLIC ->
        prepareSymbolic(w, h, dpToPx, v, scale, mirror, g)
      BackArtworkDesignFamily.ATMOSPHERIC ->
        prepareAtmospheric(w, h, v, scale, mirror, palette, g)
      BackArtworkDesignFamily.BOTANICAL ->
        prepareBotanical(w, h, dpToPx, v, scale, count, mirror, g)
      BackArtworkDesignFamily.CELESTIAL ->
        prepareCelestial(w, h, dpToPx, v, scale, count, mirror, g)
      BackArtworkDesignFamily.ABSTRACT ->
        prepareAbstract(w, h, dpToPx, v, scale, mirror, g)
      BackArtworkDesignFamily.PATTERNED ->
        preparePatterned(w, h, dpToPx, v, scale, count, mirror, g)
      BackArtworkDesignFamily.MONOCHROMATIC ->
        prepareMonochromatic(w, h, dpToPx, v, scale, mirror, g)
      BackArtworkDesignFamily.ILLUSTRATIVE ->
        prepareIllustrative(w, h, dpToPx, v, scale, count, mirror, analysis, g)
    }

    prepareBorderTreatment(w, h, dpToPx, spec.borderTreatment, g)
    return g
  }

  private fun prepareGeometric(
    w: Float,
    h: Float,
    dpToPx: (Float) -> Float,
    v: Int,
    scale: Float,
    mirror: Boolean,
    spec: BackArtworkCompositionSpec,
    g: BackArtworkPreparedGeometry,
  ) {
    val tilt = (spec.orientationDeg / 18f) * 0.08f
    when (v) {
      0 -> {
        // Clean full-bleed edge-to-edge diagonal division + structural stepped bar
        val leftY = h * (0.56f + tilt).coerceIn(0.42f, 0.70f)
        val rightY = h * (0.36f - tilt).coerceIn(0.28f, 0.56f)
        g.primaryPath.apply {
          moveTo(0f, if (!mirror) leftY else rightY)
          lineTo(w, if (!mirror) rightY else leftY)
          lineTo(w, h)
          lineTo(0f, h)
          close()
        }
        val barTop = h * 0.69f
        g.secondaryPath.apply {
          moveTo(0f, if (!mirror) barTop else barTop - h * 0.08f)
          lineTo(w, if (!mirror) barTop - h * 0.08f else barTop)
          lineTo(w, h)
          lineTo(0f, h)
          close()
        }
        g.accentDots.add(Offset(if (mirror) w * 0.24f else w * 0.76f, h * 0.78f) to dpToPx(3.6f))
      }

      1 -> {
        // Bauhaus interlocking asymmetric blocks anchored cleanly in lower 55%
        val blockW = w * 0.58f * scale.coerceAtMost(1.06f)
        val blockX = if (mirror) w - blockW else 0f
        g.primaryPath.addRect(Rect(blockX, h * 0.46f, blockX + blockW, h))
        val secX = if (mirror) 0f else w * 0.44f
        val secW = w * 0.56f
        g.secondaryPath.addRect(Rect(secX, h * 0.68f, secX + secW, h))
        val accentX = if (mirror) w * 0.18f else w * 0.72f
        g.accentDots.add(Offset(accentX, h * 0.56f) to dpToPx(5.5f))
        g.lines.add(Offset(dpToPx(10f), h * 0.42f) to Offset(w - dpToPx(10f), h * 0.42f))
      }

      2 -> {
        // Architectural column + crisp geometric half-circle balance
        val colW = w * 0.38f
        val colX = if (mirror) w * 0.62f else 0f
        g.primaryPath.addRect(Rect(colX, h * 0.42f, colX + colW, h))
        val cx = if (mirror) w * 0.36f else w * 0.64f
        val r = w * 0.22f * scale.coerceIn(0.85f, 1.12f)
        g.primaryCircles.add(Offset(cx, h * 0.74f) to r)
        g.accentDots.add(Offset(cx, h * 0.48f) to dpToPx(3.2f))
      }

      else -> {
        // Framed prism & nested horizon monolith
        val pad = dpToPx(10f)
        g.primaryPath.addRoundRect(
          RoundRect(
            rect = Rect(pad, h * 0.48f, w - pad, h - pad),
            cornerRadius = CornerRadius(dpToPx(4f), dpToPx(4f)),
          )
        )
        val innerX = if (mirror) pad + dpToPx(6f) else w * 0.42f
        val innerR = if (mirror) w * 0.58f else w - pad - dpToPx(6f)
        g.secondaryPath.addRoundRect(
          RoundRect(
            rect = Rect(innerX, h * 0.56f, innerR, h - pad - dpToPx(6f)),
            cornerRadius = CornerRadius(dpToPx(3f), dpToPx(3f)),
          )
        )
        g.accentDots.add(Offset(if (mirror) w * 0.76f else w * 0.24f, h * 0.74f) to dpToPx(4f))
      }
    }
  }

  private fun prepareOrganic(
    w: Float,
    h: Float,
    dpToPx: (Float) -> Float,
    v: Int,
    scale: Float,
    mirror: Boolean,
    g: BackArtworkPreparedGeometry,
  ) {
    when (v) {
      0 -> {
        val c1 = h * (0.48f + (if (mirror) 0.03f else -0.02f))
        val c2 = h * 0.70f
        g.primaryPath.apply {
          moveTo(0f, c1)
          cubicTo(w * 0.32f, c1 - h * 0.08f, w * 0.66f, c1 + h * 0.07f, w, c1 - h * 0.02f)
          lineTo(w, h)
          lineTo(0f, h)
          close()
        }
        g.secondaryPath.apply {
          moveTo(0f, c2)
          cubicTo(w * 0.36f, c2 + h * 0.06f, w * 0.72f, c2 - h * 0.07f, w, c2 + h * 0.02f)
          lineTo(w, h)
          lineTo(0f, h)
          close()
        }
        g.accentDots.add(Offset(if (mirror) w * 0.74f else w * 0.26f, c1 - h * 0.06f) to dpToPx(3.6f))
      }

      1 -> {
        // Flowing asymmetrical ribbon curve from side edge to bottom
        val startX = if (mirror) w else 0f
        val dir = if (mirror) -1f else 1f
        g.primaryPath.apply {
          moveTo(startX, h * 0.44f)
          cubicTo(
            startX + dir * w * 0.62f,
            h * 0.40f,
            startX + dir * w * 0.42f,
            h * 0.78f,
            startX + dir * w,
            h * 0.66f,
          )
          lineTo(startX + dir * w, h)
          lineTo(startX, h)
          close()
        }
        val cx = if (mirror) w * 0.32f else w * 0.68f
        g.primaryCircles.add(Offset(cx, h * 0.78f) to (w * 0.16f * scale.coerceAtMost(1.1f)))
        g.accentDots.add(Offset(if (mirror) w * 0.22f else w * 0.78f, h * 0.50f) to dpToPx(3.2f))
      }

      2 -> {
        // Soft river-stone pebble silhouettes stacked delicately in lower space
        val cx = if (mirror) w * 0.56f else w * 0.44f
        val rBig = w * 0.30f * scale.coerceIn(0.85f, 1.08f)
        g.primaryPath.addRoundRect(
          RoundRect(
            rect = Rect(cx - rBig, h * 0.58f, cx + rBig, h * 0.92f),
            cornerRadius = CornerRadius(rBig, rBig * 0.85f),
          )
        )
        val sx = if (mirror) w * 0.24f else w * 0.74f
        val rSmall = w * 0.19f
        g.secondaryPath.addRoundRect(
          RoundRect(
            rect = Rect(sx - rSmall, h * 0.46f, sx + rSmall, h * 0.68f),
            cornerRadius = CornerRadius(rSmall, rSmall),
          )
        )
        g.accentDots.add(Offset(cx, h * 0.75f) to dpToPx(3.5f))
      }

      else -> {
        // Soft organic dome silhouette anchored at base
        val domeW = w * 0.78f
        val dLeft = if (mirror) w - domeW + w * 0.08f else -w * 0.08f
        g.primaryPath.addRoundRect(
          RoundRect(
            left = dLeft,
            top = h * 0.45f,
            right = dLeft + domeW,
            bottom = h + h * 0.08f,
            topLeftCornerRadius = CornerRadius(domeW * 0.52f, domeW * 0.52f),
            topRightCornerRadius = CornerRadius(domeW * 0.52f, domeW * 0.52f),
          )
        )
        g.accentDots.add(Offset(if (mirror) w * 0.22f else w * 0.78f, h * 0.52f) to dpToPx(4.2f))
      }
    }
  }

  private fun prepareSymbolic(
    w: Float,
    h: Float,
    dpToPx: (Float) -> Float,
    v: Int,
    scale: Float,
    mirror: Boolean,
    g: BackArtworkPreparedGeometry,
  ) {
    val cx = w * 0.5f + (if (mirror) w * 0.02f else -w * 0.02f)
    val cy = h * 0.65f
    when (v) {
      0 -> {
        // Classic editorial arch portal with nested hairline arch & seal dot
        val medW = w * 0.56f * scale.coerceIn(0.85f, 1.12f)
        val medH = h * 0.43f
        val left = cx - medW * 0.5f
        val top = h * 0.44f
        val archR = CornerRadius(medW * 0.5f, medW * 0.5f)
        val baseR = CornerRadius(dpToPx(5f), dpToPx(5f))
        g.primaryPath.addRoundRect(
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
        val inset = dpToPx(5f)
        g.strokePath.addRoundRect(
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
        g.accentDots.add(Offset(cx, top + medH * 0.52f) to dpToPx(4f))
      }

      1 -> {
        // Concentric sacred-geometry rings + fine cardinal crosshairs
        val rOuter = min(w, h) * 0.27f * scale.coerceIn(0.85f, 1.12f)
        g.primaryCircles.add(Offset(cx, cy) to rOuter)
        g.strokePath.addOval(Rect(cx - rOuter * 1.24f, cy - rOuter * 1.24f, cx + rOuter * 1.24f, cy + rOuter * 1.24f))
        g.strokePath.addOval(Rect(cx - rOuter * 0.62f, cy - rOuter * 0.62f, cx + rOuter * 0.62f, cy + rOuter * 0.62f))
        g.lines.add(Offset(cx, cy - rOuter * 1.38f) to Offset(cx, cy + rOuter * 1.38f))
        g.accentDots.add(Offset(cx, cy) to dpToPx(3.6f))
      }

      2 -> {
        // Dual split emblem & diamond seal
        val r = min(w, h) * 0.24f * scale.coerceIn(0.85f, 1.12f)
        g.primaryPath.apply {
          moveTo(cx, cy - r)
          lineTo(cx + r * 0.92f, cy)
          lineTo(cx, cy + r)
          lineTo(cx - r * 0.92f, cy)
          close()
        }
        val rInner = r * 0.56f
        g.strokePath.addOval(Rect(cx - r * 1.15f, cy - r * 1.15f, cx + r * 1.15f, cy + r * 1.15f))
        g.secondaryPath.apply {
          moveTo(cx, cy - rInner)
          lineTo(cx + rInner * 0.92f, cy)
          lineTo(cx, cy + rInner)
          lineTo(cx - rInner * 0.92f, cy)
          close()
        }
        g.accentDots.add(Offset(cx, cy) to dpToPx(3.2f))
      }

      else -> {
        // Architectural keystone emblem + paired side points
        val kw = w * 0.44f
        val kh = h * 0.36f
        g.primaryPath.addRoundRect(
          RoundRect(
            rect = Rect(cx - kw * 0.5f, cy - kh * 0.5f, cx + kw * 0.5f, cy + kh * 0.5f),
            cornerRadius = CornerRadius(kw * 0.5f, kw * 0.5f),
          )
        )
        g.lines.add(Offset(cx - kw * 0.72f, cy) to Offset(cx + kw * 0.72f, cy))
        g.accentDots.add(Offset(cx, cy - kh * 0.5f - dpToPx(7f)) to dpToPx(2.8f))
        g.accentDots.add(Offset(cx, cy + kh * 0.5f + dpToPx(7f)) to dpToPx(2.8f))
      }
    }
  }

  private fun prepareAtmospheric(
    w: Float,
    h: Float,
    v: Int,
    scale: Float,
    mirror: Boolean,
    palette: BackArtworkRenderPalette,
    g: BackArtworkPreparedGeometry,
  ) {
    val glowCenter =
      when (v) {
        0 -> Offset(if (mirror) w * 0.36f else w * 0.64f, h * 0.64f)
        1 -> Offset(w * 0.50f, h * 0.76f)
        2 -> Offset(if (mirror) w * 0.25f else w * 0.75f, h * 0.54f)
        else -> Offset(w * 0.50f, h * 0.58f)
      }
    val glowRadius = max(w, h) * 0.62f * scale.coerceIn(0.88f, 1.15f)
    g.atmosphericBrush =
      Brush.radialGradient(
        0.0f to lerp(palette.primaryShapeColor, palette.lightSupportColor, 0.24f).copy(alpha = 0.72f),
        0.52f to palette.secondaryShapeColor.copy(alpha = 0.34f),
        1.0f to Color.Transparent,
        center = glowCenter,
        radius = glowRadius,
      )

    if (v == 1 || v == 3) {
      g.primaryPath.apply {
        moveTo(0f, h * 0.66f)
        cubicTo(w * 0.30f, h * 0.60f, w * 0.70f, h * 0.70f, w, h * 0.63f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
      }
    } else if (v == 2) {
      g.primaryCircles.add(Offset(if (mirror) w * 0.34f else w * 0.66f, h * 0.58f) to w * 0.16f)
    }
  }

  private fun prepareBotanical(
    w: Float,
    h: Float,
    dpToPx: (Float) -> Float,
    v: Int,
    scale: Float,
    count: Int,
    mirror: Boolean,
    g: BackArtworkPreparedGeometry,
  ) {
    val baseX = if (mirror) w * 0.64f else w * 0.36f
    val dir = if (mirror) -1f else 1f
    val leafCount = count.coerceIn(3, 5)

    fun addLeaf(
      anchor: Offset,
      tip: Offset,
      widthScale: Float,
      target: Path,
    ) {
      val dx = tip.x - anchor.x
      val dy = tip.y - anchor.y
      val nx = -dy * 0.42f * widthScale
      val ny = dx * 0.42f * widthScale
      val midX = (anchor.x + tip.x) * 0.5f
      val midY = (anchor.y + tip.y) * 0.5f
      target.moveTo(anchor.x, anchor.y)
      target.quadraticTo(midX + nx, midY + ny, tip.x, tip.y)
      target.quadraticTo(midX - nx, midY - ny, anchor.x, anchor.y)
      target.close()
    }

    when (v) {
      0, 1 -> {
        // Slender curved stem rising cleanly in lower 55% with alternating pressed leaves
        val stemTop = Offset(baseX + dir * w * 0.14f, h * 0.44f)
        val stemBot = Offset(baseX - dir * w * 0.04f, h * 0.94f)
        g.strokePath.apply {
          moveTo(stemBot.x, stemBot.y)
          quadraticTo(baseX + dir * w * 0.09f, h * 0.68f, stemTop.x, stemTop.y)
        }
        val leafLen = w * 0.22f * scale.coerceIn(0.84f, 1.12f)
        for (i in 0 until leafCount) {
          val t = 0.22f + (i / leafCount.toFloat()) * 0.64f
          val sx = stemBot.x + (stemTop.x - stemBot.x) * t + dir * w * 0.03f * sin(t * PI).toFloat()
          val sy = stemBot.y + (stemTop.y - stemBot.y) * t
          val side = if (i % 2 == 0) 1f else -1f
          val tip = Offset(sx + side * dir * leafLen * 0.85f, sy - leafLen * 0.65f)
          addLeaf(Offset(sx, sy), tip, 0.72f, if (i % 2 == 0) g.primaryPath else g.secondaryPath)
        }
        g.accentDots.add(Offset(stemTop.x, stemTop.y - dpToPx(4f)) to dpToPx(2.8f))
      }

      2 -> {
        // Pressed 5-petal editorial botanical rosette + slender stem
        val cx = w * 0.50f + dir * w * 0.04f
        val cy = h * 0.63f
        val petalLen = w * 0.18f * scale.coerceIn(0.85f, 1.10f)
        g.strokePath.moveTo(cx, cy + petalLen * 0.4f)
        g.strokePath.lineTo(cx - dir * w * 0.03f, h * 0.92f)
        for (i in 0 until 5) {
          val angle = -PI / 2.0 + i * (2.0 * PI / 5.0)
          val tip = Offset(cx + (cos(angle) * petalLen).toFloat(), cy + (sin(angle) * petalLen).toFloat())
          addLeaf(Offset(cx, cy), tip, 0.76f, if (i % 2 == 0) g.primaryPath else g.secondaryPath)
        }
        g.accentDots.add(Offset(cx, cy) to dpToPx(3.6f))
      }

      else -> {
        // Asymmetric corner botanical frond cluster anchored at bottom
        val origin = Offset(if (mirror) w * 0.84f else w * 0.16f, h * 0.92f)
        val frondCount = 4
        for (i in 0 until frondCount) {
          val angle = (if (mirror) -135.0 else -45.0) + (i - 1.5) * 16.0
          val rad = angle * PI / 180.0
          val len = h * (0.28f + (i % 2) * 0.07f)
          val tip = Offset(origin.x + (cos(rad) * len).toFloat(), origin.y + (sin(rad) * len).toFloat())
          addLeaf(origin, tip, 0.56f, if (i % 2 == 0) g.primaryPath else g.secondaryPath)
        }
        g.accentDots.add(Offset(if (mirror) w * 0.28f else w * 0.72f, h * 0.52f) to dpToPx(3.0f))
      }
    }
  }

  private fun prepareCelestial(
    w: Float,
    h: Float,
    dpToPx: (Float) -> Float,
    v: Int,
    scale: Float,
    count: Int,
    mirror: Boolean,
    g: BackArtworkPreparedGeometry,
  ) {
    val cx = w * 0.50f + (if (mirror) -w * 0.04f else w * 0.04f)
    val cy = h * 0.62f

    fun addFourPointStar(center: Offset, r: Float, target: Path) {
      val inner = r * 0.26f
      target.moveTo(center.x, center.y - r)
      target.quadraticTo(center.x + inner, center.y - inner, center.x + r, center.y)
      target.quadraticTo(center.x + inner, center.y + inner, center.x, center.y + r)
      target.quadraticTo(center.x - inner, center.y + inner, center.x - r, center.y)
      target.quadraticTo(center.x - inner, center.y - inner, center.x, center.y - r)
      target.close()
    }

    when (v) {
      0 -> {
        // Moon disc + subtle crescent ring + four-point star
        val r = w * 0.19f * scale.coerceIn(0.85f, 1.12f)
        g.primaryCircles.add(Offset(cx, cy) to r)
        g.strokePath.addOval(Rect(cx - r * 1.55f, cy - r * 0.52f, cx + r * 1.55f, cy + r * 0.52f))
        addFourPointStar(Offset(cx + (if (mirror) -r * 1.1f else r * 1.1f), cy - r * 1.1f), dpToPx(6.5f), g.accentPath)
        g.accentDots.add(Offset(cx - (if (mirror) -r * 1.2f else r * 1.2f), cy + r * 1.1f) to dpToPx(2.2f))
      }

      1 -> {
        // Astrolabe orbital rings + planetary moons
        val r1 = w * 0.25f * scale.coerceIn(0.85f, 1.10f)
        g.strokePath.addOval(Rect(cx - r1, cy - r1, cx + r1, cy + r1))
        g.strokePath.addOval(Rect(cx - r1 * 0.58f, cy - r1 * 0.58f, cx + r1 * 0.58f, cy + r1 * 0.58f))
        g.primaryCircles.add(Offset(cx, cy) to r1 * 0.26f)
        g.accentDots.add(Offset(cx + r1 * 0.72f, cy - r1 * 0.68f) to dpToPx(3.6f))
        g.accentDots.add(Offset(cx - r1 * 0.58f, cy) to dpToPx(2.4f))
      }

      2 -> {
        // Minimal constellation arc across lower sky
        val starCount = count.coerceIn(4, 5)
        val pts =
          List(starCount) { idx ->
            val fx = 0.20f + (idx / (starCount - 1f)) * 0.60f
            val x = if (mirror) w * (1f - fx) else w * fx
            val y = h * (0.52f + ((idx % 2) * 0.11f) + (idx * 0.045f))
            Offset(x, y)
          }
        for (i in 0 until pts.size - 1) {
          g.lines.add(pts[i] to pts[i + 1])
        }
        pts.forEachIndexed { idx, pt ->
          if (idx == 1 || idx == pts.lastIndex) {
            addFourPointStar(pt, dpToPx(5.5f), g.accentPath)
          } else {
            g.accentDots.add(pt to dpToPx(2.4f))
          }
        }
      }

      else -> {
        // Eclipse disc + crisp meridian line
        val r = w * 0.21f * scale.coerceIn(0.85f, 1.10f)
        g.primaryCircles.add(Offset(cx, cy) to r)
        g.strokePath.addOval(Rect(cx - r * 1.28f, cy - r * 1.28f, cx + r * 1.28f, cy + r * 1.28f))
        g.lines.add(Offset(cx, cy - r * 1.55f) to Offset(cx, cy + r * 1.55f))
        addFourPointStar(Offset(if (mirror) w * 0.24f else w * 0.76f, h * 0.48f), dpToPx(5f), g.accentPath)
      }
    }
  }

  private fun prepareAbstract(
    w: Float,
    h: Float,
    dpToPx: (Float) -> Float,
    v: Int,
    scale: Float,
    mirror: Boolean,
    g: BackArtworkPreparedGeometry,
  ) {
    val dir = if (mirror) -1f else 1f
    when (v) {
      0 -> {
        // Cut-paper modernist curved wedge + counter disc
        val startX = if (mirror) w else 0f
        g.primaryPath.apply {
          moveTo(startX, h * 0.45f)
          cubicTo(startX + dir * w * 0.54f, h * 0.44f, startX + dir * w * 0.68f, h * 0.72f, startX + dir * w * 0.42f, h)
          lineTo(startX, h)
          close()
        }
        g.secondaryPath.apply {
          val ox = if (mirror) 0f else w
          moveTo(ox, h * 0.56f)
          quadraticTo(ox - dir * w * 0.48f, h * 0.68f, ox - dir * w * 0.36f, h)
          lineTo(ox, h)
          close()
        }
        g.accentDots.add(Offset(if (mirror) w * 0.28f else w * 0.72f, h * 0.52f) to dpToPx(5f))
      }

      1 -> {
        // Off-axis floating rounded forms
        val mW = w * 0.54f * scale.coerceAtMost(1.06f)
        val mLeft = if (mirror) w * 0.36f else w * 0.10f
        g.primaryPath.addRoundRect(
          RoundRect(
            rect = Rect(mLeft, h * 0.46f, mLeft + mW, h * 0.88f),
            cornerRadius = CornerRadius(mW * 0.42f, mW * 0.42f),
          )
        )
        val sW = w * 0.38f
        val sLeft = if (mirror) w * 0.14f else w * 0.48f
        g.secondaryPath.addRoundRect(
          RoundRect(
            rect = Rect(sLeft, h * 0.62f, sLeft + sW, h * 0.94f),
            cornerRadius = CornerRadius(dpToPx(6f), dpToPx(6f)),
          )
        )
        g.accentDots.add(Offset(if (mirror) w * 0.24f else w * 0.76f, h * 0.54f) to dpToPx(4.2f))
      }

      2 -> {
        // Sweeping diagonal arch + sliced circle
        val cx = if (mirror) w * 0.38f else w * 0.62f
        val r = w * 0.28f
        g.primaryCircles.add(Offset(cx, h * 0.72f) to r)
        g.strokePath.apply {
          moveTo(dpToPx(12f), h * 0.50f)
          cubicTo(w * 0.38f, h * 0.40f, w * 0.62f, h * 0.88f, w - dpToPx(12f), h * 0.56f)
        }
        g.accentDots.add(Offset(if (mirror) w * 0.74f else w * 0.26f, h * 0.48f) to dpToPx(4f))
      }

      else -> {
        // Asymmetric corner sector + geometric block
        val ox = if (mirror) 0f else w
        g.primaryPath.apply {
          moveTo(ox, h)
          lineTo(ox, h * 0.46f)
          quadraticTo(w * 0.5f, h * 0.54f, ox - dir * w * 0.66f, h)
          close()
        }
        g.accentDots.add(Offset(if (mirror) w * 0.72f else w * 0.28f, h * 0.66f) to dpToPx(5.5f))
      }
    }
  }

  private fun preparePatterned(
    w: Float,
    h: Float,
    dpToPx: (Float) -> Float,
    v: Int,
    scale: Float,
    count: Int,
    mirror: Boolean,
    g: BackArtworkPreparedGeometry,
  ) {
    // Keep pattern strictly confined in the lower architectural zone so top metadata stays crisp
    val topY = h * 0.48f
    val botY = h - dpToPx(12f)
    val leftX = dpToPx(12f)
    val rightX = w - dpToPx(14f)

    when (v) {
      0 -> {
        // Confined calm lower mat + uniform micro-dot rhythm grid
        g.primaryPath.addRoundRect(
          RoundRect(
            rect = Rect(leftX, topY, rightX, botY),
            cornerRadius = CornerRadius(dpToPx(5f), dpToPx(5f)),
          )
        )
        val cols = 4
        val rows = 4
        val dotR = dpToPx(1.7f) * scale.coerceIn(0.85f, 1.15f)
        for (ri in 0 until rows) {
          val py = topY + dpToPx(12f) + ((botY - topY - dpToPx(24f)) * (ri / (rows - 1f)))
          for (ci in 0 until cols) {
            val px = leftX + dpToPx(12f) + ((rightX - leftX - dpToPx(24f)) * (ci / (cols - 1f)))
            g.patternDots.add(Offset(px, py) to dotR)
          }
        }
        g.accentDots.add(Offset(if (mirror) leftX + dpToPx(12f) else rightX - dpToPx(12f), topY + dpToPx(12f)) to dpToPx(3f))
      }

      1 -> {
        // Editorial diagonal diamond trellis in lower band
        val stepX = (rightX - leftX) / 4f
        val stepY = (botY - topY) / 3f
        for (ci in 0..3) {
          val x0 = leftX + ci * stepX
          g.lines.add(Offset(x0, topY) to Offset(x0 + stepX, botY))
          g.lines.add(Offset(x0 + stepX, topY) to Offset(x0, botY))
        }
        g.lines.add(Offset(leftX, topY) to Offset(rightX, topY))
        g.lines.add(Offset(leftX, topY + stepY * 1.5f) to Offset(rightX, topY + stepY * 1.5f))
      }

      2 -> {
        // Repeating architectural mini-arches along lower band
        val cols = count.coerceIn(3, 4)
        val archW = (rightX - leftX - dpToPx(6f) * (cols - 1)) / cols
        val archH = (botY - topY) * 0.72f
        val yTop = botY - archH
        for (i in 0 until cols) {
          val ax = leftX + i * (archW + dpToPx(6f))
          g.primaryPath.addRoundRect(
            RoundRect(
              left = ax,
              top = yTop,
              right = ax + archW,
              bottom = botY,
              topLeftCornerRadius = CornerRadius(archW * 0.5f, archW * 0.5f),
              topRightCornerRadius = CornerRadius(archW * 0.5f, archW * 0.5f),
            )
          )
        }
      }

      else -> {
        // Rhythmic horizontal colophon tick matrix
        val rows = 5
        for (ri in 0 until rows) {
          val py = topY + dpToPx(6f) + ((botY - topY - dpToPx(12f)) * (ri / (rows - 1f)))
          val inset = if (ri % 2 == 0) 0f else dpToPx(10f)
          g.lines.add(Offset(leftX + inset, py) to Offset(rightX - inset, py))
        }
        g.accentDots.add(Offset(w * 0.5f, topY - dpToPx(8f)) to dpToPx(3.0f))
      }
    }
  }

  private fun prepareMonochromatic(
    w: Float,
    h: Float,
    dpToPx: (Float) -> Float,
    v: Int,
    scale: Float,
    mirror: Boolean,
    g: BackArtworkPreparedGeometry,
  ) {
    val pad = dpToPx(10f)
    when (v) {
      0 -> {
        // Stepped monochrome tonal panels
        g.primaryPath.addRoundRect(
          RoundRect(
            rect = Rect(pad, h * 0.46f, w - pad, h - pad),
            cornerRadius = CornerRadius(dpToPx(3f), dpToPx(3f)),
          )
        )
        val inset = dpToPx(7f)
        g.secondaryPath.addRoundRect(
          RoundRect(
            rect = Rect(pad + inset, h * 0.56f, w - pad - inset, h - pad - inset),
            cornerRadius = CornerRadius(dpToPx(2f), dpToPx(2f)),
          )
        )
        g.strokePath.addRoundRect(
          RoundRect(
            rect = Rect(pad, pad, w - pad, h - pad),
            cornerRadius = CornerRadius(dpToPx(3f), dpToPx(3f)),
          )
        )
      }

      1 -> {
        // Monochrome embossed concentric discs + vertical colophon axis
        val cx = w * 0.5f
        val cy = h * 0.68f
        val r = w * 0.24f * scale.coerceIn(0.85f, 1.12f)
        g.primaryCircles.add(Offset(cx, cy) to r)
        g.strokePath.addOval(Rect(cx - r * 1.25f, cy - r * 1.25f, cx + r * 1.25f, cy + r * 1.25f))
        g.lines.add(Offset(cx, h * 0.42f) to Offset(cx, h - pad))
      }

      2 -> {
        // Diagonal tonal fold across lower surface
        g.primaryPath.apply {
          moveTo(0f, if (mirror) h * 0.44f else h * 0.64f)
          lineTo(w, if (mirror) h * 0.64f else h * 0.44f)
          lineTo(w, h)
          lineTo(0f, h)
          close()
        }
        g.strokePath.addRoundRect(
          RoundRect(
            rect = Rect(pad, pad, w - pad, h - pad),
            cornerRadius = CornerRadius(dpToPx(2f), dpToPx(2f)),
          )
        )
      }

      else -> {
        // Minimal blind-debossed plinth block
        g.primaryPath.addRect(Rect(0f, h * 0.64f, w, h))
        g.lines.add(Offset(pad, h * 0.58f) to Offset(w - pad, h * 0.58f))
        g.accentDots.add(Offset(if (mirror) w - pad - dpToPx(4f) else pad + dpToPx(4f), h * 0.52f) to dpToPx(2.8f))
      }
    }
  }

  private fun prepareIllustrative(
    w: Float,
    h: Float,
    dpToPx: (Float) -> Float,
    v: Int,
    scale: Float,
    count: Int,
    mirror: Boolean,
    analysis: BookCoverVisualAnalysis,
    g: BackArtworkPreparedGeometry,
  ) {
    when (v) {
      0 -> {
        // Minimal layered mountain / dune ridge silhouettes + small sun/moon disc
        val peakX = if (mirror) w * 0.64f else w * 0.36f
        g.primaryPath.apply {
          moveTo(0f, h * 0.72f)
          lineTo(peakX, h * 0.48f)
          lineTo(w, h * 0.68f)
          lineTo(w, h)
          lineTo(0f, h)
          close()
        }
        val peak2X = if (mirror) w * 0.28f else w * 0.72f
        g.secondaryPath.apply {
          moveTo(0f, h * 0.80f)
          lineTo(peak2X, h * 0.62f)
          lineTo(w, h * 0.82f)
          lineTo(w, h)
          lineTo(0f, h)
          close()
        }
        val sunX = if (mirror) w * 0.28f else w * 0.72f
        g.accentDots.add(Offset(sunX, h * 0.45f) to w * 0.08f * scale.coerceIn(0.85f, 1.15f))
      }

      1 -> {
        // Stylized nested portal / open-book spine silhouettes in lower half
        val cx = w * 0.5f
        val archW = w * 0.62f
        val left = cx - archW * 0.5f
        g.primaryPath.addRoundRect(
          RoundRect(
            left = left,
            top = h * 0.46f,
            right = left + archW,
            bottom = h,
            topLeftCornerRadius = CornerRadius(archW * 0.46f, archW * 0.46f),
            topRightCornerRadius = CornerRadius(archW * 0.46f, archW * 0.46f),
          )
        )
        val innerW = archW * 0.62f
        g.secondaryPath.addRoundRect(
          RoundRect(
            left = cx - innerW * 0.5f,
            top = h * 0.57f,
            right = cx + innerW * 0.5f,
            bottom = h,
            topLeftCornerRadius = CornerRadius(innerW * 0.46f, innerW * 0.46f),
            topRightCornerRadius = CornerRadius(innerW * 0.46f, innerW * 0.46f),
          )
        )
        g.accentDots.add(Offset(cx, h * 0.51f) to dpToPx(2.8f))
      }

      2 -> {
        // Radiating horizon sunburst rays above clean plinth
        val origin = Offset(w * 0.5f, h * 0.76f)
        g.primaryPath.addRect(Rect(0f, origin.y, w, h))
        val rayCount = (count + 2).coerceIn(4, 6)
        val rayLen = min(w, h) * 0.34f
        for (i in 0 until rayCount) {
          val deg = -155.0 + (130.0 * (i / (rayCount - 1f)))
          val rad = deg * PI / 180.0
          val end = Offset(origin.x + (cos(rad) * rayLen).toFloat(), origin.y + (sin(rad) * rayLen).toFloat())
          g.lines.add(origin to end)
        }
        g.accentDots.add(origin to w * 0.085f)
      }

      else -> {
        // Rhythmic sea wave crests & horizon rule guided by spatial balance
        val y1 = if (analysis.spatial.topVsBottomContrast > 0.20f) h * 0.52f else h * 0.56f
        val y2 = y1 + h * 0.16f
        g.primaryPath.apply {
          moveTo(0f, y1)
          cubicTo(w * 0.25f, y1 - h * 0.05f, w * 0.50f, y1 + h * 0.05f, w, y1 - h * 0.02f)
          lineTo(w, h)
          lineTo(0f, h)
          close()
        }
        g.secondaryPath.apply {
          moveTo(0f, y2)
          cubicTo(w * 0.32f, y2 + h * 0.05f, w * 0.68f, y2 - h * 0.05f, w, y2 + h * 0.02f)
          lineTo(w, h)
          lineTo(0f, h)
          close()
        }
        g.lines.add(Offset(dpToPx(12f), y1 - h * 0.08f) to Offset(w - dpToPx(12f), y1 - h * 0.08f))
      }
    }
  }

  private fun prepareBorderTreatment(
    w: Float,
    h: Float,
    dpToPx: (Float) -> Float,
    treatment: BackBorderTreatment,
    g: BackArtworkPreparedGeometry,
  ) {
    when (treatment) {
      BackBorderTreatment.NONE -> Unit
      BackBorderTreatment.INNER_HAIRLINE -> {
        val pad = dpToPx(7.5f)
        g.strokePath.addRoundRect(
          RoundRect(
            rect = Rect(pad, pad, w - pad - dpToPx(2f), h - pad),
            cornerRadius = CornerRadius(dpToPx(3f), dpToPx(3f)),
          )
        )
      }
      BackBorderTreatment.DOUBLE_RULE -> {
        val pad = dpToPx(6f)
        val pad2 = dpToPx(9f)
        g.strokePath.addRoundRect(
          RoundRect(
            rect = Rect(pad, pad, w - pad - dpToPx(2f), h - pad),
            cornerRadius = CornerRadius(dpToPx(2.5f), dpToPx(2.5f)),
          )
        )
        g.strokePath.addRoundRect(
          RoundRect(
            rect = Rect(pad2, pad2, w - pad2 - dpToPx(2f), h - pad2),
            cornerRadius = CornerRadius(dpToPx(2f), dpToPx(2f)),
          )
        )
      }
      BackBorderTreatment.CORNER_BRACKETS -> {
        val pad = dpToPx(8f)
        val arm = dpToPx(9f)
        val rEdge = w - pad - dpToPx(2f)
        val bEdge = h - pad
        // Bottom corners + top-right corner (avoid colliding with top-left colophon text)
        g.lines.add(Offset(rEdge - arm, pad) to Offset(rEdge, pad))
        g.lines.add(Offset(rEdge, pad) to Offset(rEdge, pad + arm))
        g.lines.add(Offset(pad, bEdge - arm) to Offset(pad, bEdge))
        g.lines.add(Offset(pad, bEdge) to Offset(pad + arm, bEdge))
        g.lines.add(Offset(rEdge - arm, bEdge) to Offset(rEdge, bEdge))
        g.lines.add(Offset(rEdge, bEdge - arm) to Offset(rEdge, bEdge))
      }
    }
  }

  fun DrawScope.renderPreparedGeometry(
    g: BackArtworkPreparedGeometry,
    spec: BackArtworkCompositionSpec,
    palette: BackArtworkRenderPalette,
    strokeStyle: Stroke,
    thinStrokeStyle: Stroke,
  ) {
    val isMono = spec.family == BackArtworkDesignFamily.MONOCHROMATIC
    val primaryFill = if (isMono) g.monoShadeColor else palette.primaryShapeColor
    val secondaryFill = if (isMono) g.monoTintColor else palette.secondaryShapeColor
    val accentFill = if (isMono) palette.hairlineColor.copy(alpha = 0.72f) else palette.accentColor

    g.atmosphericBrush?.let { brush ->
      drawRect(brush = brush)
    }

    if (!g.primaryPath.isEmpty) {
      drawPath(path = g.primaryPath, color = primaryFill)
    }
    if (!g.secondaryPath.isEmpty) {
      val secondaryAlpha =
        if (spec.family == BackArtworkDesignFamily.ATMOSPHERIC) 0.55f
        else if (spec.family == BackArtworkDesignFamily.ILLUSTRATIVE || spec.family == BackArtworkDesignFamily.ORGANIC) 0.96f
        else 0.94f
      drawPath(path = g.secondaryPath, color = secondaryFill.copy(alpha = secondaryAlpha))
    }

    for ((center, radius) in g.primaryCircles) {
      drawCircle(color = primaryFill, radius = radius, center = center)
    }

    if (!g.tertiaryPath.isEmpty) {
      drawPath(path = g.tertiaryPath, color = palette.lightSupportColor.copy(alpha = 0.85f))
    }

    if (!g.strokePath.isEmpty) {
      drawPath(path = g.strokePath, color = palette.hairlineColor, style = strokeStyle)
    }

    for ((start, end) in g.lines) {
      drawLine(color = palette.hairlineColor, start = start, end = end, strokeWidth = thinStrokeStyle.width)
    }

    for ((center, radius) in g.patternDots) {
      drawCircle(color = secondaryFill.copy(alpha = 0.78f), radius = radius, center = center)
    }

    if (!g.accentPath.isEmpty) {
      drawPath(path = g.accentPath, color = accentFill)
    }

    for ((center, radius) in g.accentDots) {
      drawCircle(color = accentFill, radius = radius, center = center)
    }
  }
}
