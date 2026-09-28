# 직접 설계한 픽셀 아이콘 v4

사용자의 재수정 요청에 따라 먼저 출혈·화상·탄환 3종만 교체했다. 나머지 6종은 v3를 유지한다. 초안부터 v3까지 원본은 수정하지 않았다.

이미지 생성 도구 없이 `scripts/build-pixel-status-icons.ps1`에 32×32 좌표와 색상을 직접 지정했다. 각 아이콘은 투명을 제외하고 3색이며 그라데이션·노이즈·광택·무작위 요소가 없다. 출혈은 핏자국, 화상은 붉은 불꽃과 황색 중심, 탄환은 단일 사선 탄환으로 표현한다.

- 원본: `resource-pack/assets/classwar/textures/status/v4/`
- 확대/32px/9px 비교 미리보기: `build/icon-previews/status-v4.png`
- 재생성: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-pixel-status-icons.ps1`
- 팩 빌드: `gradlew.bat statusIconPack`

실제 클라이언트의 글꼴 렌더링·GUI 배율에 따른 가독성은 게임 내에서 추가 확인한다. 비교 미리보기는 실제 Minecraft 스크린샷이 아니다.
