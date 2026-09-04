package org.beobma.classWarPlugin.gameClass.list

import net.kyori.adventure.title.Title
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.MapTransferBorderManager
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.UtilManager.miniMessage
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.BlockState
import org.bukkit.entity.Player
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.BoundingBox
import java.time.Duration
import kotlin.math.floor
import kotlin.random.Random

private const val BACKROOM_COOLDOWN_SECONDS = 90
private const val BACKROOM_DURATION_TICKS = 600
private const val BACKROOM_LOGICAL_SIZE = 23
private const val BACKROOM_CELL_SIZE = 3
private const val BACKROOM_ROOF_Y = 6
private const val BACKROOM_MINIMUM_BORDER_SIZE = 19.0

class BackRoom : GameClass(), GameEndHandler, PlayerDeathHandler {
    override val classId = "back-room"
    override val name = "<gray>백룸"
    override val rank = Rank.A
    override val classItemMaterial = Material.YELLOW_CONCRETE
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives = emptyList<org.beobma.classWarPlugin.skill.Passive>()

    private data class Session(
        val target: EntityData,
        val returnLocation: Location,
        val snapshots: List<BlockState>,
        val exitBox: BoundingBox,
        val borderExpansion: MapTransferBorderManager.Expansion,
        var task: BukkitTask? = null,
    )
    private data class MazeCell(val x: Int, val z: Int)
    private data class MazeLayout(
        val passages: Array<BooleanArray>,
        val entrance: MazeCell,
        val exit: MazeCell,
    )

    private var session: Session? = null

    override fun onGameEnd() = finishSession(escaped = true, playEffects = false)
    override fun onPlayerDeath() = finishSession(escaped = true, playEffects = false)

    private inner class RedSkill : Skill() {
        override val definitionId = "back-room/red-skill"
        override val name = "<bold>백룸"
        override val description = listOf(
            "<gray>10칸 내의 바라보는 적을 30초간 백룸으로 보낸다.",
            "<gray>백룸에는 상당히 넓고 복잡한 미로가 있으며, 미로를 탈출하면 원래 자리로 돌아온다.",
            "<gray>입구와 출구는 매번 무작위로 정해지며 서로 되도록 멀리 배치된다.",
            "<gray>탈출하지 못하면 6의 피해를 입는다."
        )
        override val cooldown = BACKROOM_COOLDOWN_SECONDS
        private var selectedTarget: EntityData? by requestValue { null }

        override fun isUseSuccess(): Boolean {
            if (player.world.worldBorder.size < BACKROOM_MINIMUM_BORDER_SIZE) {
                player.sendMiniMessage("<red><bold>[!] 현재 월드보더 안에는 백룸을 생성할 공간이 없습니다.")
                return false
            }
            if (session != null) {
                player.sendMiniMessage("<red><bold>[!] 이미 백룸이 사용 중입니다.")
                return false
            }
            selectedTarget = playerData.shotLaserGetEntityData(10.0, TargetType.Enemy, false)
            if (selectedTarget == null && PlayerTagManager.isTraining(player)) {
                selectedTarget = playerData
                player.sendMiniMessage("<gold><bold>[백룸 훈련]</bold> <gray>자기 자신을 백룸으로 보냅니다.")
            }
            if (selectedTarget == null) player.sendMiniMessage("<red><bold>[!] 10칸 내에 바라보는 적이 없습니다.")
            return selectedTarget != null
        }

        override fun use(): Boolean {
            val target = selectedTarget ?: return false
            selectedTarget = null
            startSession(target)
            return true
        }
    }

    private fun startSession(target: EntityData) {
        val world = player.world
        val border = world.worldBorder
        val allowedPhysicalSize = floor(border.size - 4.0).toInt()
        val allowedLogicalSize = (allowedPhysicalSize / BACKROOM_CELL_SIZE).coerceAtMost(BACKROOM_LOGICAL_SIZE)
        val mazeSize = (if (allowedLogicalSize % 2 == 0) allowedLogicalSize - 1 else allowedLogicalSize)
            .coerceAtLeast(5)
        val physicalSize = mazeSize * BACKROOM_CELL_SIZE
        val minX = floor(border.center.x - border.size / 2.0 + 2.0).toInt()
        val maxX = floor(border.center.x + border.size / 2.0 - physicalSize - 2.0).toInt()
        val minZ = floor(border.center.z - border.size / 2.0 + 2.0).toInt()
        val maxZ = floor(border.center.z + border.size / 2.0 - physicalSize - 2.0).toInt()
        val desiredX = target.entity.location.blockX - physicalSize / 2
        val desiredZ = target.entity.location.blockZ - physicalSize / 2
        val originX = if (minX <= maxX) desiredX.coerceIn(minX, maxX) else minX
        val originZ = if (minZ <= maxZ) desiredZ.coerceIn(minZ, maxZ) else minZ
        val originY = world.maxHeight - BACKROOM_ROOF_Y - 2
        val layout = generateMazeLayout(mazeSize)
        val snapshots = ArrayList<BlockState>(physicalSize * physicalSize * (BACKROOM_ROOF_Y + 1))

        for (x in 0 until physicalSize) for (z in 0 until physicalSize) for (y in 0..BACKROOM_ROOF_Y) {
            val block = world.getBlockAt(originX + x, originY + y, originZ + z)
            snapshots += block.state
            val mazeX = x / BACKROOM_CELL_SIZE
            val mazeZ = z / BACKROOM_CELL_SIZE
            val isPassage = layout.passages[mazeX][mazeZ]
            val isExit = mazeX == layout.exit.x && mazeZ == layout.exit.z
            val isCellCenter = x % BACKROOM_CELL_SIZE == BACKROOM_CELL_SIZE / 2 &&
                z % BACKROOM_CELL_SIZE == BACKROOM_CELL_SIZE / 2
            when {
                y == 0 -> block.setType(if (isExit) Material.EMERALD_BLOCK else Material.YELLOW_CONCRETE, false)
                y == BACKROOM_ROOF_Y -> block.setType(
                    if (isPassage && isCellCenter && (mazeX + mazeZ) % 6 == 0) Material.GLOWSTONE
                    else Material.YELLOW_CONCRETE,
                    false,
                )
                isPassage -> block.setType(Material.AIR, false)
                else -> block.setType(Material.YELLOW_CONCRETE, false)
            }
        }

        val entrance = Location(world,
            originX + layout.entrance.x * BACKROOM_CELL_SIZE + BACKROOM_CELL_SIZE / 2.0,
            originY + 1.0,
            originZ + layout.entrance.z * BACKROOM_CELL_SIZE + BACKROOM_CELL_SIZE / 2.0,
            target.entity.location.yaw, target.entity.location.pitch)
        val exitMinX = originX + layout.exit.x * BACKROOM_CELL_SIZE
        val exitMinZ = originZ + layout.exit.z * BACKROOM_CELL_SIZE
        val exitBox = BoundingBox(
            exitMinX + 0.15, originY + 0.8, exitMinZ + 0.15,
            exitMinX + BACKROOM_CELL_SIZE - 0.15, originY + BACKROOM_ROOF_Y - 0.2,
            exitMinZ + BACKROOM_CELL_SIZE - 0.15,
        )
        val borderExpansion = MapTransferBorderManager.expandToMaximum(world)
        val created = Session(target, target.entity.location.clone(), snapshots, exitBox, borderExpansion)
        session = created
        target.entity.teleport(entrance)
        (target as? PlayerData)?.player?.showTitle(Title.title(
            miniMessage.deserialize("<yellow><bold>THE BACKROOMS"),
            miniMessage.deserialize("<gray>30초 안에 초록색 출구를 찾으세요."),
            Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(400)),
        ))
        sounds.play(entrance, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, volume = 1.0f, pitch = 0.55f)
        particles.spawn(entrance, Particle.PORTAL, count = 70, spread = 1.0, speed = 0.22)

        created.task = object : BukkitRunnable(abilityScope) {
            var tick = 0
            override fun run() {
                if (session !== created) {
                    cancel()
                    return
                }
                val entity = target.entity
                val offline = entity is Player && !entity.isOnline
                if (!entity.isValid || offline || target.entityStatus.isDead || playerStatus.isDead) {
                    finishSession(escaped = true, playEffects = false)
                    cancel()
                    return
                }
                if (entity.boundingBox.overlaps(exitBox)) {
                    finishSession(escaped = true, playEffects = true)
                    cancel()
                    return
                }
                if (tick >= BACKROOM_DURATION_TICKS) {
                    finishSession(escaped = false, playEffects = true)
                    cancel()
                    return
                }
                if (tick % 10 == 0) {
                    val exit = exitBox.center.toLocation(world)
                    particles.spawn(exit, Particle.HAPPY_VILLAGER, count = 8, spread = 0.7, speed = 0.02)
                    if (tick >= 400) sounds.playTo((entity as? Player) ?: player, Sound.BLOCK_NOTE_BLOCK_HAT,
                        volume = 0.25f, pitch = 1.5f + ((tick - 400) / 200f))
                }
                tick++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L).also(playerData::trackTask)
    }

    private fun finishSession(escaped: Boolean, playEffects: Boolean) {
        val active = session ?: return
        session = null
        active.task?.cancel()
        active.snapshots.asReversed().forEach { state -> state.update(true, false) }
        val target = active.target
        if (target.entity.isValid) target.entity.teleport(active.returnLocation)
        active.borderExpansion.restore()
        if (playEffects) {
            val location = active.returnLocation
            particles.spawn(location.add(0.0, 1.0, 0.0), if (escaped) Particle.TOTEM_OF_UNDYING else Particle.LARGE_SMOKE,
                count = if (escaped) 32 else 45, spread = 0.8, speed = 0.12)
            sounds.play(location, if (escaped) Sound.UI_TOAST_CHALLENGE_COMPLETE else Sound.ENTITY_ELDER_GUARDIAN_CURSE,
                volume = 0.9f, pitch = if (escaped) 1.3f else 0.7f)
        }
        if (!escaped && !target.entityStatus.isDead && !playerStatus.isDead) {
            target.damage(6.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
        }
    }

    private fun generateMazeLayout(size: Int): MazeLayout {
        val cells = buildList {
            for (x in 1 until size - 1 step 2) {
                for (z in 1 until size - 1 step 2) add(MazeCell(x, z))
            }
        }
        val exit = cells.random(Random)
        val maximumDistance = cells.maxOf { kotlin.math.abs(it.x - exit.x) + kotlin.math.abs(it.z - exit.z) }
        val distantEntrances = cells.filter { cell ->
            cell != exit && kotlin.math.abs(cell.x - exit.x) + kotlin.math.abs(cell.z - exit.z) >= maximumDistance * 0.7
        }
        val entrance = distantEntrances.randomOrNull(Random)
            ?: cells.filter { it != exit }.maxBy { kotlin.math.abs(it.x - exit.x) + kotlin.math.abs(it.z - exit.z) }
        val passages = Array(size) { BooleanArray(size) }
        fun carve(x: Int, z: Int) {
            passages[x][z] = true
            val directions = listOf(2 to 0, -2 to 0, 0 to 2, 0 to -2).shuffled(Random)
            directions.forEach { (dx, dz) ->
                val nx = x + dx
                val nz = z + dz
                if (nx !in 1 until size - 1 || nz !in 1 until size - 1 || passages[nx][nz]) return@forEach
                passages[x + dx / 2][z + dz / 2] = true
                carve(nx, nz)
            }
        }
        carve(entrance.x, entrance.z)
        passages[exit.x][exit.z] = true
        return MazeLayout(passages, entrance, exit)
    }
}
