package com.example.pleoscamping.vehicle

import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue

/**
 * 차량 제어 영역 상수. 차량 게이트웨이가 쓴다 (VehicleGateway.kt에서 분리).
 */

/** 무드 조명 합성 영역: 1열 전체 + 2열 전체 (1+4+16+32+64) */
val AMBIENT_AREA = SeatArea(
  SeatValue.ROW_1_LEFT,
  SeatValue.ROW_1_RIGHT,
  SeatValue.ROW_2_LEFT,
  SeatValue.ROW_2_CENTER,
  SeatValue.ROW_2_RIGHT
)

/** 2열 독서등 영역 */
val READING_LEFT_AREA = SeatArea(SeatValue.ROW_2_LEFT)
val READING_RIGHT_AREA = SeatArea(SeatValue.ROW_2_RIGHT)

/** 공조 전원·AC 영역(=AMBIENT_AREA, 117) */
val HVAC_ALL_AREA = AMBIENT_AREA

/** 1열 전체(운전석+동승석) — 공조 AUTO·팬 */
val ROW1_AREA = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT)

/** 운전석 좌석 영역 — 공조 온도 */
val DRIVER_AREA = SeatArea(SeatValue.ROW_1_LEFT)

/** 동승석 좌석 영역 — 공조 온도 */
val PASSENGER_AREA = SeatArea(SeatValue.ROW_1_RIGHT)
