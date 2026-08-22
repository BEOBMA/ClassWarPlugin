package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Gambler : GameClass() {
    override val name = "<gray>도박사"
    override val rank = Rank.B
    override val classItemMaterial = Material.PAPER

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        YellowSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class RedSkill : Skill() {
        override val name = "<bold>히트"
        override val description = listOf(
            "<gray>덱에서 {keyword:Card}를 1장 뽑는다."
        )
        override val cooldown = 5

        override fun use() {
            // TODO: 구현 예정
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<bold>스탠드"
        override val description = listOf(
            "<gray>패를 덱으로 되돌리고 덱을 섞는다.",
            "<gray>5초간 덱으로 되돌린 {keyword:Card}의 숫자 합계 1당 가하는 피해가 1% 증가한다.",
            "{keyword:Card}의 숫자 합계 5당 체력을 1 회복한다.",
        )
        override val cooldown = 20

        override fun use() {
            // TODO: 구현 예정
        }
    }

    private class YellowSkill : Skill() {
        override val name = "<bold>더블"
        override val description = listOf(
            "<gray>덱에서 {keyword:Card}를 1장 뽑는다.",
            "<gray>이 스킬로 잭팟이 발동하면 잭팟의 효과가 2배로 증가한다.",
            "<gray>이 스킬로 버스트가 발동하면 버스트의 지속 시간이 2배로 증가한다.",
            "<gray>두 효과 모두 발동하지 않았다면 스탠드의 효과를 2배로 적용하여 발동한다."
        )
        override val cooldown = 70

        override fun use() {
            // TODO: 구현 예정
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>블랙잭"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>덱을 섞을 때마다 {keyword:Card}를 2장 뽑는다.",
            "<gray>카드를 뽑았을 때, 패의 모든 {keyword:Card}의 숫자 합계가 21이면 잭팟이 발동한다.",
            "<gray>{keyword:Card} 숫자의 합계가 21을 초과하면 버스트가 발동한다.",
            "<gray>패가 확정된 후 효과를 발동한 뒤 패를 덱으로 되돌리고 덱을 섞는다.",
            "",
            "<gray>잭팟:",
            "<gray>  체력 4 회복",
            "<gray>  8초간 <aqua><bold>4의 피해를 막는 {keyword:Shield} 얻음",
            "<gray>  8초간 가하는 피해 20% 증가",
            "<gray>  8초간 이동 속도 15% 증가",
            "<gray>버스트:",
            "<gray>  8초간 가하는 피해 15% 감소",
            "<gray>  8초간 이동 속도 10% 감소",
        )
    }
}
