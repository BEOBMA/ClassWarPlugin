package org.beobma.classWarPlugin.gameClass

import org.beobma.classWarPlugin.description.DescriptionText
import org.bukkit.Material

abstract class Weapon {
    abstract val name: String
    abstract val description: List<String>
    open val briefDescription: List<String>
        get() = DescriptionText.brief(description)
    abstract val material: Material
}

object DefaultWeapon : Weapon() {
    override val name = "<gray>철 검"
    override val description = listOf("<gray>기본 공격에 사용하는 표준 근접 무기다.")
    override val material = Material.IRON_SWORD
}
