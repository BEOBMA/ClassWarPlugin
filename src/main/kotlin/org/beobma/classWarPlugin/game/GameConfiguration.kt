package org.beobma.classWarPlugin.game

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.Rank
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
    val scatterMinRadius: Double = 45.0,
    val scatterMaxRadius: Double = 140.0,
    val minimumPlayerDistance: Double = 24.0,
    val borderEnabled: Boolean = true,
    val borderInitialSize: Double = 320.0,
    val borderCenterMinimumDistance: Double = 0.0,
    val borderCenterMaximumDistance: Double = 140.0,
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
            scatterMinRadius = config.getDouble("scatter.minimum-radius", 45.0).coerceAtLeast(0.0),
            scatterMaxRadius = config.getDouble("scatter.maximum-radius", 140.0).coerceAtLeast(0.0),
            minimumPlayerDistance = config.getDouble("scatter.minimum-player-distance", 24.0).coerceAtLeast(0.0),
            borderEnabled = config.getBoolean("border.enabled", true),
            borderInitialSize = config.getDouble("border.initial-size", 320.0).coerceAtLeast(0.0),
            borderCenterMinimumDistance = config.getDouble("border.random-center.minimum-distance", 0.0),
            borderCenterMaximumDistance = config.getDouble("border.random-center.maximum-distance", 140.0),
            borderDelaySeconds = config.getInt("border.delay-seconds", 300).coerceAtLeast(0),
            borderShrinkSeconds = config.getInt("border.shrink-seconds", 600).coerceAtLeast(0),
            borderMinimumSize = config.getDouble("border.minimum-size", 40.0).coerceAtLeast(0.0),
        ).normalized()

        if (config.contains("training")) {
            config.set("training", null)
            ClassWarPlugin.instance.saveConfig()
        }
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
            44 -> current.copy(borderCenterMinimumDistance = current.borderCenterMinimumDistance + direction * 5.0 * multiplier)
            46 -> current.copy(borderCenterMaximumDistance = current.borderCenterMaximumDistance + direction * 10.0 * multiplier)
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

    private fun GameConfiguration.normalized(): GameConfiguration {
        return copy(
            refreshChances = refreshChances.coerceIn(0, 20),
            countdownSeconds = countdownSeconds.coerceIn(0, 60),
            scatterMinRadius = scatterMinRadius.coerceAtLeast(0.0),
            scatterMaxRadius = scatterMaxRadius.coerceAtLeast(0.0),
            minimumPlayerDistance = minimumPlayerDistance.coerceAtLeast(0.0),
            borderInitialSize = borderInitialSize.coerceAtLeast(0.0),
            borderCenterMinimumDistance = borderCenterMinimumDistance.coerceAtLeast(0.0),
            borderCenterMaximumDistance = borderCenterMaximumDistance.coerceAtLeast(0.0),
            borderDelaySeconds = borderDelaySeconds.coerceAtLeast(0),
            borderShrinkSeconds = borderShrinkSeconds.coerceAtLeast(0),
            borderMinimumSize = borderMinimumSize.coerceAtLeast(0.0),
            rankWeights = rankWeights.mapValues { (_, weight) -> weight.coerceIn(0, 10_000) },
        )
    }

    private fun GameConfiguration.withRankWeight(rank: Rank, delta: Int): GameConfiguration {
        val updated = rankWeights.toMutableMap()
        updated[rank] = ((updated[rank] ?: 0) + delta).coerceIn(0, 10_000)
        return copy(rankWeights = updated)
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
        plugin.config.set("scatter.minimum-radius", current.scatterMinRadius)
        plugin.config.set("scatter.maximum-radius", current.scatterMaxRadius)
        plugin.config.set("scatter.minimum-player-distance", current.minimumPlayerDistance)
        plugin.config.set("border.enabled", current.borderEnabled)
        plugin.config.set("border.initial-size", current.borderInitialSize)
        plugin.config.set("border.random-center.minimum-distance", current.borderCenterMinimumDistance)
        plugin.config.set("border.random-center.maximum-distance", current.borderCenterMaximumDistance)
        plugin.config.set("border.delay-seconds", current.borderDelaySeconds)
        plugin.config.set("border.shrink-seconds", current.borderShrinkSeconds)
        plugin.config.set("border.minimum-size", current.borderMinimumSize)
        plugin.saveConfig()
    }
}
