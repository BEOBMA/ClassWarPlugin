package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.HitboxUtil
import org.beobma.classWarPlugin.util.PlayerNavigation
import org.bukkit.Material
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

class Exodia : GameClass(), GameStatusHandler {
    override val name = "<gray>엑조디아"
    override val rank = Rank.B
    override val classItemMaterial = Material.TRIAL_KEY
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private data class Part(val label: String, val material: Material, val display: ItemDisplay, val baseY: Double)

    private val parts = mutableListOf<Part>()
    private var collected = 0

    override fun onBattleStart() {
        parts.forEach { it.display.remove() }
        parts.clear()
        collected = 0
        playerData.getOrCreateStatus(playerData) { ExodiaPartStatus() }.updatePower(0)
        val definitions = listOf(
            "왼쪽 팔" to Material.GOLDEN_AXE,
            "오른쪽 팔" to Material.GOLDEN_SWORD,
            "왼쪽 다리" to Material.GOLDEN_BOOTS,
            "오른쪽 다리" to Material.CHAINMAIL_BOOTS,
            "몸통" to Material.GOLDEN_CHESTPLATE,
        )
        val partLocations = findPartLocations(definitions.size)
        definitions.forEachIndexed { index, (label, material) ->
            val location = partLocations[index]
            val display = location.world.spawn(location, ItemDisplay::class.java).apply {
                setItemStack(ItemStack(material))
                itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
                billboard = Display.Billboard.FIXED
                brightness = Display.Brightness(15, 15)
                transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(1.15f, 1.15f, 1.15f), Quaternionf())
                isPersistent = false
                setRotation(index * 72f, 0f)
            }
            TemporaryDisplayManager.mark(display, player.uniqueId)
            parts += Part(label, material, display, location.y)
        }
        playerData.trackTask(object : BukkitRunnable() {
            var tick = 0
            override fun run() {
                if (!player.isOnline || playerStatus.isDead || parts.isEmpty()) {
                    cancel()
                    return
                }
                parts.toList().forEachIndexed { index, part ->
                    if (!part.display.isValid) {
                        parts.remove(part)
                        return@forEachIndexed
                    }
                    val location = part.display.location
                    location.y = part.baseY + kotlin.math.sin(tick * 0.11 + index) * 0.22
                    part.display.teleport(location)
                    part.display.setRotation((tick * 3f + index * 72f) % 360f, 0f)
                    if (tick % 5 == 0) particles.spawn(location, Particle.ENCHANT, count = 5, spread = 0.32, speed = 0.02)
                    if (HitboxUtil.intersectsSphere(player.boundingBox, location.toVector(), 1.35)) collect(part)
                }
                tick++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }
    override fun onGameTimePasses() = Unit

    private fun findPartLocations(count: Int): List<Location> {
        if (count <= 0) return emptyList()
        val world = player.world
        val half = (game.settings.borderInitialSize * 0.45).coerceAtLeast(6.0)
        val bounds = PlayerNavigation.Bounds(
            minX = game.roundCenterX - half,
            maxX = game.roundCenterX + half,
            minZ = game.roundCenterZ - half,
            maxZ = game.roundCenterZ + half,
        )
        val start = PlayerNavigation.nearestNode(world, player.location)
            ?: PlayerNavigation.surfaceNode(world, player.location.blockX, player.location.blockZ)
            ?: PlayerNavigation.surfaceNode(world, game.roundCenterX.toInt(), game.roundCenterZ.toInt())
            ?: return List(count) { player.location.clone() }

        // 플레이어가 실제로 걸어서 닿을 수 있는 후보 중 서로 가장 먼 지점을 차례로 고른다.
        // 무작위 순서의 앞부분만 사용하던 기존 방식과 달리 맵의 접근 가능한 영역 전체에 퍼진다.
        val reachableNodes = PlayerNavigation.collectReachable(
            world = world,
            start = start,
            bounds = bounds,
            maxVisitedNodes = PART_REACHABLE_MAX_VISITED_NODES,
        )
        val reachableDisplayNodes = reachableNodes.filter { node ->
            world.worldBorder.isInside(PlayerNavigation.displayLocation(world, node))
        }
        val reachableSurfaceNodes = reachableDisplayNodes.asSequence()
            .filter { node -> PlayerNavigation.surfaceNode(world, node.x, node.z) == node }
            .toList()

        val selectedNodes = mutableListOf<PlayerNavigation.Node>()
        addMostSpreadNodes(reachableSurfaceNodes, selectedNodes, start, count)
        if (selectedNodes.size < count) {
            addMostSpreadNodes(reachableDisplayNodes, selectedNodes, start, count)
        }
        val locations = selectedNodes.mapTo(mutableListOf()) { PlayerNavigation.displayLocation(world, it) }

        val safeFallback = PlayerNavigation.displayLocation(world, start)
        while (locations.size < count) locations += safeFallback.clone()
        return locations
    }

    private fun addMostSpreadNodes(
        candidates: Collection<PlayerNavigation.Node>,
        selected: MutableList<PlayerNavigation.Node>,
        anchor: PlayerNavigation.Node,
        count: Int,
    ) {
        val selectedColumns = selected.mapTo(hashSetOf()) { it.x to it.z }
        val remaining = candidates.asSequence()
            .distinctBy { it.x to it.z }
            .filter { (it.x to it.z) !in selectedColumns }
            .shuffled()
            .toMutableList()
        val anchors = mutableListOf(anchor).apply { addAll(selected) }

        while (selected.size < count && remaining.isNotEmpty()) {
            val next = remaining.maxByOrNull { candidate ->
                anchors.minOf { other ->
                    val dx = (candidate.x - other.x).toDouble()
                    val dz = (candidate.z - other.z).toDouble()
                    dx * dx + dz * dz
                }
            } ?: break
            selected += next
            anchors += next
            remaining.remove(next)
        }
    }

    private fun collect(part: Part) {
        if (!parts.remove(part)) return
        part.display.remove()
        collected++
        playerData.getOrCreateStatus(playerData) { ExodiaPartStatus() }.updatePower(collected)
        particles.spawn(player, Particle.TOTEM_OF_UNDYING, count = 42, spread = 0.65, speed = 0.13)
        sounds.play(player, Sound.ENTITY_ITEM_PICKUP, volume = 0.9f, pitch = 0.8f + collected * 0.12f)
        player.sendMessage(net.kyori.adventure.text.Component.text("[엑조디아] ${part.label}을(를) 획득했습니다. ($collected/5)"))
        if (collected < 5) return
        particles.spawn(player, Particle.FLASH, count = 3)
        particles.spawn(player, Particle.TOTEM_OF_UNDYING, count = 180, spread = 1.5, speed = 0.25)
        sounds.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, volume = 1.0f, pitch = 0.65f)
        game.playerDatas.filterIsInstance<PlayerData>()
            .filter { it != playerData && !it.entityStatus.isDead }
            .forEach { it.player.health = 0.0 }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>엑조디아"
        override val description = listOf(
            "<gray>패시브", "", "<gray>게임 시작 시, 월드보더 내부 무작위 위치에 왼쪽 팔, 오른쪽 팔, 왼쪽 다리, 오른쪽 다리, 몸통이 떨어진다.",
            "<gray>모두 모으면 자신을 제외한 모든 적은 {keyword:Execution}시킨다."
        )
    }

    companion object {
        private const val PART_REACHABLE_MAX_VISITED_NODES = 120_000
    }
}

private class ExodiaPartStatus : StatusAbnormality() {
    override val name = "<gold><bold>엑조디아 부위</bold><gray>"
    override val description = listOf("<gray>수집한 엑조디아 부위의 수이다.")
    override val canRemove = false
    override val isClassMechanic = true
    override var power = 0
    override var maxPower: Int? = 5
    override var duration: Int? = null
}
