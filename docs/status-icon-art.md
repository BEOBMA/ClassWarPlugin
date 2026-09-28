# 전용 아이콘 제작 기록

내장 image_gen 도구로 각 아이콘을 개별 생성했다. 코드 키워드 참조 빈도와 사용자가 제시한 출혈·화상 예시를 기준으로 1차 9종을 선정했다. 실제 경기 사용 통계를 측정한 것은 아니다.

원본: `resource-pack/assets/classwar/textures/status/`. 생성 원본의 투명도를 보존하며 빌드에서 32×32 게임용 텍스처로 축소한다. 각 아이콘은 키워드 기반 설명/액션바에 표시되며 별도 피해 표시 문구는 아직 대상이 아니다.

## 공통 프롬프트

```text
Use case: stylized-concept. Asset type: one production Minecraft status-effect bitmap icon. Generate ONE isolated square icon on genuine transparent RGBA background. Style: extremely simple flat filled combat-status pictogram like a red bleeding splatter or red flame next to Korean UI text. Bold compact silhouette, recognizable reduced to 9 pixels tall. One flat saturated color only, no gradients, no shadows, no glow, no outline, no frame, no text, no letters, no watermark, no checkerboard drawn into image. Thick shapes and large negative cutouts. Centered front view, occupies 85 percent of square, balanced margin. Transparent exterior and cutouts. Subject: 
```

## 개별 Subject 프롬프트

- Bullet: Three short chunky upright rifle cartridges, the center cartridge slightly taller; warm brass gold #E9B34C.
- Stealth: A single watchful eye crossed by a thick diagonal slash, minimal violet #B875E8 silhouette.
- Burn: A bold red flame with three tips and one simple transparent teardrop cutout, crimson #F22D40.
- Mana: A chunky blue faceted mana crystal, only one broad angular inset facet, sapphire #4285F4.
- Vibration: A solid central gold disk with one thick curved vibration wave on each side, amber #F4BD40.
- TrueDamage: A thick ivory spear point piercing a small broken shield, very simple bold readable silhouette, ivory #F2D48A.
- VibrationExplosion: A gold angular impact burst with a simple hollow diamond center and two short shock wave marks, amber #FFC44F.
- Shield: One broad solid medieval shield with a simple transparent inset shield cutout, cyan #54D7EA.
- Bleeding: One large chunky blood droplet surrounded by three smaller round splatter drops, crimson #DE243D.
