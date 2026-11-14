package com.example.pleoscamping.mode

import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.MoveControl
import android.util.Log
import android.view.WindowManager
import com.example.pleoscamping.vehicle.AMBIENT_AREA
import com.example.pleoscamping.vehicle.CabinState
import com.example.pleoscamping.vehicle.ComfortState
import com.example.pleoscamping.vehicle.DRIVER_AREA
import com.example.pleoscamping.vehicle.PASSENGER_AREA
import com.example.pleoscamping.vehicle.READING_LEFT_AREA
import com.example.pleoscamping.vehicle.READING_RIGHT_AREA
import com.example.pleoscamping.vehicle.VehicleGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** 복원하지 못해 남긴 항목 하나. [apply]가 스냅샷에 저장해둔 값으로 다시 Set한다 */
class PendingRestoreItem(val label: String, val apply: suspend () -> Unit)

/**
 * 모드 켜기/끄기 로직.
 * 켜기 전 조명 스냅샷을 Get으로 저장하고, 끄기·주행 전환 때 역순으로 복원한다.
 * 각 단계 실패는 기록하고 다음 단계로 진행한다.
 * 복원 후 1.5초 뒤 값을 다시 읽어 어긋난 항목만 한 번 더 Set한다
 * (에뮬레이터 경쟁으로 Set 요청이 유실되는 경우를 보정).
 * 그래도 복원하지 못한 항목(주행 중 서비스가 거부하는 실내등·독서등 등)은
 * pendingRestore로 남겨 주차 복귀 때 다시 Set한다.
 */
class ModeController(private val gateway: VehicleGateway) {

  var activeMode: CampingMode? = null
    private set

  /** 주차 복귀 때 재시도할 항목 보관. 같은 이름은 최신 스냅샷 값으로 교체한다 */
  private val pending = mutableListOf<PendingRestoreItem>()

  /** 복원하지 못해 남긴 항목 목록 */
  val pendingRestore: List<PendingRestoreItem> get() = pending.toList()

  var snapshot: CabinState? = null
    private set

  /** 공조 스냅샷. usesHvac 모드를 켤 때만 만든다 */
  var comfortSnapshot: ComfortState? = null
    private set

  /** Activity 창 밝기 연결. ViewModel을 통해 MainActivity가 설치한다 */
  var readWindowBrightness: (() -> Float)? = null
  var applyWindowBrightness: ((Float) -> Unit)? = null
  private var savedBrightness: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

  private suspend fun <T> orNull(block: suspend () -> T): T? = try {
    block()
  } catch (_: Exception) {
    null
  }

  /** 한 단계 실행. @return 실패 시 "단계명: 예외클래스(메시지)", 성공 시 null */
  private suspend fun step(label: String, action: suspend () -> Unit): String? = try {
    action()
    null
  } catch (e: Exception) {
    "$label: ${e.javaClass.simpleName}(${e.message})"
  }

  /** 남은 항목을 pendingRestore에 보관한다. 같은 이름은 최신 값으로 교체한다 */
  private fun addPending(label: String, apply: suspend () -> Unit): PendingRestoreItem {
    val item = PendingRestoreItem(label, apply)
    pending.removeAll { it.label == label }
    pending.add(item)
    return item
  }

  /** 항목별로 Get을 시도해 스냅샷을 만든다. 실패 항목은 null */
  suspend fun captureSnapshot(): CabinState = CabinState(
    ambientSwitch = orNull { gateway.getAmbientSwitch(AMBIENT_AREA) },
    ambientBrightness = orNull { gateway.getAmbientBrightness(AMBIENT_AREA) },
    ambientColor = orNull { gateway.getAmbientColor(AMBIENT_AREA) },
    cabinSwitch = orNull { gateway.getCabinSwitch() },
    readingLeft = orNull { gateway.getReadingSwitch(READING_LEFT_AREA) },
    readingRight = orNull { gateway.getReadingSwitch(READING_RIGHT_AREA) },
    tailLamp = orNull { gateway.getTailLampSwitch() }
  )

  /** 공조 상태 스냅샷을 만든다. 실패 항목은 null */
  suspend fun captureComfort(): ComfortState = ComfortState(
    hvacPower = orNull { gateway.getHvacPower() },
    hvacAuto = orNull { gateway.getHvacAuto() },
    hvacAc = orNull { gateway.getHvacAc() },
    tempDriver = orNull { gateway.getHvacTemperature(DRIVER_AREA) },
    tempPassenger = orNull { gateway.getHvacTemperature(PASSENGER_AREA) },
    fanSpeed = orNull { gateway.getHvacFanSpeed() }
  )

  /** 모드를 켠다. @return 실패 목록(빈 목록이면 전부 성공) */
  suspend fun enable(mode: CampingMode, campColor: Int, campBrightness: Int): List<String> {
    val errors = mutableListOf<String>()
    snapshot = captureSnapshot()
    if (mode.usesHvac) comfortSnapshot = captureComfort()
    savedBrightness = try {
      readWindowBrightness?.invoke() ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    } catch (_: Exception) {
      WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }

    when (mode) {
      CampingMode.STARGAZING -> {
        step("실내등 끄기") { gateway.setCabinSwitch(LightSwitch.OFF) }?.let(errors::add)
        step("독서등 2열 좌 끄기") { gateway.setReadingSwitch(READING_LEFT_AREA, LightSwitch.OFF) }?.let(errors::add)
        step("독서등 2열 우 끄기") { gateway.setReadingSwitch(READING_RIGHT_AREA, LightSwitch.OFF) }?.let(errors::add)
        step("무드 조명 밝기 0") { gateway.setAmbientBrightness(AMBIENT_AREA, 0) }?.let(errors::add)
        step("무드 조명 끄기") { gateway.setAmbientSwitch(AMBIENT_AREA, LightSwitch.OFF) }?.let(errors::add)
        applyWindowBrightness?.invoke(0.05f)
      }
      CampingMode.CAMP_LIGHT -> {
        step("무드 조명 켜기") { gateway.setAmbientSwitch(AMBIENT_AREA, LightSwitch.ON) }?.let(errors::add)
        step("무드 조명 색") { gateway.setAmbientColor(AMBIENT_AREA, campColor) }?.let(errors::add)
        step("무드 조명 밝기") { gateway.setAmbientBrightness(AMBIENT_AREA, campBrightness) }?.let(errors::add)
      }
      CampingMode.MUSIC_LIGHT -> {
        step("무드 조명 켜기") { gateway.setAmbientSwitch(AMBIENT_AREA, LightSwitch.ON) }?.let(errors::add)
        step("무드 조명 밝기") { gateway.setAmbientBrightness(AMBIENT_AREA, MUSIC_LIGHT_BRIGHTNESS) }?.let(errors::add)
        step("무드 조명 색") { gateway.setAmbientColor(AMBIENT_AREA, MUSIC_LIGHT_COLOR) }?.let(errors::add)
        step("실내등 끄기") { gateway.setCabinSwitch(LightSwitch.OFF) }?.let(errors::add)
      }
      // 배터리 가드·일출 알람은 여기서 스냅샷만 저장하고 Set은 ViewModel의 단계 실행이 한다
      CampingMode.BATTERY_GUARD, CampingMode.SUNRISE_ALARM -> Unit
    }
    activeMode = mode
    return errors
  }

  /**
   * 스냅샷 역순 복원: 테일램프 → 독서등 → 실내등 → 무드(스위치→밝기→색) →
   * 공조(전원 ON이었으면 전원 맨 앞, 아니면 전원 OFF를 맨 마지막; 온도 운전석 → 온도 동승석 → 팬 → AC → AUTO) → 창 밝기.
   * Set 후 1.5초 기다렸다가 값을 다시 읽어 어긋난 항목만 한 번 더 Set한다.
   * 그래도 끝내지 못한 항목은 pendingRestore에 남기고 그 목록을 돌려준다.
   * 등받이는 원위치 보장이 없어 복원하지 않고 정지(STOP)만 보낸다.
   * '원래대로' 버튼과 기어 전환 복원이 이 함수를 함께 쓴다.
   * @param keepHvac true면 공조 복원을 건너뛴다(일출 알람 "일어났어요"용)
   * @return 이번 복원에서 처리하지 못한 항목(빈 목록이면 전부 복원)
   */
  suspend fun disable(keepHvac: Boolean = false): List<PendingRestoreItem> {
    val failedLabels = linkedSetOf<String>()

    /** 한 단계를 Set하고 실패하면 pendingRestore에 남긴다 */
    suspend fun restore(label: String, set: suspend () -> Unit) {
      if (step(label, set) != null) {
        val name = label.removeSuffix(" 복원")
        addPending(name, set)
        failedLabels.add(name)
      }
    }

    snapshot?.let { s ->
      s.tailLamp?.let { v -> restore("테일램프 복원") { gateway.setTailLampSwitch(v) } }
      s.readingLeft?.let { v -> restore("독서등 2열 좌 복원") { gateway.setReadingSwitch(READING_LEFT_AREA, v) } }
      s.readingRight?.let { v -> restore("독서등 2열 우 복원") { gateway.setReadingSwitch(READING_RIGHT_AREA, v) } }
      s.cabinSwitch?.let { v -> restore("실내등 복원") { gateway.setCabinSwitch(v) } }
      s.ambientSwitch?.let { v -> restore("무드 조명 스위치 복원") { gateway.setAmbientSwitch(AMBIENT_AREA, v) } }
      s.ambientBrightness?.let { v -> restore("무드 조명 밝기 복원") { gateway.setAmbientBrightness(AMBIENT_AREA, v) } }
      s.ambientColor?.let { v -> restore("무드 조명 색 복원") { gateway.setAmbientColor(AMBIENT_AREA, v) } }
    }
    if (!keepHvac) {
      comfortSnapshot?.let { cs ->
        // 공조 전원이 꺼진 상태에서 팬 Set은 NOT_AVAILABLE 오류가 나므로 전원 ON은 맨 앞에, OFF는 맨 마지막에 복원한다
        if (cs.hvacPower == true) {
          cs.hvacPower?.let { v -> restore("공조 전원 복원") { gateway.setHvacPower(v) } }
        }
        cs.tempDriver?.let { v -> restore("공조 온도 운전석 복원") { gateway.setHvacTemperature(DRIVER_AREA, v) } }
        cs.tempPassenger?.let { v -> restore("공조 온도 동승석 복원") { gateway.setHvacTemperature(PASSENGER_AREA, v) } }
        cs.fanSpeed?.let { v -> restore("공조 팬 복원") { gateway.setHvacFanSpeed(v) } }
        cs.hvacAc?.let { v -> restore("공조 AC 복원") { gateway.setHvacAc(v) } }
        cs.hvacAuto?.let { v -> restore("공조 AUTO 복원") { gateway.setHvacAuto(v) } }
        if (cs.hvacPower != true) {
          cs.hvacPower?.let { v -> restore("공조 전원 복원") { gateway.setHvacPower(v) } }
        }
      }
    }
    // 등받이 이동은 복원하지 않는다. 남은 움직임만 확실히 멈춘다.
    // 시트 권한을 배포 빌드에서 뺐다(SPEC18 C) — 등받이를 움직이는 건 공조·시트를 쓰는 숨긴 모드뿐이므로
    // comfortSnapshot이 있는(숨긴 모드였던) 경우에만 정지를 보낸다
    if (comfortSnapshot != null) {
      orNull { gateway.moveBackrest(READING_LEFT_AREA, MoveControl.STOP) }
      orNull { gateway.moveBackrest(READING_RIGHT_AREA, MoveControl.STOP) }
    }
    // 에뮬레이터 경쟁으로 Set이 유실될 수 있어 기다린 뒤 확인해 재적용한다.
    // delay 중 기어 전환·앱 종료로 취소되면 여기서 코루틴이 안전히 끝난다
    delay(REVERIFY_DELAY_MS)
    snapshot?.let { failedLabels.addAll(reverify(it)) }
    if (!keepHvac) comfortSnapshot?.let { failedLabels.addAll(reverifyComfort(it)) }
    applyWindowBrightness?.invoke(savedBrightness)
    activeMode = null
    snapshot = null
    comfortSnapshot = null
    return pending.filter { it.label in failedLabels }
  }

  /** 취소는 그대로 전파하고 다른 실패만 null로 바꿔 읽는다 */
  private suspend fun <T> readCurrent(block: suspend () -> T): T? = try {
    block()
  } catch (e: CancellationException) {
    throw e
  } catch (_: Exception) {
    null
  }

  /**
   * 항목 하나를 다시 읽어 스냅샷과 다르면 한 번 Set하고 결과를 로그로 남긴다.
   * 재적용은 1회만이고, 재확인 뒤에도 어긋나면 경고를 남기고 pendingRestore에 넣는다.
   * @return 여전히 어긋난 항목 이름, 복원됐으면 null
   */
  private suspend fun <T> reverifyItem(label: String, expected: T, get: suspend () -> T?, set: suspend (T) -> Unit): String? {
    val drifted = readCurrent(get) ?: return null
    if (drifted == expected) return null
    val setError = try {
      set(expected)
      null
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      "${label} 재적용: ${e.javaClass.simpleName}(${e.message})"
    }
    val after = readCurrent(get)
    return when {
      setError != null -> {
        Log.w(TAG, "복원 재적용 실패 $label: $setError")
        addPending(label) { set(expected) }
        label
      }
      after != expected -> {
        Log.w(TAG, "복원 재확인 불일치 $label: 기대=$expected 실제=$after")
        addPending(label) { set(expected) }
        label
      }
      else -> {
        Log.i(TAG, "복원 재적용 $label: 기대=$expected 어긋났던 값=$drifted")
        null
      }
    }
  }

  /**
   * 스냅샷에 값이 있는 항목만 골라 복원과 같은 순서로 재적용 확인을 돌린다.
   * @return 여전히 어긋난 항목 이름 목록
   */
  private suspend fun reverify(s: CabinState): List<String> {
    val remainingLabels = mutableListOf<String>()
    s.tailLamp?.let { reverifyItem("테일램프", it, { gateway.getTailLampSwitch() }, { v -> gateway.setTailLampSwitch(v) })?.let { l -> remainingLabels.add(l) } }
    s.readingLeft?.let { reverifyItem("독서등 2열 좌", it, { gateway.getReadingSwitch(READING_LEFT_AREA) }, { v -> gateway.setReadingSwitch(READING_LEFT_AREA, v) })?.let { l -> remainingLabels.add(l) } }
    s.readingRight?.let { reverifyItem("독서등 2열 우", it, { gateway.getReadingSwitch(READING_RIGHT_AREA) }, { v -> gateway.setReadingSwitch(READING_RIGHT_AREA, v) })?.let { l -> remainingLabels.add(l) } }
    s.cabinSwitch?.let { reverifyItem("실내등", it, { gateway.getCabinSwitch() }, { v -> gateway.setCabinSwitch(v) })?.let { l -> remainingLabels.add(l) } }
    s.ambientSwitch?.let { reverifyItem("무드 조명 스위치", it, { gateway.getAmbientSwitch(AMBIENT_AREA) }, { v -> gateway.setAmbientSwitch(AMBIENT_AREA, v) })?.let { l -> remainingLabels.add(l) } }
    s.ambientBrightness?.let { reverifyItem("무드 조명 밝기", it, { gateway.getAmbientBrightness(AMBIENT_AREA) }, { v -> gateway.setAmbientBrightness(AMBIENT_AREA, v) })?.let { l -> remainingLabels.add(l) } }
    s.ambientColor?.let { reverifyItem("무드 조명 색", it, { gateway.getAmbientColor(AMBIENT_AREA) }, { v -> gateway.setAmbientColor(AMBIENT_AREA, v) })?.let { l -> remainingLabels.add(l) } }
    return remainingLabels
  }

  /** 공조 6항목을 복원과 같은 순서(전원 ON은 맨 앞, OFF는 맨 마지막)로 재적용 확인한다 */
  private suspend fun reverifyComfort(cs: ComfortState): List<String> {
    val remainingLabels = mutableListOf<String>()
    if (cs.hvacPower == true) {
      cs.hvacPower?.let { reverifyItem("공조 전원", it, { gateway.getHvacPower() }, { v -> gateway.setHvacPower(v) })?.let { l -> remainingLabels.add(l) } }
    }
    cs.tempDriver?.let { reverifyItem("공조 온도 운전석", it, { gateway.getHvacTemperature(DRIVER_AREA) }, { v -> gateway.setHvacTemperature(DRIVER_AREA, v) })?.let { l -> remainingLabels.add(l) } }
    cs.tempPassenger?.let { reverifyItem("공조 온도 동승석", it, { gateway.getHvacTemperature(PASSENGER_AREA) }, { v -> gateway.setHvacTemperature(PASSENGER_AREA, v) })?.let { l -> remainingLabels.add(l) } }
    cs.fanSpeed?.let { reverifyItem("공조 팬", it, { gateway.getHvacFanSpeed() }, { v -> gateway.setHvacFanSpeed(v) })?.let { l -> remainingLabels.add(l) } }
    cs.hvacAc?.let { reverifyItem("공조 AC", it, { gateway.getHvacAc() }, { v -> gateway.setHvacAc(v) })?.let { l -> remainingLabels.add(l) } }
    cs.hvacAuto?.let { reverifyItem("공조 AUTO", it, { gateway.getHvacAuto() }, { v -> gateway.setHvacAuto(v) })?.let { l -> remainingLabels.add(l) } }
    if (cs.hvacPower != true) {
      cs.hvacPower?.let { reverifyItem("공조 전원", it, { gateway.getHvacPower() }, { v -> gateway.setHvacPower(v) })?.let { l -> remainingLabels.add(l) } }
    }
    return remainingLabels
  }

  /**
   * pendingRestore의 항목을 다시 Set한다. 주차(GEAR_PARK) 복귀 때 호출한다.
   * 성공한 항목은 목록에서 제거하고, 실패한 항목은
   * "이름: 예외클래스(메시지)" 목록으로 돌려준다.
   */
  suspend fun retryPendingRestore(): List<String> {
    val failures = mutableListOf<String>()
    for (item in pending.toList()) {
      try {
        item.apply()
        pending.removeAll { it.label == item.label }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        failures.add("${item.label}: ${e.javaClass.simpleName}(${e.message})")
      }
    }
    return failures
  }

  companion object {
    private const val TAG = "ModeController"

    /** 복원 Set 후 에뮬레이터 반영을 기다리는 시간 */
    private const val REVERIFY_DELAY_MS = 1500L

    /** 음악 조명 모드 초기 무드 조명 값 */
    internal const val MUSIC_LIGHT_COLOR = 0xAF52DE
    internal const val MUSIC_LIGHT_BRIGHTNESS = 60
  }
}
