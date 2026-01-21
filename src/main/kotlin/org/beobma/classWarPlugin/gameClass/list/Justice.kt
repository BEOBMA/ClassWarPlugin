package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.event.PlayerSkillDamageByPlayerEvent
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.OnHitHandler
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetPlayerData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.player.TeamType
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Exile
import org.beobma.classWarPlugin.status.list.Shield
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.addBaseDamage
import org.bukkit.Material
import org.bukkit.event.entity.EntityDamageByEntityEvent

class Justice : GameClass() {
    override val name = "<gray>판사"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.BELL
    override val weapon = JusticeSword()

    override var skills: List<Skill> = listOf(
        JusticeRedSkill(),
        JusticeOrangeSkill(),
        JusticeYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        JusticePassive()
    )
}

class JusticeSword : Weapon() {
    override val name = "<gray>무기 이름"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.WOODEN_SWORD
}

class JusticeRedSkill : Skill() {
    private val judgesUtils = JudgesUtils()

    override val name = "<bold>정의의 일격"
    override val description = listOf(
        "<gray>3칸 내의 바라보는 적에게 대검을 휘두른다.",
        "",
        "<green><bold>수적 우세</bold><gray> 상황에서는 6의 피해를,",
        "<dark_gray><bold>수적 균형</bold><gray> 상황에서는 8의 피해를,",
        "<red><bold>수적 열세</bold><gray> 상황에서는 10의 피해를 준다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val targetData = playerData.shotLaserGetPlayerData(3.0, TargetType.Enemy, false) ?: run {
            player.sendMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
            return false
        }
        when (judgesUtils.getTeamStatus(playerData)) {
            TeamStatus.Advantage -> targetData.damage(6.0, DamageType.Normal, playerData)
            TeamStatus.Balance -> targetData.damage(8.0, DamageType.Normal, playerData)
            TeamStatus.Inferiority -> targetData.damage(10.0, DamageType.Normal, playerData)
        }
        return true
    }
}

class JusticeOrangeSkill : Skill() {
    private val judgesUtils = JudgesUtils()

    override val name = "<bold>보호조치"
    override val description = listOf(
        "<gray>5초간 {keyword:Shield}을 얻는다.",
        "",
        "<green><bold>수적 우세</bold><gray> 상황과 <dark_gray><bold>수적 균형</bold><gray> 상황에서는 6의 피해를,",
        "<red><bold>수적 열세</bold><gray> 상황에서는 8의 피해를 막는다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val shield = playerData.addStatus(Shield())
        val power = when (judgesUtils.getTeamStatus(playerData)) {
            TeamStatus.Advantage, TeamStatus.Balance -> 6
            TeamStatus.Inferiority -> 8
        }
        shield.increasePower(power)
        shield.increaseDuration(5)
        return true
    }
}

class JusticeYellowSkill : Skill() {
    override val name = "<bold>길항승부"
    override val description = listOf(
        "<red><bold>수적 열세</bold><gray> 상황에서만 사용할 수 있다.",
        "",
        "<gray>아군과 적군의 수가 동일해질 때까지 무작위 적을 전장에서 5초간 {keyword:Exile}한다.",
        "",
        dictionary[Keyword.Exile]!!
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        return true
    }
}

class JusticePassive : Passive(), OnHitHandler {
    private val judgesUtils = JudgesUtils()

    override val name = "<bold>죄악"
    override val description = listOf(
        "<gray>자신 또는 아군 피격 시 공격자에게 죄를 부여한다.",
        "<gray>자신을 제외한 아군 사망 시 공격자는 15초간 유죄 상태가 된다."
    )

    override fun onHit(
        skillDamageEvent: PlayerSkillDamageByPlayerEvent?,
        attackDamageEvent: EntityDamageByEntityEvent?
    ) {
        return
    }

    override fun onAttackHit(event: EntityDamageByEntityEvent) {
        when (judgesUtils.getTeamStatus(playerData)) {
            TeamStatus.Advantage -> event.addBaseDamage(-4.0)
            TeamStatus.Balance -> event.addBaseDamage(-2.0)
            TeamStatus.Inferiority -> event.addBaseDamage(2.0)
        }
    }

    override fun onSkillAttackHit(event: PlayerSkillDamageByPlayerEvent) {
        return
    }
}
