package com.example.pleoscamping.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleoscamping.mode.CampingMode
import com.example.pleoscamping.ui.theme.PleosAccent
import com.example.pleoscamping.ui.theme.PleosAccentSolid
import com.example.pleoscamping.ui.theme.PleosCard
import com.example.pleoscamping.ui.theme.PleosDanger
import com.example.pleoscamping.ui.theme.PleosFg
import com.example.pleoscamping.ui.theme.PleosLine
import com.example.pleoscamping.ui.theme.PleosMuted
import com.example.pleoscamping.ui.theme.PleosOk
import com.example.pleoscamping.ui.theme.PleosSecondaryButton
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** 음악 조명 모드 패널(SPEC9 4절). 색 조합·시연 음원을 고르고 차 조명 미리보기를 보여 준다 */
@Composable
fun MusicLightPanel(
  state: CampingUiState,
  onColors: (MusicColors) -> Unit = {},
  onSensitivity: (Int) -> Unit,
  // 시연용 음원(SPEC8 5절)
  onDemoStart: (MusicDemo) -> Unit = {},
  onDemoStop: () -> Unit = {},
  // 소리 입력 선택(SPEC7 5절)
  onSource: (MusicSource) -> Unit = {}
) {
  // 드래그 중에는 로컬 값만 움직이고, 놓을 때 Set한다
  var sliderValue by remember(state.musicSensitivity) { mutableFloatStateOf(state.musicSensitivity.toFloat()) }

  // 박자 표시 원: beatCount가 늘면 150ms 동안 PleosAccent로 깜빡인다
  var beatFlash by remember { mutableStateOf(false) }
  LaunchedEffect(state.musicBeatCount) {
    if (state.musicBeatCount > 0) {
      beatFlash = true
      delay(150)
      beatFlash = false
    }
  }
  val beatDotColor by animateColorAsState(
    targetValue = if (beatFlash) PleosAccent else PleosLine,
    animationSpec = tween(durationMillis = 100),
    label = "beatDot"
  )

  // 큰 글자(SPEC9 4절): BPM을 신뢰할 만할 때는 숫자, 아니면 음악 반응 여부를 알린다
  val bpmKnown = state.musicBpm != null && state.musicConfidence >= 0.3f
  val bigText = when {
    bpmKnown -> "%.0f BPM".format(state.musicBpm)
    state.musicLevel > 0.02f -> "음악에 반응 중"
    else -> "음악 대기"
  }
  val demoPlaying = state.musicDemo != null
  val statusText = when {
    demoPlaying -> "시연 음원 재생 중 · ${state.musicDemo.label}"
    state.musicLevel > 0.02f -> "음악에 맞춰 조명이 움직이고 있어요"
    else -> "음악을 틀어 주세요"
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(PleosCard)
      .border(1.dp, PleosLine)
      .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Icon(
        painter = painterResource(CampingMode.MUSIC_LIGHT.iconRes),
        contentDescription = CampingMode.MUSIC_LIGHT.label,
        tint = PleosAccent,
        modifier = Modifier.size(28.dp)
      )
      Text(CampingMode.MUSIC_LIGHT.label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = PleosFg)
      Text(
        // 소스에 따라 안내 문구가 달라진다(SPEC7 5절)
        if (state.musicSource == MusicSource.OUTPUT) "차에서 나오는 음악을 들어요 · 마이크는 쓰지 않아요"
        else "마이크로 차 안 음악을 들어요 · 녹음은 저장하지 않아요",
        fontSize = 18.sp,
        color = PleosMuted,
        modifier = Modifier.weight(1f),
        textAlign = TextAlign.End
      )
    }

    // 큰 글자 + 박자 원
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
      // 문구일 때는 숫자보다 작게 놓아 한 줄에 들어오게 한다
      Text(bigText, fontSize = if (bpmKnown) 64.sp else 40.sp, fontWeight = FontWeight.Bold, color = PleosFg)
      Box(
        modifier = Modifier
          .size(28.dp)
          .clip(CircleShape)
          .background(beatDotColor)
      )
    }
    Text(statusText, fontSize = 22.sp, color = PleosMuted)

    // 조명 미리보기(SPEC8 5절): 검정 바탕 위에 차에 보내는 색·밝기를 펴서 보여 준다
    LightPreview(
      color = state.musicLightColor,
      brightness = state.musicLightBrightness,
      // 오른쪽 아래 색 조합 이름(SPEC9 4절)
      rightLabel = state.musicColors.label
    )

    // 소리 크기 막대: 기존 레벨 바를 8dp로 줄여 미리보기 아래에 둔다
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(8.dp)
        .background(PleosLine)
    ) {
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .fillMaxWidth(state.musicLevel.coerceIn(0f, 1f))
          .background(lerp(PleosOk, PleosDanger, state.musicLevel.coerceIn(0f, 1f)))
      )
    }

    // 색 조합 칩 4개(SPEC9 4절): 색 점 4개 + 이름
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      MusicColors.entries.forEach { colors ->
        val selected = state.musicColors == colors
        Column(
          modifier = Modifier
            .weight(1f)
            .then(if (selected) Modifier.border(2.dp, PleosAccent) else Modifier)
            .clickable { onColors(colors) }
            .heightIn(min = 88.dp)
            .padding(12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            colors.colors.forEach { c ->
              Box(
                modifier = Modifier
                  .size(20.dp)
                  .clip(CircleShape)
                  .background(Color(c))
              )
            }
          }
          Text(colors.label, fontSize = 22.sp, color = PleosFg)
        }
      }
    }

    // 소리 입력 선택(SPEC7 5절): 칩 모양은 시연용 음원 BPM 칩과 같다
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        "음악을 듣는 곳",
        fontSize = 20.sp,
        color = PleosMuted,
        maxLines = 1,
        modifier = Modifier.weight(1f, fill = false)
      )
      MusicSource.entries.forEach { source ->
        Text(
          source.label,
          fontSize = 22.sp,
          color = PleosFg,
          modifier = Modifier
            .then(
              if (state.musicSource == source) Modifier.border(2.dp, PleosAccent)
              else Modifier.border(1.dp, PleosLine)
            )
            .clickable { onSource(source) }
            .padding(horizontal = 20.dp, vertical = 18.dp)
        )
      }
    }

    Text("조명 반응 세기 ${sliderValue.roundToInt()}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PleosFg)
    Slider(
      value = sliderValue,
      onValueChange = { sliderValue = it },
      onValueChangeFinished = { onSensitivity(sliderValue.roundToInt()) },
      valueRange = 0f..100f,
      colors = SliderDefaults.colors(
        thumbColor = PleosAccentSolid,
        activeTrackColor = PleosAccentSolid,
        inactiveTrackColor = PleosLine
      )
    )

    // 시연용 음원(SPEC8 5절): 무료 곡 2개 + 박자음. 재생 중에 다른 곡을 고르면 그 곡으로 다시 시작한다
    run {
      var demoChoice by remember { mutableStateOf(MusicDemo.FAST) }
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          "시연용 음원",
          fontSize = 20.sp,
          color = PleosMuted,
          maxLines = 1,
          modifier = Modifier.weight(1f, fill = false)
        )
        MusicDemo.entries.forEach { demo ->
          Text(
            demo.label,
            fontSize = 22.sp,
            color = PleosFg,
            modifier = Modifier
              .then(
                if (demoChoice == demo) Modifier.border(2.dp, PleosAccent)
                else Modifier.border(1.dp, PleosLine)
              )
              .clickable {
                demoChoice = demo
                // 재생 중에 고르면 새 곡으로 다시 시작한다
                if (demoPlaying) onDemoStart(demo)
              }
              .padding(horizontal = 20.dp, vertical = 18.dp)
          )
        }
        PleosSecondaryButton(
          text = if (demoPlaying) "정지" else "재생",
          onClick = { if (demoPlaying) onDemoStop() else onDemoStart(demoChoice) }
        )
      }
      Text(
        "음원: Lofi Hip Hop Loop(omfgdude) · High-Energy Adventure Theme(SRG774) — CC0, OpenGameArt.org",
        fontSize = 18.sp,
        color = PleosMuted
      )
    }
  }
}
