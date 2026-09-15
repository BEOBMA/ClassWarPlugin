package org.beobma.classWarPlugin.growth

import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.damage.DamageContext
import org.bukkit.Material
import org.bukkit.entity.Mob

/** Eight combat families, each with all twelve ordered primary/secondary stat pairs. */
object GrowthCombatEquipment {
    private data class Build(val id: String, val name: String, val primary: GrowthStat, val secondary: GrowthStat)
    private data class Family(val id: String, val name: String, val material: Material, val slot: GrowthSlot,
        val effect: GrowthEffect, val description: String)

    private val builds = listOf(
        Build("onslaught", "맹공의", GrowthStat.STRENGTH, GrowthStat.AGILITY),
        Build("war", "전쟁의", GrowthStat.STRENGTH, GrowthStat.INTELLIGENCE),
        Build("conqueror", "패왕의", GrowthStat.STRENGTH, GrowthStat.LUCK),
        Build("rush", "질주의", GrowthStat.AGILITY, GrowthStat.STRENGTH),
        Build("flash", "섬광의", GrowthStat.AGILITY, GrowthStat.INTELLIGENCE),
        Build("meteor", "유성의", GrowthStat.AGILITY, GrowthStat.LUCK),
        Build("arcane", "비전의", GrowthStat.INTELLIGENCE, GrowthStat.STRENGTH),
        Build("nebula", "성운의", GrowthStat.INTELLIGENCE, GrowthStat.AGILITY),
        Build("foresight", "예지의", GrowthStat.INTELLIGENCE, GrowthStat.LUCK),
        Build("victory", "승리의", GrowthStat.LUCK, GrowthStat.STRENGTH),
        Build("opportunity", "기회의", GrowthStat.LUCK, GrowthStat.AGILITY),
        Build("miracle", "기적의", GrowthStat.LUCK, GrowthStat.INTELLIGENCE),
    )
    private val families = listOf(
        Family("duel-blade", "결투검", Material.IRON_SWORD, GrowthSlot.WEAPON, GrowthEffect.BASIC_MASTERY,
            "근접·원거리 기본 공격 피해 +12% (고정 피해 제외)"),
        Family("mage-rod", "마도 지팡이", Material.BLAZE_ROD, GrowthSlot.WEAPON, GrowthEffect.ARCANE_MASTERY,
            "스킬 경로 피해 +12% (기본 공격·상태이상·고정 피해 제외)"),
        Family("unyielding-plate", "불굴 갑주", Material.IRON_CHESTPLATE, GrowthSlot.ARMOR, GrowthEffect.LAST_STAND,
            "피격 전 체력이 40% 이하이면 받는 전투 피해 15% 감소 (고정 피해 제외)"),
        Family("vanguard-plate", "선봉 갑주", Material.DIAMOND_CHESTPLATE, GrowthSlot.ARMOR, GrowthEffect.VANGUARD,
            "피격 전 체력이 80% 이상이면 받는 전투 피해 12% 감소 (고정 피해 제외)"),
        Family("ambush-seal", "기습 인장", Material.FLINT, GrowthSlot.ACCESSORY, GrowthEffect.AMBUSH,
            "피격 전 체력이 80% 이상인 대상에게 가하는 피해 +12% (고정 피해 제외)"),
        Family("slayer-badge", "토벌 휘장", Material.BONE, GrowthSlot.ACCESSORY, GrowthEffect.MONSTER_SLAYER,
            "몬스터·동물에게 가하는 피해 +20% (플레이어·고정 피해 제외)"),
        Family("desperate-crystal", "결사 수정", Material.REDSTONE, GrowthSlot.RELIC, GrowthEffect.DESPERATION,
            "자신의 체력이 40% 이하이면 가하는 피해 +15% (고정 피해 제외)"),
        Family("curse-relic", "저주 성물", Material.WITHER_ROSE, GrowthSlot.RELIC, GrowthEffect.AFFLICTION,
            "상태이상 경로 피해 +18% (기본 공격·스킬 피해 제외)"),
    )

    val items: List<GrowthItem> = families.flatMap { family -> builds.map { build ->
        GrowthItem("${family.id}-${build.id}", "${build.name} ${family.name}", family.material, family.slot,
            mapOf(build.primary to 9, build.secondary to 5), family.effect, family.description)
    } }

    fun apply(context: DamageContext, attacker: GrowthPlayerState, defender: GrowthPlayerState?,
        attackerHealth: Double, targetHealth: Double) {
        context.addDamageDealtMultiplier(outgoing(attacker::has, context.path, attackerHealth,
            targetHealth, context.target.entity is Mob))
        if (defender != null) context.addDamageTakenMultiplier(incoming(defender::has, targetHealth))
    }

    /** Multiplicative bonuses match the normal damage pipeline. No extra hit events are emitted. */
    fun outgoing(has: (GrowthEffect) -> Boolean, path: DamagePath, attackerHealth: Double,
        targetHealth: Double, targetIsCreature: Boolean): Double {
        var multiplier = 1.0
        if (path.isBasicAttack && has(GrowthEffect.BASIC_MASTERY)) multiplier *= 1.12
        if (path == DamagePath.SKILL && has(GrowthEffect.ARCANE_MASTERY)) multiplier *= 1.12
        if (targetHealth >= 0.8 && has(GrowthEffect.AMBUSH)) multiplier *= 1.12
        if (targetIsCreature && has(GrowthEffect.MONSTER_SLAYER)) multiplier *= 1.20
        if (attackerHealth <= 0.4 && has(GrowthEffect.DESPERATION)) multiplier *= 1.15
        return multiplier
    }

    // Status damage is usually fixed. Scale its source amount once, before DamageContext locks
    // fixed damage, just as existing growth stat scaling does; never apply it again in outgoing.
    fun statusDamage(has: (GrowthEffect) -> Boolean): Double = if (has(GrowthEffect.AFFLICTION)) 1.18 else 1.0

    /** Used by both player-sourced DamageContext and growth-monster melee/projectile events. */
    fun incoming(has: (GrowthEffect) -> Boolean, health: Double): Double = when {
        health <= 0.4 && has(GrowthEffect.LAST_STAND) -> 0.85
        health >= 0.8 && has(GrowthEffect.VANGUARD) -> 0.88
        else -> 1.0
    }
}
