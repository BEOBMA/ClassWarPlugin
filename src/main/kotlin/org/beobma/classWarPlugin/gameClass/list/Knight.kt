package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.event.PlayerSkillDamageByPlayerEvent
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.OnHitHandler
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.gameClass.WhenHitHandler
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.getConeTargets
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetPlayerData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Bleeding
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.scheduler.BukkitRunnable

class Knight : GameClass() {
    override val name = "<gray>기사"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.IRON_SWORD
    override val weapon = KnightsSword()

    override var skills: List<Skill> = listOf(
        KnightsRedSkill(),
        KnightsOrangeSkill(),
        KnightsYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        KnightsPassive()
    )
}

class KnightsSword : Weapon() {
    override val name = "<gray>장검"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.IRON_SWORD
}

class KnightsRedSkill : Skill() {
    override val name = "<red><bold>내려베기"
    override val description = listOf(
        "<gray>2칸 내의 바라보는 적에게 6의 피해를 입히고 3초간 ${Keyword.Bleeding.string}을 3 부여한다.",
        "",
        dictionary[Keyword.Bleeding]!!,
        dictionary[Keyword.AbnormalStatusDamage]!!
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val target = playerData.shotLaserGetPlayerData(2.0, TargetType.Enemy, false) ?: run {
            player.sendMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
            return false
        }
        target.damage(6.0, DamageType.Normal, playerData)
        val status = target.getOrCreateStatus { Bleeding() }
        status.increaseDuration(3)
        status.updateDuration(3)
        return true
    }
}

class KnightsOrangeSkill : Skill() {
    override val name = "<red><bold>가로베기"
    override val description = listOf(
        "<gray>바라보는 방향으로 검을 휘두른다.",
        "<gray>적중한 모든 적에게 5의 피해를 입히고 3초간 ${Keyword.Bleeding.string}을 5 부여한다.",
        "",
        dictionary[Keyword.Bleeding]!!,
        dictionary[Keyword.AbnormalStatusDamage]!!
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val targets = playerData.getConeTargets(5.0, 100.0, TargetType.Enemy, false)
        targets.forEach {
            it.damage(5.0, DamageType.Normal, playerData)
            val status = it.getOrCreateStatus { Bleeding() }
            status.increaseDuration(5)
            status.updateDuration(3)
        }
        return true
    }
}

class KnightsYellowSkill : Skill(), WhenHitHandler {
    override val name = "<yellow><bold>패링"
    override val description = listOf(
        "<gray>기본 공격 피격 직전 사용 시 해당 피해를 무효로 한다.",
        "<gray>성공 시 재사용 대기 시간이 초기화된다."
    )
    override val cooldown = 30

    override fun use(): Boolean {
        activateParry()
        return true
    }

    private var isParry = false
    override fun whenHit(
        skillDamageEvent: PlayerSkillDamageByPlayerEvent?,
        attackDamageEvent: EntityDamageByEntityEvent?
    ) {
        return
    }

    override fun whenAttackHit(event: EntityDamageByEntityEvent) {
        if (isParry) {
            isParry = false
            event.isCancelled = true
            player.setCooldown(Material.YELLOW_DYE, 0)
        }
    }

    override fun whenSkillAttackHit(event: PlayerSkillDamageByPlayerEvent) {
        return
    }

    fun activateParry() {
        isParry = true
        val task = object : BukkitRunnable() {
            override fun run() {
                isParry = false
            }
        }.runTaskLater(ClassWarPlugin.instance, 2L)
        playerData.bukkitTasks.add(task)
    }
}

class KnightsPassive : Passive(), OnHitHandler {
    override val name = "<dark_red><bold>피로 벼려낸 검"
    override val description = listOf(
        "<gray>기본 공격 적중 시 3초간 적에게 ${Keyword.Bleeding.string}을 1 부여한다.",
        "<gray>그리고 즉시 ${Keyword.Bleeding.string}을 발동한다.",
        "",
        dictionary[Keyword.Bleeding]!!,
        dictionary[Keyword.AbnormalStatusDamage]!!
    )

    override fun onHit(
        skillDamageEvent: PlayerSkillDamageByPlayerEvent?,
        attackDamageEvent: EntityDamageByEntityEvent?
    ) {
        return
    }

    override fun onAttackHit(event: EntityDamageByEntityEvent) {
        val entity = event.entity
        val entityData = game.playerDatas.find { it.player == entity } ?: return
        val status = entityData.getOrCreateStatus { Bleeding() }
        status.increaseDuration(1)
        status.updateDuration(3)

        entityData.damage(status.power.toDouble(), DamageType.StatusAbnormality, playerData)
        status.updatePower(status.power / 2)
    }

    override fun onSkillAttackHit(event: PlayerSkillDamageByPlayerEvent) {
        return
    }
}
