package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Sniper : GameClass() {
    override val name = "<gray>저격수"
    override val rank = Rank.C
    override val classItemMaterial = Material.SPYGLASS
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )


    private class Weapon : BaseWeapon() {
        override val name = "<gray>저격총"
        override val description = listOf(
            "<gray>우클릭 시 조준한다.",
            "<gray>탄환이 장전되어 있을 때에만 사용할 수 있다.",
            "<gray>F키를 누를 시 사용한다.",
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

    private class OrangeSkill : Skill() {
        override val name = "<gold><bold>견제 사격 / 지원 사격"
        override val description = listOf(
            "<gray>사용 시 생존한 적의 수에 따라 다른 효과를 발동한다.",
            "<gray>지속 중 기본 공격과 다른 스킬을 사용할 수 없다.",
            "<gray>지속 중 스킬을 재사용하면 효과는 즉시 종료된다.",
            "",
            "<gray>생존한 적이 2명 이상이라면 자신은 효과가 종료될 때까지 {keyword:Fix}되고 감시 상태에 돌입한다.",
            "<gray> 감시 상태에서 시야 내의 적이 기본 공격을 하면 해당 적을 사격하여 3의 피해를 입힌다.",
            "",
            "<gray>생존한 적이 1명이라면 자신은 효과가 종료될 때까지 <gold><bold>이동 속도가 40% 증가</bold><gray>하고 견제 상태에 돌입한다.",
            "<gray> 견제 상태에서 자신 주위에 근접한 적을 자동으로 사격하여 3의 피해를 입힌다.",
            "",
            "<dark_gray>이 스킬은 1초에 1번만 발동한다.",
            "<dark_gray>사격 횟수가 일정 횟수에 도달하면 이 스킬은 종료된다. (감시 상태에서는 5회, 견제 상태에서는 3회)",
            "<dark_gray>이 스킬로 입히는 피해는 모두 기본 공격으로 간주한다.",
            "<dark_gray>이 스킬은 종료된 시점부터 재사용 대기 시간이 적용된다."
        )
        override val cooldown = 20

        override fun use() {
            //TODO()
        }
    }

    private class Passive : BasePassive() {
        override val name = "<yellow><bold>저지력"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 적중 시 1초간 대상의 <gold><bold>이동 속도가 5% 감소</bold><gray>한다.",
            "<gray>대상과 자신의 거리 차이가 5칸 이내라면 <gold><bold>대신 20% 감소</bold><gray>한다."
        )
    }
}
