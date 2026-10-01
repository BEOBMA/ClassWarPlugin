package org.beobma.classWarPlugin.testing

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.*
import org.bukkit.entity.LivingEntity

/** Explicit constructors avoid reflecting into private class/session implementations. */
internal object StatusTestCatalog {
    data class Entry(val id: String, val factory: (EntityData) -> StatusAbnormality)
    private fun entry(factory: () -> StatusAbnormality): Entry {
        val sample = factory()
        return Entry(sample.javaClass.simpleName) { factory() }
    }
    val entries = listOf(
        entry(::Aftermath), entry(::Resonance), entry(::Settlement),
        entry(::Abyss), entry(::AttackSpeedDecrease), entry(::AttackSpeedIncrease), entry(::Bleeding),
        entry(::BleedingLock), entry(::Brightness), entry(::Burn), entry { Charge() }, entry(::Disarm),
        entry(::Distortion), entry(::Electrocution), entry(::Enchantment), entry(::Erosion), entry(::Fix),
        entry(::Freezing), entry(::Frostbite), entry(::Invincibility), entry(::Mana), entry(::MoveSpeedDecrease),
        entry(::MoveSpeedIncrease), entry(::Radiation), entry(::Shield), entry(::Silence), entry(::Snare),
        entry(::Stealth), entry(::Stun), entry(::Vibration), entry { VibrationExplosion() },
        entry(::WhenDamageIncreased), entry(::WhenDamageReduction), entry(::GunBulletStatus),
        entry(::GamblerCardStatus), entry(::TimePhaseStatus), entry(::SniperAmmoStatus), entry(::SpiderWebChargeStatus),
        entry(::MathAnswerStackStatus), entry(::ForesightStatus), entry(::AccelerationStatus),
        entry(::AccelerationBulletStatus), entry(::DisposalStatus), entry(::RevolverBulletStatus),
        entry(::FreikugelBulletStatus), entry(::CaduceusStatus), entry(::DirectiveStatus),
        entry(::AgentDamageDealtStatus), entry(::AgentDamageTakenStatus), entry(::DomainDurationStatus),
        entry(::MetronomeStatus), entry(::WritingStatus),
        Entry("CheckpointStatus") { CheckpointStatus(it.entity.location.clone(), (it.entity as LivingEntity).health) },
    ).sortedBy { it.id }
    fun find(id: String) = entries.firstOrNull { it.id == id }
}
