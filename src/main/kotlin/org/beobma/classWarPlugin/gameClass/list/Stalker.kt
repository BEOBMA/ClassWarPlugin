package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.min

class Stalker : GameClass(), GameStatusHandler {
    override val name = "<gray>스토커"
    override val rank = Rank.B
    override val classItemMaterial = Material.SCULK_SENSOR
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private data class Trail(val location: org.bukkit.Location, val createdAt: Long, val display: BlockDisplay)
    private val trails = mutableListOf<Trail>()
    private var target: PlayerData? = null
    private var absorbedTrails = 0

    override fun onBattleStart() {
        trails.forEach { it.display.remove() }
        trails.clear()
        absorbedTrails = 0
        target = game.playerDatas.filterIsInstance<PlayerData>()
            .filter { it != playerData && !it.entityStatus.isDead }
            .randomOrNull()
        playerData.getOrCreateStatus(playerData) { StalkerTrailStatus() }.updatePower(0)
        val stalkingTarget = target ?: return
        var lastTrailLocation = stalkingTarget.player.location.clone()
        playerData.trackTask(object : BukkitRunnable() {
            var tick = 0
            override fun run() {
                if (!player.isOnline || playerStatus.isDead || stalkingTarget.entityStatus.isDead) {
                    trails.forEach { it.display.remove() }
                    trails.clear()
                    cancel()
                    return
                }
                val now = player.world.fullTime
                trails.toList().filter { now - it.createdAt >= 200L || !it.display.isValid }.forEach {
                    it.display.remove()
                    trails.remove(it)
                }
                if (stalkingTarget.player.location.distanceSquared(lastTrailLocation) >= 1.0 && tick % 5 == 0) {
                    createTrail(stalkingTarget.player.location.clone())
                    lastTrailLocation = stalkingTarget.player.location.clone()
                }
                trails.toList().forEach { trail ->
                    if (HitboxUtil.intersectsSphere(player.boundingBox, trail.location.toVector(), 0.95)) absorbTrail(trail)
                }
                if (tick % 10 == 0) showPrivatePath(stalkingTarget)
                tick++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }
    override fun onGameTimePasses() = Unit

    private fun createTrail(location: org.bukkit.Location) {
        location.y += 0.04
        val display = location.world.spawn(location, BlockDisplay::class.java).apply {
            block = Material.SCULK.createBlockData()
            brightness = Display.Brightness(8, 8)
            isPersistent = false
            isVisibleByDefault = false
            transformation = Transformation(
                Vector3f(-0.3f, 0f, -0.3f), Quaternionf(), Vector3f(0.6f, 0.035f, 0.6f), Quaternionf()
            )
        }
        TemporaryDisplayManager.mark(display, player.uniqueId)
        player.showEntity(ClassWarPlugin.instance, display)
        trails += Trail(location, player.world.fullTime, display)
    }

    private fun absorbTrail(trail: Trail) {
        if (!trails.remove(trail)) return
        trail.display.remove()
        absorbedTrails++
        playerData.getOrCreateStatus(playerData) { StalkerTrailStatus() }.updatePower(absorbedTrails)
        particles.spawn(player, Particle.SCULK_SOUL, count = 18, spread = 0.45, speed = 0.09)
        sounds.play(player, Sound.BLOCK_SCULK_CATALYST_BLOOM, volume = 0.55f, pitch = 1.35f)
    }

    private fun showPrivatePath(stalkingTarget: PlayerData) {
        if (stalkingTarget.player.world != player.world) return
        val start = player.eyeLocation.toVector()
        val end = stalkingTarget.player.boundingBox.center
        val difference = end.clone().subtract(start)
        val length = difference.length()
        if (length < 0.1) return
        val direction = difference.normalize()
        var distance = 0.0
        while (distance <= min(length, 48.0)) {
            val point = start.clone().add(direction.clone().multiply(distance)).toLocation(player.world)
            player.spawnParticle(Particle.SCULK_SOUL, point, 1, 0.0, 0.0, 0.0, 0.0)
            distance += 1.25
        }
    }

    private inner class Passive : BasePassive(), OnHitHandler {
        override val name = "<bold>스토킹"
        override val description = listOf(
            "<gray>패시브", "", "<gray>게임 시작 시 무작위 플레이어를 스토킹 대상으로 지정한다.",
            "<gray>자신은 스토킹 대상에게 가는 경로가 입자를 볼 수 있다.",
            "<gray>스토킹 대상은 이동 시 흔적을 남기며, 흔적은 10초간 유지된다.",
            "<gray>흔적에 닿으면 흔적을 흡수하고 흡수한 흔적 양에 비례하여 스토킹 대상에게 가하는 피해가 증가한다."
        )
        override fun onHit(context: DamageContext) {
            if (context.target.entity.uniqueId != target?.uniqueId) return
            context.addDamageDealtMultiplier(1.0 + absorbedTrails.coerceAtMost(30) * 0.04)
        }
    }
}

private class StalkerTrailStatus : StatusAbnormality() {
    override val name = "<dark_aqua><bold>흡수한 흔적</bold><gray>"
    override val description = listOf("<gray>스토킹 대상에게 가하는 피해를 증가시키는 흔적이다.")
    override val canRemove = false
    override val isClassMechanic = true
    override var power = 0
    override var maxPower: Int? = null
    override val showMaxPower = false
    override var duration: Int? = null
}
