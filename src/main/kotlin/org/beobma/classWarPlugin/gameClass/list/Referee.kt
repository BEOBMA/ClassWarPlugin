package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ability.TickPolicy
import org.beobma.classWarPlugin.ability.AbilityExecution

import org.beobma.classWarPlugin.ability.Control
import org.beobma.classWarPlugin.ability.ControlLease

import org.beobma.classWarPlugin.gameClass.referee.*
import org.beobma.classWarPlugin.gameClass.referee.DefenseCatalog.OPTION_COUNT as REFEREE_DEFENSE_OPTION_COUNT

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.effect.ParticleApi
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.effect.SoundApi
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.game.GamePhase
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.MapTransferBorderManager
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.UtilManager.getPlayerMaxHealth
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.Silence
import org.beobma.classWarPlugin.status.list.Snare
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.status.list.Disarm
import org.beobma.classWarPlugin.status.list.Radiation
import org.beobma.classWarPlugin.status.list.Invincibility
import org.beobma.classWarPlugin.util.CourtroomMidiPlayer
import org.beobma.classWarPlugin.util.HitboxUtil
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.beobma.classWarPlugin.skill.Passive as BasePassive

private const val REFEREE_PROSECUTION_COOLDOWN_SECONDS = 60
private const val REFEREE_ACCUSATION_SECONDS = 30
private const val REFEREE_DEFENSE_SECONDS = 34
private const val REFEREE_COMBAT_MEMORY_MILLIS = 10_000L
private const val REFEREE_ESCAPE_DISTANCE = 14.0
private const val REFEREE_MAX_EVIDENCE_PER_CRIME = 12

class Referee : GameClass(), GameStatusHandler, org.beobma.classWarPlugin.gameClass.handler.GameEndHandler {
    override fun onGameEnd() = clearSessions(listOf(playerData.uniqueId))
    override val classId = "referee"
    override val name = "<gray>심판관"
    override val rank = Rank.SPECIAL
    override val classItemMaterial = Material.MACE
    override var skills: List<Skill> = listOf(Prosecution())
    override var passives: List<BasePassive> = listOf(GreatScale(), OriginalSin())

    private val evidenceByAccused = mutableMapOf<UUID, MutableMap<Crime, ArrayDeque<Evidence>>>()
    private val skillUseTimes = mutableMapOf<UUID, ArrayDeque<Long>>()
    private val combatTraces = mutableMapOf<CombatPair, CombatTrace>()

    override fun onBattleStart() {
        activeReferees[player.uniqueId] = this
    }

    override fun onGameTimePasses() {
        if (playerData.entityStatus.isDead || abilityScope.isClosed) {
            activeReferees.remove(player.uniqueId, this)
            return
        }
        val now = (game.combatTick * 50L)
        skillUseTimes.values.forEach { times ->
            while (times.isNotEmpty() && now - times.first() > REFEREE_COMBAT_MEMORY_MILLIS) times.removeFirst()
        }
        detectEscapes(now)
    }

    private inner class Prosecution : Skill() {
        override val definitionId = "referee/prosecution"
        override val name = "<bold>기소"
        override val description = listOf(
            "<gray>10칸 내의 바라보는 플레이어를 기소한다.",
            "<gray>게임 내 생존한 모든 플레이어는 재판장으로 이동된다.",
            "",
            "<gray>판사는 30초 안에 기록된 죄목을 채팅으로 지명한다.",
            "<gray>피고인은 ${REFEREE_DEFENSE_SECONDS}초 안에 ${REFEREE_DEFENSE_OPTION_COUNT}개의 진술 중 하나를 정확히 입력해 변론한다.",
            "<gray>진술에는 하나의 진실과 여덟 개의 거짓, 하나의 자백이 섞여 있다.",
            "<gray>진실을 고르면 무죄, 자백하면 유죄, 거짓 진술은 위증으로 가중 처벌된다.",
            "",
            "<dark_gray>재판 종료 후 모든 플레이어는 원래 위치로 복귀한다.",
            "<dark_gray>재판 중 월드보더, 게임 타이머, 쿨타임과 상태이상 시간이 정지한다.",
            "<dark_gray>재판장 내부의 모든 플레이어는 {keyword:Silence}, {keyword:Disarm}, {keyword:Invincibility} 상태가 된다.",
        )
        override val cooldown = REFEREE_PROSECUTION_COOLDOWN_SECONDS
        private var selectedDefendant: PlayerData? by requestValue { null }

        override fun isUseSuccess(): Boolean {
            val training = PlayerTagManager.isTraining(player)
            if ((!training && game.phase != GamePhase.RUNNING) || game.isPaused || activeTrials.containsKey(game)) {
                player.sendMiniMessage("<red><bold>[!] 지금은 재판을 열 수 없습니다.")
                return false
            }
            if (training) {
                val trainingTarget = findTrainingDefendantInSight()
                val defendant = trainingTarget ?: playerData
                selectedDefendant = defendant
                if (trainingTarget == null) {
                    player.sendMiniMessage("<gold><bold>[모의 재판]</bold> <gray>자신이 판사와 모의 피고인을 겸합니다.")
                } else {
                    player.sendMiniMessage(
                        "<gold><bold>[모의 재판]</bold> <white>${trainingTarget.player.name}<gray>님을 피고인으로 기소합니다."
                    )
                }
                return true
            }
            val target = playerData.shotLaserGetEntityData(PROSECUTION_RANGE, TargetType.Enemy, false) as? PlayerData
            if (target == null) {
                player.sendMiniMessage("<red><bold>[!] 10칸 안에서 바라보는 생존 플레이어가 없습니다.")
                return false
            }
            if (availableCrimes(target.uniqueId).isEmpty()) {
                player.sendMiniMessage("<red><bold>[!] 해당 플레이어에게 기소할 수 있는 기록된 죄목이 없습니다.")
                return false
            }
            selectedDefendant = target
            return true
        }

        override fun use(): Boolean {
            val defendant = selectedDefendant ?: return false
            selectedDefendant = null
            if (PlayerTagManager.isTraining(player)) seedTrainingEvidence(defendant)
            TrialSession(this@Referee, defendant).start()
            return true
        }
    }

    private class GreatScale : BasePassive() {
        override val name = "<bold>대천칭"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>모든 플레이어의 범죄 혐의와 관련 증거를 기록한다.",
            "<gray>  - 폭행: 한 번에 대상 최대 체력의 50%를 초과하는 피해를 입힌 경우.",
            "<gray>  - 상해: 한 번에 대상 최대 체력의 50% 이하 피해를 입힌 경우.",
            "<gray>  - 남용: 스킬을 10초 내에 3회 이상 사용한 경우.",
            "<gray>  - 도주: 교전 후 10초 안에 상대에게서 14칸 이상 달아난 경우.",
            "<gray>  - 살인: 플레이어를 살해한 경우.",
        )
    }

    private class OriginalSin : BasePassive() {
        override val name = "<bold>원죄"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>재판에서 유죄가 된 플레이어는 죄목에 따라 처벌받는다.",
            "<gray>  - 폭행: 최대 체력이 고정값 5 감소한다.",
            "<gray>  - 상해: 10초간 {keyword:Snare}, {keyword:Silence}, {keyword:Disarm} 상태가 된다.",
            "<gray>  - 남용: 현재 적용 중인 모든 스킬 쿨타임이 2배가 된다.",
            "<gray>  - 도주: 10초간 {keyword:Radiation} 및 {keyword:Snare} 상태가 된다.",
            "<gray>  - 살인: 살인의 죄를 물어 사형한다.",
            "<gray>  - 위증: 위력과 지속시간이 2배가 되며, 살인죄는 {keyword:Execution}으로 간주한다.",
        )
    }

    private fun recordCrime(accused: PlayerData, crime: Crime, detail: String) {
        if (accused.initGame !== game || accused.entityStatus.isDead) return
        val ledger = evidenceByAccused.getOrPut(accused.uniqueId) { mutableMapOf() }
        val records = ledger.getOrPut(crime) { ArrayDeque() }
        val evidence = Evidence((game.combatTick * 50L), detail)
        if (records.lastOrNull()?.let { evidence.timeMillis - it.timeMillis < 1_000L && it.detail == detail } == true) return
        records.addLast(evidence)
        while (records.size > REFEREE_MAX_EVIDENCE_PER_CRIME) records.removeFirst()
        player.sendMiniMessage(
            "<gold><bold>[대천칭]</bold> <white>${accused.player.name}<gray> — <red>${crime.displayName}<dark_gray> ($detail)"
        )
        SoundApi.playTo(player, Sound.BLOCK_NOTE_BLOCK_BELL, 0.35F, 1.7F, SoundCategory.MASTER)
    }

    private fun recordDamageInternal(context: DamageContext, finalDamage: Double) {
        val target = context.target as? PlayerData ?: return
        if (context.attacker == target || context.attacker.initGame !== game || target.initGame !== game) return
        val threshold = target.player.getPlayerMaxHealth() * 0.5
        val crime = if (finalDamage > threshold) Crime.ASSAULT else Crime.INJURY
        recordCrime(context.attacker, crime, "${target.player.name}에게 ${"%.1f".format(finalDamage)} 피해")
        val pair = CombatPair.of(context.attacker.uniqueId, target.uniqueId)
        combatTraces[pair] = CombatTrace(
            firstOrigin = playerFor(pair.first)?.location?.clone() ?: return,
            secondOrigin = playerFor(pair.second)?.location?.clone() ?: return,
            lastCombatMillis = (game.combatTick * 50L),
        )
    }

    private fun recordSkillUseInternal(accused: PlayerData) {
        if (accused.initGame !== game || game.isPaused || accused.entityStatus.isDead) return
        val now = (game.combatTick * 50L)
        val uses = skillUseTimes.getOrPut(accused.uniqueId) { ArrayDeque() }
        uses.addLast(now)
        while (uses.isNotEmpty() && now - uses.first() > REFEREE_COMBAT_MEMORY_MILLIS) uses.removeFirst()
        if (uses.size >= 3) {
            recordCrime(accused, Crime.ABUSE, "10초 안에 스킬 ${uses.size}회 사용")
            uses.clear()
        }
    }

    private fun recordMurderInternal(killerId: UUID?, victim: PlayerData) {
        val killer = killerId?.let(::playerDataFor) ?: return
        if (killer == victim || killer.initGame !== game) return
        recordCrime(killer, Crime.MURDER, "${victim.player.name} 처치")
    }

    private fun detectEscapes(now: Long) {
        val iterator = combatTraces.iterator()
        while (iterator.hasNext()) {
            val (pair, trace) = iterator.next()
            if (now - trace.lastCombatMillis > REFEREE_COMBAT_MEMORY_MILLIS) {
                iterator.remove()
                continue
            }
            if (trace.escapeRecorded) continue
            val first = playerDataFor(pair.first) ?: continue
            val second = playerDataFor(pair.second) ?: continue
            if (first.player.world != second.player.world) continue
            if (first.player.location.distance(second.player.location) < REFEREE_ESCAPE_DISTANCE) continue
            val firstMoved = sameWorldDistance(first.player.location, trace.firstOrigin)
            val secondMoved = sameWorldDistance(second.player.location, trace.secondOrigin)
            val fugitive = if (firstMoved >= secondMoved) first else second
            recordCrime(fugitive, Crime.ESCAPE, "교전 상대에게서 ${"%.1f".format(first.player.location.distance(second.player.location))}칸 이탈")
            trace.escapeRecorded = true
        }
    }

    private fun availableCrimes(accusedId: UUID): List<Crime> = evidenceByAccused[accusedId]
        .orEmpty().filterValues { it.isNotEmpty() }.keys.sortedBy(Crime::order)

    private fun evidenceSummary(accusedId: UUID, crime: Crime): String =
        evidenceByAccused[accusedId]?.get(crime)?.lastOrNull()?.detail ?: "세부 기록 없음"

    private fun consumeEvidence(accusedId: UUID, crime: Crime) {
        evidenceByAccused[accusedId]?.get(crime)?.takeIf { it.isNotEmpty() }?.removeFirst()
    }

    private fun seedTrainingEvidence(accused: PlayerData) {
        val ledger = evidenceByAccused.getOrPut(accused.uniqueId) { mutableMapOf() }
        Crime.entries.forEach { crime ->
            val records = ledger.getOrPut(crime) { ArrayDeque() }
            if (records.isEmpty()) {
                records.addLast(Evidence((game.combatTick * 50L), "훈련용 ${crime.displayName} 모의 증거"))
            }
        }
    }

    private fun findTrainingDefendantInSight(): PlayerData? {
        val start = player.eyeLocation
        val direction = start.direction
        val targetAndDistance = player.world.players.asSequence()
            .filter { candidate ->
                candidate.uniqueId != player.uniqueId &&
                    candidate.isOnline && PlayerTagManager.isTraining(candidate)
            }
            .mapNotNull { candidate ->
                val target = findGameForPlayer(candidate)?.playerDatas
                    ?.filterIsInstance<PlayerData>()
                    ?.find { it.player.uniqueId == candidate.uniqueId }
                    ?: return@mapNotNull null
                if (target.entityStatus.isDead || !target.entityStatus.isSkillTargeting || target.hasStatus<Stealth>()) {
                    return@mapNotNull null
                }
                HitboxUtil.rayIntersectionDistance(
                    candidate.boundingBox,
                    start.toVector(),
                    direction,
                    PROSECUTION_RANGE,
                    expansion = 1.0,
                )?.let { distance -> target to distance }
            }
            .minByOrNull { it.second }
            ?: return null

        val blockHit = player.world.rayTraceBlocks(start, direction, PROSECUTION_RANGE)?.hitPosition
        if (blockHit != null && blockHit.distanceSquared(start.toVector()) <= targetAndDistance.second * targetAndDistance.second) {
            return null
        }
        return targetAndDistance.first
    }

    private fun playerDataFor(id: UUID): PlayerData? = game.playerDatas.filterIsInstance<PlayerData>()
        .find { it.uniqueId == id }

    private fun playerFor(id: UUID): Player? = playerDataFor(id)?.player

    private data class Evidence(val timeMillis: Long, val detail: String)
    private data class CombatPair(val first: UUID, val second: UUID) {
        companion object {
            fun of(a: UUID, b: UUID): CombatPair =
                if (a.toString() <= b.toString()) CombatPair(a, b) else CombatPair(b, a)
        }
    }
    private data class CombatTrace(
        val firstOrigin: Location,
        val secondOrigin: Location,
        val lastCombatMillis: Long,
        var escapeRecorded: Boolean = false,
    )

    private enum class TrialPhase { ACCUSATION, DEFENSE, VERDICT, CLOSED }
    private data class CourtSnapshot(
        val controls: ControlLease,
        val location: Location,
        val glowing: Boolean,
        val courtStatuses: List<StatusAbnormality>,
    )

    private class TrialSession(private val owner: Referee, private val defendant: PlayerData) {
        private val miniMessage = MiniMessage.miniMessage()
        private val game = owner.game
        private val judge = owner.playerData
        private val isTrainingTrial = PlayerTagManager.isTraining(judge.player)
        private val isSoloTrial = judge == defendant
        private val participants = (game.playerDatas.filterIsInstance<PlayerData>() + defendant)
            .distinctBy(PlayerData::uniqueId)
            .filter { it.player.isOnline && !it.entityStatus.isDead }
        private val sessionGames = mutableListOf<Game>().apply {
            participants.forEach { participant ->
                if (none { it === participant.initGame }) add(participant.initGame)
            }
        }
        private val priorPauseStates = IdentityHashMap<Game, Boolean>()
        private val participantPlayers = participants.map(PlayerData::player)
        private val snapshots = mutableMapOf<UUID, CourtSnapshot>()
        private val bar = BossBar.bossBar(
            miniMessage.deserialize("<gold><bold>재판 개정"), 1.0F,
            BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS,
        )
        private var phase = TrialPhase.ACCUSATION
        private var remainingTicks = REFEREE_ACCUSATION_SECONDS * 20
        private var elapsedTicks = 0
        private var selectedCrime: Crime? = null
        private var defenseOptions: List<DefenseOption> = emptyList()
        private var midiPlayer: CourtroomMidiPlayer? = null
        private var verdictGuilty = false
        private var verdictPerjury = false
        private var task: org.bukkit.scheduler.BukkitTask? = null
        private var borderExpansion: MapTransferBorderManager.Expansion? = null

        fun start() {
            if (participants.none { it == judge } || participants.none { it == defendant }) return
            sessionGames.forEach { participantGame ->
                priorPauseStates[participantGame] = participantGame.isPaused
                participantGame.isPaused = true
                activeTrials[participantGame] = this
            }
            participants.forEach { activeTrialPlayers[it.uniqueId] = this }
            borderExpansion = MapTransferBorderManager.expandToMaximum(judge.player.world)
            moveToCourtroom()
            pauseCooldowns()
            broadcast("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            broadcast("<gold><bold>⚖ 대천칭 재판소 ⚖")
            broadcast("<gray>재판장에 정숙하십시오. 모든 전투 행위와 시간이 정지됩니다.")
            broadcast("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            showTitle("<gold><bold>재판 개정", "<gray>${judge.player.name} 판사 · ${defendant.player.name} 피고인")
            participants.forEach { it.player.showBossBar(bar) }
            courtroomSound(Sound.BLOCK_END_PORTAL_SPAWN, 0.55F, 0.72F)
            courtroomSound(Sound.BLOCK_ANVIL_LAND, 0.8F, 0.7F)
            courtroomSound(Sound.BLOCK_BEACON_ACTIVATE, 0.48F, 0.68F)
            renderOpeningBurst()
            judge.player.sendMiniMessage("<yellow><bold>[기소 단계]</bold> <gray>아래 죄목 중 하나를 30초 안에 정확히 입력하세요.")
            owner.availableCrimes(defendant.uniqueId).forEach { crime ->
                judge.player.sendMiniMessage("<red><bold>${crime.displayName}</bold> <dark_gray>— ${owner.evidenceSummary(defendant.uniqueId, crime)}")
            }
            defendant.player.sendMiniMessage("<gray>판사가 죄목을 지명할 때까지 기다리십시오.")
            task = object : BukkitRunnable(owner.abilityScope, TickPolicy.SESSION) { override fun run() = tick() }
                .runTaskTimer(ClassWarPlugin.instance, 1L, 1L).also(judge::trackTask)
        }

        fun handleChat(sender: Player, rawInput: String) = AbilityExecution.with(owner.abilityScope) {
            handleChatBound(sender, rawInput)
        }

        private fun handleChatBound(sender: Player, rawInput: String) {
            val input = rawInput.trim()
            if (input.isEmpty() || phase == TrialPhase.CLOSED) return
            courtroomChat(sender, input)
            when (phase) {
                TrialPhase.ACCUSATION -> if (sender.uniqueId == judge.uniqueId) {
                    val crime = Crime.fromInput(input)
                    if (crime == null || crime !in owner.availableCrimes(defendant.uniqueId)) {
                        judge.player.sendMiniMessage("<red><bold>[각하]</bold> <gray>기록된 죄목의 이름을 정확히 입력해야 합니다.")
                        courtroomSound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.6F)
                    } else beginDefense(crime)
                }
                TrialPhase.DEFENSE -> if (sender.uniqueId == defendant.uniqueId) {
                    val selected = defenseOptions.find { it.text == input }
                    if (selected == null) {
                        defendant.player.sendMiniMessage("<red><bold>[진술 불일치]</bold> <gray>제시된 문장을 띄어쓰기까지 정확히 입력해야 합니다.")
                        courtroomSound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.55F, 0.65F)
                    } else when (selected.kind) {
                        DefenseKind.TRUTH -> beginVerdict(false, false, "진실한 변론이 증거와 일치했습니다.")
                        DefenseKind.ADMISSION -> beginVerdict(true, false, "피고인이 혐의를 인정했습니다.")
                        DefenseKind.LIE -> beginVerdict(true, true, "진술이 증거와 모순되어 위증이 성립했습니다.")
                    }
                }
                else -> Unit
            }
        }

        fun abort() {
            if (phase == TrialPhase.CLOSED) return
            broadcast("<red><bold>[재판 무효]</bold> <gray>재판 당사자가 유효하지 않아 모든 절차를 취소합니다.")
            close(false)
        }

        private fun tick() {
            if (phase == TrialPhase.CLOSED) return
            if ((!isTrainingTrial && game.phase != GamePhase.RUNNING) || !judge.player.isOnline || !defendant.player.isOnline ||
                judge.entityStatus.isDead || defendant.entityStatus.isDead
            ) {
                abort(); return
            }
            if (elapsedTicks == 0) pauseCooldowns()
            elapsedTicks++
            midiPlayer?.tick(participantPlayers)
            if (elapsedTicks % 4 == 0) renderCourtroom()
            when (phase) {
                TrialPhase.ACCUSATION -> {
                    updateBar("<gold><bold>기소할 죄목을 지명하십시오", REFEREE_ACCUSATION_SECONDS)
                    remainingTicks--; countdownCue()
                    if (remainingTicks <= 0) {
                        broadcast("<red><bold>[기소 기각]</bold> <gray>판사가 제한 시간 안에 죄목을 지명하지 못했습니다.")
                        beginVerdict(false, false, "기소 제한 시간 30초가 만료되었습니다.")
                    }
                }
                TrialPhase.DEFENSE -> {
                    updateBar("<aqua><bold>피고인의 변론", REFEREE_DEFENSE_SECONDS)
                    remainingTicks--; countdownCue()
                    if (remainingTicks <= 0) beginVerdict(true, false, "피고인이 변론 제한 시간 ${REFEREE_DEFENSE_SECONDS}초 동안 답하지 않았습니다.")
                }
                TrialPhase.VERDICT -> {
                    remainingTicks--
                    bar.progress((remainingTicks / 70.0F).coerceIn(0.0F, 1.0F))
                    if (remainingTicks == 52) {
                        courtroomSound(Sound.BLOCK_TRIAL_SPAWNER_DETECT_PLAYER, 0.65F, 0.85F)
                        broadcast("<gray><italic>배심 기록과 진술을 대조하는 중...")
                    }
                    if (remainingTicks == 30) announceVerdict()
                    if (remainingTicks <= 0) close(verdictGuilty)
                }
                TrialPhase.CLOSED -> Unit
            }
        }

        private fun beginDefense(crime: Crime) {
            if (phase != TrialPhase.ACCUSATION) return
            selectedCrime = crime
            val evidence = owner.evidenceSummary(defendant.uniqueId, crime)
            owner.consumeEvidence(defendant.uniqueId, crime)
            phase = TrialPhase.DEFENSE
            remainingTicks = REFEREE_DEFENSE_SECONDS * 20
            bar.color(BossBar.Color.BLUE)
            midiPlayer = CourtroomMidiPlayer.create()
            courtroomSound(Sound.BLOCK_ANVIL_LAND, 0.9F, 0.82F)
            courtroomSound(Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.65F, 0.72F)
            courtroomSound(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.42F, 1.35F)
            courtroomSound(Sound.ITEM_BOOK_PAGE_TURN, 0.7F, 0.78F)
            renderAccusationBurst()
            if (isSoloTrial) {
                judge.player.teleport(Location(judge.player.world, 625.5, -30.0, -508.5, 0.0F, 0.0F))
            }
            showTitle("<red><bold>${crime.displayName}죄", "<gray>피고인은 ${REFEREE_DEFENSE_SECONDS}초 안에 변론하십시오")
            broadcast("<gold><bold>[판사]</bold> <white>${defendant.player.name}<gray> 피고인을 <red><bold>${crime.displayName}죄</bold><gray>로 기소한다.")
            broadcast("<dark_gray>[증거 기록] $evidence")
            defenseOptions = DefenseCatalog.options(crime)
            defendant.player.sendMessage(Component.empty())
            defendant.player.sendMiniMessage("<aqua><bold>✦ 변론 선택지 — 정확히 한 문장만 진실입니다 ✦")
            defenseOptions.forEachIndexed { index, option ->
                defendant.player.sendMessage(Component.text("${index + 1}. ", NamedTextColor.DARK_AQUA)
                    .append(Component.text(option.text, NamedTextColor.WHITE)))
            }
            defendant.player.sendMiniMessage("<yellow>선택한 문장 전체를 ${REFEREE_DEFENSE_SECONDS}초 안에 채팅으로 똑같이 입력하세요.")
            participants.filter { it != defendant }.forEach {
                it.player.sendMiniMessage("<gray>피고인에게 ${REFEREE_DEFENSE_OPTION_COUNT}개의 비공개 변론 선택지가 전달되었습니다.")
            }
        }

        private fun beginVerdict(guilty: Boolean, perjury: Boolean, reason: String) {
            if (phase == TrialPhase.VERDICT || phase == TrialPhase.CLOSED) return
            verdictGuilty = guilty; verdictPerjury = perjury
            phase = TrialPhase.VERDICT; remainingTicks = 70
            bar.color(if (guilty) BossBar.Color.RED else BossBar.Color.GREEN)
            bar.name(miniMessage.deserialize("<gold><bold>판결문 작성 중"))
            broadcast("<dark_gray>[서기] $reason")
            courtroomSound(Sound.ITEM_BOOK_PAGE_TURN, 0.8F, 0.7F)
            courtroomSound(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 0.78F)
            renderDeliberationBurst()
        }

        private fun announceVerdict() {
            val crime = selectedCrime
            courtroomSound(Sound.BLOCK_ANVIL_LAND, 1.0F, 0.62F)
            courtroomSound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0F, if (verdictGuilty) 0.65F else 1.4F)
            if (verdictGuilty) {
                courtroomSound(Sound.ENTITY_WITHER_SPAWN, 0.32F, 0.62F)
                courtroomSound(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.55F, 0.58F)
            } else {
                courtroomSound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.25F)
                courtroomSound(Sound.BLOCK_BEACON_ACTIVATE, 0.65F, 1.42F)
            }
            val center = courtCenter()
            ParticleApi.spawn(center, if (verdictGuilty) Particle.SOUL_FIRE_FLAME else Particle.TOTEM_OF_UNDYING,
                ParticleOptions(90, 4.0, 1.7, 4.0, 0.09))
            renderVerdictBurst(verdictGuilty)
            if (verdictGuilty) {
                val suffix = if (verdictPerjury) " <dark_red>및 위증" else ""
                broadcast("<red><bold>[유 죄]</bold> <white>${defendant.player.name}<gray> — ${crime?.displayName ?: "미상"}$suffix")
                showTitle("<dark_red><bold>유 죄", if (verdictPerjury) "<red>위증 가중 처벌" else "<gray>${crime?.displayName ?: ""}죄 성립")
            } else {
                broadcast("<green><bold>[무 죄]</bold> <white>${defendant.player.name}<gray> 피고인을 석방합니다.")
                showTitle("<green><bold>무 죄", "<gray>피고인을 즉시 석방합니다")
            }
        }

        private fun close(applyPunishment: Boolean) {
            if (phase == TrialPhase.CLOSED) return
            phase = TrialPhase.CLOSED
            task?.cancel(); task = null
            participants.forEach { data ->
                data.player.hideBossBar(bar)
                val snapshot = snapshots[data.uniqueId] ?: return@forEach
                snapshot.controls.close()
                snapshot.courtStatuses.forEach(StatusAbnormality::remove)
                data.player.isGlowing = snapshot.glowing
                data.returnFromAbility(snapshot.location)
                data.gameClasses.flatMap { it.skills }.forEach { CooldownManager.resumeCooldown(data.player, it) }
            }
            borderExpansion?.restore()
            borderExpansion = null
            sessionGames.forEach { participantGame ->
                activeTrials.remove(participantGame, this)
                participantGame.isPaused = priorPauseStates[participantGame] ?: false
            }
            participants.forEach { activeTrialPlayers.remove(it.uniqueId, this) }
            broadcast("<dark_gray>━━━━━━━━━━━━ <gray>재판 종료 <dark_gray>━━━━━━━━━━━━")
            if (applyPunishment) selectedCrime?.let { owner.applyPunishment(defendant, it, verdictPerjury) }
        }

        private fun moveToCourtroom() {
            val world = judge.player.world
            val audience = participants.filter { it != judge && it != defendant }
            participants.forEach { data ->
                val courtStatuses = listOf(
                    CourtOrderStatus(),
                    Silence(),
                    Disarm(),
                    Snare(),
                    Invincibility(),
                ).onEach { status ->
                    data.addStatus(status, judge)
                    status.applyStatus(powerSet = 1)
                }
                val status = data.entityStatus
                val controls = ControlLease(owner.abilityScope, status)
                Control.entries.forEach { controls.allow(it, false) }
                snapshots[data.uniqueId] = CourtSnapshot(controls, data.player.location.clone(),
                    data.player.isGlowing, courtStatuses)
                data.player.fireTicks = 0
            }
            judge.player.teleport(Location(world, 625.5, -26.0, -483.5, 180.0F, 8.0F))
            if (!isSoloTrial) {
                defendant.player.teleport(Location(world, 625.5, -30.0, -508.5, 0.0F, 0.0F))
            }
            audience.forEachIndexed { index, data ->
                val row = index / 6; val column = index % 6
                data.player.teleport(Location(world, 604.5 + row * 2.1, -28.0, -502.5 + column * 2.0, -90.0F, 0.0F))
            }
        }

        private fun pauseCooldowns() = participants.forEach { data ->
            data.gameClasses.flatMap { it.skills }.forEach { CooldownManager.pauseCooldown(data.player, it) }
        }

        private fun updateBar(label: String, totalSeconds: Int) {
            val seconds = ((remainingTicks + 19) / 20).coerceAtLeast(0)
            bar.name(miniMessage.deserialize("$label <white>${seconds}초"))
            bar.progress((remainingTicks.toFloat() / (totalSeconds * 20)).coerceIn(0.0F, 1.0F))
        }

        private fun countdownCue() {
            if (remainingTicks in 1..100 && remainingTicks % 20 == 0)
                courtroomSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5F, 1.45F)
        }

        private fun renderCourtroom() {
            val center = courtCenter(); val world = center.world; val angleOffset = elapsedTicks * 0.045
            repeat(12) { index ->
                val angle = angleOffset + index * (PI * 2.0 / 12.0)
                val radius = if (index % 2 == 0) 9.0 else 6.7
                ParticleApi.spawn(
                    Location(world, center.x + cos(angle) * radius, center.y + 0.35 + (index % 3) * 0.35,
                        center.z + sin(angle) * radius),
                    if (index % 3 == 0) Particle.END_ROD else Particle.ENCHANT,
                )
            }
            if (elapsedTicks % 12 == 0) {
                ParticleApi.spawn(center, Particle.DUST, Particle.DustOptions(Color.fromRGB(255, 190, 35), 1.1F),
                    ParticleOptions(18, 5.5, 0.4, 8.0))
                ParticleApi.spawn(center, Particle.TRIAL_SPAWNER_DETECTION, ParticleOptions(8, 4.0, 1.2, 6.0, 0.02))
                ParticleApi.spawn(center.clone().add(0.0, 1.2, 0.0), Particle.REVERSE_PORTAL,
                    ParticleOptions(10, 2.8, 0.7, 4.2, 0.025))
            }
        }

        private fun renderOpeningBurst() {
            val center = courtCenter()
            val world = center.world
            ParticleApi.spawn(center.clone().add(0.0, 1.1, 0.0), Particle.FLASH,
                Color.fromRGB(255, 220, 120))
            ParticleApi.spawn(center.clone().add(0.0, 1.0, 0.0), Particle.END_ROD,
                ParticleOptions(48, 5.5, 1.2, 8.0, 0.045))
            ParticleApi.spawn(center.clone().add(0.0, 0.6, 0.0), Particle.ENCHANT,
                ParticleOptions(90, 7.0, 0.7, 10.0, 0.18))
            spawnDustRing(center.clone().add(0.0, 0.18, 0.0), 5.0, 48, Color.fromRGB(255, 190, 35), 1.35F)
            spawnDustRing(center.clone().add(0.0, 0.24, 0.0), 9.0, 72, Color.fromRGB(110, 205, 255), 1.0F)
        }

        private fun renderAccusationBurst() {
            val defendantCenter = defendant.player.location.clone().add(0.0, 1.0, 0.0)
            val world = defendantCenter.world
            ParticleApi.spawn(defendantCenter, Particle.FLASH, Color.fromRGB(225, 45, 45))
            ParticleApi.spawn(defendantCenter, Particle.WITCH, ParticleOptions(55, 0.75, 1.2, 0.75, 0.09))
            ParticleApi.spawn(defendantCenter, Particle.SOUL_FIRE_FLAME, ParticleOptions(34, 0.65, 1.05, 0.65, 0.055))
            spawnParticleLine(judge.player.eyeLocation, defendant.player.eyeLocation, Particle.END_ROD, 0.38)
            spawnDustRing(defendant.player.location.clone().add(0.0, 0.15, 0.0), 2.1, 36,
                Color.fromRGB(215, 45, 45), 1.25F)
        }

        private fun renderDeliberationBurst() {
            val center = courtCenter().add(0.0, 1.0, 0.0)
            ParticleApi.spawn(center, Particle.REVERSE_PORTAL, ParticleOptions(55, 4.0, 1.0, 6.0, 0.085))
            ParticleApi.spawn(center, Particle.ELECTRIC_SPARK, ParticleOptions(26, 3.2, 0.8, 5.0, 0.055))
            spawnDustRing(center.clone().add(0.0, -0.82, 0.0), 6.8, 54,
                Color.fromRGB(175, 120, 255), 1.0F)
        }

        private fun renderVerdictBurst(guilty: Boolean) {
            val center = courtCenter()
            val color = if (guilty) Color.fromRGB(210, 25, 25) else Color.fromRGB(60, 255, 135)
            repeat(3) { ring ->
                spawnDustRing(center.clone().add(0.0, 0.2 + ring * 0.42, 0.0), 3.4 + ring * 2.5,
                    44 + ring * 16, color, 1.25F - ring * 0.12F)
            }
            ParticleApi.spawn(center.clone().add(0.0, 1.4, 0.0), Particle.FLASH, color,
                ParticleOptions(count = 2))
            ParticleApi.spawn(center.clone().add(0.0, 1.1, 0.0),
                if (guilty) Particle.SOUL else Particle.END_ROD,
                ParticleOptions(75, 4.8, 1.8, 7.0, 0.11))
        }

        private fun spawnDustRing(center: Location, radius: Double, points: Int, color: Color, size: Float) {
            val dust = Particle.DustOptions(color, size)
            repeat(points) { index ->
                val angle = PI * 2.0 * index / points
                ParticleApi.spawn(
                    Location(center.world, center.x + cos(angle) * radius, center.y,
                        center.z + sin(angle) * radius),
                    Particle.DUST,
                    dust,
                )
            }
        }

        private fun spawnParticleLine(from: Location, to: Location, particle: Particle, spacing: Double) {
            if (from.world != to.world) return
            val delta = to.toVector().subtract(from.toVector())
            val length = delta.length()
            if (length <= 0.0) return
            val direction = delta.normalize()
            var distance = 0.0
            while (distance <= length) {
                val point = from.clone().add(direction.clone().multiply(distance))
                ParticleApi.spawn(point, particle)
                distance += spacing
            }
        }

        private fun courtroomChat(sender: Player, message: String) {
            val role = when (sender.uniqueId) {
                judge.uniqueId.takeIf { isSoloTrial && phase != TrialPhase.ACCUSATION } -> "[피고인] " to NamedTextColor.AQUA
                judge.uniqueId -> "[판사] " to NamedTextColor.GOLD
                defendant.uniqueId -> "[피고인] " to NamedTextColor.AQUA
                else -> "[방청객] " to NamedTextColor.GRAY
            }
            val rendered = Component.text(role.first, role.second)
                .append(Component.text(sender.name, NamedTextColor.WHITE))
                .append(Component.text(" : ", NamedTextColor.DARK_GRAY))
                .append(Component.text(message, NamedTextColor.WHITE))
            participants.filter { it.player.isOnline }.forEach { it.player.sendMessage(rendered) }
        }

        private fun broadcast(message: String) {
            val component = miniMessage.deserialize(message)
            participants.filter { it.player.isOnline }.forEach { it.player.sendMessage(component) }
        }

        private fun showTitle(title: String, subtitle: String) {
            val rendered = Title.title(miniMessage.deserialize(title), miniMessage.deserialize(subtitle))
            participants.filter { it.player.isOnline }.forEach { it.player.showTitle(rendered) }
        }

        private fun courtroomSound(sound: Sound, volume: Float, pitch: Float) = participants
            .filter { it.player.isOnline }
            .forEach { SoundApi.playTo(it.player, sound, volume, pitch, SoundCategory.MASTER) }

        private fun courtCenter(): Location = Location(judge.player.world, 625.5, -29.0, -496.0)

    }

    private fun applyPunishment(target: PlayerData, crime: Crime, perjury: Boolean) {
        if (!target.player.isOnline || target.entityStatus.isDead) return
        val multiplier = if (perjury) 2 else 1
        target.player.sendMiniMessage("<dark_red><bold>[형 집행]</bold> <gray>${crime.displayName}죄${if (perjury) " 및 위증" else ""}의 형을 집행합니다.")
        ParticleApi.spawn(target.player.location.add(0.0, 1.0, 0.0), Particle.SOUL_FIRE_FLAME,
            ParticleOptions(70, 0.8, 1.1, 0.8, 0.08))
        SoundApi.playTo(target.player, Sound.BLOCK_ANVIL_LAND, 1.0F, 0.55F, SoundCategory.MASTER)
        when (crime) {
            Crime.ASSAULT -> {
                target.attributeEffects.changeBase(Attribute.MAX_HEALTH) { (it - 5.0 * multiplier).coerceAtLeast(1.0) }
            }
            Crime.INJURY -> {
                val duration = 10 * multiplier
                target.addStatus(Snare(), playerData).applyStatus(duration = duration, powerSet = 1)
                target.addStatus(Silence(), playerData).applyStatus(duration = duration, powerSet = 1)
                target.addStatus(Disarm(), playerData).applyStatus(duration = duration, powerSet = 1)
            }
            Crime.ABUSE -> {
                val cooldownMultiplier = if (perjury) 4.0 else 2.0
                target.gameClasses.flatMap { it.skills }
                    .forEach { CooldownManager.multiplyCooldown(target.player, it, cooldownMultiplier) }
            }
            Crime.ESCAPE -> {
                val duration = 10 * multiplier
                target.addStatus(Snare(), playerData).applyStatus(duration = duration, powerSet = 1)
                target.addStatus(Radiation(), playerData).applyStatus(duration = duration, powerSet = 1)
            }
            Crime.MURDER -> {
                target.player.sendMiniMessage(if (perjury) "<dark_red><bold>위증이 확인되어 즉시 처형합니다." else "<red><bold>사형을 집행합니다.")
                if (PlayerTagManager.isTraining(target.player)) {
                    target.player.sendMiniMessage("<yellow><bold>[훈련]</bold> <gray>처형 판정과 연출만 적용하고 실제 사망은 생략합니다.")
                } else {
                    target.player.health = 0.0
                }
            }
        }
    }

    private class CourtOrderStatus : StatusAbnormality() {
        override val name = "<gold><bold>재판 명령</bold><gray>"
        override val description = listOf("<gray>침묵 · 무장해제 · 무적 상태이며 게임 시간이 정지한다.")
        override val canRemove = true
        override var power = 1
        override var maxPower: Int? = 1
        override val showPower = false
        override val showMaxPower = false
    }

    companion object {
        private const val PROSECUTION_RANGE = 10.0
        private val activeReferees = mutableMapOf<UUID, Referee>()
        private val activeTrials = IdentityHashMap<Game, TrialSession>()
        private val activeTrialPlayers = mutableMapOf<UUID, TrialSession>()

        fun recordDamage(context: DamageContext, finalDamage: Double) {
            if (finalDamage <= 0.0) return
            val target = context.target as? PlayerData ?: return
            activeReferees.values.toList().filter { it.isActiveFor(target.initGame) }
                .forEach { it.recordDamageInternal(context, finalDamage) }
        }

        fun recordSkillUse(playerData: PlayerData) {
            activeReferees.values.toList().filter { it.isActiveFor(playerData.initGame) }
                .forEach { it.recordSkillUseInternal(playerData) }
        }

        fun recordMurder(game: Game, killerId: UUID?, victim: PlayerData) {
            activeReferees.values.toList().filter { it.isActiveFor(game) }
                .forEach { it.recordMurderInternal(killerId, victim) }
        }

        fun hasActiveTrial(playerId: UUID): Boolean = activeTrialPlayers.containsKey(playerId)
        fun handleChatInput(player: Player, input: String) { activeTrialPlayers[player.uniqueId]?.handleChat(player, input) }

        fun clearSessions(participantIds: Collection<UUID>) {
            activeTrialPlayers.entries.firstOrNull { it.key in participantIds }?.value?.abort()
            participantIds.forEach(activeReferees::remove)
        }

        private fun Referee.isActiveFor(targetGame: Game): Boolean =
            game === targetGame && game.phase == GamePhase.RUNNING &&
                !playerData.entityStatus.isDead && !abilityScope.isClosed

        private fun sameWorldDistance(first: Location, second: Location): Double =
            if (first.world != second.world) Double.MAX_VALUE else first.distance(second)
    }
}
