package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 10

class Streamer : GameClass() {
    override val classId = "streamer"
    override val name = "<gray>스트리머"
    override val rank = Rank.S
    override val classItemMaterial = Material.OBSERVER
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive(),
        PassiveTwo()
    )

    private class RedSkill : Skill() {
        override val definitionId = "dummy/red-skill"
        override val name = "<bold>상점"
        override val description = listOf(
            "<gray>자신이 후원받은 금액을 소모하여 아이템을 구매할 수 있는 상점을 연다.",
            "<gray>상점에서는 무기, 갑옷, 물약, 불사의 토템과 같이 전투에 도움이 되는 아이템이 등장한다."
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    // 채팅은 최신 인터넷 방송(한국 기준 치지직)에서 자주 사용되는 유행어나 채팅 스타일로 최대한 많은 종류의 채팅을 띄워야 함.
    // 화면 우측이란, 스코어보드를 뜻함.
    // 상황에 맞는 채팅이 나오도록 해야함 (단, 순수 욕설은 금지하며 정치적이거나 특정 영역에서 민감한 단어(근들갑 이라는 단어같은 것)는 금지)
    // 닉네임도 나오게 하며(무작위) 가끔 게임 내 플레이어 닉네임도 등장하도록 해야함
    private class Passive : BasePassive() {
        override val name = "<bold>방송중"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>화면 우측에 채팅창과 시청자 수가 나타난다.",
            "",
            "<gray>시청자 수는 지속적으로 조금씩 줄어들며",
            "<gray>자신이 적에게 피해를 입히거나, 입는 등",
            "<gray>관심을 끌만한 행동을 하면 시청자 수가 증가한다.",
            "",
            "<gray>시청자 수에 비례하여 자신이 받는 피해가 감소하며, 기본 공격으로 가하는 피해가 증가한다.",
            )
    }

    // TNT는 맵을 폭파시킬 수 없고 피해만 입힘
    // 모루는 일정 시간 맵에 남아있다 소멸
    private class PassiveTwo : BasePassive() {
        override val name = "<bold>치즈"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>시청자 수에 비례하여, 자신이 자극적인 행동을 할 때마다 일정 확률로 치즈를 후원받는다. (후원 금액은 시청자 수에 비례하여 증가한다)",
            "<gray>후원받은 치즈에 비례하여 자신 혹은 자신과 가장 가까운 적에게 아래 효과가 적용된다.",
            "<gray>자신과 적 중 어떤 대상에게 적용될지는 무작위로 결정된다.",
            "",
            "<gray>1,000 치즈: 머리 위에서 모루가 떨어진다.",
            "<gray>3,000 치즈: 핫바키에 있는 아이템의 순서가 무작위로 재배치된다.",
            "<gray>5,000 치즈: 3초간 기본 무기가 사라진다.",
            "<gray>10,000 치즈: 현재 위치에 TNT가 소환된다.",
            "<gray>30,000 치즈: 5초간 공격 속도와 이동 속도가 50% 증가한다.",
            "<gray>50,000 치즈: 하늘 높이 강제로 점프된다. 이후 처음 받는 낙하 피해는 무효화된다.",
            "<gray>100,000 치즈: 5초간 인벤토리 내 모든 아이템이 제거된다.",
            "<gray>1,000,000 치즈: 모든 플레이어가 현재 위치로 이동된다."
        )
    }
}
