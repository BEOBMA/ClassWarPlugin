package org.beobma.classWarPlugin.gameClass.handler

import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent

/** 무기 아이템의 별도 입력을 사용하는 클래스가 구현하는 훅. */
interface WeaponInputHandler {
    fun onWeaponRightClick(event: PlayerInteractEvent) {}

    fun onWeaponLeftClick(event: PlayerInteractEvent) {}

    fun onWeaponSwapHand(event: PlayerSwapHandItemsEvent) {}
}
