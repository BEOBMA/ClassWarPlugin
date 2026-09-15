package org.beobma.classWarPlugin.growth

import org.beobma.classWarPlugin.ability.AbilityExecution
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.bukkit.configuration.ConfigurationSection
import kotlin.math.roundToInt

enum class GrowthStat(val label: String) { STRENGTH("힘"), AGILITY("민첩"), INTELLIGENCE("지능"), LUCK("운") }
enum class GrowthAxis(val label: String) {
    BASIC_DAMAGE("기본 공격 피해"), SKILL_DAMAGE("스킬·패시브 피해"), HEALING("회복량"), COOLDOWN("쿨다운 흐름"),
    RANGE("사거리"), KNOCKBACK("밀치기"), DURATION("효과 지속시간"), POWER("상태 위력"), CAP("상태 최대치"),
    PROBABILITY("유리한 확률"), SPEED("이동·공격 속도 효과"), SHIELD("보호막·유예 체력"), PHYSICAL_POWER("출혈·진동 위력")
}

data class GrowthEffectRule(val stat: GrowthStat, val percent: Double, val maximumBonus: Double) {
    fun multiplier(stats: (GrowthStat) -> Int) = 1.0 + (stats(stat).coerceAtLeast(0) * percent / 100).coerceIn(0.0, maximumBonus)
}

/** Ratios are percentages of the original value per point, never flat damage per hit. */
data class GrowthProfile(val primary: GrowthStat, val secondary: GrowthStat,
    val primaryPercent: Double = 1.5, val secondaryPercent: Double = 0.6,
    val basicDamageWeight: Double = 1.0, val skillDamageWeight: Double = 1.0,
    val effects: Map<GrowthAxis, GrowthEffectRule> = defaultEffects) {
    fun multiplier(axis: GrowthAxis, stats: (GrowthStat) -> Int): Double {
        if (axis == GrowthAxis.BASIC_DAMAGE || axis == GrowthAxis.SKILL_DAMAGE) {
            val weighted = stats(primary).coerceAtLeast(0).toDouble() * primaryPercent + stats(secondary).coerceAtLeast(0).toDouble() * secondaryPercent
            // No gameplay damage ceiling: basic and ability damage have independent slopes.
            return 1.0 + weighted * (if (axis == GrowthAxis.BASIC_DAMAGE) basicDamageWeight else skillDamageWeight) / 100
        }
        return effects.getValue(axis).multiplier(stats)
    }
    companion object {
        val defaultEffects = mapOf(
            GrowthAxis.HEALING to GrowthEffectRule(GrowthStat.INTELLIGENCE, 0.8, 1.0),
            GrowthAxis.COOLDOWN to GrowthEffectRule(GrowthStat.INTELLIGENCE, 0.5, 0.5),
            GrowthAxis.RANGE to GrowthEffectRule(GrowthStat.AGILITY, 0.2, 0.25),
            GrowthAxis.KNOCKBACK to GrowthEffectRule(GrowthStat.STRENGTH, 0.5, 0.5),
            GrowthAxis.DURATION to GrowthEffectRule(GrowthStat.INTELLIGENCE, 0.4, 0.5),
            GrowthAxis.POWER to GrowthEffectRule(GrowthStat.INTELLIGENCE, 0.6, 1.0),
            GrowthAxis.CAP to GrowthEffectRule(GrowthStat.INTELLIGENCE, 0.6, 1.0),
            GrowthAxis.PROBABILITY to GrowthEffectRule(GrowthStat.LUCK, 0.5, 0.5),
            GrowthAxis.SPEED to GrowthEffectRule(GrowthStat.AGILITY, 0.5, 0.5),
            GrowthAxis.SHIELD to GrowthEffectRule(GrowthStat.STRENGTH, 0.8, 1.0),
            GrowthAxis.PHYSICAL_POWER to GrowthEffectRule(GrowthStat.STRENGTH, 0.6, 1.0),
        )
        private val intellect = setOf("abyssal-veil", "barrier", "back-room", "contractor", "death-note", "darkness",
            "elementalist", "astronomer", "geometer", "grass", "area-development", "hacker", "hikikomori",
            "ice-wizard", "just-light", "jupiter", "land-wizard", "lightning-wizard", "light-wizard", "luna",
            "mathematician", "meteor", "neptune", "parasite", "pacifist", "pat-and-matt", "portal-gun", "pluto",
            "referee", "rainbow-bridge", "saturnus", "sol", "solar-system", "terra", "time-maniqulator", "tour",
            "uranus", "warlock", "writer", "watchmaker", "venus")
        private val agile = setOf("agent", "assassin", "chameleon", "crossbow", "charger", "duelist", "feather",
            "freikugel", "ghost", "gun-blader", "hide-and-seek", "high-jumper", "mercurius", "phantom", "pioneer",
            "refugees", "sagittarius", "sniper", "shy-person", "spider-man", "stalker", "swordplay", "trapper",
            "thunderclap-flash", "vampire", "warcorrespondent", "wounds-wind")
        private val lucky = setOf("blacksmith", "con-artist", "damocles", "error", "exodia", "fear", "gambler",
            "lucky-one", "roulette", "tonic")
        fun forClass(id: String): GrowthProfile {
          val base = when (id) {
            in intellect -> GrowthProfile(GrowthStat.INTELLIGENCE, GrowthStat.AGILITY)
            in agile -> GrowthProfile(GrowthStat.AGILITY, GrowthStat.STRENGTH)
            in lucky -> GrowthProfile(GrowthStat.LUCK, GrowthStat.INTELLIGENCE)
            else -> GrowthProfile(GrowthStat.STRENGTH, GrowthStat.AGILITY)
          }
          val style = GrowthClassCatalog.style(id)
          return base.copy(basicDamageWeight = style.basicWeight, skillDamageWeight = style.skillWeight)
        }
        fun read(config: ConfigurationSection, fallback: GrowthProfile): GrowthProfile {
            fun stat(key: String, default: GrowthStat) = GrowthStat.entries.firstOrNull {
                it.name.equals(config.getString(key), true) } ?: default
            fun ratio(key: String, default: Double) = config.getDouble(key, default)
                .takeIf { it.isFinite() }?.coerceIn(0.0, 5.0) ?: default
            val effects = fallback.effects.mapValues { (axis, rule) ->
                val path = "effects.${axis.name.lowercase().replace('_', '-')}"
                // Effect caps remain safety limits. Damage axes deliberately have no cap setting.
                GrowthEffectRule(stat("$path.stat", rule.stat), ratio("$path.percent", rule.percent), rule.maximumBonus)
            }
            return GrowthProfile(stat("primary", fallback.primary), stat("secondary", fallback.secondary),
                ratio("primary-percent", fallback.primaryPercent), ratio("secondary-percent", fallback.secondaryPercent),
                ratio("basic-damage-weight", fallback.basicDamageWeight), ratio("skill-damage-weight", fallback.skillDamageWeight), effects)
        }
    }
}

class GrowthPlayerState {
    var level = 1; private set
    var experience = 0; private set
    var points = 0; private set
    private val allocated = IntArray(GrowthStat.entries.size)
    val equipment = mutableMapOf<GrowthSlot, String>()
    val inventory = linkedSetOf<String>()
    private val cooldowns = mutableMapOf<String, Long>()
    fun base(stat: GrowthStat) = allocated[stat.ordinal]
    fun stat(stat: GrowthStat) = base(stat) + equipment.values.sumOf { GrowthItems.byId(it)?.stats?.get(stat) ?: 0 }
    fun has(effect: GrowthEffect) = equipment.values.any { GrowthItems.byId(it)?.effect == effect }
    fun requiredExperience(settings: GrowthSettings) = settings.experienceBase + (level - 1) * settings.experienceStep
    fun experienceProgress(settings: GrowthSettings): Float =
        if (level >= settings.maximumLevel) 1f else (experience.toFloat() / requiredExperience(settings)).coerceIn(0f, 1f)
    fun gain(amount: Int, settings: GrowthSettings): Int {
        if (amount <= 0 || level >= settings.maximumLevel) return 0
        experience = (experience.toLong() + amount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        var gained = 0
        while (level < settings.maximumLevel && experience >= requiredExperience(settings)) {
            experience -= requiredExperience(settings); level++; points += settings.pointsPerLevel; gained++
        }
        if (level == settings.maximumLevel) experience = 0
        return gained
    }
    fun allocate(stat: GrowthStat, amount: Int = 1): Boolean {
        if (amount <= 0 || points < amount) return false
        points -= amount; allocated[stat.ordinal] += amount; return true
    }
    fun equip(id: String): Boolean {
        if (id !in inventory) return false
        val item = GrowthItems.byId(id) ?: return false
        if (equipment[item.slot] == id) equipment.remove(item.slot) else equipment[item.slot] = id
        return true
    }
    fun trigger(key: String, tick: Long, cooldownSeconds: Int): Boolean {
        if (tick < (cooldowns[key] ?: Long.MIN_VALUE)) return false
        cooldowns[key] = tick + cooldownSeconds * 20L; return true
    }
}

object GrowthScaling {
    fun profile(data: PlayerData, classId: String? = null): GrowthProfile {
        val id = classId ?: AbilityExecution.current?.takeIf { it.playerData === data }?.classId
            ?: data.gameClasses.firstOrNull()?.classId ?: "general-person"
        return data.game.settings.growth.profiles[id] ?: GrowthProfile.forClass(id)
    }
    fun multiplier(data: PlayerData?, axis: GrowthAxis, classId: String? = null): Double {
        if (data == null || !data.game.mode.isGrowth) return 1.0
        val state = data.game.growth?.players?.get(data.uniqueId) ?: return 1.0
        return profile(data, classId).multiplier(axis, state::stat)
    }
    fun chance(data: PlayerData, probability: Double): Double =
        if (!data.game.mode.isGrowth) probability else
            (probability * multiplier(data, GrowthAxis.PROBABILITY)).coerceIn(0.0, maxOf(probability, 0.85).coerceAtMost(1.0))
    fun knockback(data: PlayerData, velocity: org.bukkit.util.Vector): org.bukkit.util.Vector =
        velocity.clone().multiply(multiplier(data, GrowthAxis.KNOCKBACK))
    fun cap(data: PlayerData?, amount: Int, classId: String? = null, axis: GrowthAxis = GrowthAxis.CAP) =
        (amount * multiplier(data, axis, classId)).roundToInt().coerceAtLeast(amount)
    fun statusAxis(status: org.beobma.classWarPlugin.status.StatusAbnormality): GrowthAxis = when (status) {
        is org.beobma.classWarPlugin.status.handler.MoveSpeedHandler,
        is org.beobma.classWarPlugin.status.handler.AttackSpeedHandler -> GrowthAxis.SPEED
        is org.beobma.classWarPlugin.status.list.Shield -> GrowthAxis.SHIELD
        is org.beobma.classWarPlugin.status.list.Bleeding,
        is org.beobma.classWarPlugin.status.list.Vibration -> GrowthAxis.PHYSICAL_POWER
        else -> GrowthAxis.POWER
    }
    fun feature(data: PlayerData, classId: String, key: String, amount: Double): Double {
        val rule = GrowthClassCatalog.features[classId]?.get(key) ?: error("Unregistered growth feature: $classId/$key")
        val state = data.game.growth?.players?.get(data.uniqueId)
        if (!data.game.mode.isGrowth || state == null) return amount
        return rule.apply(amount, state::stat)
    }
    fun count(data: PlayerData, classId: String, key: String, amount: Int): Int =
        feature(data, classId, key, amount.toDouble()).toInt().coerceAtLeast(amount)
    fun cooldown(data: PlayerData, amount: Int, classId: String): Int =
        kotlin.math.ceil(amount / multiplier(data, GrowthAxis.COOLDOWN, classId)).toInt().coerceAtLeast(1)
    fun harmfulChance(data: PlayerData, probability: Double): Double =
        (probability / multiplier(data, GrowthAxis.PROBABILITY)).coerceIn(0.0, 1.0)
}
