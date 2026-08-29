# ClassWarPlugin

## 개발 문서

- [ITEM_DISPLAY 검 방향·회전 및 암살자 벽 해제](docs/item-display-sword-orientation.md)

검색 키워드: `ITEM_DISPLAY`, `ItemDisplay`, `검 방향`, `검 회전`, `DisplayOrientationUtil`, `암살자`, `기사`, `웅크리기`, `벽 부착`

## 자동 업데이트

플러그인은 기본적으로 서버 시작 10초 뒤, 이후 6시간마다
[`BEOBMA/ClassWarPlugin`](https://github.com/BEOBMA/ClassWarPlugin)의 최신 정식 릴리스를 확인합니다.
더 높은 버전의 실행 JAR이 있으면 파일을 검증한 뒤 Paper의 `plugins/update` 폴더에 저장하며,
서버를 재시작할 때 업데이트가 적용됩니다. 실행 중인 플러그인 JAR은 덮어쓰지 않습니다.

- 즉시 확인: `/classwar update` (관리자 또는 콘솔)
- 설정: `config.yml`의 `auto-update` 항목
- 릴리스 조건: SemVer 태그(예: `v1.0.2`)와 JAR 내부 `plugin.yml` 버전(예: `1.0.2`)이 일치해야 합니다.
- 릴리스에 여러 JAR이 있으면 `-all` 또는 `-shadow` JAR을 우선하며, `sources`/`javadoc` JAR은 제외합니다.

## 게임 밸런스 설정

`/classwar config`에서 다음 값을 조절할 수 있습니다.

- 게임 시작 설정 → `재사용 대기시간 흐름`: 기본 `1.0배`, 일반 클릭마다 `0.1배`, Shift 클릭마다 `1.0배` 조절
- 피해 배율 설정 → 기본 공격, 원거리, 스킬, 상태이상 및 낙하·익사·화염·용암·질식·폭발 등 환경 피해 조절
- 월드보더 설정 → 일반 월드보더의 블록당 피해와 최종 자기장의 고정 피해·피해 주기 조절
- 클래스 밸런스 설정 → 클래스 선택 → 전체 효과, 피해량, 회복량, 사거리·범위, 상태이상 지속시간·수치, 클래스별 쿨다운 흐름 조절

전역 쿨다운 배율은 `config.yml`의 `skills.cooldown-flow-multiplier`에 저장됩니다.
클래스별 값은 `class-balance.classes` 아래에 현재 수치 기준 `1.0배`로 자동 생성되며,
GUI에서 바꾸거나 서버를 중지한 뒤 파일에서 직접 수정할 수 있습니다.
피해 배율은 `combat.damage-multipliers`에 저장되며 `0.0배`로 설정하면 해당 피해 유형이 비활성화됩니다.
