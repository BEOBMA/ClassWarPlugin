package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val MINIGUN_RELOAD_COOLDOWN_SECONDS = 30

class SchwereKavallerie : org.beobma.classWarPlugin.gameClass.firearm.FirearmClass(org.beobma.classWarPlugin.gameClass.firearm.FirearmProfile.MINIGUN) {
    override val classId = "schwerekavallerie"
    override val name = "<gray>중화기병"
    override val rank = Rank.S
    override val classItemMaterial = Material.NETHERITE_NAUTILUS_ARMOR
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf()

    // 반동 자체는 약하나, 쭉 누르고 있으면 어느정도 쎔
    // 연사 속도가 매우 빠르며, 탄환이 퍼지는 것 또한 꽤 강함.
    private class Weapon : BaseWeapon() {
        override val name = "<gray>미니건"
        override val description = listOf(
            "<gray>우클릭을 누르거나 누르고 있으면 바라보는 방향으로 사격한다.",
            "<gray>매 사격마다 {keyword:Bullet}을 1 소모한다.",
            "",
            "<gray>사격 시 탄환을 발사하여 적중한 적에게 {g:damage:0.1}의 피해를 입힌다.",
            "<gray>미니건을 들고 있는 동안 <gold><bold>이동 속도가 {g:speed:80}% 감소</bold><gold>하고 점프할 수 없다.",
        )
        override val material = Material.NETHERITE_HORSE_ARMOR
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "schwerekavallerie/red-skill"
        override val name = "<bold>재장전"
        override val description = listOf(
            "<gray>모든 {keyword:Bullet}을 버린다.",
            "<gray>{g:reload:10}초 동안 재장전하여 {keyword:Bullet}을 300 얻는다. (최대 300)",
            "<gray>재장전하는 동안 <gold><bold>이동 속도가 {g:speed:90}% 감소</bold><gold>하고 점프할 수 없다."
        )
        override val cooldown = MINIGUN_RELOAD_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return beginReload()
        }
        override fun isUseSuccess() = canReload()
    }
}
