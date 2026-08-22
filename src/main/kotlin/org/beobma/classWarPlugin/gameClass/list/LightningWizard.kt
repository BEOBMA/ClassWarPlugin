package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Mana
import org.bukkit.Location
import org.bukkit.Material
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
        var coolTime: Int = 0,
        var isOn: Boolean = false,
        var isOverload: Boolean = false
    )

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
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }

            val location = if (player.isSneaking) {
                playerData.shotLaserGetBlock(4.0)?.location?.add(0.0, 1.0, 0.0) ?: run {
                    player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                    return
                }
            }
            else {
                player.location.clone().add(0.0, 1.0, 0.0)
            }

            val gameClass = playerData.gameClass

            if (gameClass !is LightningWizard) return
            val markerList = gameClass.markers
            if (markerList.size >= 3) {
                markerList.removeFirstOrNull()
            }
            markerList.add(Marker(location))
            mana.decreasePower(20)
            return
        }

        override fun isUseSuccess(): Boolean {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            if (mana.power < 20) {
                player.sendMiniMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
                return false
            }
            if (player.isSneaking) {
                playerData.shotLaserGetBlock(4.0)?.location?.add(0.0, 1.0, 0.0) ?: run {
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
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            if (mana.power < 100) {
                player.sendMiniMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
                return
            }
            val gameClass = playerData.gameClass

            if (gameClass !is LightningWizard) return
            val markerList = gameClass.markers
            if (markerList.isEmpty()) {
                player.sendMiniMessage("<red><bold>[!] 생성된 표식이 존재하지 않습니다.")
                return
            }

            mana.decreasePower(100)
            markerList.forEach { it.isOverload = true }

            val task = object : BukkitRunnable() {
                override fun run() {
                    markerList.clear()
                }
            }.runTaskLater(ClassWarPlugin.instance, 200L)
            playerData.trackTask(task)
        }

        override fun isUseSuccess(): Boolean {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            if (mana.power < 100) {
                player.sendMiniMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
                return false
            }
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
