package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.handler.StatusOnHitHandler
import org.beobma.classWarPlugin.status.list.AttackSpeedIncrease
import org.beobma.classWarPlugin.status.list.MoveSpeedIncrease
import org.beobma.classWarPlugin.util.HitboxUtil
import org.beobma.classWarPlugin.util.PlayerNavigation
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Luna : PlanetClass(), GameStatusHandler {
    override val name = "<gray>달"
    override val rank = Rank.B
    override val classItemMaterial = Material.COBBLESTONE
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())

    private var lightLocation: Location? = null
    private var nextLightTick = 0L
    private var attackPower = 0
    private var attackSpeedPower = 0
    private var moveSpeedPower = 0
    private var attackStatus: LunarAttackPowerStatus? = null
    private var attackSpeedStatus: LunarAttackSpeedStatus? = null
    private var moveSpeedStatus: LunarMoveSpeedStatus? = null

    override fun onBattleStart() {
        lightLocation = null
        nextLightTick = 0L
        attackPower = 0
        attackSpeedPower = 0
        moveSpeedPower = 0
        playerData.trackTask(object : BukkitRunnable() {
            var tick = 0
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    removeAppliedStats()
                    cancel()
                    return
                }
                if (!isPowerEnabled()) {
                    lightLocation = null
                    removeAppliedStats()
                    return
                }
                restoreAppliedStats()
                val now = player.world.fullTime
                val current = lightLocation
                if (current != null && (current.world != player.world || current.distanceSquared(player.location) > 196.0)) {
                    lightLocation = null
                    nextLightTick = now
                }
                if (lightLocation == null && now >= nextLightTick) {
                    lightLocation = chooseLightLocation()
                    lightLocation?.let {
                        particles.spawn(it.clone().add(0.0, 1.2, 0.0), Particle.FLASH, count = 1)
                        sounds.play(it, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume = 0.55f, pitch = 1.75f)
                    }
                }
                lightLocation?.let { light ->
                    renderLight(light, tick)
                    if (HitboxUtil.intersectsSphere(player.boundingBox, light.toVector(), 0.75)) collectLight(light)
                }
                tick += 2
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }

    override fun onGameTimePasses() = Unit

    private fun chooseLightLocation(): Location {
        repeat(28) {
            val angle = Random.nextDouble(0.0, Math.PI * 2.0)
            val distance = Random.nextDouble(3.0, 8.0)
            val candidate = player.location.clone().add(cos(angle) * distance, 0.0, sin(angle) * distance)
            val node = PlayerNavigation.nearestNode(player.world, candidate, 4) ?: return@repeat
            val location = PlayerNavigation.displayLocation(player.world, node)
            if (player.world.worldBorder.isInside(location)) return location
        }
        return player.location.clone()
    }

    private fun renderLight(light: Location, tick: Int) {
        val white = Particle.DustOptions(Color.fromRGB(235, 242, 255), 1.25f)
        repeat(6) { index ->
            val y = index * 0.65
            particles.spawn(light.clone().add(0.0, y, 0.0), Particle.DUST, white)
        }
        if (tick % 6 == 0) {
            particles.circle(light.clone().add(0.0, 0.15, 0.0), Particle.END_ROD, 0.72, 16)
            particles.spawn(light.clone().add(0.0, 1.1, 0.0), Particle.ENCHANT, count = 8, spread = 0.38, speed = 0.015)
        }
    }

    private fun collectLight(light: Location) {
        val amount = if (player.world.time in 13000L..23000L) 2 else 1
        when (Random.nextInt(3)) {
            0 -> {
                attackPower += amount
                player.sendMiniMessage("<white><bold>달빛:</bold> <gray>공격력이 ${amount}% 상승했습니다.")
            }
            1 -> {
                attackSpeedPower += amount
                player.sendMiniMessage("<white><bold>달빛:</bold> <gray>공격 속도가 ${amount}% 상승했습니다.")
            }
            else -> {
                moveSpeedPower += amount
                player.sendMiniMessage("<white><bold>달빛:</bold> <gray>이동 속도가 ${amount}% 상승했습니다.")
            }
        }
        restoreAppliedStats()
        particles.spawn(light, Particle.TOTEM_OF_UNDYING, count = 28, spread = 0.55, speed = 0.09)
        sounds.play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume = 0.65f, pitch = 1.65f)
        lightLocation = null
        nextLightTick = player.world.fullTime + 200L
    }

    private fun restoreAppliedStats() {
        if (attackPower > 0) {
            attackStatus = playerData.getOrCreateStatus(playerData) { LunarAttackPowerStatus() }
                .also { it.applyStatus(powerSet = attackPower) }
        }
        if (attackSpeedPower > 0) {
            attackSpeedStatus = playerData.getOrCreateStatus(playerData) { LunarAttackSpeedStatus() }
                .also { it.applyStatus(powerSet = attackSpeedPower) }
        }
        if (moveSpeedPower > 0) {
            moveSpeedStatus = playerData.getOrCreateStatus(playerData) { LunarMoveSpeedStatus() }
                .also { it.applyStatus(powerSet = moveSpeedPower) }
        }
    }

    private fun removeAppliedStats() {
        listOf(attackStatus, attackSpeedStatus, moveSpeedStatus).forEach { status ->
            if (status != null && status in playerData.statusAbnormalitys) status.remove()
        }
        attackStatus = null
        attackSpeedStatus = null
        moveSpeedStatus = null
    }

    private class Passive : BasePassive() {
        override val name = "<bold>달"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>자신 주변에 지속적으로 빛이 비춰진다.",
            "<gray>비춰진 빛에 닿으면 공격력, 공격 속도, 이동 속도 중 무작위 스탯이 상승한다.",
            "<gray>밤에는 효과가 강화되어 스탯 상승폭이 증가한다."
        )
    }
}
private class LunarAttackPowerStatus : StatusAbnormality(), StatusOnHitHandler {
    override val name = "<white><bold>달빛 공격력</bold><gray>"
    override val description = listOf("<gray>달빛으로 영구 상승한 공격력이다.")
    override val canRemove = true
    override val isClassMechanic = true
    override val showInActionBar = false
    override var power = 0
    override var maxPower: Int? = null
    override var duration: Int? = null

    override fun onAttackHit(context: DamageContext) {
        context.addDamageDealtMultiplier(1.0 + power / 100.0)
    }
}

private class LunarAttackSpeedStatus : AttackSpeedIncrease() {
    override val name = "<white><bold>달빛 공격 속도</bold><gray>"
    override val isClassMechanic = true
    override val showInActionBar = false
}

private class LunarMoveSpeedStatus : MoveSpeedIncrease() {
    override val name = "<white><bold>달빛 이동 속도</bold><gray>"
    override val isClassMechanic = true
    override val showInActionBar = false
}
