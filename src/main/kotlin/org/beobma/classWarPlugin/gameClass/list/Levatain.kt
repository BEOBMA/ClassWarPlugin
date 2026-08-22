package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.GameClassManager.toItemStack
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.Bleeding
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.scheduler.BukkitRunnable
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class Levatain : GameClass(), GameStatusHandler {
    override val name = "<gray>레바테인"
    override val rank = Rank.L
    override val classItemMaterial = Material.NETHERITE_SWORD
    override val weapon: BaseWeapon
        get() = weapons[sealStage]
    override var skills: List<Skill> = listOf()
    override var passives: List<BasePassive> = listOf(ReleasePassive(), GritPassive())

    private val weapons: List<BaseWeapon> = listOf(SealedWeapon(), FirstReleaseWeapon(), SecondReleaseWeapon(), LevatainWeapon())
    private var sealStage = 0
    private var belowHalfTriggered = false
    private var belowQuarterTriggered = false

    override fun onBattleStart() {
        sealStage = 0
        belowHalfTriggered = false
        belowQuarterTriggered = false
        releaseStatus().updateState(stage = 0, release = 0)
        updateWeaponItem()
        player.sendMiniMessage("<gold><bold>[레바테인]</bold> <gray>봉인된 검의 해방도가 초기화되었습니다.")
    }

    override fun onGameTimePasses() {
        if (!player.isOnline || playerStatus.isDead) return
        addRelease(1)
        checkHealthThresholds()
    }

    private fun releaseStatus(): LevatainReleaseStatus =
        playerData.getOrCreateStatus(playerData) { LevatainReleaseStatus() }

    private fun addRelease(amount: Int) {
        if (amount <= 0 || sealStage >= FINAL_STAGE || !player.isOnline || playerStatus.isDead) return
        var release = releaseStatus().power + amount
        while (release >= MAX_RELEASE && sealStage < FINAL_STAGE) {
            release -= MAX_RELEASE
            sealStage++
            unlockNextSeal()
        }
        if (sealStage >= FINAL_STAGE) release = 0
        releaseStatus().updateState(sealStage, release)
        if (amount >= 5 && sealStage < FINAL_STAGE) {
            particles.spawn(player, Particle.ENCHANT, count = (amount / 2).coerceAtMost(18), spread = 0.38, speed = 0.03)
        }
    }

    private fun unlockNextSeal() {
        updateWeaponItem()
        val center = player.boundingBox.center.toLocation(player.world)
        val color = when (sealStage) {
            1 -> Color.fromRGB(180, 180, 190)
            2 -> Color.fromRGB(80, 220, 255)
            else -> Color.fromRGB(255, 55, 20)
        }
        particles.spawn(
            center,
            Particle.DUST,
            Particle.DustOptions(color, if (sealStage == FINAL_STAGE) 2.0f else 1.45f),
            org.beobma.classWarPlugin.effect.ParticleOptions.spread(48 + sealStage * 18, 1.0, 0.14),
        )
        particles.spawn(center, Particle.ENCHANT, count = 40 + sealStage * 20, spread = 1.2, speed = 0.16)
        if (sealStage == FINAL_STAGE) {
            particles.spawn(center, Particle.FLAME, count = 90, spread = 1.15, speed = 0.2)
            particles.spawn(center, Particle.FLASH, count = 2)
            sounds.play(center, Sound.ITEM_TRIDENT_THUNDER, volume = 1.0f, pitch = 0.8f)
            sounds.play(center, Sound.ENTITY_BLAZE_SHOOT, volume = 1.0f, pitch = 0.55f)
        } else {
            sounds.play(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, volume = 1.0f, pitch = 0.8f + sealStage * 0.25f)
            sounds.play(center, Sound.ITEM_ARMOR_EQUIP_IRON, volume = 0.9f, pitch = 0.75f + sealStage * 0.15f)
        }
        player.sendMiniMessage(
            "<gold><bold>[봉인 해제]</bold> <gray>검이 <white>${weapon.name}<gray>(으)로 변화했습니다."
        )
    }

    private fun updateWeaponItem() {
        player.inventory.setItem(0, weapon.toItemStack())
    }

    private fun checkHealthThresholds() {
        if (!player.isOnline || playerStatus.isDead || player.health <= 0.0) return
        val maximumHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        val ratio = player.health / maximumHealth
        if (!belowHalfTriggered && ratio <= 0.5) {
            belowHalfTriggered = true
            addRelease(30)
            player.sendMiniMessage("<gold><bold>[해방도]</bold> <gray>체력이 처음으로 50% 이하가 되어 <yellow>+30</yellow>")
            sounds.play(player, Sound.ENTITY_PLAYER_HURT, volume = 0.65f, pitch = 0.72f)
        }
        if (!belowQuarterTriggered && ratio <= 0.25) {
            belowQuarterTriggered = true
            addRelease(60)
            player.sendMiniMessage("<gold><bold>[해방도]</bold> <gray>체력이 처음으로 25% 이하가 되어 <yellow>+60</yellow>")
            sounds.play(player, Sound.ENTITY_WITHER_HURT, volume = 0.6f, pitch = 0.88f)
        }
    }

    private inner class ReleasePassive : BasePassive(), OnHitHandler, WhenHitHandler {
        override val name = "<bold>봉인 해제"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>아래 조건으로 해방도를 누적할 수 있다.",
            "<gray>해방도가 100이 되면 봉인을 한 단계 해제한다.",
            "<gray>  - 적에게 피해를 입힐 때마다 +5",
            "<gray>  - 적에게 피해를 받을 때마다 +7",
            "<gray>  - 적을 직접 처치 시 +100",
            "<gray>  - 처음으로 체력이 50% 이하가 되면 +30",
            "<gray>  - 처음으로 체력이 25% 이하가 되면 +60",
            "<gray>  - 1초마다 +1",
        )

        override fun onHit(context: DamageContext) {
            addRelease(5)
        }

        override fun onAttackHit(context: DamageContext) {
            if (sealStage < FINAL_STAGE || player.inventory.itemInMainHand.type != Material.NETHERITE_SWORD) return
            context.target.entity.fireTicks = maxOf(context.target.entity.fireTicks, 60)
            context.target.getOrCreateStatus(playerData) { Bleeding() }
                .applyStatus(duration = 3, powerDelta = 1)
            val targetCenter = context.target.entity.boundingBox.center.toLocation(context.target.entity.world)
            particles.spawn(targetCenter, Particle.FLAME, count = 16, spread = 0.45, speed = 0.08)
            particles.spawn(targetCenter, Particle.ASH, count = 12, spread = 0.38, speed = 0.025)
            sounds.play(targetCenter, Sound.ENTITY_BLAZE_HURT, volume = 0.65f, pitch = 0.8f)
        }

        override fun whenHit(context: DamageContext) {
            addRelease(7)
            playerData.trackTask(object : BukkitRunnable() {
                override fun run() = checkHealthThresholds()
            }.runTaskLater(ClassWarPlugin.instance, 1L))
        }
    }

    private inner class GritPassive : BasePassive(), WhenHitHandler {
        override val name = "<bold>초근성"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>받는 피해가 20%, 무기가 레바테인 상태라면 30% 감소한다."
        )

        override fun whenHit(context: DamageContext) {
            context.addDamageTakenMultiplier(if (sealStage >= FINAL_STAGE) 0.7 else 0.8)
        }
    }

    private class SealedWeapon : BaseWeapon() {
        override val name = "<gray>봉인된 검"
        override val description = listOf(
            "<gray>봉인된 상태의 검.",
            "<gray>조건 만족 시 1단계, 2단계를 거쳐 레바테인으로 해방된다."
        )
        override val material = Material.STONE_SWORD
    }

    private class FirstReleaseWeapon : BaseWeapon() {
        override val name = "<white>1단계 봉인 해제"
        override val description = listOf("<gray>봉인이 한 단계 해제된 검.")
        override val material = Material.IRON_SWORD
    }

    private class SecondReleaseWeapon : BaseWeapon() {
        override val name = "<aqua>2단계 봉인 해제"
        override val description = listOf("<gray>봉인이 두 단계 해제된 검. 다음 단계에서 레바테인이 된다.")
        override val material = Material.DIAMOND_SWORD
    }

    private class LevatainWeapon : BaseWeapon() {
        override val name = "<red><bold>레바테인"
        override val description = listOf(
            "<gray>공격 적중 시 3초간 {keyword:Burn} 상태로 만들며 {keyword:Bleeding}을 1 부여한다."
        )
        override val material = Material.NETHERITE_SWORD
    }

    companion object {
        private const val MAX_RELEASE = 100
        private const val FINAL_STAGE = 3

        fun handleKill(killerId: java.util.UUID?) {
            val id = killerId ?: return
            val killer = Bukkit.getPlayer(id) ?: return
            val currentGame = findGameForPlayer(killer) ?: return
            val killerData = currentGame.playerDatas.filterIsInstance<PlayerData>()
                .find { it.uniqueId == id } ?: return
            (killerData.gameClass as? Levatain)?.addRelease(100)
        }
    }
}

private class LevatainReleaseStatus : StatusAbnormality() {
    override val name = "<gold><bold>해방도</bold><gray>"
    override val description = listOf("<gray>100에 도달하면 레바테인의 봉인이 한 단계 해제된다.")
    override val canRemove = false
    override val isClassMechanic = true
    override var power = 0
    override var maxPower: Int? = 100
    override var duration: Int? = null

    private var stage = 0

    fun updateState(stage: Int, release: Int) {
        this.stage = stage.coerceIn(0, 3)
        updatePower(release.coerceIn(0, 100))
    }

    override fun actionBarText(): String {
        val stageText = when (stage) {
            0 -> "<gray>봉인"
            1 -> "<white>1단계"
            2 -> "<aqua>2단계"
            else -> "<red><bold>레바테인"
        }
        val releaseText = if (stage >= 3) "<red>MAX</red>" else "<gold>$power</gold><dark_gray>/</dark_gray><gold>100</gold>"
        return "$name: $releaseText <dark_gray>|</dark_gray> $stageText"
    }
}
