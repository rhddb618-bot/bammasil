package com.example.pleoscamping.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleoscamping.astro.SkyFrame
import com.example.pleoscamping.ui.theme.PleosAccent
import com.example.pleoscamping.ui.theme.SkyStar3
import kotlin.math.min

private val RadarFanFill = PleosAccent.copy(alpha = 0.18f)

/**
 * 미니 레이더 (SPEC5 2절, SPEC_DARK 3절). 160dp 정사각.
 * SkyDomeView와 같은 등거리 방위 투영(차 앞쪽 위, 오른쪽 = 조수석)으로 등급 ≤ 3.5 별만 점으로 찍고,
 * 유리 뷰가 보고 있는 범위(시선 방위 ± fov/2)를 부채꼴로 표시한다. 글자는 "앞"만.
 * 부채꼴은 PleosAccent 18% 채움 + 1dp 테두리, 별 점은 SkyStar3.
 */
@Composable
fun SkyRadar(frame: SkyFrame?, headingDeg: Float, view: SkyView, modifier: Modifier = Modifier) {
  val textMeasurer = rememberTextMeasurer()
  Box(modifier = modifier.size(160.dp)) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val cx = size.width / 2f
      val cy = size.height / 2f
      val r = min(size.width, size.height) / 2f - 14.dp.toPx()

      drawCircle(DomeBackground, radius = r, center = Offset(cx, cy))

      // 보는 범위 부채꼴: 시선 방위를 차 기준 상대 방위로 바꿔 원점에서 지평선까지
      val relCenter = ((view.azDeg - headingDeg) % 360f + 360f) % 360f
      val halfFov = view.fovDeg / 2f
      val fan = Path().apply {
        moveTo(cx, cy)
        arcTo(
          rect = Rect(cx - r, cy - r, cx + r, cy + r),
          startAngleDegrees = relCenter - halfFov - 90f,
          sweepAngleDegrees = halfFov * 2f,
          forceMoveTo = false
        )
        close()
      }
      drawPath(fan, RadarFanFill)
      drawPath(fan, PleosAccent, style = Stroke(width = 1.dp.toPx()))

      // 등급 ≤ 3.5 별만 1dp 점
      if (frame != null) {
        for (placed in frame.stars) {
          if (placed.star.mag > 3.5) continue
          if (placed.p.altDeg < 0.0) continue
          val n = project(placed.p, headingDeg)
          drawCircle(SkyStar3, radius = 1.dp.toPx(), center = Offset(cx + n.x * r, cy + n.y * r))
        }
      }

      // 지평선 원 + 차 아이콘 + "앞"
      drawCircle(DomeBorder, radius = r, center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
      drawCarIcon(cx, cy)
      drawCentered(textMeasurer, "앞", Offset(cx, cy - r - 8.dp.toPx()), PleosAccent, 12.sp)
    }
  }
}
