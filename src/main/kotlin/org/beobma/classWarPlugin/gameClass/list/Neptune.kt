package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.AttackSpeedIncrease
import org.bukkit.Material
import org.bukkit.Particle
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.util.Vector

class Neptune : PlanetClass(), GameStatusHandler, OnHitHandler {
    override val classId = "neptune"
    override val name = "<gray>해왕성"
    override val rank = Rank.A
    override val classItemMaterial = Material.LAPIS_BLOCK
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private var attackSpeedStatus: NeptuneAttackSpeedStatus? = null

    override fun onBattleStart() {
        attackSpeedStatus = null
        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    attackSpeedStatus?.remove()
                    cancel()
                    return
                }
                if (!isPowerEnabled()) {
                    attackSpeedStatus?.remove()
                    attackSpeedStatus = null
                    return
                }
                val status = attackSpeedStatus?.takeIf { it in playerData.statusAbnormalitys }
                    ?: playerData.getOrCreateStatus(playerData) { NeptuneAttackSpeedStatus() }.also { attackSpeedStatus = it }
                status.applyStatus(duration = 2, powerSet = 2400)
                particles.spawn(player.location.clone().add(0.0, 0.8, 0.0), Particle.NAUTILUS, count = 2, spread = 0.4, speed = 0.02)
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 4L))
    }

    override fun onGameTimePasses() = Unit

    override fun onAttackHit(context: DamageContext) {
        if (!isPowerEnabled() || context.path != DamagePath.BASIC_ATTACK) return
        context.addDamageDealtMultiplier(0.67)
        val living = context.target.entity as? org.bukkit.entity.LivingEntity ?: return
        val before = living.velocity.clone()
        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            override fun run() {
                if (!living.isValid || living.isDead) return
                val after = living.velocity
                val knockback = after.clone().subtract(before)
                living.velocity = before.add(Vector(knockback.x * 0.5, knockback.y, knockback.z * 0.5))
            }
        }.runTaskLater(ClassWarPlugin.instance, 1L))
    }

    private class Passive : BasePassive() {
        override val name = "<bold>해왕성"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>기본 공격의 재사용 대기 시간이 사라진다.",
            "<gray>기본 공격이 입히는 피해가 33% 감소하고, 기본 공격이 밀쳐내는 거리가 50% 감소한다."
        )
    }
}
private class NeptuneAttackSpeedStatus : AttackSpeedIncrease() {
    override val name = "<blue><bold>해왕성의 파도</bold><gray>"
    override val isClassMechanic = true
    override val showPower = false
    override val showMaxPower = false
    override val showInActionBar = false
}
