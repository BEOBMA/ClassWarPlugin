package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Hitman : GameClass() {
    override val name = "<gray>청부업자"
    override val rank = Rank.C
    override val classItemMaterial = Material.NETHERITE_HELMET
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        YellowSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )


    private class Weapon : BaseWeapon() {
        override val name = ""
        override val description = listOf("")
        override val material = Material.AIR
    }

    private class RedSkill : Skill() {
        override val name = "<gray><bold>찌르기"
        override val description = listOf(
            "<gray>2칸 내의 바라보는 적에게 6의 피해를 입힌다.",
            "<gray>대상이 자신을 바라보고 있지 않았다면 3의 피해를 추가로 입힌다.",
            "",
            "<dark_gray>재사용 대기 시간: 10초"
        )
        override val cooldown = 10

        override fun use() {
            //TODO()
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<gray><bold>단검 투척"
        override val description = listOf(
            "<gray>바라보는 방향으로 단검을 투척한다.",
            "<gray>단검이 적에게 적중하면 5의 피해를 입히고 해당 적의 뒤로 즉시 이동한다.",
            "<gray>단검이 블록에 적중하면 4초간 {keyword:Stealth}하고 해당 방향으로 빠르게 이동한다.",
            "",
            "<dark_gray>재사용 대기 시간: 10초"
        )
        override val cooldown = 10

        override fun use() {
            //TODO()
        }
    }

    private class YellowSkill : Skill() {
        override val name = "<bold>암살"
        override val description = listOf(
            "<gray>2칸 내의 바라보는 적에게 10의 피해를 입힌다.",
            "<gray>자신이 {keyword:Stealth} 중이었다면 5의 피해를 추가로 입힌다.",
            "<gray>이 스킬로 적을 처치했다면 재사용 대기시간이 75% 감소한다.",
            "",
            "<dark_gray>재사용 대기 시간: 60초"
        )
        override val cooldown = 60

        override fun use() {
            //TODO()
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>청부"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>"
        )
    }
}
