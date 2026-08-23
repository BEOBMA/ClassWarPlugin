package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Stealth
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.scheduler.BukkitRunnable
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DARKNESS_SPREAD_COOLDOWN_SECONDS = 35
private const val DARKNESS_SPREAD_DURATION_TICKS = 120L
private const val DARKNESS_STEALTH_SUPPRESSION_TICKS = 60L
private const val DARKNESS_BONUS_ATTACK_DAMAGE = 2.0
private const val DARKNESS_FIRST_HIT_DAMAGE_TAKEN_MULTIPLIER = 1.5

class Darkness : GameClass(), EnvironmentalDamageHandler {
    override val name = "<gray>어둠"
    override val rank = Rank.B
    override val classItemMaterial = Material.BLACK_CONCRETE
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = listOf(Passive())

    private var artificialDarknessUntilTick = 0L

    private fun isInDarkness(): Boolean =
        player.world.fullTime < artificialDarknessUntilTick || player.eyeLocation.block.lightFromBlocks.toInt() == 0

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (!playerData.entityStatus.isAttackable || event.damage <= 0.0) return
        if (event is EntityDamageByEntityEvent) {
            val playerDriven = event.damager is Player || (event.damager as? Projectile)?.shooter is Player
            if (playerDriven) return
        }
        passives.filterIsInstance<Passive>().firstOrNull()?.handleEnvironmentalDamage(event)
    }

    private inner class RedSkill : Skill() {
        override val name = "<bold>어둠 확산"
        override val description = listOf(
            "<gray>6초 동안 빛이 있는 곳에 있더라도 빛이 없는 곳으로 간주한다."
        )
        override val cooldown = DARKNESS_SPREAD_COOLDOWN_SECONDS

        override fun use() {
            artificialDarknessUntilTick = player.world.fullTime + DARKNESS_SPREAD_DURATION_TICKS
            passives.filterIsInstance<Passive>().firstOrNull()?.refreshDarknessState()
            sounds.play(player, Sound.ENTITY_WARDEN_HEARTBEAT, volume = 0.75f, pitch = 0.55f)
            playerData.trackTask(object : BukkitRunnable() {
                var ticks = 0
                override fun run() {
                    if (ticks++ >= DARKNESS_SPREAD_DURATION_TICKS || !player.isOnline || player.isDead) {
                        passives.filterIsInstance<Passive>().firstOrNull()?.refreshDarknessState()
                        sounds.play(player, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, volume = 0.45f, pitch = 0.6f)
                        cancel()
                        return
                    }
                    if (ticks % 3 == 0) {
                        particles.spawn(
                            player.location.clone().add(0.0, 1.0, 0.0),
                            Particle.DUST,
                            Particle.DustOptions(Color.fromRGB(12, 8, 20), 1.7f),
                            org.beobma.classWarPlugin.effect.ParticleOptions.spread(7, 0.75, 0.01),
                        )
                    }
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
        }
    }

    private inner class Passive : BasePassive(), GameStatusHandler, OnHitHandler, WhenHitHandler {
        override val name = "<bold>어둠"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>빛이 없는 곳에서 {keyword:Stealth} 상태가 되며 기본 공격 피해가 2 증가한다.",
            "<gray>기본 공격을 하거나 피해를 받으면 3초 동안 {keyword:Stealth} 상태가 해제된다.",
            "<gray>{keyword:Stealth} 상태가 아닐 때 받는 피해가 50% 증가한다."
        )

        private var grantedStealth: Stealth? = null
        private var stealthSuppressedUntilTick = 0L

        override fun onBattleStart() = refreshDarknessState()

        override fun onGameTimePasses() = refreshDarknessState()

        fun refreshDarknessState() {
            val stealthSuppressed = player.world.fullTime < stealthSuppressedUntilTick
            if (isInDarkness() && !stealthSuppressed) {
                if (grantedStealth?.let { it.power == 1 && playerData.statusAbnormalitys.contains(it) } == true) return
                grantedStealth = playerData.addStatus(Stealth(), playerData) as Stealth
                grantedStealth?.applyStatus(powerSet = 1)
                particles.spawn(player, Particle.LARGE_SMOKE, count = 10, spread = 0.35, speed = 0.01)
                sounds.play(player, Sound.BLOCK_SCULK_SENSOR_CLICKING, volume = 0.35f, pitch = 0.55f)
            } else {
                removeGrantedStealth()
            }
        }

        override fun onAttackHit(context: DamageContext) {
            suppressStealth()
            if (!isInDarkness()) return
            context.addBaseDamage(DARKNESS_BONUS_ATTACK_DAMAGE)
            particles.spawn(context.target.entity, Particle.SQUID_INK, count = 6, spread = 0.25)
            sounds.play(context.target.entity, Sound.ENTITY_PHANTOM_BITE, volume = 0.55f, pitch = 0.8f)
        }

        override fun whenHit(context: DamageContext) {
            val wasStealthed = hasActiveStealth()
            suppressStealth()
            if (!wasStealthed) context.addDamageTakenMultiplier(DARKNESS_FIRST_HIT_DAMAGE_TAKEN_MULTIPLIER)
        }

        fun handleEnvironmentalDamage(event: EntityDamageEvent) {
            val wasStealthed = hasActiveStealth()
            suppressStealth()
            if (!wasStealthed) event.damage *= DARKNESS_FIRST_HIT_DAMAGE_TAKEN_MULTIPLIER
        }

        private fun suppressStealth() {
            stealthSuppressedUntilTick = player.world.fullTime + DARKNESS_STEALTH_SUPPRESSION_TICKS
            val wasStealthed = grantedStealth != null
            removeGrantedStealth()
            if (wasStealthed) {
                particles.spawn(player, Particle.LARGE_SMOKE, count = 14, spread = 0.45, speed = 0.025)
                sounds.play(player, Sound.BLOCK_SCULK_SENSOR_CLICKING, volume = 0.4f, pitch = 1.5f)
            }
            playerData.trackTask(object : BukkitRunnable() {
                override fun run() {
                    if (!player.isOnline || player.isDead) return
                    refreshDarknessState()
                }
            }.runTaskLater(ClassWarPlugin.instance, DARKNESS_STEALTH_SUPPRESSION_TICKS))
        }

        private fun removeGrantedStealth() {
            grantedStealth?.remove()
            grantedStealth = null
        }

        private fun hasActiveStealth(): Boolean =
            playerData.statusAbnormalitys.any { it is Stealth && it.power > 0 }
    }
}
