package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.AbilityRunnable
import org.beobma.classWarPlugin.ability.AttributeEffects
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.WeaponInputHandler
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.beobma.classWarPlugin.gameClass.metronome.*
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.updateStatusActionBar
import org.beobma.classWarPlugin.status.list.MetronomeStatus
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.Color
import org.bukkit.attribute.Attribute
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos


class Metronome : GameClass(), GameStatusHandler, WeaponInputHandler {
    override val classId = "metronome"
    override val name = "<gray>메트로놈"
    override val rank = Rank.B
    override val classItemMaterial = Material.CLOCK
    override var skills: List<Skill> = listOf()

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private val rhythm = RhythmState()
    private val transport = RhythmTransport()
    private var timing = RhythmTiming(0, 0, 60)
    private var clockTick = 0L
    private var speedLease: AttributeEffects.Lease? = null
    private var attemptTick = Long.MIN_VALUE
    private var attemptedTarget: UUID? = null
    private var accepted = false
    private var consumedDamage = false
    private var feedbackUntil = 0L
    private var feedback = ""
    private var previewEnabled = false
    private fun isPreviewing() = previewEnabled && PlayerTagManager.isTraining(player)

    override fun onBattleStart() {
        rhythm.reset()
        transport.reset()
        clockTick = 0L
        previewEnabled = PlayerTagManager.isTraining(player)
        if (isPreviewing()) {
            transport.selectPreviewStage(1)
            player.sendMiniMessage("<aqua>[메트로놈] 음악 미리듣기: ${MetronomeScore.count}곡을 자동으로 재생한다. 검을 들고 F: 다음 곡 / 웅크리기 + F: 미리듣기 켜기·끄기")
        }
        installAttackSpeed()
        abilityScope.resources.own { stopMusic() }
        playFrame()
        updateDisplay()
        object : AbilityRunnable(abilityScope) {
            override fun run() {
                clockTick++
                if (rhythm.expire(clockTick)) {
                    if (!isPreviewing()) {
                        stopMusic()
                        transport.reset()
                    }
                    feedback = "<gray>박자 대기</gray>"
                    feedbackUntil = clockTick + 20
                }
                playFrame()
                if (clockTick % 2 == 0L) {
                    renderPendulum()
                    updateDisplay()
                }
            }
            override fun onCancel() { stopMusic(); speedLease?.close(); speedLease = null }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L)
    }

    override fun onGameTimePasses() = Unit
    override fun onSuspend() { speedLease?.close(); speedLease = null; stopMusic() }
    override fun onResume() {
        if (!abilityScope.started || abilityScope.isClosed) return
        installAttackSpeed()
        // Give a fresh audible downbeat after reconnecting instead of an inaudible phase offset.
        clockTick = 0L
        attemptTick = Long.MIN_VALUE
        rhythm.reset()
        transport.reset()
        playFrame()
        updateDisplay()
    }

    override fun onWeaponSwapHand(event: PlayerSwapHandItemsEvent) {
        if (!PlayerTagManager.isTraining(player) || !abilityScope.started || abilityScope.suspended || game.isPaused) return
        event.isCancelled = true
        val nextStage = if (isPreviewing()) transport.stage % MetronomeScore.count + 1 else 1
        previewEnabled = if (player.isSneaking) !previewEnabled else true
        stopMusic()
        transport.reset()
        if (previewEnabled) {
            transport.selectPreviewStage(if (player.isSneaking) 1 else nextStage)
            player.sendMiniMessage("<aqua>[메트로놈] 미리듣기: ${transport.score?.title} (${transport.score?.bpm} BPM)")
        } else {
            rhythm.reset()
            clockTick = 0L
            attemptTick = Long.MIN_VALUE
            feedback = ""
            player.sendMiniMessage("<gray>[메트로놈] 미리듣기를 종료하고 일반 박자 연습을 시작한다.")
        }
        playFrame()
        updateDisplay()
    }

    private fun installAttackSpeed() {
        speedLease?.close()
        speedLease = playerData.attributeEffects.multiply(abilityScope, Attribute.ATTACK_SPEED, 256.0, maximum = 1024.0)
    }

    /** Called once from the attack packet, before vanilla damage/invulnerability processing. */
    internal fun attempt(targetId: UUID?): Boolean {
        if (attemptTick == clockTick) return false // duplicate packets cannot earn stacks or damage
        attemptTick = clockTick
        attemptedTarget = targetId
        consumedDamage = false
        accepted = rhythm.attack(clockTick, timing)
        feedback = if (accepted) "<green>정박</green>" else "<red>엇박 · 피해 0</red>"
        feedbackUntil = clockTick + 10L
        if (!accepted) {
            if (!isPreviewing()) {
                transport.reset()
                stopMusic()
                sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.28f, 0.55f, SoundCategory.RECORDS)
            }
            particles.spawn(player.eyeLocation, Particle.SMOKE, count = 3, spread = 0.1)
        } else if (rhythm.streak % 8 == 0) {
            particles.spawn(player, Particle.NOTE, count = 4, spread = 0.45)
        }
        updateDisplay()
        return accepted
    }

    /** Runs before other class hit hooks, so a mistimed attack cannot trigger their on-hit effects. */
    internal fun allowDamage(context: DamageContext): Boolean {
        if (!context.path.isBasicAttack || context.secondaryAttack) return true
        val targetId = context.target.entity.uniqueId
        val matchesPacket = attemptTick == clockTick && attemptedTarget == targetId
        if (!matchesPacket && !attempt(targetId)) return false
        if (!accepted || consumedDamage) return false
        consumedDamage = true
        context.addDamageDealtMultiplier(rhythm.damageMultiplier)
        return true
    }

    private fun playFrame() {
        val frame = if (isPreviewing()) transport.previewTick() else transport.tick(rhythm.stage)
        timing = frame.timing
        frame.beat?.let(::playReferenceBeat)
        frame.notes.forEach { note ->
            sounds.playTo(player, instrumentSound(note.instrument), note.volume, note.pitch, SoundCategory.RECORDS)
        }
    }

    private fun playReferenceBeat(beat: Int) {
        sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.55f, if (beat == 0) 1.6f else 1.05f, SoundCategory.RECORDS)
        particles.circle(player.location.add(0.0, 0.08, 0.0), Particle.CRIT, 0.75, 12)
    }

    private fun renderPendulum() {
        val angle = timing.beatPosition * PI
        val yaw = Math.toRadians(player.location.yaw.toDouble())
        val offset = sin(angle) * 0.75
        val point = player.location.add(cos(yaw) * offset, 2.5 - kotlin.math.abs(offset) * 0.3, sin(yaw) * offset)
        val color = when (if (transport.stage == 0) 0 else (transport.stage - 1) / 6 + 1) {
            0 -> Color.fromRGB(180, 200, 220)
            1 -> Color.fromRGB(85, 200, 255)
            2 -> Color.fromRGB(90, 245, 185)
            3 -> Color.fromRGB(255, 195, 75)
            else -> Color.fromRGB(255, 100, 145)
        }
        particles.spawn(point, Particle.DUST, Particle.DustOptions(color, 0.85f))
        if (transport.stage >= 3 && clockTick % 5 == 0L) particles.spawn(point, Particle.NOTE, count = 1)
    }

    private fun updateDisplay() {
        val score = transport.score
        playerData.getOrCreateStatus(playerData) { MetronomeStatus() }.synchronize(
            rhythm.streak, rhythm.subdivision, score?.title ?: "기준 박자", timing.bpm, timing.phase,
            if (isPreviewing()) "<light_purple>미리듣기 ${transport.stage}/${MetronomeScore.count} · F 다음 곡</light_purple>" else if (clockTick < feedbackUntil) feedback else "",
        )
        playerData.updateStatusActionBar()
    }

    private fun stopMusic() {
        RhythmInstrument.entries.map(::instrumentSound).distinct().forEach { sounds.stop(player, it, SoundCategory.RECORDS) }
    }

    private fun instrumentSound(instrument: RhythmInstrument): Sound = when (instrument) {
        RhythmInstrument.HARP -> Sound.BLOCK_NOTE_BLOCK_HARP
        RhythmInstrument.GUITAR -> Sound.BLOCK_NOTE_BLOCK_GUITAR
        RhythmInstrument.FLUTE -> Sound.BLOCK_NOTE_BLOCK_FLUTE
        RhythmInstrument.BASS -> Sound.BLOCK_NOTE_BLOCK_BASS
        RhythmInstrument.BELL -> Sound.BLOCK_NOTE_BLOCK_BELL
        RhythmInstrument.PLING -> Sound.BLOCK_NOTE_BLOCK_PLING
        RhythmInstrument.BIT -> Sound.BLOCK_NOTE_BLOCK_BIT
        RhythmInstrument.KICK -> Sound.BLOCK_NOTE_BLOCK_BASEDRUM
        RhythmInstrument.SNARE -> Sound.BLOCK_NOTE_BLOCK_SNARE
        RhythmInstrument.HAT -> Sound.BLOCK_NOTE_BLOCK_HAT
    }

    private class Passive : BasePassive() {
        override val name = org.beobma.classWarPlugin.keyword.Keyword.VariableRhythm.string
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 재사용 대기 시간이 사라진다.",
            "",
            "<gray>게임 시작 후 일정한 박자로 소리가 들린다.",
            "<gray>박자에 맞춰 기본 공격을 하지 않으면 기본 공격의 피해량은 0으로 고정된다.",
            "",
            "<gray>처음에는 1초마다 박자가 들리며, 음악이 시작되면 그 음악의 BPM에 맞춰 박자도 빨라진다.",
            "<gray>들리는 박자를 2·4분할하고, 72 BPM 이하에서는 8분할하여 공격할 수 있다.",
            "<gray>박자를 틀리지 않고 연속으로 맞출 때마다 박자는 점차 음악으로 변한다.",
            "<gray>음악의 종류와 템포는 박자를 쪼개는 양에 비례하여 달라진다.",
            "<gray>더 오래, 더 빨리 정확한 박자를 유지할 때마다 기본 공격 피해량이 점차 증가한다.",
            "<gray>피해 증가는 최대 150%이며 엇박 또는 2초 넘게 공격하지 않으면 초기화된다.",
            "<gray>직접 작곡한 24곡이 60~180 BPM의 12개 템포로 점차 전개된다."
        )
    }
}
