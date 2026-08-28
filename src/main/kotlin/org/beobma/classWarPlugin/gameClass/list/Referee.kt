package org.beobma.classWarPlugin.gameClass.list

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
import org.bukkit.scheduler.BukkitRunnable
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
private const val REFEREE_DEFENSE_OPTION_COUNT = 10
private const val REFEREE_DEFENSE_LIE_COUNT = REFEREE_DEFENSE_OPTION_COUNT - 2
private const val REFEREE_COMBAT_MEMORY_MILLIS = 10_000L
private const val REFEREE_ESCAPE_DISTANCE = 14.0
private const val REFEREE_MAX_EVIDENCE_PER_CRIME = 12

class Referee : GameClass(), GameStatusHandler {
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
        if (playerData.entityStatus.isDead || this !in playerData.gameClasses) {
            activeReferees.remove(player.uniqueId, this)
            return
        }
        val now = System.currentTimeMillis()
        skillUseTimes.values.forEach { times ->
            while (times.isNotEmpty() && now - times.first() > REFEREE_COMBAT_MEMORY_MILLIS) times.removeFirst()
        }
        detectEscapes(now)
    }

    private inner class Prosecution : Skill() {
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
        private var selectedDefendant: PlayerData? = null

        override fun isUseSuccess(): Boolean {
            val training = PlayerTagManager.hasTag(player, "isTraining")
            if ((!training && game.phase != GamePhase.RUNNING) || game.isPaused || activeTrials.containsKey(game)) {
                player.sendMiniMessage("<red><bold>[!] 지금은 재판을 열 수 없습니다.")
                return false
            }
            if (training) {
                val trainingTarget = findTrainingDefendantInSight()
                val defendant = trainingTarget ?: playerData
                seedTrainingEvidence(defendant)
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

        override fun use() {
            val defendant = selectedDefendant ?: return
            selectedDefendant = null
            TrialSession(this@Referee, defendant).start()
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
        val evidence = Evidence(System.currentTimeMillis(), detail)
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
            lastCombatMillis = System.currentTimeMillis(),
        )
    }

    private fun recordSkillUseInternal(accused: PlayerData) {
        if (accused.initGame !== game || game.isPaused || accused.entityStatus.isDead) return
        val now = System.currentTimeMillis()
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
                records.addLast(Evidence(System.currentTimeMillis(), "훈련용 ${crime.displayName} 모의 증거"))
            }
        }
    }

    private fun findTrainingDefendantInSight(): PlayerData? {
        val start = player.eyeLocation
        val direction = start.direction
        val targetAndDistance = player.world.players.asSequence()
            .filter { candidate ->
                candidate.uniqueId != player.uniqueId &&
                    candidate.isOnline && PlayerTagManager.hasTag(candidate, "isTraining")
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

    private enum class Crime(val displayName: String, val order: Int) {
        ASSAULT("폭행", 0), INJURY("상해", 1), ABUSE("남용", 2), ESCAPE("도주", 3), MURDER("살인", 4);
        companion object {
            fun fromInput(input: String): Crime? = entries.find { it.displayName == input.trim() }
        }
    }

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
    private enum class DefenseKind { TRUTH, LIE, ADMISSION }
    private data class DefenseOption(val text: String, val kind: DefenseKind)
    private data class CourtSnapshot(
        val location: Location,
        val canAttack: Boolean,
        val canSkillUse: Boolean,
        val canMove: Boolean,
        val isAttackable: Boolean,
        val isSkillTargeting: Boolean,
        val glowing: Boolean,
        val courtStatuses: List<StatusAbnormality>,
    )

    private class TrialSession(private val owner: Referee, private val defendant: PlayerData) {
        private val miniMessage = MiniMessage.miniMessage()
        private val game = owner.game
        private val judge = owner.playerData
        private val isTrainingTrial = PlayerTagManager.hasTag(judge.player, "isTraining")
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
            task = object : BukkitRunnable() { override fun run() = tick() }
                .runTaskTimer(ClassWarPlugin.instance, 1L, 1L).also(judge::trackTask)
        }

        fun handleChat(sender: Player, rawInput: String) {
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
            defenseOptions = buildDefenseOptions(crime)
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
                snapshot.courtStatuses.forEach(StatusAbnormality::remove)
                data.entityStatus.canAttack = snapshot.canAttack
                data.entityStatus.canSkillUse = snapshot.canSkillUse
                data.entityStatus.canMove = snapshot.canMove
                data.entityStatus.isAttackable = snapshot.isAttackable
                data.entityStatus.isSkillTargeting = snapshot.isSkillTargeting
                data.player.isGlowing = snapshot.glowing
                if (data.player.isOnline) data.player.teleport(snapshot.location)
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
                snapshots[data.uniqueId] = CourtSnapshot(data.player.location.clone(), status.canAttack,
                    status.canSkillUse, status.canMove, status.isAttackable, status.isSkillTargeting,
                    data.player.isGlowing, courtStatuses)
                status.canAttack = false; status.canSkillUse = false; status.canMove = false
                status.isAttackable = false; status.isSkillTargeting = false
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

        private fun buildDefenseOptions(crime: Crime): List<DefenseOption> {
            val truth = when (crime) {
                Crime.ASSAULT -> listOf(
                    "나는 공격이 이어지면 피해가 커질 수 있다고 판단해 바로 거리를 벌렸다.",
                    "나는 상대의 움직임을 저지한 뒤 추가적인 공격은 하지 않았다.",
                    "나는 교전이 시작된 직후 상황을 끝내기 위해 먼저 공격을 멈췄다.",
                    "나는 한 차례 충돌 이후 상대를 계속 추격하지 않았다.",
                    "나는 상대가 물러난 뒤에는 더 이상 공격하지 않았다.",
                    "나는 공격 이후 전투를 이어가기보다 안전한 거리를 확보했다.",
                    "나는 상대에게 피해가 발생한 것을 확인한 뒤 공격을 중단했다.",
                    "나는 상대를 쓰러뜨리는 것보다 교전을 종료하는 것을 우선했다.",
                    "나는 필요한 만큼만 대응한 뒤 무기를 거두었다.",
                    "나는 상대가 더 이상 접근하지 않자 공격을 계속하지 않았다.",
                    "나는 충돌 이후 상대의 이동을 방해하지 않고 물러났다.",
                    "나는 첫 공격 이후 상황이 정리되었다고 판단해 추가 행동을 하지 않았다.",
                    "나는 상대가 후퇴하는 것을 보고 공격을 멈췄다.",
                    "나는 교전을 길게 이어갈 의도가 없어 바로 전투에서 벗어났다.",
                    "나는 상대에게 피해를 준 뒤에도 공격 기회가 있었지만 이용하지 않았다.",
                    "나는 위협이 사라진 뒤에는 상대에게 다시 접근하지 않았다.",
                    "나는 공격 직후 상황을 살핀 뒤 더 이상의 충돌을 피했다.",
                    "나는 상대와의 거리를 유지하면서 전투를 끝내려고 했다.",
                    "나는 상대를 계속 몰아붙이지 않고 공격을 한 차례에서 끝냈다.",
                    "나는 상대의 반응을 확인한 뒤 추가 피해가 없도록 물러났다."
                ).random()

                Crime.INJURY -> listOf(
                    "나는 피해가 발생한 것을 확인하자 즉시 공격을 중단했다.",
                    "나는 상대의 상태가 악화되지 않도록 교전을 끝냈다.",
                    "나는 상대가 다친 것을 본 뒤 더 이상 공격하지 않았다.",
                    "나는 상해가 발생한 직후 상대와 거리를 두었다.",
                    "나는 상대를 쓰러뜨리는 것을 목적으로 공격을 이어가지 않았다.",
                    "나는 상대가 피해를 입은 뒤에는 추격하지 않았다.",
                    "나는 피해가 예상보다 컸다는 것을 확인하고 즉시 물러났다.",
                    "나는 상대의 상태를 확인한 이후 공격 자세를 풀었다.",
                    "나는 추가적인 상해를 막기 위해 공격을 멈췄다.",
                    "나는 상대가 부상을 입은 시점부터 교전을 계속하지 않았다.",
                    "나는 한 번의 충돌 이후 전투를 확대하지 않았다.",
                    "나는 상대가 위험한 상태라고 판단해 더 이상 접근하지 않았다.",
                    "나는 피해가 발생한 이후 상대에게 추가 능력을 사용하지 않았다.",
                    "나는 상대의 체력이 감소한 것을 확인하고 전투에서 이탈했다.",
                    "나는 부상이 발생한 뒤에는 상대의 후퇴를 방해하지 않았다.",
                    "나는 상대에게 더 큰 피해를 줄 기회가 있었지만 공격하지 않았다.",
                    "나는 공격 이후 즉시 상황을 종료하려고 했다.",
                    "나는 피해가 발생한 것을 인지한 뒤 공격 대상을 바꾸거나 교전에서 빠졌다.",
                    "나는 상대의 상태가 좋지 않다는 것을 보고 더 이상 싸우지 않았다.",
                    "나는 공격을 계속하면 위험하다고 판단해 교전을 중단했다."
                ).random()

                Crime.ABUSE -> listOf(
                    "나는 급박한 상황에서 여러 위협에 대응하기 위해 능력을 연속으로 사용했다.",
                    "나는 하나의 교전을 해결하는 과정에서 짧은 시간 동안 능력을 집중해서 사용했다.",
                    "나는 여러 방향에서 공격을 받고 있었기 때문에 각각 대응할 필요가 있었다.",
                    "나는 생존이 어려운 상황에서 방어 수단으로 능력을 반복해서 사용했다.",
                    "나는 전투를 빠르게 종료하기 위해 짧은 시간 안에 능력을 연계했다.",
                    "나는 서로 다른 대상에게 대응하는 과정에서 능력 사용이 연속해서 발생했다.",
                    "나는 당시 공격을 피하면서 동시에 반격해야 하는 상황이었다.",
                    "나는 능력을 사용할 때마다 서로 다른 위협에 대응하고 있었다.",
                    "나는 한 번의 전투 과정에서 필요한 행동을 연속적으로 수행했다.",
                    "나는 상대의 공격이 계속되어 능력을 여러 번 사용할 수밖에 없었다.",
                    "나는 위협이 이어지는 동안 대응 수단을 반복해서 사용했다.",
                    "나는 능력 사용 사이마다 전투 상황을 확인하고 있었다.",
                    "나는 단순히 능력을 소모하려고 사용한 것이 아니라 실제 교전에 대응하고 있었다.",
                    "나는 주변에 여러 적이 있어 한 번의 능력만으로는 대응하기 어려웠다.",
                    "나는 전투에서 벗어나기 위한 과정에서도 능력을 사용했다.",
                    "나는 공격과 방어를 번갈아 수행하면서 능력을 여러 번 사용했다.",
                    "나는 능력을 연속으로 사용했지만 하나의 교전을 해결하기 위한 과정이었다.",
                    "나는 공격을 피하고 거리를 확보하기 위해 여러 능력을 조합했다.",
                    "나는 상대의 지속적인 압박 때문에 짧은 간격으로 대응했다.",
                    "나는 위협이 사라진 뒤에는 더 이상 능력을 연속해서 사용하지 않았다."
                ).random()

                Crime.ESCAPE -> listOf(
                    "나는 교전을 포기한 것이 아니라 더 유리한 위치를 확보하기 위해 이동했다.",
                    "나는 상대의 공격 범위에서 벗어난 뒤 다시 대응할 생각이었다.",
                    "나는 시야를 확보하기 위해 잠시 거리를 벌렸다.",
                    "나는 장애물을 이용해 상대의 공격을 피하려고 이동했다.",
                    "나는 공격을 피한 뒤 반격할 공간을 확보하기 위해 물러났다.",
                    "나는 교전을 계속하기 어려운 위치에서 벗어나려고 했다.",
                    "나는 상대와 정면으로 충돌하는 대신 위치를 바꾸었다.",
                    "나는 상대의 공격을 피하면서 다음 행동을 준비했다.",
                    "나는 단순히 멀어지려 한 것이 아니라 다른 방향에서 접근하려고 했다.",
                    "나는 불리한 지형에서 벗어나기 위해 이동했다.",
                    "나는 상대의 시야에서 잠시 벗어난 뒤 다시 교전할 생각이었다.",
                    "나는 공격을 회피하면서 안전한 위치를 확보했다.",
                    "나는 상대의 사거리 밖에서 다음 공격을 준비하려 했다.",
                    "나는 상대의 움직임을 확인하기 위해 거리를 확보했다.",
                    "나는 정면 교전을 피하고 다른 각도에서 싸우기 위해 이동했다.",
                    "나는 회복하거나 재정비할 시간을 확보하기 위해 잠시 물러났다.",
                    "나는 교전 자체를 포기하지 않고 위치만 변경했다.",
                    "나는 상대의 공격이 계속되는 상황에서 회피를 우선했다.",
                    "나는 계속 맞서 싸우기보다 한 차례 공격을 피한 뒤 다시 대응하려 했다.",
                    "나는 상대와 거리를 벌인 이후에도 교전 상황을 계속 확인하고 있었다."
                ).random()

                Crime.MURDER -> listOf(
                    "나는 생존을 위한 교전 과정에서 마지막 공격을 가했으며 살해를 계획하지 않았다.",
                    "나는 상대의 공격이 계속되는 상황에서 살아남기 위해 대응했다.",
                    "나는 상대가 교전을 멈추지 않아 마지막까지 공격을 주고받았다.",
                    "나는 즉각적인 위협에서 벗어나기 위한 과정에서 공격했다.",
                    "나는 상대를 죽이는 것을 목표로 교전을 시작한 것이 아니었다.",
                    "나는 전투가 그렇게 끝날 것이라고 예상하지 못했다.",
                    "나는 공격을 멈추면 내가 먼저 쓰러질 수 있다고 판단했다.",
                    "나는 당시 상대도 계속해서 나를 공격하고 있었다.",
                    "나는 상대의 체력 상태를 정확히 알지 못한 상태에서 공격했다.",
                    "나는 마지막 공격이 치명적일 것이라고 확신하지 못했다.",
                    "나는 전투 과정에서 살아남기 위해 공격을 이어갔다.",
                    "나는 계획적으로 상대를 추적해 살해한 것이 아니라 교전 중 사망이 발생했다.",
                    "나는 상대의 공격에 대응하는 과정에서 마지막 일격을 가했다.",
                    "나는 상황이 종료되기 전에 상대가 다시 공격할 수 있다고 판단했다.",
                    "나는 전투 중 발생한 결과였으며 사전에 살해를 준비하지 않았다.",
                    "나는 상대가 계속 위협적인 행동을 하고 있어 대응을 중단하지 못했다.",
                    "나는 마지막 순간까지 서로 공격을 주고받고 있었다.",
                    "나는 상대가 죽을 정도의 피해를 입었다는 사실을 즉시 알지 못했다.",
                    "나는 상대를 제거하는 것보다 전투에서 살아남는 것을 우선하고 있었다.",
                    "나는 교전이 끝나기 전에 발생한 공격으로 인해 사망이 발생했다."
                ).random()
            }

            val crimeSpecificLies = when (crime) {
                Crime.ASSAULT -> listOf(
                    "내가 가한 공격은 상대에게 실제 피해로 적용되지 않았다.",
                    "나는 상대에게 접근했지만 직접 공격한 것은 아니었다.",
                    "기록된 피해가 발생한 시점에는 나는 이미 공격을 멈춘 상태였다.",
                    "나는 상대를 향해 공격했지만 실제로 적중하지는 않았다.",
                    "상대가 받은 피해 중 내가 직접 가한 피해는 없었다.",
                    "나는 상대와 가까이 있었지만 공격 행동은 하지 않았다.",
                    "내 공격으로 보이는 행동은 다른 대상을 향한 것이었다.",
                    "상대에게 피해가 발생하기 직전에 나는 이미 거리를 두고 있었다.",
                    "나는 상대를 견제했을 뿐 실제 피해를 주지는 않았다.",
                    "기록된 공격 직후 상대의 체력에는 변화가 없었다.",
                    "나는 상대와 교전했지만 문제로 지적된 공격은 내가 한 것이 아니다.",
                    "상대가 받은 큰 피해는 내가 공격하기 전에 이미 발생해 있었다.",
                    "나는 상대에게 직접적인 공격 판정을 발생시키지 않았다.",
                    "내가 사용한 행동은 상대에게 피해를 주는 종류가 아니었다.",
                    "상대와 충돌한 것은 사실이지만 공격으로 이어지지는 않았다.",
                    "나는 공격하려다 중단했기 때문에 실제 적중은 발생하지 않았다.",
                    "기록된 시점에 상대를 공격한 사람은 나만 있었던 것이 아니다.",
                    "내 공격 직후 발생한 피해는 다른 공격의 결과였다.",
                    "나는 상대의 이동을 막았을 뿐 직접적인 피해를 주지는 않았다.",
                    "상대가 받은 피해량은 내가 가할 수 있는 피해량과 일치하지 않는다.",
                    "내가 공격 동작을 한 것은 맞지만 상대를 대상으로 한 것은 아니었다.",
                    "상대에게 발생한 피해는 내가 공격 범위에서 벗어난 이후의 일이다.",
                    "나는 교전에 참여했지만 해당 피해를 발생시킨 공격에는 관여하지 않았다.",
                    "상대가 피해를 입은 순간 나는 다른 플레이어를 상대하고 있었다."
                )

                Crime.INJURY -> listOf(
                    "상대가 입은 상해는 나와 교전하기 전에 이미 발생해 있었다.",
                    "내 공격 이후 상대의 상태가 악화된 것은 아니다.",
                    "내가 가한 피해만으로는 기록된 정도의 상해가 발생할 수 없었다.",
                    "상대가 크게 다친 시점에는 나는 더 이상 공격하고 있지 않았다.",
                    "내 공격이 적중한 것은 맞지만 상해를 발생시킬 정도의 피해는 아니었다.",
                    "상대의 체력 감소 대부분은 다른 원인으로 발생했다.",
                    "나는 상대에게 직접적인 피해를 주는 능력을 사용하지 않았다.",
                    "문제가 된 상처는 내가 공격하기 전부터 존재했다.",
                    "내가 마지막으로 공격했을 때 상대는 아직 정상적인 상태였다.",
                    "상대에게 발생한 큰 피해는 내 공격과 시간적으로 일치하지 않는다.",
                    "나는 상대를 공격했지만 기록된 상해와는 관련이 없다.",
                    "내 공격 이후 추가적인 피해가 발생하면서 상태가 악화된 것이다.",
                    "상대가 부상을 입은 순간 나는 공격 범위 밖에 있었다.",
                    "내가 가한 피해는 상대가 입은 전체 피해 중 극히 일부였다.",
                    "나는 상대와 충돌했지만 직접적인 상해를 입히지는 않았다.",
                    "상대의 상태가 악화된 원인은 내가 사용한 공격이 아니었다.",
                    "나는 피해를 발생시킨 공격이 아니라 그보다 이전의 공격만 했다.",
                    "상대가 다친 원인은 다른 플레이어와의 교전이었다.",
                    "내 행동과 상대의 부상이 거의 동시에 발생했을 뿐 직접적인 관계는 없다.",
                    "나는 상대의 체력이 충분한 상태에서 이미 교전을 중단했다.",
                    "문제가 된 피해가 발생했을 때 나는 다른 대상을 상대하고 있었다.",
                    "상대가 받은 피해량은 내 공격으로 발생할 수 있는 범위를 넘어선다.",
                    "나는 해당 시점에 상대에게 피해 판정을 발생시키지 않았다.",
                    "내 공격 이후 상대가 받은 추가 피해가 실제 상해의 원인이었다."
                )

                Crime.ABUSE -> listOf(
                    "기록된 능력 사용 중 일부는 실제 발동까지 이어지지 않았다.",
                    "나는 짧은 시간 동안 여러 번 시도했지만 실제 발동 횟수는 그보다 적었다.",
                    "연속된 기록 가운데 일부는 같은 능력 사용을 중복해서 기록한 것이다.",
                    "나는 능력을 사용한 것이 아니라 사용 준비 상태에 들어갔을 뿐이다.",
                    "기록된 횟수에는 다른 플레이어의 능력 사용도 포함되어 있다.",
                    "나는 해당 시간 동안 능력을 세 번 이상 사용하지 않았다.",
                    "능력 사용으로 기록된 행동 중 하나는 재사용에 실패한 행동이었다.",
                    "같은 효과가 여러 번 발생했지만 실제 능력 사용은 한 번이었다.",
                    "나는 능력을 연속해서 사용한 것이 아니라 서로 충분한 간격을 두고 사용했다.",
                    "기록된 마지막 능력 사용은 내가 한 행동이 아니다.",
                    "나는 해당 시간 구간이 시작되기 전에 이미 첫 능력을 사용했다.",
                    "문제가 된 사용 횟수 중 하나는 지속 중인 효과가 다시 발생한 것이다.",
                    "나는 능력을 발동하려 했지만 실제 효과는 발생하지 않았다.",
                    "내 능력 사용 기록에는 자동으로 발생한 효과도 포함되어 있다.",
                    "나는 같은 능력을 반복해서 사용하지 않았다.",
                    "기록된 능력 중 일부는 장비 효과였으며 직접 사용한 능력이 아니다.",
                    "문제가 된 시간 동안 실제로 내가 조작해 사용한 능력은 두 번뿐이었다.",
                    "능력 효과가 여러 차례 발생했지만 입력한 횟수는 그보다 적었다.",
                    "내가 사용한 능력 하나가 여러 개의 효과를 발생시킨 것이다.",
                    "마지막 사용 기록은 이미 종료된 능력의 후속 효과였다.",
                    "나는 능력 사용 사이에 충분한 재사용 간격을 두었다.",
                    "기록상 연속 사용으로 보이지만 실제로는 서로 다른 시점의 행동이다.",
                    "일부 효과는 다른 플레이어가 내게 적용한 것이었다.",
                    "나는 문제로 지적된 횟수만큼 능력을 직접 발동하지 않았다."
                )

                Crime.ESCAPE -> listOf(
                    "나는 상대에게서 멀어진 것이 아니라 옆 방향으로 위치를 바꾼 것이다.",
                    "교전 종료 시점의 거리는 교전 시작 시점과 크게 다르지 않았다.",
                    "나는 상대에게서 도망친 것이 아니라 공격을 피하기 위해 이동했다.",
                    "문제가 된 이동 대부분은 상대가 내게서 멀어진 결과였다.",
                    "나는 일정 거리 이상 상대에게서 벗어난 적이 없다.",
                    "나는 상대를 시야에서 놓치지 않은 채 위치만 변경했다.",
                    "내 이동 방향은 상대와 정확히 반대 방향이 아니었다.",
                    "나는 오히려 다른 경로로 상대에게 다시 접근하고 있었다.",
                    "거리가 벌어진 시점에는 이미 교전이 종료된 상태였다.",
                    "상대와의 거리가 크게 벌어진 것은 상대가 이동했기 때문이다.",
                    "나는 교전 중 상대에게서 지속적으로 멀어지지 않았다.",
                    "문제가 된 이동은 공격을 피하기 위한 짧은 회피였다.",
                    "나는 상대에게 등을 돌린 채 계속 이동한 적이 없다.",
                    "거리 변화는 순간적인 것이었고 곧 다시 상대에게 접근했다.",
                    "나는 도주 경로가 아니라 상대의 측면으로 이동하고 있었다.",
                    "교전 중 가장 멀어진 순간에도 상대를 계속 공격할 수 있는 위치였다.",
                    "나는 상대가 접근하자 위치를 조정했을 뿐 전장을 벗어나지 않았다.",
                    "상대와의 거리가 벌어지기 시작한 것은 내가 아니라 상대가 먼저 이동한 이후였다.",
                    "나는 교전 장소를 떠난 것이 아니라 같은 구역 안에서 이동했다.",
                    "문제로 지적된 거리만큼 실제로 이동하지 않았다.",
                    "나는 상대에게서 멀어졌다가 즉시 다시 접근했다.",
                    "상대와 멀어진 순간에도 다른 적과 계속 교전하고 있었다.",
                    "나는 후퇴한 것이 아니라 공격 각도를 바꾸기 위해 이동했다.",
                    "내 이동은 상대와 거리를 벌리기 위한 행동이 아니었다."
                )

                Crime.MURDER -> listOf(
                    "내 공격이 마지막으로 적중한 것은 맞지만 직접적인 사망 원인은 아니었다.",
                    "피해자가 사망한 순간에는 내가 공격하고 있지 않았다.",
                    "내 마지막 공격 이후에도 피해자는 생존한 상태였다.",
                    "결정적인 피해는 내가 아닌 다른 공격에서 발생했다.",
                    "나는 피해자의 체력을 사망할 정도까지 감소시키지 않았다.",
                    "피해자는 내 공격 이후 다른 피해를 추가로 받았다.",
                    "내 공격과 피해자의 사망 사이에는 다른 교전이 있었다.",
                    "나는 피해자에게 마지막으로 피해를 준 플레이어가 아니다.",
                    "피해자가 사망할 당시 나는 이미 교전에서 벗어난 상태였다.",
                    "내 공격은 피해자의 사망 직전 공격이 아니었다.",
                    "피해자는 나와 교전한 뒤에도 계속 움직이며 다른 플레이어와 싸웠다.",
                    "내가 마지막으로 본 피해자는 아직 충분한 체력을 가지고 있었다.",
                    "피해자를 쓰러뜨린 최종 공격은 내 공격이 아니었다.",
                    "나는 피해자와 싸운 것은 맞지만 사망까지 이어지지는 않았다.",
                    "피해자가 받은 치명적인 피해는 내가 공격을 중단한 이후 발생했다.",
                    "내 공격 직후 피해자가 사망한 것이 아니라 일정 시간이 지난 뒤 사망했다.",
                    "나는 피해자의 마지막 교전 상대가 아니었다.",
                    "피해자의 체력이 치명적인 수준으로 감소한 것은 내 공격 이후였다.",
                    "사망 직전 발생한 피해는 내가 사용할 수 없는 종류의 공격이었다.",
                    "나는 피해자가 사망하기 전에 이미 다른 장소로 이동했다.",
                    "내가 가한 마지막 피해만으로는 피해자가 사망할 수 없었다.",
                    "피해자가 사망한 시점에는 다른 플레이어도 피해자와 교전하고 있었다.",
                    "내 공격으로 피해자가 쓰러진 것이 아니라 이후 발생한 피해 때문에 사망했다.",
                    "나는 피해자에게 피해를 준 적은 있지만 마지막 일격은 가하지 않았다."
                )
            }

            val genericLies = listOf(
                "기록된 행동 중 일부는 내가 한 행동이지만 문제로 지적된 행동은 아니다.",
                "사건 당시 나는 기록에 표시된 대상과 다른 플레이어를 상대하고 있었다.",
                "내 행동과 사건의 결과가 같은 시점에 발생했을 뿐 직접적인 관계는 없다.",
                "기록된 순서와 실제로 행동한 순서는 다르다.",
                "문제가 된 행동이 발생했을 때 나는 이미 다른 행동을 하고 있었다.",
                "기록에 포함된 행동 중 일부만 내가 직접 수행한 것이다.",
                "내가 현장에 있었던 것은 맞지만 해당 행동에는 관여하지 않았다.",
                "나는 사건을 목격했을 뿐 직접적인 원인을 제공하지 않았다.",
                "기록된 대상과 내가 실제로 상대한 대상은 서로 다르다.",
                "내 행동 직후 사건이 발생했지만 내 행동 때문에 발생한 것은 아니다.",
                "나는 해당 플레이어와 접촉했지만 문제로 지적된 행동은 하지 않았다.",
                "문제가 된 시점에 나는 이미 해당 플레이어와의 교전을 끝낸 상태였다.",
                "나는 비슷한 행동을 한 것은 맞지만 기록된 시점의 행동은 내가 한 것이 아니다.",
                "내 행동은 기록되어 있지만 사건의 직접적인 원인은 아니었다.",
                "나는 사건 직전에 현장을 벗어나고 있었다.",
                "기록된 시간에는 내가 다른 플레이어와 행동하고 있었다.",
                "내가 사용한 행동과 증거에 기록된 행동은 종류가 다르다.",
                "나는 사건에 관여했지만 기록에서 주장하는 방식으로 관여한 것은 아니다.",
                "문제가 된 결과는 내가 행동을 끝낸 이후에 발생했다.",
                "나는 해당 상황에 참여했지만 직접적인 피해를 발생시키지는 않았다.",
                "기록된 행동과 내가 실제로 한 행동 사이에는 시간 차이가 있다.",
                "나는 해당 플레이어를 대상으로 행동하지 않았다.",
                "내가 현장에 있었기 때문에 행동의 주체로 오해된 것이다.",
                "나는 비슷한 위치에 있었지만 증거가 가리키는 행동은 수행하지 않았다.",
                "문제가 된 행동 직전에는 내가 같은 종류의 행동을 한 적이 없다.",
                "내 행동으로 보이는 기록 중 일부는 다른 전투에서 발생한 것이다.",
                "기록된 결과가 발생하기 전에 나는 이미 행동을 중단했다.",
                "나는 해당 사건과 같은 시간대에 다른 교전에 참여하고 있었다.",
                "증거에 기록된 행동의 대상은 내가 상대하던 대상과 일치하지 않는다.",
                "나는 사건에 영향을 줄 수 있는 위치에 있지 않았다.",
                "내가 행동한 시점과 사건이 발생한 시점은 서로 일치하지 않는다.",
                "나는 문제의 결과가 발생하기 전에 이미 상대와 거리를 두고 있었다.",
                "해당 결과가 발생한 것은 사실이지만 그것을 발생시킨 행동은 내가 하지 않았다.",
                "나는 사건 현장을 지나갔지만 교전에는 참여하지 않았다.",
                "기록에 등장하는 행동 중 내가 인정할 수 있는 것은 일부뿐이다.",
                "나는 해당 상황에 있었지만 증거에서 주장하는 행동은 하지 않았다.",
                "내가 수행한 행동은 사건의 결과와 직접 연결되지 않는다.",
                "기록된 결과가 발생할 당시 나는 다른 방향을 보고 있었다.",
                "문제가 된 행동 이전에 나는 이미 해당 행동을 끝낸 상태였다.",
                "나는 사건의 일부를 목격했지만 직접 개입하지는 않았다.",
                "몰랐다."
            )

            val lies = (crimeSpecificLies + genericLies)
                .distinct()
                .shuffled()
                .take(REFEREE_DEFENSE_LIE_COUNT)

            return buildList {
                add(DefenseOption(truth, DefenseKind.TRUTH))

                lies.forEach {
                    add(DefenseOption(it, DefenseKind.LIE))
                }

                add(
                    DefenseOption(
                        "해당 죄목의 사실을 인정하고 판결을 받아들이겠다.",
                        DefenseKind.ADMISSION
                    )
                )
            }.shuffled()
        }
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
                val attribute = target.player.getAttribute(Attribute.MAX_HEALTH) ?: return
                attribute.baseValue = (attribute.baseValue - 5.0 * multiplier).coerceAtLeast(1.0)
                target.player.health = target.player.health.coerceAtMost(attribute.value)
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
                if (PlayerTagManager.hasTag(target.player, "isTraining")) {
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
                !playerData.entityStatus.isDead && this in playerData.gameClasses

        private fun sameWorldDistance(first: Location, second: Location): Double =
            if (first.world != second.world) Double.MAX_VALUE else first.distance(second)
    }
}
