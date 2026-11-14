package com.example.pleoscamping.mode

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDistanceTest {
  @Test
  fun 같은_점의_직선거리는_0() {
    assertEquals(0.0, HomeDistance.straightKm(37.5665, 126.9780, 37.5665, 126.9780), 1e-6)
  }

  @Test
  fun 서울에서_강릉까지_직선거리와_도로거리() {
    assertEquals(168.35, HomeDistance.straightKm(37.5665, 126.9780, 37.751898, 128.876100), 0.5)
    assertEquals(219, HomeDistance.roadKm(37.5665, 126.9780, 37.751898, 128.876100))
  }

  @Test
  fun 서울에서_제주까지_도로거리() {
    assertEquals(590, HomeDistance.roadKm(37.5665, 126.9780, 33.4996, 126.5312))
  }

  @Test
  fun 도로거리는_순서를_바꿔도_같다() {
    assertEquals(
      HomeDistance.roadKm(37.5665, 126.9780, 37.751898, 128.876100),
      HomeDistance.roadKm(37.751898, 128.876100, 37.5665, 126.9780)
    )
  }
}
