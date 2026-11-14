package com.example.pleoscamping.karaoke

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * AudioTrack(44.1kHz mono PCM16, USAGE_MEDIA) 알림음 합성기.
 * 일출 알람·배터리 가드가 쓰는 chime()만 남긴다(SPEC6 1-1절).
 * 볼륨은 AudioTrack.setVolume으로 실시간 반영한다.
 */
class ToneSynth {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @Volatile
  private var volume = 0.7f

  fun setVolume(value: Float) {
    volume = value.coerceIn(0f, 1f)
    try {
      track?.setVolume(volume)
    } catch (_: Exception) {
      // 재생 중이 아니면 무시한다
    }
  }

  /** 재생 중인 알림음이 있으면 끊는다 */
  fun stop() {
    try {
      track?.let {
        it.pause()
        it.flush()
      }
    } catch (_: Exception) {
      // 정리 경로의 실패는 무시한다
    }
  }

  /** 두 음 알림(880Hz 150ms, 1320Hz 250ms). 일출 알람·배터리 가드가 재사용한다 */
  fun chime() {
    scope.launch {
      try {
        val t = buildTrack(CHIME_BUFFER_BYTES)
        t.setVolume(volume)
        t.play()
        val pcm = synthChime()
        t.write(pcm, 0, pcm.size)
        t.stop()
        t.release()
      } catch (_: Exception) {
        // 알림 재생 실패는 조용히 넘긴다
      }
    }
  }

  fun release() {
    stop()
    scope.cancel()
    try {
      track?.release()
    } catch (_: Exception) {
      // 종료 경로의 실패는 무시한다
    }
    track = null
  }

  private var track: AudioTrack? = null

  private fun buildTrack(bufferBytes: Int): AudioTrack = AudioTrack.Builder()
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

  /** 사인파 한 음을 out[start, start+length)에 얹는다. attack/release는 밀리초 포락선 */
  private fun addTone(out: ShortArray, start: Int, length: Int, freqHz: Double, attackMs: Int, releaseMs: Int) {
    if (length <= 0) return
    val attack = (attackMs * SAMPLE_RATE / 1000).coerceAtLeast(1)
    val release = (releaseMs * SAMPLE_RATE / 1000).coerceAtLeast(1)
    var phase = 0.0
    val step = 2.0 * PI * freqHz / SAMPLE_RATE
    for (i in 0 until length) {
      val idx = start + i
      if (idx < 0 || idx >= out.size) break
      val envelope = min(1.0, min(i.toDouble() / attack, (length - i).toDouble() / release))
      val value = sin(phase) * envelope * PEAK
      phase += step
      val mixed = out[idx] + (value * Short.MAX_VALUE).roundToInt()
      out[idx] = mixed.coerceIn(-32768, 32767).toShort()
    }
  }

  private fun synthChime(): ShortArray {
    val first = 150 * SAMPLE_RATE / 1000
    val second = 250 * SAMPLE_RATE / 1000
    val out = ShortArray(first + second)
    addTone(out, 0, first, 880.0, 10, 20)
    addTone(out, first, second, 1320.0, 10, 30)
    return out
  }

  companion object {
    private const val SAMPLE_RATE = 44_100
    private const val CHIME_BUFFER_BYTES = SAMPLE_RATE / 5 * 2
    private const val PEAK = 0.7
  }
}
