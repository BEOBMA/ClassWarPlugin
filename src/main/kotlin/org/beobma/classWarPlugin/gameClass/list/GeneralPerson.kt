package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.MoveSpeedIncrease
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val GENERAL_PERSON_NEARBY_RANGE_SQUARED = 100.0
private const val GENERAL_PERSON_MOVE_SPEED_BONUS_PERCENT = 20

class GeneralPerson : GameClass() {
    override val name = "<gray>일반인"
    override val rank = Rank.C
    override val classItemMaterial = Material.WHITE_BANNER
    override var skills: List<Skill> = listOf()
    override var passives: List<BasePassive> = listOf(Passive())

    private class Passive : BasePassive(), GameStatusHandler {
        override val name = "<bold>일반인"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>전투와 관련 없는 일반인이므로, 능력이 없다.",
            "<gray>다른 플레이어가 10칸 이내에 있다면 <gold><bold>이동 속도가 20% 증가</bold><gray>한다."
        )

        private var nearbySpeed: MoveSpeedIncrease? = null

        override fun onBattleStart() = refreshNearbyPlayer()

        override fun onGameTimePasses() = refreshNearbyPlayer()

        private fun refreshNearbyPlayer() {
            val hasNearbyPlayer = game.playerDatas.asSequence()
                .filterIsInstance<PlayerData>()
                .filter { it != playerData && !it.entityStatus.isDead && it.player.isOnline }
                .any {
                    it.player.world == player.world &&
                        it.player.location.distanceSquared(player.location) <= GENERAL_PERSON_NEARBY_RANGE_SQUARED
                }

            if (hasNearbyPlayer &&
                (nearbySpeed == null || nearbySpeed?.power != GENERAL_PERSON_MOVE_SPEED_BONUS_PERCENT)
            ) {
                nearbySpeed?.remove()
                nearbySpeed = playerData.addStatus(MoveSpeedIncrease(), playerData) as MoveSpeedIncrease
                nearbySpeed?.applyStatus(powerSet = GENERAL_PERSON_MOVE_SPEED_BONUS_PERCENT)
                particles.spawn(player, Particle.HAPPY_VILLAGER, count = 9, spread = 0.45)
                sounds.play(player, Sound.ENTITY_VILLAGER_YES, volume = 0.6f, pitch = 1.35f)
            } else if (!hasNearbyPlayer && nearbySpeed != null) {
                nearbySpeed?.remove()
                nearbySpeed = null
                sounds.play(player, Sound.ENTITY_VILLAGER_NO, volume = 0.35f, pitch = 1.6f)
            }
        }
    }
}
