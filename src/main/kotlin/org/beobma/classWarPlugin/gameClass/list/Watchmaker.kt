package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.PlayerManager.heal
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.SkillManager.getTargetCandidates
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Shield
import org.beobma.classWarPlugin.status.list.TimePhaseStatus
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.*
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// 밸런스 조정 상수
private const val WATCHMAKER_HAND_COOLDOWN_SECONDS = 12
private const val WATCHMAKER_MIDNIGHT_DAMAGE = 4.0
private const val WATCHMAKER_NOON_DAMAGE = 8.0
private const val WATCHMAKER_SHIELD_DURATION_SECONDS = 5
private const val WATCHMAKER_SHIELD_POWER = 4

class Watchmaker : GameClass(), GameStatusHandler {
    override val classId = "watchmaker"
    override val name = "<gray>시계공"
    override val rank = Rank.B
    override val classItemMaterial = Material.WOODEN_SWORD

    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<Passive> = listOf(
        PassiveOne()
    )

    private enum class Phase(val label: String) { DAWN("여명"), NOON("정오"), MIDNIGHT("자정") }
    private var phase = Phase.DAWN
    private var elapsed = 0

    private fun updatePhaseStatus() {
        playerData.getOrCreateStatus(playerData) { TimePhaseStatus() }
            .updatePhase(phase.label, 12 - elapsed)
    }

    override fun onBattleStart() {
        phase = Phase.DAWN
        elapsed = 0
        updatePhaseStatus()
    }
    override fun onGameTimePasses() {
        elapsed++
        if (elapsed >= 12) {
            elapsed = 0
            phase = Phase.entries[(phase.ordinal + 1) % Phase.entries.size]
            sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_BELL, pitch = 0.8f + phase.ordinal * 0.35f)
        }
        updatePhaseStatus()
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "watchmaker/red-skill"
        override val name = "<bold>시계침"
        override val description = listOf(
            "<gray>현재 위치에 6초 동안 시계침을 배치하고 회전시킨다.",
            "<gray>시계침은 2초에 걸쳐 한 바퀴 회전하며",
            "<gray>각 적마다 한 번만 현재 {keyword:TimePhase}의 효과를 적용한다.",
            "",
            "<gray>여명 - 4의 피해를 입히고 자신은 5초간 <aqua><bold>4의 피해를 막는 {keyword:Shield}을 얻는다.",
            "<gray>정오 - 8의 피해를 입힌다.",
            "<gray>자정 - 4의 피해를 입히고 자신은 체력을 4 회복한다."
        )
        override val cooldown = WATCHMAKER_HAND_COOLDOWN_SECONDS

        override fun use(): Boolean {
            val center = player.location.clone()
            val lineStart = center.clone().add(0.0, 0.7, 0.0)
            val hit = mutableSetOf<UUID>()
            val romanNumerals = listOf("XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")
            val numeralDisplays = romanNumerals.mapIndexed { index, numeral ->
                val markerAngle = 2.0 * PI * index / 12.0 - PI / 2.0
                val markerLocation = lineStart.clone().add(cos(markerAngle) * 5.15, 0.08, sin(markerAngle) * 5.15)
                val towardCenter = lineStart.toVector().subtract(markerLocation.toVector())
                towardCenter.y = 0.0
                towardCenter.normalize()
                markerLocation.yaw = 0.0f
                markerLocation.pitch = 0.0f
                val numeralRotation = Quaternionf().lookAlong(
                    Vector3f(0.0f, -1.0f, 0.0f),
                    Vector3f(towardCenter.x.toFloat(), towardCenter.y.toFloat(), towardCenter.z.toFloat()),
                )
                markerLocation.world.spawn(markerLocation, TextDisplay::class.java).apply {
                    text(Component.text(numeral, NamedTextColor.GOLD))
                    billboard = Display.Billboard.FIXED
                    lineWidth = 120
                    backgroundColor = Color.fromARGB(0, 0, 0, 0)
                    isShadowed = true
                    isSeeThrough = true
                    isPersistent = false
                    transformation = Transformation(
                        Vector3f(),
                        numeralRotation,
                        Vector3f(1.25f, 1.25f, 1.25f),
                        Quaternionf(),
                    )
                    TemporaryDisplayManager.mark(this, player.uniqueId)
                }
            }
            var tick = 0
            playerData.trackTask(object : BukkitRunnable(abilityScope) {
                override fun run() {
                    if (tick >= 120) {
                        numeralDisplays.forEach(TextDisplay::remove)
                        cancel()
                        return
                    }
                    val angle = 2.0 * PI * (tick % 40) / 40.0
                    val direction = Vector(cos(angle), 0.0, sin(angle))
                    val end = center.clone().add(direction.clone().multiply(6.0))
                    val lineEnd = end.clone().add(0.0, 0.7, 0.0)
                    particles.line(lineStart, lineEnd, Particle.END_ROD, 0.4)
                    if (tick % 4 == 0) {
                        particles.circle(lineStart, Particle.END_ROD, 6.0, 48)
                    }
                    playerData.getTargetCandidates().filter { it != playerData && it.entityStatus.isSkillTargeting && it.entity.uniqueId !in hit }.forEach { target ->
                        if (!HitboxUtil.intersectsSegment(
                                target.entity.boundingBox,
                                lineStart.toVector(),
                                lineEnd.toVector(),
                                expansion = 0.15,
                            )) return@forEach
                        hit += target.entity.uniqueId
                        when (phase) {
                            Phase.DAWN -> {
                                target.damage(WATCHMAKER_MIDNIGHT_DAMAGE, DamageType.Normal, playerData)
                                playerData.addStatus(Shield(), playerData).applyStatus(
                                    duration = WATCHMAKER_SHIELD_DURATION_SECONDS,
                                    powerDelta = WATCHMAKER_SHIELD_POWER,
                                )
                            }
                            Phase.NOON -> target.damage(WATCHMAKER_NOON_DAMAGE, DamageType.Normal, playerData)
                            Phase.MIDNIGHT -> {
                                target.damage(WATCHMAKER_MIDNIGHT_DAMAGE, DamageType.Normal, playerData)
                                playerData.heal(4.0, DamageType.Normal, playerData)
                            }
                        }
                        sounds.play(target.entity, Sound.BLOCK_NOTE_BLOCK_HAT, pitch = 1.5f)
                    }
                    tick++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
            return true
        }
    }

    private class PassiveOne : Passive() {
        override val name = "<bold>시계"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>게임 시작 시 여명 {keyword:TimePhase}에 진입한다.",
            "<gray>12초마다 여명, 정오, 자정 순서로 {keyword:TimePhase}가 변경된다."
        )
    }
}
