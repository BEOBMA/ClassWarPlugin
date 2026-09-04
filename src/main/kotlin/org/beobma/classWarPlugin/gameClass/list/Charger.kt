package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.MovementInputHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.gameClass.handler.SneakInputHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.handler.StatusPlayerMoveHandler
import org.beobma.classWarPlugin.status.list.Charge
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.player.PlayerInputEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import kotlin.math.sqrt
import org.beobma.classWarPlugin.skill.Passive as BasePassive

private const val CHARGER_RELEASE_COOLDOWN_SECONDS = 30
private const val CHARGER_REQUIRED_CHARGE = 100
private const val CHARGER_RELEASE_RADIUS = 3.0
private const val CHARGER_MAX_DAMAGE = 20.0

class Charger : GameClass(), GameStatusHandler, GameEndHandler, PlayerDeathHandler,
    MovementInputHandler, SneakInputHandler, StatusPlayerMoveHandler {
    override val classId = "charger"
    override val name = "<gray>충전기"
    override val rank = Rank.A
    override val classItemMaterial = Material.REDSTONE_BLOCK

    private val releaseSkill = RedSkill()
    override var skills: List<Skill> = listOf(releaseSkill)
    override var passives: List<BasePassive> = listOf(Passive())

    private var chargeStatus: Charge? = null
    private var chargeTask: BukkitTask? = null
    private var hasMovementInput = false
    private var warningTick = 0

    override fun onBattleStart() {
        cleanup()
        hasMovementInput = false
        warningTick = 0
        chargeStatus = playerData.getOrCreateStatus(playerData) { Charge() }.also {
            it.configureMaximum(null)
            it.clearCharge()
        }
        chargeTask = playerData.trackTask(object : BukkitRunnable(abilityScope) {
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    resetCharge()
                    cancel()
                    chargeTask = null
                    return
                }
                if (game.isPaused) return
                if (!player.isSneaking || hasMovementInput) {
                    resetCharge()
                    return
                }

                val status = chargeStatus ?: return
                renderChargeWarning(status.addCharge(1))
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L))
    }

    override fun onGameTimePasses() = Unit
    override fun onResume() { hasMovementInput = false }

    override fun onPlayerInput(event: PlayerInputEvent) {
        hasMovementInput = event.input.run {
            isForward || isBackward || isLeft || isRight || isJump
        }
        if (player.isSneaking && hasMovementInput) resetCharge()
    }

    override fun onPlayerToggleSneak(event: PlayerToggleSneakEvent) {
        if (!event.isSneaking) resetCharge()
    }

    override fun onPlayerMove(event: PlayerMoveEvent, playerData: PlayerData) {
        if (!player.isSneaking || chargePower() <= 0) return
        val from = event.from
        val to = event.to
        if (from.x != to.x || from.y != to.y || from.z != to.z) resetCharge()
    }

    override fun onGameEnd() = cleanup()

    override fun onPlayerDeath() = cleanup()

    private fun chargePower(): Int = chargeStatus?.charge ?: 0

    private fun resetCharge() {
        val status = chargeStatus ?: return
        status.clearCharge()
        warningTick = 0
    }

    private fun cleanup() {
        chargeTask?.cancel()
        chargeTask = null
        resetCharge()
        hasMovementInput = false
        warningTick = 0
    }

    private fun renderChargeWarning(charge: Int) {
        warningTick++
        val center = player.location.clone().add(0.0, 1.0, 0.0)
        val stage = when {
            charge >= 1_600 -> 5
            charge >= 900 -> 4
            charge >= 400 -> 3
            charge >= 200 -> 2
            charge >= CHARGER_REQUIRED_CHARGE -> 1
            else -> 0
        }
        val particleInterval = (6 - stage).coerceAtLeast(1)
        if (warningTick % particleInterval == 0) {
            particles.spawn(
                center,
                Particle.ELECTRIC_SPARK,
                count = 1 + stage * 2,
                spread = 0.22 + stage * 0.09,
                speed = 0.025 + stage * 0.012,
            )
            if (stage >= 2) {
                particles.spawn(
                    center,
                    Particle.END_ROD,
                    count = stage,
                    spread = 0.18 + stage * 0.07,
                    speed = 0.015,
                )
            }
        }

        val soundInterval = when (stage) {
            5 -> 6
            4 -> 9
            3 -> 13
            2 -> 18
            1 -> 26
            else -> 40
        }
        if (warningTick % soundInterval == 0) {
            sounds.play(
                player,
                Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,
                volume = 0.22f + stage * 0.08f,
                pitch = 0.62f + stage * 0.1f,
            )
            if (stage >= 4) {
                sounds.play(player, Sound.ENTITY_WARDEN_HEARTBEAT, volume = 0.35f, pitch = 1.25f)
            }
        }
    }

    private fun releaseDamage(charge: Int): Double =
        (5.0 * sqrt(charge / CHARGER_REQUIRED_CHARGE.toDouble())).coerceAtMost(CHARGER_MAX_DAMAGE)

    private inner class RedSkill : Skill() {
        override val definitionId = "charger/red-skill"
        override val name = "<bold>방출"
        override val description = listOf(
            "{keyword:Charge}이 100 이상일 때에만 사용할 수 있다.",
            "",
            "<gray>3칸 내에 있는 모든 적에게 {keyword:Charge} 수치에 비례한 피해를 가한다.",
            "<gray>피해량은 충전량이 높을수록 증가 효율이 감소하며 최대 20이다.",
        )
        override val cooldown = CHARGER_RELEASE_COOLDOWN_SECONDS

        override fun isUseSuccess(): Boolean {
            if (chargeStatus?.hasCharge(CHARGER_REQUIRED_CHARGE) == true) return true
            player.sendMiniMessage(
                "<red><bold>[!] 방출하려면 충전이 $CHARGER_REQUIRED_CHARGE 이상이어야 합니다. " +
                    "<gray>(현재: <aqua>${chargePower()}</aqua><gray>)",
            )
            return false
        }

        override fun use(): Boolean {
            val charge = chargeStatus?.consumeAll() ?: 0
            val damage = releaseDamage(charge)
            warningTick = 0

            val center = player.location.clone().add(0.0, 1.0, 0.0)
            playerData.radius(player.location, TargetType.Enemy, CHARGER_RELEASE_RADIUS, false, hitAttackableObjects = true)
                .forEach { target ->
                    target.damage(damage, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
                    particles.spawn(target.entity, Particle.ELECTRIC_SPARK, count = 18, spread = 0.5, speed = 0.14)
                }

            repeat(4) { index ->
                val radius = CHARGER_RELEASE_RADIUS * (index + 1) / 4.0
                particles.circle(center, Particle.ELECTRIC_SPARK, radius, 20 + index * 8)
            }
            particles.spawn(center, Particle.FLASH, count = 1)
            particles.spawn(center, Particle.EXPLOSION, count = 2, spread = 0.35, speed = 0.03)
            particles.spawn(center, Particle.END_ROD, count = 45, spread = 1.35, speed = 0.22)
            sounds.play(player, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, volume = 1.15f, pitch = 0.72f)
            sounds.play(player, Sound.ENTITY_GENERIC_EXPLODE, volume = 0.75f, pitch = 1.35f)
            return true
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>방전"
        override val description = listOf(
            "<gray>패시브",
            "",
            "{keyword:Charge} 최대치가 무한이 된다.",
            "<gray>웅크린 상태가 아닐 때, {keyword:Charge}은 즉시 소멸한다.",
            "<gray>웅크린 상태에서 움직이거나 점프하면 {keyword:Charge}은 즉시 소멸한다.",
        )
    }
}
