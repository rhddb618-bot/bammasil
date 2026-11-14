package com.example.pleoscamping.ui

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleoscamping.BuildConfig
import com.example.pleoscamping.mode.CampingMode
import com.example.pleoscamping.mode.VISIBLE_MODES
import com.example.pleoscamping.ui.theme.PleosAccent
import com.example.pleoscamping.ui.theme.PleosAccentSoft
import com.example.pleoscamping.ui.theme.PleosAccentSolid
import com.example.pleoscamping.ui.theme.PleosBg
import com.example.pleoscamping.ui.theme.PleosCard
import com.example.pleoscamping.ui.theme.PleosDanger
import com.example.pleoscamping.ui.theme.PleosFg
import com.example.pleoscamping.ui.theme.PleosLine
import com.example.pleoscamping.ui.theme.PleosMuted
import com.example.pleoscamping.ui.theme.PleosOn
import com.example.pleoscamping.ui.theme.PleosOnAccent
import com.example.pleoscamping.ui.theme.PleosPrimaryButton
import com.example.pleoscamping.ui.theme.PleosRaised
import com.example.pleoscamping.ui.theme.PleosSecondaryButton
import com.example.pleoscamping.ui.theme.PleosWarn
import kotlinx.coroutines.launch

@Composable
fun CampingApp(viewModel: CampingViewModel) {
  val state by viewModel.state.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current
  // 모드 켜기·끄기 버튼 공통 조건(주차 중이고 처리 중이 아닐 때)
  val toggleEnabled = state.gear == VehicleGear.GEAR_PARK && !state.busy

  // 앱 창 밝기를 ModeController와 연결한다
  DisposableEffect(context) {
    val activity = context as? Activity
    viewModel.installWindowBrightness(
      read = { activity?.window?.attributes?.screenBrightness ?: -1f },
      apply = { value ->
        activity?.window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = value } }
      }
    )
    onDispose { viewModel.installWindowBrightness(read = { -1f }, apply = {}) }
  }

  LaunchedEffect(state.message) {
    val text = state.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(text)
    viewModel.consumeMessage()
  }

  // 화면 켜짐 유지: 노래방 재생 등 활성 상태에서만 FLAG_KEEP_SCREEN_ON
  LaunchedEffect(state.keepScreenOn) {
    val activity = context as? Activity ?: return@LaunchedEffect
    if (state.keepScreenOn) {
      activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
      activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }

  CarCampingApp(state, viewModel, snackbarHostState, toggleEnabled)

  // 권한을 묻기 전 안내 창(SPEC14)
  state.permissionNotice?.let { PermissionNoticeDialog(notice = it, musicSource = state.musicSource, onConfirm = viewModel::confirmPermissionNotice) }
}

/** 차량용 배치: 상태 띠 → 모드 탭 → 켜기/끄기 줄은 고정이고 패널 영역만 세로 스크롤된다 */
@Composable
private fun CarCampingApp(
  state: CampingUiState,
  viewModel: CampingViewModel,
  snackbarHostState: SnackbarHostState,
  toggleEnabled: Boolean
) {
  val scope = rememberCoroutineScope()
  Scaffold(
    containerColor = PleosBg,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    modifier = Modifier.fillMaxSize()
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      StatusBar(
        state = state,
        onToggleKeepAlive = viewModel::setVcKeepAlive
      )
      ModeTabs(
        state = state,
        onSelect = viewModel::selectMode
      )
      PowerBar(
        state = state,
        enabled = toggleEnabled,
        onToggle = viewModel::toggleMode
      )
      val panelScroll = rememberScrollState()
      LaunchedEffect(state.selectedMode) { panelScroll.scrollTo(0) }
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(panelScroll),
        verticalArrangement = Arrangement.spacedBy(24.dp)
      ) {
        SelectedModePanel(
          state = state,
          viewModel = viewModel,
          onBlockedTap = {
            scope.launch { snackbarHostState.showSnackbar("먼저 위의 켜기를 눌러 주세요") }
          }
        )
        if (BuildConfig.DEBUG) {
          DevPanel(
            state = state,
            manualHeadingDeg = state.manualHeadingDeg,
            onManualHeading = viewModel::setManualHeading,
            onRefresh = viewModel::devRefresh,
            onDrivingTest = viewModel::devDrivingTest,
            onAction = viewModel::devAction
          )
        }
      }
    }
  }
}

/** 고른 모드(selectedMode)의 패널. 그 모드가 켜져 있지 않으면 흐린 덮개로 패널 조작을 막는다 */
@Composable
private fun SelectedModePanel(state: CampingUiState, viewModel: CampingViewModel, onBlockedTap: () -> Unit) {
  Box {
    when (state.selectedMode) {
      CampingMode.STARGAZING -> StargazingPanel(state = state)
      CampingMode.CAMP_LIGHT -> CampLightPanel(
        state = state,
        onColor = viewModel::setCampColor,
        onBrightness = viewModel::setCampBrightness,
        onFireSound = viewModel::setCampFireSound
      )
      CampingMode.MUSIC_LIGHT -> MusicLightPanel(
        state = state,
        onColors = viewModel::setMusicColors,
        onSensitivity = viewModel::setMusicSensitivity,
        onDemoStart = viewModel::startMusicDemo,
        onDemoStop = viewModel::stopMusicDemo,
        onSource = viewModel::setMusicSource
      )
      CampingMode.BATTERY_GUARD -> BatteryGuardPanel(
        state = state,
        onSaveHome = viewModel::saveGuardHomeHere,
        onResetHome = viewModel::resetGuardHome,
        onAllowHvacOff = viewModel::setGuardAllowHvacOff
      )
      CampingMode.SUNRISE_ALARM -> SunrisePanel(
        state = state,
        onTimeChange = viewModel::setSunriseTime,
        onUseSunrise = viewModel::useRecommendedSunriseTime,
        onPreHvac = viewModel::setSunrisePreHvac,
        onSeatUp = viewModel::setSunriseSeatUp,
        onSound = viewModel::setSunriseSound,
        onKeepHvac = viewModel::setSunriseKeepHvac,
        onArm = { viewModel.armSunrise(demo = false) },
        onDemo = { viewModel.armSunrise(demo = true) },
        onCancel = viewModel::cancelSunrise,
        onWokeUp = viewModel::sunriseWokeUp
      )
    }
    if (state.activeMode != state.selectedMode) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .background(PleosBg.copy(alpha = 0.6f))
          .pointerInput(Unit) { detectTapGestures(onTap = { onBlockedTap() }) }
      )
    }
  }
}

/** 상단 상태 띠: 기어, 배터리, 차량 연결, 주차 안내. DEBUG에서는 VC 점·설정 메뉴도 보여 준다.
 *  기어 ≠ P면 바탕 PleosWarn, 글자 PleosOnAccent (SPEC_DARK 3절). */
@Composable
private fun StatusBar(state: CampingUiState, onToggleKeepAlive: (Boolean) -> Unit) {
  val notPark = state.gear != null && state.gear != VehicleGear.GEAR_PARK
  val barBackground = if (notPark) PleosWarn else PleosCard
  val barTextColor = if (notPark) PleosOnAccent else PleosFg
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(barBackground)
      .border(1.dp, PleosLine)
      .padding(horizontal = 20.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(28.dp)
  ) {
    Text(gearLabel(state.gear), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = barTextColor)
    Text("배터리 ${state.batteryPercent?.let { "$it%" } ?: "--"}", fontSize = 26.sp, color = barTextColor)
    if (state.sdkReady) {
      Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(PleosOn))
      Text("차량 연결됨", fontSize = 22.sp, color = barTextColor)
    } else {
      Text("차량 없음", fontSize = 22.sp, color = barTextColor)
    }
    if (BuildConfig.DEBUG) {
      val dotColor = when {
        state.vcFailed -> PleosDanger
        state.vcState?.available == true -> PleosOn
        else -> Color(0xFF9E9E9E)
      }
      Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(dotColor))
      VcSettingsMenu(state = state, onToggle = onToggleKeepAlive)
    }
    Text(
      if (notPark) "주차 중에만 쓸 수 있어요" else "주차 중에만 사용",
      fontSize = 22.sp,
      color = if (notPark) PleosOnAccent else PleosMuted,
      modifier = Modifier.weight(1f),
      textAlign = TextAlign.End
    )
  }
}

@Composable
private fun VcSettingsMenu(state: CampingUiState, onToggle: (Boolean) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    TextButton(
      onClick = { expanded = true },
      shape = RectangleShape,
      colors = ButtonDefaults.textButtonColors(contentColor = PleosFg)
    ) {
      Text("VC", fontSize = 22.sp)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      DropdownMenuItem(
        text = { Text("VC 활성 유지", fontSize = 22.sp) },
        trailingIcon = {
          Switch(
            checked = state.vcKeepAlive,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
              checkedTrackColor = PleosAccentSolid,
              checkedThumbColor = PleosOnAccent,
              uncheckedTrackColor = PleosLine,
              uncheckedThumbColor = PleosFg,
              uncheckedBorderColor = Color.Transparent
            )
          )
        },
        onClick = {
          onToggle(!state.vcKeepAlive)
          expanded = false
        }
      )
    }
  }
}

/** 모드 탭 줄. 보이는 모드만 그린다(SPEC15). 탭은 모드를 고르기만 하고 켜고 끄는 것은 PowerBar가 한다 */
@Composable
private fun ModeTabs(state: CampingUiState, onSelect: (CampingMode) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    VISIBLE_MODES.forEach { mode ->
      val selected = state.selectedMode == mode
      val active = state.activeMode == mode
      Row(
        modifier = Modifier
          .weight(1f)
          .height(72.dp)
          .background(if (selected) PleosAccentSoft else PleosCard)
          .border(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) PleosAccent else PleosLine
          )
          .clickable { onSelect(mode) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
      ) {
        Icon(
          painter = painterResource(mode.iconRes),
          contentDescription = mode.label,
          tint = if (active) PleosAccent else PleosFg,
          modifier = Modifier.size(32.dp)
        )
        Text(
          mode.label,
          fontSize = 24.sp,
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
          color = PleosFg,
          maxLines = 1
        )
        if (active) {
          Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(PleosOn))
          Text("켜짐", fontSize = 18.sp, color = PleosOn)
        }
      }
    }
  }
}

/** 고른 모드를 켜고 끄는 단 하나의 줄 */
@Composable
private fun PowerBar(
  state: CampingUiState,
  enabled: Boolean,
  onToggle: (CampingMode) -> Unit
) {
  val active = state.activeMode == state.selectedMode
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(72.dp)
      .background(PleosCard)
      .border(1.dp, PleosLine)
      .padding(start = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      if (active) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(PleosOn))
      }
      Text(
        if (active) "켜져 있어요 · 끄면 차를 켜기 전 상태로 되돌려요" else state.selectedMode.description,
        fontSize = 22.sp,
        color = PleosFg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    if (active) {
      PleosSecondaryButton(
        text = "끄기",
        onClick = { onToggle(state.selectedMode) },
        enabled = enabled,
        modifier = Modifier.width(240.dp)
      )
    } else {
      PleosPrimaryButton(
        text = "켜기",
        onClick = { onToggle(state.selectedMode) },
        enabled = enabled,
        modifier = Modifier.width(240.dp)
      )
    }
  }
}

private fun gearLabel(gear: VehicleGear?): String = when (gear) {
  VehicleGear.GEAR_PARK -> "P"
  VehicleGear.GEAR_DRIVE -> "D"
  VehicleGear.GEAR_NEUTRAL -> "N"
  VehicleGear.GEAR_REVERSE -> "R"
  else -> "--"
}
