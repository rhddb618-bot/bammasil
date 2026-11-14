package com.example.pleoscamping.astro

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoField
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * 밤하늘 계산. 외부 라이브러리 없이 J2000 적경·적위로 고도·방위를 구하고
 * 달 위상(삭 기준 2000-01-06 18:14 UTC, 삭망월 29.530588일)을 계산한다.
 */
object SkyCalc {

  data class Star(
    val nameKo: String,
    val nameEn: String,
    val constellationKo: String,
    val raHours: Double,
    val decDeg: Double
  )

  data class VisibleStar(
    val star: Star,
    val altitudeDeg: Double,
    val azimuthDeg: Double
  )

  data class MoonInfo(
    val ageDays: Double,
    val phaseName: String,
    val illuminationPercent: Int
  )

  /** 밝은 별 20개 (J2000). 카노푸스는 한국에서 안 보이지만 고도 필터로 걸러진다 */
  private val STARS = listOf(
    Star("시리우스", "Sirius", "큰개자리", 6.75, -16.72),
    Star("카노푸스", "Canopus", "용골자리", 6.40, -52.70),
    Star("아크투루스", "Arcturus", "목자자리", 14.26, 19.18),
    Star("베가", "Vega", "거문고자리", 18.62, 38.78),
    Star("카펠라", "Capella", "마차부자리", 5.28, 45.99),
    Star("리겔", "Rigel", "오리온자리", 5.24, -8.20),
    Star("프로키온", "Procyon", "작은개자리", 7.65, 5.22),
    Star("베텔게우스", "Betelgeuse", "오리온자리", 5.92, 7.41),
    Star("알타이르", "Altair", "독수리자리", 19.85, 8.87),
    Star("알데바란", "Aldebaran", "황소자리", 4.60, 16.51),
    Star("안타레스", "Antares", "전갈자리", 16.49, -26.43),
    Star("스피카", "Spica", "처녀자리", 13.42, -11.16),
    Star("폴룩스", "Pollux", "쌍둥이자리", 7.76, 28.03),
    Star("데네브", "Deneb", "백조자리", 20.69, 45.28),
    Star("레굴루스", "Regulus", "사자자리", 10.14, 11.97),
    Star("카스토르", "Castor", "쌍둥이자리", 7.58, 31.89),
    Star("폴라리스", "Polaris", "작은곰자리", 2.53, 89.26),
    Star("미자르", "Mizar", "큰곰자리", 13.40, 54.93),
    Star("알골", "Algol", "페르세우스자리", 3.14, 40.96),
    Star("마르카브", "Markab", "페가수스자리", 23.08, 15.21)
  )

  const val SYNODIC_MONTH_DAYS = 29.530588

  /** 기준 삭 2000-01-06 18:14 UTC 의 율리우스일 */
  private const val NEW_MOON_JD = 2_451_549.7597

  fun julianDay(timeMillis: Long): Double = timeMillis / 86_400_000.0 + 2_440_587.5

  /** 지방 항성시(시간 단위) */
  fun localSiderealTimeHours(jd: Double, longitudeDeg: Double): Double {
    val d = jd - 2_451_545.0
    val t = d / 36_525.0
    val gmstDeg = 280.46061837 + 360.98564736629 * d + 0.000387933 * t * t - t * t * t / 38_710_000.0
    val lstDeg = ((gmstDeg + longitudeDeg) % 360.0 + 360.0) % 360.0
    return lstDeg / 15.0
  }

  fun altitudeDeg(star: Star, lstHours: Double, latitudeDeg: Double): Double {
    val hourAngle = Math.toRadians((lstHours - star.raHours) * 15.0)
    val dec = Math.toRadians(star.decDeg)
    val lat = Math.toRadians(latitudeDeg)
    return Math.toDegrees(asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(hourAngle)))
  }

  /** 방위(북=0°, 동=90°) */
  fun azimuthDeg(star: Star, lstHours: Double, latitudeDeg: Double): Double {
    val hourAngle = Math.toRadians((lstHours - star.raHours) * 15.0)
    val dec = Math.toRadians(star.decDeg)
    val lat = Math.toRadians(latitudeDeg)
    val alt = Math.toRadians(altitudeDeg(star, lstHours, latitudeDeg))
    val azRad = atan2(-sin(hourAngle) * cos(dec), sin(dec) - sin(lat) * sin(alt))
    return (Math.toDegrees(azRad) + 360.0) % 360.0
  }

  /** 고도 minAltDeg 이상인 별을 고도 순으로 정렬 */
  fun visibleStars(
    latitudeDeg: Double,
    longitudeDeg: Double,
    timeMillis: Long,
    minAltDeg: Double = 10.0
  ): List<VisibleStar> {
    val lst = localSiderealTimeHours(julianDay(timeMillis), longitudeDeg)
    return STARS
      .map { star -> VisibleStar(star, altitudeDeg(star, lst, latitudeDeg), azimuthDeg(star, lst, latitudeDeg)) }
      .filter { it.altitudeDeg >= minAltDeg }
      .sortedByDescending { it.altitudeDeg }
  }

  private val DIR16 = arrayOf(
    "북", "북북동", "북동", "동북동", "동", "동남동", "남동", "남남동",
    "남", "남남서", "남서", "서남서", "서", "서북서", "북서", "북북서"
  )

  /** 방위를 16방위 한글로 */
  fun direction16(azimuthDeg: Double): String =
    DIR16[((azimuthDeg + 11.25) / 22.5).toInt() % 16]

  fun moon(timeMillis: Long): MoonInfo {
    val age = moonAge(julianDay(timeMillis))
    val illumination = ((1.0 - cos(2.0 * PI * age / SYNODIC_MONTH_DAYS)) / 2.0 * 100.0).roundToInt()
    return MoonInfo(age, phaseName(age), illumination)
  }

  /** NOAA 간이식으로 그날 일출 시각(지방시)을 구한다. 극야·백야면 null */
  fun sunriseLocal(latitudeDeg: Double, longitudeDeg: Double, date: LocalDate, zone: ZoneId): LocalTime? {
    val lngHour = longitudeDeg / 15.0
    val dayOfYear = date.get(ChronoField.DAY_OF_YEAR)
    val t = dayOfYear + (6.0 - lngHour) / 24.0
    val m = Math.toRadians(0.9856 * t - 3.289)
    val l = norm360(Math.toDegrees(m) + 1.916 * sin(m) + 0.020 * sin(2.0 * m) + 282.634)
    // 적경(RA)은 황경(L)과 같은 사분면으로 맞춘다
    var ra = norm360(Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l)))))
    ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
    val raHours = norm360(ra) / 15.0
    val sinDec = 0.39782 * sin(Math.toRadians(l))
    val cosDec = cos(asin(sinDec))
    val latRad = Math.toRadians(latitudeDeg)
    val cosH = (cos(Math.toRadians(90.833)) - sinDec * sin(latRad)) / (cosDec * cos(latRad))
    if (cosH > 1.0 || cosH < -1.0) return null
    val hourAngle = (360.0 - Math.toDegrees(acos(cosH))) / 15.0
    val utcHours = norm24(hourAngle + raHours - 0.06571 * t - 6.622 - lngHour)
    // 일출은 정오 전후로 오프셋이 바뀌지 않는다고 보고 정오 기준 오프셋을 쓴다
    val offsetHours = zone.rules.getOffset(date.atTime(12, 0).atZone(zone).toInstant()).totalSeconds / 3600.0
    val localHours = norm24(utcHours + offsetHours)
    return LocalTime.of(localHours.toInt(), ((localHours - localHours.toInt()) * 60.0).toInt().coerceAtMost(59))
  }

  private fun norm360(deg: Double): Double = (deg % 360.0 + 360.0) % 360.0

  private fun norm24(hours: Double): Double = (hours % 24.0 + 24.0) % 24.0

  private fun moonAge(jd: Double): Double {
    val age = (jd - NEW_MOON_JD) % SYNODIC_MONTH_DAYS
    return if (age < 0) age + SYNODIC_MONTH_DAYS else age
  }

  /** 삭/초승/상현/보름/하현/그믐 6단계 */
  private fun phaseName(age: Double): String = when {
    age < 1.5 || age >= 28.0 -> "삭"
    age < 6.0 -> "초승"
    age < 9.0 -> "상현"
    age < 16.3 -> "보름"
    age < 23.7 -> "하현"
    else -> "그믐"
  }
}
