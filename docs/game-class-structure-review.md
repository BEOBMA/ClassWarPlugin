# gameClass/list 구조 검토

분석 기준: 2026-09-04, 커밋 `feb87ac` 및 당시 작업 디렉터리.

후속 수정: 아래는 수정 전 조사 기록이다. 이후 9개 항목에 대한 구현 변경 및 검증 결과는 [클래스 런타임 수정 내역](ability-runtime.md)에 정리했다.

핵심 개선 대상은 **클래스별 작업·효과의 수명 관리, 재접속, 복합 클래스의 소유 관계**다. 큰 파일을 나누는 작업에 앞서 이 계약을 정리해야 같은 문제가 새 클래스에서도 반복되지 않는다.

## 범위와 확인 수준

`gameClass/list` 전체를 구조·패턴 검색으로 조사하고, 위험 경로가 있는 구현과 `GameClass`, `Skill`, `PlayerData`, `GameManager`, 이벤트 리스너, 상태·밸런스 관리 코드의 호출 관계를 상세 확인했다. 모든 클래스의 전투 규칙을 하나씩 실행 검증한 전수 기능 검사는 아니다.

아래에서 **코드 경로 확인**은 관련 분기와 호출 관계가 소스에서 확인된다는 의미다. Paper 서버에서 재현하거나 성능을 측정한 결과와는 구분한다. 구현 코드는 변경하지 않았고 빌드·테스트·서버 실행은 수행하지 않았다.

| 항목 | 조사 결과 |
| --- | ---: |
| Kotlin 파일 | 95개 — `PlanetClass`, `dummy/Dummy` 포함 |
| 전체 줄 수 | 21,258줄 — 주석·빈 줄 포함 |
| 직접 Bukkit 작업을 예약하는 파일 | 65개 |
| 직접 작업 예약 호출 지점 | 99곳 |
| 위 65개 중 `GameEndHandler` 문자열이 없는 파일 | 46개 |
| 위 65개 중 `isPaused` 문자열이 있는 파일 | 11개 |
| 500줄 이상 파일 | 11개 |
| 저장소의 테스트 파일 | 6개 — 클래스 구현의 생명주기·복합 동작 전용 테스트 없음 |

예약 호출 수는 정적 호출 지점 수이며 동시 실행 작업 수가 아니다. `GameEndHandler` 부재만으로 누수를 단정할 수 없다. 일부 클래스는 매니저의 별도 정리 함수나 작업 내부 종료 조건을 사용한다. `isPaused` 검색 역시 실제로 모든 작업이 일시정지를 준수하는지 증명하지 않는다.

## 1. 클래스 단위로 작업과 효과를 종료할 계약이 없다

**우선순위: 높음 / 코드 경로 확인**

`PlayerData.trackTask()`는 작업을 플레이어와 경기 목록에 등록한다. 작업을 만든 클래스 인스턴스나 복원 동작은 기록하지 않는다. 반면 전투 중 클래스 교체는 기존 클래스와 패시브의 `GameEndHandler`만 호출한다.

- `Swordplay`는 매 틱 검을 움직이고 공격하는 작업을 시작하지만 `GameEndHandler`가 없다. `resetSwordState()`는 전투 시작 때 호출된다. 전투 중 다른 클래스로 교체해도 플레이어는 온라인·생존 상태이므로 기존 작업의 종료 조건에 걸리지 않는다. 기존 어검의 공격이 계속되는 경로다.
- `LightWizard`의 프리즘 작업도 설치 목록과 디스플레이 유효성으로만 종료 여부를 결정한다. 교체 시 클래스 소유의 프리즘 목록과 디스플레이를 닫는 경로가 없다.
- `PatAndMatt`는 상대의 `canMove`, `canAttack`, `canSkillUse`를 변경하고 작업의 다음 실행에서 복원한다. 시전자가 사망하면 `GameManager.handleDeath()`가 작업을 즉시 취소하므로 복원 분기가 실행되지 않는다. 경기가 계속되는 3명 이상의 상황에서는 생존 대상의 행동 제한이 남을 수 있다.
- `Tour` 역시 원래 위치로 돌아오는 동작이 작업 안에만 있다. 시전자 사망으로 취소되면 정상 종료 때의 귀환이 생략된다.

근거: [PlayerData.kt:41](../src/main/kotlin/org/beobma/classWarPlugin/entity/player/PlayerData.kt#L41), [GameManager.kt:1800](../src/main/kotlin/org/beobma/classWarPlugin/manager/GameManager.kt#L1800), [GameManager.kt:1875](../src/main/kotlin/org/beobma/classWarPlugin/manager/GameManager.kt#L1875), [Swordplay.kt:118](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Swordplay.kt#L118), [LightWizard.kt:82](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/LightWizard.kt#L82), [PatAndMatt.kt:37](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/PatAndMatt.kt#L37), [GameManager.kt:1389](../src/main/kotlin/org/beobma/classWarPlugin/manager/GameManager.kt#L1389), [Tour.kt:78](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Tour.kt#L78).

개선: 클래스 배정 인스턴스마다 작업·디스플레이·상태 변경·종료 콜백을 소유하는 `ClassScope`를 둔다. 교체·제거·사망·경기 종료 시 명시적인 종료 사유와 함께 닫고, 복원은 작업의 다음 틱이 없어도 실행되게 한다. 종료는 중복 호출에 안전해야 한다. 시전자 사망 후 폭발처럼 의도적으로 살아남아야 하는 효과는 별도 수명 정책으로 표현한다.

완료·취소된 작업을 등록 목록에서 제거하는 것도 이 계층이 담당할 수 있다. 현재 등록 목록은 개별 작업 종료 시 자동 축소되지 않아, 긴 훈련 중 목록 크기가 누적될 수 있다. 실제 메모리 점유량은 별도 측정이 필요하다.

## 2. 재접속은 참조 재주입만 처리하고 런타임 재개를 처리하지 않는다

**우선순위: 높음 / 코드 경로 확인**

재접속 시 `GameManager.rebindPlayer()`는 직접 배정된 클래스, 공개된 스킬과 패시브를 재주입한다. 이미 전투 초기화된 참가자에게 `onBattleStart()`를 다시 호출하지 않으며, 별도의 재개 훅도 없다.

- `Charger`는 오프라인이면 충전 작업을 취소한다. 새 작업을 만드는 곳은 `onBattleStart()`이고 `onGameTimePasses()`는 비어 있다. 작업 취소 이후 재접속하면 충전이 다시 시작되지 않는다.
- `Mercurius`의 상시 가속 작업도 같은 구조다.
- `ElementalistRuntime`은 생성 시 `private val player = playerData.player`로 Player 객체를 고정한다. 원소 충전 작업은 오프라인에서 종료되고 런타임은 재생성되지 않는다. 재접속 이후 기존 Player 참조 사용과 작업 재개 누락이 함께 남는다.
- `SolarSystem`의 내부 행성 클래스와 `GraveRobber`의 획득 클래스는 최상위 재주입 순회에 포함되지 않는다. 공개 스킬이 재주입돼도 내부 클래스 자체의 `player` 필드는 갱신되지 않는다.

근거: [GameManager.kt:1473](../src/main/kotlin/org/beobma/classWarPlugin/manager/GameManager.kt#L1473), [GameManager.kt:1526](../src/main/kotlin/org/beobma/classWarPlugin/manager/GameManager.kt#L1526), [GameManager.kt:2023](../src/main/kotlin/org/beobma/classWarPlugin/manager/GameManager.kt#L2023), [Charger.kt:53](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Charger.kt#L53), [Mercurius.kt:22](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Mercurius.kt#L22), [ElementalistRuntime.kt:104](../src/main/kotlin/org/beobma/classWarPlugin/util/ElementalistRuntime.kt#L104), [ElementalistRuntime.kt:127](../src/main/kotlin/org/beobma/classWarPlugin/util/ElementalistRuntime.kt#L127).

개선: 영속적인 전투 상태와 접속 중 실행되는 작업을 구분하고 `onSuspend`/`onResume`에 해당하는 계약을 추가한다. Player 참조는 안정적인 참가자 컨텍스트에서 현재 객체를 조회한다. `onBattleStart()`를 재접속 때 무조건 다시 부르면 자원·스택까지 초기화되므로 재개와 최초 초기화는 분리해야 한다.

## 3. 복합 클래스가 일반 클래스와 다른 규칙으로 실행된다

**우선순위: 높음 / 코드 경로 확인**

`GraveRobber`는 획득 클래스의 스킬·패시브를 자신의 목록에 붙이고, 클래스 본체는 별도의 `inheritedClasses`에 저장한다. 일부 인터페이스만 수동 전달하며 `OnHitHandler`, `WhenHitHandler`, `OtherSkillUseHandler`, `GameEndHandler`, `PlayerDeathHandler`의 클래스 본체 전달은 없다. 패시브의 핸들러가 전달되는 것과 클래스 본체의 핸들러가 전달되는 것은 별개다.

특히 `LightningWizard.RedSkill.use()`는 자신을 소유한 클래스에 직접 연결돼 있지 않고 `playerData.findGameClass(LightningWizard::class.java)`를 사용한다. 이 함수는 최상위 `gameClasses`만 검색한다. 번개술사를 도굴한 플레이어에게 직접 배정된 번개술사가 없다면 `use()`는 중간 반환하며 적란운을 만들지 않는다. 검증은 통과할 수 있으므로 쿨다운은 부과된다.

`Astronomer`, `Levatain`, `Sagittarius`에도 최상위 클래스 조회에 의존하는 경로가 있다. `SolarSystem` 역시 행성 초기화·종료·공격 이벤트를 직접 조합하므로 새 핸들러를 추가할 때 전달 경로를 함께 수정해야 한다.

근거: [GraveRobber.kt:35](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/GraveRobber.kt#L35), [GraveRobber.kt:105](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/GraveRobber.kt#L105), [PlayerData.kt:58](../src/main/kotlin/org/beobma/classWarPlugin/entity/player/PlayerData.kt#L58), [LightningWizard.kt:103](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/LightningWizard.kt#L103), [SolarSystem.kt:81](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/SolarSystem.kt#L81).

개선: 최상위 배정과 획득·내장 능력을 같은 능력 트리로 표현한다. 이벤트 전달, 종료, 재접속은 공통 순회가 맡고 태양계의 행성 활성화 여부만 정책으로 남긴다. 스킬에는 자신의 런타임 또는 필요한 상태·함수를 생성 시 연결한다. 플레이어의 클래스 목록을 역검색해 소유자를 찾는 패턴을 줄인다. 태양계에서 직접 배정 행성과 중복 효과를 막는 기존 정책도 보존해야 한다.

## 4. 공유 속성의 스냅샷 복원이 다른 효과를 덮어쓴다

**우선순위: 높음 / 코드 경로 확인**

`Dwarf`와 `Pluto`는 모두 `Attribute.SCALE.baseValue`를 직접 변경하고, 종료 때 자신이 저장한 과거 값을 그대로 되돌린다.

예를 들어 기본 크기 1.0에서 난쟁이가 적용되면 0.3이 된다. 명왕성이 이 0.3을 저장하고 축소한 동안 난쟁이를 제거하면 난쟁이는 1.0을 복원한다. 이후 명왕성이 종료되면 저장한 0.3을 복원한다. 난쟁이가 제거됐는데 난쟁이 크기가 다시 남는 순서다.

`PatAndMatt`의 불리언 스냅샷도 효과가 겹치면 같은 문제를 만든다. 먼저 끝난 효과가 다른 효과의 제한을 풀거나, 나중에 끝난 효과가 이전의 제한 상태를 다시 적용할 수 있다.

근거: [Dwarf.kt:29](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Dwarf.kt#L29), [Dwarf.kt:50](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Dwarf.kt#L50), [Pluto.kt:62](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Pluto.kt#L62), [Pluto.kt:93](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Pluto.kt#L93), [PatAndMatt.kt:40](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/PatAndMatt.kt#L40).

개선: 효과별 소유 ID와 해제 핸들을 두고, 현재 활성 효과를 합성해 최종 속성을 계산한다. 행동 가능 여부는 활성 제한의 집합에서 산출한다. 효과 종료는 자신의 기여분만 제거해야 한다. 최대 체력 변경 시 현재 체력을 어떻게 보정할지는 별도 규칙으로 유지한다.

## 5. 일시정지와 시간의 기준이 클래스마다 다르다

**우선순위: 중간 / 코드 경로 확인**

`Referee`는 재판 중 `game.isPaused = true`로 설정하고 전투와 시간이 정지한다고 안내한다. 중앙 클래스 틱은 이를 확인하지만 개별 Bukkit 작업은 자동으로 멈추지 않는다.

- `Tour`는 재판 중에도 방문 횟수를 증가시키고 대상을 텔레포트하는 코드를 실행한다.
- `Swordplay`, `LightningWizard` 등의 직접 작업에는 일시정지 확인이 없다.
- `Pluto`처럼 일시정지 때 경과 틱 누적을 멈추는 구현도 있어 클래스 간 처리가 다르다.
- 지속시간·쿨다운에 `world.fullTime`, `Bukkit.getCurrentTick()`, `System.currentTimeMillis()`, 로컬 틱 카운터가 섞여 있다. 실행을 잠시 건너뛰어도 절대 시각으로 계산한 종료 시점은 계속 지나간다.

근거: [Referee.kt:373](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Referee.kt#L373), [GameManager.kt:590](../src/main/kotlin/org/beobma/classWarPlugin/manager/GameManager.kt#L590), [Tour.kt:81](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Tour.kt#L81), [LightningWizard.kt:50](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/LightningWizard.kt#L50), [Pluto.kt:79](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Pluto.kt#L79).

개선: 전투용 게임 시계와 실제 시간 시계를 구분한다. 일반 효과는 일시정지되는 게임 시계로 예약하고, 재판 진행과 재접속 유예처럼 정지 중에도 흘러야 하는 시간만 실제 시간 정책을 선택한다. 각 스킬에 `if (isPaused)`를 붙이는 것만으로 절대 시각 문제까지 해결되지는 않는다.

## 6. 실제 효과의 성공 여부를 스킬 실행 결과로 표현하지 못한다

**우선순위: 중간 / 구조 확인**

현재 호출 순서는 `isUseSuccess()` → 취소 가능한 이벤트 → `use(): Unit` → 쿨다운 설정이다. `isUseSuccess()`는 조건 검사, 대상 선정, 임시 필드 저장, 일부 초기화와 안내 메시지까지 담당한다. 반면 `use()`가 중간에 반환해도 호출자는 실패를 알 수 없다.

위의 도굴한 번개술사 사례가 실제 실패 경로다. `LightWizard` 역시 배치 함수는 Boolean을 반환하지만 `use()`가 이를 전달하지 않는다. 선택한 대상·블록을 스킬 인스턴스 필드에 보관하는 구현도 여러 곳에 있어 한 번의 요청 상태와 스킬의 장기 상태가 섞인다.

근거: [Skill.kt:34](../src/main/kotlin/org/beobma/classWarPlugin/skill/Skill.kt#L34), [SkillManager.kt:123](../src/main/kotlin/org/beobma/classWarPlugin/manager/SkillManager.kt#L123), [LightWizard.kt:221](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/LightWizard.kt#L221), [Chameleon.kt:78](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Chameleon.kt#L78).

개선: 요청마다 대상·위치·비용을 담는 준비 결과와 `Success`/`Rejected` 등 실행 결과를 반환한다. 준비 단계에서는 자원을 소비하거나 효과를 생성하지 않고, 이벤트 승인 후 실행 결과에 따라 비용과 쿨다운을 확정한다. 발사 후 빗나감은 정상 사용으로 처리하는 등 게임 규칙상 성공의 의미도 명시해야 한다.

## 7. 클래스 식별과 밸런스가 구현 클래스 이름·호출 스택에 의존한다

**우선순위: 중간 / 구조 확인**

`Skill.id`의 기본값과 무기 식별 정보는 JVM 클래스 이름에 연결돼 있다. 밸런스 설정 키도 클래스 단순 이름에서 만들어진다. `ClassBalanceManager.resolveCallerKey()`는 최대 48개 스택 프레임에서 효과의 출처를 추론하며 `ElementalistRuntime`은 이름 기반 예외로 처리한다.

따라서 계산 코드를 새 런타임이나 공통 서비스로 옮기는 리팩터링이 밸런스 귀속까지 바꿀 수 있다. 특히 듀얼에서는 출처를 못 찾았을 때 `singleOrNull()`로도 소유자를 복원할 수 없다. 태양계와 직접 행성의 스킬 ID는 이미 `:solar` 예외로 구분하고 있어 이 조합에 충돌이 있다고 단정해서는 안 된다.

근거: [Skill.kt:24](../src/main/kotlin/org/beobma/classWarPlugin/skill/Skill.kt#L24), [GameClassManager.kt](../src/main/kotlin/org/beobma/classWarPlugin/manager/GameClassManager.kt), [ClassBalanceManager.kt:134](../src/main/kotlin/org/beobma/classWarPlugin/manager/ClassBalanceManager.kt#L134), [ClassBalanceManager.kt:218](../src/main/kotlin/org/beobma/classWarPlugin/manager/ClassBalanceManager.kt#L218), [Mars.kt:39](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Mars.kt#L39).

개선: 표시 이름과 독립적인 `ClassId`, `SkillId`, 배정 인스턴스 ID를 두고 피해·회복·상태·쿨다운에 출처를 명시적으로 전달한다. 기존 설정 키와 아이템 ID에는 호환 매핑을 둔다. 클래스명 변경이나 `TimeManiqulator`의 철자 수정도 이 호환성을 먼저 확인해야 한다.

## 8. 큰 파일은 책임별로 분리하되, 공통화의 경계를 먼저 정해야 한다

**우선순위: 중간 / 유지보수 개선**

줄 수 자체가 결함은 아니다. 다만 아래 파일들은 서로 다른 이유로 변경되는 코드가 함께 있다.

| 파일 | 줄 수 | 권장 분리 단위 |
| --- | ---: | --- |
| `Referee.kt` | 1,152 | 증거 기록·판정 규칙, 재판 상태 전이, 이동·복원 세션, 문구와 연출 |
| `Swordplay.kt` | 935 | 검 상태와 궤적 계산, 충돌·피해 규칙, 디스플레이·입자 표현 |
| `Mathematician.kt` | 840 | 문제 생성과 정답 계산, 출제 세션·입력 처리, 보상 |
| `AreaDevelopment.kt` | 728 | 영역 세션·경계 규칙, 블록·맵 변경과 복원, 전투 효과 |
| `SpiderMan.kt` | 708 | 발사·스윙 상태, 이동·충돌 계산, 표시와 정리 |
| `HideAndSeek.kt` | 686 | 방·탐색 규칙, 참가자 세션, 복원과 연출 |
| `WeaponMaster.kt` | 682 | 무기별 행동, 공통 숙련 상태, 연출 |

근거: [Referee.kt:341](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Referee.kt#L341), [Mathematician.kt:103](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Mathematician.kt#L103), [Swordplay.kt:140](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Swordplay.kt#L140).

`Elementalist`의 런타임 분리와 정리 함수는 출발점으로 참고할 만하다. 다만 `ElementalistRuntime` 자체도 1,400줄 이상이므로 파일 하나를 다른 파일 하나로 옮기는 것만으로 책임 분리가 끝나지는 않는다.

정적 설명·기본 수치와 실행 상태도 분리할 수 있다. 이미 일부 수치가 상수화돼 있지만 설명의 숫자는 별도로 적혀 있다. 공통 수치 정의에서 설명을 만들면 밸런스 수정 때 설명만 뒤처지는 위험이 줄어든다. 모든 클래스를 거대한 설정 DSL로 전환할 필요는 없다.

## 9. 대상 탐색은 공통 규칙과 성능 책임을 더 모을 수 있다

**우선순위: 중간 / 구조 확인, 성능 영향은 미측정**

공통 `SkillManager`, `HitboxUtil`이 있지만 `LightWizard`·`Sniper`·`Flooring` 등에서도 후보와 적 판정을 별도로 구성한다. 소환체, 훈련 대상, 은신, 다른 월드, 사망 상태를 어느 단계에서 제외하는지 일관성을 점검할 필요가 있다.

`getTargetCandidates()`는 조회 함수이면서 훈련 월드의 생명체를 검색하고 `game.playerDatas`에 새 데이터를 추가한다. `radius()`는 주변 엔티티마다 후보 목록을 다시 `find`한다. 매 틱 공격과 광선 분기에서 반복 호출되면 검색·목록 생성 비용이 누적될 수 있다. 또 `radius()`의 기본 동작에는 공격 가능한 오브젝트 타격이 포함돼 있어 단순 조회와 전투 효과의 경계도 흐리다.

근거: [SkillManager.kt:72](../src/main/kotlin/org/beobma/classWarPlugin/manager/SkillManager.kt#L72), [SkillManager.kt:147](../src/main/kotlin/org/beobma/classWarPlugin/manager/SkillManager.kt#L147), [LightWizard.kt:129](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/LightWizard.kt#L129), [Flooring.kt:97](../src/main/kotlin/org/beobma/classWarPlugin/skill/Flooring.kt#L97).

개선: 후보 등록과 조회를 분리하고 UUID 인덱스와 공통 대상 정책을 사용한다. 한 번의 광선·틱 처리에서는 가능한 범위에서 후보 스냅샷을 재사용한다. 지형 차단·범위 모양처럼 스킬 고유의 기하 계산은 유지한다. 공간 인덱스 같은 큰 변경은 실제 참가자·훈련 몹 수로 프로파일링한 뒤 결정한다.

## 개선 순서와 검증 시나리오

1. **종료와 복원부터 고친다.** 클래스별 작업·효과 소유 범위를 추가하고 어검술·프리즘·패트와 매트·순회공연에 먼저 적용한다. 사망 후에도 남는 효과는 규칙을 명시한다.
2. **재접속과 복합 클래스를 같은 생명주기로 묶는다.** 최초 초기화·일시 중단·재개·종료를 구분하고 도굴꾼·태양계의 하위 능력까지 공통 처리한다.
3. **효과 합성·시간·출처를 명시한다.** 속성 복원 충돌을 제거하고 게임 시계, 안정적인 ID와 실행 결과를 도입한다.
4. **큰 클래스의 순수 계산을 분리한다.** 수학 문제 생성, 검 궤적, 재판 판정부터 서버 없이 검증할 수 있게 만들고 대상 탐색은 측정 후 최적화한다.

| 검증 | 기대 결과 |
| --- | --- |
| 어검술 실행 중 다른 클래스로 교체 | 기존 검·작업·공격이 종료되고 새 클래스만 동작 |
| 프리즘 설치 후 해당 클래스 제거 | 해당 클래스가 소유한 프리즘과 작업 정리 |
| 3명 이상 경기에서 패트와 매트 시전자 사망 | 살아 있는 대상의 행동 제한 즉시 복원 |
| 충전기·수성·원소술사로 작업 실행 후 연결 종료·복귀 | 현재 Player에 연결되고 지속 능력 재개, 기존 상태는 정책대로 유지 |
| 번개술사 도굴 후 적란운·과부하 사용 | 획득한 런타임의 표식 생성·조작, 허위 성공과 쿨다운 없음 |
| 태양계·도굴꾼 소유자로 재접속·클래스 제거 | 하위 능력까지 재연결·종료 |
| 난쟁이+명왕성에서 축소 중 난쟁이 제거, 이후 명왕성 만료 | 현재 남아 있는 효과만 반영한 크기 |
| 순회공연·지속 공격 중 재판 개시 | 전투 효과의 진행·이동·잔여 시간이 정지 정책 준수 |
| 일반 사용·이벤트 취소·실행 거절 | 비용·쿨다운·이벤트 결과가 서로 일치 |
| 같은 능력의 단독·듀얼·도굴·태양계 사용 | ID와 밸런스 출처가 정의된 정책대로 적용 |

테스트는 위 동작 계약에 집중하는 편이 효과적이다. 가짜 스케줄러·게임 시계로 종료와 재개를 검증하고, Paper 서버에서는 실제 Player 재생성, 텔레포트, 속성·디스플레이 복원을 확인한다. 기존 6개 테스트는 기본 계산·설정 등을 다루지만 이 경로를 검증하지는 않는다.

기존 `SkillManager` 진입점, 역할별 핸들러, 공통 효과 API, `HitboxUtil`, 일부 클래스의 명시적 정리 함수는 유지할 기반이다. 작은 클래스까지 전부 새로운 프레임워크로 다시 쓰기보다, 위 실패 경로를 공통 계약으로 막으며 점진적으로 이전하는 것이 적절하다.
