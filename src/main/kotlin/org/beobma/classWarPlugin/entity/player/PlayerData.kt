package org.beobma.classWarPlugin.entity.player

import org.beobma.classWarPlugin.ability.AttributeEffects
import org.beobma.classWarPlugin.ability.AbilityTree

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.EntityStatus
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * 경기 참가자 한 명의 클래스, 상태이상 및 예약 작업을 묶는 전투 데이터다.
 * 동일성은 재접속으로 [player] 객체가 바뀌어도 유지되는 [uniqueId]로 결정한다.
 */
class PlayerData(
    var player: Player,
    val initGame: Game,
) : EntityData() {
    val attributeEffects = AttributeEffects(this)
    private var pendingAbilityReturn: org.bukkit.Location? = null

    /** A cancelled transport also returns a participant who was offline during cleanup. */
    fun returnFromAbility(location: org.bukkit.Location) {
        if (entityStatus.isDead) return
        if (player.isOnline) {
            player.teleport(location)
            player.fallDistance = 0f
        } else pendingAbilityReturn = location.clone()
    }

    fun restoreAbilityPosition() {
        val location = pendingAbilityReturn ?: return
        pendingAbilityReturn = null
        returnFromAbility(location)
    }
    val gameClasses: MutableList<GameClass> = mutableListOf()
    /**
     * 단일 클래스 모드와의 호환을 위한 첫 번째 클래스 접근자다.
     * 값을 설정하면 기존 [gameClasses] 전체를 교체한다.
     */
    var gameClass: GameClass?
        get() = gameClasses.firstOrNull()
        set(value) {
            gameClasses.clear()
            if (value != null) gameClasses += value
        }
    val uniqueId: UUID = player.uniqueId
    override val entity: Entity
        get() = player
    override val game: Game = initGame
    override val entityStatus: EntityStatus = PlayerStatus()
    override val bukkitTasks: MutableList<BukkitTask> = mutableListOf()
    override val statusAbnormalitys: MutableList<StatusAbnormality> = mutableListOf()

    /** 작업을 플레이어와 경기 양쪽 정리 목록에 등록하고 그대로 반환한다. */
    fun trackTask(task: BukkitTask): BukkitTask {
        if (!task.isCancelled) {
            if (task !in bukkitTasks) bukkitTasks.add(task)
            if (task !in game.tasks) game.tasks.add(task)
        }
        return task
    }

    /** 같은 경기 안에서 현재 모드 규칙상 [other]가 공격 가능한 적인지 판정한다. */
    fun isEnemyOf(other: PlayerData): Boolean =
        initGame === other.initGame && initGame.areEnemies(uniqueId, other.uniqueId)

    /** 기존 배정을 제거하고 [classes]를 순서대로 배정한다. */
    fun assignGameClasses(classes: Collection<GameClass>) {
        gameClasses.clear()
        gameClasses.addAll(classes)
    }

    /** 배정된 클래스 중 [type]과 호환되는 첫 인스턴스를 반환한다. */
    fun <T : GameClass> findGameClass(type: Class<T>): T? =
        AbilityTree.nodes(gameClasses, activeOnly = true)
            .firstOrNull { type.isInstance(it) }?.let(type::cast)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlayerData) return false

        return uniqueId == other.uniqueId
    }

    override fun hashCode(): Int {
        return uniqueId.hashCode()
    }
}
