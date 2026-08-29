package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.keyword.Keyword
import org.bukkit.Material
import org.bukkit.FluidCollisionMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.EntityType
import org.bukkit.util.Vector

/** 여러 기능에서 공유하는 MiniMessage, 엔티티 판별 및 플레이어 편의 함수를 제공한다. */
object UtilManager {
    val miniMessage = MiniMessage.miniMessage()
    private val keywordTokenRegex = "\\{keyword:([A-Za-z]+)}".toRegex()
    private val keywordTokens = enumValues<Keyword>().associateBy { it.name }
    private val dyeMaterials = listOf(
        Material.WHITE_DYE,
        Material.ORANGE_DYE,
        Material.MAGENTA_DYE,
        Material.LIGHT_BLUE_DYE,
        Material.YELLOW_DYE,
        Material.LIME_DYE,
        Material.PINK_DYE,
        Material.GRAY_DYE,
        Material.LIGHT_GRAY_DYE,
        Material.CYAN_DYE,
        Material.PURPLE_DYE,
        Material.BLUE_DYE,
        Material.BROWN_DYE,
        Material.GREEN_DYE,
        Material.RED_DYE,
        Material.BLACK_DYE
    )

    /** `{keyword:NAME}` 토큰을 해당 키워드의 MiniMessage 설명으로 치환한다. */
    fun applyKeywords(text: String): String {
        return keywordTokenRegex.replace(text) { match ->
            keywordTokens[match.groupValues[1]]?.string ?: match.value
        }
    }

    /** 플레이어 최대 체력 속성의 기본값을 반환한다. */
    fun Player.getPlayerMaxHealth(): Double {
        return requireNotNull(getAttribute(Attribute.MAX_HEALTH)) {
            "플레이어 ${uniqueId}에게 최대 체력 속성이 없습니다."
        }.baseValue
    }

    /** [message]를 MiniMessage로 역직렬화해 플레이어에게 전송한다. */
    fun Player.sendMiniMessage(message: String) {
        this.sendMessage(miniMessage.deserialize(message))
    }

    /** 엔티티가 Paper 마네킹 엔티티인지 판별한다. */
    fun Entity.isMannequin(): Boolean {
        return this.type == EntityType.MANNEQUIN
    }

    /** 스킬 아이템에 쓰는 모든 염료 재료의 Bukkit 쿨다운 표시를 해제한다. */
    fun Player.resetDyeCooldowns() {
        dyeMaterials.forEach { dye ->
            setCooldown(dye, 0)
        }
    }

    /** 발밑 중심과 네 모서리를 짧게 레이 트레이싱해 실제 지면 접촉을 판정한다. */
    fun Player.isActuallyGrounded(): Boolean {
        val box = boundingBox
        val insetX = ((box.maxX - box.minX) * 0.15).coerceAtMost(0.08)
        val insetZ = ((box.maxZ - box.minZ) * 0.15).coerceAtMost(0.08)
        val sampleY = box.minY + 0.05
        val samples = arrayOf(
            box.center.x to box.center.z,
            box.minX + insetX to box.minZ + insetZ,
            box.minX + insetX to box.maxZ - insetZ,
            box.maxX - insetX to box.minZ + insetZ,
            box.maxX - insetX to box.maxZ - insetZ,
        )

        return samples.any { (x, z) ->
            world.rayTraceBlocks(
                org.bukkit.Location(world, x, sampleY, z),
                Vector(0.0, -1.0, 0.0),
                0.12,
                FluidCollisionMode.NEVER,
                true,
            ) != null
        }
    }
}

/** 컬렉션에서 [T]인 원소에만 [action]을 실행한다. */
inline fun <reified T> Iterable<*>.forEachIs(action: (T) -> Unit) {
    for (item in this) {
        if (item is T) {
            action(item)
        }
    }
}
