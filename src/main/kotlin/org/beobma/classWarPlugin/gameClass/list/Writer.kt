package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.beobma.classWarPlugin.ability.AbilityExecution
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.handler.*
import org.beobma.classWarPlugin.gameClass.writer.WritingDeck
import org.beobma.classWarPlugin.gameClass.writer.WritingProgress
import org.beobma.classWarPlugin.gameClass.writer.WritingWork
import org.beobma.classWarPlugin.gameClass.writer.TypingDifference
import org.beobma.classWarPlugin.manager.DamageManager
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.updateStatusActionBar
import org.beobma.classWarPlugin.status.list.WritingStatus
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


class Writer : GameClass(), GameStatusHandler, GameEndHandler, PlayerDeathHandler,
    OnHitHandler, WhenHitHandler, EnvironmentalDamageHandler {
    override val classId = "writer"
    override val name = "<gray>작가"
    override val rank = Rank.B
    override val classItemMaterial = Material.BOOK
    override var skills: List<Skill> = listOf()

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private var progress = WritingProgress()
    private var work: WritingWork? = null
    private var line = 0
    private var promptSerial = 0L
    private val works = WritingDeck()

    override fun onBattleStart() {
        progress = WritingProgress()
        works.reset()
        nextWork()
        abilityScope.resources.own { unregister() }
        showPrompt()
    }

    override fun onGameTimePasses() = Unit
    override fun onGameEnd() = unregister()
    override fun onPlayerDeath() = unregister()
    override fun onSuspend() = unregister()
    override fun onResume() { if (work != null && !playerStatus.isDead) showPrompt() }

    private fun unregister() {
        sessions.computeIfPresent(playerData.uniqueId) { _, entry -> if (entry.owner === this) null else entry }
    }

    private fun nextWork() {
        work = works.next()
        line = 0
    }

    private fun showPrompt() {
        val current = work ?: return
        sessions[playerData.uniqueId] = ChatSession(this, ++promptSerial)
        player.sendMessage(Component.text(current.lines[line], NamedTextColor.WHITE))
        playerData.getOrCreateStatus(playerData) { WritingStatus() }
            .synchronize(progress.correct, progress.incorrect, line + 1, current.lines.size)
        playerData.updateStatusActionBar()
    }

    private fun answer(session: ChatSession, input: String) {
        if (sessions[playerData.uniqueId] !== session || session.serial != promptSerial ||
            abilityScope.isClosed || !abilityScope.isActive || abilityScope.suspended || !player.isOnline || playerStatus.isDead) return
        if (game.isPaused) {
            player.sendMessage(Component.text("일시 정지 중에는 채점하지 않는다.", NamedTextColor.GRAY))
            return
        }
        val current = work ?: return
        val expected = current.lines[line]
        val correct = progress.submit(expected, input)
        line++
        val finished = line >= current.lines.size
        if (correct) {
            player.sendMessage(Component.text("성공 · 기본 공격 +0.1 / 받는 피해 -1%", NamedTextColor.GREEN))
            particles.spawn(player.eyeLocation.clone().add(0.0, 0.35, 0.0), Particle.ENCHANT, count = if (finished) 18 else 6, spread = 0.25)
            sounds.playTo(player, Sound.ITEM_BOOK_PAGE_TURN, volume = 0.65f, pitch = 1.2f)
            sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_CHIME, volume = 0.35f, pitch = if (finished) 1.8f else 1.3f)
        } else {
            player.sendMessage(Component.text("오타 · 기본 공격 -0.2 / 받는 피해 +2%.", NamedTextColor.RED))
            showDifference(TypingDifference.compare(expected, input))
            particles.spawn(player.eyeLocation, Particle.SMOKE, count = 4, spread = 0.12)
            sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_BASS, volume = 0.4f, pitch = 0.65f)
        }
        if (finished) {
            particles.spawn(player, Particle.HAPPY_VILLAGER, count = if (correct) 12 else 5, spread = 0.5)
            nextWork()
        }
        showPrompt()
    }

    private fun showDifference(difference: TypingDifference) {
        fun highlighted(prefix: String, text: String, correct: BooleanArray): Component {
            var component = Component.text(prefix, NamedTextColor.GRAY)
            if (text.isEmpty()) return component.append(Component.text("(입력 없음)", NamedTextColor.RED))
            text.forEachIndexed { index, character ->
                component = component.append(Component.text(character.toString(),
                    if (correct[index]) NamedTextColor.WHITE else NamedTextColor.RED))
            }
            return component
        }
        player.sendMessage(highlighted("입력: ", difference.input, difference.inputCorrect))
        player.sendMessage(highlighted("정답: ", difference.expected, difference.expectedCorrect))
    }

    override fun onAttackHit(context: DamageContext) {
        // Bonus is after the common 0.6 basic normalization, before armor/other modifiers.
        val bonus = progress.basicDamageBonus.let { if (it > 0) growthValue("reward", it) else it }
        context.addBaseDamage(bonus / DamageManager.BASIC_ATTACK_DAMAGE_MULTIPLIER)
    }

    override fun whenHit(context: DamageContext) {
        context.addDamageTakenMultiplier(progress.damageTakenMultiplier)
    }

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (event is EntityDamageByEntityEvent &&
            (event.damager is Player || (event.damager as? Projectile)?.shooter is Player)) return
        event.damage *= progress.damageTakenMultiplier
    }

    private data class ChatSession(val owner: Writer, val serial: Long)

    companion object {
        private val sessions = ConcurrentHashMap<UUID, ChatSession>()

        /** Async chat captures an immutable ticket; Bukkit and game state are accessed on the main thread only. */
        fun captureChatInput(playerId: UUID, input: String): Runnable? {
            val session = sessions[playerId] ?: return null
            return Runnable { AbilityExecution.with(session.owner.abilityScope) { session.owner.answer(session, input) } }
        }
    }

    private class Passive : BasePassive() {
        override val name = org.beobma.classWarPlugin.keyword.Keyword.Writing.string
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>게임 시작 시 글이 제시된다.",
            "<gray>글과 동일하게 채팅을 쳐 한 줄을 완성할 때마다",
            "<gray>자신의 기본 공격 피해가 {g:writer-reward:0.1} 증가하고 받는 피해가 1% 감소한다.",
            "<gray>원래 글과 다르게 작성한 경우 대신 기본 공격 피해가 {g:basic:0.2} 감소하고 받는 피해가 2% 증가한다.",
            "<gray>공백과 문장 부호도 일치해야 하며 오답 부분은 빨간색으로 표시한 뒤 다음 줄로 넘어간다."
        )
    }
}
