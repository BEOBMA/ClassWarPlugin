package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 20

class WarCorrespondent : GameClass() {
    override val classId = "warcorrespondent"
    override val name = "<gray>종군기자"
    override val rank = Rank.S
    override val classItemMaterial = Material.OBSERVER
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class Weapon : BaseWeapon() {
        override val name = "<gray>카메라"
        override val description = listOf(
            "<gray>8칸 내의 적을 우클릭하면 촬영 상태에 들어간다.",
            "<gray>촬영 상태에서 적에게 매 틱마다 무적 시간을 무시하는 0.05의 {keyword:TrueDamage}를 입힌다.",
            "<gray>자신이 움직이거나, 스킬을 사용하거나, 점프하거나, 적이 사거리에서 벗어나면 촬영 상태는 종료된다.",
            "<gray>촬영 상태는 최대 3초간 지속되며, 지속된 시간에 비례하여 재사용 대기 시간이 적용된다. (최소 1, 최대 3)",
            "",
            "<gray>자신이 방송 상태라면 대신 적에게 매 틱마다 무적 시간을 무시하는 0.1의 {keyword:TrueDamage}를 입힌다."
        )
        override val material = Material.OBSERVER
    }

    private class RedSkill : Skill() {
        override val definitionId = "warcorrespondent/red-skill"
        override val name = "<bold>방송"
        override val description = listOf(
            "<gray>4초간 바라보는 방향을 부채꼴 형태로 넓게 쵤영한다.",
            "<gray>촬영 중 아래 조건을 만족하면 촬영을 완료하고 촬영 스택을 1 얻는다.",
            "<gray>  - 자신을 제외한 적 플레이어 2명 이상을 1초 이상 촬영",
            "<gray>  - 적 플레이어가 사망한 위치를 2초 이상 촬영",
            "",
            "<gray>촬영 스택이 3스택이 되면 자신은 방송 상태에 돌입한다.",
            "",
            "<gray>이 스킬 사용 중 기본 공격, 스킬을 사용할 수 없다.",
            "<gray>이 스킬은 Y축의 영향을 받지 않고 촬영할 수 있다."
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>취재"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>자신은 지도를 통해 다른 플레이어끼리 전투가 발생한 위치를 알 수 있다.",
            "<gray>또한 플레이어가 사망한 위치를 알 수 있다."
        )
    }
}
