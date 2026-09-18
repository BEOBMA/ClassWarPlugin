package org.beobma.classWarPlugin.growth

import org.bukkit.Material

/** New equipment families reuse implemented effects, including their shared cooldowns. */
object GrowthArsenal {
    private data class Family(val id: String, val name: String, val material: Material, val prototype: String)
    private val families = listOf(
        Family("reaper-scythe", "사신의 낫", Material.NETHERITE_HOE, "dawn-edge"),
        Family("vampire-spear", "흡혈 창", Material.TRIDENT, "blood-fang"),
        Family("runic-axe", "각인 도끼", Material.DIAMOND_AXE, "spell-edge"),
        Family("sentinel-saber", "파수꾼의 군도", Material.IRON_SWORD, "duel-blade-onslaught"),
        Family("star-grimoire", "별의 마도서", Material.ENCHANTED_BOOK, "mage-rod-onslaught"),
        Family("plague-sickle", "역병의 곡도", Material.IRON_HOE, "curse-relic-onslaught"),
        Family("aegis-cuirass", "수호 흉갑", Material.DIAMOND_CHESTPLATE, "dusk-plate"),
        Family("rebirth-mantle", "윤회의 망토", Material.LEATHER_CHESTPLATE, "phoenix-coat"),
        Family("colossus-carapace", "거신의 외피", Material.NETHERITE_CHESTPLATE, "titan-mail"),
        Family("pilgrim-vest", "순례자의 조끼", Material.CHAINMAIL_CHESTPLATE, "unyielding-plate-onslaught"),
        Family("dawn-bastion", "여명 성벽갑", Material.GOLDEN_CHESTPLATE, "vanguard-plate-onslaught"),
        Family("moonweave-robe", "월광 직조 로브", Material.LEATHER_CHESTPLATE, "guardian-core"),
        Family("falcon-brooch", "매의 브로치", Material.FEATHER, "wind-charm"),
        Family("sovereign-coin", "군주의 주화", Material.GOLD_INGOT, "gold-dice"),
        Family("bounty-medal", "현상금 메달", Material.EMERALD, "hunter-eye"),
        Family("shadow-earring", "그림자 귀걸이", Material.INK_SAC, "ambush-seal-onslaught"),
        Family("behemoth-trophy", "거수의 전리품", Material.BONE, "slayer-badge-onslaught"),
        Family("chronos-ring", "시간의 반지", Material.CLOCK, "sage-stone"),
        Family("eternity-sandglass", "영겁의 모래시계", Material.CLOCK, "sage-stone"),
        Family("verdant-censer", "신록의 향로", Material.MOSS_BLOCK, "life-seed"),
        Family("eclipse-orb", "일식의 보옥", Material.ENDER_PEARL, "guardian-core"),
        Family("oath-tablet", "맹약의 석판", Material.ECHO_SHARD, "desperate-crystal-onslaught"),
        Family("abyss-idol", "심연의 우상", Material.CRYING_OBSIDIAN, "curse-relic-onslaught"),
        Family("blood-chalice", "피의 성배", Material.NAUTILUS_SHELL, "blood-fang"),
    )

    data class Build(val id: String, val name: String, val stats: Map<GrowthStat, Int>)
    val builds: List<Build> = buildList {
        val stats = GrowthStat.entries
        val names = listOf("강철", "질풍", "비전", "행운")
        for (primary in stats) for (secondary in stats.filter { it != primary }) {
            add(Build("${primary.name.lowercase()}-${secondary.name.lowercase()}",
                "${names[primary.ordinal]}·${names[secondary.ordinal]}", mapOf(primary to 9, secondary to 5)))
        }
        for (primary in stats) {
            add(Build("pure-${primary.name.lowercase()}", "순수한 ${names[primary.ordinal]}", mapOf(primary to 14)))
            add(Build("trinity-${primary.name.lowercase()}", "삼위의 ${names[primary.ordinal]}",
                mapOf(primary to 8, stats[(primary.ordinal + 1) % 4] to 3, stats[(primary.ordinal + 2) % 4] to 3)))
        }
        add(Build("balanced", "조화로운", stats.associateWith { if (it.ordinal < 2) 4 else 3 }))
    }

    fun create(prototypes: List<GrowthItem>): List<GrowthItem> = families.flatMapIndexed { index, family ->
        val prototype = prototypes.first { it.id == family.prototype }
        // Six families per slot; effects can now be selected in alternative equipment slots.
        val slot = GrowthSlot.entries[index / 6]
        builds.map { build -> prototype.copy(id = "${family.id}-${build.id}", name = "${build.name} ${family.name}",
            material = family.material, slot = slot, stats = build.stats, eventOnly = false, rarity = GrowthRarity.HEROIC) }
    }
}
