package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.Flooring
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Frostbite
import org.beobma.classWarPlugin.status.list.Mana
import org.beobma.classWarPlugin.status.list.MoveSpeedDecrease
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class IceWizard : GameClass(), GameStatusHandler {
    override val name = "<gray>블리자드"
    override val rank = Rank.A
    override val classItemMaterial = Material.BLUE_ICE

    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    override fun onBattleStart() {
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        mana.increasePower(100)
    }

    override fun onGameTimePasses() {
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        mana.increasePower(10)
    }

    private class RedSkill : Skill() {
        private var bukkitTask: BukkitTask? = null
        private val applyDamagePlayerDatas: MutableMap<EntityData, Int> = mutableMapOf()

        override val name = "<bold>눈폭풍"
        override val description = listOf(
            "<gray>사용 시 활성화되고 다시 사용 시 비활성화되는 스킬.",
            "<gray>활성화 시 초당 {keyword:Mana}를 10 소모힌다.",
            "",
            "<gray>자신 주위 모든 적에게 초당 2의 피해를 입히고 {keyword:Frostbite}을 2 부여한다.",
            "{keyword:Mana}가 0이 되면 스킬이 강제로 비활성화되며, 자신은 2초간 {keyword:Freezing} 상태가 된다."
        )
        override val cooldown = 1

        override val isOnOffSKill: Boolean = true

        override fun use() {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }

            if (bukkitTask != null) {
                bukkitTask?.cancel()
                bukkitTask = null
                applyDamagePlayerDatas.clear()
                return
            }

            bukkitTask = playerData.trackTask(object : BukkitRunnable() {
                override fun run() {
                    if (mana.power < 10) {
                        cancel()
                        bukkitTask = null
                        return
                    }
                    val targets = playerData.radius(player.location, TargetType.Enemy, 3.0, false)
                    targets.forEach {
                        if (applyDamagePlayerDatas.getOrDefault(it, 0) == 0) return@forEach
                        val frostbiteTarget = it as? PlayerData
                        applyDamagePlayerDatas[it] = applyDamagePlayerDatas.getOrDefault(it, 20) - 1
                        it.damage(3.0, DamageType.Normal, playerData)
                        if (frostbiteTarget != null) {
                            val frostbite = frostbiteTarget.getOrCreateStatus(playerData) { Frostbite() }
                            frostbite.applyStatus(duration = 5, powerDelta = 2)
                        }
                    }
                    mana.decreasePower(10)
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
        }
    }

    private class Passive : BasePassive(), OnHitHandler, WhenHitHandler {
        override val name = "<bold>극저온"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>스킬 적중 시 5초간 적중한 적 주위에 접근 시 <gold><bold>이동 속도가 25% 감소</bold><gray>하는 영역을 생성한다.",
            "<gray>영역의 영향을 받은 적에게 {keyword:Frostbite}을 2 부여한다.",
            "<gray>이 효과는 영역 당 같은 대상에게 1번만 발동할 수 있다."
        )

        override fun onSkillAttackHit(event: DamageContext) {
            FrostZone(event.target.entity.location.clone()).spawnFlooring(playerData)
        }
    }

    private class FrostZone(override var location: Location) : Flooring() {
        override var radius: Double = 4.0
        override var targetType: TargetType = TargetType.Enemy
        override var time: Int? = 100

        private val hitPlayerDatas: MutableList<PlayerData> = mutableListOf()

        override fun onFlooringEntityHit(hitEntityData: EntityData, location: Location) {
            val hitPlayerData = hitEntityData as? PlayerData ?: return
            if (hitPlayerData in hitPlayerDatas) return
            val moveSpeedDecrease = hitPlayerData.addStatus(MoveSpeedDecrease(), playerData)
            val frostbite = hitPlayerData.getOrCreateStatus(playerData) { Frostbite() }
            moveSpeedDecrease.increasePower(25)
            frostbite.applyStatus(duration = 5, powerDelta = 2)
            moveSpeedDecrease.setContinueWhileIf { hitPlayerDatas.contains(hitPlayerData) }
            hitPlayerDatas.add(hitPlayerData)
        }

        override fun onFlooringEntityOut(hitEntityData: EntityData, location: Location) {
            val hitPlayerData = hitEntityData as? PlayerData ?: return
            hitPlayerDatas.remove(hitPlayerData)
        }

        override fun onFlooringEnd() {
            hitPlayerDatas.clear()
        }
    }
}
