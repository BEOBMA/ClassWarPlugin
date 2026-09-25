# 아이콘 v3 — 각진 윤곽과 인쇄 질감

내장 image_gen으로 기존 9종을 다시 제작했다. 사용자의 최근 예시(붉은 상처, 불타는 책, 푸른 별, 단일 탄환, 팔)를 스타일 참고로 사용했다. 둥근 광택형 v2에서 거친 윤곽, 날카로운 끝, 짙은 외곽, 제한된 밝은 강조, 내부 점무늬로 수정했다. 탄환은 사선 단일 탄환이다.

원본: `resource-pack/assets/classwar/textures/status/v3/`. 초안 및 v2 원본은 그대로 보존하고 v2 등록표는 `resource-pack/icons-v2.json`에 저장했다. `icons.json`은 v3를 사용한다. 배포본은 기존 파이프라인으로 32×32로 축소하며 아이템에서 표시되는 높이는 9로 유지한다. 작은 표시에서는 미세한 점무늬가 합쳐져 보일 수 있다. 실제 게임 내 가독성은 별도 확인이 필요하다.

## 공통 프롬프트

```text
Use case: stylized-concept. Create ONE production combat status icon for a gritty 2D game UI, genuine transparent RGBA, square. The attached five images are the STYLE AND TEXTURE REFERENCE: rough red slashes, jagged fire book, angular blue star, single tilted bullet, angular red arm. Closely match their small rough illustrated icon style. CRITICAL: flat screen-printed art, angular broken contours, sharp pointed silhouette, moderately thick uneven dark colored outline (NOT fat smooth rounded border), deep saturated midtones, only small lighter accents. Visible coarse darker halftone stipple patches INSIDE the colored shapes like the references. Mostly 2-3 inks. NO glossy highlights, NO gradients, NO pillow bevel, NO smooth mobile-game sticker look, NO giant rounded blobs. Rough hand-cut edges but clearly readable compact silhouette. Only a few bold interior lines. Center icon with 8% transparent margins, no text, no frame, no background color or drawn checkerboard, no ambient shadow or glow. Output one isolated icon, not a sheet. Subject: 
```

## 개별 프롬프트

- Bullet: One single cartridge tilted 30 degrees to the right, pointed top toward upper right, flat gold-yellow body with ochre-orange rough contour, two angled orange seam lines and fine burnt-orange stipple on one side. Follow the single bullet reference closely; NO bundle of bullets.
- Bleeding: A ragged blood stain crossed by three sharp diagonal bleeding cuts, deep crimson and oxblood contour, irregular pointed splatter edges with two tiny droplets, darker red halftone patch. No glossy reflection and no large rounded teardrop.
- Burn: A compact jagged flame leaning right, hooked pointed red tongues with sharp notches, dark wine-red irregular contour, dark scarlet body with a SMALL dull amber inner flame, coarse red stipple patches; most area is red, NOT yellow.
- Stealth: An angular narrow purple eye crossed by a sharp diagonal slash, dark plum broken contour, muted violet body with a small dusty lavender slit, coarse dark halftone on the upper lid, no round eye or glossy shine.
- Mana: An asymmetrical faceted blue crystal with chipped angular corners, deep navy ragged contour, medium cobalt body and small turquoise angular facet, dark stippling on one facet, NOT pale cyan overall.
- Vibration: Three compact jagged vertical ochre-orange wave strokes with two short bowed side brackets, dark rust irregular edge, orange body with narrow dull yellow accents and coarse stipple. Strong vibration/pulse silhouette, not a smooth rounded ring.
- TrueDamage: A sharp ivory spearhead thrust diagonally down-left through a small cracked ochre shield, deep brown ragged contours, ochre main fill with sparse cream spearhead accents, bold black-brown crack and grainy stipple; no glossy highlight.
- VibrationExplosion: An asymmetrical jagged orange impact burst, dark rust rough contour, deep amber-orange body, small pale yellow zigzag impact core, 2 short angular shockwave strokes and dark halftone near edges. Hard pointed corners, not a rounded star.
- Shield: A small angular pentagonal teal shield with chipped shoulders, deep petrol irregular contour, muted medium teal body, a small mint angular inset strip and dark halftone on one half; NOT round, NOT a shiny polished shield.
