package com.example.pleoscamping.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Vehicle SDK 래퍼. initialize()는 동기 호출이라 try/catch로 감싸고,
 * 성공한 뒤에만 도메인 객체(getLight 등)를 얻는다.
 * 콜백 API는 suspend 함수로, 기어 변경은 Flow로 노출한다.
 */
class VehicleGateway(context: Context) {

  private val vehicle = Vehicle(context.applicationContext)

  @Volatile
  private var initialized = false

  val isReady: Boolean
    get() = initialized

  /** @return 성공하면 null, 실패하면 오류 문구 */
  fun initialize(): String? = try {
    if (!initialized) {
      vehicle.initialize()
      initialized = true
    }
    null
  } catch (e: Exception) {
    "${e.javaClass.simpleName}: ${e.message}"
  }

  fun release() {
    if (!initialized) return
    try {
      vehicle.release()
    } catch (_: Exception) {
      // 종료 경로의 실패는 무시한다
    }
    initialized = false
  }

  private fun checkReady() {
    check(initialized) { "Vehicle SDK가 초기화되지 않았습니다" }
  }

  /** SDK 호출이 동기적으로 예외를 던질 수 있어 즉시 실패 처리한다 */
  private suspend fun <T> awaitResult(call: (CancellableContinuation<T>) -> Unit): T =
    suspendCancellableCoroutine { continuation ->
      try {
        call(continuation)
      } catch (e: Exception) {
        continuation.resumeWithException(e)
      }
    }

  // --- 무드 조명 ---

  suspend fun setAmbientSwitch(area: SeatArea, value: LightSwitch): Unit = awaitResult { c ->
    checkReady()
    vehicle.getLight().setAmbientLightSwitch(area, value, { _, _ -> c.resume(Unit) }, { e -> c.resumeWithException(e) })
  }

  suspend fun setAmbientBrightness(area: SeatArea, value: Int): Unit = awaitResult { c ->
    checkReady()
    vehicle.getLight().setAmbientLightBrightness(area, value, { _, _ -> c.resume(Unit) }, { e -> c.resumeWithException(e) })
  }

  suspend fun setAmbientColor(area: SeatArea, color: Int): Unit = awaitResult { c ->
    checkReady()
    // SDK 는 알파 없는 0xRRGGBB 를 받으며, Compose 색 상수(0xAARRGGBB)가 그대로 오면 음수가 된다.
    vehicle.getLight().setAmbientLightBrightnessColor(area, color and 0xFFFFFF, { _, _ -> c.resume(Unit) }, { e -> c.resumeWithException(e) })
  }

  suspend fun getAmbientSwitch(area: SeatArea): LightSwitch? = awaitResult { c ->
    checkReady()
    vehicle.getLight().getAmbientLightSwitch(area, { sw -> c.resume(sw) }, { e -> c.resumeWithException(e) })
  }

  suspend fun getAmbientBrightness(area: SeatArea): Int? = awaitResult { c ->
    checkReady()
    vehicle.getLight().getAmbientLightBrightness(area, { v -> c.resume(v) }, { e -> c.resumeWithException(e) })
  }

  suspend fun getAmbientColor(area: SeatArea): Int? = awaitResult { c ->
    checkReady()
    vehicle.getLight().getAmbientLightBrightnessColor(area, { v -> c.resume(v) }, { e -> c.resumeWithException(e) })
  }

  // --- 실내등 / 독서등 / 테일램프 ---

  suspend fun setCabinSwitch(value: LightSwitch): Unit = awaitResult { c ->
    checkReady()
    vehicle.getLight().setCabinLightSwitch(value, { _ -> c.resume(Unit) }, { e -> c.resumeWithException(e) })
  }

  suspend fun getCabinSwitch(): LightSwitch? = awaitResult { c ->
    checkReady()
    vehicle.getLight().getCabinLightSwitch({ sw -> c.resume(sw) }, { e -> c.resumeWithException(e) })
  }

  suspend fun setReadingSwitch(area: SeatArea, value: LightSwitch): Unit = awaitResult { c ->
    checkReady()
    vehicle.getLight().setReadingLightSwitch(area, value, { _ -> c.resume(Unit) }, { e -> c.resumeWithException(e) })
  }

  suspend fun getReadingSwitch(area: SeatArea): LightSwitch? = awaitResult { c ->
    checkReady()
    vehicle.getLight().getReadingLightSwitch(area, { sw -> c.resume(sw) }, { e -> c.resumeWithException(e) })
  }

  suspend fun setTailLampSwitch(value: LightSwitch): Unit = awaitResult { c ->
    checkReady()
    vehicle.getLight().setTailLampLightSwitch(value, { _ -> c.resume(Unit) }, { e -> c.resumeWithException(e) })
  }

  suspend fun getTailLampSwitch(): LightSwitch? = awaitResult { c ->
    checkReady()
    vehicle.getLight().getTailLampLightSwitch({ sw -> c.resume(sw) }, { e -> c.resumeWithException(e) })
  }

  // --- 기어 ---

  suspend fun currentGear(): VehicleGear = awaitResult { c ->
    checkReady()
    // SDK 콜백의 기어 값은 널 허용 타입이라 널은 GEAR_UNKNOWN으로 바꿔 돌려준다
    vehicle.getGear().getCurrentGearSelectionState({ g -> c.resume(g ?: VehicleGear.GEAR_UNKNOWN) }, { e -> c.resumeWithException(e) })
  }

  fun gearFlow(): Flow<VehicleGear> = callbackFlow {
    checkReady()
    val listener = object : GearSelectionListener {
      override fun onSelected(gear: VehicleGear?) {
        trySend(gear ?: VehicleGear.GEAR_UNKNOWN)
      }

      override fun onFailed(e: Exception) {
        // 실패로 Flow를 닫지 않고 로그만 남긴다
        Log.w(TAG, "기어 선택 콜백 실패", e)
      }
    }
    try {
      vehicle.getGear().registerGearSelection(listener)
    } catch (e: Exception) {
      close(e)
      return@callbackFlow
    }
    awaitClose {
      try {
        vehicle.getGear().unregisterGearSelection(listener)
      } catch (_: Exception) {
        // 해제 실패는 무시한다
      }
    }
  }

  // --- 배터리 ---

  suspend fun evBatteryPercent(): Int? = awaitResult { c ->
    checkReady()
    vehicle.getEvBattery().getEvBatteryStateOfCharge({ v -> c.resume(v) }, { e -> c.resumeWithException(e) })
  }

  suspend fun evBatteryCapacityKwh(): Float? = awaitResult { c ->
    checkReady()
    vehicle.getEvBattery().getEvBatteryCapacity({ v -> c.resume(v) }, { e -> c.resumeWithException(e) })
  }

  suspend fun evBatteryLevelKwh(): Float? = awaitResult { c ->
    checkReady()
    vehicle.getEvBattery().getEvBatteryLevel({ v -> c.resume(v) }, { e -> c.resumeWithException(e) })
  }

  suspend fun evEfficiencyKmPerKwh(): Float? = awaitResult { c ->
    checkReady()
    vehicle.getEvBattery().getCurrentEvEfficiency({ v -> c.resume(v) }, { e -> c.resumeWithException(e) })
  }

  /** 잔여 주행거리(km). 단위가 확정되지 않아 1000을 넘으면 미터로 판정해 나눈다 */
  suspend fun rangeRemainingKm(): Float? = awaitResult { c ->
    checkReady()
    vehicle.getOdometer().getRangeRemainingDistance(
      { v: Float? -> c.resume(if (v != null && v > 1000f) v / 1000f else v) },
      { e -> c.resumeWithException(e) }
    )
  }

  // --- 공조 (SPEC2) ---

  suspend fun setHvacPower(on: Boolean): Unit = awaitResult { c ->
    checkReady()
    vehicle.getHVAC().setHvacPower(HVAC_ALL_AREA, on, { _, _ -> c.resume(Unit) }, { e -> c.resumeWithException(e) })
  }

  suspend fun getHvacPower(): Boolean? = awaitResult { c ->
    checkReady()
    vehicle.getHVAC().getHvacPowerState(HVAC_ALL_AREA, { v -> c.resume(v) }, { e -> c.resumeWithException(e) })
  }

  suspend fun setHvacAc(on: Boolean): Unit = awaitResult { c ->
    checkReady()
    vehicle.getHVAC().setHvacAc(HVAC_ALL_AREA, on, { _, _ -> c.resume(Unit) }, { e -> c.resumeWithException(e) })
  }

  suspend fun getHvacAc(): Boolean? = awaitResult { c ->
    checkReady()
    vehicle.getHVAC().getHvacAcState(HVAC_ALL_AREA, { v -> c.resume(v) }, { e -> c.resumeWithException(e) })
  }

  suspend fun setHvacAuto(on: Boolean): Unit = awaitResult { c ->
    checkReady()
    vehicle.getHVAC().setHvacAuto(ROW1_AREA, on, { _, _ -> c.resume(Unit) }, { e -> c.resumeWithException(e) })
  }

  suspend fun getHvacAuto(): Boolean? = awaitResult { c ->
    checkReady()
    vehicle.getHVAC().getHvacAutoState(ROW1_AREA, { v -> c.resume(v) }, { e -> c.resumeWithException(e) })
  }

  suspend fun setHvacFanSpeed(value: Int): Unit = awaitResult { c ->
    checkReady()
    vehicle.getHVAC().setHvacFanSpeed(ROW1_AREA, value, { _, _ -> c.resume(Unit) }, { e -> c.resumeWithException(e) })
  }

  suspend fun getHvacFanSpeed(): Int? = awaitResult { c ->
    checkReady()
    vehicle.getHVAC().getHvacFanSpeed(ROW1_AREA, { v -> c.resume(v) }, { e -> c.resumeWithException(e) })
  }

  suspend fun setHvacTemperature(area: SeatArea, celsius: Float): Unit = awaitResult { c ->
    checkReady()
    vehicle.getHVAC().setHvacTemperature(area, celsius, TemperatureUnit.CELSIUS, { _, _ -> c.resume(Unit) }, { e -> c.resumeWithException(e) })
  }

  suspend fun getHvacTemperature(area: SeatArea): Float? = awaitResult { c ->
    checkReady()
    vehicle.getHVAC().getHvacTemperature(area, TemperatureUnit.CELSIUS, { v -> c.resume(v) }, { e -> c.resumeWithException(e) })
  }

  // --- 시트 (SPEC2) ---

  /** 등받이 각도 이동. 에뮬레이터에서는 2열만 지원한다 */
  suspend fun moveBackrest(area: SeatArea, control: MoveControl): Unit = awaitResult { c ->
    checkReady()
    vehicle.getSeat().moveSeatBackrestAngle(area, control, { _, _ -> c.resume(Unit) }, { e -> c.resumeWithException(e) })
  }

  suspend fun getBackrestPosition(area: SeatArea): Int? = awaitResult { c ->
    checkReady()
    vehicle.getSeat().getCurrentSeatBackrestAnglePosition(area, { v -> c.resume(v) }, { e -> c.resumeWithException(e) })
  }

  companion object {
    private const val TAG = "VehicleGateway"
  }
}
