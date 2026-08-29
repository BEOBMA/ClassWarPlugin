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
