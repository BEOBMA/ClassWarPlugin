package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.game.GamePhase
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapCanvas
import org.bukkit.map.MapCursor
import org.bukkit.map.MapCursorCollection
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView
import org.bukkit.persistence.PersistentDataType
import java.awt.Color
import java.util.UUID
import kotlin.math.floor
import kotlin.math.roundToInt

object BattleMapManager {
    private const val MAP_SIZE = 128
    private const val PREFERRED_HOTBAR_SLOT = 8
    private val miniMessage = MiniMessage.miniMessage()
    private val mapItemKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "battle-map")

    fun giveTo(playerData: PlayerData) {
        val game = playerData.initGame
        if (game.phase != GamePhase.SCATTERING && game.phase != GamePhase.RUNNING) return

        val player = playerData.player
        val mapView = game.battleMapView ?: createMap(game).also { game.battleMapView = it }
        val mapItem = createMapItem(mapView)
        val currentSlot = player.inventory.storageContents.indexOfFirst(::isBattleMap)

        when {
            currentSlot >= 0 -> player.inventory.setItem(currentSlot, mapItem)
            isBattleMap(player.inventory.itemInOffHand) -> player.inventory.setItemInOffHand(mapItem)
            else -> player.inventory.setItem(findMapSlot(player), mapItem)
        }
    }

    fun isBattleMap(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.FILLED_MAP) return false
        return item.itemMeta.persistentDataContainer.has(mapItemKey, PersistentDataType.BYTE)
    }

    fun cleanup(game: Game) {
        val mapView = game.battleMapView
        val renderer = game.battleMapRenderer
        if (mapView != null && renderer != null) mapView.removeRenderer(renderer)
        game.battleMapRenderer = null
        game.battleMapView = null
    }

    private fun createMap(game: Game): MapView {
        val mapView = Bukkit.createMap(GameManager.gameWorld).apply {
            centerX = game.roundCenterX.roundToInt()
            centerZ = game.roundCenterZ.roundToInt()
            scale = chooseScale(game)
            isTrackingPosition = false
            isUnlimitedTracking = false
            isLocked = false
        }
        val renderer = MagneticFieldRenderer(game)
        mapView.addRenderer(renderer)
        game.battleMapRenderer = renderer
        return mapView
    }

    private fun createMapItem(mapView: MapView): ItemStack = ItemStack(Material.FILLED_MAP).apply {
        itemMeta = (itemMeta as MapMeta).apply {
            setMapView(mapView)
            isScaling = false
            displayName(miniMessage.deserialize("<aqua><bold>전장 지도"))
            lore(
                listOf(
                    miniMessage.deserialize("<gray>자신의 위치와 현재 자기장을 표시합니다."),
                    miniMessage.deserialize("<blue>파랑 <dark_gray>- <gray>축소 대기"),
                    miniMessage.deserialize("<red>빨강 <dark_gray>- <gray>축소 중 또는 축소 완료"),
                )
            )
            persistentDataContainer.set(mapItemKey, PersistentDataType.BYTE, 1)
        }
    }

    private fun findMapSlot(player: Player): Int {
        for (slot in PREFERRED_HOTBAR_SLOT downTo 1) {
            if (player.inventory.getItem(slot)?.type?.isAir != false) return slot
        }
        return player.inventory.storageContents.indexOfFirst { it == null || it.type.isAir }
            .takeIf { it >= 0 }
            ?: PREFERRED_HOTBAR_SLOT
    }

    private fun chooseScale(game: Game): MapView.Scale {
        val diameter = if (game.settings.borderEnabled) {
            game.settings.borderInitialSize
        } else {
            game.settings.scatterMaxRadius * 2.0
        }
        val requiredBlocks = diameter + 32.0
        return MapView.Scale.entries.firstOrNull { scale ->
            MAP_SIZE * blocksPerPixel(scale) >= requiredBlocks
        } ?: MapView.Scale.FARTHEST
    }

    private fun blocksPerPixel(scale: MapView.Scale): Int = when (scale) {
        MapView.Scale.CLOSEST -> 1
        MapView.Scale.CLOSE -> 2
        MapView.Scale.NORMAL -> 4
        MapView.Scale.FAR -> 8
        MapView.Scale.FARTHEST -> 16
    }

    private class MagneticFieldRenderer(private val game: Game) : MapRenderer(true) {
        private val previousPixelsByPlayer = mutableMapOf<UUID, Set<Int>>()

        override fun render(mapView: MapView, canvas: MapCanvas, player: Player) {
            previousPixelsByPlayer.remove(player.uniqueId)?.forEach { encoded ->
                canvas.setPixelColor(encoded % MAP_SIZE, encoded / MAP_SIZE, null)
            }

            if (game.phase == GamePhase.FINISHED) {
                canvas.cursors = MapCursorCollection()
                return
            }

            if (game.settings.borderEnabled) {
                val pixels = drawMagneticField(mapView, canvas)
                previousPixelsByPlayer[player.uniqueId] = pixels
            }
            drawPlayerCursor(mapView, canvas, player)
        }

        private fun drawMagneticField(mapView: MapView, canvas: MapCanvas): Set<Int> {
            val borderStarted = game.originalBorderCenter != null
            val border = GameManager.gameWorld.worldBorder
            val centerX = if (borderStarted) border.center.x else game.roundCenterX
            val centerZ = if (borderStarted) border.center.z else game.roundCenterZ
            val size = if (borderStarted) border.size else game.settings.borderInitialSize
            val halfSize = size / 2.0
            val blocksPerPixel = blocksPerPixel(mapView.scale).toDouble()
            val left = worldToPixel(centerX - halfSize, mapView.centerX, blocksPerPixel)
            val right = worldToPixel(centerX + halfSize, mapView.centerX, blocksPerPixel)
            val top = worldToPixel(centerZ - halfSize, mapView.centerZ, blocksPerPixel)
            val bottom = worldToPixel(centerZ + halfSize, mapView.centerZ, blocksPerPixel)
            val shrinkingOrFinished = borderStarted && border.size < game.settings.borderInitialSize - 0.05
            val color = if (shrinkingOrFinished) SHRINKING_COLOR else WAITING_COLOR
            val pixels = mutableSetOf<Int>()

            for (offset in 0..1) {
                drawHorizontal(canvas, pixels, left, right, top + offset, color)
                drawHorizontal(canvas, pixels, left, right, bottom - offset, color)
                drawVertical(canvas, pixels, top, bottom, left + offset, color)
                drawVertical(canvas, pixels, top, bottom, right - offset, color)
            }
            return pixels
        }

        private fun drawPlayerCursor(mapView: MapView, canvas: MapCanvas, player: Player) {
            if (player.world != mapView.world) {
                canvas.cursors = MapCursorCollection()
                return
            }

            val blocksPerPixel = blocksPerPixel(mapView.scale).toDouble()
            val rawX = ((player.location.x - mapView.centerX) * 2.0 / blocksPerPixel).roundToInt()
            val rawZ = ((player.location.z - mapView.centerZ) * 2.0 / blocksPerPixel).roundToInt()
            val outsideMap = rawX !in -128..127 || rawZ !in -128..127
            val cursorType = if (outsideMap) MapCursor.Type.PLAYER_OFF_MAP else MapCursor.Type.PLAYER
            val direction = (floor(player.location.yaw * 16.0 / 360.0 + 0.5).toInt() and 15).toByte()
            val cursors = MapCursorCollection()
            cursors.addCursor(
                MapCursor(
                    rawX.coerceIn(-128, 127).toByte(),
                    rawZ.coerceIn(-128, 127).toByte(),
                    direction,
                    cursorType,
                    true,
                    Component.text("내 위치"),
                )
            )
            canvas.cursors = cursors
        }

        private fun worldToPixel(worldCoordinate: Double, mapCenter: Int, blocksPerPixel: Double): Int =
            ((worldCoordinate - mapCenter) / blocksPerPixel + MAP_SIZE / 2.0).roundToInt()

        private fun drawHorizontal(
            canvas: MapCanvas,
            pixels: MutableSet<Int>,
            startX: Int,
            endX: Int,
            y: Int,
            color: Color,
        ) {
            if (y !in 0 until MAP_SIZE) return
            for (x in startX.coerceAtLeast(0)..endX.coerceAtMost(MAP_SIZE - 1)) {
                setPixel(canvas, pixels, x, y, color)
            }
        }

        private fun drawVertical(
            canvas: MapCanvas,
            pixels: MutableSet<Int>,
            startY: Int,
            endY: Int,
            x: Int,
            color: Color,
        ) {
            if (x !in 0 until MAP_SIZE) return
            for (y in startY.coerceAtLeast(0)..endY.coerceAtMost(MAP_SIZE - 1)) {
                setPixel(canvas, pixels, x, y, color)
            }
        }

        private fun setPixel(canvas: MapCanvas, pixels: MutableSet<Int>, x: Int, y: Int, color: Color) {
            if (x !in 0 until MAP_SIZE || y !in 0 until MAP_SIZE) return
            canvas.setPixelColor(x, y, color)
            pixels.add(y * MAP_SIZE + x)
        }

        companion object {
            private val WAITING_COLOR = Color(35, 137, 218)
            private val SHRINKING_COLOR = Color(220, 47, 47)
        }
    }
}
