package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.StealthVisibilityManager
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.entity.LivingEntity
import org.bukkit.potion.PotionEffectType

class Stealth : StatusAbnormality() {
    override val name: String
        get() = Keyword.Stealth.string
    override val description: List<String>
        get() = listOf(
            Keyword.Stealth.requireDescription(),
            "",
            "<gray>수치 없음",
            "<gray>지속시간 연장 적용",
            "<gray>지속시간 종료 시 소멸"
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = 1
    override var duration: Int? = null

    override val showMaxPower = false
    override val showPower = false

    override fun onDurationChanged() {
        super.onDurationChanged()
        if (power > 0 && duration?.let { it > 0 } != false) {
            (entityData as? PlayerData)?.let(StealthVisibilityManager::hideFromEnemies)
        }
    }

    override fun onPowerChanged() {
        super.onPowerChanged()
        if (power > 0 && duration?.let { it > 0 } != false) {
            (entityData as? PlayerData)?.let(StealthVisibilityManager::hideFromEnemies)
        }
    }

    override fun onRemoveStatusAbnormality() {
        val hasOtherStealth = entityData.statusAbnormalitys.any {
            it !== this && it is Stealth && it.power > 0
        }
        if (!hasOtherStealth) {
            (entityData as? PlayerData)?.let(StealthVisibilityManager::reveal)
        }
        val currentEntity = entity
        if (!hasOtherStealth && currentEntity is LivingEntity) {
            currentEntity.removePotionEffect(PotionEffectType.INVISIBILITY)
        }
        super.onRemoveStatusAbnormality()
    }
}
