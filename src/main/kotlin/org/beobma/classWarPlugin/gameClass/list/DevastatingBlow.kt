package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.isActuallyGrounded
import org.beobma.classWarPlugin.skill.MovementSkill
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.status.list.Stun
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector

class DevastatingBlow : GameClass(), EnvironmentalDamageHandler, GameEndHandler, PlayerDeathHandler {
    override val name = "<gray>파멸의 일격"
    override val rank = Rank.A
    override val classItemMaterial = Material.POINTED_DRIPSTONE
    private val blowSkill = RedSkill()
    override var skills: List<Skill> = listOf(blowSkill)
    override var passives: List<BasePassive> = emptyList()
    private var active = false
    private var descending = false
    private var savedGravity = true
    private var savedAllowFlight = false
    private var savedFlying = false
    private var savedWalkSpeed = 0.2f
    private var savedFlySpeed = 0.1f
    private var descentX = 0.0
    private var descentZ = 0.0
    private var groundCenter: org.bukkit.Location? = null
    private var stealth: Stealth? = null
    private var hoverTask: BukkitTask? = null
    private var cooldownItem: ItemStack? = null

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (active && event.cause == EntityDamageEvent.DamageCause.FALL) {
            event.isCancelled = true
            player.fallDistance = 0f
        }
    }
    override fun onGameEnd() = cleanup()
    override fun onPlayerDeath() = cleanup()

    private inner class RedSkill : Skill(), MovementSkill {
        override val name = "<bold>파멸의 일격"
        override val description = listOf(
            "<gray>10초간 하늘 높은 곳으로 올라가 자유롭게 이동하며 {keyword:Stealth} 상태가 된다.",
            "<gray>자신을 따라다니는 지름 10칸 범위의 입자가 아래의 땅에 생성된다.",
            "<gray>이 스킬을 재사용하거나 10초가 지나면 그 위치에 고정되고, 2.5초 후 땅으로 떨어진다.",
            "<gray>떨어질 때 그 위치에 있던 모든 적은 6의 피해를 입고 1초간 {keyword:Stun}한다."
        )
        override val cooldown = 80
        override val isOnOffSKill = true
        override fun use() {
            if (active && !descending) {
                beginDescent(automatic = false)
                return
            }
            if (active) return
            cooldownItem = player.inventory.itemInMainHand.clone()
            beginAscension()
            multiplyCurrentCooldown(0.0)
        }
        override fun isUseSuccess(): Boolean = !descending
    }

    private fun beginAscension() {
        active = true
        descending = false
        savedGravity = player.hasGravity()
        savedAllowFlight = player.allowFlight
        savedFlying = player.isFlying
        savedWalkSpeed = player.walkSpeed
        savedFlySpeed = player.flySpeed
        val destination = player.location.clone().apply {
            y = (y + 22.0).coerceAtMost((world.maxHeight - 5).toDouble())
        }
        player.teleport(destination)
        player.velocity = Vector()
        player.setGravity(false)
        player.allowFlight = true
        player.isFlying = true
        player.flySpeed = maxOf(savedFlySpeed, 0.08f)
        player.fallDistance = 0f
        groundCenter = findGroundBelow()
        stealth = (playerData.addStatus(Stealth(), playerData) as Stealth).also { it.applyStatus(duration = 10, powerSet = 1) }
        particles.spawn(player, Particle.REVERSE_PORTAL, count = 75, spread = 0.8, speed = 0.16)
        sounds.play(player, Sound.ENTITY_BREEZE_WIND_BURST, volume = 1.0f, pitch = 0.65f)
        hoverTask = playerData.trackTask(object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (!active || descending || playerStatus.isDead) {
                    cancel()
                    return
                }
                if (ticks % 2 == 0) {
                    groundCenter = findGroundBelow()
                    renderTargetCircle()
                }
                particles.spawn(player.location.clone().add(0.0, -0.4, 0.0), Particle.REVERSE_PORTAL, count = 3, spread = 0.35, speed = 0.025)
                if (ticks >= 200) {
                    beginDescent(automatic = true)
                    cancel()
                    return
                }
                ticks++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
    }

    private fun beginDescent(automatic: Boolean) {
        if (!active || descending) return
        descending = true
        hoverTask?.cancel(); hoverTask = null
        stealth?.remove(); stealth = null
        descentX = player.location.x
        descentZ = player.location.z
        groundCenter = findGroundBelow()
        player.isFlying = false
        player.allowFlight = false
        player.setGravity(false)
        player.walkSpeed = 0f
        player.flySpeed = 0f
        player.velocity = Vector()
        sounds.play(player, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, volume = 0.9f, pitch = 0.58f)
        playerData.trackTask(object : BukkitRunnable() {
            var ticks = 0
            var falling = false
            override fun run() {
                if (!active || playerStatus.isDead) {
                    cleanup()
                    cancel()
                    return
                }
                lockDescentPosition(falling)
                renderTargetCircle()
                if (!falling && ticks >= 50) {
                    falling = true
                    player.setGravity(true)
                    player.velocity = Vector(0.0, -2.3, 0.0)
                    sounds.play(player, Sound.ENTITY_IRON_GOLEM_ATTACK, volume = 0.9f, pitch = 0.55f)
                }
                if (falling) {
                    lockDescentPosition(true)
                    particles.spawn(player, Particle.LARGE_SMOKE, count = 8, spread = 0.35, speed = 0.04)
                    if (player.isActuallyGrounded() || ticks >= 130) {
                        impact()
                        if (automatic) CooldownManager.setCooldown(
                            player, blowSkill, cooldownItem ?: ItemStack(Material.RED_DYE), blowSkill.cooldown * 20,
                        )
                        cancel()
                        return
                    }
                }
                ticks++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
    }

    private fun lockDescentPosition(falling: Boolean) {
        val location = player.location
        if (kotlin.math.abs(location.x - descentX) > 0.001 || kotlin.math.abs(location.z - descentZ) > 0.001) {
            player.teleport(location.clone().apply {
                x = descentX
                z = descentZ
            })
        }
        player.velocity = if (falling) {
            Vector(0.0, player.velocity.y.coerceAtMost(-1.45), 0.0)
        } else {
            Vector()
        }
    }

    private fun impact() {
        val center = player.location.clone()
        playerData.radius(center, TargetType.Enemy, 5.0, false).forEach { target ->
            target.damage(6.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
            target.getOrCreateStatus(playerData) { Stun() }.applyStatus(duration = 1, powerSet = 1)
        }
        particles.spawn(center, Particle.EXPLOSION, count = 4, spread = 1.0, speed = 0.08)
        particles.spawn(center, Particle.CLOUD, count = 85, spread = 2.5, speed = 0.18)
        particles.circle(center.clone().add(0.0, 0.15, 0.0), Particle.SOUL_FIRE_FLAME, 5.0, 72)
        sounds.play(center, Sound.ENTITY_GENERIC_EXPLODE, volume = 1.2f, pitch = 0.55f)
        cleanup()
    }

    private fun renderTargetCircle() {
        val center = groundCenter ?: findGroundBelow().also { groundCenter = it }
        particles.circle(center.clone().add(0.0, 0.12, 0.0), Particle.SOUL_FIRE_FLAME, 5.0, 56)
        particles.circle(center.clone().add(0.0, 0.16, 0.0), Particle.END_ROD, 4.5, 42)
    }

    private fun findGroundBelow(): org.bukkit.Location {
        val ray = player.world.rayTraceBlocks(
            player.location.clone().add(0.0, 0.2, 0.0), Vector(0.0, -1.0, 0.0),
            (player.world.maxHeight - player.world.minHeight).toDouble(), FluidCollisionMode.ALWAYS, true,
        )
        return ray?.hitPosition?.toLocation(player.world)?.add(0.0, 0.08, 0.0) ?: player.location.clone()
    }

    private fun cleanup() {
        if (!active && !descending && hoverTask == null && stealth == null && groundCenter == null) return
        active = false
        descending = false
        hoverTask?.cancel(); hoverTask = null
        stealth?.remove(); stealth = null
        if (player.isOnline) {
            player.setGravity(savedGravity)
            player.allowFlight = savedAllowFlight
            player.isFlying = savedFlying && savedAllowFlight
            player.walkSpeed = savedWalkSpeed
            player.flySpeed = savedFlySpeed
            player.fallDistance = 0f
        }
        groundCenter = null
    }
}
