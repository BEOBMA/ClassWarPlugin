package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.event.PlayerSkillDamageByPlayerEvent
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.OnHitHandler
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.PlayerManager.heal
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetPlayerData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.skill.Flooring
import org.beobma.classWarPlugin.skill.Meteor
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.*
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.entity.EntityDamageByEntityEvent

class Astronomer : GameClass(), GameStatusHandler {
    override val name = "<gray>천문학자"
    override val description = listOf(
        "<yellow>원거리 마법사",
        "",
        "<gray>천문학자는 별을 이용해 공격합니다."
    )
    override val classItemMaterial = Material.NETHER_STAR
    override val weapon = AstronomersStaff()

    override var skills = listOf(
        AstronomersRedSkill(),
        AstronomersOrangeSkill(),
        AstronomersYellowSkill()
    )
    override var passives: List<Passive> = listOf(
        AstronomersPassive()
    )

    override fun onBattleStart() {
        val mana = playerData.getOrCreateStatus { Mana() }
        mana.updatePower(100)
    }

    override fun onGameTimePasses() {
        val mana = playerData.getOrCreateStatus { Mana() }
        mana.increasePower(5)
    }
}

class AstronomersStaff : Weapon() {
    override val name = "<gray>대인용 지팡이"
    override val description = listOf("<gray>검처럼 사용할 수 있는 지팡이.")
    override val material = Material.WOODEN_SWORD
}

class AstronomersRedSkill : Skill() {
    override val name = "<bold>별자리"
    override val description = listOf("<gray>8칸 내의 바라보는 적에게 5의 피해를 입힌다.")
    override val cooldown = 10

    override fun use(): Boolean {
        val target = playerData.shotLaserGetPlayerData(8.0, TargetType.Enemy, false) ?: run {
            player.sendMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
            return false
        }
        target.damage(5.0, DamageType.Normal, playerData)
        return true
    }
}

class AstronomersOrangeSkill : Skill() {
    override val name = "<bold>별의 죽음"
    override val description = listOf(
        "<gray>8칸 내의 바라보는 블럭에 4초간 블랙홀을 만든다.",
        "<gray>블랙홀에 근접한 적은 끌어당겨지고 초당 2의 피해를 입는다.",
        "<dark_gray>웅크린 상태에서 사용하면 자신의 위치에 블랙홀을 만들 수도 있다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val origin = player.location
        val blackHole = AstronomersBlackHole()
        blackHole.location = if (player.isSneaking) {
            origin.clone()
        } else {
            val block = playerData.shotLaserGetBlock(8.0) ?: run {
                player.sendMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                return false
            }
            block.location.add(0.5, 1.0, 0.5)
        }
        blackHole.spawnFlooring(playerData)
        return true
    }
}

class AstronomersBlackHole : Flooring() {
    override var location = player.location
    override var radius: Double = 5.0
    override var targetType: TargetType = TargetType.Enemy
    override var time: Int? = 4

    override fun onFlooringPlayerHit(hitPlayerData: PlayerData, location: Location) {
        val hitPlayer = hitPlayerData.player
        val dir = location.clone().subtract(hitPlayer.location).toVector().normalize().multiply(0.1)
        hitPlayer.velocity = dir
        hitPlayerData.damage(2.0, DamageType.Normal, playerData, false)
    }
}

class AstronomersYellowSkill : Skill() {
    override val name = "<bold>별이 빛나는 밤"
    override val description = listOf(
        "<gray>자신의 위치에 5초간 지속되는 넓은 범위의 결계를 생성한다.",
        "<gray>결계 내부의 적과 아군은 각각 이하의 효과를 얻는다.",
        "",
        "<gold><bold>받는 피해 15% <red>증가<gray> | <gold>받는 피해 15% <green>감소",
        "<gold><bold>이동 속도 20% <red>감소<gray> | <gold>이동 속도 20% <green>증가",
        "<gray><bold>초당 2의 피해를 입음 | 초당 <green>체력을 2 회복"
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        val enemyField = AstronomersEnemyField()
        val teamField = AstronomersTeamField()
        enemyField.spawnFlooring(playerData)
        teamField.spawnFlooring(playerData)
        return true
    }
}

class AstronomersEnemyField : Flooring() {
    private val affected = mutableSetOf<PlayerData>()

    override var location = player.location
    override var radius: Double = 8.0
    override var targetType: TargetType = TargetType.Enemy
    override var time: Int? = 5

    override fun onFlooringPlayerHit(hitPlayerData: PlayerData, location: Location) {
        hitPlayerData.damage(2.0, DamageType.Normal, playerData, false)
        if (affected.add(hitPlayerData)) {
            val moveSpeedDecrease = hitPlayerData.addStatus(MoveSpeedDecrease())
            val whenDamageIncrease = hitPlayerData.addStatus(WhenDamageIncreased())
            moveSpeedDecrease.increasePower(20)
            whenDamageIncrease.increasePower(15)
            moveSpeedDecrease.setContinueWhileIf { affected.contains(playerData) }
            whenDamageIncrease.setContinueWhileIf { affected.contains(playerData) }
        }
    }

    override fun onFlooringPlayerOut(hitPlayerData: PlayerData, location: Location) {
        affected.remove(playerData)
    }

    override fun onFlooringEnd() {
        affected.clear()
    }
}
class AstronomersTeamField : Flooring() {
    private val affected = mutableSetOf<PlayerData>()

    override var location = player.location
    override var radius: Double = 8.0
    override var targetType: TargetType = TargetType.Team
    override var time: Int? = 5

    override fun onFlooringPlayerHit(hitPlayerData: PlayerData, location: Location) {
        hitPlayerData.heal(2.0, DamageType.Normal, playerData)
        if (affected.add(hitPlayerData)) {
            val moveSpeedIncrease = hitPlayerData.addStatus(MoveSpeedIncrease())
            val whenDamageReduction = hitPlayerData.addStatus(WhenDamageReduction())
            moveSpeedIncrease.increasePower(20)
            whenDamageReduction.increasePower(15)
            moveSpeedIncrease.setContinueWhileIf { affected.contains(playerData) }
            whenDamageReduction.setContinueWhileIf { affected.contains(playerData) }
        }
    }

    override fun onFlooringPlayerOut(hitPlayerData: PlayerData, location: Location) {
        affected.remove(playerData)
    }

    override fun onFlooringEnd() {
        affected.clear()
    }
}

class AstronomersPassive : Passive(), OnHitHandler {
    override val name = "<blue><bold>천문관측"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>${Keyword.Mana.string} 회복 속도가 감소한다.",
        "<gray>스킬 적중 시 ${Keyword.Mana.string}를 전부 소모하고 적중한 적 주변에 별을 떨어트린다.",
        "<gray>떨어트리는 별의 수는 소모한 ${Keyword.Mana.string} 양에 비례하여 증가한다. (20당 1개, 최대 5개)",
        "<gray>별은 적중한 적에게 1의 ${Keyword.TrueDamage.string}를 입힌다.",
        "",
        dictionary[Keyword.TrueDamage] ?: ""
    )

    override fun onHit(
        skillDamageEvent: PlayerSkillDamageByPlayerEvent?,
        attackDamageEvent: EntityDamageByEntityEvent?
    ) {
        return
    }

    override fun onAttackHit(event: EntityDamageByEntityEvent) {
        return
    }

    override fun onSkillAttackHit(event: PlayerSkillDamageByPlayerEvent) {
        val mana = playerData.getOrCreateStatus { Mana() }
        val count = (mana.power / 20).coerceIn(1, 5)
        val targetLoc = event.entity.player.location.add(0.0, 5.0, 0.0)
        repeat(count) {
            val starMeteor = AstronomersStarMeteor()
            starMeteor.location = targetLoc
            starMeteor.spawnMeteor(playerData)
        }
        mana.updatePower(0)
    }
}

class AstronomersStarMeteor : Meteor() {
    override var location: Location = player.location.add(0.0, 5.0, 0.0)
    override var speed: Double = 0.5
    override var isWallHit: Boolean = true
    override var targetType: TargetType = TargetType.Enemy

    override fun onMeteorPlayerHit(hitPlayerData: PlayerData, location: Location) {
        hitPlayerData.damage(1.0, DamageType.True, playerData)
    }
}