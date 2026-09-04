package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ability.AbilityExecution

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.GameManager.gameClassList
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val CONTRACTOR_CONTRACT_COOLDOWN_SECONDS = 40
private const val CONTRACTOR_SUCCESS_DAMAGE = 5.0

class Contractor : GameClass(), org.beobma.classWarPlugin.gameClass.handler.GameEndHandler {
    override fun onGameEnd() = clearSessions(listOf(playerData.uniqueId))
    override val classId = "contractor"
    override val name = "<gray>청부업자"
    override val rank = Rank.B
    override val classItemMaterial = Material.SULFUR_CUBE_BUCKET
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = listOf()

    private val miniMessage = MiniMessage.miniMessage()

    private inner class RedSkill : Skill() {
        override val definitionId = "contractor/red-skill"
        override val name = "<bold>청부"
        override val description = listOf(
            "<gray>사용 시 무작위 플레이어의 직업을 맞출 수 있는 인벤토리가 열린다.",
            "<gray>성공적으로 직업을 맞추면 해당 적의 위치로 즉시 이동하고 5의 피해를 입힌다."
        )
        override val cooldown = CONTRACTOR_CONTRACT_COOLDOWN_SECONDS

        private var pendingTarget: PlayerData? by requestValue { null }

        override fun isUseSuccess(): Boolean {
            pendingTarget = game.playerDatas.filterIsInstance<PlayerData>()
                .filter { it != playerData && it.player.isOnline && !it.entityStatus.isDead && it.gameClasses.isNotEmpty() }
                .randomOrNull(Random)
            if (pendingTarget != null) return true
            player.sendMiniMessage("<red><bold>[!] 청부 대상으로 지정할 생존 적이 없습니다.")
            return false
        }

        override fun use(): Boolean {
            val target = pendingTarget ?: return false
            pendingTarget = null
            openGuessInventory(target)
            return true
        }
    }

    private fun openGuessInventory(target: PlayerData) {
        clearSessions(listOf(player.uniqueId))
        val choices = gameClassList
        val inventorySize = ((choices.size + 8) / 9 * 9).coerceIn(9, 54)
        val inventory = Bukkit.createInventory(
            null,
            inventorySize,
            miniMessage.deserialize("<dark_gray>청부 대상: <white>${target.player.name}"),
        )
        choices.take(inventorySize).forEachIndexed { index, gameClass ->
            inventory.setItem(index, ItemStack(gameClass.classItemMaterial).apply {
                itemMeta = itemMeta.apply {
                    displayName(miniMessage.deserialize(gameClass.name))
                    lore(listOf(miniMessage.deserialize("<gray>이 직업으로 추측합니다.")))
                }
            })
        }

        activeGuesses[player.uniqueId] = GuessSession(
            owner = this,
            targetId = target.uniqueId,
            choices = choices.take(inventorySize).map { it.javaClass },
        )
        PlayerTagManager.addTag(player, GUESS_INVENTORY_TAG)
        player.openInventory(inventory)
        player.sendMiniMessage(
            "<gold><bold>[청부]</bold> <white>${target.player.name}<gray>님의 직업을 선택하세요."
        )
        sounds.play(player, Sound.BLOCK_CHEST_OPEN, volume = 0.75f, pitch = 0.75f)
    }

    private fun resolveGuess(session: GuessSession, slot: Int) {
        if (activeGuesses[player.uniqueId] !== session) return
        val guessedClass = session.choices.getOrNull(slot) ?: return
        val target = game.playerDatas.filterIsInstance<PlayerData>()
            .find { it.uniqueId == session.targetId }

        activeGuesses.remove(player.uniqueId)
        PlayerTagManager.removeTag(player, GUESS_INVENTORY_TAG)
        player.closeInventory()

        if (target == null || !target.player.isOnline || target.entityStatus.isDead || target.gameClasses.isEmpty()) {
            player.sendMiniMessage("<red><bold>[청부 실패]</bold> <gray>대상이 더 이상 유효하지 않습니다.")
            particles.spawn(player, Particle.SMOKE, count = 18, spread = 0.45, speed = 0.04)
            sounds.play(player, Sound.BLOCK_NOTE_BLOCK_BASS, volume = 0.9f, pitch = 0.55f)
            return
        }

        if (target.gameClasses.none { it.javaClass == guessedClass }) {
            player.sendMiniMessage("<red><bold>[청부 실패]</bold> <gray>직업을 잘못 추측했습니다.")
            particles.spawn(player, Particle.SMOKE, count = 22, spread = 0.5, speed = 0.05)
            sounds.play(player, Sound.ENTITY_VILLAGER_NO, volume = 0.8f, pitch = 0.7f)
            return
        }

        val from = player.location.clone().add(0.0, 1.0, 0.0)
        val destination = target.player.location.clone()
        particles.spawn(from, Particle.REVERSE_PORTAL, count = 42, spread = 0.65, speed = 0.12)
        sounds.play(from, Sound.ENTITY_ENDERMAN_TELEPORT, volume = 0.9f, pitch = 0.75f)
        player.teleport(destination)
        val targetCenter = target.entity.boundingBox.center.toLocation(target.entity.world)
        particles.spawn(targetCenter, Particle.PORTAL, count = 52, spread = 0.7, speed = 0.16)
        particles.spawn(targetCenter, Particle.CRIT, count = 18, spread = 0.42, speed = 0.1)
        sounds.play(targetCenter, Sound.ENTITY_PLAYER_ATTACK_CRIT, volume = 1.0f, pitch = 0.72f)
        target.damage(CONTRACTOR_SUCCESS_DAMAGE, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
        player.sendMiniMessage(
            "<green><bold>[청부 성공]</bold> <white>${target.player.name}<gray>님의 직업을 맞혔습니다."
        )
    }

    companion object {
        private const val GUESS_INVENTORY_TAG = "openContractGuessInventory"

        private data class GuessSession(
            val owner: Contractor,
            val targetId: UUID,
            val choices: List<Class<out GameClass>>,
        )

        private val activeGuesses: ConcurrentHashMap<UUID, GuessSession> = ConcurrentHashMap()

        fun isGuessInventoryOpen(player: Player): Boolean =
            PlayerTagManager.hasTag(player, GUESS_INVENTORY_TAG)

        fun handleInventoryClick(player: Player, rawSlot: Int) {
            val session = activeGuesses[player.uniqueId] ?: return
            AbilityExecution.with(session.owner.abilityScope) { session.owner.resolveGuess(session, rawSlot) }
        }

        fun handleInventoryClose(player: Player) {
            if (!PlayerTagManager.hasTag(player, GUESS_INVENTORY_TAG)) return
            activeGuesses.remove(player.uniqueId)
            PlayerTagManager.removeTag(player, GUESS_INVENTORY_TAG)
        }

        fun clearSessions(playerIds: Collection<UUID>) {
            playerIds.forEach { playerId ->
                val hadSession = activeGuesses.remove(playerId) != null
                Bukkit.getPlayer(playerId)?.let { player ->
                    val hadInventoryTag = PlayerTagManager.hasTag(player, GUESS_INVENTORY_TAG)
                    PlayerTagManager.removeTag(player, GUESS_INVENTORY_TAG)
                    if (hadSession || hadInventoryTag) player.closeInventory()
                }
            }
        }
    }
}
