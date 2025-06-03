package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.event.PlayerSkillDamageByPlayerEvent
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.gameClass.WhenHitHandler
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.vibrationExplosion
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Mana
import org.beobma.classWarPlugin.status.list.Shield
import org.beobma.classWarPlugin.status.list.Vibration
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.bukkit.event.entity.EntityDamageByEntityEvent

class LandWizard : GameClass(), GameStatusHandler {
    override val name = "<gray>대지 마법사"
    override val description = listOf(
        "<blue>탱킹형 마법사",
        "",
        "<gray>대지 마법사는 받는 피해를 줄이는 패시브를 활용하여 받는 피해를 줄이고, 기본 스킬로 적에게 피해를 누적합니다."
    )
    override val classItemMaterial = Material.SANDSTONE
    override val weapon: Weapon = LandWizardsStaff()

    override var skills: List<Skill> = listOf(
        LandWizardsRedSkill(),
        LandWizardsOrangeSkill()
    )

    override var passives: List<Passive> = listOf(
        LandWizardsPassive()
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

class LandWizardsStaff : Weapon() {
    override val name = "<gray>대인용 지팡이"
    override val description = listOf("<gray>검처럼 사용할 수 있는 지팡이.")
    override val material = Material.WOODEN_SWORD
}

class LandWizardsRedSkill : Skill() {
    override val name = "<gold><bold>지진"
    override val description = listOf(
        "${Keyword.Mana.string}를 20 소모하고 사용할 수 있다.",
        "",
        "<gray>사용 시 주위 모든 적에게 2의 피해를 입히고 10초간 ${Keyword.Vibration.string}을 2 부여한다.",
        "",
        dictionary[Keyword.Vibration]!!,
        dictionary[Keyword.AbnormalStatusDamage]!!
    )
    override val cooldown = 2

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus { Mana() }
        if (mana.power < 20) {
            player.sendMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }
        mana.decreasePower(20)
        val targets = playerData.radius(player.location, TargetType.Enemy, 4.0, false)
        targets.forEach {
            val vibration = it.getOrCreateStatus { Vibration() }
            vibration.increasePower(2)
            vibration.updateDuration(10)
            it.damage(2.0, DamageType.Normal, playerData)
        }
        return true
    }
}

class LandWizardsOrangeSkill : Skill() {
    override val name = "<gold><bold>탄성 반발"
    override val description = listOf(
        "${Keyword.Mana.string}를 100 소모하고 사용할 수 있다.",
        "",
        "<gray>사용 시 5초간 <aqua><bold>8의 피해를 막는 보호막</bold><gray>을 얻고 주위 모든 적에게 ${Keyword.VibrationExplosion.string}을 적용한다.",
        "",
        dictionary[Keyword.VibrationExplosion]!!,
        dictionary[Keyword.AbnormalStatusDamage]!!
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus { Mana() }
        val shield = playerData.addStatus(Shield())
        if (mana.power < 100) {
            player.sendMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }
        mana.decreasePower(100)
        shield.increasePower(8)
        shield.updateDuration(5)

        val targets = playerData.radius(player.location, TargetType.Enemy, 4.0, false)
        targets.forEach {
            it.vibrationExplosion(playerData)
        }
        return true
    }
}

class LandWizardsPassive : Passive(), WhenHitHandler {
    override val name = "<gold><bold>암석화"
    override val description = listOf(
        "<gray>기본 공격 피격 시 <gold><bold>받는 피해가 30% 감소</bold><gray>한다."
    )

    override fun whenHit(
        skillDamageEvent: PlayerSkillDamageByPlayerEvent?,
        attackDamageEvent: EntityDamageByEntityEvent?
    ) {
        return
    }

    override fun whenAttackHit(event: EntityDamageByEntityEvent) {
        event.damage *= 0.7
    }

    override fun whenSkillAttackHit(event: PlayerSkillDamageByPlayerEvent) {
        return
    }
}
