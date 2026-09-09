package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 8
private const val DUMMY_BLUE_SKILL_COOLDOWN_SECONDS = 20
private const val DUMMY_YELLOW_SKILL_COOLDOWN_SECONDS = 90

class Pioneer : GameClass() {
    override val classId = "pioneer"
    override val name = "<gray>선각자"
    override val rank = Rank.S
    override val classItemMaterial = Material.ENDER_EYE
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        YellowSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive(),
        PassiveTwo(),
        PassiveThree()
    )

    private class RedSkill : Skill() {
        override val definitionId = "pioneer/red-skill"
        override val name = "<bold>속검"
        override val description = listOf(
            "<gray>바라보는 방향으로 짧게 돌진하며 적을 베어 3의 피해를 입힌다.",
            "<gray>적중 시 2초간 {keyword:Burn} 상태로 만들고 10초간 {keyword:Vibration}을 2 부여한다.",
            "",
            "<gray>가속 스택이 3 이상이라면",
            "<gray>스킬 적중 직후 기본 공격 적중 시 적의 뒤로 이동하며 추가로 2의 피해를 입힌다.",
            "",
            "<gray>가속 스택이 5라면",
            "<gray>스킬 적중 직후 기본 공격 적중 시 {AccelerationBullet}을 1 얻는다.",
            "<gray>또한 추가로 대상에게 {keyword:VibrationExplosion}을 적용한다.",
            "",
            "<dark_gray>사용 후 핫바키가 1번으로 자동 교체된다."
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    // 0.2초 직전에 사용해야함
    private class OrangeSkill : Skill() {
        override val definitionId = "pioneer/orange-skill"
        override val name = "<bold>예지"
        override val description = listOf(
            "<gray>적의 공격을 받기 직전에 스킬을 사용하면 해당 공격을 회피한다.",
            "<gray>회피에 성공 직후 공격자에게 피해를 입히면 2의 추가 피해를 입히고 {AccelerationBullet}을 1 얻는다.",
            "",
            "<dark_gray>이 스킬 대신 검을 우클릭하여 사용할 수도 있다.",
            "<dark_gray>사용 후 핫바키가 1번으로 자동 교체된다."
        )
        override val cooldown = DUMMY_BLUE_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    // 처분 궁극기
    private class YellowSkill : Skill() {
        override val definitionId = "pioneer/yellow-skill"
        override val name = "<bold>처분"
        override val description = listOf(
            "<gray>이 스킬은 5번까지 재사용할 수 있다.",
            "",
            "<gray>1번째 사용 시 바라보는 방향으로 짧게 돌진하며 적을 베어 2의 피해를 입힌다.",
            "<gray>적중 시 2초간 {keyword:Burn} 상태로 만들고 10초간 {keyword:Vibration}을 2 부여한다.",
            "",
            "<gray>2~4번째 사용 시 바라보는 방향으로 검을 휘둘러 1의 피해를 입힌다.",
            "<gray>적중 시 10초간 {keyword:Vibration}을 1 부여한다.",
            "<gray>사용 직후 기본 공격 적중 시 {AccelerationBullet}을 2 소모하여 위 효과와 피해를 2배로 다시 적용한다.",
            "",
            "<gray>5번째 사용 시 바라보는 방향으로 마지막 일격을 날려 1의 피해를 입힌다.",
            "<gray>적중 시 {keyword:VibrationExplosion}을 2회 적용한다.",
            "<gray>위 효과로 {keyword:VibrationExplosion}이 2회 모두 적용될 때까지 적의 {keyword:Vibration}은 감소하지 않는다.",
            "",
            "<dark_gray>이 스킬 대신 검을 우클릭하여 사용할 수도 있다.",
            "<dark_gray>사용 후 핫바키가 1번으로 자동 교체된다.",
            "<dark_gray>최초 사용 후 10초간 기본 공격 적중 후 핫바키가 해당 스킬의 위치로 자동 교체된다.",
            "<dark_gray>최초 사용 후 10초 뒤에 남은 재사용 횟수와 관계 없이 이 스킬은 종료된다."
        )
        override val cooldown = DUMMY_YELLOW_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>예지안"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>게임 시작 시 예지안 스택을 30 얻는다.",
            "<gray>피격 시 예지안 스택이 3 감소한다.",
            "<gray>전투에서 벗어난지 10초가 지나면 예지안 스택은 천천히 30까지 회복한다.",
            "",
            "<gray>예지안 스택이 있으며, 적이 투사체, 순간이동, 이동 스킬, 공격 스킬을 발동할 때",
            "<gray>각각 아래의 효과를 발동하고 예지안 스택이 2 감소한다.",
            "<gray>  - 투사체의 경우 궤적을 볼 수 있다.",
            "<gray>  - 순간이동의 경우 순간이동 도착 위치를 볼 수 있다.",
            "<gray>  - 이동 스킬의 경우 이동하는 거리와 도착 위치를 볼 수 있다.",
            "<gray>  - 공격 스킬의 경우 공격 스킬의 범위를 볼 수 있다."
        )
    }

    private class PassiveTwo : BasePassive() {
        override val name = "<bold>미래 가속"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>같은 적에게 피해를 입힐 때마다 가속 스택을 1 얻는다. (최대 스택 5, 6초마다 최대 1만 얻을 수 있음)",
            "<gray>다른 적에게 피해를 입히거나, 4초간 가속 스택을 얻지 못하면 소멸한다.",
            "",
            "<gray>가속 스택 1당 이동 속도와 공격 속도가 4%씩 증가한다.",
            "<gray>가속 스택이 5라면 스킬 사용 후 발생하는 딜레이가 감소한다."
        )
    }

    private class PassiveThree : BasePassive() {
        override val name = "<bold>작열"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>적에게 {keyword:VibrationExplosion}을 적용할 때",
            "<gray>대상이 {keyword:Burn} 상태라면 {keyword:Burn} 상태를 해제하고 지속시간에 비례한 {keyword:AbnormalStatusDamage}를 입힌다."
        )
    }
}
