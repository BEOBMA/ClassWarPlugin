package org.beobma.classWarPlugin.growth

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.game.GamePhase
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.scheduler.BukkitRunnable
import java.util.concurrent.CompletableFuture
import kotlin.math.floor

/** Async chunk loading, main-thread snapshots, and bounded off-thread analysis. */
class GrowthTerrain(private val game: Game, private val world: World) : AutoCloseable {
    @Volatile private var closed = false
    private var worker: CompletableFuture<RegionLayout>? = null
    fun prepare(done: (RegionLayout?, String?) -> Unit) {
        val size = game.settings.borderInitialSize.toInt()
        val ox = floor(game.roundCenterX - size / 2.0).toInt()
        val oz = floor(game.roundCenterZ - size / 2.0).toInt()
        val positions = buildList { for (z in (oz shr 4)..((oz + size - 1) shr 4))
            for (x in (ox shr 4)..((ox + size - 1) shr 4)) add(x to z) }
        val snapshots = mutableMapOf<Pair<Int, Int>, ChunkSnapshot>()
        val pending = mutableMapOf<Pair<Int, Int>, CompletableFuture<Chunk>>()
        var cursor = 0
        var ticks = 0
        val task = object : BukkitRunnable() {
            override fun run() {
                if (closed || game.phase != GamePhase.COUNTDOWN) { cancel(); return }
                if (++ticks > 20 * 180) { cancel(); done(null, "지역 준비가 180초를 초과했습니다. 맵 크기를 줄이거나 청크를 미리 생성해 주세요."); return }
                try {
                    for ((position, future) in pending.toMap()) if (future.isDone) {
                        val chunk = future.join()
                        snapshots[position] = chunk.getChunkSnapshot(true, true, false)
                        pending.remove(position)
                    }
                    repeat((4 - pending.size).coerceAtLeast(0)) {
                        if (cursor < positions.size) {
                            val position = positions[cursor++]
                            pending[position] = world.getChunkAtAsync(position.first, position.second, true)
                        }
                    }
                    if (cursor == positions.size && pending.isEmpty()) {
                        cancel()
                        val immutable = snapshots.toMap()
                        val minHeight = world.minHeight
                        val maxHeight = world.maxHeight
                        val seed = kotlin.random.Random.nextInt()
                        worker = CompletableFuture.supplyAsync {
                            val surface = sample(ox, oz, size, minHeight, maxHeight, immutable) { closed }
                            RegionGenerator.generate(surface, game.settings.growth,
                                kotlin.math.ceil(game.settings.borderMinimumSize).toInt().coerceAtLeast(2), seed) { closed }
                        }
                        worker!!.whenComplete { result, error ->
                            val plugin = ClassWarPlugin.instance
                            if (plugin.isEnabled) org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                                if (!closed && game.phase == GamePhase.COUNTDOWN) done(result,
                                    error?.let { it.cause?.message ?: it.message ?: "지역 생성 실패" })
                            })
                        }
                    }
                } catch (error: Exception) {
                    cancel(); done(null, "지형 준비 실패: ${error.cause?.message ?: error.message}")
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L)
        game.tasks.add(task)
    }
    override fun close() { closed = true; worker?.cancel(true) }

    companion object {
        private val hazards = setOf(Material.WATER, Material.LAVA, Material.CACTUS, Material.MAGMA_BLOCK,
            Material.FIRE, Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW,
            Material.SWEET_BERRY_BUSH)
        fun safeFloor(material: Material) = material.isSolid && material !in hazards &&
            !material.name.endsWith("_LEAVES") && !material.name.endsWith("_LOG") &&
            !material.name.endsWith("_FENCE") && !material.name.endsWith("_WALL")
        fun clear(material: Material) = !material.isSolid && material !in hazards
        private fun sample(ox: Int, oz: Int, size: Int, minY: Int, maxY: Int,
            chunks: Map<Pair<Int, Int>, ChunkSnapshot>, cancelled: () -> Boolean): SurfaceSnapshot {
            val heights = IntArray(size * size) { SurfaceSnapshot.BLOCKED }
            val terrain = Array(size * size) { "plains" }
            val water = BooleanArray(size * size)
            for (z in 0 until size) for (x in 0 until size) {
                if (cancelled() || Thread.currentThread().isInterrupted) error("지역 생성 취소")
                val wx = ox + x; val wz = oz + z; val index = z * size + x
                val chunk = chunks.getValue((wx shr 4) to (wz shr 4))
                val cx = wx and 15; val cz = wz and 15
                val top = chunk.getHighestBlockYAt(cx, cz).coerceIn(minY, maxY - 3)
                var sawTree = false
                for (y in top downTo maxOf(minY, top - 32)) {
                    val material = chunk.getBlockType(cx, y, cz)
                    if (material == Material.WATER) { water[index] = true; break }
                    if (material.name.endsWith("_LEAVES") || material.name.endsWith("_LOG")) sawTree = true
                    if (!safeFloor(material)) continue
                    if (clear(chunk.getBlockType(cx, y + 1, cz)) && clear(chunk.getBlockType(cx, y + 2, cz))) {
                        heights[index] = y + 1
                        val biome = chunk.getBiome(cx, y, cz).key.key
                        terrain[index] = when {
                            sawTree || "forest" in biome || "jungle" in biome || "taiga" in biome -> "forest"
                            "beach" in biome -> "beach"
                            "snow" in biome || "frozen" in biome -> "snow"
                            "desert" in biome || material == Material.SAND -> "desert"
                            "peak" in biome || "mountain" in biome || y > 110 -> "mountain"
                            material.name.contains("BRICK") || material.name.endsWith("PLANKS") -> "ruins"
                            else -> "plains"
                        }
                    }
                    break
                }
            }
            for (z in 0 until size) for (x in 0 until size) {
                val i = z * size + x
                if (heights[i] == SurfaceSnapshot.BLOCKED || terrain[i] == "forest") continue
                if (listOf(x - 8 to z, x + 8 to z, x to z - 8, x to z + 8).any { (a, b) ->
                        a in 0 until size && b in 0 until size && water[b * size + a] }) terrain[i] = "beach"
            }
            return SurfaceSnapshot(ox, oz, size, heights, terrain)
        }
    }
}
