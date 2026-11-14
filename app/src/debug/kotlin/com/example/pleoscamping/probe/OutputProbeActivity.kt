package com.example.pleoscamping.probe

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 디버그 빌드 전용 시험 화면: Visualizer(출력 믹스, 세션 0)로 일반 앱이
 * "차 스피커로 나가는 소리"를 읽을 수 있는지 에뮬레이터에서 확인한다.
 * src/main 의 클래스는 참조하지 않는다. 안드로이드 프레임워크만 사용.
 *
 * 실행 예:
 *   adb shell am start -n com.example.pleoscamping/com.example.pleoscamping.probe.OutputProbeActivity \
 *     --ei sec 20 --ez tone true --ei bpm 120
 *
 * extra:
 *   sec  (int,     기본 20) 측정 시간(초)
 *   tone (boolean, 기본 false) true면 이 화면이 직접 시험음을 함께 재생
 *   bpm  (int,     기본 120) 시험음 박자
 */
class OutputProbeActivity : Activity() {

    /** 캡처 하나: (절대 시각 ms, 파형 RMS, 저음 에너지) */
    private class Capture(val timeMs: Long, val rms: Double, val low: Double)

    private companion object {
        const val TAG = "OutProbe"
        const val REQ_RECORD_AUDIO = 4701
        const val SAMPLE_RATE = 44100
    }

    // UI
    private lateinit var logView: TextView

    // 인텐트 extra 설정
    private var sec = 20
    private var tone = false
    private var bpm = 120

    // 공통 상태
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var destroyed = false

    // Visualizer 측정 상태 (캡처 리스너는 별도 스레드에서 올 수 있으므로 dataLock 로 보호)
    private val dataLock = Any()
    private val entries = ArrayList<Capture>()
    private var lastRms = 0.0
    private var reportedIndex = 0
    private var probeFinished = false
    private var startElapsed = 0L
    private var visualizer: Visualizer? = null
    private var captureSize = 0
    private var measurementOk = false
    private val measurement = Visualizer.MeasurementPeakRms()

    // 시험음 상태
    private val toneLock = Any()
    private var toneThread: Thread? = null
    private var toneTrack: AudioTrack? = null

    private val listener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(v: Visualizer, waveform: ByteArray, samplingRate: Int) {
            val rms = waveformRms(waveform)
            synchronized(dataLock) { lastRms = rms }
        }

        override fun onFftDataCapture(v: Visualizer, fft: ByteArray, samplingRate: Int) {
            val low = fftLowEnergy(fft, samplingRate)
            val now = SystemClock.elapsedRealtime()
            synchronized(dataLock) { entries.add(Capture(now, lastRms, low)) }
        }
    }

    private val secondTick = object : Runnable {
        override fun run() {
            if (probeFinished || destroyed) return
            reportSecond()
            mainHandler.postDelayed(this, 1000L)
        }
    }

    private val stopRunnable = Runnable { finishProbe() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sec = intent.getIntExtra("sec", 20).coerceAtLeast(1)
        tone = intent.getBooleanExtra("tone", false)
        bpm = intent.getIntExtra("bpm", 120).coerceAtLeast(1)

        val scroll = ScrollView(this)
        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(24, 24, 24, 24)
        }
        scroll.addView(
            logView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        setContentView(scroll)

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startProbe()
        } else {
            log("RECORD_AUDIO 권한 없음 — 요청함")
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_RECORD_AUDIO) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startProbe()
        } else {
            log("FAIL permission")
        }
    }

    private fun startProbe() {
        log("probe start sec=$sec tone=$tone bpm=$bpm")

        var range: IntArray? = null
        try {
            range = Visualizer.getCaptureSizeRange()
            log("captureSizeRange=[${range[0]}..${range[1]}] maxCaptureRate=${Visualizer.getMaxCaptureRate()} mHz")
        } catch (e: Exception) {
            log("FAIL rangeInfo: ${e.javaClass.simpleName}: ${e.message}")
        }

        val v: Visualizer = try {
            Visualizer(0)
        } catch (e: Exception) {
            log("FAIL create: ${e.javaClass.simpleName}: ${e.message}")
            return
        }
        visualizer = v

        try {
            captureSize = range?.getOrNull(range.size - 1) ?: 1024
            val st = v.setCaptureSize(captureSize)
            log("setCaptureSize($captureSize) -> $st (applied=${v.captureSize})")
        } catch (e: Exception) {
            log("FAIL setCaptureSize: ${e.javaClass.simpleName}: ${e.message}")
            cleanupVisualizer()
            return
        }
        try {
            val st = v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
            log("setScalingMode(NORMALIZED) -> $st")
        } catch (e: Exception) {
            log("FAIL setScalingMode: ${e.javaClass.simpleName}: ${e.message}")
            cleanupVisualizer()
            return
        }
        try {
            val st = v.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS)
            measurementOk = st == Visualizer.SUCCESS
            log("setMeasurementMode(PEAK_RMS) -> $st (ok=$measurementOk)")
        } catch (e: Exception) {
            measurementOk = false
            log("setMeasurementMode 실패(무시): ${e.javaClass.simpleName}: ${e.message}")
        }
        try {
            val rate = Visualizer.getMaxCaptureRate()
            val st = v.setDataCaptureListener(listener, rate, true, true)
            log("setDataCaptureListener(rate=$rate, waveform=true, fft=true) -> $st")
        } catch (e: Exception) {
            log("FAIL setDataCaptureListener: ${e.javaClass.simpleName}: ${e.message}")
            cleanupVisualizer()
            return
        }
        try {
            val st = v.setEnabled(true)
            log("setEnabled(true) -> $st samplingRate=${v.samplingRate} mHz")
        } catch (e: Exception) {
            log("FAIL setEnabled: ${e.javaClass.simpleName}: ${e.message}")
            cleanupVisualizer()
            return
        }

        startElapsed = SystemClock.elapsedRealtime()
        mainHandler.postDelayed(secondTick, 1000L)
        mainHandler.postDelayed(stopRunnable, sec * 1000L)
        if (tone) startTone()
    }

    // 1초마다 최근 1초치 집계 한 줄
    private fun reportSecond() {
        val snapshot: ArrayList<Capture>
        synchronized(dataLock) {
            snapshot = ArrayList(entries.subList(reportedIndex, entries.size))
            reportedIndex = entries.size
        }
        val now = SystemClock.elapsedRealtime()
        val t = ((now - startElapsed + 999L) / 1000L).toInt()
        val count = snapshot.size
        var rmsSum = 0.0
        var rmsMax = 0.0
        var lowSum = 0.0
        var lowMax = 0.0
        for (c in snapshot) {
            rmsSum += c.rms
            if (c.rms > rmsMax) rmsMax = c.rms
            lowSum += c.low
            if (c.low > lowMax) lowMax = c.low
        }
        val sb = StringBuilder(
            String.format(
                Locale.US,
                "t=%ds captures=%d rmsAvg=%.3f rmsMax=%.3f lowAvg=%.1f lowMax=%.1f",
                t, count,
                if (count > 0) rmsSum / count else 0.0,
                rmsMax,
                if (count > 0) lowSum / count else 0.0,
                lowMax
            )
        )
        if (measurementOk) {
            try {
                val v = visualizer
                if (v != null) {
                    val st = v.getMeasurementPeakRms(measurement)
                    if (st == Visualizer.SUCCESS) {
                        sb.append(" mPeak=").append(measurement.mPeak)
                            .append(" mRms=").append(measurement.mRms).append("mB")
                    }
                }
            } catch (_: Exception) {
                // 측정값은 보조 정보이므로 무시
            }
        }
        log(sb.toString())
    }

    private fun finishProbe() {
        if (probeFinished) return
        probeFinished = true
        mainHandler.removeCallbacks(secondTick)
        val v = visualizer
        if (v != null) {
            try {
                log("setEnabled(false) -> ${v.setEnabled(false)}")
            } catch (e: Exception) {
                log("FAIL setEnabled(false): ${e.javaClass.simpleName}: ${e.message}")
            }
            try {
                v.release()
                log("visualizer released")
            } catch (e: Exception) {
                log("FAIL release: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        summarize()
    }

    private fun summarize() {
        val snapshot: List<Capture> = synchronized(dataLock) { ArrayList(entries) }
        var rmsSum = 0.0
        var rmsMax = 0.0
        var nonSilent = 0
        for (c in snapshot) {
            rmsSum += c.rms
            if (c.rms > rmsMax) rmsMax = c.rms
            if (c.rms > 0.01) nonSilent++
        }
        val n = snapshot.size
        log(
            String.format(
                Locale.US,
                "RESULT captures=%d nonSilent=%d rmsAvg=%.4f rmsMax=%.4f",
                n, nonSilent,
                if (n > 0) rmsSum / n else 0.0,
                rmsMax
            )
        )

        val intervalMs: Double = if (n >= 2) {
            (snapshot[n - 1].timeMs - snapshot[0].timeMs).toDouble() / (n - 1)
        } else 0.0
        val bpmText = if (n < 40) "none" else (estimateBpm(snapshot, intervalMs) ?: "none")
        log(String.format(Locale.US, "RESULT bpmEstimate=%s captureIntervalMs=%.1f", bpmText, intervalMs))
        log("RESULT done")
    }

    /**
     * 저음 에너지의 증가분(양수만)을 onset 로 보고, 캡처 간격 정수배 지연에 대해
     * 자기상관을 구해 60~180 BPM 범위에서 최대가 되는 지연을 BPM으로 바꾼다.
     * 최대 지연 주변은 포물선 보간으로 소수 지연을 찾는다. 못 구하면 null.
     */
    private fun estimateBpm(data: List<Capture>, intervalMs: Double): String? {
        val n = data.size
        if (intervalMs <= 0.0) return null
        val onset = DoubleArray(n)
        for (i in 1 until n) {
            val d = data[i].low - data[i - 1].low
            if (d > 0.0) onset[i] = d
        }
        val dMin = maxOf(1.0, ceil(60000.0 / (180.0 * intervalMs))).toInt() // 180 BPM = 짧은 주기
        val dMax = minOf(floor(60000.0 / (60.0 * intervalMs)).toInt(), n - 2) // 60 BPM = 긴 주기
        if (dMin > dMax) return null
        val r = DoubleArray(dMax + 1)
        var bestD = dMin
        var bestR = Double.NEGATIVE_INFINITY
        for (d in dMin..dMax) {
            var s = 0.0
            for (i in 0 until n - d) {
                s += onset[i] * onset[i + d]
            }
            r[d] = s
            if (s > bestR) {
                bestR = s
                bestD = d
            }
        }
        var dFrac = bestD.toDouble()
        if (bestD > dMin && bestD < dMax) {
            val rm = r[bestD - 1]
            val r0 = r[bestD]
            val rp = r[bestD + 1]
            val denom = rm - 2.0 * r0 + rp
            if (denom != 0.0) {
                var shift = 0.5 * (rm - rp) / denom
                if (shift < -0.5) shift = -0.5
                if (shift > 0.5) shift = 0.5
                dFrac = bestD + shift
            }
        }
        val beatMs = dFrac * intervalMs
        if (beatMs <= 0.0) return null
        return String.format(Locale.US, "%.1f", 60000.0 / beatMs)
    }

    // 파형(부호 없는 8비트) → RMS: v = ((b and 0xFF) - 128) / 128.0
    private fun waveformRms(waveform: ByteArray): Double {
        if (waveform.isEmpty()) return 0.0
        var sum = 0.0
        for (b in waveform) {
            val v = ((b.toInt() and 0xFF) - 128) / 128.0
            sum += v * v
        }
        return sqrt(sum / waveform.size)
    }

    /**
     * FFT 바이트 배열: [0]=실수부 0번, [1]=실수부 n/2번, 그 뒤 k=1..n/2-1 의 (실수, 허수) 쌍.
     * k번 주파수 = k × (samplingRate/1000) / captureSize Hz (samplingRate 인자는 mHz 단위).
     * 40~200 Hz 에 드는 bin들의 크기(sqrt(re²+im²)) 합. bin 이 하나도 없으면 k=1..4 사용.
     */
    private fun fftLowEnergy(fft: ByteArray, samplingRateMHz: Int): Double {
        val n = captureSize
        if (n <= 0 || fft.size < 2) return 0.0
        val binHz = (samplingRateMHz / 1000.0) / n
        if (binHz <= 0.0) return 0.0
        val kLimit = fft.size / 2 - 1 // k = 1..n/2-1 → 배열 인덱스 2k+1 까지 사용
        if (kLimit < 1) return 0.0
        var kMin = ceil(40.0 / binHz).toInt()
        var kMax = floor(200.0 / binHz).toInt()
        if (kMin < 1) kMin = 1
        if (kMax > kLimit) kMax = kLimit
        if (kMin > kMax) {
            // 40~200 Hz 범위에 bin 이 없으면 k=1..4 로 대체
            kMin = 1
            kMax = min(4, kLimit)
        }
        if (kMin > kMax) return 0.0
        var sum = 0.0
        for (k in kMin..kMax) {
            val re = fft[2 * k].toInt().toDouble()
            val im = fft[2 * k + 1].toInt().toDouble()
            sum += sqrt(re * re + im * im)
        }
        return sum
    }

    private fun startTone() {
        val thread = Thread { playTone() }
        toneThread = thread
        thread.start()
    }

    /**
     * 시험음: 44100 Hz 모노 PCM 16비트 스트리밍. 박(60/bpm 초)마다 킥 하나
     * (150 ms, 150→50 Hz 지수 하강 사인, 진폭 0.9 에서 지수 감쇠), 박 사이는 무음.
     */
    private fun playTone() {
        var track: AudioTrack? = null
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val trackBuf = if (minBuf > 0) minBuf * 2 else SAMPLE_RATE
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(trackBuf)
                .build()
            track = t
            synchronized(toneLock) { toneTrack = t }
            t.play()
            log("tone start bpm=$bpm")

            val kickLen = (0.150 * SAMPLE_RATE).toInt()
            val beatLen = (60.0 / bpm * SAMPLE_RATE).toInt()
            val total = sec * SAMPLE_RATE

            // 킥 하나를 미리 만들어 매 박 재사용
            val kick = ShortArray(kickLen)
            var phase = 0.0
            val dt = 1.0 / SAMPLE_RATE
            for (i in 0 until kickLen) {
                val tSec = i * dt
                val freq = 50.0 + 100.0 * exp(-25.0 * tSec) // 150 → 50 Hz 지수 하강
                phase += 2.0 * PI * freq * dt
                val amp = 0.9 * exp(-18.0 * tSec) // 진폭 지수 감쇠
                val sample = (amp * sin(phase) * 32767.0).toInt()
                kick[i] = sample.coerceIn(-32768, 32767).toShort()
            }
            val silenceLen = (beatLen - kickLen).coerceAtLeast(0)
            val silence = ShortArray(silenceLen)

            var written = 0
            while (written < total && !destroyed && !Thread.currentThread().isInterrupted) {
                var w = t.write(kick, 0, min(kickLen, total - written))
                if (w < 0) break
                written += w
                if (written < total && silenceLen > 0) {
                    w = t.write(silence, 0, min(silenceLen, total - written))
                    if (w < 0) break
                    written += w
                }
            }
            t.stop()
            t.release()
            synchronized(toneLock) { if (toneTrack === t) toneTrack = null }
            log("tone end written=$written/$total samples")
        } catch (e: Exception) {
            log("FAIL tone: ${e.javaClass.simpleName}: ${e.message}")
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
            synchronized(toneLock) { if (track != null && toneTrack === track) toneTrack = null }
        }
    }

    private fun cleanupVisualizer() {
        val v = visualizer ?: return
        try { v.setEnabled(false) } catch (_: Exception) {}
        try { v.release() } catch (_: Exception) {}
        visualizer = null
    }

    private fun log(line: String) {
        Log.i(TAG, line)
        if (destroyed) return
        runOnUiThread {
            logView.append(line)
            logView.append("\n")
        }
    }

    override fun onDestroy() {
        destroyed = true
        probeFinished = true
        mainHandler.removeCallbacksAndMessages(null)
        toneThread?.interrupt()
        synchronized(toneLock) {
            try { toneTrack?.stop() } catch (_: Exception) {}
            try { toneTrack?.release() } catch (_: Exception) {}
            toneTrack = null
        }
        val v = visualizer
        if (v != null) {
            try { v.setEnabled(false) } catch (_: Exception) {}
            try { v.release() } catch (_: Exception) {}
            visualizer = null
        }
        super.onDestroy()
    }
}
