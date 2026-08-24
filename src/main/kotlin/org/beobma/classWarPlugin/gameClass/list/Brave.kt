package org.beobma.classWarPlugin.gameClass.list

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.DisplayOrientationUtil
import org.beobma.classWarPlugin.util.PlayerNavigation
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.sin

private const val BRAVE_PULL_REQUIRED_CLICKS = 12
private const val BRAVE_PULL_COMBO_TICKS = 14L
private const val BRAVE_PULL_REACH = 2.8
private const val BRAVE_BURN_DURATION_SECONDS = 4
private const val BRAVE_BURN_DAMAGE_RATIO = 0.008
private const val BRAVE_CRITICAL_MULTIPLIER = 1.35
private const val BRAVE_SITE_SEARCH_LIMIT = 30_000
private const val BRAVE_MINIMUM_SITE_DISTANCE_SQUARED = 100.0
private const val BRAVE_BASIC_ATTACK_MULTIPLIER = 0.6

class Brave : GameClass(), GameStatusHandler, GameEndHandler, PlayerDeathHandler, OnHitHandler {
    override val name = "<gray>용사"
    override val rank = Rank.S
    override val classItemMaterial = Material.COPPER_SWORD
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(ChosenSwordPassive())

    private val collectedVariants = mutableSetOf<SwordVariant>()
    private val swordSites = mutableListOf<SwordSite>()
    private var swordVisualTask: BukkitTask? = null
    private val burns = mutableMapOf<UUID, BurnRecord>()

    override fun onBattleStart() {
        cleanupSite(removeRegistration = false)
        clearBurns()
        collectedVariants.clear()
        activeBraves[player.uniqueId] = this
        spawnSwordSites()
        player.sendMiniMessage("<gold><bold>[선택 받은 검]</bold> <gray>다섯 전설검이 월드보더 곳곳에서 당신을 기다립니다.")
        sounds.playTo(player, Sound.BLOCK_BEACON_ACTIVATE, volume = 0.55f, pitch = 1.45f)
    }

    override fun onGameTimePasses() = Unit

    override fun onGameEnd() {
        cleanupSite(removeRegistration = true)
        clearBurns()
    }

    override fun onPlayerDeath() {
        cleanupSite(removeRegistration = true)
        clearBurns()
    }

    override fun onAttackHit(context: DamageContext) {
        if (context.path != DamagePath.BASIC_ATTACK) return
        val variant = heldVariant() ?: return
        // DamageManager의 공통 기본 공격 0.6배 이후에도 설명의 추가 피해량이
        // 그대로 남도록 역보정한다.
        context.addBaseDamage(variant.bonusDamage / BRAVE_BASIC_ATTACK_MULTIPLIER)

        if (variant == SwordVariant.INFINITY_EDGE && isVanillaCriticalAttack()) {
            context.addDamageDealtMultiplier(BRAVE_CRITICAL_MULTIPLIER)
            particles.spawn(context.target.entity, Particle.CRIT, count = 18, spread = 0.48, speed = 0.12)
            sounds.play(context.target.entity, Sound.ENTITY_PLAYER_ATTACK_CRIT, volume = 0.75f, pitch = 0.92f)
        }
        if (variant == SwordVariant.DAINSLEIF_CRIMSON) applyBurn(context.target)
    }

    private fun heldVariant(): SwordVariant? {
        val raw = player.inventory.itemInMainHand.itemMeta?.persistentDataContainer
            ?.get(swordVariantKey, PersistentDataType.INTEGER) ?: return null
        return SwordVariant.entries.getOrNull(raw)
    }

    private fun isVanillaCriticalAttack(): Boolean =
        player.fallDistance > 0.0f && player.velocity.y < 0.0 &&
            !player.isInWater && !player.isInsideVehicle && player.attackCooldown >= 0.9f

    private fun spawnSwordSites() {
        val locations = findSwordLocations(SwordVariant.entries.size)
        SwordVariant.entries.forEachIndexed { index, variant ->
            val location = locations[index]
            val display = location.world.spawn(location.clone().add(0.0, 0.48, 0.0), ItemDisplay::class.java).apply {
                setItemStack(createLegendarySword(variant))
                itemDisplayTransform = ItemDisplay.ItemDisplayTransform.NONE
                billboard = Display.Billboard.FIXED
                brightness = Display.Brightness(15, 15)
                interpolationDuration = 2
                teleportDuration = 2
                isPersistent = false
            }
            DisplayOrientationUtil.alignSwordBladeVertically(display, Vector(0.22, -1.0, 0.13), 1.45f)
            TemporaryDisplayManager.mark(display, player.uniqueId)
            swordSites += SwordSite(variant, location.clone(), display)
        }

        swordVisualTask = playerData.trackTask(object : BukkitRunnable() {
            var tick = 0

            override fun run() {
                if (!player.isOnline || playerStatus.isDead || swordSites.isEmpty()) {
                    swordSites.forEach { it.display.remove() }
                    cancel()
                    return
                }
                swordSites.toList().forEachIndexed { index, site ->
                    if (!site.display.isValid) {
                        swordSites.remove(site)
                        return@forEachIndexed
                    }
                    if (tick % 8 == 0) {
                        val center = site.location.clone().add(0.0, 0.78, 0.0)
                        particles.circle(center, Particle.ENCHANT, 0.72, 12)
                        particles.spawn(
                            center,
                            Particle.DUST,
                            Particle.DustOptions(site.variant.color, 1.25f),
                            ParticleOptions.spread(5, 0.38, 0.015),
                        )
                        particles.spawn(center, Particle.END_ROD, count = 1, spread = 0.22, speed = 0.015)
                    }
                    if ((tick + index * 16) % 80 == 0) {
                        sounds.play(
                            site.location,
                            Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                            volume = 0.6f,
                            pitch = 0.72f + site.variant.ordinal * 0.08f,
                        )
                    }
                    val lift = site.pullProgress.toDouble() / BRAVE_PULL_REQUIRED_CLICKS * 0.42
                    site.display.teleport(site.location.clone().add(0.0, 0.48 + lift + sin(tick * 0.1 + index) * 0.018, 0.0).apply {
                        yaw = 0.0f
                        pitch = 0.0f
                    })
                }
                tick += 2
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }

    private fun findSwordLocations(count: Int): List<Location> {
        val world = player.world
        val border = world.worldBorder
        val margin = 1.25
        val half = (border.size * 0.5 - margin).coerceAtLeast(0.0)
        val bounds = PlayerNavigation.Bounds(
            border.center.x - half,
            border.center.x + half,
            border.center.z - half,
            border.center.z + half,
        )
        val start = PlayerNavigation.nearestNode(world, player.location)
            ?: PlayerNavigation.surfaceNode(world, player.location.blockX, player.location.blockZ)
        if (start == null) return List(count) { player.location.clone() }

        val allCandidates = PlayerNavigation.collectReachable(world, start, bounds, BRAVE_SITE_SEARCH_LIMIT)
            .asSequence()
            .filter { PlayerNavigation.surfaceNode(world, it.x, it.z) == it }
            .map { PlayerNavigation.displayLocation(world, it) }
            .filter(border::isInside)
            .distinctBy { Triple(it.blockX, it.blockY, it.blockZ) }
            .shuffled()
            .toList()

        val preferred = allCandidates.filter { it.distanceSquared(player.location) >= 36.0 }
        val pool = if (preferred.size >= count) preferred else allCandidates
        val selected = mutableListOf<Location>()
        pool.forEach { candidate ->
            if (selected.size >= count) return@forEach
            if (selected.all { it.distanceSquared(candidate) >= BRAVE_MINIMUM_SITE_DISTANCE_SQUARED }) {
                selected += candidate
            }
        }
        pool.forEach { candidate ->
            if (selected.size >= count) return@forEach
            if (selected.none { it.blockX == candidate.blockX && it.blockY == candidate.blockY && it.blockZ == candidate.blockZ }) {
                selected += candidate
            }
        }

        val fallback = PlayerNavigation.displayLocation(world, start)
        while (selected.size < count) selected += fallback.clone()
        return selected.take(count).map { it.clone().apply { yaw = 0.0f; pitch = 0.0f } }
    }

    private fun findPullSite(clickedEntity: Entity?): SwordSite? {
        if (clickedEntity != null) {
            return swordSites.firstOrNull { it.display.uniqueId == clickedEntity.uniqueId && it.display.isValid }
        }
        return swordSites.asSequence()
            .filter { it.display.isValid && player.world == it.location.world }
            .filter { player.boundingBox.expand(BRAVE_PULL_REACH).contains(it.location.toVector()) }
            .minByOrNull { it.location.distanceSquared(player.location) }
    }

    private fun attemptPull(clickedEntity: Entity? = null): Boolean {
        val site = findPullSite(clickedEntity) ?: return false
        val base = site.location
        if (player.world != base.world || !player.boundingBox.expand(BRAVE_PULL_REACH).contains(base.toVector())) return false

        val now = player.world.fullTime
        swordSites.filter { it !== site }.forEach { it.pullProgress = 0 }
        if (now - site.lastPullTick > BRAVE_PULL_COMBO_TICKS) {
            site.pullProgress = (site.pullProgress - 3).coerceAtLeast(0)
        }
        site.lastPullTick = now
        site.pullProgress = (site.pullProgress + 1).coerceAtMost(BRAVE_PULL_REQUIRED_CLICKS)

        val ratio = site.pullProgress.toDouble() / BRAVE_PULL_REQUIRED_CLICKS
        val filled = (ratio * 12.0).toInt().coerceIn(0, 12)
        val bar = "<green>${"■".repeat(filled)}<dark_gray>${"■".repeat(12 - filled)}"
        player.sendActionBar(MiniMessage.miniMessage().deserialize("<gold><bold>${site.variant.label}</bold> $bar"))
        particles.spawn(base.clone().add(0.0, 0.8, 0.0), Particle.CRIT, count = 5 + site.pullProgress / 2, spread = 0.28, speed = 0.055)
        sounds.play(base, Sound.ITEM_ARMOR_EQUIP_IRON, volume = 0.48f, pitch = 0.68f + ratio.toFloat() * 0.75f)

        if (site.pullProgress >= BRAVE_PULL_REQUIRED_CLICKS) claimSword(site)
        return true
    }

    private fun claimSword(site: SwordSite) {
        if (!swordSites.remove(site)) return
        val variant = site.variant
        val isFirstLegendarySword = collectedVariants.isEmpty()
        collectedVariants += variant
        val center = site.location.clone().add(0.0, 0.95, 0.0)
        site.display.remove()

        val weaponSlot = if (playerData.gameClasses.indexOf(this) == 1) 8 else 0
        val sword = createLegendarySword(variant)
        if (isFirstLegendarySword) {
            player.inventory.setItem(weaponSlot, sword)
            player.inventory.heldItemSlot = weaponSlot
        } else {
            player.inventory.addItem(sword).values.forEach { leftover ->
                player.world.dropItemNaturally(player.location, leftover).apply {
                    owner = player.uniqueId
                    pickupDelay = 0
                }
            }
        }
        particles.spawn(center, Particle.FLASH, count = 2)
        particles.spawn(center, Particle.TOTEM_OF_UNDYING, count = 75, spread = 0.9, speed = 0.18)
        particles.spawn(center, Particle.END_ROD, count = 40, spread = 0.7, speed = 0.12)
        sounds.play(center, Sound.ITEM_TRIDENT_THUNDER, volume = 0.75f, pitch = 1.35f)
        sounds.play(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, volume = 0.9f, pitch = 1.1f)
        player.sendMiniMessage(
            "<gold><bold>[선택 받은 검]</bold> <white>${variant.label}</white><gray>을(를) 획득했습니다. " +
                "<dark_gray>[${collectedVariants.size}/${SwordVariant.entries.size}]",
        )
        if (swordSites.isEmpty()) {
            swordVisualTask?.cancel()
            swordVisualTask = null
            activeBraves.remove(player.uniqueId, this)
            player.sendMiniMessage("<gold><bold>모든 전설검을 획득했습니다!</bold>")
        }
    }

    private fun createLegendarySword(variant: SwordVariant): ItemStack = ItemStack(Material.IRON_SWORD).apply {
        itemMeta = itemMeta.apply {
            displayName(MiniMessage.miniMessage().deserialize("<white><italic:false> "))
            lore(variant.lore.map { MiniMessage.miniMessage().deserialize("<italic:false>$it") })
            isUnbreakable = true
            addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE)
            persistentDataContainer.set(swordVariantKey, PersistentDataType.INTEGER, variant.ordinal)
        }
    }

    private fun applyBurn(target: EntityData) {
        val now = target.entity.world.fullTime
        val existing = burns[target.entity.uniqueId]
        val status = target.getOrCreateStatus(playerData) { BurningPainStatus() }
        if (existing != null) {
            existing.stacks = (existing.stacks + 1).coerceAtMost(4)
            existing.expiresAtTick = now + BRAVE_BURN_DURATION_SECONDS * 20L
            status.applyStatus(duration = BRAVE_BURN_DURATION_SECONDS, powerSet = existing.stacks)
            return
        }

        val record = BurnRecord(target, status, stacks = 1, expiresAtTick = now + BRAVE_BURN_DURATION_SECONDS * 20L)
        burns[target.entity.uniqueId] = record
        status.applyStatus(duration = BRAVE_BURN_DURATION_SECONDS, powerSet = 1)
        record.task = playerData.trackTask(object : BukkitRunnable() {
            override fun run() {
                val active = burns[target.entity.uniqueId]
                val living = target.entity as? LivingEntity
                if (active !== record || living == null || !living.isValid || living.isDead || target.entityStatus.isDead) {
                    burns.remove(target.entity.uniqueId, record)
                    cancel()
                    return
                }

                val currentTick = living.world.fullTime
                if (currentTick > record.expiresAtTick) {
                    burns.remove(target.entity.uniqueId, record)
                    cancel()
                    return
                }
                val maximumHealth = living.getAttribute(Attribute.MAX_HEALTH)?.value ?: living.health
                target.damage(
                    maximumHealth * BRAVE_BURN_DAMAGE_RATIO * record.stacks,
                    DamageType.StatusAbnormality,
                    playerData,
                    damagePath = DamagePath.STATUS_EFFECT,
                )
                particles.spawn(living, Particle.FLAME, count = 5 + record.stacks * 3, spread = 0.38, speed = 0.045)
                particles.spawn(living, Particle.SMOKE, count = 2 + record.stacks, spread = 0.3, speed = 0.02)
                sounds.play(living, Sound.ITEM_FIRECHARGE_USE, volume = 0.28f, pitch = 1.35f)

                if (currentTick >= record.expiresAtTick) {
                    burns.remove(target.entity.uniqueId, record)
                    cancel()
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 20L, 20L))
    }

    private fun clearBurns() {
        burns.values.toList().forEach { record ->
            record.task?.cancel()
            if (record.status in record.target.statusAbnormalitys) record.status.remove()
        }
        burns.clear()
    }

    private fun cleanupSite(removeRegistration: Boolean) {
        swordVisualTask?.cancel()
        swordVisualTask = null
        swordSites.forEach { it.display.remove() }
        swordSites.clear()
        if (removeRegistration) activeBraves.remove(player.uniqueId, this)
    }

    private data class SwordSite(
        val variant: SwordVariant,
        val location: Location,
        val display: ItemDisplay,
        var pullProgress: Int = 0,
        var lastPullTick: Long = Long.MIN_VALUE,
    )

    private data class BurnRecord(
        val target: EntityData,
        val status: BurningPainStatus,
        var stacks: Int,
        var expiresAtTick: Long,
        var task: BukkitTask? = null,
    )

    private class ChosenSwordPassive : BasePassive() {
        override val name = "<bold>선택 받은 검"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>게임 시작 시 구현된 모든 전설검이 월드보더 내부의 서로 다른 무작위 위치에 꽂힌다.",
            "<gray>각 검 가까이에서 우클릭을 연타하면 해당 검을 획득해 고유 효과를 사용할 수 있다.",
            "<gray>검을 획득해도 나머지 전설검은 사라지지 않으며, 모든 검을 계속 획득할 수 있다.",
        )
    }

    private enum class SwordVariant(
        val label: String,
        val bonusDamage: Double,
        val color: Color,
        val lore: List<String>,
    ) {
        ARCANE_SHADE("아케인셰이드 투핸드소드 (+9)", 6.0, Color.fromRGB(77, 224, 126), arcaneShadeLore()),
        INFINITY_EDGE("무한의 대검", 2.0, Color.fromRGB(95, 155, 255), infinityEdgeLore()),
        DAINSLEIF_CRIMSON("다인슬라이프-진홍", 2.0, Color.fromRGB(180, 20, 35), dainsleifLore()),
        OATH("맹세", 1.0, Color.fromRGB(255, 214, 72), oathLore()),
        ZENITH("제니스", 2.0, Color.fromRGB(255, 75, 75), zenithLore()),
    }

    companion object {
        private val activeBraves = mutableMapOf<UUID, Brave>()
        private val swordVariantKey: NamespacedKey
            get() = NamespacedKey(ClassWarPlugin.instance, "brave-sword-variant")

        fun handlePullInteract(event: PlayerInteractEvent): Boolean {
            if (event.hand != EquipmentSlot.HAND) return false
            if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return false
            val brave = activeBraves[event.player.uniqueId] ?: return false
            if (!brave.attemptPull()) return false
            event.isCancelled = true
            return true
        }

        fun handlePullInteract(player: Player, clickedEntity: Entity): Boolean {
            val brave = activeBraves[player.uniqueId] ?: return false
            return brave.attemptPull(clickedEntity)
        }
    }
}

private class BurningPainStatus : StatusAbnormality() {
    override val name = "<dark_red><bold>타오르는 고통</bold><gray>"
    override val description = listOf(
        "<gray>매초 중첩당 최대 체력의 <dark_red>0.8%</dark_red>만큼 고정 피해를 입는다.",
        "<gray>최대 4회 중첩된다.",
    )
    override val canRemove = true
    override var power = 1
    override var maxPower: Int? = 4
    override var duration: Int? = BRAVE_BURN_DURATION_SECONDS
}

private fun arcaneShadeLore(): List<String> = listOf(
    "<yellow>   ★★★★★ ★★★★★ ★★★★★",
    "<yellow>   ★★★★★ ★★<gray>☆☆☆ ☆☆☆☆☆",
    "<green><bold>          위대한 루시드의",
    "<red><bold>     아케인셰이드 투핸드소드 (+9)",
    "<white>             (레전드리 아이템)",
    "<gold>                 교환 불가",
    "<dark_gray>                                공격력 증가량",
    "<white>                                +120892913",
    "<gold>⦁ REQ LEV : 200",
    "<white>⦁ REQ STR : 600<dark_gray>  ⦁ REQ LUK : 000",
    "<dark_gray>⦁ REQ DEX : 000  ⦁ REQ INT : 000",
    "<dark_gray>  초보자  <white>전사  <dark_gray>마법사  궁수  도적  해적",
    "<dark_gray>----------------------",
    "<white>무기 분류 : 두손검 (두손무기)",
    "<white>공격속도 : 보통",
    "<aqua>최대 HP : +20<white>(0 <aqua>+20<white>)",
    "<aqua>공격력 : +295<white> (0<green> +95<white> +100)",
    "<aqua>마력 : +78<white>  (100<green> +95<white> +100)",
    "<aqua>방어력 : +5<white> (0<green> +5<white>)",
    "<white>업그레이드 가능 횟수 : 0<gold>(복구 가능 횟수: 0)",
    "<white>황금 망치 제련 적용",
    "<gold>가위 사용 가능 횟수 : 9회",
    "<dark_gray>----------------------",
    "<green>L 잠재옵션",
    "<white>공격력 : +12%",
    "<white>공격력 : +12%",
    "<white>보스 몬스터 공격 시 데미지 : +40%",
    "<dark_gray>----------------------",
    "<green>L 에디셔널 잠재옵션",
    "<white>+ 공격력 : +12%",
    "<white>+ 몬스터 방어율 무시 : +4%",
    "<white>+ 공격력 : +9%",
    "<dark_gray>----------------------",
    "<gold>장착 시 1회에 한해 매력 200의 경험치를 얻을",
    "<gold>수 있습니다.(일일제한, 최대치 초과 시 제외)",
)

private fun infinityEdgeLore(): List<String> = listOf(
    "<white><bold>                                        무한의 대검",
    "<gold>                                                    💰  <white>3400",
    " ",
    "<blue>  공격력 70",
    "<blue>  치명타 확률 20%",
    "<blue>  치명타 피해량 35%",
    " ",
    "<white>  신화급 기본 지속 효과: 다른 모든 전설급 아이템에 공격력 5.  ",
    " ",
    "<aqua>  슬롯에 추가하려면 끌어다 넣으세요",
    "<aqua>  표시된 슬롯에 추가하려면 오른쪽 클해 주세요",
    "<aqua>  상/하위 아이템을 보시려면 왼쪽 클릭해 주세요",
)

private fun dainsleifLore(): List<String> = listOf(
    "<dark_red><bold>ㅣ<white><bold:false>다인슬라이프-진홍",
    "<dark_red><bold>ㅣ<white><bold:false>초월",
    "<dark_red><bold>ㅣ<gray><bold:false>양손검",
    " ",
    "<dark_gray>----------------------",
    "<dark_green>  공격력 +73",
    "<dark_green>  공격 속도 +40%",
    "<dark_green>  방어 관통 +7%",
    " ",
    "<dark_gray>----------------------",
    "<dark_purple><bold>ㅣ<white><bold:false>고유 장착 효과",
    "<yellow>  발화",
    "<gray>  기본 공격의 대상에게 최대 4회 중첩되는",
    "<gray>  [타오르는 고통] 효과를 4초 동안 부여합니다.",
    "<gray>  [타오르는 고통]은 매 초마다 <dark_green>대상 최대 체력의",
    "<dark_green>  0.8%<gray>를 고정 피해로 입힙니다.",
    " ",
    "<dark_gray>----------------------",
    "<gray>좌클릭으로 착용 가능",
)

private fun oathLore(): List<String> = listOf(
    "<dark_gray>----------------------",
    " ",
    "<yellow>          맹세<white>             🗡",
    " ",
    "<dark_gray>----------------------",
    " ",
    "<black>  ■■■■<green>+2<black>■■■■  ",
    "<black>  ■■■■<green>+1<black>■■■■  ",
    "<black>  ■■<green>+1<white>🗡<green>+1<black>■■ ",
    "<black>  ■■■■<green>+1<black>■■■■  ",
    " ",
    "<dark_gray>----------------------",
    " ",
    " ",
    "<dark_gray>----------------------",
    "<green>    +<white>        아티팩트 레벨 증가        ",
    "<dark_gray>----------------------",
)

private fun zenithLore(): List<String> = listOf(
    "<red>제니스",
    "<white>271 근접 공격력",
    "<white>46% 치명타 확률",
    "<white>보통 속도",
    "<white>강한 밀쳐내기",
    "<green>+15% 공격력",
    "<green>+10% 속도",
    "<green>+5% 치명타 확률",
    "<green>+10% 크기",
    "<green>+15% 넉백",
)
