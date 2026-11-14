package com.example.pleoscamping.astro

import android.util.Log
import com.example.pleoscamping.BuildConfig
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.moonPhase
import io.github.cosinekitty.astronomy.searchAltitude
import io.github.cosinekitty.astronomy.searchRiseSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** 고도(altDeg)·방위(azDeg). 방위 0=북, 90=동 */
data class SkyPoint(val altDeg: Double, val azDeg: Double)

data class PlacedStar(val star: SkyStar, val p: SkyPoint)

data class PlacedBody(val label: String, val p: SkyPoint, val mag: Double)

data class MoonInfo(
  val p: SkyPoint,
  val phaseDeg: Double,
  val illumination: Double,
  val ageDays: Double,
  val phaseName: String,
  val riseMillis: Long?,
  val setMillis: Long?
)

/** 별자리 선분. conId는 선분이 속한 별자리 약자(예 "Ori") (SPEC4 2절) */
data class SkyLine(val conId: String, val a: SkyPoint, val b: SkyPoint)

data class SkyFrame(
  val timeMillis: Long,
  val stars: List<PlacedStar>,
  val lines: List<SkyLine>,
  val constellationLabels: List<Pair<SkyConstellation, SkyPoint>>,
  /** 고도 > 10°인 별자리 전부(rank 무관). 보이는 별자리 목록에 쓴다 (SPEC4 2절) */
  val allConstellations: List<Pair<SkyConstellation, SkyPoint>>,
  val planets: List<PlacedBody>,
  val moon: MoonInfo?,
  val darkStartMillis: Long?,
  val darkEndMillis: Long?,
  val meteorShower: MeteorShower?,
  /** 계산 시각의 해 고도(도). 낮·박명·밤 판정에 쓴다 (SPEC18) */
  val sunAltDeg: Double = -90.0
)

data class MeteorShower(val ko: String, val peakMonth: Int, val peakDay: Int, val zhr: Int, val radiantKo: String)

/** 방위 4구역 (SPEC4 2절) */
enum class Sector(val ko: String) { FRONT("앞"), RIGHT("오른쪽"), BACK("뒤"), LEFT("왼쪽") }

/** 보이는 별자리 한 건 (SPEC4 2절). relativeDeg는 차 앞쪽 기준 상대 방위 */
data class VisibleConstellation(
  val con: SkyConstellation,
  val altDeg: Double,
  val azDeg: Double,
  val relativeDeg: Double,
  val sector: Sector
)

/**
 * 밤하늘 한 프레임 계산 (SPEC3 2-2). 순수 함수이고 무겁다(약 2,500번의 horizon 호출) —
 * 반드시 Dispatchers.Default에서 호출한다.
 */
object SkyEngine {

  /** IMO 주요 유성우 */
  val METEOR_SHOWERS = listOf(
    MeteorShower("사분의자리 유성우", 1, 4, 80, "목동자리 북쪽"),
    MeteorShower("거문고자리 유성우", 4, 22, 18, "거문고자리"),
    MeteorShower("물병자리 에타 유성우", 5, 6, 50, "물병자리"),
    MeteorShower("페르세우스자리 유성우", 8, 13, 100, "페르세우스자리"),
    MeteorShower("오리온자리 유성우", 10, 21, 20, "오리온자리"),
    MeteorShower("사자자리 유성우", 11, 17, 15, "사자자리"),
    MeteorShower("쌍둥이자리 유성우", 12, 14, 150, "쌍둥이자리"),
    MeteorShower("작은곰자리 유성우", 12, 22, 10, "작은곰자리")
  )

  /** 화면에 그리는 맨눈 행성 5개 (SPEC3 1절) */
  private val PLANETS = listOf(
    Body.Mercury to "수성",
    Body.Venus to "금성",
    Body.Mars to "화성",
    Body.Jupiter to "목성",
    Body.Saturn to "토성"
  )

  private const val SYNODIC_MONTH_DAYS = 29.53

  fun frame(data: SkyData.Loaded, latDeg: Double, lonDeg: Double, timeMillis: Long, zone: ZoneId): SkyFrame {
    val time = Time.fromMillisecondsSince1970(timeMillis)
    val observer = Observer(latDeg, lonDeg, 0.0)

    // 별: 고도 > -2°만. ★ horizon()의 ra 인자는 시간(hours) 단위라 ra_deg / 15.0
    val stars = ArrayList<PlacedStar>(data.stars.size)
    for (s in data.stars) {
      val topo = horizon(time, observer, s.raDeg / 15.0, s.decDeg, Refraction.Normal)
      if (topo.altitude > -2.0) {
        stars += PlacedStar(s, SkyPoint(topo.altitude, topo.azimuth))
      }
    }

    // 별자리 선: 경로 안의 연속된 점 두 개가 선분. 양 끝 중 하나라도 고도 > -2°인 것만
    val lines = ArrayList<SkyLine>()
    for ((conId, paths) in data.lines) {
      for (path in paths) {
        var prev: SkyPoint? = null
        for (point in path) {
          val topo = horizon(time, observer, point[0] / 15.0, point[1], Refraction.Normal)
          val cur = SkyPoint(topo.altitude, topo.azimuth)
          val before = prev
          if (before != null && (before.altDeg > -2.0 || cur.altDeg > -2.0)) {
            lines += SkyLine(conId, before, cur)
          }
          prev = cur
        }
      }
    }

    // 별자리 이름 위치: 고도 > 10°인 것 전부를 담고(allConstellations), 라벨은 그중 rank ≤ 2만 (SPEC4 2절)
    val allConstellations = ArrayList<Pair<SkyConstellation, SkyPoint>>()
    for (c in data.constellations.values) {
      val topo = horizon(time, observer, c.raDeg / 15.0, c.decDeg, Refraction.Normal)
      if (topo.altitude > 10.0) {
        allConstellations += c to SkyPoint(topo.altitude, topo.azimuth)
      }
    }
    val labels = allConstellations.filter { it.first.rank <= 2 }

    // 행성: 관측자 기준 OfDate 적도좌표 → 다시 horizon(). 고도 > 0°만
    val planets = ArrayList<PlacedBody>()
    for ((body, label) in PLANETS) {
      val eq = equator(body, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
      val topo = horizon(time, observer, eq.ra, eq.dec, Refraction.Normal)
      if (topo.altitude > 0.0) {
        planets += PlacedBody(label, SkyPoint(topo.altitude, topo.azimuth), illumination(body, time).mag)
      }
    }

    // 달: 항상 계산한다(지평선 아래면 그리는 쪽에서 생략)
    val moonEq = equator(Body.Moon, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
    val moonTopo = horizon(time, observer, moonEq.ra, moonEq.dec, Refraction.Normal)
    val phaseDeg = moonPhase(time)
    val moon = MoonInfo(
      p = SkyPoint(moonTopo.altitude, moonTopo.azimuth),
      phaseDeg = phaseDeg,
      illumination = illumination(Body.Moon, time).phaseFraction,
      ageDays = phaseDeg / 360.0 * SYNODIC_MONTH_DAYS,
      phaseName = phaseName(phaseDeg),
      riseMillis = searchRiseSet(Body.Moon, observer, Direction.Rise, time, 1.0)?.toMillisecondsSince1970(),
      setMillis = searchRiseSet(Body.Moon, observer, Direction.Set, time, 1.0)?.toMillisecondsSince1970()
    )

    // 계산 시각의 해 고도. 낮·박명·밤 판정에 쓴다 (SPEC18 A)
    val sunEq = equator(Body.Sun, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
    val sunAltDeg = horizon(time, observer, sunEq.ra, sunEq.dec, Refraction.Normal).altitude

    // 천문 박명: 해가 -18° 아래로 내려가는/올라오는 시각
    val darkStartMillis =
      searchAltitude(Body.Sun, observer, Direction.Set, time, 1.0, -18.0)?.toMillisecondsSince1970()
    val darkEndMillis =
      searchAltitude(Body.Sun, observer, Direction.Rise, time, 1.0, -18.0)?.toMillisecondsSince1970()

    // 검증용 로그 (SPEC5 4절, DEBUG 빌드에서만)
    if (BuildConfig.DEBUG) {
      logSkyCheck(data, latDeg, lonDeg, timeMillis, observer, time, moon)
    }

    return SkyFrame(
      timeMillis = timeMillis,
      stars = stars,
      lines = lines,
      constellationLabels = labels,
      allConstellations = allConstellations,
      planets = planets,
      moon = moon,
      darkStartMillis = darkStartMillis,
      darkEndMillis = darkEndMillis,
      meteorShower = meteorShowerNear(timeMillis, zone),
      sunAltDeg = sunAltDeg
    )
  }

  /** 보이는 별자리: 이름 위치 고도 > 10°인 별자리를 차량 기준 상대 방위로 나눠 반환 (SPEC4 2절).
   *  정렬: rank 오름차순 → 고도 내림차순 */
  fun visibleConstellations(frame: SkyFrame, headingDeg: Float): List<VisibleConstellation> =
    frame.allConstellations
      .map { (con, p) ->
        val rel = (p.azDeg - headingDeg + 360.0) % 360.0
        VisibleConstellation(con, p.altDeg, p.azDeg, rel, sectorOf(rel))
      }
      .sortedWith(compareBy<VisibleConstellation> { it.con.rank }.thenByDescending { it.altDeg })

  /** 상대 방위 구역: 315~45 앞, 45~135 오른쪽, 135~225 뒤, 225~315 왼쪽 */
  private fun sectorOf(relativeDeg: Double): Sector = when {
    relativeDeg >= 315.0 || relativeDeg < 45.0 -> Sector.FRONT
    relativeDeg < 135.0 -> Sector.RIGHT
    relativeDeg < 225.0 -> Sector.BACK
    else -> Sector.LEFT
  }

  /** 오늘(zone 기준) ±2일 안에 극대인 유성우. 연초·연말 경계도 함께 본다 */
  private fun meteorShowerNear(timeMillis: Long, zone: ZoneId): MeteorShower? {
    val today = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()
    var best: MeteorShower? = null
    var bestDiff = Int.MAX_VALUE
    for (shower in METEOR_SHOWERS) {
      for (year in today.year - 1..today.year + 1) {
        val peak = runCatching { LocalDate.of(year, shower.peakMonth, shower.peakDay) }.getOrNull() ?: continue
        val diff = abs(ChronoUnit.DAYS.between(today, peak)).toInt()
        if (diff <= 2 && diff < bestDiff) {
          best = shower
          bestDiff = diff
        }
      }
    }
    return best
  }

  private fun phaseName(phaseDeg: Double): String = when {
    phaseDeg < 22.5 -> "삭"
    phaseDeg < 67.5 -> "초승"
    phaseDeg < 112.5 -> "상현"
    phaseDeg < 157.5 -> "차오름"
    phaseDeg < 202.5 -> "보름"
    phaseDeg < 247.5 -> "이지러짐"
    phaseDeg < 292.5 -> "하현"
    phaseDeg < 337.5 -> "그믐"
    else -> "삭"
  }

  /** SkyCheck 로그 태그 (SPEC5 4절) */
  private const val SKY_CHECK_TAG = "SkyCheck"

  /** 검증 대표 별: 이름 → HIP (SPEC5 4절) */
  private val CHECK_STARS = listOf(
    "Polaris" to 11767,
    "Vega" to 91262,
    "Sirius" to 32349,
    "Betelgeuse" to 27989,
    "Capella" to 24608,
    "Deneb" to 102098,
    "Altair" to 97649,
    "Arcturus" to 69673
  )

  /**
   * 검증용 로그 (SPEC5 4절, DEBUG 전용). frame의 가시 목록과 무관하게
   * 기준 별·행성 5개·달을 지평선 아래여도 horizon()으로 직접 계산해 찍는다.
   * offsetMin은 frame()이 시간 이동 offset을 알 수 없어 항상 0으로 찍는다(계산 시각 자체는 offset이 반영됨).
   */
  private fun logSkyCheck(
    data: SkyData.Loaded,
    latDeg: Double,
    lonDeg: Double,
    timeMillis: Long,
    observer: Observer,
    time: Time,
    moon: MoonInfo
  ) {
    fun f2(v: Double): String = String.format(Locale.US, "%.2f", v)
    Log.i(
      SKY_CHECK_TAG,
      "SkyCheck|lat=${String.format(Locale.US, "%.4f", latDeg)}" +
        "|lon=${String.format(Locale.US, "%.4f", lonDeg)}" +
        "|time=${Instant.ofEpochMilli(timeMillis)}|offsetMin=0"
    )
    for ((label, hip) in CHECK_STARS) {
      val s = data.stars.firstOrNull { it.hip == hip } ?: continue
      val topo = horizon(time, observer, s.raDeg / 15.0, s.decDeg, Refraction.Normal)
      Log.i(SKY_CHECK_TAG, "SkyCheck|$label|hip=$hip|alt=${f2(topo.altitude)}|az=${f2(topo.azimuth)}")
    }
    for ((body, _) in PLANETS) {
      val eq = equator(body, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
      val topo = horizon(time, observer, eq.ra, eq.dec, Refraction.Normal)
      Log.i(SKY_CHECK_TAG, "SkyCheck|$body|alt=${f2(topo.altitude)}|az=${f2(topo.azimuth)}")
    }
    Log.i(
      SKY_CHECK_TAG,
      "SkyCheck|Moon|alt=${f2(moon.p.altDeg)}|az=${f2(moon.p.azDeg)}|phase=${f2(moon.phaseDeg)}"
    )
  }
}
