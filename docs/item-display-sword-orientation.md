# ITEM_DISPLAY 검 방향·회전 및 암살자 벽 해제

이 문서는 `ItemDisplay`로 표시한 기본 Minecraft 검의 검날 방향이 어긋나거나, 회전 애니메이션에서 검이 엉뚱한 축을 향하는 문제의 원인과 해결 방법을 기록한다. 암살자의 벽 부착 상태를 웅크리기로 해제하는 입력 처리도 함께 설명한다.

## 빠른 결론

기본 검 아이템은 모델의 XY 평면에서 대각선으로 그려져 있다. 또한 클라이언트의 `ItemDisplayRenderer`는 display transformation을 적용한 뒤 아이템 모델에 Y축 180° 회전을 추가한다.

따라서 display transformation이 실제로 받는 검 모델의 기준축은 다음과 같다.

```text
검날 축(손잡이 -> 검끝): normalize(-1, 1, 0)
검 앞면 법선:            (0, 0, -1)
```

아이템 그림만 보고 `normalize(1, 1, 0)`과 `(0, 0, 1)`을 사용하면 안 된다. 이 잘못된 축을 사용했을 때 나타난 실제 증상은 다음과 같았다.

- 암살자: 투척 방향과 무관하게 검끝이 위쪽을 향함
- 기사: 검이 부채꼴로 이동하지만 검끝은 진행 방향의 왼쪽 접선을 향함

해결 구현은 [`DisplayOrientationUtil.kt`](../src/main/kotlin/org/beobma/classWarPlugin/util/DisplayOrientationUtil.kt)에 있다.

## 렌더 변환 순서

`Billboard.FIXED`인 ITEM_DISPLAY의 최종 방향은 개념적으로 다음 순서로 합성된다.

```text
world pose
  = entity yaw/pitch
  * display transformation
  * item renderer Y 180 degrees
  * item model
```

행렬은 오른쪽 항부터 모델에 적용된다. 그러므로 우리가 지정하는 display transformation은 이미 Y축 180° 회전을 거친 검의 축을 목표 월드 축으로 보내야 한다.

관련 자료:

- [PaperMC Display entities 문서](https://docs.papermc.io/paper/dev/display-entities/)
- [Paper ItemDisplay Javadoc](https://jd.papermc.io/paper/26.2/org/bukkit/entity/ItemDisplay.html)
- [Minecraft 1.21.11 DisplayRenderer 구현 참고](https://github.com/H1lkaaaGD/Minecraft_1.21.11_Source/blob/main/net/minecraft/client/renderer/entity/DisplayRenderer.java)
- [JOML Quaternionf API](https://joml-ci.github.io/JOML/apidocs/org/joml/Quaternionf.html)

## 방향 계산 알고리즘

검날 방향만 맞추면 검날 축을 중심으로 한 roll이 결정되지 않는다. 따라서 검날 축과 검 앞면 법선을 모두 맞춘다.

1. 렌더 후 기본 검날 축 `s`를 `normalize(-1, 1, 0)`으로 둔다.
2. 렌더 후 기본 앞면 법선 `m`을 `(0, 0, -1)`로 둔다.
3. 목표 검날 방향 `d`를 정규화한다.
4. 원하는 앞면 법선 `n`을 `d`에 수직인 평면으로 투영하고 정규화한다.
5. `rotationTo(s, d)`로 검날 축을 먼저 맞춘다.
6. 회전된 앞면 법선 `m'`과 목표 법선 `n` 사이의 부호 있는 각도를 구한다.
7. `d`를 축으로 추가 회전하여 roll을 맞춘다.

부호 있는 roll 각도는 다음 식을 사용한다.

```text
theta = atan2(d dot (m' cross n), m' dot n)
```

최종 쿼터니언은 다음 순서다.

```text
q = q_twist * q_blade
```

JOML의 `lookAlong`은 전달한 방향을 양의 Z축으로 보내는 연산이다. 로컬 축을 목표 방향으로 보내는 함수로 오해하기 쉬우므로 이 용도에서는 `rotationTo`와 명시적인 roll 보정을 사용한다.

## ItemDisplay 설정

공통 유틸은 방향 계산과 함께 다음 설정을 강제한다.

```kotlin
display.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.NONE
display.billboard = Display.Billboard.FIXED
display.setRotation(0.0f, 0.0f)
display.transformation = Transformation(
    Vector3f(),
    rotation,
    Vector3f(scale, scale, scale),
    Quaternionf(),
)
```

주의할 점:

- `NONE`은 GUI, 손, 바닥 표시용 모델 transform이 추가되는 것을 막는다.
- display를 플레이어의 `Location`에서 스폰하거나 해당 `Location`으로 teleport하면 yaw/pitch도 복사될 수 있다.
- 월드 방향을 쿼터니언에 직접 넣는 현재 방식에서는 `setRotation(0, 0)`으로 엔티티 회전을 중립화해야 한다.
- 이동할 때마다 yaw/pitch가 다시 복사될 수 있으므로 이동 직후 방향 유틸을 다시 호출한다.

## 클래스별 적용

### 암살자 단검 투척

적용 위치: [`Assassin.kt`](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Assassin.kt)

- 스폰 시 플레이어가 바라본 3D 방향을 `flightDirection`에 저장한다.
- `alignSwordBladeVertically`를 사용한다.
- 검날 축은 `flightDirection`을 향한다.
- 검의 넓은 면은 수직으로 유지한다.
- 투사체 teleport가 yaw/pitch를 다시 설정할 수 있어 이동 직후 매번 같은 방향을 적용한다.

```kotlin
DisplayOrientationUtil.alignSwordBladeVertically(
    display,
    flightDirection,
    scale = 1.45f,
)
```

### 기사 가로베기

적용 위치: [`Knight.kt`](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Knight.kt)

- 부채꼴 애니메이션의 매 틱 `direction`을 계산한다.
- display 위치와 검날 방향에 같은 `direction`을 사용한다.
- `alignSwordBladeHorizontally`를 호출해 검의 넓은 면을 위쪽으로 눕힌다.

```kotlin
DisplayOrientationUtil.alignSwordBladeHorizontally(
    display,
    direction,
    scale = 3.2f,
)
```

방향 유틸을 스폰 시 한 번만 호출하면 검의 위치만 회전하고 모델 방향은 회전하지 않는다. 회전 애니메이션에서는 반드시 매 프레임 호출한다.

## 암살자 벽 부착 안정화

벽 부착은 [`Assassin.kt`](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Assassin.kt)에서 다음 순서로 처리한다.

1. 투사체의 현재 위치는 이미 블록 내부일 수 있으므로 최초 발사점에서 진행 방향으로 다시 ray trace한다.
2. ray trace 결과에서 정확한 충돌면, 바깥쪽 `BlockFace` 법선, 지지 블록을 얻는다.
3. ray trace가 실패하면 진행 방향의 반대쪽에서 가장 큰 성분을 골라 반드시 수직인 블록 면 법선을 만든다. 임의의 대각선 벡터로 벽에서 밀어내지 않는다.
4. 플레이어의 발 위치가 아니라 서 있는 상태의 전체 크기(`width >= 0.6`, `height >= 1.8`)로 후보 히트박스를 만든다.
5. Paper의 `Entity.wouldCollideUsing`으로 블록, 충돌 가능한 엔티티, 월드보더와 겹치지 않는지 검사한다.
6. 최초 후보가 막혀 있으면 벽면의 가로·세로 방향으로 가까운 후보를 순서대로 검사한다.
7. 안전한 후보가 하나도 없으면 벽 속으로 강제 teleport하지 않고 부착을 포기한다.
8. 플레이어가 날아오는 동안 지형이 바뀔 수 있으므로 실제 부착 직전에 같은 검사를 한 번 더 수행한다.

관련 Paper API: [Entity collision Javadoc](https://jd.papermc.io/paper/26.2/org/bukkit/entity/Entity.html#wouldCollideUsing(org.bukkit.util.BoundingBox))

### 충돌 보정과 실제 이동 입력 구분

`PlayerMoveEvent`의 좌표 차이만으로 벽 이탈을 판정하면 다음 상황을 실제 이동으로 오인한다.

- 블록 충돌 보정
- 다른 엔티티가 플레이어를 미는 현상
- 서버와 클라이언트 위치 동기화 오차

따라서 부착 중 `PlayerMoveEvent`는 좌표를 저장된 `wallAnchor`로 되돌리는 역할만 한다. 실제 이탈은 Paper의 `PlayerInputEvent`가 보고한 전진·후진·좌·우·점프 입력으로 처리한다.

관련 파일:

- 이동 입력 훅: [`MovementInputHandler.kt`](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/handler/MovementInputHandler.kt)
- 입력 이벤트 전달: [`OnPlayerInputEvent.kt`](../src/main/kotlin/org/beobma/classWarPlugin/listener/OnPlayerInputEvent.kt)
- 좌표 잠금과 이탈 처리: [`Assassin.kt`](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Assassin.kt)

벽에 닿기 전부터 이동 키를 누르고 있던 경우 즉시 떨어지지 않도록 입력을 처음에는 무장 해제할 수 있다. 모든 이동 키를 한 번 놓은 뒤 새 이동 입력이 들어와야 벽에서 떨어진다.

부착 유지 작업은 매 틱 다음 상태도 보정한다.

- 중력 비활성화
- 속도 0 고정
- 낙하 거리 초기화
- 앵커에서 밀려났다면 현재 시선은 유지하고 위치만 복원
- 지지 블록이 사라지면 정상적으로 벽 해제

## 벽에 박힌 단검의 생명주기

블록 충돌 시 이동 중이던 투사체 display는 투사체 종료와 함께 제거된다. 대신 정확한 충돌면에 별도의 `ItemDisplay`를 생성한다.

- display 중심은 충돌면 바깥쪽으로 `0.35` 블록 이동시켜 손잡이가 보이고 검끝은 벽 안쪽을 향하게 한다.
- 검날 방향은 원래 `flightDirection`을 유지한다.
- `TemporaryDisplayManager`에 등록해 게임·훈련 종료 시에도 누수가 없게 한다.
- 암살자의 `Stealth` 상태가 존재하는 동안 display를 유지한다.
- 벽에서 떨어진 뒤 6초 은신이 끝나 `Stealth`가 제거되는 다음 틱에 display도 제거한다.
- 사망·연결 종료처럼 은신을 즉시 제거하는 경로에서는 display도 즉시 제거한다.

새 단검이 다시 벽에 박히면 이전 박힌 단검과 감시 작업은 먼저 정리한다.

## 암살자 벽 부착 중 웅크리기

`PlayerMoveEvent`만으로는 웅크리기 키 입력을 항상 즉시 감지할 수 없다. 웅크리기 상태 변화는 `PlayerToggleSneakEvent`로 처리한다.

관련 파일:

- 입력 훅: [`SneakInputHandler.kt`](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/handler/SneakInputHandler.kt)
- 이벤트 전달: [`OnPlayerToggleSneakEvent.kt`](../src/main/kotlin/org/beobma/classWarPlugin/listener/OnPlayerToggleSneakEvent.kt)
- 리스너 등록: [`ClassWarPlugin.kt`](../src/main/kotlin/org/beobma/classWarPlugin/ClassWarPlugin.kt)
- 암살자 처리: [`Assassin.kt`](../src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/Assassin.kt)

암살자는 웅크리기를 시작했고 `wallAttached`가 참일 때 다음 순서로 해제한다.

1. `detachFromWall(keepStealth = true, playSound = true)` 호출
2. 중력 복원
3. 단검 투척 쿨다운 재개
4. 벽 해제 후 은신 6초 유지
5. Y 속도 `-0.2`를 적용해 즉시 아래로 떨어지기 시작

웅크리기를 해제하는 이벤트(`event.isSneaking == false`)에는 반응하지 않는다.

## 검증 방법

### 게임 내 확인

암살자:

- 북·남·동·서 방향으로 각각 단검 투척
- 위와 아래를 바라보며 단검 투척
- 모든 경우 손잡이는 플레이어 쪽, 검끝은 진행 방향인지 확인
- 벽 부착 후 웅크리자마자 아래로 떨어지는지 확인
- 해제 후 은신과 쿨다운이 정상적으로 이어지는지 확인

기사:

- 서로 다른 플레이어 yaw에서 가로베기 사용
- 검이 부채꼴 위치와 함께 회전하는지 확인
- 매 프레임 검끝이 중심에서 바깥쪽 `direction`을 향하는지 확인

### 빌드

현재 Gradle 8.8은 Java 25로 직접 실행할 때 실패할 수 있다. Gradle 런타임은 Java 21을 사용하고 프로젝트 툴체인은 `build.gradle.kts` 설정을 따르게 한다.

```powershell
$env:JAVA_HOME='C:\Users\ssdss\.jdks\ms-21.0.11'
.\gradlew.bat --% build -Pkotlin.jvm.target.validation.mode=warning
```

배포 파일:

```text
build/libs/ClassWarPlugin-1.0-SNAPSHOT-all.jar
```

## 다시 문제가 생겼을 때 확인할 목록

1. `swordModelBladeAxis`가 `(-1, 1, 0)`인지 확인한다.
2. `swordModelFaceNormal`이 `(0, 0, -1)`인지 확인한다.
3. ITEM_DISPLAY가 `Billboard.FIXED`인지 확인한다.
4. 엔티티 yaw/pitch와 transformation 회전을 중복 적용하지 않았는지 확인한다.
5. teleport 뒤 방향 유틸을 다시 호출하는지 확인한다.
6. 회전 애니메이션의 매 틱 목표 방향이 실제로 달라지는지 확인한다.
7. `leftRotation`과 `rightRotation`의 합성 순서를 바꾸지 않았는지 확인한다.
8. 커스텀 리소스팩 모델이라면 기본 검과 모델 축이 다를 수 있으므로 별도 기준축을 정의한다.
9. 벽 부착은 `PlayerMoveEvent`의 이동 거리로 해제하지 말고 `PlayerInputEvent`의 실제 입력으로 해제한다.
10. 벽 앵커를 만들기 전에 `wouldCollideUsing`으로 서 있는 플레이어 전체 히트박스를 검사한다.
11. 박힌 단검 display는 `Stealth` 상태가 사라질 때 제거되는지 확인한다.

## Codex용 요약

이 문제를 다시 작업할 때 ITEM_DISPLAY의 보이는 검 축을 원본 텍스처 축으로 추정하지 않는다. 클라이언트가 display transformation 이후 적용하는 Y 180°를 포함해 `(-1, 1, 0)`과 `(0, 0, -1)`을 입력 모델 축으로 사용한다. 검날은 `rotationTo`, roll은 부호 있는 `atan2` 회전으로 맞춘다. 암살자는 수직 면, 기사는 수평 면을 목표 법선으로 사용하며, 움직이는 display는 teleport 이후 매 틱 방향을 다시 적용한다. 벽 부착은 재 ray trace한 충돌면과 `wouldCollideUsing`을 통과한 서 있는 플레이어 크기의 앵커만 사용한다. 좌표 보정은 `PlayerMoveEvent`, 실제 이탈은 `PlayerInputEvent`로 분리한다. 벽에 박힌 단검 display는 `Stealth`가 사라질 때 제거한다.
