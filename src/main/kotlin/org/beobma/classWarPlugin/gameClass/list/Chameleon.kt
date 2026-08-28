package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.GameManager.canDispatchClassHandlers
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.Tag
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

private const val CHAMELEON_COOLDOWN_SECONDS = 60
private const val CHAMELEON_DISGUISE_DURATION_SECONDS = 20

class Chameleon : GameClass(), OnHitHandler, WhenHitHandler, GameEndHandler, PlayerDeathHandler {
    override val name = "<gray>카멜레온"
    override val rank = Rank.B
    override val classItemMaterial = Material.BLACK_CONCRETE
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = listOf(Passive())
    private var display: BlockDisplay? = null
    private var interaction: Interaction? = null
    private var stealth: Stealth? = null
    private var copiedMaterial = Material.STONE
    private var lockedLocation: Location? = null
    private var redUntilTick = 0L
    private var disguiseGeneration = 0

    override fun onHit(context: DamageContext) = flashRed()
    override fun whenHit(context: DamageContext) = flashRed()
    override fun onGameEnd() = clearDisguise()
    override fun onPlayerDeath() = clearDisguise()

    private inner class RedSkill : Skill() {
        override val name = "<bold>위장"
        override val description = listOf(
            "<gray>2칸 내의 바라보는 블럭으로 위장한다.",
            "<gray>위장은 최대 ${CHAMELEON_DISGUISE_DURATION_SECONDS}초 동안 유지된다.",
            "<gray>자신은 {keyword:Stealth} 상태가 되고, 블럭에 피격 판정이 전이된다.",
            "<gray>웅크리고 있으면 진짜 블럭처럼 밟을 수 있고, 한 칸에 고정된다.",
            "<gray>피격 혹은 공격 시 잠시 블럭이 빨간색으로 변한다."
        )
        override val cooldown = CHAMELEON_COOLDOWN_SECONDS
        private var selectedBlock: Block? = null

        override fun isUseSuccess(): Boolean {
            selectedBlock = playerData.shotLaserGetBlock(2.0)
            if (selectedBlock == null) player.sendMiniMessage("<red><bold>[!] 2칸 내에 바라보는 블록이 없습니다.")
            return selectedBlock != null
        }

        override fun use() {
            val block = selectedBlock ?: return
            selectedBlock = null
            activateDisguise(block)
        }
    }

    private fun activateDisguise(block: Block) {
        clearDisguise()
        val generation = ++disguiseGeneration
        val expiresAtTick = Bukkit.getCurrentTick().toLong() + CHAMELEON_DISGUISE_DURATION_SECONDS * 20L
        copiedMaterial = block.type.takeUnless { it.isAir } ?: Material.STONE
        val normalBlockData = block.blockData.clone()
        val visual = player.world.spawn(player.location, BlockDisplay::class.java).apply {
            this.block = normalBlockData
            billboard = Display.Billboard.FIXED
            brightness = Display.Brightness(15, 15)
            isPersistent = false
        }
        TemporaryDisplayManager.mark(visual, player.uniqueId)
        val hitbox = player.world.spawn(player.location, Interaction::class.java).apply {
            interactionWidth = 1.0f
            interactionHeight = 1.0f
            isResponsive = true
            isPersistent = false
        }
        display = visual
        interaction = hitbox
        disguiseOwners[hitbox.uniqueId] = this
        stealth = playerData.addStatus(Stealth(), playerData) as Stealth
        stealth?.applyStatus(duration = CHAMELEON_DISGUISE_DURATION_SECONDS, powerSet = 1)
        sounds.play(player, Sound.ENTITY_PARROT_AMBIENT, volume = 0.65f, pitch = 1.25f)
        particles.spawn(player, Particle.POOF, count = 26, spread = 0.65, speed = 0.08)
        playerData.trackTask(object : BukkitRunnable() {
            private var lastBase: Location? = null
            private var showingDamageFlash = false

            override fun run() {
                if (generation != disguiseGeneration) {
                    cancel()
                    return
                }
                val currentDisplay = display
                val currentHitbox = interaction
                if (Bukkit.getCurrentTick().toLong() >= expiresAtTick || !player.isOnline || playerStatus.isDead ||
                    currentDisplay?.isValid != true || currentHitbox?.isValid != true
                ) {
                    clearDisguise()
                    cancel()
                    return
                }
                if (player.isSneaking) {
                    if (lockedLocation == null) lockedLocation = player.location.block.location.add(0.5, 0.0, 0.5)
                    val locked = lockedLocation!!
                    if (player.location.distanceSquared(locked) > 1.0E-6) {
                        player.teleport(locked.clone().apply { yaw = player.location.yaw; pitch = player.location.pitch })
                    }
                } else {
                    lockedLocation = null
                }
                val base = (lockedLocation ?: player.location).block.location
                val previousBase = lastBase
                if (previousBase == null || previousBase.world != base.world ||
                    previousBase.blockX != base.blockX || previousBase.blockY != base.blockY || previousBase.blockZ != base.blockZ
                ) {
                    currentDisplay.teleport(base)
                    currentHitbox.teleport(base.clone().add(0.5, 0.0, 0.5))
                    lastBase = base.clone()
                }
                val shouldShowDamageFlash = Bukkit.getCurrentTick().toLong() < redUntilTick
                if (showingDamageFlash != shouldShowDamageFlash) {
                    currentDisplay.block = if (shouldShowDamageFlash) Material.RED_CONCRETE.createBlockData() else normalBlockData
                    showingDamageFlash = shouldShowDamageFlash
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
    }

    private fun flashRed() {
        if (display?.isValid != true) return
        redUntilTick = Bukkit.getCurrentTick().toLong() + 8L
        particles.spawn(display!!.location.add(0.5, 0.5, 0.5), Particle.DAMAGE_INDICATOR, count = 6, spread = 0.3)
        sounds.play(display!!.location, Sound.BLOCK_WOOD_HIT, volume = 0.45f, pitch = 1.35f)
    }

    private fun clearDisguise() {
        disguiseGeneration++
        interaction?.let { disguiseOwners.remove(it.uniqueId) }
        interaction?.remove()
        display?.remove()
        stealth?.remove()
        interaction = null
        display = null
        stealth = null
        lockedLocation = null
    }

    private class Passive : BasePassive() {
        override val name = "<bold>보호색"
        override val description = listOf("<gray>패시브", "", "<gray>위장 중 블록의 피격 판정을 통해 대신 피해를 받을 수 있다.")
    }

    companion object {
        private val disguiseOwners = mutableMapOf<UUID, Chameleon>()

        fun handleDisguiseDamage(event: EntityDamageByEntityEvent): Boolean {
            val owner = disguiseOwners[event.entity.uniqueId] ?: return false
            event.isCancelled = true

            val directDamager = event.damager
            val attacker = when (directDamager) {
                is Player -> directDamager
                is Projectile -> directDamager.shooter as? Player
                else -> null
            } ?: return true
            val attackerData = findGameForPlayer(attacker)?.playerDatas?.filterIsInstance<PlayerData>()
                ?.find { it.uniqueId == attacker.uniqueId } ?: return true
            if (!attackerData.canDispatchClassHandlers() || !owner.playerData.canDispatchClassHandlers()) return true

            val path = if (directDamager is Projectile) DamagePath.RANGED_ATTACK else DamagePath.BASIC_ATTACK
            val baseDamage = if (directDamager is Player) {
                calculatePlayerAttackDamage(event, attacker)
            } else {
                event.damage
            }
            owner.playerData.damage(baseDamage, DamageType.Normal, attackerData, damagePath = path)

            // Interaction 엔티티는 일반 공격 대상처럼 공격 충전량을 소비하지 않으므로 직접 초기화한다.
            if (directDamager is Player) attacker.resetCooldown()
            return true
        }

        /**
         * Interaction 엔티티 공격은 서버가 플레이어 대상의 무기 피해를 계산하기 전에 끝날 수 있다.
         * 실제 플레이어를 공격했을 때와 같은 공격력, 충전량, 대인 인챈트 및 치명타를 복원한다.
         */
        private fun calculatePlayerAttackDamage(
            event: EntityDamageByEntityEvent,
            attacker: Player,
        ): Double {
            val attackStrength = attacker.getCooledAttackStrength(0.5f).coerceIn(0.0f, 1.0f)
            val attackStrengthDouble = attackStrength.toDouble()
            var baseDamage = (attacker.getAttribute(Attribute.ATTACK_DAMAGE)?.value ?: 1.0) *
                (0.2 + attackStrengthDouble * attackStrengthDouble * 0.8)
            if (event.isCritical || isVanillaCriticalAttack(attacker, attackStrength)) {
                baseDamage *= 1.5
            }

            val sharpnessLevel = attacker.inventory.itemInMainHand.getEnchantmentLevel(Enchantment.SHARPNESS)
            val enchantmentDamage = if (sharpnessLevel > 0) {
                (0.5 * sharpnessLevel + 0.5) * attackStrengthDouble
            } else {
                0.0
            }

            // Paper가 이미 올바른 피해를 제공한 경우에는 그 값을 보존한다.
            return maxOf(event.damage, baseDamage + enchantmentDamage)
        }

        private fun isVanillaCriticalAttack(attacker: Player, attackStrength: Float): Boolean =
            attackStrength > 0.9f && attacker.fallDistance > 0.0f &&
                !Tag.CLIMBABLE.isTagged(attacker.location.block.type) && !attacker.isInWater &&
                !attacker.hasPotionEffect(PotionEffectType.BLINDNESS) && !attacker.isInsideVehicle &&
                !attacker.isSprinting && !attacker.isGliding
    }
}
