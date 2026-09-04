package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private const val THUNDERCLAP_MAX_CHARGES = 6
private const val THUNDERCLAP_RECHARGE_SECONDS = 12

class ThunderclapFlash : GameClass(), GameStatusHandler {
    override val classId = "thunderclap-flash"
    override val name = "<gray>벽력일섬"
    override val rank = Rank.A
    override val classItemMaterial = Material.GOLDEN_HORSE_ARMOR
    private val flashSkill = RedSkill()
    override var skills: List<Skill> = listOf(flashSkill)
    override var passives = emptyList<org.beobma.classWarPlugin.skill.Passive>()
    private var charges = THUNDERCLAP_MAX_CHARGES
    private var rechargeSeconds = 0
    private val hitHistory = mutableMapOf<UUID, Pair<Long, Int>>()

    override fun onBattleStart() {
        charges = THUNDERCLAP_MAX_CHARGES
        rechargeSeconds = 0
        hitHistory.clear()
        refreshStatus()
    }

    override fun onGameTimePasses() {
        if (charges < THUNDERCLAP_MAX_CHARGES && ++rechargeSeconds >= THUNDERCLAP_RECHARGE_SECONDS) {
            rechargeSeconds = 0
            charges++
            sounds.playTo(player, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, volume = 0.45f, pitch = 1.75f)
        }
        refreshStatus()
    }

    private fun refreshStatus() {
        playerData.getOrCreateStatus(playerData) { ChargeStatus() }.updatePower(charges)
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "thunderclap-flash/red-skill"
        override val name = "<bold>벽력일섬"
        override val description = listOf(
            "<gray>최대 6회 충전되는 충전형 스킬.", "",
            "<gray>6칸 내의 바라보는 적의 6칸 뒤로 순간이동한다. (벽이 있다면 벽에서 멈춘다.)",
            "<gray>이때 적은 4의 피해를 입는다.",
            "<gray>6초 안에 이 스킬로 여러번 피해를 입으면 피해량이 50%씩 감소한다. (최소 1의 피해를 보장함)"
        )
        override val cooldown = 0
        private var selectedTarget: EntityData? by requestValue { null }

        override fun isUseSuccess(): Boolean {
            if (charges <= 0) {
                player.sendMiniMessage("<red><bold>[!] 충전 횟수가 없습니다.")
                return false
            }
            selectedTarget = playerData.shotLaserGetEntityData(6.0, TargetType.Enemy, false)
            if (selectedTarget == null) player.sendMiniMessage("<red><bold>[!] 6칸 내에 바라보는 적이 없습니다.")
            return selectedTarget != null
        }

        override fun use(): Boolean {
            val target = selectedTarget ?: return false
            selectedTarget = null
            charges--
            rechargeSeconds = 0
            refreshStatus()

            val targetCenter = target.entity.boundingBox.center
            var direction = targetCenter.clone().subtract(player.boundingBox.center).setY(0.0)
            if (direction.lengthSquared() < 1.0E-8) direction = player.location.direction.setY(0.0)
            direction.normalize()
            val rayStart = targetCenter.toLocation(player.world).add(0.0, 0.2, 0.0)
            val dashRange = ClassBalanceManager.scaleRange(playerData, 6.0)
            val obstruction = player.world.rayTraceBlocks(rayStart, direction, dashRange)
            val distance = ((obstruction?.hitPosition?.distance(targetCenter) ?: dashRange) - 0.8).coerceAtLeast(0.6)
            val destination = target.entity.location.clone().add(direction.clone().multiply(distance)).apply {
                yaw = player.location.yaw
                pitch = player.location.pitch
            }
            val from = player.location.clone()
            player.teleport(destination)

            val now = game.combatTick
            val previous = hitHistory[target.entity.uniqueId]
            val count = if (previous != null && now - previous.first <= 120L) previous.second else 0
            val damage = (4.0 * 0.5.pow(count)).coerceAtLeast(1.0)
            hitHistory[target.entity.uniqueId] = now to (count + 1)
            target.damage(damage, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)

            renderThunderclap(from, destination, target.entity.boundingBox.center.toLocation(player.world))
            sounds.play(target.entity, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, volume = 1.05f, pitch = 1.55f)
            sounds.play(target.entity, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, volume = 0.52f, pitch = 1.8f)
            sounds.play(player, Sound.ENTITY_BREEZE_SHOOT, volume = 0.9f, pitch = 1.65f)
            sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, volume = 1.0f, pitch = 1.45f)
            return true
        }
    }

    private fun renderThunderclap(from: Location, destination: Location, impact: Location) {
        val start = from.clone().add(0.0, 0.9, 0.0)
        val end = destination.clone().add(0.0, 0.9, 0.0)
        val delta = end.toVector().subtract(start.toVector())
        val distance = delta.length().coerceAtLeast(0.01)
        val forward = delta.clone().multiply(1.0 / distance)
        var right = Vector(-forward.z, 0.0, forward.x)
        if (right.lengthSquared() < 1.0E-8) right = Vector(1.0, 0.0, 0.0) else right.normalize()
        val up = right.clone().crossProduct(forward).normalize()
        val gold = Particle.DustOptions(Color.fromRGB(255, 196, 22), 1.65f)
        val whiteGold = Particle.DustOptions(Color.fromRGB(255, 245, 170), 1.15f)
        val orange = Particle.DustOptions(Color.fromRGB(255, 115, 12), 1.25f)

        particles.spawn(from.clone().add(0.0, 0.12, 0.0), Particle.DUST_PLUME, count = 38, spread = 0.65, speed = 0.16)
        particles.spawn(start, Particle.FLASH, count = 1)
        particles.line(start, end, Particle.DUST, gold, 0.09, ParticleOptions(force = true))
        particles.line(start, end, Particle.END_ROD, 0.14, ParticleOptions(force = true))
        particles.line(start.clone().add(right.clone().multiply(0.22)), end.clone().add(right.clone().multiply(0.22)),
            Particle.DUST, whiteGold, 0.15, ParticleOptions(force = true))
        particles.line(start.clone().subtract(right.clone().multiply(0.22)), end.clone().subtract(right.clone().multiply(0.22)),
            Particle.DUST, orange, 0.15, ParticleOptions(force = true))

        repeat(5) { arc ->
            var previous = start.clone()
            repeat(14) { segment ->
                val progress = (segment + 1) / 14.0
                val taper = sin(PI * progress)
                val alternating = if ((segment + arc) % 2 == 0) 1.0 else -1.0
                val sideways = alternating * taper * Random.nextDouble(0.18, 0.62)
                val vertical = taper * Random.nextDouble(-0.34, 0.38)
                val point = start.clone().add(delta.clone().multiply(progress))
                    .add(right.clone().multiply(sideways))
                    .add(up.clone().multiply(vertical))
                particles.line(previous, point, Particle.ELECTRIC_SPARK, 0.1, ParticleOptions(force = true))
                if (segment % 2 == 0) {
                    particles.line(previous, point, Particle.DUST, whiteGold, 0.18, ParticleOptions(force = true))
                }
                previous = point
            }
        }

        repeat(3) { ring ->
            val radius = 0.55 + ring * 0.42
            repeat(24) { index ->
                val angle = 2.0 * PI * index / 24.0
                val point = impact.clone()
                    .add(right.clone().multiply(cos(angle) * radius))
                    .add(up.clone().multiply(sin(angle) * radius))
                particles.spawn(point, Particle.DUST, if (ring == 2) orange else gold, ParticleOptions(force = true))
            }
        }
        particles.spawn(impact, Particle.FLASH, count = 1)
        particles.spawn(impact, Particle.ELECTRIC_SPARK, count = 70, spread = 1.05, speed = 0.24, force = true)
        particles.spawn(impact, Particle.CRIT, count = 42, spread = 0.8, speed = 0.18)
        particles.spawn(impact, Particle.SWEEP_ATTACK, count = 5, spread = 0.65, speed = 0.08)

        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            var tick = 0
            override fun run() {
                if (tick >= 7 || !player.isOnline) {
                    cancel()
                    return
                }
                val fade = 1.0 - tick / 7.0
                repeat((24 * fade).toInt().coerceAtLeast(5)) { index ->
                    val progress = ((index * 0.61803398875 + tick * 0.137) % 1.0)
                    val point = start.clone().add(delta.clone().multiply(progress))
                        .add(right.clone().multiply(Random.nextDouble(-0.28, 0.28) * fade))
                    if ((index + tick) % 3 == 0) {
                        particles.spawn(point, Particle.ELECTRIC_SPARK, ParticleOptions(force = true))
                    } else {
                        particles.spawn(point, Particle.DUST, whiteGold, ParticleOptions(force = true))
                    }
                }
                particles.spawn(impact, Particle.ELECTRIC_SPARK, count = (18 * fade).toInt(), spread = 0.7, speed = 0.08, force = true)
                tick++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L))
    }

    private class ChargeStatus : StatusAbnormality() {
        override val name = "<yellow><bold>벽력 충전</bold><gray>"
        override val description = listOf("<gray>벽력일섬의 남은 충전 횟수이다.")
        override val canRemove = false
        override val isClassMechanic = true
        override var power = THUNDERCLAP_MAX_CHARGES
        override var maxPower: Int? = THUNDERCLAP_MAX_CHARGES
        override var duration: Int? = null
    }
}
