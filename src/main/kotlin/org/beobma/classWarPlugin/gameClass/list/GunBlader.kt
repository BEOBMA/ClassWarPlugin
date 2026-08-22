package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class GunBlader : GameClass() {
    override val name = "<gray>총검사"
    override val rank = Rank.A
    override val classItemMaterial = Material.IRON_SWORD
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class Weapon : BaseWeapon() {
        override val name = "<gray>총검"
        override val description = listOf(
            "<gray>기본 공격 적중 시 10초간 {keyword:Vibration}을 1 부여한다.",
            "",
            "<gray>우클릭하면 {keyword:Bullet}을 1발 소모하여 바라보는 방향으로 사격한다.",
            "<gray>사격은 적중한 적에게 2의 피해를 입히고 {keyword:VibrationExplosion}을 적용한다."
        )
        override val material = Material.IRON_SWORD
    }

    private class RedSkill : Skill() {
        override val name = "<bold>돌파"
        override val description = listOf(
            "<gray>바라보는 방향으로 3칸 돌진한다.",
            "{keyword:Bullet}이 있다면 소모하여 대신 6칸 돌진한다.",
            "",
            "<gray>처음 충돌한 적을 베어 4의 피해를 입히고 10초간 {keyword:Vibration}을 3 부여한다."
        )
        override val cooldown = 12

        override fun use() {
            // TODO: 스킬 효과 구현 예정
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<bold>전탄 격발"
        override val description = listOf(
            "<gray>16칸 내의 바라보는 적을 조준하고 장전된 {keyword:Bullet}을 모두 소모하여 사격한다.",
            "<gray>{keyword:Bullet}마다 2의 피해를 입히고, 10초간 {keyword:Vibration}을 1 부여한다.",
            "<gray>마지막 {keyword:Bullet}이 적중하면 {keyword:VibrationExplosion}을 적용한다."
        )
        override val cooldown = 55

        override fun use() {
            // TODO: 스킬 효과 구현 예정
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>총검술"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 3회 적중 시 {keyword:Bullet}을 1 얻는다. ({keyword:Bullet}은 최대 4발 얻을 수 있다.)",
            "<gray>20초간 기본 공격, 스킬을 사용하지 않으면 최대 4발까지 장전한다."
        )
    }
}
