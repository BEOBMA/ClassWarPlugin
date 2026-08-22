package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.PlayerManager.heal
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.*
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

class Warlock : GameClass() {
    override val name = "<gray>워락"
    override val rank = Rank.B
    override val classItemMaterial = Material.GRAY_GLAZED_TERRACOTTA

    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private val markedUntil = mutableMapOf<UUID, Long>()

    private inner class RedSkill : Skill() {
        override val name = "<bold>역병의 낙인"
        override val description = listOf(
            "<dark_red><bold>체력을 5 소모</bold><gray>하고 사용할 수 있다.",
            "",
            "<gray>8칸 내의 바라보는 적에게 8초간 역병의 낙인을 새긴다.",
            "",
            "<gray>역병의 낙인이 새겨진 적은 워락에게 받는 피해가 10% 증가하고",
            "<gray>자신과 주변 3칸 이내의 적은 매초 1의 피해를 입는다.",
        )
        override val cooldown = 20

        override fun use() {
            val target = playerData.shotLaserGetEntityData(8.0, TargetType.Enemy, false) ?: return
            player.health = (player.health - 5.0).coerceAtLeast(1.0)
            markedUntil[target.entity.uniqueId] = player.world.fullTime + 160L
            particles.line(player.eyeLocation, target.entity.location.add(0.0, target.entity.height / 2.0, 0.0), Particle.WITCH, 0.3)
            sounds.play(target.entity, Sound.ENTITY_WITHER_SPAWN, volume = 0.42f, pitch = 1.5f)
            var seconds = 0
            playerData.trackTask(object : BukkitRunnable() {
                override fun run() {
                    if (seconds++ >= 8 || markedUntil[target.entity.uniqueId]?.let { it <= player.world.fullTime } != false) {
                        markedUntil.remove(target.entity.uniqueId)
                        cancel(); return
                    }
                    target.damage(1.0, DamageType.StatusAbnormality, playerData)
                    playerData.radius(target.entity.location, TargetType.Enemy, 3.0, false)
                        .filter { it.entity.uniqueId != target.entity.uniqueId }
                        .forEach { it.damage(1.0, DamageType.StatusAbnormality, playerData) }
                    particles.circle(target.entity.location, Particle.WITCH, 3.0, 24)
                }
            }.runTaskTimer(ClassWarPlugin.instance, 20L, 20L))
        }

        override fun isUseSuccess(): Boolean {
            if (player.health <= 5.0) {
                player.sendMiniMessage("<red><bold>[!] 체력이 부족합니다.")
                return false
            }
            if (playerData.shotLaserGetEntityData(8.0, TargetType.Enemy, false) != null) return true
            player.sendMiniMessage("<red><bold>[!] 바라보는 적이 없습니다.")
            return false
        }
    }

    private inner class Passive : BasePassive(), OnHitHandler {
        override val name = "<bold>저편의 계약"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>역병의 낙인이 새겨진 적에게 피해를 입히면 입힌 피해의 50% 만큼 체력을 회복한다."
        )

        override fun onHit(context: DamageContext) {
            if ((markedUntil[context.target.entity.uniqueId] ?: 0L) <= player.world.fullTime) return
            context.addDamageDealtMultiplier(1.1)
            playerData.heal(context.damage * 0.5, DamageType.Normal, playerData)
            particles.spawn(player, Particle.HEART, count = 2, spread = 0.25)
        }
    }
}
