package org.beobma.classWarPlugin.growth

enum class GrowthCombatStyle(val label: String, val basicWeight: Double, val skillWeight: Double) {
    BASIC("기본 공격 중심", 1.4, 0.65), HYBRID("복합 전투", 1.0, 1.0),
    ABILITY("스킬 중심", 0.25, 1.4), SUMMON("소환·패시브 중심", 0.2, 1.0)
}

/** Counts are discrete thresholds, not rounded percentages (a tiny bonus cannot create a summon). */
data class GrowthFeatureRule(val label: String, val stat: GrowthStat, val pointsPerStep: Int = 1,
    val flatStep: Int = 0, val maximumExtra: Int = 0, val percent: Double = 0.5, val maximumBonus: Double = 0.5) {
    fun apply(base: Double, stats: (GrowthStat) -> Int): Double {
        val points = stats(stat).coerceAtLeast(0)
        return if (flatStep > 0) base + ((points.toLong() / pointsPerStep) * flatStep).coerceAtMost(maximumExtra.toLong())
            else base * (1.0 + (points * percent / 100).coerceAtMost(maximumBonus))
    }
}

/** Explicit coverage of every playable class, including the Solar System children and copied abilities. */
object GrowthClassCatalog {
    val styles: Map<String, GrowthCombatStyle> = buildMap {
        fun register(style: GrowthCombatStyle, ids: String) = ids.split(' ').forEach { id ->
            check(put(id, style) == null) { "Duplicate growth class: $id" }
        }
        register(GrowthCombatStyle.BASIC, "agent assassin avenger berserker blacksmith brave con-artist crossbow damocles darkness dwarf error exodia feather general-person ghost grass hero high-jumper just-light knight lucky-one mercurius metronome neptune peanuts refugees roulette shy-person stalker tonic train-carriage writer")
        register(GrowthCombatStyle.HYBRID, "abyssal-veil anchor barrier bull chameleon chubby conflict duelist freikugel gambler grave-robber gun-blader hikikomori luna mathematician pacifist pioneer pluto reverse sniper solar-system spider-man terra time-maniqulator vampire venus")
        register(GrowthCombatStyle.ABILITY, "back-room charger contractor death-note devastating-blow elementalist geometer hacker hide-and-seek ice-wizard land-wizard lightning-wizard light-wizard mars meteor parasite pat-and-matt phantom portal-gun rainbow-bridge referee thunderclap-flash tour train trapper uranus warcorrespondent warlock watchmaker weapon-master wounds-wind")
        register(GrowthCombatStyle.SUMMON, "astronomer fear jupiter levatain sagittarius saturnus sol swordplay terrorist")
        register(GrowthCombatStyle.ABILITY, "hunter sturmtruppe spezialeinheitsmitglied schwerekavallerie firearmsmaster")
    }
    fun style(id: String) = styles[id] ?: GrowthCombatStyle.HYBRID

    /** Reviewed weapon-only damage sources. Keep skill weights for copied/secondary effects intact.
     * Do not infer this from an empty skills list: Ghost, Grass and summons deal passive damage.
     */
    val weaponOnlyWeights: Map<String, Double> = buildMap {
        // Low combat impact utility passives need the largest growth compensation.
        "general-person feather high-jumper just-light shy-person refugees".split(' ').forEach { put(it, 2.0) }
        // Defensive, mobility and setup abilities still rely on weapon hits to finish a fight.
        "avenger barrier blacksmith chameleon con-artist darkness dwarf hacker hero hikikomori mathematician portal-gun spider-man terra time-maniqulator tour train train-carriage writer".split(' ').forEach { put(it, 1.7) }
        // Rifle shots use RANGED_ATTACK; reload is not a separate damaging skill.
        put("sniper", 1.4)
    }

    private fun count(label: String, stat: GrowthStat, points: Int, extra: Int, step: Int = 1) =
        GrowthFeatureRule(label, stat, points, step, extra)
    private fun value(label: String, stat: GrowthStat, percent: Double = 0.5, cap: Double = 0.5) =
        GrowthFeatureRule(label, stat, percent = percent, maximumBonus = cap)
    val features: Map<String, Map<String, GrowthFeatureRule>> = mapOf(
        "swordplay" to mapOf("passive-swords" to count("어검술 검 개수", GrowthStat.AGILITY, 20, 6),
            "infinite-swords" to count("인피니트 검 개수", GrowthStat.INTELLIGENCE, 10, 18)),
        "saturnus" to mapOf("rocks" to count("공전 바위 개수", GrowthStat.STRENGTH, 15, 7)),
        "sagittarius" to mapOf("arrows" to count("추가 빛 화살 개수", GrowthStat.AGILITY, 25, 4)),
        "terrorist" to mapOf("bombs" to count("사망 시 폭탄 개수", GrowthStat.INTELLIGENCE, 10, 20, 2)),
        "astronomer" to mapOf("meteors" to count("천문관측 운석 최대 개수", GrowthStat.INTELLIGENCE, 15, 5)),
        "light-wizard" to mapOf("prisms" to count("프리즘 설치 한도", GrowthStat.INTELLIGENCE, 20, 5)),
        "train" to mapOf("stations" to count("기차역 설치 한도", GrowthStat.INTELLIGENCE, 20, 4)),
        "crossbow" to mapOf("bolts" to count("박힌 볼트 유지 개수", GrowthStat.AGILITY, 20, 5)),
        "contractor" to mapOf("daggers" to count("장부 정리 단검 개수", GrowthStat.AGILITY, 20, 4)),
        "spider-man" to mapOf("charges" to count("거미줄 최대 충전", GrowthStat.AGILITY, 25, 3)),
        "thunderclap-flash" to mapOf("charges" to count("벽력일섬 최대 충전", GrowthStat.AGILITY, 25, 3)),
        "gun-blader" to mapOf("bullets" to count("장전 가능한 탄환", GrowthStat.AGILITY, 20, 4)),
        "weapon-master" to mapOf("mastery" to count("달인 최대 스택", GrowthStat.AGILITY, 20, 4)),
        "pioneer" to mapOf("foresight" to count("선견 최대 스택", GrowthStat.INTELLIGENCE, 10, 30, 3)),
        "just-light" to mapOf("light-radius" to value("광원 배치 범위", GrowthStat.INTELLIGENCE, 0.5)),
        "shy-person" to mapOf("detection" to value("관찰자 감지 거리", GrowthStat.INTELLIGENCE, 0.3)),
        "exodia" to mapOf("pickup" to value("부위 수집 거리", GrowthStat.LUCK, 0.5)),
        "grave-robber" to mapOf("dig-range" to value("도굴 가능 거리", GrowthStat.LUCK, 0.5)),
        "high-jumper" to mapOf("jump" to value("축적 도약력", GrowthStat.STRENGTH, 0.3, 0.3)),
        "dwarf" to mapOf("health" to value("왜소한 몸 최대 체력", GrowthStat.STRENGTH, 0.5)),
        "feather" to mapOf("jump" to value("가벼운 몸 도약력", GrowthStat.AGILITY, 0.3, 0.3)),
        "portal-gun" to mapOf("momentum" to value("포탈 운동량 증가량", GrowthStat.STRENGTH, 0.3, 0.3)),
        "writer" to mapOf("reward" to value("정답 문장의 공격 보상", GrowthStat.INTELLIGENCE, 0.5)),
        "mathematician" to mapOf("answer-time" to value("수학 문제 제한시간", GrowthStat.INTELLIGENCE, 0.3)),
        "hacker" to mapOf("code-time" to value("코드 입력 제한시간", GrowthStat.INTELLIGENCE, 0.3)),
    )
}
