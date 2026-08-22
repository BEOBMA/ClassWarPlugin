package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Duelist : GameClass() {
    override val name = "<gray>결투가"
    override val rank = Rank.C
    override val classItemMaterial = Material.SPECTRAL_ARROW
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class RedSkill : Skill() {
        override val name = "<bold>팡트"
        override val description = listOf(
            "<gray>바라보는 방향으로 짧게 도약한다.",
            "<gray>이후 2칸 내의 가장 가까운 적에게 2의 피해를 입힌다.",
            "",
            "<dark_gray>결투 상대를 우선으로 공격한다."
        )
        override val cooldown = 5

        override fun use() {
            // TODO: 구현 예정
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<bold>앙 가르드"
        override val description = listOf(
            "<gray>10칸 내의 바라보는 적에게 15초간 결투를 선포한다.",
            "",
            "<gray>자신과 적은 서로의 공격으로 받는 피해가 30% 증가하고,",
            "<gray>다른 대상에게 받는 피해는 30% 감소한다.",
            "",
            "<gray>결투 상대에게 팡트를 3번 연속 적중시키면 추가로 6의 피해를 입힌다.",
            "<gray>결투 중, 팡트를 적중시키는데 성공하면 재사용 대기 시간이 2초 감소한다."
        )
        override val cooldown = 70

        override fun use() {
            // TODO: 구현 예정
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>자세 흐트러짐"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>결투 중 팡트 사용 후 적에게 피해를 입히지 못했다면 자세 흐트러짐 상태가 된다.",
            "<gray>자세 흐트러짐 상태에서 결투 상대에게 받는 피해가 25% 증가한다.",
            "",
            "<gray>피해를 받거나, 결투가 종료되면 자세 흐트러짐이 제거된다."
        )
    }
}
