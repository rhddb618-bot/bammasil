package com.example.pleoscamping.karaoke

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.isActive

/**
 * 시연용 박자 음원(SPEC6 5절, DEBUG 빌드 전용).
 * 에뮬레이터가 PC 마이크 소리를 안정적으로 받지 못해, 앱이 16 kHz 모노 PCM을
 * 마디 단위로 합성해 스피커로 무한 반복 재생하고 같은 덩어리를 [onChunk]로 넘긴다.
 * 마이크는 거치지 않는다.
 *
 * 한 마디(4박자) 구성:
 * - 킥: 매 박 첫 120 ms, 60→40 Hz 사인, 지수 감쇠(τ 30 ms), 진폭 0.8
 * - 클랩: 2·4박 첫 60 ms, 백색잡음 1차 차분(고역 강조), 진폭 0.35
 * - 하이햇: 반박마다 첫 25 ms, 백색잡음 × 지수 감쇠(τ 8 ms), 진폭 0.18
 * - 베이스: 1·3박 첫 250 ms, 55 Hz 사인, 진폭 0.3
 */
class DemoBeatSource(
  private val bpm: Int,
  private val onChunk: (samples: ShortArray, count: Int) -> Unit
) {

  private var track: AudioTrack? = null

  /** 잡음 합성용 난수. buildBar보다 먼저 초기화되어야 한다 */
  private val random = Random(20261110)

  /** 한 마디 PCM. 재생은 이 배열을 계속 순환한다 */
  private val bar: ShortArray = buildBar()

  /** AudioTrack(USAGE_MEDIA, CONTENT_TYPE_MUSIC, 16 kHz, MODE_STREAM)을 만들고 재생을 시작한다 */
  fun start(): Boolean {
    if (track != null) return true
    val bufferBytes = max(
      AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
      CHUNK_SIZE * 2 * 4
    )
    val t = try {
      AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(bufferBytes)
        .build()
    } catch (e: Exception) {
      Log.w(TAG, "AudioTrack 생성 실패", e)
      null
    } ?: return false
    return try {
      t.play()
      track = t
      true
    } catch (e: Exception) {
      Log.w(TAG, "AudioTrack.play 실패", e)
      t.release()
      false
    }
  }

  /**
   * 1024샘플씩 마디를 순환하며 쓴다. write는 블로킹이라 재생 속도에 자연히 맞춰지고,
   * 쓰기 직후 같은 덩어리를 [onChunk]로 넘긴다. 취소되거나 쓰기에 실패하면 반환한다.
   */
  suspend fun loop() {
    val t = track ?: return
    val chunk = ShortArray(CHUNK_SIZE)
    var pos = 0
    while (coroutineContext.isActive) {
      for (i in 0 until CHUNK_SIZE) {
        chunk[i] = bar[pos]
        pos = (pos + 1) % bar.size
      }
      val written = try {
        t.write(chunk, 0, CHUNK_SIZE)
      } catch (e: Exception) {
        Log.w(TAG, "AudioTrack.write 실패", e)
        break
      }
      if (written <= 0) break
      onChunk(chunk, written)
    }
  }

  /** 재생을 멈추고 AudioTrack을 해제한다. 블로킹 write도 여기서 풀린다 */
  fun stop() {
    val t = track ?: return
    track = null
    try {
      t.stop()
    } catch (_: Exception) {
      // 이미 멈춘 경우 무시한다
    }
    t.release()
  }

  // --- 합성 ---

  private fun buildBar(): ShortArray {
    val beat = (60.0 * SAMPLE_RATE / bpm).toInt()
    val total = beat * BEATS_PER_BAR
    val mix = FloatArray(total)
    for (b in 0 until BEATS_PER_BAR) {
      val start = b * beat
      addKick(mix, start)
      addHat(mix, start)
      addHat(mix, start + beat / 2)
      if (b == 1 || b == 3) addClap(mix, start) // 2·4박
      if (b == 0 || b == 2) addBass(mix, start) // 1·3박
    }
    val out = ShortArray(total)
    for (i in 0 until total) {
      // 전체 볼륨 0.6. 각 진폭의 합(1.63)을 곱해도 1을 넘지 않아 클리핑이 없고, 혹시 넘으면 잘라 막는다
      val v = (mix[i].toDouble() * MASTER_GAIN).coerceIn(-1.0, 1.0)
      out[i] = (v * Short.MAX_VALUE).toInt().toShort()
    }
    return out
  }

  private fun addKick(pcm: FloatArray, start: Int) {
    var phase = 0.0
    for (i in 0 until KICK_SAMPLES) {
      val pos = start + i
      if (pos >= pcm.size) break
      val freq = KICK_FREQ_START + (KICK_FREQ_END - KICK_FREQ_START) * i / KICK_SAMPLES
      phase += 2.0 * PI * freq / SAMPLE_RATE
      val t = i / SAMPLE_RATE.toDouble()
      pcm[pos] += (KICK_AMP * exp(-t / KICK_TAU_SEC) * sin(phase)).toFloat()
    }
  }

  private fun addClap(pcm: FloatArray, start: Int) {
    var prev = 0.0
    for (i in 0 until CLAP_SAMPLES) {
      val pos = start + i
      if (pos >= pcm.size) break
      val raw = random.nextDouble(-1.0, 1.0)
      val diff = raw - prev // 1차 차분 필터로 고역을 강조한다
      prev = raw
      pcm[pos] += (CLAP_AMP * diff).toFloat()
    }
  }

  private fun addHat(pcm: FloatArray, start: Int) {
    for (i in 0 until HAT_SAMPLES) {
      val pos = start + i
      if (pos >= pcm.size) break
      val t = i / SAMPLE_RATE.toDouble()
      pcm[pos] += (HAT_AMP * exp(-t / HAT_TAU_SEC) * random.nextDouble(-1.0, 1.0)).toFloat()
    }
  }

  private fun addBass(pcm: FloatArray, start: Int) {
    for (i in 0 until BASS_SAMPLES) {
      val pos = start + i
      if (pos >= pcm.size) break
      val t = i / SAMPLE_RATE.toDouble()
      pcm[pos] += (BASS_AMP * sin(2.0 * PI * BASS_FREQ * t)).toFloat()
    }
  }

  companion object {
    private const val TAG = "DemoBeatSource"
    private const val SAMPLE_RATE = 16_000

    /** write·onChunk 단위 샘플 수 */
    private const val CHUNK_SIZE = 1024

    private const val BEATS_PER_BAR = 4

    /** 킥: 매 박, 60→40 Hz 사인 120 ms, τ 30 ms, 진폭 0.8 */
    private const val KICK_SAMPLES = 120 * SAMPLE_RATE / 1000
    private const val KICK_FREQ_START = 60.0
    private const val KICK_FREQ_END = 40.0
    private const val KICK_TAU_SEC = 0.030
    private const val KICK_AMP = 0.8

    /** 클랩: 2·4박, 백색잡음 60 ms 1차 차분, 진폭 0.35 */
    private const val CLAP_SAMPLES = 60 * SAMPLE_RATE / 1000
    private const val CLAP_AMP = 0.35

    /** 하이햇: 반박마다 백색잡음 25 ms, τ 8 ms, 진폭 0.18 */
    private const val HAT_SAMPLES = 25 * SAMPLE_RATE / 1000
    private const val HAT_TAU_SEC = 0.008
    private const val HAT_AMP = 0.18

    /** 베이스: 1·3박 55 Hz 사인 250 ms, 진폭 0.3 */
    private const val BASS_SAMPLES = 250 * SAMPLE_RATE / 1000
    private const val BASS_FREQ = 55.0
    private const val BASS_AMP = 0.3

    /** 전체 볼륨 */
    private const val MASTER_GAIN = 0.6
  }
}
