package org.beobma.classWarPlugin.gameClass.referee

import java.util.UUID

enum class CrimeType(val label: String, val severity: Int) { ASSAULT("폭행", 2), MURDER("살인", 3) }
enum class Plea { CONFESS, SELF_DEFENSE, DENY }
data class CrimeRecord(val type: CrimeType, val offender: UUID, val victim: UUID,
    val victimName: String, val tick: Long, val damage: Double, val selfDefense: Boolean)
data class Verdict(val severity: Int, val perjury: Boolean = false)

/** Evidence is local to one referee. A ten-second ceasefire starts a new encounter. */
class CrimeLedger {
    private data class Encounter(val firstAttacker: UUID, var tick: Long,
        val damage: MutableMap<UUID, MutableList<Pair<Long, Double>>> = mutableMapOf())
    private val encounters = mutableMapOf<Set<UUID>, Encounter>()
    private val records = mutableMapOf<UUID, MutableList<CrimeRecord>>()

    fun damage(offender: UUID, victim: UUID, victimName: String, tick: Long, amount: Double) {
        if (offender == victim || !amount.isFinite() || amount <= 0) return
        encounters.entries.removeIf { tick - it.value.tick > 200 }
        val encounter = encounters.getOrPut(setOf(offender, victim)) { Encounter(offender, tick) }
        encounter.tick = tick
        val recent = encounter.damage.getOrPut(offender) { mutableListOf() }
        recent.removeIf { tick - it.first > 200 }
        recent += tick to amount
        val total = recent.sumOf { it.second }
        if (total >= 8.0) {
            add(CrimeRecord(CrimeType.ASSAULT, offender, victim, victimName, tick, total,
                encounter.firstAttacker != offender))
            recent.clear()
        }
    }

    fun murder(offender: UUID, victim: UUID, victimName: String, tick: Long) {
        if (offender == victim) return
        val encounter = encounters[setOf(offender, victim)]?.takeIf { tick - it.tick <= 200 }
        add(CrimeRecord(CrimeType.MURDER, offender, victim, victimName, tick, 0.0,
            encounter != null && encounter.firstAttacker != offender))
        encounters.remove(setOf(offender, victim))
    }

    private fun add(record: CrimeRecord) {
        val list = records.getOrPut(record.offender) { mutableListOf() }
        if (list.size >= 256) list.removeAt(list.indexOfFirst { it.type == CrimeType.ASSAULT }.coerceAtLeast(0))
        list += record
    }
    fun heaviest(id: UUID): CrimeRecord? = records[id]?.maxByOrNull { it.type.severity }
    fun murders(id: UUID): Int = records[id]?.count { it.type == CrimeType.MURDER } ?: 0
    fun clear() { encounters.clear(); records.clear() }

    companion object {
        /** Preview evidence is ephemeral and must never enter the real crime ledger. */
        fun trialCharge(record: CrimeRecord?, training: Boolean, defendant: UUID, tick: Long): CrimeRecord? =
            record ?: if (training) CrimeRecord(CrimeType.ASSAULT, defendant, UUID(0, 0),
                "연습용 대상", tick, 8.0, selfDefense = true) else null

        fun verdict(record: CrimeRecord, plea: Plea?, murders: Int): Verdict = when (plea) {
            Plea.CONFESS -> Verdict((record.type.severity - 1).coerceAtLeast(1))
            Plea.SELF_DEFENSE -> if (record.selfDefense) Verdict(0) else
                Verdict(if (record.type == CrimeType.MURDER && murders >= 2) 4 else 3, true)
            Plea.DENY -> Verdict(if (record.type == CrimeType.MURDER && murders >= 2) 4 else 3, true)
            null -> Verdict(record.type.severity)
        }
    }
}
