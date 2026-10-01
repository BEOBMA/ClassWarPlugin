package org.beobma.classWarPlugin.testing

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.GameManager
import org.beobma.classWarPlugin.manager.GameManager.startTraining
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.updateStatusActionBar
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.*
import org.beobma.classWarPlugin.status.list.Vibration
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.inventory.*
import org.bukkit.event.player.*
import org.bukkit.inventory.*
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/** Never registered as a playable class. The training snapshot owns the tester's inventory. */
internal class StatusLabClass : GameClass(), GameStatusHandler,
    org.beobma.classWarPlugin.gameClass.handler.ResonanceResourceUser {
    override val classId = "status-laboratory"
    override val name = "상태이상 실험실"
    override val rank = Rank.C
    override val classItemMaterial = Material.BOOK
    override val skills = emptyList<Skill>()
    override var passives = emptyList<Passive>()
    override fun onBattleStart() { StatusLaboratory.attach(abilityScope) }
    override fun onGameTimePasses() { StatusLaboratory.tick(abilityScope) }
}

object StatusLaboratory : Listener {
    private data class Session(val scope: AbilityScope, var power: Int = 10, var seconds: Int = 10,
        var menu: Inventory? = null, var page: Int = 0, var nextUse: Long = 0,
        val touched: MutableSet<EntityData> = mutableSetOf(), val created: MutableSet<StatusAbnormality> = mutableSetOf(),
        val fires: MutableMap<LivingEntity, Int> = mutableMapOf(),
        val expirations: MutableMap<StatusAbnormality,Long> = mutableMapOf())
    private val sessions = mutableMapOf<UUID, Session>()
    private val key get() = NamespacedKey(ClassWarPlugin.instance, "status-lab-tool")
    private val tokenKey get() = NamespacedKey(ClassWarPlugin.instance, "status-lab-session")
    private val mm = MiniMessage.miniMessage()
    private fun message(player: Player, text: String) = player.sendMessage(Component.text(text))
    internal fun tick(scope: AbilityScope) {
        val s = sessions[scope.playerData.uniqueId] ?: return
        val active = s.touched.flatMap { it.statusAbnormalitys }.toSet()
        s.expirations.entries.toList().forEach { (status, end) ->
            if (status !in active || scope.game.combatTick >= end) {
                status.cleanupFromManager(); s.expirations.remove(status); s.created.remove(status)
            }
        }
        s.touched.filterIsInstance<PlayerData>().forEach { it.updateStatusActionBar() }
    }

    fun command(player: Player, args: List<String>) {
        if (org.beobma.classWarPlugin.info.Info.game != null) { message(player,"게임 중에는 실험실에 들어갈 수 없다."); return }
        var session = sessions[player.uniqueId]
        if (session == null) {
            if (PlayerTagManager.isTraining(player)) { message(player,"기존 연습을 /cw exit로 종료한 뒤 사용한다."); return }
            player.startTraining(StatusLabClass())
            session = sessions[player.uniqueId] ?: return
        }
        if (args.firstOrNull() == "clear") { clear(session); message(player,"이 실험실에서 적용한 상태를 해제했다."); return }
        if (args.isNotEmpty()) {
            val power = args[0].toIntOrNull()
            val seconds = args.getOrNull(1)?.toIntOrNull() ?: if (args.size == 1) 10 else null
            if (power == null || seconds == null || power !in 1..1000 || seconds !in 1..300) {
                message(player,"사용법: /cw test status [수치 1~1000] [초 1~300] 또는 clear"); return
            }
            session.power = power; session.seconds = seconds
        }
        open(session, 0)
    }
    internal fun attach(scope: AbilityScope) {
        val session = Session(scope)
        sessions[scope.playerData.uniqueId] = session
        val player = scope.playerData.player
        player.inventory.addItem(tool(session,"menu",Material.BOOK,"<gold>상태이상 목록"), tool(session,"clear",Material.MILK_BUCKET,"<green>실험 상태 해제"))
        scope.resources.own {
            session.menu?.let { if (player.openInventory.topInventory === it) player.closeInventory() }
            clear(session)
            sessions.remove(player.uniqueId, session)
        }
        message(player,"[실험실] 책 우클릭: 아이템 목록 / 상태 아이템 우클릭: 자신 / 타격: 대상 / 우유: 해제 / 종료: /cw exit")
    }
    private fun tool(s: Session, id: String, material: Material, title: String): ItemStack = ItemStack(material).apply {
        itemMeta = itemMeta.apply {
            displayName(mm.deserialize(title))
            lore(listOf(Component.text("우클릭: 자신 · 타격: 대상"),Component.text("실험실 전용 · /cw test status [수치] [초]")))
            persistentDataContainer.set(key, PersistentDataType.STRING, id)
            persistentDataContainer.set(tokenKey,PersistentDataType.STRING,s.scope.instanceId.toString())
        }
    }
    private fun open(s: Session, requested: Int) {
        val player = s.scope.playerData.player
        val last = (StatusTestCatalog.entries.size-1)/45
        s.page = requested.coerceIn(0,last)
        val inventory = Bukkit.createInventory(null,54,Component.text("상태 실험 ${s.page+1}/${last+1} · ${s.power} / ${s.seconds}초"))
        StatusTestCatalog.entries.drop(s.page*45).take(45).forEachIndexed { slot, entry ->
            val status = entry.factory(s.scope.playerData)
            inventory.setItem(slot, tool(s,entry.id,if(status.isClassMechanic) Material.PAPER else Material.BLAZE_ROD,status.name).apply {
                itemMeta = itemMeta.apply { lore(status.description.map(mm::deserialize) + listOf(
                    Component.text("클릭하여 적용 아이템을 받는다. (${entry.id})"),
                    Component.text(if(status.isClassMechanic || status is Distortion) "클래스/영역 전용: 자원·표시 테스트, 원래 스킬은 자동 발동하지 않는다." else "실제 상태이상 효과를 적용한다."))) }
            })
        }
        inventory.setItem(45,tool(s,"previous",Material.ARROW,"이전 페이지"))
        inventory.setItem(49,tool(s,"clear",Material.MILK_BUCKET,"실험 상태 모두 해제"))
        inventory.setItem(53,tool(s,"next",Material.ARROW,"다음 페이지"))
        s.menu = inventory; player.openInventory(inventory)
    }
    private fun valid(player: Player, item: ItemStack?): Session? {
        val session = sessions[player.uniqueId] ?: return null
        if (!player.isOp || !ClassWarPlugin.instance.config.getBoolean("internal.testing.commands-enabled",false) ||
            session.scope.isClosed || !session.scope.isActive) return null
        return session.takeIf { item?.itemMeta?.persistentDataContainer?.get(tokenKey,PersistentDataType.STRING) == it.scope.instanceId.toString() }
    }
    private fun id(item: ItemStack?) = item?.itemMeta?.persistentDataContainer?.get(key,PersistentDataType.STRING)

    @EventHandler(priority = EventPriority.LOWEST)
    fun interact(e: PlayerInteractEvent) {
        if (e.hand != EquipmentSlot.HAND || e.action !in listOf(Action.RIGHT_CLICK_AIR,Action.RIGHT_CLICK_BLOCK)) return
        if (id(e.item) == null) return
        e.isCancelled = true
        val s = valid(e.player,e.item) ?: return
        when(val id = id(e.item)) {
            "menu" -> open(s,0)
            "clear" -> clear(s)
            else -> apply(s,id ?: return,s.scope.playerData)
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    fun hit(e: EntityDamageByEntityEvent) {
        val player = e.damager as? Player ?: return
        val item = player.inventory.itemInMainHand
        val id = id(item) ?: return
        e.isCancelled = true // The applicator is not an attack and must work through stun/invincibility.
        val s = valid(player,item) ?: return
        if (e.entity is Player && e.entity.uniqueId !in sessions) { message(player,"대상 플레이어도 /cw test status에 참가해야 한다."); return }
        if (e.entity !is LivingEntity) return
        Targeting.synchronizeTraining(s.scope.playerData)
        val target = (e.entity as? Player)?.let { sessions[it.uniqueId]?.scope?.playerData }
            ?: s.scope.game.playerDatas.firstOrNull { it.entity.uniqueId == e.entity.uniqueId } ?: return
        if (id == "clear") clearTarget(target) else apply(s,id,target)
    }
    private fun apply(s: Session, id: String, target: EntityData) {
        val entry = StatusTestCatalog.find(id) ?: return
        if (s.scope.game.combatTick < s.nextUse) return
        s.nextUse = s.scope.game.combatTick+4
        val before = target.statusAbnormalitys.toSet()
        val living = target.entity as? LivingEntity ?: return
        s.fires.putIfAbsent(living,living.fireTicks)
        s.touched += target
        AbilityExecution.with(s.scope) {
            val status = entry.factory(target)
            if (status is ResonanceResource) {
                // Keep the same target resource so repeated hits can cross the 30-point threshold.
                if (status is Resonance && s.scope.playerData.player.isSneaking) {
                    target.statusAbnormalitys.filterIsInstance<Resonance>().firstOrNull()?.consume()
                } else {
                    val resource = if (status is Aftermath)
                        target.getOrCreateStatus(s.scope.playerData) { Aftermath() }
                    else target.getOrCreateStatus(s.scope.playerData) { Resonance() }
                    resource.increasePower(s.power)
                }
                s.created += target.statusAbnormalitys.filter { it !in before }
                // These two statuses manage their shared 10-second refresh themselves.
                return@with
            }
            // Refresh the lab's own copy, without accumulating duplicate speed/visibility leases.
            target.statusAbnormalitys.filter { it.javaClass == status.javaClass && it in s.created }.toList().forEach { it.cleanupFromManager() }
            target.addStatus(status,s.scope.playerData)
            status.duration = s.seconds
            if (status is VibrationExplosion) {
                val vibration = target.getOrCreateStatus(s.scope.playerData) { Vibration() }
                if (vibration.power <= 0) { vibration.updatePower(s.power); vibration.updateDuration(s.seconds) }
            }
            status.updatePower(s.power.coerceAtMost(status.maxPower ?: s.power))
            if (status in target.statusAbnormalitys) status.updateDuration(s.seconds)
            when(status) {
                is GamblerCardStatus -> status.updateCards(listOf(3,5,7))
                is DomainDurationStatus -> status.synchronize(false,false,s.seconds*20)
                is AgentMechanicStatus -> status.synchronize("<yellow>실험 ${s.power}</yellow>")
            }
            s.created += target.statusAbnormalitys.filter { it !in before }
            // A single session clock expires all entries, including non-removable resources.
            target.statusAbnormalitys.filter { it !in before }.forEach { s.expirations[it] = s.scope.game.combatTick+s.seconds*20L }
        }
        message(s.scope.playerData.player,"[실험] ${entry.id} → ${living.name} (${s.seconds}초, 수치는 상태 자체 상한 적용)")
    }
    private fun clearTarget(target: EntityData) {
        sessions.values.forEach { s ->
            target.statusAbnormalitys.filter { it in s.created }.toList().forEach { it.cleanupFromManager(); s.created.remove(it) }
            (target.entity as? LivingEntity)?.let { entity -> s.fires.remove(entity)?.let { entity.fireTicks = it } }
        }
        (target as? PlayerData)?.updateStatusActionBar()
    }
    private fun clear(s: Session) {
        s.created.toList().forEach { it.cleanupFromManager() }; s.created.clear(); s.expirations.clear()
        s.fires.forEach { (entity, fire) -> if(entity.isValid) entity.fireTicks = fire }; s.fires.clear()
        s.touched.filterIsInstance<PlayerData>().forEach { it.updateStatusActionBar() }; s.touched.clear()
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun click(e: InventoryClickEvent) {
        val player = e.whoClicked as? Player ?: return
        val s = sessions[player.uniqueId] ?: return
        if (s.menu !== e.view.topInventory) {
            if ((id(e.currentItem)!=null || id(e.cursor)!=null || (e.hotbarButton in 0..8 && id(player.inventory.getItem(e.hotbarButton))!=null)) && e.view.topInventory.type != InventoryType.CRAFTING) e.isCancelled = true
            return
        }
        e.isCancelled = true
        if (!player.isOp || !s.scope.isActive || e.rawSlot !in 0..53 || e.click != ClickType.LEFT) return
        when(id(e.currentItem)) {
            "previous" -> open(s,s.page-1)
            "next" -> open(s,s.page+1)
            "clear" -> clear(s)
            else -> {
                val entry = StatusTestCatalog.find(id(e.currentItem) ?: return) ?: return
                if (player.inventory.firstEmpty() !in 0..35) { message(player,"인벤토리에 빈 칸이 필요하다."); return }
                player.inventory.addItem(tool(s,entry.id,Material.BLAZE_ROD,entry.factory(s.scope.playerData).name))
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun drag(e: InventoryDragEvent) { if(sessions[e.whoClicked.uniqueId]?.menu === e.view.topInventory || id(e.oldCursor)!=null) e.isCancelled=true }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun drop(e: PlayerDropItemEvent) { if(id(e.itemDrop.itemStack)!=null) e.isCancelled=true }
}
