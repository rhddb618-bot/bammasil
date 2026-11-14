package com.example.pleoscamping.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleoscamping.mode.CampingMode
import com.example.pleoscamping.ui.theme.PleosAccent
import com.example.pleoscamping.ui.theme.PleosAccentSoft
import com.example.pleoscamping.ui.theme.PleosAccentSolid
import com.example.pleoscamping.ui.theme.PleosCard
import com.example.pleoscamping.ui.theme.PleosFg
import com.example.pleoscamping.ui.theme.PleosLine
import com.example.pleoscamping.ui.theme.PleosMuted
import com.example.pleoscamping.ui.theme.PleosOnAccent
import com.example.pleoscamping.ui.theme.PleosPrimaryButton
import com.example.pleoscamping.ui.theme.PleosRaised
import com.example.pleoscamping.ui.theme.PleosSecondaryButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlinx.coroutines.delay

/** 일출 알람 모드 패널 */
@Composable
fun SunrisePanel(
  state: CampingUiState,
  onTimeChange: (hour: Int, minute: Int) -> Unit,
  onUseSunrise: () -> Unit,
  onPreHvac: (Boolean) -> Unit,
  onSeatUp: (Boolean) -> Unit,
  onSound: (Boolean) -> Unit,
  onKeepHvac: (Boolean) -> Unit,
  onArm: () -> Unit,
  onDemo: () -> Unit,
  onCancel: () -> Unit,
  onWokeUp: () -> Unit
) {
  // 남은 시간 표시를 위한 1초 tick
  var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      nowMillis = System.currentTimeMillis()
      delay(1_000)
    }
  }

  val armed = state.sunriseArmed
  val ringing = state.sunriseRinging
  val timeEditable = !armed && !ringing

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
        painter = painterResource(CampingMode.SUNRISE_ALARM.iconRes),
        contentDescription = CampingMode.SUNRISE_ALARM.label,
        tint = PleosAccent,
        modifier = Modifier.size(28.dp)
      )
      Text(CampingMode.SUNRISE_ALARM.label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = PleosFg)
    }

    // 알람 시각: 시 [−][+], 분 [−][+](5분 단위)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Text("알람 시각", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PleosFg)
      PleosSecondaryButton(
        text = "−",
        onClick = { onTimeChange((state.sunriseHour + 23) % 24, state.sunriseMinute) },
        modifier = Modifier.size(72.dp, 72.dp),
        enabled = timeEditable
      )
      Text("%02d".format(state.sunriseHour), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = PleosFg)
      PleosSecondaryButton(
        text = "+",
        onClick = { onTimeChange((state.sunriseHour + 1) % 24, state.sunriseMinute) },
        modifier = Modifier.size(72.dp, 72.dp),
        enabled = timeEditable
      )
      Text("시", fontSize = 24.sp, color = PleosFg)
      PleosSecondaryButton(
        text = "−",
        onClick = {
          val total = (state.sunriseHour * 60 + state.sunriseMinute - 5).mod(24 * 60)
          onTimeChange(total / 60, total % 60)
        },
        modifier = Modifier.size(72.dp, 72.dp),
        enabled = timeEditable
      )
      Text("%02d".format(state.sunriseMinute), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = PleosFg)
      PleosSecondaryButton(
        text = "+",
        onClick = {
          val total = (state.sunriseHour * 60 + state.sunriseMinute + 5).mod(24 * 60)
          onTimeChange(total / 60, total % 60)
        },
        modifier = Modifier.size(72.dp, 72.dp),
        enabled = timeEditable
      )
      Text("분", fontSize = 24.sp, color = PleosFg)
      // 추천 시간 칩: 추천이면 PleosAccent 글자 + 1dp 테두리, 직접 고르면 PleosMuted
      if (state.sunriseIsRecommended) {
        Text(
          "추천 시간",
          fontSize = 20.sp,
          color = PleosAccent,
          modifier = Modifier
            .border(1.dp, PleosAccent)
            .padding(horizontal = 10.dp, vertical = 4.dp)
        )
      } else {
        Text("직접 설정", fontSize = 20.sp, color = PleosMuted)
      }
    }

    // 추천 근거(세 줄까지). 계산이 안 되는 위치면 안내 문구로 대체한다
    Text(sunriseReason(state), fontSize = 20.sp, color = PleosMuted, maxLines = 3)

    PleosSecondaryButton(
      text = "추천 시간으로",
      onClick = onUseSunrise,
      modifier = Modifier.fillMaxWidth(),
      enabled = timeEditable
    )

    SunriseToggle("공조 미리 켜기(22°C)", state.sunrisePreHvac, onPreHvac)
    SunriseToggle("등받이 세우기(2열 좌·우)", state.sunriseSeatUp, onSeatUp)
    SunriseToggle("소리 알람", state.sunriseSound, onSound)
    SunriseToggle("깬 뒤 공조 유지", state.sunriseKeepHvac, onKeepHvac)

    // 상태 줄: 진행 중엔 현재 단계, 대기 중엔 알람 시각과 남은 시간
    val statusLine = when {
      ringing -> state.sunriseStatus ?: "알람 울림"
      state.sunriseStatus != null -> state.sunriseStatus
      armed -> {
        val remainMs = ((state.sunriseTargetMillis ?: nowMillis) - nowMillis).coerceAtLeast(0)
        if (state.sunriseDemo) {
          "알람 ${"%02d:%02d".format(state.sunriseHour, state.sunriseMinute)} · 남은 ${remainMs / 60_000}분 ${(remainMs % 60_000) / 1000}초"
        } else {
          "알람 ${"%02d:%02d".format(state.sunriseHour, state.sunriseMinute)} · 남은 시간 ${remainMs / 3_600_000}시간 ${remainMs % 3_600_000 / 60_000}분"
        }
      }
      else -> "알람을 설정하면 시간표대로 깨워 드려요"
    }
    Text(statusLine, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PleosFg)
    SunriseTimeline(status = state.sunriseStatus, ringing = ringing)

    when {
      ringing -> {
        PleosPrimaryButton(
          text = "일어났어요",
          onClick = onWokeUp,
          modifier = Modifier.fillMaxWidth()
        )
      }
      armed -> {
        PleosSecondaryButton(
          text = "알람 취소",
          onClick = onCancel,
          modifier = Modifier.fillMaxWidth()
        )
      }
      else -> {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
          PleosPrimaryButton(
            text = "알람 설정",
            onClick = onArm,
            modifier = Modifier.weight(1f)
          )
          PleosSecondaryButton(
            text = "3분 시연",
            onClick = onDemo,
            modifier = Modifier.weight(1f)
          )
        }
      }
    }

    Text("시동을 켠 채로 이 화면을 켜 두어야 해요. 시동을 끄면 앱이 멈춰요.", fontSize = 22.sp, color = PleosMuted)
  }
}

/**
 * 추천 시간 근거 문구(SPEC6 3절). 좌표·시각은 실제 계산 값이고
 * 위치가 기본값이면 "기본 위치(서울)"로 표시한다. 계산 실패(극지 등)면 안내 문구.
 */
private fun sunriseReason(state: CampingUiState): String {
  val civil = state.sunriseRecommendedMillis
  val sunrise = state.sunriseActualMillis
  if (civil == null || sunrise == null) {
    return "이 위치에서는 일출 시각을 계산할 수 없어요"
  }
  val zone = ZoneId.systemDefault()
  val civilTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(civil), zone)
  val sunriseTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(sunrise), zone)
  val location = if (state.usingDefaultLocation) {
    "기본 위치(서울)"
  } else {
    val ns = if (state.latitude >= 0) "N" else "S"
    val ew = if (state.longitude >= 0) "E" else "W"
    "현재 위치(%.2f°%s, %.2f°%s)".format(abs(state.latitude), ns, abs(state.longitude), ew)
  }
  val dayWord = if (sunriseTime.toLocalDate() == LocalDate.now(zone)) "오늘" else "내일"
  return "추천 근거: $location $dayWord 일출 " +
    "%02d:%02d".format(sunriseTime.hour, sunriseTime.minute) +
    ". 밖이 밝아지기 시작하는 " +
    "%02d:%02d".format(civilTime.hour, civilTime.minute) +
    "에 일어나면 준비하고 일출을 볼 수 있어요."
}

/**
 * 시간표 4칸(30분 전/10분 전/3분 전/알람 시각). 폭이 좁으면 2×2로 놓는다.
 * 현재 칸은 sunriseStatus 문구 매칭으로만 정한다(상태 로직 추가 없음).
 */
@Composable
private fun SunriseTimeline(status: String?, ringing: Boolean) {
  val labels = listOf("30분 전", "10분 전", "3분 전", "알람 시각")
  val descriptions = listOf("무드 조명 밝히기", "공조 켜기 · 22°C", "등받이 세우기", "실내등 · 알람")
  val current = when {
    ringing -> 3
    status == null -> -1
    status.startsWith("조명 밝히는 중") -> 0
    status.startsWith("공조 켬") -> 1
    status.startsWith("등받이 세움") -> 2
    status.startsWith("알람 울림") -> 3
    else -> -1
  }
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val rows = if (maxWidth >= 480.dp) listOf(labels) else labels.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      rows.forEach { rowLabels ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          rowLabels.forEach { label ->
            val index = labels.indexOf(label)
            val active = index == current
            Box(
              modifier = Modifier
                .weight(1f)
                .background(if (active) PleosAccentSoft else PleosRaised)
                .then(if (active) Modifier.border(1.dp, PleosAccent) else Modifier)
                .padding(vertical = 12.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(label, fontSize = 22.sp, color = PleosFg)
                Text(descriptions[index], fontSize = 18.sp, color = PleosMuted)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SunriseToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 72.dp)
      .toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(label, fontSize = 22.sp, color = PleosFg)
    Switch(
      checked = checked,
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
}
