package org.beobma.classWarPlugin.listener

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.manager.GameManager.handleReconnect
import org.beobma.classWarPlugin.manager.GameManager.handleTemporaryDisconnect
import org.beobma.classWarPlugin.manager.GameManager.refreshPlayerListVisibility
import org.beobma.classWarPlugin.manager.GameManager.stopTraining
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.StealthVisibilityManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRegisterChannelEvent
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class OnPlayerConnectionEvent(
    private val plugin: ClassWarPlugin,
) : Listener {
    private val validationTasks = ConcurrentHashMap<UUID, BukkitTask>()
    private val acceptedPlayers = ConcurrentHashMap.newKeySet<UUID>()

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        validationTasks.remove(event.player.uniqueId)?.cancel()
        acceptedPlayers.remove(event.player.uniqueId)
        StealthVisibilityManager.revealTo(event.player)
        if (PlayerTagManager.hasTag(event.player, "isTraining")) {
            event.player.stopTraining()
        }
        handleTemporaryDisconnect(event.player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val brand = player.clientBrandName
        if (brand != null) {
            validateClient(player, brand)
            return
        }

        validationTasks.remove(player.uniqueId)?.cancel()
        validationTasks[player.uniqueId] = plugin.server.scheduler.runTaskLater(plugin, Runnable {
            validationTasks.remove(player.uniqueId)
            if (!player.isOnline) return@Runnable
            validateClient(player, player.clientBrandName)
        }, CLIENT_BRAND_WAIT_TICKS)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerRegisterChannel(event: PlayerRegisterChannelEvent) {
        if (!event.channel.equals(ClassWarPlugin.LUNAR_APOLLO_CHANNEL, ignoreCase = true)) return
        blockClient(event.player, "Lunar Client")
    }

    private fun validateClient(player: Player, brand: String?) {
        if (!brand.equals(VANILLA_CLIENT_BRAND, ignoreCase = true)) {
            blockClient(player, brand)
            return
        }
        if (!acceptedPlayers.add(player.uniqueId)) return

        handleReconnect(player)
        refreshPlayerListVisibility()
        StealthVisibilityManager.refreshAll()
    }

    private fun blockClient(player: Player, brand: String?) {
        validationTasks.remove(player.uniqueId)?.cancel()
        acceptedPlayers.remove(player.uniqueId)
        if (!player.isOnline) return

        val safeBrand = brand
            ?.filterNot(Char::isISOControl)
            ?.take(64)
            ?.ifBlank { UNKNOWN_CLIENT_BRAND }
            ?: UNKNOWN_CLIENT_BRAND
        plugin.logger.warning("기본 클라이언트가 아닌 접속을 차단했습니다: ${player.name} ($safeBrand)")
        player.kick(
            Component.text("마인크래프트 기본 클라이언트로만 접속할 수 있습니다.", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("감지된 클라이언트: $safeBrand", NamedTextColor.GRAY))
        )
    }

    private companion object {
        const val VANILLA_CLIENT_BRAND = "vanilla"
        const val UNKNOWN_CLIENT_BRAND = "알 수 없음"
        const val CLIENT_BRAND_WAIT_TICKS = 40L
    }
}
