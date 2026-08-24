package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 6
private const val DUMMY_BLUE_SKILL_COOLDOWN_SECONDS = 0

// 해당 클래스는 구현하지 않고 보류 처리함.
class Wand : GameClass() {
    override val name = "<gray>완드"
    override val rank = Rank.S
    override val classItemMaterial = Material.STICK
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class RedSkill : Skill() {
        override val name = "<bold>복사"
        override val description = listOf(
            "<gray>6칸 내의 바라보는 블럭을 복사하여 완드에 집어넣는다."
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use() {
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<bold>사용"
        override val description = listOf(
            "<gray>더미 설명"
        )
        override val cooldown = DUMMY_BLUE_SKILL_COOLDOWN_SECONDS

        override fun use() {
        }
    }

    // 블럭을 만든다고 써있는 경우, 실제 블럭이 아닌 BLOCK_DISPLAY로 구성할 것
    private class Passive : BasePassive() {
        override val name = "<bold>완드"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>사용 스킬의 효과는 완드에 넣어진 아이템에 따라 변한다.",
            "<gray>아이템별 효과는 아래와 같다.",
            "<gray>  - 흑요석: 바라보는 방향으로 흑요석을 만들어 밀어내 모든 적과 투사체를 막아낸다.",
            "<gray>  - 거미줄: 10칸 내의 바라보는 플레이어를 3초간 {keyword:Silence}, {keyword:Snare} 상태로 만든다.",
            "<gray>  - 기반암: 4초간 자신의 기본 공격 피해가 2 증가한다.",
            "<gray>  - 청금석: 자신 주위의 모든 투사체에 레이저를 연결하여 3초간 멈추게 만든다.",
            "<gray>  - 눈 계열 블럭: 4초간 바라보는 방향으로 4틱마다 눈덩이를 발사한다.",
            "<gray>  - 레드스톤 계열 블럭: 4초간 바라보는 방향으로 레이저를 발사하여 틱당 0.1의 피해를 입힌다.",
        )
    }
}
