package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.MathAnswerStackStatus
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
private const val MATHEMATICIAN_CALCULATION_COOLDOWN_SECONDS = 1
private const val MATHEMATICIAN_PROOF_COOLDOWN_SECONDS = 120
private const val MATHEMATICIAN_DAMAGE_PERCENT_PER_STACK = 1.0

class Mathematician : GameClass(), GameStatusHandler {
    override val name = "<gray>수학자"
    override val rank = Rank.A
    override val classItemMaterial = Material.WRITABLE_BOOK
    override var skills: List<Skill> = listOf(RedSkill(), OrangeSkill())
    override var passives: List<BasePassive> = listOf(Passive())

    private var difficulty = 1
    private var secondsUntilProblem = 30

    override fun onBattleStart() {
        difficulty = 1
        secondsUntilProblem = 30
        clearSessions(listOf(player.uniqueId))
        stackStatus().updatePower(0)
        player.sendMiniMessage(
            "<aqua><bold>[수학자]</bold> <gray>문제 난이도: <yellow>1/$MAX_DIFFICULTY</yellow> · 첫 문제까지 30초"
        )
    }

    override fun onGameTimePasses() {
        if (playerStatus.isDead || !player.isOnline) return
        secondsUntilProblem--
        if (secondsUntilProblem > 0) return
        secondsUntilProblem = 30
        issueProblem()
    }

    private fun stackStatus(): MathAnswerStackStatus =
        playerData.getOrCreateStatus(playerData) { MathAnswerStackStatus() }

    private fun changeDifficulty(delta: Int): Boolean {
        val next = (difficulty + delta).coerceIn(1, MAX_DIFFICULTY)
        if (next == difficulty) return false
        difficulty = next
        player.sendMiniMessage(
            "<aqua><bold>[수학자]</bold> <gray>문제 난이도가 " +
                "<yellow>$difficulty/$MAX_DIFFICULTY</yellow>(으)로 변경되었습니다."
        )
        particles.spawn(player, Particle.ENCHANT, count = 24, spread = 0.55, speed = 0.1)
        sounds.play(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, volume = 0.8f, pitch = 0.8f + difficulty * 0.12f)
        return true
    }

    private fun issueProblem() {
        activeProblems.remove(player.uniqueId)?.timeoutTask?.cancel()
        val (question, answer) = generateProblem(difficulty)
        val timeLimitSeconds = problemTimeLimitSeconds(difficulty)
        val token = UUID.randomUUID()
        val timeout = playerData.trackTask(object : BukkitRunnable() {
            override fun run() {
                val current = activeProblems[player.uniqueId] ?: return
                if (current.token != token) return
                failProblem("제한 시간을 초과했습니다.")
            }
        }.runTaskLater(ClassWarPlugin.instance, timeLimitSeconds * 20L))
        activeProblems[player.uniqueId] = MathProblem(this, answer.toString(), difficulty, token, timeout)
        player.sendMiniMessage(
            "<aqua><bold>[수학 문제 · 난이도 $difficulty]</bold> <white>$question " +
                "<gray>${timeLimitSeconds}초 안에 정답만 입력하세요."
        )
        particles.spawn(player, Particle.HAPPY_VILLAGER, count = 12, spread = 0.45, speed = 0.04)
        sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_CHIME, volume = 0.9f, pitch = 1.15f)
    }

    private fun problemTimeLimitSeconds(level: Int): Int = when (level) {
        in 1..5 -> 15
        6 -> 20
        7 -> 22
        8 -> 25
        9 -> 28
        else -> 30
    }

    private fun generateProblem(level: Int): Pair<String, Int> = when (level) {
        1 -> {
            val a = Random.nextInt(8, 31)
            val b = Random.nextInt(3, 18)
            if (Random.nextBoolean()) "$a + $b = ?" to a + b else "$a − $b = ?" to a - b
        }
        2 -> {
            val a = Random.nextInt(3, 14)
            val b = Random.nextInt(3, 14)
            "$a × $b = ?" to a * b
        }
        3 -> {
            val a = Random.nextInt(4, 15)
            val b = Random.nextInt(3, 11)
            val c = Random.nextInt(5, 31)
            "$a × $b + $c = ?" to a * b + c
        }
        4 -> {
            val a = Random.nextInt(8, 21)
            val b = Random.nextInt(3, 10)
            val c = Random.nextInt(2, 8)
            "($a + $b) × $c = ?" to (a + b) * c
        }
        5 -> {
            val x = Random.nextInt(4, 18)
            val a = Random.nextInt(3, 10)
            val b = Random.nextInt(8, 40)
            "$a·x + $b = ${a * x + b}, x = ?" to x
        }
        6 -> generateQuadraticProblem()
        7 -> generateSequenceProblem()
        8 -> generateLogarithmProblem()
        9 -> generateCalculusProblem()
        else -> generateSeniorProblem()
    }

    private fun generateQuadraticProblem(): Pair<String, Int> {
        val smallerRoot = Random.nextInt(1, 8)
        val largerRoot = Random.nextInt(smallerRoot + 1, 13)
        val rootSum = smallerRoot + largerRoot
        val rootProduct = smallerRoot * largerRoot
        return "x² − $rootSum·x + $rootProduct = 0, 큰 근 x = ?" to largerRoot
    }

    private fun generateSequenceProblem(): Pair<String, Int> {
        if (Random.nextBoolean()) {
            val firstTerm = Random.nextInt(2, 12)
            val difference = Random.nextInt(2, 8)
            val termCount = Random.nextInt(7, 13)
            val lastTerm = firstTerm + (termCount - 1) * difference
            val sum = termCount * (firstTerm + lastTerm) / 2
            return "a₁ = $firstTerm, d = $difference, n = $termCount ⇒ Sₙ = ?" to sum
        }

        val firstTerm = Random.nextInt(1, 5)
        val ratio = Random.nextInt(2, 4)
        val termCount = Random.nextInt(5, 8)
        var term = firstTerm
        var sum = 0
        repeat(termCount) {
            sum += term
            term *= ratio
        }
        return "a₁ = $firstTerm, r = $ratio, n = $termCount ⇒ Sₙ = ?" to sum
    }

    private fun generateLogarithmProblem(): Pair<String, Int> {
        val base = Random.nextInt(2, 6)
        val knownExponent = Random.nextInt(1, 4)
        val answerExponent = Random.nextInt(2, 5)
        val rightSide = knownExponent + answerExponent
        val knownValue = intPower(base, knownExponent)
        val answer = intPower(base, answerExponent)
        val baseSubscript = toSubscript(base)
        return "log$baseSubscript(x) + log$baseSubscript($knownValue) = $rightSide, x = ?" to answer
    }

    private fun generateCalculusProblem(): Pair<String, Int> {
        if (Random.nextBoolean()) {
            val coefficient = Random.nextInt(1, 5)
            val linear = Random.nextInt(1, 8)
            val point = Random.nextInt(1, 6)
            val answer = 2 * coefficient * point + linear
            return "f(x) = ${coefficient}x² + ${linear}x + 1, f′($point) = ?" to answer
        }

        val coefficient = Random.nextInt(1, 5)
        val point = Random.nextInt(2, 8)
        val answer = 2 * coefficient * point
        return "limₕ→₀ [${coefficient}(($point + h)² − ${point * point}) ÷ h] = ?" to answer
    }

    private fun generateSeniorProblem(): Pair<String, Int> = when (Random.nextInt(3)) {
        0 -> {
            val cubicCoefficient = Random.nextInt(1, 4)
            val quadraticCoefficient = Random.nextInt(1, 6)
            val linearCoefficient = Random.nextInt(1, 8)
            val upperBound = Random.nextInt(2, 6)
            val answer = cubicCoefficient * intPower(upperBound, 3) +
                quadraticCoefficient * intPower(upperBound, 2) + linearCoefficient * upperBound
            "∫₀${toSuperscript(upperBound)} (${3 * cubicCoefficient}x² + ${2 * quadraticCoefficient}x + $linearCoefficient) dx = ?" to answer
        }
        1 -> {
            val outerPower = 3
            val coefficient = Random.nextInt(2, 6)
            val constant = Random.nextInt(1, 7)
            val point = Random.nextInt(1, 5)
            val innerValue = coefficient * point + constant
            val answer = outerPower * coefficient * intPower(innerValue, outerPower - 1)
            "f(x) = ($coefficient·x + $constant)³, f′($point) = ?" to answer
        }
        else -> {
            val denominatorCoefficient = Random.nextInt(2, 7)
            val answer = Random.nextInt(2, 8)
            val numeratorCoefficient = denominatorCoefficient * answer
            val numeratorConstant = Random.nextInt(1, 10)
            val denominatorConstant = Random.nextInt(1, 10)
            "limₙ→∞ (${numeratorCoefficient}n² + $numeratorConstant) ÷ " +
                "(${denominatorCoefficient}n² + $denominatorConstant) = ?" to answer
        }
    }

    private fun toSubscript(value: Int): String = value.toString().map { digit ->
        when (digit) {
            '0' -> '₀'
            '1' -> '₁'
            '2' -> '₂'
            '3' -> '₃'
            '4' -> '₄'
            '5' -> '₅'
            '6' -> '₆'
            '7' -> '₇'
            '8' -> '₈'
            '9' -> '₉'
            '-' -> '₋'
            else -> digit
        }
    }.joinToString("")

    private fun toSuperscript(value: Int): String = value.toString().map { digit ->
        when (digit) {
            '0' -> '⁰'
            '1' -> '¹'
            '2' -> '²'
            '3' -> '³'
            '4' -> '⁴'
            '5' -> '⁵'
            '6' -> '⁶'
            '7' -> '⁷'
            '8' -> '⁸'
            '9' -> '⁹'
            '-' -> '⁻'
            else -> digit
        }
    }.joinToString("")

    private fun intPower(base: Int, exponent: Int): Int {
        var result = 1
        repeat(exponent) { result *= base }
        return result
    }

    private fun acceptInput(session: MathProblem, input: String) {
        if (activeProblems[player.uniqueId] !== session) return
        if (input.trim() != session.answer) {
            failProblem("오답입니다. 정답은 ${session.answer}였습니다.")
            return
        }
        session.timeoutTask.cancel()
        activeProblems.remove(player.uniqueId)
        val status = stackStatus()
        val before = status.power
        status.updatePower((before + session.difficulty).coerceAtMost(100))
        player.sendMiniMessage(
            "<green><bold>[정답]</bold> <gray>난이도만큼 스택을 획득했습니다. <gold>$before → ${status.power}</gold>"
        )
        particles.spawn(player, Particle.TOTEM_OF_UNDYING, count = 22, spread = 0.5, speed = 0.12)
        sounds.play(player, Sound.ENTITY_PLAYER_LEVELUP, volume = 0.9f, pitch = 1.35f)
    }

    private fun failProblem(reason: String) {
        activeProblems.remove(player.uniqueId)?.timeoutTask?.cancel()
        stackStatus().updatePower(0)
        player.sendMiniMessage("<red><bold>[문제 실패]</bold> <gray>$reason 정답 스택이 모두 사라집니다.")
        particles.spawn(player, Particle.SMOKE, count = 18, spread = 0.45, speed = 0.05)
        sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_BASS, volume = 0.9f, pitch = 0.55f)
    }

    private inner class RedSkill : Skill() {
        override val name = "<bold>난이도 상승"
        override val description = listOf(
            "<gray>출제되는 수학 문제의 난이도를 상승시킨다."
        )
        override val cooldown = MATHEMATICIAN_CALCULATION_COOLDOWN_SECONDS
        override fun isUseSuccess(): Boolean {
            if (difficulty < MAX_DIFFICULTY) return true
            player.sendMiniMessage("<red><bold>[!] 이미 최고 난이도입니다.")
            return false
        }
        override fun use() { changeDifficulty(1) }
    }

    private inner class OrangeSkill : Skill() {
        override val name = "<bold>난이도 하락"
        override val description = listOf("<gray>출제되는 수학 문제의 난이도를 하락시킨다.")
        override val cooldown = MATHEMATICIAN_PROOF_COOLDOWN_SECONDS
        override fun isUseSuccess(): Boolean {
            if (difficulty > 1) return true
            player.sendMiniMessage("<red><bold>[!] 이미 최저 난이도입니다.")
            return false
        }
        override fun use() { changeDifficulty(-1) }
    }

    private inner class Passive : BasePassive(), OnHitHandler {
        override val name = "<bold>문제 풀이"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>30초마다 설정한 난이도에 따라 수학 문제가 출제된다.",
            "<gray>난이도는 1~10이며 최대 난이도에는 고등학교 3학년 과정이 포함된다.", "",
            "<gray>정답을 맞추면 정답 스택을 맞춘 문제의 난이도에 비례하여 얻는다.",
            "<gray>오답이거나 제한 시간 초과 시 정답 스택이 전부 사라진다.", "",
            "<gray>정답 스택당 가하는 피해가 1% 증가하고 이동 속도가 1% 증가한다. (최대 100스택)"
        )

        override fun onHit(context: DamageContext) {
            val stacks = stackStatus().power
            if (stacks > 0) {
                context.addDamageDealtMultiplier(1.0 + stacks * MATHEMATICIAN_DAMAGE_PERCENT_PER_STACK / 100.0)
            }
        }
    }

    companion object {
        private const val MAX_DIFFICULTY = 10

        private data class MathProblem(
            val owner: Mathematician,
            val answer: String,
            val difficulty: Int,
            val token: UUID,
            val timeoutTask: BukkitTask,
        )

        private val activeProblems: ConcurrentHashMap<UUID, MathProblem> = ConcurrentHashMap()

        fun hasActiveProblem(playerId: UUID): Boolean = activeProblems.containsKey(playerId)

        fun handleChatInput(player: Player, input: String) {
            val session = activeProblems[player.uniqueId] ?: return
            session.owner.acceptInput(session, input)
        }

        fun clearSessions(playerIds: Collection<UUID>) {
            playerIds.forEach { activeProblems.remove(it)?.timeoutTask?.cancel() }
        }
    }
}
