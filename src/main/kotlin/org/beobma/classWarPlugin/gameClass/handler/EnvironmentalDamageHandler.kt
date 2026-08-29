package org.beobma.classWarPlugin.gameClass.handler

import org.bukkit.event.entity.EntityDamageEvent

/** 클래스가 바닐라 환경 피해 이벤트를 관찰하거나 수정할 수 있는 처리기다. */
interface EnvironmentalDamageHandler {
    fun onEnvironmentalDamage(event: EntityDamageEvent)
}
