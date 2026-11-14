package com.example.pleoscamping.karaoke

import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EnvelopeBeatTracker 단위 시험(SPEC7 7절).
 * 초당 약 19회 캡처(간격 52±3 ms, 고정 시드)를 흉내 낸 합성 입력으로
 * BPM 추정·박 검출·무음 판정·자동 음량 맞춤을 확인한다.
 */
class EnvelopeBeatTrackerTest {

  private val durationMs = 12_000L

  /** 12초 분량의 캡처 시각(간격 52±3 ms 흔들림, 고정 시드) */
  private fun captureTimes(): LongArray {
    val rand = Random(7L)
    val times = ArrayList<Long>()
    var t = 0L
    while (t < durationMs) {
      times.add(t)
      t += 52L + (rand.nextInt(7) - 3)
    }
    return times.toLongArray()
  }

  /**
   * 박 시각마다 150 ms 동안 low는 400에서(시정수 40 ms) rms는 0.6에서(시정수 100 ms)
   * 지수 감쇠하고 나머지는 low=2, rms=0.02인 입력을 12초 동안 넣는다.
   * rmsScale로 음량만 조절한다.
   */
  private fun feed(
    tracker: EnvelopeBeatTracker,
    bpm: Int,
    rmsScale: Double = 1.0
  ): List<EnvelopeBeatTracker.Result> {
    val periodMs = 60_000.0 / bpm
    val results = ArrayList<EnvelopeBeatTracker.Result>()
    for (t in captureTimes()) {
      val dt = t % periodMs
      val low: Float
      val rms: Float
      if (dt < 150.0) {
        low = (2.0 + 398.0 * exp(-dt / 40.0)).toFloat()
        rms = ((0.02 + 0.58 * exp(-dt / 100.0)) * rmsScale).toFloat()
      } else {
        low = 2f
        rms = (0.02 * rmsScale).toFloat()
      }
      results.add(tracker.process(t, rms, low))
    }
    return results
  }

  private fun assertTrack(target: Int) {
    val results = feed(EnvelopeBeatTracker(), target)
    assertTrue("결과가 비어 있음", results.isNotEmpty())
    val last = results.last()
    assertNotNull("BPM 미추정(무음 판정?) bpm=null", last.bpm)
    val bpm = last.bpm!!
    assertTrue(
      "목표 $target BPM에서 $bpm 추정(±4 허용)",
      abs(bpm - target) <= 4.0f
    )
    assertTrue(
      "confidence=${last.confidence}가 0.3 미만",
      last.confidence >= 0.3f
    )
    val beats = results.count { it.beat }
    val actual = (durationMs / (60_000.0 / target)).toInt()
    assertTrue(
      "센 박 ${beats}개, 실제 박 ${actual}개(70~130% 허용)",
      beats >= (actual * 0.7).toInt() && beats <= (actual * 1.3).toInt()
    )
  }

  @Test
  fun detects100Bpm() = assertTrack(100)

  @Test
  fun detects120Bpm() = assertTrack(120)

  @Test
  fun detects128Bpm() = assertTrack(128)

  @Test
  fun detects140Bpm() = assertTrack(140)

  @Test
  fun silenceGivesNoBeatNoBpmZeroLevel() {
    val tracker = EnvelopeBeatTracker()
    val results = captureTimes().filter { it < 5_000L }.map { tracker.process(it, 0f, 0f) }
    assertTrue("결과가 비어 있음", results.isNotEmpty())
    assertTrue("무음에서는 bpm이 null이어야 함", results.last().bpm == null)
    assertTrue("무음에서는 beat가 없어야 함", results.none { it.beat })
    assertTrue("무음에서는 level이 0이어야 함", results.all { it.level == 0f })
  }

  @Test
  fun levelAdaptsToQuietPlayback() {
    val times = captureTimes()
    val cutoff = times.indexOfFirst { it >= durationMs - 2_000L }
    assertTrue("마지막 2초 구간을 찾지 못함", cutoff >= 0)
    val loud = feed(EnvelopeBeatTracker(), 120, 1.0)
    val quiet = feed(EnvelopeBeatTracker(), 120, 0.1)
    val loudAvg = loud.drop(cutoff).map { it.level }.average()
    val quietAvg = quiet.drop(cutoff).map { it.level }.average()
    assertTrue("큰 음량의 마지막 2초 level 평균 $loudAvg < 0.2", loudAvg >= 0.2)
    assertTrue("작은 음량의 마지막 2초 level 평균 $quietAvg < 0.2", quietAvg >= 0.2)
  }
}
