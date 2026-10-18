package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class Intrusion : GameClass(), org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler,
    org.beobma.classWarPlugin.gameClass.handler.PlayerKillHandler,
    org.beobma.classWarPlugin.gameClass.handler.ConfirmedHitHandler {
    data class Mark(val target: org.beobma.classWarPlugin.entity.player.PlayerData, val highlighted: Boolean)
    private var healReady = 0L
    private val highlighted = mutableMapOf<java.util.UUID, Long>()

    fun mapMarks(): List<Mark> = game.playerDatas.filterIsInstance<org.beobma.classWarPlugin.entity.player.PlayerData>()
        .filter { it.player.isOnline && !it.entityStatus.isDead && playerData.isEnemyOf(it) &&
            org.beobma.classWarPlugin.manager.CombatManager.blocksNaturalRegeneration(it.player) }
        .map { Mark(it, lowHealth(it)) }

    private fun lowHealth(target: org.beobma.classWarPlugin.entity.player.PlayerData): Boolean =
        target.player.health < (target.player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0) * 0.4

    override fun onBattleStart() {
        healReady = 0L
        highlighted.clear()
        org.beobma.classWarPlugin.manager.BattleMapManager.giveTo(playerData, allowTraining = true)
        val speed = playerData.attributeEffects.multiply(abilityScope, org.bukkit.attribute.Attribute.MOVEMENT_SPEED, 1.0)
        var previous = player.location.clone()
        playerData.trackTask(object : org.beobma.classWarPlugin.ability.AbilityRunnable(abilityScope) {
            override fun run() {
                val current = player.location
                val movement = if (previous.world == current.world) current.toVector().subtract(previous.toVector()).setY(0.0)
                    else org.bukkit.util.Vector()
                val marks = mapMarks().filter { it.highlighted }
                val alive = game.playerDatas.filterIsInstance<org.beobma.classWarPlugin.entity.player.PlayerData>()
                    .filter { !it.entityStatus.isDead }.map { it.uniqueId }.toSet()
                val markedIds = marks.map { it.target.uniqueId }.toSet()
                highlighted.keys.removeAll { it in alive && it !in markedIds }
                marks.forEach { highlighted[it.target.uniqueId] = game.combatTick }
                highlighted.entries.removeIf { game.combatTick - it.value > 20 }
                val rushing = movement.lengthSquared() > 0.0001 && marks.any {
                    val destination = it.target.player.location
                    if (destination.world != current.world) false else {
                        val toward = destination.toVector().subtract(current.toVector()).setY(0.0)
                        toward.lengthSquared() > 0.01 && movement.clone().normalize().dot(toward.normalize()) > 0.5
                    }
                }
                speed.setMultiplier(if (rushing) 1.5 else 1.0)
                if (rushing) particles.spawn(current, org.bukkit.Particle.CLOUD, count = 3, spread = 0.15, speed = 0.01)
                previous = current.clone()
            }
        }.runTaskTimer(org.beobma.classWarPlugin.ClassWarPlugin.instance, 1L, 2L))
    }
    override fun onGameTimePasses() = Unit
    override fun onConfirmedHit(context: org.beobma.classWarPlugin.damage.DamageContext) {
        val target = context.target as? org.beobma.classWarPlugin.entity.player.PlayerData ?: return
        if (playerData.isEnemyOf(target) && lowHealth(target) &&
            org.beobma.classWarPlugin.manager.CombatManager.blocksNaturalRegeneration(target.player))
            highlighted[target.uniqueId] = game.combatTick
        else highlighted.remove(target.uniqueId)
    }
    override fun onPlayerKill(victimId: java.util.UUID) {
        val markedAt = highlighted.remove(victimId) ?: return
        if (game.combatTick - markedAt > 20 || game.combatTick < healReady) return
        healReady = game.combatTick + 1000
        val maximum = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
        with(org.beobma.classWarPlugin.manager.PlayerManager) { playerData.heal(maximum * 0.3, org.beobma.classWarPlugin.util.DamageType.Normal, playerData) }
        particles.spawn(player, org.bukkit.Particle.HEART, count = 12, spread = 0.6, speed = 0.02)
        sounds.play(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, volume = 0.65f, pitch = 1.6f)
    }
    override val classId = "intrusion"
    override val name = "<gray>난입"
    override val rank = Rank.B
    override val classItemMaterial = Material.RABBIT_FOOT
    override var skills: List<Skill> = listOf(
    )

    override var passives: List<BasePassive> = listOf(
        Passive(),
        PassiveTwo()
    )

    private class Passive : BasePassive() {
        override val name = "<bold>난입의 본능"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>전투 상태인 적이 있을 때, 해당 적의 위치가 지도에 표시된다.",
            "<gray>만약, 해당 적의 체력이 최대 체력의 40% 미만이라면",
            "<gray>해당 적은 지도에 강조 표기되고, 해당 적이 있는 방향으로 향할 때 <gold><bold>이동 속도가 50% 증가</bold><gray>한다."
        )
    }

    private class PassiveTwo : BasePassive() {
        override val name = "<bold>도파민"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>난입의 본능에 의해 강조 표기된 대상을 직접 처치했다면",
            "<gray>자신은 <green><bold>자신 최대 체력의 30% 만큼 체력을 회복</bold><gray>한다.",
            "<gray>이 효과는 50초의 재사용 대기 시간을 가진다."
        )
    }
}
