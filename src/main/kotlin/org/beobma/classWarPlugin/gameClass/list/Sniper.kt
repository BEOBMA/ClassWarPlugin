package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ability.Targeting

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WeaponInputHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.MoveSpeedDecrease
import org.beobma.classWarPlugin.status.list.SniperAmmoStatus
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.*
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.util.Vector
import kotlin.random.Random

// 밸런스 조정 상수
private const val SNIPER_RELOAD_COOLDOWN_SECONDS = 1
private const val SNIPER_RIFLE_DAMAGE = 7.0
private const val SNIPER_CLOSE_RANGE_BLOCKS = 5.0
private const val SNIPER_CLOSE_SLOW_PERCENT = 20
private const val SNIPER_LONG_SLOW_PERCENT = 5

class Sniper : GameClass(), WeaponInputHandler, GameStatusHandler {
    override val classId = "sniper"
    override val name = "<gray>저격수"
    override val rank = Rank.B
    override val classItemMaterial = Material.SPYGLASS
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private var speedEffect: AutoCloseable? = null
    private fun setSpeed(multiplier: Double) {
        speedEffect?.close()
        speedEffect = if (multiplier == 1.0) null else playerData.attributeEffects.walkSpeed(abilityScope, multiplier)
    }
    override fun onSuspend() { aiming = false; if (!reloading) setSpeed(1.0) }

    private var loaded = true
    private var aiming = false
    private var reloading = false

    private fun ammoStatus(): SniperAmmoStatus =
        playerData.getOrCreateStatus(playerData) { SniperAmmoStatus() }

    override fun onBattleStart() {
        loaded = true
        aiming = false
        reloading = false
        ammoStatus().setLoaded(true)
    }

    override fun onGameTimePasses() {
        if (aiming && !player.isHandRaised) { aiming = false; setSpeed(1.0) }
    }

    private data class ShotTrace(val target: EntityData?, val end: Location)

    private fun traceShot(start: Location, direction: Vector, expansion: Double): ShotTrace {
        val normalized = direction.clone().normalize()
        val maximumRange = ClassBalanceManager.scaleRange(playerData, 64.0)
        val blockHit = start.world.rayTraceBlocks(start, normalized, maximumRange)
        val blockDistance = blockHit?.hitPosition?.distance(start.toVector()) ?: maximumRange
        val targetHit = Targeting.select(
            playerData, TargetType.Enemy, start.world, includeStealth = false,
        ).asSequence()
            .mapNotNull { candidate ->
                HitboxUtil.rayIntersectionDistance(
                    candidate.entity.boundingBox,
                    start.toVector(),
                    normalized,
                    maximumRange,
                    expansion,
                )?.let { distance -> candidate to distance }
            }
            .filter { it.second < blockDistance }
            .minByOrNull { it.second }

        val distance = targetHit?.second ?: blockDistance
        return ShotTrace(targetHit?.first, start.clone().add(normalized.multiply(distance)))
    }

    private fun inaccurateDirection(direction: Vector): Vector {
        val forward = direction.clone().normalize()
        var right = forward.clone().crossProduct(Vector(0.0, 1.0, 0.0))
        if (right.lengthSquared() < 1.0E-6) right = forward.clone().crossProduct(Vector(1.0, 0.0, 0.0))
        right.normalize()
        val up = right.clone().crossProduct(forward).normalize()
        return forward
            .add(right.multiply(Random.nextDouble(-0.065, 0.065)))
            .add(up.multiply(Random.nextDouble(-0.065, 0.065)))
            .normalize()
    }

    override fun onWeaponRightClick(event: PlayerInteractEvent) {
        if (!loaded || reloading) {
            event.isCancelled = true
            player.sendMiniMessage("<red><bold>[!] 탄환이 장전되어 있지 않습니다.")
            sounds.playTo(player, Sound.BLOCK_DISPENSER_FAIL, pitch = 1.4f)
            return
        }
        aiming = true
        setSpeed(0.4)
    }

    override fun onWeaponSwapHand(event: PlayerSwapHandItemsEvent) {
        event.isCancelled = true
        if (!loaded || reloading) {
            player.sendMiniMessage("<red><bold>[!] 탄환이 장전되어 있지 않습니다.")
            return
        }
        val isPreciseShot = aiming && player.isHandRaised
        loaded = false
        ammoStatus().setLoaded(false)
        aiming = false
        setSpeed(1.0)
        val start = player.eyeLocation.clone()
        val shotDirection = if (isPreciseShot) start.direction.normalize() else inaccurateDirection(start.direction)
        val trace = traceShot(start, shotDirection, expansion = if (isPreciseShot) 0.3 else 0.15)
        val target = trace.target
        val end = trace.end
        particles.line(start, end, Particle.ELECTRIC_SPARK, spacing = 0.35)
        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            var tick = 0
            override fun run() {
                if (tick >= 12) {
                    cancel()
                    return
                }
                if (tick % 2 == 0) particles.line(start, end, Particle.END_ROD, spacing = 1.1)
                tick++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
        sounds.play(player, Sound.ENTITY_FIREWORK_ROCKET_BLAST, volume = 1.4f, pitch = 0.65f)
        target?.damage(SNIPER_RIFLE_DAMAGE, DamageType.Normal, playerData, damagePath = DamagePath.RANGED_ATTACK)
    }

    private fun reload() {
        if (loaded || reloading) {
            player.sendMiniMessage("<red><bold>[!] 이미 탄환이 장전되어 있습니다.")
            return
        }
        reloading = true
        ammoStatus().setReloading(2)
        aiming = false
        setSpeed(0.6)
        sounds.play(player, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, pitch = 1.5f)
        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            override fun onCancel() { reloading = false; setSpeed(1.0) }
            var ticksRemaining = 40
            override fun run() {
                if (!player.isOnline || player.isDead) {
                    reloading = false
                    ammoStatus().setLoaded(false)
                    cancel()
                    return
                }
                ticksRemaining--
                ammoStatus().updateReloadTicks(ticksRemaining)
                if (ticksRemaining > 0) return
                loaded = true
                reloading = false
                ammoStatus().setLoaded(true)
                setSpeed(1.0)
                sounds.play(player, Sound.BLOCK_IRON_TRAPDOOR_OPEN, pitch = 1.8f)
                particles.spawn(player, Particle.CRIT, count = 8, spread = 0.3)
                cancel()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L))
    }

    private class Weapon : BaseWeapon() {
        override val name = "<gray>저격총"
        override val description = listOf(
            "<gray>우클릭 시 조준한다.",
            "<gray>탄환이 장전되어 있을 때에만 사용할 수 있다.",
            "<gray>양손들기 키를 누를 시 사용한다.",
            "<gray>조준하지 않고 발사할 수도 있지만 탄도가 무작위로 어긋난다.",
            "",
            "<gray>사용 시 장전된 탄환을 소모하여 바라보는 방향으로 사격한다.",
            "<gray>적중한 적은 7의 피해를 입는다.",
            "",
            "<dark_gray>이 스킬은 기본 공격으로 간주한다."
        )
        override val material = Material.SPYGLASS
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "sniper/red-skill"
        override val name = "<gray><bold>재장전"
        override val description = listOf(
            "<gray>사용 시 저격총을 재장전한다.",
            "<gray>재장전하는 동안 <gold><bold>이동 속도가 40% 감소</bold><gold>한다."
        )
        override val cooldown = SNIPER_RELOAD_COOLDOWN_SECONDS

        override fun use(): Boolean {
            this@Sniper.reload()
            return true
        }

        override fun isUseSuccess(): Boolean = !reloading && !loaded
    }

    private class Passive : BasePassive(), OnHitHandler {
        override val name = "<bold>저지력"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 적중 시 1초간 대상의 <gold><bold>이동 속도가 5% 감소</bold><gray>한다.",
            "<gray>대상과 자신의 거리 차이가 5칸 이내라면 <gold><bold>대신 20% 감소</bold><gray>한다."
        )

        override fun onAttackHit(context: DamageContext) {
            val distance = kotlin.math.sqrt(HitboxUtil.distanceSquared(player.boundingBox, context.target.entity.boundingBox))
            val slow = context.target.addStatus(MoveSpeedDecrease(), playerData)
            slow.applyStatus(
                duration = 1,
                powerSet = if (distance <= SNIPER_CLOSE_RANGE_BLOCKS) {
                    SNIPER_CLOSE_SLOW_PERCENT
                } else {
                    SNIPER_LONG_SLOW_PERCENT
                },
            )
            particles.spawn(context.target.entity, Particle.CRIT, count = 5, spread = 0.25)
        }
    }
}
