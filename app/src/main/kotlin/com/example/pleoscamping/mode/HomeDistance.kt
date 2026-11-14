package com.example.pleoscamping.mode

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 현재 위치에서 집까지 거리 어림 계산. 나중에 실제 도로 거리(경로 서비스)로 바꿀 때는 roadKm만 바꿔 끼우면 된다 */
object HomeDistance {
  const val ROAD_FACTOR = 1.3              // 직선거리 → 도로 거리 어림 계수
  const val DEFAULT_HOME_LAT = 37.5665     // 기본 집 위치: 서울시청
  const val DEFAULT_HOME_LON = 126.9780

  /** 두 지점의 직선거리(km). 하버사인, 지구 반지름 6371.0 km */
  fun straightKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val radLat1 = Math.toRadians(lat1)
    val radLat2 = Math.toRadians(lat2)
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(radLat1) * cos(radLat2) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return 6371.0 * c
  }

  /** 도로 어림 거리(km) = 직선거리 × ROAD_FACTOR 를 반올림한 정수 */
  fun roadKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    return (straightKm(lat1, lon1, lat2, lon2) * ROAD_FACTOR).roundToInt()
  }
}
