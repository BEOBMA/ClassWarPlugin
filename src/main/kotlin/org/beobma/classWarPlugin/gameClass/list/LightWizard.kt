package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class LightWizard : GameClass() {
    override val name = "<gray>프리즘"
    override val rank = Rank.A
    override val classItemMaterial = Material.LIGHT

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class RedSkill : Skill() {
        override val name = "<bold>프리즘"
        override val description = listOf(
            "<gray>10칸 내의 바라보는 블럭에 프리즘을 설치한다. (최대 3개)",
            "<gray>최대 개수를 초과하여 설치할 경우 가장 오래된 프리즘을 제거하고 설치한다."
        )
        override val cooldown = 1

        override fun use() {
            // TODO: 프리즘 설치 로직 구현
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<bold>분광"
        override val description = listOf(
            "<gray>바라보는 방향으로 빛의 광선을 발사한다.",
            "<gray>광선에 직접 적중한 적은 8의 피해를 입는다.",
            "",
            "<gray>광선이 프리즘에 적중하면 해당 프리즘이 활성화된다.",
            "<gray>활성화된 프리즘은 십자 방향으로 빛의 광선을 방출한다.",
            "<gray>프리즘에서 방출된 빛의 광선에 적중한 적은 4의 피해를 입는다.",
            "",
            "<dark_gray>흩뿌려진 광선 또한 또다시 프리즘으로 반사될 수 있다.",
            "<dark_gray>단, 빛의 광선은 같은 프리즘에 1번만 반사될 수 있다.",
            "<dark_gray>적은 처음 발사한 광선을 포함하여 여러 광선에 적중될 수 있다.",
            "<dark_gray>적중한 모든 적은 4의 피해를 입으나, 반사된 횟수에 따라 피해량이 절반으로 감소한다. (최소 1)"
        )
        override val cooldown = 20

        override fun use() {
            // TODO: 광선 발사 및 반사 처리 구현
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>루멘"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>프리즘에서 방출된 빛의 광선에 적중한 적에게 {keyword:Brightness}를 1 부여한다."
        )
    }
}
