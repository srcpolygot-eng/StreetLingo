package com.example.ui.street

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.data.model.CityDistrict
import com.example.data.model.PropKind
import com.example.data.model.StreetLocal
import com.example.data.model.StreetProp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun StreetCanvas2D(
  city: CityDistrict,
  playerX: Float,
  playerZ: Float,
  playerYawRad: Float,
  completedNpcIds: Set<String>,
  nearbyLocalId: String?,
  onWalkToWorldPos: (Float, Float) -> Unit,
  onSelectLocal: (StreetLocal) -> Unit,
  onSelectProp: (StreetProp) -> Unit,
  modifier: Modifier = Modifier,
) {
  val textMeasurer = rememberTextMeasurer()
  val transition = rememberInfiniteTransition(label = "2dStreetAnim")
  val bobOffset by transition.animateFloat(
    initialValue = -3f,
    targetValue = 3f,
    animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
    label = "bobOffset",
  )
  val beaconPulse by transition.animateFloat(
    initialValue = 0.7f,
    targetValue = 1.25f,
    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    label = "beaconPulse",
  )

  val nameStyle = remember {
    TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1F2A))
  }
  val signStyle = remember {
    TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
  }

  Canvas(
    modifier = modifier
      .fillMaxSize()
      .pointerInput(city.id, playerX, playerZ) {
        detectTapGestures { tap ->
          val w = size.width.toFloat()
          val h = size.height.toFloat()
          val pixelsPerMeter = (w / 17f).coerceIn(28f, 64f)
          val centerX = w * 0.5f
          val centerY = h * 0.52f

          val tapWorldX = playerX + (tap.x - centerX) / pixelsPerMeter
          val tapWorldZ = playerZ - (tap.y - centerY) / pixelsPerMeter

          val hitLocal = city.locals.firstOrNull { local ->
            hypot(local.worldX - tapWorldX, local.worldZ - tapWorldZ) < 1.9f
          }
          if (hitLocal != null) {
            onSelectLocal(hitLocal)
            return@detectTapGestures
          }

          val hitProp = city.props.firstOrNull { prop ->
            hypot(prop.worldX - tapWorldX, prop.worldZ - tapWorldZ) < 1.5f
          }
          if (hitProp != null) {
            onSelectProp(hitProp)
            return@detectTapGestures
          }

          onWalkToWorldPos(tapWorldX, tapWorldZ)
        }
      },
  ) {
    val ppm = (size.width / 17f).coerceIn(28f, 64f)
    val camScreenX = size.width * 0.5f
    val camScreenY = size.height * 0.52f

    fun toScreen(wx: Float, wz: Float): Offset = Offset(
      x = camScreenX + (wx - playerX) * ppm,
      y = camScreenY - (wz - playerZ) * ppm,
    )

    // 1. Background ground & sidewalk
    drawRect(
      brush = Brush.verticalGradient(
        colors = listOf(city.skyBottomColor.copy(alpha = 0.25f), city.sidewalkColor),
      ),
      size = size,
    )

    // 2. Cobblestone boulevard road
    val roadLeft = toScreen(-2.6f, playerZ).x
    val roadRight = toScreen(2.6f, playerZ).x
    drawRect(
      color = city.roadColor,
      topLeft = Offset(roadLeft, 0f),
      size = Size(roadRight - roadLeft, size.height),
    )

    // Sidewalk curbs
    drawLine(
      color = Color.White.copy(alpha = 0.5f),
      start = Offset(roadLeft, 0f),
      end = Offset(roadLeft, size.height),
      strokeWidth = 3f,
    )
    drawLine(
      color = Color.White.copy(alpha = 0.5f),
      start = Offset(roadRight, 0f),
      end = Offset(roadRight, size.height),
      strokeWidth = 3f,
    )

    // Boulevard lane dashes & crosswalks
    val centerRoadX = toScreen(0f, playerZ).x
    for (zStep in 0..50 step 3) {
      val p1 = toScreen(0f, zStep.toFloat())
      val p2 = toScreen(0f, zStep + 1.4f)
      drawLine(
        color = Color(0xFFFFF3D6).copy(alpha = 0.55f),
        start = Offset(centerRoadX, p1.y),
        end = Offset(centerRoadX, p2.y),
        strokeWidth = 3f,
      )
    }

    // 3. Top-down 2.5D extruded buildings
    for (b in city.buildings) {
      val topLeft = toScreen(b.worldX - b.width / 2f, b.worldZ + b.depth / 2f)
      val bw = b.width * ppm
      val bh = b.depth * ppm

      // Drop shadow
      drawRoundRect(
        color = Color.Black.copy(alpha = 0.16f),
        topLeft = Offset(topLeft.x + 8f, topLeft.y + 10f),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(12f, 12f),
      )

      // Roof & building footprint
      drawRoundRect(
        color = b.roofColor,
        topLeft = topLeft,
        size = Size(bw, bh),
        cornerRadius = CornerRadius(12f, 12f),
      )

      // Facade ledge facing street
      val streetEdgeX = if (b.worldX < 0f) topLeft.x + bw - 14f else topLeft.x
      drawRect(
        color = b.wallColor,
        topLeft = Offset(streetEdgeX, topLeft.y + 6f),
        size = Size(14f, bh - 12f),
      )

      // Striped shop awning protruding onto sidewalk
      val awningX = if (b.worldX < 0f) topLeft.x + bw else topLeft.x - 16f
      drawRoundRect(
        color = b.awningColor,
        topLeft = Offset(awningX, topLeft.y + bh * 0.18f),
        size = Size(16f, bh * 0.64f),
        cornerRadius = CornerRadius(4f, 4f),
      )

      // Shop title on roof
      val measured = textMeasurer.measure(b.title, style = signStyle)
      drawText(
        textLayoutResult = measured,
        topLeft = Offset(
          topLeft.x + (bw - measured.size.width) * 0.5f,
          topLeft.y + (bh - measured.size.height) * 0.5f,
        ),
      )
    }

    // 4. Interactive Street Props
    for (prop in city.props) {
      val pos = toScreen(prop.worldX, prop.worldZ)
      when (prop.kind) {
        PropKind.TREE -> {
          drawCircle(Color.Black.copy(alpha = 0.15f), radius = 19f, center = Offset(pos.x + 4f, pos.y + 4f))
          drawCircle(Color(0xFF287D5E), radius = 19f, center = pos)
          drawCircle(Color(0xFF47A77E), radius = 12f, center = Offset(pos.x - 3f, pos.y - 3f))
        }
        PropKind.LAMP -> {
          drawCircle(Color(0xFFFFD875).copy(alpha = 0.25f), radius = 26f, center = pos)
          drawCircle(Color(0xFF2D3142), radius = 7f, center = pos)
          drawCircle(Color(0xFFFFE599), radius = 4f, center = pos)
        }
        PropKind.FOUNTAIN -> {
          drawCircle(Color(0xFFD8C8B4), radius = 28f, center = pos)
          drawCircle(Color(0xFF4E9CE0), radius = 21f, center = pos)
          drawCircle(Color(0xFFA7DCFF).copy(alpha = 0.7f), radius = 10f * beaconPulse, center = pos)
        }
        PropKind.CAFE_TABLE -> {
          drawCircle(Color.Black.copy(alpha = 0.12f), radius = 14f, center = Offset(pos.x + 2f, pos.y + 3f))
          drawCircle(Color(0xFFFFF8EE), radius = 13f, center = pos)
          drawCircle(city.accentColor, radius = 13f, center = pos, style = Stroke(width = 2.5f))
        }
        PropKind.SIGNPOST -> {
          drawRoundRect(
            color = Color(0xFF1D5B9E),
            topLeft = Offset(pos.x - 14f, pos.y - 9f),
            size = Size(28f, 18f),
            cornerRadius = CornerRadius(5f, 5f),
          )
        }
      }
    }

    // 5. Interactive Local NPCs
    for (local in city.locals) {
      val pos = toScreen(local.worldX, local.worldZ)
      val isNearby = local.id == nearbyLocalId
      val isDone = completedNpcIds.contains(local.id)

      if (isNearby) {
        drawCircle(
          color = city.accentColor.copy(alpha = 0.24f),
          radius = 30f * beaconPulse,
          center = pos,
        )
      }

      // Shadow
      drawCircle(Color.Black.copy(alpha = 0.2f), radius = 16f, center = Offset(pos.x + 2f, pos.y + 5f))
      // Shoulders / outfit
      drawCircle(local.outfitColor, radius = 15f, center = pos)
      // Head
      drawCircle(local.skinTone, radius = 10f, center = Offset(pos.x, pos.y - 3f + bobOffset * 0.3f))
      // Hair cap
      drawArc(
        color = local.hairColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(pos.x - 10f, pos.y - 13f + bobOffset * 0.3f),
        size = Size(20f, 14f),
      )

      // Name pill above local
      val labelText = if (isDone) "✓ ${local.name}" else local.name
      val measured = textMeasurer.measure(labelText, style = nameStyle)
      val pillW = measured.size.width + 16f
      val pillH = measured.size.height + 8f
      val pillX = pos.x - pillW * 0.5f
      val pillY = pos.y - 36f + bobOffset * 0.5f
      drawRoundRect(
        color = if (isNearby) Color(0xFFFFF1D6) else Color.White.copy(alpha = 0.94f),
        topLeft = Offset(pillX, pillY),
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(12f, 12f),
      )
      drawRoundRect(
        color = if (isDone) Color(0xFF287D5E) else city.accentColor,
        topLeft = Offset(pillX, pillY),
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(12f, 12f),
        style = Stroke(width = 2f),
      )
      drawText(measured, topLeft = Offset(pillX + 8f, pillY + 4f))
    }

    // 6. Player Traveler Avatar with directional cone
    val pPos = Offset(camScreenX, camScreenY)
    val coneAngle = Math.toDegrees(playerYawRad.toDouble()).toFloat() - 90f
    drawArc(
      color = Color(0xFF1D5B9E).copy(alpha = 0.18f),
      startAngle = coneAngle - 28f,
      sweepAngle = 56f,
      useCenter = true,
      topLeft = Offset(pPos.x - 56f, pPos.y - 56f),
      size = Size(112f, 112f),
    )
    drawCircle(Color.White, radius = 16f, center = pPos)
    drawCircle(Color(0xFF1D5B9E), radius = 12f, center = pPos)
    val dirTip = Offset(
      pPos.x + sin(playerYawRad) * 18f,
      pPos.y - cos(playerYawRad) * 18f,
    )
    drawCircle(Color(0xFFE0961E), radius = 5f, center = dirTip)
  }
}
