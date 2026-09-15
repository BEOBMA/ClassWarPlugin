package org.beobma.classWarPlugin.growth

import org.bukkit.Material

enum class GrowthSlot(val label: String) { WEAPON("무기 보조"), ARMOR("방어구"), ACCESSORY("장신구"), RELIC("유물") }
enum class GrowthEffect { EXECUTE, LIFESTEAL, SPELLBLADE, WARD, SECOND_WIND, HASTE, FORTUNE, HUNTER, REGEN, BARRIER, FOCUS, TITAN }
data class GrowthItem(val id: String, val name: String, val material: Material, val slot: GrowthSlot,
    val stats: Map<GrowthStat, Int>, val effect: GrowthEffect, val description: String, val eventOnly: Boolean = false)

/** Inventory stores IDs, not physical tokens: skills, deaths and item cleanup cannot duplicate equipment. */
object GrowthItems {
    private fun stats(a: GrowthStat, value: Int, b: GrowthStat, second: Int) = mapOf(a to value, b to second)
    val all = listOf(
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
    private val index = all.associateBy { it.id }
    fun byId(id: String) = index[id]
    fun owned(ids: Set<String>): List<GrowthItem> = all.filter { it.id in ids }
}
