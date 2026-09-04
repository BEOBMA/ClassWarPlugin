package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ability.AbilityCatalog

import org.beobma.classWarPlugin.ability.AbilityTree

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.PlayerManager.classSet
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import java.util.IdentityHashMap
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val GRAVE_ROBBER_ROB_COOLDOWN_SECONDS = 360
private const val GRAVE_ROBBER_INTERACTION_RANGE_SQUARED = 4.0

class GraveRobber : GameClass() {
    override val classId = "grave-robber"
    override val name = "<gray>도굴꾼"
    override val rank = Rank.B
    override val classItemMaterial = Material.IRON_SHOVEL
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = listOf(Passive())

    private val inheritedClasses: MutableList<GameClass> = mutableListOf()
    private val inheritedClassIds = mutableSetOf<String>()

    override val childAbilities: List<GameClass> get() = inheritedClasses

    private inner class RedSkill : Skill() {
        override val definitionId = "grave-robber/red-skill"
        override val name = "<bold>도굴"
        override val description = listOf(
            "<gray>다른 플레이어가 사망한 위치에서만 사용할 수 있다.",
            "",
            "<gray>사망한 플레이어를 도굴하여 해당 플레이어의 능력, 패시브를 모두 얻는다."
        )
        override val cooldown = GRAVE_ROBBER_ROB_COOLDOWN_SECONDS

        private var selectedRecord: DeathRecord? by requestValue { null }

        override fun isUseSuccess(): Boolean {
            selectedRecord = recordsFor(game)
                .filter { it.location.world == player.world && it.victimId != player.uniqueId }
                .minByOrNull { it.location.distanceSquared(player.location) }
                ?.takeIf { it.location.distanceSquared(player.location) <= GRAVE_ROBBER_INTERACTION_RANGE_SQUARED }
            if (selectedRecord == null) {
                player.sendMiniMessage("<red><bold>[!] 다른 플레이어가 사망한 위치에서만 사용할 수 있습니다.")
                return false
            }
            return true
        }

        override fun use(): Boolean {
            val record = selectedRecord ?: return false
            selectedRecord = null
            if (record !in recordsFor(game)) return false

            val stolenClasses = runCatching {
                record.classIds.map { AbilityCatalog.create(it) }
                    .filter { it.classId !in inheritedClassIds && it.classId != classId }
            }.getOrElse {
                player.sendMiniMessage("<red><bold>[!] 사망한 플레이어의 클래스를 복원하지 못했습니다.")
                return false
            }

            if (stolenClasses.isEmpty()) return false
            if (!recordsFor(game).remove(record)) return false
            val existingSkillIds = skills.mapTo(mutableSetOf()) { it.definitionId }
            val addedSkills = stolenClasses.flatMap { it.skills }.filter { existingSkillIds.add(it.definitionId) }
            val existingPassiveTypes = passives.mapTo(mutableSetOf()) { it.javaClass.name }
            val addedPassives = stolenClasses.flatMap { it.passives }
                .filter { existingPassiveTypes.add(it.javaClass.name) }
            skills = skills + addedSkills
            passives = passives + addedPassives

            stolenClasses.forEach { stolenClass ->
                if (inheritedClassIds.add(stolenClass.classId)) inheritedClasses += stolenClass
            }
            playerData.classSet(initializeHandlers = false)
            AbilityTree.start(inheritedClasses)

            particles.spawn(player.location.clone().add(0.0, 1.0, 0.0), Particle.SOUL_FIRE_FLAME, count = 34, spread = 0.8, speed = 0.08)
            particles.spawn(record.location.clone().add(0.0, 0.5, 0.0), Particle.SCULK_SOUL, count = 18, spread = 0.45, speed = 0.05)
            sounds.play(player, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, volume = 0.7f, pitch = 0.65f)
            sounds.play(player, Sound.ENTITY_WITHER_SPAWN, volume = 0.35f, pitch = 1.45f)
            player.sendMiniMessage("<green><bold>[!] ${record.victimName}님의 능력과 패시브를 획득했습니다.")
            return true
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
            val classIds: List<String>,
        )

        private val recordsByGame: IdentityHashMap<Game, MutableList<DeathRecord>> = IdentityHashMap()

        private fun recordsFor(game: Game): MutableList<DeathRecord> =
            recordsByGame.getOrPut(game) { mutableListOf() }

        fun recordDeath(playerData: PlayerData) {
            fun acquiredIds(ability: GameClass): List<String> = if (ability is GraveRobber) {
                ability.childAbilities.flatMap(::acquiredIds)
            } else listOf(ability.classId)
            val fallenClasses = playerData.gameClasses.flatMap(::acquiredIds).distinct()
            if (fallenClasses.isEmpty()) return
            recordsFor(playerData.initGame) += DeathRecord(
                playerData.uniqueId,
                playerData.player.name,
                playerData.player.location.clone(),
                fallenClasses,
            )
        }

        fun clearDeathRecords(game: Game) {
            recordsByGame.remove(game)
        }
    }
}
