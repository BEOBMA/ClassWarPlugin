package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
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
    override val rank = Rank.C
    override val classItemMaterial = Material.IRON_AXE
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(RedSkill(), OrangeSkill())
    override var passives: List<BasePassive> = listOf(Passive())


    private class Weapon : BaseWeapon() {
        override val name = "<gray>광전사의 도끼"
        override val description: List<String> = listOf("<gray>무겁고 날카롭다.")
        override val material: Material = Material.IRON_AXE

    }

    private class RedSkill : Skill() {
        override val name: String
            get() = "<dark_red><bold>피의 분노"
        override val description: List<String>
            get() = listOf("<gray>사용 시 5초 동안 <gold><bold>공격 속도가 30%</bold><gray> 증가한다.")
        override val cooldown: Int
            get() = 10

        override fun use(){
            val attackSpeedIncrease = AttackSpeedIncrease()
            val playerAttackSpeedIncrease = playerData.addStatus(attackSpeedIncrease, playerData)
            playerAttackSpeedIncrease.applyStatus(
                duration = 5,
                powerDelta = 30
            )
        }
    }

    private class OrangeSkill : Skill() {
        override val name: String
            get() = "<yellow><bold>라그나로크"
        override val description: List<String>
            get() = listOf(
                "<gray>사용 시 10초 동안 <gold><bold>이동 속도가 30%</bold><gray> 증가하고",
                "<gold><bold>받는 피해가 40%</bold><gray> 감소한다."
            )
        override val cooldown: Int
            get() = 40

        override fun use() {
            val playerMoveSpeedIncrease = playerData.addStatus(MoveSpeedIncrease(), playerData)
            val playerWhenDamageReduction = playerData.addStatus(WhenDamageReduction(), playerData)

            playerMoveSpeedIncrease.applyStatus(
                duration = 10,
                powerDelta = 30
            )
            playerWhenDamageReduction.applyStatus(
                duration = 10,
                powerDelta = 40
            )
        }
    }

    private class Passive : BasePassive(), OnHitHandler {
        override val name: String
            get() = "<red><bold>광전사의 의지"
        override val description: List<String>
            get() = listOf(
                "<gray>패시브",
                "",
                "<gray>기본 공격 적중 시 잃은 체력에 비례해 피해량이 증가한다.",
                "<gray>또한, 체력이 50% 이하라면 피해량의 10% 만큼 <green><bold>체력을 회복</bold><gray>한다.",
                "<dark_gray>잃은 체력 1당 0.2씩, 최대 4까지 피해량이 증가한다."
            )

        override fun onAttackHit(event: DamageContext) {
            val missingHealth = player.getPlayerMaxHealth() - player.health
            val damageBoost = (missingHealth * 0.2).coerceAtMost(4.0)
            event.addBaseDamage(damageBoost)

            if (player.getPlayerMaxHealth() / 2 > player.health) {
                playerData.heal(event.damage / 10, DamageType.Normal, playerData)
            }
        }
    }
}
