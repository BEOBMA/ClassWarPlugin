package org.beobma.classWarPlugin.game

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage


enum class GameSetDetailType(val component: Component) {
    Draw(MiniMessage.miniMessage().deserialize("제한시간 초과로 인한 무승부")),
    Annihilation(MiniMessage.miniMessage().deserialize("상대팀을 전멸시켜 승리"))
}