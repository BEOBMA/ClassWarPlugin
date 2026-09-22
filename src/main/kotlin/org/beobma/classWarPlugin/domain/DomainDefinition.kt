package org.beobma.classWarPlugin.domain

import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

enum class DomainTarget {
    SURROUNDING, LOOKING_AT_PLAYER;

    fun <T> resolve(selected: T?, caster: T, training: Boolean): T? = when (this) {
        SURROUNDING -> null
        LOOKING_AT_PLAYER -> selected ?: caster.takeIf { training }
    }
}

/** Relative block coordinates. The runtime owns all terrain changes and restores block states. */
data class DomainBlock(val x: Int, val y: Int, val z: Int, val material: Material)

data class DomainDefinition(
    val name: String,
    val radius: Int,
    val durationTicks: Int,
    val target: DomainTarget = DomainTarget.SURROUNDING,
    val targetRange: Double = radius.toDouble() - 2.0,
    val subtitleDelayMillis: Long = 600,
    val titleDurationMillis: Long = 2600,
    val floor: Material = Material.DEEPSLATE_TILES,
    val interior: (Int) -> List<DomainBlock> = { emptyList() },
    val onStart: (DomainSession) -> Unit = {},
    val onTick: (DomainSession) -> Unit = {},
    val onEnd: (DomainSession) -> Unit = {},
    val presentation: (DomainSession) -> DomainPresentation = { DomainEffects(it) },
    /** Null leaves lighting unchanged; explicit LIGHT blocks are restored with the terrain. */
    val interiorLightLevel: Int? = null,
) {
    init {
        require(name.isNotBlank())
        require(radius in 4..24) { "Domain radius must be between 4 and 24 blocks" }
        require(durationTicks > 0 && targetRange.isFinite() && targetRange > 0)
        require(subtitleDelayMillis >= 0 && titleDurationMillis > subtitleDelayMillis)
        require(floor.isBlock && floor.isSolid)
        require(interiorLightLevel == null || interiorLightLevel in 0..15)
    }
}

/** Add an instance to a SPECIAL class's skills, with its own definition ID and cooldown. */
class DomainSkill(
    override val definitionId: String,
    val domain: DomainDefinition,
    override val cooldown: Int?,
) : Skill() {
    override val name = "영역전개: ${domain.name}"
    override val description = listOf("3초간 무방비 상태로 {keyword:Area}을 전개한다.",
        "전개 연출 동안 주변에 {keyword:Distortion}을 적용한다.")
    override fun use(): Boolean = DomainManager.expand(abilityScope, domain)
}

/** Monotonic real time for the introduction; combat duration is deliberately separate. */
class DomainTimeline(private val startedNanos: Long, private val subtitleDelay: Long, private val titleDuration: Long) {
    private var titleStartedNanos: Long? = null
    fun elapsedMillis(nowNanos: Long) = ((nowNanos - startedNanos) / 1_000_000L).coerceAtLeast(0)
    fun castComplete(nowNanos: Long) = elapsedMillis(nowNanos) >= 3000
    fun beginTitle(nowNanos: Long) { if (titleStartedNanos == null) titleStartedNanos = nowNanos }
    fun subtitleVisible(nowNanos: Long) = titleStartedNanos?.let { (nowNanos - it) / 1_000_000 >= subtitleDelay } ?: false
    fun fightStarted(nowNanos: Long) = titleStartedNanos?.let { (nowNanos - it) / 1_000_000 >= titleDuration } ?: false
}
