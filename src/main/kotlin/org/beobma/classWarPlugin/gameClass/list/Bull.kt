package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.MoveSpeedIncrease
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.min

private const val BULL_MAX_MOVE_SPEED_BONUS_PERCENT = 200

class Bull : GameClass(), GameStatusHandler {
    override val classId = "bull"
    override val name = "<gray>황소"
    override val rank = Rank.B
    override val classItemMaterial = Material.COPPER_NAUTILUS_ARMOR
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private var chargeTicks = 0
    private var inactiveTicks = 0

    override fun onBattleStart() {
        chargeTicks = 0
        inactiveTicks = 0
        var lastLocation = player.location.clone()
        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    cancel()
                    return
                }
                if (game.isPaused) {
                    lastLocation = player.location.clone()
                    return
                }
                val current = player.location
                val dx = current.x - lastLocation.x
                val dz = current.z - lastLocation.z
                val moved = dx * dx + dz * dz > 0.000001
                val horizontalVelocity = player.velocity.x * player.velocity.x + player.velocity.z * player.velocity.z
                val activelyRunning = player.isSprinting && (moved || horizontalVelocity > 0.0004)
                lastLocation = current.clone()
                if (!activelyRunning) {
                    inactiveTicks++
                    if (inactiveTicks < 12) {
                        if (chargeTicks >= 60) {
                            val currentPower = ((chargeTicks - 60) / 3 + 10)
                                .coerceAtMost(BULL_MAX_MOVE_SPEED_BONUS_PERCENT)
                            playerData.getOrCreateStatus(playerData) { BullChargeSpeedStatus() }
                                .applyStatus(duration = 2, powerSet = currentPower)
                        }
                        return
                    }
                    resetCharge()
                    return
                }
                inactiveTicks = 0
                chargeTicks++
                if (chargeTicks < 60) return
                val speedPower = ((chargeTicks - 60) / 3 + 10)
                    .coerceAtMost(BULL_MAX_MOVE_SPEED_BONUS_PERCENT)
                playerData.getOrCreateStatus(playerData) { BullChargeSpeedStatus() }
                    .applyStatus(duration = 2, powerSet = speedPower)
                if (chargeTicks % 4 == 0) {
                    particles.spawn(player.location.clone().add(0.0, 0.15, 0.0), Particle.CLOUD, count = 4, spread = 0.25, speed = 0.035)
                }
                val target = playerData.radius(player.location, TargetType.Enemy, 1.45, false, hitAttackableObjects = true)
                    .firstOrNull { player.boundingBox.expand(0.35).overlaps(it.entity.boundingBox) }
                    ?: return
                val horizontalSpeed = Vector(player.velocity.x, 0.0, player.velocity.z).length()
                val damage = min(10.0, 3.0 + (chargeTicks - 60) / 22.0 + horizontalSpeed * 2.5)
                target.damage(damage, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
                (target.entity as? LivingEntity)?.let { living ->
                    val push = Vector(dx, 0.0, dz).let { if (it.lengthSquared() > 1.0E-6) it.normalize() else player.location.direction.setY(0).normalize() }
                    living.velocity = push.multiply(1.15 + damage * 0.07).setY(0.38)
                }
                particles.spawn(target.entity, Particle.CRIT, count = 34, spread = 0.55, speed = 0.18)
                sounds.play(target.entity, Sound.ENTITY_RAVAGER_ATTACK, volume = 0.9f, pitch = 0.78f)
                resetCharge()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L))
    }

    override fun onGameTimePasses() = Unit

    private fun resetCharge() {
        inactiveTicks = 0
        if (chargeTicks == 0) return
        chargeTicks = 0
        playerData.statusAbnormalitys.filterIsInstance<BullChargeSpeedStatus>().forEach { it.remove() }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>교통사고"
        override val description = listOf(
            "<gray>패시브", "", "<gray>달리기를 3초 이상 지속하면 이동 속도가 점점 빨라진다.",
            "<gray>멈추거나 적에게 충돌하면 이동 속도가 원래대로 돌아온다.",
            "<gray>충돌한 적에게는 이동 속도에 비례하여 최대 10의 피해를 입히고 밀쳐낸다."
        )
    }
}

private class BullChargeSpeedStatus : MoveSpeedIncrease() {
    override val name = "<gold><bold>황소 돌진</bold><gray>"
    override val isClassMechanic = true
}
