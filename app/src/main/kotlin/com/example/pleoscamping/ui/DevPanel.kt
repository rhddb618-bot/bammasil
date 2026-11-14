package com.example.pleoscamping.ui

import ai.pleos.playground.vehicle.constant.control.MoveControl
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleoscamping.ui.theme.PleosAccent
import com.example.pleoscamping.ui.theme.PleosButtonAlt
import com.example.pleoscamping.ui.theme.PleosCard
import com.example.pleoscamping.ui.theme.PleosFg
import com.example.pleoscamping.ui.theme.PleosLine
import com.example.pleoscamping.ui.theme.PleosMuted
import com.example.pleoscamping.ui.theme.PleosSecondaryButton
import com.example.pleoscamping.vehicle.DRIVER_AREA
import com.example.pleoscamping.vehicle.READING_LEFT_AREA
import com.example.pleoscamping.vehicle.VehicleGateway
import kotlin.math.roundToInt

/** 개발 시험 카드용 현재 값. 읽기 실패 항목은 null */
data class DevComfort(
  val hvacPower: Boolean?,
  val hvacAuto: Boolean?,
  val hvacAc: Boolean?,
  val tempDriver: Float?,
  val fanSpeed: Int?,
  val backrestLeft: Int?,
  val backrestRight: Int?
)

/**
 * 개발 시험 카드(DEBUG 전용). 주행 중 공조·시트 쓰기 정책을 실측한다.
 * 모드 상태와 무관하게 동작하며 스냅샷은 만들지 않는다.
 */
@Composable
fun DevPanel(
  state: CampingUiState,
  manualHeadingDeg: Float?,
  onManualHeading: (Float?) -> Unit,
  onRefresh: () -> Unit,
  onDrivingTest: () -> Unit,
  onAction: (label: String, action: suspend VehicleGateway.() -> Unit) -> Unit
) {
  val d = state.devComfort
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RectangleShape,
    colors = CardDefaults.cardColors(containerColor = PleosCard),
    elevation = CardDefaults.cardElevation(0.dp),
    border = BorderStroke(1.dp, PleosLine)
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("개발 시험", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PleosFg)
        Text("DEBUG 빌드 전용", fontSize = 22.sp, color = PleosMuted)
      }
      Text(
        "공조 전원 ${onOff(d?.hvacPower)} · AUTO ${onOff(d?.hvacAuto)} · AC ${onOff(d?.hvacAc)} · " +
          "운전석 ${d?.tempDriver?.let { "%.1f".format(it) } ?: "--"} · 팬 ${d?.fanSpeed ?: "--"} · " +
          "등받이 좌 ${d?.backrestLeft ?: "--"}/우 ${d?.backrestRight ?: "--"}",
        fontSize = 22.sp,
        color = PleosFg
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PleosSecondaryButton(
          text = "공조 전원 토글",
          onClick = { onAction("공조 전원 토글") { setHvacPower(!(d?.hvacPower ?: true)) } },
          modifier = Modifier.weight(1f)
        )
        PleosSecondaryButton(
          text = "AUTO 토글",
          onClick = { onAction("AUTO 토글") { setHvacAuto(!(d?.hvacAuto ?: false)) } },
          modifier = Modifier.weight(1f)
        )
        PleosSecondaryButton(
          text = "AC 토글",
          onClick = { onAction("AC 토글") { setHvacAc(!(d?.hvacAc ?: false)) } },
          modifier = Modifier.weight(1f)
        )
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PleosSecondaryButton(
          text = "운전석 온도 −0.5",
          onClick = { onAction("운전석 온도 −0.5") { setHvacTemperature(DRIVER_AREA, stepTemp(d?.tempDriver, -0.5f)) } },
          modifier = Modifier.weight(1f)
        )
        PleosSecondaryButton(
          text = "운전석 온도 +0.5",
          onClick = { onAction("운전석 온도 +0.5") { setHvacTemperature(DRIVER_AREA, stepTemp(d?.tempDriver, +0.5f)) } },
          modifier = Modifier.weight(1f)
        )
        PleosSecondaryButton(
          text = "팬 −1",
          onClick = { onAction("팬 −1") { setHvacFanSpeed(((d?.fanSpeed ?: 3) - 1).coerceIn(1, 8)) } },
          modifier = Modifier.weight(1f)
        )
        PleosSecondaryButton(
          text = "팬 +1",
          onClick = { onAction("팬 +1") { setHvacFanSpeed(((d?.fanSpeed ?: 3) + 1).coerceIn(1, 8)) } },
          modifier = Modifier.weight(1f)
        )
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PleosSecondaryButton(
          text = "등받이 2열 좌 앞으로",
          onClick = { onAction("등받이 2열 좌 앞으로") { moveBackrest(READING_LEFT_AREA, MoveControl.FORWARD) } },
          modifier = Modifier.weight(1f)
        )
        PleosSecondaryButton(
          text = "등받이 2열 좌 정지",
          onClick = { onAction("등받이 2열 좌 정지") { moveBackrest(READING_LEFT_AREA, MoveControl.STOP) } },
          modifier = Modifier.weight(1f)
        )
        PleosSecondaryButton(
          text = "등받이 2열 좌 뒤로",
          onClick = { onAction("등받이 2열 좌 뒤로") { moveBackrest(READING_LEFT_AREA, MoveControl.BACKWARD) } },
          modifier = Modifier.weight(1f)
        )
      }
      PleosSecondaryButton(
        text = "주행 시험(6초 뒤 실행)",
        onClick = onDrivingTest,
        modifier = Modifier.fillMaxWidth()
      )
      PleosSecondaryButton(
        text = "새로 읽기",
        onClick = onRefresh,
        modifier = Modifier.fillMaxWidth()
      )
      Text("별 보기 방향(시험용)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PleosFg)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DevHeadingButton("앞쪽=북", selected = manualHeadingDeg == 0f,
          onClick = { onManualHeading(0f) }, modifier = Modifier.weight(1f))
        DevHeadingButton("앞쪽=동", selected = manualHeadingDeg == 90f,
          onClick = { onManualHeading(90f) }, modifier = Modifier.weight(1f))
        DevHeadingButton("앞쪽=남", selected = manualHeadingDeg == 180f,
          onClick = { onManualHeading(180f) }, modifier = Modifier.weight(1f))
        DevHeadingButton("앞쪽=서", selected = manualHeadingDeg == 270f,
          onClick = { onManualHeading(270f) }, modifier = Modifier.weight(1f))
        DevHeadingButton("자동", selected = manualHeadingDeg == null,
          onClick = { onManualHeading(null) }, modifier = Modifier.weight(1f))
      }
    }
  }
}

/** 별 보기 방향 선택 보조 버튼: 선택된 것은 테두리 2dp PleosAccent */
@Composable
private fun DevHeadingButton(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  OutlinedButton(
    onClick = onClick,
    modifier = modifier.height(72.dp),
    shape = RectangleShape,
    contentPadding = PaddingValues(horizontal = 4.dp),
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = PleosButtonAlt,
      contentColor = PleosFg
    ),
    border = BorderStroke(2.dp, if (selected) PleosAccent else Color.Transparent)
  ) {
    Text(text, fontSize = 22.sp, maxLines = 1, softWrap = false)
  }
}

private fun onOff(value: Boolean?): String = when (value) {
  true -> "ON"
  false -> "OFF"
  null -> "--"
}

/** 온도를 0.5 단위로 옮기고 허용 범위(15~30)에 맞춘다 */
private fun stepTemp(current: Float?, delta: Float): Float {
  val base = current ?: 22.0f
  val stepped = ((base + delta) * 2).roundToInt() / 2.0f
  return stepped.coerceIn(15.0f, 30.0f)
}
