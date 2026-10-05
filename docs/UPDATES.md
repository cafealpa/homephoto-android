# Android 앱 업데이트

앱 **설정 → 앱 업데이트 → 새 버전 확인**에서 공개 GitHub Releases를 조회한다. 새 APK를 다운로드한 뒤 **설치 / 다시 설치**를 누른다. 처음에는 Android의 '이 출처 허용' 설정이 필요할 수 있으며, 허용 후 앱으로 돌아와 같은 버튼을 누른다.

## 배포 계약

- 서버가 쓰던 GitHub Releases 방식과 동일하며, Android 앱의 저장소 `cafealpa/homephoto-android`를 사용한다. 서버 JAR 저장소와는 구분된다.
- 최근 릴리즈 100개에서 draft/prerelease를 제외하고 `homephoto-android-{versionCode}.apk` 이름의 업로드 완료 자산을 찾는다. 설치된 versionCode보다 큰 값 중 가장 큰 것을 선택한다.
- GitHub 자산 API의 `size`, `digest`(sha256), 다운로드 URL을 사용한다. 체크섬이 없는 신규 자산은 오류로 안내하며 자동으로 이전 버전을 대신 제시하지 않는다. 릴리즈 이름/태그는 화면 표시용이고 실제 버전 판단은 숫자 versionCode다.
- APK의 패키지 ID는 `com.chochocho.homephotoclient`여야 한다. 실제 APK versionCode는 파일명과 같아야 하고, **현재 앱과 같은 서명 키**로 서명해야 한다. 이 첫 버전은 서명 키 교체를 지원하지 않는다.
- 다운로드 크기와 SHA-256, 실제 APK 패키지/버전/최소 SDK/서명을 확인한다. 설치는 Android 설치 화면에서 사용자가 승인한다.
- GitHub에는 홈서버 API 키나 사진 데이터를 전송하지 않는다. 공개 릴리즈 조회라 GitHub 토큰도 앱에 저장하지 않는다.

## 릴리즈 준비

1. `app/build.gradle.kts`의 versionCode를 이전 배포보다 증가시키고 versionName을 갱신한다.
2. Android Studio의 **Generate Signed Bundle / APK → APK**로 기존 배포 키를 사용해 release APK를 만든다. 서명 키/비밀번호는 저장소에 넣지 않는다.
3. 프로젝트 루트에서 실행한다.

```powershell
.\prepare-apk-release.ps1 -ApkPath .\app\release\app-release.apk
```

4. 출력된 `build/apk-release/{versionCode}/homephoto-android-{versionCode}.apk`를 `cafealpa/homephoto-android`의 정식 GitHub Release에 첨부한다. SHA-256 파일은 수동 검증용으로 함께 첨부할 수 있다.
5. 공개 전에 파일명, 버전, 서명, GitHub 자산 digest를 확인한다. 스크립트는 업로드나 공개를 실행하지 않는다.

명령줄 서명은 `HOMEPHOTO_KEYSTORE`, `HOMEPHOTO_STORE_PASSWORD`, `HOMEPHOTO_KEY_ALIAS`, `HOMEPHOTO_KEY_PASSWORD` 환경변수를 지정한 프로세스에서 `:app:assembleRelease`로 실행한다. 키를 지정하지 않은 release 빌드는 unsigned이며 공개할 수 없다. Android Studio에서 기존 키를 지정해 빌드해도 된다. debug 키로 설치된 앱은 다른 release 키로 업데이트할 수 없다. 업데이트 기능이 없는 구버전에서는 이 기능이 포함된 앱을 한 번 직접 설치해야 한다.

## 설치 실패/취소와 재사용

완료 APK와 릴리즈 정보는 앱 내부 `files/updates/`에 보관한다. 설치 화면을 열었다는 이유로 삭제하지 않는다. 설치 취소/실패, 앱 종료, 휴대폰 재시작 뒤에도 받은 파일을 검증해 네트워크 없이 재설치할 수 있다. 업데이트 확인 요청이 실패해도 받은 APK의 설치 버튼은 유지된다.

크기 또는 SHA-256이 다르거나 파일이 사라졌을 때만 다시 다운로드한다. `.part`는 설치 대상으로 쓰지 않으며 부분 다운로드 이어받기는 지원하지 않는다. 설치 후 앱의 versionCode가 올라간 것을 확인하면 이전 APK를 정리한다. APK는 Android 자동 백업/기기 이전 대상에서 제외한다.

다운로드는 앱 프로세스 안에서 화면 이동·회전과 무관하게 한 번만 실행된다. OS가 프로세스를 종료하면 부분 다운로드는 다시 시작한다. 실제 완료 파일은 그대로 남는다. 저장소 데이터 삭제/앱 삭제 시에는 보관 APK도 삭제된다.

## 참고

- [GitHub 릴리즈 조회 API](https://docs.github.com/en/rest/releases/releases#list-releases)
- [GitHub 자산 digest와 다운로드 URL](https://docs.github.com/en/rest/releases/assets#get-a-release-asset)
- [Android FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)

단위 테스트는 APK 캐시 재사용/손상/중단/버전 정리와 GitHub 릴리즈 선택을 검증한다. 실서비스 릴리즈 업로드 및 실제 휴대폰 업데이트는 별도 배포 검증이다.

## 이번 구현 검증

- Android 단위 테스트 32개 통과, debug APK 빌드 성공.
- 릴리즈 준비 스크립트의 debug APK 거부 확인.
- Android 34 에뮬레이터에서 테스트 APK를 다운로드 완료 상태로 준비하고 네트워크를 끈 상태로 설치 권한 안내 → 설치 창 → 취소 → 앱 강제 종료/재실행 → 같은 APK 재설치 → versionCode 1에서 2로 업데이트 성공 확인.
- 실제 공개 GitHub 릴리즈 조회 성공(검증 시점 릴리즈 0개). 공개 APK의 실제 다운로드·릴리즈 서명 키·사용자 휴대폰 업데이트는 아직 검증하지 않았다.

## 로컬 서명 설정으로 빌드

`build-release.ps1`은 기본적으로 `%USERPROFILE%/.android/homephoto-release/signing.json`의 `keystore`, `alias`, `storePassword`, `keyPassword`를 읽어 테스트·서명 빌드·릴리즈 파일 준비를 실행한다. `-SigningConfig`로 다른 로컬 파일을 지정할 수 있다. 이 JSON과 키는 비밀 파일이며 Git/릴리즈에 첨부하지 않는다. 키와 설정 파일을 함께 별도 안전한 위치에 백업해야 이후 같은 서명으로 업데이트할 수 있다.

```powershell
.\build-release.ps1
```

1.1.0(2)은 새 정식 서명 키를 사용하는 첫 공개 버전이다. 이전 개발용 앱과 서명이 다르면 덮어 설치가 거부될 수 있다. 앱 삭제 시 로컬 설정·백업 이력이 초기화되므로 기존 데이터 상태를 확인한 뒤 전환해야 한다. 1.1.0 이후는 이 정식 키를 계속 사용한다.
