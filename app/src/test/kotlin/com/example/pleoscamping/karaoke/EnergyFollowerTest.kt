package com.example.pleoscamping.karaoke

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EnergyFollower 단위 시험(SPEC9 1절).
 * 50 ms 간격 입력으로 일정한 입력·주기적 튄 입력·크기 무관성·reset을 확인한다.
 */
class EnergyFollowerTest {

  private class Sample(val spike: Boolean, val punch: Float)

  /** 평소 10, 500 ms마다 한 번 100(×scale)인 입력을 10초 넣고 결과를 모은다. */
  private fun feedSpikes(scale: Float): List<Sample> {
    val follower = EnergyFollower()
    val samples = ArrayList<Sample>()
    var t = 0L
    while (t < 10_000L) {
      val spike = t % 500L == 0L
      val x = (if (spike) 100f else 10f) * scale
      samples.add(Sample(spike, follower.process(t, x)))
      t += 50L
    }
    return samples
  }

  /** 마지막 5초에서 튄 순간은 0.8 이상, 튀지 않은 순간의 평균은 0.1 이하인지 확인. */
  private fun assertSpikesStandOut(samples: List<Sample>) {
    val last5s = samples.takeLast(100)
    val spikes = last5s.filter { it.spike }
    assertTrue("마지막 5초에 튄 순간이 ${spikes.size}개뿐", spikes.size >= 5)
    for (s in spikes) {
      assertTrue("튄 순간 punch=${s.punch}가 0.8 미만", s.punch >= 0.8f)
    }
    val quietAvg = last5s.filterNot { it.spike }.map { it.punch }.average()
    assertTrue("튀지 않은 순간의 평균 ${quietAvg}가 0.1 초과", quietAvg <= 0.1)
  }

  @Test
  fun constantInputStaysQuiet() {
    val follower = EnergyFollower()
    var t = 0L
    while (t <= 5_000L) {
      val punch = follower.process(t, 100f)
      assertTrue("일정한 입력에서 punch=${punch}가 0.05 초과", punch <= 0.05f)
      t += 50L
    }
  }

  @Test
  fun periodicSpikesStandOut() = assertSpikesStandOut(feedSpikes(1f))

  @Test
  fun spikesStandOutRegardlessOfScale() = assertSpikesStandOut(feedSpikes(0.01f))

  @Test
  fun resetMakesNextCallFirst() {
    val follower = EnergyFollower()
    follower.process(0L, 10f)
    follower.process(50L, 100f)
    follower.reset()
    val punch = follower.process(60_000L, 100f)
    assertTrue("reset 뒤 첫 반환 ${punch}이 0이 아님", punch == 0f)
  }
}
