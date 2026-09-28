# 선택형 상태이상 아이콘

초안 9종은 원본 그대로 유지하고, 나머지 상태 키워드 아이콘 53종을 같은 단순한 실루엣 스타일로 추가했다. 등록표는 `resource-pack/icons.json`, 각 PNG는 `resource-pack/assets/classwar/textures/status/`에 있다. v2·v3·v4 시안은 보존한다.

`Keyword.string` 및 `{keyword:Bleeding}` 같은 기존 키워드 토큰은 아이콘 번역 컴포넌트와 원래 텍스트를 함께 출력한다. 아이템 이름·설명, 키워드 기반 상태 액션바에 공통 적용된다. 피해 텍스트도 연결된 상태 키워드가 있는 처형·출혈·화상·고정 피해·빙결 파쇄·진동 폭발·낙뢰·상태 피해에 아이콘을 표시한다. 키워드와 연결되지 않은 직접 작성 문자열에는 자동으로 추가하지 않는다. 이름 검색은 아이콘 없는 원래 이름을 사용한다.

아이콘은 `classwar.icon.bleeding` 같은 번역 키를 클라이언트에서 해석한다. 번역이 없으면 빈 문자열로 대체하며 공백 역시 번역 문자열 안에만 들어 있다. 따라서 리소스팩이 없는 플레이어는 `출혈`만 보고, 적용한 플레이어는 `아이콘 출혈`을 본다. 아이템을 다른 사람에게 전달해도 서버가 소유자별로 아이템을 변경할 필요가 없다. 서버에서 번역 키를 미리 일반 문자열로 변환하지 않아야 한다.

## 구조 및 추가 방법

- `resource-pack/pack.mcmeta`: Minecraft Java 26.2, 리소스팩 버전 88.0.
- `resource-pack/icons.json`: 키워드 이름별 이미지·높이·기준선 등록.
- `resource-pack/assets/classwar/textures/status/`: 사용자 제작 투명 PNG를 넣는 폴더.
- `gradle/status-icons.gradle.kts`: 폰트, 영문 기본 번역 및 한국어 번역, ZIP 생성.

예를 들어 `bleeding.png`를 이미지 폴더에 넣은 후 `icons.json`의 출혈 항목을 다음처럼 변경한다.

```json
"Bleeding": {
  "texture": "classwar:status/bleeding.png",
  "height": 9,
  "ascent": 8
}
```

항목의 이름은 `Keyword.kt`의 열거형 이름과 정확히 같아야 한다. 예: `Burn`, `Stun`, `VibrationExplosion`. 새 상태는 해당 키워드를 정의하고 상태 이름/설명에서 사용하면 같은 방식으로 등록할 수 있다. 등록하지 않은 아이콘은 리소스팩이 있어도 표시하지 않는다.

권장 원본은 투명 배경 정사각형 PNG이다. 새 초안 아이콘 53종은 1254×1254로 저장하며, 배포 ZIP에는 기존 생성 작업에서 최대 32×32로 축소한다. `height`는 화면상 높이이며 1~32, `ascent`는 0~height이다. 색상은 PNG 자체 색상을 유지한다. 전체 62개 키워드 아이콘을 포함한다. 추가 초안 PNG는 `scripts/build-status-icon-drafts.ps1`로 다시 생성할 수 있다.

## 빌드 및 적용

```powershell
.\gradlew.bat statusIconPack
```

결과: `build/resource-packs/ClassWar-status-icons.zip`. 원본 폴더 자체가 아니라 생성된 ZIP을 사용한다. 빌드 시 등록명, 이미지 경로, 사용자 PNG 존재/디코딩, 높이를 검증한다. 폰트와 번역은 같은 목록으로 동시에 생성하므로 직접 문자 코드를 맞출 필요가 없다. 전체 ZIP을 함께 업데이트한다.

클라이언트의 resourcepacks 폴더에 ZIP을 넣고 활성화하면 된다. 서버 자동 배포를 사용하려면 별도로 ZIP을 호스팅하고 서버 리소스팩 URL/해시를 설정한다. 이 작업은 자동 호스팅·다운로드 강제·서버 설정 변경을 하지 않는다.

검증: 팩 없음/있음/해제 상태에서 아이템 설명과 액션바를 확인하고, 서로 다른 적용 상태의 플레이어끼리 아이템을 전달한다. 새 접속이나 아이템 재발급 없이 클라이언트가 리소스를 다시 읽은 후 표시가 바뀌어야 한다. 다른 팩이 같은 `classwar` 폰트·번역 키를 덮어쓰면 해당 팩의 우선순위를 조정한다.

참고: [MiniMessage 빈 fallback](https://docs.papermc.io/adventure/minimessage/format/#fallback), [26.2 리소스팩 버전](https://feedback.minecraft.net/hc/en-us/articles/46690753273997-Minecraft-Java-Edition-26-2).
