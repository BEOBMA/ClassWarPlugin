package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.ability.AbilityTree
import org.beobma.classWarPlugin.ability.AbilityExecution

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
import org.beobma.classWarPlugin.game.CooperativeAction

class OnPlayerInteractEvent : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        if ("cw-afterglow-echo" in event.rightClicked.scoreboardTags) { event.isCancelled = true; return }
        if (org.beobma.classWarPlugin.domain.DomainManager.isLocked(event.player.uniqueId)) return
        val held = event.player.inventory.getItem(event.hand)
        if (org.beobma.classWarPlugin.growth.GrowthControls.useToken(event.player, held)) {
            event.isCancelled = true; return
        }
        if (event.hand != EquipmentSlot.HAND) return
        val data = findGameForPlayer(event.player)?.playerDatas?.filterIsInstance<PlayerData>()
            ?.firstOrNull { it.player == event.player } ?: return
        if (!data.canDispatchClassHandlers()) return
        if (!data.initGame.canPerform(data.uniqueId, CooperativeAction.BASIC_ATTACK)) {
            event.isCancelled = true
            return
        }
        if (Brave.handlePullInteract(event.player, event.rightClicked)) { event.isCancelled = true; return }
        val item = event.player.inventory.itemInMainHand
        if (item.type.isAir || getSkillId(item, event.player.uniqueId) != null) return
        val tag = getWeaponClassId(item)
        val active = AbilityTree.nodes(data.gameClasses, activeOnly = true)
        val tagged = active.any { it.classId == tag || it.javaClass.name == tag }
        val owners = active.filter {
            if (tagged) it.classId == tag || it.javaClass.name == tag else it.weapon.material == item.type
        }
        AbilityTree.handlers(owners, WeaponInputHandler::class.java, includeDescendants = false)
            .forEach { bound -> bound.call { it.onWeaponInteractEntity(event) } }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // Tagged domain crystals are skill buttons, never placeable vanilla end crystals.
        event.item?.takeIf { it.type == org.bukkit.Material.END_CRYSTAL && getSkillId(it,event.player.uniqueId) != null }
            ?.let { event.setUseItemInHand(Event.Result.DENY); event.setUseInteractedBlock(Event.Result.DENY) }
        if (org.beobma.classWarPlugin.domain.DomainManager.isLocked(event.player.uniqueId)) return
        val held = event.hand?.let { event.player.inventory.getItem(it) }
        if (org.beobma.classWarPlugin.growth.GrowthControls.useToken(event.player, held,
                event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK)) {
            event.isCancelled = true; return
        }
        if (event.action == Action.RIGHT_CLICK_BLOCK && Referee.hasActiveTrial(event.player.uniqueId)) {
            event.isCancelled = true
            return
        }
        if (event.hand != EquipmentSlot.HAND) return
        val isRightClick = event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK
        val isLeftClick = event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK
        if (!isRightClick && !isLeftClick) return

        val player = event.player
        val isTraining = PlayerTagManager.isTraining(player)
        if (!isGaming() && !isTraining) return

        // RIGHT_CLICK_AIR에서는 서버/아이템 종류에 따라 event.item이 비어 있을 수 있으므로
        // 실제 주 손 아이템을 기준으로 스킬 사용을 시도한다.
        val clickedItem = event.item ?: player.inventory.itemInMainHand
        val currentGame = findGameForPlayer(player) ?: return
        val playerData = currentGame.playerDatas.filterIsInstance<PlayerData>()
            .find { it.player.uniqueId == player.uniqueId } ?: return
        if (!playerData.canDispatchClassHandlers()) return
        val canBasicAttack = currentGame.canPerform(playerData.uniqueId, CooperativeAction.BASIC_ATTACK)
        if (canBasicAttack && Brave.handlePullInteract(event)) return
        if (isLeftClick && playerData.entityStatus.canAttack && !playerData.hasStatus<Disarm>() &&
            canBasicAttack && AttackableObjectManager.hitBasicAttack(player)
        ) {
            event.isCancelled = true
            return
        }
        if (clickedItem.type.isAir) return
        val taggedClassId = getWeaponClassId(clickedItem)
        val skillId = getSkillId(clickedItem, player.uniqueId)
        if (skillId == null) {
            if (!canBasicAttack) {
                event.isCancelled = true
                return
            }
            val hasValidWeaponTag = taggedClassId != null &&
                AbilityTree.nodes(playerData.gameClasses, activeOnly = true).any { (it.classId == taggedClassId || it.javaClass.name == taggedClassId) }
            AbilityTree.nodes(playerData.gameClasses, activeOnly = true)
                .filter { gameClass ->
                    if (hasValidWeaponTag) (gameClass.classId == taggedClassId || gameClass.javaClass.name == taggedClassId)
                    else clickedItem.type == gameClass.weapon.material
                }
                .let { AbilityTree.handlers(it, WeaponInputHandler::class.java, includeDescendants = false) }
                .forEach { bound ->
                    bound.call { handler -> if (isRightClick) handler.onWeaponRightClick(event) else handler.onWeaponLeftClick(event) }
                    if (event.useInteractedBlock() == Event.Result.DENY) return
                }
            return
        }
        val ownerClass = playerData.gameClasses.find { gameClass -> gameClass.skills.any { it.matchesId(skillId) } } ?: return
        val skill = ownerClass.skills.find { it.matchesId(skillId) } ?: return
        val inputHandler = skill.ownerClass as? SkillInputHandler
        if (inputHandler != null) {
            if (!AbilityExecution.with(skill.abilityScope) { inputHandler.prepareSkillInput(event, skill) }) return
        } else if (!isRightClick) {
            return
        }

        event.isCancelled = true
        playerData.use(skill, clickedItem)
    }
}
