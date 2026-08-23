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

// 밸런스 조정 상수
private const val BERSERKER_RAGNAROK_COOLDOWN_SECONDS = 60
private const val BERSERKER_RAGNAROK_DURATION_SECONDS = 8
private const val BERSERKER_RAGNAROK_DURATION_TICKS = 160L
private const val BERSERKER_RAGNAROK_SPEED_BONUS_PERCENT = 50
private const val BERSERKER_MISSING_HEALTH_DAMAGE_RATIO = 0.2
private const val BERSERKER_MAX_BONUS_DAMAGE = 4.0
private const val BERSERKER_LOW_HEALTH_RATIO = 0.5
private const val BERSERKER_LIFESTEAL_RATIO = 0.1
private const val BERSERKER_RAGNAROK_MIN_HEALTH = 1.0

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
            get() = BERSERKER_RAGNAROK_COOLDOWN_SECONDS

        override fun use() {
            val playerMoveSpeedIncrease = playerData.addStatus(MoveSpeedIncrease(), playerData)
            val playerAttackSpeedIncrease = playerData.addStatus(AttackSpeedIncrease(), playerData)

            playerMoveSpeedIncrease.applyStatus(
                duration = BERSERKER_RAGNAROK_DURATION_SECONDS,
                powerDelta = BERSERKER_RAGNAROK_SPEED_BONUS_PERCENT
            )
            playerAttackSpeedIncrease.applyStatus(
                duration = BERSERKER_RAGNAROK_DURATION_SECONDS,
                powerDelta = BERSERKER_RAGNAROK_SPEED_BONUS_PERCENT
            )
            ragnarokUntil = player.world.fullTime + BERSERKER_RAGNAROK_DURATION_TICKS
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
            val damageBoost = (missingHealth * BERSERKER_MISSING_HEALTH_DAMAGE_RATIO)
                .coerceAtMost(BERSERKER_MAX_BONUS_DAMAGE)
            event.addBaseDamage(damageBoost)

            if (player.getPlayerMaxHealth() * BERSERKER_LOW_HEALTH_RATIO > player.health) {
                playerData.heal(event.damage * BERSERKER_LIFESTEAL_RATIO, DamageType.Normal, playerData)
            }
        }

        override fun whenHit(context: DamageContext) {
            if (player.world.fullTime < ragnarokUntil) {
                context.capDamage(player.health - BERSERKER_RAGNAROK_MIN_HEALTH)
            }
        }
    }
}
