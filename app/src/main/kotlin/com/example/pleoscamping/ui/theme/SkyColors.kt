package com.example.pleoscamping.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 별 보기 하늘 색 토큰 (SPEC_DARK 2절).
 * SkyDomeView·SkyWindshieldView·SkyRadarView가 함께 쓴다.
 */
val SkyTop = Color(0xFF05060C) // 하늘 꼭대기
val SkyLow = Color(0xFF0E1224) // 지평선 쪽 하늘
val SkyGround = Color(0xFF04050A) // 땅
val SkyHorizon = Color(0xFF333A5C) // 지평선 선, 돔 테두리
val SkyLine = Color(0xFF2E3558) // 별자리 선
val SkyLineDim = Color(0xFF1A1F36) // 강조 중 나머지 선
val SkyStar1 = Color(0xFFFFFFFF) // 가장 밝은 별
val SkyStar2 = Color(0xFFD3DBFF)
val SkyStar3 = Color(0xFF8F9AC8)
val SkyStar4 = Color(0xFF5A648E) // 가장 어두운 별
val SkyStarWarm = Color(0xFFFFE2C4) // 붉은 별(B−V > 1.2) 보간용
val SkyText = Color(0xFF8E98C2) // 별자리 이름, 방위 글자, 로딩 글자
val SkyName = Color(0xFFC6CEF0) // 밝은 별 이름
val SkyPlanet = Color(0xFFE9D8A6) // 행성
val SkyMoon = Color(0xFFE6E9F5) // 달
val SkyCross = Color(0xFF1E2440) // 가운데 십자

/** 별 색: 등급 구간별 보간 (≤1 → Star1, 1~2.5 → Star1→2, 2.5~4 → Star2→3, 4~5 → Star3→4).
 *  B−V > 1.2(붉은 별)면 SkyStarWarm 쪽으로 35% 섞는다 (SPEC_DARK 2절) */
fun skyStarColor(mag: Double, bv: Double): Color {
  val base = when {
    mag <= 1.0 -> SkyStar1
    mag <= 2.5 -> lerp(SkyStar1, SkyStar2, ((mag - 1.0) / 1.5).toFloat())
    mag <= 4.0 -> lerp(SkyStar2, SkyStar3, ((mag - 2.5) / 1.5).toFloat())
    else -> lerp(SkyStar3, SkyStar4, ((mag - 4.0) / 1.0).coerceIn(0.0, 1.0).toFloat())
  }
  return if (bv > 1.2) lerp(base, SkyStarWarm, 0.35f) else base
}
