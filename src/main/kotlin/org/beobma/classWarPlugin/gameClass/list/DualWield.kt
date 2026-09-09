package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.skill.Passive as BasePassive


class DualWield : GameClass(), org.beobma.classWarPlugin.gameClass.handler.ConfirmedHitHandler {
    override val classId = "dualwield"
    override val name = "<gray>쌍수"
    override val rank = Rank.A
    override val classItemMaterial = Material.DIAMOND_SWORD
    override var skills: List<Skill> = listOf()
    override val extraItemMaterials get() = listOf(org.bukkit.inventory.ItemStack(Material.IRON_SWORD))

    override fun onConfirmedHit(context: org.beobma.classWarPlugin.damage.DamageContext) {
        if (!context.path.isBasicAttack || context.secondaryAttack ||
            !player.inventory.itemInOffHand.type.name.endsWith("_SWORD")) return
        val offhand = player.inventory.itemInOffHand.clone()
        // Reuse the original attack input so the common basic-attack multiplier is applied once.
        val amount = context.baseDamage
        object : org.beobma.classWarPlugin.ability.AbilityRunnable(abilityScope) {
            override fun run() {
                if (!player.inventory.itemInOffHand.isSimilar(offhand) ||
                    !context.target.entity.isValid || context.target.entity.isDead || context.target.entityStatus.isDead ||
                    context.target.entity.world != player.world ||
                    context.target.entity.location.distanceSquared(player.location) > 16.0) return
                player.swingOffHand()
                context.target.damage(amount * 0.5, org.beobma.classWarPlugin.util.DamageType.Normal,
                    playerData, damagePath = org.beobma.classWarPlugin.damage.DamagePath.BASIC_ATTACK, secondaryAttack = true)
            }
        }.runTaskLater(org.beobma.classWarPlugin.ClassWarPlugin.instance, 10L)
    }

    override var passives: List<BasePassive> = listOf(
        Passive()
    )


    private class Passive : BasePassive() {
        override val name = "<bold>이도류"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>게임 시작 시 검을 하나 더 가지고 시작한다.",
            "<gray>검을 왼손에 장착한 상태에서 기본 공격 적중 시",
            "<gray>0.5초 후 왼손에 든 무기로 공격하여 기본 공격 피해량의 절반에 해당하는 피해를 입힌다."
        )
    }
}
