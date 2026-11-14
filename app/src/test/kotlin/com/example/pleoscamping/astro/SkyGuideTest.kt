package com.example.pleoscamping.astro

import org.junit.Assert.assertEquals
import org.junit.Test

/** SkyGuide 시험 (SPEC11 1절) */
class SkyGuideTest {
  @Test
  fun heightWord_구간별_말() {
    assertEquals("낮게", SkyGuide.heightWord(5.0))
    assertEquals("중간", SkyGuide.heightWord(20.0))
    assertEquals("중간", SkyGuide.heightWord(44.9))
    assertEquals("높이", SkyGuide.heightWord(45.0))
    assertEquals("높이", SkyGuide.heightWord(69.9))
    assertEquals("머리 위", SkyGuide.heightWord(70.0))
  }

  @Test
  fun sentence_중간_높이_앞쪽() {
    assertEquals(
      "고래자리는 차 앞쪽, 중간 높이에 있어요.",
      SkyGuide.sentence("고래자리", Sector.FRONT, 43.0, null)
    )
  }

  @Test
  fun sentence_낮은_곳_왼쪽_해설_덧붙임() {
    assertEquals(
      "오리온자리는 차 왼쪽, 낮은 곳에 있어요. 해설.",
      SkyGuide.sentence("오리온자리", Sector.LEFT, 10.0, "해설.")
    )
  }

  @Test
  fun sentence_머리_위() {
    assertEquals(
      "카시오페이아자리는 거의 머리 위에 있어요.",
      SkyGuide.sentence("카시오페이아자리", Sector.BACK, 75.0, null)
    )
  }

  @Test
  fun phaseOf_해_고도_경계() {
    assertEquals(SkyPhase.DAY, SkyGuide.phaseOf(0.1))
    assertEquals(SkyPhase.TWILIGHT, SkyGuide.phaseOf(0.0))
    assertEquals(SkyPhase.TWILIGHT, SkyGuide.phaseOf(-5.9))
    assertEquals(SkyPhase.NIGHT, SkyGuide.phaseOf(-6.0))
  }

  @Test
  fun starCondition_달빛_단계() {
    assertEquals(StarCondition("달이 떠 있지 않아 별 보기 좋은 밤이에요", 0), SkyGuide.starCondition(SkyPhase.NIGHT, 0.9, -3.0))
    assertEquals(StarCondition("달빛이 약해 별 보기 좋은 밤이에요", 0), SkyGuide.starCondition(SkyPhase.NIGHT, 0.2, 20.0))
    assertEquals(StarCondition("달빛이 조금 있어요. 밝은 별자리는 잘 보여요", 1), SkyGuide.starCondition(SkyPhase.NIGHT, 0.5, 20.0))
    assertEquals(StarCondition("달이 밝아 어두운 별은 잘 안 보여요", 2), SkyGuide.starCondition(SkyPhase.NIGHT, 0.9, 20.0))
    assertEquals(StarCondition("오늘 밤은 달빛이 약해 별 보기 좋아요", 0), SkyGuide.starCondition(SkyPhase.DAY, 0.2, 20.0))
    assertEquals(StarCondition("오늘 밤은 달이 밝아요", 2), SkyGuide.starCondition(SkyPhase.TWILIGHT, 0.9, -3.0))
  }
}
