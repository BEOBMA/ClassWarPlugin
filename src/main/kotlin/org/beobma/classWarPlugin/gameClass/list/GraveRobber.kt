package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.MovementInputHandler
import org.beobma.classWarPlugin.gameClass.handler.OnSkillUseHandler
import org.beobma.classWarPlugin.gameClass.handler.SneakInputHandler
import org.beobma.classWarPlugin.gameClass.handler.WeaponInputHandler
import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.beobma.classWarPlugin.manager.PlayerManager.classSet
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInputEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import java.util.IdentityHashMap
import org.beobma.classWarPlugin.status.handler.StatusPlayerMoveHandler
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class GraveRobber : GameClass(), GameStatusHandler, OnSkillUseHandler, EnvironmentalDamageHandler,
    MovementInputHandler, SneakInputHandler, StatusPlayerMoveHandler, WeaponInputHandler {
    override val name = "<gray>도굴꾼"
    override val rank = Rank.B
    override val classItemMaterial = Material.IRON_SHOVEL
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = listOf(Passive())

    private val inheritedClasses: MutableList<GameClass> = mutableListOf()
    private val inheritedClassTypes: MutableSet<Class<out GameClass>> = mutableSetOf()

    override fun onBattleStart() = Unit

    override fun onGameTimePasses() {
        inheritedClasses.filterIsInstance<GameStatusHandler>().forEach(GameStatusHandler::onGameTimePasses)
    }

    override fun onSkillUse(event: PlayerSkillUseEvent) {
        inheritedClasses.filterIsInstance<OnSkillUseHandler>().forEach { it.onSkillUse(event) }
    }

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        inheritedClasses.filterIsInstance<EnvironmentalDamageHandler>().forEach { it.onEnvironmentalDamage(event) }
    }

    override fun onPlayerInput(event: PlayerInputEvent) {
        inheritedClasses.filterIsInstance<MovementInputHandler>().forEach { it.onPlayerInput(event) }
    }

    override fun onPlayerToggleSneak(event: PlayerToggleSneakEvent) {
        inheritedClasses.filterIsInstance<SneakInputHandler>().forEach { it.onPlayerToggleSneak(event) }
    }

    override fun onPlayerMove(event: PlayerMoveEvent, playerData: PlayerData) {
        inheritedClasses.filterIsInstance<StatusPlayerMoveHandler>().forEach { it.onPlayerMove(event, playerData) }
    }

    override fun onWeaponRightClick(event: PlayerInteractEvent) {
        inheritedClasses.filterIsInstance<WeaponInputHandler>().forEach { it.onWeaponRightClick(event) }
    }

    override fun onWeaponSwapHand(event: PlayerSwapHandItemsEvent) {
        inheritedClasses.filterIsInstance<WeaponInputHandler>().forEach { it.onWeaponSwapHand(event) }
    }

    private inner class RedSkill : Skill() {
        override val name = "<bold>도굴"
        override val description = listOf(
            "<gray>다른 플레이어가 사망한 위치에서만 사용할 수 있다.",
            "",
            "<gray>사망한 플레이어를 도굴하여 해당 플레이어의 능력, 패시브를 모두 얻는다."
        )
        override val cooldown = 360

        private var selectedRecord: DeathRecord? = null

        override fun isUseSuccess(): Boolean {
            selectedRecord = recordsFor(game)
                .filter { it.location.world == player.world && it.victimId != player.uniqueId }
                .minByOrNull { it.location.distanceSquared(player.location) }
                ?.takeIf { it.location.distanceSquared(player.location) <= 4.0 }
            if (selectedRecord == null) {
                player.sendMiniMessage("<red><bold>[!] 다른 플레이어가 사망한 위치에서만 사용할 수 있습니다.")
                return false
            }
            return true
        }

        override fun use() {
            val record = selectedRecord ?: return
            selectedRecord = null
            if (!recordsFor(game).remove(record)) return

            val stolenClass = runCatching {
                record.classType.getDeclaredConstructor().newInstance()
            }.getOrElse {
                player.sendMiniMessage("<red><bold>[!] 사망한 플레이어의 클래스를 복원하지 못했습니다.")
                return
            }
            stolenClass.inject(playerData)

            val existingSkillIds = skills.mapTo(mutableSetOf()) { it.id }
            val addedSkills = stolenClass.skills.filter { existingSkillIds.add(it.id) }
            val existingPassiveTypes = passives.mapTo(mutableSetOf()) { it.javaClass.name }
            val addedPassives = stolenClass.passives.filter { existingPassiveTypes.add(it.javaClass.name) }
            skills = skills + addedSkills
            passives = passives + addedPassives

            playerData.classSet(initializeHandlers = false)
            addedPassives.filterIsInstance<GameStatusHandler>().forEach(GameStatusHandler::onBattleStart)
            if (inheritedClassTypes.add(stolenClass.javaClass)) {
                inheritedClasses += stolenClass
                if (stolenClass is GameStatusHandler) stolenClass.onBattleStart()
            }

            particles.spawn(player.location.clone().add(0.0, 1.0, 0.0), Particle.SOUL_FIRE_FLAME, count = 34, spread = 0.8, speed = 0.08)
            particles.spawn(record.location.clone().add(0.0, 0.5, 0.0), Particle.SCULK_SOUL, count = 18, spread = 0.45, speed = 0.05)
            sounds.play(player, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, volume = 0.7f, pitch = 0.65f)
            sounds.play(player, Sound.ENTITY_WITHER_SPAWN, volume = 0.35f, pitch = 1.45f)
            player.sendMiniMessage("<green><bold>[!] ${record.victimName}님의 능력과 패시브를 획득했습니다.")
        }
    }

    private class Passive : BasePassive(), GameStatusHandler {
        override val name = "<bold>도굴꾼의 직감"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>플레이어가 사망한 위치를 가리키는 입자를 볼 수 있다."
        )

        private var soundTick = 0

        override fun onBattleStart() = Unit

        override fun onGameTimePasses() {
            val record = recordsFor(game)
                .filter { it.location.world == player.world && it.victimId != player.uniqueId }
                .minByOrNull { it.location.distanceSquared(player.location) }
                ?: return
            val from = player.eyeLocation.clone()
            val target = record.location.clone().add(0.0, 0.7, 0.0)
            val direction = target.toVector().subtract(from.toVector())
            if (direction.lengthSquared() < 1.0E-8) return
            direction.normalize()
            repeat(8) { index ->
                particles.spawnTo(
                    player,
                    from.clone().add(direction.clone().multiply(0.7 + index * 0.38)),
                    Particle.SOUL_FIRE_FLAME,
                    count = 1,
                )
            }
            particles.spawnTo(player, target, Particle.SCULK_SOUL, count = 4, spread = 0.3, speed = 0.01)
            if (++soundTick % 5 == 0) {
                sounds.playTo(player, Sound.BLOCK_SCULK_SENSOR_CLICKING, volume = 0.28f, pitch = 0.75f)
            }
        }
    }

    companion object {
        private data class DeathRecord(
            val victimId: java.util.UUID,
            val victimName: String,
            val location: Location,
            val classType: Class<out GameClass>,
        )

        private val recordsByGame: IdentityHashMap<Game, MutableList<DeathRecord>> = IdentityHashMap()

        private fun recordsFor(game: Game): MutableList<DeathRecord> =
            recordsByGame.getOrPut(game) { mutableListOf() }

        fun recordDeath(playerData: PlayerData) {
            val fallenClass = playerData.gameClass ?: return
            recordsFor(playerData.initGame) += DeathRecord(
                playerData.uniqueId,
                playerData.player.name,
                playerData.player.location.clone(),
                fallenClass.javaClass,
            )
        }

        fun clearDeathRecords(game: Game) {
            recordsByGame.remove(game)
        }
    }
}
