package com.example.pleoscamping.vehicle

import ai.pleos.playground.vehicle.constant.control.LightSwitch

/** 조명 상태 스냅샷. 읽기에 실패한 항목은 null로 두고 복원 때 건너뛴다 */
data class CabinState(
  val ambientSwitch: LightSwitch?,
  val ambientBrightness: Int?,
  val ambientColor: Int?,
  val cabinSwitch: LightSwitch?,
  val readingLeft: LightSwitch?,
  val readingRight: LightSwitch?,
  val tailLamp: LightSwitch?
)

/** 공조 스냅샷. 공조를 건드리는 모드(배터리 가드·일출 알람)만 저장한다 */
data class ComfortState(
  val hvacPower: Boolean?,
  val hvacAuto: Boolean?,
  val hvacAc: Boolean?,
  val tempDriver: Float?,
  val tempPassenger: Float?,
  val fanSpeed: Int?
)
