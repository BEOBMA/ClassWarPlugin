package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.CheckpointStatus
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable

// 밸런스 조정 상수
private const val TIME_MANIPULATOR_CHECKPOINT_COOLDOWN_SECONDS = 35
private const val TIME_MANIPULATOR_REWIND_COOLDOWN_SECONDS = 1
private const val TIME_MANIPULATOR_CHECKPOINT_DURATION_SECONDS = 15
private const val TIME_MANIPULATOR_DEATH_THRESHOLD_HEALTH = 1.0
private const val TIME_MANIPULATOR_PARADOX_HEALTH_MULTIPLIER = 0.5

class TimeManiqulator : GameClass() {
    override val classId = "time-maniqulator"
    override val name = "<gray>시간 조작자"
    override val rank = Rank.A
    override val classItemMaterial = Material.CLOCK

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private fun saveCheckpoint() {
        playerData.getStatus<CheckpointStatus>()?.remove()
        val status = CheckpointStatus(player.location.clone(), player.health)
        playerData.addStatus(status, playerData)
        status.applyStatus(duration = TIME_MANIPULATOR_CHECKPOINT_DURATION_SECONDS, powerSet = 1)
        val savedLocation = status.savedLocation.clone()
        particles.spawn(savedLocation.clone().add(0.0, 1.0, 0.0), Particle.FLASH, count = 1)
        particles.spawn(savedLocation.clone().add(0.0, 1.0, 0.0), Particle.PORTAL, count = 20, spread = 0.7, speed = 0.12)
        sounds.play(player, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, pitch = 1.4f)
        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            var tick = 0
            override fun run() {
                if (playerData.getStatus<CheckpointStatus>() !== status) {
                    cancel()
                    return
                }
                val base = savedLocation.clone().add(0.0, 0.08, 0.0)
                particles.circle(base, Particle.END_ROD, 1.15, 18)
                particles.line(base, base.clone().add(0.0, 2.2, 0.0), Particle.REVERSE_PORTAL, spacing = 0.35)
                if (tick++ % 4 == 0) {
                    particles.spawn(base.clone().add(0.0, 1.1, 0.0), Particle.ENCHANT, count = 8, spread = 0.65, speed = 0.02)
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 5L))
    }

    private fun restoreCheckpoint(healthMultiplier: Double = 1.0): Boolean {
        val saved = playerData.getStatus<CheckpointStatus>() ?: return false
        saved.remove()
        val from = player.location.clone()
        player.teleport(saved.savedLocation)
        val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        player.health = (saved.savedHealth * healthMultiplier).coerceIn(1.0, maxHealth)
        particles.spawn(from, Particle.REVERSE_PORTAL, count = 35, spread = 0.7, speed = 0.15)
        particles.spawn(player, Particle.REVERSE_PORTAL, count = 35, spread = 0.7, speed = 0.15)
        sounds.play(player, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, pitch = 1.6f)
        return true
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "time-maniqulator/red-skill"
        override val name = "<bold>체크포인트"
        override val description = listOf(
            "<gray>현재 위치와 체력을 15초간 {keyword:Checkpoint}로 저장한다.",
            "",
            "<gray>{keyword:Checkpoint}가 유지되는 동안 회귀를 사용하여 저장한 위치와 체력으로 돌아갈 수 있다."
        )
        override val cooldown = TIME_MANIPULATOR_CHECKPOINT_COOLDOWN_SECONDS

        override fun use(): Boolean {
            saveCheckpoint()
            return true
        }
    }

    private inner class OrangeSkill : Skill(), org.beobma.classWarPlugin.skill.MovementSkill {
        override val definitionId = "time-maniqulator/orange-skill"
        override val name = "<bold>회귀"
        override val description = listOf(
            "<gray>저장된 {keyword:Checkpoint}가 있을 때에만 사용할 수 있다.",
            "",
            "<gray>{keyword:Checkpoint}를 불러온다.",
            "<gray>사용 후 {keyword:Checkpoint}는 제거된다."
        )
        override val cooldown = TIME_MANIPULATOR_REWIND_COOLDOWN_SECONDS

        override fun use(): Boolean {
            restoreCheckpoint()
            return true
        }

        override fun isUseSuccess(): Boolean {
            if (playerData.getStatus<CheckpointStatus>() != null) return true
            player.sendMiniMessage("<red><bold>[!] 저장된 체크포인트가 없습니다.")
            return false
        }
    }

    private inner class Passive : BasePassive(), WhenHitHandler {
        override val name = "<bold>시간 역설"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>체력이 1 미만으로 내려가는 피해를 받을 때, 사망을 {keyword:Invalidity}로 하고 {keyword:Checkpoint}를 불러온다.",
            "<gray>이 효과로 불러온 경우 체력을 불러오는 효과가 50%로 감소한다.",
            "<gray>이후 {keyword:Checkpoint}는 제거된다."
        )

        override fun whenHit(context: DamageContext) {
            if (playerData.getStatus<CheckpointStatus>() == null ||
                player.health - context.damage >= TIME_MANIPULATOR_DEATH_THRESHOLD_HEALTH
            ) return
            context.isCancelled = true
            restoreCheckpoint(TIME_MANIPULATOR_PARADOX_HEALTH_MULTIPLIER)
        }
    }
}
