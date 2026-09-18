package org.beobma.classWarPlugin.growth

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.EntityType

/** Immutable match snapshot. Time values use combat seconds, including during pause. */
data class GrowthSettings(
    val maximumRegions: Int = 16,
    val minimumRegions: Int = 4,
    val generationAttempts: Int = 12,
    val periodSeconds: Int = 120,
    val warningsPerPeriod: Int = 1,
    val forbiddenDamage: Double = 2.0,
    val finalShrinkSeconds: Int = 120,
    val maximumLevel: Int = 30,
    val pointsPerLevel: Int = 3,
    val healthPerLevel: Double = 2.0,
    val experienceBase: Int = 60,
    val experienceStep: Int = 25,
    val mobsPerRegion: Int = DEFAULT_MOBS_PER_REGION,
    val maximumMobs: Int = DEFAULT_MAXIMUM_MOBS,
    val mobExperience: Int = 30,
    val playerExperience: Int = 100,
    val dropChance: Double = DEFAULT_DROP_CHANCE,
    val eventsEnabled: Boolean = true,
    val profiles: Map<String, GrowthProfile> = emptyMap(),
    val events: List<GrowthEventDefinition> = GrowthEventDefinition.defaults,
    val mobHealthVisible: Boolean = true,
) {
    companion object {
        const val DEFAULT_MOBS_PER_REGION = 12
        const val DEFAULT_MAXIMUM_MOBS = 192
        const val DEFAULT_DROP_CHANCE = 0.45

        /** Add new default guardians once, preserving existing events and explicit empty event lists. */
        fun upgradeEventDefaults(config: ConfigurationSection): Boolean {
            val marker = "growth.events-revision"
            if (config.getInt(marker, 0) >= 1) return false
            val hasEvents = config.contains("growth.events", true)
            val explicitlyEmpty = hasEvents && config.getConfigurationSection("growth.events")?.getKeys(false)?.isEmpty() == true
            if (!explicitlyEmpty) GrowthEventDefinition.defaults.filter { !hasEvents || it.mobType != null }.forEach { event ->
                val path = "growth.events.${event.id}"
                if (!config.contains(path, true)) {
                    config.set("$path.day", event.day)
                    config.set("$path.night", event.night)
                    config.set("$path.terrain-tags", event.terrainTags.toList())
                    config.set("$path.reward", event.reward)
                    config.set("$path.lifetime-seconds", event.lifetimeSeconds)
                    event.mobType?.let { config.set("$path.mob-type", it.name) }
                }
            }
            config.set(marker, 1)
            return true
        }

        /** Upgrade only the previous defaults once; preserve customized and disabled populations. */
        fun upgradePopulationDefaults(config: ConfigurationSection): Boolean {
            val marker = "growth.population-revision"
            if (config.getInt(marker, 0) >= 1) return false
            if (config.getInt("growth.mobs.per-region", 4) == 4)
                config.set("growth.mobs.per-region", DEFAULT_MOBS_PER_REGION)
            if (config.getInt("growth.mobs.maximum", 96) == 96)
                config.set("growth.mobs.maximum", DEFAULT_MAXIMUM_MOBS)
            if (config.getDouble("growth.items.drop-chance", 0.18) == 0.18)
                config.set("growth.items.drop-chance", DEFAULT_DROP_CHANCE)
            config.set(marker, 1)
            return true
        }

        const val WARNING = "맵 지형에 따라 지역 생성과 게임 시작이 어려울 수 있으며, 이동 경로에 문제가 발생할 수 있습니다."
        fun read(config: ConfigurationSection): GrowthSettings {
            val root = "growth"
            fun int(key: String, default: Int, range: IntRange) = config.getInt("$root.$key", default).coerceIn(range)
            fun number(key: String, default: Double, min: Double, max: Double) =
                config.getDouble("$root.$key", default).takeIf { it.isFinite() }?.coerceIn(min, max) ?: default
            val max = int("regions.maximum", 16, 2..32)
            val profiles = config.getConfigurationSection("growth.classes")?.getKeys(false).orEmpty().mapNotNull { id ->
                val p = config.getConfigurationSection("growth.classes.$id") ?: return@mapNotNull null
                id to GrowthProfile.read(p, GrowthProfile.forClass(id))
            }.toMap()
            val events = config.getConfigurationSection("growth.events")?.let { section ->
                section.getKeys(false).mapNotNull { id ->
                    val e = section.getConfigurationSection(id) ?: return@mapNotNull null
                    val item = e.getString("reward", "world-tree") ?: "world-tree"
                    if (GrowthItems.byId(item) == null) return@mapNotNull null
                    val mobName = e.getString("mob-type")?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
                    val mobType = mobName?.let { name -> GrowthEventDefinition.monsterTypes.firstOrNull { it.name == name } }
                    if (mobName != null && mobType == null) return@mapNotNull null
                    GrowthEventDefinition(id, e.getInt("day", 2).coerceIn(1, 100),
                        e.getBoolean("night", false), e.getStringList("terrain-tags").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(), item,
                        e.getInt("lifetime-seconds", 90).coerceIn(10, 3600), mobType)
                }
            } ?: GrowthEventDefinition.defaults
            return GrowthSettings(max, int("regions.minimum", 4, 2..max), int("regions.attempts", 12, 1..40),
                int("period-seconds", 120, 10..3600), int("warnings-per-period", 1, 1..8),
                number("forbidden-damage", 2.0, 0.1, 100.0), int("final-shrink-seconds", 120, 10..3600),
                int("level.maximum", 30, 2..100), int("level.points", 3, 1..10),
                number("level.health", 2.0, 0.0, 20.0), int("level.experience-base", 60, 1..10000),
                int("level.experience-step", 25, 0..10000), int("mobs.per-region", DEFAULT_MOBS_PER_REGION, 0..32),
                int("mobs.maximum", DEFAULT_MAXIMUM_MOBS, 0..512), int("mobs.experience", 30, 1..10000),
                int("level.player-experience", 100, 0..10000), number("items.drop-chance", DEFAULT_DROP_CHANCE, 0.0, 1.0),
                config.getBoolean("growth.events-enabled", true), profiles, events,
                config.getBoolean("growth.mobs.show-health", true))
        }
    }
}

data class GrowthEventDefinition(val id: String, val day: Int, val night: Boolean,
    val terrainTags: Set<String>, val reward: String, val lifetimeSeconds: Int, val mobType: EntityType? = null) {
    val phaseIndex get() = (day - 1) * 2 + if (night) 1 else 0
    companion object {
        val monsterTypes = setOf(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
            EntityType.HUSK, EntityType.STRAY, EntityType.DROWNED, EntityType.WITHER_SKELETON)
        val defaults = listOf(
            GrowthEventDefinition("ancient-grove", 2, false, setOf("forest", "plains"), "world-tree", 90),
            GrowthEventDefinition("midnight-relic", 3, true, emptySet(), "moon-heart", 90),
            GrowthEventDefinition("grove-guardian", 1, true, setOf("forest", "plains"), "world-tree", 90, EntityType.ZOMBIE),
            GrowthEventDefinition("moon-guardian", 2, true, emptySet(), "moon-heart", 90, EntityType.SKELETON),
        )
    }
}
