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
import org.bukkit.Material
import org.bukkit.event.entity.EntityDamageByEntityEvent

class Judge : GameClass() {
    override val name = "<gray>심판자"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.BELL
    override val weapon = JudgesSword()

    override var skills: List<Skill> = listOf(
        JudgesRedSkill(),
        JudgesOrangeSkill(),
        JudgesYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        JudgesPassive()
    )
}

class JudgesSword : Weapon() {
    override val name = "<gray>무기 이름"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.WOODEN_SWORD
}

class JudgesRedSkill : Skill() {
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

class JudgesOrangeSkill : Skill() {
    private val judgesUtils = JudgesUtils()

    override val name = "<bold>보호조치"
    override val description = listOf(
        "<gray>5초간 ${Keyword.Shield.string}을 얻는다.",
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

class JudgesYellowSkill : Skill() {
    private val judgesUtils = JudgesUtils()

    override val name = "<bold>길항승부"
    override val description = listOf(
        "<red><bold>수적 열세</bold><gray> 상황에서만 사용할 수 있다.",
        "",
        "<gray>아군과 적군의 수가 동일해질 때까지 무작위 적을 전장에서 5초간 ${Keyword.Exile.string}한다.",
        "",
        dictionary[Keyword.Exile]!!
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        if (judgesUtils.getTeamStatus(playerData) != TeamStatus.Inferiority) {
            player.sendMessage("<red><bold>[!] 수적 열세 상황에서만 사용할 수 있습니다.")
            return false
        }
        while (judgesUtils.getTeamStatus(playerData) != TeamStatus.Balance) {
            val enemies = game.playerDatas.filter { it.team != playerData.team && !it.playerStatus.isDead && it.getStatus<Exile>() == null }
            val enemy = enemies.randomOrNull() ?: break
            val exile = enemy.getOrCreateStatus { Exile() }
            exile.increaseDuration(5)
        }
        return true
    }
}

class JudgesPassive : Passive(), OnHitHandler {
    private val judgesUtils = JudgesUtils()

    override val name = "<bold>공정성"
    override val description = listOf(
        "<gray>기본 공격 적중 시,",
        "<green><bold>수적 우세</bold><gray> 상황에서는 피해량이 4 감소하고,",
        "<dark_gray><bold>수적 균형</bold><gray> 상황에서는 피해량이 2 감소하고,",
        "<red><bold>수적 열세</bold><gray> 상황에서는 피해량이 2 증가한다."
    )

    override fun onHit(
        skillDamageEvent: PlayerSkillDamageByPlayerEvent?,
        attackDamageEvent: EntityDamageByEntityEvent?
    ) {
        return
    }

    override fun onAttackHit(event: EntityDamageByEntityEvent) {
        when (judgesUtils.getTeamStatus(playerData)) {
            TeamStatus.Advantage -> event.damage -= 4
            TeamStatus.Balance -> event.damage -= 2
            TeamStatus.Inferiority -> event.damage += 2
        }
    }

    override fun onSkillAttackHit(event: PlayerSkillDamageByPlayerEvent) {
        return
    }
}

class JudgesUtils {
    fun getTeamStatus(playerData: PlayerData): TeamStatus {
        val (allies, enemies) = playerData.game.playerDatas.partition {
            it.team == playerData.team && !it.playerStatus.isDead
        }
        val enemyCount = enemies.count { it.team != TeamType.Spectator && !it.playerStatus.isDead }

        return when {
            allies.size > enemyCount -> TeamStatus.Advantage
            allies.size < enemyCount -> TeamStatus.Inferiority
            else -> TeamStatus.Balance
        }
    }
}

enum class TeamStatus {
    Advantage, Balance, Inferiority
}

