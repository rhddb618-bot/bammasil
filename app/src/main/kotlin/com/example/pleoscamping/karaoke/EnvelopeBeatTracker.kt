package com.example.pleoscamping.karaoke

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 스피커 출력 캡처(초당 약 19개)로 박(onset)·BPM·음량을 구하는 추적기(SPEC7 1절).
 * 순수 Kotlin이라 android 의존 없이 단위 시험할 수 있다.
 */
class EnvelopeBeatTracker {

  /** process 1회의 결과. bpm·confidence는 마지막 추정값을 유지한다. */
  data class Result(val beat: Boolean, val bpm: Float?, val confidence: Float, val level: Float)

  /** 무음 판정 창(ms) */
  private val silenceWindowMs = 1_000L
  /** flux 통계(평균·표준편차) 창(ms) */
  private val fluxStatsMs = 1_500L
  /** flux 최댓값 비교 창(ms) */
  private val fluxMaxMs = 3_000L
  /** BPM 추정에 쓰는 flux 기록 창(ms) */
  private val envWindowMs = 8_000L
  /** BPM 추정을 시작할 최소 기록 길이(ms)와 다시 추정하는 간격(ms) */
  private val minSpanMs = 2_000L
  private val estimateEveryMs = 500L

  /** 최근 rms 기록(시각, 값). 무음 판정에 쓴다 */
  private val rmsHistory = ArrayDeque<Pair<Long, Float>>()
  /** 최근 flux 기록(시각, 값). 통계·최댓값에 쓴다 */
  private val fluxHistory = ArrayDeque<Pair<Long, Float>>()
  /** 최근 8초의 flux 기록(시각, 값). 캡처 순서 그대로 BPM 자기상관에 쓴다 */
  private val envHistory = ArrayDeque<Pair<Long, Float>>()

  private var prevLow: Float? = null
  private var lastTimeMs: Long? = null
  private var rmsEma = 0f
  private var peak = 0f
  private var lastBeatAt: Long? = null
  private var firstFluxAtMs: Long? = null
  private var lastEstimateAtMs: Long? = null

  private var bpm: Float? = null
  private var confidence = 0f

  /** 상태를 처음으로 되돌린다 */
  fun reset() {
    rmsHistory.clear()
    clearOnsets()
  }

  /**
   * 캡처 하나를 넣는다.
   * timeMs: 캡처 시각(단조 증가, ms). rms: 0..1 파형 RMS. low: 저음(40~200 Hz) 에너지(0 이상, 단위 무관)
   */
  fun process(timeMs: Long, rms: Float, low: Float): Result {
    rmsHistory.add(timeMs to rms)
    trimBefore(rmsHistory, timeMs - fluxMaxMs)

    // 규칙 1: 최근 1초 rms 평균이 임계 미만이면 무음. onset 기록을 지우고 정지 상태를 돌려준다.
    val avgRms = rmsHistory.filter { it.first >= timeMs - silenceWindowMs }.map { it.second }.average()
    if (avgRms < 0.005) {
      clearOnsets()
      lastTimeMs = null
      rmsEma = 0f
      peak = 0f
      return Result(beat = false, bpm = null, confidence = 0f, level = 0f)
    }

    val first = lastTimeMs == null
    val dtMs = if (first) 0f else (timeMs - lastTimeMs!!).toFloat()
    lastTimeMs = timeMs

    // 규칙 2: 자동 음량 맞춤. peak는 즉시 오르고 천천히 내린다.
    if (first) {
      rmsEma = rms
      peak = max(rms, 0.02f)
    } else {
      rmsEma += 0.3f * (rms - rmsEma)
      peak = if (rms > peak) rms else max(0.02f, peak * 0.9995f.pow(dtMs / 50f))
    }
    val level = (rmsEma / peak).coerceIn(0f, 1f)

    // 규칙 3: onset 세기. 저음 에너지의 양의 변화량만 쓴다.
    val flux = if (prevLow == null) 0f else max(0f, low - prevLow!!)
    prevLow = low
    fluxHistory.add(timeMs to flux)
    trimBefore(fluxHistory, timeMs - fluxMaxMs)
    if (firstFluxAtMs == null) firstFluxAtMs = timeMs

    // 규칙 4: 박 판정. 최근 1.5초 통계와 최근 3초 최댓값, 최소 박 간격을 함께 본다.
    val recentFlux = fluxHistory.filter { it.first >= timeMs - fluxStatsMs }.map { it.second.toDouble() }
    val mean = recentFlux.average()
    val sd = sqrt(recentFlux.map { (it - mean) * (it - mean) }.average())
    val recentMax = fluxHistory.maxOf { it.second }
    val minGapMs = bpm?.let { 0.6f * 60_000f / it } ?: 250f
    val gapOk = lastBeatAt?.let { timeMs - it >= minGapMs } ?: true
    val beat = flux > (mean + 1.5 * sd).toFloat() && flux > 0.1f * recentMax && gapOk
    if (beat) lastBeatAt = timeMs

    // 규칙 5: flux 기록을 8초 창에 쌓고, 기록이 2초 이상이면 0.5초마다 BPM을 다시 추정한다.
    envHistory.add(timeMs to flux)
    trimBefore(envHistory, timeMs - envWindowMs)
    val spanMs = timeMs - (firstFluxAtMs ?: timeMs)
    val due = lastEstimateAtMs?.let { timeMs - it >= estimateEveryMs } ?: true
    if (spanMs >= minSpanMs && due) {
      lastEstimateAtMs = timeMs
      estimateBpm()
    }

    // 규칙 6: bpm·confidence는 마지막 추정값을 그대로 실어 보낸다.
    return Result(beat, bpm, confidence, level)
  }

  /** onset 기록만 지운다. rms 기록은 무음 판정에 필요해 남긴다 */
  private fun clearOnsets() {
    fluxHistory.clear()
    envHistory.clear()
    prevLow = null
    lastBeatAt = null
    firstFluxAtMs = null
    lastEstimateAtMs = null
    bpm = null
    confidence = 0f
  }

  /** q에서 before(ms)보다 오래된 기록을 앞에서부터 지운다 */
  private fun trimBefore(q: ArrayDeque<Pair<Long, Float>>, before: Long) {
    while (q.isNotEmpty() && q.first().first < before) q.removeFirst()
  }

  /** flux 배열 자기상관으로 BPM을 다시 추정한다. 120 BPM 쪽을 선호해 절반·두 배 박자 오류를 줄인다. */
  private fun estimateBpm() {
    val n = envHistory.size
    if (n < 4) return
    // 한 칸의 시간. 캡처 간격이 흔들려도 평균 간격으로 지연을 환산한다.
    val tFirst = envHistory.first().first
    val tLast = envHistory.last().first
    val dt = (tLast - tFirst).toDouble() / (n - 1)
    if (dt <= 0.0) return
    // 캡처 순서 배열 e(평균을 뺀 flux 값)의 자기상관. 박 판정·level 로직은 flux 값 그대로 쓴다.
    val mean = envHistory.map { it.second.toDouble() }.average()
    val dev = DoubleArray(n) { envHistory[it].second - mean }
    val ac0 = dev.map { it * it }.sum()
    if (ac0 <= 0.0) {
      confidence = 0f
      return
    }
    // 필요한 지연 최댓값 = 4 * (60000/70/dt)캡처. dt≈52 ms면 약 66칸
    val maxLag = min(n - 1, ceil(4.0 * 60_000.0 / 70.0 / dt).toInt())
    val ac = DoubleArray(maxLag + 1)
    for (lag in 0..maxLag) {
      var sum = 0.0
      for (i in 0 until n - lag) sum += dev[i] * dev[i + lag]
      ac[lag] = sum
    }
    // 소수 지연은 선형 보간
    fun acAt(lag: Double): Double {
      if (lag <= 0.0) return ac[0]
      val f = floor(lag).toInt()
      if (f >= maxLag) return ac[maxLag]
      val frac = lag - f
      return ac[f] + (ac[f + 1] - ac[f]) * frac
    }
    var bestScore = Double.NEGATIVE_INFINITY
    var bestBpm = 0.0
    var bestLag = 0.0
    var cand = 70.0
    while (cand <= 180.0) {
      val lag = 60_000.0 / cand / dt
      var score = acAt(lag)
      if (2 * lag <= maxLag) score += 0.5 * acAt(2 * lag)
      // 3P가 기록 길이를 넘으면 그 항은 뺀다
      if (3 * lag <= maxLag) score += 0.33 * acAt(3 * lag)
      val l2 = log2(cand / 120.0) / 0.6
      score *= exp(-0.5 * l2 * l2)
      if (score > bestScore) {
        bestScore = score
        bestBpm = cand
        bestLag = lag
      }
      cand += 0.5
    }
    // 정밀화: 대략값의 ±6%를 0.1 BPM 간격으로 훑는다. 기록 안에 들어오는 가장 긴 배수 지연들의
    // 자기상관 합 ac(4P)+ac(3P)+ac(2P)이 최대인 bpm을 최종값으로 쓴다(기록을 넘는 항은 뺀다).
    // 지연이 길수록 같은 양자화 오차가 차지하는 비율이 줄어 주기가 더 정밀해진다.
    var refinedBpm = bestBpm
    var refinedScore = Double.NEGATIVE_INFINITY
    val lo = bestBpm * 0.94
    val hi = bestBpm * 1.06
    val steps = ((hi - lo) / 0.1).toInt()
    for (i in 0..steps) {
      val cand2 = lo + i * 0.1
      val lag2 = 60_000.0 / cand2 / dt
      var score2 = 0.0
      if (4 * lag2 <= maxLag) score2 += acAt(4 * lag2)
      if (3 * lag2 <= maxLag) score2 += acAt(3 * lag2)
      if (2 * lag2 <= maxLag) score2 += acAt(2 * lag2)
      if (score2 > refinedScore) {
        refinedScore = score2
        refinedBpm = cand2
      }
    }
    bestBpm = refinedBpm
    val prev = bpm
    bpm = when {
      prev == null -> bestBpm.toFloat()
      abs(bestBpm - prev.toDouble()) >= 8.0 -> bestBpm.toFloat()
      else -> prev + 0.3f * (bestBpm.toFloat() - prev)
    }
    confidence = ((acAt(bestLag) / ac0 - 0.1) / 0.4).coerceIn(0.0, 1.0).toFloat()
  }
}
