package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WeaponInputHandler
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
import org.bukkit.block.Block
import org.bukkit.event.player.PlayerInteractEvent
import java.util.UUID

class WoundsWind : GameClass(), OnHitHandler, WeaponInputHandler {
    override val name = "<gray>바람의 상처"
    override val rank = Rank.A
    override val classItemMaterial = Material.WIND_CHARGE
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private var lastSlashTick = Long.MIN_VALUE

    override fun onWeaponLeftClick(event: PlayerInteractEvent) {
        event.isCancelled = true
        launchSlashFromSwing()
    }

    /** 적이나 블록을 실제로 맞히지 않은 팔 휘두르기 입력에서도 검기를 발사한다. */
    fun launchSlashFromSwing() {
        if (!playerStatus.canAttack || playerStatus.isDead) return
        launchSlash(player.eyeLocation.clone())
    }

    override fun onAttackHit(context: DamageContext) {
        if (context.path != DamagePath.BASIC_ATTACK) return
        context.isCancelled = true
        val direction = context.target.entity.boundingBox.center.subtract(player.eyeLocation.toVector()).normalize()
        launchSlash(player.eyeLocation.clone().setDirection(direction))
    }

    private fun launchSlash(start: Location) {
        val now = org.bukkit.Bukkit.getCurrentTick().toLong()
        if (now - lastSlashTick < 5L) return
        lastSlashTick = now
        WindSlashProjectile(start).spawnProjectile(playerData)
        sounds.play(player, Sound.ENTITY_BREEZE_SHOOT, volume = 0.8f, pitch = 1.25f)
    }

    private inner class WindSlashProjectile(override var location: Location) : Projectile() {
        override var targetType = TargetType.Enemy
        override var speed = 1.35
        override var isWallHit = true
        override var isPlayerHit = true
        override val isPlayerHitRemove = false
        override var time: Int? = 2
        override var xSize = 0.75
        override var ySize = 0.7
        override var zSize = 0.75
        private val hit = mutableSetOf<UUID>()

        override fun onProjectileMove(location: Location) {
            particles.spawn(location, Particle.SWEEP_ATTACK, count = 1)
            particles.spawn(location, Particle.CLOUD, count = 3, spread = 0.18, speed = 0.025)
        }

        override fun onProjectileEntityHit(hitEntityData: EntityData, location: Location) {
            if (!hit.add(hitEntityData.entity.uniqueId)) return
            hitEntityData.damage(2.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
            particles.spawn(hitEntityData.entity, Particle.CRIT, count = 14, spread = 0.4, speed = 0.11)
            sounds.play(hitEntityData.entity, Sound.ENTITY_PLAYER_ATTACK_SWEEP, volume = 0.8f, pitch = 0.85f)
        }

        override fun onProjectileBlockHit(hitBlock: Block, location: Location) {
            particles.spawn(location, Particle.CLOUD, count = 12, spread = 0.35, speed = 0.08)
        }
    }

    private class Weapon : BaseWeapon() {
        override val name = "<gray>검기검"
        override val material = Material.IRON_SWORD
        override val description = listOf("<gray>기본 공격 대신 적을 관통하는 검기를 날린다.")
    }

    private class Passive : BasePassive() {
        override val name = "<bold>검기"
        override val description = listOf(
            "<gray>패시브", "", "<gray>기본 공격을 할 수 없다.",
            "<gray>대신 검기가 날아가 적중한 모든 적에게 2의 피해를 입힌다."
        )
    }
}
