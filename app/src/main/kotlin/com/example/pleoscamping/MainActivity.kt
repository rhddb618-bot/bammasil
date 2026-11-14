package com.example.pleoscamping

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.pleoscamping.ui.CampingApp
import com.example.pleoscamping.ui.CampingViewModel
import com.example.pleoscamping.ui.theme.PleosCampingTheme

/** Pleos FusedLocation SDK 권한(dangerous). 차량 방위(heading)에 쓴다 */
private const val FUSED_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"

class MainActivity : ComponentActivity() {

  private val viewModel: CampingViewModel by viewModels()

  private val locationPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
      if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) refreshLocation()
      viewModel.onFusedLocationPermissionResult(grants[FUSED_LOCATION_PERMISSION] == true)
    }

  /** 노래방 모드의 RECORD_AUDIO 요청. ViewModel이 콜백으로 받는다 */
  private val micPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      viewModel.onMicPermissionResult(granted)
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      PleosCampingTheme { CampingApp(viewModel) }
    }

    viewModel.requestMicPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    viewModel.requestLocationPermission = { locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, FUSED_LOCATION_PERMISSION)) }

    val fusedGranted = checkSelfPermission(FUSED_LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED
    if (hasLocationPermission() && fusedGranted) {
      refreshLocation()
      viewModel.onFusedLocationPermissionResult(true)
    } else {
      if (hasLocationPermission()) refreshLocation()
      viewModel.askLocationPermission()
    }
  }

  override fun onStart() {
    super.onStart()
    viewModel.onAppForeground()
    // 앞으로 올 때마다 차량 위치를 다시 읽는다(주차 위치가 바뀐 뒤에도 하늘이 맞도록)
    if (hasLocationPermission()) refreshLocation()
  }

  override fun onStop() {
    viewModel.onAppBackground()
    super.onStop()
  }

  private fun hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

  /** 마지막으로 알려진 위치를 한 번 읽는다. 저장하지 않고 화면 계산에만 쓴다 */
  private fun refreshLocation() {
    if (!hasLocationPermission()) return
    try {
      val manager = getSystemService(LocationManager::class.java) ?: return
      val location: Location? = manager.allProviders
        .mapNotNull { provider ->
          try {
            manager.getLastKnownLocation(provider)
          } catch (_: SecurityException) {
            null
          } catch (_: IllegalArgumentException) {
            null
          }
        }
        .maxByOrNull { it.time }
      if (location != null) {
        viewModel.updateLocation(location.latitude, location.longitude)
      }
    } catch (_: SecurityException) {
      // 권한이 없으면 기본 위치(서울)를 쓴다
    }
  }
}
