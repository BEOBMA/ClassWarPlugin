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
        1 -> generateBasicArithmeticProblem()
        2 -> generateMultiplicationProblem()
        3 -> generateMixedArithmeticProblem()
        4 -> generateAppliedArithmeticProblem()
        5 -> generateLinearProblem()
        6 -> generateQuadraticProblem()
        7 -> generateSequenceProblem()
        8 -> generateLogarithmProblem()
        9 -> generateCalculusProblem()
        else -> generateSeniorProblem()
    }

    private fun generateBasicArithmeticProblem(): Pair<String, Int> = when (Random.nextInt(PROBLEM_VARIANTS_PER_DIFFICULTY)) {
        0 -> {
            val a = Random.nextInt(10, 201); val b = Random.nextInt(5, 151)
            "$a + $b = ?" to a + b
        }
        1 -> {
            val answer = Random.nextInt(5, 151); val b = Random.nextInt(5, 151)
            "${answer + b} − $b = ?" to answer
        }
        2 -> {
            val a = Random.nextInt(10, 101); val b = Random.nextInt(5, 81); val c = Random.nextInt(5, 61)
            "$a + $b − $c = ?" to a + b - c
        }
        3 -> {
            val x = Random.nextInt(5, 151); val b = Random.nextInt(5, 101)
            "x + $b = ${x + b}, x = ?" to x
        }
        4 -> {
            val a = Random.nextInt(-100, 101); val b = Random.nextInt(-100, 101)
            "|$a − ($b)| = ?" to kotlin.math.abs(a - b)
        }
        5 -> {
            val x = Random.nextInt(5, 101); val a = Random.nextInt(10, 151)
            "$a + x = ${a + x}, x = ?" to x
        }
        6 -> {
            val value = Random.nextInt(10, 1000)
            "$value 을(를) 십의 자리로 반올림한 값 = ?" to ((value + 5) / 10) * 10
        }
        7 -> {
            val a = Random.nextInt(-50, 51); val b = Random.nextInt(-50, 51)
            "($a) + ($b) = ?" to a + b
        }
        8 -> {
            val divisor = Random.nextInt(3, 13); val quotient = Random.nextInt(3, 31)
            val remainder = Random.nextInt(1, divisor)
            "${divisor * quotient + remainder} ÷ $divisor 의 나머지 = ?" to remainder
        }
        9 -> {
            val value = Random.nextInt(100, 1000)
            "$value 의 각 자리 숫자의 합 = ?" to value / 100 + value / 10 % 10 + value % 10
        }
        10 -> {
            val values = List(4) { Random.nextInt(-50, 101) }
            "${values.joinToString(", ")} 중 최댓값과 최솟값의 차 = ?" to
                (values.maxOf { it } - values.minOf { it })
        }
        else -> {
            val a = Random.nextInt(-15, 16); val b = Random.nextInt(-15, 16)
            "a = $a, b = $b 일 때 2a + b = ?" to 2 * a + b
        }
    }

    private fun generateMultiplicationProblem(): Pair<String, Int> = when (Random.nextInt(PROBLEM_VARIANTS_PER_DIFFICULTY)) {
        0 -> {
            val a = Random.nextInt(3, 31); val b = Random.nextInt(3, 31)
            "$a × $b = ?" to a * b
        }
        1 -> {
            val divisor = Random.nextInt(2, 21); val quotient = Random.nextInt(3, 41)
            "${divisor * quotient} ÷ $divisor = ?" to quotient
        }
        2 -> {
            val a = Random.nextInt(2, 21); val b = Random.nextInt(2, 11)
            "${a}² + $b = ?" to a * a + b
        }
        3 -> {
            val a = Random.nextInt(4, 26); val b = Random.nextInt(3, 16); val c = Random.nextInt(5, 101)
            "$a × $b − $c = ?" to a * b - c
        }
        4 -> {
            val a = Random.nextInt(2, 16); val b = Random.nextInt(2, 16)
            val c = Random.nextInt(2, 16); val d = Random.nextInt(2, 16)
            "$a × $b + $c × $d = ?" to a * b + c * d
        }
        5 -> {
            val a = Random.nextInt(2, 10); val b = Random.nextInt(2, 10); val c = Random.nextInt(2, 8)
            "$a × $b × $c = ?" to a * b * c
        }
        6 -> {
            val a = Random.nextInt(8, 31); val b = Random.nextInt(2, a)
            "($a + $b) × ($a − $b) = ?" to (a + b) * (a - b)
        }
        7 -> {
            val divisor = Random.nextInt(4, 16); val quotient = Random.nextInt(5, 31)
            val remainder = Random.nextInt(1, divisor)
            "${divisor * quotient + remainder}을(를) $divisor 로 나눈 몫 = ?" to quotient
        }
        8 -> {
            val boxes = Random.nextInt(3, 16); val perBox = Random.nextInt(6, 25)
            "상자 $boxes 개에 물건이 각각 $perBox 개씩 있다. 총 개수 = ?" to boxes * perBox
        }
        9 -> {
            val a = Random.nextInt(3, 21); val b = Random.nextInt(3, 21); val c = Random.nextInt(2, 16)
            "$a × $c + $b × $c = ?" to (a + b) * c
        }
        10 -> {
            val hours = Random.nextInt(1, 7); val minutes = Random.nextInt(1, 12) * 5
            "$hours 시간 $minutes 분은 모두 몇 분인가?" to hours * 60 + minutes
        }
        else -> {
            val n = Random.nextInt(4, 8)
            "$n! = ?" to factorial(n)
        }
    }

    private fun generateMixedArithmeticProblem(): Pair<String, Int> = when (Random.nextInt(PROBLEM_VARIANTS_PER_DIFFICULTY)) {
        0 -> {
            val a = Random.nextInt(4, 21); val b = Random.nextInt(3, 16); val c = Random.nextInt(5, 61)
            "$a × $b + $c = ?" to a * b + c
        }
        1 -> {
            val a = Random.nextInt(5, 31); val b = Random.nextInt(3, 21); val c = Random.nextInt(2, 11)
            "($a + $b) × $c = ?" to (a + b) * c
        }
        2 -> {
            val a = Random.nextInt(20, 101); val b = Random.nextInt(3, 16)
            val c = Random.nextInt(3, 16); val d = Random.nextInt(5, 51)
            "$a + $b × $c − $d = ?" to a + b * c - d
        }
        3 -> {
            val average = Random.nextInt(10, 51); val a = Random.nextInt(5, average * 2)
            val b = Random.nextInt(5, average * 2); val c = average * 3 - a - b
            "($a + $b + $c) ÷ 3 = ?" to average
        }
        4 -> {
            val a = Random.nextInt(6, 31); val b = Random.nextInt(6, 31)
            "lcm($a, $b) = ?" to a / greatestCommonDivisor(a, b) * b
        }
        5 -> {
            val a = Random.nextInt(12, 81); val b = Random.nextInt(12, 81)
            "gcd($a, $b) = ?" to greatestCommonDivisor(a, b)
        }
        6 -> {
            val quotient = Random.nextInt(5, 41); val divisor = Random.nextInt(2, 11)
            val a = Random.nextInt(2, quotient * divisor); val b = quotient * divisor - a
            "($a + $b) ÷ $divisor = ?" to quotient
        }
        7 -> {
            val a = Random.nextInt(4, 16); val b = Random.nextInt(3, 13)
            val c = Random.nextInt(2, 8); val d = Random.nextInt(5, 41)
            "(($a + $b) × $c) − $d = ?" to (a + b) * c - d
        }
        8 -> {
            val middle = Random.nextInt(10, 61)
            "${middle - 2}부터 ${middle + 2}까지 연속된 다섯 정수의 합 = ?" to middle * 5
        }
        9 -> {
            val first = Random.nextInt(5, 31); val difference = Random.nextInt(1, 9) * 2
            val values = List(4) { first + it * difference }
            "${values.joinToString(", ")}의 평균 = ?" to first + difference * 3 / 2
        }
        10 -> {
            val denominator = Random.nextInt(2, 9); val numerator = Random.nextInt(1, denominator)
            val unit = Random.nextInt(3, 21); val value = denominator * unit
            "$value 의 $numerator/$denominator = ?" to numerator * unit
        }
        else -> {
            val a = Random.nextInt(8, 31); val b = Random.nextInt(2, a)
            "$a² − $b² = ?" to a * a - b * b
        }
    }

    private fun generateAppliedArithmeticProblem(): Pair<String, Int> = when (Random.nextInt(PROBLEM_VARIANTS_PER_DIFFICULTY)) {
        0 -> {
            val percent = listOf(10, 20, 25, 40, 50, 75).random()
            val unit = if (percent == 25 || percent == 75) 4 else 10
            val value = Random.nextInt(2, 21) * unit
            "$value 의 $percent% = ?" to value * percent / 100
        }
        1 -> {
            val left = Random.nextInt(2, 10); val right = Random.nextInt(2, 10); val factor = Random.nextInt(3, 16)
            "$left : $right = ${left * factor} : x, x = ?" to right * factor
        }
        2 -> {
            val width = Random.nextInt(4, 31); val height = Random.nextInt(4, 31)
            "가로 $width, 세로 $height 인 직사각형의 둘레 = ?" to 2 * (width + height)
        }
        3 -> {
            val base = Random.nextInt(2, 21) * 2; val height = Random.nextInt(3, 26)
            "밑변 $base, 높이 $height 인 삼각형의 넓이 = ?" to base * height / 2
        }
        4 -> {
            val a = Random.nextInt(8, 31); val b = Random.nextInt(2, 8); val c = Random.nextInt(5, 51)
            "($a − $b)² + $c = ?" to (a - b) * (a - b) + c
        }
        5 -> {
            val radius = Random.nextInt(3, 21)
            "반지름이 $radius 인 원의 둘레 (π = 3) = ?" to 6 * radius
        }
        6 -> {
            val firstBase = Random.nextInt(4, 21) * 2; val secondBase = Random.nextInt(3, 18) * 2
            val height = Random.nextInt(3, 16)
            "윗변 $firstBase, 아랫변 $secondBase, 높이 $height 인 사다리꼴의 넓이 = ?" to
                (firstBase + secondBase) * height / 2
        }
        7 -> {
            val speed = Random.nextInt(4, 21); val time = Random.nextInt(3, 16)
            "초속 $speed m로 $time 초 이동한 거리(m) = ?" to speed * time
        }
        8 -> {
            val discount = listOf(10, 20, 25, 50).random()
            val unit = if (discount == 25) 4 else 10
            val price = Random.nextInt(5, 31) * unit * 100
            "$price 원 상품을 $discount% 할인한 가격(원) = ?" to price * (100 - discount) / 100
        }
        9 -> {
            val count = Random.nextInt(3, 13); val unitPrice = Random.nextInt(2, 21) * 100
            "물건 $count 개의 가격이 각각 $unitPrice 원이다. 총 가격(원) = ?" to count * unitPrice
        }
        10 -> {
            val sides = Random.nextInt(5, 13)
            "$sides 각형의 내각의 합(도) = ?" to (sides - 2) * 180
        }
        else -> {
            val width = Random.nextInt(3, 16); val height = Random.nextInt(3, 16); val depth = Random.nextInt(3, 16)
            "가로 $width, 세로 $height, 높이 $depth 인 직육면체의 부피 = ?" to width * height * depth
        }
    }

    private fun generateLinearProblem(): Pair<String, Int> = when (Random.nextInt(PROBLEM_VARIANTS_PER_DIFFICULTY)) {
        0 -> {
            val x = Random.nextInt(-20, 31); val a = Random.nextInt(2, 13); val b = Random.nextInt(5, 51)
            "$a·x + $b = ${a * x + b}, x = ?" to x
        }
        1 -> {
            val x = Random.nextInt(3, 31); val a = Random.nextInt(2, 13); val b = Random.nextInt(5, 51)
            "$a·x − $b = ${a * x - b}, x = ?" to x
        }
        2 -> {
            val x = Random.nextInt(-10, 31); val a = Random.nextInt(2, 11); val b = Random.nextInt(2, 16)
            "$a(x + $b) = ${a * (x + b)}, x = ?" to x
        }
        3 -> {
            val divisor = Random.nextInt(2, 11); val quotient = Random.nextInt(2, 21); val b = Random.nextInt(2, 16)
            "x ÷ $divisor + $b = ${quotient + b}, x = ?" to divisor * quotient
        }
        4 -> {
            val x = Random.nextInt(3, 31); val y = Random.nextInt(1, x)
            "x + y = ${x + y}, x − y = ${x - y}, x = ?" to x
        }
        5 -> {
            val x = Random.nextInt(-12, 21); val a = Random.nextInt(3, 11); val c = Random.nextInt(1, a)
            val b = Random.nextInt(2, 31); val d = (a - c) * x + b
            "${a}x ${signed(b)} = ${c}x ${signed(d)}, x = ?" to x
        }
        6 -> {
            val a = Random.nextInt(2, 11); val b = Random.nextInt(-20, 21); val x = Random.nextInt(-10, 16)
            "f(x) = ${a}x ${signed(b)} 일 때 f($x) = ?" to a * x + b
        }
        7 -> {
            val x = Random.nextInt(2, 21); val y = Random.nextInt(2, 21)
            "x + y = ${x + y}, 2x − y = ${2 * x - y}, y = ?" to y
        }
        8 -> {
            val center = Random.nextInt(-10, 11); val distance = Random.nextInt(2, 16)
            "|x ${signed(-center)}| = $distance, 큰 해 x = ?" to center + distance
        }
        9 -> {
            val first = Random.nextInt(3, 41)
            "연속된 세 정수의 합이 ${3 * (first + 1)}일 때 가장 작은 수 = ?" to first
        }
        10 -> {
            val left = Random.nextInt(2, 10); val right = Random.nextInt(2, 10); val scale = Random.nextInt(3, 16)
            "x : ${right * scale} = $left : $right, x = ?" to left * scale
        }
        else -> {
            val divisor = Random.nextInt(2, 11); val quotient = Random.nextInt(-10, 21); val addend = Random.nextInt(2, 21)
            "(x − $addend) ÷ $divisor = $quotient, x = ?" to quotient * divisor + addend
        }
    }

    private fun generateQuadraticProblem(): Pair<String, Int> {
        val smallerRoot = Random.nextInt(1, 8)
        val largerRoot = Random.nextInt(smallerRoot + 1, 13)
        val rootSum = smallerRoot + largerRoot
        val rootProduct = smallerRoot * largerRoot
        return when (Random.nextInt(PROBLEM_VARIANTS_PER_DIFFICULTY)) {
            0 -> "x² − $rootSum·x + $rootProduct = 0, 큰 근 x = ?" to largerRoot
            1 -> "x² − $rootSum·x + $rootProduct = 0, 작은 근 x = ?" to smallerRoot
            2 -> "x² − $rootSum·x + $rootProduct = 0, 두 근의 합 = ?" to rootSum
            3 -> "x² − $rootSum·x + $rootProduct = 0, 두 근의 곱 = ?" to rootProduct
            4 -> {
                val a = Random.nextInt(1, 6); val vertexX = Random.nextInt(-8, 9); val constant = Random.nextInt(1, 20)
                "f(x) = ${a}x² ${signed(-2 * a * vertexX)}x + $constant, 꼭짓점의 x좌표 = ?" to vertexX
            }
            5 -> "x² − $rootSum·x + $rootProduct = 0의 판별식 = ?" to rootSum * rootSum - 4 * rootProduct
            6 -> "x² − $rootSum·x + $rootProduct = 0, 두 근의 차 = ?" to largerRoot - smallerRoot
            7 -> {
                val vertexX = Random.nextInt(-10, 11); val minimum = Random.nextInt(-20, 21)
                "f(x) = (x ${signed(-vertexX)})² ${signed(minimum)}, 최솟값 = ?" to minimum
            }
            8 -> {
                val vertexX = Random.nextInt(-10, 11); val maximum = Random.nextInt(1, 31)
                "f(x) = −(x ${signed(-vertexX)})² + $maximum, 최댓값 = ?" to maximum
            }
            9 -> {
                val x = Random.nextInt(-6, 11)
                "f(x) = x² − $rootSum·x + $rootProduct 일 때 f($x) = ?" to x * x - rootSum * x + rootProduct
            }
            10 -> {
                val root = Random.nextInt(2, 16)
                "x² − ${2 * root}x + ${root * root} = 0, x = ?" to root
            }
            else -> {
                val positiveRoot = Random.nextInt(3, 31)
                "x² = ${positiveRoot * positiveRoot}, 양의 해 x = ?" to positiveRoot
            }
        }
    }

    private fun generateSequenceProblem(): Pair<String, Int> = when (Random.nextInt(PROBLEM_VARIANTS_PER_DIFFICULTY)) {
        0 -> {
            val firstTerm = Random.nextInt(2, 12)
            val difference = Random.nextInt(2, 8)
            val termCount = Random.nextInt(7, 13)
            val lastTerm = firstTerm + (termCount - 1) * difference
            val sum = termCount * (firstTerm + lastTerm) / 2
            "a₁ = $firstTerm, d = $difference, n = $termCount ⇒ Sₙ = ?" to sum
        }
        1 -> {
            val firstTerm = Random.nextInt(1, 5); val ratio = Random.nextInt(2, 4); val termCount = Random.nextInt(5, 9)
            var term = firstTerm; var sum = 0
            repeat(termCount) { sum += term; term *= ratio }
            "a₁ = $firstTerm, r = $ratio, n = $termCount ⇒ Sₙ = ?" to sum
        }
        2 -> {
            val first = Random.nextInt(2, 21); val difference = Random.nextInt(-6, 10); val n = Random.nextInt(8, 21)
            "a₁ = $first, d = $difference 일 때 a${toSubscript(n)} = ?" to first + (n - 1) * difference
        }
        3 -> {
            val first = Random.nextInt(1, 16); val difference = Random.nextInt(2, 11); val n = Random.nextInt(6, 16)
            val last = first + (n - 1) * difference
            "a₁ = $first, a${toSubscript(n)} = $last 인 등차수열의 공차 d = ?" to difference
        }
        4 -> {
            val first = Random.nextInt(1, 6); val second = Random.nextInt(1, 6); val n = Random.nextInt(8, 14)
            var previous = first; var current = second
            repeat(n - 2) { val next = previous + current; previous = current; current = next }
            "a₁ = $first, a₂ = $second, aₙ = aₙ₋₁ + aₙ₋₂ 일 때 a${toSubscript(n)} = ?" to current
        }
        5 -> {
            val first = Random.nextInt(1, 5); val ratio = Random.nextInt(2, 4); val n = Random.nextInt(5, 8)
            "a₁ = $first, r = $ratio 인 등비수열의 a${toSubscript(n)} = ?" to first * intPower(ratio, n - 1)
        }
        6 -> {
            val first = Random.nextInt(1, 6); val ratio = Random.nextInt(2, 5); val n = Random.nextInt(4, 8)
            val last = first * intPower(ratio, n - 1)
            "a₁ = $first, a${toSubscript(n)} = $last 인 등비수열의 공비 r = ?" to ratio
        }
        7 -> {
            val n = Random.nextInt(10, 31)
            "1부터 $n 까지 자연수의 합 = ?" to n * (n + 1) / 2
        }
        8 -> {
            val start = Random.nextInt(3, 21); val n = Random.nextInt(7, 13)
            val answer = if (n % 2 == 1) start + n - 1 else -(start + n - 1)
            "$start, ${-(start + 1)}, ${start + 2}, ${-(start + 3)}, ... 의 제$n 항 = ?" to answer
        }
        9 -> {
            val coefficient = Random.nextInt(1, 5); val linear = Random.nextInt(-4, 7); val n = Random.nextInt(5, 13)
            "aₙ = ${coefficient}n² ${signed(linear)}n 일 때 a${toSubscript(n)} = ?" to
                coefficient * n * n + linear * n
        }
        10 -> {
            val left = Random.nextInt(2, 31); val mean = Random.nextInt(left + 1, left + 21)
            val right = 2 * mean - left
            "$left, x, $right 가 등차수열일 때 x = ?" to mean
        }
        else -> {
            val first = Random.nextInt(1, 8); val multiplier = Random.nextInt(2, 4)
            val addend = Random.nextInt(1, 6); val n = Random.nextInt(5, 8)
            var term = first
            repeat(n - 1) { term = multiplier * term + addend }
            "a₁ = $first, aₙ = $multiplier·aₙ₋₁ + $addend 일 때 a${toSubscript(n)} = ?" to term
        }
    }

    private fun generateLogarithmProblem(): Pair<String, Int> = when (Random.nextInt(PROBLEM_VARIANTS_PER_DIFFICULTY)) {
        0 -> {
            val base = Random.nextInt(2, 6)
            val knownExponent = Random.nextInt(1, 4)
            val answerExponent = Random.nextInt(2, 5)
            val rightSide = knownExponent + answerExponent
            val knownValue = intPower(base, knownExponent)
            val answer = intPower(base, answerExponent)
            val baseSubscript = toSubscript(base)
            "log$baseSubscript(x) + log$baseSubscript($knownValue) = $rightSide, x = ?" to answer
        }
        1 -> {
            val base = Random.nextInt(2, 8); val exponent = Random.nextInt(2, 7)
            "log${toSubscript(base)}(${intPower(base, exponent)}) = ?" to exponent
        }
        2 -> {
            val base = Random.nextInt(2, 7); val exponent = Random.nextInt(2, 7)
            "${base}ˣ = ${intPower(base, exponent)}, x = ?" to exponent
        }
        3 -> {
            val root = Random.nextInt(3, 31); val addend = Random.nextInt(2, 31)
            "√${root * root} + $addend = ?" to root + addend
        }
        4 -> {
            val base = Random.nextInt(2, 6); val a = Random.nextInt(3, 8)
            val b = Random.nextInt(2, 7); val c = Random.nextInt(1, minOf(a + b, 7))
            "$base${toSuperscript(a)} × $base${toSuperscript(b)} ÷ $base${toSuperscript(c)} = ?" to intPower(base, a + b - c)
        }
        5 -> {
            val base = Random.nextInt(2, 7); val largerExponent = Random.nextInt(4, 8)
            val smallerExponent = Random.nextInt(1, largerExponent)
            "log${toSubscript(base)}(${intPower(base, largerExponent)}) − " +
                "log${toSubscript(base)}(${intPower(base, smallerExponent)}) = ?" to
                largerExponent - smallerExponent
        }
        6 -> {
            val base = Random.nextInt(2, 6); val coefficient = Random.nextInt(2, 10); val exponent = Random.nextInt(2, 7)
            "$coefficient × ${base}ˣ = ${coefficient * intPower(base, exponent)}, x = ?" to exponent
        }
        7 -> {
            val firstRoot = Random.nextInt(3, 21); val secondRoot = Random.nextInt(3, 21)
            "√${firstRoot * firstRoot} × √${secondRoot * secondRoot} = ?" to firstRoot * secondRoot
        }
        8 -> {
            val cubeRoot = Random.nextInt(2, 13); val addend = Random.nextInt(2, 31)
            "∛${cubeRoot * cubeRoot * cubeRoot} + $addend = ?" to cubeRoot + addend
        }
        9 -> {
            val firstExponent = Random.nextInt(2, 7); val secondExponent = Random.nextInt(2, 7)
            "log₂(${intPower(2, firstExponent)}) × log₃(${intPower(3, secondExponent)}) = ?" to
                firstExponent * secondExponent
        }
        10 -> {
            val base = Random.nextInt(2, 6); val outer = Random.nextInt(2, 4)
            val inner = Random.nextInt(2, 4); val removed = Random.nextInt(1, outer * inner)
            "(${base}${toSuperscript(inner)})${toSuperscript(outer)} ÷ " +
                "$base${toSuperscript(removed)} = ?" to intPower(base, outer * inner - removed)
        }
        else -> {
            val exponent = Random.nextInt(3, 9); val coefficient = Random.nextInt(2, 10)
            "${coefficient * intPower(10, exponent)} = $coefficient × 10ˣ, x = ?" to exponent
        }
    }

    private fun generateCalculusProblem(): Pair<String, Int> = when (Random.nextInt(PROBLEM_VARIANTS_PER_DIFFICULTY)) {
        0 -> {
            val coefficient = Random.nextInt(1, 5)
            val linear = Random.nextInt(1, 8)
            val point = Random.nextInt(1, 6)
            val answer = 2 * coefficient * point + linear
            "f(x) = ${coefficient}x² + ${linear}x + 1, f′($point) = ?" to answer
        }
        1 -> {
            val coefficient = Random.nextInt(1, 5); val point = Random.nextInt(2, 8)
            "limₕ→₀ [${coefficient}(($point + h)² − ${point * point}) ÷ h] = ?" to 2 * coefficient * point
        }
        2 -> {
            val cubic = Random.nextInt(1, 5); val linear = Random.nextInt(1, 9); val point = Random.nextInt(1, 6)
            "f(x) = ${cubic}x³ + ${linear}x, f′($point) = ?" to 3 * cubic * point * point + linear
        }
        3 -> {
            val a = Random.nextInt(1, 6); val b = Random.nextInt(1, 9); val upper = Random.nextInt(2, 8)
            "∫₀${toSuperscript(upper)} (${2 * a}x + $b) dx = ?" to a * upper * upper + b * upper
        }
        4 -> {
            val a = Random.nextInt(2, 16)
            "limₓ→$a (x² − ${a * a}) ÷ (x − $a) = ?" to 2 * a
        }
        5 -> {
            val coefficient = Random.nextInt(1, 4); val point = Random.nextInt(1, 5)
            "f(x) = ${coefficient}x⁴, f′($point) = ?" to 4 * coefficient * intPower(point, 3)
        }
        6 -> {
            val coefficient = Random.nextInt(1, 6); val point = Random.nextInt(1, 7)
            "y = ${coefficient}x² 위 x = $point 에서 접선의 y절편 = ?" to -coefficient * point * point
        }
        7 -> {
            val coefficient = Random.nextInt(1, 5); val upper = Random.nextInt(2, 7)
            "∫₀${toSuperscript(upper)} ${3 * coefficient}x² dx = ?" to coefficient * intPower(upper, 3)
        }
        8 -> {
            val first = Random.nextInt(1, 9); val second = Random.nextInt(1, 9); val point = Random.nextInt(1, 8)
            "f(x) = (x + $first)(x + $second), f′($point) = ?" to 2 * point + first + second
        }
        9 -> {
            val first = Random.nextInt(1, 10); val second = Random.nextInt(first + 1, first + 11)
            "f(x) = x²의 x = $first 에서 x = $second 까지 평균변화율 = ?" to first + second
        }
        10 -> {
            val acceleration = Random.nextInt(1, 6); val initialVelocity = Random.nextInt(1, 11); val time = Random.nextInt(2, 9)
            "위치 s(t) = ${acceleration}t² + ${initialVelocity}t, t = $time 에서 속도 = ?" to
                2 * acceleration * time + initialVelocity
        }
        else -> {
            val slopeHalf = Random.nextInt(1, 6); val upper = Random.nextInt(2, 11)
            "∫₀${toSuperscript(upper)} ${2 * slopeHalf}x dx = ?" to slopeHalf * upper * upper
        }
    }

    private fun generateSeniorProblem(): Pair<String, Int> = when (Random.nextInt(PROBLEM_VARIANTS_PER_DIFFICULTY)) {
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
        2 -> {
            val denominatorCoefficient = Random.nextInt(2, 7)
            val answer = Random.nextInt(2, 8)
            val numeratorCoefficient = denominatorCoefficient * answer
            val numeratorConstant = Random.nextInt(1, 10)
            val denominatorConstant = Random.nextInt(1, 10)
            "limₙ→∞ (${numeratorCoefficient}n² + $numeratorConstant) ÷ " +
                "(${denominatorCoefficient}n² + $denominatorConstant) = ?" to answer
        }
        3 -> {
            val n = Random.nextInt(6, 13); val r = Random.nextInt(2, minOf(6, n))
            "${n}C$r = ?" to combination(n, r)
        }
        4 -> {
            val a = Random.nextInt(-8, 9); val b = Random.nextInt(-8, 9)
            val c = Random.nextInt(-8, 9); val d = Random.nextInt(-8, 9)
            "det[[$a, $b], [$c, $d]] = ?" to a * d - b * c
        }
        5 -> {
            val coefficient = Random.nextInt(2, 7); val constant = Random.nextInt(1, 8); val point = Random.nextInt(1, 6)
            val inner = coefficient * point * point + constant
            "f(x) = (${coefficient}x² + $constant)², f′($point) = ?" to 4 * coefficient * point * inner
        }
        6 -> {
            val n = Random.nextInt(6, 11); val r = Random.nextInt(2, minOf(5, n))
            "${n}P$r = ?" to permutation(n, r)
        }
        7 -> {
            val n = Random.nextInt(6, 13); val r = Random.nextInt(2, minOf(6, n))
            "(1 + x)${toSuperscript(n)}에서 x${toSuperscript(r)}의 계수 = ?" to combination(n, r)
        }
        8 -> {
            val a = Random.nextInt(-5, 6); val d = Random.nextInt(-5, 6); val f = Random.nextInt(-5, 6)
            val b = Random.nextInt(-5, 6); val c = Random.nextInt(-5, 6); val e = Random.nextInt(-5, 6)
            "det[[$a, $b, $c], [0, $d, $e], [0, 0, $f]] = ?" to a * d * f
        }
        9 -> {
            val a = Random.nextInt(-8, 9); val b = Random.nextInt(-8, 9)
            val c = Random.nextInt(-8, 9); val d = Random.nextInt(-8, 9)
            "벡터 ($a, $b) · ($c, $d) = ?" to a * c + b * d
        }
        10 -> {
            val red = Random.nextInt(1, 10)
            "빨간 공 $red 개와 파란 공 ${10 - red} 개 중 하나를 뽑을 때 빨간 공 확률(%) = ?" to red * 10
        }
        else -> {
            val first = Random.nextInt(1, 16) * 2; val second = Random.nextInt(1, 16) * 2
            "$first·sin30° + $second·cos60° = ?" to (first + second) / 2
        }
    }

    private fun greatestCommonDivisor(first: Int, second: Int): Int {
        var a = kotlin.math.abs(first); var b = kotlin.math.abs(second)
        while (b != 0) { val remainder = a % b; a = b; b = remainder }
        return a
    }

    private fun combination(n: Int, r: Int): Int {
        val selected = minOf(r, n - r)
        var result = 1
        for (index in 1..selected) result = result * (n - selected + index) / index
        return result
    }

    private fun factorial(value: Int): Int = (2..value).fold(1) { result, factor -> result * factor }

    private fun permutation(n: Int, r: Int): Int =
        ((n - r + 1)..n).fold(1) { result, factor -> result * factor }

    private fun signed(value: Int): String = if (value >= 0) "+ $value" else "− ${-value}"

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
            "<gray>난이도는 1~10이며 최대 난이도에는 고등학교 3학년 과정이 포함된다.",
            "<gray>사칙연산부터 수열, 로그, 미적분, 확률, 벡터까지 120개 이상의 유형이 출제된다.", "",
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
        private const val PROBLEM_VARIANTS_PER_DIFFICULTY = 12

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
