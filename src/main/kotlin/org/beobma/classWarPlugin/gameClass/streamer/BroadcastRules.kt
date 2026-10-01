package org.beobma.classWarPlugin.gameClass.streamer

/** Unspecified balance values live here, separate from Bukkit and presentation. */
internal object BroadcastRules {
    const val INITIAL_VIEWERS = 1000
    const val MAX_VIEWERS = 1_000_000
    val tiers = listOf(1000, 3000, 5000, 10000, 30000, 50000, 100000, 1000000)
    fun decay(viewers: Int) = (viewers - maxOf(1, viewers / 100)).coerceAtLeast(0)
    fun excited(viewers: Int) = (viewers + maxOf(200, viewers / 10)).coerceAtMost(MAX_VIEWERS)
    fun bonus(viewers: Int) = (viewers.coerceAtLeast(0) / 100000.0).coerceAtMost(1.0)
    fun chance(viewers: Int) = (0.15 + viewers.coerceAtLeast(0) / 2000000.0).coerceAtMost(0.65)
    fun tier(amount: Int) = tiers.lastOrNull { amount >= it }
    fun donation(viewers: Int, roll: Double): Int = (viewers.coerceIn(0, MAX_VIEWERS) * (0.5 + roll.coerceIn(0.0, 1.0) * 1.5)).toInt().coerceAtLeast(100)
    // Original short-form chat, separated by actual combat context; no fabricated real chat quotes.
    val idle = listOf("ㅎㅇ", "방금 왔는데 뭐하는거임", "지금 몇 판째?", "이거 무슨 클래스임?", "소리 잘 들림", "ㅇㅇ", "채팅 보고 있나", "오늘 몇 시까지 함?", "일단 보는 중", "상점 한번 보자", "이 맵 넓네", "다음 판도 이거 함?", "잠깐 물 가지러 감", "브금 없어도 긴장되네", "아까부터 보고 있었음", "설명 듣고도 모르겠음 ㅋㅋ", "이거 직접 하는 게 더 어렵겠지", "오 아직 안 끝났네")
    val attack = listOf("오", "와", "ㄷㄷ", "ㅋㅋㅋㅋㅋㅋ", "이게 맞네", "방금 뭐임?", "오 좀 치네", "ㅇㅈ", "나이스", "이건 잘했다", "판정 뭐야 ㅋㅋ", "그게 닿네", "상대 당황한 거 같은데", "방금은 인정", "아 이 각이 나오네", "ㅋㅋㅋ 이걸", "한 대 더", "방금 깔끔했다", "오 맞췄어", "이 맛에 보는 거지")
    val hurt = listOf("아", "아니 ㅋㅋㅋㅋ", "뭐해 ㅋㅋ", "피해 피해", "체력 봐", "잠깐잠깐", "아 그걸 맞네", "일단 빠져", "욕심내지 말고", "ㅋㅋㅋㅋ 아깝다", "괜찮음 아직", "왜 들어갔어 ㅋㅋ", "상대도 잘하네", "아프다", "아 이건 좀", "거리 좀 벌려", "침착", "지금 채팅 볼 때 아님", "거기서 맞네", "이거 버티나")
    val names = listOf("ㅇㅇ", "지나가던사람", "김감자", "퇴근하고옴", "닉네임뭐하지", "고양이집사", "물만두", "user2048", "점심뭐먹지", "눈팅중", "주말언제옴", "새벽두시", "이름없음", "아이스아메", "겜보는사람", "키보드먼지")
}
