package org.beobma.classWarPlugin.growth

import org.beobma.classWarPlugin.damage.DamagePath
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player

/** Snapshot at impact: conditions never use post-hit health or change damage paths. */
data class GrowthCombatFacts(
    val path: DamagePath = DamagePath.BASIC_ATTACK, val distance: Double = 5.0,
    val attackerHealth: Double = 0.6, val targetHealth: Double = 0.6,
    val attackerMax: Double = 20.0, val targetMax: Double = 20.0,
    val attackerMob: Boolean = false, val targetMob: Boolean = false, val targetPlayer: Boolean = false,
    val attackerGrounded: Boolean = true, val attackerSprinting: Boolean = false,
    val targetGrounded: Boolean = true, val targetSneaking: Boolean = false, val night: Boolean = false,
)

object GrowthUniqueEquipment {
    data class Rule(val effect: GrowthEffect, val id: String, val name: String, val material: Material,
        val slot: GrowthSlot, val multiplier: Double, val description: String, val matches: (GrowthCombatFacts) -> Boolean) {
        val offensive get() = multiplier > 1.0
    }
    val rules = listOf(
        Rule(GrowthEffect.MELEE_FURY, "cleaver", "분쇄자의 대검", Material.IRON_SWORD, GrowthSlot.WEAPON, 1.14, "근접 기본 공격 피해 +14%") { f -> f.path == DamagePath.BASIC_ATTACK },
        Rule(GrowthEffect.DEADEYE, "deadeye", "명사수의 조준경", Material.SPYGLASS, GrowthSlot.WEAPON, 1.16, "원거리 기본 공격 피해 +16%") { f -> f.path == DamagePath.RANGED_ATTACK },
        Rule(GrowthEffect.CLOSE_CASTER, "spell-gauntlet", "접전 주문장갑", Material.AMETHYST_SHARD, GrowthSlot.WEAPON, 1.18, "4칸 이내 적에게 스킬 피해 +18%") { f -> f.path == DamagePath.SKILL && f.distance <= 4 },
        Rule(GrowthEffect.FAR_CASTER, "horizon-staff", "지평선 지팡이", Material.BLAZE_ROD, GrowthSlot.WEAPON, 1.18, "8칸 이상 떨어진 적에게 스킬 피해 +18%") { f -> f.path == DamagePath.SKILL && f.distance >= 8 },
        Rule(GrowthEffect.HEALTHY_HUNTER, "ranger-knife", "유격대 단검", Material.IRON_AXE, GrowthSlot.WEAPON, 1.14, "자신의 체력이 80% 이상이면 몬스터에게 피해 +14%") { f -> f.attackerHealth >= 0.8 && f.targetMob },
        Rule(GrowthEffect.DUEL_PREDATOR, "challenger", "도전자의 칼날", Material.DIAMOND_SWORD, GrowthSlot.WEAPON, 1.12, "플레이어에게 가하는 피해 +12%") { f -> f.targetPlayer },
        Rule(GrowthEffect.MELEE_GUARD, "bladeproof", "검막 갑주", Material.IRON_CHESTPLATE, GrowthSlot.ARMOR, 0.88, "받는 근접 기본 공격 피해 12% 감소") { f -> f.path == DamagePath.BASIC_ATTACK },
        Rule(GrowthEffect.MISSILE_GUARD, "arrowproof", "화살막 갑주", Material.CHAINMAIL_CHESTPLATE, GrowthSlot.ARMOR, 0.85, "받는 원거리 기본 공격 피해 15% 감소") { f -> f.path == DamagePath.RANGED_ATTACK },
        Rule(GrowthEffect.SPELL_GUARD, "spellproof", "주문막 법의", Material.LEATHER_CHESTPLATE, GrowthSlot.ARMOR, 0.88, "받는 스킬 피해 12% 감소") { f -> f.path == DamagePath.SKILL },
        Rule(GrowthEffect.BEAST_GUARD, "beastproof", "수렵 방호복", Material.LEATHER_CHESTPLATE, GrowthSlot.ARMOR, 0.82, "몬스터에게 받는 전투 피해 18% 감소") { f -> f.attackerMob },
        Rule(GrowthEffect.CLOSE_GUARD, "closeproof", "근접 방벽갑", Material.DIAMOND_CHESTPLATE, GrowthSlot.ARMOR, 0.9, "3칸 이내 공격자에게 받는 피해 10% 감소") { f -> f.distance <= 3 },
        Rule(GrowthEffect.DISTANT_GUARD, "farproof", "원경 보호갑", Material.GOLDEN_CHESTPLATE, GrowthSlot.ARMOR, 0.88, "8칸 이상 떨어진 공격자에게 받는 피해 12% 감소") { f -> f.distance >= 8 },
        Rule(GrowthEffect.GIANT_HUNTER, "giant-mark", "거인 사냥 표식", Material.BONE, GrowthSlot.ACCESSORY, 1.16, "대상 최대 체력이 자신의 125% 이상이면 피해 +16%") { f -> f.targetMax >= f.attackerMax * 1.25 },
        Rule(GrowthEffect.UNDERDOG, "defiant-ring", "저항자의 반지", Material.IRON_NUGGET, GrowthSlot.ACCESSORY, 1.14, "자신의 남은 체력 비율이 대상보다 낮으면 피해 +14%") { f -> f.attackerHealth < f.targetHealth },
        Rule(GrowthEffect.FINISHER_SKILL, "execution-seal", "종결의 인장", Material.FLINT, GrowthSlot.ACCESSORY, 1.2, "체력 30% 이하 대상에게 스킬 피해 +20%") { f -> f.path == DamagePath.SKILL && f.targetHealth <= 0.3 },
        Rule(GrowthEffect.STEADY_AIM, "steady-lens", "안정 조준 렌즈", Material.GLASS, GrowthSlot.ACCESSORY, 1.14, "지상에서 질주하지 않을 때 원거리 기본 공격 피해 +14%") { f -> f.path == DamagePath.RANGED_ATTACK && f.attackerGrounded && !f.attackerSprinting },
        Rule(GrowthEffect.SPRINT_STRIKE, "charge-buckle", "돌격 버클", Material.RABBIT_FOOT, GrowthSlot.ACCESSORY, 1.12, "질주 중 근접 기본 공격 피해 +12%") { f -> f.path == DamagePath.BASIC_ATTACK && f.attackerSprinting },
        Rule(GrowthEffect.AIR_ASSAULT, "sky-pin", "공습의 핀", Material.FEATHER, GrowthSlot.ACCESSORY, 1.14, "공중에서 기본 공격 피해 +14%") { f -> f.path.isBasicAttack && !f.attackerGrounded },
        Rule(GrowthEffect.NIGHT_GUARD, "night-ward", "야행의 등불", Material.SOUL_LANTERN, GrowthSlot.RELIC, 0.9, "밤에 받는 전투 피해 10% 감소") { f -> f.night },
        Rule(GrowthEffect.DAY_GUARD, "day-ward", "백주의 성화", Material.LANTERN, GrowthSlot.RELIC, 0.9, "낮에 받는 전투 피해 10% 감소") { f -> !f.night },
        Rule(GrowthEffect.CROUCH_GUARD, "stone-idol", "웅크린 석상", Material.STONE, GrowthSlot.RELIC, 0.88, "웅크리는 동안 받는 전투 피해 12% 감소") { f -> f.targetSneaking },
        Rule(GrowthEffect.AIR_GUARD, "cloud-vessel", "구름의 항아리", Material.WHITE_DYE, GrowthSlot.RELIC, 0.88, "공중에서 받는 전투 피해 12% 감소 (낙하 피해 제외)") { f -> !f.targetGrounded },
        Rule(GrowthEffect.EVEN_GUARD, "balance-orb", "평형의 구슬", Material.PRISMARINE_CRYSTALS, GrowthSlot.RELIC, 0.9, "체력이 40% 초과 80% 미만일 때 받는 피해 10% 감소") { f -> f.targetHealth > 0.4 && f.targetHealth < 0.8 },
        Rule(GrowthEffect.GIANT_GUARD, "titan-ward", "거신의 봉인", Material.OBSIDIAN, GrowthSlot.RELIC, 0.86, "공격자 최대 체력이 자신의 125% 이상이면 받는 피해 14% 감소") { f -> f.attackerMax >= f.targetMax * 1.25 },
    )
    val items = rules.flatMap { rule -> GrowthArsenal.builds.map { build ->
        GrowthItem("unique-${rule.id}-${build.id}", "${build.name} ${rule.name}", rule.material,
            rule.slot, build.stats, rule.effect, "${rule.description} (고정 피해 제외, 적중 시점 기준)")
    } }
    fun equipped(state: GrowthPlayerState?) = state != null && rules.any { state.has(it.effect) }
    fun apply(context: org.beobma.classWarPlugin.damage.DamageContext, attacker: GrowthPlayerState,
        defender: GrowthPlayerState?, facts: GrowthCombatFacts) {
        context.addDamageDealtMultiplier(outgoing(attacker::has, facts))
        if (defender != null) context.addDamageTakenMultiplier(incoming(defender::has, facts))
    }
    fun outgoing(has: (GrowthEffect) -> Boolean, facts: GrowthCombatFacts) =
        rules.filter { it.offensive && has(it.effect) && it.matches(facts) }.fold(1.0) { value, rule -> value * rule.multiplier }
    fun incoming(has: (GrowthEffect) -> Boolean, facts: GrowthCombatFacts) =
        rules.filter { !it.offensive && has(it.effect) && it.matches(facts) }.fold(1.0) { value, rule -> value * rule.multiplier }

    fun facts(attacker: LivingEntity, target: LivingEntity, path: DamagePath): GrowthCombatFacts {
        fun maximum(entity: LivingEntity) = (entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0).coerceAtLeast(1.0)
        val aMax = maximum(attacker); val tMax = maximum(target)
        return GrowthCombatFacts(path,
            if (attacker.world == target.world) attacker.location.distance(target.location) else Double.POSITIVE_INFINITY,
            attacker.health / aMax, target.health / tMax, aMax, tMax,
            attacker is Mob, target is Mob, target is Player,
            attacker.isOnGround, (attacker as? Player)?.isSprinting == true,
            target.isOnGround, (target as? Player)?.isSneaking == true, target.world.time in 13000L..22999L)
    }
}
