package com.example.pleoscamping.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleoscamping.astro.SkyFrame
import com.example.pleoscamping.astro.StarName
import com.example.pleoscamping.ui.theme.PleosAccent
import com.example.pleoscamping.ui.theme.SkyCross
import com.example.pleoscamping.ui.theme.SkyGround
import com.example.pleoscamping.ui.theme.SkyHorizon
import com.example.pleoscamping.ui.theme.SkyLine
import com.example.pleoscamping.ui.theme.SkyLineDim
import com.example.pleoscamping.ui.theme.SkyLow
import com.example.pleoscamping.ui.theme.SkyName
import com.example.pleoscamping.ui.theme.SkyPlanet
import com.example.pleoscamping.ui.theme.SkyText
import com.example.pleoscamping.ui.theme.SkyTop
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** 시선 상태: 방위(절대, 0=북)·고도·가로 시야 (SPEC5 1절) */
data class SkyView(val azDeg: Float, val altDeg: Float, val fovDeg: Float)

// 하늘 색 (SPEC_DARK 2절). 강조(선·이름·"앞" 표시)는 PleosAccent

/**
 * 1인칭 지평선 뷰(앞유리 뷰, SPEC5 1절). 시선 중심은 view(방위·고도), 가로 시야 fov.
 * 투영은 stereographic이고 오른쪽이 +X다. 드래그로 시선을 돌리고 핀치로 시야를 조절한다.
 * frame이 null이면 하늘·땅·방위 띠만 그리고 "밤하늘 계산 중…"을 보인다.
 */
@Composable
fun SkyWindshield(
  frame: SkyFrame?,
  headingDeg: Float,
  view: SkyView,
  onViewChange: (SkyView) -> Unit,
  highlightId: String?,
  names: Map<Int, StarName>,
  modifier: Modifier = Modifier
) {
  val textMeasurer = rememberTextMeasurer()
  val currentView by rememberUpdatedState(view)
  val currentOnViewChange by rememberUpdatedState(onViewChange)

  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(2f)
  ) {
    Canvas(
      modifier = Modifier.clipToBounds()
        .fillMaxSize()
        .pointerInput(Unit) {
          detectTransformGestures { _, pan, zoom, _ ->
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            if (w <= 0f || h <= 0f) return@detectTransformGestures
            val v = currentView
            // 가로 pan: 왼쪽으로 끌면 시선이 오른쪽으로(하늘이 손을 따라 움직인다)
            val az = ((v.azDeg - pan.x / w * v.fovDeg) % 360f + 360f) % 360f
            val alt = (v.altDeg + pan.y / h * (v.fovDeg / 2f)).coerceIn(-10f, 80f)
            val fov = (v.fovDeg / zoom).coerceIn(50f, 120f)
            currentOnViewChange(SkyView(az, alt, fov))
          }
        }
    ) {
      drawWindshield(textMeasurer, frame, view, headingDeg, highlightId, names)
    }

    if (frame == null) {
      Text(
        "밤하늘 계산 중…",
        color = SkyText,
        fontSize = 20.sp,
        modifier = Modifier.align(Alignment.Center)
      )
    }
  }
}

/** stereographic 투영 (SPEC5 1절 공식 그대로). 시선 중심 (az0, alt0), 가로 시야 fov가 화면 폭에 맞는다 */
private class WindshieldProjector(w: Float, h: Float, az0: Double, alt0: Double, fovDeg: Float) {
  private val lam0 = Math.toRadians(az0)
  private val phi1 = Math.toRadians(alt0)
  private val scale = (w / 2f) / (2.0 * tan(Math.toRadians(fovDeg.toDouble()) / 4.0)).toFloat()
  private val cx = w / 2f
  private val cy = h / 2f

  /** (az, alt)를 화면 좌표로 담는다. 시선 뒤쪽(cosc < −0.2)이면 false */
  fun project(azDeg: Double, altDeg: Double, out: FloatArray): Boolean {
    val lam = Math.toRadians(azDeg)
    val phi = Math.toRadians(altDeg)
    val cosc = sin(phi1) * sin(phi) + cos(phi1) * cos(phi) * cos(lam - lam0)
    if (cosc < -0.2) return false
    val k = 2.0 / (1.0 + cosc)
    val x = k * cos(phi) * sin(lam - lam0)                    // 오른쪽 +
    val y = k * (cos(phi1) * sin(phi) - sin(phi1) * cos(phi) * cos(lam - lam0))  // 위쪽 +
    out[0] = cx + (scale * x).toFloat()
    out[1] = cy - (scale * y).toFloat()
    return true
  }
}

private fun DrawScope.drawWindshield(
  measurer: TextMeasurer,
  frame: SkyFrame?,
  view: SkyView,
  headingDeg: Float,
  highlightId: String?,
  names: Map<Int, StarName>
) {
  val w = size.width
  val h = size.height

  // ① 하늘: 위 SkyTop → 아래 SkyLow 세로 그라데이션(지평선 근처가 약간 밝음)
  drawRect(SkyTop)
  drawRect(Brush.verticalGradient(listOf(SkyTop, SkyLow), endY = h))

  val proj = WindshieldProjector(w, h, view.azDeg.toDouble(), view.altDeg.toDouble(), view.fovDeg)
  val pt = FloatArray(2)

  // ② 땅: alt=0 선을 시선 ±100°에서 2°마다 투영해 꺾은선으로 잇고 아래를 채운다
  val ground = Path()
  val horizon = Path()
  var started = false
  var d = -100.0
  while (d <= 100.0001) {
    if (proj.project(view.azDeg + d, 0.0, pt)) {
      if (started) {
        ground.lineTo(pt[0], pt[1])
        horizon.lineTo(pt[0], pt[1])
      } else {
        ground.moveTo(pt[0], pt[1])
        horizon.moveTo(pt[0], pt[1])
        started = true
      }
    }
    d += 2.0
  }
  if (started) {
    ground.lineTo(w, h)
    ground.lineTo(0f, h)
    ground.close()
    drawPath(ground, SkyGround)
    drawPath(horizon, SkyHorizon, style = Stroke(width = 1.5.dp.toPx()))
  }

  // ③ 방위 띠: alt=−2°에 15°마다 눈금, 45°마다 글자 두 줄(차 기준 + 절대 방위)
  val tickHalf = 3.dp.toPx()
  var az = Math.ceil((view.azDeg - 100.0) / 15.0) * 15.0
  while (az <= view.azDeg + 100.0001) {
    val azNorm = ((az % 360.0) + 360.0) % 360.0
    if (proj.project(azNorm, -2.0, pt) && pt[1] > -40f && pt[1] < h) {
      drawLine(
        SkyHorizon,
        start = Offset(pt[0], pt[1] - tickHalf),
        end = Offset(pt[0], pt[1] + tickHalf),
        strokeWidth = 1.dp.toPx()
      )
      if (azNorm % 45.0 == 0.0) {
        val rel = ((azNorm - headingDeg) % 360.0 + 360.0) % 360.0
        val carDir = when (rel.roundToInt()) {
          0 -> "앞"
          90 -> "오른쪽"
          180 -> "뒤"
          270 -> "왼쪽"
          else -> null
        }
        val topY = pt[1] + 16.dp.toPx()
        if (carDir != null) {
          // "앞"은 PleosAccent 굵게 (SPEC5 1절, SPEC_DARK 2절)
          val isFront = carDir == "앞"
          val style = TextStyle(
            fontSize = 16.sp,
            color = if (isFront) PleosAccent else SkyText,
            fontWeight = if (isFront) FontWeight.Bold else null
          )
          val layout = measurer.measure(carDir, style)
          drawText(layout, topLeft = Offset(pt[0] - layout.size.width / 2f, topY - layout.size.height / 2f))
        }
        drawCentered(measurer, absDirKo(azNorm), Offset(pt[0], topY + 18.dp.toPx()), SkyText, 12.sp)
      }
    }
    az += 15.0
  }

  if (frame != null) {
    val pa = FloatArray(2)
    val pb = FloatArray(2)

    // ④ 별자리 선. 강조 중이면 나머지는 어둡게, 강조 별자리는 굵게. 양 끝 모두 시선 앞쪽일 때만
    for (line in frame.lines) {
      if (!proj.project(line.a.azDeg, line.a.altDeg, pa)) continue
      if (!proj.project(line.b.azDeg, line.b.altDeg, pb)) continue
      val hi = highlightId != null && line.conId == highlightId
      drawLine(
        color = if (hi) PleosAccent else if (highlightId != null) SkyLineDim else SkyLine,
        start = Offset(pa[0], pa[1]),
        end = Offset(pb[0], pb[1]),
        strokeWidth = (if (hi) 2.5f else 1f).dp.toPx()
      )
    }

    // ⑤ 별: 등급별 반지름·색은 SkyDomeView와 같고, 반지름에 fov 보정(확대하면 커진다). 고도 −5° 아래는 생략
    val fovScale = (100f / view.fovDeg).coerceIn(0.7f, 1.6f)
    val nameLabels = ArrayList<Pair<Offset, String>>(32)
    for (placed in frame.stars) {
      if (placed.p.altDeg < -5.0) continue
      if (!proj.project(placed.p.azDeg, placed.p.altDeg, pt)) continue
      val radiusPx = starRadiusDp(placed.star.mag) * fovScale
      if (pt[0] < -radiusPx - 60f || pt[0] > w + radiusPx + 60f ||
        pt[1] < -radiusPx - 60f || pt[1] > h + radiusPx + 60f
      ) continue
      val center = Offset(pt[0], pt[1])
      drawCircle(starColor(placed.star.mag, placed.star.bv), radius = radiusPx.dp.toPx(), center = center)
      val name = names[placed.star.hip]
      if (name != null && placed.star.mag <= 1.6) nameLabels += center to name.ko
    }

    // ⑥ 라벨: SkyDomeView의 placedRects 방식 재사용
    val placedRects = ArrayList<Rect>()
    // 별 이름(등급 ≤ 1.6 & 이름 있음): 별 오른쪽 위. 겹쳐도 그 자리에 그린다
    for ((center, text) in nameLabels) {
      val fsPx = 16.sp.toPx()
      val lw = text.length * fsPx * 0.6f
      val lh = fsPx * 1.2f
      val left = center.x + 6.dp.toPx()
      val top = center.y - 6.dp.toPx() - lh
      val rect = placeLabelRect(placedRects, Rect(left, top, left + lw, top + lh), lh, force = true) ?: continue
      val layout = measurer.measure(text, TextStyle(fontSize = 16.sp, color = SkyName))
      drawText(layout, topLeft = Offset(rect.left, rect.top))
      placedRects += rect
    }
    // 별자리 이름: 화면 안의 rank ≤ 2 또는 강조 별자리. 겹치면 생략
    var highlightCenter: Offset? = null
    var highlightText: String? = null
    for ((con, p) in frame.allConstellations) {
      val isHi = con.id == highlightId
      if (con.rank > 2 && !isHi) continue
      if (!proj.project(p.azDeg, p.altDeg, pt)) continue
      if (pt[0] < 0f || pt[0] > w || pt[1] < 0f || pt[1] > h) continue
      if (isHi) {
        highlightCenter = Offset(pt[0], pt[1])
        highlightText = con.ko
        continue
      }
      val fsPx = 18.sp.toPx()
      val lw = con.ko.length * fsPx * 0.6f
      val lh = fsPx * 1.2f
      val base = Rect(pt[0] - lw / 2f, pt[1] - lh / 2f, pt[0] + lw / 2f, pt[1] + lh / 2f)
      val rect = placeLabelRect(placedRects, base, lh) ?: continue
      val layout = measurer.measure(con.ko, TextStyle(fontSize = 18.sp, color = SkyText))
      drawText(layout, topLeft = Offset(rect.left, rect.top))
      placedRects += rect
    }
    // 강조 별자리 이름: 겹침 검사 없이 크게
    val hiText = highlightText
    if (hiText != null && highlightCenter != null) {
      drawCentered(measurer, hiText, highlightCenter, PleosAccent, 26.sp)
    }

    // ⑦ 행성: 원 + 이름. 달: SkyDomeView의 두 원 근사 재사용(반지름 10dp)
    for (planet in frame.planets) {
      if (!proj.project(planet.p.azDeg, planet.p.altDeg, pt)) continue
      val center = Offset(pt[0], pt[1])
      drawCircle(SkyPlanet, radius = 4.dp.toPx(), center = center)
      val layout = measurer.measure(planet.label, TextStyle(fontSize = 16.sp, color = SkyPlanet))
      drawText(layout, topLeft = Offset(center.x + 7.dp.toPx(), center.y - layout.size.height / 2f))
    }
    frame.moon?.let { moon ->
      if (moon.p.altDeg > 0.0 && proj.project(moon.p.azDeg, moon.p.altDeg, pt)) {
        // 달 뒤 하늘색: 세로 그라데이션 위치에 맞춰 보간한 색으로 덮는다
        val t = (pt[1] / h).coerceIn(0f, 1f)
        drawMoonApprox(
          center = Offset(pt[0], pt[1]),
          radiusPx = 10.dp.toPx(),
          phaseDeg = moon.phaseDeg,
          shade = lerp(SkyTop, SkyLow, t)
        )
      }
    }
  }

  // ⑧ 화면 가운데 작은 십자 — 시선 중심 표시
  val r = 3.dp.toPx()
  drawLine(SkyCross, Offset(w / 2f - r, h / 2f), Offset(w / 2f + r, h / 2f), strokeWidth = 1.dp.toPx())
  drawLine(SkyCross, Offset(w / 2f, h / 2f - r), Offset(w / 2f, h / 2f + r), strokeWidth = 1.dp.toPx())
}

/** 절대 방위 8방위 약자 (SPEC5 1절) */
private fun absDirKo(azDeg: Double): String = when ((azDeg.roundToInt() / 45) % 8) {
  0 -> "N"
  1 -> "NE"
  2 -> "E"
  3 -> "SE"
  4 -> "S"
  5 -> "SW"
  6 -> "W"
  else -> "NW"
}
