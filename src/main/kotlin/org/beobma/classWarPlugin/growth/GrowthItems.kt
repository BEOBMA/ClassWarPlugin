package org.beobma.classWarPlugin.growth

import org.bukkit.Material
import kotlin.math.roundToInt
import kotlin.random.Random

enum class GrowthRarity(val label: String, val color: String) {
    HEROIC("영웅", "light_purple"), LEGENDARY("전설", "yellow"), TRANSCENDENT("초월", "red")
}

enum class GrowthSlot(val label: String) { WEAPON("무기 보조"), ARMOR("방어구"), ACCESSORY("장신구"), RELIC("유물") }
enum class GrowthEffect {
    EXECUTE, LIFESTEAL, SPELLBLADE, WARD, SECOND_WIND, HASTE, FORTUNE, HUNTER, REGEN, BARRIER, FOCUS, TITAN,
    BASIC_MASTERY, ARCANE_MASTERY, LAST_STAND, VANGUARD, AMBUSH, MONSTER_SLAYER, DESPERATION, AFFLICTION,
    MELEE_FURY, DEADEYE, CLOSE_CASTER, FAR_CASTER, HEALTHY_HUNTER, DUEL_PREDATOR, MELEE_GUARD, MISSILE_GUARD, SPELL_GUARD, BEAST_GUARD, CLOSE_GUARD, DISTANT_GUARD, GIANT_HUNTER, UNDERDOG, FINISHER_SKILL, STEADY_AIM, SPRINT_STRIKE, AIR_ASSAULT, NIGHT_GUARD, DAY_GUARD, CROUCH_GUARD, AIR_GUARD, EVEN_GUARD, GIANT_GUARD,
}
data class GrowthItem(val id: String, val name: String, val material: Material, val slot: GrowthSlot,
    val stats: Map<GrowthStat, Int>, val effect: GrowthEffect, val description: String, val eventOnly: Boolean = false,
    val rarity: GrowthRarity = GrowthRarity.HEROIC) {
    val displayName get() = "<${rarity.color}>[${rarity.label}] $name</${rarity.color}>"
}

/** Inventory stores IDs, not physical tokens: skills, deaths and item cleanup cannot duplicate equipment. */
object GrowthItems {
    private fun stats(a: GrowthStat, value: Int, b: GrowthStat, second: Int) = mapOf(a to value, b to second)
    private val originals = listOf(
        GrowthItem("dawn-edge", "여명의 칼날", Material.NETHERITE_SWORD, GrowthSlot.WEAPON,
            stats(GrowthStat.STRENGTH, 10, GrowthStat.AGILITY, 4), GrowthEffect.EXECUTE, "체력 30% 이하 대상 피해 +15%"),
        GrowthItem("blood-fang", "혈빛 송곳니", Material.REDSTONE, GrowthSlot.WEAPON,
            stats(GrowthStat.AGILITY, 9, GrowthStat.STRENGTH, 5), GrowthEffect.LIFESTEAL, "적중 시 피해의 6% 회복 (초당 최대 2 체력)"),
        GrowthItem("spell-edge", "마력의 검", Material.AMETHYST_SHARD, GrowthSlot.WEAPON,
            stats(GrowthStat.INTELLIGENCE, 10, GrowthStat.AGILITY, 4), GrowthEffect.SPELLBLADE, "스킬 사용 후 5초 내 기본 공격 피해 +25% (6초)"),
        GrowthItem("dusk-plate", "황혼의 갑주", Material.NETHERITE_CHESTPLATE, GrowthSlot.ARMOR,
            stats(GrowthStat.STRENGTH, 8, GrowthStat.INTELLIGENCE, 4), GrowthEffect.WARD, "받는 전투 피해 10% 감소"),
        GrowthItem("phoenix-coat", "불사조 외투", Material.LEATHER_CHESTPLATE, GrowthSlot.ARMOR,
            stats(GrowthStat.LUCK, 8, GrowthStat.STRENGTH, 5), GrowthEffect.SECOND_WIND, "체력 30% 미만 피격 후 최대 체력 8% 회복 (30초)"),
        GrowthItem("titan-mail", "거인의 사슬", Material.CHAINMAIL_CHESTPLATE, GrowthSlot.ARMOR,
            stats(GrowthStat.STRENGTH, 12, GrowthStat.AGILITY, 2), GrowthEffect.TITAN, "최대 체력 +15%"),
        GrowthItem("wind-charm", "질풍의 부적", Material.FEATHER, GrowthSlot.ACCESSORY,
            stats(GrowthStat.AGILITY, 10, GrowthStat.LUCK, 4), GrowthEffect.HASTE, "이동 속도 +8%"),
        GrowthItem("gold-dice", "황금 주사위", Material.GOLD_NUGGET, GrowthSlot.ACCESSORY,
            stats(GrowthStat.LUCK, 12, GrowthStat.INTELLIGENCE, 2), GrowthEffect.FORTUNE, "장비 발견 확률 +10%p"),
        GrowthItem("hunter-eye", "사냥꾼의 눈", Material.ENDER_EYE, GrowthSlot.ACCESSORY,
            stats(GrowthStat.AGILITY, 7, GrowthStat.STRENGTH, 7), GrowthEffect.HUNTER, "몬스터 처치 경험치 +20%"),
        GrowthItem("sage-stone", "현자의 돌", Material.DIAMOND, GrowthSlot.RELIC,
            stats(GrowthStat.INTELLIGENCE, 10, GrowthStat.LUCK, 4), GrowthEffect.FOCUS, "스킬 재사용 대기시간 흐름 +10%"),
        GrowthItem("world-tree", "세계수의 심장", Material.OAK_SAPLING, GrowthSlot.RELIC,
            stats(GrowthStat.STRENGTH, 10, GrowthStat.INTELLIGENCE, 10), GrowthEffect.REGEN, "안전 지역에서 5초마다 최대 체력 2% 회복", true),
        GrowthItem("moon-heart", "달의 핵", Material.HEART_OF_THE_SEA, GrowthSlot.RELIC,
            stats(GrowthStat.LUCK, 10, GrowthStat.AGILITY, 10), GrowthEffect.BARRIER, "다음 피격 피해 30% 감소 (20초)", true),
    )
    // Alternate stat builds retain the same implemented effect and share its cooldown.
    // Event originals keep their exclusive IDs and stronger 20-point stat budgets.
    private fun variant(base: String, id: String, name: String, material: Material,
        primary: GrowthStat, secondary: GrowthStat): GrowthItem = originals.first { it.id == base }.copy(
        id = id, name = name, material = material, stats = stats(primary, 10, secondary, 4), eventOnly = false)

    private val bases = originals + listOf(
        variant("dawn-edge", "comet-edge", "혜성의 칼날", Material.DIAMOND_SWORD,
            GrowthStat.AGILITY, GrowthStat.LUCK),
        variant("dawn-edge", "oracle-edge", "예언의 칼날", Material.QUARTZ,
            GrowthStat.INTELLIGENCE, GrowthStat.STRENGTH),
        variant("dawn-edge", "fate-edge", "운명의 칼날", Material.GOLDEN_SWORD,
            GrowthStat.LUCK, GrowthStat.INTELLIGENCE),
        variant("blood-fang", "beast-fang", "야수의 송곳니", Material.IRON_AXE,
            GrowthStat.STRENGTH, GrowthStat.LUCK),
        variant("blood-fang", "soul-fang", "영혼의 송곳니", Material.GHAST_TEAR,
            GrowthStat.INTELLIGENCE, GrowthStat.AGILITY),
        variant("blood-fang", "crimson-contract", "진홍의 계약", Material.PAPER,
            GrowthStat.LUCK, GrowthStat.INTELLIGENCE),
        variant("spell-edge", "rune-hammer", "룬 망치", Material.IRON_PICKAXE,
            GrowthStat.STRENGTH, GrowthStat.INTELLIGENCE),
        variant("spell-edge", "arcane-needle", "비전의 바늘", Material.PRISMARINE_SHARD,
            GrowthStat.AGILITY, GrowthStat.LUCK),
        variant("spell-edge", "miracle-wand", "기적의 지팡이", Material.BLAZE_ROD,
            GrowthStat.LUCK, GrowthStat.STRENGTH),

        variant("dusk-plate", "mist-cloak", "안개의 망토", Material.LEATHER_CHESTPLATE,
            GrowthStat.AGILITY, GrowthStat.INTELLIGENCE),
        variant("dusk-plate", "rune-robe", "룬의 로브", Material.LEATHER_CHESTPLATE,
            GrowthStat.INTELLIGENCE, GrowthStat.LUCK),
        variant("dusk-plate", "royal-vest", "왕실의 조끼", Material.GOLDEN_CHESTPLATE,
            GrowthStat.LUCK, GrowthStat.STRENGTH),
        variant("phoenix-coat", "ember-plate", "잔불의 갑주", Material.IRON_CHESTPLATE,
            GrowthStat.STRENGTH, GrowthStat.INTELLIGENCE),
        variant("phoenix-coat", "dawn-cloak", "새벽의 망토", Material.LEATHER_CHESTPLATE,
            GrowthStat.AGILITY, GrowthStat.LUCK),
        variant("phoenix-coat", "soul-robe", "소생의 법의", Material.LEATHER_CHESTPLATE,
            GrowthStat.INTELLIGENCE, GrowthStat.AGILITY),
        variant("titan-mail", "mountain-vest", "산맥의 조끼", Material.DIAMOND_CHESTPLATE,
            GrowthStat.AGILITY, GrowthStat.STRENGTH),
        variant("titan-mail", "astral-mail", "성운의 갑주", Material.CHAINMAIL_CHESTPLATE,
            GrowthStat.INTELLIGENCE, GrowthStat.STRENGTH),
        variant("titan-mail", "king-mail", "왕의 사슬갑옷", Material.GOLDEN_CHESTPLATE,
            GrowthStat.LUCK, GrowthStat.STRENGTH),

        variant("wind-charm", "storm-belt", "폭풍의 허리띠", Material.LEATHER,
            GrowthStat.STRENGTH, GrowthStat.AGILITY),
        variant("wind-charm", "zephyr-pendant", "미풍의 펜던트", Material.LIGHT_BLUE_DYE,
            GrowthStat.INTELLIGENCE, GrowthStat.AGILITY),
        variant("wind-charm", "wanderer-ring", "방랑자의 반지", Material.IRON_NUGGET,
            GrowthStat.LUCK, GrowthStat.AGILITY),
        variant("gold-dice", "prospector-badge", "탐광자의 휘장", Material.RAW_GOLD,
            GrowthStat.STRENGTH, GrowthStat.LUCK),
        variant("gold-dice", "magpie-feather", "까치의 깃털", Material.BLACK_DYE,
            GrowthStat.AGILITY, GrowthStat.LUCK),
        variant("gold-dice", "treasure-compass", "보물 나침반", Material.COMPASS,
            GrowthStat.INTELLIGENCE, GrowthStat.LUCK),
        variant("hunter-eye", "trophy-necklace", "전리품 목걸이", Material.BONE,
            GrowthStat.STRENGTH, GrowthStat.LUCK),
        variant("hunter-eye", "scholar-lens", "탐구자의 렌즈", Material.SPYGLASS,
            GrowthStat.INTELLIGENCE, GrowthStat.AGILITY),
        variant("hunter-eye", "tracker-token", "추적자의 증표", Material.RABBIT_FOOT,
            GrowthStat.LUCK, GrowthStat.STRENGTH),

        variant("sage-stone", "war-hourglass", "전쟁의 모래시계", Material.CLOCK,
            GrowthStat.STRENGTH, GrowthStat.INTELLIGENCE),
        variant("sage-stone", "echo-crystal", "메아리 수정", Material.ECHO_SHARD,
            GrowthStat.AGILITY, GrowthStat.INTELLIGENCE),
        variant("sage-stone", "destiny-orb", "천운의 구슬", Material.ENDER_PEARL,
            GrowthStat.LUCK, GrowthStat.INTELLIGENCE),
        variant("world-tree", "life-seed", "생명의 씨앗", Material.WHEAT_SEEDS,
            GrowthStat.STRENGTH, GrowthStat.LUCK),
        variant("world-tree", "forest-dew", "숲의 이슬", Material.SLIME_BALL,
            GrowthStat.AGILITY, GrowthStat.INTELLIGENCE),
        variant("world-tree", "spring-vessel", "샘물의 성배", Material.NAUTILUS_SHELL,
            GrowthStat.INTELLIGENCE, GrowthStat.STRENGTH),
        variant("moon-heart", "guardian-core", "수호자의 핵", Material.PRISMARINE_CRYSTALS,
            GrowthStat.STRENGTH, GrowthStat.INTELLIGENCE),
        variant("moon-heart", "twilight-mirror", "황혼의 거울", Material.GLASS,
            GrowthStat.AGILITY, GrowthStat.LUCK),
        variant("moon-heart", "moon-fragment", "달빛 파편", Material.END_STONE,
            GrowthStat.INTELLIGENCE, GrowthStat.LUCK),
    ) + GrowthCombatEquipment.items
    // Stable original IDs remain valid. Event originals start at legendary, never in the hero pool.
    val all: List<GrowthItem> = (bases + GrowthArsenal.create(bases) + GrowthUniqueEquipment.items).flatMap { base ->
        val original = if (base.eventOnly) base.copy(rarity = GrowthRarity.LEGENDARY) else base
        listOf(original) + GrowthRarity.entries.filter { it.ordinal > original.rarity.ordinal }.map { rarity ->
            val multiplier = if (base.eventOnly) 1.5 else if (rarity == GrowthRarity.LEGENDARY) 1.5 else 2.0
            base.copy(id = "${base.id}-${rarity.name.lowercase()}", name = "${rarity.label} ${base.name}",
                stats = base.stats.mapValues { (_, value) -> (value * multiplier).roundToInt() },
                rarity = rarity, eventOnly = true)
        }
    }
    private val index = all.associateBy { it.id }
    val ordinary = all.filter { it.rarity == GrowthRarity.HEROIC }
    val legendary = all.filter { it.rarity == GrowthRarity.LEGENDARY }
    val transcendent = all.filter { it.rarity == GrowthRarity.TRANSCENDENT }
    const val TRANSCENDENT_CHANCE = 0.10
    fun monsterReward(limited: Boolean, random: Random): GrowthItem = when {
        !limited -> ordinary
        random.nextDouble() < TRANSCENDENT_CHANCE -> transcendent
        else -> legendary
    }.random(random)
    const val PAGE_SIZE = 45
    fun pageCount(ids: Set<String>) = ((owned(ids).size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    fun page(ids: Set<String>, page: Int) = owned(ids)
        .drop(page.coerceIn(0, pageCount(ids) - 1) * PAGE_SIZE).take(PAGE_SIZE)
    fun byId(id: String) = index[id]
    fun owned(ids: Set<String>): List<GrowthItem> = all.filter { it.id in ids }
}
