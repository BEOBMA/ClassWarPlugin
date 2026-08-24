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
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.BoundingBox
import java.time.Duration
import kotlin.math.floor
import kotlin.random.Random

private const val BACKROOM_COOLDOWN_SECONDS = 90
private const val BACKROOM_DURATION_TICKS = 600
private const val BACKROOM_SIZE = 17

class BackRoom : GameClass(), GameEndHandler, PlayerDeathHandler {
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
        var task: BukkitTask? = null,
    )

    private var session: Session? = null

    override fun onGameEnd() = finishSession(escaped = true, playEffects = false)
    override fun onPlayerDeath() = finishSession(escaped = true, playEffects = false)

    private inner class RedSkill : Skill() {
        override val name = "<bold>백룸"
        override val description = listOf(
            "<gray>10칸 내의 바라보는 적을 30초간 백룸으로 보낸다.",
            "<gray>백룸에는 상당히 복잡한 미로가 있으며, 미로를 탈출하면 원래 자리로 돌아온다.",
            "<gray>탈출하지 못하면 6의 피해를 입는다."
        )
        override val cooldown = BACKROOM_COOLDOWN_SECONDS
        private var selectedTarget: EntityData? = null

        override fun isUseSuccess(): Boolean {
            if (session != null) {
                player.sendMiniMessage("<red><bold>[!] 이미 백룸이 사용 중입니다.")
                return false
            }
            selectedTarget = playerData.shotLaserGetEntityData(10.0, TargetType.Enemy, false)
            if (selectedTarget == null && PlayerTagManager.hasTag(player, "isTraining")) {
                selectedTarget = playerData
                player.sendMiniMessage("<gold><bold>[백룸 훈련]</bold> <gray>자기 자신을 백룸으로 보냅니다.")
            }
            if (selectedTarget == null) player.sendMiniMessage("<red><bold>[!] 10칸 내에 바라보는 적이 없습니다.")
            return selectedTarget != null
        }

        override fun use() {
            val target = selectedTarget ?: return
            selectedTarget = null
            startSession(target)
        }
    }

    private fun startSession(target: EntityData) {
        val world = player.world
        val border = world.worldBorder
        val half = border.size / 2.0 - BACKROOM_SIZE - 2.0
        val minX = floor(border.center.x - half).toInt()
        val maxX = floor(border.center.x + half - BACKROOM_SIZE).toInt()
        val minZ = floor(border.center.z - half).toInt()
        val maxZ = floor(border.center.z + half - BACKROOM_SIZE).toInt()
        val desiredX = target.entity.location.blockX - BACKROOM_SIZE / 2
        val desiredZ = target.entity.location.blockZ - BACKROOM_SIZE / 2
        val originX = if (minX <= maxX) desiredX.coerceIn(minX, maxX) else target.entity.location.blockX - BACKROOM_SIZE / 2
        val originZ = if (minZ <= maxZ) desiredZ.coerceIn(minZ, maxZ) else target.entity.location.blockZ - BACKROOM_SIZE / 2
        val originY = world.maxHeight - 6
        val passages = generateMaze()
        val snapshots = ArrayList<BlockState>(BACKROOM_SIZE * BACKROOM_SIZE * 5)

        for (x in 0 until BACKROOM_SIZE) for (z in 0 until BACKROOM_SIZE) for (y in 0..4) {
            val block = world.getBlockAt(originX + x, originY + y, originZ + z)
            snapshots += block.state
            when {
                y == 0 -> block.setType(if (x == BACKROOM_SIZE - 2 && z == BACKROOM_SIZE - 2) Material.EMERALD_BLOCK else Material.YELLOW_CONCRETE, false)
                y == 4 -> block.setType(if (passages[x][z] && (x + z) % 6 == 0) Material.GLOWSTONE else Material.YELLOW_CONCRETE, false)
                passages[x][z] -> block.setType(Material.AIR, false)
                else -> block.setType(Material.YELLOW_CONCRETE, false)
            }
        }

        val entrance = Location(world, originX + 1.5, originY + 1.0, originZ + 1.5,
            target.entity.location.yaw, target.entity.location.pitch)
        val exitBox = BoundingBox(
            originX + BACKROOM_SIZE - 2.85, originY + 0.8, originZ + BACKROOM_SIZE - 2.85,
            originX + BACKROOM_SIZE - 1.15, originY + 3.3, originZ + BACKROOM_SIZE - 1.15,
        )
        val created = Session(target, target.entity.location.clone(), snapshots, exitBox)
        session = created
        target.entity.teleport(entrance)
        (target as? PlayerData)?.player?.showTitle(Title.title(
            miniMessage.deserialize("<yellow><bold>THE BACKROOMS"),
            miniMessage.deserialize("<gray>30초 안에 초록색 출구를 찾으세요."),
            Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(400)),
        ))
        sounds.play(entrance, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, volume = 1.0f, pitch = 0.55f)
        particles.spawn(entrance, Particle.PORTAL, count = 70, spread = 1.0, speed = 0.22)

        created.task = object : BukkitRunnable() {
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

    private fun generateMaze(): Array<BooleanArray> {
        val passages = Array(BACKROOM_SIZE) { BooleanArray(BACKROOM_SIZE) }
        fun carve(x: Int, z: Int) {
            passages[x][z] = true
            val directions = listOf(2 to 0, -2 to 0, 0 to 2, 0 to -2).shuffled(Random)
            directions.forEach { (dx, dz) ->
                val nx = x + dx
                val nz = z + dz
                if (nx !in 1 until BACKROOM_SIZE - 1 || nz !in 1 until BACKROOM_SIZE - 1 || passages[nx][nz]) return@forEach
                passages[x + dx / 2][z + dz / 2] = true
                carve(nx, nz)
            }
        }
        carve(1, 1)
        passages[BACKROOM_SIZE - 2][BACKROOM_SIZE - 2] = true
        return passages
    }
}
