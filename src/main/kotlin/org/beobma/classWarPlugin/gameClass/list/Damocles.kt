package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DisplayOrientationUtil
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.util.Vector
import kotlin.random.Random

private const val DAMOCLES_SWORD_SCALE = 1.0f

class Damocles : GameClass(), GameStatusHandler {
    override val classId = "damocles"
    override val name = "<gray>다모클레스"
    override val rank = Rank.S
    override val classItemMaterial = Material.MUSIC_DISC_11
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private var sword: ItemDisplay? = null
    private var deathCheckStarted = false
    private var executionChance = 0.0002

    override fun onBattleStart() {
        sword?.remove()
        deathCheckStarted = false
        executionChance = 0.0002
        val swordLocation = player.location.clone().add(0.0, 3.2, 0.0).apply {
            yaw = 0f
            pitch = 0f
        }
        val display = player.world.spawn(swordLocation, ItemDisplay::class.java).apply {
            setItemStack(ItemStack(Material.NETHERITE_SWORD))
            itemDisplayTransform = ItemDisplay.ItemDisplayTransform.NONE
            billboard = Display.Billboard.FIXED
            brightness = Display.Brightness(15, 15)
            isPersistent = false
        }
        DisplayOrientationUtil.alignSwordBladeVertically(display, Vector(0.0, -1.0, 0.0), DAMOCLES_SWORD_SCALE)
        TemporaryDisplayManager.mark(display, player.uniqueId)
        sword = display
        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            var tick = 0
            override fun run() {
                val current = sword
                if (!player.isOnline || playerStatus.isDead || current == null || !current.isValid) {
                    current?.remove()
                    sword = null
                    cancel()
                    return
                }
                val nextLocation = player.location.clone()
                    .add(0.0, 3.2 + kotlin.math.sin(tick * 0.09) * 0.18, 0.0)
                    .apply {
                        // 플레이어의 시선 회전을 표시 엔티티에 전달하지 않는다.
                        yaw = 0f
                        pitch = 0f
                    }
                current.teleport(nextLocation)
                if (tick % 6 == 0) particles.spawn(current.location, Particle.ENCHANT, count = 7, spread = 0.55, speed = 0.015)
                tick += 2
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }
    override fun onGameTimePasses() = Unit

    private fun startDeathChecks() {
        if (deathCheckStarted) return
        deathCheckStarted = true
        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            override fun run() {
                if (playerStatus.isDead || sword?.isValid != true) {
                    cancel()
                    return
                }
                if (Random.nextDouble() >= executionChance) return
                val blade = sword
                sword = null
                blade?.remove()
                particles.spawn(player, Particle.FLASH, count = 2)
                particles.spawn(player, Particle.SWEEP_ATTACK, count = 16, spread = 0.6, speed = 0.08)
                sounds.play(player, Sound.ENTITY_WITHER_BREAK_BLOCK, volume = 1.0f, pitch = 0.52f)
                player.health = 0.0
                cancel()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 5L, 5L))
    }

    private inner class Passive : BasePassive(), OnHitHandler, WhenHitHandler {
        override val name = "<bold>시한부"
        override val description = listOf(
            "<gray>패시브", "", "<gray>게임 시작 시 머리 위에 거대한 검이 매달린다.",
            "<gray>검이 존재하는 동안 아래의 효과를 모두 얻는다.", "",
            "<gray>  - 가하는 피해 50% 증가", "<gray>  - 받는 피해 50% 감소", "",
            "<gray>대신 처음 피해를 받은 순간부터 매 5틱마다 자신이 {keyword:Execution}당할 확률이 생긴다.",
            "<gray>확률은 아래와 같다.", "<gray>  - 기본 확률 0.02%", "<gray>  - 이후 피해를 받을 때마다 0.05%씩 확률 증가."
        )
        override fun onHit(context: DamageContext) {
            if (sword?.isValid == true) context.addDamageDealtMultiplier(1.5)
        }
        override fun whenHit(context: DamageContext) {
            if (sword?.isValid != true) return
            context.addDamageTakenMultiplier(0.5)
            if (deathCheckStarted) executionChance += 0.0005 else startDeathChecks()
        }
    }
}
