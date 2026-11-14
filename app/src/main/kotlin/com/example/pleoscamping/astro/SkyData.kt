package com.example.pleoscamping.astro

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 별 하나 (stars.json, J2000, 등급 ≤ 5.0) */
data class SkyStar(
  val hip: Int,
  val raDeg: Double,
  val decDeg: Double,
  val mag: Double,
  val bv: Double
)

/** 별자리 이름 정보 (constellations.json). raDeg/decDeg는 이름 표시 위치 */
data class SkyConstellation(
  val id: String,
  val ko: String,
  val en: String,
  val rank: Int,
  val raDeg: Double,
  val decDeg: Double
)

/** 밝은 별 고유 이름 (star_names.json). 키는 HIP 번호 */
data class StarName(
  val ko: String,
  val en: String,
  val bayer: String,
  val con: String
)

/**
 * assets/sky/ JSON 4개를 읽는 로더 (SPEC3 2-1).
 * 한 번 읽으면 프로세스가 끝날 때까지 캐시한다. JSON은 org.json(안드로이드 내장)만 쓴다.
 */
object SkyData {

  class Loaded(
    val stars: List<SkyStar>,
    val lines: Map<String, List<List<DoubleArray>>>,
    val constellations: Map<String, SkyConstellation>,
    val names: Map<Int, StarName>,
    val notes: Map<String, String>
  )

  @Volatile
  private var loaded: Loaded? = null

  suspend fun load(context: Context): Loaded {
    loaded?.let { return it }
    val app = context.applicationContext
    return withContext(Dispatchers.IO) {
      loaded?.let { return@withContext it }
      val result = Loaded(
        stars = readStars(app),
        lines = readLines(app),
        constellations = readConstellations(app),
        names = readNames(app),
        notes = readNotes(app)
      )
      loaded = result
      result
    }
  }

  private fun readText(context: Context, path: String): String =
    context.assets.open(path).bufferedReader().use { it.readText() }

  /** {"stars":[[hip, ra_deg, dec_deg, mag, bv], ...]} */
  private fun readStars(context: Context): List<SkyStar> {
    val arr = JSONObject(readText(context, "sky/stars.json")).getJSONArray("stars")
    val out = ArrayList<SkyStar>(arr.length())
    for (i in 0 until arr.length()) {
      val row = arr.getJSONArray(i)
      out += SkyStar(
        hip = row.getInt(0),
        raDeg = row.getDouble(1),
        decDeg = row.getDouble(2),
        mag = row.getDouble(3),
        bv = row.getDouble(4)
      )
    }
    return out
  }

  /** {"lines":{"Ori":[[[ra_deg,dec_deg], ...], ...], ...}} 각 경로의 점들을 순서대로 이어 선분을 만든다 */
  private fun readLines(context: Context): Map<String, List<List<DoubleArray>>> {
    val lines = JSONObject(readText(context, "sky/constellation_lines.json")).getJSONObject("lines")
    val out = HashMap<String, List<List<DoubleArray>>>(lines.length())
    val ids = lines.keys()
    while (ids.hasNext()) {
      val id = ids.next()
      val paths = lines.getJSONArray(id)
      val pathList = ArrayList<List<DoubleArray>>(paths.length())
      for (i in 0 until paths.length()) {
        val points = paths.getJSONArray(i)
        val pointList = ArrayList<DoubleArray>(points.length())
        for (j in 0 until points.length()) {
          val pt = points.getJSONArray(j)
          pointList += doubleArrayOf(pt.getDouble(0), pt.getDouble(1))
        }
        pathList += pointList
      }
      out[id] = pathList
    }
    return out
  }

  /** {"constellations":{"Ori":{"ko":..,"en":..,"rank":..,"ra":..,"dec":..}, ...}} */
  private fun readConstellations(context: Context): Map<String, SkyConstellation> {
    val cons = JSONObject(readText(context, "sky/constellations.json")).getJSONObject("constellations")
    val out = HashMap<String, SkyConstellation>(cons.length())
    val ids = cons.keys()
    while (ids.hasNext()) {
      val id = ids.next()
      val c = cons.getJSONObject(id)
      out[id] = SkyConstellation(
        id = id,
        ko = c.getString("ko"),
        en = c.getString("en"),
        rank = c.getInt("rank"),
        raDeg = c.getDouble("ra"),
        decDeg = c.getDouble("dec")
      )
    }
    return out
  }

  /** {"names":{"11767":{"ko":..,"en":..,"bayer":..,"c":..}, ...}} 키는 hip 문자열 */
  private fun readNames(context: Context): Map<Int, StarName> {
    val names = JSONObject(readText(context, "sky/star_names.json")).getJSONObject("names")
    val out = HashMap<Int, StarName>(names.length())
    val keys = names.keys()
    while (keys.hasNext()) {
      val hip = keys.next()
      val n = names.getJSONObject(hip)
      out[hip.toInt()] = StarName(
        ko = n.getString("ko"),
        en = n.getString("en"),
        bayer = n.optString("bayer"),
        con = n.optString("c")
      )
    }
    return out
  }

  /** {"notes":{"Ori":"…", ...}} 유명한 별자리 한 줄 해설. 파일이 없거나 읽기에 실패하면 빈 맵 */
  private fun readNotes(context: Context): Map<String, String> {
    return runCatching {
      val notes = JSONObject(readText(context, "sky/constellation_notes.json")).getJSONObject("notes")
      val out = HashMap<String, String>(notes.length())
      val ids = notes.keys()
      while (ids.hasNext()) {
        val id = ids.next()
        out[id] = notes.getString(id)
      }
      out
    }.getOrDefault(emptyMap())
  }
}
