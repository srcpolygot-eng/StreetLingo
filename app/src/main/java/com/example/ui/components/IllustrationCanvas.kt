package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.IllustrationPrimitive
import com.example.data.model.ParsedIllustration

fun parseHexColorSafe(hex: String, fallback: Color = Color(0xFF1D5B9E)): Color {
  val cleaned = hex.trim().removePrefix("#")
  return try {
    when (cleaned.length) {
      6 -> Color(0xFF000000L or cleaned.toLong(16))
      8 -> Color(cleaned.toLong(16))
      else -> fallback
    }
  } catch (_: Exception) {
    fallback
  }
}

@Composable
fun InstantIllustrationCanvas(
  parsed: ParsedIllustration,
  isStreaming: Boolean,
  modelName: String,
  modifier: Modifier = Modifier,
) {
  val textMeasurer = rememberTextMeasurer()
  val infiniteTransition = rememberInfiniteTransition(label = "streamPulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.45f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(650),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "pulseAlpha",
  )

  val defaultBgTop = MaterialTheme.colorScheme.primaryContainer
  val defaultBgBottom = MaterialTheme.colorScheme.tertiaryContainer
  val labelTextStyle = remember {
    TextStyle(
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      color = Color(0xFF1B1F2A),
    )
  }

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(20.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val bgPrimitive = parsed.primitives.filterIsInstance<IllustrationPrimitive.Background>().lastOrNull()
      val topColor = bgPrimitive?.let { parseHexColorSafe(it.topHex, defaultBgTop) } ?: defaultBgTop
      val bottomColor = bgPrimitive?.let { parseHexColorSafe(it.bottomHex, defaultBgBottom) } ?: defaultBgBottom

      drawRect(
        brush = Brush.verticalGradient(colors = listOf(topColor, bottomColor)),
        size = size,
      )

      for (primitive in parsed.primitives) {
        when (primitive) {
          is IllustrationPrimitive.Background -> Unit
          is IllustrationPrimitive.Circle -> {
            val c = parseHexColorSafe(primitive.colorHex)
            val radius = primitive.r * size.minDimension
            val center = Offset(primitive.cx * size.width, primitive.cy * size.height)
            if (primitive.filled) {
              drawCircle(color = c, radius = radius, center = center, style = Fill)
            } else {
              drawCircle(color = c, radius = radius, center = center, style = Stroke(width = 4.dp.toPx()))
            }
          }
          is IllustrationPrimitive.Rect -> {
            val c = parseHexColorSafe(primitive.colorHex)
            val topLeft = Offset(primitive.x * size.width, primitive.y * size.height)
            val rectSize = Size(primitive.w * size.width, primitive.h * size.height)
            val corner = primitive.rx * size.minDimension
            if (primitive.filled) {
              drawRoundRect(
                color = c,
                topLeft = topLeft,
                size = rectSize,
                cornerRadius = CornerRadius(corner, corner),
                style = Fill,
              )
            } else {
              drawRoundRect(
                color = c,
                topLeft = topLeft,
                size = rectSize,
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = 3.5.dp.toPx()),
              )
            }
          }
          is IllustrationPrimitive.Oval -> {
            val c = parseHexColorSafe(primitive.colorHex)
            val topLeft = Offset(primitive.x * size.width, primitive.y * size.height)
            val ovalSize = Size(primitive.w * size.width, primitive.h * size.height)
            if (primitive.filled) {
              drawOval(color = c, topLeft = topLeft, size = ovalSize, style = Fill)
            } else {
              drawOval(color = c, topLeft = topLeft, size = ovalSize, style = Stroke(width = 3.5.dp.toPx()))
            }
          }
          is IllustrationPrimitive.Line -> {
            val c = parseHexColorSafe(primitive.colorHex)
            drawLine(
              color = c,
              start = Offset(primitive.x1 * size.width, primitive.y1 * size.height),
              end = Offset(primitive.x2 * size.width, primitive.y2 * size.height),
              strokeWidth = primitive.stroke.dp.toPx().coerceAtMost(18f),
              cap = StrokeCap.Round,
            )
          }
          is IllustrationPrimitive.Arc -> {
            val c = parseHexColorSafe(primitive.colorHex)
            drawArc(
              color = c,
              startAngle = primitive.startDeg,
              sweepAngle = primitive.sweepDeg,
              useCenter = false,
              topLeft = Offset(primitive.x * size.width, primitive.y * size.height),
              size = Size(primitive.w * size.width, primitive.h * size.height),
              style = Stroke(
                width = primitive.stroke.dp.toPx().coerceAtMost(16f),
                cap = StrokeCap.Round,
              ),
            )
          }
          is IllustrationPrimitive.Polygon -> {
            val c = parseHexColorSafe(primitive.colorHex)
            val path = Path().apply {
              val first = primitive.points.first()
              moveTo(first.first * size.width, first.second * size.height)
              for (pt in primitive.points.drop(1)) {
                lineTo(pt.first * size.width, pt.second * size.height)
              }
              close()
            }
            if (primitive.filled) {
              drawPath(path = path, color = c, style = Fill)
            } else {
              drawPath(
                path = path,
                color = c,
                style = Stroke(width = 3.5.dp.toPx(), join = StrokeJoin.Round),
              )
            }
          }
          is IllustrationPrimitive.Label -> {
            val badgeColor = parseHexColorSafe(primitive.colorHex, Color(0xFF1B1F2A))
            val measured = textMeasurer.measure(primitive.text, style = labelTextStyle)
            val padH = 8.dp.toPx()
            val padV = 4.dp.toPx()
            val boxW = measured.size.width + padH * 2f
            val boxH = measured.size.height + padV * 2f
            val left = (primitive.x * size.width - boxW / 2f).coerceIn(6f, (size.width - boxW - 6f).coerceAtLeast(6f))
            val top = (primitive.y * size.height - boxH / 2f).coerceIn(6f, (size.height - boxH - 6f).coerceAtLeast(6f))
            drawRoundRect(
              color = Color.White.copy(alpha = 0.92f),
              topLeft = Offset(left, top),
              size = Size(boxW, boxH),
              cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            )
            drawRoundRect(
              color = badgeColor,
              topLeft = Offset(left, top),
              size = Size(boxW, boxH),
              cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
              style = Stroke(width = 1.5.dp.toPx()),
            )
            drawText(
              textLayoutResult = measured,
              topLeft = Offset(left + padH, top + padV),
            )
          }
        }
      }
    }

    if (parsed.primitives.isEmpty() && isStreaming) {
      Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Icon(
          imageVector = Icons.Rounded.Brush,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
          modifier = Modifier.size(34.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Streaming live brush strokes...",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Surface(
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(10.dp),
      shape = RoundedCornerShape(999.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
      tonalElevation = 2.dp,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (isStreaming) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha)),
          )
        } else {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
          )
        }
        Text(
          text = if (isStreaming) "Streaming ($modelName)" else "$modelName · ${parsed.primitives.size} layers",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}
