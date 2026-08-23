package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.game.GameSettings
import org.beobma.classWarPlugin.manager.GameClassManager.toItemStack
import org.beobma.classWarPlugin.manager.GameManager.gameClassList
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

enum class ConfigCategory {
    GAME,
    RANK,
    SCATTER,
    BORDER,
    COMBAT,
}

object InventoryManager {
    private val miniMessage = MiniMessage.miniMessage()
    private val classIdKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "class-id")
    private val nextPage = ItemStack(Material.ARROW, 1).apply {
        itemMeta = itemMeta.apply {
            displayName(miniMessage.deserialize("<gray>다음 페이지"))
        }
    }
    private val previousPage = ItemStack(Material.ARROW, 1).apply {
        itemMeta = itemMeta.apply {
            displayName(miniMessage.deserialize("<gray>이전 페이지"))
        }
    }
    private val nullItem = ItemStack(Material.LIGHT_GRAY_STAINED_GLASS, 1).apply {
        itemMeta = itemMeta.apply {
            displayName(miniMessage.deserialize("<gray>비어있음"))
        }
    }

    fun Player.openClassListInventory(page: Int) {
        val visibleClasses = gameClassList.filter { isOp || it.rank != Rank.SPECIAL }
        val inventory = buildClassListInventory(page, visibleClasses) ?: return
        PlayerTagManager.addTag(this, "openClassListInventory")
        openInventory(inventory)
    }

    fun Player.openTrainingClassListInventory(page: Int) {
        val visibleClasses = gameClassList.filter { isOp || it.rank != Rank.SPECIAL }
        val inventory = buildClassListInventory(page, visibleClasses) ?: return
        PlayerTagManager.addTag(this, "openTrainingClassListInventory")
        openInventory(inventory)
    }

    fun PlayerData.openAssignedClassInventory() {
        val gameClass = gameClass ?: return
        val remainingRefreshes = initGame.refreshesRemaining[player.uniqueId] ?: 0
        val inventory = Bukkit.createInventory(null, 54, miniMessage.deserialize("<dark_gray>클래스 배정"))
        fillWith(inventory, Material.GRAY_STAINED_GLASS_PANE, " ")

        inventory.setItem(4, createClassItem(gameClass))
        inventory.setItem(19, gameClass.weapon.toItemStack())

        val skillSlots = listOf(20, 21, 22, 23, 24, 25, 26, 27)
        gameClass.skills.forEachIndexed { index, skill ->
            val slot = skillSlots.getOrNull(index) ?: return@forEachIndexed
            inventory.setItem(slot, createFullDescriptionItem(
                skillDyeMaterial(index),
                skill.name,
                skill.description,
                ItemDescriptionManager.cooldownLines(skill.cooldown),
            ))
        }

        gameClass.passives.forEachIndexed { index, passive ->
            if (index >= 7) return@forEachIndexed
            inventory.setItem(30 + index, createFullDescriptionItem(
                Material.WHITE_DYE, passive.name, passive.description
            ))
        }

        inventory.setItem(45, ItemStack(Material.NETHER_STAR).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize("<aqua><bold>클래스 새로고침"))
                lore(listOf(
                    ItemDescriptionManager.renderLoreLine("<gray>남은 횟수: <yellow><bold>$remainingRefreshes"),
                    ItemDescriptionManager.renderLoreLine("<gray>클릭하면 중복되지 않는 새 클래스를 배정합니다.")
                ))
            }
        })
        inventory.setItem(53, ItemStack(Material.LIME_DYE).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize("<green><bold>클래스 확정"))
                lore(listOf(ItemDescriptionManager.renderLoreLine("<gray>확정하면 다시 변경할 수 없습니다.")))
            }
        })

        if (PlayerTagManager.hasTag(player, "openAssignedClassInventory")) {
            PlayerTagManager.addTag(player, "openingAssignedClassInventory")
        }
        PlayerTagManager.addTag(player, "openAssignedClassInventory")
        player.openInventory(inventory)
    }

    fun Player.openConfigInventory() {
        val inventory = Bukkit.createInventory(null, 27, miniMessage.deserialize("<dark_gray>ClassWar 설정 카테고리"))
        fillWith(inventory, Material.BLACK_STAINED_GLASS_PANE, " ")

        inventory.setItem(10, createDescriptionItem(
            Material.CLOCK,
            "<yellow><bold>게임 시작 설정",
            listOf("<gray>새로고침 횟수와 시작 카운트다운을 설정합니다."),
        ))
        inventory.setItem(12, createDescriptionItem(
            Material.NETHER_STAR,
            "<yellow><bold>랭크 확률 설정",
            listOf("<gray>각 클래스 랭크의 등장 가중치를 설정합니다."),
        ))
        inventory.setItem(14, createDescriptionItem(
            Material.RECOVERY_COMPASS,
            "<yellow><bold>맵 및 산개 설정",
            listOf("<gray>산개 반경과 플레이어 최소 간격을 설정합니다."),
        ))
        inventory.setItem(16, createDescriptionItem(
            Material.BARRIER,
            "<yellow><bold>월드보더 설정",
            listOf("<gray>월드보더 사용 여부와 축소 방식을 설정합니다."),
        ))
        inventory.setItem(20, createDescriptionItem(
            Material.ARMOR_STAND,
            "<yellow><bold>전투 표시 설정",
            listOf("<gray>피해량 텍스트 등 전투 표시 기능을 설정합니다."),
        ))
        openConfigView(inventory, null)
    }

    fun Player.openConfigCategoryInventory(category: ConfigCategory) {
        val settings = GameSettings.snapshot()
        val title = when (category) {
            ConfigCategory.GAME -> "게임 시작 설정"
            ConfigCategory.RANK -> "랭크 확률 설정"
            ConfigCategory.SCATTER -> "맵 및 산개 설정"
            ConfigCategory.BORDER -> "월드보더 설정"
            ConfigCategory.COMBAT -> "전투 표시 설정"
        }
        val inventory = Bukkit.createInventory(null, 27, miniMessage.deserialize("<dark_gray>$title"))
        fillWith(inventory, Material.BLACK_STAINED_GLASS_PANE, " ")

        when (category) {
            ConfigCategory.GAME -> {
                inventory.setItem(11, createSettingItem(Material.NETHER_STAR, "새로고침 횟수", settings.refreshChances, "회"))
                inventory.setItem(15, createSettingItem(Material.CLOCK, "시작 카운트다운", settings.countdownSeconds, "초"))
            }

            ConfigCategory.RANK -> {
                inventory.setItem(10, createRankWeightItem(Material.NETHER_STAR, Rank.SPECIAL, settings.rankWeights))
                inventory.setItem(11, createRankWeightItem(Material.ORANGE_DYE, Rank.L, settings.rankWeights))
                inventory.setItem(12, createRankWeightItem(Material.MAGENTA_DYE, Rank.S, settings.rankWeights))
                inventory.setItem(14, createRankWeightItem(Material.LIME_DYE, Rank.A, settings.rankWeights))
                inventory.setItem(15, createRankWeightItem(Material.LIGHT_BLUE_DYE, Rank.B, settings.rankWeights))
                inventory.setItem(16, createRankWeightItem(Material.YELLOW_DYE, Rank.C, settings.rankWeights))
            }

            ConfigCategory.SCATTER -> {
                inventory.setItem(10, createSettingItem(Material.COMPASS, "최소 산개 반경", settings.scatterMinRadius, "블록"))
                inventory.setItem(13, createSettingItem(Material.RECOVERY_COMPASS, "최대 산개 반경", settings.scatterMaxRadius, "블록"))
                inventory.setItem(16, createSettingItem(Material.PLAYER_HEAD, "플레이어 최소 간격", settings.minimumPlayerDistance, "블록"))
            }

            ConfigCategory.BORDER -> {
                inventory.setItem(10, createToggleItem("월드보더 사용", settings.borderEnabled))
                inventory.setItem(11, createSettingItem(Material.BARRIER, "월드보더 초기 크기", settings.borderInitialSize, "블록"))
                inventory.setItem(12, createSettingItem(Material.REPEATER, "월드보더 대기 시간", settings.borderDelaySeconds, "초"))
                inventory.setItem(13, createSettingItem(Material.REDSTONE, "월드보더 축소 시간", settings.borderShrinkSeconds, "초"))
                inventory.setItem(14, createSettingItem(Material.IRON_BARS, "월드보더 최소 크기", settings.borderMinimumSize, "블록"))
                inventory.setItem(15, createSettingItem(Material.COMPASS, "중심 최소 이동 거리", settings.borderCenterMinimumDistance, "블록"))
                inventory.setItem(16, createSettingItem(Material.RECOVERY_COMPASS, "중심 최대 이동 거리", settings.borderCenterMaximumDistance, "블록"))
            }

            ConfigCategory.COMBAT -> {
                inventory.setItem(13, createToggleItem("피해량 텍스트 표시", settings.damageIndicatorsEnabled))
            }
        }

        inventory.setItem(18, ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize("<yellow><bold>카테고리로 돌아가기"))
            }
        })

        openConfigView(inventory, category)
    }

    private fun Player.openConfigView(inventory: Inventory, category: ConfigCategory?) {
        if (PlayerTagManager.hasTag(this, "openConfigInventory")) {
            PlayerTagManager.addTag(this, "openingConfigInventory")
        }
        PlayerTagManager.removeIf(this) { it.startsWith("configCategory:") }
        category?.let { PlayerTagManager.addTag(this, "configCategory:${it.name}") }
        PlayerTagManager.addTag(this, "openConfigInventory")
        openInventory(inventory)
    }

    fun getOpenConfigCategory(player: Player): ConfigCategory? =
        PlayerTagManager.findTag(player) { it.startsWith("configCategory:") }
            ?.substringAfter("configCategory:")
            ?.let { name -> ConfigCategory.entries.find { it.name == name } }

    fun getClassFromItem(item: ItemStack): GameClass? {
        val classId = item.itemMeta.persistentDataContainer
            .get(classIdKey, PersistentDataType.STRING) ?: return null
        return gameClassList.find { it.javaClass.name == classId }
    }

    fun Player.openClassStatusInventory(gameClass: GameClass) {
        val inventory = Bukkit.createInventory(null, 27, miniMessage.deserialize(UtilManager.applyKeywords(gameClass.name)))
        inventory.setItem(0, gameClass.weapon.toItemStack())
        for (i in 0..gameClass.skills.size) {
            val skill = gameClass.skills.getOrNull(i) ?: break
            inventory.setItem(i + 1, createFullDescriptionItem(
                skillDyeMaterial(i),
                skill.name,
                skill.description,
                ItemDescriptionManager.cooldownLines(skill.cooldown),
            ))
        }
        for (i in gameClass.skills.size + 1..gameClass.passives.size + gameClass.skills.size + 1) {
            val passive = gameClass.passives.getOrNull(i - gameClass.skills.size - 1) ?: break
            val material = Material.WHITE_DYE
            inventory.setItem(i, createFullDescriptionItem(
                material, passive.name, passive.description
            ))
        }
        openInventory(inventory)
    }

    private fun buildClassListInventory(page: Int, classList: List<GameClass?>): Inventory? {
        val totalPages = (classList.size + 18 - 1) / 18
        if (page !in 0..<totalPages) return null
        val inventory = Bukkit.createInventory(null, 27, miniMessage.deserialize("클래스 목록 (페이지 ${page + 1}/${totalPages})"))
        val startIdx = page * 18
        val endIdx = minOf(startIdx + 18, classList.size)

        fillWithNullItems(inventory)

        for (i in startIdx until endIdx) {
            val gameClass = classList[i] ?: continue
            inventory.setItem(i - startIdx, createClassItem(gameClass))
        }

        if (page > 0) {
            inventory.setItem(18, previousPage)
        }

        if (page < totalPages - 1) {
            inventory.setItem(26, nextPage)
        }

        return inventory
    }

    private fun fillWithNullItems(inventory: Inventory) {
        for (i in 0..26) {
            inventory.setItem(i, nullItem)
        }
    }

    private fun fillWith(inventory: Inventory, material: Material, name: String) {
        val item = ItemStack(material).apply {
            itemMeta = itemMeta.apply { displayName(miniMessage.deserialize(name)) }
        }
        for (slot in 0 until inventory.size) inventory.setItem(slot, item)
    }

    private fun createDescriptionItem(material: Material, name: String, lines: List<String>): ItemStack =
        ItemStack(material).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize(UtilManager.applyKeywords(name)))
                lore(lines.map(ItemDescriptionManager::renderLoreLine))
            }
        }

    fun skillDyeMaterial(index: Int): Material = when (index) {
        0 -> Material.RED_DYE
        1 -> Material.ORANGE_DYE
        2 -> Material.YELLOW_DYE
        3 -> Material.GREEN_DYE
        4 -> Material.BLUE_DYE
        5 -> Material.PURPLE_DYE
        6 -> Material.PINK_DYE
        7 -> Material.BLACK_DYE
        else -> Material.WHITE_DYE
    }

    private fun createFullDescriptionItem(
        material: Material,
        name: String,
        details: List<String>,
        alwaysVisibleLines: List<String> = emptyList(),
    ): ItemStack = ItemDescriptionManager.apply(
        ItemStack(material).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize(UtilManager.applyKeywords(name)))
            }
        },
        details,
        alwaysVisibleLines,
    )

    private fun createSettingItem(material: Material, name: String, value: Number, unit: String): ItemStack =
        ItemStack(material).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize("<yellow><bold>$name"))
                lore(listOf(
                    ItemDescriptionManager.renderLoreLine("<gray>현재 값: <white><bold>$value $unit"),
                    ItemDescriptionManager.renderLoreLine(""),
                    ItemDescriptionManager.renderLoreLine("<green>좌클릭: 증가 <red>우클릭: 감소"),
                    ItemDescriptionManager.renderLoreLine("<gray>Shift 클릭: 10배 조절")
                ))
            }
        }

    private fun createToggleItem(name: String, enabled: Boolean): ItemStack =
        ItemStack(if (enabled) Material.LIME_DYE else Material.GRAY_DYE).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize("<yellow><bold>$name"))
                lore(listOf(
                    ItemDescriptionManager.renderLoreLine(
                        if (enabled) "<gray>현재 값: <green><bold>활성화" else "<gray>현재 값: <red><bold>비활성화"
                    ),
                    ItemDescriptionManager.renderLoreLine(""),
                    ItemDescriptionManager.renderLoreLine("<gray>클릭하여 활성화 여부를 변경합니다.")
                ))
            }
        }

    private fun createRankWeightItem(material: Material, rank: Rank, weights: Map<Rank, Int>): ItemStack {
        val weight = weights[rank] ?: 0
        val totalWeight = weights.values.sum().coerceAtLeast(1)
        val chance = weight.toDouble() / totalWeight * 100.0
        return ItemStack(material).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize("${rank.formattedName} <yellow>등장 가중치"))
                lore(listOf(
                    ItemDescriptionManager.renderLoreLine("<gray>현재 가중치: <white><bold>$weight"),
                    ItemDescriptionManager.renderLoreLine("<gray>전체 기준 확률: <white><bold>${"%.2f".format(chance)}%"),
                    ItemDescriptionManager.renderLoreLine(""),
                    ItemDescriptionManager.renderLoreLine("<green>좌클릭: 증가 <red>우클릭: 감소"),
                    ItemDescriptionManager.renderLoreLine("<gray>Shift 클릭: 10배 조절"),
                ))
            }
        }
    }

    private fun createClassItem(gameClass: GameClass): ItemStack {
        val name = miniMessage.deserialize(UtilManager.applyKeywords(gameClass.name))
        val rank = listOf(ItemDescriptionManager.renderLoreLine("<gray>랭크: ${gameClass.rank.formattedName}"))
        return ItemStack(gameClass.classItemMaterial, 1).apply {
            itemMeta = itemMeta.apply {
                displayName(name)
                lore(rank)
                persistentDataContainer.set(classIdKey, PersistentDataType.STRING, gameClass.javaClass.name)
            }
        }
    }
}
