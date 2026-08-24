package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Projectile
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Player
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.inventory.ItemStack

class Sagittarius : GameClass() {
    override val name = "<gray>궁수자리"
    override val rank = Rank.A
    override val classItemMaterial = Material.BOW
    override var skills: List<Skill> = emptyList()
    override val weapon: BaseWeapon = Weapon()
    override var passives: List<BasePassive> = listOf(Passive())
    override val extraItemMaterials: List<ItemStack> = listOf(ItemStack(Material.ARROW, 64))

    private fun launchLightArrows(arrow: AbstractArrow) {
        val baseDirection = arrow.velocity.clone().takeIf { it.lengthSquared() > 1.0E-8 }?.normalize()
            ?: player.location.direction.normalize()
        repeat(2) { index ->
            val direction = baseDirection.clone().rotateAroundY(if (index == 0) -0.12 else 0.12)
            val start = arrow.location.clone().add(direction.clone().multiply(0.35)).setDirection(direction)
            LightArrow(start).spawnProjectile(playerData)
        }
        particles.spawn(arrow.location, Particle.FLASH, count = 1)
        sounds.play(arrow.location, Sound.ITEM_TRIDENT_THROW, volume = 0.55f, pitch = 1.8f)
    }

    private inner class LightArrow(override var location: Location) : Projectile() {
        override var targetType = TargetType.Enemy
        override var speed = 1.7
        override var isWallHit = true
        override var isPlayerHit = true
        override val isPlayerHitRemove = true
        override var time: Int? = 2
        override var xSize = 0.35
        override var ySize = 0.35
        override var zSize = 0.35

        override fun onProjectileMove(location: Location) {
            particles.spawn(location, Particle.END_ROD, count = 2, spread = 0.06, speed = 0.01)
            particles.spawn(location, Particle.WAX_ON, count = 1)
        }

        override fun onProjectileEntityHit(hitEntityData: EntityData, location: Location) {
            hitEntityData.damage(4.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
            particles.spawn(hitEntityData.entity, Particle.FLASH, count = 1)
            sounds.play(hitEntityData.entity, Sound.ENTITY_ARROW_HIT_PLAYER, volume = 0.7f, pitch = 1.65f)
        }
    }

    private class Weapon : BaseWeapon() {
        override val name = "<gray>활"
        override val description = listOf("<gray>화살 적중 시 빛의 화살 2개를 추가로 발사한다.")
        override val material = Material.BOW
    }

    private class Passive : BasePassive() {
        override val name = "<bold>궁수"
        override val description = listOf(
            "<gray>패시브", "", "<gray>화살 적중 시 빛으로 이루어진 화살 2개를 더 발사한다.",
            "<gray>이 효과로 발사된 화살은 적중 시 4의 피해를 입힌다."
        )
    }

    companion object {
        fun handleArrowHit(event: ProjectileHitEvent) {
            if (event.hitEntity == null) return
            val arrow = event.entity as? AbstractArrow ?: return
            if (!arrow.addScoreboardTag("cw-sagittarius-triggered")) return
            val shooter = arrow.shooter as? Player ?: return
            val data = findGameForPlayer(shooter)?.playerDatas?.filterIsInstance<PlayerData>()
                ?.find { it.uniqueId == shooter.uniqueId } ?: return
            data.findGameClass(Sagittarius::class.java)?.launchLightArrows(arrow)
        }
    }
}
