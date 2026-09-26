package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive


class Flashbang : GameClass(), org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler {
    private val cooldowns = mutableMapOf<java.util.UUID,Long>()
    private val flashes = mutableMapOf<java.util.UUID,Pair<org.bukkit.entity.TextDisplay,Long>>()
    private var clock=0L
    override fun onBattleStart() {
        abilityScope.resources.own { flashes.values.forEach { it.first.remove() }; flashes.clear(); cooldowns.clear() }
        object : org.beobma.classWarPlugin.ability.AbilityRunnable(abilityScope) {
            override fun run() {
                clock++
                flashes.toMap().forEach { (id,flash) ->
                    val viewer=org.bukkit.Bukkit.getPlayer(id)
                    if(viewer==null || !viewer.isOnline || viewer.isDead || viewer.world!=player.world || clock>=flash.second) {
                        flash.first.remove(); flashes.remove(id)
                    } else {
                        flash.first.teleport(viewer.eyeLocation.add(viewer.eyeLocation.direction.multiply(0.8)))
                        val remaining=flash.second-clock
                        val alpha=if(remaining>=20) 245 else (remaining*245/20).toInt()
                        flash.first.textOpacity=alpha.toByte()
                        flash.first.backgroundColor=org.bukkit.Color.fromARGB(alpha,255,255,255)
                    }
                }
                if(clock%2!=0L) return
                org.beobma.classWarPlugin.ability.Targeting.select(playerData,org.beobma.classWarPlugin.util.TargetType.Enemy)
                    .mapNotNull { it.entity as? org.bukkit.entity.Player }.forEach { enemy ->
                        if(clock<(cooldowns[enemy.uniqueId] ?: 0)) return@forEach
                        val eye=enemy.eyeLocation
                        val hit=player.boundingBox.clone().expand(0.15).rayTrace(eye.toVector(),eye.direction,24.0) ?: return@forEach
                        val distance=hit.hitPosition.distance(eye.toVector())
                        if(distance>0.01 && eye.world.rayTraceBlocks(eye,eye.direction,distance)!=null) return@forEach
                        cooldowns[enemy.uniqueId]=clock+600
                        val display=enemy.world.spawn(eye.clone().add(eye.direction.multiply(0.8)),org.bukkit.entity.TextDisplay::class.java) {
                            it.isVisibleByDefault=false; it.isPersistent=false
                            it.text(net.kyori.adventure.text.Component.text("████████",net.kyori.adventure.text.format.NamedTextColor.WHITE))
                            it.billboard=org.bukkit.entity.Display.Billboard.CENTER; it.isSeeThrough=true
                            it.brightness=org.bukkit.entity.Display.Brightness(15,15)
                            it.backgroundColor=org.bukkit.Color.fromARGB(245,255,255,255)
                            it.transformation=it.transformation.apply { scale.set(20f); translation.y=-2f }
                        }
                        enemy.showEntity(org.beobma.classWarPlugin.ClassWarPlugin.instance,display)
                        flashes[enemy.uniqueId]=display to (clock+60)
                        enemy.playSound(enemy.location,org.bukkit.Sound.BLOCK_BEACON_ACTIVATE,0.5f,2f)
                        enemy.spawnParticle(org.bukkit.Particle.END_ROD,eye,3,0.15,0.15,0.15,0.0)
                    }
            }
        }.runTaskTimer(org.beobma.classWarPlugin.ClassWarPlugin.instance,1,1)
    }
    override fun onGameTimePasses() = Unit
    override fun onSuspend() { flashes.values.forEach { it.first.remove() }; flashes.clear() }
    override val classId = "flashbang"
    override val name = "<gray>섬광탄"
    override val rank = Rank.C
    override val classItemMaterial = Material.LIGHT
    override var skills: List<Skill> = listOf()

    override var passives: List<BasePassive> = listOf(Passive())


    private class Passive : BasePassive() {
        override val name = "<bold>섬광"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>24칸 내에서 장애물 없이 자신을 바라본 적의 시야를 3초간 밝은 빛으로 가린다. (대상 당 재사용 대기 시간 30초)"
        )
    }
}
