package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.ClassWarPlugin
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Display
import java.util.UUID

/** 스킬이 생성한 비영구 표시 엔티티를 소유자별로 식별하고 가시성을 관리한다. */
object TemporaryDisplayManager {
    private val visibilitySuppressions = mutableSetOf<VisibilitySuppression>()

    private fun ownerTag(ownerId: UUID): String = "cw-${ownerId.toString().take(8)}"

    /** [display]에 소유자 태그를 붙이고 현재 가시성 억제 규칙을 즉시 적용한다. */
    fun mark(display: Display, ownerId: UUID) {
        display.addScoreboardTag(ownerTag(ownerId))
        display.isPersistent = false
        visibilitySuppressions.asSequence()
            .filter { it.worldId == display.world.uid && ownerId in it.ownerIds }
            .flatMap { it.viewerIds.asSequence() }
            .distinct()
            .mapNotNull(Bukkit::getPlayer)
            .filter { it.isOnline }
            .forEach { it.hideEntity(ClassWarPlugin.instance, display) }
    }

    /** [world]에서 [ownerId]가 소유한 모든 표시 엔티티를 제거한다. */
    fun clear(world: World, ownerId: UUID) {
        val tag = ownerTag(ownerId)
        world.getEntitiesByClass(Display::class.java)
            .filter { tag in it.scoreboardTags }
            .forEach(Display::remove)
    }

    /** 소유자들의 기존/신규 임시 표시 엔티티를 지정한 관전자에게 숨긴다. */
    fun suppressVisibility(
        world: World,
        ownerIds: Collection<UUID>,
        viewerIds: Collection<UUID>,
    ): VisibilitySuppression {
        val suppression = VisibilitySuppression(world.uid, ownerIds.toSet(), viewerIds.toSet())
        visibilitySuppressions += suppression
        displaysOwnedBy(world, suppression.ownerIds).forEach { display ->
            suppression.viewerIds.asSequence()
                .mapNotNull(Bukkit::getPlayer)
                .filter { it.isOnline }
                .forEach { it.hideEntity(ClassWarPlugin.instance, display) }
        }
        return suppression
    }

    /** [close]할 때까지 특정 소유자의 표시를 지정 관전자에게 숨기는 해제 가능한 핸들이다. */
    class VisibilitySuppression internal constructor(
        internal val worldId: UUID,
        internal val ownerIds: Set<UUID>,
        internal val viewerIds: Set<UUID>,
    ) : AutoCloseable {
        private var active = true

        override fun close() {
            if (!active) return
            active = false
            visibilitySuppressions -= this
            val world = Bukkit.getWorld(worldId) ?: return
            displaysOwnedBy(world, ownerIds).forEach { display ->
                viewerIds.asSequence()
                    .filterNot { viewerId -> isHiddenByAnotherSuppression(display, viewerId) }
                    .mapNotNull(Bukkit::getPlayer)
                    .filter { it.isOnline }
                    .forEach { it.showEntity(ClassWarPlugin.instance, display) }
            }
        }
    }

    private fun displaysOwnedBy(world: World, ownerIds: Set<UUID>): Sequence<Display> {
        val tags = ownerIds.mapTo(hashSetOf(), ::ownerTag)
        return world.getEntitiesByClass(Display::class.java).asSequence()
            .filter { display -> display.scoreboardTags.any(tags::contains) }
    }

    private fun isHiddenByAnotherSuppression(display: Display, viewerId: UUID): Boolean =
        visibilitySuppressions.any { suppression ->
            suppression.worldId == display.world.uid &&
                viewerId in suppression.viewerIds &&
                suppression.ownerIds.any { ownerTag(it) in display.scoreboardTags }
        }
}
