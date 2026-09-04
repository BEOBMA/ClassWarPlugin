package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ability.AbilityExecution

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.effect.ParticleApi
import org.beobma.classWarPlugin.effect.SoundApi
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.MovementInputHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.OnSkillUseHandler
import org.beobma.classWarPlugin.gameClass.handler.SneakInputHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInputEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DEATH_NOTE_NAMING_COOLDOWN_SECONDS = 45
private const val DEATH_NOTE_MARK_DURATION_SECONDS = 30
private const val DEATH_NOTE_ACTION_THRESHOLD = 10

class DeathNote : GameClass(), org.beobma.classWarPlugin.gameClass.handler.GameEndHandler {
    override fun onGameEnd() = clearSessions(listOf(playerData.uniqueId))
    override val classId = "death-note"
    override val name = "<gray>데스노트"
    override val rank = Rank.L
    override val classItemMaterial = Material.BOOK

    private val namingSkill = RedSkill()
    override var skills: List<Skill> = listOf(namingSkill)
    override var passives: List<BasePassive> = listOf()

    private val miniMessage = MiniMessage.miniMessage()

    private inner class RedSkill : Skill() {
        override val definitionId = "death-note/red-skill"
        override val name = "<bold>기명"
        override val description = listOf(
            "<gray>생존한 적 중 한 명을 선택해 데스노트에 적는다.",
            "",
            "<gray>아래 행동 중 하나가 무작위로 죽음의 조건으로 지정된다.",
            "<gray>데스노트에 적힌 적이 30초 동안 지정된 행동을 총 10회 하면 심장마비로 사망한다.",
            "",
            "<gray>무작위로 지정될 수 있는 행동은 다음과 같다.",
            "<gray>  - 이동 입력",
            "<gray>  - 기본 공격",
            "<gray>  - 스킬 사용",
            "<gray>  - 점프",
            "<gray>  - 웅크리기",
        )
        override val cooldown = DEATH_NOTE_NAMING_COOLDOWN_SECONDS

        private var selectableTargets: List<PlayerData> by requestValue { emptyList() }

        override fun isUseSuccess(): Boolean {
            selectableTargets = livingEnemies()
            if (selectableTargets.isNotEmpty()) return true
            player.sendMiniMessage("<red><bold>[!] 데스노트에 적을 생존 적이 없습니다.")
            return false
        }

        override fun use(): Boolean {
            val targets = selectableTargets
            selectableTargets = emptyList()
            openTargetInventory(targets)
            return true
        }
    }

    private fun livingEnemies(): List<PlayerData> = game.playerDatas.asSequence()
        .filterIsInstance<PlayerData>()
        .filter { it != playerData && it.player.isOnline && !it.entityStatus.isDead }
        .sortedBy { it.player.name.lowercase() }
        .toList()

    private fun openTargetInventory(targets: List<PlayerData>) {
        clearSessions(listOf(player.uniqueId), removeMarks = false)
        val visibleTargets = targets.take(54)
        val inventorySize = ((visibleTargets.size + 8) / 9 * 9).coerceIn(9, 54)
        val inventory = Bukkit.createInventory(
            null,
            inventorySize,
            miniMessage.deserialize("<dark_red><bold>데스노트에 적을 이름"),
        )
        visibleTargets.forEachIndexed { slot, target ->
            inventory.setItem(slot, ItemStack(Material.PLAYER_HEAD).apply {
                itemMeta = (itemMeta as SkullMeta).apply {
                    owningPlayer = target.player
                    displayName(miniMessage.deserialize("<red>${target.player.name}"))
                    lore(
                        listOf(
                            miniMessage.deserialize("<gray>클릭하여 이 이름을 기록합니다."),
                            miniMessage.deserialize("<dark_red>기명 후에는 되돌릴 수 없습니다."),
                        )
                    )
                }
            })
        }

        activeSelections[player.uniqueId] = SelectionSession(
            owner = this,
            targetIds = visibleTargets.map(PlayerData::uniqueId),
        )
        PlayerTagManager.addTag(player, SELECTION_INVENTORY_TAG)
        player.openInventory(inventory)
        player.sendMiniMessage("<dark_red><bold>[데스노트]</bold> <gray>기록할 이름을 선택하세요.")
        sounds.play(player, Sound.ITEM_BOOK_PAGE_TURN, volume = 0.9f, pitch = 0.58f)
    }

    private fun resolveSelection(session: SelectionSession, slot: Int) {
        if (activeSelections[player.uniqueId] !== session) return
        val targetId = session.targetIds.getOrNull(slot) ?: return
        activeSelections.remove(player.uniqueId)
        PlayerTagManager.removeTag(player, SELECTION_INVENTORY_TAG)
        player.closeInventory()

        val target = livingEnemies().find { it.uniqueId == targetId }
        if (target == null) {
            player.sendMiniMessage("<red><bold>[기명 실패]</bold> <gray>대상이 더 이상 유효하지 않습니다.")
            sounds.play(player, Sound.BLOCK_NOTE_BLOCK_BASS, volume = 0.9f, pitch = 0.48f)
            return
        }

        activeMarks.remove(player.uniqueId)?.remove()
        val mark = DeathNoteMarkStatus()
        target.addStatus(mark, playerData)
        mark.applyStatus(duration = MARK_DURATION_SECONDS, powerSet = 1)
        activeMarks[player.uniqueId] = mark

        player.sendMiniMessage(
            "<dark_red><bold>[기명 완료]</bold> <white>${target.player.name}<gray>님의 이름을 기록했습니다. " +
                "<dark_red>죽음의 조건: <white>${mark.requiredActionName}"
        )
        ParticleApi.spawn(target.player, Particle.SQUID_INK, count = 45, spread = 0.72, speed = 0.08)
        ParticleApi.spawn(target.player, Particle.WITCH, count = 32, spread = 0.58, speed = 0.06)
        SoundApi.play(target.player, Sound.ENTITY_WITHER_SPAWN, volume = 0.42f, pitch = 0.48f)
        SoundApi.playTo(player, Sound.ITEM_BOOK_PAGE_TURN, volume = 1.0f, pitch = 0.4f)
    }

    private class DeathNoteMarkStatus : StatusAbnormality(), MovementInputHandler, SneakInputHandler,
        OnHitHandler, OnSkillUseHandler {
        private enum class RequiredAction(val displayName: String) {
            MOVEMENT("이동 입력"),
            JUMP("점프"),
            SNEAK("웅크리기"),
            ATTACK("기본 또는 원거리 공격"),
            SKILL("스킬 사용"),
        }

        private val requiredAction = RequiredAction.entries.random()
        val requiredActionName: String
            get() = requiredAction.displayName

        override val name = "<dark_red><bold>데스노트</bold><gray>"
        override val description = listOf(
            "<gray>지정된 행동을 할 때마다 죽음의 진행도가 오른다.",
            "<gray>진행도가 10에 도달하면 심장마비로 사망한다.",
        )
        override val canRemove = true
        override var power = 1
        override var maxPower: Int? = 1
        override val showPower = false
        override val showMaxPower = false
        override val showInActionBar = false
        override var duration: Int? = null

        private var actions = 0
        private var movementHeld = false
        private var jumpHeld = false
        private var executed = false

        override fun onPlayerInput(event: PlayerInputEvent) {
            if (executed || power <= 0) return
            val input = event.input
            val hasMovement = input.isForward || input.isBackward || input.isLeft || input.isRight
            if (requiredAction == RequiredAction.MOVEMENT && hasMovement && !movementHeld) {
                recordAction()
            }
            if (requiredAction == RequiredAction.JUMP && input.isJump && !jumpHeld) {
                recordAction()
            }
            movementHeld = hasMovement
            jumpHeld = input.isJump
        }

        override fun onPlayerToggleSneak(event: PlayerToggleSneakEvent) {
            if (requiredAction == RequiredAction.SNEAK && event.isSneaking) {
                recordAction()
            }
        }

        override fun onAttackHit(context: DamageContext) {
            if (requiredAction == RequiredAction.ATTACK) recordAction()
        }

        override fun onSkillUse(event: PlayerSkillUseEvent) {
            if (requiredAction == RequiredAction.SKILL) recordAction()
        }

        private fun recordAction() {
            if (executed || power <= 0 || entityStatus.isDead) return
            actions++

            val target = entityData as? PlayerData ?: return
            val progress = actions.toDouble() / DEATH_ACTION_THRESHOLD
            val pitch = (0.62 + progress * 0.65).toFloat()
            val center = target.player.boundingBox.center.toLocation(target.player.world)
            SoundApi.playTo(target.player, Sound.ENTITY_WARDEN_HEARTBEAT, volume = 0.8f, pitch = pitch)
            SoundApi.play(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, volume = 0.55f, pitch = pitch)
            ParticleApi.spawn(
                center,
                Particle.DUST,
                Particle.DustOptions(Color.fromRGB(125 + (110 * progress).toInt(), 0, 0), 1.45f),
                org.beobma.classWarPlugin.effect.ParticleOptions.spread(12, 0.34, 0.025),
            )
            ParticleApi.spawn(center, Particle.SCULK_SOUL, count = 9, spread = 0.42, speed = 0.045)
            ParticleApi.spawn(center, Particle.SQUID_INK, count = 7, spread = 0.3, speed = 0.035)

            if (actions >= DEATH_ACTION_THRESHOLD) triggerHeartAttack(target)
        }

        private fun triggerHeartAttack(target: PlayerData) {
            if (executed) return
            executed = true
            val center = target.player.boundingBox.center.toLocation(target.player.world)
            ParticleApi.spawn(center, Particle.DAMAGE_INDICATOR, count = 45, spread = 0.5, speed = 0.2)
            ParticleApi.spawn(center, Particle.SQUID_INK, count = 80, spread = 0.78, speed = 0.16)
            ParticleApi.spawn(center, Particle.FLASH, count = 1)
            SoundApi.play(target.player, Sound.ENTITY_WARDEN_HEARTBEAT, volume = 1.5f, pitch = 0.45f)
            SoundApi.play(target.player, Sound.ENTITY_WITHER_DEATH, volume = 0.65f, pitch = 0.55f)
            casterData.player.takeIf(Player::isOnline)?.sendMiniMessage(
                "<dark_red><bold>[데스노트]</bold> <white>${target.player.name}<gray>님의 죽음이 완성되었습니다."
            )
            target.damage(
                target.player.health + 2048.0,
                DamageType.True,
                casterData,
                bypassShield = true,
                damagePath = DamagePath.SKILL,
            )
            remove()
        }

        override fun onRemoveStatusAbnormality() {
            activeMarks.remove(casterData.uniqueId, this)
            super.onRemoveStatusAbnormality()
        }
    }

    companion object {
        private const val SELECTION_INVENTORY_TAG = "openDeathNoteSelectionInventory"
        private const val MARK_DURATION_SECONDS = DEATH_NOTE_MARK_DURATION_SECONDS
        private const val DEATH_ACTION_THRESHOLD = DEATH_NOTE_ACTION_THRESHOLD

        private data class SelectionSession(
            val owner: DeathNote,
            val targetIds: List<UUID>,
        )

        private val activeSelections = ConcurrentHashMap<UUID, SelectionSession>()
        private val activeMarks = ConcurrentHashMap<UUID, DeathNoteMarkStatus>()

        fun isSelectionInventoryOpen(player: Player): Boolean =
            PlayerTagManager.hasTag(player, SELECTION_INVENTORY_TAG)

        fun handleInventoryClick(player: Player, rawSlot: Int) {
            val session = activeSelections[player.uniqueId] ?: return
            AbilityExecution.with(session.owner.abilityScope) { session.owner.resolveSelection(session, rawSlot) }
        }

        fun handleInventoryClose(player: Player) {
            if (!PlayerTagManager.hasTag(player, SELECTION_INVENTORY_TAG)) return
            activeSelections.remove(player.uniqueId)
            PlayerTagManager.removeTag(player, SELECTION_INVENTORY_TAG)
        }

        fun clearSessions(playerIds: Collection<UUID>, removeMarks: Boolean = true) {
            playerIds.forEach { playerId ->
                val hadSession = activeSelections.remove(playerId) != null
                if (removeMarks) activeMarks.remove(playerId)?.remove()
                Bukkit.getPlayer(playerId)?.let { player ->
                    val hadTag = PlayerTagManager.hasTag(player, SELECTION_INVENTORY_TAG)
                    PlayerTagManager.removeTag(player, SELECTION_INVENTORY_TAG)
                    if (hadSession || hadTag) player.closeInventory()
                }
            }
        }
    }
}
