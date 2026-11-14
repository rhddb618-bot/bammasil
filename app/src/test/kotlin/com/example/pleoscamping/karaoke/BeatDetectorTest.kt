package com.example.pleoscamping.karaoke

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BeatDetector 단위 시험(SPEC6 1-6절).
 * 16 kHz 합성 신호(박자마다 60 Hz 사인 킥 80 ms 지수 감쇠 + 반박마다 백색잡음 하이햇 20 ms
 * + 배경 잡음 진폭 0.02)로 BPM 추정 정확도와 무음 판정을 확인한다.
 */
class BeatDetectorTest {

  private val sampleRate = 16_000

  /** 목표 BPM의 10초 드럼 트랙을 합성한다 */
  private fun synthTrack(bpm: Int, seconds: Int = 10): ShortArray {
    val total = seconds * sampleRate
    val out = ShortArray(total)
    val rand = Random(42L)
    // 배경 잡음(진폭 0.02)
    for (i in 0 until total) {
      out[i] = ((rand.nextDouble() * 2 - 1) * 0.02 * 32767).toInt().coerceIn(-32768, 32767).toShort()
    }
    val halfBeatSamples = sampleRate * 60.0 / bpm / 2.0
    val kickLen = sampleRate * 80 / 1000
    val hatLen = sampleRate * 20 / 1000
    var k = 0
    while (true) {
      val start = (k * halfBeatSamples).toInt()
      if (start >= total) break
      if (k % 2 == 0) {
        // 킥: 60 Hz 사인, 80 ms, 지수 감쇠(시정수 20 ms)
        for (i in 0 until kickLen) {
          val idx = start + i
          if (idx >= total) break
          val t = i.toDouble() / sampleRate
          val value = sin(2.0 * PI * 60.0 * t) * exp(-t / 0.02) * 0.8
          val mixed = out[idx] + (value * 32767).toInt()
          out[idx] = mixed.coerceIn(-32768, 32767).toShort()
        }
      } else {
        // 하이햇: 백색잡음 20 ms
        for (i in 0 until hatLen) {
          val idx = start + i
          if (idx >= total) break
          val value = (rand.nextDouble() * 2 - 1) * 0.25
          val mixed = out[idx] + (value * 32767).toInt()
          out[idx] = mixed.coerceIn(-32768, 32767).toShort()
        }
      }
      k++
    }
    return out
  }

  /** 20ms 조각(실제 녹음 루프와 같은 크기)으로 나눠 넣고 마지막 결과를 돌려준다 */
  private fun feed(detector: BeatDetector, pcm: ShortArray): List<BeatDetector.Result> {
    val all = ArrayList<BeatDetector.Result>()
    val chunk = sampleRate / 50
    var pos = 0
    while (pos < pcm.size) {
      val n = minOf(chunk, pcm.size - pos)
      all.addAll(detector.process(pcm.copyOfRange(pos, pos + n), n))
      pos += n
    }
    return all
  }

  private fun assertBpm(target: Int) {
    val detector = BeatDetector(sampleRate)
    val results = feed(detector, synthTrack(target))
    assertTrue("결과가 비어 있음", results.isNotEmpty())
    val last = results.last()
    assertNotNull("BPM 미추정(무음 판정?) bpm=null", last.bpm)
    val bpm = last.bpm!!
    assertTrue(
      "목표 $target BPM에서 $bpm 추정(±3 허용, 절반·두 배는 실패)",
      abs(bpm - target) <= 3.0f
    )
  }

  @Test
  fun detects100Bpm() = assertBpm(100)

  @Test
  fun detects120Bpm() = assertBpm(120)

  @Test
  fun detects128Bpm() = assertBpm(128)

  @Test
  fun detects140Bpm() = assertBpm(140)

  @Test
  fun silenceGivesNoBeatAndNoBpm() {
    val detector = BeatDetector(sampleRate)
    val total = 10 * sampleRate
    val noise = ShortArray(total) { i ->
      (((i * 2654435761L % 1000L) / 1000.0 - 0.5) * 2 * 0.001 * 32767).toInt().toShort()
    }
    val results = feed(detector, noise)
    assertTrue(results.isNotEmpty())
    assertTrue("무음에서는 bpm이 null이어야 함", results.last().bpm == null)
    assertTrue("무음에서는 beat가 없어야 함", results.none { it.beat })
  }
}
