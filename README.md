# Noterly Watch

[Noterly](https://github.com/lhh9799/noterly)의 Galaxy Watch 8(Wear OS 6) 앱입니다. 폰 앱의 알림 항목을 보고, 완료·스누즈·추가·편집할 수 있습니다.
명세는 [docs/Noterly 워치 앱 명세서.md](docs/Noterly%20워치%20앱%20명세서.md)에 있습니다.

## 구조

```
noterly-watch/
  wearshared/   스냅샷·명령 데이터 클래스, Data Layer 경로, 반복 계산(Dart 포팅), 대기 명령 겹쳐 보기. 순수 Kotlin(JVM)
  wear/         워치 앱 (Compose for Wear OS Material 3, 타일, 컴플리케이션)
```

폰 쪽 변경(updatedAt·rev, 스냅샷 전송, 명령 처리)은 noterly 저장소의 `claude/galaxy-watch-app-vvmdi1` 브랜치에 있습니다.

## 동작

- 폰이 유일한 원본입니다. 폰은 저장할 때마다 `/noterly/snapshot` DataItem으로 전체 목록을 보냅니다.
- 워치는 고치고 싶은 것을 `/noterly/cmd/{commandId}` DataItem으로 보냅니다. 연결이 끊겨 있어도 워치에 남았다가 다시 연결되면 전달되고, 폰이 적용한 뒤 지웁니다.
- 워치는 마지막 스냅샷 위에 아직 폰이 처리하지 않은 명령을 겹쳐 보여 줍니다. 스냅샷의 `appliedCommands`에 들어간 명령은 대기열에서 지웁니다.
- 워치 앱이 켜질 때와 폰이 다시 연결될 때 `/noterly/sync-request` 메시지로 최신 스냅샷을 요청합니다.
- 워치는 활성 항목마다 다음 발송 시각에 정확한 알람을 걸어 두고, 알람이 울릴 때 폰이 연결돼 있지 않을 때만 직접 알림을 띄웁니다. 연결돼 있으면 폰 알림이 넘어오므로 두 번 울리지 않습니다. 직접 띄운 회차는 BridgingManager의 제외 태그에 넣어, 재연결 뒤 폰에서 같은 회차가 넘어오지 않게 합니다.

## 빌드

Android Studio(또는 Android SDK 36)가 필요합니다.

```
./gradlew :wear:assembleDevDebug        # 폰의 GitHub 릴리스 APK(dev 플레이버)와 짝
./gradlew :wear:assembleDevRelease
./gradlew :wear:assembleProductionRelease
```

- **applicationId와 서명이 폰 앱과 같아야** Data Layer가 연결됩니다. 폰의 릴리스 APK는 `dev` 플레이버(`uk.co.tdsstudios.noterly.dev`)이므로 워치도 `dev`를 설치합니다.
- 릴리스 서명: `local.properties` 또는 `~/.gradle/gradle.properties`에 `noterly.keyProperties=<noterly>/noterly/android/key.properties`를 적으면 폰과 같은 키로 서명합니다. 없으면 폰 앱처럼 로컬 debug 키로 서명하며, 같은 PC에서 빌드하면 서로 맞습니다.
- 설치: Wi-Fi ADB로 `adb -s <워치> install wear/build/outputs/apk/dev/release/wear-dev-release.apk`

Android SDK 없이 공용 모듈만 테스트할 때:

```
./gradlew -Pnoterly.sharedOnly :wearshared:test
```

반복 계산 테스트 케이스(`wearshared/src/test/resources/recurrence_cases.json`)는 폰 저장소의 `noterly/test/recurrence_cases.json`과 같은 파일이며, Dart와 Kotlin이 같은 결과를 내는지 확인합니다.

## 브랜치

`main`(릴리스) ← `develop` ← `feature/*`. 기능은 feature 브랜치에서 만들고 `develop`에 `--no-ff`로 병합합니다.

## 명세와 다른 점

- 워치 앱은 폰 저장소의 모듈이 아니라 이 저장소에 있습니다. 폰 쪽 채널 코드는 `android/app` 대신 로컬 플러그인(`noterly/packages/noterly_wear`)에 두었습니다. 플러그인이어야 알림 버튼과 워치 명령을 처리하는 백그라운드 Flutter 엔진에도 채널이 등록됩니다.
- 끊겼을 때만 알람을 거는 대신 항상 걸어 두고 울릴 때 연결 여부를 확인합니다. 연결 상태가 바뀌는 순간을 놓쳐도 중복이나 누락이 생기지 않습니다.
- 워치 자체 알림은 폰처럼 항목마다 한 번(스누즈 끝 또는 dateTime)만 울리고, 다음 반복은 완료한 뒤에 예약합니다. `upcoming`은 표시와, 워치에서 반복을 바꾼 항목의 계산에 씁니다.
- 스냅샷에 `appliedCommands`(폰이 적용·무시한 최근 명령 ID)를 추가했습니다. 워치가 대기 명령을 언제 지울지 알기 위해서입니다.
- 화면 문구는 폰 앱과 같은 영어입니다(폰 앱에도 한국어 번역이 없고, 명세의 비목표에 번역 추가가 있음).
