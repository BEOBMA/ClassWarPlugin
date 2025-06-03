package org.beobma.classWarPlugin.map

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

enum class MapType(val component: Component) {
    Forest(MiniMessage.miniMessage().deserialize("<green><bold>숲"))
}