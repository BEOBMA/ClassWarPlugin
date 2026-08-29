package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Radiation
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound

class TrainCarriage : GameClass(), GameStatusHandler, WhenHitHandler, GameEndHandler, PlayerDeathHandler {
    override val name = "<gray>기차화통"
    override val rank = Rank.C
    override val classItemMaterial = Material.FIREWORK_ROCKET
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private var radiation: Radiation? = null

    override fun onBattleStart() {
        radiation?.remove()
        radiation = playerData.addStatus(Radiation(), playerData) as Radiation
        radiation?.applyStatus(powerSet = 1)
        particles.spawn(player, Particle.ELECTRIC_SPARK, count = 20, spread = 0.65, speed = 0.07)
        sounds.play(player, Sound.ENTITY_MINECART_RIDING, volume = 0.65f, pitch = 0.75f)
    }

    override fun onGameTimePasses() {
        if (radiation?.power != 1) radiation?.applyStatus(powerSet = 1)
        particles.spawn(player, Particle.WAX_ON, count = 3, spread = 0.45, speed = 0.01)
    }

    override fun whenHit(context: DamageContext) = context.addDamageTakenMultiplier(0.8)
    override fun onGameEnd() = clear()
    override fun onPlayerDeath() = clear()

    private fun clear() {
        radiation?.remove()
        radiation = null
    }

    private class Passive : BasePassive() {
        override val name = "<bold>기차화통"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>자신의 위치가 모든 플레이어의 지도에 드러나며, 영구적인 {keyword:Radiation} 상태가 된다.",
            "<gray>받는 피해가 20% 감소한다."
        )
    }
}
