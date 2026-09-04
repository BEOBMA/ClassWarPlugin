package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.ability.AbilityExecution

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import kotlin.math.round
import kotlin.math.roundToInt

/** 클래스별로 조정 가능한 밸런스 축과 대응 설정 키다. */
enum class ClassBalanceField(val configName: String) {
    OVERALL("overall-multiplier"),
    DAMAGE("damage-multiplier"),
    HEALING("healing-multiplier"),
    RANGE("range-multiplier"),
    STATUS_DURATION("status-duration-multiplier"),
    STATUS_POWER("status-power-multiplier"),
    COOLDOWN_FLOW("cooldown-flow-multiplier"),
}

/**
 * 한 클래스에 적용할 독립적인 밸런스 배율 묶음이다.
 * [overallMultiplier]는 피해·회복·사거리·상태 지속시간·상태 세기와 곱해진다.
 */
data class ClassBalanceModifiers(
    val overallMultiplier: Double = 1.0,
    val damageMultiplier: Double = 1.0,
    val healingMultiplier: Double = 1.0,
    val rangeMultiplier: Double = 1.0,
    val statusDurationMultiplier: Double = 1.0,
    val statusPowerMultiplier: Double = 1.0,
    val cooldownFlowMultiplier: Double = 1.0,
) {
    /** [field]에 저장된 원시 배율을 반환한다. */
    fun value(field: ClassBalanceField): Double = when (field) {
        ClassBalanceField.OVERALL -> overallMultiplier
        ClassBalanceField.DAMAGE -> damageMultiplier
        ClassBalanceField.HEALING -> healingMultiplier
        ClassBalanceField.RANGE -> rangeMultiplier
        ClassBalanceField.STATUS_DURATION -> statusDurationMultiplier
        ClassBalanceField.STATUS_POWER -> statusPowerMultiplier
        ClassBalanceField.COOLDOWN_FLOW -> cooldownFlowMultiplier
    }

    /** 전체 배율을 포함해 실제 계산에 사용할 [field]의 배율을 반환한다. */
    fun effective(field: ClassBalanceField): Double = when (field) {
        ClassBalanceField.OVERALL -> overallMultiplier
        ClassBalanceField.COOLDOWN_FLOW -> cooldownFlowMultiplier
        else -> overallMultiplier * value(field)
    }

    /** [field]만 [value]로 교체한 사본을 반환한다. */
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

/**
 * 클래스별 배율을 설정 파일에서 읽고 스킬 호출 문맥에 맞는 배율을 선택한다.
 * 배율은 `0.1..10.0` 범위의 소수 첫째 자리로 정규화된다.
 */
object ClassBalanceManager {
    private const val ROOT_PATH = "class-balance"
    private const val MINIMUM_MULTIPLIER = 0.1
    private const val MAXIMUM_MULTIPLIER = 10.0
    private val miniMessageTagPattern = Regex("<[^>]+>")
    private val camelCaseBoundary = Regex("([a-z0-9])([A-Z])")

    private data class ClassDescriptor(val canonicalName: String, val configKey: String)

    @Volatile
    private var defaultModifiers = ClassBalanceModifiers()

    @Volatile
    private var modifiersByKey: Map<String, ClassBalanceModifiers> = emptyMap()

    @Volatile
    private var descriptors: List<ClassDescriptor> = emptyList()

    /** 등록 클래스의 배율을 불러오고 누락된 설정 항목을 기본값으로 생성한다. */
    fun load(config: FileConfiguration, classes: Collection<GameClass>) {
        val uniqueClasses = classes.distinctBy { it.javaClass.name }
        descriptors = uniqueClasses.map { ClassDescriptor(it.javaClass.name, configKey(it)) }

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

    /** 클래스 단순 이름을 안정적인 kebab-case 설정 키로 변환한다. */
    fun configKey(gameClass: GameClass): String = gameClass.classId

    /** [gameClass]의 현재 배율을 반환하며 미등록 클래스에는 기본 배율을 사용한다. */
    fun modifiers(gameClass: GameClass): ClassBalanceModifiers =
        modifiersByKey[configKey(gameClass)] ?: defaultModifiers

    /** [field]를 `0.1 * stepMultiplier`만큼 변경하고 즉시 설정 파일에 저장한다. */
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

    /** [gameClass]의 모든 배율을 현재 기본 배율로 되돌리고 저장한다. */
    fun reset(gameClass: GameClass) {
        val key = configKey(gameClass)
        modifiersByKey = modifiersByKey.toMutableMap().apply { put(key, defaultModifiers) }
        val plugin = ClassWarPlugin.instance
        ClassBalanceField.entries.forEach { field ->
            plugin.config.set("$ROOT_PATH.classes.$key.${field.configName}", defaultModifiers.value(field))
        }
        plugin.saveConfig()
    }

    /** 경기 전역 배율과 [skill] 소유 클래스의 쿨다운 흐름 배율을 결합한다. */
    fun cooldownFlowMultiplier(player: Player, skill: Skill): Double {
        val game = GameManager.findGameForPlayer(player)
        val globalMultiplier = game?.settings?.cooldownFlowMultiplier ?: 1.0
        val playerData = game?.playerDatas
            ?.filterIsInstance<PlayerData>()
            ?.firstOrNull { it.uniqueId == player.uniqueId }
            ?: return globalMultiplier
        val key = resolveSkillKey(skill)
        val classMultiplier = modifiersByKey[key]?.cooldownFlowMultiplier ?: 1.0
        return (globalMultiplier * classMultiplier).coerceAtLeast(MINIMUM_MULTIPLIER)
    }

    /** 피해 경로와 호출 클래스에 맞는 피해 배율을 [amount]에 적용한다. */
    fun scaleDamage(attacker: PlayerData, path: DamagePath, amount: Double): Double {
        val key = if (path.isBasicAttack) {
            getWeaponClassId(attacker.player.inventory.itemInMainHand)?.let(::keyForClassName)
                ?: resolveCallerKey(attacker)
        } else {
            resolveCallerKey(attacker)
        }
        return amount * effective(key, ClassBalanceField.DAMAGE)
    }

    /** 호출 클래스의 회복 배율을 [amount]에 적용한다. */
    fun scaleHealing(healer: PlayerData, amount: Double): Double =
        amount * effective(resolveCallerKey(healer), ClassBalanceField.HEALING)

    /** 호출 클래스의 사거리 배율을 [amount]에 적용하고 음수 결과를 방지한다. */
    fun scaleRange(source: EntityData, amount: Double): Double {
        return (amount * rangeMultiplier(source)).coerceAtLeast(0.0)
    }

    /** [source]의 호출 클래스에 해당하는 사거리 배율을 반환한다. */
    fun rangeMultiplier(source: EntityData): Double {
        val playerData = source as? PlayerData ?: return 1.0
        return effective(resolveCallerKey(playerData), ClassBalanceField.RANGE)
    }

    /** 상태 지속시간에 호출 클래스 배율을 적용하되 0이 아닌 값의 부호를 보존한다. */
    fun scaleStatusDuration(caster: PlayerData?, duration: Int): Int =
        scalePositiveInt(duration, effective(caster?.let(::resolveCallerKey), ClassBalanceField.STATUS_DURATION))

    /** 상태 세기에 호출 클래스 배율을 적용하되 0이 아닌 값의 부호를 보존한다. */
    fun scaleStatusPower(caster: PlayerData?, power: Int): Int =
        scalePositiveInt(power, effective(caster?.let(::resolveCallerKey), ClassBalanceField.STATUS_POWER))

    private fun resolveSkillKey(skill: Skill): String = skill.definitionId.substringBefore('/')

    private fun resolveCallerKey(playerData: PlayerData): String? =
        AbilityExecution.current
            ?.takeIf { it.playerData === playerData }?.classId

    private fun keyForClassName(className: String): String? = descriptors.firstOrNull { descriptor ->
        className == descriptor.configKey || className == descriptor.canonicalName || className.startsWith("${descriptor.canonicalName}\$")
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

    private fun normalize(value: Double): Double =
        round(value.coerceIn(MINIMUM_MULTIPLIER, MAXIMUM_MULTIPLIER) * 10.0) / 10.0
}
