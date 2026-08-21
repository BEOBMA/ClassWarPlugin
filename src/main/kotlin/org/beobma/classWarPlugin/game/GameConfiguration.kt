package org.beobma.classWarPlugin.game

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.Rank
import org.bukkit.Location
import org.bukkit.configuration.file.FileConfiguration

private val defaultRankWeights = mapOf(
    Rank.SPECIAL to 1,
    Rank.L to 4,
    Rank.S to 10,
    Rank.A to 20,
    Rank.B to 30,
    Rank.C to 35,
)

data class GameConfiguration(
    val refreshChances: Int = 3,
    val countdownSeconds: Int = 5,
    val damageIndicatorsEnabled: Boolean = true,
    val rankWeights: Map<Rank, Int> = defaultRankWeights,
    val centerX: Double = 704.5,
    val centerZ: Double = -615.5,
    val trainingWorld: String = "world",
    val trainingX: Double = 33.5,
    val trainingY: Double = -60.0,
    val trainingZ: Double = -27.5,
    val trainingYaw: Float = -135.0F,
    val trainingPitch: Float = 0.0F,
    val scatterMinRadius: Double = 45.0,
    val scatterMaxRadius: Double = 140.0,
    val minimumPlayerDistance: Double = 24.0,
    val borderEnabled: Boolean = true,
    val borderInitialSize: Double = 320.0,
    val borderDelaySeconds: Int = 300,
    val borderShrinkSeconds: Int = 600,
    val borderMinimumSize: Double = 40.0,
)

object GameSettings {
    private var current = GameConfiguration()

    fun load(config: FileConfiguration) {
        current = GameConfiguration(
            refreshChances = config.getInt("selection.refresh-chances", 3).coerceIn(0, 20),
            countdownSeconds = config.getInt("selection.countdown-seconds", 5).coerceIn(0, 60),
            damageIndicatorsEnabled = config.getBoolean("combat.damage-indicators.enabled", true),
            rankWeights = Rank.entries.associateWith { rank ->
                config.getInt("rank-chances.${rank.name.lowercase()}", defaultRankWeights.getValue(rank))
            },
            centerX = config.getDouble("map.center-x", 704.5),
            centerZ = config.getDouble("map.center-z", -615.5),
            trainingWorld = config.getString("training.spawn.world", "world") ?: "world",
            trainingX = config.getDouble("training.spawn.x", 33.5),
            trainingY = config.getDouble("training.spawn.y", -60.0),
            trainingZ = config.getDouble("training.spawn.z", -27.5),
            trainingYaw = config.getDouble("training.spawn.yaw", -135.0).toFloat(),
            trainingPitch = config.getDouble("training.spawn.pitch", 0.0).toFloat(),
            scatterMinRadius = config.getDouble("scatter.minimum-radius", 45.0).coerceAtLeast(0.0),
            scatterMaxRadius = config.getDouble("scatter.maximum-radius", 140.0).coerceAtLeast(5.0),
            minimumPlayerDistance = config.getDouble("scatter.minimum-player-distance", 24.0).coerceAtLeast(2.0),
            borderEnabled = config.getBoolean("border.enabled", true),
            borderInitialSize = config.getDouble("border.initial-size", 320.0).coerceAtLeast(20.0),
            borderDelaySeconds = config.getInt("border.delay-seconds", 300).coerceAtLeast(0),
            borderShrinkSeconds = config.getInt("border.shrink-seconds", 600).coerceAtLeast(1),
            borderMinimumSize = config.getDouble("border.minimum-size", 40.0).coerceAtLeast(10.0),
        ).normalized()
    }

    fun snapshot(): GameConfiguration = current.copy()

    fun adjust(slot: Int, increase: Boolean, multiplier: Int) {
        val direction = if (increase) 1 else -1
        current = when (slot) {
            10 -> current.copy(refreshChances = (current.refreshChances + direction * multiplier).coerceIn(0, 20))
            12 -> current.copy(countdownSeconds = (current.countdownSeconds + direction * multiplier).coerceIn(0, 60))
            14 -> current.copy(scatterMinRadius = current.scatterMinRadius + direction * 5.0 * multiplier)
            16 -> current.copy(scatterMaxRadius = current.scatterMaxRadius + direction * 10.0 * multiplier)
            22 -> current.copy(borderEnabled = !current.borderEnabled)
            24 -> current.copy(damageIndicatorsEnabled = !current.damageIndicatorsEnabled)
            28 -> current.copy(minimumPlayerDistance = current.minimumPlayerDistance + direction * 2.0 * multiplier)
            30 -> current.copy(borderInitialSize = current.borderInitialSize + direction * 10.0 * multiplier)
            32 -> current.copy(borderDelaySeconds = current.borderDelaySeconds + direction * 30 * multiplier)
            34 -> current.copy(borderShrinkSeconds = current.borderShrinkSeconds + direction * 30 * multiplier)
            40 -> current.copy(borderMinimumSize = current.borderMinimumSize + direction * 5.0 * multiplier)
            37 -> current.withRankWeight(Rank.SPECIAL, direction * multiplier)
            38 -> current.withRankWeight(Rank.L, direction * multiplier)
            39 -> current.withRankWeight(Rank.S, direction * multiplier)
            41 -> current.withRankWeight(Rank.A, direction * multiplier)
            42 -> current.withRankWeight(Rank.B, direction * multiplier)
            43 -> current.withRankWeight(Rank.C, direction * multiplier)
            else -> current
        }.normalized()
        save()
    }

    fun setTrainingSpawn(location: Location) {
        current = current.copy(
            trainingWorld = location.world.name,
            trainingX = location.x,
            trainingY = location.y,
            trainingZ = location.z,
            trainingYaw = location.yaw,
            trainingPitch = location.pitch,
        )
        save()
    }

    private fun GameConfiguration.normalized(): GameConfiguration {
        val maximumRadius = scatterMaxRadius.coerceAtLeast(5.0)
        val minimumRadius = scatterMinRadius.coerceIn(0.0, maximumRadius - 1.0)
        val initialBorder = borderInitialSize.coerceAtLeast(maximumRadius * 2.0 + 10.0)
        val minimumBorder = borderMinimumSize.coerceIn(10.0, initialBorder - 1.0)
        return copy(
            refreshChances = refreshChances.coerceIn(0, 20),
            countdownSeconds = countdownSeconds.coerceIn(0, 60),
            scatterMinRadius = minimumRadius,
            scatterMaxRadius = maximumRadius,
            minimumPlayerDistance = minimumPlayerDistance.coerceIn(2.0, maximumRadius * 2.0),
            borderInitialSize = initialBorder,
            borderDelaySeconds = borderDelaySeconds.coerceAtLeast(0),
            borderShrinkSeconds = borderShrinkSeconds.coerceAtLeast(1),
            borderMinimumSize = minimumBorder,
            rankWeights = rankWeights.mapValues { (_, weight) -> weight.coerceIn(0, 10_000) }
                .let { weights -> if (weights.values.sum() > 0) weights else defaultRankWeights },
        )
    }

    private fun GameConfiguration.withRankWeight(rank: Rank, delta: Int): GameConfiguration {
        val updated = rankWeights.toMutableMap()
        updated[rank] = ((updated[rank] ?: 0) + delta).coerceIn(0, 10_000)
        return if (updated.values.sum() > 0) copy(rankWeights = updated) else this
    }

    private fun save() {
        val plugin = ClassWarPlugin.instance
        plugin.config.set("selection.refresh-chances", current.refreshChances)
        plugin.config.set("selection.countdown-seconds", current.countdownSeconds)
        plugin.config.set("combat.damage-indicators.enabled", current.damageIndicatorsEnabled)
        Rank.entries.forEach { rank ->
            plugin.config.set("rank-chances.${rank.name.lowercase()}", current.rankWeights[rank] ?: 0)
        }
        plugin.config.set("map.center-x", current.centerX)
        plugin.config.set("map.center-z", current.centerZ)
        plugin.config.set("training.spawn.world", current.trainingWorld)
        plugin.config.set("training.spawn.x", current.trainingX)
        plugin.config.set("training.spawn.y", current.trainingY)
        plugin.config.set("training.spawn.z", current.trainingZ)
        plugin.config.set("training.spawn.yaw", current.trainingYaw)
        plugin.config.set("training.spawn.pitch", current.trainingPitch)
        plugin.config.set("scatter.minimum-radius", current.scatterMinRadius)
        plugin.config.set("scatter.maximum-radius", current.scatterMaxRadius)
        plugin.config.set("scatter.minimum-player-distance", current.minimumPlayerDistance)
        plugin.config.set("border.enabled", current.borderEnabled)
        plugin.config.set("border.initial-size", current.borderInitialSize)
        plugin.config.set("border.delay-seconds", current.borderDelaySeconds)
        plugin.config.set("border.shrink-seconds", current.borderShrinkSeconds)
        plugin.config.set("border.minimum-size", current.borderMinimumSize)
        plugin.saveConfig()
    }
}
