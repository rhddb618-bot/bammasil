package com.example.pleoscamping.astro

/** 하늘 밝기 단계. 해 고도로 나눈다 (SPEC18) */
enum class SkyPhase { DAY, TWILIGHT, NIGHT }

/** 달빛 기준 별 보기 조건. level 0 좋음, 1 보통, 2 달이 밝음. 날씨(구름)는 알 수 없어 반영하지 않는다 (SPEC18) */
data class StarCondition(val text: String, val level: Int)

/** 별 보기 화면에 쓰는 쉬운 말 안내 (SPEC11 1절) */
object SkyGuide {
  /** 목록에 쓰는 높이 말 */
  fun heightWord(altDeg: Double): String = when {
    altDeg < 20.0 -> "낮게"
    altDeg < 45.0 -> "중간"
    altDeg < 70.0 -> "높이"
    else -> "머리 위"
  }

  /** 한 문장 안내. note가 있으면 뒤에 붙인다 */
  fun sentence(nameKo: String, sector: Sector, altDeg: Double, note: String?): String {
    val base = if (altDeg >= 70.0) {
      "${nameKo}는 거의 머리 위에 있어요."
    } else {
      val direction = when (sector) {
        Sector.FRONT -> "앞쪽"
        Sector.RIGHT -> "오른쪽"
        Sector.BACK -> "뒤쪽"
        Sector.LEFT -> "왼쪽"
      }
      val height = when {
        altDeg < 20.0 -> "낮은 곳"
        altDeg < 45.0 -> "중간 높이"
        else -> "높은 곳"
      }
      "${nameKo}는 차 ${direction}, ${height}에 있어요."
    }
    return if (note.isNullOrEmpty()) base else "$base $note"
  }

  /** 해 고도 > 0° 낮, > -6° 박명(하늘이 아직 밝음), 그 아래 밤 */
  fun phaseOf(sunAltDeg: Double): SkyPhase = when {
    sunAltDeg > 0.0 -> SkyPhase.DAY
    sunAltDeg > -6.0 -> SkyPhase.TWILIGHT
    else -> SkyPhase.NIGHT
  }

  /** 달빛만으로 고른 한 줄 안내. 밤이 아니면 "오늘 밤" 예고로 말한다 */
  fun starCondition(phase: SkyPhase, moonIllumination: Double, moonAltDeg: Double): StarCondition =
    if (phase == SkyPhase.NIGHT) {
      when {
        moonAltDeg < 0.0 -> StarCondition("달이 떠 있지 않아 별 보기 좋은 밤이에요", 0)
        moonIllumination < 0.35 -> StarCondition("달빛이 약해 별 보기 좋은 밤이에요", 0)
        moonIllumination < 0.7 -> StarCondition("달빛이 조금 있어요. 밝은 별자리는 잘 보여요", 1)
        else -> StarCondition("달이 밝아 어두운 별은 잘 안 보여요", 2)
      }
    } else {
      when {
        moonIllumination < 0.35 -> StarCondition("오늘 밤은 달빛이 약해 별 보기 좋아요", 0)
        moonIllumination < 0.7 -> StarCondition("오늘 밤은 달빛이 조금 있어요", 1)
        else -> StarCondition("오늘 밤은 달이 밝아요", 2)
      }
    }
}
