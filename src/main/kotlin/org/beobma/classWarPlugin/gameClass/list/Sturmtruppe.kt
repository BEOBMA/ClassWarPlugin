package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val ASSAULT_RELOAD_COOLDOWN_SECONDS = 8

class Sturmtruppe : org.beobma.classWarPlugin.gameClass.firearm.FirearmClass(org.beobma.classWarPlugin.gameClass.firearm.FirearmProfile.ASSAULT) {
    override val classId = "sturmtruppe"
    override val name = "<gray>돌격대"
    override val rank = Rank.A
    override val classItemMaterial = Material.IRON_NAUTILUS_ARMOR
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf()

    // 반동이 꽤 강함, 다른 FPS 게임처럼 자연스러운 반동을 만들어야함
    private class Weapon : BaseWeapon() {
        override val name = "<gray>돌격소총"
        override val description = listOf(
            "<gray>우클릭을 누르거나 누르고 있으면 바라보는 방향으로 사격한다.",
            "<gray>매 사격마다 {keyword:Bullet}을 1 소모한다.",
            "",
            "<gray>사격 시 탄환을 발사하여 적중한 적에게 {g:damage:0.2}의 피해를 입힌다."
        )
        override val material = Material.IRON_HORSE_ARMOR
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "sturmtruppe/red-skill"
        override val name = "<bold>재장전"
        override val description = listOf(
            "<gray>모든 {keyword:Bullet}을 버린다.",
            "<gray>{g:reload:3}초 동안 재장전하여 {keyword:Bullet}을 30 얻는다. (최대 30)",
            "<gray>재장전하는 동안 <gold><bold>이동 속도가 {g:speed:30}% 감소</bold><gold>한다."
        )
        override val cooldown = ASSAULT_RELOAD_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return beginReload()
        }
        override fun isUseSuccess() = canReload()
    }
}
