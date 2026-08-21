package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class RuneWizard : GameClass() {
    override val name = "<gray>룬마법사"
    override val rank = Rank.C
    override val classItemMaterial = Material.ECHO_SHARD
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
        override val name = "<gray>무기 이름"
        override val description = listOf("<gray>무기 설명")
        override val material = Material.DIAMOND
    }

    private class RedSkill : Skill() {
        override val name = "<blue><bold>마나 순환"
        override val description = listOf(
            "{keyword:Mana}를 200 소모하고 사용할 수 있다.",
            "",
            "<gray>사용 시 마나를 불태워 주위 모든 적에게 5의 피해를 입힌다.",
            "<gray>스킬 적중 시 소모한 {keyword:Mana}의 50%를 돌려받는다."
        )
        override val cooldown = 5

        override fun use() {
            // TODO: 마나 소모 및 범위 피해 후 50% 마나 회복
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<blue><bold>방출"
        override val description = listOf(
            "{keyword:Mana}를 500 소모하고 사용할 수 있다.",
            "",
            "<gray>사용 시 4칸 내의 바라보는 적에게 8의 피해를 입힌다."
        )
        override val cooldown = 5

        override fun use() {
            // TODO: 4칸 내 대상에게 피해
        }
    }

    private class YellowSkill : Skill() {
        override val name = "<blue><bold>인피니티"
        override val description = listOf(
            "{keyword:Mana}를 1000 소모하고 사용할 수 있다.",
            "",
            "<gray>사용 시 10초간 {keyword:Mana}를 무한히 사용할 수 있게 된다.",
            "<gray>이 효과가 지속되는 동안 {keyword:Mana}를 회복할 수 없다."
        )
        override val cooldown = 10

        override fun use() {
            // TODO: 10초간 마나 무제한, 회복 불가
        }
    }

    private class Passive : BasePassive() {
        override val name = "<blue><bold>룬의 힘"
        override val description = listOf(
            "<gray>패시브",
            "",
            "{keyword:Mana} 최대치가 대폭 증가한다.",
            "<gray>전투 시작 시 현재 {keyword:Mana}가 300으로 설정된다.",
            "<gray>스킬 적중 시 소모한 {keyword:Mana}의 50% 만큼 추가로 소모하고 피해를 {keyword:TrueDamage}로 전환한다.",
            "",
            Keyword.TrueDamage.description!!
        )
    }
}
