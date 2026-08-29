package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val PACIFIST_ATTACK_DAMAGE = 0.0
private const val PACIFIST_HORIZONTAL_KNOCKBACK = 2.15
private const val PACIFIST_VERTICAL_KNOCKBACK = 0.62
private const val PACIFIST_BORDER_CHECK_TICKS = 50

class Pacifist : GameClass() {
    override val name = "<gray>평화주의자"
    override val rank = Rank.A
    override val classItemMaterial = Material.BARRIER
    override var skills: List<Skill> = listOf()
    override var passives: List<BasePassive> = listOf(Passive())

    private inner class Passive : BasePassive(), OnHitHandler {
        override val name = "<bold>평화"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>기본 공격으로 가하는 피해가 0이 된다.",
            "<gray>기본 공격 적중 시 적을 크게 밀친다.",
            "<gray>밀치는 효과로 적을 월드보더 밖으로 밀쳐내면 해당 적은 탈락한다."
        )

        override fun onAttackHit(context: DamageContext) {
            context.capDamage(PACIFIST_ATTACK_DAMAGE)
            val target = context.target.entity
            val attackerCenter = player.boundingBox.center
            val targetCenter = target.boundingBox.center
            val direction = targetCenter.clone().subtract(attackerCenter).setY(0.0)
            if (direction.lengthSquared() < 1.0E-8) {
                direction.copy(player.location.direction).setY(0.0)
            }
            if (direction.lengthSquared() < 1.0E-8) direction.copy(Vector(1.0, 0.0, 0.0))
            target.velocity = direction.normalize()
                .multiply(PACIFIST_HORIZONTAL_KNOCKBACK)
                .setY(PACIFIST_VERTICAL_KNOCKBACK)

            particles.line(
                player.eyeLocation,
                target.location.clone().add(0.0, target.height * 0.5, 0.0),
                Particle.CLOUD,
                0.18,
            )
            particles.spawn(target, Particle.EXPLOSION, count = 2, spread = 0.3)
            sounds.play(target, Sound.ENTITY_IRON_GOLEM_ATTACK, volume = 0.8f, pitch = 1.5f)

            val targetPlayer = context.target as? PlayerData ?: return
            playerData.trackTask(object : BukkitRunnable() {
                private var elapsedTicks = 0

                override fun run() {
                    if (!targetPlayer.player.isOnline || targetPlayer.entityStatus.isDead) {
                        cancel()
                        return
                    }
                    if (!targetPlayer.player.world.worldBorder.isInside(targetPlayer.player.location)) {
                        particles.spawn(targetPlayer.player, Particle.EXPLOSION_EMITTER, count = 1)
                        sounds.play(targetPlayer.player, Sound.ENTITY_WITHER_BREAK_BLOCK, volume = 0.8f, pitch = 1.25f)
                        targetPlayer.damage(
                            targetPlayer.player.health + 100.0,
                            DamageType.True,
                            playerData,
                            bypassShield = true,
                            damagePath = DamagePath.SKILL,
                        )
                        cancel()
                        return
                    }
                    if (++elapsedTicks >= PACIFIST_BORDER_CHECK_TICKS) cancel()
                }
            }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L))
        }
    }
}
