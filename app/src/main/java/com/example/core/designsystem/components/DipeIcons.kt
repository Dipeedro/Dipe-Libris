package com.example.core.designsystem.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom editorial vector icons built on the exact 24dp Material icon grid and
 * 1.9dp stroke geometry so all navigation tabs share one consistent visual weight.
 */
object DipeIcons {

  val BookshelfOutlined: ImageVector by lazy {
    ImageVector.Builder(
        name = "BookshelfOutlined",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
      )
      .apply {
        // Left tall upright book spine
        path(
          stroke = SolidColor(Color.Black),
          strokeLineWidth = 1.9f,
          strokeLineCap = StrokeCap.Round,
          strokeLineJoin = StrokeJoin.Round,
        ) {
          moveTo(3.5f, 4.5f)
          lineTo(7.5f, 4.5f)
          lineTo(7.5f, 19.5f)
          lineTo(3.5f, 19.5f)
          close()
          moveTo(3.5f, 8.2f)
          lineTo(7.5f, 8.2f)
        }
        // Center medium upright book spine
        path(
          stroke = SolidColor(Color.Black),
          strokeLineWidth = 1.9f,
          strokeLineCap = StrokeCap.Round,
          strokeLineJoin = StrokeJoin.Round,
        ) {
          moveTo(9.5f, 6.5f)
          lineTo(13.5f, 6.5f)
          lineTo(13.5f, 19.5f)
          lineTo(9.5f, 19.5f)
          close()
          moveTo(9.5f, 16.0f)
          lineTo(13.5f, 16.0f)
        }
        // Right leaning book resting on the shelf
        path(
          stroke = SolidColor(Color.Black),
          strokeLineWidth = 1.9f,
          strokeLineCap = StrokeCap.Round,
          strokeLineJoin = StrokeJoin.Round,
        ) {
          moveTo(15.2f, 5.6f)
          lineTo(19.0f, 4.8f)
          lineTo(21.4f, 18.8f)
          lineTo(17.6f, 19.6f)
          close()
        }
      }
      .build()
  }

  val BookshelfFilled: ImageVector by lazy {
    ImageVector.Builder(
        name = "BookshelfFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
      )
      .apply {
        // Left spine (solid with crisp negative-space top band)
        path(
          fill = SolidColor(Color.Black),
          pathFillType = PathFillType.EvenOdd,
        ) {
          moveTo(3.2f, 4.0f)
          lineTo(7.8f, 4.0f)
          lineTo(7.8f, 20.0f)
          lineTo(3.2f, 20.0f)
          close()
          moveTo(4.2f, 7.2f)
          lineTo(6.8f, 7.2f)
          lineTo(6.8f, 8.8f)
          lineTo(4.2f, 8.8f)
          close()
        }
        // Center spine (solid with crisp negative-space lower band)
        path(
          fill = SolidColor(Color.Black),
          pathFillType = PathFillType.EvenOdd,
        ) {
          moveTo(9.2f, 6.0f)
          lineTo(13.8f, 6.0f)
          lineTo(13.8f, 20.0f)
          lineTo(9.2f, 20.0f)
          close()
          moveTo(10.2f, 15.6f)
          lineTo(12.8f, 15.6f)
          lineTo(12.8f, 17.2f)
          lineTo(10.2f, 17.2f)
          close()
        }
        // Right leaning spine
        path(
          fill = SolidColor(Color.Black),
        ) {
          moveTo(14.9f, 5.4f)
          lineTo(19.3f, 4.4f)
          lineTo(21.8f, 19.0f)
          lineTo(17.4f, 20.0f)
          close()
        }
      }
      .build()
  }
}
