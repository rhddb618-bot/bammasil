package com.example.pleoscamping.karaoke

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 마이크 PCM에서 박자(BPM)를 찾는 순수 Kotlin 감지기(SPEC6 1-2절).
 * 안드로이드 의존이 없어 단위 시험이 가능하다.
 *
 * 1. 길이 1024·홉 256 프레임(16 kHz에서 홉 16 ms)에 Hann 창을 얹고 1024점 radix-2 FFT로
 *    크기 스펙트럼을 구한다.
 * 2. log(1 + 100·|X|)으로 압축한 스펙트럼의 직전 프레임 대비 양의 차이 합(spectral flux)을
 *    onset 세기로 쓴다. 40~200 Hz(킥)는 ×2.0, 200~4000 Hz는 ×1.0, 그 위는 무시한다.
 * 3. 레벨은 프레임 RMS(0~1)의 지수 이동 평균.
 * 4. 최근 1초 평균 레벨이 0.01보다 낮으면 무음으로 본다(beat=false, bpm=null, confidence=0).
 * 5. peak picking: onset이 최근 0.5초 이동 평균 × 1.4 + 0.02보다 크고 앞·뒤 홉보다 크며
 *    직전 후보와 0.25초 이상 떨어졌을 때 박자 후보로 삼는다.
 * 6. BPM: 최근 6초 onset(평균 제거)의 자기상관을 70~180 BPM 지연에서 계산하고
 *    120 BPM 중심 로그 가우시안(σ = 0.6 옥타브) 가중 최댓값을 고른 뒤 0.15 비율로 지수 평활한다.
 *    confidence = (자기상관 최댓값 / 0번 지연 에너지 - 0.1) / 0.4 를 0~1로 자른 값.
 * 7. confidence ≥ 0.3이면 마지막 박자 + 주기로 예측 박자를 내고(±70 ms 안의 후보로 위상 보정),
 *    그보다 낮으면 박자 후보 자체를 beat로 낸다.
 */
class BeatDetector(private val sampleRate: Int = 16_000) {

  data class Result(
    val beat: Boolean,
    val bpm: Float?,
    val confidence: Float,
    val level: Float,
    val timeSec: Double
  )

  private val frameSize = 1024
  private val hopSize = 256
  private val bins = frameSize / 2
  private val hopSec = hopSize.toDouble() / sampleRate
  private val fft = Fft(frameSize)
  private val window = DoubleArray(frameSize) { i -> 0.5 - 0.5 * cos(2.0 * Math.PI * i / (frameSize - 1)) }

  /** 다음 프레임을 채워 넣는 버퍼 */
  private val carry = ShortArray(frameSize)
  private var carryLen = 0
  private var hopIndex = 0L

  private val re = DoubleArray(frameSize)
  private val im = DoubleArray(frameSize)
  private var prevComp: DoubleArray? = null
  private var prevFlux = 0.0
  private var hasPrevFlux = false
  private var prevPrevFlux = 0.0
  private var hasPrevPrevFlux = false

  /** onset 버퍼(시각, 세기). 최근 6초만 유지한다 */
  private val onsetTimes = ArrayDeque<Double>()
  private val onsetValues = ArrayDeque<Double>()

  private var levelEma = 0.0
  private val levelWindow = ArrayDeque<Double>()

  private var lastPeakTime = -1.0
  private var lastBeatTime = -1.0
  private var smoothBpm = 0.0
  private var hasSmoothBpm = false

  /** 들어온 PCM을 홉 단위로 처리한다. 프레임이 완성될 때마다 Result를 하나씩 만든다 */
  fun process(samples: ShortArray, count: Int): List<Result> {
    val results = ArrayList<Result>()
    var pos = 0
    while (pos < count) {
      val n = min(count - pos, frameSize - carryLen)
      System.arraycopy(samples, pos, carry, carryLen, n)
      carryLen += n
      pos += n
      if (carryLen == frameSize) {
        results.add(processFrame())
        System.arraycopy(carry, hopSize, carry, 0, frameSize - hopSize)
        carryLen = frameSize - hopSize
      }
    }
    return results
  }

  fun reset() {
    carryLen = 0
    hopIndex = 0
    java.util.Arrays.fill(re, 0.0)
    java.util.Arrays.fill(im, 0.0)
    prevComp = null
    hasPrevFlux = false
    hasPrevPrevFlux = false
    onsetTimes.clear()
    onsetValues.clear()
    levelEma = 0.0
    levelWindow.clear()
    lastPeakTime = -1.0
    lastBeatTime = -1.0
    smoothBpm = 0.0
    hasSmoothBpm = false
  }

  private fun binOf(freqHz: Double): Int =
    (freqHz * frameSize / sampleRate).toInt().coerceIn(0, bins)

  private fun processFrame(): Result {
    val timeSec = (hopIndex * hopSize + frameSize).toDouble() / sampleRate
    val prevTime = timeSec - hopSec
    hopIndex++

    // 프레임 RMS(창 적용 전 원본)
    var sum = 0.0
    for (i in 0 until frameSize) {
      val v = carry[i] / 32768.0
      sum += v * v
    }
    val rms = sqrt(sum / frameSize)
    levelEma = if (levelWindow.isEmpty()) rms else levelEma + LEVEL_EMA_ALPHA * (rms - levelEma)
    levelWindow.addLast(levelEma)
    val oneSecHops = maxOf(1, sampleRate / hopSize)
    while (levelWindow.size > oneSecHops) levelWindow.removeFirst()

    // 크기 스펙트럼 → log(1 + 100·|X|)
    for (i in 0 until frameSize) {
      re[i] = carry[i] / 32768.0 * window[i]
      im[i] = 0.0
    }
    fft.transform(re, im)
    val comp = DoubleArray(bins + 1)
    for (k in 0..bins) {
      comp[k] = ln(1.0 + 100.0 * sqrt(re[k] * re[k] + im[k] * im[k]))
    }

    // spectral flux(대역 가중, 양의 차이만)
    val prev = prevComp
    var flux = 0.0
    if (prev != null) {
      val lowBin = binOf(KICK_LOW_HZ)
      val kickHighBin = binOf(KICK_HIGH_HZ)
      val highBin = binOf(BAND_HIGH_HZ)
      for (k in lowBin..highBin) {
        val d = comp[k] - prev[k]
        if (d > 0.0) flux += d * (if (k <= kickHighBin) KICK_WEIGHT else 1.0)
      }
    }
    prevComp = comp

    onsetTimes.addLast(timeSec)
    onsetValues.addLast(flux)
    val sixSecHops = (ONSET_HISTORY_SEC * sampleRate / hopSize).toInt() + 1
    while (onsetTimes.size > sixSecHops) {
      onsetTimes.removeFirst()
      onsetValues.removeFirst()
    }

    // 무음 판정
    val avgLevel = levelWindow.sum() / levelWindow.size
    if (avgLevel < SILENCE_LEVEL) {
      lastBeatTime = -1.0
      hasSmoothBpm = false
      return Result(beat = false, bpm = null, confidence = 0f, level = levelEma.toFloat(), timeSec = timeSec)
    }

    // 직전 홉이 박자 후보(peak)인지 확정한다(한 홉 지연 판정)
    var peakNow = false
    if (hasPrevFlux && hasPrevPrevFlux) {
      val recentMean = recentOnsetMean(prevTime)
      val separated = lastPeakTime < 0.0 || prevTime - lastPeakTime >= MIN_BEAT_GAP_SEC
      if (separated &&
        prevFlux > prevPrevFlux && prevFlux >= flux &&
        prevFlux > recentMean * PEAK_RATIO + PEAK_FLOOR
      ) {
        peakNow = true
        lastPeakTime = prevTime
      }
    }
    hasPrevPrevFlux = hasPrevFlux
    prevPrevFlux = prevFlux
    hasPrevFlux = true
    prevFlux = flux

    // BPM 추정
    val estimate = estimateBpm()
    if (estimate != null) {
      val raw = estimate.first
      smoothBpm = if (hasSmoothBpm) smoothBpm + BPM_SMOOTH * (raw - smoothBpm) else raw
      hasSmoothBpm = true
    }
    val confidence = estimate?.second ?: 0.0

    // 박자 출력
    var beat = false
    if (peakNow) {
      // 후보가 나오면 박자로 내고 마지막 박자 시각을 후보 시각에 맞춘다(위상 보정·재동기화)
      beat = true
      lastBeatTime = prevTime
    } else if (confidence >= CONFIDENCE_BEAT && hasSmoothBpm) {
      val period = 60.0 / smoothBpm
      if (timeSec >= lastBeatTime + period) {
        beat = true
        lastBeatTime = timeSec
      }
    }

    return Result(
      beat = beat,
      bpm = if (hasSmoothBpm) smoothBpm.toFloat() else null,
      confidence = confidence.toFloat(),
      level = levelEma.toFloat(),
      timeSec = timeSec
    )
  }

  /** prevTime 기준 최근 0.5초 onset 세기의 평균 */
  private fun recentOnsetMean(prevTime: Double): Double {
    var total = 0.0
    var count = 0
    val it = onsetTimes.iterator()
    for (v in onsetValues) {
      val t = it.next()
      if (t >= prevTime - PEAK_WINDOW_SEC) {
        total += v
        count++
      }
    }
    return if (count > 0) total / count else 0.0
  }

  /**
   * 최근 6초 onset(평균 제거)의 자기상관을 70~180 BPM 지연에서 계산하고
   * 로그 가우시안 가중 최댓값을 고른다. @return (BPM, confidence), 데이터가 부족하면 null
   */
  private fun estimateBpm(): Pair<Double, Double>? {
    val n = onsetValues.size
    if (n < MIN_BPM_BUFFER_HOPS) return null
    val vals = onsetValues.toDoubleArray()
    val mean = vals.average()
    for (i in vals.indices) vals[i] -= mean

    var r0 = 0.0
    for (v in vals) r0 += v * v
    r0 /= n
    if (r0 <= 1e-12) return null

    val minLag = Math.ceil(60.0 * sampleRate / (MAX_BPM * hopSize)).toInt()
    val maxLag = Math.floor(60.0 * sampleRate / (MIN_BPM * hopSize)).toInt().coerceAtMost(n - 2)
    if (maxLag < minLag) return null

    var bestLag = -1
    var bestWeightedScore = 0.0
    var bestRawScore = 0.0
    for (lag in minLag..maxLag) {
      var s = 0.0
      for (k in 0 until n - lag) s += vals[k] * vals[k + lag]
      s /= (n - lag)
      val bpm = 60.0 * sampleRate / (lag * hopSize)
      val octaves = ln(bpm / CENTER_BPM) / ln(2.0)
      val weight = exp(-0.5 * (octaves / OCTAVE_SIGMA) * (octaves / OCTAVE_SIGMA))
      val weighted = s * weight
      if (bestLag < 0 || weighted > bestWeightedScore) {
        bestLag = lag
        bestWeightedScore = weighted
        bestRawScore = s
      }
    }
    if (bestLag < 0 || bestRawScore <= 0.0) return null
    val periodicity = bestRawScore / r0
    val confidence = ((periodicity - 0.1) / 0.4).coerceIn(0.0, 1.0)
    return 60.0 * sampleRate / (bestLag * hopSize) to confidence
  }

  /** 직접 짠 radix-2 FFT(1024점) */
  private class Fft(n: Int) {
    private val size: Int
    private val levels: Int
    private val cosTable: DoubleArray
    private val sinTable: DoubleArray

    init {
      require(n > 0 && (n and (n - 1)) == 0) { "FFT 크기는 2의 거듭제곱이어야 함: $n" }
      size = n
      var l = 0
      var v = 1
      while (v < n) {
        v = v shl 1
        l++
      }
      levels = l
      cosTable = DoubleArray(n / 2) { cos(2.0 * Math.PI * it / n) }
      sinTable = DoubleArray(n / 2) { sin(2.0 * Math.PI * it / n) }
    }

    fun transform(re: DoubleArray, im: DoubleArray) {
      for (i in 0 until size) {
        val j = Integer.reverse(i) ushr (32 - levels)
        if (j > i) {
          var t = re[i]; re[i] = re[j]; re[j] = t
          t = im[i]; im[i] = im[j]; im[j] = t
        }
      }
      var blockSize = 2
      while (blockSize <= size) {
        val half = blockSize / 2
        val tableStep = size / blockSize
        var blockStart = 0
        while (blockStart < size) {
          var j = 0
          for (offset in 0 until half) {
            val k = j
            val pre = re[blockStart + offset + half] * cosTable[k] + im[blockStart + offset + half] * sinTable[k]
            val pim = -re[blockStart + offset + half] * sinTable[k] + im[blockStart + offset + half] * cosTable[k]
            re[blockStart + offset + half] = re[blockStart + offset] - pre
            im[blockStart + offset + half] = im[blockStart + offset] - pim
            re[blockStart + offset] += pre
            im[blockStart + offset] += pim
            j += tableStep
          }
          blockStart += blockSize
        }
        blockSize = blockSize shl 1
      }
    }
  }

  companion object {
    /** 프레임 길이·홉(샘플) */
    private const val KICK_LOW_HZ = 40.0
    private const val KICK_HIGH_HZ = 200.0
    private const val BAND_HIGH_HZ = 4000.0
    private const val KICK_WEIGHT = 2.0

    /** 레벨 지수 이동 평균 계수 */
    private const val LEVEL_EMA_ALPHA = 0.2

    /** 무음 판정 문턱(최근 1초 평균 레벨) */
    private const val SILENCE_LEVEL = 0.01

    /** peak picking */
    private const val PEAK_WINDOW_SEC = 0.5
    private const val PEAK_RATIO = 1.4
    private const val PEAK_FLOOR = 0.02
    private const val MIN_BEAT_GAP_SEC = 0.25

    /** BPM 추정 */
    private const val ONSET_HISTORY_SEC = 6.0
    private const val MIN_BPM_BUFFER_HOPS = 125 // 약 2초
    private const val MIN_BPM = 70.0
    private const val MAX_BPM = 180.0
    private const val CENTER_BPM = 120.0
    private const val OCTAVE_SIGMA = 0.6
    private const val BPM_SMOOTH = 0.15

    /** 예측 박자를 내는 최소 confidence */
    private const val CONFIDENCE_BEAT = 0.3
  }
}
