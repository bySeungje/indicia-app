# 인디시아 (Indicia)

운전자가 같은 지점, 같은 상황에서 반복한 조작(창문, 와이퍼, 좌석, 공조)을 기억했다가, 다음에 그 지점과 상황이 오면 차의 공조와 좌석과 안내를 미리 맞춰 두는 차량용 앱. 운전을 채점하거나 가르치지 않는다. 이름 Indicia 는 "표시, 단서"를 뜻하는 라틴어.

## 여섯 기능

| 기능 | 차가 읽는 조작과 값 | 차가 해 주는 일 |
|---|---|---|
| 오늘의 드라이브 | 주행 거리, 브레이크 페달, 방향지시등, 창문과 공조 | 그날 주행을 한 장으로 정리. 출장 주행은 업무 기록으로 따로 남음 |
| 출발 자세 | 마지막 주차 때의 좌석 앞뒤 위치와 등받이 각도 | 평소 위치와 비교해 등받이와 핸들 조정 방법을 안내 |
| 바깥 공기 | 창문을 올리거나 내기 순환을 켠 지점 | 그 지점에 가까워지면 내기 순환을 미리 켬 |
| 앞유리 | 비 없이 와이퍼를 짧게 돌린 지점 | 그 지점에 닿기 전에 미리 알림 |
| 충전 기록 | 배터리 잔량이 오르는 속도 | 늦어진 시간을 충전기 출력 몫과 배터리 보호 몫으로 나눠 보여 줌 |
| 정체 중 공조 | 기어 D, 속도 0 으로 10분 넘게 선 정차 | 공조 설정(지금, 약하게, 끔)마다 유지할 수 있는 시간을 계산 |

주행 중에는 앱 화면이 스스로 뜨지 않는다. 차의 주행 화면과 내비게이션이 앞에 남고, 안내는 주차(기어 P) 뒤에 짧은 카드로 나온다.

## 실행 방법

1. Pleos Connect 에뮬레이터(v2.1.0)를 켠다.
2. APK 를 설치한다. 에뮬레이터 앱은 사용자 10 으로 돈다.
   ```
   adb install -r apk/인디시아_release.apk
   adb shell pm grant --user 10 app.chajeung android.car.permission.CAR_SPEED
   adb shell appops set --user 10 app.chajeung SYSTEM_ALERT_WINDOW allow
   adb shell am start --user 10 -n app.chajeung/.MainActivity
   ```
3. 가상 조작판에서 기어, 속도, 창문, 와이퍼, 배터리 잔량을 바꾸면 기능이 동작한다.

## 테스트 환경

- Pleos Connect Emulator v2.1.0 (Android 14 Automotive), macOS
- Android Gradle Plugin 8.11.1, Kotlin 2.2.20, compileSdk 35, minSdk 28
- 차량 값: Pleos Vehicle SDK(창문, 와이퍼, 좌석, 공조, 배터리, 브레이크, 계기판 거리, 방향지시등), 표준 Car API(속도, 기어), Pleos NaviHelper(경로)

## 파일 위치

- APK: `apk/인디시아_release.apk`
- 소스 코드: `app/`

## 빌드

```
./gradlew assembleRelease
```

패키지 이름 `app.chajeung` 은 개발 초기 가칭에서 남은 것이다.
