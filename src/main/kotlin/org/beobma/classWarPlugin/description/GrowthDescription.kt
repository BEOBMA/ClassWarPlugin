package org.beobma.classWarPlugin.description

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.damageMultiplier
import org.beobma.classWarPlugin.growth.*
import org.beobma.classWarPlugin.manager.GameManager
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.manager.ClassBalanceField
import org.beobma.classWarPlugin.manager.DamageManager
import org.beobma.classWarPlugin.damage.DamagePath
import org.bukkit.entity.Player
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Explicit numeric bindings, never guesses whether an arbitrary number in prose is a game rule. */
object GrowthDescription {
    data class Context(val classId: String, val profile: GrowthProfile, val stats: (GrowthStat) -> Int,
        val cooldownFlow: Double = 1.0, val baseMultipliers: Map<String, Double> = emptyMap())
    private val token = Regex("\\{g:([a-z/-]+):(-?\\d+(?:\\.\\d+)?)\\}")

    fun context(player: Player, classId: String?): Context? {
        if (classId == null) return null
        val game = GameManager.findGameForPlayer(player) ?: return null
        if (!game.mode.isGrowth) return null
        val data = game.playerDatas.filterIsInstance<PlayerData>().firstOrNull { it.uniqueId == player.uniqueId } ?: return null
        return forData(data, classId)
    }

    fun forData(data: PlayerData, classId: String): Context? {
        val game = data.game
        val state = game.growth?.players?.get(data.uniqueId) ?: return null
        if (!game.mode.isGrowth) return null
        fun factor(field: ClassBalanceField) = ClassBalanceManager.descriptionMultiplier(classId, field)
        return Context(classId, GrowthScaling.profile(data, classId), state::stat,
            game.settings.cooldownFlowMultiplier * factor(ClassBalanceField.COOLDOWN_FLOW) *
                if (state.has(GrowthEffect.FOCUS)) 1.1 else 1.0,
            mapOf("damage" to factor(ClassBalanceField.DAMAGE) * game.settings.damageMultiplier(DamagePath.SKILL),
                "status-damage" to factor(ClassBalanceField.DAMAGE) * game.settings.damageMultiplier(DamagePath.STATUS_EFFECT),
                "ranged" to factor(ClassBalanceField.DAMAGE) * game.settings.damageMultiplier(DamagePath.RANGED_ATTACK) * DamageManager.BASIC_ATTACK_DAMAGE_MULTIPLIER,
                "attack-bonus" to DamageManager.BASIC_ATTACK_DAMAGE_MULTIPLIER,
                "healing" to factor(ClassBalanceField.HEALING), "range" to factor(ClassBalanceField.RANGE),
                "duration" to factor(ClassBalanceField.STATUS_DURATION),
                "power" to factor(ClassBalanceField.STATUS_POWER), "speed" to factor(ClassBalanceField.STATUS_POWER),
                "physical-power" to factor(ClassBalanceField.STATUS_POWER), "shield" to factor(ClassBalanceField.STATUS_POWER)))
    }

    fun baseText(line: String): String = token.replace(line) { it.groupValues[2] }
    fun render(lines: List<String>, context: Context?): List<String> = lines.map { render(it, context) }
    fun render(line: String, context: Context?): String = token.replace(line) { match ->
        val original = match.groupValues[2]
        if (context == null) return@replace original
        val base = original.toDouble()
        val (value, coefficient) = evaluate(match.groupValues[1], base, context)
        val displayed = number(value)
        val numeric = if (displayed.toDouble() > base) "<green>$displayed</green>" else displayed
        "$numeric($coefficient)"
    }

    fun number(value: Double): String = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()

    internal fun evaluate(operation: String, base: Double, context: Context): Pair<Double, String> {
        val profile = context.profile
        if (operation == "writer-reward") {
            val (basic, basicCoefficient) = evaluate("basic", base, context)
            val rule = GrowthClassCatalog.features.getValue("writer").getValue("reward")
            return rule.apply(basic, context.stats) to "$basicCoefficient · ${rule.stat.label} ${number(rule.percent)}% 추가 배율"
        }
        if (operation == "mastery-damage") {
            val rule = GrowthClassCatalog.features.getValue("weapon-master").getValue("mastery")
            return rule.apply(base / 10, context.stats) * 10 to "${rule.stat.label} ${rule.pointsPerStep}당 +10%p"
        }
        if (operation.startsWith("feature/")) {
            val rule = GrowthClassCatalog.features.getValue(context.classId).getValue(operation.substringAfter('/'))
            val coefficient = if (rule.flatStep > 0)
                "${rule.stat.label} ${rule.pointsPerStep}당 +${rule.flatStep} · 최대 ${number(base + rule.maximumExtra)}"
            else "${rule.stat.label} ${number(base * rule.percent)}%"
            val raw = rule.apply(base, context.stats)
            val value = if (operation.substringAfter('/') in setOf("answer-time", "code-time")) floor(raw) else raw
            return value to coefficient
        }
        val axis = when (operation) {
            "damage", "status-damage" -> GrowthAxis.SKILL_DAMAGE
            "basic", "ranged", "attack-bonus" -> GrowthAxis.BASIC_DAMAGE
            "healing" -> GrowthAxis.HEALING
            "range" -> GrowthAxis.RANGE
            "duration", "time", "duration-floor" -> GrowthAxis.DURATION
            "speed", "speed-bonus" -> GrowthAxis.SPEED
            "health", "health-bonus", "shield" -> GrowthAxis.SHIELD
            "physical-power" -> GrowthAxis.PHYSICAL_POWER
            "power" -> GrowthAxis.POWER
            "cap" -> GrowthAxis.CAP
            "chance", "risk" -> GrowthAxis.PROBABILITY
            "cooldown", "reload", "reload-seconds" -> GrowthAxis.COOLDOWN
            "knockback" -> GrowthAxis.KNOCKBACK
            else -> error("Unknown growth description operation: $operation")
        }
        val multiplier = profile.multiplier(axis, context.stats)
        val scaledBase = base * (context.baseMultipliers[operation] ?: 1.0)
        val raw = scaledBase * multiplier
        val value = when (operation) {
            "duration", "power", "speed", "shield", "physical-power" -> when {
                base > 0 -> raw.roundToInt().coerceAtLeast(1).toDouble()
                base < 0 -> raw.roundToInt().coerceAtMost(-1).toDouble()
                else -> 0.0
            }
            "cap" -> raw.roundToInt().toDouble()
            "time" -> floor(raw * 20) / 20
            "duration-floor" -> floor(raw)
            "cooldown" -> ceil(base * 20 / (multiplier * context.cooldownFlow).coerceAtLeast(0.1)) / 20
            "reload" -> ceil(base * 20 / multiplier) / 20
            "reload-seconds" -> ceil(base / multiplier)
            "chance" -> raw.coerceIn(0.0, maxOf(base, 85.0).coerceAtMost(100.0))
            "risk" -> base / multiplier
            else -> raw
        }
        val coefficient = if (axis == GrowthAxis.SKILL_DAMAGE || axis == GrowthAxis.BASIC_DAMAGE) {
            val weight = if (axis == GrowthAxis.BASIC_DAMAGE) profile.basicDamageWeight else profile.skillDamageWeight
            listOf(profile.primary to scaledBase * profile.primaryPercent * weight,
                profile.secondary to scaledBase * profile.secondaryPercent * weight)
                .groupBy({ it.first }, { it.second }).map { (stat, values) -> stat to values.sum() }
                .filter { it.second != 0.0 }.joinToString(" + ") { "${it.first.label} ${number(it.second)}%" }.ifEmpty { "계수 0%" }
        } else {
            val rule = profile.effects.getValue(axis)
            when (operation) {
                "cooldown", "reload", "reload-seconds" -> "${rule.stat.label} ${number(rule.percent)}% 회복 속도"
                "risk" -> "${rule.stat.label} ${number(rule.percent)}% 역비례"
                else -> "${rule.stat.label} ${number(scaledBase * rule.percent)}%"
            }
        }
        return value to coefficient
    }
}
