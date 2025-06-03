package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.event.PlayerSkillDamageByPlayerEvent
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.gameClass.WhenHitHandler
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.*
import org.beobma.classWarPlugin.status.list.Mana
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.scheduler.BukkitRunnable

class FireWizard : GameClass(), GameStatusHandler {
    override val name = "<gray>불마법사"
    override val description = listOf(
        "<blue>근거리 마법사",
        "",
        "<gray>불마법사는 가까운 거리의 적에게 화상 피해를 입힙니다."
    )
    override val classItemMaterial = Material.FIRE_CHARGE
    override val weapon = FireWizardsStaff()

    override var skills: List<Skill> = listOf(
        FireWizardsRedSkill(),
        FireWizardsOrangeSkill()
    )

    override var passives: List<Passive> = listOf(
        FireWizardsPassive()
    )

    override fun onBattleStart() {
        val mana = playerData.getOrCreateStatus { Mana() }
        mana.increasePower(100)
    }

    override fun onGameTimePasses() {
        val mana = playerData.getOrCreateStatus { Mana() }
        mana.increasePower(10)
    }
}

class FireWizardsStaff : Weapon() {
    override val name = "<gray>대인용 지팡이"
    override val description = listOf("<gray>검처럼 사용할 수 있는 지팡이.")
    override val material = Material.WOODEN_SWORD
}

class FireWizardsRedSkill : Skill() {
    override val name = "<red><bold>발화"
    override val description = listOf(
        "${Keyword.Mana.string}를 40 소모하고 사용할 수 있다.",
        "",
        "<gray>사용 시 주위 모든 적에게 8의 피해를 입히고 4초간 ${Keyword.Burn.string} 상태로 만든다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus { Mana() }
        if (mana.power < 40) {
            player.sendMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }
        mana.decreasePower(40)

        val targets = playerData.radius(player.location, TargetType.Enemy, 5.0, false)
        targets.forEach {
            it.damage(8.0, DamageType.Normal, playerData)
            it.player.fireTicks += 80
        }
        return true
    }
}

class FireWizardsOrangeSkill : Skill() {
    override val name = "<red><bold>불기둥"
    override val description = listOf(
        "${Keyword.Mana.string}를 100 소모하고 사용할 수 있다.",
        "",
        "<gray>사용 시 자신 위치에 마법진을 만든다.",
        "<gray>3초 후 마법진에 불기둥이 떨어지며, 적중한 모든 대상에게 25의 피해를 입히고 5초간 ${Keyword.Burn.string} 상태로 만든다.",
        "<dark_gray>웅크린 상태에서 사용하면 4칸 내의 바라보는 블럭에 마법진을 만들 수도 있다."
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus { Mana() }
        if (mana.power < 100) {
            player.sendMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }
        mana.decreasePower(100)

        val location = if (player.isSneaking) {
            playerData.shotLaserGetBlock(4.0)?.location?.add(0.5, 1.0, 0.5) ?: run {
                player.sendMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                return false
            }
        } else {
            player.location.clone()
        }

        playerData.bukkitTasks.add(
            object : BukkitRunnable() {
                override fun run() {
                    val targets = playerData.radius(location, TargetType.All, 5.0, true)
                    targets.forEach {
                        it.damage(25.0, DamageType.Normal, playerData)
                        it.player.fireTicks += 100
                    }
                }
            }.runTaskLater(ClassWarPlugin.instance, 60L)
        )
        return true
    }
}

class FireWizardsPassive : Passive(), WhenHitHandler {
    override val name = "<red><bold>화염 장막"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>기본 공격 피격 시 공격자를 2초간 ${Keyword.Burn.string} 상태로 만든다."
    )

    override fun whenHit(
        skillDamageEvent: PlayerSkillDamageByPlayerEvent?,
        attackDamageEvent: EntityDamageByEntityEvent?
    ) {
        return
    }

    override fun whenAttackHit(event: EntityDamageByEntityEvent) {
        event.damager.fireTicks += 40
    }

    override fun whenSkillAttackHit(event: PlayerSkillDamageByPlayerEvent) {
        return
    }
}