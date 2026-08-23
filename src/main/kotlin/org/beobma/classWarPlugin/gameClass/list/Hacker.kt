package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.Snare
import org.beobma.classWarPlugin.status.list.Radiation
import org.bukkit.Color
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val HACKER_HACKING_COOLDOWN_SECONDS = 240
private const val HACKER_SNARE_DURATION_SECONDS = 10
private const val HACKER_CORRUPTION_DAMAGE_DEALT_MULTIPLIER = 0.8
private const val HACKER_CORRUPTION_DAMAGE_TAKEN_MULTIPLIER = 1.2
private const val HACKER_ROOT_DAMAGE_DEALT_MULTIPLIER = 1.5
private const val HACKER_ROOT_DAMAGE_TAKEN_MULTIPLIER = 0.7

private const val MAX_HACK_STAGE = 3
private val hackTimeLimits = intArrayOf(35, 32, 28)

class Hacker : GameClass(), GameStatusHandler {
    override val name = "<gray>해커"
    override val rank = Rank.A
    override val classItemMaterial = Material.COMPARATOR

    private val hackSkill = RedSkill()
    override var skills: List<Skill> = listOf(hackSkill)
    override var passives: List<BasePassive> = listOf()

    override fun onBattleStart() {
        clearSessions(listOf(player.uniqueId))
        hackSkill.resetProgress()
        playerData.getOrCreateStatus(playerData) { HackerAccessStatus() }.updateStage(0)
    }

    override fun onGameTimePasses() = Unit

    private class RedSkill : Skill() {
        override val name = "<bold>해킹"
        override val description = listOf(
            "<gray>채팅창에 출력된 한 줄의 코드를 제한시간 안에 똑같이 입력한다.",
            "<gray>성공할 때마다 다음 해킹의 코드가 길어지고 제한시간이 짧아진다.",
            "",
            "<gray>모든 단계: 자신을 제외한 생존자를 10초간 {keyword:Radiation}시키고 {keyword:Snare}한다.",
            "<gray>2단계: 생존자에게 영구적인 시스템 손상 디버프를 적용한다.",
            "<gray>  - 가하는 피해 20% 감소, 받는 피해 20% 증가",
            "<gray>3단계: 자신에게 영구적인 루트 권한 버프를 적용한다.",
            "<gray>  - 가하는 피해 50% 증가, 받는 피해 30% 감소",
        )
        override val cooldown = HACKER_HACKING_COOLDOWN_SECONDS

        private var completedHacks = 0

        fun resetProgress() {
            completedHacks = 0
        }

        override fun isUseSuccess(): Boolean {
            if (hasActiveSession(player.uniqueId)) {
                player.sendMiniMessage("<red><bold>[!] 이미 해킹을 진행 중입니다.")
                return false
            }
            if (completedHacks >= MAX_HACK_STAGE) {
                player.sendMiniMessage("<gold><bold>[!] 이미 최고 단계의 루트 권한을 획득했습니다.")
                return false
            }
            if (livingTargets().isEmpty()) {
                player.sendMiniMessage("<red><bold>[!] 해킹할 생존 플레이어가 없습니다.")
                return false
            }
            return true
        }

        override fun use() {
            val stage = completedHacks + 1
            val timeLimit = hackTimeLimits[stage - 1]
            sounds.play(player, Sound.BLOCK_BEACON_ACTIVATE, volume = 0.75f, pitch = 1.55f + stage * 0.12f)
            particles.spawn(player, Particle.ENCHANT, count = 28 + stage * 10, spread = 0.65, speed = 0.1)
            player.sendMiniMessage(
                "<aqua><bold>[해킹 $stage/$MAX_HACK_STAGE]</bold> <gray>${difficultyLabel(stage)} 난이도 코드를 " +
                    "<yellow>${timeLimit}초</yellow><gray> 안에 입력하세요."
            )
            issuePrompt(stage, timeLimit)
        }

        private fun issuePrompt(stage: Int, timeLimit: Int) {
            val expected = generateHackCode(stage)
            val token = UUID.randomUUID()
            val expiresAtTick = Bukkit.getCurrentTick().toLong() + timeLimit * 20L
            val progressStatus = playerData.getOrCreateStatus(playerData) { HackerProgressStatus() }
            progressStatus.start(stage, expiresAtTick)
            val timeout = playerData.trackTask(object : BukkitRunnable() {
                override fun run() {
                    val current = activeSessions[player.uniqueId] ?: return
                    if (current.token != token) return
                    failSession("입력 제한시간을 초과했습니다.")
                }
            }.runTaskLater(ClassWarPlugin.instance, timeLimit * 20L))
            activeSessions.put(
                player.uniqueId,
                HackSession(this, stage, expected, token, timeout, progressStatus),
            )?.let { previous ->
                previous.timeoutTask.cancel()
                if (previous.progressStatus !== progressStatus) previous.progressStatus.remove()
            }
            player.sendMiniMessage(
                "<aqua><bold>[해킹 코드]</bold> <dark_gray>(${expected.length}자)</dark_gray> <white>$expected"
            )
            sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_BIT, volume = 0.9f, pitch = 1.1f + stage * 0.2f)
        }

        private fun generateHackCode(stage: Int): String {
            val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            fun token(length: Int): String = buildString {
                repeat(length) { append(alphabet[Random.nextInt(alphabet.length)]) }
            }

            return when (stage) {
                1 -> {
                    val node = token(4)
                    val key = token(12)
                    val route = token(5)
                    val checksum = token(6)
                    "auth[$node]::key=0x$key;route=$route;crc=$checksum"
                }

                2 -> {
                    val node = token(6)
                    val key = token(24)
                    val route = token(9)
                    val nonce = token(10)
                    val checksum = token(10)
                    "sudo.net[node_$node]::decrypt(key=0x$key);route=/sys/cache/$route;nonce=$nonce;crc=$checksum"
                }

                else -> {
                    val node = token(8)
                    val kernel = token(36)
                    val route = token(12)
                    val nonce = token(14)
                    val signature = token(20)
                    val acl = token(10)
                    val checksum = token(12)
                    "root.override[node_$node]::{kernel=0x$kernel;route=/dev/shm/$route;nonce=$nonce;sig=$signature;acl=$acl;crc=$checksum}"
                }
            }
        }

        fun acceptInput(session: HackSession, input: String) {
            if (activeSessions[player.uniqueId] !== session) return
            if (input.trim() != session.expected) {
                failSession("입력한 코드가 일치하지 않습니다.")
                return
            }
            session.timeoutTask.cancel()
            activeSessions.remove(player.uniqueId)
            session.progressStatus.remove()
            completeHack(session.stage)
        }

        private fun completeHack(stage: Int) {
            if (stage != completedHacks + 1) return
            completedHacks = stage
            playerData.getOrCreateStatus(playerData) { HackerAccessStatus() }.updateStage(stage)

            val targets = livingTargets()
            applyBreach(targets)
            when (stage) {
                2 -> applyPermanentDebuff(targets)
                3 -> applyPermanentBuff()
            }

            val progressionMessage = if (stage < MAX_HACK_STAGE) {
                "<yellow>다음 해킹 난이도가 상승합니다."
            } else {
                "<gold>루트 권한을 획득했습니다."
            }
            player.sendMiniMessage(
                "<green><bold>[해킹 성공]</bold> <gray>${stage}단계 해킹이 완료되었습니다. $progressionMessage"
            )
            particles.spawn(player, Particle.ELECTRIC_SPARK, count = 35 + stage * 20, spread = 0.9, speed = 0.15)
            particles.spawn(player, Particle.ENCHANT, count = 28 + stage * 16, spread = 0.8, speed = 0.1)
            sounds.play(player, Sound.BLOCK_BEACON_POWER_SELECT, volume = 1.0f, pitch = 1.1f + stage * 0.18f)
        }

        private fun applyBreach(targets: List<PlayerData>) {
            targets.forEach { target ->
                target.getOrCreateStatus(playerData) { Snare() }
                    .applyStatus(duration = HACKER_SNARE_DURATION_SECONDS, powerSet = 1)
                target.addStatus(Radiation(), playerData)
                    .applyStatus(duration = HACKER_SNARE_DURATION_SECONDS, powerSet = 1)
                if (target.player.isOnline) {
                    target.player.sendMiniMessage(
                        "<red><bold>[시스템 침입]</bold> <gray>해커에 의해 10초간 위치가 노출되고 속박됩니다."
                    )
                    particles.spawn(
                        target.player.location.clone().add(0.0, 1.0, 0.0),
                        Particle.DUST,
                        Particle.DustOptions(Color.RED, 1.8f),
                        ParticleOptions.spread(42, 0.78, 0.055),
                    )
                    particles.spawn(target.player, Particle.ELECTRIC_SPARK, count = 20, spread = 0.55, speed = 0.09)
                    sounds.play(target.player, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, volume = 0.9f, pitch = 0.55f)
                }
            }
        }

        private fun applyPermanentDebuff(targets: List<PlayerData>) {
            targets.forEach { target ->
                target.getOrCreateStatus(playerData) { HackerSystemCorruptionStatus() }
                    .applyStatus(powerSet = 1)
                if (target.player.isOnline) {
                    target.player.sendMiniMessage(
                        "<dark_purple><bold>[시스템 손상]</bold> <gray>가하는 피해가 20% 감소하고 받는 피해가 20% 증가합니다."
                    )
                    particles.spawn(target.player, Particle.WITCH, count = 55, spread = 0.8, speed = 0.13)
                    sounds.play(target.player, Sound.ENTITY_ELDER_GUARDIAN_CURSE, volume = 0.55f, pitch = 1.45f)
                }
            }
        }

        private fun applyPermanentBuff() {
            playerData.getOrCreateStatus(playerData) { HackerRootAccessStatus() }
                .applyStatus(powerSet = 1)
            player.sendMiniMessage(
                "<gold><bold>[ROOT ACCESS]</bold> <gray>가하는 피해가 50% 증가하고 받는 피해가 30% 감소합니다."
            )
            particles.spawn(player, Particle.TOTEM_OF_UNDYING, count = 95, spread = 1.0, speed = 0.2)
            particles.spawn(player, Particle.END_ROD, count = 60, spread = 0.85, speed = 0.13)
            sounds.play(player, Sound.BLOCK_END_PORTAL_SPAWN, volume = 0.85f, pitch = 1.35f)
            sounds.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, volume = 1.0f, pitch = 1.1f)
        }

        private fun livingTargets(): List<PlayerData> = game.playerDatas.asSequence()
            .filterIsInstance<PlayerData>()
            .filter { it != playerData && !it.entityStatus.isDead }
            .toList()

        private fun difficultyLabel(stage: Int): String = when (stage) {
            1 -> "<green>보통</green>"
            2 -> "<yellow>어려움</yellow>"
            else -> "<red><bold>극한</bold></red>"
        }

        private fun failSession(reason: String) {
            activeSessions.remove(player.uniqueId)?.let { session ->
                session.timeoutTask.cancel()
                session.progressStatus.remove()
            }
            player.sendMiniMessage("<red><bold>[해킹 실패]</bold> <gray>$reason")
            particles.spawn(player, Particle.SMOKE, count = 16, spread = 0.35, speed = 0.04)
            sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_BASS, volume = 0.9f, pitch = 0.55f)
        }
    }

    companion object {
        private data class HackSession(
            val skill: RedSkill,
            val stage: Int,
            val expected: String,
            val token: UUID,
            val timeoutTask: BukkitTask,
            val progressStatus: HackerProgressStatus,
        )

        private val activeSessions: ConcurrentHashMap<UUID, HackSession> = ConcurrentHashMap()

        fun hasActiveSession(playerId: UUID): Boolean = activeSessions.containsKey(playerId)

        fun handleChatInput(player: Player, input: String) {
            val session = activeSessions[player.uniqueId] ?: return
            session.skill.acceptInput(session, input)
        }

        fun clearSessions(playerIds: Collection<UUID>) {
            playerIds.forEach { playerId ->
                activeSessions.remove(playerId)?.let { session ->
                    session.timeoutTask.cancel()
                    session.progressStatus.remove()
                }
            }
        }
    }
}

private class HackerProgressStatus : StatusAbnormality() {
    override val name = "<aqua><bold>해킹 진행</bold><gray>"
    override val description = listOf("<gray>현재 진행 중인 해킹의 단계와 코드 입력 제한시간이다.")
    override val canRemove = true
    override val isClassMechanic = true
    override var power = 1
    override var maxPower: Int? = MAX_HACK_STAGE
    override val showPower = false
    override val showMaxPower = false
    override var duration: Int? = null

    private var expiresAtTick: Long = 0L

    fun start(stage: Int, expiresAtTick: Long) {
        this.expiresAtTick = expiresAtTick
        updatePower(stage.coerceIn(1, MAX_HACK_STAGE))
    }

    override fun actionBarText(): String {
        val remainingTicks = (expiresAtTick - Bukkit.getCurrentTick().toLong()).coerceAtLeast(0L)
        val remainingSeconds = (remainingTicks + 19L) / 20L
        return "<aqua><bold>해킹 $power/$MAX_HACK_STAGE</bold></aqua>: <yellow>${remainingSeconds}초</yellow>"
    }
}

private class HackerSystemCorruptionStatus : StatusAbnormality(), OnHitHandler, WhenHitHandler {
    override val name = "<dark_purple><bold>시스템 손상</bold><gray>"
    override val description = listOf(
        "<gray>해커의 2단계 해킹으로 시스템이 영구적으로 손상되었다.",
        "<gray>가하는 피해가 20% 감소하고 받는 피해가 20% 증가한다.",
    )
    override val canRemove = false
    override var power = 1
    override var maxPower: Int? = 1
    override val showPower = false
    override val showMaxPower = false
    override var duration: Int? = null

    override fun onHit(context: DamageContext) {
        context.addDamageDealtMultiplier(HACKER_CORRUPTION_DAMAGE_DEALT_MULTIPLIER)
    }

    override fun whenHit(context: DamageContext) {
        context.addDamageTakenMultiplier(HACKER_CORRUPTION_DAMAGE_TAKEN_MULTIPLIER)
    }
}

private class HackerRootAccessStatus : StatusAbnormality(), OnHitHandler, WhenHitHandler {
    override val name = "<gold><bold>ROOT ACCESS</bold><gray>"
    override val description = listOf(
        "<gray>해커의 3단계 해킹으로 획득한 강력한 영구 권한이다.",
        "<gray>가하는 피해가 50% 증가하고 받는 피해가 30% 감소한다.",
    )
    override val canRemove = false
    override val isClassMechanic = true
    override var power = 1
    override var maxPower: Int? = 1
    override val showPower = false
    override val showMaxPower = false
    override var duration: Int? = null

    override fun onHit(context: DamageContext) {
        context.addDamageDealtMultiplier(HACKER_ROOT_DAMAGE_DEALT_MULTIPLIER)
    }

    override fun whenHit(context: DamageContext) {
        context.addDamageTakenMultiplier(HACKER_ROOT_DAMAGE_TAKEN_MULTIPLIER)
    }
}

private class HackerAccessStatus : StatusAbnormality() {
    override val name = "<aqua><bold>해킹 단계</bold><gray>"
    override val description = listOf("<gray>성공한 해킹 단계이다. 3단계에서 루트 권한을 획득한다.")
    override val canRemove = false
    override val isClassMechanic = true
    override var power = 0
    override var maxPower: Int? = MAX_HACK_STAGE
    override var duration: Int? = null

    fun updateStage(stage: Int) {
        updatePower(stage.coerceIn(0, MAX_HACK_STAGE))
    }

    override fun actionBarText(): String {
        val stageText = if (power >= MAX_HACK_STAGE) {
            "<gold><bold>ROOT</bold></gold>"
        } else {
            "<aqua>$power</aqua><dark_gray>/</dark_gray><aqua>$MAX_HACK_STAGE</aqua>"
        }
        return "$name: $stageText"
    }
}
