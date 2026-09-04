package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.MovementSkill
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound

private const val CONFLICT_TELEPORT_DISTANCE = 8.0

class Conflict : GameClass() {
    override val classId = "conflict"
    override val name = "<gray>불화"
    override val rank = Rank.B
    override val classItemMaterial = Material.STICK
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = emptyList()

    private inner class RedSkill : Skill(), MovementSkill {
        override val definitionId = "conflict/red-skill"
        override val name = "<bold>불화"
        override val description = listOf(
            "<gray>바라보는 방향으로 순간이동한다.",
            "<gray>3초 내에 다시 사용하면 자신은 피해를 2 받는다.",
            "<gray>여러번 사용할 때마다 피해량이 증가한다."
        )
        override val cooldown = 0

        override fun use(): Boolean {
            val direction = player.eyeLocation.direction.normalize()
            val teleportDistance = ClassBalanceManager.scaleRange(playerData, CONFLICT_TELEPORT_DISTANCE)
            val ray = player.world.rayTraceBlocks(
                player.eyeLocation, direction, teleportDistance,
                FluidCollisionMode.NEVER, true,
            )
            val maximumDistance = (ray?.hitPosition?.distance(player.eyeLocation.toVector())
                ?.minus(0.65) ?: teleportDistance).coerceAtLeast(0.0)
            val start = player.location.clone()
            var destination = start.clone()
            var distance = 0.25
            while (distance <= maximumDistance) {
                val candidate = start.clone().add(direction.clone().multiply(distance))
                if (!candidate.block.isPassable || !candidate.clone().add(0.0, 1.0, 0.0).block.isPassable) break
                destination = candidate
                distance += 0.25
            }
            destination.yaw = start.yaw
            destination.pitch = start.pitch
            player.teleport(destination)
            particles.line(start.clone().add(0.0, 1.0, 0.0), destination.clone().add(0.0, 1.0, 0.0), Particle.REVERSE_PORTAL, 0.28)
            particles.spawn(destination.clone().add(0.0, 1.0, 0.0), Particle.WITCH, count = 24, spread = 0.45, speed = 0.08)
            sounds.play(start, Sound.ENTITY_ENDERMAN_TELEPORT, volume = 0.7f, pitch = 1.35f)

            val chain = playerData.getOrCreateStatus(playerData) { ConflictChainStatus() }
            val repeatedDamage = chain.power
            chain.applyStatus(duration = 3, powerSet = repeatedDamage + 2)
            if (repeatedDamage > 0) {
                playerData.damage(repeatedDamage.toDouble(), DamageType.True, playerData, damagePath = DamagePath.STATUS_EFFECT)
                particles.spawn(player, Particle.DAMAGE_INDICATOR, count = (repeatedDamage / 2) * 5, spread = 0.35, speed = 0.08)
                sounds.play(player, Sound.ENTITY_PLAYER_HURT, volume = 0.65f, pitch = 0.75f)
            }
            return true
        }
    }
}

private class ConflictChainStatus : StatusAbnormality() {
    override val name = "<light_purple><bold>불화 연쇄</bold><gray>"
    override val description = listOf("<gray>지속 시간 안에 불화를 다시 사용하면 표시된 수치만큼 고정 피해를 받는다.")
    override val canRemove = true
    override val isClassMechanic = true
    override var power = 0
    override var maxPower: Int? = null
    override val showMaxPower = false
    override var duration: Int? = null
}
