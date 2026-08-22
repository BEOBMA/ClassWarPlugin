package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Sniper : GameClass() {
    override val name = "<gray>저격수"
    override val rank = Rank.B
    override val classItemMaterial = Material.SPYGLASS
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )


    private class Weapon : BaseWeapon() {
        override val name = "<gray>저격총"
        override val description = listOf(
            "<gray>우클릭 시 조준한다.",
            "<gray>탄환이 장전되어 있을 때에만 사용할 수 있다.",
            "<gray>양손들기 키를 누를 시 사용한다.",
            "",
            "<gray>사용 시 장전된 탄환을 소모하여 바라보는 방향으로 사격한다.",
            "<gray>적중한 적은 7의 피해를 입는다.",
            "",
            "<dark_gray>이 스킬은 기본 공격으로 간주한다."
        )
        override val material = Material.SPYGLASS
    }

    private class RedSkill : Skill() {
        override val name = "<gray><bold>재장전"
        override val description = listOf(
            "<gray>사용 시 저격총을 재장전한다.",
            "<gray>재장전하는 동안 <gold><bold>이동 속도가 40% 감소</bold><gold>한다."
        )
        override val cooldown = 1

        override fun use() {
            //TODO()
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>저지력"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 적중 시 1초간 대상의 <gold><bold>이동 속도가 5% 감소</bold><gray>한다.",
            "<gray>대상과 자신의 거리 차이가 5칸 이내라면 <gold><bold>대신 20% 감소</bold><gray>한다."
        )
    }
}
