package com.example.pleoscamping.karaoke

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** 소리 크기가 평소보다 얼마나 튀었는지를 0..1로 돌려주는 추적기(SPEC9 1절). */
class EnergyFollower {

  private var avg = 0f
  private var peak = 0f
  private var lastMs = 0L
  private var started = false

  /**
   * timeMs: 캡처 시각(ms, 단조 증가). x: 그 순간의 소리 크기(0 이상, 단위 무관). 반환: 0..1.
   * 1.5초 느린 평균 avg에서 벗어난 편차를 최근 최대 편차 peak로 나눈 비율이라
   * 입력의 절대 크기에는 무관하다.
   */
  fun process(timeMs: Long, x: Float): Float {
    if (!started) {
      started = true
      lastMs = timeMs
      avg = x
      peak = 0f
      return 0f
    }
    val dt = (timeMs - lastMs).toDouble().coerceAtLeast(1.0)
    lastMs = timeMs
    avg += ((1.0 - exp(-dt / 1500.0)) * (x - avg)).toFloat()
    val dev = max(0f, x - avg)
    peak = max(dev, (peak * exp(-dt / 6000.0)).toFloat())
    return if (peak > 1e-6f) min(1f, dev / peak) else 0f
  }

  /** 상태를 처음으로 되돌린다. 다음 process 호출은 첫 호출로 취급된다. */
  fun reset() {
    started = false
    avg = 0f
    peak = 0f
    lastMs = 0L
  }
}
