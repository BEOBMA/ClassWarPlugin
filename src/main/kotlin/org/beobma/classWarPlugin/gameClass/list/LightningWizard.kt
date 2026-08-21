package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
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
    override val name = "<gray>번개 마법사"
    override val rank = Rank.C
    override val classItemMaterial = Material.LIGHTNING_ROD
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        YellowSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )


    private class Weapon : BaseWeapon() {
        override val name = "<gray>지팡이 대용 검"
        override val description = listOf("<gray>무기 설명")
        override val material = Material.WOODEN_SWORD
    }

    private data class Marker(
        val location: Location,
        var coolTime: Int = 0,
        var isOn: Boolean = false,
        var isOverload: Boolean = false
    )

    private class RedSkill : Skill() {
        override val name = "<light_purple><bold>적란운"
        override val description = listOf(
            "{keyword:Mana}를 20 소모하고 사용할 수 있다.",
            "",
            "<gray>자신의 위치 또는 바라보는 블럭 위에 표식을 남긴다. 최대 3개.",
            "<dark_gray>웅크리면 4칸 내 지정 설치 가능."
        )
        override val cooldown = 1

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
        override val name = "<light_purple><bold>낙뢰 충전"
        override val description = listOf(
            "<gray>표식이 1개 이상 존재할 때만 사용할 수 있다.",
            "{keyword:Mana}를 40 소모하고 가장 가까운 표식을 활성화한다."
        )
        override val cooldown = 10

        override fun use() {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            val gameClass = playerData.gameClass
            if (gameClass !is LightningWizard) return
            val markerList = gameClass.markers
            if (markerList.isEmpty()) {
                player.sendMiniMessage("<red><bold>[!] 생성된 표식이 존재하지 않습니다.")
                return
            }

            markerList.forEach { it.isOn = false }
            markerList.minByOrNull { it.location.distanceSquared(player.location) }?.isOn = true

            mana.decreasePower(40)
        }

        override fun isUseSuccess(): Boolean {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            if (mana.power < 40) {
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

    private class YellowSkill : Skill() {
        override val name = "<yellow><bold>과부하"
        override val description = listOf(
            "{keyword:Mana}를 100 소모하고 사용할 수 있다.",
            "<gray>10초간 모든 표식을 과부하 상태로 만든다. 이후 모든 표식 제거."
        )
        override val cooldown = 20

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
        override val name = "<light_purple><bold>암페어"
        override val description = listOf(
            "<gray>공격 스킬 적중 시 재사용 대기 시간을 5% 돌려받는다."
        )
    }
}
