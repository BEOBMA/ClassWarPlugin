package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Flooring
import org.beobma.classWarPlugin.skill.Projectile
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Abyss
import org.beobma.classWarPlugin.status.list.Mana
import org.beobma.classWarPlugin.status.list.Erosion
import org.beobma.classWarPlugin.status.list.Silence
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class AbyssalVeil : GameClass() {
    override val name = "<gray>심연 장막"
    override val rank = Rank.C
    override val classItemMaterial = Material.BLACK_CONCRETE
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class RedSkill : Skill() {
        override val name = "<bold>검은 연기"
        override val description = listOf(
            "<gray>자신 위치에 8초간 유지되는 검은 연기를 형성한다.",
            "<gray>자신은 영역 안에서 {keyword:Stealth} 상태가 된다.",
            "",
            "<dark_gray>웅크린 상태에서 사용하면 4칸 내의 바라보는 블럭에 연기를 형성할 수도 있다."
        )
        override val cooldown = 35

        override fun use() {
            val smoke = Smoke()
            smoke.inject(playerData)

            smoke.location = if (player.isSneaking) {
                playerData.shotLaserGetBlock(4.0)?.location?.add(0.5, 1.0, 0.5) ?: run {
                    player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                    return
                }
            } else {
                player.location.clone()
            }
            smoke.spawnFlooring(playerData)
            sounds.play(smoke.location, Sound.ENTITY_WITHER_AMBIENT, volume = 0.7f, pitch = 0.55f)
        }

        override fun isUseSuccess(): Boolean {
            if (player.isSneaking) {
                playerData.shotLaserGetBlock(4.0)?.location?.add(0.5, 1.0, 0.5) ?: run {
                    player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                    return false
                }
            }

            return true
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<bold>잠식"
        override val description = listOf(
            "<gray>바라보는 방향으로 잠식된 연기를 발사한다.",
            "<gray>적중한 모든 적에게 5의 피해를 입히고 4초간 {keyword:Abyss} 상태로 만든다.",
            "<gray>대상이 {keyword:Erosion} 상태였다면 소모하여 대상을 3초간 {keyword:Silence} 상태로 만든다.",
        )
        override val cooldown = 35

        override fun use() {
            val projectile = ProjectileSmoke()
            projectile.location = player.location.clone()

            projectile.spawnProjectile(playerData)
            sounds.play(player, Sound.ENTITY_WARDEN_SONIC_CHARGE, volume = 0.48f, pitch = 0.62f)
        }

        override fun isUseSuccess(): Boolean {
            return true
        }
    }


    private class Smoke : Flooring() {
        private val applied = mutableListOf<PlayerData>()
        private var ownerStealth: Stealth? = null

        override lateinit var location: Location
        override var radius: Double = 5.0
        override var targetType: TargetType = TargetType.All
        override var time: Int? = 8

        private var visualTick = 0

        override fun onFlooringContinue(location: Location) {
            val tick = visualTick++
            if (tick % 3 == 0) {
                particles.spawn(
                    location.clone().add(0.0, 1.35, 0.0),
                    Particle.BLOCK,
                    Material.BLACK_CONCRETE.createBlockData(),
                    ParticleOptions(count = 34, offsetX = 3.9, offsetY = 1.45, offsetZ = 3.9, speed = 0.025),
                )
                particles.spawn(
                    location.clone().add(0.0, 1.2, 0.0),
                    Particle.SQUID_INK,
                    count = 10,
                    spread = 3.4,
                    speed = 0.012,
                )
            }
            if (tick % 5 == 0) {
                particles.circle(location, Particle.LARGE_SMOKE, radius, 32)
            }
        }

        override fun onFlooringEntityHit(hitEntityData: EntityData, location: Location) {
            val hitPlayerData = hitEntityData as? PlayerData ?: return
            if (hitPlayerData == playerData) {
                if (ownerStealth == null) {
                    val stealth = Stealth()
                    playerData.addStatus(stealth, playerData)
                    stealth.applyStatus(powerSet = 1)
                    ownerStealth = stealth
                }
                return
            }
            hitPlayerData.player.isGlowing = true
            applied.add(hitPlayerData)
        }

        override fun onFlooringEntityOut(hitEntityData: EntityData, location: Location) {
            val hitPlayerData = hitEntityData as? PlayerData ?: return
            if (hitPlayerData == playerData) {
                ownerStealth?.remove(); ownerStealth = null
                return
            }
            hitPlayerData.player.isGlowing = false
            applied.remove(hitPlayerData)
        }

        override fun onFlooringEnd() {
            applied.forEach { playerData ->
                playerData.player.isGlowing = false
            }
            ownerStealth?.remove()
        }
    }

    private class Passive : BasePassive(), OnHitHandler {
        override val name = "<bold>잠식"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 적중 시 대상을 {keyword:Erosion} 상태로 만든다."
        )

        override fun onAttackHit(context: DamageContext) {
            val erosion = context.target.getOrCreateStatus(playerData) { Erosion() }
            erosion.applyStatus(duration = 8, powerSet = 1)
            particles.spawn(context.target.entity, Particle.SQUID_INK, count = 8, spread = 0.3)
            sounds.play(context.target.entity, Sound.BLOCK_SCULK_SPREAD, volume = 0.5f, pitch = 0.7f)
        }
    }

    private class ProjectileSmoke : Projectile() {
        override lateinit var location: Location
        override var targetType: TargetType = TargetType.Enemy
        override var speed: Double = 0.5
        override var isWallHit: Boolean = false
        override var isPlayerHit: Boolean = true
        override val isPlayerHitRemove: Boolean = false
        override var time: Int? = 3

        private val hitSet = mutableSetOf<java.util.UUID>()

        override fun onProjectileEntityHit(hitEntityData: EntityData, location: Location) {
            if (hitSet.add(hitEntityData.entity.uniqueId)) {
                val abyss = hitEntityData.getOrCreateStatus(playerData) { Abyss() }
                abyss.applyStatus(duration = 4)
                hitEntityData.getStatus<Erosion>()?.let { erosion ->
                    erosion.remove()
                    hitEntityData.getOrCreateStatus(playerData) { Silence() }.applyStatus(duration = 3, powerSet = 1)
                }
                hitEntityData.damage(5.0, DamageType.Normal, playerData)
                particles.spawn(hitEntityData.entity, Particle.SQUID_INK, count = 10, spread = 0.4)
                sounds.play(hitEntityData.entity, Sound.ENTITY_ENDERMAN_HURT, pitch = 0.6f)
                return
            }
        }

        override fun onProjectileMove(location: Location) {
            particles.spawn(location, Particle.SQUID_INK, count = 2, spread = 0.12)
        }
    }
}
