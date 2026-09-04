package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.OnSkillUseHandler
import org.beobma.classWarPlugin.gameClass.handler.WeaponInputHandler
import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.getConeTargets
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.Vibration
import org.beobma.classWarPlugin.status.list.VibrationExplosion
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.*
import org.bukkit.event.player.PlayerInteractEvent
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val GUN_BLADER_BREAKTHROUGH_COOLDOWN_SECONDS = 12
private const val GUN_BLADER_FULL_BURST_COOLDOWN_SECONDS = 55
private const val GUN_BLADER_BASIC_DAMAGE = 2.0
private const val GUN_BLADER_BREAKTHROUGH_DAMAGE = 4.0
private const val GUN_BLADER_FULL_BURST_DAMAGE = 2.0
private const val GUN_BLADER_VIBRATION_DURATION_SECONDS = 10
private const val GUN_BLADER_BREAKTHROUGH_VIBRATION_POWER = 3
private const val GUN_BLADER_VIBRATION_POWER = 1

class GunBlader : GameClass(), WeaponInputHandler, GameStatusHandler, OnSkillUseHandler {
    override val classId = "gun-blader"
    override val name = "<gray>총검사"
    override val rank = Rank.A
    override val classItemMaterial = Material.IRON_SWORD
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private var basicHits = 0
    private var idleSeconds = 0

    private fun bulletStatus(): BulletStatus =
        playerData.getOrCreateStatus(playerData) { BulletStatus() }

    private class BulletStatus : StatusAbnormality() {
        override val name = Keyword.Bullet.string
        override val description = listOf(Keyword.Bullet.description ?: "")
        override val canRemove = false
        override val isClassMechanic = true
        override var maxPower: Int? = 4
        override var duration: Int? = null
    }

    override fun onBattleStart() { bulletStatus().updatePower(4); idleSeconds = 0 }
    override fun onGameTimePasses() {
        if (++idleSeconds >= 20) bulletStatus().updatePower(4)
    }
    override fun onSkillUse(event: PlayerSkillUseEvent) { idleSeconds = 0 }

    override fun onWeaponRightClick(event: PlayerInteractEvent) {
        event.isCancelled = true
        val bullets = bulletStatus()
        if (bullets.power <= 0) { player.sendMiniMessage("<red><bold>[!] 탄환이 없습니다."); return }
        bullets.decreasePower(1); idleSeconds = 0
        val start = player.eyeLocation
        val target = playerData.shotLaserGetEntityData(24.0, TargetType.Enemy, false)
        val end = target?.entity?.location?.add(0.0, target.entity.height / 2, 0.0) ?: start.clone().add(start.direction.multiply(24))
        particles.line(start, end, Particle.SMOKE, 0.25)
        sounds.play(player, Sound.ENTITY_FIREWORK_ROCKET_BLAST, pitch = 1.5f)
        target?.let {
            it.damage(GUN_BLADER_BASIC_DAMAGE, DamageType.Normal, playerData)
            it.addStatus(VibrationExplosion(), playerData).applyStatus(duration = 1, powerDelta = 1)
        }
    }

    private class Weapon : BaseWeapon() {
        override val name = "<gray>총검"
        override val description = listOf(
            "<gray>기본 공격 적중 시 10초간 {keyword:Vibration}을 1 부여한다.",
            "",
            "<gray>우클릭하면 {keyword:Bullet}을 1발 소모하여 바라보는 방향으로 사격한다.",
            "<gray>사격은 적중한 적에게 2의 피해를 입히고 {keyword:VibrationExplosion}을 적용한다."
        )
        override val material = Material.IRON_SWORD
    }

    private inner class RedSkill : Skill(), org.beobma.classWarPlugin.skill.MovementSkill {
        override val definitionId = "gun-blader/red-skill"
        override val name = "<bold>돌파"
        override val description = listOf(
            "<gray>바라보는 방향으로 3칸 돌진한다.",
            "{keyword:Bullet}이 있다면 소모하여 대신 6칸 돌진한다.",
            "",
            "<gray>처음 충돌한 적을 베어 4의 피해를 입히고 10초간 {keyword:Vibration}을 3 부여한다."
        )
        override val cooldown = GUN_BLADER_BREAKTHROUGH_COOLDOWN_SECONDS

        override fun use(): Boolean {
            val bullets = bulletStatus()
            val boosted = bullets.power > 0
            if (boosted) bullets.decreasePower(1)
            idleSeconds = 0
            player.velocity = player.location.direction.normalize().multiply(if (boosted) 1.8 else 1.05).setY(0.12)
            val target = playerData.getConeTargets(if (boosted) 6.0 else 3.0, 45.0, TargetType.Enemy, false, hitAttackableObjects = true).firstOrNull()
            target?.let {
                it.damage(GUN_BLADER_BREAKTHROUGH_DAMAGE, DamageType.Normal, playerData)
                it.getOrCreateStatus(playerData) { Vibration() }.applyStatus(
                    duration = GUN_BLADER_VIBRATION_DURATION_SECONDS,
                    powerDelta = GUN_BLADER_BREAKTHROUGH_VIBRATION_POWER,
                )
                particles.spawn(it.entity, Particle.SWEEP_ATTACK, count = 2, spread = 0.2)
            }
            sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, pitch = 0.8f)
            return true
        }
    }

    private inner class OrangeSkill : Skill() {
        override val definitionId = "gun-blader/orange-skill"
        override val name = "<bold>전탄 격발"
        override val description = listOf(
            "<gray>16칸 내의 바라보는 적을 조준하고 장전된 {keyword:Bullet}을 모두 소모하여 사격한다.",
            "<gray>{keyword:Bullet}마다 2의 피해를 입히고, 10초간 {keyword:Vibration}을 1 부여한다.",
            "<gray>마지막 {keyword:Bullet}이 적중하면 {keyword:VibrationExplosion}을 적용한다."
        )
        override val cooldown = GUN_BLADER_FULL_BURST_COOLDOWN_SECONDS

        override fun use(): Boolean {
            val target = playerData.shotLaserGetEntityData(16.0, TargetType.Enemy, false) ?: return false
            val bullets = bulletStatus()
            val shots = bullets.power
            bullets.updatePower(0); idleSeconds = 0
            repeat(shots) { index ->
                target.damage(GUN_BLADER_FULL_BURST_DAMAGE, DamageType.Normal, playerData)
                target.getOrCreateStatus(playerData) { Vibration() }.applyStatus(
                    duration = GUN_BLADER_VIBRATION_DURATION_SECONDS,
                    powerDelta = GUN_BLADER_VIBRATION_POWER,
                )
                if (index == shots - 1) target.addStatus(VibrationExplosion(), playerData).applyStatus(duration = 1, powerDelta = 1)
            }
            particles.line(player.eyeLocation, target.entity.location.add(0.0, target.entity.height / 2, 0.0), Particle.ELECTRIC_SPARK, 0.2)
            sounds.play(player, Sound.ENTITY_GENERIC_EXPLODE, volume = 1.3f, pitch = 1.7f)
            return true
        }

        override fun isUseSuccess(): Boolean {
            if (bulletStatus().power <= 0) { player.sendMiniMessage("<red><bold>[!] 탄환이 없습니다."); return false }
            if (playerData.shotLaserGetEntityData(16.0, TargetType.Enemy, false) != null) return true
            player.sendMiniMessage("<red><bold>[!] 바라보는 적이 없습니다.")
            return false
        }
    }

    private inner class Passive : BasePassive(), OnHitHandler {
        override val name = "<bold>총검술"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 3회 적중 시 {keyword:Bullet}을 1 얻는다. ({keyword:Bullet}은 최대 4발 얻을 수 있다.)",
            "<gray>20초간 기본 공격, 스킬을 사용하지 않으면 최대 4발까지 장전한다."
        )

        override fun onAttackHit(context: DamageContext) {
            idleSeconds = 0
            context.target.getOrCreateStatus(playerData) { Vibration() }.applyStatus(
                duration = GUN_BLADER_VIBRATION_DURATION_SECONDS,
                powerDelta = GUN_BLADER_VIBRATION_POWER,
            )
            if (++basicHits >= 3) {
                basicHits = 0;
                bulletStatus().increasePower(1)
                sounds.playTo(player, Sound.BLOCK_IRON_TRAPDOOR_OPEN, pitch = 1.8f)
            }
        }
    }
}
