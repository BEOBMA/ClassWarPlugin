package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.manager.PlayerManager.heal
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.MoveSpeedDecrease
import org.beobma.classWarPlugin.status.list.MoveSpeedIncrease
import org.beobma.classWarPlugin.status.list.Shield
import org.beobma.classWarPlugin.status.list.GamblerCardStatus
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.*
import java.util.ArrayDeque

// 밸런스 조정 상수
private const val GAMBLER_HIT_COOLDOWN_SECONDS = 5
private const val GAMBLER_STAND_COOLDOWN_SECONDS = 20
private const val GAMBLER_DOUBLE_COOLDOWN_SECONDS = 70
private const val GAMBLER_EFFECT_DURATION_SECONDS = 8
private const val GAMBLER_SHIELD_POWER = 4
private const val GAMBLER_MOVE_SPEED_BONUS_PERCENT = 15
private const val GAMBLER_MOVE_SPEED_PENALTY_PERCENT = 10

class Gambler : GameClass(), GameStatusHandler {
    override val classId = "gambler"
    override val name = "<gray>도박사"
    override val rank = Rank.B
    override val classItemMaterial = Material.PAPER

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        YellowSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private val deck = ArrayDeque<Int>()
    private val hand = mutableListOf<Int>()
    private var damageMultiplier = 1.0
    private var damageMultiplierUntil = 0L

    private fun updateCardStatus() {
        playerData.getOrCreateStatus(playerData) { GamblerCardStatus() }.updateCards(hand)
    }

    override fun onBattleStart() = shuffleAndDeal()
    override fun onGameTimePasses() = Unit

    private fun freshDeck(): List<Int> = buildList {
        repeat(4) { addAll(1..9); repeat(4) { add(10) } }
    }.shuffled()

    private fun shuffleAndDeal() {
        deck.clear()
        freshDeck().forEach(deck::addLast)
        hand.clear()
        updateCardStatus()
        drawCard(resolve = false)
        drawCard(resolve = true)
    }

    private fun drawCard(resolve: Boolean = true, doubled: Boolean = false): Outcome {
        if (deck.isEmpty()) freshDeck().forEach(deck::addLast)
        val card = deck.removeFirst()
        hand += card
        updateCardStatus()
        sounds.playTo(player, Sound.ITEM_BOOK_PAGE_TURN, pitch = 0.8f + card * 0.04f)
        particles.spawn(player, Particle.ENCHANT, count = 8, spread = 0.35)
        if (!resolve) return Outcome.NONE
        val outcome = when {
            hand.sum() == 21 -> Outcome.JACKPOT
            hand.sum() > 21 -> Outcome.BUST
            else -> Outcome.NONE
        }
        if (outcome != Outcome.NONE) resolveOutcome(outcome, doubled)
        return outcome
    }

    private fun resolveOutcome(outcome: Outcome, doubled: Boolean) {
        val factor = if (doubled) 2 else 1
        when (outcome) {
            Outcome.JACKPOT -> {
                playerData.heal(4.0 * factor, DamageType.Normal, playerData)
                playerData.addStatus(Shield(), playerData)
                    .applyStatus(duration = GAMBLER_EFFECT_DURATION_SECONDS, powerDelta = GAMBLER_SHIELD_POWER * factor)
                playerData.addStatus(MoveSpeedIncrease(), playerData).applyStatus(
                    duration = GAMBLER_EFFECT_DURATION_SECONDS,
                    powerSet = GAMBLER_MOVE_SPEED_BONUS_PERCENT * factor,
                )
                setDamageMultiplier(1.0 + 0.2 * factor, 8)
                sounds.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, volume = 1.2f, pitch = 1.25f)
                particles.spawn(player, Particle.TOTEM_OF_UNDYING, count = 35, spread = 0.7, speed = 0.2)
            }
            Outcome.BUST -> {
                playerData.addStatus(MoveSpeedDecrease(), playerData).applyStatus(
                    duration = GAMBLER_EFFECT_DURATION_SECONDS * factor,
                    powerSet = GAMBLER_MOVE_SPEED_PENALTY_PERCENT,
                )
                setDamageMultiplier(0.85, 8 * factor)
                sounds.play(player, Sound.ENTITY_VILLAGER_NO, pitch = 0.65f)
                particles.spawn(player, Particle.SMOKE, count = 20, spread = 0.5)
            }
            Outcome.NONE -> Unit
        }
        shuffleAndDeal()
    }

    private fun stand(multiplier: Int = 1) {
        val total = hand.sum()
        playerData.heal((total / 5.0) * multiplier, DamageType.Normal, playerData)
        setDamageMultiplier(1.0 + total * 0.01 * multiplier, 5)
        sounds.play(player, Sound.BLOCK_NOTE_BLOCK_CHIME, pitch = 1.2f)
        shuffleAndDeal()
    }

    private fun setDamageMultiplier(multiplier: Double, seconds: Int) {
        damageMultiplier = multiplier
        damageMultiplierUntil = game.combatTick + seconds * 20L
    }

    private enum class Outcome { NONE, JACKPOT, BUST }

    private inner class RedSkill : Skill() {
        override val definitionId = "gambler/red-skill"
        override val name = "<bold>히트"
        override val description = listOf(
            "<gray>덱에서 {keyword:Card}를 1장 뽑는다."
        )
        override val cooldown = GAMBLER_HIT_COOLDOWN_SECONDS

        override fun use(): Boolean {
            drawCard()
            return true
        }
    }

    private inner class OrangeSkill : Skill() {
        override val definitionId = "gambler/orange-skill"
        override val name = "<bold>스탠드"
        override val description = listOf(
            "<gray>패를 덱으로 되돌리고 덱을 섞는다.",
            "<gray>5초간 덱으로 되돌린 {keyword:Card}의 숫자 합계 1당 가하는 피해가 1% 증가한다.",
            "{keyword:Card}의 숫자 합계 5당 체력을 1 회복한다.",
        )
        override val cooldown = GAMBLER_STAND_COOLDOWN_SECONDS

        override fun use(): Boolean {
            stand()
            return true
        }
    }

    private inner class YellowSkill : Skill() {
        override val definitionId = "gambler/yellow-skill"
        override val name = "<bold>더블"
        override val description = listOf(
            "<gray>덱에서 {keyword:Card}를 1장 뽑는다.",
            "<gray>이 스킬로 잭팟이 발동하면 잭팟의 효과가 2배로 증가한다.",
            "<gray>이 스킬로 버스트가 발동하면 버스트의 지속 시간이 2배로 증가한다.",
            "<gray>두 효과 모두 발동하지 않았다면 스탠드의 효과를 2배로 적용하여 발동한다."
        )
        override val cooldown = GAMBLER_DOUBLE_COOLDOWN_SECONDS

        override fun use(): Boolean {
            val outcome = drawCard(doubled = true)
            if (outcome == Outcome.NONE) stand(2)
            return true
        }
    }

    private inner class Passive : BasePassive(), OnHitHandler {
        override val name = "<bold>블랙잭"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>덱을 섞을 때마다 {keyword:Card}를 2장 뽑는다.",
            "<gray>카드를 뽑았을 때, 패의 모든 {keyword:Card}의 숫자 합계가 21이면 잭팟이 발동한다.",
            "<gray>{keyword:Card} 숫자의 합계가 21을 초과하면 버스트가 발동한다.",
            "<gray>패가 확정된 후 효과를 발동한 뒤 패를 덱으로 되돌리고 덱을 섞는다.",
            "",
            "<gray>잭팟:",
            "<gray>  체력 4 회복",
            "<gray>  8초간 <aqua><bold>4의 피해를 막는 {keyword:Shield} 얻음",
            "<gray>  8초간 가하는 피해 20% 증가",
            "<gray>  8초간 이동 속도 15% 증가",
            "<gray>버스트:",
            "<gray>  8초간 가하는 피해 15% 감소",
            "<gray>  8초간 이동 속도 10% 감소",
        )

        override fun onHit(context: DamageContext) {
            if (game.combatTick >= damageMultiplierUntil) damageMultiplier = 1.0
            context.addDamageDealtMultiplier(damageMultiplier)
        }
    }
}
