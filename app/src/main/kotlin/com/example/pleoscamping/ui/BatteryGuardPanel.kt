package com.example.pleoscamping.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleoscamping.mode.BatteryGuard
import com.example.pleoscamping.mode.CampingMode
import com.example.pleoscamping.ui.theme.PleosAccent
import com.example.pleoscamping.ui.theme.PleosAccentSolid
import com.example.pleoscamping.ui.theme.PleosCard
import com.example.pleoscamping.ui.theme.PleosDanger
import com.example.pleoscamping.ui.theme.PleosFg
import com.example.pleoscamping.ui.theme.PleosLine
import com.example.pleoscamping.ui.theme.PleosMuted
import com.example.pleoscamping.ui.theme.PleosOk
import com.example.pleoscamping.ui.theme.PleosOnAccent
import com.example.pleoscamping.ui.theme.PleosRaised
import com.example.pleoscamping.ui.theme.PleosSave
import com.example.pleoscamping.ui.theme.PleosSecondaryButton
import com.example.pleoscamping.ui.theme.PleosWarn
import kotlin.math.roundToInt

// 단계 배지 색: 정상 / 절약 / 경계 / 위험 (SPEC_UI 6장, SPEC_DARK 3절)
private val STAGE_COLORS = arrayOf(
  PleosOk, // 정상
  PleosSave, // 절약
  PleosWarn, // 경계
  PleosDanger // 위험
)

/** 배터리 가드 모드 패널 */
@Composable
fun BatteryGuardPanel(
  state: CampingUiState,
  onSaveHome: () -> Unit,
  onResetHome: () -> Unit,
  onAllowHvacOff: (Boolean) -> Unit
) {
  val result = state.guardResult
  val stage = result?.stage ?: 0
  val stageColor = STAGE_COLORS[stage.coerceIn(0, 3)]
  val vehicleEfficiency = state.guardVehicleEfficiency

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(PleosCard)
      .border(1.dp, PleosLine)
      .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Icon(
        painter = painterResource(CampingMode.BATTERY_GUARD.iconRes),
        contentDescription = CampingMode.BATTERY_GUARD.label,
        tint = PleosAccent,
        modifier = Modifier.size(28.dp)
      )
      Text(CampingMode.BATTERY_GUARD.label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = PleosFg)
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      BigMetric(modifier = Modifier.weight(1f), label = "배터리", value = state.batteryPercent?.let { "$it%" } ?: "--")
      BigMetric(modifier = Modifier.weight(1f), label = "귀가 필요", value = result?.requiredPercent?.let { "${it.roundToInt()}%" } ?: "--")
      BigMetric(
        modifier = Modifier.weight(1f),
        label = "여유",
        value = result?.headroomPercent?.let { "${it.roundToInt()}%" } ?: "--",
        valueColor = stageColor
      )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      Box(
        modifier = Modifier
          .background(stageColor)
          .padding(horizontal = 16.dp, vertical = 6.dp)
      ) {
        Text(BatteryGuard.stageName(stage), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PleosOnAccent)
      }
      Text(BatteryGuard.stageMessage(stage), fontSize = 22.sp, color = PleosFg)
    }

    Text("집까지 약 ${state.guardHomeKm} km", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PleosFg)
    Text(
      text = when {
        state.usingDefaultLocation -> "현재 위치를 알 수 없어 ${state.guardHomeKm} km로 계산했어요."
        state.guardHomeSaved -> "현재 위치에서 저장한 집까지 · 직선거리에 1.3을 곱한 어림값"
        else -> "현재 위치에서 서울(기본값)까지 · 직선거리에 1.3을 곱한 어림값"
      },
      fontSize = 20.sp,
      color = PleosMuted
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      PleosSecondaryButton(text = "지금 위치를 집으로 저장", onClick = onSaveHome, modifier = Modifier.weight(1f))
      PleosSecondaryButton(text = "기본값(서울)으로", onClick = onResetHome, modifier = Modifier.weight(1f))
    }

    val eff = vehicleEfficiency ?: state.guardEfficiency
    Text(
      "여유 배터리 ${state.guardMarginPct}% · 전비 ${"%.1f".format(eff)} km/kWh(${if (vehicleEfficiency != null) "차량 값" else "기본값"})",
      fontSize = 20.sp,
      color = PleosMuted
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 72.dp)
        .toggleable(value = state.guardAllowHvacOff, onValueChange = onAllowHvacOff, role = Role.Switch),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text("공조 끄기 허용", fontSize = 22.sp, color = PleosFg)
      Switch(
        checked = state.guardAllowHvacOff,
        onCheckedChange = null,
        colors = SwitchDefaults.colors(
          checkedTrackColor = PleosAccentSolid,
          checkedThumbColor = PleosOnAccent,
          uncheckedTrackColor = PleosLine,
          uncheckedThumbColor = PleosFg,
          uncheckedBorderColor = Color.Transparent
        )
      )
    }

    val c = state.guardComfort
    Text(
      "현재 공조: 전원 ${onOff(c?.hvacPower)} · 자동 ${onOff(c?.hvacAuto)} · 에어컨 ${onOff(c?.hvacAc)} · " +
        "운전석 ${c?.tempDriver?.let { "%.1f".format(it) + "°C" } ?: "--"} · 바람 ${c?.fanSpeed ?: "--"}",
      fontSize = 22.sp,
      color = PleosFg
    )
  }
}

@Composable
private fun BigMetric(modifier: Modifier, label: String, value: String, valueColor: Color? = null) {
  Column(
    modifier = modifier.background(PleosRaised).padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Text(label, fontSize = 22.sp, color = PleosMuted)
    Text(value, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = valueColor ?: PleosFg)
  }
}

private fun onOff(value: Boolean?): String = when (value) {
  true -> "켬"
  false -> "끔"
  null -> "--"
}
