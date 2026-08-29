package org.beobma.classWarPlugin.game

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.Rank
import org.bukkit.configuration.file.FileConfiguration

private val defaultRankWeights = mapOf(
    Rank.SPECIAL to 1,
    Rank.L to 40,
    Rank.S to 101,
    Rank.A to 202,
    Rank.B to 303,
    Rank.C to 353,
)

enum class DamageMultiplierType(val configName: String) {
    OVERALL("overall"),
    BASIC_ATTACK("basic-attack"),
    RANGED_ATTACK("ranged-attack"),
    SKILL("skill"),
    STATUS_EFFECT("status-effect"),
    FALL("fall"),
    DROWNING("drowning"),
    FIRE("fire"),
    LAVA("lava"),
    SUFFOCATION("suffocation"),
    EXPLOSION("explosion"),
    POISON_MAGIC("poison-magic"),
    STARVATION("starvation"),
    VOID("void"),
    FREEZING("freezing"),
    CONTACT("contact"),
    LIGHTNING("lightning"),
    MOB_ATTACK("mob-attack"),
    IMPACT("impact"),
    WORLD_BORDER("world-border"),
    OTHER_ENVIRONMENT("other-environment"),
}

private val defaultDamageMultipliers = DamageMultiplierType.entries.associateWith { 1.0 }

data class GameConfiguration(
    val refreshChances: Int = 3,
    val countdownSeconds: Int = 5,
    val cooldownFlowMultiplier: Double = 1.0,
    val damageIndicatorsEnabled: Boolean = true,
    val playerListVisible: Boolean = false,
    val deathMessagesEnabled: Boolean = true,
    val deathMessagesShowKiller: Boolean = true,
    val deathMessagesShowCause: Boolean = true,
    val damageMultipliers: Map<DamageMultiplierType, Double> = defaultDamageMultipliers,
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
    val borderDamageBuffer: Double = 5.0,
    val borderDamagePerBlock: Double = 0.2,
    val finalBorderDescentSeconds: Int = 180,
    val finalBorderDamage: Double = 2.0,
    val finalBorderDamageIntervalSeconds: Double = 1.0,
)

fun GameConfiguration.damageMultiplier(type: DamageMultiplierType): Double {
    val overall = damageMultipliers[DamageMultiplierType.OVERALL] ?: 1.0
    return if (type == DamageMultiplierType.OVERALL) {
        overall
    } else {
        overall * (damageMultipliers[type] ?: 1.0)
    }
}

fun GameConfiguration.damageMultiplier(path: DamagePath): Double = damageMultiplier(when (path) {
    DamagePath.BASIC_ATTACK -> DamageMultiplierType.BASIC_ATTACK
    DamagePath.RANGED_ATTACK -> DamageMultiplierType.RANGED_ATTACK
    DamagePath.SKILL -> DamageMultiplierType.SKILL
    DamagePath.STATUS_EFFECT -> DamageMultiplierType.STATUS_EFFECT
})

object GameSettings {
    private var current = GameConfiguration()

    fun load(config: FileConfiguration) {
        current = GameConfiguration(
            refreshChances = config.getInt("selection.refresh-chances", 3).coerceIn(0, 20),
            countdownSeconds = config.getInt("selection.countdown-seconds", 5).coerceIn(0, 60),
            cooldownFlowMultiplier = config.getDouble("skills.cooldown-flow-multiplier", 1.0)
                .coerceIn(0.1, 10.0),
            damageIndicatorsEnabled = config.getBoolean("combat.damage-indicators.enabled", true),
            playerListVisible = config.getBoolean("display.player-list-visible", false),
            deathMessagesEnabled = config.getBoolean("combat.death-messages.enabled", true),
            deathMessagesShowKiller = config.getBoolean("combat.death-messages.show-killer", true),
            deathMessagesShowCause = config.getBoolean("combat.death-messages.show-cause", true),
            damageMultipliers = DamageMultiplierType.entries.associateWith { type ->
                config.getDouble("combat.damage-multipliers.${type.configName}", 1.0)
                    .takeIf(Double::isFinite)
                    ?.coerceIn(0.0, 10.0)
                    ?: 1.0
            },
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
            borderDamageBuffer = config.getDouble("border.damage-buffer", 5.0).coerceAtLeast(0.0),
            borderDamagePerBlock = config.getDouble("border.damage-per-block", 0.2).coerceAtLeast(0.0),
            finalBorderDescentSeconds = config.getInt("border.final-descent-seconds", 180).coerceAtLeast(0),
            finalBorderDamage = config.getDouble("border.final-damage", 2.0).coerceAtLeast(0.0),
            finalBorderDamageIntervalSeconds = config.getDouble("border.final-damage-interval-seconds", 1.0)
                .coerceAtLeast(0.1),
        ).normalized()

        var configChanged = false
        if (!config.contains("border.final-descent-seconds")) {
            config.set("border.final-descent-seconds", current.finalBorderDescentSeconds)
            configChanged = true
        }
        if (!config.contains("skills.cooldown-flow-multiplier", true)) {
            config.set("skills.cooldown-flow-multiplier", current.cooldownFlowMultiplier)
            configChanged = true
        }
        if (!config.contains("border.damage-buffer")) {
            config.set("border.damage-buffer", current.borderDamageBuffer)
            configChanged = true
        }
        if (!config.contains("border.damage-per-block", true)) {
            config.set("border.damage-per-block", current.borderDamagePerBlock)
            configChanged = true
        }
        if (!config.contains("border.final-damage", true)) {
            config.set("border.final-damage", current.finalBorderDamage)
            configChanged = true
        }
        if (!config.contains("border.final-damage-interval-seconds", true)) {
            config.set("border.final-damage-interval-seconds", current.finalBorderDamageIntervalSeconds)
            configChanged = true
        }
        DamageMultiplierType.entries.forEach { type ->
            val path = "combat.damage-multipliers.${type.configName}"
            if (!config.contains(path, true)) {
                config.set(path, current.damageMultipliers[type] ?: 1.0)
                configChanged = true
            }
        }
        if (!config.contains("display.player-list-visible")) {
            config.set("display.player-list-visible", current.playerListVisible)
            configChanged = true
        }
        if (!config.contains("combat.death-messages.enabled")) {
            config.set("combat.death-messages.enabled", current.deathMessagesEnabled)
            configChanged = true
        }
        if (!config.contains("combat.death-messages.show-killer")) {
            config.set("combat.death-messages.show-killer", current.deathMessagesShowKiller)
            configChanged = true
        }
        if (!config.contains("combat.death-messages.show-cause")) {
            config.set("combat.death-messages.show-cause", current.deathMessagesShowCause)
            configChanged = true
        }
        if (config.contains("training")) {
            config.set("training", null)
            configChanged = true
        }
        if (configChanged) {
            ClassWarPlugin.instance.saveConfig()
        }
    }

    fun snapshot(): GameConfiguration = current.copy()

    fun adjust(slot: Int, increase: Boolean, multiplier: Int) {
        val direction = if (increase) 1 else -1
        current = when (slot) {
            10 -> current.copy(refreshChances = (current.refreshChances + direction * multiplier).coerceIn(0, 20))
            12 -> current.copy(countdownSeconds = (current.countdownSeconds + direction * multiplier).coerceIn(0, 60))
            60 -> current.copy(
                cooldownFlowMultiplier = current.cooldownFlowMultiplier + direction * 0.1 * multiplier,
            )
            14 -> current.copy(scatterMinRadius = current.scatterMinRadius + direction * 5.0 * multiplier)
            16 -> current.copy(scatterMaxRadius = current.scatterMaxRadius + direction * 10.0 * multiplier)
            22 -> current.copy(borderEnabled = !current.borderEnabled)
            24 -> current.copy(damageIndicatorsEnabled = !current.damageIndicatorsEnabled)
            52 -> current.copy(playerListVisible = !current.playerListVisible)
            54 -> current.copy(deathMessagesEnabled = !current.deathMessagesEnabled)
            56 -> current.copy(deathMessagesShowKiller = !current.deathMessagesShowKiller)
            58 -> current.copy(deathMessagesShowCause = !current.deathMessagesShowCause)
            28 -> current.copy(minimumPlayerDistance = current.minimumPlayerDistance + direction * 2.0 * multiplier)
            30 -> current.copy(borderInitialSize = current.borderInitialSize + direction * 10.0 * multiplier)
            32 -> current.copy(borderDelaySeconds = current.borderDelaySeconds + direction * 30 * multiplier)
            34 -> current.copy(borderShrinkSeconds = current.borderShrinkSeconds + direction * 30 * multiplier)
            40 -> current.copy(borderMinimumSize = current.borderMinimumSize + direction * 5.0 * multiplier)
            44 -> current.copy(borderCenterMinimumDistance = current.borderCenterMinimumDistance + direction * 5.0 * multiplier)
            46 -> current.copy(borderCenterMaximumDistance = current.borderCenterMaximumDistance + direction * 10.0 * multiplier)
            48 -> current.copy(finalBorderDescentSeconds = current.finalBorderDescentSeconds + direction * 10 * multiplier)
            50 -> current.copy(borderDamageBuffer = current.borderDamageBuffer + direction * multiplier)
            62 -> current.copy(borderDamagePerBlock = current.borderDamagePerBlock + direction * 0.1 * multiplier)
            64 -> current.copy(finalBorderDamage = current.finalBorderDamage + direction * 0.5 * multiplier)
            66 -> current.copy(
                finalBorderDamageIntervalSeconds = current.finalBorderDamageIntervalSeconds + direction * 0.1 * multiplier,
            )
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

    fun adjustDamageMultiplier(type: DamageMultiplierType, increase: Boolean, stepMultiplier: Int) {
        val direction = if (increase) 1.0 else -1.0
        val updated = current.damageMultipliers.toMutableMap()
        val value = (updated[type] ?: 1.0) + direction * 0.1 * stepMultiplier
        updated[type] = oneDecimal(value.coerceIn(0.0, 10.0))
        current = current.copy(damageMultipliers = updated).normalized()
        save()
    }

    private fun GameConfiguration.normalized(): GameConfiguration {
        return copy(
            refreshChances = refreshChances.coerceIn(0, 20),
            countdownSeconds = countdownSeconds.coerceIn(0, 60),
            cooldownFlowMultiplier = oneDecimal(cooldownFlowMultiplier.coerceIn(0.1, 10.0)),
            scatterMinRadius = scatterMinRadius.coerceAtLeast(0.0),
            scatterMaxRadius = scatterMaxRadius.coerceAtLeast(0.0),
            minimumPlayerDistance = minimumPlayerDistance.coerceAtLeast(0.0),
            borderInitialSize = borderInitialSize.coerceAtLeast(0.0),
            borderCenterMinimumDistance = borderCenterMinimumDistance.coerceAtLeast(0.0),
            borderCenterMaximumDistance = borderCenterMaximumDistance.coerceAtLeast(0.0),
            borderDelaySeconds = borderDelaySeconds.coerceAtLeast(0),
            borderShrinkSeconds = borderShrinkSeconds.coerceAtLeast(0),
            borderMinimumSize = borderMinimumSize.coerceAtLeast(0.0),
            borderDamageBuffer = borderDamageBuffer.coerceAtLeast(0.0),
            borderDamagePerBlock = oneDecimal(borderDamagePerBlock.coerceIn(0.0, 100.0)),
            finalBorderDescentSeconds = finalBorderDescentSeconds.coerceAtLeast(0),
            finalBorderDamage = oneDecimal(finalBorderDamage.coerceIn(0.0, 100.0)),
            finalBorderDamageIntervalSeconds = oneDecimal(finalBorderDamageIntervalSeconds.coerceIn(0.1, 60.0)),
            damageMultipliers = DamageMultiplierType.entries.associateWith { type ->
                oneDecimal((damageMultipliers[type] ?: 1.0).coerceIn(0.0, 10.0))
            },
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
        plugin.config.set("skills.cooldown-flow-multiplier", oneDecimal(current.cooldownFlowMultiplier))
        plugin.config.set("combat.damage-indicators.enabled", current.damageIndicatorsEnabled)
        plugin.config.set("display.player-list-visible", current.playerListVisible)
        plugin.config.set("combat.death-messages.enabled", current.deathMessagesEnabled)
        plugin.config.set("combat.death-messages.show-killer", current.deathMessagesShowKiller)
        plugin.config.set("combat.death-messages.show-cause", current.deathMessagesShowCause)
        DamageMultiplierType.entries.forEach { type ->
            plugin.config.set(
                "combat.damage-multipliers.${type.configName}",
                oneDecimal(current.damageMultipliers[type] ?: 1.0),
            )
        }
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
        plugin.config.set("border.damage-buffer", current.borderDamageBuffer)
        plugin.config.set("border.damage-per-block", current.borderDamagePerBlock)
        plugin.config.set("border.final-descent-seconds", current.finalBorderDescentSeconds)
        plugin.config.set("border.final-damage", current.finalBorderDamage)
        plugin.config.set("border.final-damage-interval-seconds", current.finalBorderDamageIntervalSeconds)
        plugin.saveConfig()
    }

    private fun oneDecimal(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0
}
