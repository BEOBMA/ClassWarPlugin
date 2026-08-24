package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.manager.DamageIndicatorManager
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.*
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.scheduler.BukkitRunnable
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID

private const val REVERSE_ZONE_COOLDOWN_SECONDS = 60
private const val REVERSE_ZONE_DURATION_TICKS = 200L
private const val REVERSE_ZONE_RADIUS = 6.0

class Reverse : GameClass(), GameStatusHandler, GameEndHandler, PlayerDeathHandler {
    override val name = "<gray>리버스"
    override val rank = Rank.A
    override val classItemMaterial = Material.JIGSAW
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = listOf(Passive())

    override fun onBattleStart() {
        removeZones(player.uniqueId)
        reverseStatuses(playerData, playerData)
    }

    override fun onGameTimePasses() {
        reverseStatuses(playerData, playerData)
        activeZones.filter { it.owner === playerData }.forEach { zone ->
            game.playerDatas.filter { candidate -> candidate !== playerData && shouldReverse(candidate) }
                .forEach { reverseStatuses(it, playerData) }
        }
    }

    override fun onGameEnd() = removeZones(player.uniqueId)
    override fun onPlayerDeath() = removeZones(player.uniqueId)

    private inner class RedSkill : Skill() {
        override val name = "<bold>반전 영역"
        override val description = listOf(
            "<gray>10초간 자신의 위치에 반전 영역을 설치한다.", "",
            "<gray>자신의 패시브 효과의 대상은 영역 안의 모든 적에게도 적용되게 된다.",
            "<gray>또한, 영역 안에서 받는 피해는 치유로 전환되고",
            "<gray>받는 치유는 피해로 전환된다."
        )
        override val cooldown = REVERSE_ZONE_COOLDOWN_SECONDS

        override fun use() {
            val center = player.location.clone()
            val zone = Zone(playerData, center, Bukkit.getCurrentTick().toLong() + REVERSE_ZONE_DURATION_TICKS)
            activeZones += zone
            sounds.play(center, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, volume = 0.9f, pitch = 0.65f)
            playerData.trackTask(object : BukkitRunnable() {
                var tick = 0L
                override fun run() {
                    if (Bukkit.getCurrentTick().toLong() >= zone.expiresAtTick || playerStatus.isDead || !player.isOnline) {
                        activeZones.remove(zone)
                        particles.spawn(center, Particle.REVERSE_PORTAL, count = 30, spread = REVERSE_ZONE_RADIUS, speed = 0.05)
                        sounds.play(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, volume = 0.65f, pitch = 1.4f)
                        cancel()
                        return
                    }
                    repeat(48) { index ->
                        val angle = (index / 48.0) * Math.PI * 2.0 + tick * 0.025
                        val point = center.clone().add(kotlin.math.cos(angle) * REVERSE_ZONE_RADIUS, 0.15,
                            kotlin.math.sin(angle) * REVERSE_ZONE_RADIUS)
                        val color = if ((index + tick.toInt()) % 2 == 0) Color.PURPLE else Color.RED
                        particles.spawn(point, Particle.DUST, Particle.DustOptions(color, 1.3f))
                    }
                    if (tick % 10L == 0L) particles.spawn(center.clone().add(0.0, 0.35, 0.0), Particle.REVERSE_PORTAL,
                        count = 18, spread = REVERSE_ZONE_RADIUS * 0.65, speed = 0.02)
                    tick += 2L
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>반전"
        override val description = listOf(
            "<gray>패시브", "", "<gray>자신이 받는 모든 부정적 상태이상은 긍정적 상태이상이 된다.",
            "<gray>자신이 받는 모든 긍정적 상태이상은 부정적 상태이상이 된다.",
            "<gray>이 효과로 반전된 상태이상은 다시 반전될 수 없다."
        )
    }

    companion object {
        private data class Zone(val owner: PlayerData, val center: Location, val expiresAtTick: Long)
        private data class Inversion(val status: StatusAbnormality, val power: Int)
        private val activeZones = mutableListOf<Zone>()
        private val reversedStatuses: MutableSet<StatusAbnormality> =
            Collections.newSetFromMap(IdentityHashMap<StatusAbnormality, Boolean>())

        fun shouldReverse(target: EntityData): Boolean {
            if (target is PlayerData && target.gameClasses.any { it is Reverse }) return true
            val now = Bukkit.getCurrentTick().toLong()
            activeZones.removeIf { it.expiresAtTick <= now }
            return activeZones.any { zone ->
                if (zone.owner.game !== target.game || zone.owner.entityStatus.isDead) return@any false
                if (target is PlayerData && !zone.owner.isEnemyOf(target)) return@any false
                HitboxUtil.intersectsSphere(target.entity.boundingBox, zone.center.toVector(), REVERSE_ZONE_RADIUS)
            }
        }

        fun invertDamageIfNeeded(context: DamageContext): Boolean {
            if (!shouldReverse(context.target) || context.damage <= 0.0) return false
            val living = context.target.entity as? LivingEntity ?: return false
            val maximum = living.getAttribute(Attribute.MAX_HEALTH)?.value ?: living.health
            val amount = context.damage.coerceAtMost((maximum - living.health).coerceAtLeast(0.0))
            if (amount > 0.0) living.health = (living.health + amount).coerceAtMost(maximum)
            context.isCancelled = true
            living.world.spawnParticle(Particle.HEART, living.boundingBox.center.toLocation(living.world), 6, 0.35, 0.45, 0.35, 0.02)
            living.world.playSound(living.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.65f)
            return true
        }

        fun invertEnvironmentalDamageIfNeeded(event: EntityDamageEvent, target: PlayerData): Boolean {
            if (!shouldReverse(target) || event.finalDamage <= 0.0) return false
            val maximum = target.player.getAttribute(Attribute.MAX_HEALTH)?.value ?: target.player.health
            target.player.health = (target.player.health + event.finalDamage).coerceAtMost(maximum)
            event.isCancelled = true
            target.player.world.spawnParticle(Particle.HEART, target.player.boundingBox.center.toLocation(target.player.world),
                6, 0.35, 0.45, 0.35, 0.02)
            return true
        }

        fun invertHealingIfNeeded(target: EntityData, amount: Double): Boolean {
            if (!shouldReverse(target) || amount <= 0.0) return false
            val living = target.entity as? LivingEntity ?: return false
            val applied = amount.coerceAtMost(living.health)
            if (applied <= 0.0) return true
            DamageIndicatorManager.show(living, applied, target.game.settings.damageIndicatorsEnabled)
            living.playHurtAnimation(0.0f)
            living.health = (living.health - applied).coerceAtLeast(0.0)
            living.world.spawnParticle(Particle.DAMAGE_INDICATOR, living.boundingBox.center.toLocation(living.world),
                7, 0.35, 0.45, 0.35, 0.03)
            living.world.playSound(living.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.45f, 1.65f)
            return true
        }

        private fun reverseStatuses(target: EntityData, caster: PlayerData) {
            reversedStatuses.removeIf { it.power <= 0 }
            target.statusAbnormalitys.toList().filter { status ->
                status.power > 0 && !status.isClassMechanic && status !in reversedStatuses
            }.forEach { original ->
                val inversion = inverseOf(original) ?: return@forEach
                val duration = original.duration
                original.remove()
                val replacement = target.addStatus(inversion.status, caster)
                reversedStatuses += replacement
                replacement.applyStatus(duration = duration, powerSet = inversion.power.coerceAtLeast(1))
                val living = target.entity as? LivingEntity
                living?.world?.spawnParticle(Particle.WITCH, living.boundingBox.center.toLocation(living.world),
                    14, 0.4, 0.55, 0.4, 0.08)
            }
        }

        private fun inverseOf(status: StatusAbnormality): Inversion? = when (status) {
            is MoveSpeedIncrease -> Inversion(MoveSpeedDecrease(), status.power)
            is MoveSpeedDecrease -> Inversion(MoveSpeedIncrease(), status.power)
            is AttackSpeedIncrease -> Inversion(AttackSpeedDecrease(), status.power)
            is AttackSpeedDecrease -> Inversion(AttackSpeedIncrease(), status.power)
            is WhenDamageReduction -> Inversion(WhenDamageIncreased(), status.power)
            is WhenDamageIncreased -> Inversion(WhenDamageReduction(), status.power)
            is Shield -> Inversion(Bleeding(), status.power.coerceAtLeast(1))
            is Bleeding -> Inversion(Shield(), (status.power * 2).coerceAtLeast(2))
            is Invincibility -> Inversion(WhenDamageIncreased(), 50)
            is Abyss -> Inversion(Brightness(), 1)
            is Brightness -> Inversion(Abyss(), 1)
            is Stealth -> Inversion(Radiation(), 1)
            is Radiation -> Inversion(Stealth(), 1)
            is Vibration, is VibrationExplosion, is BleedingLock ->
                Inversion(Shield(), (status.power * 2).coerceAtLeast(2))
            is Silence, is Snare, is Stun, is Disarm, is Freezing, is Frostbite,
            is Electrocution, is Erosion -> Inversion(MoveSpeedIncrease(), 25)
            else -> null
        }

        private fun removeZones(ownerId: UUID) {
            activeZones.removeIf { it.owner.uniqueId == ownerId }
        }
    }
}
