package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.SkillManager.getConeTargets
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Bleeding
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.bukkit.scheduler.BukkitRunnable

class Knight : GameClass() {
    override val name = "<gray>기사"
    override val rank = Rank.B
    override val classItemMaterial = Material.IRON_SWORD
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill(),
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )


    private class Weapon : BaseWeapon() {
        override val name = "<gray>장검"
        override val description = listOf(
            "<gray>기본 공격 피격 직전 우클릭하면 해당 피해를 무효로 한다.",
            "<gray>패링에 성공하면 가로베기의 재사용 대기 시간이 초기화된다.",
            "<gray>이 효과는 24초마다 한 번만 사용할 수 있다."
        )
        override val material = Material.IRON_SWORD
    }

    private class RedSkill : Skill() {
        override val name = "<bold>가로베기"
        override val description = listOf(
            "<gray>바라보는 방향으로 검을 휘두른다.",
            "<gray>적중한 모든 적에게 4의 피해를 입히고 4초간 {keyword:Bleeding}을 4 부여한다."
        )
        override val cooldown = 12

        override fun use() {
            val targets = playerData.getConeTargets(5.0, 100.0, TargetType.Enemy, false)
            targets.forEach {
                it.damage(5.0, DamageType.Normal, playerData)
                val targetPlayer = it as? PlayerData
                if (targetPlayer != null) {
                    val status = targetPlayer.getOrCreateStatus(playerData) { Bleeding() }
                    status.applyStatus(duration = 3, powerSet = 5)
                }
            }
        }
    }

    private class Passive : BasePassive(), OnHitHandler {
        override val name = "<bold>피로 벼려낸 검"
        override val description = listOf(
            "<gray>기본 공격 적중 시 3초간 적에게 {keyword:Bleeding}을 1 부여하고 {keyword:Bleeding}을 발동시킨다.",
            "<gray>이후 대상의 {keyword:Bleeding} 수치가 절반으로 감소한다."
        )

        override fun onAttackHit(event: DamageContext) {
            val entityData = event.target as? PlayerData ?: return
            val status = entityData.getOrCreateStatus(playerData) { Bleeding() }
            status.applyStatus(duration = 3, powerSet = 1)

            entityData.damage(status.power.toDouble(), DamageType.StatusAbnormality, playerData)
            status.updatePower(status.power / 2)
        }

    }
}
