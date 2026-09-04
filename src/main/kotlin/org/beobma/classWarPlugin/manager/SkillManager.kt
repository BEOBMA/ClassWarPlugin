package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.ability.Targeting

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.skill.SkillContext
import org.beobma.classWarPlugin.status.list.Silence
import org.beobma.classWarPlugin.status.list.Stun
import org.beobma.classWarPlugin.status.list.Enchantment
import org.beobma.classWarPlugin.status.list.Fix
import org.beobma.classWarPlugin.skill.MovementSkill
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.beobma.classWarPlugin.util.TargetType.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import kotlin.math.cos

/** 스킬 아이템 식별, 사용 검증과 공통 대상 탐색을 담당한다. */
object SkillManager {
    private val skillIdKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "skill-id")
    private val skillOwnerKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "skill-owner")

    /** 아이템에 스킬 식별자와 소유자 UUID를 기록하고 같은 인스턴스를 반환한다. */
    fun markSkillItem(item: ItemStack, skill: Skill, ownerId: UUID): ItemStack = item.apply {
        itemMeta = itemMeta.apply {
            persistentDataContainer.set(skillIdKey, PersistentDataType.STRING, skill.id)
            persistentDataContainer.set(skillOwnerKey, PersistentDataType.STRING, ownerId.toString())
        }
    }

    /** [ownerId]가 소유한 스킬 아이템인 경우에만 기록된 스킬 ID를 반환한다. */
    fun getSkillId(item: ItemStack, ownerId: UUID): String? {
        val container = item.itemMeta.persistentDataContainer
        if (container.get(skillOwnerKey, PersistentDataType.STRING) != ownerId.toString()) return null
        return container.get(skillIdKey, PersistentDataType.STRING)
    }

    /** Returns a read-only candidate snapshot. Training registration belongs to the session tick. */
    fun EntityData.getTargetCandidates(): List<EntityData> = Targeting.candidates(this)

    /**
     * 상태·침묵·쿨다운·이동 제한을 검사한 뒤 스킬 이벤트와 효과를 실행한다.
     *
     * @return 효과가 실행되어 사용 요청이 최종 승인됐는지 여부
     */
    fun EntityData.use(skill: Skill, clickedItem: ItemStack): Boolean {
        val playerData = this as? PlayerData ?: return false
        if (skill.abilityScope.playerData !== playerData || skill.abilityScope.isClosed ||
            skill.abilityScope.suspended || entityStatus.isDead || game.isPaused) return false
        if (!entityStatus.canSkillUse) {
            playerData.player.sendMiniMessage("<red><bold>[!] 현재 스킬을 사용할 수 없는 상태입니다.")
            return false
        }
        if (playerData.hasStatus<Stun>() || playerData.hasStatus<Enchantment>()) {
            playerData.player.sendMiniMessage("<red><bold>[!] 기절 상태에서는 스킬을 사용할 수 없습니다.")
            return false
        }
        if (playerData.hasStatus<Silence>() && !skill.canUseWhileSilenced) {
            playerData.player.sendMiniMessage("<red><bold>[!] 침묵 상태에서는 스킬을 사용할 수 없습니다.")
            return false
        }
        if (CooldownManager.hasCooldown(playerData.player, skill)) {
            playerData.player.sendMiniMessage("<red><bold>[!] 재사용 대기 중입니다.")
            return false
        }
        if (skill is MovementSkill && playerData.hasStatus<Fix>()) {
            playerData.player.sendMiniMessage("<red><bold>[!] 고정 상태에서는 이동 스킬을 사용할 수 없습니다.")
            return false
        }

        val baseCooldownTicks = when (val cooldown = skill.cooldown) {
            null -> 0
            Int.MAX_VALUE -> 999999 * 20
            else -> cooldown.coerceAtLeast(0) * 20
        }

        val context = SkillContext(playerData, skill, clickedItem, baseCooldownTicks)
        val playerSkillUseEvent = PlayerSkillUseEvent(context)
        val success = skill.request(context) {
            Bukkit.getServer().pluginManager.callEvent(playerSkillUseEvent)
            !playerSkillUseEvent.isCancelled
        }
        if (!success) return false
        if (context.cooldownTicks > 0) {
            CooldownManager.setCooldown(playerData.player, skill, clickedItem, context.cooldownTicks)
        }
        context.commit()

        return true
    }
    /**
     * [location] 중심의 구형 범위와 [targetType]을 모두 만족하는 대상을 반환한다.
     * [radius]에는 클래스 사거리 배율이 적용된다.
     */
    fun EntityData.radius(
        location: Location,
        targetType: TargetType,
        radius: Double,
        oneself: Boolean,
        hitAttackableObjects: Boolean = false,
    ): List<EntityData> {
        val effectiveRadius = ClassBalanceManager.scaleRange(this, radius)
        if (hitAttackableObjects && this is PlayerData && targetType == Enemy) {
            AttackableObjectManager.hitSphere(uniqueId, location, effectiveRadius)
        }
        val candidates = Targeting.select(this, targetType, location.world, includeSelf = oneself)
            .associateBy { it.entity.uniqueId }
        return location.world.getNearbyEntities(location, effectiveRadius, effectiveRadius, effectiveRadius)
            .mapNotNull { candidates[it.uniqueId] }
            .filter { HitboxUtil.intersectsSphere(it.entity.boundingBox, location.toVector(), effectiveRadius) }
    }

    /**
     * 시선 광선이 가장 먼저 만나는 유효 대상을 반환한다.
     * [wallShot]이 `false`면 대상보다 앞에 있는 블록이 광선을 차단한다.
     */
    fun EntityData.shotLaserGetEntityData(maxRange: Double, targetType: TargetType, wallShot: Boolean): EntityData? {
        val sourcePlayer = this as? PlayerData ?: return null
        val world = entity.world
        val playerDatas = Targeting.select(this, targetType, includeStealth = false)
        val entity = entity
        if (entity !is LivingEntity) return null
        val startLocation = entity.eyeLocation
        val direction = startLocation.direction

        val maxDistance = ClassBalanceManager.scaleRange(this, maxRange)

        val hitEntityData = playerDatas.asSequence()
            .mapNotNull { candidate ->
                HitboxUtil.rayIntersectionDistance(
                    candidate.entity.boundingBox,
                    startLocation.toVector(),
                    direction,
                    maxDistance,
                    expansion = 1.0,
                )?.let { distance -> candidate to distance }
            }
            .minByOrNull { it.second }
            ?: return null
        val hitEntity = hitEntityData.first.entity
        if (!wallShot) {
            val blockRayTraceResult = world.rayTraceBlocks(startLocation, direction, maxDistance)
            val blockPosition = blockRayTraceResult?.hitPosition
            if (blockPosition != null &&
                blockPosition.distanceSquared(startLocation.toVector()) <=
                hitEntityData.second * hitEntityData.second
            ) {
                return null
            }
        }
        return hitEntityData.first
    }

    /** 클래스 사거리 배율을 적용한 시선 광선이 처음 만나는 블록을 반환한다. */
    fun EntityData.shotLaserGetBlock(maxRange: Double): Block? {
        val sourcePlayer = this as? PlayerData ?: return null
        val world = sourcePlayer.player.world
        val startLocation = sourcePlayer.player.eyeLocation
        val direction = startLocation.direction

        val maxDistance = ClassBalanceManager.scaleRange(this, maxRange)

        val blockRayTraceResult = world.rayTraceBlocks(startLocation, direction, maxDistance)
        return blockRayTraceResult?.hitBlock
    }
    /**
     * 플레이어 시선 기준 [angle]도의 원뿔과 [radius] 블록 안에 있는 유효 대상을 반환한다.
     * 반경에는 클래스 사거리 배율이 적용된다.
     */
    fun EntityData.getConeTargets(radius: Double, angle: Double, targetType: TargetType, includeSelf: Boolean, hitAttackableObjects: Boolean = false): List<EntityData> {
        val sourcePlayer = this as? PlayerData ?: return emptyList()
        val effectiveRadius = ClassBalanceManager.scaleRange(this, radius)
        if (hitAttackableObjects && targetType == Enemy) {
            AttackableObjectManager.hitCone(sourcePlayer.uniqueId, sourcePlayer.player.eyeLocation, effectiveRadius, angle)
        }
        val playerLocation = sourcePlayer.player.location
        val playerDirection = playerLocation.direction.normalize()

        return Targeting.select(this, targetType, includeSelf = includeSelf).filter { targetPlayerData ->
            val distanceSquared = HitboxUtil.distanceSquared(targetPlayerData.entity.boundingBox, playerLocation.toVector())
            if (distanceSquared > effectiveRadius * effectiveRadius) return@filter false
            if (distanceSquared == 0.0) return@filter true

            val targetPoint = HitboxUtil.closestPoint(targetPlayerData.entity.boundingBox, playerLocation.toVector())
            val directionToTarget = targetPoint.clone().subtract(playerLocation.toVector()).normalize()

            val dotProduct = playerDirection.dot(directionToTarget)
            dotProduct >= cos(Math.toRadians(angle / 2))
        }
    }

}
