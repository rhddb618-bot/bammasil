package com.example.pleoscamping.vehicle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 에뮬레이터 Virtual Console 상태 */
data class VcState(
  val available: Boolean,
  val browserActive: Boolean,
  val hvacPowerOn: Boolean
)

/**
 * 에뮬레이터 안 VC 서버(http://127.0.0.1:8088) 조회.
 * BuildConfig.DEBUG일 때만 호출한다.
 */
object VcProbe {

  private const val BASE_URL = "http://127.0.0.1:8088"

  /** GET /vehicle/state. 연결 실패면 null */
  suspend fun state(): VcState? = withContext(Dispatchers.IO) {
    try {
      val connection = URL("$BASE_URL/vehicle/state").openConnection() as HttpURLConnection
      connection.connectTimeout = 2_000
      connection.readTimeout = 2_000
      connection.requestMethod = "GET"
      try {
        if (connection.responseCode != 200) return@withContext null
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        VcState(
          available = json.optJSONObject("availability")?.optBoolean("available") ?: false,
          browserActive = json.optJSONObject("browser")?.optBoolean("active") ?: false,
          hvacPowerOn = json.optJSONObject("hvac")?.optBoolean("power_on") ?: false
        )
      } finally {
        connection.disconnect()
      }
    } catch (_: Exception) {
      null
    }
  }

  /** POST /browser/session?client=pleos-camping (VC 활성 유지 하트비트) */
  suspend fun keepAlive(): Boolean = withContext(Dispatchers.IO) {
    try {
      val connection = URL("$BASE_URL/browser/session?client=pleos-camping").openConnection() as HttpURLConnection
      connection.connectTimeout = 1_500
      connection.readTimeout = 1_500
      connection.requestMethod = "POST"
      connection.doOutput = true
      try {
        connection.outputStream.use { it.write(ByteArray(0)) }
        connection.responseCode in 200..299
      } finally {
        connection.disconnect()
      }
    } catch (_: Exception) {
      false
    }
  }
}
