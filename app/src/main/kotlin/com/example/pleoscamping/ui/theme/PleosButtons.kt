package com.example.pleoscamping.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pleos 공통 버튼 두 가지 (SPEC_UI 3장, SPEC_DARK 3절).
 * 모두 높이 72dp, 직각(RectangleShape), 그림자 없음.
 * Primary: 채움 PleosAccentSolid / PleosOnAccent 글자, 비활성 PleosRaised / PleosMuted.
 * Secondary: 채움 PleosRaised / PleosFg 글자 / 1dp PleosLine 테두리, 비활성 글자 PleosMuted.
 */

/** 주 버튼(끄기, 재생, 알람 설정, 일어났어요, 주행 시험): 채움 PleosAccentSolid, PleosOnAccent 글자 */
@Composable
fun PleosPrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.height(72.dp),
    shape = RectangleShape,
    colors = ButtonDefaults.buttonColors(
      containerColor = PleosAccentSolid,
      contentColor = PleosOnAccent,
      disabledContainerColor = PleosRaised,
      disabledContentColor = PleosMuted
    )
  ) {
    Text(text, fontSize = 24.sp)
  }
}

/** 보조 버튼(켜기, 원래대로, 정지, 알람 취소, −/+, 곡 선택, 새로 읽기, 개발 시험): 채움 PleosRaised, PleosFg 글자, 1dp PleosLine 테두리 */
@Composable
fun PleosSecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.height(72.dp),
    shape = RectangleShape,
    colors = ButtonDefaults.buttonColors(
      containerColor = PleosRaised,
      contentColor = PleosFg,
      disabledContainerColor = PleosRaised,
      disabledContentColor = PleosMuted
    ),
    border = BorderStroke(1.dp, PleosLine)
  ) {
    Text(text, fontSize = 24.sp)
  }
}
