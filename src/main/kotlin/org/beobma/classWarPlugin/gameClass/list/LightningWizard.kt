package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Mana
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.scheduler.BukkitRunnable

class LightningWizard : GameClass() {
    val markers: MutableList<Marker> = mutableListOf()
    override val name = "<gray>번개 마법사"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.LIGHTNING_ROD
    override val weapon = LightningWizardsStaff()

    override var skills: List<Skill> = listOf(
        LightningWizardsRedSkill(),
        LightningWizardsOrangeSkill(),
        LightningWizardsYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        LightningWizardsPassive()
    )
}

class LightningWizardsStaff : Weapon() {
    override val name = "<gray>지팡이 대용 검"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.WOODEN_SWORD
}

data class Marker(
    val location: Location,
    var coolTime: Int = 0,
    var isOn: Boolean = false,
    var isOverload: Boolean = false
)

class LightningWizardsRedSkill : Skill() {
    override val name = "<light_purple><bold>적란운"
    override val description = listOf(
        "${Keyword.Mana.string}를 20 소모하고 사용할 수 있다.",
        "",
        "<gray>자신의 위치 또는 바라보는 블럭 위에 표식을 남긴다. 최대 3개.",
        "<dark_gray>웅크리면 4칸 내 지정 설치 가능."
    )
    override val cooldown = 1

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus { Mana() }

        if (mana.power < 20) {
            player.sendMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }

        val location = if (player.isSneaking) {
            playerData.shotLaserGetBlock(4.0)?.location?.add(0.0, 1.0, 0.0) ?: run {
                player.sendMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                return false
            }
        }
        else {
            player.location.clone().add(0.0, 1.0, 0.0)
        }

        val gameClass = playerData.gameClass

        if (gameClass !is LightningWizard) return false
        val markerList = gameClass.markers
        if (markerList.size >= 3) {
            markerList.removeFirstOrNull()
        }
        markerList.add(Marker(location))
        mana.decreasePower(20)
        return true
    }
}

class LightningWizardsOrangeSkill : Skill() {
    override val name = "<light_purple><bold>낙뢰 충전"
    override val description = listOf(
        "<gray>표식이 1개 이상 존재할 때만 사용할 수 있다.",
        "${Keyword.Mana.string}를 40 소모하고 가장 가까운 표식을 활성화한다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus { Mana() }
        val gameClass = playerData.gameClass
        if (mana.power < 40) {
            player.sendMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }
        if (gameClass !is LightningWizard) return false
        val markerList = gameClass.markers
        if (markerList.isEmpty()) {
            player.sendMessage("<red><bold>[!] 생성된 표식이 존재하지 않습니다.")
            return false
        }

        markerList.forEach { it.isOn = false }
        markerList.minByOrNull { it.location.distanceSquared(player.location) }?.isOn = true

        mana.decreasePower(40)
        return true
    }
}

class LightningWizardsYellowSkill : Skill() {
    override val name = "<yellow><bold>과부하"
    override val description = listOf(
        "${Keyword.Mana.string}를 100 소모하고 사용할 수 있다.",
        "<gray>10초간 모든 표식을 과부하 상태로 만든다. 이후 모든 표식 제거."
    )
    override val cooldown = 20

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus { Mana() }
        val gameClass = playerData.gameClass
        if (mana.power < 100) {
            player.sendMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }

        if (gameClass !is LightningWizard) return false
        val markerList = gameClass.markers
        if (markerList.isEmpty()) {
            player.sendMessage("<red><bold>[!] 생성된 표식이 존재하지 않습니다.")
            return false
        }

        mana.decreasePower(100)
        markerList.forEach { it.isOverload = true }

        val task = object : BukkitRunnable() {
            override fun run() {
                markerList.clear()
            }
        }.runTaskLater(ClassWarPlugin.instance, 200L)
        playerData.bukkitTasks.add(task)
        return true
    }
}

class LightningWizardsPassive : Passive() {
    override val name = "<light_purple><bold>암페어"
    override val description = listOf(
        "<gray>공격 스킬 적중 시 재사용 대기 시간을 5% 돌려받는다."
    )
}
