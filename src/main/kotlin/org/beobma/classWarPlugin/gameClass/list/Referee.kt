package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.domain.*
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.gameClass.handler.*
import org.beobma.classWarPlugin.gameClass.referee.*
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.status.list.*
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val REFEREE_RED_SKILL_COOLDOWN_SECONDS = 10
private const val REFEREE_ORANGE_SKILL_COOLDOWN_SECONDS = 40
private const val REFEREE_DOMAIN_SKILL_COOLDOWN_SECONDS = 300

class Referee : GameClass(), OnHitHandler, ConfirmedHitHandler, GameEndHandler {
    private val ledger = CrimeLedger()
    private var attacks = 0
    private var exhaustedUntil = 0L
    private val indictments = mutableMapOf<UUID, Long>()
    private var trial: Trial? = null

    override fun onGameEnd() {
        trial?.session?.close()
        ledger.clear()
        indictments.clear()
        attacks = 0
    }

    override fun onAttackHit(context: DamageContext) {
        if (!context.secondaryAttack && ledger.heaviest(context.target.entity.uniqueId) != null) context.addBaseDamage(1.0)
    }

    override fun onConfirmedHit(context: DamageContext) {
        if (!context.path.isBasicAttack || context.secondaryAttack) return
        if (++attacks % 2 == 0) illuminate(context.target, 1)
    }

    private fun illuminate(target: EntityData, amount: Int) {
        if (game.combatTick < exhaustedUntil) return
        target.getOrCreateStatus(playerData) { Brightness() }.applyStatus(powerDelta = amount)
        particles.spawn(target.entity.location, Particle.END_ROD, count = 12, spread = 0.4)
    }

    private fun target(range: Double): EntityData? = playerData.shotLaserGetEntityData(range, TargetType.Enemy, false)

    private fun smite(): Boolean {
        val enemy = target(4.0) ?: return false
        val direction = enemy.entity.location.toVector().subtract(player.location.toVector()).setY(0.0)
        if (direction.lengthSquared() > 0.01) direction.normalize().multiply(0.55)
        player.velocity = direction.setY(0.55)
        object : AbilityRunnable(abilityScope) {
            override fun run() {
                if (!enemy.entity.isValid || enemy.entityStatus.isDead || enemy.entity.world != player.world ||
                    player.location.distanceSquared(enemy.entity.location) > 36.0) return
                enemy.damage(6.0, DamageType.Normal, playerData)
                if (ledger.heaviest(enemy.entity.uniqueId) != null) illuminate(enemy, 2)
                sounds.play(enemy.entity.location, Sound.BLOCK_ANVIL_LAND, pitch = 0.8f)
                particles.spawn(enemy.entity.location, Particle.CRIT, count = 25, spread = 0.5)
            }
        }.runTaskLater(ClassWarPlugin.instance, 8L)
        return true
    }

    private fun indict(): Boolean {
        val enemy = target(10.0) ?: return false
        enemy.addStatus(MoveSpeedDecrease(), playerData).applyStatus(duration = 10, powerSet = 20)
        enemy.addStatus(WhenDamageIncreased(), playerData).applyStatus(duration = 10, powerSet = 20)
        indictments.entries.removeIf { it.value <= game.combatTick }
        if (ledger.heaviest(enemy.entity.uniqueId) != null) indictments[enemy.entity.uniqueId] = game.combatTick + 200
        particles.spawn(enemy.entity.location.clone().add(0.0, 2.0, 0.0), Particle.END_ROD, count = 30, spread = 0.4)
        sounds.play(enemy.entity.location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, pitch = 0.8f)
        return true
    }

    private fun openCourt(): Boolean {
        val training = org.beobma.classWarPlugin.manager.PlayerTagManager.isTraining(player)
        val defendant = DomainTarget.LOOKING_AT_PLAYER.resolve(target(10.0) as? PlayerData, playerData, training)
            ?: return false
        val center = player.location.clone().apply { x = blockX + 0.5; y = blockY.toDouble(); z = blockZ + 0.5 }
        if (defendant.player.world != player.world || defendant.player.location.distanceSquared(center) > 100.0 ||
            defendant.player.location.y < center.y - 1.0) return false
        val charge = CrimeLedger.trialCharge(ledger.heaviest(defendant.uniqueId), training, defendant.uniqueId, game.combatTick)
        if (charge == null || trial != null || activeTrials.containsKey(defendant.uniqueId)) {
            player.sendMiniMessage("<red>기록된 죄가 있는 재판 가능한 플레이어를 바라보세요.")
            sounds.play(player, Sound.BLOCK_NOTE_BLOCK_BASS, pitch = 0.5f)
            return false
        }
        val murders = ledger.murders(defendant.uniqueId)
        val originalPositions = game.playerDatas.filterIsInstance<PlayerData>()
            .filter { it.player.world == player.world }.associate { it.uniqueId to it.player.location.clone() }
        var court: Trial? = null
        return DomainManager.expand(abilityScope, DomainDefinition(
            name = "대천칭", radius = 10, durationTicks = 400, target = DomainTarget.SURROUNDING,
            interior = DomainInteriors::courtroom,
            onStart = { session ->
                if (defendant.uniqueId !in session.participants || defendant.entityStatus.isDead || !defendant.player.isOnline) {
                    session.close()
                } else {
                    val current = Trial(session, defendant, charge, murders)
                    court = current
                    trial = current
                    current.start()
                }
            },
            onTick = { session ->
                val current = court
                if (current == null || !defendant.player.isOnline || defendant.entityStatus.isDead ||
                    defendant.uniqueId !in session.participants) session.close()
                else current.tick()
            },
            onEnd = { session ->
                court?.let { current ->
                    current.close()
                    session.participants.forEach { id ->
                        originalPositions[id]?.let { location -> Bukkit.getPlayer(id)?.let { session.relocate(it, location) } }
                    }
                    trial = null
                    ledger.clear()
                    exhaustedUntil = game.combatTick + 400
                    if (session.finishedNormally && current.completed && abilityScope.isActive && player.isOnline && !playerStatus.isDead && !defendant.entityStatus.isDead &&
                        defendant.player.isOnline && !game.isPaused) current.punish()
                }
            },
        ))
    }

    private inner class Trial(val session: DomainSession, val defendant: PlayerData,
        val charge: CrimeRecord, val murders: Int) : Listener, AutoCloseable {
        private val resources = ResourceScope()
        private val menu = Bukkit.createInventory(null, 9, Component.text("대천칭 — 변론 선택"))
        private var plea: Plea? = null
        private var elapsed = 0
        var completed = false
            private set

        fun start() {
            activeTrials[defendant.uniqueId] = this@Referee
            resources.own { activeTrials.remove(defendant.uniqueId, this@Referee) }
            // Explicit locks also cover allies; harmful status filtering must not let spectators fight.
            game.playerDatas.filterIsInstance<PlayerData>().filter { it.uniqueId in session.participants }.forEach { data ->
                val lock = data.entityStatus.controlLocks.acquire(Control.MOVE, Control.ATTACK, Control.SKILL)
                resources.own { lock.close() }
                val immunity = data.addStatus(Invincibility(), playerData)
                resources.own { immunity.cleanupFromManager() }
            }
            if (defendant.uniqueId != playerData.uniqueId)
                session.relocate(player, session.center.clone().add(0.0, 0.0, -3.0).apply { yaw = 0f; pitch = 0f })
            session.relocate(defendant.player, session.center.clone().add(0.0, 0.0, 0.0).apply { yaw = 180f; pitch = 0f })
            listOf(Triple(1, Material.PAPER, "죄를 인정한다 — 한 단계 경감"),
                Triple(4, Material.SHIELD, "정당방위였다 — 선제 공격 기록 확인"),
                Triple(7, Material.BARRIER, "내가 저지르지 않았다 — 위증 시 가중")).forEach { (slot, material, label) ->
                menu.setItem(slot, ItemStack(material).apply { itemMeta = itemMeta.apply { displayName(Component.text(label)) } })
            }
            Bukkit.getPluginManager().registerEvents(this, ClassWarPlugin.instance)
            resources.own { HandlerList.unregisterAll(this) }
            resources.own { if (defendant.player.openInventory.topInventory === menu) defendant.player.closeInventory() }
            session.players().forEach {
                it.sendMessage(Component.text("[죄목] ${charge.type.label} / 피해자: ${charge.victimName} / 피해: ${charge.damage} / 기록 틱: ${charge.tick}"))
                it.sendMessage(Component.text("20초 내 변론. 미응답 시 원래 형량. 사형은 살인 2건 이상과 위증이 함께 있을 때만 적용됩니다."))
            }
            defendant.player.openInventory(menu)
            defendant.player.sendMiniMessage("<gold>창을 닫았다면 채팅으로 인정 / 정당방위 / 부인을 입력하세요.")
        }

        @EventHandler
        fun click(event: InventoryClickEvent) {
            if (event.view.topInventory !== menu) return
            event.isCancelled = true
            if (event.whoClicked.uniqueId != defendant.uniqueId) return
            val choice = when (event.rawSlot) { 1 -> Plea.CONFESS; 4 -> Plea.SELF_DEFENSE; 7 -> Plea.DENY; else -> return }
            choose(choice)
        }

        @EventHandler
        fun drag(event: InventoryDragEvent) { if (event.view.topInventory === menu) event.isCancelled = true }

        @EventHandler
        fun teleport(event: org.bukkit.event.player.PlayerTeleportEvent) {
            if (event.player.uniqueId in session.participants && session.relocating != event.player.uniqueId) event.isCancelled = true
        }

        @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
        fun damage(event: org.bukkit.event.entity.EntityDamageEvent) {
            if (event.entity.uniqueId in session.participants) event.isCancelled = true
        }

        fun choose(choice: Plea) {
            if (plea != null || completed || game.isPaused) return
            plea = choice
            defendant.player.sendMessage(Component.text("변론이 접수되었습니다. 판결을 기다리세요."))
        }

        fun tick() {
            if (elapsed % 20 == 0) {
                val beam = session.center.clone().add(0.0, 4.0, -2.0)
                particles.line(beam.clone().add(-2.0, 0.0, 0.0), beam.clone().add(2.0, 0.0, 0.0), Particle.END_ROD, spacing = 0.2)
                for (side in listOf(-2.0, 2.0)) {
                    val pan = beam.clone().add(side, -1.5, 0.0)
                    particles.line(pan, beam.clone().add(side, 0.0, 0.0), Particle.END_ROD, spacing = 0.25)
                    particles.circle(pan, Particle.END_ROD, 0.7, 16)
                }
                defendant.player.sendActionBar(Component.text("변론 시간: ${20 - elapsed / 20}초"))
            }
            if (++elapsed != 400) return
            completed = true
            val verdict = CrimeLedger.verdict(charge, plea, murders)
            val text = when (verdict.severity) { 0 -> "무죄"; 1 -> "경형"; 2 -> "중형"; 3 -> "중형 · 가중"; else -> "사형" }
            session.players().forEach {
                it.showTitle(Title.title(Component.text(if (verdict.perjury) "「위증 · 판결」" else "「판결」"), Component.text(text)))
                it.playSound(it.location, Sound.BLOCK_ANVIL_LAND, 1f, 0.6f)
            }
        }

        fun punish() {
            when (CrimeLedger.verdict(charge, plea, murders).severity) {
                0 -> Unit
                1 -> defendant.addStatus(MoveSpeedDecrease(), playerData).applyStatus(duration = 10, powerSet = 30)
                2 -> {
                    defendant.addStatus(Silence(), playerData).applyStatus(duration = 5, powerSet = 1)
                    defendant.addStatus(Disarm(), playerData).applyStatus(duration = 5, powerSet = 1)
                    defendant.addStatus(Snare(), playerData).applyStatus(duration = 3, powerSet = 1)
                }
                3 -> {
                    defendant.statusAbnormalitys.filterIsInstance<Shield>().toList().forEach { it.remove() }
                    defendant.addStatus(Silence(), playerData).applyStatus(duration = 8, powerSet = 1)
                    defendant.addStatus(Disarm(), playerData).applyStatus(duration = 8, powerSet = 1)
                    defendant.addStatus(WhenDamageIncreased(), playerData).applyStatus(duration = 10, powerSet = 30)
                }
                4 -> {
                    val execution = DamageContext(playerData, defendant,
                        org.beobma.classWarPlugin.damage.DamagePath.SKILL, DamageType.True, defendant.player.health, bypassShield = true)
                    org.beobma.classWarPlugin.manager.DamageManager.recordSuccessfulDamage(execution)
                    defendant.player.health = 0.0
                }
            }
        }

        override fun close() { resources.close() }
    }

    companion object {
        private val activeTrials = ConcurrentHashMap<UUID, Referee>()
        private fun observers(game: Game): List<Referee> = game.playerDatas.filterIsInstance<PlayerData>()
            .filter { !it.entityStatus.isDead }.flatMap { AbilityTree.nodes(it.gameClasses, activeOnly = true) }
            .filterIsInstance<Referee>().filter { it.abilityScope.isActive && !it.abilityScope.suspended }

        fun recordDamage(context: DamageContext, damage: Double) {
            val victim = context.target as? PlayerData ?: return
            if (context.isCancelled || damage <= 0 || !context.attacker.isEnemyOf(victim)) return
            observers(context.attacker.game).forEach {
                it.ledger.damage(context.attacker.uniqueId, victim.uniqueId, victim.player.name, it.game.combatTick, damage)
            }
        }

        fun recordMurder(game: Game, killerId: UUID?, victim: PlayerData) {
            val killer = game.playerDatas.filterIsInstance<PlayerData>().find { it.uniqueId == killerId } ?: return
            if (!killer.isEnemyOf(victim)) return
            observers(game).forEach { it.ledger.murder(killer.uniqueId, victim.uniqueId, victim.player.name, game.combatTick) }
        }

        fun onBrightnessBurst(target: EntityData) {
            observers(target.game).forEach { referee ->
                if ((referee.indictments[target.entity.uniqueId] ?: 0L) > referee.game.combatTick) {
                    AbilityExecution.with(referee.abilityScope) { target.damage(5.0, DamageType.True, referee.playerData, bypassShield = true) }
                }
            }
        }

        fun hasActiveTrial(id: UUID): Boolean = activeTrials.containsKey(id)
        fun blocksDamage(attacker: UUID, target: UUID): Boolean = activeTrials.values.any { referee ->
            referee.trial?.session?.participants?.let { attacker in it || target in it } == true
        }
        fun handleChatInput(player: Player, input: String) {
            val referee = activeTrials[player.uniqueId] ?: return
            val plea = when (input.trim()) { "인정", "1" -> Plea.CONFESS; "정당방위", "2" -> Plea.SELF_DEFENSE; "부인", "3" -> Plea.DENY; else -> null }
            if (plea == null) player.sendMiniMessage("<yellow>인정 / 정당방위 / 부인 중 하나를 입력하세요.")
            else referee.trial?.choose(plea)
        }
        fun clearSessions(ids: Collection<UUID>) {
            activeTrials.values.toSet().filter { it.playerData.uniqueId in ids || it.trial?.defendant?.uniqueId in ids }
                .forEach { it.trial?.session?.close() }
        }
    }
    override val classId = "referee"
    override val name = "<gray>심판관"
    override val rank = Rank.SPECIAL
    override val classItemMaterial = Material.MACE
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        DomainSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class Weapon : BaseWeapon() {
        override val name = "<gray>법봉"

        override val description = listOf(
            "<gray>2번째 기본 공격마다 {keyword:Brightness}를 1 부여한다.",
            "<gray>죄가 기록된 대상을 공격하면 피해량이 1 증가한다."
        )

        override val material = Material.IRON_SWORD
    }


    private inner class RedSkill : Skill() {
        override val definitionId = "referee/red-skill"
        override val name = "<bold>강타"

        override val description = listOf(
            "<gray>4칸 내의 바라보는 적에게 점프하며 내려찍어 6의 피해를 입힌다.",
            "<gray>죄가 기록된 대상을 공격하면 추가로 {keyword:Brightness}를 2 부여한다."
        )

        override val cooldown = REFEREE_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean = smite()
    }

    private inner class OrangeSkill : Skill() {
        override val definitionId = "referee/orange-skill"
        override val name = "<bold>기소"

        override val description = listOf(
            "<gray>10칸 내의 바라보는 적을 기소한다.",
            "<gray>기소된 적은 10초간 이동 속도가 20% 감소하고, 받는 피해가 20% 증가한다.",
            "<gray>죄가 기록된 대상이라면 10초간 {keyword:Brightness}가 최대 수치에 도달할 때 추가로 5의 {keyword:TrueDamage}를 입는다."
        )

        override val cooldown = REFEREE_ORANGE_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean = indict()
    }

    private inner class DomainSkill : Skill() {
        override val definitionId = "referee/domain-skill"
        override val name = "<bold>「영역 전개」-「대천칭」"

        override val description = listOf(
            "<gray>10칸 내의 바라보는 적이 있을 때 발동할 수 있다.",
            "<gray>바라보는 플레이어에게 기록된 죄가 있을 때에만 사용할 수 있다.",
            "",
            "<gray>20초간 20칸 너비의 {keyword:Area}을 전개한다.",
            "<gray>{keyword:Area} 내부에서 자신을 포함한 모든 플레이어는 {keyword:Disability}, {keyword:Invincibility} 상태가 된다.",
            "",
            "<gray>바라보는 플레이어는 피고인, 자신은 심판자가 되어 재판을 시작한다.",
            "<gray>피고인에게 기록된 죄 중, 가장 무거운 죄가 지정된다.",
            "<gray>피고인은 {keyword:Area}이 종료되기 전까지 죄를 인정하거나, 반론해야한다.",
            "<gray>반론 도중 위증이 섞인 주장을 했다면 위증의 죄를 물어 죄가 증가한다.",
            "<gray>죄를 인정했다면 죄가 경감된다.",
            "<gray>성공적으로 반론했다면 무죄가 된다.",
            "<gray>영역이 종료될 때, 판결의 결과가 적용된다.",
            "",
            "<gray>영역 종료 후, 자신은 기록된 죄를 모두 잃는다.",
            "<gray>또한 20초간 적에게 {keyword:Brightness}를 부여할 수 없다."
        )

        override val cooldown = REFEREE_DOMAIN_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean = openCourt()
    }


    private class Passive : BasePassive() {
        override val name = "<bold>원죄"

        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>모든 플레이어의 폭행(10초 내 누적 피해 8 이상)과 살인을 기록한다.",
            "<gray>교전의 선제 공격자를 증거로 남겨 정당방위를 판별한다."
        )
    }
}
