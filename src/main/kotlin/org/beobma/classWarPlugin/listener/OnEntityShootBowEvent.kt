package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.gameClass.list.Uranus
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityShootBowEvent

class OnEntityShootBowEvent : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onShoot(event: EntityShootBowEvent) {
        Uranus.handleBowShot(event)
        val shooter = event.entity as? org.bukkit.entity.Player
        val data = shooter?.let { org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer(it) }
            ?.playerDatas?.filterIsInstance<org.beobma.classWarPlugin.entity.player.PlayerData>()?.firstOrNull { it.player == shooter }
        if (data != null) {
            val start = event.projectile.location
            val velocity = event.projectile.velocity
            if (velocity.lengthSquared() > 0.001) {
                val end = start.world.rayTraceBlocks(start, velocity.clone().normalize(), 32.0)?.hitPosition?.toLocation(start.world)
                    ?: start.clone().add(velocity.clone().normalize().multiply(32.0))
                org.beobma.classWarPlugin.ability.AbilityForecast.line(data, start, end, independent = true)
            }
        }
        val bow = event.bow ?: return
        val classId = org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId(bow) ?: return
        event.projectile.persistentDataContainer.set(weaponKey, org.bukkit.persistence.PersistentDataType.STRING, classId)
    }

    companion object {
        val weaponKey get() = org.bukkit.NamespacedKey(org.beobma.classWarPlugin.ClassWarPlugin.instance, "projectile-weapon")
    }
}
