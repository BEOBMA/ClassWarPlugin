package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WeaponInputHandler
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.SkillManager.getConeTargets
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Bleeding
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.DisplayOrientationUtil
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import java.util.UUID

class Knight : GameClass(), WeaponInputHandler {
    override val name = "<gray>기사"
    override val rank = Rank.B
    override val classItemMaterial = Material.IRON_SWORD
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill(),
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private var parryUntilTick = 0L
    private var parryReadyTick = 0L

    override fun onWeaponRightClick(event: PlayerInteractEvent) {
        event.isCancelled = true
        val now = player.world.fullTime
        if (now < parryReadyTick) {
            player.sendMiniMessage("<red><bold>[!] 패링을 다시 준비하는 중입니다.")
            return
        }
        parryUntilTick = now + 6L
        sounds.playTo(player, Sound.ITEM_ARMOR_EQUIP_IRON, pitch = 1.7f)
        particles.spawn(player, Particle.SWEEP_ATTACK, count = 2, spread = 0.2)
    }


    private class Weapon : BaseWeapon() {
        override val name = "<gray>장검"
        override val description = listOf(
            "<gray>기본 공격 피격 직전 우클릭하면 해당 피해를 무효로 한다.",
            "<gray>패링에 성공하면 가로베기의 재사용 대기 시간이 초기화된다.",
            "<gray>이 효과는 24초마다 한 번만 사용할 수 있다."
        )
        override val material = Material.IRON_SWORD
    }

    private class RedSkill : Skill() {
        override val name = "<bold>가로베기"
        override val description = listOf(
            "<gray>바라보는 방향으로 검을 휘두른다.",
            "<gray>적중한 모든 적에게 4의 피해를 입히고 4초간 {keyword:Bleeding}을 4 부여한다."
        )
        override val cooldown = 12

        override fun use() {
            val center = player.location.clone().add(0.0, 1.15, 0.0)
            val display = center.world.spawn(center, ItemDisplay::class.java)
            display.setItemStack(ItemStack(Material.IRON_SWORD))
            display.isPersistent = false
            TemporaryDisplayManager.mark(display, player.uniqueId)
            val baseDirection = player.location.direction.setY(0).normalize()
            val hit = mutableSetOf<UUID>()
            playerData.trackTask(object : BukkitRunnable() {
                var tick = 0
                override fun run() {
                    if (tick > 10 || !display.isValid) {
                        display.remove()
                        cancel()
                        return
                    }
                    val angle = Math.toRadians(-65.0 + 130.0 * tick / 10.0)
                    val direction = baseDirection.clone().rotateAroundY(-angle)
                    val bladeStart = center.clone().add(direction.clone().multiply(0.8))
                    val bladeEnd = center.clone().add(direction.clone().multiply(5.0))
                    val displayLocation = center.clone().add(direction.clone().multiply(2.7))
                    display.teleport(displayLocation)
                    DisplayOrientationUtil.alignSwordBladeHorizontally(display, direction, scale = 3.2f)

                    playerData.getConeTargets(5.5, 140.0, TargetType.Enemy, false).forEach { target ->
                        if (target.entity.uniqueId in hit) return@forEach
                        if (!org.beobma.classWarPlugin.util.HitboxUtil.intersectsSegment(
                                target.entity.boundingBox,
                                bladeStart.toVector(),
                                bladeEnd.toVector(),
                                expansion = 0.2,
                            )) return@forEach
                        hit += target.entity.uniqueId
                        target.damage(4.0, DamageType.Normal, playerData)
                        target.getOrCreateStatus(playerData) { Bleeding() }
                            .applyStatus(duration = 4, powerSet = 4)
                        particles.spawn(target.entity, Particle.SWEEP_ATTACK, count = 1)
                    }
                    if (tick % 2 == 0) particles.line(bladeStart, bladeEnd, Particle.SWEEP_ATTACK, spacing = 1.4)
                    tick++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
            sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, volume = 1.0f, pitch = 0.75f)
        }
    }

    private inner class Passive : BasePassive(), OnHitHandler, WhenHitHandler {
        override val name = "<bold>피로 벼려낸 검"
        override val description = listOf(
            "<gray>기본 공격 적중 시 3초간 적에게 {keyword:Bleeding}을 1 부여하고 {keyword:Bleeding}을 발동시킨다.",
            "<gray>이후 대상의 {keyword:Bleeding} 수치가 절반으로 감소한다."
        )

        override fun onAttackHit(event: DamageContext) {
            val entityData = event.target
            val status = entityData.getOrCreateStatus(playerData) { Bleeding() }
            status.applyStatus(duration = 3, powerSet = 1)

            entityData.damage(status.power.toDouble(), DamageType.StatusAbnormality, playerData)
            status.updatePower(status.power / 2)
        }

        override fun whenAttackHit(context: DamageContext) {
            val now = player.world.fullTime
            if (now > parryUntilTick || now < parryReadyTick) return
            context.isCancelled = true
            parryUntilTick = 0L
            parryReadyTick = now + 24L * 20L
            CooldownManager.resetCooldown(player, skills.first())
            sounds.play(player, Sound.ITEM_SHIELD_BLOCK, volume = 1.3f, pitch = 1.4f)
            particles.spawn(player, Particle.FLASH, count = 1)
        }

    }
}
