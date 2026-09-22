package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 1
private const val DUMMY_ORANGE_SKILL_COOLDOWN_SECONDS = 2
private const val DUMMY_YELLOW_SKILL_COOLDOWN_SECONDS = 0
private const val DUMMY_DOMAIN_SKILL_COOLDOWN_SECONDS = 300

class Creator : GameClass() {
    override val classId = "creator"
    override val name = "<gray>창조자"
    override val rank = Rank.SPECIAL
    override val classItemMaterial = Material.COMMAND_BLOCK
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        DomainSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class RedSkill : Skill() {
        override val definitionId = "dummy/red-skill"
        override val name = "<bold>창조 - 사슬"
        override val description = listOf(
            "<gray>창조:",
            "<gray> 20칸 내의 바라보는 블럭에 사슬을 내려 꽂는다.",
            "<gray> 사슬의 너비는 1칸이며, 적중한 모든 적에게 2의 피해를 입힌다.",
            "<gray> 창조된 사슬은 그 자리에 남으며, 최대 10개까지 존재할 수 있다.",
            "",
            "<gray>파괴:",
            "<gray> 사슬로부터 2칸 내에 있는 모든 적의 4초간 이동 속도를 20% 감소시킨다.",
            "<gray> 위 효과가 5초 안에 5번 적용되면 대신 사슬에 묶여 2초간 {keyword:Snare} 상태가 된다."
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class OrangeSkill : Skill() {
        override val definitionId = "dummy/orange-skill"
        override val name = "<bold>창조 - 빛의 창"
        override val description = listOf(
            "{keyword:Mana}를 30 소모하여 발동할 수 있다.",
            "",
            "<gray>창조:",
            "<gray> 바라보는 방향으로 빛의 창을 소환하여 날린다.",
            "<gray> 빛의 창은 매우 빠르게 날아가며, 적중한 적에게 5의 피해를 입힌다.",
            "<gray> 빛의 창은 적중된 위치 혹은 플레이어에게 박힌 상태로 남으며, 최대 3개까지 존재할 수 있다.",
            "",
            "<gray>파괴:",
            "<gray> 빛의 창이 폭발하여 3칸 이내의 적에게 2의 피해를 입힌다."
        )
        override val cooldown = DUMMY_ORANGE_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class YellowSkill : Skill() {
        override val definitionId = "dummy/orange-skill"
        override val name = "<bold>파괴"
        override val description = listOf(
            "{keyword:Mana}를 50 소모하여 발동할 수 있다.",
            "",
            "<gray>자신의 스킬로 창조한 모든 창조물을 파괴한다."
        )
        override val cooldown = DUMMY_YELLOW_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class DomainSkill : Skill() {
        override val definitionId = "dummy/domain-skill"
        override val name = "<bold>「영역 전개」-「창조 공간」"
        override val description = listOf(
            "<gray>15초간 30칸 너비의 {keyword:Area}을 전개한다.",
            "",
            "{keyword:Area}에서 자신의 {keyword:Mana}는 무한대가 되며",
            "<gray>자신의 모든 창조 스킬은 반드시 적에게 적중한다.",
            "<gray>창조물은 파괴 스킬 없이도 1초 후 자동으로 파괴된다.",
            "",
            "<gray>영역 종료 후, 자신이 창조한 모든 창조물이 제거된다.",
            "<gray>또한 20초간 {keyword:Mana} 회복 속도가 대폭 감소한다."
        )
        override val cooldown = DUMMY_DOMAIN_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>권능"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>일부 스킬이 창조와 파괴로 나뉜다.",
            "<gray>창조는 {keyword:Mana}를 소모하여 창조물을 소환하고",
            "<gray>파괴는 다른 스킬을 사용하여 창조물을 파괴한다.",
        )
    }
}
