package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.SkillManager.getSkillId
import org.beobma.classWarPlugin.manager.SkillManager.use
import org.beobma.classWarPlugin.gameClass.handler.WeaponInputHandler
import org.beobma.classWarPlugin.gameClass.handler.SkillInputHandler
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class OnPlayerInteractEvent : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val isRightClick = event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK
        val isLeftClick = event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK
        if (!isRightClick && !isLeftClick) return

        val player = event.player
        val isTraining = PlayerTagManager.hasTag(player, "isTraining")
        if (!isGaming() && !isTraining) return

        // RIGHT_CLICK_AIR에서는 서버/아이템 종류에 따라 event.item이 비어 있을 수 있으므로
        // 실제 주 손 아이템을 기준으로 스킬 사용을 시도한다.
        val clickedItem = event.item ?: player.inventory.itemInMainHand
        if (clickedItem.type.isAir) return
        val currentGame = findGameForPlayer(player) ?: return
        val playerData = currentGame.playerDatas.filterIsInstance<PlayerData>()
            .find { it.player.uniqueId == player.uniqueId } ?: return
        val skillId = getSkillId(clickedItem, player.uniqueId)
        if (skillId == null) {
            val gameClass = playerData.gameClass
            if (isRightClick && gameClass is WeaponInputHandler && clickedItem.type == gameClass.weapon.material) {
                gameClass.onWeaponRightClick(event)
            }
            return
        }
        val skill = playerData.gameClass?.skills?.find { it.id == skillId } ?: return
        val inputHandler = playerData.gameClass as? SkillInputHandler
        if (inputHandler != null) {
            if (!inputHandler.prepareSkillInput(event, skill)) return
        } else if (!isRightClick) {
            return
        }

        event.isCancelled = true
        playerData.use(skill, clickedItem)
    }
}
