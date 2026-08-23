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
import org.beobma.classWarPlugin.status.list.Freezing
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val ICE_WIZARD_SKILL_COOLDOWN_SECONDS = 1
private const val ICE_WIZARD_FREEZING_DURATION_SECONDS = 2
private const val ICE_WIZARD_PROJECTILE_DAMAGE = 2.0
private const val ICE_WIZARD_FROSTBITE_DURATION_SECONDS = 5
private const val ICE_WIZARD_FROSTBITE_POWER = 2

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

    private var isBlizzardActive = false

    override fun onBattleStart() {
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        mana.increasePower(100)
    }

    override fun onGameTimePasses() {
        if (isBlizzardActive) return
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        mana.increasePower(10)
    }

    private inner class RedSkill : Skill() {
        private var bukkitTask: BukkitTask? = null
        private val nextDamageTickByEntity: MutableMap<UUID, Long> = mutableMapOf()

        override val name = "<bold>눈폭풍"
        override val description = listOf(
            "<gray>사용 시 활성화되고 다시 사용 시 비활성화되는 스킬.",
            "<gray>활성화 시 초당 {keyword:Mana}를 10 소모힌다.",
            "",
            "<gray>자신 주위 모든 적에게 초당 2의 피해를 입히고 {keyword:Frostbite}을 2 부여한다.",
            "{keyword:Mana}가 0이 되면 스킬이 강제로 비활성화되며, 자신은 2초간 {keyword:Freezing} 상태가 된다."
        )
        override val cooldown = ICE_WIZARD_SKILL_COOLDOWN_SECONDS

        override val isOnOffSKill: Boolean = true

        override fun use() {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }

            if (bukkitTask != null) {
                bukkitTask?.cancel()
                bukkitTask = null
                isBlizzardActive = false
                nextDamageTickByEntity.clear()
                sounds.play(player, Sound.BLOCK_GLASS_BREAK, pitch = 1.5f)
                return
            }

            isBlizzardActive = true
            bukkitTask = playerData.trackTask(object : BukkitRunnable() {
                private var elapsedTicks = 0

                override fun run() {
                    if (!player.isOnline || player.isDead) {
                        isBlizzardActive = false
                        bukkitTask = null
                        cancel()
                        return
                    }
                    if (elapsedTicks > 0 && elapsedTicks % 20 == 0) {
                        if (mana.power < 10) {
                            playerData.getOrCreateStatus(playerData) { Freezing() }
                                .applyStatus(duration = ICE_WIZARD_FREEZING_DURATION_SECONDS, powerSet = 1)
                            sounds.play(player, Sound.ENTITY_PLAYER_HURT_FREEZE, pitch = 0.7f)
                            isBlizzardActive = false
                            nextDamageTickByEntity.clear()
                            cancel()
                            bukkitTask = null
                            return
                        }
                        mana.decreasePower(10)
                        sounds.play(player, Sound.WEATHER_RAIN, volume = 0.22f, pitch = 1.6f)
                        if (mana.power <= 0) {
                            playerData.getOrCreateStatus(playerData) { Freezing() }
                                .applyStatus(duration = ICE_WIZARD_FREEZING_DURATION_SECONDS, powerSet = 1)
                            sounds.play(player, Sound.ENTITY_PLAYER_HURT_FREEZE, pitch = 0.7f)
                            isBlizzardActive = false
                            nextDamageTickByEntity.clear()
                            cancel()
                            bukkitTask = null
                            return
                        }
                    }

                    val now = player.world.fullTime
                    val targets = playerData.radius(player.location, TargetType.Enemy, 3.0, false)
                    targets.forEach { target ->
                        if (now < nextDamageTickByEntity.getOrDefault(target.entity.uniqueId, Long.MIN_VALUE)) return@forEach
                        nextDamageTickByEntity[target.entity.uniqueId] = now + 20L
                        target.damage(ICE_WIZARD_PROJECTILE_DAMAGE, DamageType.Normal, playerData)
                        target.getOrCreateStatus(playerData) { Frostbite() }.applyStatus(
                            duration = ICE_WIZARD_FROSTBITE_DURATION_SECONDS,
                            powerDelta = ICE_WIZARD_FROSTBITE_POWER,
                        )
                    }
                    particles.circle(player.location, Particle.SNOWFLAKE, 3.0, 14)
                    particles.spawn(player.location.add(0.0, 1.0, 0.0), Particle.SNOWFLAKE, count = 5, spread = 2.0, speed = 0.02)
                    elapsedTicks++
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
        override var time: Int? = 5

        private val affectedEntities: MutableSet<EntityData> = mutableSetOf()

        override fun onFlooringContinue(location: Location) {
            particles.circle(location, Particle.SNOWFLAKE, radius, 28)
        }

        override fun onFlooringEntityHit(hitEntityData: EntityData, location: Location) {
            if (!affectedEntities.add(hitEntityData)) return
            val moveSpeedDecrease = hitEntityData.addStatus(MoveSpeedDecrease(), playerData)
            val frostbite = hitEntityData.getOrCreateStatus(playerData) { Frostbite() }
            moveSpeedDecrease.increasePower(25)
            frostbite.applyStatus(
                duration = ICE_WIZARD_FROSTBITE_DURATION_SECONDS,
                powerDelta = ICE_WIZARD_FROSTBITE_POWER,
            )
            moveSpeedDecrease.setContinueWhileIf { affectedEntities.contains(hitEntityData) }
        }

        override fun onFlooringEntityOut(hitEntityData: EntityData, location: Location) {
            affectedEntities.remove(hitEntityData)
        }

        override fun onFlooringEnd() {
            affectedEntities.clear()
        }
    }
}
