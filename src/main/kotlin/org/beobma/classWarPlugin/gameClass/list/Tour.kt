package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val TOUR_START_COOLDOWN_SECONDS = 60
private const val TOUR_TARGET_RANGE = 6.0
private const val TOUR_MIN_VISITS = 8
private const val TOUR_VISIT_INTERVAL_TICKS = 60L

class Tour : GameClass() {
    override val classId = "tour"
    override val name = "<gray>순회공연"
    override val rank = Rank.C
    override val classItemMaterial = Material.GOAT_HORN
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = listOf()

    private inner class RedSkill : Skill() {
        override val definitionId = "tour/red-skill"
        override val name = "<bold>순회공연 시작!"
        override val description = listOf(
            "<gray>6칸 내의 바라보는 적이 순회공연을 하게 만든다.", "",
            "<gray>순회공연 중인 플레이어는 3초마다 순서대로 생존한 모든 플레이어의 위치로 즉시 이동된다.",
            "<gray>순회공연이 종료되면 원래 위치로 돌아온다.", "",
            "<dark_gray>생존한 플레이어가 8명 이하라면 8번 이동할 때까지 반복된다."
        )
        override val cooldown = TOUR_START_COOLDOWN_SECONDS

        private var selectedTarget: PlayerData? by requestValue { null }

        override fun isUseSuccess(): Boolean {
            selectedTarget = playerData.shotLaserGetEntityData(TOUR_TARGET_RANGE, TargetType.Enemy, false) as? PlayerData
            if (selectedTarget != null) return true
            player.sendMiniMessage("<red><bold>[!] 6칸 내에 바라보는 적 플레이어가 없습니다.")
            return false
        }

        override fun use(): Boolean {
            val target = selectedTarget ?: return false
            selectedTarget = null
            val originalLocation = target.player.location.clone()
            val performers = game.playerDatas.filterIsInstance<PlayerData>()
                .filter { !it.entityStatus.isDead && it.player.isOnline }
            if (performers.isEmpty()) return false
            val totalVisits = if (performers.size <= TOUR_MIN_VISITS) TOUR_MIN_VISITS else performers.size

            game.playerDatas.filterIsInstance<PlayerData>()
                .filter { it.player.isOnline }
                .forEach { viewer ->
                    viewer.player.sendMiniMessage(
                        "<light_purple><bold>[순회공연]</bold> <white>${target.player.name}<gray>님이 순회공연을 시작합니다!"
                    )
                    sounds.playTo(viewer.player, Sound.ITEM_GOAT_HORN_SOUND_0, volume = 0.65f, pitch = 1.15f)
                }

            fun teleportWithShow(destination: Location) {
                val departure = target.player.location.clone().add(0.0, 1.0, 0.0)
                particles.spawn(departure, Particle.NOTE, count = 18, spread = 0.5, speed = 0.08)
                sounds.play(departure, Sound.ENTITY_ENDERMAN_TELEPORT, volume = 0.7f, pitch = 1.5f)
                target.player.teleport(destination)
                target.player.fallDistance = 0f
                particles.spawn(target.player, Particle.NOTE, count = 24, spread = 0.65, speed = 0.12)
                particles.spawn(target.player, Particle.FIREWORK, count = 10, spread = 0.4, speed = 0.08)
                sounds.play(target.player, Sound.BLOCK_NOTE_BLOCK_BELL, volume = 0.85f, pitch = 1.25f)
            }

            playerData.trackTask(object : BukkitRunnable(abilityScope, cancelOnDisconnect = true) {
                private var visits = 0
                private var returned = false
                private fun returnHome() {
                    if (returned) return
                    returned = true
                    if (target.player.isOnline && !target.entityStatus.isDead) teleportWithShow(originalLocation)
                    else target.returnFromAbility(originalLocation)
                }
                override fun onCancel() = returnHome()

                override fun run() {
                    if (!target.player.isOnline || target.entityStatus.isDead) {
                        cancel()
                        return
                    }
                    if (visits >= totalVisits) {
                        returnHome()
                        target.player.sendMiniMessage("<light_purple><bold>[순회공연]</bold> <gray>공연이 종료되어 원래 위치로 돌아왔습니다.")
                        sounds.play(target.player, Sound.ENTITY_FIREWORK_ROCKET_BLAST, volume = 0.9f, pitch = 1.3f)
                        cancel()
                        return
                    }
                    val alive = game.playerDatas.filterIsInstance<PlayerData>()
                        .filter { !it.entityStatus.isDead && it.player.isOnline && it.player.world == target.player.world }
                    if (alive.isEmpty()) {
                        returnHome()
                        cancel()
                        return
                    }
                    val host = alive[visits % alive.size]
                    teleportWithShow(host.player.location.clone().add(0.0, 0.15, 0.0))
                    target.player.sendMiniMessage(
                        "<light_purple><bold>[공연 ${visits + 1}/$totalVisits]</bold> <gray>${host.player.name}님의 위치입니다."
                    )
                    visits++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, TOUR_VISIT_INTERVAL_TICKS))
            return true
        }
    }
}
