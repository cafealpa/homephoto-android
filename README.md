# 홈 포토 백업 — 안드로이드 앱

폰의 사진·동영상을 집 서버로 백업하고, 서버에 쌓인 사진을 타임라인·인물별로 보는 앱.
디자인 의도와 화면 구조는 [`docs/UI.md`](docs/UI.md).

서버(Kotlin + Spring Boot)·얼굴 인식 워커·전체 시스템 설계는 별도 저장소
[cafealpa/homePhotobackupPjt](https://github.com/cafealpa/homePhotobackupPjt)에 있습니다
— 아키텍처는 [DESIGN.md](https://github.com/cafealpa/homePhotobackupPjt/blob/main/docs/DESIGN.md), 서버 실행법은 그쪽 README 참고.

## 스택

- Kotlin 2.2, Jetpack Compose (Material3, BOM 2025.06), minSdk 31 / targetSdk 36
- 네트워크: Retrofit + OkHttp (`X-Api-Key` 헤더 인증), 이미지: Coil
- 백그라운드 백업: WorkManager(포그라운드 서비스), 설정: DataStore Preferences
- 로컬 상태: SQLite(`data/local/BackupDb.kt`) — 파일별 해시·업로드 상태·실패 이력

## 빌드·실행

Android Studio에서 이 저장소 루트를 연다. `local.properties`(SDK 경로)는 자동 생성되며
git에 올라가지 않는다. 명령줄:

```bash
./gradlew :app:assembleDebug
```

첫 실행 후 **상단 설정 버튼**에서 서버 주소(`http://내부IP:8080`)와 API 키를 입력한다.
집 LAN의 서버가 평문 http라서 `res/xml/network_security_config.xml`이 cleartext를 허용한다
(외부 공개 시 제거).

## 소스 구조

```
app/src/main/java/com/chochocho/homephotoclient/
├─ MainActivity.kt          탭 5개(홈·사진·검색·인물·백업), 상단 설정 라우팅, HomePhotoTheme 적용
├─ ui/
│  ├─ theme/                Color.kt · Type.kt · Theme.kt — 디자인 토큰 (여기만 색·간격 정의)
│  ├─ HomeScreen.kt         가족 앨범형 홈, 대표 사진·인물·최근 사진
│  ├─ SearchScreen.kt       문장 검색·월별 탐색·인물 바로가기
│  ├─ TimelineScreen.kt     월별 썸네일 그리드
│  ├─ PeopleScreen.kt       인물 그리드 → 인물 상세, 이름 붙이기
│  ├─ BackupScreen.kt       백업 요약·진행·실패 이력·스킵 관리
│  ├─ SettingsScreen.kt     서버 연결·자동 백업 옵션
│  └─ AssetBrowsing.kt      ThumbCell, FullScreenViewer, 페이지네이션 상태 (공용)
├─ backup/                  BackupEngine(스캔·해시·업로드), BackupWorker/Scheduler(WorkManager)
└─ data/                    HomePhotoApi(Retrofit), MediaScanner, SettingsRepository,
                            ErrorMessages(예외 → 사용자 문구), local/BackupDb
```

## 디자인 규칙 (요약 — 상세는 docs/UI.md)

- 색·글꼴·모서리·간격은 `ui/theme/`의 토큰만 사용. 화면 코드에 `Color(0x...)` 새로 쓰지 않기
- 사진 위 오버레이 텍스트만 `HomePhotoColors.Overlay*` 직접 사용 허용
- 다크 전용이며 다이내믹 컬러는 사용하지 않음
- 문구는 한국어, 버튼은 짧은 명령형("저장", "다시 시도"), 오류는 "~할 수 없어요" + 할 일
