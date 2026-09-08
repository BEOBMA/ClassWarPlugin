package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 8

class Freikugel : GameClass() {
    override val classId = "freikugel"
    override val name = "<gray>마탄의 사수"
    override val rank = Rank.A
    override val classItemMaterial = Material.CRYING_OBSIDIAN
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class Weapon : BaseWeapon() {
        override val name = "<gray>리볼버"
        override val description = listOf(
            "<gray>우클릭 시 {keyword:Bullet} 혹은 {keyword:FreikugelBullet}을 1발 소모하고 사격한다.",
            "<gray>사격은 적중한 적에게 3의 피해를 입힌다.",
            "<gray>{keyword:FreikugelBullet}을 소모하였다면 자신이 2의 피해를 입는다.",
            "<gray>사용 후 다른 스킬을 사용할 때까지 다시 사용할 수 없다.",
            "<gray>이 공격은 기본 공격으로 간주한다."
        )
        override val material = Material.IRON_HORSE_ARMOR
    }

    private class RedSkill : Skill() {
        override val definitionId = "freikugel/red-skill"
        override val name = "<bold>패닝 / 퀵드로우"
        override val description = listOf(
            "<gray>바라보는 방향으로 {keyword:FreikugelBullet}을 제외한 모든 {keyword:Bullet}을 소모하여 사격한다.",
            "<gray>매 사격마다 반동이 강해지며, 이 사격은 2의 피해를 입힌다.",
            "",
            "<gray>남은 {keyword:Bullet}이 {keyword:FreikugelBullet} 뿐이라면 위 효과 대신 아래 효과로 발동된다.",
            "<gray>바라보는 방향으로 {keyword:FreikugelBullet}을 소모하여 사격한다.",
            "<gray>이 사격은 적에게 5의 피해를 입히고 밀쳐낸다."
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>마탄환"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>{keyword:Bullet} 6발과 {keyword:FreikugelBullet} 1발을 가진 채 게임을 시작한다.",
            "<gray>{keyword:Bullet}과 {keyword:FreikugelBullet}을 모두 소모하면 2초간 재장전한다.",
            "<gray>재장전 중에는 기본 공격과 스킬을 사용할 수 없다."
        )
    }
}
