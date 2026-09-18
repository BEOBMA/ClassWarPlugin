package org.beobma.classWarPlugin.growth

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.MapTransferBorderManager
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.Locale
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/** Cosmetic entities are match-owned, non-persistent, and visible only with their wearer. */
class GrowthPresentation(private val runtime: GrowthModeRuntime) : AutoCloseable {
    private val plugin get() = ClassWarPlugin.instance
    private val mini = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
    private val ornaments = mutableMapOf<Pair<UUID, GrowthSlot>, ItemDisplay>()
    private val chestViewers = mutableMapOf<UUID, MutableSet<UUID>>()
    private val mobNames = mutableMapOf<UUID, String>()
    private var boundaries: RegionBoundaries? = null
    private var ticks = 0
    private val dust = mapOf(RegionState.SAFE to Color.fromRGB(60, 225, 255),
        RegionState.WARNING to Color.fromRGB(255, 215, 35), RegionState.FORBIDDEN to Color.fromRGB(245, 45, 55))
        .mapValues { Particle.DustOptions(it.value, 1.2f) }

    fun ownsEntity(id: UUID) = ornaments.values.any { it.uniqueId == id }

    fun tick() {
        ticks++
        val viewers = Bukkit.getOnlinePlayers().toList()
        val participants = runtime.participants()
        val active = participants.filter { it.player.isOnline && !it.player.isDead && !it.entityStatus.isDead }
        val activeIds = active.mapTo(mutableSetOf()) { it.uniqueId }
        ornaments.keys.filter { it.first !in activeIds }.toList().forEach { ornaments.remove(it)?.remove() }
        participants.filter { it.uniqueId !in activeIds }.forEach { restoreChest(it.player) }
        active.forEach { data ->
            val state = runtime.players[data.uniqueId] ?: return@forEach
            val player = data.player
            for (slot in listOf(GrowthSlot.WEAPON, GrowthSlot.ACCESSORY, GrowthSlot.RELIC)) {
                val key = data.uniqueId to slot
                val item = state.equipment[slot]?.let(GrowthItems::byId)
                if (item == null || player.isInvisible || player.isSwimming || player.isGliding) {
                    ornaments.remove(key)?.remove(); continue
                }
                val loc = player.location.clone()
                val yaw = Math.toRadians(player.bodyYaw.toDouble())
                val right = if (slot == GrowthSlot.ACCESSORY) 0.37 else if (slot == GrowthSlot.RELIC) -0.3 else 0.0
                val back = if (slot == GrowthSlot.WEAPON) -0.28 else if (slot == GrowthSlot.RELIC) 0.23 else 0.0
                val height = when (slot) { GrowthSlot.WEAPON -> 1.05; GrowthSlot.ACCESSORY -> 1.4; else -> 0.72 }
                loc.add(cos(yaw) * right - sin(yaw) * back, height - if (player.isSneaking) 0.27 else 0.0,
                    sin(yaw) * right + cos(yaw) * back)
                loc.yaw = player.bodyYaw; loc.pitch = 0f
                var display = ornaments[key]
                if (display == null || !display.isValid || display.world != player.world) {
                    display?.remove()
                    display = player.world.spawn(loc, ItemDisplay::class.java) { spawned ->
                        spawned.isVisibleByDefault = false
                        spawned.isPersistent = false; spawned.isInvulnerable = true; spawned.setGravity(false)
                        spawned.persistentDataContainer.set(runtime.entityKey, PersistentDataType.STRING, "cosmetic")
                        spawned.teleportDuration = 2
                        spawned.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
                        val scale = if (slot == GrowthSlot.WEAPON) 0.8f else 0.3f
                        spawned.transformation = Transformation(Vector3f(),
                            Quaternionf().rotateZ(if (slot == GrowthSlot.WEAPON) -0.6f else 0f), Vector3f(scale), Quaternionf())
                    }
                    ornaments[key] = display
                }
                val material = if (slot != GrowthSlot.WEAPON) item.material else when (item.id) {
                    "blood-fang" -> Material.IRON_SWORD; "spell-edge" -> Material.DIAMOND_SWORD; else -> item.material
                }
                val stack = ItemStack(material)
                if (display.itemStack != stack) display.setItemStack(stack)
                display.teleport(loc)
                for (viewer in viewers) {
                    val visible = viewer.world == player.world && viewer.canSee(player) &&
                        viewer.location.distanceSquared(player.location) <= 48 * 48
                    if (visible && !viewer.canSee(display)) viewer.showEntity(plugin, display)
                    else if (!visible && viewer.canSee(display)) viewer.hideEntity(plugin, display)
                }
            }
            if (ticks % 5 == 0) refreshArmor(data)
        }
        if (ticks % 5 != 0) return // 0.5-second environmental UI; bounded to nearby points per viewer.
        refreshMobNames()
        val map = runtime.layout ?: return
        if (boundaries == null) boundaries = RegionBoundaries(map)
        if (MapTransferBorderManager.isExpanded(runtime.world)) return
        for (data in active) {
            val player = data.player
            if (player.world != runtime.world) continue
            val loc = player.location
            for (point in boundaries!!.near(loc.x, loc.z)) {
                val y = point.y?.toDouble() ?: loc.y
                if (kotlin.math.abs(y - loc.y) > 20) continue
                for (height in listOf(0.2, 1.6)) player.spawnParticle(Particle.DUST, point.x, y + height, point.z,
                    1, 0.0, 0.0, 0.0, 0.0, dust.getValue(point.state(map)))
            }
        }
    }

    fun refreshMobNames() {
        mobNames.keys.retainAll(runtime.mobs.keys)
        runtime.mobs.forEach { (id, record) ->
            val entity = record.data.entity
            if (!entity.isValid || entity.isDead) return@forEach
            val hp = if (runtime.settings.mobHealthVisible) " <red>♥ ${String.format(Locale.ROOT, "%.1f/%.1f", entity.health,
                entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: entity.health)}" else ""
            val title = record.event?.let { "<gold>★ ${GrowthItems.byId(it.reward)!!.name} 수호자" }
                ?: "<white>${if (entity is Monster) "지역 몬스터" else "야생 동물"}"
            val name = "<yellow>Lv.${record.level} $title$hp"
            if (mobNames.put(id, name) != name) entity.customName(mini.deserialize(name))
        }
    }

    /** Client-only armor never changes real armor attributes, durability or class inventory. */
    fun refreshArmor(data: PlayerData) {
        val player = data.player
        val item = runtime.players[data.uniqueId]?.equipment?.get(GrowthSlot.ARMOR)?.let(GrowthItems::byId)
        if (item == null || !player.isOnline || player.isDead || data.entityStatus.isDead || player.isInvisible) {
            restoreChest(player); return
        }
        val stack = ItemStack(item.material)
        if (stack.itemMeta is LeatherArmorMeta) stack.itemMeta = (stack.itemMeta as LeatherArmorMeta).apply { setColor(Color.ORANGE) }
        val sent = chestViewers.getOrPut(player.uniqueId) { mutableSetOf() }
        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (viewer.world == player.world && viewer.canSee(player) && viewer.location.distanceSquared(player.location) <= 64 * 64) {
                viewer.sendEquipmentChange(player, EquipmentSlot.CHEST, stack)
                sent.add(viewer.uniqueId)
            } else if (sent.remove(viewer.uniqueId)) {
                viewer.sendEquipmentChange(player, EquipmentSlot.CHEST, player.inventory.chestplate)
            }
        }
    }

    fun hideFrom(viewer: Player, target: UUID) {
        ornaments.filterKeys { it.first == target }.values.forEach { viewer.hideEntity(plugin, it) }
    }

    private fun restoreChest(player: Player) {
        chestViewers.remove(player.uniqueId)?.forEach { id -> Bukkit.getPlayer(id)?.let { viewer ->
            if (viewer.world == player.world) viewer.sendEquipmentChange(player, EquipmentSlot.CHEST,
                player.inventory.chestplate)
        } }
    }

    override fun close() {
        ornaments.values.forEach { it.remove() }; ornaments.clear()
        runtime.participants().forEach { restoreChest(it.player) }
        chestViewers.clear(); mobNames.clear(); boundaries = null
    }
}
