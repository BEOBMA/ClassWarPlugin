# ClassWarPlugin

Paper 서버에서 여러 클래스를 무작위로 배정받아 전투하는 미니게임 플러그인입니다.
일반 개인전과 꼬리잡기, 두 클래스를 동시에 사용하는 듀얼 모드를 지원하며 클래스별 스킬,
상태이상, 훈련장, 자기장, 전장 지도와 게임 밸런스 설정을 하나의 플러그인에서 제공합니다.

## 주요 기능

- 클래식, 꼬리잡기, 듀얼, 꼬리잡기 듀얼의 네 가지 경기 모드
- 등급별 가중치가 적용되는 무작위 클래스 배정과 재추첨
- 클래스별 무기, 액티브 스킬, 패시브 및 상태이상 전투 시스템
- 실제 경기에 영향을 주지 않고 클래스를 시험하는 개인 훈련 모드
- 안전한 산개 위치 탐색, 월드보더 축소와 최종 자기장
- 자신의 위치와 자기장 경계를 표시하는 전장 지도
- 피해 표시, 사망 메시지, 탭 목록 및 이름표 표시 설정
- 전체 피해와 피해 원인별 배율, 클래스별 세부 밸런스 설정
- 일시적인 연결 해제 후 5분 이내 경기 복귀 지원
- GitHub Releases 기반 선택적 자동 업데이트

## 요구 사항

| 항목 | 요구 버전 |
| --- | --- |
| 서버 | Paper API `26.2` 호환 서버 |
| Java 런타임 | Java 25 이상 |
| 플레이어 | 정규 경기 시작 시 온라인 비훈련 참가자 2명 이상 |
| 외부 플러그인 | 없음 |

Spigot/Bukkit 및 다른 Paper 포크에서의 동작은 보장하지 않습니다. 서버와 플러그인의
`api-version`이 맞지 않으면 Paper가 플러그인 로드를 거부할 수 있습니다.

## 설치

1. [GitHub Releases](https://github.com/BEOBMA/ClassWarPlugin/releases)에서 최신 정식 릴리스의
   `ClassWarPlugin-<버전>-all.jar`를 내려받습니다.
2. 서버를 완전히 종료합니다.
3. JAR을 서버의 `plugins` 디렉터리에 넣습니다.
4. Java 25 이상으로 Paper 서버를 시작합니다.
5. 생성된 `plugins/ClassWarPlugin/config.yml`을 확인합니다.
6. 서버 콘솔에서 플러그인이 오류 없이 활성화되었는지 확인합니다.

일반 JAR인 `ClassWarPlugin-<버전>.jar`에는 Kotlin과 Gson 런타임이 포함되지 않으므로 단독으로
배포하지 마세요. 서버의 `/reload` 명령은 Bukkit 객체와 예약 작업을 불완전하게 남길 수 있으므로
사용하지 않는 것을 권장합니다.

## 시작

1. 운영자는 `/cw config`에서 경기 설정을 확인합니다.
2. 참가자 두 명 이상이 접속한 상태에서 `/cw start`를 실행합니다.
3. GUI에서 경기 모드를 선택합니다.
4. 각 참가자가 배정된 클래스를 확인하고 필요하면 재추첨한 뒤 확정합니다.
5. 모든 참가자가 확정하면 카운트다운과 산개를 거쳐 전투가 시작됩니다.
6. 승자가 결정되면 자동으로 종료되며, 운영자는 `/cw stop`으로 강제 종료할 수 있습니다.

개별 클래스는 정규 경기가 없을 때 `/cw training`으로 연습할 수 있고 `/cw exit`으로 종료할 수 있습니다.

## 모드

| 모드 | 배정 클래스 수 | 규칙 |
| --- | ---: | --- |
| 클래식 | 1 | 자신을 제외한 모든 참가자와 싸워 마지막까지 생존합니다. |
| 꼬리잡기 | 1 | 지정된 표적만 공격할 수 있으며 처치 후 다음 표적을 이어받습니다. 기생충은 등장하지 않습니다. |
| 듀얼 | 2 | 서로 다른 클래스 두 개의 무기와 스킬을 함께 사용합니다. |
| 꼬리잡기 듀얼 | 2 | 클래스 두 개를 사용하면서 꼬리잡기 표적 규칙을 적용합니다. 기생충은 등장하지 않습니다. |

꼬리잡기 모드에서는 자신의 현재 표적만 개인용 강조 효과로 표시됩니다. `/cw target`을 실행하면
현재 표적을 다시 확인하고 나침반 위치를 갱신할 수 있습니다.

## 명령어

기본 명령어는 `/classwar`이며 `/cw`를 별칭으로 사용할 수 있습니다.

| 명령어 | 사용 주체 | 설명 |
| --- | --- | --- |
| `/cw help [1-2]` | 모두 | 명령어 도움말을 표시합니다. |
| `/cw classlist` | 플레이어 | 정규 경기가 없을 때 전체 클래스 목록을 엽니다. |
| `/cw training` | 플레이어 | 클래스 선택 GUI를 열어 개인 훈련을 시작합니다. |
| `/cw exit` | 플레이어 | 진행 중인 개인 훈련을 종료하고 이전 상태를 복원합니다. |
| `/cw abilities [플레이어]` | 플레이어/OP | 자신의 배정 클래스를 확인합니다. OP만 다른 플레이어를 조회할 수 있습니다. |
| `/cw target` | 플레이어 | 꼬리잡기 표적을 확인하고 나침반을 갱신합니다. |
| `/cw config` | 플레이어 | 개인 설명 설정을 엽니다. OP에게는 서버 경기 설정도 표시됩니다. |
| `/cw start` | OP 플레이어 | 경기 모드 선택 GUI를 엽니다. |
| `/cw stop` | OP/콘솔 | 현재 정규 경기를 즉시 종료합니다. |
| `/cw assign <플레이어> <능력> [슬롯]` | OP/콘솔 | 선택 또는 전투 중 지정 슬롯의 클래스를 강제로 배정합니다. |
| `/cw remove <플레이어> <능력\|슬롯\|all>` | OP/콘솔 | 배정된 클래스 일부 또는 전체를 제거합니다. |
| `/cw reload` | OP/콘솔 | 설정과 클래스 밸런스를 다시 불러옵니다. |
| `/cw update` | OP/콘솔 | GitHub의 최신 정식 릴리스를 즉시 확인합니다. |

능력 인수에는 탭 완성으로 제공되는 영문 설정 키, 클래스명 또는 게임 내 한글 이름을 사용할 수 있습니다.
현재 별도의 permission node는 제공하지 않으며 관리자 기능은 서버 OP 여부로 제한합니다.

## 설정

기본 설정 파일은 [`src/main/resources/config.yml`](src/main/resources/config.yml)에서 확인할 수 있습니다.
실제 서버에서는 `plugins/ClassWarPlugin/config.yml`이 사용됩니다. 누락된 항목은 플러그인 시작 또는
`/cw reload` 시 현재 기본값으로 자동 생성됩니다.

| 설정 경로 | 용도 |
| --- | --- |
| `selection` | 클래스 재추첨 횟수와 시작 카운트다운 시간 |
| `skills.cooldown-flow-multiplier` | 전체 스킬 쿨다운 흐름 속도. `2.0`이면 실제 대기시간이 절반입니다. |
| `class-balance` | 클래스별 전체 효과, 피해, 회복, 범위, 상태이상과 쿨다운 배율 |
| `rank-chances` | 클래스 등급별 추첨 가중치 |
| `display` | 경기 중 탭 목록 표시 여부 |
| `combat.damage-indicators` | 피해량 텍스트 표시 여부 |
| `combat.death-messages` | 사망 메시지와 공격자·원인 표시 여부 |
| `combat.damage-multipliers` | 전체 및 피해 원인별 피해 배율 |
| `map` | 경기 기본 중심 좌표 |
| `scatter` | 산개 최소·최대 반경과 참가자 최소 간격 |
| `border` | 자기장 크기, 대기·축소 시간, 피해와 최종 자기장 설정 |
| `auto-update` | GitHub 저장소, 확인 주기, 파일 이름 규칙과 최대 다운로드 크기 |

설정 GUI에서 일반 클릭은 한 단계, Shift 클릭은 큰 단계로 값을 조절합니다. 서버 공용 설정은 변경 즉시
파일에 저장되며 진행 중인 경기에는 기존 설정 스냅샷이 유지되고 다음 경기부터 적용됩니다. 클래스별
밸런스는 `class-balance.classes` 아래에 첫 실행 시 자동 생성됩니다.

`combat.damage-multipliers.overall`과 개별 원인 배율은 서로 곱해집니다. 개별 피해 배율을 `0.0`으로
설정하면 해당 피해 원인이 비활성화됩니다. 시간 설정은 초, 거리·반경·크기 설정은 블록 단위입니다.

파일을 직접 수정할 때는 서버를 종료한 상태에서 작업하는 것이 가장 안전합니다. 실행 중 수정했다면
`/cw reload`를 사용하세요. `auto-update` 설정 변경은 예약 작업을 다시 만들기 위해 서버를 재시작해야 합니다.

## 자동 업데이트

자동 업데이트는 기본적으로 활성화되어 있습니다. 서버 시작 10초 뒤 첫 확인을 수행하고 이후 6시간마다
[`BEOBMA/ClassWarPlugin`](https://github.com/BEOBMA/ClassWarPlugin)의 최신 정식 릴리스를 확인합니다.

- 초안 및 프리릴리스는 설치하지 않습니다.
- 릴리스 태그는 SemVer 형식이어야 하며 JAR 내부 버전과 일치해야 합니다.
  예: 태그 `v1.0.2`, `plugin.yml` 버전 `1.0.2`.
- `sources`와 `javadoc` JAR은 제외하고 `-all` 또는 `-shadow` JAR을 우선합니다.
- 다운로드 주소는 GitHub HTTPS로 제한하며 크기, JAR 구조, 플러그인 이름과 버전을 검증합니다.
- GitHub가 SHA-256 다이제스트를 제공하면 내려받은 파일의 다이제스트도 검증합니다.
- 검증된 파일은 Paper의 `plugins/update` 디렉터리에 저장되며 다음 서버 재시작 때 적용됩니다.
- 실행 중인 플러그인 JAR은 직접 덮어쓰지 않습니다.

폐쇄망 서버 또는 수동 업데이트 환경에서는 다음과 같이 비활성화할 수 있습니다.

```yaml
auto-update:
  enabled: false
```

## 소스에서 빌드

빌드에는 Gradle 실행용 JDK 21과 Kotlin JVM 툴체인용 JDK 25가 필요합니다. 현재 Gradle Wrapper는
8.14.4이며, Java 25로 Gradle 자체를 실행할 때 호환 문제가 생기면 `JAVA_HOME`을 JDK 21로 지정하세요.
JDK 25는 로컬에 설치되어 있어야 Gradle 툴체인이 이를 찾아 컴파일할 수 있습니다.

### Windows PowerShell

```powershell
git clone https://github.com/BEOBMA/ClassWarPlugin.git
Set-Location ClassWarPlugin
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat clean build
```

### Linux/macOS

```bash
git clone https://github.com/BEOBMA/ClassWarPlugin.git
cd ClassWarPlugin
JAVA_HOME=/path/to/jdk-21 ./gradlew clean build
```

빌드가 성공하면 다음 파일이 생성됩니다.

- `build/libs/ClassWarPlugin-<버전>-all.jar`: 서버 설치 및 릴리스용 실행 JAR
- `build/libs/ClassWarPlugin-<버전>.jar`: 의존성이 포함되지 않은 개발용 JAR

`build` 작업은 컴파일, 테스트, Shadow JAR 생성과 필수 런타임 클래스 검증을 모두 수행합니다.

## 릴리스 체크리스트

1. `build.gradle.kts`의 `version`을 새 버전으로 변경합니다.
2. JDK 21 Gradle 런타임과 JDK 25 툴체인으로 `clean build`를 실행합니다.
3. 모든 테스트와 `verifyShadowJarContents`가 통과했는지 확인합니다.
4. 버전과 일치하는 태그를 생성합니다. 예: `v1.0.2`.
5. GitHub에 정식 릴리스를 생성하고 `ClassWarPlugin-<버전>-all.jar`만 실행 파일로 첨부합니다.
6. 테스트 서버에서 신규 설치와 기존 설정을 사용한 업데이트를 각각 확인합니다.

## 라이선스

이 프로젝트는 [MIT License](LICENSE)를 기반으로 배포됩니다. 개인·상업적 사용, 수정, 병합,
배포, 재라이선스와 판매가 허용됩니다. 단, 원본 또는 수정본을 소스나 바이너리 형태로 재배포할 때는
다음 저작권 고지와 이 저장소의 `LICENSE` 전문을 수정 없이 반드시 함께 포함해야 합니다.

```text
Copyright (c) 2026 BEOBMA
```

원본 프로젝트: [BEOBMA/ClassWarPlugin](https://github.com/BEOBMA/ClassWarPlugin)

공식 빌드 JAR에는 동일한 라이선스가 `META-INF/LICENSE` 경로로 포함됩니다. 플러그인을 다른
프로젝트나 배포 묶음에 포함하는 경우에도 이 파일을 제거하지 마세요.
