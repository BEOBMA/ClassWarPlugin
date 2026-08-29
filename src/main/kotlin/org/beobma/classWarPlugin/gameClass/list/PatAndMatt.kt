package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable

private const val PAT_AND_MATT_COOLDOWN_SECONDS = 40

class PatAndMatt : GameClass() {
    override val name = "<gray>패트와 매트"
    override val rank = Rank.B
    override val classItemMaterial = Material.LEATHER
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives = emptyList<org.beobma.classWarPlugin.skill.Passive>()

    private inner class RedSkill : Skill() {
        override val name = "<bold>패트와 매트"
        override val description = listOf("<gray>6칸 내의 바라보는 적이 4초간 자신의 행동을 따라하게 만든다.")
        override val cooldown = PAT_AND_MATT_COOLDOWN_SECONDS
        private var selectedTarget: PlayerData? = null

        override fun isUseSuccess(): Boolean {
            selectedTarget = playerData.shotLaserGetEntityData(6.0, TargetType.Enemy, false) as? PlayerData
            if (selectedTarget == null) player.sendMiniMessage("<red><bold>[!] 6칸 내에 바라보는 플레이어가 없습니다.")
            return selectedTarget != null
        }

        override fun use() {
            val target = selectedTarget ?: return
            selectedTarget = null
            val status = target.entityStatus
            val originalCanMove = status.canMove
            val originalCanAttack = status.canAttack
            val originalCanSkillUse = status.canSkillUse
            status.canMove = false
            status.canAttack = false
            status.canSkillUse = false
            var previousCasterLocation = player.location.clone()
            sounds.play(player, Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, volume = 0.8f, pitch = 1.15f)
            sounds.play(target.player, Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, volume = 0.8f, pitch = 0.8f)
            playerData.trackTask(object : BukkitRunnable() {
                var tick = 0
                override fun run() {
                    if (tick >= 80 || !player.isOnline || !target.player.isOnline || playerStatus.isDead || status.isDead) {
                        status.canMove = originalCanMove
                        status.canAttack = originalCanAttack
                        status.canSkillUse = originalCanSkillUse
                        particles.spawn(target.player, Particle.POOF, count = 16, spread = 0.5, speed = 0.07)
                        cancel()
                        return
                    }
                    val casterLocation = player.location.clone()
                    val movement = casterLocation.toVector().subtract(previousCasterLocation.toVector())
                    val destination = target.player.location.clone().add(movement).apply {
                        yaw = casterLocation.yaw
                        pitch = casterLocation.pitch
                    }
                    if (destination.block.isPassable && destination.clone().add(0.0, 1.0, 0.0).block.isPassable) {
                        target.player.teleport(destination)
                    } else {
                        target.player.setRotation(casterLocation.yaw, casterLocation.pitch)
                    }
                    target.player.isSprinting = player.isSprinting
                    target.player.isSneaking = player.isSneaking
                    target.player.velocity = player.velocity
                    if (tick % 5 == 0) {
                        particles.line(player.location.add(0.0, 1.0, 0.0), target.player.location.add(0.0, 1.0, 0.0), Particle.ENCHANT, 0.45)
                    }
                    previousCasterLocation = casterLocation
                    tick++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
        }
    }
}
