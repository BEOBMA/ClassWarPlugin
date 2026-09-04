package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ability.Control
import org.beobma.classWarPlugin.ability.ControlLease

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.EntityStatus
import org.beobma.classWarPlugin.entity.DamageRedirectEntityData
import org.beobma.classWarPlugin.entity.PlayerOwnedEntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.GameManager.canDispatchClassHandlers
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.getTargetCandidates
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.MovementSkill
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.MoveSpeedIncrease
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.min

class Phantom : GameClass(), GameEndHandler, PlayerDeathHandler {
    override val classId = "phantom"
    override val name = "<gray>팬텀"
    override val rank = Rank.S
    override val classItemMaterial = Material.PHANTOM_MEMBRANE
    private val departure = DepartureSkill()
    override var skills: List<Skill> = listOf(departure)
    override var passives: List<BasePassive> = listOf(SlashPassive())
    private var active = false
    private var returning = false
    private var body: ArmorStand? = null
    private var bodyData: PhantomBodyData? = null
    private var stealth: Stealth? = null
    private var speed: MoveSpeedIncrease? = null
    private var cooldownItem: ItemStack? = null
    private var returnTask: BukkitTask? = null
    private var returnStateSaved = false
    private var controls: ControlLease? = null
    private var savedGravity = true
    private var savedInvulnerable = false
    private var savedCollidable = true
    private val marks = linkedMapOf<EntityData, PhantomSlashMarkStatus>()

    override fun onGameEnd() = cleanup(releaseMarks = false)
    override fun onPlayerDeath() = cleanup(releaseMarks = false)

    private inner class DepartureSkill : Skill(), MovementSkill {
        override val definitionId = "phantom/departure-skill"
        override val name = "<bold>이탈"
        override val description = listOf(
            "<gray>자신의 육체를 남기고 전방으로 도약하며, 20초간 {keyword:Stealth} 상태가 되고 <gold><bold>이동 속도가 20% 증가</bold><gray>한다.",
            "<gray>남겨진 육체가 공격이나 스킬에 적중하면 자신이 대신 피해를 받는다.",
            "{keyword:Stealth} 상태에서 가장 가까운 적에게 가는 경로가 입자로 표시된다.",
            "<gray>상대는 {keyword:Stealth} 상태인 자신과 가까워지면 주변에 특수한 입자가 표시된다.",
            "<gray>이 스킬을 재사용하면 모든 벽을 통과해 빠르게 육체로 돌아온다."
        )
        override val cooldown = 80
        override val isOnOffSKill = true

        override fun use(): Boolean {
            if (active) {
                if (!returning) beginReturn()
                return true
            }
            cooldownItem = player.inventory.itemInMainHand.clone()
            beginDeparture()
            multiplyCurrentCooldown(0.0)
            return true
        }
    }

    private fun beginDeparture() {
        active = true
        returning = false
        val origin = player.location.clone()
        val spawnedBody = player.world.spawn(origin, ArmorStand::class.java).apply {
            isPersistent = false
            setGravity(false)
            isInvulnerable = false
            isCollidable = false
            setArms(true)
            setBasePlate(false)
            equipment.setHelmet(player.inventory.helmet.clone())
            equipment.setChestplate(player.inventory.chestplate.clone())
            equipment.setLeggings(player.inventory.leggings.clone())
            equipment.setBoots(player.inventory.boots.clone())
            equipment.setItemInMainHand(player.inventory.itemInMainHand.clone())
        }
        body = spawnedBody
        bodyData = PhantomBodyData(playerData, spawnedBody).also(game.playerDatas::add)
        activeBodies[spawnedBody.uniqueId] = this
        stealth = (playerData.addStatus(Stealth(), playerData) as Stealth).also { it.applyStatus(duration = 20, powerSet = 1) }
        speed = (playerData.addStatus(MoveSpeedIncrease(), playerData) as MoveSpeedIncrease).also { it.applyStatus(duration = 20, powerSet = 20) }
        player.velocity = player.eyeLocation.direction.normalize().multiply(1.2).setY(0.38)
        particles.spawn(origin.clone().add(0.0, 1.0, 0.0), Particle.SOUL, count = 65, spread = 0.75, speed = 0.14)
        sounds.play(origin, Sound.ENTITY_PHANTOM_FLAP, volume = 0.85f, pitch = 0.65f)
        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            var ticks = 0
            override fun run() {
                if (!active || !player.isOnline || playerStatus.isDead) {
                    if (active) cleanup(releaseMarks = false)
                    cancel()
                    return
                }
                if (ticks % 10 == 0) {
                    showPathToNearestEnemy()
                    warnNearbyEnemies()
                }
                if (ticks >= 400) {
                    beginReturn()
                    CooldownManager.setCooldown(player, departure, cooldownItem ?: ItemStack(Material.RED_DYE), departure.cooldown * 20)
                    cancel()
                    return
                }
                ticks += 2
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }

    private fun beginReturn() {
        val anchor = body?.location?.clone() ?: run {
            cleanup(releaseMarks = false)
            return
        }
        if (returning || !player.isOnline || playerStatus.isDead) return
        returning = true
        returnStateSaved = true
        savedGravity = player.hasGravity()
        savedInvulnerable = player.isInvulnerable
        savedCollidable = player.isCollidable
        controls?.close()
        controls = ControlLease(abilityScope, playerStatus)
        controls?.allow(Control.MOVE, false)
        controls?.allow(Control.ATTACK, false)
        controls?.allow(Control.SKILL, false)
        player.setGravity(false)
        player.isInvulnerable = true
        player.isCollidable = false
        player.velocity = Vector()
        particles.spawn(player, Particle.REVERSE_PORTAL, count = 42, spread = 0.55, speed = 0.12)
        sounds.play(player, Sound.ENTITY_PHANTOM_SWOOP, volume = 0.9f, pitch = 1.25f)

        returnTask = playerData.trackTask(object : BukkitRunnable(abilityScope) {
            var ticks = 0
            override fun run() {
                if (!active || !returning || !player.isOnline || playerStatus.isDead || body?.isValid != true) {
                    cleanup(releaseMarks = false)
                    cancel()
                    return
                }
                val from = player.location.clone()
                val difference = anchor.toVector().subtract(from.toVector())
                val distance = difference.length()
                val step = (1.6 + ticks * 0.11).coerceAtMost(4.2)
                if (distance <= step || ticks >= 80) {
                    val destination = anchor.clone().apply {
                        yaw = from.yaw
                        pitch = from.pitch
                    }
                    player.teleport(destination)
                    renderReturnBurst(destination)
                    cleanup(releaseMarks = true)
                    cancel()
                    return
                }
                val destination = from.clone().add(difference.normalize().multiply(step))
                destination.yaw = from.yaw
                destination.pitch = from.pitch
                player.teleport(destination)
                player.velocity = Vector()
                particles.line(
                    from.clone().add(0.0, 1.0, 0.0),
                    destination.clone().add(0.0, 1.0, 0.0),
                    Particle.SOUL_FIRE_FLAME,
                    0.38,
                )
                particles.spawn(destination.clone().add(0.0, 1.0, 0.0), Particle.REVERSE_PORTAL, count = 9, spread = 0.35, speed = 0.045)
                if (ticks % 4 == 0) sounds.play(destination, Sound.ENTITY_PHANTOM_FLAP, volume = 0.36f, pitch = 1.45f)
                ticks++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
    }

    private fun showPathToNearestEnemy() {
        val target = playerData.getTargetCandidates().asSequence()
            .filter { it != playerData && !it.entityStatus.isDead && it.entity.world == player.world }
            .filter { it !is PlayerData || playerData.isEnemyOf(it) }
            .minByOrNull { player.boundingBox.center.distanceSquared(it.entity.boundingBox.center) } ?: return
        val start = player.eyeLocation.toVector()
        val difference = target.entity.boundingBox.center.clone().subtract(start)
        val length = difference.length()
        if (length < 0.1) return
        val direction = difference.normalize()
        var distance = 0.0
        while (distance <= min(length, 48.0)) {
            particles.spawnTo(player, start.clone().add(direction.clone().multiply(distance)).toLocation(player.world), Particle.SOUL_FIRE_FLAME)
            distance += 1.2
        }
    }

    private fun warnNearbyEnemies() {
        game.playerDatas.filterIsInstance<PlayerData>()
            .filter { it != playerData && !it.entityStatus.isDead && it.player.world == player.world }
            .filter { it.player.boundingBox.center.distanceSquared(player.boundingBox.center) <= 36.0 }
            .forEach { enemy ->
                particles.spawnTo(enemy.player, player.location.clone().add(0.0, 1.0, 0.0), Particle.SCULK_SOUL, 8, 0.75, 0.03)
                sounds.playTo(enemy.player, Sound.ENTITY_PHANTOM_AMBIENT, 0.35f, 0.55f)
            }
    }

    private fun cleanup(releaseMarks: Boolean) {
        if (!active && !returning && body == null && marks.isEmpty()) return
        active = false
        returning = false
        returnTask?.cancel()
        returnTask = null
        body?.let { armorStand ->
            activeBodies.remove(armorStand.uniqueId)
            armorStand.remove()
        }
        body = null
        bodyData?.let(game.playerDatas::remove)
        bodyData = null
        stealth?.remove(); stealth = null
        speed?.remove(); speed = null
        restoreReturnState()
        marks.toMap().forEach { (target, status) ->
            val amount = status.power
            status.remove()
            if (releaseMarks && amount > 0 && !target.entityStatus.isDead) {
                target.damage(amount * 0.1, DamageType.True, playerData, damagePath = DamagePath.SKILL)
                renderReleasedSlashes(target, amount)
            }
        }
        marks.clear()
    }

    private fun restoreReturnState() {
        if (!returnStateSaved) return
        returnStateSaved = false
        if (!player.isOnline) return
        controls?.close(); controls = null
        player.setGravity(savedGravity)
        player.isInvulnerable = savedInvulnerable
        player.isCollidable = savedCollidable
        player.velocity = Vector()
    }

    private fun renderReturnBurst(location: org.bukkit.Location) {
        val center = location.clone().add(0.0, 1.0, 0.0)
        particles.spawn(center, Particle.SOUL, count = 85, spread = 0.85, speed = 0.16)
        particles.spawn(center, Particle.REVERSE_PORTAL, count = 55, spread = 0.7, speed = 0.14)
        particles.spawn(center, Particle.SWEEP_ATTACK, count = 12, spread = 0.8, speed = 0.1)
        sounds.play(location, Sound.ENTITY_PHANTOM_BITE, volume = 0.95f, pitch = 0.7f)
    }

    private fun renderReleasedSlashes(target: EntityData, amount: Int) {
        val center = target.entity.boundingBox.center.toLocation(target.entity.world)
        val halfWidth = 1.15
        listOf(
            Vector(halfWidth, 0.85, 0.0),
            Vector(-halfWidth, 0.85, 0.0),
            Vector(0.0, 0.85, halfWidth),
        ).forEach { offset ->
            particles.line(
                center.clone().add(offset),
                center.clone().subtract(offset),
                Particle.SWEEP_ATTACK,
                0.28,
            )
        }
        particles.spawn(center, Particle.SWEEP_ATTACK, count = (amount * 5).coerceIn(10, 48), spread = 0.8, speed = 0.12)
        particles.spawn(center, Particle.CRIT, count = (amount * 4).coerceIn(12, 44), spread = 0.7, speed = 0.16)
        particles.spawn(center, Particle.ENCHANT, count = 26, spread = 0.75, speed = 0.08)
        sounds.play(target.entity, Sound.ENTITY_PLAYER_ATTACK_SWEEP, volume = 1.0f, pitch = 0.58f)
        sounds.play(target.entity, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, volume = 0.45f, pitch = 1.8f)
    }

    private inner class SlashPassive : BasePassive(), OnHitHandler {
        override val name = "<bold>넌 이미 베여있다"
        override val description = listOf(
            "<gray>패시브", "", "<gray>이탈 스킬 사용 중, 적에게 피해를 입히면 피해량이 0으로 고정된다.",
            "<gray>대신 피해를 입힐 때마다 검흔을 1 부여한다.",
            "<gray>이탈 스킬 종료 후 몸으로 돌아온 뒤, 검흔을 베어 수치당 0.1의 고정 피해를 입힌다."
        )
        override fun onHit(context: DamageContext) {
            if (!active || returning || context.target == playerData) return
            context.isCancelled = true
            val mark = context.target.getOrCreateStatus(playerData) { PhantomSlashMarkStatus() }
            mark.increasePower(1)
            marks[context.target] = mark
            particles.spawn(context.target.entity, Particle.SWEEP_ATTACK, count = 2, spread = 0.25, speed = 0.02)
        }
    }

    companion object {
        private val activeBodies = mutableMapOf<UUID, Phantom>()

        /** 팬텀이 남긴 육체에 가해진 바닐라 근접·투사체 피해를 실제 플레이어에게 전달합니다. */
        fun handleBodyDamage(event: EntityDamageByEntityEvent): Boolean {
            val phantom = activeBodies[event.entity.uniqueId] ?: return false
            event.isCancelled = true
            if (!phantom.active || phantom.playerStatus.isDead || phantom.body?.isValid != true) return true

            val attacker = when (val damager = event.damager) {
                is Player -> damager
                is Projectile -> damager.shooter as? Player
                else -> null
            } ?: return true
            val attackerGame = findGameForPlayer(attacker) ?: return true
            if (attackerGame !== phantom.game) return true
            val attackerData = attackerGame.playerDatas.filterIsInstance<PlayerData>()
                .firstOrNull { it.uniqueId == attacker.uniqueId }
                ?: return true
            if (!attackerData.canDispatchClassHandlers()) return true

            val path = if (event.damager is Projectile) DamagePath.RANGED_ATTACK else DamagePath.BASIC_ATTACK
            phantom.bodyData?.damage(
                event.damage,
                DamageType.Normal,
                attackerData,
                damagePath = path,
            )
            phantom.body?.playHurtAnimation(0.0F)
            return true
        }
    }
}

private class PhantomBodyData(
    override val ownerData: PlayerData,
    override val entity: ArmorStand,
) : EntityData(), PlayerOwnedEntityData, DamageRedirectEntityData {
    override val game = ownerData.initGame
    override val entityStatus = object : EntityStatus() {}
    override val bukkitTasks: MutableList<BukkitTask> = mutableListOf()
    override val statusAbnormalitys: MutableList<StatusAbnormality> = mutableListOf()

    override fun redirectDamage(
        damage: Double,
        damageType: DamageType,
        damager: PlayerData,
        isInvincibilityTimeIgnore: Boolean,
        bypassShield: Boolean,
        damagePath: DamagePath?,
        armorIgnoreRatio: Double,
    ) {
        ownerData.damage(
            damage,
            damageType,
            damager,
            isInvincibilityTimeIgnore,
            bypassShield,
            damagePath,
            armorIgnoreRatio,
        )
    }
}

private class PhantomSlashMarkStatus : StatusAbnormality() {
    override val name = "<dark_aqua><bold>검흔</bold><gray>"
    override val description = listOf("<gray>팬텀이 육체로 돌아올 때 수치만큼 고정 피해를 입는다.")
    override val canRemove = true
    override var power = 0
    override var maxPower: Int? = null
    override val showMaxPower = false
    override var duration: Int? = null
}
