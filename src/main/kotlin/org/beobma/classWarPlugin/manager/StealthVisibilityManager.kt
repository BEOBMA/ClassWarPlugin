package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/** 은신 중인 참가자를 적의 클라이언트에서 완전히 숨기고 복구한다. */
object StealthVisibilityManager {
    private val stealthedPlayers: MutableMap<UUID, PlayerData> = mutableMapOf()

    fun hideFromEnemies(targetData: PlayerData) {
        stealthedPlayers[targetData.uniqueId] = targetData
        refreshTarget(targetData)
    }

    fun reveal(targetData: PlayerData) {
        stealthedPlayers.remove(targetData.uniqueId)
        val target = targetData.player
        Bukkit.getOnlinePlayers().forEach { viewer ->
            viewer.showPlayer(ClassWarPlugin.instance, target)
        }
    }

    /** 사망하여 관전자가 된 플레이어가 모든 은신 참가자를 볼 수 있게 한다. */
    fun revealTo(viewer: Player) {
        stealthedPlayers.values.forEach { targetData ->
            viewer.showPlayer(ClassWarPlugin.instance, targetData.player)
        }
    }

    /** 접속 또는 엔티티 재바인딩 후 현재 게임 관계에 맞게 가시성을 다시 전송한다. */
    fun refreshAll() {
        stealthedPlayers.values.toList().forEach(::refreshTarget)
    }

    fun showAll() {
        stealthedPlayers.values.toList().forEach(::reveal)
        stealthedPlayers.clear()
    }

    private fun refreshTarget(targetData: PlayerData) {
        if (targetData.entityStatus.isDead) {
            reveal(targetData)
            return
        }

        val target = targetData.player
        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (shouldHide(viewer, targetData)) {
                viewer.hidePlayer(ClassWarPlugin.instance, target)
            } else {
                viewer.showPlayer(ClassWarPlugin.instance, target)
            }
        }
    }

    private fun shouldHide(viewer: Player, targetData: PlayerData): Boolean {
        if (viewer.uniqueId == targetData.uniqueId) return false
        val viewerData = targetData.game.playerDatas
            .filterIsInstance<PlayerData>()
            .find { it.uniqueId == viewer.uniqueId }
            ?: return false
        if (viewerData.entityStatus.isDead) return false
        return viewerData.isEnemyOf(targetData)
    }
}
