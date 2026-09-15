package org.beobma.classWarPlugin.growth

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.game.GameSettings
import org.beobma.classWarPlugin.game.MatchMode
import org.beobma.classWarPlugin.game.MatchModifier
import org.beobma.classWarPlugin.game.PrimaryMode
import org.beobma.classWarPlugin.info.Info
import org.beobma.classWarPlugin.manager.GameManager
import org.beobma.classWarPlugin.manager.PlayerManager.refreshClassItemDescriptions
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.UUID

/** A real holder prevents forged item names or stale menus from mutating a new match. */
class GrowthMenu(val owner: UUID, val kind: String, val runtime: GrowthModeRuntime?, val page: Int = 0) : InventoryHolder {
    lateinit var contents: Inventory
    val equipmentIds = mutableMapOf<Int, String>()
    override fun getInventory(): Inventory = contents
    companion object {
        private val mini = MiniMessage.miniMessage()
        val settings = listOf(
            Triple("regions.maximum", "최대 지역 수", 16), Triple("regions.minimum", "최소 지역 수", 4),
            Triple("period-seconds", "낮/밤 길이(초)", 120), Triple("warnings-per-period", "주기당 경고 지역", 1),
            Triple("final-shrink-seconds", "최종 축소(초)", 120), Triple("level.maximum", "최대 레벨", 30),
            Triple("level.points", "레벨당 스탯 포인트", 3), Triple("mobs.per-region", "지역당 몬스터·동물", GrowthSettings.DEFAULT_MOBS_PER_REGION),
            Triple("mobs.maximum", "최대 몬스터·동물", GrowthSettings.DEFAULT_MAXIMUM_MOBS), Triple("regions.attempts", "지역 수별 재시도", 12),
            Triple("level.experience-base", "기본 필요 경험치", 60), Triple("level.experience-step", "레벨별 필요 경험치 증가", 25),
            Triple("mobs.experience", "몬스터 기본 경험치", 30), Triple("level.player-experience", "플레이어 처치 경험치", 100),
        )
        private fun range(key: String): IntRange = when (key) {
            "regions.maximum", "regions.minimum" -> 2..32
            "regions.attempts" -> 1..40
            "period-seconds", "final-shrink-seconds" -> 10..3600
            "warnings-per-period" -> 1..8
            "level.maximum" -> 2..100
            "level.points" -> 1..10
            "mobs.per-region" -> 0..32
            "mobs.maximum" -> 0..512
            "level.experience-base", "mobs.experience" -> 1..10000
            else -> 0..10000
        }
        private val decimalSettings = listOf(
            Triple("forbidden-damage", "초당 금지구역 피해", 2.0),
            Triple("level.health", "레벨당 기본 최대 체력", 2.0),
            Triple("items.drop-chance", "장비 드롭 확률(0~1)", GrowthSettings.DEFAULT_DROP_CHANCE),
        )
        fun item(material: Material, name: String, lines: List<String>): ItemStack = ItemStack(material).apply {
            itemMeta = itemMeta.apply { displayName(mini.deserialize(name)); lore(lines.map(mini::deserialize)) }
        }
        fun open(player: Player, kind: String, page: Int = 0) {
            val runtime = GameManager.findGameForPlayer(player)?.growth
            if (kind == "config" && !player.isOp) return
            val state = runtime?.players?.get(player.uniqueId)
            if (kind != "config" && state == null) { player.sendMessage("성장 모드 참가자만 사용할 수 있습니다."); return }
            val itemPage = if (kind == "items") page.coerceIn(0, GrowthItems.pageCount(state!!.inventory) - 1) else 0
            val menu = GrowthMenu(player.uniqueId, kind, runtime, itemPage)
            val inv = Bukkit.createInventory(menu, 54, mini.deserialize("<dark_green>성장 · $kind"))
            menu.contents = inv
            when (kind) {
                "stats" -> {
                    GrowthStat.entries.forEachIndexed { index, stat ->
                        inv.setItem(10 + index * 2, item(listOf(Material.IRON_SWORD, Material.FEATHER, Material.BOOK, Material.GOLD_NUGGET)[index],
                            "<gold>${stat.label}: ${state!!.stat(stat)}", listOf("<gray>배분 ${state.base(stat)} + 장비 ${state.stat(stat) - state.base(stat)}",
                                "<yellow>클릭: 1점 / Shift: 남은 점수 전부")))
                    }
                    inv.setItem(4, item(Material.EXPERIENCE_BOTTLE, "<green>Lv.${state!!.level} · 미배분 ${state.points}점",
                        listOf("<gray>경험치 ${state.experience}/${state.requiredExperience(runtime.settings)}", "<gray>레벨당 최대 체력 +${runtime.settings.healthPerLevel}")))
                    val data = runtime.participants().first { it.uniqueId == player.uniqueId }
                    org.beobma.classWarPlugin.ability.AbilityTree.nodes(data.gameClasses, true).take(18).forEachIndexed { index, ability ->
                        inv.setItem(27 + index, item(ability.classItemMaterial, ability.name,
                            listOf("<gray>클릭하여 스킬·패시브의 현재 수치 확인")))
                    }
                    inv.setItem(49, item(Material.CHEST, "<gold>장비 보관함", listOf("<gray>클릭하여 장착")))
                }
                "items" -> {
                    GrowthItems.page(state!!.inventory, itemPage).forEachIndexed { index, def ->
                        menu.equipmentIds[index] = def.id
                        val equipped = state.equipment[def.slot] == def.id
                        inv.setItem(index, item(def.material,
                            "${if (equipped) "<green>[장착]" else "<gold>[보유]"} ${def.name}",
                            listOf("<yellow>${def.slot.label}") + def.stats.map { "<gray>${it.key.label} +${it.value}" } +
                                listOf("<aqua>${def.description}", "<gray>클릭: 장착/해제 · 같은 슬롯은 교체", if (def.eventOnly) "<gold>시간 한정 이벤트 보상" else "<gray>지역 몬스터에게서 획득")))
                    }
                    if (menu.equipmentIds.isEmpty()) inv.setItem(22, item(Material.GLASS_PANE,
                        "<gray>보유한 장비가 없습니다", listOf("<gray>지역 몬스터와 한정 이벤트에서 획득하세요.")))
                    inv.setItem(49, item(Material.NETHER_STAR, "<gold>스탯 확인", listOf("<gray>클릭하여 성장 정보 확인")))
                    val pages = GrowthItems.pageCount(state.inventory)
                    inv.setItem(48, item(Material.BOOK, "<yellow>${itemPage + 1} / $pages 페이지",
                        listOf("<gray>보유 장비 ${state.inventory.size}종", "<gray>같은 고유 효과는 중첩되지 않습니다.")))
                    if (itemPage > 0) inv.setItem(45, item(Material.ARROW, "<yellow>이전 페이지", emptyList()))
                    if (itemPage + 1 < pages) inv.setItem(53, item(Material.ARROW, "<yellow>다음 페이지", emptyList()))
                }
                "config" -> {
                    settings.forEachIndexed { index, (key, label, default) ->
                        inv.setItem(index, item(Material.COMPARATOR, "<yellow>$label: ${ClassWarPlugin.instance.config.getInt("growth.$key", default)}",
                            listOf("<gray>좌클릭 +1 / 우클릭 -1 / Shift ×10", "<gray>다음 경기부터 적용")))
                    }
                    decimalSettings.forEachIndexed { index, (key, label, default) ->
                        inv.setItem(18 + index, item(Material.COMPARATOR, "<yellow>$label: ${ClassWarPlugin.instance.config.getDouble("growth.$key", default)}",
                            listOf("<gray>좌클릭 증가 / 우클릭 감소 / Shift ×10", "<gray>피해·체력 0.1, 드롭률 0.01씩 조정")))
                    }
                    inv.setItem(22, item(Material.ENDER_EYE, "<yellow>한정 이벤트: ${ClassWarPlugin.instance.config.getBoolean("growth.events-enabled", true)}", listOf("<gray>클릭하여 전환")))
                    inv.setItem(23, item(Material.NAME_TAG, "<yellow>몬스터 체력 표시: ${ClassWarPlugin.instance.config.getBoolean("growth.mobs.show-health", true)}", listOf("<gray>클릭하여 전환 · 다음 경기부터 적용")))
                    inv.setItem(49, item(Material.BARRIER, "<red>맵 호환성 경고", listOf("<red>${GrowthSettings.WARNING}")))
                }
            }
            player.openInventory(inv)
        }
        fun click(player: Player, menu: GrowthMenu, slot: Int, shift: Boolean, right: Boolean) {
            if (menu.owner != player.uniqueId || slot !in 0..53) return
            if (menu.kind == "config") {
                if (!player.isOp) return
                val plugin = ClassWarPlugin.instance
                if (slot == 22 || slot == 23) {
                    val key = if (slot == 22) "growth.events-enabled" else "growth.mobs.show-health"
                    plugin.config.set(key, !plugin.config.getBoolean(key, true))
                    plugin.saveConfig(); GameSettings.load(plugin.config); open(player, "config"); return
                }
                if (slot in 18..20) {
                    val (key, _, default) = decimalSettings[slot - 18]
                    val step = (if (key == "items.drop-chance") 0.01 else 0.1) * (if (shift) 10 else 1) * if (right) -1 else 1
                    val next = plugin.config.getDouble("growth.$key", default) + step
                    val max = if (key == "items.drop-chance") 1.0 else if (key == "level.health") 20.0 else 100.0
                    val min = if (key == "forbidden-damage") 0.1 else 0.0
                    plugin.config.set("growth.$key", kotlin.math.round(next.coerceIn(min, max) * 100) / 100)
                    plugin.saveConfig(); GameSettings.load(plugin.config); open(player, "config"); return
                }
                val (key, _, default) = settings.getOrNull(slot) ?: return
                val next = plugin.config.getInt("growth.$key", default) + (if (right) -1 else 1) * (if (shift) 10 else 1)
                plugin.config.set("growth.$key", next.coerceIn(range(key)))
                val maxRegions = plugin.config.getInt("growth.regions.maximum", 16).coerceIn(2, 32)
                plugin.config.set("growth.regions.minimum", plugin.config.getInt("growth.regions.minimum", 4).coerceIn(2, maxRegions))
                plugin.saveConfig(); GameSettings.load(plugin.config)
                open(player, "config"); return
            }
            val runtime = menu.runtime ?: return
            if (Info.game?.growth !== runtime || runtime.game.isPaused) return
            val data = runtime.participants().firstOrNull { it.uniqueId == player.uniqueId && !it.entityStatus.isDead } ?: return
            val state = runtime.players[player.uniqueId] ?: return
            when (menu.kind) {
                "stats" -> {
                    if (slot == 49) { open(player, "items"); return }
                    if (slot in 27..44) {
                        org.beobma.classWarPlugin.ability.AbilityTree.nodes(data.gameClasses, true).getOrNull(slot - 27)?.let {
                            with(org.beobma.classWarPlugin.manager.InventoryManager) { player.openClassStatusInventory(it) }
                        }
                        return
                    }
                    val index = listOf(10, 12, 14, 16).indexOf(slot)
                    if (index >= 0) state.allocate(GrowthStat.entries[index], if (shift) state.points else 1)
                }
                "items" -> {
                    if (slot == 49) { open(player, "stats"); return }
                    if (slot == 45 && menu.page > 0) { open(player, "items", menu.page - 1); return }
                    if (slot == 53 && menu.page + 1 < GrowthItems.pageCount(state.inventory)) {
                        open(player, "items", menu.page + 1); return
                    }
                    menu.equipmentIds[slot]?.let { state.equip(it) }
                }
            }
            runtime.refresh(data); refreshClassItemDescriptions(data); open(player, menu.kind, menu.page)
        }
    }
}

object GrowthCommands {
    fun execute(sender: CommandSender, args: List<String>): Boolean {
        val action = args.firstOrNull()?.lowercase() ?: "stats"
        if (action in setOf("stats", "items", "config")) {
            val player = sender as? Player
            if (player == null) sender.sendMessage("플레이어 전용 명령입니다.") else GrowthMenu.open(player, action)
            return true
        }
        val runtime = Info.game?.growth
        if (action == "regions") {
            if (runtime?.layout == null) { sender.sendMessage("준비된 성장 지역이 없습니다."); return true }
            runtime.layout!!.regions.forEach { r -> sender.sendMessage("${r.id + 1}. ${r.name} [${r.state.label}] → ${r.neighbors.map { it + 1 }}") }
            runtime.eventMarkers().forEach { marker ->
                sender.sendMessage("${marker.name}: ${marker.location.blockX}, ${marker.location.blockZ}")
            }
            return true
        }
        if (!sender.isOp) { sender.sendMessage("관리자 전용 명령입니다."); return true }
        when (action) {
            "start" -> {
                val modifiers = mutableSetOf<MatchModifier>()
                for (token in args.drop(1)) {
                    val modifier = MatchModifier.entries.firstOrNull { it.name.equals(token.replace('-', '_'), true) }
                    if (modifier == null) { sender.sendMessage("알 수 없는 모드: $token"); return true }
                    modifiers.add(modifier)
                }
                sender.sendMessage(GameManager.startNewGame(MatchMode(modifiers, PrimaryMode.GROWTH)) ?: "성장 모드 클래스 선택을 시작했습니다.")
            }
            "nextphase" -> sender.sendMessage(if (runtime?.nextPhase() == true) "다음 시간대로 전환했습니다." else "진행 중인 지역 전환이 없습니다.")
            "spawnmobs" -> { runtime?.respawnMobs(); sender.sendMessage("성장 몬스터 재생성 요청을 처리했습니다.") }
            "xp" -> {
                val player = args.getOrNull(1)?.let(Bukkit::getPlayerExact)
                val amount = args.getOrNull(2)?.toIntOrNull()
                val data = runtime?.participants()?.firstOrNull { it.player == player }
                if (data == null || amount == null || amount !in 1..100000) sender.sendMessage("/cw growth xp <플레이어> <1~100000>")
                else runtime.award(data, amount)
            }
            "give" -> {
                val player = args.getOrNull(1)?.let(Bukkit::getPlayerExact)
                val data = runtime?.participants()?.firstOrNull { it.player == player }
                if (data == null || !runtime.grant(data, args.getOrNull(2).orEmpty()))
                    sender.sendMessage("/cw growth give <플레이어> <장비 ID>")
            }
            else -> sender.sendMessage("/cw growth <start|stats|items|regions|config|nextphase|spawnmobs|xp|give>")
        }
        return true
    }
}
