package com.example.pleoscamping.karaoke

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

/** `res/raw`의 시연 곡을 반복 재생하는 작은 재생기 */
class DemoTrackPlayer(
  private val context: Context,
  /** true면 재생할 때 오디오 포커스를 요청하고, 잃으면 멈춘다 (SPEC18 E) */
  private val useAudioFocus: Boolean = false,
  /** 포커스를 완전히 잃어 재생을 멈춘 뒤 부른다(메인 스레드) */
  private val onFocusLost: () -> Unit = {}
) {
  private var player: MediaPlayer? = null
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private var focusRequest: AudioFocusRequest? = null
  private var pausedByFocus = false

  /** 포커스 변화: 완전 상실이면 끊고, 잠깐 상실이면 멈췄다가 되찾으면 이어 재생한다 (SPEC18 E) */
  private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
    when (change) {
      AudioManager.AUDIOFOCUS_LOSS -> runCatching {
        stop()
        onFocusLost()
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> runCatching {
        if (player?.isPlaying == true) {
          pause()
          pausedByFocus = true
        }
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> runCatching {
        player?.setVolume(0.3f, 0.3f)
      }
      AudioManager.AUDIOFOCUS_GAIN -> runCatching {
        player?.setVolume(1f, 1f)
        if (pausedByFocus) {
          start()
          pausedByFocus = false
        }
      }
    }
  }

  /** 이미 재생 중이면 멈추고 새로 시작한다. 실패하면 false */
  fun play(resId: Int): Boolean {
    stop()
    if (useAudioFocus) {
      val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        .setOnAudioFocusChangeListener(focusListener, Handler(Looper.getMainLooper()))
        .build()
      if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        Log.w(TAG, "오디오 포커스를 얻지 못함")
        return false
      }
      focusRequest = request
    }
    val p = MediaPlayer()
    return try {
      p.setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build()
      )
      context.resources.openRawResourceFd(resId).use { fd -> p.setDataSource(fd) }
      p.isLooping = true
      p.prepare()
      p.start()
      player = p
      true
    } catch (e: Exception) {
      Log.e(TAG, "시연 곡 재생 실패 resId=$resId", e)
      runCatching { p.release() }
      player = null
      // 준비·시작이 실패해도 잡은 포커스가 있으면 돌려준다 (SPEC18 E)
      stop()
      false
    }
  }

  /** 재생을 멈추고 해제한다 */
  fun stop() {
    player?.let { p ->
      try {
        if (p.isPlaying) p.stop()
      } catch (e: Exception) {
        Log.w(TAG, "재생 정지 실패(무시)", e)
      }
      runCatching { p.release() }
    }
    player = null
    focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
    focusRequest = null
    pausedByFocus = false
  }

  /** 포커스를 잠깐 잃어 재생을 일시정지할 때 */
  private fun pause() {
    val p = player ?: return
    runCatching { p.pause() }
  }

  /** 일시정지했던 재생을 포커스를 되찾아 이어할 때 */
  private fun start() {
    val p = player ?: return
    runCatching { p.start() }
  }

  companion object {
    private const val TAG = "DemoTrackPlayer"
  }
}
