package com.example.pleoscamping.karaoke

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 박자 감지 상태(SPEC6 1-3절). listening은 마이크·시연 음원 루프가 도는 중인지, demo는 시연 음원으로 듣는 중인지 */
data class BeatState(
  val bpm: Float?,
  val confidence: Float,
  val level: Float,
  val beatCount: Long,
  val listening: Boolean,
  val demo: Boolean = false,
  val bass: Float = 0f,
  val treble: Float = 0f,
  val punch: Float = 0f
)

/**
 * 마이크 입력을 [BeatDetector]에 넘겨 박자 상태를 내보낸다.
 * 녹음은 저장하지도 보내지도 않고 감지에만 쓴다.
 * 오디오 소스는 UNPROCESSED를 먼저 시도하고 실패하면 MIC로 돌아간다.
 */
class MicMeter(private val context: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val detector = BeatDetector(SAMPLE_RATE)

  private val _beat = MutableStateFlow(BeatState(null, 0f, 0f, 0L, false))
  val beat: StateFlow<BeatState> = _beat.asStateFlow()

  private var record: AudioRecord? = null
  private var job: Job? = null

  // 시연용 음원(SPEC6 5절, DEBUG 빌드 전용)
  private var demoSource: DemoBeatSource? = null
  private var demoJob: Job? = null

  /** 데모 루프가 살아 있는 동안에는 마이크 루프가 BeatState를 덮어쓰지 않게 막는다 */
  @Volatile
  private var demoActive = false
  private var demoBeatCount = 0L

  /** 측정을 시작한다. 시작할 수 없으면 이유를 로그로 남기고 false를 돌려준다 */
  fun start(): Boolean {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "시작 실패: RECORD_AUDIO 권한 없음")
      return false
    }
    if (job?.isActive == true) return true
    val rec = buildRecorder(MediaRecorder.AudioSource.UNPROCESSED)
      ?: buildRecorder(MediaRecorder.AudioSource.MIC)
    if (rec == null) {
      Log.w(TAG, "시작 실패: UNPROCESSED·MIC 모두 AudioRecord 생성 불가")
      return false
    }
    record = rec
    job = scope.launch { loop(rec) }
    return true
  }

  /** 오디오 소스 하나로 AudioRecord를 만들어 본다. 실패하면 null */
  private fun buildRecorder(source: Int): AudioRecord? {
    val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (minBuffer <= 0) {
      Log.w(TAG, "AudioRecord($source) minBuffer=$minBuffer")
      return null
    }
    return try {
      val rec = AudioRecord(
        source,
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        maxOf(minBuffer, SAMPLE_RATE * 2 / 10)
      )
      if (rec.state != AudioRecord.STATE_INITIALIZED) {
        Log.w(TAG, "AudioRecord($source) 초기화 실패 state=${rec.state}")
        rec.release()
        null
      } else {
        rec
      }
    } catch (e: Exception) {
      Log.w(TAG, "AudioRecord($source) 생성 예외", e)
      null
    }
  }

  private suspend fun loop(rec: AudioRecord) {
    val buffer = ShortArray(SAMPLE_RATE / 50) // 20ms
    var beatCount = 0L
    try {
      rec.startRecording()
      while (coroutineContext.isActive && rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
        val read = rec.read(buffer, 0, buffer.size)
        if (read <= 0) continue
        val results = detector.process(buffer, read)
        var last: BeatDetector.Result? = null
        for (r in results) {
          if (r.beat) beatCount++
          last = r
        }
        if (last != null && !demoActive) {
          _beat.value = BeatState(last.bpm, last.confidence, last.level, beatCount, true)
        }
      }
    } finally {
      try {
        rec.stop()
      } catch (_: Exception) {
        // 이미 멈춘 경우 무시한다
      }
      rec.release()
      if (record === rec) record = null
      if (!demoActive) {
        // 데모 시작으로 취소된 길이 아니면 상태를 되돌린다
        detector.reset()
        _beat.value = BeatState(null, 0f, 0f, 0L, false)
      }
    }
  }

  /** 측정을 멈춘다. 녹음 해제는 루프 종료 시점에 이루어진다 */
  fun stop() {
    job?.cancel()
    job = null
    try {
      record?.stop() // 녹음을 멈춰 블로킹 read를 푼다
    } catch (_: Exception) {
      // 이미 멈춘 경우 무시한다
    }
  }

  // --- 시연용 음원 (SPEC6 5절, DEBUG 빌드 전용) ---

  /**
   * 데모를 시작한다. 마이크 녹음을 멈추고 DemoBeatSource를 Dispatchers.Default에서
   * 돌리며, onChunk마다 녹음 루프와 같은 BeatDetector 처리로 BeatState를 갱신한다.
   */
  fun startDemo(bpm: Int): Boolean {
    stopDemo()
    stop() // 마이크 녹음을 멈춘다
    val source = DemoBeatSource(bpm) { samples, count -> onDemoChunk(samples, count) }
    if (!source.start()) {
      Log.w(TAG, "시연 음원 시작 실패: AudioTrack을 만들 수 없음")
      return false
    }
    demoActive = true
    demoSource = source
    detector.reset()
    demoBeatCount = 0L
    _beat.value = BeatState(null, 0f, 0f, 0L, true, demo = true)
    demoJob = scope.launch(Dispatchers.Default) { source.loop() }
    return true
  }

  /**
   * 데모 재생을 멈추고 AudioTrack을 해제한다. 데모가 없으면 아무것도 하지 않는다.
   * 모드가 켜져 있으면 호출부(ViewModel)가 마이크 녹음으로 되돌린다.
   */
  fun stopDemo() {
    if (demoSource == null && demoJob == null && !demoActive) return
    demoJob?.cancel()
    demoJob = null
    demoActive = false
    demoSource?.stop() // write 블로킹이 여기서 풀린 뒤 루프가 끝난다
    demoSource = null
    detector.reset()
    demoBeatCount = 0L
    _beat.value = BeatState(null, 0f, 0f, 0L, false)
  }

  /** 데모 chunk를 녹음 루프와 같은 방식으로 처리한다 */
  private fun onDemoChunk(samples: ShortArray, count: Int) {
    val results = detector.process(samples, count)
    var last: BeatDetector.Result? = null
    for (r in results) {
      if (r.beat) demoBeatCount++
      last = r
    }
    if (last != null) {
      _beat.value = BeatState(last.bpm, last.confidence, last.level, demoBeatCount, true, demo = true)
    }
  }

  fun release() {
    stopDemo()
    stop()
    scope.cancel()
    try {
      record?.release()
    } catch (_: Exception) {
      // 종료 경로의 실패는 무시한다
    }
    record = null
    _beat.value = BeatState(null, 0f, 0f, 0L, false)
  }

  companion object {
    private const val TAG = "MicMeter"
    private const val SAMPLE_RATE = 16_000
  }
}
