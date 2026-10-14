package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 10
private const val DUMMY_BLUE_SKILL_COOLDOWN_SECONDS = 10

class Alchemist : GameClass() {
    override val classId = "alchemist"
    override val name = "<gray>연금술사"
    override val rank = Rank.L
    override val classItemMaterial = Material.POTION
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )


    private class RedSkill : Skill() {
        override val definitionId = "dummy/red-skill"
        override val name = "<bold>연성"
        override val description = listOf(
            "<gray>연금술 재료를 2개 이상 가지고 있을 때에만 사용할 수 있다.",
            "",
            "<gray>자신은 스킬을 지속하는 동안 {keyword:Disarm}, {keyword:Silence} 상태가 된다.",
            "",
            "<gray>스킬 사용 시 보유한 연금술 재료가 들어있는 인벤토리가 열린다.",
            "<gray>임의의 연금술 재료를 2개 선택하고 조합하면 우측에 완성될 물약을 확인할 수 있다.",
            "<gray>조합을 완료한 후 물약을 선택하면 선택한 연금술 재료를 소모하고 해당 물약을 얻는다.",
            "<gray>이후 이 스킬은 종료된다.",
            "",
            "<dark_gray>물약을 5개 이상 보유 중이라면 스킬을 사용할 수 없다."
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class OrangeSkill : Skill() {
        override val definitionId = "dummy/orange-skill"
        override val name = "<bold>연성 해제"
        override val description = listOf(
            "<gray>연성의 효과로 생성한 물약이 있을 때에만 사용할 수 있다.",
            "",
            "<gray>자신은 스킬을 지속하는 동안 {keyword:Disarm}, {keyword:Silence} 상태가 된다.",
            "",
            "<gray>스킬 사용 시 보유한 물약이 들어있는 인벤토리가 열린다.",
            "<gray>임의의 물약 하나를 선택하면 우측에 해당 물약을 연성 해제했을 떄 얻는 연금술 재료를 확인할 수 있다.",
            "<gray>물약을 선택한 후 재료 중 하나를 선택하면 선택한 물약을 소모하고 해당 연금술 재료를 얻는다.",
            "<gray>이후 이 스킬은 종료된다."
        )
        override val cooldown = DUMMY_BLUE_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    // 물 + 물
    private class PotionOfWater : Skill() {
        override val definitionId = "dummy/potion-of-water"
        override val name: String = "<bold>물의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>좌클릭 시 바라보는 방향으로 투척한다.",
            "<gray> 투척 시 적중한 적에게 3의 피해를 입힌다.",
            "<gray> 투척된 물약이 블럭, 적에게 적중했다면 그 위치에 3초간 <gold><bold>이동 속도를 20% 감소</bold><gray>시키는 장판을 만든다.",
            "",
            "<gray>우클릭 시 물약을 마신다.",
            "<green><bold> 체력을 4 회복</bold><gray>한다."
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }


    private class Passive : BasePassive() {
        override val name = "<bold>재료 수급"
        override val description = listOf(
            "<gray>패시브",
            "",
            "{keyword:Charge}이 100이면 {keyword:Charge}을 모두 소모하여",
            "<gray>무작위 연금술 재료를 1개 얻는다.",
            "",
            "<dark_gray>연금술 재료는 물, 불, 흙, 공기, 빛으로 구성된다."
        )
    }
}
