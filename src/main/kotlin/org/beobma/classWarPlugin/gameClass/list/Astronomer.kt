package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.PlayerManager.heal
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.skill.Flooring
import org.beobma.classWarPlugin.skill.Meteor
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.*
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.Material

class Astronomer : GameClass(), GameStatusHandler {
    override val name = "<gray>천문학자"
    override val rank = Rank.B
    override val classItemMaterial = Material.NETHER_STAR
    override var skills: List<Skill> = listOf(
        RedSkill()
    )
    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    override fun onBattleStart() {
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        mana.updatePower(100)
    }

    override fun onGameTimePasses() {
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        mana.increasePower(5)
    }

    private class RedSkill : Skill() {
        override val name = "<bold>별의 죽음"
        override val description = listOf(
            "<gray>8칸 내의 바라보는 블럭에 6초간 블랙홀을 만든다.",
            "<gray>블랙홀에 근접한 적은 끌어당겨지고 초당 2의 피해를 입는다.",
            "<gray>블랙홀의 영향을 받는 적에게서 초당 {keyword:Mana}를 2 강탈한다.",
            "",
            "<dark_gray>웅크린 상태에서 사용하면 자신의 위치에 블랙홀을 만들 수도 있다."
        )
        override val cooldown = 30

        override fun use() {
            val origin = player.location
            val blackHole = BlackHole()
            blackHole.location = if (player.isSneaking) {
                origin.clone()
            } else {
                val block = playerData.shotLaserGetBlock(8.0) ?: run {
                    player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                    return
                }
                block.location.add(0.5, 1.0, 0.5)
            }
            blackHole.spawnFlooring(playerData)
        }

        override fun isUseSuccess(): Boolean {
            if (!player.isSneaking) {
                playerData.shotLaserGetBlock(8.0) ?: run {
                    player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                    return false
                }
            }

            return true
        }
    }

    private class Passive : BasePassive(), OnHitHandler {
        override val name = "<blue><bold>천문관측"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>{keyword:Mana} 회복 속도가 감소한다.",
            "<gray>스킬 적중 시 {keyword:Mana}를 전부 소모하고 적중한 적 주변에 별을 떨어트린다.",
            "<gray>떨어트리는 별의 수는 소모한 {keyword:Mana} 양에 비례하여 증가한다. (20당 1개, 최대 5개)",
            "<gray>별은 적중한 적에게 1의 {keyword:TrueDamage}를 입힌다."
        )

        override fun onSkillAttackHit(event: DamageContext) {
            if (event.damageType == DamageType.True) return
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            val count = (mana.power / 20).coerceIn(1, 5)
            val targetLoc = event.target.entity.location.add(0.0, 5.0, 0.0)
            repeat(count) {
                val starMeteor = StarMeteor()
                starMeteor.location = targetLoc
                starMeteor.spawnMeteor(playerData)
            }
            mana.updatePower(0)
        }
    }


    private class BlackHole : Flooring() {
        override lateinit var location: Location
        override var radius: Double = 5.0
        override var targetType: TargetType = TargetType.Enemy
        override var time: Int? = 4

        //TODO(이펙트)
        override fun onFlooringEntityHit(hitEntityData: EntityData, location: Location) {
            val hitEntity = hitEntityData.entity
            val dir = location.clone().subtract(hitEntity.location).toVector().normalize().multiply(0.1)
            hitEntity.velocity = dir
            hitEntityData.damage(2.0, DamageType.Normal, playerData, false)
        }
    }

    private class EnemyField : Flooring() {
        private val affected = mutableSetOf<PlayerData>()

        override lateinit var location: Location
        override var radius: Double = 8.0
        override var targetType: TargetType = TargetType.Enemy
        override var time: Int? = 5

        override fun onFlooringEntityHit(hitEntityData: EntityData, location: Location) {
            val hitPlayerData = hitEntityData as? PlayerData ?: return
            hitPlayerData.damage(2.0, DamageType.Normal, playerData, false)
            if (affected.add(hitPlayerData)) {
                val moveSpeedDecrease = hitPlayerData.addStatus(MoveSpeedDecrease(), playerData)
                val whenDamageIncrease = hitPlayerData.addStatus(WhenDamageIncreased(), playerData)
                moveSpeedDecrease.increasePower(20)
                whenDamageIncrease.increasePower(15)
                moveSpeedDecrease.setContinueWhileIf { affected.contains(hitPlayerData) }
                whenDamageIncrease.setContinueWhileIf { affected.contains(hitPlayerData) }
            }
        }

        override fun onFlooringEntityOut(hitEntityData: EntityData, location: Location) {
            val hitPlayerData = hitEntityData as? PlayerData ?: return
            affected.remove(hitPlayerData)
        }

        override fun onFlooringEnd() {
            affected.clear()
        }
    }
    private class SelfField : Flooring() {
        private val affected = mutableSetOf<PlayerData>()

        override lateinit var location: Location
        override var radius: Double = 8.0
        override var targetType: TargetType = TargetType.Self
        override var time: Int? = 5

        override fun onFlooringEntityHit(hitEntityData: EntityData, location: Location) {
            val hitPlayerData = hitEntityData as? PlayerData ?: return
            hitPlayerData.heal(2.0, DamageType.Normal, playerData)
            if (affected.add(hitPlayerData)) {
                val moveSpeedIncrease = hitPlayerData.addStatus(MoveSpeedIncrease(), playerData)
                val whenDamageReduction = hitPlayerData.addStatus(WhenDamageReduction(), playerData)
                moveSpeedIncrease.increasePower(20)
                whenDamageReduction.increasePower(15)
                moveSpeedIncrease.setContinueWhileIf { affected.contains(hitPlayerData) }
                whenDamageReduction.setContinueWhileIf { affected.contains(hitPlayerData) }
            }
        }

        override fun onFlooringEntityOut(hitEntityData: EntityData, location: Location) {
            val hitPlayerData = hitEntityData as? PlayerData ?: return
            affected.remove(hitPlayerData)
        }

        override fun onFlooringEnd() {
            affected.clear()
        }
    }

    private class StarMeteor : Meteor() {
        override lateinit var location: Location
        override var speed: Double = 0.5
        override var isWallHit: Boolean = false
        override var targetType: TargetType = TargetType.Enemy

        //TODO()
        override fun onMeteorEntityHit(hitEntityData: EntityData, location: Location) {
            hitEntityData.damage(1.0, DamageType.True, playerData)
        }
    }
}
