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
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
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
import kotlin.math.ceil

class WoundsWind : GameClass(), OnHitHandler, WeaponInputHandler {
    override val classId = "wounds-wind"
    override val name = "<gray>바람의 상처"
    override val rank = Rank.A
    override val classItemMaterial = Material.WIND_CHARGE
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private var slashReadyTick = 0L

    override fun onWeaponRightClick(event: PlayerInteractEvent) {
        event.isCancelled = true
        if (!playerStatus.canAttack || playerStatus.isDead) return
        val now = game.combatTick
        if (now < slashReadyTick) {
            player.sendMiniMessage("<red><bold>[!] 기본 공격을 다시 사용할 수 있을 때까지 검기를 발사할 수 없습니다.")
            return
        }
        slashReadyTick = now + ceil(player.cooldownPeriod.toDouble()).toLong().coerceAtLeast(1L)
        launchSlash(player.eyeLocation.clone())
        player.resetCooldown()
    }

    override fun onAttackHit(context: DamageContext) {
        if (context.path != DamagePath.BASIC_ATTACK) return
        context.isCancelled = true
    }

    private fun launchSlash(start: Location) {
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
        override val description = listOf(
            "<gray>우클릭하면 바라보는 방향으로 적을 관통하는 검기를 날린다.",
            "<gray>검기 발사 후 기본 공격 재사용 대기 시간이 적용된다."
        )
    }

    private class Passive : BasePassive() {
        override val name = "<bold>검기"
        override val description = listOf(
            "<gray>패시브", "", "<gray>기본 공격으로 피해를 입힐 수 없다.",
            "<gray>검을 우클릭하면 검기가 날아가 적중한 모든 적에게 2의 피해를 입힌다."
        )
    }
}
