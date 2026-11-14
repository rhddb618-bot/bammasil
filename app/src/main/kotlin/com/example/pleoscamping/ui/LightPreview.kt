package com.example.pleoscamping.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleoscamping.ui.theme.PleosFg
import com.example.pleoscamping.ui.theme.PleosLine

/** 차 조명 미리보기 칸(SPEC10 2절). 검정 바탕 위에 차에 보내는 색·밝기를 펴서 보여 준다. color가 null이면 꺼진 모양 */
@Composable
fun LightPreview(color: Int?, brightness: Int, rightLabel: String, modifier: Modifier = Modifier) {
  val previewColor = color?.let { Color(it) } ?: PleosLine
  val previewAlpha = 0.15f + 0.85f * brightness / 100f
  val animatedColor by animateColorAsState(
    targetValue = previewColor,
    animationSpec = tween(durationMillis = 180),
    label = "previewColor"
  )
  val animatedAlpha by animateFloatAsState(
    targetValue = previewAlpha,
    animationSpec = tween(durationMillis = 180),
    label = "previewAlpha"
  )
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(160.dp)
      .background(Color.Black)
      .border(1.dp, PleosLine)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(animatedColor.copy(alpha = animatedAlpha))
    )
    Text(
      "차 조명 미리보기 · 밝기 $brightness",
      fontSize = 18.sp,
      color = PleosFg,
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(12.dp)
    )
    Text(
      rightLabel,
      fontSize = 18.sp,
      color = PleosFg,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(12.dp)
    )
  }
}
