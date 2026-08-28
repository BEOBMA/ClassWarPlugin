package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.game.GameSettings
import org.beobma.classWarPlugin.game.MatchMode
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
    private val matchModeKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "match-mode")
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
        val assignedClasses = gameClasses.toList()
        if (assignedClasses.isEmpty()) return
        val isDual = assignedClasses.size == 2
        val remainingRefreshes = initGame.refreshesRemaining[player.uniqueId] ?: 0
        val title = if (isDual) "<dark_gray>듀얼 클래스 배정" else "<dark_gray>클래스 배정"
        val inventory = Bukkit.createInventory(null, 54, miniMessage.deserialize(title))
        fillWith(inventory, Material.GRAY_STAINED_GLASS_PANE, " ")

        if (isDual) populateDualAssignedClasses(inventory, assignedClasses)
        else populateSingleAssignedClass(inventory, assignedClasses.first())

        inventory.setItem(45, ItemStack(Material.NETHER_STAR).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize(
                    if (isDual) "<aqua><bold>두 클래스 모두 새로고침" else "<aqua><bold>클래스 새로고침"
                ))
                lore(listOf(
                    ItemDescriptionManager.renderLoreLine("<gray>남은 횟수: <yellow><bold>$remainingRefreshes"),
                    ItemDescriptionManager.renderLoreLine(
                        if (isDual) "<gray>클릭하면 현재 조합을 버리고 두 클래스를 함께 다시 배정합니다."
                        else "<gray>클릭하면 중복되지 않는 새 클래스를 배정합니다."
                    )
                ))
            }
        })
        inventory.setItem(53, ItemStack(Material.LIME_DYE).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize(
                    if (isDual) "<green><bold>두 클래스 동시 확정" else "<green><bold>클래스 확정"
                ))
                lore(listOf(ItemDescriptionManager.renderLoreLine(
                    if (isDual) "<gray>두 클래스를 한 번에 확정하며 이후에는 변경할 수 없습니다."
                    else "<gray>확정하면 다시 변경할 수 없습니다."
                )))
            }
        })

        if (PlayerTagManager.hasTag(player, "openAssignedClassInventory")) {
            PlayerTagManager.addTag(player, "openingAssignedClassInventory")
        }
        PlayerTagManager.addTag(player, "openAssignedClassInventory")
        player.openInventory(inventory)
    }

    private fun populateSingleAssignedClass(inventory: Inventory, gameClass: GameClass) {
        inventory.setItem(4, createClassItem(gameClass))
        inventory.setItem(19, gameClass.weapon.toItemStack())
        val skillSlots = listOf(20, 21, 22, 23, 24, 25, 26, 27)
        gameClass.skills.forEachIndexed { index, skill ->
            val slot = skillSlots.getOrNull(index) ?: return@forEachIndexed
            inventory.setItem(slot, createFullDescriptionItem(
                skillDyeMaterial(index), skill.name, skill.description,
                ItemDescriptionManager.cooldownLines(skill.cooldown),
            ))
        }
        gameClass.passives.forEachIndexed { index, passive ->
            if (index >= 7) return@forEachIndexed
            inventory.setItem(30 + index, createFullDescriptionItem(
                Material.WHITE_DYE, passive.name, passive.description
            ))
        }
    }

    private fun populateDualAssignedClasses(inventory: Inventory, classes: List<GameClass>) {
        val first = classes[0]
        val second = classes[1]
        inventory.setItem(2, createClassItem(first))
        inventory.setItem(6, createClassItem(second))
        inventory.setItem(11, first.weapon.toItemStack())
        inventory.setItem(15, second.weapon.toItemStack())

        populateAssignedSkills(inventory, first, (18..25).toList())
        populateAssignedSkills(inventory, second, (27..34).toList(), first.skills.size)
        populateAssignedPassives(inventory, first, (36..42).toList())
        populateAssignedPassives(inventory, second, (46..52).toList())
    }

    private fun populateAssignedSkills(
        inventory: Inventory,
        gameClass: GameClass,
        slots: List<Int>,
        dyeOffset: Int = 0,
    ) {
        gameClass.skills.forEachIndexed { index, skill ->
            val slot = slots.getOrNull(index) ?: return@forEachIndexed
            inventory.setItem(slot, createFullDescriptionItem(
                skillDyeMaterial(dyeOffset + index), skill.name, skill.description,
                ItemDescriptionManager.cooldownLines(skill.cooldown),
            ))
        }
    }

    private fun populateAssignedPassives(inventory: Inventory, gameClass: GameClass, slots: List<Int>) {
        gameClass.passives.forEachIndexed { index, passive ->
            val slot = slots.getOrNull(index) ?: return@forEachIndexed
            inventory.setItem(slot, createFullDescriptionItem(
                Material.WHITE_DYE, passive.name, passive.description
            ))
        }
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
            "<yellow><bold>화면 및 메시지 설정",
            listOf("<gray>Tab 목록, 피해량 텍스트와 사망 메시지를 설정합니다."),
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
            ConfigCategory.COMBAT -> "화면 및 메시지 설정"
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
                inventory.setItem(22, createSettingItem(Material.RED_STAINED_GLASS, "최종 자기장 하강 시간", settings.finalBorderDescentSeconds, "초"))
                inventory.setItem(23, createSettingItem(Material.MAGMA_BLOCK, "보더 피해 유예 거리", settings.borderDamageBuffer, "블록"))
            }

            ConfigCategory.COMBAT -> {
                inventory.setItem(10, createToggleItem(
                    "Tab 플레이어 목록 표시",
                    settings.playerListVisible,
                    listOf("<red><bold>주의: 비활성화하면 관전 모드에서 플레이어 선택 및 관전에 문제가 생길 수 있습니다."),
                ))
                inventory.setItem(12, createToggleItem("사망 메시지 표시", settings.deathMessagesEnabled))
                inventory.setItem(14, createToggleItem("사망 메시지에 처치자 표시", settings.deathMessagesShowKiller))
                inventory.setItem(16, createToggleItem("사망 메시지에 사망 사유 표시", settings.deathMessagesShowCause))
                inventory.setItem(22, createToggleItem("피해량 텍스트 표시", settings.damageIndicatorsEnabled))
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

    fun Player.openGameModeInventory() {
        val inventory = Bukkit.createInventory(null, 27, miniMessage.deserialize("<dark_gray>게임 모드 선택"))
        fillWith(inventory, Material.BLACK_STAINED_GLASS_PANE, " ")
        inventory.setItem(10, createMatchModeItem(Material.IRON_SWORD, MatchMode.CLASSIC))
        inventory.setItem(12, createMatchModeItem(Material.AMETHYST_SHARD, MatchMode.DUAL))
        inventory.setItem(14, createMatchModeItem(Material.RECOVERY_COMPASS, MatchMode.TAIL_TAG))
        inventory.setItem(16, createMatchModeItem(Material.ENDER_EYE, MatchMode.TAIL_TAG_DUAL))
        listOf(
            "openGameModeInventory",
            "openConfigInventory",
            "openingConfigInventory",
            "openClassListInventory",
            "openTrainingClassListInventory",
            "openClassStatusInventory",
            "openingClassStatusInventory",
        ).forEach { PlayerTagManager.removeTag(this, it) }
        PlayerTagManager.removeIf(this) {
            it.startsWith("configCategory:") ||
                it.startsWith("classListPage:") ||
                it.startsWith("classStatusReturn:")
        }
        closeInventory()
        PlayerTagManager.addTag(this, "openGameModeInventory")
        openInventory(inventory)
    }

    fun getMatchModeFromItem(item: ItemStack): MatchMode? {
        val modeName = item.itemMeta.persistentDataContainer
            .get(matchModeKey, PersistentDataType.STRING) ?: return null
        return MatchMode.entries.find { it.name == modeName }
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

    private fun createMatchModeItem(material: Material, mode: MatchMode): ItemStack =
        createDescriptionItem(
            material,
            mode.displayName,
            listOf(mode.description, "", "<green>클릭하여 이 모드로 게임을 시작합니다."),
        ).apply {
            itemMeta = itemMeta.apply {
                persistentDataContainer.set(matchModeKey, PersistentDataType.STRING, mode.name)
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
        8 -> Material.LIME_DYE
        9 -> Material.CYAN_DYE
        10 -> Material.LIGHT_BLUE_DYE
        11 -> Material.MAGENTA_DYE
        12 -> Material.BROWN_DYE
        13 -> Material.LIGHT_GRAY_DYE
        14 -> Material.GRAY_DYE
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

    private fun createToggleItem(name: String, enabled: Boolean, extraLines: List<String> = emptyList()): ItemStack =
        ItemStack(if (enabled) Material.LIME_DYE else Material.GRAY_DYE).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize("<yellow><bold>$name"))
                lore((listOf(
                    ItemDescriptionManager.renderLoreLine(
                        if (enabled) "<gray>현재 값: <green><bold>활성화" else "<gray>현재 값: <red><bold>비활성화"
                    ),
                    ItemDescriptionManager.renderLoreLine(""),
                    ItemDescriptionManager.renderLoreLine("<gray>클릭하여 활성화 여부를 변경합니다.")
                ) + extraLines.map(ItemDescriptionManager::renderLoreLine)))
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
