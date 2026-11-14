# 밤마실

**밤에 차를 세우고, 차 앞유리 너머 별자리를 찾고 무드 조명으로 캠핑 분위기를 만드는 Pleos Connect 차량 앱입니다.**

화면에는 두 가지 기능이 있습니다.

- **별 보기**: 켜면 실내등·독서등·무드 조명을 끄고 화면을 어둡게 낮춥니다. 차의 위치와 차 앞쪽 방향을 기준으로 지금 보이는 별자리·행성·달을 그리고, 별자리를 차 기준 앞·오른쪽·뒤·왼쪽으로 묶어 보여 줍니다. 낮에 열면 별이 보이지 않는다고 알리고 [오늘 밤 하늘 보기]로 어두워지는 시각의 하늘을 보여 줍니다.
- **캠핑 조명**: 무드 조명을 모닥불·랜턴·노을·휴식 블루·취침 레드 5가지 색으로 바꾸고, 색마다 어울리는 소리(장작·풀벌레·파도·빗소리·바람)를 함께 켭니다.

끄거나 주행(D)으로 바꾸면 차의 조명을 켜기 전 상태로 되돌립니다. 주차(P)에서만 켜집니다.

## APK

- 위치: [`apk/DBDKS_밤마실_release.apk`](apk/DBDKS_밤마실_release.apk)
- release 빌드, APK 서명 v3, versionName 1.2 (versionCode 7), 패키지 `com.example.pleoscamping`

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect 에뮬레이터 **v2.1.0** (AAOS 14) |
| 화면 규격 | **2560 × 1440** (가로, 밀도 1x) |
| 빌드 | compileSdk 36, minSdk 28, targetSdk 36, JDK 17 이상 |
| SDK | Pleos Vehicle 2.3.2, FusedLocation 1.1.3 |

## 실행 방법

1. Pleos Connect 에뮬레이터 v2.1.0을 켭니다.
2. APK를 설치합니다. 에뮬레이터의 사용자는 10번이라 `--user 10`을 붙입니다.
   ```
   adb install -r --user 10 apk/DBDKS_밤마실_release.apk
   ```
3. 앱 서랍에서 **밤마실**(인디고 바탕 달 아이콘)을 엽니다.
4. 첫 실행 때 앱이 먼저 권한 안내를 보여 주고, [확인]을 누르면 권한 창 2개가 뜹니다. 모두 허용합니다.
   - 위치: 지금 있는 곳의 밤하늘 계산
   - "알 수 없는 작업": Pleos FusedLocation 권한(차가 바라보는 방향). Pleos 쪽 설명 문구가 비어 있어 이렇게 보입니다.
5. 위쪽에서 **별 보기** 또는 **캠핑 조명**을 고르고 [켜기]를 누릅니다.

## 필요한 설정

- **가상 차량 콘솔(Virtual Console)을 열어 두어야 조명 제어가 반영됩니다.** 에뮬레이터의 차량 값은 Virtual Console 페이지가 열려 있을 때만 바뀝니다. 페이지가 닫혀 있으면 앱이 명령을 보내도 조명 값이 그대로입니다.
  - PC 브라우저에서 열 때: `adb forward tcp:8088 tcp:8088` 뒤 `http://127.0.0.1:8088`
  - 무드 조명 색은 Virtual Console 아래쪽 계기판에 보입니다. 실내등·독서등은 콘솔에 표시가 없습니다.
- **기어는 P(주차)** 여야 켜집니다. Virtual Console에서 D로 바꾸면 앱이 조명을 켜기 전 상태로 되돌립니다(D에서는 Pleos 시스템이 앱 화면을 가립니다).
- 에뮬레이터는 차량 방위를 0°(북)로만 줍니다. 별 보기 목록 위의 "차 앞쪽 방향"에서 북·동·남·서를 고르면 방향에 따라 목록과 시선이 바뀝니다.
- 위치는 에뮬레이터의 GPS 값을 씁니다. 바꾸려면 `adb emu geo fix <경도> <위도>` (예: 서울 `127.0276 37.4979`). 위치를 받지 못하면 서울로 계산합니다.
- 인터넷 연결은 필요 없습니다. 천문 계산은 기기 안에서 합니다.

## 소스에서 빌드

```
./gradlew testDebugUnitTest     # 단위 시험 25개
./gradlew assembleDebug         # 개발 빌드(개발 시험 카드 포함)
./gradlew assembleRelease       # 배포 빌드
```

- Pleos SDK(`ai.pleos.playground:Vehicle`, `FusedLocation`)는 Pleos 저장소(`nexus-playground.pleos.ai`)에서 받습니다.
- 배포 서명 키는 저장소에 없습니다. `../signing/keystore.properties`가 있으면 그 키로 서명하고, 없으면 서명 없이 빌드됩니다.

## 폴더

```
apk/                     제출용 release APK
app/src/main/kotlin/com/example/pleoscamping/
  MainActivity.kt        권한 요청, 위치 읽기, 화면 진입
  astro/                 밤하늘 계산(SkyEngine), 별 데이터 읽기, 쉬운 말 안내(SkyGuide)
  mode/                  모드 켜기·끄기와 조명 복원(ModeController)
  vehicle/               Pleos Vehicle SDK 호출을 감싼 VehicleGateway
  ui/                    화면(HomeScreen, StargazingPanel, CampLightPanel 등)과 CampingViewModel
  karaoke/               소리 재생기(DemoTrackPlayer)와, 화면에서 숨겨 둔 음악 조명용 코드
app/src/main/assets/sky/ 별·별자리 데이터
app/src/main/res/raw/    색별 소리 5가지
```

화면에서 숨겨 둔 기능(음악 조명, 배터리 가드, 일출 알람)의 코드가 함께 들어 있습니다. `mode/CampingMode.kt`의 `VISIBLE_MODES`가 화면에 보이는 모드를 정합니다. 숨긴 기능이 쓰는 권한(공조, 시트, 오디오 녹음, 인터넷)은 개발 빌드(`app/src/debug/AndroidManifest.xml`)에만 있습니다.

## 권한(배포 빌드)

| 권한 | 쓰임 |
|---|---|
| `pleos.car.permission.CAR_LIGHTS`, `CONTROL_CAR_LIGHTS` | 무드 조명 색·밝기 제어, 별 보기에서 실내등·독서등 끄기, 켜기 전 조명 상태 읽기와 복원 |
| `pleos.car.permission.CAR_DRIVE` | 기어 상태(주차일 때만 동작, 주행 전환 때 복원) |
| `pleos.car.permission.CAR_ENERGY` | 배터리 잔량 표시 |
| `pleos.car.permission.FUSED_LOCATION` | 차가 바라보는 방향 |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | 지금 있는 곳의 밤하늘 계산 |

## 쓴 오픈소스와 음원

- Astronomy Engine (MIT, Don Cross): 천문 계산
- d3-celestial (BSD-3-Clause, Olaf Frohn): 별·별자리 데이터. 별 목록은 XHIP(Anderson & Francis 2012, VizieR V/137D)
- Lucide (ISC): 아이콘
- 소리 5가지와 숨긴 기능의 시연 음원: 모두 CC0, OpenGameArt.org (출처는 앱 화면과 `ui/CampLightPanel.kt`에 적혀 있습니다)

라이선스 전문은 `app/src/main/assets/licenses/`에 있습니다.
