package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 4
private const val DUMMY_BLUE_SKILL_COOLDOWN_SECONDS = 20

class Crossbow : GameClass() {
    override val classId = "crossbow"
    override val name = "<gray>석궁"
    override val rank = Rank.A
    override val classItemMaterial = Material.CROSSBOW
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
    )

    override var passives: List<BasePassive> = listOf()
    override val extraItemMaterials: List<ItemStack> = listOf(ItemStack(Material.ARROW, 32))

    private class Weapon : BaseWeapon() {
        override val name = "<gray>석궁"
        override val description = listOf(
            "<gray>공격 적중 시 적중한 적 뒤에 박힌 볼트를 남긴다. (최대 3개)",
            "<gray>박힌 볼트는 다른 스킬로 활용할 수 있으며, 4초가 지나면 제거된다.",
            "<gray>석궁의 최대 피해량이 4로 제한된다."
        )
        override val material = Material.CROSSBOW
    }

    private class RedSkill : Skill() {
        override val definitionId = "crossbow/red-skill"
        override val name = "<bold>회수"
        override val description = listOf(
            "<gray>모든 박힌 볼트를 자신의 위치로 끌어와 회수한다.",
            "<gray>끌어당겨지는 박힌 볼트에 닿은 적은 1의 {keyword:TrueDamage}를 입는다.",
            "<gray>박힌 볼트가 하나 이상의 적에게 적중했다면 석궁이 즉시 재장전된다."
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class OrangeSkill : Skill() {
        override val definitionId = "crossbow/orange-skill"
        override val name = "<bold>기동"
        override val description = listOf(
            "<gray>바라보는 방향에 위치한 박힌 볼트로 빠르게 이동한다.",
            "<gray>닿은 적에게 2의 피해를 입힌다."
        )
        override val cooldown = DUMMY_BLUE_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }
}
