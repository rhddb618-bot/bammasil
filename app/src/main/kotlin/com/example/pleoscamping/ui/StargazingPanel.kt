package com.example.pleoscamping.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pleoscamping.astro.MoonInfo
import com.example.pleoscamping.astro.Sector
import com.example.pleoscamping.astro.SkyData
import com.example.pleoscamping.astro.SkyEngine
import com.example.pleoscamping.astro.SkyFrame
import com.example.pleoscamping.astro.SkyGuide
import com.example.pleoscamping.astro.SkyPhase
import com.example.pleoscamping.astro.StarName
import com.example.pleoscamping.astro.VisibleConstellation
import com.example.pleoscamping.mode.CampingMode
import com.example.pleoscamping.ui.theme.PleosAccent
import com.example.pleoscamping.ui.theme.PleosAccentSoft
import com.example.pleoscamping.ui.theme.PleosBg
import com.example.pleoscamping.ui.theme.PleosCard
import com.example.pleoscamping.ui.theme.PleosFg
import com.example.pleoscamping.ui.theme.PleosLine
import com.example.pleoscamping.ui.theme.PleosMuted
import com.example.pleoscamping.ui.theme.PleosPrimaryButton
import com.example.pleoscamping.ui.theme.PleosRaised
import com.example.pleoscamping.ui.theme.PleosSecondaryButton
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val LICENSE_NOTICE =
  "아이콘: Lucide (ISC License, Lucide Contributors). " +
    "계산: Astronomy Engine (MIT, Don Cross) · 별·별자리 데이터: d3-celestial (BSD-3-Clause, Olaf Frohn), " +
    "XHIP (Anderson & Francis 2012, VizieR V/137D), IAU. This research has made use of the VizieR catalogue " +
    "access tool, CDS, Strasbourg, France (DOI 10.26093/cds/vizier)."

/** 별 보기 모드 패널: 앞유리 뷰 + 미니 레이더 + 보이는 별자리 목록 (SPEC3 3절, SPEC4 4절, SPEC5 3절) */
@Composable
fun StargazingPanel(state: CampingUiState) {
  var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (isActive) {
      nowMillis = System.currentTimeMillis()
      delay(30_000)
    }
  }

  val context = LocalContext.current
  val zone = remember { ZoneId.systemDefault() }
  var starNames by remember { mutableStateOf<Map<Int, StarName>>(emptyMap()) }
  var frame by remember { mutableStateOf<SkyFrame?>(null) }
  var timeOffsetMin by remember { mutableIntStateOf(0) }

  // frame()은 무겁다(약 2,500번 horizon 호출) → Default 디스패처에서 60초마다.
  // 시간 이동 offset을 계산 시각에 더하고, offset이 바뀌면 즉시 재계산한다 (SPEC5 3절)
  LaunchedEffect(state.latitude, state.longitude, timeOffsetMin) {
    val lat = state.latitude
    val lon = state.longitude
    val data = SkyData.load(context)
    starNames = data.names
    while (isActive) {
      val at = System.currentTimeMillis() + timeOffsetMin * 60_000L
      val next = withContext(Dispatchers.Default) {
        runCatching { SkyEngine.frame(data, lat, lon, at, zone) }.getOrNull()
      }
      if (next != null) frame = next
      delay(60_000)
    }
  }

  // HomeScreen은 이 패널에 state·onDisable만 넘기므로 같은 ViewModel 인스턴스를 직접 얻는다
  val vm: CampingViewModel = viewModel()

  // 유효 방위: 직접 설정이 우선, 없으면 차량 방위, 그래도 없으면 북쪽 기준 (SPEC4 1절)
  val headingDeg = state.manualHeadingDeg ?: state.vehicleHeadingDeg ?: 0f
  // 방위를 5° 단위로 양자화해 목록 재계산을 줄인다(섹터 판정에는 5° 오차가 무시 가능)
  val headingKey = (headingDeg / 5f).roundToInt() * 5
  val visible = remember(frame, headingKey) {
    frame?.let { SkyEngine.visibleConstellations(it, headingKey.toFloat()) }
  }

  // 해 고도로 나눈 하늘 밝기 단계 (SPEC18 A). 프레임이 아직 없으면 null
  val phase = frame?.let { SkyGuide.phaseOf(it.sunAltDeg) }

  // 패널 로컬 시선 상태 (SPEC5 3절). headingDeg가 바뀌면 사용자가 끌지 않았을 때만 방위를 따라간다
  var view by remember { mutableStateOf(SkyView(headingDeg, 22f, 110f)) }
  var userMoved by remember { mutableStateOf(false) }
  // 앞쪽 복귀: 시선을 차 앞쪽으로 되돌린다. 오른쪽 아래 레이더 터치로 실행
  val frontReset: () -> Unit = {
    view = SkyView(headingDeg, 22f, 110f)
    userMoved = false
  }
  // 차 앞쪽 방향을 고르면 시선도 새 앞쪽으로 돌린다 (SPEC18 B)
  val onSelectHeading: (Float?) -> Unit = { deg ->
    vm.setManualHeading(deg)
    view = SkyView(deg ?: state.vehicleHeadingDeg ?: 0f, 22f, 110f)
    userMoved = false
  }
  LaunchedEffect(headingDeg) {
    if (!userMoved) view = view.copy(azDeg = headingDeg)
  }

  // 목록 항목 터치: 강조 + 시선을 그 별자리 이름 위치로(고도는 10…60으로 제한) (SPEC5 3절)
  val onSelectItem: (VisibleConstellation) -> Unit = { item ->
    vm.selectConstellation(item.con.id)
    view = view.copy(
      azDeg = item.azDeg.toFloat(),
      altDeg = item.altDeg.toFloat().coerceIn(10f, 60f)
    )
    userMoved = true
  }

  var showLicenses by remember { mutableStateOf(false) }

  // 방향 출처 글자 (SPEC4 4절)
  val headingSource = headingSourceText(state.manualHeadingDeg, state.vehicleHeadingDeg)

  // 목록 제목 (SPEC18 A): 낮·박명이면 안 보이는 별자리로, 밤이면 시각에 맞게
  val listTitle = when {
    phase == SkyPhase.DAY || phase == SkyPhase.TWILIGHT -> "하늘이 밝아 안 보이는 별자리"
    timeOffsetMin == 0 -> "지금 보이는 별자리"
    else -> "이 시각에 보이는 별자리"
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .background(PleosCard)
      .border(1.dp, PleosLine)
      .padding(24.dp)
  ) {
    val wide = this@BoxWithConstraints.maxWidth >= 1100.dp
    val locationTimeText =
      "위치: ${if (state.usingDefaultLocation) "기본 위치(서울)" else "현재 위치"} · " +
        "${formatDateTime(nowMillis + timeOffsetMin * 60_000L)}${offsetSuffix(timeOffsetMin)}"
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // ① 제목 줄 + 오른쪽에 위치·시각(시간 이동 offset 반영)
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Icon(
          painter = painterResource(CampingMode.STARGAZING.iconRes),
          contentDescription = CampingMode.STARGAZING.label,
          tint = PleosAccent,
          modifier = Modifier.size(28.dp)
        )
        Text(CampingMode.STARGAZING.label, fontSize = 26.sp, color = PleosFg, fontWeight = FontWeight.Bold)
        // 달빛 기준 별 보기 조건 한 줄 안내 (SPEC18 A). 넓은 화면에서 달 정보가 있을 때만
        val moon = frame?.moon
        if (wide && phase != null && moon != null) {
          val cond = SkyGuide.starCondition(phase, moon.illumination, moon.p.altDeg)
          Text(
            cond.text,
            fontSize = 20.sp,
            maxLines = 1,
            color = when (cond.level) {
              0 -> PleosAccent
              1 -> PleosFg
              else -> PleosMuted
            }
          )
          Text("달빛 기준 · 날씨는 반영하지 않아요", fontSize = 18.sp, color = PleosMuted, maxLines = 1)
        }
        Text(
          locationTimeText,
          fontSize = 22.sp,
          color = PleosFg,
          modifier = Modifier.weight(1f),
          textAlign = TextAlign.End,
          maxLines = 2
        )
      }

      // ② 앞유리 뷰 + 오른쪽 아래 미니 레이더 overlay
      val windshield: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxWidth()) {
          SkyWindshield(
            frame = frame,
            headingDeg = headingDeg,
            view = view,
            onViewChange = { v ->
              if (v.azDeg != view.azDeg) userMoved = true
              view = v
            },
            highlightId = state.selectedConstellation,
            names = starNames,
            modifier = Modifier.fillMaxWidth()
          )
          Text(
            "좌우로 끌면 시선이 돌아가요 · 오른쪽 아래 동그라미를 누르면 앞쪽으로 돌아와요",
            fontSize = 18.sp,
            color = PleosMuted,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(PleosBg.copy(alpha = 0.7f)).padding(horizontal = 10.dp, vertical = 6.dp)
          )
          Box(
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .clickable(onClick = frontReset)
              .padding(12.dp)
          ) {
            SkyRadar(
              frame = frame,
              headingDeg = headingDeg,
              view = view
            )
          }
          // 낮·박명 안내 덮개 (SPEC18 A): 하늘이 밝아 별이 안 보이는 시각임을 알린다
          if (phase == SkyPhase.DAY || phase == SkyPhase.TWILIGHT) {
            Column(
              modifier = Modifier
                .align(Alignment.Center)
                .background(PleosBg.copy(alpha = 0.85f))
                .border(1.dp, PleosLine)
                .padding(horizontal = 32.dp, vertical = 24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Text(
                when {
                  phase == SkyPhase.DAY && timeOffsetMin == 0 -> "지금은 낮이라 별이 보이지 않아요"
                  phase == SkyPhase.DAY -> "이 시각은 낮이라 별이 보이지 않아요"
                  timeOffsetMin == 0 -> "하늘이 아직 밝아 별이 잘 보이지 않아요"
                  else -> "이 시각은 하늘이 밝아 별이 잘 보이지 않아요"
                },
                fontSize = 26.sp,
                color = PleosFg,
                fontWeight = FontWeight.Bold
              )
              Text("뒤에 보이는 그림은 햇빛이 없다면 보일 별자리 위치예요.", fontSize = 20.sp, color = PleosMuted)
              // 가장 어두워지는 시각이 계산됐을 때만 밤하늘로 건너뛰는 버튼을 준다
              val darkStart = frame?.darkStartMillis
              if (darkStart != null) {
                PleosPrimaryButton(
                  text = if (timeOffsetMin == 0) {
                    "오늘 밤 하늘 보기 (${darkStart.formatHmOrDash(zone)})"
                  } else {
                    "밤하늘 보기 (${darkStart.formatHmOrDash(zone)})"
                  },
                  onClick = { timeOffsetMin = ((darkStart - System.currentTimeMillis()) / 60_000L).toInt() + 1 }
                )
              }
            }
          }
        }
      }

      // ③ 조작 줄: 시간 이동 5개. 앞쪽 복귀는 오른쪽 아래 레이더 터치로 (SPEC5 3절)
      val timeButtons: @Composable RowScope.() -> Unit = {
        HeadingButton("1시간 전", selected = false, onClick = { timeOffsetMin -= 60 }, modifier = Modifier.weight(1f), fontSize = 20.sp)
        HeadingButton("15분 전", selected = false, onClick = { timeOffsetMin -= 15 }, modifier = Modifier.weight(1f), fontSize = 20.sp)
        HeadingButton("지금", selected = timeOffsetMin == 0, onClick = { timeOffsetMin = 0 }, modifier = Modifier.weight(1f), fontSize = 20.sp)
        HeadingButton("15분 뒤", selected = false, onClick = { timeOffsetMin += 15 }, modifier = Modifier.weight(1f), fontSize = 20.sp)
        HeadingButton("1시간 뒤", selected = false, onClick = { timeOffsetMin += 60 }, modifier = Modifier.weight(1f), fontSize = 20.sp)
      }

      if (wide) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            windshield()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              timeButtons()
            }
            // ④ 정보 4칸을 가로 한 줄로
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              infoCellData(frame, zone).forEach { (title, lines) ->
                InfoCell(title, lines, Modifier.weight(1f))
              }
            }
          }
          // 오른쪽: 지금 보이는 별자리 목록 + 오픈소스 고지 버튼 (방향 출처 글자 포함)
          Column(
            modifier = Modifier.width(360.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            HeadingSelector(state.manualHeadingDeg, headingSource, onSelectHeading, Modifier.fillMaxWidth())
            VisibleConstellationList(
              visible = visible,
              title = listTitle,
              selectedConstellation = state.selectedConstellation,
              onSelectConstellation = onSelectItem,
              modifier = Modifier
                .fillMaxWidth()
                .height(610.dp)
            )
            PleosSecondaryButton(
              text = "오픈소스 고지",
              onClick = { showLicenses = true },
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      } else {
        // 좁은 화면: 제목 → 유리 뷰 → 조작 → 목록 → 정보 → 버튼
        windshield()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          timeButtons()
        }
        HeadingSelector(state.manualHeadingDeg, headingSource, onSelectHeading, Modifier.fillMaxWidth())
        VisibleConstellationList(
          visible = visible,
          title = listTitle,
          selectedConstellation = state.selectedConstellation,
          onSelectConstellation = onSelectItem,
          modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
        )
        InfoCells(frame, zone)
        PleosSecondaryButton(
          text = "오픈소스 고지",
          onClick = { showLicenses = true },
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }

  if (showLicenses) {
    LicenseDialog(onDismiss = { showLicenses = false })
  }
}

/** 오른쪽 열: 방향 출처 + 방위별 보이는 별자리 목록. 항목 터치 → 강조 + 시선 이동 (SPEC4 4절, SPEC5 3절) */
@Composable
private fun VisibleConstellationList(
  visible: List<VisibleConstellation>?,
  title: String,
  selectedConstellation: String?,
  onSelectConstellation: (VisibleConstellation) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // 제목 줄
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        title,
        fontSize = 20.sp,
        color = PleosFg,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f)
      )
    }

    // 섹션 4개: 앞 · 오른쪽 · 뒤 · 왼쪽 순서
    for (sector in Sector.values()) {
      Text(sector.ko, fontSize = 20.sp, color = PleosFg, fontWeight = FontWeight.Bold)
      val items = visible?.filter { it.sector == sector } ?: emptyList()
      if (items.isEmpty()) {
        Text("없음", fontSize = 22.sp, color = PleosMuted)
      } else {
        for (item in items) {
          ConstellationItem(
            name = item.con.ko,
            starred = item.con.rank == 1,
            altitudeText = "고도 ${item.altDeg.roundToInt()}°",
            selected = item.con.id == selectedConstellation,
            onClick = { onSelectConstellation(item) }
          )
        }
      }
    }
  }
}

/** 시간 이동 보조 버튼: 직각, 64dp 높이. PleosRaised 바탕·PleosMuted 글자,
 *  선택된 것("지금")은 PleosAccent 글자 + 1dp PleosAccent 테두리 (SPEC_DARK 3절) */
@Composable
private fun HeadingButton(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  fontSize: TextUnit = 18.sp
) {
  OutlinedButton(
    onClick = onClick,
    modifier = modifier.height(64.dp),
    shape = RectangleShape,
    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = PleosRaised,
      contentColor = if (selected) PleosAccent else PleosMuted
    ),
    border = if (selected) BorderStroke(1.dp, PleosAccent) else BorderStroke(1.dp, Color.Transparent)
  ) {
    Text(text, fontSize = fontSize, maxLines = 1, softWrap = false)
  }
}

/** 차 앞쪽 방향 고르기: 자동(차량 방위) / 북 / 동 / 남 / 서 (SPEC18 B) */
@Composable
private fun HeadingSelector(
  manualHeadingDeg: Float?,
  headingSource: String,
  onSelect: (Float?) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("차 앞쪽 방향", fontSize = 20.sp, color = PleosFg, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
      Text(headingSource, fontSize = 18.sp, color = PleosMuted, maxLines = 1)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      HeadingButton("자동", selected = manualHeadingDeg == null, onClick = { onSelect(null) }, modifier = Modifier.weight(1f), fontSize = 20.sp)
      HeadingButton("북", selected = manualHeadingDeg == 0f, onClick = { onSelect(0f) }, modifier = Modifier.weight(1f), fontSize = 20.sp)
      HeadingButton("동", selected = manualHeadingDeg == 90f, onClick = { onSelect(90f) }, modifier = Modifier.weight(1f), fontSize = 20.sp)
      HeadingButton("남", selected = manualHeadingDeg == 180f, onClick = { onSelect(180f) }, modifier = Modifier.weight(1f), fontSize = 20.sp)
      HeadingButton("서", selected = manualHeadingDeg == 270f, onClick = { onSelect(270f) }, modifier = Modifier.weight(1f), fontSize = 20.sp)
    }
  }
}

/** 목록 항목: 터치하면 강조 선택. 선택 항목은 PleosAccentSoft 바탕 + 1dp PleosAccent 테두리,
 *  ★ 표시는 PleosAccent (SPEC_DARK 3절) */
@Composable
private fun ConstellationItem(
  name: String,
  starred: Boolean,
  altitudeText: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (selected) Modifier.background(PleosAccentSoft).border(1.dp, PleosAccent) else Modifier)
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Text(
      buildAnnotatedString {
        append(name)
        if (starred) withStyle(SpanStyle(color = PleosAccent)) { append(" ★") }
        append(" · $altitudeText")
      },
      fontSize = 22.sp,
      color = PleosFg
    )
  }
}

/** 방향 출처 글자: 직접 설정 / 차량 방위 / 방위 없음 (SPEC4 4절) */
private fun headingSourceText(manualHeadingDeg: Float?, vehicleHeadingDeg: Float?): String {
  if (manualHeadingDeg != null) return "직접 고름 · ${dirKo(manualHeadingDeg)}쪽"
  if (vehicleHeadingDeg != null) return "차량 방위 ${vehicleHeadingDeg.roundToInt()}°"
  return "방위 없음 · 북쪽 기준"
}

private fun dirKo(deg: Float): String = when (deg.roundToInt()) {
  0 -> "북"
  90 -> "동"
  180 -> "남"
  270 -> "서"
  else -> "${deg.roundToInt()}°"
}

/** 시간 이동 표시: offset ≠ 0이면 시각 뒤에 " (+3시간)"처럼 붙인다 (SPEC5 3절) */
private fun offsetSuffix(offsetMin: Int): String {
  if (offsetMin == 0) return ""
  val sign = if (offsetMin > 0) "+" else "-"
  val total = abs(offsetMin)
  val h = total / 60
  val m = total % 60
  val body = when {
    h > 0 && m > 0 -> "${h}시간 ${m}분"
    h > 0 -> "${h}시간"
    else -> "${m}분"
  }
  return " (${sign}${body})"
}

@Composable
private fun InfoCells(frame: SkyFrame?, zone: ZoneId) {
  val cells = infoCellData(frame, zone)
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    if (maxWidth >= 700.dp) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEach { (title, lines) -> InfoCell(title, lines, Modifier.weight(1f)) }
      }
    } else {
      Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          InfoCell(cells[0].first, cells[0].second, Modifier.weight(1f))
          InfoCell(cells[1].first, cells[1].second, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          InfoCell(cells[2].first, cells[2].second, Modifier.weight(1f))
          InfoCell(cells[3].first, cells[3].second, Modifier.weight(1f))
        }
      }
    }
  }
}

/** 정보 4칸의 제목·내용. 가로 배치(StargazingPanel)와 세로 배치(InfoCells) 양쪽에서 사용 */
private fun infoCellData(frame: SkyFrame?, zone: ZoneId): List<Pair<String, List<String>>> {
  val moon = frame?.moon
  // 낮에는 행성이 떠 있어도 안 보이므로 칸 제목을 바꾼다 (SPEC18 A)
  val planetsTitle =
    if (frame != null && SkyGuide.phaseOf(frame.sunAltDeg) == SkyPhase.DAY) "떠 있는 행성(낮이라 안 보임)" else "보이는 행성"
  return listOf(
    "달" to moonLines(moon, zone),
    planetsTitle to listOf(
      frame?.planets
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(" · ") { it.label }
        ?: "지금은 없음"
    ),
    "가장 어두운 시간" to listOf(darkRangeText(frame, zone)),
    "유성우" to listOf(
      frame?.meteorShower
        ?.let { "${it.ko} 극대 ${it.peakMonth}/${it.peakDay} · 시간당 ${it.zhr}개 · 복사점 ${it.radiantKo}" }
        ?: "오늘 ±2일 안 유성우 없음"
    )
  )
}

@Composable
private fun InfoCell(title: String, lines: List<String>, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .background(PleosRaised)
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Text(title, fontSize = 18.sp, color = PleosMuted)
    lines.forEach { Text(it, fontSize = 22.sp, color = PleosFg) }
  }
}

private fun moonLines(moon: MoonInfo?, zone: ZoneId): List<String> {
  if (moon == null) return listOf("정보 없음")
  return listOf(
    "${moon.phaseName} · 월령 ${"%.1f".format(moon.ageDays)}일 · 조도 ${(moon.illumination * 100).roundToInt()}%",
    "월출 ${moon.riseMillis.formatHmOrDash(zone)} · 월몰 ${moon.setMillis.formatHmOrDash(zone)}"
  )
}

private fun darkRangeText(frame: SkyFrame?, zone: ZoneId): String {
  val start = frame?.darkStartMillis
  val end = frame?.darkEndMillis
  if (start == null && end == null) return "백야/계산 불가"
  return "${start.formatHmOrDash(zone)} ~ ${end.formatHmOrDash(zone)}"
}

private fun Long?.formatHmOrDash(zone: ZoneId): String =
  this?.let { DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "—"

private fun formatDateTime(millis: Long): String =
  SimpleDateFormat("M월 d일 a h시 m분", Locale.KOREAN).format(Date(millis))

@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
  val context = LocalContext.current
  var licenseText by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(Unit) {
    licenseText = withContext(Dispatchers.IO) {
      val engine = runCatching {
        context.assets.open("licenses/astronomy-engine-MIT.txt").bufferedReader().use { it.readText() }
      }.getOrDefault("")
      val celestial = runCatching {
        context.assets.open("licenses/d3-celestial-BSD-3-Clause.txt").bufferedReader().use { it.readText() }
      }.getOrDefault("")
      val lucide = runCatching {
        context.assets.open("licenses/lucide-ISC.txt").bufferedReader().use { it.readText() }
      }.getOrDefault("")
      engine.trimEnd() + "\n\n" + celestial.trimEnd() + "\n\n" + lucide.trimEnd()
    }
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = PleosCard,
    shape = RectangleShape,
    title = { Text("오픈소스 고지", fontSize = 22.sp, color = PleosFg, fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .heightIn(max = 420.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(LICENSE_NOTICE, fontSize = 16.sp, color = PleosFg)
        Text(licenseText ?: "불러오는 중…", fontSize = 14.sp, color = PleosFg)
      }
    },
    confirmButton = {
      OutlinedButton(
        onClick = onDismiss,
        shape = RectangleShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PleosFg),
        border = BorderStroke(1.dp, PleosFg)
      ) {
        Text("닫기", fontSize = 20.sp)
      }
    }
  )
}
