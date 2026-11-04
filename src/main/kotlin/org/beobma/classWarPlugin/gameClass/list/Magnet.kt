package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 8
private const val DUMMY_BLUE_SKILL_COOLDOWN_SECONDS = 8

class Magnet : GameClass() {
    override val classId = "magnet"
    override val name = "<gray>자석"
    override val rank = Rank.A
    override val classItemMaterial = Material.IRON_BLOCK
    override var skills: List<Skill> = listOf(
        MagnetOneSkill(),
        MagnetTwoSkill(),
        MagnetThreeSkill(),
        MagnetFourSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class MagnetOneSkill : Skill() {
        override val definitionId = "dummy/red-skill"
        override val name = "<bold>자석 - N극"
        override val description = listOf(
            "<gray>플레이어에게 부착되어 유지되는 부착형 스킬",
            "",
            "<gray>10칸 내의 바라보는 플레이어에게 이 스킬을 부착한다.",
            "<gray>웅크린 상태에서 사용하면 자신에게 부착한다.",
            "",
            "<gray>이 스킬의 원래 주인은 이 스킬을 들고 <key:key.throw>를 눌러 이 자석의 극을 바꿀 수 있다.",
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class MagnetTwoSkill : Skill() {
        override val definitionId = "dummy/orange-skill"
        override val name = "<bold>자석 - N극"
        override val description = listOf(
            "<gray>플레이어에게 부착되어 유지되는 부착형 스킬",
            "",
            "<gray>10칸 내의 바라보는 플레이어에게 이 스킬을 부착한다.",
            "<gray>웅크린 상태에서 사용하면 자신에게 부착한다.",
            "",
            "<gray>이 스킬의 원래 주인은 이 스킬을 들고 <key:key.throw>를 눌러 이 자석의 극을 바꿀 수 있다.",
        )
        override val cooldown = DUMMY_BLUE_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class MagnetThreeSkill : Skill() {
        override val definitionId = "dummy/orange-skill"
        override val name = "<bold>자석 - S극"
        override val description = listOf(
            "<gray>플레이어에게 부착되어 유지되는 부착형 스킬",
            "",
            "<gray>10칸 내의 바라보는 플레이어에게 이 스킬을 부착한다.",
            "<gray>웅크린 상태에서 사용하면 자신에게 부착한다.",
            "",
            "<gray>이 스킬의 원래 주인은 이 스킬을 들고 <key:key.throw>를 눌러 이 자석의 극을 바꿀 수 있다.",
        )
        override val cooldown = DUMMY_BLUE_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class MagnetFourSkill : Skill() {
        override val definitionId = "dummy/orange-skill"
        override val name = "<bold>자석 - S극"
        override val description = listOf(
            "<gray>플레이어에게 부착되어 유지되는 부착형 스킬",
            "",
            "<gray>10칸 내의 바라보는 플레이어에게 이 스킬을 부착한다.",
            "<gray>웅크린 상태에서 사용하면 자신에게 부착한다.",
            "",
            "<gray>이 스킬의 원래 주인은 이 스킬을 들고 <key:key.throw>를 눌러 이 자석의 극을 바꿀 수 있다.",
        )
        override val cooldown = DUMMY_BLUE_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>자성"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>자석 스킬이 부착된 플레이어끼리 영향을 주고받는다.",
            "<gray>서로 다른 극을 가진 플레이어끼리는 끌어당겨지고",
            "<gray>서로 같은 극을 가진 플레이어끼리는 밀려난다.",
            "",
            "<gray>이 효과로 플레이어를 지형지물, 다른 플레이어와 충돌시키면",
            "<gray>가속도에 비례한 피해를 입힌다. (최대 8)",
            "<gray>자신은 이 효과로 피해를 받지 않는다.",
            "",
            "<dark_gray>자석 스킬은 원래 주인으로부터 20칸 멀어지면 원래 주인에게 돌아온다.",
            "<dark_gray>또한 최초 부착 시점으로부터 20초가 지나면 원래 주인에게 돌아온다."
        )
    }
}
