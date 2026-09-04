package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.*
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import java.util.UUID

// 밸런스 조정 상수
private const val DUELIST_FENTE_COOLDOWN_SECONDS = 5
private const val DUELIST_EN_GARDE_COOLDOWN_SECONDS = 70
private const val DUELIST_FENTE_DAMAGE = 2.0
private const val DUELIST_FENTE_FINISH_DAMAGE = 6.0
private const val DUELIST_MARK_DURATION_SECONDS = 15
private const val DUELIST_OPPONENT_DAMAGE_MULTIPLIER = 1.3
private const val DUELIST_OUTSIDER_DAMAGE_MULTIPLIER = 0.7
private const val DUELIST_DISRUPTED_DAMAGE_TAKEN_MULTIPLIER = 1.25

class Duelist : GameClass() {
    override val classId = "duelist"
    override val name = "<gray>결투가"
    override val rank = Rank.C
    override val classItemMaterial = Material.SPECTRAL_ARROW
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private var opponentId: UUID? = null
    private var duelUntil = 0L
    private var fenteChain = 0
    private var disrupted = false

    private fun inDuel(): Boolean = opponentId != null && game.combatTick < duelUntil

    private class DuelMark(private val duelistId: UUID) : StatusAbnormality(), WhenHitHandler {
        override val name = "<gold><bold>결투"
        override val description = listOf("<gray>결투 중 받는 피해가 공격자에 따라 변경된다.")
        override val canRemove = true
        override var power = 1
        override var duration: Int? = DUELIST_MARK_DURATION_SECONDS
        override val showPower = false
        override val showMaxPower = false
        override fun whenHit(context: DamageContext) {
            context.addDamageTakenMultiplier(
                if (context.attacker.uniqueId == duelistId) DUELIST_OPPONENT_DAMAGE_MULTIPLIER
                else DUELIST_OUTSIDER_DAMAGE_MULTIPLIER
            )
        }
    }

    private inner class RedSkill : Skill(), org.beobma.classWarPlugin.skill.MovementSkill {
        override val definitionId = "duelist/red-skill"
        override val name = "<bold>팡트"
        override val description = listOf(
            "<gray>바라보는 방향으로 짧게 도약한다.",
            "<gray>이후 2칸 내의 가장 가까운 적에게 2의 피해를 입힌다.",
            "",
            "<dark_gray>결투 상대를 우선으로 공격한다."
        )
        override val cooldown = DUELIST_FENTE_COOLDOWN_SECONDS

        override fun use(): Boolean {
            player.velocity = player.location.direction.normalize().multiply(1.15).setY(0.18)
            sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, pitch = 1.5f)
            playerData.trackTask(object : BukkitRunnable(abilityScope) {
                override fun run() {
                    val candidates = playerData.radius(player.location, TargetType.Enemy, 2.0, false, hitAttackableObjects = true)
                    val target = candidates.firstOrNull { it.entity.uniqueId == opponentId }
                        ?: candidates.minByOrNull { HitboxUtil.distanceSquared(it.entity.boundingBox, player.boundingBox) }
                    if (target == null) {
                        if (inDuel()) disrupted = true
                        sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, pitch = 0.8f)
                        return
                    }
                    target.damage(DUELIST_FENTE_DAMAGE, DamageType.Normal, playerData)
                    particles.line(player.eyeLocation, target.entity.location.add(0.0, target.entity.height / 2.0, 0.0), Particle.CRIT, 0.2)
                    if (inDuel() && target.entity.uniqueId == opponentId) {
                        disrupted = false
                        fenteChain++
                        CooldownManager.reduceCooldown(player, this@RedSkill, 40)
                        if (fenteChain >= 3) {
                            target.damage(DUELIST_FENTE_FINISH_DAMAGE, DamageType.Normal, playerData)
                            fenteChain = 0
                            sounds.play(target.entity, Sound.ENTITY_PLAYER_ATTACK_CRIT, pitch = 0.7f)
                        }
                    } else fenteChain = 0
                }
            }.runTaskLater(ClassWarPlugin.instance, 4L))
            return true
        }
    }

    private inner class OrangeSkill : Skill() {
        override val definitionId = "duelist/orange-skill"
        override val name = "<bold>앙 가르드"
        override val description = listOf(
            "<gray>10칸 내의 바라보는 적에게 15초간 결투를 선포한다.",
            "",
            "<gray>자신과 적은 서로의 공격으로 받는 피해가 30% 증가하고,",
            "<gray>다른 대상에게 받는 피해는 30% 감소한다.",
            "",
            "<gray>결투 상대에게 팡트를 3번 연속 적중시키면 추가로 6의 피해를 입힌다.",
            "<gray>결투 중, 팡트를 적중시키는데 성공하면 재사용 대기 시간이 2초 감소한다."
        )
        override val cooldown = DUELIST_EN_GARDE_COOLDOWN_SECONDS

        override fun use(): Boolean {
            val target = playerData.shotLaserGetEntityData(10.0, TargetType.Enemy, false) ?: return false
            opponentId = target.entity.uniqueId
            duelUntil = game.combatTick + 300L
            fenteChain = 0; disrupted = false
            target.addStatus(DuelMark(player.uniqueId), playerData)
                .applyStatus(duration = DUELIST_MARK_DURATION_SECONDS, powerSet = 1)
            particles.line(player.eyeLocation, target.entity.location.add(0.0, target.entity.height / 2.0, 0.0), Particle.ENCHANT, 0.25)
            sounds.play(player, Sound.ENTITY_ENDER_DRAGON_GROWL, volume = 0.6f, pitch = 1.5f)
            return true
        }

        override fun isUseSuccess(): Boolean {
            if (playerData.shotLaserGetEntityData(10.0, TargetType.Enemy, false) != null) return true
            player.sendMiniMessage("<red><bold>[!] 바라보는 적이 없습니다.")
            return false
        }
    }

    private inner class Passive : BasePassive(), OnHitHandler, WhenHitHandler {
        override val name = "<bold>자세 흐트러짐"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>결투 중 팡트 사용 후 적에게 피해를 입히지 못했다면 자세 흐트러짐 상태가 된다.",
            "<gray>자세 흐트러짐 상태에서 결투 상대에게 받는 피해가 25% 증가한다.",
            "",
            "<gray>피해를 받거나, 결투가 종료되면 자세 흐트러짐이 제거된다."
        )

        override fun onHit(context: DamageContext) {
            if (inDuel() && context.target.entity.uniqueId == opponentId) {
                context.addDamageDealtMultiplier(DUELIST_OPPONENT_DAMAGE_MULTIPLIER)
            }
        }

        override fun whenHit(context: DamageContext) {
            if (!inDuel()) { disrupted = false; opponentId = null; return }
            if (context.attacker.uniqueId == opponentId) {
                if (disrupted) context.addDamageTakenMultiplier(DUELIST_DISRUPTED_DAMAGE_TAKEN_MULTIPLIER)
                disrupted = false
            } else context.addDamageTakenMultiplier(DUELIST_OUTSIDER_DAMAGE_MULTIPLIER)
        }
    }
}
