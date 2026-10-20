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
            "<gray> 적중한 적에게 2의 피해를 입힌다.",
            "<gray> 물약이 적중한 위치에 3초간 <gold><bold>이동 속도를 20% 감소</bold><gray>시키는 장판을 만든다.",
            "",
            "<gray>우클릭 시 물약을 마신다.",
            "<green><bold> 체력을 4 회복</bold><gray>한다."
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 물 + 불
    private class PotionOfWaterFire : Skill() {
        override val definitionId = "dummy/potion-of-waterfire"
        override val name: String = "<bold>증기의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>좌클릭 시 바라보는 방향으로 투척한다.",
            "<gray> 적중한 적에게 3의 피해를 입힌다.",
            "<gray> 추가로 2초간 {keyword:Burn} 상태로 만든다."
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 물 + 흙
    private class PotionOfWaterDirt : Skill() {
        override val definitionId = "dummy/potion-of-waterdirt"
        override val name: String = "<bold>진흙의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>좌클릭 시 바라보는 방향으로 투척한다.",
            "<gray> 적중한 적에게 2의 피해를 입힌다.",
            "<gray> 추가로 2초간 적의 시야를 가린다"
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 물 + 공기
    private class PotionOfWaterAir : Skill() {
        override val definitionId = "dummy/potion-of-waterair"
        override val name: String = "<bold>폭우의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>좌클릭 시 바라보는 방향으로 투척한다.",
            "<gray> 적중한 위치에 5초간 폭우를 내린다.",
            "<gray> 폭우에 닿은 적에게 초당 1의 피해를 입히고, 1초간 <gold><bold>이동 속도를 15% 감소</bold><gray>시킨다."
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 물 + 빛
    private class PotionOfWaterLight : Skill() {
        override val definitionId = "dummy/potion-of-waterlight"
        override val name: String = "<bold>정화의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>좌클릭 시 바라보는 방향으로 투척한다.",
            "<gray> 적중한 적의 해제 가능한 모든 긍정적인 효과를 제거한다.",
            "<gray> 해제한 긍정적인 효과의 개수 만큼 {keyword:TrueDamage}를 입힌다. (최대 6)",
            "",
            "<gray>우클릭 시 물약을 마신다.",
            "<gray> 자신에게 적용된 모든 부정적인 효과를 제거한다.",
            "<gray> 해제한 부정적인 효과의 개수 만큼 <green><bold>자신의 체력을 회복</bold><gray>한다. (최대 6)"
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 불 + 불
    private class PotionOfFireFire : Skill() {
        override val definitionId = "dummy/potion-of-firefire"
        override val name: String = "<bold>폭발의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>좌클릭 시 바라보는 방향으로 투척한다.",
            "<gray> 적중한 위치에 폭발을 일으켜 적중한 모든 적에게 4의 피해를 입힌다."
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 불 + 흙
    private class PotionOfFireDirt : Skill() {
        override val definitionId = "dummy/potion-of-firedirt"
        override val name: String = "<bold>융해의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>좌클릭 시 바라보는 방향으로 투척한다.",
            "<gray> 적중한 위치 주변 모든 블럭을 5초간 용융시켜 해당 블럭에 닿은 모든 적에게 초당 2의 피해를 입힌다.",
            "<gray> 3초 이상 닿아있던 적은 추가로 {keyword:Burn} 상태로 만든다."
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 불 + 공기
    private class PotionOfFireAir : Skill() {
        override val definitionId = "dummy/potion-of-fireair"
        override val name: String = "<bold>열기의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>좌클릭 시 바라보는 방향으로 투척한다.",
            "<gray> 적중한 적에게 2의 피해를 입히고 3초간 장착한 무작위 방어구 1개를 제거한다."
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 불 + 빛
    private class PotionOfFireLight : Skill() {
        override val definitionId = "dummy/potion-of-firelight"
        override val name: String = "<bold>염화의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>좌클릭 시 바라보는 방향으로 투척한다.",
            "<gray> 적중한 적에게 4의 피해를 입히고 3초간 대상이 받는 체력 회복 효과를 모두 차단한다.",
            "<gray> 만약 지속 시간 도중 적이 체력 회복을 시도했다면 대상을 3초간 {keyword:Burn} 상태로 만든다."
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 흙 + 흙
    private class PotionOfDirtDirt : Skill() {
        override val definitionId = "dummy/potion-of-dirtdirt"
        override val name: String = "<bold>구축의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>좌클릭 시 바라보는 방향으로 투척한다.",
            "<gray> 적중한 적을 3초간 감옥에 가둔다.",
            "",
            "<gray>우클릭 시 물약을 마신다.",
            "<gray> 3초간 <aqua><bold>6의 피해를 막는 {keyword:Shield}을 얻는다."
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 흙 + 공기
    private class PotionOfDirtAir : Skill() {
        override val definitionId = "dummy/potion-of-dirtair"
        override val name: String = "<bold>연막의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>좌클릭 시 바라보는 방향으로 투척한다.",
            "<gray> 적중한 위치에 5초간 연막을 만든다.",
            "<gray> 연막 내부에서 자신은 {keyword:Stealth} 상태가 된다."
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 흙 + 빛
    private class PotionOfDirtLight : Skill() {
        override val definitionId = "dummy/potion-of-dirtlight"
        override val name: String = "<bold>생명의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>우클릭 시 물약을 마신다.",
            "<gray> 3초 이내에 사망할 경우, {keyword:Invalidity}로 한다. (1회 제한, 생명의 물약을 사용할 때마다 초기화)"
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 공기 + 공기
    private class PotionOfAirAir : Skill() {
        override val definitionId = "dummy/potion-of-airair"
        override val name: String = "<bold>돌풍의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>좌클릭 시 바라보는 방향으로 투척한다.",
            "<gray> 적중한 위치에 강한 돌풍을 일으켜 주변 적을 크게 밀어낸다."
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 공기 + 빛
    private class PotionOfAirLight : Skill() {
        override val definitionId = "dummy/potion-of-airlight"
        override val name: String = "<bold>폭풍의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>좌클릭 시 바라보는 방향으로 투척한다.",
            "<gray> 적중한 위치에 폭풍을 일으켜 주변 무작위 위치에 3초간 번개를 20회 소환한다.",
            "<gray> 번개에 적중한 적에게 2의 피해를 입히고, 3초간 {keyword:Burn} 상태로 만든다."
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 빛 + 빛
    private class PotionOfLightLight : Skill() {
        override val definitionId = "dummy/potion-of-lightlight"
        override val name: String = "<bold>신성의 물약"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>좌클릭, 우클릭 시 사용한다.",
            "<gray> 모든 물약의 효과를 적용한다.",
            "<gray> 마셨을 때 사용되는 효과는 모두 자신에게",
            "<gray> 투척했을 때 사용되는 효과는 모두 투척 후 적중한 적 혹은 위치에 적용된다."
        )

        override val cooldown: Int = 0

        override fun use(): Boolean {
            return true
        }
    }

    // 빛이 나올 확률은 다른 것보다 확연히 낮은 확률 (빛 2개가 나올 확률은 게임에서 1번 나올까 말까한 수준)
    private class Passive : BasePassive() {
        override val name = "<bold>재료 수급"
        override val description = listOf(
            "<gray>패시브",
            "",
            "{keyword:Charge}이 100이면 {keyword:Charge}을 모두 소모하여",
            "<gray>무작위 연금술 재료를 1개 얻는다. (최대 10개만 보유 가능)",
            "",
            "<dark_gray>연금술 재료는 물, 불, 흙, 공기, 빛으로 구성된다."
        )
    }
}
