package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.ability.AbilityExecution

import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.entity.player.PlayerStatus
import org.beobma.classWarPlugin.description.DescriptionText
import org.bukkit.entity.Player

/**
 * 플레이어가 직접 발동하는 클래스 스킬의 기반 형식이다.
 *
 * [cooldown]은 초 단위이며 `null`이면 재사용 대기시간을 적용하지 않는다. 실행은
 * [org.beobma.classWarPlugin.manager.SkillManager]를 통해 이루어져야 상태 검사, 이벤트,
 * 재사용 대기시간 처리가 모두 적용된다.
 */
abstract class Skill : EffectApiAccess {
    protected lateinit var playerData: PlayerData
    protected val player: Player get() = playerData.player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var game: Game

    lateinit var ownerClass: org.beobma.classWarPlugin.gameClass.GameClass
        private set
    val abilityScope get() = ownerClass.abilityScope

    fun bind(data: PlayerData, owner: org.beobma.classWarPlugin.gameClass.GameClass) {
        ownerClass = owner
        inject(data)
    }

    abstract val name: String
    abstract val definitionId: String
    val id: String get() = if (this::ownerClass.isInitialized) "$definitionId@${abilityScope.instanceId}" else definitionId
    open fun matchesId(candidate: String): Boolean = candidate == id || candidate == definitionId || candidate == javaClass.name
    abstract val description: List<String>
    open val briefDescription: List<String>
        get() = DescriptionText.brief(description)
    abstract val cooldown: Int?

    open val isOnOffSKill: Boolean = false
    open val canUseWhileSilenced: Boolean = false

    private var activeContext: SkillContext? = null

    /** 스킬의 실제 효과를 실행한다. */
    abstract fun use(): Boolean

    /** 자원이나 추가 조건을 검사한다. `false`면 이벤트와 [use] 호출 없이 종료한다. */
    open fun isUseSuccess(): Boolean = true

    /** 스킬이 참조할 플레이어·상태·경기를 [playerData] 기준으로 연결한다. */
    fun inject(playerData: PlayerData) {
        if (playerData.entityStatus !is PlayerStatus) return
        this.playerData = playerData
        this.playerStatus = playerData.entityStatus
        this.game = playerData.initGame
    }

    internal fun request(context: SkillContext, authorize: () -> Boolean): Boolean {
        val previous = activeContext
        if (previous != null) return false
        activeContext = context
        try {
            return AbilityExecution.with(abilityScope) {
                abilityScope.isActive && isUseSuccess() && authorize() && abilityScope.isActive &&
                    !playerStatus.isDead && !game.isPaused && use()
            }
        } finally {
            context.preparedValues.clear()
            activeContext = previous
        }
    }

    /** Selected targets belong to a single request and are discarded on rejection or cancellation. */
    protected fun <T> requestValue(initial: () -> T): kotlin.properties.ReadWriteProperty<Any?, T> =
        object : kotlin.properties.ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T {
                val values = checkNotNull(activeContext) { "No active skill request" }.preparedValues
                @Suppress("UNCHECKED_CAST")
                return values.getOrPut(property.name, initial) as T
            }
            override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
                checkNotNull(activeContext).preparedValues[property.name] = value
            }
        }

    /** 현재 실행 중인 스킬의 기본 재사용 대기시간에 [multiplier]를 곱한다. */
    protected fun multiplyCurrentCooldown(multiplier: Double) {
        activeContext?.multiplyCooldown(multiplier)
    }
}
