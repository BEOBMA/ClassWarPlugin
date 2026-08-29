package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.GameManager.canDispatchClassHandlers
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.AttackableObjectManager
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.SkillManager.getSkillId
import org.beobma.classWarPlugin.manager.SkillManager.use
import org.beobma.classWarPlugin.gameClass.list.Referee
import org.beobma.classWarPlugin.gameClass.list.HideAndSeek
import org.beobma.classWarPlugin.gameClass.list.Brave
import org.beobma.classWarPlugin.gameClass.handler.WeaponInputHandler
import org.beobma.classWarPlugin.gameClass.handler.SkillInputHandler
import org.beobma.classWarPlugin.status.list.Disarm
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Event
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot

class OnPlayerInteractEvent : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (Brave.handlePullInteract(event.player, event.rightClicked)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (HideAndSeek.handleInteract(event)) return
        if (Brave.handlePullInteract(event)) return
        if (event.action == Action.RIGHT_CLICK_BLOCK && Referee.hasActiveTrial(event.player.uniqueId)) {
            event.isCancelled = true
            return
        }
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
        val currentGame = findGameForPlayer(player) ?: return
        val playerData = currentGame.playerDatas.filterIsInstance<PlayerData>()
            .find { it.player.uniqueId == player.uniqueId } ?: return
        if (!playerData.canDispatchClassHandlers()) return
        if (isLeftClick && playerData.entityStatus.canAttack && !playerData.hasStatus<Disarm>() &&
            AttackableObjectManager.hitBasicAttack(player)
        ) {
            event.isCancelled = true
            return
        }
        if (clickedItem.type.isAir) return
        val taggedClassId = getWeaponClassId(clickedItem)
        val skillId = getSkillId(clickedItem, player.uniqueId)
        if (skillId == null) {
            val hasValidWeaponTag = taggedClassId != null &&
                playerData.gameClasses.any { it.javaClass.name == taggedClassId }
            playerData.gameClasses
                .filter { gameClass ->
                    if (hasValidWeaponTag) gameClass.javaClass.name == taggedClassId
                    else clickedItem.type == gameClass.weapon.material
                }
                .filterIsInstance<WeaponInputHandler>()
                .forEach { handler ->
                    if (isRightClick) handler.onWeaponRightClick(event) else handler.onWeaponLeftClick(event)
                    if (event.useInteractedBlock() == Event.Result.DENY) return
                }
            return
        }
        val ownerClass = playerData.gameClasses.find { gameClass -> gameClass.skills.any { it.id == skillId } } ?: return
        val skill = ownerClass.skills.find { it.id == skillId } ?: return
        val inputHandler = ownerClass as? SkillInputHandler
        if (inputHandler != null) {
            if (!inputHandler.prepareSkillInput(event, skill)) return
        } else if (!isRightClick) {
            return
        }

        event.isCancelled = true
        playerData.use(skill, clickedItem)
    }
}
