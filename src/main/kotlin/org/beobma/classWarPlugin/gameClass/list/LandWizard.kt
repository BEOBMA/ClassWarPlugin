package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Mana
import org.beobma.classWarPlugin.status.list.Shield
import org.beobma.classWarPlugin.status.list.Vibration
import org.beobma.classWarPlugin.status.list.VibrationExplosion
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.damage.DamageContext
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.beobma.classWarPlugin.effect.ParticleOptions

// 밸런스 조정 상수
private const val LAND_WIZARD_EARTHQUAKE_COOLDOWN_SECONDS = 3
private const val LAND_WIZARD_RESONANCE_COOLDOWN_SECONDS = 18
private const val LAND_WIZARD_EARTHQUAKE_DAMAGE = 2.0
private const val LAND_WIZARD_VIBRATION_DURATION_SECONDS = 10
private const val LAND_WIZARD_VIBRATION_POWER = 2
private const val LAND_WIZARD_SHIELD_DURATION_SECONDS = 5
private const val LAND_WIZARD_SHIELD_POWER = 8
private const val LAND_WIZARD_SHIELD_DAMAGE_TAKEN_MULTIPLIER = 0.7

class LandWizard : GameClass(), GameStatusHandler {
    override val name = "<gray>지맥술사"
    override val rank = Rank.B
    override val classItemMaterial = Material.SANDSTONE

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    override fun onBattleStart() {
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        mana.increasePower(100)
    }

    override fun onGameTimePasses() {
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        mana.increasePower(10)
    }

    private class RedSkill : Skill() {
        override val name = "<bold>지진"
        override val description = listOf(
            "{keyword:Mana}를 20 소모하고 사용할 수 있다.",
            "",
            "<gray>사용 시 주위 모든 적에게 2의 피해를 입히고 10초간 {keyword:Vibration}을 2 부여한다."
        )
        override val cooldown = LAND_WIZARD_EARTHQUAKE_COOLDOWN_SECONDS

        override fun use() {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            mana.decreasePower(20)
            val targets = playerData.radius(player.location, TargetType.Enemy, 4.0, false)
            targets.forEach {
                val vibration = it.getOrCreateStatus(playerData) { Vibration() }
                vibration.applyStatus(
                    duration = LAND_WIZARD_VIBRATION_DURATION_SECONDS,
                    powerDelta = LAND_WIZARD_VIBRATION_POWER,
                )
                it.damage(LAND_WIZARD_EARTHQUAKE_DAMAGE, DamageType.Normal, playerData)
            }
            listOf(1.5, 2.7, 4.0).forEachIndexed { index, radius ->
                particles.circle(player.location.clone().add(0.0, 0.08 + index * 0.03, 0.0), Particle.DUST_PLUME, radius, 24 + index * 8)
            }
            particles.spawn(
                player.location.clone().add(0.0, 0.15, 0.0),
                Particle.BLOCK,
                Material.DEEPSLATE.createBlockData(),
                ParticleOptions.spread(count = 32, spread = 3.2, speed = 0.09),
            )
            particles.spawn(player.location, Particle.CAMPFIRE_COSY_SMOKE, count = 12, spread = 2.8, speed = 0.02)
            sounds.play(player, Sound.ENTITY_GENERIC_EXPLODE, volume = 0.48f, pitch = 0.72f)
        }

        override fun isUseSuccess(): Boolean {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            if (mana.power < 20) {
                player.sendMiniMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
                return false
            }
            return true
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<bold>공진"
        override val description = listOf(
            "{keyword:Mana}를 60 소모하고 사용할 수 있다.",
            "",
            "<gray>사용 시 5초간 <aqua><bold>8의 피해를 막는 {keyword:Shield}을 얻고 주위 모든 적에게 {keyword:VibrationExplosion}을 적용한다."
        )
        override val cooldown = LAND_WIZARD_RESONANCE_COOLDOWN_SECONDS

        override fun use() {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            val shield = playerData.addStatus(Shield(), playerData)

            mana.decreasePower(60)
            shield.applyStatus(
                duration = LAND_WIZARD_SHIELD_DURATION_SECONDS,
                powerDelta = LAND_WIZARD_SHIELD_POWER,
            )

            val targets = playerData.radius(player.location, TargetType.Enemy, 4.0, false)
            targets.forEach {
                val vibrationExplosion = it.addStatus(VibrationExplosion(), playerData)
                vibrationExplosion.applyStatus(duration = 1, powerDelta = 1)
            }
            listOf(1.5, 2.8, 4.0).forEachIndexed { index, radius ->
                particles.circle(player.location.clone().add(0.0, 0.3 + index * 0.18, 0.0), Particle.ENCHANT, radius, 28 + index * 8)
            }
            particles.spawn(player.location.clone().add(0.0, 1.0, 0.0), Particle.REVERSE_PORTAL, count = 42, spread = 1.6, speed = 0.08)
            particles.spawn(player.location.clone().add(0.0, 0.5, 0.0), Particle.ELECTRIC_SPARK, count = 24, spread = 2.5, speed = 0.06)
            sounds.play(player, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume = 0.85f, pitch = 0.9f)
        }

        override fun isUseSuccess(): Boolean {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            if (mana.power < 60) {
                player.sendMiniMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
                return false
            }
            return true
        }
    }

    private class Passive : BasePassive(), WhenHitHandler {
        override val name = "<bold>암석화"
        override val description = listOf(
            "{keyword:Shield}을 보유한 동안 <gold><bold>기본 공격으로 받는 피해가 30% 감소</bold><gray>한다."
        )

        override fun whenAttackHit(event: DamageContext) {
            if (playerData.hasStatus<Shield>()) {
                event.addDamageTakenMultiplier(LAND_WIZARD_SHIELD_DAMAGE_TAKEN_MULTIPLIER)
            }
        }
    }
}
