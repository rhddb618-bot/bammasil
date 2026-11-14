package com.example.pleoscamping.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pleoscamping.ui.theme.PleosCard
import com.example.pleoscamping.ui.theme.PleosFg
import com.example.pleoscamping.ui.theme.PleosMuted
import com.example.pleoscamping.ui.theme.PleosPrimaryButton

/**
 * 권한을 묻는 시스템 창을 띄우기 직전에 보여 주는 안내 창(SPEC14).
 * 바깥을 누르거나 뒤로 가기로 닫히지 않고 [확인]만 있다.
 */
@Composable
fun PermissionNoticeDialog(notice: PermissionNotice, musicSource: MusicSource, onConfirm: () -> Unit) {
  val title: String
  val lines: List<String>
  val mutedLine: String
  when (notice) {
    PermissionNotice.LOCATION -> {
      title = "권한 창이 곧 떠요"
      lines = listOf(
        "아래 두 가지를 물어요. 허용해 주세요.",
        "• 위치: 지금 있는 곳의 밤하늘을 계산해요.",
        "• '알 수 없는 작업': 차가 바라보는 방향을 읽어요.",
        "   Pleos 차량 권한이라 이름이 이렇게 보여요."
      )
      mutedLine = "허용하지 않아도 앱은 쓸 수 있지만 별 보기의 위치와 방향이 정확하지 않아요."
    }
    PermissionNotice.AUDIO -> {
      title = "오디오 권한 창이 곧 떠요"
      lines = listOf(
        if (musicSource == MusicSource.OUTPUT) {
          "음악 조명은 차 스피커에서 나오는 음악을 읽어 박자에 맞춰 조명을 바꿔요."
        } else {
          "음악 조명은 마이크로 들리는 음악의 박자에 맞춰 조명을 바꿔요."
        },
        "안드로이드는 이것을 '오디오 녹음' 권한으로 물어요. 허용해 주세요."
      )
      mutedLine = "소리는 박자를 찾는 데만 쓰고 녹음해 저장하지 않아요."
    }
  }

  Dialog(
    onDismissRequest = {},
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = false,
      dismissOnClickOutside = false
    )
  ) {
    Column(
      Modifier.width(900.dp).background(PleosCard).padding(32.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Text(title, fontSize = 28.sp, color = PleosFg, fontWeight = FontWeight.Bold)
      lines.forEach { Text(it, fontSize = 22.sp, color = PleosFg) }
      Text(mutedLine, fontSize = 20.sp, color = PleosMuted)
      Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
        PleosPrimaryButton(text = "확인", onClick = onConfirm, modifier = Modifier.widthIn(min = 200.dp))
      }
    }
  }
}
