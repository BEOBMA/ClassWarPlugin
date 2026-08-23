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
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.beobma.classWarPlugin.manager.SkillManager.radius

// 밸런스 조정 상수
private const val ASTRONOMER_SKILL_COOLDOWN_SECONDS = 30
private const val ASTRONOMER_STAR_DAMAGE = 2.0
private const val ASTRONOMER_TRUE_DAMAGE = 1.0
private const val ASTRONOMER_MANA_PER_METEOR = 20
private const val ASTRONOMER_MAX_METEOR_COUNT = 5
private const val ASTRONOMER_MANA_STEAL = 2
private const val ASTRONOMER_METEOR_EXPLOSION_RADIUS = 1.75

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

    private var blackHoleActiveUntil = 0L

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
        override val cooldown = ASTRONOMER_SKILL_COOLDOWN_SECONDS

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
            playerData.findGameClass(Astronomer::class.java)?.blackHoleActiveUntil = player.world.fullTime + 120L
            sounds.play(blackHole.location, Sound.ENTITY_WITHER_SHOOT, volume = 0.6f, pitch = 0.5f)
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

        override fun onSkillAttackHit(context: DamageContext) {
            if (context.damageType == DamageType.True) return
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            val count = (mana.power / ASTRONOMER_MANA_PER_METEOR).coerceIn(1, ASTRONOMER_MAX_METEOR_COUNT)
            val targetLoc = context.target.entity.location.clone()
            val classData = playerData.findGameClass(Astronomer::class.java)
            val soundAndDisplayEndTick = minOf(
                player.world.fullTime + 60L,
                classData?.blackHoleActiveUntil ?: player.world.fullTime,
            )
            repeat(count) {
                val landingAngle = Random.nextDouble(0.0, Math.PI * 2.0)
                val landingRadius = Random.nextDouble(1.25, 4.5)
                val landing = targetLoc.clone().add(cos(landingAngle) * landingRadius, 0.2, sin(landingAngle) * landingRadius)
                val approach = Vector(Random.nextDouble(-7.0, 7.0), 14.0, Random.nextDouble(-7.0, 7.0))
                val starMeteor = StarMeteor(Vector(-approach.x / 28.0, 0.0, -approach.z / 28.0))
                starMeteor.location = landing.clone().add(approach)
                starMeteor.time = null
                starMeteor.continueWhile = { player.world.fullTime < soundAndDisplayEndTick }
                starMeteor.spawnMeteor(playerData)
                sounds.play(starMeteor.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume = 0.45f, pitch = 1.65f + it * 0.06f)
            }
            mana.updatePower(0)
        }
    }


    private class BlackHole : Flooring() {
        override lateinit var location: Location
        override var radius: Double = 5.0
        override var targetType: TargetType = TargetType.Enemy
        override var time: Int? = 6

        private var visualTick = 0

        override fun onFlooringContinue(location: Location) {
            val tick = visualTick++
            if (tick % 2 == 0) {
                particles.circle(location.clone().add(0.0, 0.25, 0.0), Particle.PORTAL, radius, 28)
                particles.circle(location.clone().add(0.0, 0.65, 0.0), Particle.REVERSE_PORTAL, radius * 0.62, 20)
            }
            repeat(5) { index ->
                val angle = tick * 0.18 + index * Math.PI * 0.4
                val spiralRadius = radius * (1.0 - (tick % 30) / 35.0)
                particles.spawn(
                    location.clone().add(cos(angle) * spiralRadius, 0.25 + index * 0.18, sin(angle) * spiralRadius),
                    Particle.END_ROD,
                    count = 1,
                )
            }
            particles.spawn(
                location.clone().add(0.0, 0.32, 0.0),
                Particle.BLOCK_MARKER,
                Material.BLACK_CONCRETE.createBlockData(),
                ParticleOptions(count = 6, offsetX = 0.38, offsetY = 0.22, offsetZ = 0.38),
            )
            if (tick % 20 == 0) sounds.play(location, Sound.BLOCK_PORTAL_AMBIENT, volume = 0.32f, pitch = 0.55f)
        }

        override fun onFlooringEnd() {
            location.world.players.forEach { viewer ->
                sounds.stop(viewer, Sound.BLOCK_PORTAL_AMBIENT)
                sounds.stop(viewer, Sound.ENTITY_WITHER_SHOOT)
            }
        }

        override fun onFlooringEntityHit(hitEntityData: EntityData, location: Location) {
            val hitEntity = hitEntityData.entity
            val dir = location.clone().subtract(hitEntity.location).toVector().normalize().multiply(0.1)
            hitEntity.velocity = dir
            hitEntityData.damage(ASTRONOMER_STAR_DAMAGE, DamageType.Normal, playerData, false)
            val victimMana = (hitEntityData as? PlayerData)?.getOrCreateStatus(playerData) { Mana() }
            if (victimMana != null && victimMana.power > 0) {
                val stolen = minOf(ASTRONOMER_MANA_STEAL, victimMana.power)
                victimMana.decreasePower(stolen)
                playerData.getOrCreateStatus(playerData) { Mana() }.increasePower(stolen)
            }
        }
    }

    private class EnemyField : Flooring() {
        private val affected = mutableSetOf<EntityData>()

        override lateinit var location: Location
        override var radius: Double = 8.0
        override var targetType: TargetType = TargetType.Enemy
        override var time: Int? = 5

        override fun onFlooringEntityHit(hitEntityData: EntityData, location: Location) {
            hitEntityData.damage(ASTRONOMER_STAR_DAMAGE, DamageType.Normal, playerData, false)
            if (affected.add(hitEntityData)) {
                val moveSpeedDecrease = hitEntityData.addStatus(MoveSpeedDecrease(), playerData)
                val whenDamageIncrease = hitEntityData.addStatus(WhenDamageIncreased(), playerData)
                moveSpeedDecrease.increasePower(20)
                whenDamageIncrease.increasePower(15)
                moveSpeedDecrease.setContinueWhileIf { affected.contains(hitEntityData) }
                whenDamageIncrease.setContinueWhileIf { affected.contains(hitEntityData) }
            }
        }

        override fun onFlooringEntityOut(hitEntityData: EntityData, location: Location) {
            affected.remove(hitEntityData)
        }

        override fun onFlooringEnd() {
            affected.clear()
        }
    }
    private class SelfField : Flooring() {
        private val affected = mutableSetOf<EntityData>()

        override lateinit var location: Location
        override var radius: Double = 8.0
        override var targetType: TargetType = TargetType.Self
        override var time: Int? = 5

        override fun onFlooringEntityHit(hitEntityData: EntityData, location: Location) {
            hitEntityData.heal(2.0, DamageType.Normal, playerData)
            if (affected.add(hitEntityData)) {
                val moveSpeedIncrease = hitEntityData.addStatus(MoveSpeedIncrease(), playerData)
                val whenDamageReduction = hitEntityData.addStatus(WhenDamageReduction(), playerData)
                moveSpeedIncrease.increasePower(20)
                whenDamageReduction.increasePower(15)
                moveSpeedIncrease.setContinueWhileIf { affected.contains(hitEntityData) }
                whenDamageReduction.setContinueWhileIf { affected.contains(hitEntityData) }
            }
        }

        override fun onFlooringEntityOut(hitEntityData: EntityData, location: Location) {
            affected.remove(hitEntityData)
        }

        override fun onFlooringEnd() {
            affected.clear()
        }
    }

    private class StarMeteor(private val horizontalVelocity: Vector) : Meteor() {
        override lateinit var location: Location
        override var speed: Double = 0.5
        override var isWallHit: Boolean = true
        override var targetType: TargetType = TargetType.Enemy
        override var time: Int? = 3

        private var display: BlockDisplay? = null
        private var visualTick = 0

        private fun updateDisplay(location: Location) {
            val current = display ?: location.world.spawn(
                location.clone().add(-0.3, -0.3, -0.3),
                BlockDisplay::class.java,
            ).also { spawned ->
                spawned.block = Material.SEA_LANTERN.createBlockData()
                spawned.isPersistent = false
                TemporaryDisplayManager.mark(spawned, player.uniqueId)
                display = spawned
            }
            current.teleport(location.clone().add(-0.3, -0.3, -0.3))
            current.transformation = Transformation(
                Vector3f(),
                Quaternionf().rotateXYZ(visualTick * 0.08f, visualTick * 0.11f, visualTick * 0.06f),
                Vector3f(0.6f, 0.6f, 0.6f),
                Quaternionf(),
            )
        }

        override fun onMeteorMove(location: Location) {
            location.add(horizontalVelocity)
            updateDisplay(location)
            particles.spawn(location, Particle.END_ROD, count = 1, spread = 0.08)
            if (visualTick++ % 3 == 0) particles.spawn(location, Particle.ELECTRIC_SPARK, count = 1, spread = 0.04)
        }

        override fun onMeteorEntityHit(hitEntityData: EntityData, location: Location) {
            hitEntityData.damage(ASTRONOMER_TRUE_DAMAGE, DamageType.True, playerData)
            particles.spawn(location, Particle.FLASH, count = 1)
            sounds.play(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, pitch = 1.9f)
        }

        override fun onMeteorBlockHit(hitBlock: org.bukkit.block.Block, location: Location) {
            particles.spawn(location, Particle.FLASH, count = 1)
            particles.spawn(location, Particle.END_ROD, count = 18, spread = 1.0, speed = 0.12)
            sounds.play(location, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, volume = 0.65f, pitch = 1.7f)
            playerData.radius(location, TargetType.Enemy, ASTRONOMER_METEOR_EXPLOSION_RADIUS, false).forEach {
                it.damage(ASTRONOMER_TRUE_DAMAGE, DamageType.True, playerData)
            }
        }

        override fun onMeteorEnd(location: Location) {
            display?.remove()
            display = null
        }
    }
}
