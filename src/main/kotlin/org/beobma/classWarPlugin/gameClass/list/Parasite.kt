package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.FluidCollisionMode
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import kotlin.math.cos
import kotlin.math.sin

// 밸런스 조정 상수
private const val PARASITE_HATCH_COOLDOWN_SECONDS = 360
private const val PARASITE_HATCH_DAMAGE = 10.0

private const val THIRD_PERSON_FOLLOW_DISTANCE = 3.0
private const val THIRD_PERSON_MINIMUM_DISTANCE = 0.45
private const val THIRD_PERSON_WALL_PADDING = 0.35

class Parasite : GameClass(), GameStatusHandler, EnvironmentalDamageHandler {
    override val name = "<gray>기생충"
    override val rank = Rank.S
    override val classItemMaterial = Material.SILVERFISH_SPAWN_EGG
    private val hatchSkill = RedSkill()
    override var skills: List<Skill> = listOf(hatchSkill)
    override var passives: List<BasePassive> = listOf(Passive())

    private var host: PlayerData? = null
    private var parasiteStealth: Stealth? = null
    private var followTask: BukkitTask? = null

    fun isParasitizing(): Boolean = host != null

    override fun onBattleStart() {
        host = game.playerDatas.filterIsInstance<PlayerData>()
            .filter { it != playerData && !it.entityStatus.isDead && it.player.isOnline }
            .randomOrNull()
        val selectedHost = host
        if (selectedHost == null) {
            player.sendMiniMessage("<red><bold>[기생]</bold> <gray>기생할 생존 플레이어가 없습니다.")
            return
        }
        applyParasiteStealth()
        startFollowingHost()
        player.sendMiniMessage("<dark_red><bold>[기생]</bold> <gray>${selectedHost.player.name}님의 몸에 기생했습니다.")
    }

    override fun onGameTimePasses() {
        val currentHost = host ?: return
        applyParasiteStealth()
        player.fallDistance = 0f
        if (currentHost.entityStatus.isDead || !currentHost.player.isOnline) {
            hatchSkill.hatch(automatic = true)
            return
        }
        val survivors = game.playerDatas.filterIsInstance<PlayerData>()
            .count { !it.entityStatus.isDead && it.player.isOnline }
        if (survivors <= 2) {
            hatchSkill.hatch(automatic = true)
            return
        }
    }

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (!isParasitizing()) return
        event.isCancelled = true
        player.fallDistance = 0f
    }

    private fun applyParasiteStealth() {
        val current = parasiteStealth
        if (current != null && current in playerData.statusAbnormalitys && current.power > 0) return
        parasiteStealth = (playerData.addStatus(Stealth(), playerData) as Stealth).also {
            it.applyStatus(powerSet = 1)
        }
    }

    private fun startFollowingHost() {
        followTask?.cancel()
        followTask = playerData.trackTask(object : BukkitRunnable() {
            override fun run() {
                val currentHost = host
                if (currentHost == null) {
                    followTask = null
                    cancel()
                    return
                }
                if (!player.isOnline || !currentHost.player.isOnline) return
                followBehind(currentHost.player)
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
    }

    private fun followBehind(hostPlayer: Player) {
        val hostLocation = hostPlayer.location
        val forward = hostLocation.direction.setY(0.0)
        if (forward.lengthSquared() < 1.0E-6) {
            val yaw = Math.toRadians(hostLocation.yaw.toDouble())
            forward.setX(-sin(yaw)).setZ(cos(yaw))
        }
        val backward = forward.normalize().multiply(-1.0)
        val hostEyeLocation = hostPlayer.eyeLocation
        val wallHit = hostPlayer.world.rayTraceBlocks(
            hostEyeLocation,
            backward,
            THIRD_PERSON_FOLLOW_DISTANCE,
            FluidCollisionMode.NEVER,
            true,
        )
        val followDistance = wallHit?.hitPosition
            ?.distance(hostEyeLocation.toVector())
            ?.minus(THIRD_PERSON_WALL_PADDING)
            ?.coerceIn(THIRD_PERSON_MINIMUM_DISTANCE, THIRD_PERSON_FOLLOW_DISTANCE)
            ?: THIRD_PERSON_FOLLOW_DISTANCE
        val destination = hostLocation.clone().add(backward.multiply(followDistance)).apply {
            yaw = hostLocation.yaw
            pitch = hostLocation.pitch
        }

        player.teleport(destination)
        player.velocity = hostPlayer.velocity
        player.fallDistance = 0f
    }

    private inner class RedSkill : Skill() {
        override val name = "<bold>부화"
        override val description = listOf(
            "<gray>기생 상태를 해제하고, 기생한 숙주의 몸을 뚫고 나온다.",
            "<gray>숙주가 생존해있다면 10의 피해를 입힌다.",
            "<gray>숙주가 사망해있다면 자신은 10의 피해를 입는다."
        )
        override val cooldown = PARASITE_HATCH_COOLDOWN_SECONDS

        override fun isUseSuccess(): Boolean {
            if (isParasitizing()) return true
            player.sendMiniMessage("<red><bold>[!] 현재 기생 중인 숙주가 없습니다.")
            return false
        }

        override fun use() = hatch(automatic = false)

        fun hatch(automatic: Boolean) {
            val currentHost = host ?: return
            val hostWasAlive = !currentHost.entityStatus.isDead && currentHost.player.isOnline
            host = null
            followTask?.cancel()
            followTask = null
            parasiteStealth?.remove()
            parasiteStealth = null

            val exitLocation = currentHost.player.location.clone()
                .add(currentHost.player.location.direction.clone().multiply(-0.9))
                .add(0.0, 0.25, 0.0)
            if (exitLocation.world == player.world) player.teleport(exitLocation)
            player.fallDistance = 0f

            if (hostWasAlive) {
                currentHost.damage(
                    PARASITE_HATCH_DAMAGE,
                    DamageType.Normal,
                    playerData,
                    bypassShield = true,
                    damagePath = DamagePath.SKILL,
                )
                particles.spawn(currentHost.player, Particle.DAMAGE_INDICATOR, count = 24, spread = 0.5, speed = 0.12)
                sounds.play(currentHost.player, Sound.ENTITY_PLAYER_ATTACK_CRIT, volume = 1.0f, pitch = 0.55f)
            } else {
                playerData.damage(
                    PARASITE_HATCH_DAMAGE,
                    DamageType.True,
                    playerData,
                    bypassShield = true,
                    damagePath = DamagePath.SKILL,
                )
                particles.spawn(player, Particle.SOUL, count = 20, spread = 0.5, speed = 0.1)
                sounds.play(player, Sound.ENTITY_SILVERFISH_DEATH, volume = 0.9f, pitch = 0.55f)
            }
            particles.spawn(player, Particle.INFESTED, count = 30, spread = 0.65, speed = 0.12)
            sounds.play(player, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, volume = 0.75f, pitch = 1.45f)
            player.sendMiniMessage(
                if (automatic) "<dark_red><bold>[자동 부화]</bold> <gray>부화 조건을 만족하여 숙주에게서 나왔습니다."
                else "<dark_red><bold>[부화]</bold> <gray>숙주에게서 나왔습니다."
            )
        }
    }

    private inner class Passive : BasePassive(), WhenHitHandler {
        override val name = "<bold>기생"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>게임 시작 시 생존한 무작위 플레이어 한 명에게 기생한다.",
            "<gray>기생 중 숙주의 뒤를 3인칭 시점처럼 따라다닌다.",
            "<gray>벽에 끼이거나 다른 플레이어에게 공격받아도 피해를 받지 않으며,",
            "<gray>피해를 입힐 수 없고 {keyword:Stealth} 상태가 된다.",
            "<gray>아래 조건 중 하나라도 만족하면 부화가 자동으로 사용된다.",
            "<gray>  - 기생한 플레이어가 사망한 경우",
            "<gray>  - 생존한 플레이어가 기생한 플레이어와 자신 뿐인 경우"
        )

        override fun whenHit(context: org.beobma.classWarPlugin.damage.DamageContext) {
            if (isParasitizing()) context.isCancelled = true
        }
    }
}
