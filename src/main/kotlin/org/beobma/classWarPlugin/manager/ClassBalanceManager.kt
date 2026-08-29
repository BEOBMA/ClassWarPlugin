package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import java.lang.StackWalker
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.round
import kotlin.math.roundToInt

enum class ClassBalanceField(val configName: String) {
    OVERALL("overall-multiplier"),
    DAMAGE("damage-multiplier"),
    HEALING("healing-multiplier"),
    RANGE("range-multiplier"),
    STATUS_DURATION("status-duration-multiplier"),
    STATUS_POWER("status-power-multiplier"),
    COOLDOWN_FLOW("cooldown-flow-multiplier"),
}

data class ClassBalanceModifiers(
    val overallMultiplier: Double = 1.0,
    val damageMultiplier: Double = 1.0,
    val healingMultiplier: Double = 1.0,
    val rangeMultiplier: Double = 1.0,
    val statusDurationMultiplier: Double = 1.0,
    val statusPowerMultiplier: Double = 1.0,
    val cooldownFlowMultiplier: Double = 1.0,
) {
    fun value(field: ClassBalanceField): Double = when (field) {
        ClassBalanceField.OVERALL -> overallMultiplier
        ClassBalanceField.DAMAGE -> damageMultiplier
        ClassBalanceField.HEALING -> healingMultiplier
        ClassBalanceField.RANGE -> rangeMultiplier
        ClassBalanceField.STATUS_DURATION -> statusDurationMultiplier
        ClassBalanceField.STATUS_POWER -> statusPowerMultiplier
        ClassBalanceField.COOLDOWN_FLOW -> cooldownFlowMultiplier
    }

    fun effective(field: ClassBalanceField): Double = when (field) {
        ClassBalanceField.OVERALL -> overallMultiplier
        ClassBalanceField.COOLDOWN_FLOW -> cooldownFlowMultiplier
        else -> overallMultiplier * value(field)
    }

    fun with(field: ClassBalanceField, value: Double): ClassBalanceModifiers = when (field) {
        ClassBalanceField.OVERALL -> copy(overallMultiplier = value)
        ClassBalanceField.DAMAGE -> copy(damageMultiplier = value)
        ClassBalanceField.HEALING -> copy(healingMultiplier = value)
        ClassBalanceField.RANGE -> copy(rangeMultiplier = value)
        ClassBalanceField.STATUS_DURATION -> copy(statusDurationMultiplier = value)
        ClassBalanceField.STATUS_POWER -> copy(statusPowerMultiplier = value)
        ClassBalanceField.COOLDOWN_FLOW -> copy(cooldownFlowMultiplier = value)
    }
}

object ClassBalanceManager {
    private const val ROOT_PATH = "class-balance"
    private const val MINIMUM_MULTIPLIER = 0.1
    private const val MAXIMUM_MULTIPLIER = 10.0
    private val miniMessageTagPattern = Regex("<[^>]+>")
    private val camelCaseBoundary = Regex("([a-z0-9])([A-Z])")
    private val stackWalker = StackWalker.getInstance()
    private val callerKeyCache = ConcurrentHashMap<String, String>()

    private data class ClassDescriptor(val canonicalName: String, val configKey: String)

    @Volatile
    private var defaultModifiers = ClassBalanceModifiers()

    @Volatile
    private var modifiersByKey: Map<String, ClassBalanceModifiers> = emptyMap()

    @Volatile
    private var descriptors: List<ClassDescriptor> = emptyList()

    fun load(config: FileConfiguration, classes: Collection<GameClass>) {
        val uniqueClasses = classes.distinctBy { it.javaClass.name }
        descriptors = uniqueClasses.map { ClassDescriptor(it.javaClass.name, configKey(it)) }
        callerKeyCache.clear()

        defaultModifiers = readModifiers(config, "$ROOT_PATH.defaults", ClassBalanceModifiers())
        val loaded = linkedMapOf<String, ClassBalanceModifiers>()
        var changed = false
        ClassBalanceField.entries.forEach { field ->
            val fieldPath = "$ROOT_PATH.defaults.${field.configName}"
            if (!config.contains(fieldPath, true)) {
                config.set(fieldPath, defaultModifiers.value(field))
                changed = true
            }
        }
        uniqueClasses.forEach { gameClass ->
            val key = configKey(gameClass)
            val path = "$ROOT_PATH.classes.$key"
            if (!config.contains("$path.name", true)) {
                config.set("$path.name", gameClass.name.replace(miniMessageTagPattern, ""))
                changed = true
            }
            val modifiers = readModifiers(config, path, defaultModifiers)
            loaded[key] = modifiers
            ClassBalanceField.entries.forEach { field ->
                val fieldPath = "$path.${field.configName}"
                if (!config.contains(fieldPath, true)) {
                    config.set(fieldPath, modifiers.value(field))
                    changed = true
                }
            }
        }
        modifiersByKey = loaded.toMap()
        if (changed) ClassWarPlugin.instance.saveConfig()
    }

    fun configKey(gameClass: GameClass): String = configKey(gameClass.javaClass.simpleName)

    fun modifiers(gameClass: GameClass): ClassBalanceModifiers =
        modifiersByKey[configKey(gameClass)] ?: defaultModifiers

    fun adjust(gameClass: GameClass, field: ClassBalanceField, increase: Boolean, stepMultiplier: Int) {
        val key = configKey(gameClass)
        val current = modifiersByKey[key] ?: defaultModifiers
        val direction = if (increase) 1.0 else -1.0
        val nextValue = normalize(current.value(field) + direction * 0.1 * stepMultiplier)
        val updated = current.with(field, nextValue)
        modifiersByKey = modifiersByKey.toMutableMap().apply { put(key, updated) }
        val plugin = ClassWarPlugin.instance
        plugin.config.set("$ROOT_PATH.classes.$key.${field.configName}", nextValue)
        plugin.saveConfig()
    }

    fun reset(gameClass: GameClass) {
        val key = configKey(gameClass)
        modifiersByKey = modifiersByKey.toMutableMap().apply { put(key, defaultModifiers) }
        val plugin = ClassWarPlugin.instance
        ClassBalanceField.entries.forEach { field ->
            plugin.config.set("$ROOT_PATH.classes.$key.${field.configName}", defaultModifiers.value(field))
        }
        plugin.saveConfig()
    }

    fun cooldownFlowMultiplier(player: Player, skill: Skill): Double {
        val game = GameManager.findGameForPlayer(player)
        val globalMultiplier = game?.settings?.cooldownFlowMultiplier ?: 1.0
        val playerData = game?.playerDatas
            ?.filterIsInstance<PlayerData>()
            ?.firstOrNull { it.uniqueId == player.uniqueId }
            ?: return globalMultiplier
        val key = resolveSkillKey(playerData, skill)
        val classMultiplier = key?.let { modifiersByKey[it] }?.cooldownFlowMultiplier ?: 1.0
        return (globalMultiplier * classMultiplier).coerceAtLeast(MINIMUM_MULTIPLIER)
    }

    fun scaleDamage(attacker: PlayerData, path: DamagePath, amount: Double): Double {
        val key = if (path.isBasicAttack) {
            getWeaponClassId(attacker.player.inventory.itemInMainHand)?.let(::keyForClassName)
                ?: resolveCallerKey(attacker)
        } else {
            resolveCallerKey(attacker)
        }
        return amount * effective(key, ClassBalanceField.DAMAGE)
    }

    fun scaleHealing(healer: PlayerData, amount: Double): Double =
        amount * effective(resolveCallerKey(healer), ClassBalanceField.HEALING)

    fun scaleRange(source: EntityData, amount: Double): Double {
        return (amount * rangeMultiplier(source)).coerceAtLeast(0.0)
    }

    fun rangeMultiplier(source: EntityData): Double {
        val playerData = source as? PlayerData ?: return 1.0
        return effective(resolveCallerKey(playerData), ClassBalanceField.RANGE)
    }

    fun scaleStatusDuration(caster: PlayerData?, duration: Int): Int =
        scalePositiveInt(duration, effective(caster?.let(::resolveCallerKey), ClassBalanceField.STATUS_DURATION))

    fun scaleStatusPower(caster: PlayerData?, power: Int): Int =
        scalePositiveInt(power, effective(caster?.let(::resolveCallerKey), ClassBalanceField.STATUS_POWER))

    private fun resolveSkillKey(playerData: PlayerData, skill: Skill): String? {
        keyForClassName(skill.javaClass.name)?.let { return it }
        playerData.gameClasses.firstOrNull { owner -> owner.skills.any { it === skill || it.id == skill.id } }
            ?.let { return configKey(it) }
        return resolveCallerKey(playerData)
    }

    private fun resolveCallerKey(playerData: PlayerData): String? {
        val frameNames = stackWalker.walk { frames ->
            frames.limit(48).map { it.className }.toList()
        }
        frameNames.forEach { className ->
            callerKeyCache[className]?.let { return it }
            keyForClassName(className)?.let { key ->
                callerKeyCache[className] = key
                return key
            }
            if (className.startsWith("org.beobma.classWarPlugin.util.ElementalistRuntime")) {
                descriptors.firstOrNull { it.canonicalName.endsWith(".Elementalist") }?.configKey?.let { key ->
                    callerKeyCache[className] = key
                    return key
                }
            }
        }
        return playerData.gameClasses.singleOrNull()?.let(::configKey)
    }

    private fun keyForClassName(className: String): String? = descriptors.firstOrNull { descriptor ->
        className == descriptor.canonicalName || className.startsWith("${descriptor.canonicalName}\$")
    }?.configKey

    private fun readModifiers(
        config: FileConfiguration,
        path: String,
        fallback: ClassBalanceModifiers,
    ): ClassBalanceModifiers = ClassBalanceModifiers(
        overallMultiplier = readMultiplier(config, path, ClassBalanceField.OVERALL, fallback.overallMultiplier),
        damageMultiplier = readMultiplier(config, path, ClassBalanceField.DAMAGE, fallback.damageMultiplier),
        healingMultiplier = readMultiplier(config, path, ClassBalanceField.HEALING, fallback.healingMultiplier),
        rangeMultiplier = readMultiplier(config, path, ClassBalanceField.RANGE, fallback.rangeMultiplier),
        statusDurationMultiplier = readMultiplier(
            config, path, ClassBalanceField.STATUS_DURATION, fallback.statusDurationMultiplier,
        ),
        statusPowerMultiplier = readMultiplier(config, path, ClassBalanceField.STATUS_POWER, fallback.statusPowerMultiplier),
        cooldownFlowMultiplier = readMultiplier(
            config, path, ClassBalanceField.COOLDOWN_FLOW, fallback.cooldownFlowMultiplier,
        ),
    )

    private fun readMultiplier(
        config: FileConfiguration,
        path: String,
        field: ClassBalanceField,
        fallback: Double,
    ): Double {
        val value = config.getDouble("$path.${field.configName}", fallback)
        return normalize(if (value.isFinite()) value else fallback)
    }

    private fun effective(key: String?, field: ClassBalanceField, fallback: Double = 1.0): Double =
        (key?.let { modifiersByKey[it] } ?: defaultModifiers).effective(field).takeIf { it.isFinite() } ?: fallback

    private fun scalePositiveInt(value: Int, multiplier: Double): Int {
        if (value == 0) return 0
        val scaled = (value.toDouble() * multiplier).roundToInt()
        return if (value > 0) scaled.coerceAtLeast(1) else scaled.coerceAtMost(-1)
    }

    private fun configKey(simpleName: String): String = simpleName
        .replace(camelCaseBoundary, "$1-$2")
        .lowercase(Locale.ROOT)

    private fun normalize(value: Double): Double =
        round(value.coerceIn(MINIMUM_MULTIPLIER, MAXIMUM_MULTIPLIER) * 10.0) / 10.0
}
