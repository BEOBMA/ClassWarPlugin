package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.PlayerManager.heal
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.UtilManager.getPlayerMaxHealth
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.AttackSpeedIncrease
import org.beobma.classWarPlugin.status.list.MoveSpeedIncrease
import org.beobma.classWarPlugin.status.list.WhenDamageReduction
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.damage.DamageContext
import org.bukkit.Material

class Berserker : GameClass() {
    override val name: String = "<gray>광전사"
    override val rank = Rank.B
    override val classItemMaterial = Material.IRON_AXE
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = listOf(Passive())

    private var ragnarokUntil = 0L


    private class Weapon : BaseWeapon() {
        override val name = "<gray>철 도끼"
        override val description: List<String> = listOf("")
        override val material: Material = Material.IRON_AXE

    }

    private inner class RedSkill : Skill() {
        override val name: String
            get() = "<bold>라그나로크"
        override val description: List<String>
            get() = listOf(
                "<gray>8초간 이동 속도와 공격 속도가 50% 증가한다.",
                "<gray>라그나로크가 지속되는 동안 체력이 1 미만으로 감소하지 않는다."
            )
        override val cooldown: Int
            get() = 60

        override fun use() {
            val playerMoveSpeedIncrease = playerData.addStatus(MoveSpeedIncrease(), playerData)
            val playerAttackSpeedIncrease = playerData.addStatus(AttackSpeedIncrease(), playerData)

            playerMoveSpeedIncrease.applyStatus(
                duration = 8,
                powerDelta = 50
            )
            playerAttackSpeedIncrease.applyStatus(
                duration = 8,
                powerDelta = 50
            )
            ragnarokUntil = player.world.fullTime + 160L
            sounds.play(player, org.bukkit.Sound.ENTITY_RAVAGER_ROAR, volume = 1.1f, pitch = 0.75f)
            particles.spawn(player, org.bukkit.Particle.ANGRY_VILLAGER, count = 18, spread = 0.6)
        }
    }

    private inner class Passive : BasePassive(), OnHitHandler, WhenHitHandler {
        override val name: String
            get() = "<red><bold>광전사의 의지"
        override val description: List<String>
            get() = listOf(
                "<gray>패시브",
                "",
                "<gray>기본 공격 적중 시 가하는 피해가 잃은 체력에 비례하여 증가한다. (최대 50%)"
            )

        override fun onAttackHit(event: DamageContext) {
            val missingHealth = player.getPlayerMaxHealth() - player.health
            val damageBoost = (missingHealth * 0.2).coerceAtMost(4.0)
            event.addBaseDamage(damageBoost)

            if (player.getPlayerMaxHealth() / 2 > player.health) {
                playerData.heal(event.damage / 10, DamageType.Normal, playerData)
            }
        }

        override fun whenHit(context: DamageContext) {
            if (player.world.fullTime < ragnarokUntil) context.capDamage(player.health - 1.0)
        }
    }
}
