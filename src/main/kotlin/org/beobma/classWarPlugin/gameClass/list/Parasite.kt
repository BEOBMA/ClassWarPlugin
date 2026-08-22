package org.beobma.classWarPlugin.gameClass.list

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
import org.bukkit.event.entity.EntityDamageEvent
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class Parasite : GameClass(), GameStatusHandler, EnvironmentalDamageHandler {
    override val name = "<gray>기생충"
    override val rank = Rank.S
    override val classItemMaterial = Material.SILVERFISH_SPAWN_EGG
    private val hatchSkill = RedSkill()
    override var skills: List<Skill> = listOf(hatchSkill)
    override var passives: List<BasePassive> = listOf(Passive())

    private var host: PlayerData? = null
    private var parasiteStealth: Stealth? = null

    private fun isParasitic(): Boolean = host != null

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
        player.sendMiniMessage("<dark_red><bold>[기생]</bold> <gray>${selectedHost.player.name}님의 몸에 기생했습니다.")
        particles.spawn(selectedHost.player, Particle.INFESTED, count = 18, spread = 0.45, speed = 0.05)
        sounds.play(selectedHost.player, Sound.ENTITY_SILVERFISH_AMBIENT, volume = 0.65f, pitch = 0.7f)
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
        particles.spawn(currentHost.player, Particle.INFESTED, count = 3, spread = 0.28, speed = 0.015)
    }

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (!isParasitic()) return
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

    private inner class RedSkill : Skill() {
        override val name = "<bold>부화"
        override val description = listOf(
            "<gray>기생 상태를 해제하고, 기생한 숙주의 몸을 뚫고 나온다.",
            "<gray>숙주가 생존해있다면 10의 피해를 입힌다.",
            "<gray>숙주가 사망해있다면 자신은 10의 피해를 입는다."
        )
        override val cooldown = 360

        override fun isUseSuccess(): Boolean {
            if (isParasitic()) return true
            player.sendMiniMessage("<red><bold>[!] 현재 기생 중인 숙주가 없습니다.")
            return false
        }

        override fun use() = hatch(automatic = false)

        fun hatch(automatic: Boolean) {
            val currentHost = host ?: return
            val hostWasAlive = !currentHost.entityStatus.isDead && currentHost.player.isOnline
            host = null
            parasiteStealth?.remove()
            parasiteStealth = null

            val exitLocation = currentHost.player.location.clone()
                .add(currentHost.player.location.direction.clone().multiply(-0.9))
                .add(0.0, 0.25, 0.0)
            if (exitLocation.world == player.world) player.teleport(exitLocation)
            player.fallDistance = 0f

            if (hostWasAlive) {
                currentHost.damage(
                    10.0,
                    DamageType.Normal,
                    playerData,
                    bypassShield = true,
                    damagePath = DamagePath.SKILL,
                )
                particles.spawn(currentHost.player, Particle.DAMAGE_INDICATOR, count = 24, spread = 0.5, speed = 0.12)
                sounds.play(currentHost.player, Sound.ENTITY_PLAYER_ATTACK_CRIT, volume = 1.0f, pitch = 0.55f)
            } else {
                playerData.damage(
                    10.0,
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
            "<gray>기생 상태에서는 피해를 입지 않고, {keyword:Stealth} 상태가 된다.",
            "<gray>아래 조건 중 하나라도 만족하면 부화가 자동으로 사용된다.",
            "<gray>  - 기생한 플레이어가 사망한 경우",
            "<gray>  - 생존한 플레이어가 기생한 플레이어와 자신 뿐인 경우"
        )

        override fun whenHit(context: org.beobma.classWarPlugin.damage.DamageContext) {
            if (isParasitic()) context.isCancelled = true
        }
    }
}
