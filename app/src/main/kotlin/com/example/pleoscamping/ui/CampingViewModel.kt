package com.example.pleoscamping.ui

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pleoscamping.BuildConfig
import com.example.pleoscamping.R
import com.example.pleoscamping.astro.SunTimes
import com.example.pleoscamping.karaoke.BeatState
import com.example.pleoscamping.karaoke.DemoBeatSource
import com.example.pleoscamping.karaoke.DemoTrackPlayer
import com.example.pleoscamping.karaoke.MicMeter
import com.example.pleoscamping.karaoke.OutputTap
import com.example.pleoscamping.karaoke.ToneSynth
import com.example.pleoscamping.mode.BatteryGuard
import com.example.pleoscamping.mode.CampingMode
import com.example.pleoscamping.mode.GuardInput
import com.example.pleoscamping.mode.GuardResult
import com.example.pleoscamping.mode.HomeDistance
import com.example.pleoscamping.mode.ModeController
import com.example.pleoscamping.vehicle.AMBIENT_AREA
import com.example.pleoscamping.vehicle.CabinState
import com.example.pleoscamping.vehicle.DRIVER_AREA
import com.example.pleoscamping.vehicle.PASSENGER_AREA
import com.example.pleoscamping.vehicle.READING_LEFT_AREA
import com.example.pleoscamping.vehicle.READING_RIGHT_AREA
import com.example.pleoscamping.vehicle.VehicleGateway
import com.example.pleoscamping.vehicle.VcProbe
import com.example.pleoscamping.vehicle.VcState
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** 캠핑 조명 기본값: 기본 색은 모닥불, 기본 밝기는 30(SPEC6 2절) */
const val DEFAULT_CAMP_COLOR = 0xFFFF6A1A.toInt()
const val DEFAULT_CAMP_BRIGHTNESS = 30

/** 음악 조명이 소리를 읽는 방법(SPEC7 4절). 기본은 스피커 출력이고 마이크는 예비 */
enum class MusicSource(val label: String) {
  OUTPUT("차 스피커"),
  MIC("마이크(예비)")
}

/** 시연용 음원 종류(SPEC8 4-2절). 무료 곡 2개와 기존 박자음 */
enum class MusicDemo(val label: String) {
  SLOW("느린 곡"),
  FAST("빠른 곡"),
  BEAT("박자음")
}

/** 음악 조명 색 조합(SPEC9 3-2절). 분위기 자동 판정 대신 사용자가 직접 고른다 */
enum class MusicColors(val label: String, val colors: List<Int>) {
  SUNSET("노을", listOf(0xFFFFB347.toInt(), 0xFFFF8FA3.toInt(), 0xFFB39DDB.toInt(), 0xFFFFC24D.toInt())),
  PASTEL("파스텔", listOf(0xFFFFD54F.toInt(), 0xFFFF7EB6.toInt(), 0xFF4FC3F7.toInt(), 0xFF7CE0A0.toInt())),
  FLAME("불꽃", listOf(0xFFFF2D2D.toInt(), 0xFFFF6A1A.toInt(), 0xFFFFE0B2.toInt(), 0xFFFF3D71.toInt())),
  NEON("네온", listOf(0xFF00E5FF.toInt(), 0xFFAF52DE.toInt(), 0xFFFF2D95.toInt(), 0xFF5B5BFF.toInt()))
}

/** 권한을 묻기 전에 띄우는 안내(SPEC14) */
enum class PermissionNotice { LOCATION, AUDIO }

data class CampingUiState(
  val sdkReady: Boolean = false,
  val gear: VehicleGear? = null,
  val batteryPercent: Int? = null,
  val activeMode: CampingMode? = null,
  val selectedMode: CampingMode = CampingMode.STARGAZING,
  val vcState: VcState? = null,
  val vcFailed: Boolean = false,
  val vcKeepAlive: Boolean = false,
  val busy: Boolean = false,
  val message: String? = null,
  val snapshot: CabinState? = null,
  val campColor: Int = DEFAULT_CAMP_COLOR,
  val campBrightness: Int = DEFAULT_CAMP_BRIGHTNESS,
  // 고른 색의 소리 토글(SPEC16 3절). 고른 색에 맞는 소리 켜짐
  val campFireSound: Boolean = false,
  // 캠핑 조명 미리보기용 색·밝기(SPEC10 1-4절). 지금 차에 보낸 값, 색이 null이면 꺼짐
  val campLightColor: Int? = null,
  val campLightBrightness: Int = 0,
  val cabinLightOn: Boolean = false,
  val readingLightOn: Boolean = false,
  val tailLampOn: Boolean = false,
  val latitude: Double = 37.5665,
  val longitude: Double = 126.9780,
  val usingDefaultLocation: Boolean = true,
  val keepScreenOn: Boolean = false,
  // 음악 조명 (SPEC6 1-4절)
  val musicListening: Boolean = false,
  val musicBpm: Float? = null,
  val musicConfidence: Float = 0f,
  val musicLevel: Float = 0f,
  val musicBeatCount: Long = 0L,
  val musicSensitivity: Int = 50,
  val musicSource: MusicSource = MusicSource.OUTPUT,
  // 색 조합(SPEC9 3-2절). 조명은 이 색 4개를 펄스마다 돌아가며 쓴다
  val musicColors: MusicColors = MusicColors.NEON,
  // 조명 미리보기용 색·밝기(SPEC8 4-8절). 색이 null이면 꺼짐
  val musicLightColor: Int? = null,
  val musicLightBrightness: Int = 0,
  // 시연용 음원(SPEC6 5절·SPEC8 4-2절, DEBUG 빌드 전용). null이면 꺼짐
  val musicDemo: MusicDemo? = null,
  // 휴식 블루 10분 타이머 종료 시각(epoch ms). null이면 꺼짐
  val restBlueDeadline: Long? = null,
  val devComfort: DevComfort? = null,
  // 배터리 가드
  val guardHomeKm: Int = 50,
  val guardMarginPct: Int = 15,
  val guardEfficiency: Float = 5.0f,
  // 저장한 집 위치(SPEC12 2절). guardHomeSaved가 false면 기본값(서울)이다
  val guardHomeLat: Double = HomeDistance.DEFAULT_HOME_LAT,
  val guardHomeLon: Double = HomeDistance.DEFAULT_HOME_LON,
  val guardHomeSaved: Boolean = false,
  val guardVehicleEfficiency: Float? = null,
  val guardAllowHvacOff: Boolean = true,
  val guardResult: GuardResult? = null,
  val guardComfort: DevComfort? = null,
  // 일출 알람 (SPEC6 3절)
  val sunriseHour: Int = 6,
  val sunriseMinute: Int = 30,
  val sunriseRecommendedMillis: Long? = null,
  val sunriseActualMillis: Long? = null,
  val sunriseIsRecommended: Boolean = false,
  val sunriseArmed: Boolean = false,
  val sunriseRinging: Boolean = false,
  val sunriseDemo: Boolean = false,
  val sunrisePreHvac: Boolean = true,
  val sunriseSeatUp: Boolean = true,
  val sunriseSound: Boolean = true,
  val sunriseKeepHvac: Boolean = true,
  val sunriseStatus: String? = null,
  val sunriseTargetMillis: Long? = null,
  // 별 보기 v3 (SPEC4 1절)
  val vehicleHeadingDeg: Float? = null,     // FusedLocation heading (confidence > 0이고 권한 있을 때)
  val manualHeadingDeg: Float? = null,      // 사용자가 고른 "앞쪽" 방위(0 북, 90 동, 180 남, 270 서). null이면 자동
  val selectedConstellation: String? = null, // 강조할 별자리 약자(예 "Ori"). null이면 강조 없음
  // 권한을 묻기 전 안내(SPEC14). null이면 안 보임
  val permissionNotice: PermissionNotice? = null
)

class CampingViewModel(application: Application) : AndroidViewModel(application) {

  private val gateway = VehicleGateway(application)
  private val controller = ModeController(gateway)
  private val toneSynth = ToneSynth()
  private val micMeter = MicMeter(application)
  private val outputTap = OutputTap(application)
  private val demoTrackPlayer = DemoTrackPlayer(application)
  /** 장작 소리 재생기(SPEC10 1-3절). 다른 소리가 재생되면 끈다 (SPEC18 E) */
  private val firePlayer = DemoTrackPlayer(
    application,
    useAudioFocus = true,
    onFocusLost = {
      stopFireSound()
      showMessage("다른 소리가 재생돼 캠핑 조명 소리를 껐어요")
    }
  )

  private val _state = MutableStateFlow(CampingUiState())
  val state: StateFlow<CampingUiState> = _state.asStateFlow()

  private var foreground = false
  private var lastGear: VehicleGear? = null
  private var monitorJob: Job? = null
  private var heartbeatJob: Job? = null
  private var lightSyncJob: Job? = null
  private var guardJob: Job? = null
  private var sunriseJob: Job? = null
  private var alarmSoundJob: Job? = null
  private var restBlueJob: Job? = null
  /** 캠핑 조명 움직임 루프(SPEC10 1-2절). 모닥불·휴식 블루일 때만 돈다 */
  private var campAnimJob: Job? = null
  /** 모닥불 일렁임 밝기·색 보간에 쓰는 부드러운 난수(0..1) */
  private var fireFlickerN = 0.5f
  private var fireColorN = 0.5f
  /** 움직임 루프가 직전에 보낸 색·밝기(중복 Set 방지) */
  private var lastCampAnimColor: Int? = null
  private var lastCampAnimBrightness: Int? = null
  /** 취침 레드로 들어가기 전의 기준 밝기(SPEC17). 나올 때 되돌린다 */
  private var campBrightnessBeforeRed: Int? = null
  private var fusedLocation: FusedLocation? = null
  private var headingListener: FusedLocationListener? = null

  /** 배터리 가드가 마지막으로 적용한 단계. -1이면 아직 적용 전(첫 판정 때 무조건 적용) */
  private var guardStage = -1

  /** 조명 연동이 마지막으로 보낸 값(중복 Set 방지) */
  private var lastSentColor: Int? = null
  private var lastSentBrightness: Int? = null

  /** 박자 감지: 이미 반영한 beatCount. 마이크 소스에서 새 박을 알아볼 때 쓴다 */
  private var seenBeatCount = 0L
  /** 소리 세기 envelope(0..1). 펄스에서 올라가고 MUSIC_RELEASE_MS에 걸쳐 내려간다 */
  @Volatile private var musicEnv = 0f
  /** Set 사이에 놓친 envelope 최댓값. 다음 프레임이 놓치지 않고 반영하도록 들고 있다 */
  @Volatile private var musicHold = 0f
  /** 마지막 envelope 갱신 시각(elapsedRealtime ms) */
  @Volatile private var musicEnvAt = 0L
  /** 색을 넘길 기준이 되는 펄스 수. 색은 펄스 4개마다 다음 색으로 바뀐다 */
  @Volatile private var musicPulseCount = 0L
  /** 마지막 펄스 시각(elapsedRealtime ms). 펄스 최소 간격과 색 넘김 판정에 쓴다 */
  @Volatile private var musicPulseAt = 0L
  /** 느린 음량(0..1). 밝기 기준값에 쓰인다 */
  @Volatile private var musicLoudSlow = 0f

  /** 스피커 출력 소스의 시연 음원: 소리만 재생하고 분석은 outputTap이 한다(SPEC7 4절) */
  private var demoPlayer: DemoBeatSource? = null
  private var demoPlayerJob: Job? = null

  /** MainActivity가 RECORD_AUDIO 요청 런처를 설치한다 */
  var requestMicPermission: (() -> Unit)? = null

  /** MainActivity가 위치·FusedLocation 권한 요청 런처를 설치한다(SPEC14) */
  var requestLocationPermission: (() -> Unit)? = null

  // onCleared 시점에는 viewModelScope가 이미 닫히므로 종료 정리용 별도 스코프
  private val shutdownScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  init {
    // 저장된 집 위치를 읽어 상태에 반영한다(SPEC12 2-4절)
    val savedPrefs = getApplication<Application>().getSharedPreferences("pleos_camping", Context.MODE_PRIVATE)
    val savedHomeLat = savedPrefs.getString("guard_home_lat", null)?.toDoubleOrNull()
    val savedHomeLon = savedPrefs.getString("guard_home_lon", null)?.toDoubleOrNull()
    if (savedHomeLat != null && savedHomeLon != null) {
      _state.update { it.copy(guardHomeLat = savedHomeLat, guardHomeLon = savedHomeLon, guardHomeSaved = true) }
    }
    startMonitor()
    viewModelScope.launch {
      // 고른 소리 입력 소스만 조명에 반영한다(SPEC7 4절)
      micMeter.beat.collect { b -> if (_state.value.musicSource == MusicSource.MIC) onBeatState(b) }
    }
    viewModelScope.launch {
      outputTap.beat.collect { b -> if (_state.value.musicSource == MusicSource.OUTPUT) onBeatState(b) }
    }
  }

  /** MainActivity에서 Activity 창 밝기를 연결한다 */
  fun installWindowBrightness(read: () -> Float, apply: (Float) -> Unit) {
    controller.readWindowBrightness = read
    controller.applyWindowBrightness = apply
  }

  fun onAppForeground() {
    foreground = true
    startMonitor()
    restartHeartbeat()
  }

  /** 백그라운드로 가면 모드 복원 후 SDK를 해제한다 */
  fun onAppBackground() {
    foreground = false
    heartbeatJob?.cancel()
    heartbeatJob = null
    viewModelScope.launch {
      stopModeSideEffects()
      _state.update { it.copy(busy = true) }
      stopMonitor()
      if (controller.activeMode != null) {
        val remaining = controller.disable()
        _state.update { it.copy(activeMode = null, snapshot = null) }
        resetModeSideEffectState()
        if (remaining.isNotEmpty()) showMessage("백그라운드 복원 일부 실패 — ${remaining.joinToString("; ") { it.label }}")
      }
      gateway.release()
      _state.update { it.copy(busy = false, sdkReady = false) }
    }
  }

  private fun startMonitor() {
    if (monitorJob?.isActive == true) return
    monitorJob = viewModelScope.launch {
      val initError = gateway.initialize()
      _state.update { it.copy(sdkReady = initError == null) }
      if (initError != null) {
        showMessage("차량 SDK 초기화 실패: $initError")
        return@launch
      }
      try {
        _state.update { it.copy(gear = gateway.currentGear()) }
      } catch (e: Exception) {
        showMessage("기어 조회 실패: ${describe(e)}")
      }
      launch {
        try {
          gateway.gearFlow().collect { gear -> onGearChanged(gear) }
        } catch (e: Exception) {
          showMessage("기어 구독 실패: ${describe(e)}")
        }
      }
      // 배터리·VC 상태 10초 폴링
      while (isActive) {
        try {
          _state.update { it.copy(batteryPercent = gateway.evBatteryPercent()) }
        } catch (e: Exception) {
          showMessage("배터리 조회 실패: ${describe(e)}")
        }
        if (BuildConfig.DEBUG) refreshVcState()
        delay(10_000)
      }
    }
  }

  private fun stopMonitor() {
    monitorJob?.cancel()
    monitorJob = null
  }

  private suspend fun refreshVcState() {
    val result = VcProbe.state()
    _state.update { it.copy(vcState = result, vcFailed = result == null) }
  }

  /** 기어가 P를 벗어나면 활성 모드를 즉시 끄고, P로 돌아오면 밀린 복원 항목을 다시 Set한다 */
  private fun onGearChanged(gear: VehicleGear) {
    val wasActive = controller.activeMode
    val wasPark = lastGear == VehicleGear.GEAR_PARK
    lastGear = gear
    _state.update { it.copy(gear = gear) }
    if (gear != VehicleGear.GEAR_PARK) {
      if (wasActive != null) {
        stopModeSideEffects()
        viewModelScope.launch {
          val remaining = controller.disable()
          _state.update { it.copy(activeMode = null, snapshot = null) }
          resetModeSideEffectState()
          if (remaining.isEmpty()) {
            showMessage("주행으로 바뀌어 원래 상태로 되돌렸어요")
          } else {
            showMessage("주행 중에는 실내등·독서등을 바꿀 수 없어 주차 시 되돌려요")
          }
        }
      }
    } else if (!wasPark && controller.pendingRestore.isNotEmpty()) {
      viewModelScope.launch {
        val failures = controller.retryPendingRestore()
        if (failures.isEmpty()) {
          showMessage("주차 상태로 돌아와 실내등·독서등을 원래대로 되돌렸어요")
        } else {
          showMessage("주차 복원 재시도 실패 — ${failures.joinToString("; ")}")
        }
      }
    }
  }

  /** 카드 터치로 아래 패널에 보여 줄 모드를 고른다(SPEC_UI 9절). 모드를 켜지는 않는다 */
  fun selectMode(mode: CampingMode) {
    _state.update { it.copy(selectedMode = mode) }
  }

  /** 같은 모드 토글이면 끄기, 다른 모드면 앞 모드 복원 뒤 새 모드 켜기 */
  fun toggleMode(mode: CampingMode) {
    val current = _state.value
    if (current.busy) return
    viewModelScope.launch {
      _state.update { it.copy(busy = true) }
      if (current.activeMode == mode) {
        stopModeSideEffects()
        turnOff()
        _state.update { it.copy(busy = false) }
      } else {
        if (current.activeMode != null) {
          stopModeSideEffects()
          val offRemaining = controller.disable()
          resetModeSideEffectState()
          if (offRemaining.isNotEmpty()) showMessage("이전 모드 복원 일부 실패 — ${offRemaining.joinToString("; ") { it.label }}")
        }
        val s = _state.value
        val errors = controller.enable(mode, s.campColor, s.campBrightness)
        _state.update {
          it.copy(
            activeMode = mode,
            selectedMode = mode,
            snapshot = controller.snapshot,
            cabinLightOn = false,
            readingLightOn = false,
            tailLampOn = false,
            busy = false
          )
        }
        refreshKeepScreenOn()
        if (errors.isNotEmpty()) showMessage("${mode.label} 켜기 일부 실패 — ${errors.joinToString("; ")}")
        // 캠핑 조명: 미리보기 값을 넣고 움직임 루프를 시작한다(SPEC10 1-2·1-4절)
        if (mode == CampingMode.CAMP_LIGHT) {
          _state.update { it.copy(campLightColor = s.campColor, campLightBrightness = s.campBrightness) }
          restartCampAnim()
        }
        // 음악 조명: 소리 듣기를 시작한다(권한이 없으면 요청)
        if (mode == CampingMode.MUSIC_LIGHT) {
          startMusicListening()
        }
        if (mode == CampingMode.BATTERY_GUARD) {
          // 첫 판정부터 적용한다
          guardStage = -1
          startGuardLoop()
        }
        if (mode == CampingMode.SUNRISE_ALARM) {
          refreshSunriseRecommendation()
        }
      }
    }
  }

  private suspend fun turnOff() {
    val remaining = controller.disable()
    _state.update { it.copy(activeMode = null, snapshot = null) }
    resetModeSideEffectState()
    if (remaining.isNotEmpty()) showMessage("되돌리기 일부 실패 — ${remaining.joinToString("; ") { it.label }}")
  }

  // --- 캠핑 조명 패널 조작 ---

  /** 색 프리셋을 고른다. 취침 레드는 밝기도 15로 함께 Set하고, 휴식 블루는 10분 타이머를 건다 */
  fun setCampColor(color: Int) {
    cancelRestBlueTimer()
    val prevIsRed = (_state.value.campColor and 0xFFFFFF) == (CAMP_COLOR_RED and 0xFFFFFF)
    val newIsRed = (color and 0xFFFFFF) == (CAMP_COLOR_RED and 0xFFFFFF)
    if (newIsRed && !prevIsRed) campBrightnessBeforeRed = _state.value.campBrightness
    val restoreBrightness = if (!newIsRed && prevIsRed) campBrightnessBeforeRed else null
    if (!newIsRed && prevIsRed) campBrightnessBeforeRed = null
    viewModelScope.launch {
      try {
        gateway.setAmbientColor(AMBIENT_AREA, color)
        if ((color and 0xFFFFFF) == (CAMP_COLOR_RED and 0xFFFFFF)) {
          gateway.setAmbientBrightness(AMBIENT_AREA, CAMP_RED_BRIGHTNESS)
          _state.update {
            it.copy(
              campColor = color,
              campBrightness = CAMP_RED_BRIGHTNESS,
              campLightColor = color,
              campLightBrightness = CAMP_RED_BRIGHTNESS,
              restBlueDeadline = null
            )
          }
        } else {
          if (restoreBrightness != null) {
            gateway.setAmbientBrightness(AMBIENT_AREA, restoreBrightness)
            _state.update {
              it.copy(
                campColor = color,
                campBrightness = restoreBrightness,
                campLightColor = color,
                campLightBrightness = restoreBrightness,
                restBlueDeadline = null
              )
            }
          } else {
            _state.update { it.copy(campColor = color, campLightColor = color, restBlueDeadline = null) }
          }
        }
        // 소리가 켜져 있으면 새 색의 프리셋 소리로 바꿔 튼다(SPEC16 3절)
        if (_state.value.campFireSound) {
          val p = presetForColor(color)
          if (p == null || !firePlayer.play(p.soundRes)) stopFireSound()
        }
        if ((color and 0xFFFFFF) == (CAMP_COLOR_REST_BLUE and 0xFFFFFF)) {
          startRestBlueTimer()
        }
        restartCampAnim()
      } catch (e: Exception) {
        showMessage("색 변경 실패: ${describe(e)}")
      }
    }
  }

  /** 휴식 블루 10분 뒤 랜턴으로 바꾸는 타이머 */
  private fun startRestBlueTimer() {
    restBlueJob?.cancel()
    val deadline = System.currentTimeMillis() + REST_BLUE_DURATION_MS
    _state.update { it.copy(restBlueDeadline = deadline) }
    restBlueJob = viewModelScope.launch {
      delay(REST_BLUE_DURATION_MS)
      _state.update { it.copy(restBlueDeadline = null) }
      if (_state.value.activeMode == CampingMode.CAMP_LIGHT) {
        showMessage("10분이 지나 랜턴 색으로 바꿨어요")
        setCampColor(CAMP_COLOR_LANTERN)
      }
    }
  }

  /** 다른 색을 고르거나 모드가 꺼지면 타이머를 취소한다 */
  private fun cancelRestBlueTimer() {
    restBlueJob?.cancel()
    restBlueJob = null
    if (_state.value.restBlueDeadline != null) {
      _state.update { it.copy(restBlueDeadline = null) }
    }
  }

  fun setCampBrightness(value: Int) {
    viewModelScope.launch {
      try {
        gateway.setAmbientBrightness(AMBIENT_AREA, value)
        _state.update { it.copy(campBrightness = value, campLightBrightness = value) }
        restartCampAnim()
      } catch (e: Exception) {
        showMessage("밝기 변경 실패: ${describe(e)}")
      }
    }
  }

  /** 고른 색의 소리 토글(SPEC16 3절). 캠핑 조명이 켜져 있고 고른 색에 소리가 있어야 켤 수 있다 */
  fun setCampFireSound(on: Boolean) {
    if (!on) {
      stopFireSound()
      return
    }
    val s = _state.value
    if (s.activeMode != CampingMode.CAMP_LIGHT) {
      showMessage("캠핑 조명을 켠 뒤에 소리를 켤 수 있어요")
      return
    }
    val preset = presetForColor(s.campColor)
    if (preset == null) {
      showMessage("이 색에는 소리가 없어요")
      return
    }
    if (firePlayer.play(preset.soundRes)) {
      _state.update { it.copy(campFireSound = true) }
    } else {
      showMessage("소리를 틀 수 없어요")
    }
  }

  /** 장작 소리를 끄고 토글 상태를 물린다 */
  private fun stopFireSound() {
    firePlayer.stop()
    if (_state.value.campFireSound) {
      _state.update { it.copy(campFireSound = false) }
    }
  }

  /** 캠핑 조명 움직임 루프(SPEC10 1-2절). 조건이 맞으면 다시 시작하고, 안 맞으면 멈추기만 한다 */
  private fun restartCampAnim() {
    val wasRunning = campAnimJob?.isActive == true
    stopCampAnim()
    val s = _state.value
    val rgb = s.campColor and 0xFFFFFF
    val animated = rgb == (DEFAULT_CAMP_COLOR and 0xFFFFFF) || rgb == (CAMP_COLOR_REST_BLUE and 0xFFFFFF)
    if (s.activeMode != CampingMode.CAMP_LIGHT || !animated) {
      // 루프가 돌다가 다른 프리셋·모드 꺼짐으로 멈추면 기준 밝기로 한 번 되돌린다
      if (wasRunning) {
        viewModelScope.launch {
          try {
            gateway.setAmbientBrightness(AMBIENT_AREA, s.campBrightness)
          } catch (e: Exception) {
            Log.w(TAG, "캠핑 조명 기준 밝기 되돌리기 실패", e)
          }
        }
      }
      return
    }
    campAnimJob = viewModelScope.launch {
      val start = SystemClock.elapsedRealtime()
      while (isActive) {
        val st = _state.value
        if (st.activeMode != CampingMode.CAMP_LIGHT) break
        val nowRgb = st.campColor and 0xFFFFFF
        val base = st.campBrightness
        if (nowRgb == (DEFAULT_CAMP_COLOR and 0xFFFFFF)) {
          // 모닥불 일렁임: 부드러운 난수로 밝기(약 50~140%)와 색(주황↔호박)을 흔든다
          fireFlickerN += 0.6f * (Random.nextFloat() - fireFlickerN)
          fireColorN += 0.15f * (Random.nextFloat() - fireColorN)
          val brightness = (base * (0.50f + 0.90f * fireFlickerN)).roundToInt().coerceIn(5, 100)
          val color = lerpRgb(0xFFFF6A1A.toInt(), 0xFFFFA040.toInt(), fireColorN)
          sendCampAnimValues(brightness, color)
        } else if (nowRgb == (CAMP_COLOR_REST_BLUE and 0xFFFFFF)) {
          // 휴식 블루 숨쉬기: 6초 주기 사인파, 색은 그대로
          val t = (SystemClock.elapsedRealtime() - start).toFloat()
          val brightness = (base * (0.75f + 0.25f * sin(2.0 * PI * t / 6000f))).roundToInt().coerceIn(5, 100)
          sendCampAnimValues(brightness, null)
        } else {
          break
        }
        delay(CAMP_ANIM_INTERVAL_MS)
      }
    }
  }

  /** 움직임 루프만 멈춘다(기준 밝기 되돌리기 없이). 모드 끄기·백그라운드에서 쓴다 */
  private fun stopCampAnim() {
    campAnimJob?.cancel()
    campAnimJob = null
    lastCampAnimColor = null
    lastCampAnimBrightness = null
  }

  /** 루프가 계산한 밝기·색을 차에 보내고 미리보기에 반영한다. Set은 값이 변한 만큼만 */
  private suspend fun sendCampAnimValues(brightness: Int, color: Int?) {
    if (color != null) _state.update { it.copy(campLightColor = color) }
    _state.update { it.copy(campLightBrightness = brightness) }
    try {
      if (color != null) {
        val lastColor = lastCampAnimColor
        if (lastColor == null || abs(colorChannelSum(color) - colorChannelSum(lastColor)) >= 12) {
          gateway.setAmbientColor(AMBIENT_AREA, color)
          lastCampAnimColor = color
        }
      }
      val lastBrightness = lastCampAnimBrightness
      if (lastBrightness == null || abs(brightness - lastBrightness) >= 2) {
        gateway.setAmbientBrightness(AMBIENT_AREA, brightness)
        lastCampAnimBrightness = brightness
      }
    } catch (e: Exception) {
      // Set 실패는 로그만 남기고 루프를 계속 돈다
      Log.w(TAG, "캠핑 조명 움직임 Set 실패(계속)", e)
    }
  }

  /** 두 색을 t(0..1)로 RGB 선형 보간한 ARGB */
  private fun lerpRgb(a: Int, b: Int, t: Float): Int {
    fun channel(shift: Int): Int =
      ((a shr shift and 0xFF) + ((b shr shift and 0xFF) - (a shr shift and 0xFF)) * t).roundToInt()
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
  }

  /** 색의 R+G+B 합(색 변화 폭 판정용) */
  private fun colorChannelSum(color: Int): Int =
    (color shr 16 and 0xFF) + (color shr 8 and 0xFF) + (color and 0xFF)

  fun setCabinLight(on: Boolean) {
    viewModelScope.launch {
      try {
        gateway.setCabinSwitch(if (on) LightSwitch.ON else LightSwitch.OFF)
        _state.update { it.copy(cabinLightOn = on) }
      } catch (e: Exception) {
        showMessage("실내등 변경 실패: ${describe(e)}")
      }
    }
  }

  /** 독서등 2열 좌·우를 함께 제어한다 */
  fun setReadingLights(on: Boolean) {
    viewModelScope.launch {
      try {
        val value = if (on) LightSwitch.ON else LightSwitch.OFF
        gateway.setReadingSwitch(READING_LEFT_AREA, value)
        gateway.setReadingSwitch(READING_RIGHT_AREA, value)
        _state.update { it.copy(readingLightOn = on) }
      } catch (e: Exception) {
        showMessage("독서등 변경 실패: ${describe(e)}")
      }
    }
  }

  fun setTailLamp(on: Boolean) {
    viewModelScope.launch {
      try {
        gateway.setTailLampSwitch(if (on) LightSwitch.ON else LightSwitch.OFF)
        _state.update { it.copy(tailLampOn = on) }
      } catch (e: Exception) {
        showMessage("테일램프 변경 실패: ${describe(e)}")
      }
    }
  }

  // --- 음악 조명 (SPEC6 1절) ---

  /** 소리 듣기(스피커 출력 기본, 마이크 예비)를 시작하고 조명 연동 루프를 돈다. 권한이 없으면 요청한다 */
  private fun startMusicListening() {
    if (_state.value.activeMode != CampingMode.MUSIC_LIGHT) return
    if (_state.value.musicListening) return
    if (!hasMicPermission()) {
      askMicPermission()
      return
    }
    lastSentColor = null
    lastSentBrightness = null
    seenBeatCount = 0L
    musicEnv = 0f
    musicHold = 0f
    musicEnvAt = 0L
    musicPulseCount = 0L
    musicPulseAt = 0L
    musicLoudSlow = 0f
    if (_state.value.musicSource == MusicSource.OUTPUT) {
      if (!outputTap.start()) {
        // 출력 읽기를 못 만들면 마이크로 자동 대체한다(SPEC7 4절)
        showMessage("스피커 출력을 읽을 수 없어 마이크로 들어요")
        _state.update { it.copy(musicSource = MusicSource.MIC) }
        if (!micMeter.start()) {
          showMessage("마이크를 시작할 수 없어 박자를 들을 수 없어요")
          return
        }
      }
    } else if (!micMeter.start()) {
      showMessage("마이크를 시작할 수 없어 박자를 들을 수 없어요")
      return
    }
    _state.update {
      it.copy(musicListening = true, musicBpm = null, musicConfidence = 0f, musicLevel = 0f, musicBeatCount = 0L)
    }
    refreshKeepScreenOn()
    startLightSync()
  }

  private fun stopMusicListening() {
    stopDemoPlayer() // 스피커 출력 시연 음원이 돌고 있으면 함께 멈춘다(SPEC7 4절)
    micMeter.stopDemo() // 마이크 시연 음원이 돌고 있으면 함께 멈춘다(SPEC6 5절)
    demoTrackPlayer.stop() // 시연 곡이 돌고 있으면 함께 멈춘다(SPEC8 4-6절)
    outputTap.stop()
    micMeter.stop()
    lightSyncJob?.cancel()
    lightSyncJob = null
    lastSentColor = null
    lastSentBrightness = null
    seenBeatCount = 0L
    _state.update {
      it.copy(
        musicDemo = null,
        musicListening = false,
        musicBpm = null,
        musicConfidence = 0f,
        musicLevel = 0f,
        musicBeatCount = 0L,
        musicLightColor = null, // 미리보기도 끈다(SPEC8 4-8절)
        musicLightBrightness = 0
      )
    }
    refreshKeepScreenOn()
  }

  /** 모드가 꺼지거나 주행 전환·앱이 뒤로 갈 때 소리 듣기를 끊는다(기존 stopKaraokeIfNeeded 자리) */
  private fun stopMusicLightIfNeeded() {
    if (_state.value.activeMode == CampingMode.MUSIC_LIGHT) stopMusicListening()
  }

  /** 색 조합을 고른다(SPEC9 3-2절). 바꾸면 즉시 새 색이 반영되도록 마지막 전송 색을 지운다 */
  fun setMusicColors(c: MusicColors) {
    if (_state.value.musicColors == c) return
    lastSentColor = null
    _state.update { it.copy(musicColors = c) }
  }

  fun setMusicSensitivity(value: Int) {
    _state.update { it.copy(musicSensitivity = value.coerceIn(0, 100)) }
  }

  /** 소리 입력을 바꾼다. 듣는 중이면 끊고 새 소스로 다시 시작한다(SPEC7 4절) */
  fun setMusicSource(source: MusicSource) {
    if (_state.value.musicSource == source) return
    if (_state.value.musicListening) {
      stopMusicListening()
      _state.update { it.copy(musicSource = source) }
      startMusicListening()
    } else {
      _state.update { it.copy(musicSource = source) }
    }
  }

  // --- 시연용 음원 (SPEC6 5절, DEBUG 빌드 전용) ---

  /**
   * 시연 음원 재생을 시작한다(SPEC8 4-6절). 느린 곡·빠른 곡은 DemoTrackPlayer로 재생하고
   * 분석은 지금 고른 소리 입력이 한다(출력이면 outputTap, 마이크면 마이크가 스피커 소리를 들음).
   * 박자음은 기존 120 BPM 동작 그대로다. 다른 시연이 재생 중이면 먼저 멈춘다.
   */
  fun startMusicDemo(demo: MusicDemo) {
    if (_state.value.activeMode != CampingMode.MUSIC_LIGHT) return
    // 다른 시연이 재생 중이면 먼저 멈춘다
    stopDemoPlayer()
    micMeter.stopDemo()
    demoTrackPlayer.stop()
    if (demo == MusicDemo.BEAT) {
      // 기존 120 BPM 박자음(SPEC6 5절). 스피커 출력은 소리만 내고 분석은 outputTap이,
      // 마이크는 검출기에 직접 넣는다
      if (_state.value.musicSource == MusicSource.MIC) {
        if (!micMeter.startDemo(120)) {
          showMessage("시연 음원을 시작할 수 없어요")
          // startDemo가 마이크 녹음도 함께 멈췄으니 마이크 듣기로 되돌린다
          _state.update {
            it.copy(musicListening = false, musicBpm = null, musicConfidence = 0f, musicLevel = 0f, musicBeatCount = 0L)
          }
          startMusicListening()
          return
        }
      } else {
        if (!_state.value.musicListening && !outputTap.start()) {
          showMessage("시연 음원을 시작할 수 없어요")
          return
        }
        val source = DemoBeatSource(120) { _, _ -> }
        if (!source.start()) {
          showMessage("시연 음원을 시작할 수 없어요")
          return
        }
        demoPlayer = source
        demoPlayerJob = viewModelScope.launch(Dispatchers.Default) { source.loop() }
      }
    } else {
      // 느린 곡·빠른 곡: 시연 곡을 재생하고 분석은 지금 고른 소리 입력이 한다
      val resId = if (demo == MusicDemo.SLOW) R.raw.demo_slow else R.raw.demo_fast
      if (!demoTrackPlayer.play(resId)) {
        showMessage("시연 음원을 시작할 수 없어요")
        return
      }
      // 듣는 중이 아니면 startMusicListening과 같은 길로 듣기를 시작한다
      if (!_state.value.musicListening) startMusicListening()
    }
    lastSentColor = null
    lastSentBrightness = null
    seenBeatCount = 0L
    musicEnv = 0f
    musicHold = 0f
    musicEnvAt = 0L
    musicPulseCount = 0L
    musicPulseAt = 0L
    musicLoudSlow = 0f
    _state.update {
      it.copy(
        musicDemo = demo,
        musicListening = true,
        musicBpm = null,
        musicConfidence = 0f,
        musicLevel = 0f,
        musicBeatCount = 0L
      )
    }
    refreshKeepScreenOn()
    startLightSync()
  }

  /** 시연 음원을 끊고 모드가 아직 켜져 있으면 소리 듣기로 돌아간다 */
  fun stopMusicDemo() {
    if (_state.value.musicDemo == null) return
    stopDemoPlayer()
    micMeter.stopDemo()
    demoTrackPlayer.stop()
    _state.update {
      it.copy(
        musicDemo = null,
        musicListening = false,
        musicBpm = null,
        musicConfidence = 0f,
        musicLevel = 0f,
        musicBeatCount = 0L
      )
    }
    if (_state.value.activeMode == CampingMode.MUSIC_LIGHT) {
      startMusicListening()
    }
  }

  /** 스피커 출력용 시연 음원 재생을 멈춘다 */
  private fun stopDemoPlayer() {
    demoPlayerJob?.cancel()
    demoPlayerJob = null
    demoPlayer?.stop()
    demoPlayer = null
  }

  /** 박자 상태 반영. 펄스에서 musicEnv를 올리고 느린 음량(musicLoudSlow)을 따라간다(SPEC9 3-3절).
   *  스피커 출력은 OutputTap이 계산한 punch를 쓰고 마이크는 박이 새로 오면 punch를 1로 친다 */
  private fun onBeatState(b: BeatState) {
    val now = SystemClock.elapsedRealtime()
    val mic = _state.value.musicSource == MusicSource.MIC
    // 마이크는 level이 작게 나와서 증폭해 쓴다
    val level = if (mic) min(1.0, b.level * 4.0).toFloat() else b.level
    val punch = if (mic) {
      if (b.beatCount > seenBeatCount) {
        seenBeatCount = b.beatCount
        1f
      } else {
        0f
      }
    } else {
      b.punch
    }
    musicEnv = max(punch, (musicEnv * exp(-(now - musicEnvAt) / MUSIC_RELEASE_MS)).toFloat())
    musicEnvAt = now
    musicHold = max(musicHold, musicEnv)
    if (punch >= MUSIC_PULSE_THRESHOLD && now - musicPulseAt >= MUSIC_PULSE_MIN_GAP_MS) {
      musicPulseCount++
      musicPulseAt = now
    }
    musicLoudSlow += 0.05f * (level - musicLoudSlow)
    _state.update {
      it.copy(
        musicListening = b.listening && it.activeMode == CampingMode.MUSIC_LIGHT,
        musicBpm = b.bpm,
        musicConfidence = b.confidence,
        musicLevel = b.level,
        // 화면의 박 표시 점: 출력은 펄스 수, 마이크는 검출기 박 수를 넣는다
        musicBeatCount = if (mic) b.beatCount else musicPulseCount
      )
    }
  }

  // --- 모드 부수 효과(재생·타이머) 정리 ---

  /** 활성 모드의 재생·가드 루프·알람 타이머를 즉시 멈춘다. 스냅샷 복원은 호출부의 disable이 맡는다 */
  private fun stopModeSideEffects() {
    stopMusicLightIfNeeded()
    stopGuardLoop()
    stopSunriseAlarm()
    cancelRestBlueTimer()
    stopCampAnim()
    stopFireSound()
  }

  /** 모드가 완전히 꺼진 뒤 배터리 가드·일출 알람 상태를 초기화한다 */
  private fun resetModeSideEffectState() {
    _state.update {
      it.copy(
        guardResult = null,
        guardComfort = null,
        sunriseArmed = false,
        sunriseRinging = false,
        sunriseDemo = false,
        sunriseStatus = null,
        sunriseTargetMillis = null,
        sunriseRecommendedMillis = null,
        sunriseActualMillis = null,
        sunriseIsRecommended = false,
        restBlueDeadline = null,
        campFireSound = false,
        campLightColor = null,
        campLightBrightness = 0,
        selectedConstellation = null
      )
    }
    refreshKeepScreenOn()
  }

  /** 화면 켜짐 유지: 음악 조명 듣는 중, 배터리 가드 활성, 일출 알람 대기·울림 중 */
  private fun refreshKeepScreenOn() {
    val s = _state.value
    val keep = s.musicListening ||
      s.activeMode == CampingMode.BATTERY_GUARD ||
      (s.activeMode == CampingMode.SUNRISE_ALARM && (s.sunriseArmed || s.sunriseRinging))
    _state.update { it.copy(keepScreenOn = keep) }
  }

  // --- 배터리 가드 ---

  /** 현재 위치와 집 위치로 집까지 거리를 다시 계산한다. 현재 위치를 모르면 50 km로 둔다 */
  private fun refreshGuardHomeKm() {
    val s = _state.value
    val km = if (s.usingDefaultLocation) GUARD_FALLBACK_HOME_KM
      else HomeDistance.roadKm(s.latitude, s.longitude, s.guardHomeLat, s.guardHomeLon)
    if (km != s.guardHomeKm) {
      _state.update { it.copy(guardHomeKm = km) }
      reevaluateGuardSoon()
    }
  }

  /** 지금 위치를 집으로 저장한다 */
  fun saveGuardHomeHere() {
    val s = _state.value
    if (s.usingDefaultLocation) {
      showMessage("현재 위치를 알 수 없어 집을 저장하지 못했어요")
      return
    }
    getApplication<Application>().getSharedPreferences("pleos_camping", Context.MODE_PRIVATE)
      .edit()
      .putString("guard_home_lat", s.latitude.toString())
      .putString("guard_home_lon", s.longitude.toString())
      .apply()
    _state.update { it.copy(guardHomeLat = s.latitude, guardHomeLon = s.longitude, guardHomeSaved = true) }
    refreshGuardHomeKm()
    showMessage("지금 위치를 집으로 저장했어요")
  }

  /** 집 위치를 기본값(서울)으로 되돌린다 */
  fun resetGuardHome() {
    getApplication<Application>().getSharedPreferences("pleos_camping", Context.MODE_PRIVATE)
      .edit()
      .remove("guard_home_lat")
      .remove("guard_home_lon")
      .apply()
    _state.update {
      it.copy(guardHomeLat = HomeDistance.DEFAULT_HOME_LAT, guardHomeLon = HomeDistance.DEFAULT_HOME_LON, guardHomeSaved = false)
    }
    refreshGuardHomeKm()
    showMessage("집 위치를 기본값(서울)으로 바꿨어요")
  }

  fun setGuardAllowHvacOff(on: Boolean) {
    // 반영은 다음 3단계 진입 때다
    _state.update { it.copy(guardAllowHvacOff = on) }
  }

  /** 설정을 바꾸면 다음 주기를 기다리지 않고 다시 판정한다(적용 단계는 유지해 히스테리시스가 유지된다) */
  private fun reevaluateGuardSoon() {
    if (_state.value.activeMode == CampingMode.BATTERY_GUARD) startGuardLoop()
  }

  /** 5초마다 배터리·전비를 읽어 단계를 다시 판정한다. 시작 직후 한 번 바로 평가한다 */
  private fun startGuardLoop() {
    guardJob?.cancel()
    guardJob = viewModelScope.launch {
      while (isActive && _state.value.activeMode == CampingMode.BATTERY_GUARD) {
        evaluateGuardOnce()
        delay(GUARD_INTERVAL_MS)
      }
    }
  }

  private fun stopGuardLoop() {
    guardJob?.cancel()
    guardJob = null
  }

  private suspend fun evaluateGuardOnce() {
    val s = _state.value
    val battery = orNull { gateway.evBatteryPercent() }
    val vehicleEfficiency = orNull { gateway.evEfficiencyKmPerKwh() }?.takeIf { it > 0f }
    val capacity = orNull { gateway.evBatteryCapacityKwh() }
    val result = BatteryGuard.evaluate(
      GuardInput(
        batteryPercent = battery ?: s.batteryPercent,
        homeKm = s.guardHomeKm,
        marginPct = s.guardMarginPct,
        kmPerKwh = vehicleEfficiency ?: s.guardEfficiency,
        capacityKwh = capacity,
        previousStage = guardStage
      )
    )
    _state.update {
      it.copy(
        batteryPercent = battery ?: it.batteryPercent,
        guardVehicleEfficiency = vehicleEfficiency,
        guardResult = result
      )
    }
    refreshGuardComfort()
    // 단계가 바뀔 때만 그 단계 동작을 보낸다
    if (result.stage != guardStage) {
      val previous = guardStage
      guardStage = result.stage
      applyGuardStage(result.stage, previous)
    }
  }

  /** 패널의 현재 공조 상태 한 줄을 갱신한다 */
  private suspend fun refreshGuardComfort() {
    val d = DevComfort(
      hvacPower = orNull { gateway.getHvacPower() },
      hvacAuto = orNull { gateway.getHvacAuto() },
      hvacAc = orNull { gateway.getHvacAc() },
      tempDriver = orNull { gateway.getHvacTemperature(DRIVER_AREA) },
      fanSpeed = orNull { gateway.getHvacFanSpeed() },
      backrestLeft = null,
      backrestRight = null
    )
    _state.update { it.copy(guardComfort = d) }
  }

  private suspend fun applyGuardStage(stage: Int, previous: Int) {
    when {
      stage == 0 -> if (previous >= 1) {
        // 단계가 내려와 0이 되면 스냅샷 값으로 되돌린다
        restoreGuardFromSnapshot()
        showMessage("배터리 여유가 회복되어 공조·조명을 원래대로 되돌렸어요")
      }
      // 단계가 내려가면 공조 전원이 꺼져 팬 Set이 안 되므로 복원을 먼저 하고 1단계부터 다시 적용한다
      stage < previous -> {
        restoreGuardFromSnapshot()
        for (s in 1..stage) when (s) {
          1 -> applyGuardStage1()
          2 -> applyGuardStage2()
          else -> applyGuardStage3()
        }
      }
      // 단계가 올라가면 빠진 중간 단계 동작을 순서대로 모두 실행한다
      else -> for (s in maxOf(previous + 1, 1)..stage) when (s) {
        1 -> applyGuardStage1()
        2 -> applyGuardStage2()
        else -> applyGuardStage3()
      }
    }
  }

  /** 가드 1단계: 팬 속도를 2 이하로 줄인다 */
  private suspend fun applyGuardStage1() {
    val fan = minOf(controller.comfortSnapshot?.fanSpeed ?: 2, 2)
    guardStep("팬 ${fan}으로 줄이기") { gateway.setHvacFanSpeed(fan) }
  }

  /** 가드 2단계: 팬 1, 온도 22, AC 끄기, 무드 밝기 20, 실내등 끄기 */
  private suspend fun applyGuardStage2() {
    guardStep("팬 1") { gateway.setHvacFanSpeed(1) }
    guardStep("온도 22.0") {
      gateway.setHvacTemperature(DRIVER_AREA, 22.0f)
      gateway.setHvacTemperature(PASSENGER_AREA, 22.0f)
    }
    guardStep("AC 끄기") { gateway.setHvacAc(false) }
    guardStep("무드 조명 밝기 20") {
      gateway.setAmbientSwitch(AMBIENT_AREA, LightSwitch.ON)
      gateway.setAmbientBrightness(AMBIENT_AREA, 20)
    }
    guardStep("실내등 끄기") { gateway.setCabinSwitch(LightSwitch.OFF) }
  }

  /** 가드 3단계: 공조 끄기(설정 시), 무드 조명 끄기, chime 3회 */
  private suspend fun applyGuardStage3() {
    // 실측: 공조 전원을 끄기 전에 팬 값을 먼저 맞춘다
    if (_state.value.guardAllowHvacOff) {
      guardStep("팬 1 맞춤") { gateway.setHvacFanSpeed(1) }
      guardStep("공조 끄기") { gateway.setHvacPower(false) }
    }
    guardStep("무드 조명 끄기") {
      gateway.setAmbientBrightness(AMBIENT_AREA, 0)
      gateway.setAmbientSwitch(AMBIENT_AREA, LightSwitch.OFF)
    }
    viewModelScope.launch {
      repeat(3) {
        toneSynth.chime()
        delay(GUARD_CHIME_INTERVAL_MS)
      }
    }
  }

  /** 가드가 바꾼 공조·조명을 모드를 켜기 전 값으로 되돌린다(모드는 유지한다) */
  private suspend fun restoreGuardFromSnapshot() {
    val cs = controller.comfortSnapshot
    val s = controller.snapshot
    if (cs != null) {
      // 팬 Set은 공조 전원이 켜져 있어야 하므로 전원 ON은 맨 앞에, OFF는 맨 마지막에 보낸다
      if (cs.hvacPower == true) guardStep("공조 전원 켜기") { gateway.setHvacPower(true) }
      cs.tempDriver?.let { v -> guardStep("온도 운전석 복원") { gateway.setHvacTemperature(DRIVER_AREA, v) } }
      cs.tempPassenger?.let { v -> guardStep("온도 동승석 복원") { gateway.setHvacTemperature(PASSENGER_AREA, v) } }
      cs.fanSpeed?.let { v -> guardStep("팬 복원") { gateway.setHvacFanSpeed(v) } }
      cs.hvacAc?.let { v -> guardStep("AC 복원") { gateway.setHvacAc(v) } }
      if (cs.hvacPower == false) guardStep("공조 전원 끄기") { gateway.setHvacPower(false) }
    }
    if (s != null) {
      s.ambientBrightness?.let { v ->
        guardStep("무드 조명 밝기 복원") {
          if (v > 0) gateway.setAmbientSwitch(AMBIENT_AREA, LightSwitch.ON)
          gateway.setAmbientBrightness(AMBIENT_AREA, v)
        }
      }
      s.cabinSwitch?.let { v -> guardStep("실내등 복원") { gateway.setCabinSwitch(v) } }
    }
  }

  private suspend fun guardStep(label: String, action: suspend () -> Unit) {
    try {
      action()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      showMessage("배터리 가드 $label 실패: ${describe(e)}")
    }
  }

  /**
   * 조명 연동 루프(기존 노래방 startLightSync 자리 재사용, SPEC6 1-4절·SPEC7 4절·SPEC9 3-3절).
   * 음악이 들리는 동안 소리 세기(musicEnv)만으로 밝기를 정한다. 색은 펄스 4개마다 다음 색.
   * 음악이 안 들리면 2초 주기로 30↔50 숨쉬기.
   * 차량 Set은 최소 200ms 간격을 지키고 밝기는 마지막 값과 2 이상 다를 때만 보낸다.
   */
  private fun startLightSync() {
    lightSyncJob?.cancel()
    lightSyncJob = viewModelScope.launch {
      var lastSetAt = 0L
      while (isActive && _state.value.activeMode == CampingMode.MUSIC_LIGHT && _state.value.musicListening) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSetAt >= MUSIC_SET_INTERVAL_MS) {
          lastSetAt = now
          sendMusicLightFrame(now)
        }
        delay(LIGHT_SYNC_TICK_MS)
      }
    }
  }

  /** 이번 주기에 보낼 색·밝기를 정해 Set한다(SPEC9 3-3절). 음악이 들리는 동안 소리 세기 하나로
   *  밝기를 정하고 색은 펄스 4개마다 다음 색, 안 들리면 숨쉬기(30↔50)와 색 조합의 첫 색을 쓴다 */
  private suspend fun sendMusicLightFrame(now: Long) {
    val s = _state.value
    val colors = s.musicColors.colors
    val color: Int
    val brightness: Int
    if (s.musicLevel > 0.02f) {
      // Set와 Set 사이에 놓친 최댓값(musicHold)까지 합쳐 세기를 잡고, 다음 프레임을 위해 비운다
      val env = max(musicHold, (musicEnv * exp(-(now - musicEnvAt) / MUSIC_RELEASE_MS)).toFloat())
      musicHold = 0f
      brightness = (25.0 + 20.0 * musicLoudSlow + 55.0 * (s.musicSensitivity / 50.0) * env)
        .toInt().coerceIn(10, 100)
      // 색은 펄스 4개마다 다음 색. 펄스가 오래 멎으면 8초마다 다음 색으로 넘어간다
      var colorStep = musicPulseCount / 4L
      val sincePulse = now - musicPulseAt
      if (musicPulseAt > 0L && sincePulse > MUSIC_COLOR_FALLBACK_MS) {
        colorStep += 1L + (sincePulse - MUSIC_COLOR_FALLBACK_MS) / MUSIC_COLOR_FALLBACK_MS
      }
      color = colors[(colorStep % colors.size).toInt()]
    } else {
      // 숨쉬기: 2초 삼각파로 30↔50, 색은 색 조합의 첫 색
      val cycle = (now % (2L * BREATH_PERIOD_MS)).toDouble() / BREATH_PERIOD_MS // 0..2
      val frac = if (cycle <= 1.0) cycle else 2.0 - cycle
      brightness = (BREATH_MIN_BRIGHTNESS + (BREATH_MAX_BRIGHTNESS - BREATH_MIN_BRIGHTNESS) * frac).toInt()
      color = colors.first()
    }
    if (color != lastSentColor) {
      try {
        gateway.setAmbientColor(AMBIENT_AREA, color)
        lastSentColor = color
      } catch (e: Exception) {
        Log.w(TAG, "음악 조명 색 실패", e)
      }
    }
    // 밝기는 마지막으로 보낸 값과 2 이상 다를 때만 보낸다(차량 Set 아끼기)
    val lastBrightness = lastSentBrightness
    if (lastBrightness == null || abs(brightness - lastBrightness) >= 2) {
      try {
        gateway.setAmbientBrightness(AMBIENT_AREA, brightness)
        lastSentBrightness = brightness
      } catch (e: Exception) {
        Log.w(TAG, "음악 조명 밝기 실패", e)
      }
    }
    // 미리보기 갱신: 색이 다르거나 밝기 차이가 2 이상일 때만 상태를 바꾼다(SPEC8 4-8절)
    val cur = _state.value
    if (cur.musicLightColor != color || abs(brightness - cur.musicLightBrightness) >= 2) {
      _state.update { it.copy(musicLightColor = color, musicLightBrightness = brightness) }
    }
  }

  private fun hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

  /** RECORD_AUDIO 요청 결과. 허용되면 바로 듣기를 시작하고 거부되면 안내한다 */
  fun onMicPermissionResult(granted: Boolean) {
    if (_state.value.activeMode != CampingMode.MUSIC_LIGHT) return
    if (granted) {
      startMusicListening()
    } else {
      showMessage("오디오 권한이 없어 음악을 들을 수 없어요")
    }
  }

  /** 위치·FusedLocation 권한을 묻기 전 안내(SPEC14 1-2절). 처음이면 안내를 띄우고 아니면 곧바로 요청한다 */
  fun askLocationPermission() {
    val prefs = getApplication<Application>().getSharedPreferences("pleos_camping", Context.MODE_PRIVATE)
    if (prefs.getBoolean("perm_notice_location_shown", false)) {
      requestLocationPermission?.invoke()
      return
    }
    // 이미 같은 안내가 떠 있으면 상태를 다시 바꾸지 않는다
    if (_state.value.permissionNotice == PermissionNotice.LOCATION) return
    _state.update { it.copy(permissionNotice = PermissionNotice.LOCATION) }
  }

  /** 오디오 권한을 묻기 전 안내(SPEC14 1-3절). 처음이면 안내를 띄우고 아니면 곧바로 요청한다 */
  private fun askMicPermission() {
    val prefs = getApplication<Application>().getSharedPreferences("pleos_camping", Context.MODE_PRIVATE)
    if (prefs.getBoolean("perm_notice_audio_shown", false)) {
      requestMicPermission?.invoke()
      return
    }
    // 이미 같은 안내가 떠 있으면 상태를 다시 바꾸지 않는다
    if (_state.value.permissionNotice == PermissionNotice.AUDIO) return
    _state.update { it.copy(permissionNotice = PermissionNotice.AUDIO) }
  }

  /** 권한 안내 창의 [확인](SPEC14 1-4절). 안내를 닫고 해당 시스템 권한 창을 띄운다 */
  fun confirmPermissionNotice() {
    val notice = _state.value.permissionNotice ?: return
    _state.update { it.copy(permissionNotice = null) }
    getApplication<Application>().getSharedPreferences("pleos_camping", Context.MODE_PRIVATE)
      .edit()
      .putBoolean(
        if (notice == PermissionNotice.LOCATION) "perm_notice_location_shown" else "perm_notice_audio_shown",
        true
      )
      .apply()
    if (notice == PermissionNotice.LOCATION) {
      requestLocationPermission?.invoke()
    } else {
      requestMicPermission?.invoke()
    }
  }

  // --- 별 보기: 차량 방위·별자리 강조 (SPEC4 1절) ---

  /** FusedLocation 권한 결과. 허용되면 차량 방위(heading) 구독을 시작한다 */
  fun onFusedLocationPermissionResult(granted: Boolean) {
    if (granted) startHeadingUpdates()
  }

  /** FusedLocation으로 차량 방위를 받아 온다. 미지원·예외는 로그만 남긴다 */
  private fun startHeadingUpdates() {
    if (fusedLocation != null) return
    try {
      val fl = FusedLocation(getApplication<Application>().applicationContext)
      fl.initialize()
      if (!fl.isSupported()) return
      val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
          if (location != null && location.confidence > 0f) {
            _state.update { it.copy(vehicleHeadingDeg = location.heading) }
          }
        }

        override fun onError(exception: Exception) {
          Log.w(HEADING_TAG, "차량 방위 오류", exception)
        }
      }
      fl.registerFusedLocationCallback(listener)
      fusedLocation = fl
      headingListener = listener
    } catch (e: Exception) {
      Log.w(HEADING_TAG, "차량 방위 구독 실패", e)
    }
  }

  /** 사용자가 고른 "앞쪽" 방위. null이면 차량 방위(자동)를 쓴다 */
  fun setManualHeading(deg: Float?) {
    _state.update { it.copy(manualHeadingDeg = deg) }
  }

  /** 강조할 별자리 약자. 같은 id를 다시 고르면 해제(null)한다 */
  fun selectConstellation(id: String?) {
    _state.update { it.copy(selectedConstellation = if (id != null && id == it.selectedConstellation) null else id) }
  }

  // --- 일출 알람 ---

  /** 알람 시각 조정. −/+로 직접 고르면 "직접 설정"이 된다. 설정 중(armed)에는 바꾸지 못한다 */
  fun setSunriseTime(hour: Int, minute: Int) {
    if (_state.value.sunriseArmed) return
    _state.update {
      it.copy(sunriseHour = hour.coerceIn(0, 23), sunriseMinute = minute.coerceIn(0, 59), sunriseIsRecommended = false)
    }
  }

  fun setSunrisePreHvac(on: Boolean) {
    _state.update { it.copy(sunrisePreHvac = on) }
  }

  fun setSunriseSeatUp(on: Boolean) {
    _state.update { it.copy(sunriseSeatUp = on) }
  }

  fun setSunriseSound(on: Boolean) {
    _state.update { it.copy(sunriseSound = on) }
  }

  fun setSunriseKeepHvac(on: Boolean) {
    _state.update { it.copy(sunriseKeepHvac = on) }
  }

  // --- 일출 알람: 추천 시간 (SPEC6 3절) ---

  /**
   * 다음 시민박명 시작·일출을 계산해 알람 시·분을 추천 시간으로 채운다.
   * 모드를 켤 때와 위치가 바뀔 때 부른다. armed면 시·분은 그대로 두고 추천값만 갱신한다.
   */
  private fun refreshSunriseRecommendation() {
    val s = _state.value
    viewModelScope.launch(Dispatchers.Default) {
      val result = try {
        SunTimes.next(s.latitude, s.longitude, System.currentTimeMillis())
      } catch (e: Exception) {
        Log.w(TAG, "일출 계산 실패: ${describe(e)}")
        null
      }
      _state.update { cur ->
        if (result == null) {
          cur.copy(sunriseRecommendedMillis = null, sunriseActualMillis = null, sunriseIsRecommended = false)
        } else if (cur.sunriseArmed || cur.sunriseRinging) {
          cur.copy(sunriseRecommendedMillis = result.civilDawnMillis, sunriseActualMillis = result.sunriseMillis)
        } else {
          val civil = ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.civilDawnMillis), ZoneId.systemDefault())
          cur.copy(
            sunriseRecommendedMillis = result.civilDawnMillis,
            sunriseActualMillis = result.sunriseMillis,
            sunriseIsRecommended = true,
            sunriseHour = civil.hour,
            sunriseMinute = civil.minute
          )
        }
      }
    }
  }

  /** [추천 시간으로] 버튼: 알람 시·분을 추천 시간(시민박명 시작)으로 되돌린다 */
  fun useRecommendedSunriseTime() {
    val rec = _state.value.sunriseRecommendedMillis
    if (rec == null) {
      showMessage("이 위치에서는 일출 시각을 계산할 수 없어요")
      return
    }
    val civil = ZonedDateTime.ofInstant(Instant.ofEpochMilli(rec), ZoneId.systemDefault())
    _state.update {
      it.copy(sunriseHour = civil.hour, sunriseMinute = civil.minute, sunriseIsRecommended = true)
    }
  }

  /** 설정한 시·분으로 다음 알람 시각(epoch ms)을 구한다. 지났으면 내일 같은 시각 */
  private fun nextAlarmMillis(): Long {
    val zone = ZoneId.systemDefault()
    val now = ZonedDateTime.now(zone)
    var target = now.toLocalDate().atTime(_state.value.sunriseHour, _state.value.sunriseMinute).atZone(zone)
    if (!target.toInstant().isAfter(now.toInstant())) target = target.plusDays(1)
    return target.toInstant().toEpochMilli()
  }

  /** 알람을 건다. demo면 알람 시각을 지금+3분으로 잡고 시간표를 압축해 실행한다 */
  fun armSunrise(demo: Boolean) {
    if (_state.value.activeMode != CampingMode.SUNRISE_ALARM) return
    val target = if (demo) System.currentTimeMillis() + SUNRISE_DEMO_MS else nextAlarmMillis()
    sunriseJob?.cancel()
    _state.update {
      it.copy(sunriseArmed = true, sunriseRinging = false, sunriseDemo = demo, sunriseStatus = null, sunriseTargetMillis = target)
    }
    refreshKeepScreenOn()
    sunriseJob = viewModelScope.launch {
      var lightStage = -1
      var hvacDone = false
      var seatDone = false
      while (isActive && _state.value.activeMode == CampingMode.SUNRISE_ALARM &&
        _state.value.sunriseArmed && !_state.value.sunriseRinging
      ) {
        val beforeMs = target - System.currentTimeMillis()
        val lightLeadMs = if (demo) DEMO_LIGHT_LEAD_MS else SUNRISE_LIGHT_LEAD_MS
        val stageMs = if (demo) DEMO_LIGHT_STEP_MS else SUNRISE_LIGHT_STEP_MS
        if (beforeMs <= lightLeadMs) {
          val stage = ((lightLeadMs - beforeMs) / stageMs).toInt().coerceIn(0, SUNRISE_LIGHT_STEPS - 1)
          if (stage > lightStage) {
            lightStage = stage
            applySunriseLight(stage)
            _state.update { it.copy(sunriseStatus = "조명 밝히는 중 ${stage + 1}/$SUNRISE_LIGHT_STEPS") }
          }
        }
        if (beforeMs <= (if (demo) DEMO_HVAC_LEAD_MS else SUNRISE_HVAC_LEAD_MS) && !hvacDone) {
          hvacDone = true
          if (_state.value.sunrisePreHvac) {
            alarmStep("공조 켜기") {
              gateway.setHvacPower(true)
              gateway.setHvacTemperature(DRIVER_AREA, 22.0f)
              gateway.setHvacTemperature(PASSENGER_AREA, 22.0f)
              gateway.setHvacAuto(true)
            }
            _state.update { it.copy(sunriseStatus = "공조 켬") }
          }
        }
        if (beforeMs <= (if (demo) DEMO_SEAT_LEAD_MS else SUNRISE_SEAT_LEAD_MS) && !seatDone) {
          seatDone = true
          if (_state.value.sunriseSeatUp) {
            viewModelScope.launch {
              alarmStep("등받이 세우기") {
                gateway.moveBackrest(READING_LEFT_AREA, MoveControl.FORWARD)
                gateway.moveBackrest(READING_RIGHT_AREA, MoveControl.FORWARD)
              }
              delay(SUNRISE_BACKREST_HOLD_MS)
              alarmStep("등받이 정지") {
                gateway.moveBackrest(READING_LEFT_AREA, MoveControl.STOP)
                gateway.moveBackrest(READING_RIGHT_AREA, MoveControl.STOP)
              }
            }
            _state.update { it.copy(sunriseStatus = "등받이 세움") }
          }
        }
        if (beforeMs <= 0) {
          alarmStep("실내등 켜기") { gateway.setCabinSwitch(LightSwitch.ON) }
          _state.update { it.copy(sunriseRinging = true, sunriseStatus = "알람 울림") }
          refreshKeepScreenOn()
          if (_state.value.sunriseSound) startAlarmSound()
          break
        }
        delay(1_000)
      }
    }
  }

  /** 조명 일출 단계 하나: 밝기 0→100, 색은 밤→주황→아침 빛으로 보간한다 */
  private suspend fun applySunriseLight(stage: Int) {
    val brightness = stage * 100 / (SUNRISE_LIGHT_STEPS - 1)
    alarmStep("일출 조명 ${stage + 1}") {
      if (stage == 0) gateway.setAmbientSwitch(AMBIENT_AREA, LightSwitch.ON)
      gateway.setAmbientBrightness(AMBIENT_AREA, brightness)
      gateway.setAmbientColor(AMBIENT_AREA, sunriseColor(stage))
    }
  }

  private fun sunriseColor(stage: Int): Int {
    val half = SUNRISE_LIGHT_STEPS / 2
    return if (stage < half) {
      lerpRgb(SUNRISE_COLOR_NIGHT, SUNRISE_COLOR_DAWN, stage.toDouble() / (half - 1))
    } else {
      lerpRgb(SUNRISE_COLOR_DAWN, SUNRISE_COLOR_MORNING, (stage - half).toDouble() / (half - 1))
    }
  }

  private fun lerpRgb(from: Int, to: Int, fraction: Double): Int {
    val f = fraction.coerceIn(0.0, 1.0)
    fun ch(shift: Int) = ((from shr shift and 0xFF) + (((to shr shift and 0xFF) - (from shr shift and 0xFF)) * f).toInt())
    return ch(16) shl 16 or (ch(8) shl 8) or ch(0)
  }

  private fun startAlarmSound() {
    alarmSoundJob?.cancel()
    alarmSoundJob = viewModelScope.launch {
      while (isActive && _state.value.sunriseRinging) {
        toneSynth.chime()
        delay(ALARM_CHIME_INTERVAL_MS)
      }
    }
  }

  private fun stopAlarmSound() {
    alarmSoundJob?.cancel()
    alarmSoundJob = null
  }

  private fun stopSunriseAlarm() {
    sunriseJob?.cancel()
    sunriseJob = null
    stopAlarmSound()
  }

  /** 알람 타이머와 소리만 끄고 상태를 되돌린다(스냅샷 복원은 호출부가 disable로 한다) */
  private fun resetSunriseAlarmState() {
    stopSunriseAlarm()
    _state.update {
      it.copy(sunriseArmed = false, sunriseRinging = false, sunriseDemo = false, sunriseStatus = null, sunriseTargetMillis = null)
    }
    refreshKeepScreenOn()
  }

  /** "일어났어요": 소리를 끄고 조명·등받이만 복원한다. 공조는 토글에 따라 켜 둔다 */
  fun sunriseWokeUp() {
    viewModelScope.launch {
      if (!_state.value.sunriseRinging || _state.value.busy) return@launch
      _state.update { it.copy(busy = true) }
      val keepHvac = _state.value.sunriseKeepHvac
      resetSunriseAlarmState()
      val remaining = controller.disable(keepHvac = keepHvac)
      _state.update { it.copy(activeMode = null, snapshot = null, busy = false) }
      resetModeSideEffectState()
      if (keepHvac) showMessage("좋은 아침이에요. 공조는 켜 둘게요")
      if (remaining.isNotEmpty()) showMessage("되돌리기 일부 실패 — ${remaining.joinToString("; ") { it.label }}")
    }
  }

  /** "알람 취소": 소리를 끄고 전부 복원한다 */
  fun cancelSunrise() {
    viewModelScope.launch {
      if (!_state.value.sunriseArmed && !_state.value.sunriseRinging) return@launch
      if (_state.value.busy) return@launch
      _state.update { it.copy(busy = true) }
      resetSunriseAlarmState()
      val remaining = controller.disable()
      _state.update { it.copy(activeMode = null, snapshot = null, busy = false) }
      resetModeSideEffectState()
      showMessage("알람을 취소하고 원래 상태로 되돌렸어요")
      if (remaining.isNotEmpty()) showMessage("되돌리기 일부 실패 — ${remaining.joinToString("; ") { it.label }}")
    }
  }

  private suspend fun alarmStep(label: String, action: suspend () -> Unit) {
    try {
      action()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      showMessage("일출 알람 $label 실패: ${describe(e)}")
    }
  }

  // --- 개발 시험 카드 ---

  fun devRefresh() {
    viewModelScope.launch {
      val d = DevComfort(
        hvacPower = orNull { gateway.getHvacPower() },
        hvacAuto = orNull { gateway.getHvacAuto() },
        hvacAc = orNull { gateway.getHvacAc() },
        tempDriver = orNull { gateway.getHvacTemperature(DRIVER_AREA) },
        fanSpeed = orNull { gateway.getHvacFanSpeed() },
        backrestLeft = orNull { gateway.getBackrestPosition(READING_LEFT_AREA) },
        backrestRight = orNull { gateway.getBackrestPosition(READING_RIGHT_AREA) }
      )
      _state.update { it.copy(devComfort = d) }
    }
  }

  /** 개발 시험 버튼. 성공·실패를 Snackbar로 보이고 끝나면 카드 값을 새로 읽는다 */
  fun devAction(label: String, action: suspend VehicleGateway.() -> Unit) {
    viewModelScope.launch {
      try {
        action(gateway)
        showMessage("$label 완료")
      } catch (e: Exception) {
        Log.w(TAG, "개발 시험 $label 실패", e)
        showMessage("$label: ${describe(e)}")
      }
      devRefresh()
    }
  }

  /** 주행 정책 실측 시험. 6초 뒤 공조·시트·조명을 순서대로 Set하고 결과를 DevTest 로그로 남긴다 */
  fun devDrivingTest() {
    viewModelScope.launch {
      delay(DEV_TEST_DELAY_MS)
      testStep("공조 전원 ON") { gateway.setHvacPower(true) }
      testStep("AUTO ON") { gateway.setHvacAuto(true) }
      testStep("AC ON") { gateway.setHvacAc(true) }
      testStep("운전석 온도 23.0") { gateway.setHvacTemperature(DRIVER_AREA, 23.0f) }
      testStep("팬 2") { gateway.setHvacFanSpeed(2) }
      testStep("등받이 2열 좌 FORWARD") { gateway.moveBackrest(READING_LEFT_AREA, MoveControl.FORWARD) }
      delay(BACKREST_STOP_DELAY_MS)
      testStep("등받이 2열 좌 STOP") { gateway.moveBackrest(READING_LEFT_AREA, MoveControl.STOP) }
      testStep("무드 조명 밝기 70") { gateway.setAmbientBrightness(AMBIENT_AREA, 70) }
      testStep("실내등 ON") { gateway.setCabinSwitch(LightSwitch.ON) }
      showMessage("주행 시험 끝 — logcat DevTest 참조")
    }
  }

  /** 시험 단계 하나를 실행하고 결과를 DevTest 로그로 남긴다 */
  private suspend fun testStep(name: String, set: suspend () -> Unit) {
    val result = try {
      set()
      "$name: OK"
    } catch (e: Exception) {
      "$name: ${describe(e)}"
    }
    Log.i(DEV_TEST_TAG, result)
  }

  // --- VC (개발용) ---

  fun setVcKeepAlive(on: Boolean) {
    _state.update { it.copy(vcKeepAlive = on) }
    restartHeartbeat()
  }

  private fun restartHeartbeat() {
    heartbeatJob?.cancel()
    heartbeatJob = null
    // VC 하트비트는 디버그에서만
    if (!BuildConfig.DEBUG || !foreground || !_state.value.vcKeepAlive) return
    heartbeatJob = viewModelScope.launch {
      while (isActive) {
        if (foreground) VcProbe.keepAlive()
        delay(1_500)
      }
    }
  }

  // --- 위치·메시지 ---

  fun updateLocation(latitude: Double, longitude: Double) {
    _state.update { it.copy(latitude = latitude, longitude = longitude, usingDefaultLocation = false) }
    // 위치가 바뀌면 집까지 거리를 다시 계산한다(SPEC12 2-5절)
    refreshGuardHomeKm()
    // 위치가 바뀌면 일출 알람 추천 시간을 다시 계산한다
    if (_state.value.activeMode == CampingMode.SUNRISE_ALARM) {
      refreshSunriseRecommendation()
    }
  }

  fun consumeMessage() {
    _state.update { it.copy(message = null) }
  }

  private fun showMessage(text: String) {
    _state.update { it.copy(message = text) }
  }

  private fun describe(e: Exception): String = "${e.javaClass.simpleName}(${e.message})"

  private suspend fun <T> orNull(block: suspend () -> T): T? = try {
    block()
  } catch (_: Exception) {
    null
  }

  override fun onCleared() {
    // 비정상 종료 경로 방어: 복원 후 SDK 해제
    shutdownScope.launch {
      if (controller.activeMode != null) controller.disable()
      runCatching { controller.retryPendingRestore() }
      gateway.release()
    }
    // 차량 방위 구독 해제 (SPEC4 1절)
    val fl = fusedLocation
    val listener = headingListener
    fusedLocation = null
    headingListener = null
    if (fl != null) {
      try {
        if (listener != null) fl.unregisterFusedLocationCallback(listener)
        fl.release()
      } catch (e: Exception) {
        Log.w(HEADING_TAG, "차량 방위 SDK 해제 실패", e)
      }
    }
    stopDemoPlayer()
    demoTrackPlayer.stop()
    firePlayer.stop()
    toneSynth.release()
    micMeter.release()
    outputTap.release()
    super.onCleared()
  }

  companion object {
    private const val TAG = "CampingViewModel"

    /** 차량 방위 로그 태그 */
    private const val HEADING_TAG = "Heading"

    /** 주행 시험 시작 전 대기 */
    private const val DEV_TEST_DELAY_MS = 6_000L

    /** 등받이 FORWARD 뒤 STOP까지의 대기 */
    private const val BACKREST_STOP_DELAY_MS = 2_000L

    /** 배터리 가드 평가 주기 */
    private const val GUARD_INTERVAL_MS = 5_000L

    /** 현재 위치를 모를 때 쓰는 집까지 거리 기본값 */
    private const val GUARD_FALLBACK_HOME_KM = 50

    /** 배터리 가드 위험 경보 chime 간격 */
    private const val GUARD_CHIME_INTERVAL_MS = 2_000L

    /** 일출 알람 chime 반복 간격 */
    private const val ALARM_CHIME_INTERVAL_MS = 2_000L

    /** 3분 시연 길이 */
    private const val SUNRISE_DEMO_MS = 3 * 60_000L

    /** 일출 조명 단계 수 */
    private const val SUNRISE_LIGHT_STEPS = 30

    /** 알람까지 조명·공조·등받이를 미리 시작하는 시점(정상) */
    private const val SUNRISE_LIGHT_LEAD_MS = 30 * 60_000L
    private const val SUNRISE_HVAC_LEAD_MS = 10 * 60_000L
    private const val SUNRISE_SEAT_LEAD_MS = 3 * 60_000L

    /** 정상 일출 조명 단계 간격 */
    private const val SUNRISE_LIGHT_STEP_MS = 60_000L

    /** 3분 시연에서의 압축 시점·간격 */
    private const val DEMO_LIGHT_LEAD_MS = 150_000L
    private const val DEMO_HVAC_LEAD_MS = 50_000L
    private const val DEMO_SEAT_LEAD_MS = 20_000L
    private const val DEMO_LIGHT_STEP_MS = 4_000L

    /** 일출 알람 등받이 FORWARD 뒤 STOP까지의 대기 */
    private const val SUNRISE_BACKREST_HOLD_MS = 3_000L

    /** 일출 조명 색(알파 없는 0xRRGGBB): 밤 → 주황 → 아침 빛 */
    private const val SUNRISE_COLOR_NIGHT = 0x1B2A6B
    private const val SUNRISE_COLOR_DAWN = 0xFF8C42
    private const val SUNRISE_COLOR_MORNING = 0xFFE3A3

    /** 주행 시험 결과 로그 태그 */
    private const val DEV_TEST_TAG = "DevTest"

    /** 음악 조명: 차량 Set 최소 간격(넘치는 Set은 건너뛴다) */
    private const val MUSIC_SET_INTERVAL_MS = 200L

    /** 음악 조명 연동 루프 틱 */
    private const val LIGHT_SYNC_TICK_MS = 50L

    /** 소리 세기 envelope가 꺼지는 시간 상수(SPEC9 3-3절) */
    private const val MUSIC_RELEASE_MS = 350.0

    /** 펄스로 치는 최소 punch 값과 펄스 최소 간격(SPEC9 3-3절) */
    private const val MUSIC_PULSE_THRESHOLD = 0.6f
    private const val MUSIC_PULSE_MIN_GAP_MS = 250L

    /** 펄스가 멎었을 때 색을 넘어가는 주기(SPEC9 3-3절) */
    private const val MUSIC_COLOR_FALLBACK_MS = 8_000L

    /** 무음·저신뢰 숨쉬기: 밝기 30↔50 */
    private const val BREATH_PERIOD_MS = 1_000L
    private const val BREATH_MIN_BRIGHTNESS = 30
    private const val BREATH_MAX_BRIGHTNESS = 50

    /** 휴식 블루 → 랜턴 자동 전환 시간 */
    private const val REST_BLUE_DURATION_MS = 10 * 60_000L

    /** 캠핑 조명 움직임 루프 주기(SPEC10 1-2절) */
    private const val CAMP_ANIM_INTERVAL_MS = 200L
  }
}
