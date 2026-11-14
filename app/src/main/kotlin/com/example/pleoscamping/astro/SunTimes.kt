package com.example.pleoscamping.astro

import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.searchAltitude
import io.github.cosinekitty.astronomy.searchRiseSet

/**
 * 다음 일출·시민박명 시작 시각(Astronomy Engine, SPEC6 3절).
 * 둘 다 지금 이후 첫 번째를 찾으므로 저녁에 물으면 자동으로 내일 아침이 된다.
 * 시민박명 시작 = 해 중심이 지평선 아래 6°에 도달하는 시각(일출보다 26~31분 이르다, 한국 기준).
 */
object SunTimes {

  data class Result(
    /** 다음 시민박명 시작(epoch ms) */
    val civilDawnMillis: Long,
    /** 다음 일출(epoch ms) */
    val sunriseMillis: Long
  )

  /** 지금 이후 첫 일출과 시민박명 시작을 찾는다. 계산이 안 되는 위치(극지 등)면 null */
  fun next(lat: Double, lon: Double, nowMillis: Long): Result? {
    val obs = Observer(lat, lon, 0.0)
    val now = Time.fromMillisecondsSince1970(nowMillis)
    val sunrise = searchRiseSet(Body.Sun, obs, Direction.Rise, now, 2.0) ?: return null
    val civil = searchAltitude(Body.Sun, obs, Direction.Rise, now, 2.0, -6.0) ?: return null
    return Result(
      civilDawnMillis = civil.toMillisecondsSince1970(),
      sunriseMillis = sunrise.toMillisecondsSince1970()
    )
  }
}
