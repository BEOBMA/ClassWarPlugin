package org.beobma.classWarPlugin.manager

import io.papermc.paper.datacomponent.DataComponentType
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.TooltipDisplay
import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.game.DamageMultiplierType
import org.beobma.classWarPlugin.game.GameSettings
import org.beobma.classWarPlugin.game.MatchMode
import org.beobma.classWarPlugin.game.damageMultiplier
import org.beobma.classWarPlugin.manager.GameClassManager.toItemStack
import org.beobma.classWarPlugin.manager.GameManager.gameClassList
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/** 설정 인벤토리의 최상위 화면 범주다. */
enum class ConfigCategory {
    PERSONAL,
    GAME,
    RANK,
    SCATTER,
    BORDER,
    COMBAT,
    DAMAGE,
    CLASS_BALANCE,
}

/** 클래스 선택, 훈련 및 서버 설정용 인벤토리 UI를 생성하고 갱신한다. */
object InventoryManager {
    private const val CLASS_BALANCE_PAGE_SIZE = 45
    private data class DamageConfigItem(
        val slot: Int,
        val type: DamageMultiplierType,
        val material: Material,
        val name: String,
    )

    private val damageConfigItems = listOf(
        DamageConfigItem(10, DamageMultiplierType.OVERALL, Material.NETHER_STAR, "전체 피해"),
        DamageConfigItem(11, DamageMultiplierType.BASIC_ATTACK, Material.IRON_SWORD, "기본 공격 피해"),
        DamageConfigItem(12, DamageMultiplierType.RANGED_ATTACK, Material.BOW, "원거리 공격 피해"),
        DamageConfigItem(13, DamageMultiplierType.SKILL, Material.BLAZE_ROD, "스킬 피해"),
        DamageConfigItem(14, DamageMultiplierType.STATUS_EFFECT, Material.FERMENTED_SPIDER_EYE, "상태이상 피해"),
        DamageConfigItem(15, DamageMultiplierType.FALL, Material.FEATHER, "낙하 피해"),
        DamageConfigItem(16, DamageMultiplierType.DROWNING, Material.WATER_BUCKET, "익사·건조 피해"),
        DamageConfigItem(19, DamageMultiplierType.FIRE, Material.FLINT_AND_STEEL, "화염 피해"),
        DamageConfigItem(20, DamageMultiplierType.LAVA, Material.LAVA_BUCKET, "용암 피해"),
        DamageConfigItem(21, DamageMultiplierType.SUFFOCATION, Material.SAND, "질식·끼임 피해"),
        DamageConfigItem(22, DamageMultiplierType.EXPLOSION, Material.TNT, "폭발 피해"),
        DamageConfigItem(23, DamageMultiplierType.POISON_MAGIC, Material.SPIDER_EYE, "독·위더·마법 피해"),
        DamageConfigItem(24, DamageMultiplierType.STARVATION, Material.ROTTEN_FLESH, "굶주림 피해"),
        DamageConfigItem(25, DamageMultiplierType.VOID, Material.ENDER_PEARL, "공허·강제 처치 피해"),
        DamageConfigItem(28, DamageMultiplierType.FREEZING, Material.POWDER_SNOW_BUCKET, "동상 피해"),
        DamageConfigItem(29, DamageMultiplierType.CONTACT, Material.CACTUS, "접촉 피해"),
        DamageConfigItem(30, DamageMultiplierType.LIGHTNING, Material.LIGHTNING_ROD, "번개 피해"),
        DamageConfigItem(31, DamageMultiplierType.MOB_ATTACK, Material.ZOMBIE_HEAD, "몹·비플레이어 투사체 피해"),
        DamageConfigItem(32, DamageMultiplierType.IMPACT, Material.ANVIL, "충돌·낙하 블록 피해"),
        DamageConfigItem(33, DamageMultiplierType.WORLD_BORDER, Material.BARRIER, "월드보더·최종 자기장 피해"),
        DamageConfigItem(34, DamageMultiplierType.OTHER_ENVIRONMENT, Material.SHIELD, "기타 환경 피해"),
    )
    private val miniMessage = MiniMessage.miniMessage()
    private val classIconVisibleTooltipComponents: Set<DataComponentType> = setOf(
        DataComponentTypes.CUSTOM_NAME,
        DataComponentTypes.ITEM_NAME,
        DataComponentTypes.LORE,
        DataComponentTypes.TOOLTIP_DISPLAY,
    )
    private val classIconImplicitTooltipComponents: Set<DataComponentType> = setOf(
        DataComponentTypes.ATTRIBUTE_MODIFIERS,
        DataComponentTypes.JUKEBOX_PLAYABLE,
        DataComponentTypes.WEAPON,
        DataComponentTypes.TOOL,
        DataComponentTypes.EQUIPPABLE,
        DataComponentTypes.BLOCKS_ATTACKS,
        DataComponentTypes.INSTRUMENT,
        DataComponentTypes.OMINOUS_BOTTLE_AMPLIFIER,
        DataComponentTypes.SULFUR_CUBE_CONTENT,
    )
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

    private class KeywordInventoryHolder : InventoryHolder {
        lateinit var backingInventory: Inventory
        override fun getInventory(): Inventory = backingInventory
    }

    /** 설명이 등록된 모든 키워드를 아이콘과 툴팁으로 보여준다. */
    fun Player.openKeywordInventory() {
        val holder = KeywordInventoryHolder()
        val inventory = Bukkit.createInventory(
            holder,
            36,
            miniMessage.deserialize("<dark_gray>키워드 사전"),
        )
        holder.backingInventory = inventory
        Keyword.describedEntries.forEachIndexed { slot, keyword ->
            inventory.setItem(slot, createDescriptionItem(
                keyword.icon,
                keyword.string,
                listOf(
                    keyword.requireDescription(),
                    "",
                    "<dark_gray>영문명: <gray>${keyword.name}",
                ),
            ))
        }
        openInventory(inventory)
    }

    fun isKeywordInventory(inventory: Inventory): Boolean =
        inventory.holder is KeywordInventoryHolder

    fun Player.openClassListInventory(page: Int) {
        val visibleClasses = gameClassList.filter { isOp || it.rank != Rank.SPECIAL }
        val inventory = buildClassListInventory(page, visibleClasses, this) ?: return
        PlayerTagManager.addFlag(this, PlayerFlag.OPEN_CLASS_LIST_INVENTORY)
        openInventory(inventory)
    }

    fun Player.openTrainingClassListInventory(page: Int) {
        val visibleClasses = gameClassList.filter { isOp || it.rank != Rank.SPECIAL }
        val inventory = buildClassListInventory(page, visibleClasses, this) ?: return
        PlayerTagManager.addFlag(this, PlayerFlag.OPEN_TRAINING_CLASS_LIST_INVENTORY)
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

        if (isDual) populateDualAssignedClasses(inventory, assignedClasses, player)
        else populateSingleAssignedClass(inventory, assignedClasses.first(), player)

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

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_ASSIGNED_CLASS_INVENTORY)) {
            PlayerTagManager.addFlag(player, PlayerFlag.OPENING_ASSIGNED_CLASS_INVENTORY)
        }
        PlayerTagManager.addFlag(player, PlayerFlag.OPEN_ASSIGNED_CLASS_INVENTORY)
        player.openInventory(inventory)
    }

    private fun populateSingleAssignedClass(inventory: Inventory, gameClass: GameClass, viewer: Player) {
        inventory.setItem(4, createClassItem(gameClass, viewer))
        inventory.setItem(19, gameClass.weapon.toItemStack(viewer))
        val skillSlots = listOf(20, 21, 22, 23, 24, 25, 26, 27)
        gameClass.skills.forEachIndexed { index, skill ->
            val slot = skillSlots.getOrNull(index) ?: return@forEachIndexed
            inventory.setItem(slot, createFullDescriptionItem(
                viewer, skillDyeMaterial(index), skill.name, skill.description, skill.briefDescription,
                ItemDescriptionManager.cooldownLines(skill.cooldown),
            ))
        }
        gameClass.passives.forEachIndexed { index, passive ->
            if (index >= 7) return@forEachIndexed
            inventory.setItem(30 + index, createFullDescriptionItem(
                viewer, Material.WHITE_DYE, passive.name, passive.description, passive.briefDescription,
            ))
        }
    }

    private fun populateDualAssignedClasses(inventory: Inventory, classes: List<GameClass>, viewer: Player) {
        val first = classes[0]
        val second = classes[1]
        inventory.setItem(2, createClassItem(first, viewer))
        inventory.setItem(6, createClassItem(second, viewer))
        inventory.setItem(11, first.weapon.toItemStack(viewer))
        inventory.setItem(15, second.weapon.toItemStack(viewer))

        populateAssignedSkills(inventory, first, (18..25).toList(), viewer = viewer)
        populateAssignedSkills(inventory, second, (27..34).toList(), first.skills.size, viewer)
        populateAssignedPassives(inventory, first, (36..42).toList(), viewer)
        populateAssignedPassives(inventory, second, (46..52).toList(), viewer)
    }

    private fun populateAssignedSkills(
        inventory: Inventory,
        gameClass: GameClass,
        slots: List<Int>,
        dyeOffset: Int = 0,
        viewer: Player,
    ) {
        gameClass.skills.forEachIndexed { index, skill ->
            val slot = slots.getOrNull(index) ?: return@forEachIndexed
            inventory.setItem(slot, createFullDescriptionItem(
                viewer, skillDyeMaterial(dyeOffset + index), skill.name, skill.description, skill.briefDescription,
                ItemDescriptionManager.cooldownLines(skill.cooldown),
            ))
        }
    }

    private fun populateAssignedPassives(
        inventory: Inventory,
        gameClass: GameClass,
        slots: List<Int>,
        viewer: Player,
    ) {
        gameClass.passives.forEachIndexed { index, passive ->
            val slot = slots.getOrNull(index) ?: return@forEachIndexed
            inventory.setItem(slot, createFullDescriptionItem(
                viewer, Material.WHITE_DYE, passive.name, passive.description, passive.briefDescription,
            ))
        }
    }

    fun Player.openConfigInventory() {
        val inventory = Bukkit.createInventory(null, 27, miniMessage.deserialize("<dark_gray>ClassWar 설정 카테고리"))
        fillWith(inventory, Material.BLACK_STAINED_GLASS_PANE, " ")

        inventory.setItem(22, createDescriptionItem(
            if (PlayerPreferenceManager.usesDetailedDescriptions(this)) Material.WRITABLE_BOOK else Material.BOOK,
            "<aqua><bold>개인 설명 설정",
            listOf(
                if (PlayerPreferenceManager.usesDetailedDescriptions(this)) {
                    "<gray>현재 표시 방식: <green><bold>상세 설명"
                } else {
                    "<gray>현재 표시 방식: <yellow><bold>간략 설명 (기본값)"
                },
                "",
                "<gray>자신에게 표시되는 클래스 설명만 변경합니다.",
                "<green>OP 권한 없이도 사용할 수 있습니다.",
            ),
        ))

        if (isOp) {
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
            inventory.setItem(18, createDescriptionItem(
                Material.IRON_SWORD,
                "<yellow><bold>피해 배율 설정",
                listOf("<gray>공격과 환경 피해를 유형별로 조절하거나 비활성화합니다."),
            ))
            inventory.setItem(20, createDescriptionItem(
                Material.ARMOR_STAND,
                "<yellow><bold>화면 및 메시지 설정",
                listOf("<gray>Tab 목록, 피해량 텍스트와 사망 메시지를 설정합니다."),
            ))
            inventory.setItem(24, createDescriptionItem(
                Material.REPEATER,
                "<yellow><bold>클래스 밸런스 설정",
                listOf("<gray>클래스별 피해, 사거리, 상태이상 등 전투 수치 배율을 설정합니다."),
            ))
        }
        openConfigView(inventory, null)
    }

    fun Player.openConfigCategoryInventory(category: ConfigCategory) {
        if (category == ConfigCategory.CLASS_BALANCE) {
            openClassBalanceListInventory(0)
            return
        }
        val settings = GameSettings.snapshot()
        val title = when (category) {
            ConfigCategory.PERSONAL -> "개인 설명 설정"
            ConfigCategory.GAME -> "게임 시작 설정"
            ConfigCategory.RANK -> "랭크 확률 설정"
            ConfigCategory.SCATTER -> "맵 및 산개 설정"
            ConfigCategory.BORDER -> "월드보더 설정"
            ConfigCategory.COMBAT -> "화면 및 메시지 설정"
            ConfigCategory.DAMAGE -> "피해 배율 설정"
            ConfigCategory.CLASS_BALANCE -> "클래스 밸런스 설정"
        }
        val inventorySize = if (category == ConfigCategory.DAMAGE) 54 else 27
        val inventory = Bukkit.createInventory(null, inventorySize, miniMessage.deserialize("<dark_gray>$title"))
        fillWith(inventory, Material.BLACK_STAINED_GLASS_PANE, " ")

        when (category) {
            ConfigCategory.PERSONAL -> {
                inventory.setItem(13, createToggleItem(
                    "상세 설명 표시",
                    PlayerPreferenceManager.usesDetailedDescriptions(this),
                    listOf(
                        "<gray>비활성화하면 핵심 효과만 간략하게 표시합니다.",
                        "<gray>활성화하면 세부 조건과 용어 설명까지 표시합니다.",
                        "",
                        "<yellow>기본값: 간략 설명",
                        "<green>이 설정은 플레이어별로 영구 저장됩니다.",
                    ),
                ))
            }

            ConfigCategory.GAME -> {
                inventory.setItem(11, createSettingItem(Material.NETHER_STAR, "새로고침 횟수", settings.refreshChances, "회"))
                inventory.setItem(13, createMultiplierSettingItem(
                    Material.CLOCK,
                    "재사용 대기시간 흐름",
                    settings.cooldownFlowMultiplier,
                    listOf(
                        "<gray>2.0배면 재사용 대기시간이 2배 빠르게 흐릅니다.",
                        "<gray>모든 클래스 스킬에 공통으로 적용됩니다.",
                    ),
                ))
                inventory.setItem(15, createSettingItem(Material.CLOCK, "시작 카운트다운", settings.countdownSeconds, "초"))
                inventory.setItem(17, createDescriptionItem(
                    Material.CHEST,
                    "<yellow><bold>기본 지급 아이템 편집",
                    listOf("<gray>클릭하여 게임 시작 시 지급할 아이템을 직접 넣거나 빼세요.", "<gray>갑옷, 무기, 활 등 모든 아이템을 설정할 수 있습니다."),
                ))
                inventory.setItem(26, createDescriptionItem(
                    settings.classWeapon?.type ?: Material.IRON_SWORD,
                    "<yellow><bold>클래스 기본 무기 편집",
                    listOf("<gray>클릭하여 모든 클래스에 지급되는 기본 무기를 교체합니다.", "<gray>비워 두면 각 클래스의 고유 기본 무기를 사용합니다."),
                ))
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
                inventory.setItem(24, createSettingItem(Material.BARRIER, "월드보더 블록당 피해", settings.borderDamagePerBlock, "피해"))
                inventory.setItem(25, createSettingItem(Material.REDSTONE_BLOCK, "최종 자기장 피해", settings.finalBorderDamage, "피해"))
                inventory.setItem(26, createSettingItem(Material.REPEATER, "최종 자기장 피해 주기", settings.finalBorderDamageIntervalSeconds, "초"))
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
                inventory.setItem(24, createSettingItem(Material.TOTEM_OF_UNDYING, "플레이어별 추가 목숨", settings.playerLives, "개"))
                inventory.setItem(26, createToggleItem(
                    "처치 보상",
                    settings.eliminationRewardsEnabled,
                    listOf("<gray>BREAK: 최대 체력의 35% 회복", "<gray>TERMINATE: 최대 체력의 50% 회복"),
                ))
            }

            ConfigCategory.DAMAGE -> damageConfigItems.forEach { item ->
                val configuredValue = settings.damageMultipliers[item.type] ?: 1.0
                val extraLines = buildList {
                    if (item.type == DamageMultiplierType.OVERALL) {
                        add("<gray>모든 공격 및 환경 피해에 공통으로 곱해집니다.")
                    } else {
                        add("<gray>전체 피해 배율 적용 후: <aqua><bold>${"%.1f".format(settings.damageMultiplier(item.type))}배")
                    }
                    add("<gray>0.0배로 설정하면 해당 피해를 받지 않습니다.")
                }
                inventory.setItem(
                    item.slot,
                    createMultiplierSettingItem(item.material, item.name, configuredValue, extraLines),
                )
            }

            ConfigCategory.CLASS_BALANCE -> Unit
        }

        val backSlot = if (category == ConfigCategory.DAMAGE) 45 else 18
        inventory.setItem(backSlot, ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize("<yellow><bold>카테고리로 돌아가기"))
            }
        })

        openConfigView(inventory, category)
    }

    /** 게임 시작 시 지급할 공용 아이템을 편집하는 인벤토리를 연다. */
    fun Player.openStartingItemsInventory() {
        val inventory = Bukkit.createInventory(null, 54, miniMessage.deserialize("<dark_gray>기본 지급 아이템 편집"))
        GameSettings.snapshot().startingItems.forEachIndexed { slot, item ->
            if (slot < inventory.size) inventory.setItem(slot, item.clone())
        }
        openInventory(inventory)
        // openInventory가 이전 설정 창의 Close 이벤트를 먼저 발생시키므로 그 뒤에 편집 상태를 표시한다.
        PlayerTagManager.addFlag(this, PlayerFlag.OPEN_STARTING_ITEMS_INVENTORY)
    }

    /** 모든 클래스에 공통으로 적용할 기본 무기 템플릿을 편집한다. */
    fun Player.openClassWeaponInventory() {
        val inventory = Bukkit.createInventory(null, 9, miniMessage.deserialize("<dark_gray>클래스 기본 무기 편집"))
        GameSettings.snapshot().classWeapon?.let { inventory.setItem(4, it.clone()) }
        openInventory(inventory)
        PlayerTagManager.addFlag(this, PlayerFlag.OPEN_CLASS_WEAPON_INVENTORY)
    }

    fun getDamageMultiplierTypeFromSlot(slot: Int): DamageMultiplierType? =
        damageConfigItems.firstOrNull { it.slot == slot }?.type

    fun Player.openClassBalanceListInventory(page: Int) {
        val classes = gameClassList
        val totalPages = maxOf(1, (classes.size + CLASS_BALANCE_PAGE_SIZE - 1) / CLASS_BALANCE_PAGE_SIZE)
        val safePage = page.coerceIn(0, totalPages - 1)
        val inventory = Bukkit.createInventory(
            null,
            54,
            miniMessage.deserialize("<dark_gray>클래스 밸런스 (${safePage + 1}/$totalPages)"),
        )
        fillWith(inventory, Material.BLACK_STAINED_GLASS_PANE, " ")
        val startIndex = safePage * CLASS_BALANCE_PAGE_SIZE
        val endIndex = minOf(startIndex + CLASS_BALANCE_PAGE_SIZE, classes.size)
        for (index in startIndex until endIndex) {
            inventory.setItem(index - startIndex, createClassItem(classes[index], this))
        }
        inventory.setItem(45, createDescriptionItem(Material.ARROW, "<yellow><bold>카테고리로 돌아가기", emptyList()))
        if (safePage > 0) {
            inventory.setItem(48, createDescriptionItem(Material.ARROW, "<yellow><bold>이전 페이지", emptyList()))
        }
        if (safePage < totalPages - 1) {
            inventory.setItem(50, createDescriptionItem(Material.ARROW, "<yellow><bold>다음 페이지", emptyList()))
        }

        PlayerTagManager.removeValue(this, PlayerTagValue.CLASS_BALANCE_PAGE)
        PlayerTagManager.removeValue(this, PlayerTagValue.CLASS_BALANCE_CLASS)
        PlayerTagManager.setValue(this, PlayerTagValue.CLASS_BALANCE_PAGE, safePage)
        openConfigView(inventory, ConfigCategory.CLASS_BALANCE)
    }

    fun Player.openClassBalanceDetailInventory(gameClass: GameClass) {
        val modifiers = ClassBalanceManager.modifiers(gameClass)
        val inventory = Bukkit.createInventory(
            null,
            27,
            miniMessage.deserialize("<dark_gray>클래스 밸런스: ${UtilManager.applyKeywords(gameClass.name)}"),
        )
        fillWith(inventory, Material.BLACK_STAINED_GLASS_PANE, " ")
        val fields = listOf(
            Triple(10, ClassBalanceField.DAMAGE, Material.IRON_SWORD),
            Triple(11, ClassBalanceField.HEALING, Material.GOLDEN_APPLE),
            Triple(12, ClassBalanceField.RANGE, Material.SPYGLASS),
            Triple(13, ClassBalanceField.OVERALL, Material.NETHER_STAR),
            Triple(14, ClassBalanceField.STATUS_DURATION, Material.CLOCK),
            Triple(15, ClassBalanceField.STATUS_POWER, Material.BLAZE_POWDER),
            Triple(16, ClassBalanceField.COOLDOWN_FLOW, Material.REPEATER),
        )
        fields.forEach { (slot, field, material) ->
            inventory.setItem(slot, createClassBalanceSettingItem(material, field, modifiers))
        }
        inventory.setItem(18, createDescriptionItem(Material.ARROW, "<yellow><bold>클래스 목록으로 돌아가기", emptyList()))
        inventory.setItem(22, createDescriptionItem(
            Material.BARRIER,
            "<red><bold>이 클래스 설정 초기화",
            listOf("<gray>클릭하면 모든 배율을 기본값으로 되돌립니다."),
        ))

        PlayerTagManager.setValue(
            this,
            PlayerTagValue.CLASS_BALANCE_CLASS,
            ClassBalanceManager.configKey(gameClass),
        )
        openConfigView(inventory, ConfigCategory.CLASS_BALANCE)
    }

    fun getOpenClassBalancePage(player: Player): Int =
        PlayerTagManager.getValue(player, PlayerTagValue.CLASS_BALANCE_PAGE)
            ?.toIntOrNull()
            ?: 0

    fun getSelectedClassBalance(player: Player): GameClass? {
        val key = PlayerTagManager.getValue(player, PlayerTagValue.CLASS_BALANCE_CLASS)
            ?: return null
        return gameClassList.firstOrNull { ClassBalanceManager.configKey(it) == key }
    }

    private fun Player.openConfigView(inventory: Inventory, category: ConfigCategory?) {
        if (PlayerTagManager.hasFlag(this, PlayerFlag.OPEN_CONFIG_INVENTORY)) {
            PlayerTagManager.addFlag(this, PlayerFlag.OPENING_CONFIG_INVENTORY)
        }
        PlayerTagManager.removeValue(this, PlayerTagValue.CONFIG_CATEGORY)
        category?.let { PlayerTagManager.setValue(this, PlayerTagValue.CONFIG_CATEGORY, it.name) }
        PlayerTagManager.addFlag(this, PlayerFlag.OPEN_CONFIG_INVENTORY)
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
            PlayerFlag.OPEN_GAME_MODE_INVENTORY,
            PlayerFlag.OPEN_CONFIG_INVENTORY,
            PlayerFlag.OPENING_CONFIG_INVENTORY,
            PlayerFlag.OPEN_CLASS_LIST_INVENTORY,
            PlayerFlag.OPEN_TRAINING_CLASS_LIST_INVENTORY,
            PlayerFlag.OPEN_CLASS_STATUS_INVENTORY,
            PlayerFlag.OPENING_CLASS_STATUS_INVENTORY,
        ).forEach { PlayerTagManager.removeFlag(this, it) }
        PlayerTagManager.removeValue(this, PlayerTagValue.CONFIG_CATEGORY)
        PlayerTagManager.removeValue(this, PlayerTagValue.CLASS_LIST_PAGE)
        PlayerTagManager.removeValue(this, PlayerTagValue.CLASS_STATUS_RETURN)
        closeInventory()
        PlayerTagManager.addFlag(this, PlayerFlag.OPEN_GAME_MODE_INVENTORY)
        openInventory(inventory)
    }

    fun getMatchModeFromItem(item: ItemStack): MatchMode? {
        val modeName = item.itemMeta.persistentDataContainer
            .get(matchModeKey, PersistentDataType.STRING) ?: return null
        return MatchMode.entries.find { it.name == modeName }
    }

    fun getOpenConfigCategory(player: Player): ConfigCategory? =
        PlayerTagManager.getValue(player, PlayerTagValue.CONFIG_CATEGORY)
            ?.let { name -> ConfigCategory.entries.find { it.name == name } }

    fun getClassFromItem(item: ItemStack): GameClass? {
        val classId = item.itemMeta.persistentDataContainer
            .get(classIdKey, PersistentDataType.STRING) ?: return null
        return gameClassList.find { it.javaClass.name == classId }
    }

    fun Player.openClassStatusInventory(gameClass: GameClass) {
        val inventory = Bukkit.createInventory(null, 27, miniMessage.deserialize(UtilManager.applyKeywords(gameClass.name)))
        inventory.setItem(0, gameClass.weapon.toItemStack(this))
        for (i in 0..gameClass.skills.size) {
            val skill = gameClass.skills.getOrNull(i) ?: break
            inventory.setItem(i + 1, createFullDescriptionItem(
                this,
                skillDyeMaterial(i),
                skill.name,
                skill.description,
                skill.briefDescription,
                ItemDescriptionManager.cooldownLines(skill.cooldown),
            ))
        }
        for (i in gameClass.skills.size + 1..gameClass.passives.size + gameClass.skills.size + 1) {
            val passive = gameClass.passives.getOrNull(i - gameClass.skills.size - 1) ?: break
            val material = Material.WHITE_DYE
            inventory.setItem(i, createFullDescriptionItem(
                this, material, passive.name, passive.description, passive.briefDescription,
            ))
        }
        openInventory(inventory)
    }

    private fun buildClassListInventory(page: Int, classList: List<GameClass?>, viewer: Player): Inventory? {
        val totalPages = (classList.size + 18 - 1) / 18
        if (page !in 0..<totalPages) return null
        val inventory = Bukkit.createInventory(null, 27, miniMessage.deserialize("클래스 목록 (페이지 ${page + 1}/${totalPages})"))
        val startIdx = page * 18
        val endIdx = minOf(startIdx + 18, classList.size)

        fillWithNullItems(inventory)

        for (i in startIdx until endIdx) {
            val gameClass = classList[i] ?: continue
            inventory.setItem(i - startIdx, createClassItem(gameClass, viewer))
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
        viewer: Player,
        material: Material,
        name: String,
        details: List<String>,
        briefDetails: List<String>,
        alwaysVisibleLines: List<String> = emptyList(),
    ): ItemStack = ItemDescriptionManager.applyForPlayer(
        ItemStack(material).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize(UtilManager.applyKeywords(name)))
            }
        },
        viewer,
        details,
        briefDetails,
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

    private fun createMultiplierSettingItem(
        material: Material,
        name: String,
        value: Double,
        extraLines: List<String> = emptyList(),
    ): ItemStack = ItemStack(material).apply {
        itemMeta = itemMeta.apply {
            displayName(miniMessage.deserialize("<yellow><bold>$name"))
            lore((listOf(
                ItemDescriptionManager.renderLoreLine("<gray>현재 값: <white><bold>${"%.1f".format(value)}배"),
                ItemDescriptionManager.renderLoreLine(""),
            ) + extraLines.map(ItemDescriptionManager::renderLoreLine) + listOf(
                ItemDescriptionManager.renderLoreLine(""),
                ItemDescriptionManager.renderLoreLine("<green>좌클릭: +0.1배 <red>우클릭: -0.1배"),
                ItemDescriptionManager.renderLoreLine("<gray>Shift 클릭: 1.0배씩 조절"),
            )))
        }
    }

    private fun createClassBalanceSettingItem(
        material: Material,
        field: ClassBalanceField,
        modifiers: ClassBalanceModifiers,
    ): ItemStack {
        val name = when (field) {
            ClassBalanceField.OVERALL -> "전체 효과 수치"
            ClassBalanceField.DAMAGE -> "피해량"
            ClassBalanceField.HEALING -> "회복량"
            ClassBalanceField.RANGE -> "스킬 사거리·범위"
            ClassBalanceField.STATUS_DURATION -> "상태이상 지속시간"
            ClassBalanceField.STATUS_POWER -> "상태이상 수치"
            ClassBalanceField.COOLDOWN_FLOW -> "재사용 대기시간 흐름"
        }
        val extraLines = buildList {
            if (field == ClassBalanceField.OVERALL) {
                add("<gray>피해·회복·사거리·상태이상 배율에 추가로 곱해집니다.")
            }
            if (field != ClassBalanceField.OVERALL && field != ClassBalanceField.COOLDOWN_FLOW) {
                add("<gray>전체 효과 배율 적용 후: <aqua><bold>${"%.1f".format(modifiers.effective(field))}배")
            }
            if (field == ClassBalanceField.COOLDOWN_FLOW) {
                add("<gray>전역 재사용 대기시간 흐름 배율과 곱해집니다.")
            }
        }
        return createMultiplierSettingItem(material, name, modifiers.value(field), extraLines)
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

    private fun createClassItem(gameClass: GameClass, viewer: Player): ItemStack {
        val name = miniMessage.deserialize(UtilManager.applyKeywords(gameClass.name))
        val loreLines = buildList {
            add(ItemDescriptionManager.renderLoreLine("<gray>랭크: ${gameClass.rank.formattedName}"))
            add(ItemDescriptionManager.renderLoreLine(
                if (PlayerPreferenceManager.usesDetailedDescriptions(viewer)) {
                    "<gray>설명 표시: <green>상세"
                } else {
                    "<gray>설명 표시: <yellow>간략"
                }
            ))
            if (gameClass.skills.isNotEmpty()) {
                add(ItemDescriptionManager.renderLoreLine(""))
                add(ItemDescriptionManager.renderLoreLine(
                    "<red><bold>스킬</bold><gray>: ${gameClass.skills.joinToString("<gray>, ") { it.name }}"
                ))
            }
            if (gameClass.passives.isNotEmpty()) {
                add(ItemDescriptionManager.renderLoreLine(
                    "<white><bold>패시브</bold><gray>: ${gameClass.passives.joinToString("<gray>, ") { it.name }}"
                ))
            }
        }
        return ItemStack(gameClass.classItemMaterial, 1).apply {
            itemMeta = itemMeta.apply {
                displayName(name)
                lore(loreLines)
                persistentDataContainer.set(classIdKey, PersistentDataType.STRING, gameClass.javaClass.name)
            }
            hideVanillaClassIconTooltip()
        }
    }

    /** Keeps the class name/lore while hiding material-provided lines such as damage and record information. */
    private fun ItemStack.hideVanillaClassIconTooltip() {
        val hiddenComponents = dataTypes
            .filterNotTo(linkedSetOf()) { it in classIconVisibleTooltipComponents }
            .apply { addAll(classIconImplicitTooltipComponents) }
        setData(
            DataComponentTypes.TOOLTIP_DISPLAY,
            TooltipDisplay.tooltipDisplay().hiddenComponents(hiddenComponents),
        )
    }
}
