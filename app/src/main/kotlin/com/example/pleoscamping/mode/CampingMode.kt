package com.example.pleoscamping.mode

import androidx.annotation.DrawableRes
import com.example.pleoscamping.R

/**
 * 캠핑 모드. iconRes는 패널·카드에 쓰는 VectorDrawable이다.
 * usesHvac가 true인 모드는 켤 때 공조 스냅샷(ComfortState)도 저장한다.
 */
enum class CampingMode(
  val label: String,
  val description: String,
  @DrawableRes val iconRes: Int,
  val usesHvac: Boolean
) {
  STARGAZING("별 보기", "실내 조명을 모두 끄고 밤하늘을 안내해요", R.drawable.ic_mode_star, false),
  CAMP_LIGHT("캠핑 조명", "무드 조명과 실내등으로 캠핑 분위기를 만들어요", R.drawable.ic_mode_fire, false),
  MUSIC_LIGHT("음악 조명", "음악에 맞춰 조명이 움직여요", R.drawable.ic_mode_music, false),
  BATTERY_GUARD("배터리 가드", "집에 갈 배터리를 지키며 공조를 단계적으로 줄여요", R.drawable.ic_mode_battery, true),
  SUNRISE_ALARM("일출 알람", "일출에 맞춰 조명·공조·시트로 부드럽게 깨워요", R.drawable.ic_mode_sunrise, true)
}

/** 화면에 보이는 모드(SPEC15). 나머지 모드는 코드만 남기고 탭에서 숨긴다 — 실차 확인 뒤 다시 연다 */
val VISIBLE_MODES: List<CampingMode> = listOf(CampingMode.STARGAZING, CampingMode.CAMP_LIGHT)
