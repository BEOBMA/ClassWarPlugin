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
import org.beobma.classWarPlugin.util.DisplayOrientationUtil
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector

class Sagittarius : GameClass() {
    override val name = "<gray>궁수자리"
    override val rank = Rank.A
    override val classItemMaterial = Material.BOW
    override var skills: List<Skill> = emptyList()
    override val weapon: BaseWeapon = Weapon()
    override var passives: List<BasePassive> = listOf(Passive())
    override val extraItemMaterials: List<ItemStack> = listOf(ItemStack(Material.ARROW, 64))

    private fun launchLightArrows(arrow: AbstractArrow, target: LivingEntity) {
        val forward = arrow.velocity.clone().takeIf { it.lengthSquared() > 1.0E-8 }?.normalize()
            ?: player.eyeLocation.direction.normalize()
        val horizontalRight = Vector(-forward.z, 0.0, forward.x).let { right ->
            if (right.lengthSquared() > 1.0E-8) right.normalize() else Vector(1.0, 0.0, 0.0)
        }
        val rearCenter = player.eyeLocation.clone()
            .subtract(forward.clone().multiply(1.15))
            .add(0.0, 0.2, 0.0)
        val targetCenter = target.boundingBox.center.toLocation(target.world)
        listOf(-0.82 to 0.28, 0.82 to -0.18).forEach { (side, height) ->
            val start = rearCenter.clone()
                .add(horizontalRight.clone().multiply(side))
                .add(0.0, height, 0.0)
            val direction = targetCenter.toVector().subtract(start.toVector())
            if (direction.lengthSquared() > 1.0E-8) {
                start.direction = direction.normalize()
                LightArrow(start).spawnProjectile(playerData)
            }
        }
        particles.spawn(rearCenter, Particle.FLASH, count = 1)
        particles.spawn(rearCenter, Particle.WAX_ON, count = 18, spread = 0.8, speed = 0.07)
        sounds.play(rearCenter, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, volume = 0.48f, pitch = 1.85f)
        sounds.play(rearCenter, Sound.BLOCK_AMETHYST_CLUSTER_HIT, volume = 0.6f, pitch = 1.65f)
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
        override val itemDisplayItem = ItemStack(Material.SPECTRAL_ARROW)
        private var flightSoundTick = 0

        override fun onItemDisplaySpawn(display: ItemDisplay, location: Location) {
            display.billboard = Display.Billboard.FIXED
            display.brightness = Display.Brightness(15, 15)
            orientLightArrow(display, location.direction)
        }

        override fun onItemDisplayMove(display: ItemDisplay, location: Location, speed: Double, tick: Int) {
            orientLightArrow(display, location.direction)
        }

        override fun onProjectileMove(location: Location) {
            particles.spawn(location, Particle.END_ROD, count = 2, spread = 0.06, speed = 0.01)
            particles.spawn(location, Particle.WAX_ON, count = 1)
            if (flightSoundTick++ % 4 == 0) {
                val pitch = 1.55f + (flightSoundTick % 12) * 0.025f
                sounds.play(location, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume = 0.18f, pitch = pitch)
            }
        }

        override fun onProjectileEntityHit(hitEntityData: EntityData, location: Location) {
            hitEntityData.damage(4.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
            particles.spawn(hitEntityData.entity, Particle.FLASH, count = 1)
            sounds.play(hitEntityData.entity, Sound.ENTITY_ARROW_HIT_PLAYER, volume = 0.7f, pitch = 1.65f)
            sounds.play(hitEntityData.entity, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, volume = 0.55f, pitch = 1.8f)
        }

        private fun orientLightArrow(display: ItemDisplay, direction: Vector) {
            DisplayOrientationUtil.alignSwordBladeVertically(display, direction, scale = 1.0f)
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
            val target = event.hitEntity as? LivingEntity ?: return
            val arrow = event.entity as? AbstractArrow ?: return
            if (!arrow.addScoreboardTag("cw-sagittarius-triggered")) return
            val shooter = arrow.shooter as? Player ?: return
            val data = findGameForPlayer(shooter)?.playerDatas?.filterIsInstance<PlayerData>()
                ?.find { it.uniqueId == shooter.uniqueId } ?: return
            data.findGameClass(Sagittarius::class.java)?.launchLightArrows(arrow, target)
        }
    }
}
