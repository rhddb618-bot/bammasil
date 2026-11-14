package com.example.pleoscamping.karaoke

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 스피커로 나가는 소리를 출력 믹스(세션 0)의 [Visualizer]로 읽어
 * [EnvelopeBeatTracker]에 넘기고 박자 상태([BeatState])를 내보낸다.
 * 소리를 녹음·저장하지 않고 출력 파형·FFT만 감지에 쓴다.
 * Visualizer 생성이나 설정에 실패하면 시작하지 못한다.
 */
class OutputTap(private val context: Context) {

  private val tracker = EnvelopeBeatTracker()

  /** 저음 크기가 평소보다 얼마나 튀었는지(0..1)를 따라간다(SPEC9 2-2) */
  private val follower = EnergyFollower()

  private val _beat = MutableStateFlow(BeatState(null, 0f, 0f, 0L, false))
  val beat: StateFlow<BeatState> = _beat.asStateFlow()

  // 캡처 리스너는 별도 스레드에서 올 수 있으므로 dataLock으로 보호한다
  private val dataLock = Any()
  private var lastRms = 0f

  // 대역별 비중(전체 대비 low·high의 몫)의 지수 평균(SPEC8 2-2)
  private var bassEma = 0f
  private var trebleEma = 0f

  private var visualizer: Visualizer? = null
  private var captureSize = 0

  private var beatCount = 0L
  /** 마지막 상태 로그 시각(elapsedRealtime, ms) */
  private var lastLogMs = 0L

  private val listener = object : Visualizer.OnDataCaptureListener {
    override fun onWaveFormDataCapture(v: Visualizer, waveform: ByteArray, samplingRate: Int) {
      val rms = waveformRms(waveform)
      synchronized(dataLock) { lastRms = rms }
    }

    override fun onFftDataCapture(v: Visualizer, fft: ByteArray, samplingRate: Int) {
      val rms = synchronized(dataLock) { lastRms }
      val low = fftBandEnergy(fft, samplingRate, 40.0, 200.0, fallback = true)
      val mid = fftBandEnergy(fft, samplingRate, 200.0, 2000.0, fallback = false)
      val high = fftBandEnergy(fft, samplingRate, 2000.0, 8000.0, fallback = false)
      val sum = low + mid + high
      // 무음(sum == 0)이면 값이 튀지 않게 갱신하지 않는다(SPEC8 2-2)
      if (sum > 0f) {
        val bassNow = low / sum
        val trebleNow = high / sum
        bassEma += BAND_EMA_ALPHA * (bassNow - bassEma)
        trebleEma += BAND_EMA_ALPHA * (trebleNow - trebleEma)
      }
      val nowMs = SystemClock.elapsedRealtime()
      val punch = follower.process(nowMs, low)
      val result = tracker.process(nowMs, rms, low)
      if (result.beat) beatCount++
      // 1초마다 상태를 한 줄 로그로 남긴다(SPEC7 9-4, SPEC8 2-3, SPEC9 2-4)
      if (nowMs - lastLogMs >= 1_000L) {
        lastLogMs = nowMs
        Log.d(TAG, "bpm=${result.bpm} conf=${result.confidence} level=${result.level} beats=$beatCount rms=$rms low=$low bass=$bassEma treble=$trebleEma punch=$punch")
      }
      _beat.value = BeatState(result.bpm, result.confidence, result.level, beatCount, true, bass = bassEma, treble = trebleEma, punch = punch)
    }
  }

  /** 출력 읽기를 시작한다. 권한이 없거나 Visualizer 생성·설정에 실패하면 로그를 남기고 false */
  fun start(): Boolean {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "시작 실패: RECORD_AUDIO 권한 없음")
      return false
    }
    if (visualizer != null) return true
    val range = try {
      Visualizer.getCaptureSizeRange()
    } catch (e: Exception) {
      Log.w(TAG, "captureSizeRange 조회 실패", e)
      null
    }
    if (range == null || range.size < 2) {
      Log.w(TAG, "시작 실패: captureSizeRange 없음")
      return false
    }
    val size = range[1]
    val v = try {
      Visualizer(0)
    } catch (e: Exception) {
      Log.w(TAG, "시작 실패: Visualizer(0) 생성 불가", e)
      return false
    }
    visualizer = v
    val ok = step("setCaptureSize($size)") { v.setCaptureSize(size) } &&
      step("setScalingMode") { v.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED) } &&
      step("setDataCaptureListener") {
        v.setDataCaptureListener(listener, Visualizer.getMaxCaptureRate(), true, true)
      } &&
      step("setEnabled") { v.setEnabled(true) }
    if (!ok) {
      releaseVisualizer()
      return false
    }
    captureSize = size
    tracker.reset()
    follower.reset()
    beatCount = 0L
    lastLogMs = 0L
    bassEma = 0f
    trebleEma = 0f
    synchronized(dataLock) { lastRms = 0f }
    return true
  }

  /** Visualizer 설정 단계 하나를 실행한다. 예외나 오류 코드면 로그를 남기고 false */
  private fun step(name: String, block: () -> Int): Boolean {
    val status = try {
      block()
    } catch (e: Exception) {
      Log.w(TAG, "$name 예외", e)
      return false
    }
    if (status != Visualizer.SUCCESS) {
      Log.w(TAG, "$name 실패 status=$status")
      return false
    }
    return true
  }

  /** 출력 읽기를 멈추고 상태를 되돌린다 */
  fun stop() {
    releaseVisualizer()
    tracker.reset()
    follower.reset()
    beatCount = 0L
    lastLogMs = 0L
    bassEma = 0f
    trebleEma = 0f
    synchronized(dataLock) { lastRms = 0f }
    _beat.value = BeatState(null, 0f, 0f, 0L, false)
  }

  /** 더 쓰지 않을 때 해제한다 */
  fun release() {
    stop()
  }

  /** Visualizer를 끄고 해제한다. 없으면 아무것도 하지 않는다 */
  private fun releaseVisualizer() {
    val v = visualizer ?: return
    visualizer = null
    try {
      v.setEnabled(false)
    } catch (_: Exception) {
      // 이미 멈춘 경우 무시한다
    }
    try {
      v.release()
    } catch (_: Exception) {
      // 종료 경로의 실패는 무시한다
    }
  }

  // --- 캡처 계산(OutputProbeActivity와 같은 방식) ---

  /** 파형(부호 없는 8비트) → RMS: v = ((b and 0xFF) - 128) / 128.0 */
  private fun waveformRms(waveform: ByteArray): Float {
    if (waveform.isEmpty()) return 0f
    var sum = 0.0
    for (b in waveform) {
      val v = ((b.toInt() and 0xFF) - 128) / 128.0
      sum += v * v
    }
    return sqrt(sum / waveform.size).toFloat()
  }

  /**
   * FFT 바이트 배열: [0]=실수부 0번, [1]=실수부 n/2번, 그 뒤 k=1..n/2-1의 (실수, 허수) 쌍.
   * k번 주파수 = k × (samplingRate/1000) / captureSize Hz(samplingRate 인자는 mHz 단위).
   * minHz~maxHz에 드는 bin의 크기(sqrt(re²+im²)) 합. fallback이 참이면 bin이 하나도
   * 없을 때 k=1..4로 대체한다(low 대역의 이전 동작 유지용).
   */
  private fun fftBandEnergy(fft: ByteArray, samplingRateMHz: Int, minHz: Double, maxHz: Double, fallback: Boolean): Float {
    val n = captureSize
    if (n <= 0 || fft.size < 2) return 0f
    val binHz = (samplingRateMHz / 1000.0) / n
    if (binHz <= 0.0) return 0f
    val kLimit = fft.size / 2 - 1 // k = 1..n/2-1 → 배열 인덱스 2k+1까지 사용
    if (kLimit < 1) return 0f
    var kMin = ceil(minHz / binHz).toInt()
    var kMax = floor(maxHz / binHz).toInt()
    if (kMin < 1) kMin = 1
    if (kMax > kLimit) kMax = kLimit
    if (kMin > kMax) {
      // 대역에 bin이 없으면 k=1..4로 대체한다(low 대역에서만 쓴다)
      if (!fallback) return 0f
      kMin = 1
      kMax = min(4, kLimit)
    }
    if (kMin > kMax) return 0f
    var sum = 0.0
    for (k in kMin..kMax) {
      val re = fft[2 * k].toInt().toDouble()
      val im = fft[2 * k + 1].toInt().toDouble()
      sum += sqrt(re * re + im * im)
    }
    return sum.toFloat()
  }

  companion object {
    private const val TAG = "OutputTap"
    /** 대역별 비중 지수 평균의 계수(SPEC8 2-2) */
    private const val BAND_EMA_ALPHA = 0.05f
  }
}
