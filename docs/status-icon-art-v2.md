# 아이콘 v2 — 참고 이미지 스타일 반영

기존 9종 초안은 `resource-pack/assets/classwar/textures/status/*.png`에 그대로 보존했다. 기존 등록표도 `resource-pack/icons-draft-v1.json`에 보관했다. 새 원본은 `resource-pack/assets/classwar/textures/status/v2/`에 별도 저장하며 `icons.json`은 v2를 사용한다. 사용하지 않는 초안 PNG는 배포 ZIP에서 제외한다.

사용자가 제공한 화염·핏자국·진동·청록색 잎 4장을 스타일 참고 이미지로 사용했다. 굵은 유색 외곽, 둥근 실루엣, 밝은 내부 색면을 반영한다. 잎은 스타일 참고이며 기존 9종의 상태 의미를 바꾸지는 않는다. 내장 image_gen으로 아이콘별 개별 제작했다. 원본 알파를 유지하여 빌드에서 32×32로 축소한다.

## 공통 프롬프트

```text
Use case: stylized-concept. Generate one redesigned production game status icon, square transparent RGBA PNG. Attached images 1-4 are STYLE REFERENCES ONLY: flame, blood splat, vibrating waves, turquoise leaf. Match their chunky cartoon pictogram language: thick rounded dark-colored contour, strong saturated main fill, one or two large lighter inset shapes for shading, slight organic asymmetry, simple clean bold silhouette. NOT monochrome signage. No text, frame, surrounding badge, drop shadow, glowing haze, gradients, scenery, extra objects, tiny ornament or checkerboard background. True transparent exterior. Centered with 8% padding on each edge; readable at 9 pixels tall. Use 3 or at most 4 flat colors with dark edges and simple lighter interior. Create only the requested subject, not a contact sheet. Subject: 
```

## 개별 프롬프트

- Burn: A round red flame with a curled top, dark burgundy thick edge, scarlet middle body, simple yellow-orange flame core. Strongly match reference 1.
- Bleeding: An irregular chunky blood splat with round lobes, dark maroon thick outline, crimson red fill and a small red-orange highlight lobe. Splat like reference 2, NOT a teardrop.
- Vibration: Two pairs of rounded orange crescent shockwaves around a central pale gold jagged pulse. Dark burnt-orange edges, orange fill, pale yellow inner bands. Match reference 3.
- Bullet: Three chunky short brass cartridges, middle slightly taller, dark ochre thick outlines, golden brass body and one pale gold broad highlight.
- Stealth: A compact purple eye crossed by a chunky diagonal slash, thick dark plum contours, violet fill and one lavender broad highlight. Simple closed silhouette.
- Mana: A single squat chunky blue mana crystal, thick navy outline, saturated blue main facet, two broad cyan/light-blue facets. No sparkles or floating shards.
- TrueDamage: A broad ivory spear tip piercing a cracked small golden shield, dark ochre thick outlines, warm gold shield and cream spear, minimal shapes, no small debris.
- VibrationExplosion: A squat orange starburst with rounded sharp lobes, thick burnt-orange edges, amber inner fill and a large pale yellow four-point impact core. Two short curved shockwave marks. Different from normal vibration, same palette.
- Shield: A broad rounded cyan medieval shield, dark teal thick outline, turquoise fill, one broad mint inset shield-shaped highlight, not an empty outline.

## 초안 복구

`icons-draft-v1.json` 내용을 `icons.json`에 복원하고 `gradlew.bat statusIconPack`을 실행하면 초안 팩을 다시 생성할 수 있다. 기존/수정 원본을 삭제할 필요가 없다.
