package org.beobma.classWarPlugin.game

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.Rank
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.inventory.ItemStack
import java.util.Locale

private val defaultRankWeights = mapOf(
    Rank.SPECIAL to 1,
    Rank.L to 40,
    Rank.S to 101,
    Rank.A to 202,
    Rank.B to 303,
    Rank.C to 353,
)

/** 전역 피해 배율과 피해 원인별 세부 배율을 구분하는 설정 키다. */
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

/** 관리자 설정 화면에서 증감하거나 전환할 수 있는 경기 설정 항목이다. */
enum class GameSetting {
    REFRESH_CHANCES,
    COUNTDOWN_SECONDS,
    COOLDOWN_FLOW_MULTIPLIER,
    SCATTER_MINIMUM_RADIUS,
    SCATTER_MAXIMUM_RADIUS,
    BORDER_ENABLED,
    DAMAGE_INDICATORS_ENABLED,
    PLAYER_LIST_VISIBLE,
    DEATH_MESSAGES_ENABLED,
    DEATH_MESSAGES_SHOW_KILLER,
    DEATH_MESSAGES_SHOW_CAUSE,
    MINIMUM_PLAYER_DISTANCE,
    BORDER_INITIAL_SIZE,
    BORDER_DELAY_SECONDS,
    BORDER_SHRINK_SECONDS,
    BORDER_MINIMUM_SIZE,
    BORDER_CENTER_MINIMUM_DISTANCE,
    BORDER_CENTER_MAXIMUM_DISTANCE,
    FINAL_BORDER_DESCENT_SECONDS,
    BORDER_DAMAGE_BUFFER,
    BORDER_DAMAGE_PER_BLOCK,
    FINAL_BORDER_DAMAGE,
    FINAL_BORDER_DAMAGE_INTERVAL_SECONDS,
    RANK_SPECIAL_WEIGHT,
    RANK_L_WEIGHT,
    RANK_S_WEIGHT,
    RANK_A_WEIGHT,
    RANK_B_WEIGHT,
    RANK_C_WEIGHT,
}

private object GameConfigPath {
    const val REFRESH_CHANCES = "selection.refresh-chances"
    const val COUNTDOWN_SECONDS = "selection.countdown-seconds"
    const val STARTING_ITEMS = "selection.starting-items"
    const val CLASS_WEAPON = "selection.class-weapon"
    const val COOLDOWN_FLOW_MULTIPLIER = "skills.cooldown-flow-multiplier"
    const val DAMAGE_INDICATORS_ENABLED = "combat.damage-indicators.enabled"
    const val PLAYER_LIST_VISIBLE = "display.player-list-visible"
    const val DEATH_MESSAGES_ENABLED = "combat.death-messages.enabled"
    const val DEATH_MESSAGES_SHOW_KILLER = "combat.death-messages.show-killer"
    const val DEATH_MESSAGES_SHOW_CAUSE = "combat.death-messages.show-cause"
    const val CENTER_X = "map.center-x"
    const val CENTER_Z = "map.center-z"
    const val SCATTER_MINIMUM_RADIUS = "scatter.minimum-radius"
    const val SCATTER_MAXIMUM_RADIUS = "scatter.maximum-radius"
    const val MINIMUM_PLAYER_DISTANCE = "scatter.minimum-player-distance"
    const val BORDER_ENABLED = "border.enabled"
    const val BORDER_INITIAL_SIZE = "border.initial-size"
    const val BORDER_CENTER_MINIMUM_DISTANCE = "border.random-center.minimum-distance"
    const val BORDER_CENTER_MAXIMUM_DISTANCE = "border.random-center.maximum-distance"
    const val BORDER_DELAY_SECONDS = "border.delay-seconds"
    const val BORDER_SHRINK_SECONDS = "border.shrink-seconds"
    const val BORDER_MINIMUM_SIZE = "border.minimum-size"
    const val BORDER_DAMAGE_BUFFER = "border.damage-buffer"
    const val BORDER_DAMAGE_PER_BLOCK = "border.damage-per-block"
    const val FINAL_BORDER_DESCENT_SECONDS = "border.final-descent-seconds"
    const val FINAL_BORDER_DAMAGE = "border.final-damage"
    const val FINAL_BORDER_DAMAGE_INTERVAL_SECONDS = "border.final-damage-interval-seconds"
    const val LEGACY_TRAINING = "training"

    fun damageMultiplier(type: DamageMultiplierType): String =
        "combat.damage-multipliers.${type.configName}"

    fun rankWeight(rank: Rank): String =
        "rank-chances.${rank.name.lowercase(Locale.ROOT)}"
}

private object GameConfigLimit {
    val REFRESH_CHANCES = 0..20
    val COUNTDOWN_SECONDS = 0..60
    const val MINIMUM_COOLDOWN_FLOW_MULTIPLIER = 0.1
    const val MINIMUM_FINAL_DAMAGE_INTERVAL_SECONDS = 0.1
    const val MAXIMUM_MULTIPLIER = 10.0
    const val MAXIMUM_DAMAGE = 100.0
    const val MAXIMUM_FINAL_DAMAGE_INTERVAL_SECONDS = 60.0
    const val MAXIMUM_RANK_WEIGHT = 10_000
}

private object GameConfigStep {
    const val COOLDOWN_FLOW_MULTIPLIER = 0.1
    const val SCATTER_MINIMUM_RADIUS = 5.0
    const val SCATTER_MAXIMUM_RADIUS = 10.0
    const val MINIMUM_PLAYER_DISTANCE = 2.0
    const val BORDER_INITIAL_SIZE = 10.0
    const val BORDER_DELAY_SECONDS = 30
    const val BORDER_SHRINK_SECONDS = 30
    const val BORDER_MINIMUM_SIZE = 5.0
    const val BORDER_CENTER_MINIMUM_DISTANCE = 5.0
    const val BORDER_CENTER_MAXIMUM_DISTANCE = 10.0
    const val FINAL_BORDER_DESCENT_SECONDS = 10
    const val BORDER_DAMAGE_BUFFER = 1.0
    const val BORDER_DAMAGE_PER_BLOCK = 0.1
    const val FINAL_BORDER_DAMAGE = 0.5
    const val FINAL_BORDER_DAMAGE_INTERVAL_SECONDS = 0.1
    const val DAMAGE_MULTIPLIER = 0.1
}

/**
 * 새 경기에 복사되는 불변 설정 스냅샷이다.
 *
 * 이름이 `Seconds`로 끝나는 값은 초, 거리·크기·반경 값은 블록 단위다. 피해 및 흐름 배율은
 * `1.0`이 원본 값이며, [damageMultipliers]의 세부 배율은 전체 배율과 곱해진다.
 */
data class GameConfiguration(
    val refreshChances: Int = 3,
    val countdownSeconds: Int = 5,
    val startingItems: List<ItemStack> = defaultStartingItems(),
    val classWeapon: ItemStack? = null,
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

private fun defaultStartingItems(): List<ItemStack> = listOf(
    ItemStack(org.bukkit.Material.IRON_HELMET),
    ItemStack(org.bukkit.Material.IRON_CHESTPLATE),
    ItemStack(org.bukkit.Material.IRON_LEGGINGS),
    ItemStack(org.bukkit.Material.IRON_BOOTS),
)

/** 전체 피해 배율과 [type]의 세부 배율을 결합한다. */
fun GameConfiguration.damageMultiplier(type: DamageMultiplierType): Double {
    val overall = damageMultipliers[DamageMultiplierType.OVERALL] ?: 1.0
    return if (type == DamageMultiplierType.OVERALL) {
        overall
    } else {
        overall * (damageMultipliers[type] ?: 1.0)
    }
}

/** [DamagePath]를 대응하는 피해 배율 설정으로 변환해 결합 배율을 반환한다. */
fun GameConfiguration.damageMultiplier(path: DamagePath): Double = damageMultiplier(when (path) {
    DamagePath.BASIC_ATTACK -> DamageMultiplierType.BASIC_ATTACK
    DamagePath.RANGED_ATTACK -> DamageMultiplierType.RANGED_ATTACK
    DamagePath.SKILL -> DamageMultiplierType.SKILL
    DamagePath.STATUS_EFFECT -> DamageMultiplierType.STATUS_EFFECT
})

/**
 * 서버 설정 파일과 현재 편집 중인 경기 기본값 사이의 경계다.
 * 변경 메서드는 값을 유효 범위로 정규화한 뒤 즉시 설정 파일에 저장한다.
 */
object GameSettings {
    private var current = GameConfiguration()

    /** 설정을 읽고 누락된 키를 기본값으로 채운다. 더 이상 쓰지 않는 키도 함께 정리한다. */
    fun load(config: FileConfiguration) {
        val defaults = GameConfiguration()
        current = GameConfiguration(
            refreshChances = config.getInt(GameConfigPath.REFRESH_CHANCES, defaults.refreshChances),
            countdownSeconds = config.getInt(GameConfigPath.COUNTDOWN_SECONDS, defaults.countdownSeconds),
            startingItems = if (config.contains(GameConfigPath.STARTING_ITEMS, true)) {
                config.getList(GameConfigPath.STARTING_ITEMS)
                    ?.mapNotNull(::deserializeItemStack)
                    ?: emptyList()
            } else {
                defaults.startingItems.map(ItemStack::clone)
            },
            classWeapon = deserializeItemStack(config.get(GameConfigPath.CLASS_WEAPON)),
            cooldownFlowMultiplier = config.getDouble(
                GameConfigPath.COOLDOWN_FLOW_MULTIPLIER,
                defaults.cooldownFlowMultiplier,
            ),
            damageIndicatorsEnabled = config.getBoolean(
                GameConfigPath.DAMAGE_INDICATORS_ENABLED,
                defaults.damageIndicatorsEnabled,
            ),
            playerListVisible = config.getBoolean(GameConfigPath.PLAYER_LIST_VISIBLE, defaults.playerListVisible),
            deathMessagesEnabled = config.getBoolean(
                GameConfigPath.DEATH_MESSAGES_ENABLED,
                defaults.deathMessagesEnabled,
            ),
            deathMessagesShowKiller = config.getBoolean(
                GameConfigPath.DEATH_MESSAGES_SHOW_KILLER,
                defaults.deathMessagesShowKiller,
            ),
            deathMessagesShowCause = config.getBoolean(
                GameConfigPath.DEATH_MESSAGES_SHOW_CAUSE,
                defaults.deathMessagesShowCause,
            ),
            damageMultipliers = DamageMultiplierType.entries.associateWith { type ->
                config.getDouble(
                    GameConfigPath.damageMultiplier(type),
                    defaults.damageMultipliers.getValue(type),
                )
            },
            rankWeights = Rank.entries.associateWith { rank ->
                config.getInt(GameConfigPath.rankWeight(rank), defaults.rankWeights.getValue(rank))
            },
            centerX = config.getDouble(GameConfigPath.CENTER_X, defaults.centerX),
            centerZ = config.getDouble(GameConfigPath.CENTER_Z, defaults.centerZ),
            scatterMinRadius = config.getDouble(GameConfigPath.SCATTER_MINIMUM_RADIUS, defaults.scatterMinRadius),
            scatterMaxRadius = config.getDouble(GameConfigPath.SCATTER_MAXIMUM_RADIUS, defaults.scatterMaxRadius),
            minimumPlayerDistance = config.getDouble(
                GameConfigPath.MINIMUM_PLAYER_DISTANCE,
                defaults.minimumPlayerDistance,
            ),
            borderEnabled = config.getBoolean(GameConfigPath.BORDER_ENABLED, defaults.borderEnabled),
            borderInitialSize = config.getDouble(GameConfigPath.BORDER_INITIAL_SIZE, defaults.borderInitialSize),
            borderCenterMinimumDistance = config.getDouble(
                GameConfigPath.BORDER_CENTER_MINIMUM_DISTANCE,
                defaults.borderCenterMinimumDistance,
            ),
            borderCenterMaximumDistance = config.getDouble(
                GameConfigPath.BORDER_CENTER_MAXIMUM_DISTANCE,
                defaults.borderCenterMaximumDistance,
            ),
            borderDelaySeconds = config.getInt(GameConfigPath.BORDER_DELAY_SECONDS, defaults.borderDelaySeconds),
            borderShrinkSeconds = config.getInt(GameConfigPath.BORDER_SHRINK_SECONDS, defaults.borderShrinkSeconds),
            borderMinimumSize = config.getDouble(GameConfigPath.BORDER_MINIMUM_SIZE, defaults.borderMinimumSize),
            borderDamageBuffer = config.getDouble(GameConfigPath.BORDER_DAMAGE_BUFFER, defaults.borderDamageBuffer),
            borderDamagePerBlock = config.getDouble(
                GameConfigPath.BORDER_DAMAGE_PER_BLOCK,
                defaults.borderDamagePerBlock,
            ),
            finalBorderDescentSeconds = config.getInt(
                GameConfigPath.FINAL_BORDER_DESCENT_SECONDS,
                defaults.finalBorderDescentSeconds,
            ),
            finalBorderDamage = config.getDouble(GameConfigPath.FINAL_BORDER_DAMAGE, defaults.finalBorderDamage),
            finalBorderDamageIntervalSeconds = config.getDouble(
                GameConfigPath.FINAL_BORDER_DAMAGE_INTERVAL_SECONDS,
                defaults.finalBorderDamageIntervalSeconds,
            ),
        ).normalized()

        var configChanged = false
        current.configEntries().forEach { (path, value) ->
            if (!config.contains(path, true)) {
                config.set(path, value)
                configChanged = true
            }
        }
        if (config.contains(GameConfigPath.LEGACY_TRAINING, true)) {
            config.set(GameConfigPath.LEGACY_TRAINING, null)
            configChanged = true
        }
        if (configChanged) {
            ClassWarPlugin.instance.saveConfig()
        }
    }

    /** 이후 설정 변경과 독립적인 현재 설정 사본을 반환한다. */
    fun snapshot(): GameConfiguration = current.copy()

    /** 게임 시작 시 모든 참가자에게 지급할 아이템을 저장한다. */
    fun setStartingItems(items: Collection<ItemStack>) {
        current = current.copy(startingItems = items.filter { !it.type.isAir }.map(ItemStack::clone))
        save()
    }

    /** 모든 클래스에 공통으로 적용할 기본 무기 템플릿을 저장한다. null이면 클래스 고유 무기를 사용한다. */
    fun setClassWeapon(item: ItemStack?) {
        current = current.copy(classWeapon = item?.takeUnless { it.type.isAir }?.clone())
        save()
    }

    /**
     * [setting]을 정의된 한 단계와 [multiplier]의 곱만큼 변경하고 저장한다.
     * 불리언 항목은 [increase]와 관계없이 한 번 전환된다.
     */
    fun adjust(setting: GameSetting, increase: Boolean, multiplier: Int) {
        if (multiplier <= 0) return
        val direction = if (increase) 1 else -1
        current = when (setting) {
            GameSetting.REFRESH_CHANCES -> current.copy(refreshChances = current.refreshChances + direction * multiplier)
            GameSetting.COUNTDOWN_SECONDS -> current.copy(
                countdownSeconds = current.countdownSeconds + direction * multiplier,
            )
            GameSetting.COOLDOWN_FLOW_MULTIPLIER -> current.copy(
                cooldownFlowMultiplier = current.cooldownFlowMultiplier +
                    direction * GameConfigStep.COOLDOWN_FLOW_MULTIPLIER * multiplier,
            )
            GameSetting.SCATTER_MINIMUM_RADIUS -> current.copy(
                scatterMinRadius = current.scatterMinRadius +
                    direction * GameConfigStep.SCATTER_MINIMUM_RADIUS * multiplier,
            )
            GameSetting.SCATTER_MAXIMUM_RADIUS -> current.copy(
                scatterMaxRadius = current.scatterMaxRadius +
                    direction * GameConfigStep.SCATTER_MAXIMUM_RADIUS * multiplier,
            )
            GameSetting.BORDER_ENABLED -> current.copy(borderEnabled = !current.borderEnabled)
            GameSetting.DAMAGE_INDICATORS_ENABLED -> current.copy(
                damageIndicatorsEnabled = !current.damageIndicatorsEnabled,
            )
            GameSetting.PLAYER_LIST_VISIBLE -> current.copy(playerListVisible = !current.playerListVisible)
            GameSetting.DEATH_MESSAGES_ENABLED -> current.copy(deathMessagesEnabled = !current.deathMessagesEnabled)
            GameSetting.DEATH_MESSAGES_SHOW_KILLER -> current.copy(
                deathMessagesShowKiller = !current.deathMessagesShowKiller,
            )
            GameSetting.DEATH_MESSAGES_SHOW_CAUSE -> current.copy(
                deathMessagesShowCause = !current.deathMessagesShowCause,
            )
            GameSetting.MINIMUM_PLAYER_DISTANCE -> current.copy(
                minimumPlayerDistance = current.minimumPlayerDistance +
                    direction * GameConfigStep.MINIMUM_PLAYER_DISTANCE * multiplier,
            )
            GameSetting.BORDER_INITIAL_SIZE -> current.copy(
                borderInitialSize = current.borderInitialSize +
                    direction * GameConfigStep.BORDER_INITIAL_SIZE * multiplier,
            )
            GameSetting.BORDER_DELAY_SECONDS -> current.copy(
                borderDelaySeconds = current.borderDelaySeconds +
                    direction * GameConfigStep.BORDER_DELAY_SECONDS * multiplier,
            )
            GameSetting.BORDER_SHRINK_SECONDS -> current.copy(
                borderShrinkSeconds = current.borderShrinkSeconds +
                    direction * GameConfigStep.BORDER_SHRINK_SECONDS * multiplier,
            )
            GameSetting.BORDER_MINIMUM_SIZE -> current.copy(
                borderMinimumSize = current.borderMinimumSize +
                    direction * GameConfigStep.BORDER_MINIMUM_SIZE * multiplier,
            )
            GameSetting.BORDER_CENTER_MINIMUM_DISTANCE -> current.copy(
                borderCenterMinimumDistance = current.borderCenterMinimumDistance +
                    direction * GameConfigStep.BORDER_CENTER_MINIMUM_DISTANCE * multiplier,
            )
            GameSetting.BORDER_CENTER_MAXIMUM_DISTANCE -> current.copy(
                borderCenterMaximumDistance = current.borderCenterMaximumDistance +
                    direction * GameConfigStep.BORDER_CENTER_MAXIMUM_DISTANCE * multiplier,
            )
            GameSetting.FINAL_BORDER_DESCENT_SECONDS -> current.copy(
                finalBorderDescentSeconds = current.finalBorderDescentSeconds +
                    direction * GameConfigStep.FINAL_BORDER_DESCENT_SECONDS * multiplier,
            )
            GameSetting.BORDER_DAMAGE_BUFFER -> current.copy(
                borderDamageBuffer = current.borderDamageBuffer +
                    direction * GameConfigStep.BORDER_DAMAGE_BUFFER * multiplier,
            )
            GameSetting.BORDER_DAMAGE_PER_BLOCK -> current.copy(
                borderDamagePerBlock = current.borderDamagePerBlock +
                    direction * GameConfigStep.BORDER_DAMAGE_PER_BLOCK * multiplier,
            )
            GameSetting.FINAL_BORDER_DAMAGE -> current.copy(
                finalBorderDamage = current.finalBorderDamage +
                    direction * GameConfigStep.FINAL_BORDER_DAMAGE * multiplier,
            )
            GameSetting.FINAL_BORDER_DAMAGE_INTERVAL_SECONDS -> current.copy(
                finalBorderDamageIntervalSeconds = current.finalBorderDamageIntervalSeconds +
                    direction * GameConfigStep.FINAL_BORDER_DAMAGE_INTERVAL_SECONDS * multiplier,
            )
            GameSetting.RANK_SPECIAL_WEIGHT -> current.withRankWeight(Rank.SPECIAL, direction * multiplier)
            GameSetting.RANK_L_WEIGHT -> current.withRankWeight(Rank.L, direction * multiplier)
            GameSetting.RANK_S_WEIGHT -> current.withRankWeight(Rank.S, direction * multiplier)
            GameSetting.RANK_A_WEIGHT -> current.withRankWeight(Rank.A, direction * multiplier)
            GameSetting.RANK_B_WEIGHT -> current.withRankWeight(Rank.B, direction * multiplier)
            GameSetting.RANK_C_WEIGHT -> current.withRankWeight(Rank.C, direction * multiplier)
        }.normalized()
        save()
    }

    /** 피해 배율을 `0.1 * stepMultiplier`만큼 변경하고 저장한다. */
    fun adjustDamageMultiplier(type: DamageMultiplierType, increase: Boolean, stepMultiplier: Int) {
        if (stepMultiplier <= 0) return
        val direction = if (increase) 1.0 else -1.0
        val updated = current.damageMultipliers.toMutableMap()
        val value = (updated[type] ?: defaultDamageMultipliers.getValue(type)) +
            direction * GameConfigStep.DAMAGE_MULTIPLIER * stepMultiplier
        updated[type] = oneDecimal(value.coerceIn(0.0, GameConfigLimit.MAXIMUM_MULTIPLIER))
        current = current.copy(damageMultipliers = updated).normalized()
        save()
    }

    private fun GameConfiguration.normalized(): GameConfiguration {
        val defaults = GameConfiguration()
        return copy(
            refreshChances = refreshChances.coerceIn(GameConfigLimit.REFRESH_CHANCES),
            countdownSeconds = countdownSeconds.coerceIn(GameConfigLimit.COUNTDOWN_SECONDS),
            cooldownFlowMultiplier = oneDecimal(
                cooldownFlowMultiplier.finiteOr(defaults.cooldownFlowMultiplier).coerceIn(
                    GameConfigLimit.MINIMUM_COOLDOWN_FLOW_MULTIPLIER,
                    GameConfigLimit.MAXIMUM_MULTIPLIER,
                ),
            ),
            centerX = centerX.finiteOr(defaults.centerX),
            centerZ = centerZ.finiteOr(defaults.centerZ),
            scatterMinRadius = scatterMinRadius.finiteOr(defaults.scatterMinRadius).coerceAtLeast(0.0),
            scatterMaxRadius = scatterMaxRadius.finiteOr(defaults.scatterMaxRadius).coerceAtLeast(0.0),
            minimumPlayerDistance = minimumPlayerDistance.finiteOr(defaults.minimumPlayerDistance).coerceAtLeast(0.0),
            borderInitialSize = borderInitialSize.finiteOr(defaults.borderInitialSize).coerceAtLeast(0.0),
            borderCenterMinimumDistance = borderCenterMinimumDistance
                .finiteOr(defaults.borderCenterMinimumDistance)
                .coerceAtLeast(0.0),
            borderCenterMaximumDistance = borderCenterMaximumDistance
                .finiteOr(defaults.borderCenterMaximumDistance)
                .coerceAtLeast(0.0),
            borderDelaySeconds = borderDelaySeconds.coerceAtLeast(0),
            borderShrinkSeconds = borderShrinkSeconds.coerceAtLeast(0),
            borderMinimumSize = borderMinimumSize.finiteOr(defaults.borderMinimumSize).coerceAtLeast(0.0),
            borderDamageBuffer = borderDamageBuffer.finiteOr(defaults.borderDamageBuffer).coerceAtLeast(0.0),
            borderDamagePerBlock = oneDecimal(
                borderDamagePerBlock.finiteOr(defaults.borderDamagePerBlock)
                    .coerceIn(0.0, GameConfigLimit.MAXIMUM_DAMAGE),
            ),
            finalBorderDescentSeconds = finalBorderDescentSeconds.coerceAtLeast(0),
            finalBorderDamage = oneDecimal(
                finalBorderDamage.finiteOr(defaults.finalBorderDamage)
                    .coerceIn(0.0, GameConfigLimit.MAXIMUM_DAMAGE),
            ),
            finalBorderDamageIntervalSeconds = oneDecimal(
                finalBorderDamageIntervalSeconds.finiteOr(defaults.finalBorderDamageIntervalSeconds).coerceIn(
                    GameConfigLimit.MINIMUM_FINAL_DAMAGE_INTERVAL_SECONDS,
                    GameConfigLimit.MAXIMUM_FINAL_DAMAGE_INTERVAL_SECONDS,
                ),
            ),
            damageMultipliers = DamageMultiplierType.entries.associateWith { type ->
                oneDecimal(
                    (damageMultipliers[type] ?: defaults.damageMultipliers.getValue(type))
                        .finiteOr(defaults.damageMultipliers.getValue(type))
                        .coerceIn(0.0, GameConfigLimit.MAXIMUM_MULTIPLIER),
                )
            },
            rankWeights = Rank.entries.associateWith { rank ->
                (rankWeights[rank] ?: defaults.rankWeights.getValue(rank))
                    .coerceIn(0, GameConfigLimit.MAXIMUM_RANK_WEIGHT)
            },
        )
    }

    private fun GameConfiguration.withRankWeight(rank: Rank, delta: Int): GameConfiguration {
        val updated = rankWeights.toMutableMap()
        updated[rank] = ((updated[rank] ?: defaultRankWeights.getValue(rank)) + delta)
            .coerceIn(0, GameConfigLimit.MAXIMUM_RANK_WEIGHT)
        return copy(rankWeights = updated)
    }

    private fun save() {
        val plugin = ClassWarPlugin.instance
        current.configEntries().forEach(plugin.config::set)
        plugin.config.set(GameConfigPath.CLASS_WEAPON, current.classWeapon?.clone())
        plugin.saveConfig()
    }

    private fun GameConfiguration.configEntries(): Map<String, Any> = buildMap {
        put(GameConfigPath.REFRESH_CHANCES, refreshChances)
        put(GameConfigPath.COUNTDOWN_SECONDS, countdownSeconds)
        put(GameConfigPath.STARTING_ITEMS, startingItems.map(ItemStack::clone))
        put(GameConfigPath.COOLDOWN_FLOW_MULTIPLIER, oneDecimal(cooldownFlowMultiplier))
        put(GameConfigPath.DAMAGE_INDICATORS_ENABLED, damageIndicatorsEnabled)
        put(GameConfigPath.PLAYER_LIST_VISIBLE, playerListVisible)
        put(GameConfigPath.DEATH_MESSAGES_ENABLED, deathMessagesEnabled)
        put(GameConfigPath.DEATH_MESSAGES_SHOW_KILLER, deathMessagesShowKiller)
        put(GameConfigPath.DEATH_MESSAGES_SHOW_CAUSE, deathMessagesShowCause)
        DamageMultiplierType.entries.forEach { type ->
            put(GameConfigPath.damageMultiplier(type), oneDecimal(damageMultipliers.getValue(type)))
        }
        Rank.entries.forEach { rank ->
            put(GameConfigPath.rankWeight(rank), rankWeights.getValue(rank))
        }
        put(GameConfigPath.CENTER_X, centerX)
        put(GameConfigPath.CENTER_Z, centerZ)
        put(GameConfigPath.SCATTER_MINIMUM_RADIUS, scatterMinRadius)
        put(GameConfigPath.SCATTER_MAXIMUM_RADIUS, scatterMaxRadius)
        put(GameConfigPath.MINIMUM_PLAYER_DISTANCE, minimumPlayerDistance)
        put(GameConfigPath.BORDER_ENABLED, borderEnabled)
        put(GameConfigPath.BORDER_INITIAL_SIZE, borderInitialSize)
        put(GameConfigPath.BORDER_CENTER_MINIMUM_DISTANCE, borderCenterMinimumDistance)
        put(GameConfigPath.BORDER_CENTER_MAXIMUM_DISTANCE, borderCenterMaximumDistance)
        put(GameConfigPath.BORDER_DELAY_SECONDS, borderDelaySeconds)
        put(GameConfigPath.BORDER_SHRINK_SECONDS, borderShrinkSeconds)
        put(GameConfigPath.BORDER_MINIMUM_SIZE, borderMinimumSize)
        put(GameConfigPath.BORDER_DAMAGE_BUFFER, borderDamageBuffer)
        put(GameConfigPath.BORDER_DAMAGE_PER_BLOCK, borderDamagePerBlock)
        put(GameConfigPath.FINAL_BORDER_DESCENT_SECONDS, finalBorderDescentSeconds)
        put(GameConfigPath.FINAL_BORDER_DAMAGE, finalBorderDamage)
        put(GameConfigPath.FINAL_BORDER_DAMAGE_INTERVAL_SECONDS, finalBorderDamageIntervalSeconds)
    }

    private fun oneDecimal(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0

    private fun deserializeItemStack(value: Any?): ItemStack? = when (value) {
        is ItemStack -> value.clone()
        is Map<*, *> -> runCatching {
            @Suppress("UNCHECKED_CAST")
            ItemStack.deserialize(value as Map<String, Any>)
        }.getOrNull()
        else -> null
    }

    private fun Double.finiteOr(fallback: Double): Double = if (isFinite()) this else fallback
}
