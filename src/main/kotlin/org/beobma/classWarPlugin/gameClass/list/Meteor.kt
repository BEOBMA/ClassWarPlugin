package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Mana
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.beobma.classWarPlugin.damage.DamageContext
import org.bukkit.scheduler.BukkitRunnable

class Meteor : GameClass() {
    override val name = "<gray>메테오"
    override val rank = Rank.B
    override val classItemMaterial = Material.FIRE_CHARGE
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )
    private class RedSkill : Skill() {
        override val name = "<bold>유성 낙하"
        override val description = listOf(
            "<gray>18칸 내의 바라보는 위치에 2.5초 후 운석을 떨어트린다.",
            "<gray>적중한 모든 대상에게 중심부는 10, 외각은 거리에 비례하여 최소 5의 피해를 입히고 5초간 {keyword:Burn} 상태로 만든다.",
            "<gray>운석이 떨어진 위치에는 10초간 불타는 지형이 남으며, 지형 위의 적은 초당 1의 피해를 받고 {keyword:Burn} 지속 시간이 감소하지 않는다",
            "",
            "<dark_gray>웅크린 상태에서 사용하면 자신의 위치에 시전할 수도 있다."
        )
        override val cooldown = 40

        override fun use() {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            mana.decreasePower(100)

            val location = if (player.isSneaking) {
                playerData.shotLaserGetBlock(4.0)?.location?.add(0.5, 1.0, 0.5) ?: run {
                    player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                    return
                }
            } else {
                player.location.clone()
            }

            playerData.trackTask(
                object : BukkitRunnable() {
                    override fun run() {
                        val targets = playerData.radius(location, TargetType.All, 5.0, true)
                        targets.forEach {
                            it.damage(25.0, DamageType.Normal, playerData)
                            it.entity.fireTicks += 100
                        }
                    }
                }.runTaskLater(ClassWarPlugin.instance, 60L)
            )
        }

        override fun isUseSuccess(): Boolean {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            if (mana.power < 100) {
                player.sendMiniMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
                return false
            }
            if (player.isSneaking) {
                playerData.shotLaserGetBlock(4.0)?.location?.add(0.5, 1.0, 0.5) ?: run {
                    player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                    return false
                }
            }
            return true
        }
    }

    private class Passive : BasePassive(), WhenHitHandler {
        override val name = "<bold>화염 장막"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 피격 시 공격자를 2초간 {keyword:Burn} 상태로 만든다."
        )

        override fun whenAttackHit(event: DamageContext) {
            event.attacker.player.fireTicks += 40
        }
    }
}
