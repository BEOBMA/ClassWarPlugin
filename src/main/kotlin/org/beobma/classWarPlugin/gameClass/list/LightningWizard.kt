package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.scheduler.BukkitRunnable

class LightningWizard : GameClass() {
    private val markers: MutableList<Marker> = mutableListOf()
    override val name = "<gray>뇌운술사"
    override val rank = Rank.B
    override val classItemMaterial = Material.LIGHTNING_ROD

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private data class Marker(
        val location: Location,
        var isOverload: Boolean = false,
        var ageSeconds: Int = 0,
    )

    private fun createMarker(location: Location) {
        markers.clear()
        val marker = Marker(location.clone())
        markers += marker
        sounds.play(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, volume = 0.7f, pitch = 1.7f)
        playerData.trackTask(object : BukkitRunnable() {
            override fun run() {
                if (marker !in markers) { cancel(); return }
                marker.ageSeconds++
                particles.spawn(marker.location, Particle.CLOUD, count = 16, spread = 1.2, speed = 0.02)
                if (marker.isOverload || marker.ageSeconds % 5 == 0) strike(marker.location)
                if (marker.isOverload && marker.ageSeconds >= 5) {
                    markers.remove(marker)
                    cancel()
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 20L, 20L))
    }

    private fun strike(location: Location) {
        location.world.strikeLightningEffect(location)
        location.world.players
            .filter { it.location.distanceSquared(location) <= 64.0 * 64.0 }
        sounds.play(
            location,
            Sound.ENTITY_LIGHTNING_BOLT_IMPACT,
            volume = 0.62f,
            pitch = 1.3f,
            category = SoundCategory.MASTER,
        )
        sounds.play(
            location,
            Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
            volume = 0.34f,
            pitch = 1.5f,
            category = SoundCategory.MASTER,
        )
        playerData.radius(location, org.beobma.classWarPlugin.util.TargetType.Enemy, 3.0, false)
            .forEach { it.damage(4.0, org.beobma.classWarPlugin.util.DamageType.Normal, playerData) }
        particles.spawn(location, Particle.ELECTRIC_SPARK, count = 30, spread = 1.4, speed = 0.12)
    }

    private class RedSkill : Skill() {
        override val name = "<bold>적란운"
        override val description = listOf(
            "<gray>10칸 내의 바라보는 블럭에 적란운을 생성한다.",
            "<gray>이미 적란운이 존재한다면 기존 적란운을 제거하고 생성한다.",
            "",
            "<dark_gray>웅크린 상태에서 사용하면 자신의 위치에 적란운을 생성할 수도 있다."
        )
        override val cooldown = 8

        override fun use() {
            val location = if (player.isSneaking) {
                player.location.clone().add(0.0, 1.0, 0.0)
            }
            else {
                playerData.shotLaserGetBlock(10.0)?.location?.add(0.5, 1.0, 0.5) ?: run {
                    player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                    return
                }
            }

            val gameClass = playerData.gameClass

            if (gameClass !is LightningWizard) return
            gameClass.createMarker(location)
            return
        }

        override fun isUseSuccess(): Boolean {
            if (!player.isSneaking) {
                playerData.shotLaserGetBlock(10.0)?.location?.add(0.0, 1.0, 0.0) ?: run {
                    player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                    return false
                }
            }
            return true
        }
    }
    private class OrangeSkill : Skill() {
        override val name = "<bold>과부하"
        override val description = listOf(
            "<gray>적란운을 5초간 과부하시킨다.",
            "<gray>과부하된 적란운은 매초 낙뢰를 발생시키며, 지속시간 종료 후 적란운은 소멸한다."
        )
        override val cooldown = 50

        override fun use() {
            val gameClass = playerData.gameClass

            if (gameClass !is LightningWizard) return
            val markerList = gameClass.markers
            if (markerList.isEmpty()) {
                player.sendMiniMessage("<red><bold>[!] 생성된 표식이 존재하지 않습니다.")
                return
            }

            markerList.forEach { marker -> marker.isOverload = true; marker.ageSeconds = 0 }
            sounds.play(player, Sound.BLOCK_BEACON_POWER_SELECT, pitch = 1.8f)
        }

        override fun isUseSuccess(): Boolean {
            val gameClass = playerData.gameClass
            if (gameClass !is LightningWizard) return false
            val markerList = gameClass.markers
            if (markerList.isEmpty()) {
                player.sendMiniMessage("<red><bold>[!] 생성된 표식이 존재하지 않습니다.")
                return false
            }
            return true
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>낙뢰"
        override val description = listOf(
            "<gray>적란운은 5초마다 주변에 낙뢰를 발생시킨다.",
            "<gray>낙뢰는 주변 3칸 이내의 모든 적에게 4의 피해를 입힌다."
        )
    }
}
