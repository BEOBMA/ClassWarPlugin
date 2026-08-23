package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.GameRule
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

class Fear : GameClass(), GameStatusHandler, GameEndHandler {
    override val name = "<gray>공포"
    override val rank = Rank.SPECIAL
    override val classItemMaterial = Material.CRYING_OBSIDIAN
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(CrawlingFear(), Despair())
    private val sanity = mutableMapOf<UUID, Double>()
    private val lastHealth = mutableMapOf<UUID, Double>()
    private val knownDead = mutableSetOf<UUID>()
    private val shadowCooldown = mutableMapOf<UUID, Int>()
    private var previousTime = 0L
    private var previousDaylightCycle = true
    private var elapsedSeconds = 0
    private var nightLocked = false

    override fun onBattleStart() {
        val world = player.world
        previousTime = world.time
        previousDaylightCycle = world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)
        world.time = 18000L
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        nightLocked = true
        sanity.clear(); lastHealth.clear(); knownDead.clear(); shadowCooldown.clear()
        game.playerDatas.filterIsInstance<PlayerData>().filter { it != playerData }.forEach {
            sanity[it.uniqueId] = 100.0
            lastHealth[it.uniqueId] = it.player.health
            if (it.entityStatus.isDead) knownDead += it.uniqueId
        }
        elapsedSeconds = 0
        particles.spawn(player, Particle.SOUL, count = 95, spread = 1.15, speed = 0.15)
        sounds.play(player, Sound.ENTITY_WARDEN_EMERGE, volume = 0.75f, pitch = 0.48f)
    }

    override fun onGameTimePasses() {
        if (!player.isOnline || playerStatus.isDead) return
        player.world.time = 18000L
        elapsedSeconds++
        val participants = game.playerDatas.filterIsInstance<PlayerData>()
        val newlyDead = participants.filter { it != playerData && it.entityStatus.isDead && knownDead.add(it.uniqueId) }
        participants.filter { it != playerData && !it.entityStatus.isDead && it.player.isOnline }.forEach { target ->
            val previousHealth = lastHealth.put(target.uniqueId, target.player.health) ?: target.player.health
            val lostHealth = (previousHealth - target.player.health).coerceAtLeast(0.0)
            var value = sanity[target.uniqueId] ?: 100.0
            if (lostHealth > 0.0) value -= lostHealth * 6.0
            newlyDead.forEach { victim ->
                if (victim.player.world == target.player.world &&
                    target.player.boundingBox.center.distanceSquared(victim.player.boundingBox.center) <= 625.0
                ) value -= 25.0
            }
            value += if (target.player.location.block.lightLevel >= 8) 2.0 else -1.0
            value = value.coerceIn(0.0, 100.0)
            sanity[target.uniqueId] = value
            applyFearEffects(target, value)
        }
    }

    override fun onGameEnd() {
        if (!nightLocked) return
        player.world.time = previousTime
        player.world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, previousDaylightCycle)
        nightLocked = false
    }

    private fun applyFearEffects(target: PlayerData, value: Double) {
        val victim = target.player
        val cooldown = (shadowCooldown[target.uniqueId] ?: 0) - 1
        shadowCooldown[target.uniqueId] = max(0, cooldown)
        if (value <= 75.0 && Random.nextDouble() < 0.28) {
            victim.spawnParticle(Particle.LARGE_SMOKE, victim.eyeLocation.clone().add(randomOffset(2.5)), 12, 0.35, 0.55, 0.35, 0.025)
            victim.playSound(victim.location, Sound.AMBIENT_CAVE, 0.45f, Random.nextDouble(0.5, 0.9).toFloat())
        }
        if (value <= 50.0) {
            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 55, 0, false, false, false))
            if (elapsedSeconds % 6 == 0) victim.playSound(victim.location, Sound.ENTITY_WARDEN_HEARTBEAT, 0.55f, 0.65f)
        }
        if (value <= 25.0 && cooldown <= 0) {
            spawnShadow(target)
            shadowCooldown[target.uniqueId] = Random.nextInt(4, 8)
        }
        if (value <= 10.0 && Random.nextDouble() < 0.22) {
            target.damage(1.0, DamageType.StatusAbnormality, playerData, false, damagePath = DamagePath.STATUS_EFFECT)
            victim.playSound(victim.location, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.42f, 1.65f)
        }
    }

    private fun randomOffset(radius: Double): Vector {
        val angle = Random.nextDouble(0.0, Math.PI * 2.0)
        return Vector(kotlin.math.cos(angle) * radius, Random.nextDouble(-0.6, 1.3), kotlin.math.sin(angle) * radius)
    }

    private fun spawnShadow(target: PlayerData) {
        val victim = target.player
        val start = victim.location.clone().add(randomOffset(Random.nextDouble(5.0, 8.0))).apply { y = victim.location.y }
        val display = start.world.spawn(start, BlockDisplay::class.java).apply {
            block = Material.BLACK_CONCRETE.createBlockData()
            brightness = Display.Brightness(0, 0)
            isPersistent = false
            transformation = Transformation(
                Vector3f(-0.35f, 0f, -0.35f), Quaternionf(), Vector3f(0.7f, 1.85f, 0.7f), Quaternionf()
            )
        }
        TemporaryDisplayManager.mark(display, player.uniqueId)
        victim.playSound(victim.location, Sound.ENTITY_ENDERMAN_STARE, 0.4f, 0.52f)
        playerData.trackTask(object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (!display.isValid || !victim.isOnline || target.entityStatus.isDead || ticks >= 45) {
                    display.remove()
                    cancel()
                    return
                }
                val difference = victim.boundingBox.center.clone().subtract(display.boundingBox.center)
                if (difference.lengthSquared() > 0.04) {
                    display.teleport(display.location.add(difference.normalize().multiply(0.16)))
                }
                victim.spawnParticle(Particle.ASH, display.location.clone().add(0.0, 0.9, 0.0), 3, 0.3, 0.65, 0.3, 0.01)
                ticks += 2
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }

    private class CrawlingFear : BasePassive() {
        override val name = "<bold>기어다니는 공포"
        override val description = listOf(
            "<gray>패시브", "", "<gray>게임 시작 시 시간을 밤으로 만들고, 밤으로 고정한다.",
            "<gray>자신을 제외한 모든 플레이어는 정신력을 가지고 시작한다. (정신력은 표시되지 않는다.)",
            "<gray>정신력이 0에 가까워질 때마다 각종 공포 효과가 나타나며, 점점 강해진다.",
            "<gray>빛이 있는 곳에 있으면 정신력이 천천히 상승한다.",
            "<gray>피해를 받거나, 사망을 목격하면 정신력이 크게 감소한다."
        )
    }
    private class Despair : BasePassive() {
        override val name = "<bold>절망"
        override val description = listOf(
            "<gray>패시브", "", "<gray>정신력이 감소하면 아래 효과가 나타날 수 있다.",
            "<gray>  - 입자 효과", "<gray>  - 공포적인 효과음, 환경음, 배경음이 들림",
            "<gray>  - 주위에 검은색 그림자 생명체가 보이거나, 다가옴",
            "<gray>  - 검은색 그림자가 증가하거나, 자신을 공격하여 피해를 입힘."
        )
    }
}
