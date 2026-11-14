package com.example.pleoscamping.mode

/** 배터리 가드 판정 입력 */
data class GuardInput(
  val batteryPercent: Int?,
  val homeKm: Int,
  val marginPct: Int,
  val kmPerKwh: Float,
  val capacityKwh: Float?,
  val previousStage: Int
)

/** 판정 결과. 배터리 %를 못 읽었으면 단계는 이전 값을 유지한다 */
data class GuardResult(
  val stage: Int,
  val requiredPercent: Float?,
  val headroomPercent: Float?
)

/**
 * 배터리 가드 순수 계산(SDK 호출 없음).
 * 귀가 필요 %와 여유 %를 구하고 절약 단계(0 정상 ~ 3 위험)를 정한다.
 * 단계는 히스테리시스를 둔다: 올라갈 땐 경계값 그대로,
 * 내려갈 땐 경계값 +2를 넘어야 내려간다. previousStage가 -1이면 첫 판정이다.
 */
object BatteryGuard {

  /** 용량 조회 실패·0일 때 쓰는 기본 배터리 용량(kWh) */
  const val DEFAULT_CAPACITY_KWH = 77.4f

  fun evaluate(input: GuardInput): GuardResult {
    val capacity = if ((input.capacityKwh ?: 0f) > 0f) input.capacityKwh!! else DEFAULT_CAPACITY_KWH
    val requiredPercent = (input.homeKm / input.kmPerKwh / capacity * 100.0 + input.marginPct).toFloat()
    val headroom = input.batteryPercent?.let { it - requiredPercent }
    val stage = if (headroom == null) {
      input.previousStage.coerceAtLeast(0)
    } else {
      stageFor(headroom.toDouble(), input.previousStage)
    }
    return GuardResult(stage, requiredPercent, headroom)
  }

  /** 경계값 그대로 판정한 단계 */
  private fun risingStage(headroomPct: Double): Int = when {
    headroomPct > 20.0 -> 0
    headroomPct > 10.0 -> 1
    headroomPct > 5.0 -> 2
    else -> 3
  }

  private fun stageFor(headroomPct: Double, previousStage: Int): Int {
    val rising = risingStage(headroomPct)
    if (rising >= previousStage) return rising
    // 내려갈 땐 경계값 +2 기준으로 재판정해 못 넘으면 이전 단계를 유지한다
    val falling = when {
      headroomPct > 22.0 -> 0
      headroomPct > 12.0 -> 1
      headroomPct > 7.0 -> 2
      else -> 3
    }
    return if (falling < previousStage) falling else previousStage
  }

  fun stageName(stage: Int): String = when (stage) {
    0 -> "정상"
    1 -> "절약"
    2 -> "경계"
    else -> "위험"
  }

  fun stageMessage(stage: Int): String = when (stage) {
    0 -> "여유가 충분해요"
    1 -> "바람을 줄여 조금 아껴요"
    2 -> "공조와 조명을 줄여요"
    else -> "지금 출발하지 않으면 집에 가기 어려워요"
  }
}
