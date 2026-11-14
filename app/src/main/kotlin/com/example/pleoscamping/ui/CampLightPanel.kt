package com.example.pleoscamping.ui

import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleoscamping.R
import com.example.pleoscamping.mode.CampingMode
import com.example.pleoscamping.ui.theme.PleosAccent
import com.example.pleoscamping.ui.theme.PleosAccentSolid
import com.example.pleoscamping.ui.theme.PleosCard
import com.example.pleoscamping.ui.theme.PleosFg
import com.example.pleoscamping.ui.theme.PleosLine
import com.example.pleoscamping.ui.theme.PleosMuted
import com.example.pleoscamping.ui.theme.PleosOnAccent
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** 캠핑 조명 색 프리셋(SPEC6 2절). note는 선택 시 색 점 줄 아래에 보여 주는 근거 문구 */
data class ColorPreset(
  val label: String,
  val color: Int,
  val note: String,
  val soundLabel: String,
  @RawRes val soundRes: Int,
  val soundCredit: String
)

/** 프리셋 색(ARGB). ViewModel의 자동 전환·밝기 동반 Set이 같은 값을 참조한다 */
internal val CAMP_COLOR_LANTERN = 0xFFFFC24D.toInt()
internal val CAMP_COLOR_REST_BLUE = 0xFF5B7CFA.toInt()
internal val CAMP_COLOR_RED = 0xFFC8261E.toInt()

/** 취침 레드를 고를 때 함께 적용하는 밝기 */
internal const val CAMP_RED_BRIGHTNESS = 15

// ARGB 상수: 알파 FF가 없는 6자리 리터럴은 Color(Int)에서 완전 투명이 된다
internal val COLOR_PRESETS = listOf(
  ColorPreset(
    "모닥불", 0xFFFF6A1A.toInt(),
    "불빛을 소리와 함께 15분 바라보면 혈압이 평균 약 6mmHg 내려갔어요(Lynn, 2014). 따뜻한 빛이라 잠도 덜 방해해요. 불빛이 일렁여요.",
    "장작 소리", R.raw.fire_crackle,
    "장작 소리: Fireplace Sound loop(PagDev) — CC0, OpenGameArt.org"
  ),
  ColorPreset(
    "랜턴", CAMP_COLOR_LANTERN,
    "색온도가 낮은 따뜻한 빛은 수면 호르몬(멜라토닌)을 덜 억제해요.",
    "풀벌레 소리", R.raw.night_crickets,
    "풀벌레 소리: Crickets Ambient Noise(Wolfgang_) — CC0, OpenGameArt.org"
  ),
  ColorPreset(
    "노을", 0xFFFF4D6D.toInt(),
    "해 질 녘 하늘빛으로 분위기를 내는 색이에요.",
    "파도 소리", R.raw.waves,
    "파도 소리: Sea and river wave sounds(RandomMind) — CC0, OpenGameArt.org"
  ),
  ColorPreset(
    "휴식 블루", CAMP_COLOR_REST_BLUE,
    "운전 뒤 긴장을 빨리 풀어 줘요. 파란 조명에서 스트레스 뒤 이완이 약 3배 빨랐어요(Minguillon, 2017). 잠을 방해하므로 10분 뒤 랜턴 색으로 바뀌어요. 천천히 숨쉬듯 밝아졌다 어두워져요.",
    "빗소리", R.raw.rain,
    "빗소리: Rain (loopable)(Ylmir) — CC0, OpenGameArt.org"
  ),
  ColorPreset(
    "취침 레드", CAMP_COLOR_RED,
    "잠들기 전이나 한밤중에. 어두운 붉은 빛은 눈부심과 수면 방해가 가장 적어요.",
    "바람 소리", R.raw.wind,
    "바람 소리: wind1(Luke.RUSTLTD) — CC0, OpenGameArt.org"
  )
)

/** 색 값으로 프리셋을 찾는다(알파 무시). 없으면 null */
internal fun presetForColor(color: Int): ColorPreset? = COLOR_PRESETS.firstOrNull { (it.color and 0xFFFFFF) == (color and 0xFFFFFF) }

/** 캠핑 조명 모드 패널 */
@Composable
fun CampLightPanel(
  state: CampingUiState,
  onColor: (Int) -> Unit,
  onBrightness: (Int) -> Unit,
  // 장작 소리 토글(SPEC10 3절)
  onFireSound: (Boolean) -> Unit = {}
) {
  // 드래그 중에는 로컬 값만 움직이고, 놓을 때 Set한다
  var sliderValue by remember(state.campBrightness) { mutableFloatStateOf(state.campBrightness.toFloat()) }

  // 휴식 블루 남은 시간 표시를 위한 1초 tick
  var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(state.restBlueDeadline) {
    if (state.restBlueDeadline != null) {
      while (true) {
        nowMillis = System.currentTimeMillis()
        delay(1_000)
      }
    }
  }

  // 기존 저장값은 알파 0인 6자리 값일 수 있으므로 RGB 24bit만 비교한다
  val selectedPreset = COLOR_PRESETS.firstOrNull { (it.color and 0xFFFFFF) == (state.campColor and 0xFFFFFF) }

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
        painter = painterResource(CampingMode.CAMP_LIGHT.iconRes),
        contentDescription = CampingMode.CAMP_LIGHT.label,
        tint = PleosAccent,
        modifier = Modifier.size(28.dp)
      )
      Text(CampingMode.CAMP_LIGHT.label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = PleosFg)
    }

    // 조명 미리보기(SPEC10 3절): 차에 보내는 색·밝기와 고른 프리셋 이름을 보여 준다
    LightPreview(
      color = state.campLightColor,
      brightness = state.campLightBrightness,
      rightLabel = selectedPreset?.label ?: ""
    )

    Text("색 프리셋", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PleosFg)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      COLOR_PRESETS.forEach { preset ->
        val selected = preset === selectedPreset
        Column(
          modifier = Modifier
            .widthIn(min = 96.dp)
            .heightIn(min = 96.dp)
            .clickable { onColor(preset.color) },
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Box(
            modifier = Modifier
              .size(72.dp) // 64dp 동그라미 + 바깥 4dp 테두리 자리
              .then(
                if (selected) {
                  Modifier.border(4.dp, PleosFg, CircleShape)
                } else {
                  Modifier
                }
              )
              .padding(4.dp)
              .size(64.dp)
              .clip(CircleShape)
              .background(Color(preset.color))
          )
          Text(preset.label, fontSize = 22.sp, color = PleosFg)
        }
      }
    }
    // 선택된 프리셋의 근거 문구(세 줄까지)
    if (selectedPreset != null) {
      Text(selectedPreset.note, fontSize = 20.sp, color = PleosMuted, maxLines = 3)
    }
    // 고른 색에 맞는 소리 줄(SPEC16)
    if (selectedPreset != null) {
      LightToggle(label = selectedPreset.soundLabel, checked = state.campFireSound, onChange = onFireSound)
      Text(selectedPreset.soundCredit, fontSize = 18.sp, color = PleosMuted)
    }
    // 휴식 블루 자동 전환 남은 시간
    if (selectedPreset?.color == CAMP_COLOR_REST_BLUE && state.restBlueDeadline != null) {
      val remainMs = (state.restBlueDeadline - nowMillis).coerceAtLeast(0L)
      Text(
        "남은 시간 ${remainMs / 60_000}:${"%02d".format(remainMs % 60_000 / 1_000)}",
        fontSize = 20.sp,
        color = PleosMuted
      )
    }

    Text("밝기 ${sliderValue.roundToInt()}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PleosFg)
    Slider(
      value = sliderValue,
      onValueChange = { sliderValue = it },
      onValueChangeFinished = { onBrightness(sliderValue.roundToInt()) },
      valueRange = 0f..100f,
      colors = SliderDefaults.colors(
        thumbColor = PleosAccentSolid,
        activeTrackColor = PleosAccentSolid,
        inactiveTrackColor = PleosLine
      )
    )

  }
}

@Composable
private fun LightToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
