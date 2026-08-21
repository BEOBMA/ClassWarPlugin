package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material


class Paladin : GameClass() {
    override val name = "<gray>팔라딘"
    override val rank = Rank.C
    override val classItemMaterial = Material.MACE
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
        override val material = Material.WOODEN_SWORD
    }

    private class RedSkill : Skill() {
        override val name = "<gold><bold>맹세"
        override val description = listOf(
            "<gray>5초간 자신의 <gold><bold>기본 공격 피해가 2 증가</bold><gray>한다.",
            "<gray>또한, 기본 공격 적중 시 {keyword:Brightness}를 1 부여한다.",
            "",
            Keyword.Brightness.description!!
        )
        override val cooldown = 10

        override fun use() {
            // TODO: 5초간 기본 공격 피해 +2, 적중 시 밝기 1 적용
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<yellow><bold>빛의 강타"
        override val description = listOf(
            "<gray>3칸 내의 바라보는 적에게 (6 + 기본 공격 피해량)의 피해를 입히고 {keyword:Brightness}를 3 부여한다.",
            "",
            Keyword.Brightness.description!!
        )
        override val cooldown = 10

        override fun use() {
            // TODO: 대상에게 피해 및 밝기 3 부여
        }
    }

    private class YellowSkill : Skill() {
        override val name = "<yellow><bold>빛의 방패"
        override val description = listOf(
            "<gray>10초간 다음 3번의 공격으로부터 절반의 피해를 받는다.",
            "<gray>또한, 공격자에게 {keyword:Brightness}를 2 부여한다.",
            "<gray>이 효과는 원래 피해량이 5 이상인 경우에만 발동한다.",
            "",
            Keyword.Brightness.description!!
        )
        override val cooldown = Int.MAX_VALUE

        override fun use() {
            // TODO: 10초간 3회 데미지 반감 + 조건부 반사 밝기
        }
    }

    private class Passive : BasePassive() {
        override val name = "<gold><bold>복수의 맹세"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>자신이 기본 공격에 피격되면 공격자에게 표식을 부여한다.",
            "<gray>표식을 가진 적에게 기본 공격 적중 시 2의 피해를 추가로 입히고 {keyword:Brightness}를 2 부여한다.",
            "<gray>효과 발동 후 표식은 제거된다.",
            "",
            Keyword.Brightness.description!!
        )
    }
}
