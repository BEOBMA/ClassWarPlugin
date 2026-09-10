package org.beobma.classWarPlugin.gameClass.agent

import java.util.UUID

/** Combat-tick based state; no wall clock, Bukkit tasks or entity references. */
internal class AgentState {
    var basicHits = 0; private set
    var nextMorph = 200L; private set
    var successes = 0; private set
    var failures = 0; private set
    var directive = 0; private set
    var deadline = 200L; private set
    var target: UUID? = null; private set
    var requiredWeapon = 0; private set
    var progress = 0; private set
    var resolved = false; private set
    private val hitTargets = mutableSetOf<UUID>()
    private var skillHit = false
    private var basicUsed = false
    var lastSucceeded: Boolean? = null; private set
    var dealtPercent = 100; private set
    var takenPercent = 100; private set
    val dealtMultiplier get() = dealtPercent / 100.0
    val takenMultiplier get() = takenPercent / 100.0
    val nextDirective get() = if (lastSucceeded == false) directive else if (directive >= 10) 1 else directive + 1

    fun reset(now: Long) {
        basicHits = 0; nextMorph = now + 200; successes = 0; failures = 0
        directive = 0; deadline = now + 200; progress = 0; resolved = false
        hitTargets.clear(); target = null; skillHit = false; basicUsed = false
        lastSucceeded = null; dealtPercent = 100; takenPercent = 100
    }
    fun morphed(now: Long) { basicHits = 0; nextMorph = now + 200 }
    fun basicHit(): Boolean { basicHits++; return basicHits >= 3 }
    fun begin(now: Long, targetId: UUID?, weapon: Int) {
        directive = nextDirective
        target = targetId; requiredWeapon = weapon; progress = 0; resolved = false
        hitTargets.clear(); skillHit = false; basicUsed = false
        deadline = if (directive == 10) Long.MAX_VALUE else now + when (directive) {
            1 -> 200; 2 -> 160; 3 -> 120; else -> 400
        }
    }
    fun basicUsed() { if (directive == 3) basicUsed = true }
    fun hit(id: UUID, basic: Boolean, skill: Boolean, weapon: Int, behind: Boolean, sprinting: Boolean) {
        if (resolved || directive == 0) return
        if (basic) basicUsed()
        if (skill) skillHit = true
        hitTargets += id
        when (directive) {
            1, 5 -> if (id == target) progress = 1
            2 -> progress = hitTargets.size
            4 -> if (weapon == requiredWeapon) progress = 1
            6 -> if (basic) progress++
            7 -> if (behind) progress = 1
            8 -> if (sprinting && basic) progress = 1
            9 -> if (skill) progress = 1
        }
    }
    fun result(now: Long, allEnemiesDefeated: Boolean = false): Boolean? {
        if (directive == 0 || resolved) return null
        if (directive == 10) return if (allEnemiesDefeated) true else null
        if (directive == 3 && basicUsed) return false
        val complete = when (directive) {
            2 -> if (progress >= 2) true else null
            3 -> if (now >= deadline && skillHit) true else null
            6 -> if (progress >= 3) true else null
            else -> if (progress > 0) true else null
        }
        return complete ?: if (now >= deadline) false else null
    }
    fun resolve(success: Boolean) {
        if (resolved) return
        resolved = true
        lastSucceeded = success
        if (success) {
            successes++
            dealtPercent += 20
            takenPercent = (takenPercent - 10).coerceAtLeast(0)
        }
        else { failures++; dealtPercent = (dealtPercent - 5).coerceAtLeast(50); takenPercent = (takenPercent + 5).coerceAtMost(150) }
    }
}
