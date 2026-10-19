# Noterly 워치 앱 명세서

Oct 19, 2026 · @이현호

## 개요

Galaxy Watch 8에서 Noterly 알림 항목을 보고, 완료·스누즈·추가할 수 있는 Wear OS 앱을 만든다. 데이터의 원본은 폰 앱이고, 워치는 폰과 같은 항목 목록을 동기화해 보여 주며 워치에서 한 조작은 폰에 반영된다.

| 항목 | 내용 |
| --- | --- |
| 워치 | Galaxy Watch 8 (Wear OS 6, API 36) |
| 폰 | Galaxy S26 Ultra(최우선), S22 Ultra |
| 사용자 | 본인 1명, 개인용 (스토어 배포 없음) |
| 폰 앱 기준 | lhh9799/noterly main, 2026-10-19 시점 코드 |

비목표: 다른 워치·iPhone 지원, 서버나 클라우드 동기화, 워치 단독 사용(폰 없이 처음 설치해서 쓰는 경우), 번역 추가. 이번 문서는 명세만 다루고 개발은 시작하지 않는다.

## 현재 폰 앱 구조

폰 앱은 모든 항목을 JSON 파일 하나에 저장하고, flutter\_local\_notifications로 알림을 띄우거나 예약한다. 워치 동기화는 이 구조를 바꾸지 않고 그 위에 얻는다.

**알림 항목 (NotificationItem, lib/models/notification\_item.dart)**

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| id | String (UUID) | 항목 식별자. 알림 ID는 id.hashCode (Dart 해시) |
| title, body | String | 제목, 내용 |
| colour | int (ARGB) | 항목 색 |
| dateTime | ISO 8601 또는 null | null이면 즉시 알림, 값이 있으면 예약. 반복 항목은 다음 발송 시각 |
| repetitionData | 객체 또는 null | 반복 설정 (아래) |
| archived, archivedDateTime | bool, ISO 8601 | 완료(보관) 여부와 시각 |
| snoozeDateTime | ISO 8601 또는 null | 스누즈 끝나는 시각 |
| dismissible | bool | 스와이프로 알림을 지울 수 있는지 (지워도 완료 처리는 아님) |

**반복 (RepetitionData, lib/models/repetition\_data.dart)**: type(매시·매일·매주·매월·매년, enum 인덱스로 저장), number(간격), weekdays(1=월\~7=일). 매주에 요일이 있으면 다음 발송은 nextWeekdayDateTime이 계산하고, 달력 기준으로 날짜를 더해 서머타임에도 시각이 유지된다.

**앱 전체 설정 (AppData)**: snoozeDuration(기본 1시간), snoozeToastText, dismissibleNotificationsEnabled(꺼지면 모든 알림이 고정 알림이 되고 항목별 설정은 보존).

**저장**: 앱 문서 폴더의 data.json. 임시 파일에 쓴 뒤 이름을 바꾸는 원자적 저장과 .bak 백업을 쓴다 (FileManager). 모든 변경은 AppManager의 addItem·editItem·archiveItem·deleteItem·restore…·saveSettings를 거쳐 \_save()로 모인다.

**알림과 스케줄**: 즉시 항목은 show, 예약·스누즈 항목은 zonedSchedule(exactAllowWhileIdle). 알림에는 'Mark as done'과 'Snooze' 버튼이 있고, dismissible이 아니면 ongoing(고정)으로 뜬다. 반복 항목을 완료하면 스누즈를 지우고 dateTime을 미래가 될 때까지 다음 반복 시각으로 옮긴 뒤 다시 예약한다. 반복이 아니면 보관한다.

**여러 실행 경로**: 알림 버튼은 백그라운드 isolate에서 처리되고, 파일을 저장한 뒤 IsolateNameServer로 메인 isolate에 'update'를 보낸다. background\_fetch 헤드리스 작업도 있다. 워치 변경을 처리하는 코드도 이 패턴을 따라야 한다.

동기화에 없는 것: 항목에 수정 시각(updatedAt)이나 데이터 버전이 없고, 삭제는 목록에서 바로 빠진다. 알림 ID인 Dart hashCode는 Kotlin에서 같은 값을 만들 수 없다.

## 워치 앱 기능 요구사항

워치에서는 보기와 빠른 조작(완료, 스누즈)을 먼저 만들고, 그다음 반복·색을 포함한 추가와 편집을 넣는다. 우선순위 P0은 첫 버전에 반드시 들어가는 것이다.

| 기능 | 내용 | 우선순위 |
| --- | --- | --- |
| 활성 목록 | 보관되지 않은 항목을 다음 알림 시각 순으로. 색 점, 제목, 시각(또는 스누즈 끝 시각), 반복·스누즈 표시. 회전 베젤로 스크롤 | P0 |
| 항목 상세 | 제목, 내용 전체, 예약 시각, 반복 설명(예: 매주 (월, 수, 금)) | P0 |
| 완료 | 폰의 'Mark as done'과 같은 규칙: 반복이면 다음 회차로, 아니면 보관 | P0 |
| 스누즈 | 폰 설정의 snoozeDuration만큼 미룸 | P0 |
| 워치 알림 | 폰과 연결돼 있으면 넘어온 폰 알림을 쓰고, 끊기면 워치가 단독으로 띄움 (아래 '알림 동작') | P0 |
| 타일 | 다음 항목 3개와 '새 메모' 버튼 | P1 |
| 새 항목 만들기 | 제목을 음성·키보드로 입력, 시각은 '지금', '30분 뒤', '1시간 뒤', '직접 선택' 중 고름. 색은 처음에 폰과 같은 12색(colour\_picker.dart) 중 무작위로 정하고, 색 동그라미를 한 번 탭할 때마다 다른 색으로 바뀜 | P1 |
| 반복 설정 | 반복 없음·매시·매일·매주·매월·매년과 간격(숫자). 매주는 요일을 여러 개 고를 수 있음(폰의 요일 반복과 같은 규칙). 만들 때와 편집할 때 모두 | P1 |
| 편집 | 제목·시각·반복·색 수정, 삭제 | P1 |
| 보관 항목 보기·복원 | 최근 완료한 항목 목록, 탭하면 복원 | P1 |
| 컴플리케이션 | 워치 페이스에 다음 항목의 시각과 제목 | P2 |

워치에서 하지 않는 것: dismissible 스위치, 앱 설정 변경. 이 값들은 폰에서 바꾸고 워치는 보여주기만 한다.

디자인은 Compose for Wear OS의 Material 3(Wear용 Material 3 Expressive)를 쓴다. 폰 앱의 Material 3 Expressive 적용과 같은 계열이다.

## 동기화 설계

폰이 유일한 원본이다. 폰은 저장할 때마다 전체 목록 스냅샷을 워치로 보내고, 워치는 직접 고치는 대신 '명령'을 폰에 보낸다. 폰이 명령을 기존 AppManager 함수로 적용하면 새 스냅샷이 다시 워치로 간다. 항목 수가 수십 개 수준이라 전체 스냅샷으로 충분하다.

&#91;embedded content: 폰·워치 동기화 구조\]

워치는 받은 스냅샷으로 화면과 알림을 만들고, 바꾸고 싶은 것은 명령으로 폰에 보낸다. 점선은 워치 앱을 열 때의 최신 스냅샷 요청이다.

**통신 수단**: Google Wearable Data Layer API (play-services-wearable). Galaxy Watch 8과 갤럭시 폰은 Galaxy Wearable 앱으로 연결돼 있어 별도 서버 없이 동작한다. 두 앱은 applicationId(uk.co.tdsstudios.noterly)와 서명 키가 같아야 서로 데이터를 주고받을 수 있다.

| 경로 | 방향 | API | 내용 |
| --- | --- | --- | --- |
| /noterly/snapshot | 폰 → 워치 | DataClient (DataItem 1개, urgent) | 항목 전체 + 설정 + rev |
| /noterly/cmd/{commandId} | 워치 → 폰 | DataClient (명령마다 DataItem 1개) | 완료·스누즈·추가 등 명령 |
| /noterly/sync-request | 워치 → 폰 | MessageClient | 워치 앱이 켜질 때 최신 스냅샷 요청 |

명령을 MessageClient가 아닌 DataItem으로 보내는 이유: 연결이 끊겼을 때도 워치에 남아 있다가 다시 연결되면 자동으로 전달된다. 폰은 적용한 명령의 DataItem을 지워 큐를 비운다.

**스냅샷 형식** (JSON 문자열을 DataMap에 담음)

```json
{
  "schema": 1,
  "rev": 128,
  "generatedAt": "2026-10-19T09:00:00+09:00",
  "settings": { "snoozeSeconds": 3600, "dismissibleEnabled": true },
  "items": [ { "...NotificationItem.toJson() 그대로": "",
               "updatedAt": "2026-10-19T08:55:12+09:00",
               "upcoming": ["2026-10-20T08:00:00+09:00", "..."] } ]
}
```

upcoming은 반복 항목의 앞으로 5회 발송 시각을 폰이 미리 계산한 것이다. 워치 목록·타일 표시와 연결이 끊겼을 때의 단독 알림에 쓴다. 워치에서 반복을 새로 정하거나 바꾼 항목은 아직 폰 계산이 없으므로, 반복 계산(nextRepeatDateTime, nextWeekdayDateTime)을 Kotlin으로 옮겨 wearshared에 둔다. 재연결 후에는 폰이 보낸 값이 우선한다.

**명령 형식**: { commandId(UUID), type, itemId, baseRev, issuedAt, payload }. type은 done, snooze, create, update, delete, restore 중 하나다.

| 명령 | 폰에서 호출할 기존 동작 |
| --- | --- |
| done | 반복이면 snoozeDateTime=null 후 updateRepeatingNotification, 아니면 archiveItem (알림 버튼과 같은 코드 경로) |
| snooze | snoozeDateTime = issuedAt + snoozeDuration 후 editItem + showOrUpdateNotification |
| create | addItem (제목·내용·dateTime·repetitionData·색은 워치에서 정한 값, dismissible은 false) |
| update | 제목·내용·dateTime·repetitionData·색을 바꾼 editItem. 반복이 바뀌면 updateRepeatingNotification으로 다시 예약 |
| delete / restore | deleteItem / restoreArchivedItem |

**폰 쪽 변경**

1. NotificationItem에 updatedAt을, AppData에 rev(저장할 때마다 +1)를 추가한다. 기존 data.json에 없으면 기본값을 쓰므로 호환된다.
2. android/app에 Kotlin WearSync 코드를 두고 MethodChannel 'noterly/wear'로 Dart와 잇는다.
3. AppManager.\_save() 끝에서 스냅샷을 보낸다. 백그라운드 isolate(알림 버튼, background\_fetch)에서 저장해도 보내지도록 채널을 그 isolate에도 등록한다.
4. WearableListenerService(Kotlin)가 /noterly/cmd와 sync-request를 받는다. 앱이 꺼져 있으면 헤드리스 FlutterEngine으로 Dart 진입점 wearCommandEntryPoint를 실행해 AppManager로 적용하고, 알림 버튼처럼 메인 isolate에 'update'를 보낸다.

**충돌과 중복**

- 같은 commandId는 한 번만 적용한다 (폰이 최근 200개 ID를 기억).
- 명령의 대상 항목이 없거나 이미 보관됐으면 무시하고 스냅샷만 다시 보낸다.
- 오프라인 중 워치에서 완료한 반복 항목을 그사이 폰에서도 완료했다면 두 번 넘어가면 안 된다. done 명령에 워치가 본 dateTime을 담고, 폰의 dateTime이 이미 그보다 뒤면 무시한다.
- update는 항목의 updatedAt이 명령 발행 시각보다 늦으면(폰에서 더 나중에 고침) 버린다. 나머지는 나중에 도착한 쪽이 이긴다.

**워치 쪽 상태**: 마지막 스냅샷을 저장해 두고, 아직 폰이 처리하지 않은 명령을 그 위에 겹쳐 보여준다 (예: 완료 누른 항목은 바로 목록에서 빠짐). 스냅샷의 rev가 오르면 적용된 명령을 지운다. 폰과 연결이 없으면 목록 위에 '마지막 동기화 HH:mm'을 표시한다.

## 알림 동작

폰과 연결돼 있으면 알림은 지금처럼 폰이 띄우고 Galaxy Wearable이 워치로 넘겨 준다 (실기기에서 확인됨, 2026-10-19). 연결이 끊기면 워치가 마지막 스냅샷과 대기 중인 명령을 바탕으로 단독으로 알림을 띄운다.

| 상황 | 폰 | 워치 |
| --- | --- | --- |
| 예약 시각 도달 (연결됨) | 지금처럼 알림 | 넘어온 폰 알림이 진동, Mark as done·Snooze 버튼 |
| 예약 시각 도달 (끊김) | 지금처럼 알림 | 워치 자체 알림: 진동 + 완료·스누즈 버튼 |
| 넘어온 알림의 버튼 | 기존 백그라운드 처리(handleResponse)가 실행 | 폰 알림이 갱신·취소되면 따라감 |
| 워치 앱·자체 알림에서 완료·스누즈 | 명령을 받아 적용(끊겼으면 재연결 때), 폰 알림 갱신·취소 | 즉시 반영, 명령은 대기 |
| 폰에서 완료·스누즈 | 지금처럼 처리 | 새 스냅샷으로 목록·예약 갱신 |
| dismissible 항목 스와이프 | 지금처럼 목록에 남기고 다시 안 띄움 | 지워도 완료 처리 아님 |

세부 규칙:

- 연결 판단: 워치가 NodeClient·CapabilityClient로 Noterly가 설치된 폰이 연결돼 있는지 본다. 끊기면 스냅샷의 발송 시각(upcoming과 스누즈 시각)을 AlarmManager setExactAndAllowWhileIdle로 예약하고, 다시 연결되면 아직 안 울린 자체 예약을 모두 취소한다.
- 연결 중에는 워치가 자체 알림을 띄우지 않으므로 같은 알림이 두 번 울리지 않는다.
- 경계 상황: 끊긴 동안 워치가 이미 울린 회차가 재연결 직후 폰에서 넘어오면 한 번 더 보일 수 있다. 폰 알림에 항목 id와 발송 시각을 담은 bridge tag를 붙이고, 워치가 이미 울린 회차는 BridgingManager로 제외한다. flutter\_local\_notifications로 bridge tag를 붙일 수 있는지 확인하고, 안 되면 그 부분만 Kotlin으로 보완한다.
- 끊긴 동안 반복 항목을 워치에서 완료하면 워치가 Kotlin 반복 계산으로 다음 회차를 예약하고, 재연결 후 폰의 새 스냅샷으로 덮어쓴다.
- 워치 자체 알림은 dismissibleNotificationsEnabled와 항목의 dismissible을 따라 ongoing 여부를 정하고, 알림 ID는 Dart hashCode가 아닌 UUID 기반 정수를 쓴다.
- 폰 알림 코드는 bridge tag 추가 외에는 바꾸지 않는다.
- 넘어온 알림을 탭하면 워치 앱의 해당 항목 상세가 열리게 하는 것은 선택 사항이다 (P2).

## 기술 스택과 프로젝트 구조

워치 앱은 Flutter가 아닌 Kotlin + Compose for Wear OS로 만든다. Flutter는 타일·컴플리케이션·회전 베젤·Wear용 Material 3를 제대로 지원하지 않고 워치에서 배터리를 더 쓴다. 폰 앱은 지금처럼 Flutter를 유지한다.

| 구분 | 선택 |
| --- | --- |
| 언어·UI | Kotlin, Jetpack Compose for Wear OS, androidx.wear.compose:compose-material3 |
| 타일·컴플리케이션 | androidx.wear.tiles + ProtoLayout Material3, androidx.wear.watchface.complications.datasource |
| 동기화 | com.google.android.gms:play-services-wearable (폰·워치 공통) |
| 워치 저장 | DataStore에 마지막 스냅샷과 대기 중 명령 저장 |
| 음성 입력 | RemoteInput / 시스템 음성 입력 |
| minSdk | 36 (Wear OS 6, Galaxy Watch 8만 대상) |

저장소 구조 (같은 저장소, 같은 Gradle 빌드에 모듈을 추가):

```
noterly/
  lib/                         폰 앱 (Flutter), wear_sync.dart 추가
  android/
    settings.gradle            include ":wear", ":wearshared"
    app/                       폰 앱. WearSync.kt, WearCommandService.kt 추가
    wear/                      워치 앱 (applicationId 동일, 같은 서명 키)
    wearshared/                스냅샷·명령 데이터 클래스, 경로 상수 (Kotlin)
```

빌드는 폰 앱은 지금처럼 flutter build apk, 워치 앱은 android 폴더에서 gradlew :wear:assembleRelease. 워치 설치는 개인용이므로 Wi-Fi ADB로 직접 설치한다. 두 APK는 key.properties의 같은 키로 서명해야 하며, 디버그 빌드끼리도 같은 debug 키여야 한다.

워치의 반복 표시 문구(예: 매주 (월, 수, 금))는 폰이 스냅샷에 완성된 문자열(repeatLabel)로 넣어 준다. 폰의 언어 설정과 같은 표현이 나오고 번역을 새로 만들 필요가 없다.

## 테스트와 개발 단계

검증은 PC 에뮬레이터에서 최대한 하고, 실기기 확인은 마지막에 모아서 한 번만 부탁한다. 폰 에뮬레이터(S26U\_API37)와 Wear OS 6 에뮬레이터(Large Round)를 Android Studio의 Wear OS 페어링으로 연결한다. 페어링에는 Play 스토어가 들어간 폰 이미지가 필요하므로, 지금 에뮬레이터가 아니면 한 대를 더 만든다.

단계 (각 단계가 끝나면 포크 main에 병합):

1. **폰 준비**: updatedAt·rev 추가, WearSync 채널, 저장할 때마다 스냅샷 전송. 확인: 기존 data.json이 그대로 읽히고, 알림 버튼으로 완료해도 스냅샷이 나간다.
2. **워치 읽기 전용**: wear 모듈, 목록·상세 화면. 확인: 폰에서 추가·수정·완료한 것이 워치 목록에 바로 반영된다.
3. **워치 조작**: done·snooze 명령, WearCommandService, 헤드리스 진입점. 확인: 폰 앱을 강제 종료한 상태에서도 워치 완료가 폰에 반영되고 폰 알림과 넘어온 알림이 사라진다. 요일 반복 항목을 완료하면 다음 선택 요일로 넘어간다.
4. **워치 단독 알림**: 연결 상태 감지, 끊겼을 때 자체 예약, bridge tag로 중복 막기, Kotlin 반복 계산. 확인: 비행기 모드로 끊은 뒤에도 워치가 제시간에 울리고, 재연결 후 같은 회차가 두 번 울리지 않으며, 대기 명령이 폰에 반영된다.
5. **추가·반복·편집·타일·보관**: create·update·delete·restore 명령, 반복 설정 화면, 무작위 색과 탭해서 색 바꾸기, 타일, 보관 목록 (P1).
6. **컴플리케이션** (P2).

자동 테스트: 스냅샷 직렬화와 명령 적용(중복 commandId, 이미 보관된 항목, 반복 두 번 완료)을 Dart 단위 테스트로 확인한다. 반복 계산은 Dart와 Kotlin에 같은 테스트 케이스(요일 반복, 격주, 월말, 서머타임이 없는 Asia/Seoul과 있는 표준시대)를 돌려 결과가 같은지 본다.

실기기에서만 확인할 것 (마지막에 한 번): Galaxy Watch 8 + S26 Ultra 실제 연결에서의 동기화 지연, 블루투스를 끊었을 때 워치 단독 알림으로 넘어가는지, Galaxy Wearable이 bridge tag 제외를 지키는지, 배터리 절약 모드에서 워치 알림 시각 정확도.
