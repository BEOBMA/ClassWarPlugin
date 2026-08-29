package org.beobma.classWarPlugin.gameClass

import org.beobma.classWarPlugin.description.DescriptionText
import org.bukkit.Material

/** 클래스의 기본 공격 아이템에 필요한 표시 정보다. */
abstract class Weapon {
    abstract val name: String
    abstract val description: List<String>
    open val briefDescription: List<String>
        get() = DescriptionText.brief(description)
    abstract val material: Material
}

/** 별도 무기를 선언하지 않은 클래스에 제공되는 표준 철 검이다. */
object DefaultWeapon : Weapon() {
    override val name = "<gray>철 검"
    override val description = listOf("<gray>기본 공격에 사용하는 표준 근접 무기다.")
    override val material = Material.IRON_SWORD
}
