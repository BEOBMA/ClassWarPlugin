package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ability.AbilityExecution

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.GameManager.gameClassList
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.AbilityRunnable
import org.beobma.classWarPlugin.ability.Targeting
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.gameClass.handler.*
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.SkillManager.getSkillId
import org.beobma.classWarPlugin.manager.SkillManager.use
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.skill.MovementSkill
import org.beobma.classWarPlugin.skill.Projectile
import org.beobma.classWarPlugin.util.HitboxUtil
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.event.player.PlayerInteractEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val CONTRACTOR_CONTRACT_COOLDOWN_SECONDS = 8
private const val CONTRACTOR_ORANGE_COOLDOWN_SECONDS = 12
private const val CONTRACTOR_YELLOW_COOLDOWN_SECONDS = 16
private const val CONTRACTOR_GREEN_COOLDOWN_SECONDS = 90

class Contractor : GameClass(), GameStatusHandler, ConfirmedHitHandler, WeaponInputHandler {
    override val classId = "contractor"
    override val name = "<gray>청부업자"
    override val rank = Rank.S
    override val classItemMaterial = Material.SULFUR_CUBE_BUCKET
    override var skills: List<Skill> = listOf(RedSkill(), OrangeSkill(), YellowSkill(), GreenSkill())
    override var passives: List<BasePassive> = listOf(Passive(), PassiveTwo())

    private data class Dagger(val location: Location, val expires: Long)
    private val daggers = mutableListOf<Dagger>()
    private val marked = mutableMapOf<UUID, Long>()
    private var stacks = 0
    private var empowered = false
    private var stabbing = false
    private var stabHit = false
    private inner class Processing : StatusAbnormality() {
        override val name = "<gold>처리"
        override val description = listOf("<gray>5스택에서 다음 기본 공격으로 단검을 생성합니다.")
        override val canRemove = false
        override val isClassMechanic = true
        override var maxPower: Int? = 5
        override var duration: Int? = null
    }
    private fun syncStacks() { playerData.getOrCreateStatus(playerData) { Processing() }.updatePower(stacks) }
    override fun onBattleStart() {
        daggers.clear(); marked.clear(); stacks = 0; empowered = false; syncStacks()
        object : AbilityRunnable(abilityScope) {
            override fun run() {
                daggers.removeAll { it.expires <= game.combatTick }
                marked.entries.removeIf { it.value <= game.combatTick }
                daggers.toList().filter { it.location.world == player.world }.forEach { dagger ->
                    particles.spawn(dagger.location, Particle.END_ROD)
                    if (dagger.location.distanceSquared(player.location) <= 2.25) recover(dagger)
                }
            }
            override fun onCancel() { daggers.clear(); marked.clear() }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 2L)
    }
    override fun onGameTimePasses() {}
    private fun spawnDagger(location: Location) {
        if (daggers.size >= 16) daggers.removeAt(0)
        daggers += Dagger(location.clone(), game.combatTick + 300)
        particles.spawn(location, Particle.CRIT, count = 8, spread = 0.2)
    }
    private fun behind(target: EntityData): Location {
        val delta = target.entity.location.toVector().subtract(player.location.toVector()).setY(0.0)
        if (delta.lengthSquared() < 0.001) delta.setZ(1.0)
        return target.entity.location.add(delta.normalize().multiply(1.2))
    }
    private fun recover(dagger: Dagger) {
        if (!daggers.remove(dagger)) return
        CooldownManager.resetCooldown(player, skills[1])
        val target = Targeting.select(playerData, TargetType.Enemy).filter {
            it.entity.location.distanceSquared(dagger.location) <= 64 && player.hasLineOfSight(it.entity)
        }.sortedWith(compareByDescending<EntityData> { (marked[it.entity.uniqueId] ?: 0) > game.combatTick }
            .thenBy { (it.entity as? LivingEntity)?.health ?: Double.MAX_VALUE }).firstOrNull() ?: return
        particles.line(dagger.location, target.entity.location.add(0.0, 1.0, 0.0), Particle.CRIT, 0.2)
        repeat(if ((marked[target.entity.uniqueId] ?: 0) > game.combatTick) 3 else 1) {
            target.damage(1.0, DamageType.True, playerData)
        }
    }
    override fun onConfirmedHit(context: DamageContext) {
        if (context.target == playerData) return
        if (context.path == DamagePath.SKILL) {
            stacks = (stacks + 1).coerceAtMost(5)
            if (stabbing) { stabHit = true; empowered = true }
        } else if (context.path.isBasicAttack && !context.secondaryAttack && stacks >= 5) {
            stacks = 0; spawnDagger(behind(context.target))
        }
        syncStacks()
    }
    override fun onWeaponRightClick(event: PlayerInteractEvent) {
        event.isCancelled = true
        val skill = skills[1]
        val item = player.inventory.contents.filterNotNull().firstOrNull { getSkillId(it, player.uniqueId)?.let(skill::matchesId) == true } ?: return
        playerData.use(skill, item)
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "contractor/red-skill"
        override val name = "<bold>찌르기"
        override val description = listOf(
            "<gray>바라보는 방향으로 잛게 칼을 찔러 적에게 3의 피해를 입힌다.",
            "<gray>적중 시 재사용 대기 시간이 3초 감소하며, 다음 찌르기가 강화된다.",
            "",
            "<gray>강화된 찌르기 발동 시 사거리가 소폭 증가하고",
            "<gray>적중 여부와 관계 없이 사거리 끝자락에 단검을 생성한다."
        )
        override val cooldown = CONTRACTOR_CONTRACT_COOLDOWN_SECONDS


        override fun isUseSuccess(): Boolean { return true }

        override fun use(): Boolean {
            val range = if (empowered) 4.5 else 3.0
            val enhanced = empowered; empowered = false; stabHit = false
            val start = player.eyeLocation
            val target = playerData.shotLaserGetEntityData(range, TargetType.Enemy, false)
            val end = start.world.rayTraceBlocks(start, start.direction, range)?.hitPosition?.toLocation(start.world)
                ?: start.clone().add(start.direction.multiply(range))
            particles.line(start, end, Particle.CRIT, 0.15)
            stabbing = true
            try { target?.damage(3.0, DamageType.Normal, playerData) } finally { stabbing = false }
            if (stabHit) multiplyCurrentCooldown(5.0 / 8.0)
            if (enhanced) spawnDagger(end.clone().subtract(0.0, 1.0, 0.0))
            return true
        }
    }

    private inner class OrangeSkill : Skill(), MovementSkill {
        override val definitionId = "contractor/orange-skill"
        override val name = "<bold>순보"
        override val description = listOf(
            "<gray>8칸 내의 바라보는 적의 뒤 또는 단검의 위치로 순간이동한다.",
            "<gray>이동 경로에 있던 모든 적에게 2의 피해를 입힌다.",
            "<gray>단검을 회수하면 이 스킬의 재사용 대기 시간이 초기화된다.",
            "",
            "<dark_gray>이 스킬 대신 검을 우클릭하여 사용할 수도 있다."
        )
        override val cooldown = CONTRACTOR_ORANGE_COOLDOWN_SECONDS


        override fun isUseSuccess(): Boolean { return true }

        override fun use(): Boolean {
            val start = player.location
            val dagger = daggers.filter { it.location.world == player.world && it.location.distanceSquared(start) <= 64.0 }
                .filter { val delta = it.location.toVector().subtract(player.eyeLocation.toVector());
                    delta.lengthSquared() < 0.1 || delta.normalize().dot(player.eyeLocation.direction) >= 0.85 }
                .minByOrNull { it.location.distanceSquared(start) }
            val target = if (dagger == null) playerData.shotLaserGetEntityData(8.0, TargetType.Enemy, false) else null
            val destination = dagger?.location?.clone() ?: target?.let(::behind) ?: return false
            destination.yaw = start.yaw; destination.pitch = start.pitch
            if (!destination.block.isPassable || !destination.clone().add(0.0, 1.0, 0.0).block.isPassable) return false
            if (!player.teleport(destination)) return false
            player.fallDistance = 0f
            Targeting.select(playerData, TargetType.Enemy).filter {
                HitboxUtil.intersectsSegment(it.entity.boundingBox, start.toVector(), destination.toVector(), 0.8)
            }.forEach { it.damage(2.0, DamageType.Normal, playerData) }
            if (dagger != null) { recover(dagger); multiplyCurrentCooldown(0.0) }
            return true
        }
    }

    private inner class YellowSkill : Skill(), MovementSkill {
        override val definitionId = "contractor/yellow-skill"
        override val name = "<bold>암살"
        override val description = listOf(
            "<gray>바라보는 방향으로 단검을 던지고 자신은 약간 뒤로 이동한다.",
            "<gray>단검이 적에게 적중하면 2의 피해를 입히고, 적 뒤에 단검을 생성한다.",
            "<gray>이 스킬에 적중한 적은 5초간 단검을 회수하여 입히는 피해가 추가로 2번 적중한다."
        )
        override val cooldown = CONTRACTOR_YELLOW_COOLDOWN_SECONDS


        override fun isUseSuccess(): Boolean { return true }

        override fun use(): Boolean {
            val origin = player.eyeLocation
            player.velocity = origin.direction.multiply(-0.5).setY(0.15)
            object : Projectile() {
                override var location = origin
                override var targetType = TargetType.Enemy
                override var speed = 1.2
                override var isWallHit = true
                override var isPlayerHit = true
                override val isPlayerHitRemove = true
                override var time: Int? = 2
                override val itemDisplayItem = ItemStack(Material.IRON_SWORD)
                override fun onProjectileEntityHit(hitEntityData: EntityData, location: Location) {
                    hitEntityData.damage(2.0, DamageType.Normal, playerData)
                    marked[hitEntityData.entity.uniqueId] = game.combatTick + 100
                    spawnDagger(behind(hitEntityData))
                }
                override fun onProjectileMove(location: Location) { particles.spawn(location, Particle.CRIT) }
            }.spawnProjectile(playerData)
            return true
        }
    }

    //        †
    //
    //    †   나   †
    //
    //        †
    // 위와 같은 형태
    private inner class GreenSkill : Skill() {
        override val definitionId = "contractor/green-skill"
        override val name = "<bold>장부 정리"
        override val description = listOf(
            "<gray>자신 주변 십자 범위로 4개의 단검을 생성한다.",
            "<gray>주변 모든 적에게 2의 피해를 입힌다.",
            "<gray>십자 범위 내에 벽이 존재한다면 단검은 벽에서 멈춰서 생성된다."
        )
        override val cooldown = CONTRACTOR_GREEN_COOLDOWN_SECONDS


        override fun isUseSuccess(): Boolean { return true }

        override fun use(): Boolean {
            val start = player.location.add(0.0, 0.3, 0.0)
            repeat(4) { index ->
                val direction = org.bukkit.util.Vector(1.0, 0.0, 0.0).rotateAroundY(index * Math.PI / 2)
                val hit = start.world.rayTraceBlocks(start, direction, 4.0)?.hitPosition
                val end = hit?.subtract(direction.clone().multiply(0.3))?.toLocation(start.world)
                    ?: start.clone().add(direction.multiply(4.0))
                spawnDagger(end)
            }
            playerData.radius(player.location, TargetType.Enemy, 4.0, false).filter { player.hasLineOfSight(it.entity) }
                .forEach { it.damage(2.0, DamageType.Normal, playerData) }
            return true
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>회수"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>단검에 닿으면 단검 주위 8칸 이내의 적 하나에게 단검을 던져 1의 {keyword:TrueDamage}를 입힌다.",
            "",
            "<dark_gray>암살 스킬에 적중된 적을 우선적으로 공격하며, 체력이 낮은 적을 우선적으로 공격한다."
        )
    }

    private class PassiveTwo : BasePassive() {
        override val name = "<bold>깔끔한 처리"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>적에게 스킬로 피해를 입힐 때마다 처리 스택을 1 얻는다. (최대 5)",
            "<gray>처리 중첩이 최대치일 때 소모하여 다음 기본 공격 적중 시 적중한 적 뒤에 단검을 생성한다."
        )
    }
}
