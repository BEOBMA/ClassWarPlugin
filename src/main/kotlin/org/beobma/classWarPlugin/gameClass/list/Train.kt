package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.gameClass.handler.SneakInputHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.SkillManager.getTargetCandidates
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Silence
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.BoundingBox
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.atan2

private const val TRAIN_STATION_COOLDOWN_SECONDS = 10
private const val TRAIN_RIDE_COOLDOWN_SECONDS = 30
private const val TRAIN_MAX_STATIONS = 8

class Train : GameClass(), SneakInputHandler, WhenHitHandler, GameEndHandler, PlayerDeathHandler {
    override val name = "<gray>기차"
    override val rank = Rank.A
    override val classItemMaterial = Material.RAIL
    override var skills: List<Skill> = listOf(RedSkill(), OrangeSkill())
    override var passives = emptyList<org.beobma.classWarPlugin.skill.Passive>()

    private data class Station(val location: Location, val display: BlockDisplay)
    private data class PassengerState(val data: PlayerData, val canMove: Boolean, val canSkillUse: Boolean)

    private val stations = mutableListOf<Station>()
    private val railDisplays = mutableListOf<BlockDisplay>()
    private val passengers = mutableMapOf<UUID, PassengerState>()
    private var rideTask: BukkitTask? = null
    private var trainDisplay: ItemDisplay? = null
    private var disembarkRequested = false

    override fun onPlayerToggleSneak(event: PlayerToggleSneakEvent) {
        if (event.isSneaking && rideTask != null) disembarkRequested = true
    }

    override fun whenHit(context: DamageContext) {
        if (rideTask != null && context.attacker.uniqueId in passengers) context.addDamageTakenMultiplier(0.1)
    }

    override fun onGameEnd() = clearAll()
    override fun onPlayerDeath() = clearAll()

    private inner class RedSkill : Skill() {
        override val name = "<bold>기차역"
        override val description = listOf(
            "<gray>사용 시 현재 블록에 기차역을 설치한다.",
            "<gray>기차역이 2개 이상 있다면 두 기차역은 선로로 연결된다."
        )
        override val cooldown = TRAIN_STATION_COOLDOWN_SECONDS

        override fun isUseSuccess(): Boolean {
            if (rideTask != null) {
                player.sendMiniMessage("<red><bold>[!] 기차 이동 중에는 역을 설치할 수 없습니다.")
                return false
            }
            return true
        }

        override fun use() {
            val location = player.location.block.location.add(0.0, 0.06, 0.0)
            stations.removeAll { station ->
                if (HitboxUtil.distanceSquared(player.boundingBox, station.location.toVector()) > 2.25) return@removeAll false
                station.display.remove()
                true
            }
            if (stations.size >= TRAIN_MAX_STATIONS) stations.removeFirst().display.remove()
            val display = player.world.spawn(location, BlockDisplay::class.java).apply {
                block = Material.POWERED_RAIL.createBlockData()
                billboard = Display.Billboard.FIXED
                brightness = Display.Brightness(15, 15)
                isPersistent = false
            }
            TemporaryDisplayManager.mark(display, player.uniqueId)
            stations += Station(location, display)
            rebuildRails()
            particles.spawn(location, Particle.ELECTRIC_SPARK, count = 20, spread = 0.75, speed = 0.08)
            sounds.play(location, Sound.BLOCK_BELL_USE, volume = 0.8f, pitch = 1.25f)
        }
    }

    private inner class OrangeSkill : Skill() {
        override val name = "<bold>기차놀이"
        override val description = listOf(
            "<gray>기차역에 서 있을 때에만 사용할 수 있다.", "",
            "<gray>바라보는 방향으로 기차를 타고 다음 역까지 이동한다.",
            "<gray>이동 중 충돌한 적은 기차에 강제로 태운다.",
            "<gray>열차에 태워진 적은 {keyword:Silence} 상태가 되며, 자신에게 가하는 피해가 90% 감소한다.",
            "<gray>자신은 웅크리면 기차에서 내릴 수 있다."
        )
        override val cooldown = TRAIN_RIDE_COOLDOWN_SECONDS
        private var route: Pair<Station, Station>? = null

        override fun isUseSuccess(): Boolean {
            if (rideTask != null || stations.size < 2) {
                player.sendMiniMessage("<red><bold>[!] 연결된 기차역이 부족하거나 이미 이동 중입니다.")
                return false
            }
            val current = stations.minByOrNull { HitboxUtil.distanceSquared(player.boundingBox, it.location.toVector()) }
            if (current == null || HitboxUtil.distanceSquared(player.boundingBox, current.location.toVector()) > 2.25) {
                player.sendMiniMessage("<red><bold>[!] 기차역 위에서만 사용할 수 있습니다.")
                return false
            }
            val view = player.location.direction.clone().setY(0.0).normalize()
            val candidates = stations.filter { it !== current }
            val next = candidates.maxByOrNull { station ->
                val direction = station.location.toVector().subtract(current.location.toVector()).setY(0.0)
                if (direction.lengthSquared() < 1.0E-8) -2.0 else view.dot(direction.normalize())
            } ?: return false
            route = current to next
            return true
        }

        override fun use() {
            val selected = route ?: return
            route = null
            beginRide(selected.first.location, selected.second.location)
        }
    }

    private fun beginRide(from: Location, to: Location) {
        stopRide()
        disembarkRequested = false
        val delta = to.toVector().subtract(from.toVector())
        val steps = ceil(delta.length() / 0.72).toInt().coerceAtLeast(1)
        val step = delta.multiply(1.0 / steps)
        val display = player.world.spawn(from.clone().add(0.0, 0.35, 0.0), ItemDisplay::class.java).apply {
            setItemStack(ItemStack(Material.MINECART))
            itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
            billboard = Display.Billboard.FIXED
            brightness = Display.Brightness(15, 15)
            isPersistent = false
        }
        TemporaryDisplayManager.mark(display, player.uniqueId)
        trainDisplay = display
        sounds.play(from, Sound.ENTITY_MINECART_RIDING, volume = 1.0f, pitch = 0.72f)
        rideTask = object : BukkitRunnable() {
            var index = 0
            override fun run() {
                if (index > steps || disembarkRequested || !player.isOnline || playerStatus.isDead) {
                    stopRide()
                    cancel()
                    return
                }
                val location = from.clone().add(step.clone().multiply(index.toDouble()))
                player.teleport(location.clone().apply { yaw = player.location.yaw; pitch = player.location.pitch })
                display.teleport(location.clone().add(0.0, 0.35, 0.0))
                val trainBox = BoundingBox(location.x - 1.1, location.y - 0.15, location.z - 1.1,
                    location.x + 1.1, location.y + 1.65, location.z + 1.1)
                playerData.getTargetCandidates().filterIsInstance<PlayerData>()
                    .filter { it != playerData && playerData.isEnemyOf(it) && !it.entityStatus.isDead }
                    .filter { it.player.world == player.world && it.player.boundingBox.overlaps(trainBox) }
                    .forEach(::capturePassenger)
                passengers.values.forEach { passenger ->
                    passenger.data.player.teleport(location.clone().add(0.0, 0.15, 0.0).apply {
                        yaw = passenger.data.player.location.yaw
                        pitch = passenger.data.player.location.pitch
                    })
                    if (index % 10 == 0) {
                        passenger.data.getOrCreateStatus(playerData) { Silence() }
                            .applyStatus(duration = 2, powerSet = 1)
                    }
                }
                particles.spawn(location, Particle.CAMPFIRE_COSY_SMOKE, count = 3, spread = 0.28, speed = 0.025)
                particles.spawn(location, Particle.ELECTRIC_SPARK, count = 2, spread = 0.5, speed = 0.03)
                if (index % 8 == 0) sounds.play(location, Sound.BLOCK_CHAIN_PLACE, volume = 0.35f, pitch = 0.75f)
                index++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L).also(playerData::trackTask)
    }

    private fun capturePassenger(target: PlayerData) {
        if (passengers.containsKey(target.uniqueId)) return
        passengers[target.uniqueId] = PassengerState(target, target.entityStatus.canMove, target.entityStatus.canSkillUse)
        target.entityStatus.canMove = false
        target.entityStatus.canSkillUse = false
        target.addStatus(Silence(), playerData).applyStatus(duration = 2, powerSet = 1)
        particles.spawn(target.player, Particle.POOF, count = 14, spread = 0.45, speed = 0.07)
        sounds.play(target.player, Sound.ENTITY_MINECART_INSIDE, volume = 0.8f, pitch = 0.8f)
    }

    private fun rebuildRails() {
        railDisplays.forEach(BlockDisplay::remove)
        railDisplays.clear()
        if (stations.size < 2) return
        stations.forEachIndexed { index, station ->
            val direction = when (index) {
                0 -> stations[1].location.toVector().subtract(station.location.toVector())
                stations.lastIndex -> station.location.toVector().subtract(stations[index - 1].location.toVector())
                else -> stations[index + 1].location.toVector().subtract(stations[index - 1].location.toVector())
            }
            orientRail(station.display, direction)
        }
        stations.zipWithNext().forEach { (start, end) ->
            val delta = end.location.toVector().subtract(start.location.toVector())
            val points = ceil(delta.length()).toInt().coerceAtLeast(1)
            val step = delta.clone().multiply(1.0 / points)
            repeat(points + 1) { index ->
                val location = start.location.clone().add(step.clone().multiply(index.toDouble()))
                val rail = player.world.spawn(location, BlockDisplay::class.java).apply {
                    block = Material.RAIL.createBlockData()
                    billboard = Display.Billboard.FIXED
                    brightness = Display.Brightness(15, 15)
                    isPersistent = false
                }
                orientRail(rail, step)
                TemporaryDisplayManager.mark(rail, player.uniqueId)
                railDisplays += rail
            }
            particles.line(start.location.clone().add(0.0, 0.12, 0.0), end.location.clone().add(0.0, 0.12, 0.0), Particle.END_ROD, 0.8)
        }
    }

    /** 기본 남북 방향의 레일 모델을 실제 연결 선분의 접선 방향으로 회전한다. */
    private fun orientRail(display: BlockDisplay, direction: Vector) {
        if (direction.lengthSquared() < 1.0E-8) return
        val horizontal = kotlin.math.sqrt(direction.x * direction.x + direction.z * direction.z)
        val yaw = atan2(direction.x, direction.z).toFloat()
        val pitch = -atan2(direction.y, horizontal).toFloat()
        val rotation = Quaternionf().rotateY(yaw).rotateX(pitch)
        val scale = Vector3f(1.04f, 1.0f, 1.04f)
        val center = Vector3f(0.5f, 0.0625f, 0.5f)
        val transformedCenter = Vector3f(center).mul(scale)
        rotation.transform(transformedCenter)
        val translation = Vector3f(center).sub(transformedCenter)
        display.transformation = Transformation(
            translation,
            rotation,
            scale,
            Quaternionf(),
        )
    }

    private fun stopRide() {
        rideTask?.cancel()
        rideTask = null
        trainDisplay?.remove()
        trainDisplay = null
        passengers.values.forEach { state ->
            state.data.entityStatus.canMove = state.canMove
            state.data.entityStatus.canSkillUse = state.canSkillUse
        }
        passengers.clear()
        disembarkRequested = false
    }

    private fun clearAll() {
        stopRide()
        stations.forEach { it.display.remove() }
        railDisplays.forEach(BlockDisplay::remove)
        stations.clear()
        railDisplays.clear()
    }
}
