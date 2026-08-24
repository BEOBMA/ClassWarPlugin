package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.manager.GameClassManager.toWeaponItemStack
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import kotlin.random.Random

private const val BLACKSMITH_ENHANCE_COOLDOWN_SECONDS = 6
private const val BLACKSMITH_MAX_ENHANCEMENT = 10
private const val BLACKSMITH_DAMAGE_PER_LEVEL = 1.0
private const val BLACKSMITH_BASIC_ATTACK_MULTIPLIER = 0.6

class Blacksmith : GameClass(), GameStatusHandler, OnHitHandler {
    override val name = "<gray>대장장이"
    override val rank = Rank.B
    override val classItemMaterial = Material.ANVIL
    override val weapon: BaseWeapon
        get() = EnhancedWeapon(enhancementLevel)
    override var skills: List<Skill> = listOf(EnhanceSkill())
    override var passives: List<BasePassive> = emptyList()

    private var enhancementLevel = 0

    override fun onBattleStart() {
        enhancementLevel = 0
        refreshWeapon()
    }

    override fun onGameTimePasses() = Unit

    override fun onAttackHit(context: DamageContext) {
        if (context.path != DamagePath.BASIC_ATTACK || enhancementLevel <= 0) return
        if (getWeaponClassId(player.inventory.itemInMainHand) != javaClass.name) return
        // DamageManager가 모든 기본 공격에 0.6배를 적용하므로, 최종 추가 피해가
        // 강화 단계당 정확히 1이 되도록 여기서 역보정한다.
        context.addBaseDamage(enhancementLevel * BLACKSMITH_DAMAGE_PER_LEVEL / BLACKSMITH_BASIC_ATTACK_MULTIPLIER)
    }

    private fun refreshWeapon() {
        val slot = if (playerData.gameClasses.indexOf(this) == 1) 8 else 0
        player.inventory.setItem(slot, toWeaponItemStack())
    }

    private fun chanceAt(level: Int): EnhanceChance = when (level) {
        0 -> EnhanceChance(success = 90.0, destroy = 0.0)
        1 -> EnhanceChance(success = 85.0, destroy = 0.0)
        2 -> EnhanceChance(success = 75.0, destroy = 2.0)
        3 -> EnhanceChance(success = 65.0, destroy = 5.0)
        4 -> EnhanceChance(success = 55.0, destroy = 10.0)
        5 -> EnhanceChance(success = 45.0, destroy = 15.0)
        6 -> EnhanceChance(success = 35.0, destroy = 23.0)
        7 -> EnhanceChance(success = 28.0, destroy = 30.0)
        8 -> EnhanceChance(success = 22.0, destroy = 40.0)
        else -> EnhanceChance(success = 15.0, destroy = 50.0)
    }

    private data class EnhanceChance(val success: Double, val destroy: Double)

    private class EnhancedWeapon(private val level: Int) : BaseWeapon() {
        override val name = "<gray>검 <white>(+$level)"
        override val description = listOf(
            "<gray>강화 수치 1마다 기본 공격 피해가 <red>1</red> 증가한다.",
        )
        override val material = Material.IRON_SWORD
    }

    private inner class EnhanceSkill : Skill() {
        override val name = "<bold>강화"
        override val description = listOf(
            "<gray>검 강화를 시도한다.",
            "<gray>단계별 확률에 따라 강화에 성공하거나, 실패하거나, 파괴된다.",
            "<gray>파괴되면 게임 시작 시 제공되는 <white>검 (+0)</white>으로 돌아간다.",
            "<gray>강화 1회마다 기본 공격 피해가 <red>1</red> 증가한다.",
            "<gray>최대 강화 수치는 <gold>+$BLACKSMITH_MAX_ENHANCEMENT</gold>이다.",
        )
        override val cooldown = BLACKSMITH_ENHANCE_COOLDOWN_SECONDS

        override fun isUseSuccess(): Boolean {
            if (enhancementLevel < BLACKSMITH_MAX_ENHANCEMENT) return true
            player.sendMiniMessage("<yellow><bold>[!] 이미 최고 강화 단계입니다.")
            sounds.play(player, Sound.BLOCK_ANVIL_LAND, volume = 0.55f, pitch = 1.45f)
            return false
        }

        override fun use() {
            val before = enhancementLevel
            val chance = chanceAt(before)
            val roll = Random.nextDouble(100.0)

            sounds.play(player, Sound.BLOCK_ANVIL_USE, volume = 0.9f, pitch = 0.92f + before * 0.025f)
            particles.spawn(player.location.clone().add(0.0, 1.0, 0.0), Particle.ENCHANT, count = 18, spread = 0.55, speed = 0.04)

            when {
                roll < chance.success -> {
                    enhancementLevel++
                    refreshWeapon()
                    particles.spawn(player, Particle.HAPPY_VILLAGER, count = 30, spread = 0.65, speed = 0.1)
                    particles.spawn(player, Particle.END_ROD, count = 14, spread = 0.45, speed = 0.08)
                    sounds.play(player, Sound.ENTITY_PLAYER_LEVELUP, volume = 0.85f, pitch = 1.25f + enhancementLevel * 0.035f)
                    player.sendMiniMessage(
                        "<green><bold>강화 성공!</bold> <gray>검이 <white>(+$enhancementLevel)</white>이 되었습니다. " +
                            "<dark_gray>[성공 ${chance.success.toInt()}%]",
                    )
                }

                roll < chance.success + chance.destroy -> {
                    enhancementLevel = 0
                    refreshWeapon()
                    particles.spawn(player, Particle.LARGE_SMOKE, count = 42, spread = 0.75, speed = 0.1)
                    particles.spawn(player, Particle.EXPLOSION, count = 2, spread = 0.25)
                    sounds.play(player, Sound.BLOCK_ANVIL_DESTROY, volume = 1.0f, pitch = 0.72f)
                    player.sendMiniMessage("<red><bold>어이쿠 손이 미끄러졌네</bold>")
                    player.sendMiniMessage("<gray>검이 파괴되어 <white>(+0)</white>으로 돌아갔습니다.")
                }

                else -> {
                    particles.spawn(player, Particle.SMOKE, count = 22, spread = 0.5, speed = 0.035)
                    sounds.play(player, Sound.BLOCK_ANVIL_LAND, volume = 0.75f, pitch = 0.62f)
                    player.sendMiniMessage(
                        "<yellow><bold>강화 실패.</bold> <gray>검은 <white>(+$enhancementLevel)</white>을 유지합니다. " +
                            "<dark_gray>[실패 ${(100.0 - chance.success - chance.destroy).toInt()}%]",
                    )
                }
            }
        }
    }
}
