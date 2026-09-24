package org.beobma.classWarPlugin.gameClass.streamer

import net.kyori.adventure.text.Component
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.effect.ParticleApi
import org.beobma.classWarPlugin.effect.SoundApi
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.updateStatusActionBar
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.*
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.*
import org.bukkit.inventory.*
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionType
import org.bukkit.scoreboard.*
import org.bukkit.util.Vector
import kotlin.random.Random

class BroadcastRuntime(private val scope: AbilityScope) : Listener {
    companion object {
        // Unlink out-of-order copied abilities instead of restoring a retired private scoreboard.
        private val visibleBroadcasts = mutableSetOf<BroadcastRuntime>()
    }
    private val owner get() = scope.playerData
    private val player get() = owner.player
    private val game get() = scope.game
    private var viewers = BroadcastRules.INITIAL_VIEWERS
    private var cheese = 0L
    private var lastExcitement = -100L
    private var seconds = 0
    private val chat = ArrayDeque<String>()
    private lateinit var board: Scoreboard
    private var previous: Scoreboard? = null
    private var menu: Inventory? = null
    private lateinit var balance: BroadcastStatus
    private val fallProtected = mutableSetOf<java.util.UUID>()
    private var donationEffect = false
    val bonus get() = BroadcastRules.bonus(viewers)

    private data class Product(val material: Material, val price: Int, val title: String, val potion: PotionType? = null)
    private val products = listOf(
        Product(Material.IRON_SWORD, 1000, "철 검"), Product(Material.BOW, 2000, "활"),
        Product(Material.ARROW, 200, "화살 16개"), Product(Material.DIAMOND_SWORD, 15000, "다이아몬드 검"),
        Product(Material.IRON_HELMET, 3000, "철 투구"), Product(Material.IRON_CHESTPLATE, 5000, "철 흉갑"),
        Product(Material.IRON_LEGGINGS, 4000, "철 각반"), Product(Material.IRON_BOOTS, 2500, "철 장화"),
        Product(Material.POTION, 3000, "치유 물약", PotionType.HEALING),
        Product(Material.POTION, 5000, "신속 물약", PotionType.SWIFTNESS),
        Product(Material.GOLDEN_APPLE, 10000, "황금 사과"), Product(Material.TOTEM_OF_UNDYING, 100000, "불사의 토템"),
    )

    fun start() {
        board = Bukkit.getScoreboardManager().newScoreboard
        board.registerNewObjective("cw_stream", Criteria.DUMMY, Component.text("§a● 가상 방송" )).displaySlot = DisplaySlot.SIDEBAR
        board.registerNewTeam("hidden_names").apply {
            setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
            game.playerDatas.filterIsInstance<PlayerData>().forEach { addEntry(it.player.name) }
        }
        balance = BroadcastStatus { "<green>시청자</green> <white>$viewers</white> <gold>치즈</gold> <yellow>$cheese</yellow>" }
        owner.addStatus(balance, owner); balance.updatePower(1)
        Bukkit.getPluginManager().registerEvents(this, ClassWarPlugin.instance)
        scope.resources.own { hide(); HandlerList.unregisterAll(this); fallProtected.clear() }
        chat.addLast("가상 시청자들이 입장했다.")
        show(); render()
    }

    fun show() {
        if (!::board.isInitialized || scope.isClosed || !player.isOnline) return
        if (player.scoreboard !== board) { previous = player.scoreboard; player.scoreboard = board }
        visibleBroadcasts += this
    }
    fun hide() {
        menu?.let { if (player.openInventory.topInventory === it) player.closeInventory() }; menu = null
        if (::board.isInitialized) visibleBroadcasts.filter { it !== this && it.previous === board }.forEach { it.previous = previous }
        if (::board.isInitialized && player.scoreboard === board) previous?.let { player.scoreboard = it }
        visibleBroadcasts -= this
        previous = null
    }
    fun tick() {
        viewers = BroadcastRules.decay(viewers)
        if (++seconds % 4 == 0) say(BroadcastRules.idle.random())
        render()
    }
    fun excite(hurt: Boolean) {
        if (donationEffect || game.combatTick - lastExcitement < 20) return
        lastExcitement = game.combatTick
        viewers = BroadcastRules.excited(viewers)
        say((if (hurt) BroadcastRules.hurt else BroadcastRules.attack).random())
        if (Random.nextDouble() < BroadcastRules.chance(viewers)) {
            val amount = BroadcastRules.donation(viewers, Random.nextDouble())
            cheese = (cheese + amount).coerceAtMost(1_000_000_000L)
            say("치즈 ${amount}개 후원!", "후원 알림")
            SoundApi.play(player.location, Sound.BLOCK_NOTE_BLOCK_CHIME, volume = 0.5f, pitch = 1.4f)
            ParticleApi.spawn(player, Particle.HAPPY_VILLAGER, count = 14, spread = 0.5)
            val enemy = Targeting.select(owner, TargetType.Enemy).minByOrNull { it.entity.location.distanceSquared(player.location) }
            val target = if (enemy != null && Random.nextBoolean()) enemy else owner
            AbilityExecution.with(scope) { donate(target, amount) }
        }
        render()
    }
    private fun say(message: String, nickname: String? = null) {
        val name = nickname ?: if (Random.nextInt(10) == 0) game.playerDatas.filterIsInstance<PlayerData>().randomOrNull()?.player?.name ?: "관객"
            else BroadcastRules.names.random()
        chat.addLast("§7$name§f: $message")
        while (chat.size > 7) chat.removeFirst()
    }
    private fun render() {
        val objective = board.getObjective("cw_stream") ?: return
        board.entries.toList().forEach(board::resetScores)
        val lines = listOf("§a시청자 §f$viewers", "§6치즈 §e$cheese", "§8─ 가상 채팅 ─") + chat.toList()
        lines.forEachIndexed { i, line -> objective.getScore("§${i.toString(16)}$line").score = lines.size - i }
        owner.updateStatusActionBar()
    }

    fun openShop(): Boolean {
        if (owner.statusAbnormalitys.any { it is BroadcastEscrow }) return false
        menu = Bukkit.createInventory(null, 27, Component.text("치즈 상점 · $cheese")).also { inventory ->
            products.forEachIndexed { index, product ->
                inventory.setItem(index, item(product).apply { itemMeta = itemMeta.apply {
                    displayName(Component.text("${product.title} · ${product.price} 치즈"))
                    lore(listOf(Component.text("클릭하여 구매한다.")))
                } })
            }
            player.openInventory(inventory)
        }
        return true
    }
    private fun item(p: Product) = ItemStack(p.material, if (p.material == Material.ARROW) 16 else 1).apply {
        p.potion?.let { type -> itemMeta = (itemMeta as PotionMeta).apply { basePotionType = type } }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun click(e: InventoryClickEvent) {
        if (e.view.topInventory !== menu || menu == null) return
        e.isCancelled = true
        if (e.whoClicked.uniqueId != owner.uniqueId || !scope.isActive || scope.suspended || game.isPaused ||
            owner.entityStatus.isDead || !owner.entityStatus.canSkillUse) return
        if (e.click != ClickType.LEFT || owner.statusAbnormalitys.any { it is BroadcastEscrow }) return
        val product = products.getOrNull(e.rawSlot) ?: return
        if (cheese < product.price) { player.sendMessage("§c치즈가 부족하다."); return }
        // Require one empty storage slot, then give directly: never deduct for an overflow purchase.
        val slot = player.inventory.firstEmpty()
        if (slot !in 0..35) { player.sendMessage("§c인벤토리에 빈 공간이 필요하다."); return }
        cheese -= product.price
        player.inventory.setItem(slot, item(product)); render()
        SoundApi.play(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume = 0.5f, pitch = 1.2f)
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun drag(e: InventoryDragEvent) { if (menu != null && e.view.topInventory === menu) e.isCancelled = true }
    @EventHandler
    fun closed(e: InventoryCloseEvent) { if (e.inventory === menu) menu = null }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun fall(e: EntityDamageEvent) {
        if (e.cause == EntityDamageEvent.DamageCause.FALL && e.entity.uniqueId in fallProtected && scope.isActive) {
            fallProtected.remove(e.entity.uniqueId); e.isCancelled = true; e.entity.fallDistance = 0f
        }
    }

    private fun donate(target: EntityData, amount: Int) {
        when (BroadcastRules.tier(amount)) {
            1000 -> anvil(target)
            3000 -> (target.entity as? Player)?.inventory?.let { inv ->
                if (target.statusAbnormalitys.any { it is BroadcastEscrow }) return
                val shuffled = (0..8).map { inv.getItem(it)?.clone() }.shuffled()
                shuffled.forEachIndexed { i, stack -> inv.setItem(i, stack) }
                SoundApi.play(target.entity.location, Sound.ITEM_BUNDLE_INSERT, volume = 0.6f)
            }
            5000 -> BroadcastEscrow.hide(target, scope, false)
            10000 -> tnt(target.entity.location)
            30000 -> {
                target.getOrCreateStatus(owner) { AttackSpeedIncrease() }.applyStatus(duration = 5, powerSet = 50)
                target.getOrCreateStatus(owner) { MoveSpeedIncrease() }.applyStatus(duration = 5, powerSet = 50)
                ParticleApi.spawn(target.entity, Particle.ELECTRIC_SPARK, count = 24, spread = 0.8)
            }
            50000 -> {
                fallProtected += target.entity.uniqueId
                target.entity.velocity = target.entity.velocity.apply { y = 2.2 }
                ParticleApi.spawn(target.entity, Particle.CLOUD, count = 20, spread = 0.5)
                SoundApi.play(target.entity.location, Sound.ENTITY_BREEZE_WIND_BURST, volume = 0.7f)
            }
            100000 -> BroadcastEscrow.hide(target, scope, true)
            1000000 -> {
                val point = target.entity.location
                game.playerDatas.filterIsInstance<PlayerData>().filter { !it.entityStatus.isDead && it.player.isOnline }
                    .forEach { it.player.teleport(point); it.player.fallDistance = 0f }
                ParticleApi.spawn(point, Particle.PORTAL, count = 100, spread = 1.5)
                SoundApi.play(point, Sound.ENTITY_ENDERMAN_TELEPORT, volume = 1f, pitch = 0.6f)
            }
        }
    }

    private fun hit(target: EntityData, amount: Double) {
        donationEffect = true
        try { target.damage(amount, DamageType.Normal, owner, damagePath = DamagePath.SKILL) }
        finally { donationEffect = false }
    }
    private fun display(point: Location, material: Material) = point.world.spawn(point, BlockDisplay::class.java).also {
        it.block = material.createBlockData(); it.setGravity(false); it.teleportDuration = 1
        TemporaryDisplayManager.mark(it, owner.uniqueId)
    }
    private fun anvil(target: EntityData) {
        val start = target.entity.location.add(-0.5, 7.0, -0.5)
        val model = display(start, Material.ANVIL)
        object : AbilityRunnable(scope) {
            var landed = false; var age = 0; var speed = 0.1
            val hitIds = mutableSetOf<java.util.UUID>()
            override fun run() {
                if (++age > 100) { cancel(); return }
                if (landed) return
                speed = (speed + 0.08).coerceAtMost(1.4)
                val old = model.location; val next = old.clone().subtract(0.0, speed, 0.0)
                val ground = old.world.rayTraceBlocks(old.clone().add(0.5, 0.0, 0.5), Vector(0.0, -1.0, 0.0), speed, FluidCollisionMode.NEVER, true)
                if (ground != null) { next.y = ground.hitPosition.y; landed = true }
                val box = org.bukkit.util.BoundingBox(next.x, next.y, next.z, old.x + 1, old.y + 1, old.z + 1)
                Targeting.candidates(owner, old.world).filter { (it === owner || Targeting.isEnemy(owner, it)) && it.entity.boundingBox.overlaps(box) }
                    .forEach { if (hitIds.add(it.entity.uniqueId)) hit(it, 4.0) }
                model.teleport(next)
                ParticleApi.spawn(next, Particle.CRIT, count = 3, spread = 0.3)
                if (landed) SoundApi.play(next, Sound.BLOCK_ANVIL_LAND, volume = 0.6f, pitch = 1.3f)
            }
            override fun onCancel() { model.remove() }
        }.runTaskTimer(ClassWarPlugin.instance, 1, 1)
    }
    private fun tnt(point: Location) {
        val model = display(point.clone().add(-0.5, 0.0, -0.5), Material.TNT)
        SoundApi.play(point, Sound.ENTITY_TNT_PRIMED, volume = 0.7f)
        object : AbilityRunnable(scope) {
            var age = 0
            override fun run() {
                ParticleApi.spawn(point.clone().add(0.0, 1.0, 0.0), Particle.SMOKE, count = 2, spread = 0.15)
                if (++age < 40) return
                val p = point.toVector()
                Targeting.candidates(owner, point.world).filter { it === owner || Targeting.isEnemy(owner, it) }.forEach {
                    val b = it.entity.boundingBox
                    val near = Vector(p.x.coerceIn(b.minX, b.maxX), p.y.coerceIn(b.minY, b.maxY), p.z.coerceIn(b.minZ, b.maxZ))
                    if (near.distanceSquared(p) <= 16) hit(it, 6.0)
                }
                ParticleApi.spawn(point, Particle.EXPLOSION, count = 4, spread = 1.2)
                SoundApi.play(point, Sound.ENTITY_GENERIC_EXPLODE, volume = 0.8f, pitch = 1.1f)
                cancel()
            }
            override fun onCancel() { model.remove() }
        }.runTaskTimer(ClassWarPlugin.instance, 1, 1)
    }
}

private class BroadcastStatus(private val text: () -> String) : StatusAbnormality() {
    override val name = "<green>방송중"
    override val description = listOf("시청자 수와 사용할 수 있는 치즈를 표시한다.")
    override val canRemove = false
    override val isClassMechanic = true
    override fun actionBarText() = text()
}
