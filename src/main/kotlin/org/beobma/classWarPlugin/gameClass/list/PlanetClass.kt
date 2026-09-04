package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ability.AbilityTree

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass

/** 단독 행성 클래스와 태양계가 공유하는 활성화 상태. */
abstract class PlanetClass : GameClass() {
    private var solarPowerProvider: (() -> Boolean)? = null

    internal fun bindSolarPower(provider: () -> Boolean) {
        solarPowerProvider = provider
    }

    internal val isSolarAbility: Boolean get() = solarPowerProvider != null

    internal fun isPowerEnabled(): Boolean = solarPowerProvider?.invoke() ?: true
}

object PlanetPowerRegistry {
    fun <T : PlanetClass> hasPower(playerData: PlayerData, type: Class<T>): Boolean =
        AbilityTree.nodes(playerData.gameClasses, activeOnly = true).any { gameClass ->
            when {
                type.isInstance(gameClass) -> type.cast(gameClass).isPowerEnabled()
                gameClass is SolarSystem -> gameClass.isPlanetActive(type)
                else -> false
            }
        }
}
