package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ability.AbilityExecution

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.effect.ParticleApi
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.effect.SoundApi
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Frostbite
import org.beobma.classWarPlugin.util.DisplayOrientationUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable

class Uranus : PlanetClass() {
    override val classId = "uranus"
    override val name = "<gray>천왕성"
    override val rank = Rank.A
    override val classItemMaterial = Material.ICE
    override val weapon: BaseWeapon = IcicleBow()
    override val extraItemMaterials: List<ItemStack> = listOf(ItemStack(Material.ARROW, 48))
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())

    private class IcicleBow : BaseWeapon() {
        override val name = "<gray>고드름 활"
        override val description = listOf("<gray>화살 대신 고드름을 발사한다.")
        override val material = Material.BOW
    }

    private class Passive : BasePassive() {
        override val name = "<bold>천왕성"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>화살 발사 시, 화살 대신 고드름을 발사한다.",
            "<gray>고드름 적중 시 화살 피해의 66%에 해당하는 피해를 입히고 {keyword:Frostbite}을 4 부여한다.",
            "<gray>고드름으로 {keyword:Freezing} 상태인 적에게 피해를 입힐 때, 고드름의 피해는 기본 공격으로 간주된다."
        )
    }

    companion object {
        private val icicleKey: NamespacedKey
            get() = NamespacedKey(ClassWarPlugin.instance, "uranus-icicle")

        fun handleBowShot(event: EntityShootBowEvent) {
            val shooter = event.entity as? Player ?: return
            val arrow = event.projectile as? AbstractArrow ?: return
            val playerData = findGameForPlayer(shooter)?.playerDatas?.filterIsInstance<PlayerData>()
                ?.find { it.uniqueId == shooter.uniqueId } ?: return
            val abilityScope = playerData.findGameClass(Uranus::class.java)?.abilityScope ?: return
            if (!PlanetPowerRegistry.hasPower(playerData, Uranus::class.java)) return

            arrow.persistentDataContainer.set(icicleKey, PersistentDataType.BYTE, 1)
            arrow.isVisibleByDefault = false
            arrow.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED
            arrow.isSilent = true
            Bukkit.getOnlinePlayers().forEach { viewer ->
                viewer.hideEntity(ClassWarPlugin.instance, arrow)
            }
            val display = arrow.world.spawn(arrow.location, ItemDisplay::class.java).apply {
                setItemStack(ItemStack(Material.PRISMARINE_SHARD))
                itemDisplayTransform = ItemDisplay.ItemDisplayTransform.NONE
                billboard = Display.Billboard.FIXED
                brightness = Display.Brightness(15, 15)
                interpolationDuration = 1
                teleportDuration = 1
                isPersistent = false
            }
            AbilityExecution.with(abilityScope) { TemporaryDisplayManager.mark(display, shooter.uniqueId) }
            orientIcicle(display, arrow)

            playerData.trackTask(object : BukkitRunnable(abilityScope) {
                override fun run() {
                    if (!arrow.isValid || arrow.isDead || arrow.isInBlock || arrow.isOnGround) {
                        display.remove()
                        cancel()
                        return
                    }
                    display.teleport(arrow.location)
                    orientIcicle(display, arrow)
                    ParticleApi.spawn(arrow.location, Particle.SNOWFLAKE, 3, 0.08, 0.012)
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
            SoundApi.play(shooter.location, Sound.BLOCK_GLASS_BREAK, 0.35f, 1.75f)
        }

        fun isIcicle(projectile: org.bukkit.entity.Entity): Boolean =
            projectile.persistentDataContainer.get(icicleKey, PersistentDataType.BYTE) == 1.toByte()

        fun applySuccessfulIcicleHit(target: EntityData, attacker: PlayerData) {
            target.getOrCreateStatus(attacker) { Frostbite() }
                .applyStatus(duration = 5, powerDelta = 4)
            val center = target.entity.boundingBox.center.toLocation(target.entity.world)
            ParticleApi.spawn(center, Particle.SNOWFLAKE, ParticleOptions(22, 0.45, 0.55, 0.45, 0.07))
            ParticleApi.spawn(center, Particle.ITEM, ItemStack(Material.ICE), ParticleOptions(10, 0.35, 0.4, 0.35, 0.06))
            SoundApi.play(center, Sound.BLOCK_GLASS_BREAK, 0.65f, 1.25f)
        }

        private fun orientIcicle(display: ItemDisplay, arrow: AbstractArrow) {
            val velocity = arrow.velocity
            if (velocity.lengthSquared() < 1.0E-8) return
            DisplayOrientationUtil.alignSwordBladeVertically(display, velocity, scale = 1.5f)
        }
    }
}
