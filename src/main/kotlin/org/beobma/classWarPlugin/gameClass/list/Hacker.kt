package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Snare
import org.bukkit.Color
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

private const val HACK_INPUT_TIME_LIMIT_SECONDS = 35

class Hacker : GameClass() {
    override val name = "<gray>해커"
    override val rank = Rank.A
    override val classItemMaterial = Material.COMPARATOR
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = listOf()

    private class RedSkill : Skill() {
        override val name = "<bold>해킹"
        override val description = listOf(
            "<gray>가장 가까운 플레이어 하나를 대상으로 하고, 해킹 상태에 돌입한다.",
            "<gray>해킹 상태에서는 제한시간 내에 채팅창에 나오는 텍스트를 똑같이 입력해야한다.",
            "",
            "<gray>3번 성공하면 해킹 대상의 좌표가 모든 플레이어에게 공유되며",
            "<gray>해킹당한 플레이어는 10초간 {keyword:Snare}된다."
        )
        override val cooldown = 240

        private var selectedTarget: PlayerData? = null

        override fun isUseSuccess(): Boolean {
            if (hasActiveSession(player.uniqueId)) {
                player.sendMiniMessage("<red><bold>[!] 이미 해킹을 진행 중입니다.")
                return false
            }
            selectedTarget = game.playerDatas.asSequence()
                .filterIsInstance<PlayerData>()
                .filter { it != playerData && !it.entityStatus.isDead && it.player.isOnline }
                .filter { it.player.world == player.world }
                .minByOrNull { it.player.location.distanceSquared(player.location) }
            if (selectedTarget == null) {
                player.sendMiniMessage("<red><bold>[!] 해킹할 다른 플레이어가 없습니다.")
                return false
            }
            return true
        }

        override fun use() {
            val target = selectedTarget ?: return
            selectedTarget = null
            sounds.play(player, Sound.BLOCK_BEACON_ACTIVATE, volume = 0.65f, pitch = 1.8f)
            particles.spawn(player, Particle.ENCHANT, count = 24, spread = 0.5, speed = 0.08)
            player.sendMiniMessage(
                "<aqua><bold>[해킹]</bold> <gray>${target.player.name}님을 해킹합니다. " +
                    "각 코드를 ${HACK_INPUT_TIME_LIMIT_SECONDS}초 안에 입력하세요."
            )
            issuePrompt(target.uniqueId, 0)
        }

        private fun issuePrompt(targetId: UUID, successes: Int) {
            val expected = generateHackCode(successes)
            val token = UUID.randomUUID()
            val timeout = playerData.trackTask(object : BukkitRunnable() {
                override fun run() {
                    val current = activeSessions[player.uniqueId] ?: return
                    if (current.token != token) return
                    failSession("입력 제한시간을 초과했습니다.")
                }
            }.runTaskLater(ClassWarPlugin.instance, HACK_INPUT_TIME_LIMIT_SECONDS * 20L))
            activeSessions.put(player.uniqueId, HackSession(this, targetId, expected, successes, token, timeout))
                ?.timeoutTask?.cancel()
            player.sendMiniMessage(
                "<aqua><bold>[해킹 ${successes + 1}/3]</bold> " +
                    "<gray>${HACK_INPUT_TIME_LIMIT_SECONDS}초 내 직접 입력: <white>$expected"
            )
            sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_BIT, volume = 0.8f, pitch = 1.2f + successes * 0.2f)
        }

        private fun generateHackCode(stage: Int): String {
            val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            fun token(length: Int): String = buildString {
                repeat(length) { append(alphabet[Random.nextInt(alphabet.length)]) }
            }

            val node = token(4)
            val decryptionKey = token(12 + stage * 2)
            val route = token(5)
            val checksum = token(6)
            return "auth[$node]::key=0x$decryptionKey;route=$route;crc=$checksum"
        }

        fun acceptInput(session: HackSession, input: String) {
            if (activeSessions[player.uniqueId] !== session) return
            if (input.trim() != session.expected) {
                failSession("입력한 코드가 일치하지 않습니다.")
                return
            }
            session.timeoutTask.cancel()
            val successes = session.successes + 1
            if (successes < 3) {
                particles.spawn(player, Particle.ELECTRIC_SPARK, count = 10, spread = 0.3, speed = 0.08)
                sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_PLING, volume = 0.8f, pitch = 1.3f + successes * 0.2f)
                issuePrompt(session.targetId, successes)
                return
            }
            activeSessions.remove(player.uniqueId)
            completeHack(session.targetId)
        }

        private fun completeHack(targetId: UUID) {
            val target = game.playerDatas.filterIsInstance<PlayerData>()
                .find { it.uniqueId == targetId && !it.entityStatus.isDead && it.player.isOnline }
            if (target == null) {
                failSession("대상이 더 이상 유효하지 않습니다.")
                return
            }
            target.getOrCreateStatus(playerData) { Snare() }.applyStatus(duration = 10, powerSet = 1)
            val location = target.player.location
            game.playerDatas.filterIsInstance<PlayerData>()
                .filter { it.player.isOnline }
                .forEach { viewer ->
                    viewer.player.sendMiniMessage(
                        "<red><bold>[해킹 완료]</bold> <white>${target.player.name}<gray>의 좌표: " +
                            "<gold>${location.blockX}, ${location.blockY}, ${location.blockZ}</gold>"
                    )
                    sounds.playTo(viewer.player, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, volume = 0.5f, pitch = 1.65f)
                }
            particles.spawn(
                target.player.location.clone().add(0.0, 1.0, 0.0),
                Particle.DUST,
                Particle.DustOptions(Color.RED, 1.8f),
                org.beobma.classWarPlugin.effect.ParticleOptions.spread(30, 0.65, 0.04),
            )
            sounds.play(target.player, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, volume = 1.0f, pitch = 0.55f)
        }

        private fun failSession(reason: String) {
            activeSessions.remove(player.uniqueId)?.timeoutTask?.cancel()
            player.sendMiniMessage("<red><bold>[해킹 실패]</bold> <gray>$reason")
            particles.spawn(player, Particle.SMOKE, count = 16, spread = 0.35, speed = 0.04)
            sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_BASS, volume = 0.9f, pitch = 0.55f)
        }
    }

    companion object {
        private data class HackSession(
            val skill: RedSkill,
            val targetId: UUID,
            val expected: String,
            val successes: Int,
            val token: UUID,
            val timeoutTask: BukkitTask,
        )

        private val activeSessions: ConcurrentHashMap<UUID, HackSession> = ConcurrentHashMap()

        fun hasActiveSession(playerId: UUID): Boolean = activeSessions.containsKey(playerId)

        fun handleChatInput(player: Player, input: String) {
            val session = activeSessions[player.uniqueId] ?: return
            session.skill.acceptInput(session, input)
        }

        fun clearSessions(playerIds: Collection<UUID>) {
            playerIds.forEach { playerId -> activeSessions.remove(playerId)?.timeoutTask?.cancel() }
        }
    }
}
