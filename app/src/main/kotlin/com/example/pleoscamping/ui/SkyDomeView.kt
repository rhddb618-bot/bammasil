package com.example.pleoscamping.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleoscamping.astro.PlacedStar
import com.example.pleoscamping.astro.SkyFrame
import com.example.pleoscamping.astro.SkyPoint
import com.example.pleoscamping.astro.StarName
import com.example.pleoscamping.ui.theme.PleosAccent
import com.example.pleoscamping.ui.theme.SkyHorizon
import com.example.pleoscamping.ui.theme.SkyLine
import com.example.pleoscamping.ui.theme.SkyLineDim
import com.example.pleoscamping.ui.theme.SkyMoon
import com.example.pleoscamping.ui.theme.SkyName
import com.example.pleoscamping.ui.theme.SkyPlanet
import com.example.pleoscamping.ui.theme.SkyText
import com.example.pleoscamping.ui.theme.SkyTop
import com.example.pleoscamping.ui.theme.skyStarColor
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// 하늘 색 (SPEC_DARK 2절). DomeBackground·DomeBorder는 SkyRadar(SPEC5 2절)가 재사용
internal val DomeBackground = SkyTop
internal val DomeBorder = SkyHorizon
private val DomeGrid = SkyLineDim
private val CardinalText = SkyText
private val ConstellationLineColor = SkyLine
private val StarLabelColor = SkyName
private val ConstellationLabelColor = SkyText
private val PlanetColor = SkyPlanet
private val MoonBrightColor = SkyMoon

private val HighlightColor = PleosAccent         // 강조 별자리 선·이름
private val DimmedLineColor = SkyLineDim         // 강조 중 나머지 별자리 선
private val NorthMarkColor = SkyText             // 북쪽 N 표시
private val CarIconColor = PleosAccent           // 가운데 차 아이콘

/** 고도 보조 원(점선) */
private val ALTITUDE_RINGS = doubleArrayOf(30.0, 60.0)

/** 등급 0..5의 별 반지름(dp) 표 (SPEC3 2-3: ≤0:5, 1:4.2, 2:3.4, 3:2.6, 4:1.9, 5:1.3) */
private val MAG_RADIUS_DP = floatArrayOf(5.0f, 4.2f, 3.4f, 2.6f, 1.9f, 1.3f)

/** 정규화된 돔 좌표(-1..1, 반지름 1). 그리기 시 반지름을 곱해 픽셀로 바꾼다.
 *  SkyRadar(SPEC5 2절)가 같은 투영을 재사용한다 */
internal class Norm(val x: Float, val y: Float)

private class DomeLine(val conId: String, val a: Norm, val b: Norm)

private class StarDot(val at: Norm, val radiusDp: Float, val color: Color)

private class LabelItem(val text: String, val at: Norm, val color: Color, val fontSize: TextUnit)

private class PlanetDot(val label: String, val at: Norm)

private class MoonDot(val at: Norm, val phaseDeg: Double)

/** 프레임 → 화면 좌표(정규화)를 미리 계산해 둔 캐시. Canvas는 이것을 그리기만 한다 */
private class DomeCache(
  val lines: List<DomeLine>,
  val starDots: List<StarDot>,
  val starLabels: List<LabelItem>,
  val constellationLabels: List<LabelItem>,
  val highlightLabel: LabelItem?,
  val planetDots: List<PlanetDot>,
  val moonDot: MoonDot?
)

/** 등거리 방위 투영: 천정이 중심, r = (90 − alt)/90. 차 앞쪽(rel 0)이 위 (SPEC4 3절).
 *  투영에 방위각 대신 상대 방위 rel = az − headingDeg를 쓴다: x = cx + r·sin(rel), y = cy − r·cos(rel) (차 안 화면: 오른쪽 = 조수석 쪽) */
internal fun project(p: SkyPoint, headingDeg: Float): Norm {
  val rel = Math.toRadians(p.azDeg - headingDeg)
  val f = ((90.0 - p.altDeg) / 90.0).toFloat()
  return Norm((f * sin(rel)).toFloat(), (-f * cos(rel)).toFloat())
}

private fun buildDomeCache(
  frame: SkyFrame,
  starNames: Map<Int, StarName>,
  headingDeg: Float,
  highlightId: String?
): DomeCache {
  val lines = ArrayList<DomeLine>(frame.lines.size)
  for (line in frame.lines) {
    lines += DomeLine(line.conId, project(line.a, headingDeg), project(line.b, headingDeg))
  }

  // 강조 별자리 소속 별을 가리기 위한 선 끝점 목록
  val highlightEndpoints =
    if (highlightId == null) emptyList()
    else frame.lines.filter { it.conId == highlightId }.flatMap { line -> listOf(line.a, line.b) }

  val starDots = ArrayList<StarDot>(frame.stars.size)
  val starLabels = ArrayList<LabelItem>()
  for (placed in frame.stars) {
    if (placed.p.altDeg < 0.0) continue // 고도 < 0인 별은 그리지 않는다
    val at = project(placed.p, headingDeg)
    val baseColor = starColor(placed.star.mag, placed.star.bv)
    // 강조 중이면 강조 별자리 밖의 별은 밝기 40%로 줄인다 (SPEC4 3절)
    val color =
      if (highlightId != null && !starInConstellation(placed, highlightId, highlightEndpoints, starNames)) {
        lerp(baseColor, Color.Black, 0.6f)
      } else {
        baseColor
      }
    starDots += StarDot(at, starRadiusDp(placed.star.mag), color)
    val name = starNames[placed.star.hip]
    if (name != null && placed.star.mag <= 1.6) {
      starLabels += LabelItem(name.ko, at, StarLabelColor, 18.sp)
    }
  }

  val conLabels = frame.constellationLabels
    .filter { it.first.id != highlightId } // 강조 별자리 이름은 아래에서 따로 크게 그린다
    .map { LabelItem(it.first.ko, project(it.second, headingDeg), ConstellationLabelColor, 20.sp) }

  val highlightLabel = if (highlightId == null) null else frame.allConstellations
    .firstOrNull { it.first.id == highlightId }
    ?.let { LabelItem(it.first.ko, project(it.second, headingDeg), HighlightColor, 26.sp) }

  val planetDots = frame.planets.map { PlanetDot(it.label, project(it.p, headingDeg)) }

  val moonDot = frame.moon
    ?.takeIf { it.p.altDeg > 0.0 } // 달이 지평선 위일 때만 그린다
    ?.let { MoonDot(project(it.p, headingDeg), it.phaseDeg) }

  return DomeCache(lines, starDots, starLabels, conLabels, highlightLabel, planetDots, moonDot)
}

/** 강조 별자리 소속 별: star_names의 c가 일치하거나 그 별자리 선 끝점에서 0.6° 안에 있는 별 (SPEC4 3절) */
private fun starInConstellation(
  placed: PlacedStar,
  conId: String,
  highlightEndpoints: List<SkyPoint>,
  starNames: Map<Int, StarName>
): Boolean {
  if (starNames[placed.star.hip]?.con == conId) return true
  for (ep in highlightEndpoints) {
    if (angularDistDeg(placed.p, ep) <= 0.6) return true
  }
  return false
}

/** 두 지평좌표(고도·방위)의 각거리(도) */
private fun angularDistDeg(a: SkyPoint, b: SkyPoint): Double {
  val alt1 = Math.toRadians(a.altDeg)
  val alt2 = Math.toRadians(b.altDeg)
  val dAz = Math.toRadians(a.azDeg - b.azDeg)
  val c = (sin(alt1) * sin(alt2) + cos(alt1) * cos(alt2) * cos(dAz)).coerceIn(-1.0, 1.0)
  return Math.toDegrees(acos(c))
}

internal fun starRadiusDp(mag: Double): Float {
  if (mag <= 0.0) return MAG_RADIUS_DP[0]
  if (mag >= 5.0) return MAG_RADIUS_DP[5]
  val i = mag.toInt()
  return MAG_RADIUS_DP[i] + (MAG_RADIUS_DP[i + 1] - MAG_RADIUS_DP[i]) * (mag - i).toFloat()
}

/** 별 색: 등급 구간별 보간과 붉은 별(B−V > 1.2) 보간은 SkyColors.skyStarColor가 담당 (SPEC_DARK 2절) */
internal fun starColor(mag: Double, bv: Double): Color = skyStarColor(mag, bv)

/** 라벨 겹침 방지(placedRects 방식). base에서 겹치면 dropStep만큼 아래로 maxDrops번 내려 본다.
 *  force=true면 끝내 겹쳐도 마지막 위치를 돌려주고(그 자리에 그림), false면 겹치면 null(생략).
 *  SkyWindshield(SPEC5 1절)가 같은 방식을 재사용한다 */
internal fun placeLabelRect(
  placedRects: List<Rect>,
  base: Rect,
  dropStep: Float,
  maxDrops: Int = 2,
  force: Boolean = false
): Rect? {
  var rect = base
  var drops = 0
  while (drops < maxDrops && placedRects.any { it.overlaps(rect) }) {
    drops++
    rect = Rect(base.left, base.top + dropStep * drops, base.right, base.bottom + dropStep * drops)
  }
  return if (force || placedRects.none { it.overlaps(rect) }) rect else null
}

/** 달 두 원 근사: 밝은 원 위에 어두운(shade) 원을 phaseDeg에 따라 가로로 밀어 월상을 나타낸다.
 *  SkyWindshield(SPEC5 1절)가 같은 근사를 재사용한다 */
internal fun DrawScope.drawMoonApprox(center: Offset, radiusPx: Float, phaseDeg: Double, shade: Color) {
  drawCircle(MoonBrightColor, radius = radiusPx, center = center)
  val shift = 2.0 * radiusPx * (if (phaseDeg < 180.0) phaseDeg else 360.0 - phaseDeg) / 180.0
  val dx = if (phaseDeg < 180.0) -shift else shift
  drawCircle(shade, radius = radiusPx, center = Offset(center.x + dx.toFloat(), center.y))
}

/**
 * 밤하늘 돔 지도 (SPEC3 2-3, SPEC4 3절). 정사각형, 부모 폭 기준, 최대 640dp.
 * 차 앞쪽(headingDeg)이 위. highlightId가 있으면 그 별자리를 강조한다.
 * frame이 null이면 같은 크기의 빈 원과 "밤하늘 계산 중…"을 보인다.
 */
@Composable
fun SkyDome(
  frame: SkyFrame?,
  headingDeg: Float,
  highlightId: String?,
  modifier: Modifier = Modifier,
  starNames: Map<Int, StarName> = emptyMap()
) {
  val textMeasurer = rememberTextMeasurer()
  val cache = remember(frame, starNames, headingDeg, highlightId) {
    frame?.let { buildDomeCache(it, starNames, headingDeg, highlightId) }
  }

  Box(
    // 바깥에서 정해 준 크기(width 등)가 있으면 그 크기를 따른다. widthIn(max)는 더 넓게 나올 때만 640dp로 제한
    modifier = modifier
      .fillMaxWidth()
      .widthIn(max = 640.dp)
      .aspectRatio(1f),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val cx = size.width / 2f
      val cy = size.height / 2f
      val domeR = min(size.width, size.height) / 2f - 34.dp.toPx()

      // ① 바탕 원 + 지평선 테두리
      drawCircle(DomeBackground, radius = domeR, center = Offset(cx, cy))
      drawCircle(DomeBorder, radius = domeR, center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))

      // ② 고도 30°·60° 보조 원(점선)
      val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))
      for (alt in ALTITUDE_RINGS) {
        drawCircle(
          DomeGrid,
          radius = domeR * ((90.0 - alt) / 90.0).toFloat(),
          center = Offset(cx, cy),
          style = Stroke(width = 1.dp.toPx(), pathEffect = dash)
        )
      }

      // ③ 방위 글자(원 바깥 여백): 앞=위, 오른쪽=조수석 쪽, 뒤=아래, 왼쪽 (SPEC4 3절)
      val cardinalOffset = domeR + 17.dp.toPx()
      drawCentered(textMeasurer, "앞", Offset(cx, cy - cardinalOffset), CardinalText, 22.sp)
      drawCentered(textMeasurer, "오른쪽", Offset(cx + cardinalOffset, cy), CardinalText, 22.sp)
      drawCentered(textMeasurer, "뒤", Offset(cx, cy + cardinalOffset), CardinalText, 22.sp)
      drawCentered(textMeasurer, "왼쪽", Offset(cx - cardinalOffset, cy), CardinalText, 22.sp)

      // 북쪽 표시: 상대 방위 −headingDeg 자리, 지평선 원 바로 안쪽에 눈금 + "N" (SPEC4 3절)
      val nRel = Math.toRadians(-headingDeg.toDouble())
      val nx = sin(nRel).toFloat()
      val ny = -cos(nRel).toFloat()
      drawLine(
        NorthMarkColor,
        start = Offset(cx + nx * domeR, cy + ny * domeR),
        end = Offset(cx + nx * (domeR - 6.dp.toPx()), cy + ny * (domeR - 6.dp.toPx())),
        strokeWidth = 1.dp.toPx()
      )
      val nTextR = domeR - 20.dp.toPx()
      drawCentered(textMeasurer, "N", Offset(cx + nx * nTextR, cy + ny * nTextR), NorthMarkColor, 16.sp)

      val c = cache
      if (c == null) {
        drawCarIcon(cx, cy)
        return@Canvas
      }

      // ④ 별자리 선 + ⑤ 별은 지평선 원 안에 잘라 그린다. 강조 중에는 색·굵기·밝기를 바꾼다 (SPEC4 3절)
      val domePath = Path().apply { addOval(Rect(cx - domeR, cy - domeR, cx + domeR, cy + domeR)) }
      clipPath(domePath) {
        for (line in c.lines) {
          val hi = highlightId != null && line.conId == highlightId
          drawLine(
            color = if (hi) HighlightColor else if (highlightId != null) DimmedLineColor else ConstellationLineColor,
            start = Offset(cx + line.a.x * domeR, cy + line.a.y * domeR),
            end = Offset(cx + line.b.x * domeR, cy + line.b.y * domeR),
            strokeWidth = (if (hi) 2.5f else 1f).dp.toPx()
          )
        }
        for (dot in c.starDots) {
          drawCircle(
            color = dot.color,
            radius = dot.radiusDp.dp.toPx(),
            center = Offset(cx + dot.at.x * domeR, cy + dot.at.y * domeR)
          )
        }
      }

      // 가운데 차 아이콘: 별보다 위에 그린다 (SPEC4 3절)
      drawCarIcon(cx, cy)

      // 그려진 라벨의 추정 사각형 목록(겹침 방지용).
      // 폭 = 글자 수 × 글자크기 × 0.6, 높이 = 글자크기 × 1.2
      val placedRects = ArrayList<Rect>()

      // ⑥ 별 이름(등급 ≤ 1.6이고 이름 있는 별): 별 오른쪽 위.
      // 겹치면 라벨 높이만큼 아래로 두 번까지 내려 보고, 그래도 겹치면 그 자리에 그린다(생략하지 않음)
      for (label in c.starLabels) {
        val fsPx = label.fontSize.toPx()
        val w = label.text.length * fsPx * 0.6f
        val h = fsPx * 1.2f
        val baseLeft = cx + label.at.x * domeR + 6.dp.toPx()
        val baseTop = cy + label.at.y * domeR - 6.dp.toPx() - h
        val layout = textMeasurer.measure(label.text, TextStyle(fontSize = label.fontSize, color = label.color))
        val rect = placeLabelRect(placedRects, Rect(baseLeft, baseTop, baseLeft + w, baseTop + h), h, force = true)
        if (rect != null) {
          drawText(layout, topLeft = Offset(rect.left, rect.top))
          placedRects += rect
        }
      }

      // ⑦ 별자리 이름: 별 이름을 먼저 그린 뒤 배치한다.
      // 겹치면 라벨 높이만큼 아래로 두 번까지 내려 보고, 그래도 겹치면 생략한다
      for (label in c.constellationLabels) {
        val fsPx = label.fontSize.toPx()
        val w = label.text.length * fsPx * 0.6f
        val h = fsPx * 1.2f
        val centerX = cx + label.at.x * domeR
        val centerY = cy + label.at.y * domeR
        val layout = textMeasurer.measure(label.text, TextStyle(fontSize = label.fontSize, color = label.color))
        val rect = placeLabelRect(
          placedRects,
          Rect(centerX - w / 2f, centerY - h / 2f, centerX + w / 2f, centerY + h / 2f),
          h
        ) ?: continue
        drawText(layout, topLeft = Offset(rect.left, rect.top))
        placedRects += rect
      }

      // ⑦′ 강조 별자리 이름: 겹침 검사 없이 크게 그린다 (SPEC4 3절)
      c.highlightLabel?.let { label ->
        val layout = textMeasurer.measure(label.text, TextStyle(fontSize = label.fontSize, color = label.color))
        drawText(
          layout,
          topLeft = Offset(
            cx + label.at.x * domeR - layout.size.width / 2f,
            cy + label.at.y * domeR - layout.size.height / 2f
          )
        )
      }

      // ⑧ 행성: 원 + 이름
      for (planet in c.planetDots) {
        val center = Offset(cx + planet.at.x * domeR, cy + planet.at.y * domeR)
        drawCircle(PlanetColor, radius = 4.dp.toPx(), center = center)
        val layout = textMeasurer.measure(planet.label, TextStyle(fontSize = 18.sp, color = PlanetColor))
        drawText(layout, topLeft = Offset(center.x + 7.dp.toPx(), center.y - layout.size.height / 2f))
      }

      // ⑨ 달: 밝은 원 위에 어두운 원을 phaseDeg에 따라 가로로 밀어 월상을 근사한다
      c.moonDot?.let { moon ->
        drawMoonApprox(
          center = Offset(cx + moon.at.x * domeR, cy + moon.at.y * domeR),
          radiusPx = 9.dp.toPx(),
          phaseDeg = moon.phaseDeg,
          shade = DomeBackground
        )
      }
    }

    if (frame == null) {
      Text("밤하늘 계산 중…", color = CardinalText, fontSize = 20.sp)
    }
  }
}

/** 가운데 차 아이콘: 12×20dp 직사각형(모서리 0) + 앞쪽을 가리키는 작은 삼각형 (SPEC4 3절).
 *  SkyRadar(SPEC5 2절)가 재사용한다 */
internal fun DrawScope.drawCarIcon(cx: Float, cy: Float) {
  val halfW = 6.dp.toPx()
  val halfH = 10.dp.toPx()
  drawRect(
    color = CarIconColor,
    topLeft = Offset(cx - halfW, cy - halfH),
    size = Size(halfW * 2f, halfH * 2f)
  )
  val triangle = Path().apply {
    moveTo(cx - 4.dp.toPx(), cy - halfH)
    lineTo(cx + 4.dp.toPx(), cy - halfH)
    lineTo(cx, cy - halfH - 6.dp.toPx())
    close()
  }
  drawPath(triangle, CarIconColor)
}

internal fun DrawScope.drawCentered(
  measurer: TextMeasurer,
  text: String,
  center: Offset,
  color: Color,
  fontSize: TextUnit
) {
  val layout = measurer.measure(text, TextStyle(fontSize = fontSize, color = color))
  drawText(
    layout,
    topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f)
  )
}
