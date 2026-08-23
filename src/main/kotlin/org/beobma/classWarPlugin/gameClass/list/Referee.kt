package org.beobma.classWarPlugin.gameClass.list

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.game.GamePhase
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.UtilManager.getPlayerMaxHealth
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.Silence
import org.beobma.classWarPlugin.status.list.Snare
import org.beobma.classWarPlugin.util.CourtroomMidiPlayer
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
private const val REFEREE_DEFENSE_SECONDS = 40
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
        if (playerData.entityStatus.isDead || playerData.gameClass !== this) {
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
            "<gray>피고인은 40초 안에 10개의 진술 중 하나를 정확히 입력해 변론한다.",
            "<gray>진술에는 하나의 진실과 아홉 개의 거짓이 섞여 있다.",
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
                seedTrainingEvidence()
                selectedDefendant = playerData
                player.sendMiniMessage("<gold><bold>[모의 재판]</bold> <gray>자신이 판사와 모의 피고인을 겸합니다.")
                return true
            }
            val target = playerData.shotLaserGetEntityData(10.0, TargetType.Enemy, false) as? PlayerData
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
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 0.35F, 1.7F)
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

    private fun seedTrainingEvidence() {
        val ledger = evidenceByAccused.getOrPut(playerData.uniqueId) { mutableMapOf() }
        Crime.entries.forEach { crime ->
            val records = ledger.getOrPut(crime) { ArrayDeque() }
            if (records.isEmpty()) {
                records.addLast(Evidence(System.currentTimeMillis(), "훈련용 ${crime.displayName} 모의 증거"))
            }
        }
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
        val courtStatus: StatusAbnormality,
    )

    private class TrialSession(private val owner: Referee, private val defendant: PlayerData) {
        private val miniMessage = MiniMessage.miniMessage()
        private val game = owner.game
        private val judge = owner.playerData
        private val isTrainingTrial = PlayerTagManager.hasTag(judge.player, "isTraining")
        private val isSoloTrial = judge == defendant
        private val participants = game.playerDatas.filterIsInstance<PlayerData>()
            .filter { it.player.isOnline && !it.entityStatus.isDead }
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

        fun start() {
            if (participants.none { it == judge } || participants.none { it == defendant }) return
            activeTrials[game] = this
            participants.forEach { activeTrialPlayers[it.uniqueId] = this }
            game.isPaused = true
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
            midiPlayer?.tick(participants.map(PlayerData::player))
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
                    if (remainingTicks <= 0) beginVerdict(true, false, "피고인이 변론 제한 시간 40초 동안 답하지 않았습니다.")
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
            if (isSoloTrial) {
                judge.player.teleport(Location(judge.player.world, 625.5, -30.0, -508.5, 0.0F, 0.0F))
            }
            showTitle("<red><bold>${crime.displayName}죄", "<gray>피고인은 40초 안에 변론하십시오")
            broadcast("<gold><bold>[판사]</bold> <white>${defendant.player.name}<gray> 피고인을 <red><bold>${crime.displayName}죄</bold><gray>로 기소합니다.")
            broadcast("<dark_gray>[증거 기록] $evidence")
            defenseOptions = buildDefenseOptions(crime)
            defendant.player.sendMessage(Component.empty())
            defendant.player.sendMiniMessage("<aqua><bold>✦ 변론 선택지 — 정확히 한 문장만 진실입니다 ✦")
            defenseOptions.forEachIndexed { index, option ->
                defendant.player.sendMessage(Component.text("${index + 1}. ", NamedTextColor.DARK_AQUA)
                    .append(Component.text(option.text, NamedTextColor.WHITE)))
            }
            defendant.player.sendMiniMessage("<yellow>선택한 문장 전체를 40초 안에 채팅으로 똑같이 입력하세요.")
            participants.filter { it != defendant }.forEach {
                it.player.sendMiniMessage("<gray>피고인에게 10개의 비공개 변론 선택지가 전달되었습니다.")
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
        }

        private fun announceVerdict() {
            val crime = selectedCrime
            courtroomSound(Sound.BLOCK_ANVIL_LAND, 1.0F, 0.62F)
            courtroomSound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0F, if (verdictGuilty) 0.65F else 1.4F)
            val center = courtCenter()
            center.world.spawnParticle(if (verdictGuilty) Particle.SOUL_FIRE_FLAME else Particle.TOTEM_OF_UNDYING,
                center, 90, 4.0, 1.7, 4.0, 0.09)
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
                snapshot.courtStatus.remove()
                data.entityStatus.canAttack = snapshot.canAttack
                data.entityStatus.canSkillUse = snapshot.canSkillUse
                data.entityStatus.canMove = snapshot.canMove
                data.entityStatus.isAttackable = snapshot.isAttackable
                data.entityStatus.isSkillTargeting = snapshot.isSkillTargeting
                data.player.isGlowing = snapshot.glowing
                if (data.player.isOnline) data.player.teleport(snapshot.location)
                data.gameClass?.skills?.forEach { CooldownManager.resumeCooldown(data.player, it) }
            }
            activeTrials.remove(game, this)
            participants.forEach { activeTrialPlayers.remove(it.uniqueId, this) }
            game.isPaused = false
            broadcast("<dark_gray>━━━━━━━━━━━━ <gray>재판 종료 <dark_gray>━━━━━━━━━━━━")
            if (applyPunishment) selectedCrime?.let { owner.applyPunishment(defendant, it, verdictPerjury) }
        }

        private fun moveToCourtroom() {
            val world = judge.player.world
            val audience = participants.filter { it != judge && it != defendant }
            participants.forEach { data ->
                val courtStatus = CourtOrderStatus()
                data.addStatus(courtStatus, judge); courtStatus.applyStatus(powerSet = 1)
                val status = data.entityStatus
                snapshots[data.uniqueId] = CourtSnapshot(data.player.location.clone(), status.canAttack,
                    status.canSkillUse, status.canMove, status.isAttackable, status.isSkillTargeting,
                    data.player.isGlowing, courtStatus)
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
            data.gameClass?.skills?.forEach { CooldownManager.pauseCooldown(data.player, it) }
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
                world.spawnParticle(if (index % 3 == 0) Particle.END_ROD else Particle.ENCHANT,
                    center.x + cos(angle) * radius, center.y + 0.35 + (index % 3) * 0.35,
                    center.z + sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0)
            }
            if (elapsedTicks % 12 == 0) {
                world.spawnParticle(Particle.DUST, center, 18, 5.5, 0.4, 8.0, 0.0,
                    Particle.DustOptions(Color.fromRGB(255, 190, 35), 1.1F))
                world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, center, 8, 4.0, 1.2, 6.0, 0.02)
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
            .forEach { it.player.playSound(it.player.location, sound, SoundCategory.MASTER, volume, pitch) }

        private fun courtCenter(): Location = Location(judge.player.world, 625.5, -29.0, -496.0)

        private fun buildDefenseOptions(crime: Crime): List<DefenseOption> {
            val truth = when (crime) {
                Crime.ASSAULT -> listOf("나는 즉시 공격을 멈추고 더 큰 피해가 발생하지 않도록 물러났다.", "나는 치명적인 공격을 피했고 상황을 끝내기 위해 거리를 벌렸다.")
                Crime.INJURY -> listOf("나는 충돌 직후 공격을 중단하고 상대의 상태를 확인했다.", "나는 더 이상의 상해를 막기 위해 즉시 교전을 끝냈다.")
                Crime.ABUSE -> listOf("나는 급박한 위협에 대응하려고 연속해서 능력을 사용했다.", "나는 같은 상황을 해결하기 위해 제한된 시간에 능력을 집중 사용했다.")
                Crime.ESCAPE -> listOf("나는 전투를 포기한 것이 아니라 더 유리한 위치로 이동했다.", "나는 상대의 공격을 피하며 시야를 확보하려고 거리를 벌렸다.")
                Crime.MURDER -> listOf("나는 생존을 위한 교전 중 마지막 공격을 가했으며 계획된 살해는 아니었다.", "나는 먼저 시작된 치명적 공격에서 살아남기 위해 맞서 싸웠다.")
            }.random()
            val lies = when (crime) {
                Crime.ASSAULT -> listOf("나는 그 플레이어에게 어떠한 피해도 입히지 않았다.", "그 피해는 전부 다른 플레이어가 가한 것이다.", "나는 사건 당시 전혀 다른 장소에 있었다.", "상대의 최대 체력 절반을 넘는 피해는 기록 오류다.")
                Crime.INJURY -> listOf("나는 그 플레이어와 교전한 사실 자체가 없다.", "상대는 나를 만나기 전부터 이미 다쳐 있었다.", "내 공격은 한 번도 상대에게 닿지 않았다.", "기록된 피해는 다른 사람의 공격이다.")
                Crime.ABUSE -> listOf("나는 10초 동안 능력을 한 번도 사용하지 않았다.", "능력 사용 기록은 모두 다른 플레이어의 것이다.", "나는 사건 당시 침묵 상태여서 능력을 쓸 수 없었다.", "세 번의 능력 사용은 전부 하나의 능력으로 계산된 오류다.")
                Crime.ESCAPE -> listOf("나는 교전 내내 한 블록도 움직이지 않았다.", "상대가 멀어진 것이며 나는 제자리에 있었다.", "나는 사건 당시 다른 차원에 있었다.", "기록된 거리는 순간이동 오류로 생겼다.")
                Crime.MURDER -> listOf("나는 그 플레이어를 살해하지 않았다.", "결정적인 공격은 다른 플레이어가 가했다.", "피해자는 나와 싸우기 전에 이미 사망했다.", "나는 사건 당시 피해자와 같은 월드에 없었다.")
            } + listOf("모든 증거는 판사가 조작한 것이므로 인정할 수 없다.", "목격자들의 기억이 모두 틀렸고 내 기억만 정확하다.",
                "대천칭의 기록은 나와 이름이 같은 다른 사람에 대한 것이다.", "나는 그 순간 아무 행동도 하지 않았다고 확신한다.")
            return buildList {
                add(DefenseOption(truth, DefenseKind.TRUTH))
                lies.take(8).forEach { add(DefenseOption(it, DefenseKind.LIE)) }
                add(DefenseOption("해당 죄목의 사실을 인정하고 판결을 받아들이겠다.", DefenseKind.ADMISSION))
            }.shuffled()
        }
    }

    private fun applyPunishment(target: PlayerData, crime: Crime, perjury: Boolean) {
        if (!target.player.isOnline || target.entityStatus.isDead) return
        val multiplier = if (perjury) 2 else 1
        target.player.sendMiniMessage("<dark_red><bold>[형 집행]</bold> <gray>${crime.displayName}죄${if (perjury) " 및 위증" else ""}의 형을 집행합니다.")
        target.player.world.spawnParticle(Particle.SOUL_FIRE_FLAME, target.player.location.add(0.0, 1.0, 0.0), 70, 0.8, 1.1, 0.8, 0.08)
        target.player.playSound(target.player.location, Sound.BLOCK_ANVIL_LAND, SoundCategory.MASTER, 1.0F, 0.55F)
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
                applyTemporaryDisarm(target, duration)
            }
            Crime.ABUSE -> {
                val cooldownMultiplier = if (perjury) 4.0 else 2.0
                target.gameClass?.skills?.forEach { CooldownManager.multiplyCooldown(target.player, it, cooldownMultiplier) }
            }
            Crime.ESCAPE -> {
                val duration = 10 * multiplier
                target.addStatus(Snare(), playerData).applyStatus(duration = duration, powerSet = 1)
                applyRadiation(target, duration)
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

    private fun applyTemporaryDisarm(target: PlayerData, seconds: Int) {
        val prior = target.entityStatus.canAttack
        target.entityStatus.canAttack = false
        target.addStatus(CourtDisarmStatus(), playerData).applyStatus(duration = seconds, powerSet = 1)
        object : BukkitRunnable() {
            override fun run() { if (!target.entityStatus.isDead) target.entityStatus.canAttack = prior }
        }.runTaskLater(ClassWarPlugin.instance, seconds * 20L).also(target::trackTask)
    }

    private fun applyRadiation(target: PlayerData, seconds: Int) {
        val prior = target.player.isGlowing
        target.player.isGlowing = true
        target.addStatus(RadiationStatus(), playerData).applyStatus(duration = seconds, powerSet = 1)
        object : BukkitRunnable() {
            override fun run() { if (target.player.isOnline) target.player.isGlowing = prior }
        }.runTaskLater(ClassWarPlugin.instance, seconds * 20L).also(target::trackTask)
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

    private class CourtDisarmStatus : StatusAbnormality() {
        override val name = "<dark_gray><bold>무장해제</bold><gray>"
        override val description = listOf("<gray>기본 공격을 사용할 수 없다.")
        override val canRemove = true
        override var power = 1
        override var maxPower: Int? = 1
        override val showPower = false
        override val showMaxPower = false
    }

    private class RadiationStatus : StatusAbnormality() {
        override val name = "<white><bold>발광</bold><gray>"
        override val description = listOf("<gray>주변 플레이어에게 위치가 드러난다.")
        override val canRemove = true
        override var power = 1
        override var maxPower: Int? = 1
        override val showPower = false
        override val showMaxPower = false
    }

    companion object {
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
                !playerData.entityStatus.isDead && playerData.gameClass === this

        private fun sameWorldDistance(first: Location, second: Location): Double =
            if (first.world != second.world) Double.MAX_VALUE else first.distance(second)
    }
}
