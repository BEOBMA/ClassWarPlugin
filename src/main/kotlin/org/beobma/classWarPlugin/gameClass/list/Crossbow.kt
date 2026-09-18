package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.*
import org.beobma.classWarPlugin.gameClass.handler.*
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.util.*
import org.bukkit.Location
import org.beobma.classWarPlugin.effect.CombatVisuals
import org.beobma.classWarPlugin.effect.EmbeddedWeaponDisplay
import org.bukkit.util.Vector
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.inventory.meta.CrossbowMeta
import java.util.UUID
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val CROSSBOW_RED_SKILL_COOLDOWN_SECONDS = 4
private const val CROSSBOW_BLUE_SKILL_COOLDOWN_SECONDS = 20

class Crossbow : GameClass(), GameStatusHandler, OnHitHandler, ConfirmedHitHandler {
    override val classId = "crossbow"
    override val name = "<gray>석궁"
    override val rank = Rank.A
    override val classItemMaterial = Material.CROSSBOW
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
    )

    override var passives: List<BasePassive> = listOf()
    override val extraItemMaterials: List<ItemStack> get() = listOf(ItemStack(Material.ARROW, 32))

    private data class Bolt(val marker: EmbeddedWeaponDisplay, val expires: Long) {
        val location: Location get() = marker.location
    }
    private val bolts = ArrayDeque<Bolt>()
    private var recalling = false
    private var nextReloadSound = 0L
    override fun onBattleStart() {
        bolts.forEach { it.marker.close() }
        bolts.clear()
        object : AbilityRunnable(abilityScope) {
            var frames = 0
            override fun run() {
                frames++
                bolts.removeAll {
                    (it.expires <= game.combatTick || !it.marker.isSupported).also { remove -> if (remove) it.marker.close() }
                }
                bolts.filter { it.location.world == player.world }.forEach {
                    if (frames % 2 == 0) particles.spawn(it.location, Particle.ELECTRIC_SPARK)
                    if (frames % 5 == 0) {
                        val axis = it.location.toVector().subtract(player.eyeLocation.toVector())
                        CombatVisuals.ring(it.location, axis, 0.25 + 0.05 * kotlin.math.sin(frames * 0.3), CombatVisuals.CYAN, 12)
                        particles.spawn(it.location, Particle.END_ROD)
                    }
                }
            }
            override fun onCancel() { bolts.forEach { it.marker.close() }; bolts.clear() }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 2L)
    }
    override fun onGameTimePasses() {}
    override fun onAttackHit(context: DamageContext) {
        if (!game.mode.isGrowth && context.path == DamagePath.RANGED_ATTACK && context.weaponClassId == classId) context.capDamage(4.0)
    }
    override fun onConfirmedHit(context: DamageContext) {
        if (recalling && context.path == DamagePath.SKILL) {
            particles.spawn(context.target.entity, Particle.ELECTRIC_SPARK, count = 8, spread = 0.2, speed = 0.05)
            reload()
            return
        }
        if (context.path != DamagePath.RANGED_ATTACK || context.weaponClassId != classId) return
        val target = context.target.entity
        val direction = target.location.toVector().subtract(player.location.toVector()).setY(0.0)
        if (direction.lengthSquared() < 0.001) direction.copy(player.location.direction.setY(0.0))
        if (direction.lengthSquared() < 0.001) return
        val requested = target.location.add(direction.normalize().multiply(0.8))
        val marker = EmbeddedWeaponDisplay.spawn(abilityScope, requested, Material.ARROW, 0.8f) ?: return
        val location = marker.location
        while (bolts.size >= growthCount("bolts", 3)) bolts.removeFirst().marker.close()
        bolts.addLast(Bolt(marker, game.combatTick + 80))
        CombatVisuals.pulse(abilityScope, location, direction, 0.55, CombatVisuals.CYAN)
        particles.spawn(location, Particle.CRIT, count = 12, spread = 0.2, speed = 0.08)
        particles.circle(location, Particle.ELECTRIC_SPARK, 0.35, 12)
        sounds.play(location, Sound.ITEM_TRIDENT_HIT, volume = 0.55f, pitch = 1.7f)
    }

    private fun reload() {
        player.inventory.contents.filterNotNull().firstOrNull {
            it.type == Material.CROSSBOW && getWeaponClassId(it) == classId
        }?.let { item ->
            val meta = item.itemMeta as CrossbowMeta
            meta.setChargedProjectiles(listOf(ItemStack(Material.ARROW)))
            item.itemMeta = meta
            if (game.combatTick >= nextReloadSound) {
                nextReloadSound = game.combatTick + 6
                sounds.playTo(player, Sound.ITEM_CROSSBOW_LOADING_END, volume = 0.7f, pitch = 1.4f)
                particles.spawn(player.eyeLocation, Particle.ELECTRIC_SPARK, count = 6, spread = 0.2)
            }
        }
    }

    private class Weapon : BaseWeapon() {
        override val name = "<gray>석궁"
        override val description = listOf(
            "<gray>공격 적중 시 적중한 적 뒤쪽 지면에 박힌 볼트를 남긴다. (최대 {g:feature/bolts:3}개)",
            "<gray>박힌 볼트는 다른 스킬로 활용할 수 있으며, 4초가 지나면 제거된다.",
            "<gray>클래식 모드에서는 석궁의 최대 피해량이 4로 제한된다."
        )
        override val material = Material.CROSSBOW
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "crossbow/red-skill"
        override val name = "<bold>회수"
        override val description = listOf(
            "<gray>모든 박힌 볼트를 자신의 위치로 끌어와 회수한다.",
            "<gray>끌어당겨지는 박힌 볼트에 닿은 적은 {g:damage:1}의 {keyword:TrueDamage}를 입는다.",
            "<gray>박힌 볼트가 하나 이상의 적에게 적중했다면 석궁이 즉시 재장전된다."
        )
        override val cooldown = CROSSBOW_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            val recalled = bolts.filter { it.expires > game.combatTick && it.location.world == player.world }
            if (recalled.isEmpty()) return false
            bolts.removeAll(recalled.toSet())
            sounds.play(player, Sound.ITEM_TRIDENT_RETURN, volume = 0.8f, pitch = 1.35f)
            particles.circle(player.location.add(0.0, 0.8, 0.0), Particle.ELECTRIC_SPARK, 0.7, 20)
            recalled.forEach { bolt ->
                val current = bolt.location.clone()
                particles.circle(current, Particle.END_ROD, 0.4, 12)
                val hit = mutableSetOf<UUID>()
                object : AbilityRunnable(abilityScope) {
                    var ticks = 0
                    override fun run() {
                        if (++ticks > 80 || current.world != player.world) { cancel(); return }
                        val end = player.eyeLocation
                        val delta = end.toVector().subtract(current.toVector())
                        val next = if (delta.lengthSquared() <= 2.25) end else current.clone().add(delta.normalize().multiply(1.5))
                        Targeting.select(playerData, TargetType.Enemy).filter {
                            it.entity.uniqueId !in hit && HitboxUtil.intersectsSegment(it.entity.boundingBox, current.toVector(), next.toVector(), 0.35)
                        }.forEach {
                            hit += it.entity.uniqueId
                            recalling = true
                            try { it.damage(1.0, DamageType.True, playerData) } finally { recalling = false }
                        }
                        CombatVisuals.tracer(current, next, CombatVisuals.CYAN)
                        bolt.marker.move(next, next.toVector().subtract(current.toVector()))
                        particles.line(current, next, Particle.CRIT, 0.3)
                        if (ticks % 2 == 0) particles.spawn(next, Particle.ELECTRIC_SPARK, count = 2, spread = 0.1)
                        current.x = next.x; current.y = next.y; current.z = next.z
                        if (current.distanceSquared(end) < 0.1) {
                            particles.spawn(end, Particle.END_ROD, count = 4, spread = 0.12)
                            CombatVisuals.ring(end.clone().add(end.direction.multiply(0.45)), end.direction, 0.25, CombatVisuals.CYAN, 12)
                            cancel()
                        }
                    }
                    override fun onCancel() { bolt.marker.close() }
                }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L)
            }
            return true
        }
    }

    private inner class OrangeSkill : Skill(), org.beobma.classWarPlugin.skill.MovementSkill {
        override val definitionId = "crossbow/orange-skill"
        override val name = "<bold>기동"
        override val description = listOf(
            "<gray>바라보는 방향에 위치한 박힌 볼트로 빠르게 이동한다.",
            "<gray>닿은 적에게 {g:damage:2}의 피해를 입힌다."
        )
        override val cooldown = CROSSBOW_BLUE_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            val eye = player.eyeLocation
            val bolt = bolts.filter { it.expires > game.combatTick && it.location.world == player.world }
                .filter {
                    val delta = it.location.toVector().subtract(eye.toVector())
                    delta.lengthSquared() > 0.01 && eye.direction.dot(delta.normalize()) > 0.9
                }.minByOrNull { it.location.distanceSquared(eye) } ?: return false
            val destination = bolt.location.clone()
            CombatVisuals.pulse(abilityScope, player.location.add(0.0, 0.12, 0.0), Vector(0.0, 1.0, 0.0), 1.0, CombatVisuals.CYAN)
            sounds.play(player, Sound.ENTITY_BREEZE_JUMP, volume = 0.7f, pitch = 1.4f)
            particles.circle(player.location.add(0.0, 0.15, 0.0), Particle.CLOUD, 0.65, 16)
            val hits = mutableSetOf<UUID>()
            object : AbilityRunnable(abilityScope) {
                var ticks = 0
                override fun run() {
                    if (++ticks > 40 || destination.world != player.world || !playerStatus.canMove) { cancel(); return }
                    val start = player.location
                    val delta = destination.toVector().subtract(start.toVector())
                    if (delta.lengthSquared() < 0.4) {
                        CombatVisuals.pulse(abilityScope, player.location.add(0.0, 0.2, 0.0), Vector(0.0, 1.0, 0.0), 1.1, CombatVisuals.CYAN)
                        particles.circle(player.location.add(0.0, 0.15, 0.0), Particle.ELECTRIC_SPARK, 0.8, 20)
                        sounds.play(player, Sound.ITEM_TRIDENT_RETURN, volume = 0.5f, pitch = 1.8f)
                        cancel(); return
                    }
                    val next = start.clone().add(delta.normalize().multiply(minOf(1.2, delta.length())))
                    if (!next.block.isPassable || !next.clone().add(0.0, 1.0, 0.0).block.isPassable) { cancel(); return }
                    if (!player.teleport(next)) { cancel(); return }
                    player.fallDistance = 0f
                    CombatVisuals.tracer(start.clone().add(0.0, 0.65, 0.0), next.clone().add(0.0, 0.65, 0.0), CombatVisuals.CYAN)
                    if (ticks % 4 == 0) CombatVisuals.ring(next.clone().add(0.0, 0.65, 0.0), delta, 0.45, CombatVisuals.SILVER, 16)
                    if (ticks % 3 == 0) particles.spawn(start, Particle.CLOUD, count = 3, spread = 0.15)
                    Targeting.select(playerData, TargetType.Enemy).filter {
                        it.entity.uniqueId !in hits && HitboxUtil.intersectsSegment(it.entity.boundingBox, start.toVector(), next.toVector(), 0.8)
                    }.forEach {
                        hits += it.entity.uniqueId; it.damage(2.0, DamageType.Normal, playerData)
                        particles.spawn(it.entity, Particle.SWEEP_ATTACK)
                        sounds.play(it.entity, Sound.ENTITY_PLAYER_ATTACK_SWEEP, volume = 0.5f, pitch = 1.6f)
                    }
                }
            }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L)
            return true
        }
    }
}
