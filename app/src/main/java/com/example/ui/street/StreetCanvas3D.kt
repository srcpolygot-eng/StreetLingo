package com.example.ui.street

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.data.model.CityDistrict
import com.example.data.model.PropKind
import com.example.data.model.StreetBuilding
import com.example.data.model.StreetLocal
import com.example.data.model.StreetProp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private data class ProjectedHitTarget(
  val x: Float,
  val y: Float,
  val radius: Float,
  val local: StreetLocal? = null,
  val prop: StreetProp? = null,
)

private data class Vec3(val x: Float, val y: Float, val z: Float)

private sealed interface Drawable3D {
  val sortZ: Float
  data class BuildingItem(val b: StreetBuilding, override val sortZ: Float) : Drawable3D
  data class LocalItem(val l: StreetLocal, override val sortZ: Float) : Drawable3D
  data class PropItem(val p: StreetProp, override val sortZ: Float) : Drawable3D
}

@Composable
fun StreetCanvas3D(
  city: CityDistrict,
  playerX: Float,
  playerZ: Float,
  playerYawRad: Float,
  playerPitchRad: Float,
  completedNpcIds: Set<String>,
  nearbyLocalId: String?,
  onRotateCamera: (Float, Float) -> Unit,
  onSelectLocal: (StreetLocal) -> Unit,
  onSelectProp: (StreetProp) -> Unit,
  modifier: Modifier = Modifier,
) {
  val textMeasurer = rememberTextMeasurer()
  val hitTargets = remember { mutableStateListOf<ProjectedHitTarget>() }

  val transition = rememberInfiniteTransition(label = "3dStreetAnim")
  val bob by transition.animateFloat(
    initialValue = -0.05f,
    targetValue = 0.05f,
    animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
    label = "3dBob",
  )
  val glowPulse by transition.animateFloat(
    initialValue = 0.85f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse),
    label = "3dGlow",
  )

  val labelStyle = remember {
    TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1F2A))
  }
  val subLabelStyle = remember {
    TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4A5060))
  }

  Canvas(
    modifier = modifier
      .fillMaxSize()
      .pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
          change.consume()
          onRotateCamera(-dragAmount.x * 0.0045f, -dragAmount.y * 0.0022f)
        }
      }
      .pointerInput(city.id) {
        detectTapGestures { tap ->
          val matched = hitTargets.lastOrNull { target ->
            hypot(tap.x - target.x, tap.y - target.y) <= target.radius
          }
          when {
            matched?.local != null -> onSelectLocal(matched.local)
            matched?.prop != null -> onSelectProp(matched.prop)
          }
        }
      },
  ) {
    hitTargets.clear()

    val w = size.width
    val h = size.height
    val focal = w * 0.82f
    val camEyeY = 1.75f
    val horizonY = h * (0.48f + playerPitchRad * 0.7f)
    val sinYaw = sin(playerYawRad)
    val cosYaw = cos(playerYawRad)

    fun toCamera(wx: Float, wy: Float, wz: Float): Vec3 {
      val dx = wx - playerX
      val dy = wy - camEyeY
      val dz = wz - playerZ
      val camX = dx * cosYaw - dz * sinYaw
      val camZ = dx * sinYaw + dz * cosYaw
      return Vec3(camX, dy, camZ)
    }

    fun projectCam(cam: Vec3): Offset? {
      if (cam.z <= 0.35f) return null
      val sx = w * 0.5f + (cam.x / cam.z) * focal
      val sy = horizonY - (cam.y / cam.z) * focal
      return Offset(sx, sy)
    }

    fun projectWorld(wx: Float, wy: Float, wz: Float): Offset? = projectCam(toCamera(wx, wy, wz))

    // 1. Sky & Sun/Moon backdrop
    drawRect(
      brush = Brush.verticalGradient(
        colors = listOf(city.skyTopColor, city.skyBottomColor),
        startY = 0f,
        endY = horizonY.coerceAtLeast(10f),
      ),
      size = Size(w, horizonY.coerceAtLeast(10f)),
    )

    // Sun or twilight moon orb reacting to yaw
    val sunScreenX = w * 0.5f - playerYawRad * w * 0.35f
    drawCircle(
      color = Color(0xFFFFF4C7).copy(alpha = 0.88f),
      radius = 36f * glowPulse,
      center = Offset(sunScreenX, (horizonY * 0.36f).coerceAtLeast(40f)),
    )

    // 2. Ground plane (Sidewalk base + Cobblestone Road in perspective)
    drawRect(
      brush = Brush.verticalGradient(
        colors = listOf(city.sidewalkColor.copy(alpha = 0.85f), city.sidewalkColor),
        startY = horizonY,
        endY = h,
      ),
      topLeft = Offset(0f, horizonY),
      size = Size(w, (h - horizonY).coerceAtLeast(0f)),
    )

    // Perspective cobblestone road strips
    for (zStart in 1..48 step 2) {
      val z0 = zStart.toFloat()
      val z1 = (zStart + 2).toFloat()
      val pL0 = projectWorld(-2.6f, 0f, z0)
      val pR0 = projectWorld(2.6f, 0f, z0)
      val pL1 = projectWorld(-2.6f, 0f, z1)
      val pR1 = projectWorld(2.6f, 0f, z1)
      if (pL0 != null && pR0 != null && pL1 != null && pR1 != null) {
        val roadPath = Path().apply {
          moveTo(pL0.x, pL0.y)
          lineTo(pR0.x, pR0.y)
          lineTo(pR1.x, pR1.y)
          lineTo(pL1.x, pL1.y)
          close()
        }
        val shade = if (zStart % 4 == 1) city.roadColor else city.roadColor.copy(alpha = 0.92f)
        drawPath(roadPath, color = shade)

        // Center dashed street line
        val c0 = projectWorld(0f, 0.02f, z0 + 0.3f)
        val c1 = projectWorld(0f, 0.02f, z1 - 0.3f)
        if (c0 != null && c1 != null) {
          drawLine(
            color = Color(0xFFFFF1CE).copy(alpha = 0.55f),
            start = c0,
            end = c1,
            strokeWidth = (18f / (z0 - playerZ).coerceAtLeast(1f)).coerceIn(1.5f, 5f),
          )
        }
      }
    }

    // 3. Collect all 3D drawable items and painter-sort back-to-front by camera Z!
    val drawList = mutableListOf<Drawable3D>()
    for (b in city.buildings) {
      val cz = toCamera(b.worldX, 0f, b.worldZ).z
      if (cz > -3f && cz < 55f) drawList += Drawable3D.BuildingItem(b, cz)
    }
    for (p in city.props) {
      val cz = toCamera(p.worldX, 0f, p.worldZ).z
      if (cz > 0.4f && cz < 52f) drawList += Drawable3D.PropItem(p, cz)
    }
    for (l in city.locals) {
      val cz = toCamera(l.worldX, 0f, l.worldZ).z
      if (cz > 0.6f && cz < 52f) drawList += Drawable3D.LocalItem(l, cz)
    }
    drawList.sortByDescending { it.sortZ }

    for (item in drawList) {
      when (item) {
        is Drawable3D.BuildingItem -> drawBuilding3D(item.b, ::toCamera, ::projectCam, textMeasurer, labelStyle)
        is Drawable3D.PropItem -> {
          val target = drawProp3D(item.p, ::toCamera, ::projectCam, glowPulse)
          if (target != null) hitTargets += target
        }
        is Drawable3D.LocalItem -> {
          val target = drawLocal3D(
            local = item.l,
            isNearby = item.l.id == nearbyLocalId,
            isDone = completedNpcIds.contains(item.l.id),
            bob = bob,
            glowPulse = glowPulse,
            accentColor = city.accentColor,
            toCamera = ::toCamera,
            projectCam = ::projectCam,
            textMeasurer = textMeasurer,
            labelStyle = labelStyle,
            subLabelStyle = subLabelStyle,
          )
          if (target != null) hitTargets += target
        }
      }
    }
  }
}

private fun DrawScope.drawQuad(p1: Offset?, p2: Offset?, p3: Offset?, p4: Offset?, color: Color, outline: Color? = null) {
  if (p1 == null || p2 == null || p3 == null || p4 == null) return
  val path = Path().apply {
    moveTo(p1.x, p1.y)
    lineTo(p2.x, p2.y)
    lineTo(p3.x, p3.y)
    lineTo(p4.x, p4.y)
    close()
  }
  drawPath(path, color = color)
  if (outline != null) {
    drawPath(path, color = outline, style = Stroke(width = 1.5f))
  }
}

private fun DrawScope.drawBuilding3D(
  b: StreetBuilding,
  toCamera: (Float, Float, Float) -> Vec3,
  projectCam: (Vec3) -> Offset?,
  textMeasurer: TextMeasurer,
  labelStyle: TextStyle,
) {
  val halfW = b.width * 0.5f
  val halfD = b.depth * 0.5f
  val innerX = if (b.worldX < 0f) b.worldX + halfW else b.worldX - halfW
  val outerX = if (b.worldX < 0f) b.worldX - halfW else b.worldX + halfW
  val zNear = b.worldZ - halfD
  val zFar = b.worldZ + halfD

  // Street-facing facade (at innerX, from zNear to zFar)
  val fBL = projectCam(toCamera(innerX, 0f, zNear))
  val fBR = projectCam(toCamera(innerX, 0f, zFar))
  val fTR = projectCam(toCamera(innerX, b.height, zFar))
  val fTL = projectCam(toCamera(innerX, b.height, zNear))

  // Front-end wall facing player (at zNear, from outerX to innerX)
  val eBL = projectCam(toCamera(outerX, 0f, zNear))
  val eTL = projectCam(toCamera(outerX, b.height, zNear))

  drawQuad(eBL, fBL, fTL, eTL, color = b.wallColor.copy(alpha = 0.88f), outline = Color.Black.copy(alpha = 0.12f))
  drawQuad(fBL, fBR, fTR, fTL, color = b.wallColor, outline = Color.Black.copy(alpha = 0.14f))

  // Warm windows on street-facing facade
  for (floor in 0..1) {
    val y0 = 3.2f + floor * 2.3f
    val y1 = y0 + 1.25f
    for (wIdx in 0..1) {
      val wz0 = zNear + 0.9f + wIdx * 2.4f
      val wz1 = wz0 + 1.3f
      drawQuad(
        projectCam(toCamera(innerX, y0, wz0)),
        projectCam(toCamera(innerX, y0, wz1)),
        projectCam(toCamera(innerX, y1, wz1)),
        projectCam(toCamera(innerX, y1, wz0)),
        color = b.windowGlow,
        outline = Color(0xFF423A32),
      )
    }
  }

  // Shop awning jutting onto sidewalk
  val awningEdgeX = if (b.worldX < 0f) innerX + 1.2f else innerX - 1.2f
  drawQuad(
    projectCam(toCamera(innerX, 2.55f, zNear + 0.4f)),
    projectCam(toCamera(innerX, 2.55f, zFar - 0.4f)),
    projectCam(toCamera(awningEdgeX, 2.1f, zFar - 0.4f)),
    projectCam(toCamera(awningEdgeX, 2.1f, zNear + 0.4f)),
    color = b.awningColor,
    outline = Color.White.copy(alpha = 0.4f),
  )

  // Roof cornice trim
  drawQuad(
    fTL,
    fTR,
    projectCam(toCamera(innerX, b.height + 0.45f, zFar)),
    projectCam(toCamera(innerX, b.height + 0.45f, zNear)),
    color = b.roofColor,
  )

  // Building storefront sign
  val signCenter = projectCam(toCamera(innerX, 2.85f, b.worldZ))
  if (signCenter != null && toCamera(innerX, 2.85f, b.worldZ).z < 26f) {
    val measured = textMeasurer.measure(b.title, style = labelStyle)
    val pw = measured.size.width + 14f
    val ph = measured.size.height + 6f
    drawRoundRect(
      color = Color.White.copy(alpha = 0.9f),
      topLeft = Offset(signCenter.x - pw * 0.5f, signCenter.y - ph * 0.5f),
      size = Size(pw, ph),
      cornerRadius = CornerRadius(8f, 8f),
    )
    drawText(measured, topLeft = Offset(signCenter.x - measured.size.width * 0.5f, signCenter.y - measured.size.height * 0.5f))
  }
}

private fun DrawScope.drawProp3D(
  prop: StreetProp,
  toCamera: (Float, Float, Float) -> Vec3,
  projectCam: (Vec3) -> Offset?,
  glowPulse: Float,
): ProjectedHitTarget? {
  val base = projectCam(toCamera(prop.worldX, 0f, prop.worldZ)) ?: return null
  val camZ = toCamera(prop.worldX, 0f, prop.worldZ).z
  val scale = (size.width * 0.82f / camZ).coerceIn(8f, 160f)

  when (prop.kind) {
    PropKind.LAMP -> {
      val top = projectCam(toCamera(prop.worldX, 3.1f, prop.worldZ)) ?: base
      drawLine(Color(0xFF282D3B), start = base, end = top, strokeWidth = (scale * 0.08f).coerceAtLeast(2f))
      drawCircle(Color(0xFFFFD875).copy(alpha = 0.25f), radius = scale * 0.42f * glowPulse, center = top)
      drawCircle(Color(0xFFFFECAA), radius = scale * 0.16f, center = top)
    }
    PropKind.TREE -> {
      val trunkTop = projectCam(toCamera(prop.worldX, 1.8f, prop.worldZ)) ?: base
      drawLine(Color(0xFF6B482F), start = base, end = trunkTop, strokeWidth = (scale * 0.18f).coerceAtLeast(3f))
      drawCircle(Color(0xFF287D5E), radius = scale * 0.65f, center = trunkTop)
      drawCircle(Color(0xFF4CA880), radius = scale * 0.42f, center = Offset(trunkTop.x - scale * 0.16f, trunkTop.y - scale * 0.18f))
    }
    PropKind.FOUNTAIN -> {
      val top = projectCam(toCamera(prop.worldX, 1.3f, prop.worldZ)) ?: base
      drawOval(
        color = Color(0xFFD7C8B5),
        topLeft = Offset(base.x - scale * 0.85f, base.y - scale * 0.22f),
        size = Size(scale * 1.7f, scale * 0.45f),
      )
      drawOval(
        color = Color(0xFF4F9BE0),
        topLeft = Offset(base.x - scale * 0.68f, base.y - scale * 0.15f),
        size = Size(scale * 1.36f, scale * 0.3f),
      )
      drawLine(Color(0xFFA8DCFF), start = base, end = top, strokeWidth = (scale * 0.12f).coerceAtLeast(2f))
      drawCircle(Color(0xFFD6F1FF).copy(alpha = 0.75f), radius = scale * 0.22f * glowPulse, center = top)
    }
    PropKind.CAFE_TABLE -> {
      val top = projectCam(toCamera(prop.worldX, 0.85f, prop.worldZ)) ?: base
      drawLine(Color(0xFF3E3A36), start = base, end = top, strokeWidth = (scale * 0.08f).coerceAtLeast(2f))
      drawOval(
        color = Color(0xFFFFF8EE),
        topLeft = Offset(top.x - scale * 0.45f, top.y - scale * 0.14f),
        size = Size(scale * 0.9f, scale * 0.28f),
      )
    }
    PropKind.SIGNPOST -> {
      val top = projectCam(toCamera(prop.worldX, 1.7f, prop.worldZ)) ?: base
      drawLine(Color(0xFF3B4254), start = base, end = top, strokeWidth = (scale * 0.08f).coerceAtLeast(2f))
      drawRoundRect(
        color = Color(0xFF1D5B9E),
        topLeft = Offset(top.x - scale * 0.4f, top.y - scale * 0.2f),
        size = Size(scale * 0.8f, scale * 0.36f),
        cornerRadius = CornerRadius(6f, 6f),
      )
    }
  }
  return ProjectedHitTarget(base.x, base.y - scale * 0.5f, (scale * 0.8f).coerceAtLeast(36f), prop = prop)
}

private fun DrawScope.drawLocal3D(
  local: StreetLocal,
  isNearby: Boolean,
  isDone: Boolean,
  bob: Float,
  glowPulse: Float,
  accentColor: Color,
  toCamera: (Float, Float, Float) -> Vec3,
  projectCam: (Vec3) -> Offset?,
  textMeasurer: TextMeasurer,
  labelStyle: TextStyle,
  subLabelStyle: TextStyle,
): ProjectedHitTarget? {
  val feet = projectCam(toCamera(local.worldX, 0f, local.worldZ)) ?: return null
  val headCenter = projectCam(toCamera(local.worldX, 1.55f + bob, local.worldZ)) ?: return null
  val camZ = toCamera(local.worldX, 0f, local.worldZ).z
  val bodyH = (feet.y - headCenter.y).coerceIn(18f, 260f)
  val unit = bodyH / 3.2f

  // Ground shadow + proximity halo
  drawOval(
    color = Color.Black.copy(alpha = 0.2f),
    topLeft = Offset(feet.x - unit * 0.8f, feet.y - unit * 0.2f),
    size = Size(unit * 1.6f, unit * 0.42f),
  )
  if (isNearby) {
    drawOval(
      color = accentColor.copy(alpha = 0.35f),
      topLeft = Offset(feet.x - unit * 1.25f * glowPulse, feet.y - unit * 0.32f),
      size = Size(unit * 2.5f * glowPulse, unit * 0.64f),
      style = Stroke(width = 3f),
    )
  }

  // Legs
  drawLine(
    color = Color(0xFF273043),
    start = Offset(feet.x - unit * 0.22f, feet.y),
    end = Offset(feet.x - unit * 0.22f, feet.y - bodyH * 0.38f),
    strokeWidth = (unit * 0.22f).coerceAtLeast(3f),
  )
  drawLine(
    color = Color(0xFF273043),
    start = Offset(feet.x + unit * 0.22f, feet.y),
    end = Offset(feet.x + unit * 0.22f, feet.y - bodyH * 0.38f),
    strokeWidth = (unit * 0.22f).coerceAtLeast(3f),
  )

  // Torso outfit
  drawRoundRect(
    color = local.outfitColor,
    topLeft = Offset(feet.x - unit * 0.55f, headCenter.y + unit * 0.38f),
    size = Size(unit * 1.1f, bodyH * 0.48f),
    cornerRadius = CornerRadius(unit * 0.3f, unit * 0.3f),
  )

  // Waving arm when nearby
  val waveY = if (isNearby) headCenter.y + unit * (0.15f + bob * 3f) else headCenter.y + unit * 0.9f
  drawLine(
    color = local.outfitColor,
    start = Offset(feet.x + unit * 0.5f, headCenter.y + unit * 0.55f),
    end = Offset(feet.x + unit * 0.95f, waveY),
    strokeWidth = (unit * 0.2f).coerceAtLeast(2.5f),
  )

  // Head & Hair
  drawCircle(color = local.skinTone, radius = unit * 0.46f, center = headCenter)
  drawArc(
    color = local.hairColor,
    startAngle = 180f,
    sweepAngle = 180f,
    useCenter = true,
    topLeft = Offset(headCenter.x - unit * 0.48f, headCenter.y - unit * 0.5f),
    size = Size(unit * 0.96f, unit * 0.62f),
  )

  // Floating nameplate & role if within 28m
  if (camZ < 28f) {
    val title = if (isDone) "✓ ${local.name}" else local.name
    val mTitle = textMeasurer.measure(title, style = labelStyle)
    val mRole = textMeasurer.measure(local.roleTitle, style = subLabelStyle)
    val boxW = maxOf(mTitle.size.width, mRole.size.width) + 20f
    val boxH = mTitle.size.height + mRole.size.height + 12f
    val bx = headCenter.x - boxW * 0.5f
    val by = headCenter.y - unit * 0.7f - boxH - 8f

    drawRoundRect(
      color = if (isNearby) Color(0xFFFFF4DC) else Color.White.copy(alpha = 0.94f),
      topLeft = Offset(bx, by),
      size = Size(boxW, boxH),
      cornerRadius = CornerRadius(14f, 14f),
    )
    drawRoundRect(
      color = if (isDone) Color(0xFF287D5E) else accentColor,
      topLeft = Offset(bx, by),
      size = Size(boxW, boxH),
      cornerRadius = CornerRadius(14f, 14f),
      style = Stroke(width = if (isNearby) 3f else 2f),
    )
    drawText(mTitle, topLeft = Offset(headCenter.x - mTitle.size.width * 0.5f, by + 5f))
    drawText(mRole, topLeft = Offset(headCenter.x - mRole.size.width * 0.5f, by + 6f + mTitle.size.height))
  }

  return ProjectedHitTarget(
    x = headCenter.x,
    y = (headCenter.y + feet.y) * 0.5f,
    radius = (bodyH * 0.75f).coerceAtLeast(48f),
    local = local,
  )
}
