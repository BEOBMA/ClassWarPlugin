package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.gameClass.handler.*
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.Disarm
import org.beobma.classWarPlugin.util.*
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val WARCORRESPONDENT_RED_SKILL_COOLDOWN_SECONDS = 20

class WarCorrespondent : GameClass(), WeaponInputHandler, OnSkillUseHandler, GameStatusHandler, MovementInputHandler {
    override val classId = "warcorrespondent"
    override val name = "<gray>종군기자"
    override val rank = Rank.S
    override val classItemMaterial = Material.OBSERVER
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    data class Report(val location: Location, val death: Boolean, val tick: Long, val subject: UUID)
    private val reports = mutableListOf<Report>()
    private var camera: BukkitTask? = null
    private var cameraReady = 0L
    private var filming = false
    private var stacks = 0
    private val broadcasting get() = stacks >= 3
    fun mapReports(): List<Report> = reports.toList()

    private inner class Coverage : StatusAbnormality() {
        override val name = "<gold>취재"
        override val description = listOf("<gray>촬영을 3회 완료하면 방송 상태가 됩니다.")
        override val canRemove = false
        override val isClassMechanic = true
        override var maxPower: Int? = 3
        override var duration: Int? = null
        override fun actionBarText() = if (broadcasting) "<red>● 방송 중" else "<gold>촬영 $stacks / 3"
    }
    private fun syncCoverage() { playerData.getOrCreateStatus(playerData) { Coverage() }.updatePower(stacks) }
    override fun onBattleStart() {
        stacks = 0; reports.clear(); syncCoverage()
        abilityScope.resources.own { camera?.cancel(); camera = null; reports.clear() }
    }
    override fun onGameTimePasses() { reports.removeAll { !it.death && game.combatTick - it.tick > 1200 } }
    override fun onSuspend() { camera?.cancel() }
    override fun onPlayerInput(event: org.bukkit.event.player.PlayerInputEvent) {
        if (event.input.isJump || event.input.isForward || event.input.isBackward || event.input.isLeft || event.input.isRight) camera?.cancel()
    }
    override fun onSkillUse(event: PlayerSkillUseEvent) { camera?.cancel() }

    override fun onWeaponRightClick(event: PlayerInteractEvent) {
        event.isCancelled = true
        startCamera()
    }
    override fun onWeaponInteractEntity(event: org.bukkit.event.player.PlayerInteractEntityEvent) {
        event.isCancelled = true
        startCamera(event.rightClicked.uniqueId)
    }
    private fun startCamera(targetId: UUID? = null) {
        if (camera != null || filming || game.combatTick < cameraReady || !playerStatus.canAttack || playerData.hasStatus<Disarm>()) return
        val target = if (targetId == null) playerData.shotLaserGetEntityData(8.0, TargetType.Enemy, false)
            else Targeting.select(playerData, TargetType.Enemy, includeStealth = false).firstOrNull {
                it.entity.uniqueId == targetId && it.entity.location.distanceSquared(player.location) <= 64.0 && player.hasLineOfSight(it.entity)
            }
        if (target == null) return
        val origin = player.location
        camera = object : AbilityRunnable(abilityScope, cancelOnDisconnect = true) {
            var ticks = 0
            override fun run() {
                if (ticks >= 60 || player.world != origin.world || player.location.distanceSquared(origin) > 0.0001 ||
                    !playerStatus.canAttack || playerData.hasStatus<Disarm>() ||
                    target !in Targeting.select(playerData, TargetType.Enemy) ||
                    target.entity.location.distanceSquared(player.location) > 64.0 || !player.hasLineOfSight(target.entity)) {
                    cancel(); return
                }
                ticks++
                target.damage(if (broadcasting) 0.1 else 0.05, DamageType.True, playerData, isInvincibilityTimeIgnore = true)
                if (ticks % 4 == 0) particles.line(player.eyeLocation, target.entity.location.add(0.0, 1.0, 0.0), Particle.END_ROD, 0.4)
            }
            override fun onCancel() { cameraReady = game.combatTick + ticks.coerceIn(20, 60); camera = null }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L)
    }

    companion object {
        private fun observers(game: Game) = game.playerDatas.filterIsInstance<PlayerData>()
            .filter { !it.entityStatus.isDead }.flatMap { AbilityTree.nodes(it.gameClasses, activeOnly = true) }
            .filterIsInstance<WarCorrespondent>()
        fun recordCombat(context: DamageContext) {
            val target = context.target as? PlayerData ?: return
            if (target == context.attacker) return
            observers(context.attacker.game).filter { it.playerData != target && it.playerData != context.attacker }.forEach {
                it.reports.removeAll { report -> !report.death && report.subject == target.uniqueId }
                it.reports += Report(target.player.location.clone(), false, it.game.combatTick, target.uniqueId)
            }
        }
        fun recordDeath(data: PlayerData) {
            observers(data.game).filter { it.playerData != data }.forEach {
                it.reports += Report(data.player.location.clone(), true, it.game.combatTick, data.uniqueId)
                while (it.reports.size > 128) it.reports.removeAt(0)
            }
        }
    }

    private class Weapon : BaseWeapon() {
        override val name = "<gray>카메라"
        override val description = listOf(
            "<gray>8칸 내의 적을 우클릭하면 촬영 상태에 들어간다.",
            "<gray>촬영 상태에서 적에게 매 틱마다 무적 시간을 무시하는 0.05의 {keyword:TrueDamage}를 입힌다.",
            "<gray>자신이 움직이거나, 스킬을 사용하거나, 점프하거나, 적이 사거리에서 벗어나면 촬영 상태는 종료된다.",
            "<gray>촬영 상태는 최대 3초간 지속되며, 지속된 시간에 비례하여 재사용 대기 시간이 적용된다. (최소 1, 최대 3)",
            "",
            "<gray>자신이 방송 상태라면 대신 적에게 매 틱마다 무적 시간을 무시하는 0.1의 {keyword:TrueDamage}를 입힌다."
        )
        override val material = Material.OBSERVER
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "warcorrespondent/red-skill"
        override val name = "<bold>방송"
        override val description = listOf(
            "<gray>4초간 바라보는 방향의 16칸, 120도 부채꼴 범위를 촬영한다.",
            "<gray>촬영 중 아래 조건을 만족하면 촬영을 완료하고 촬영 스택을 1 얻는다.",
            "<gray>  - 자신을 제외한 적 플레이어 2명 이상을 1초 이상 촬영",
            "<gray>  - 적 플레이어가 사망한 위치를 2초 이상 촬영",
            "",
            "<gray>촬영 스택이 3스택이 되면 자신은 방송 상태에 돌입한다.",
            "",
            "<gray>이 스킬 사용 중 기본 공격, 스킬을 사용할 수 없다.",
            "<gray>이 스킬은 Y축의 영향을 받지 않고 촬영할 수 있다."
        )
        override val cooldown = WARCORRESPONDENT_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            if (filming || broadcasting) return false
            camera?.cancel()
            filming = true
            val lease = ControlLease(abilityScope, playerStatus)
            lease.allow(Control.ATTACK, false); lease.allow(Control.SKILL, false)
            object : AbilityRunnable(abilityScope, cancelOnDisconnect = true) {
                var ticks = 0
                val players = mutableMapOf<UUID, Int>()
                val deaths = mutableMapOf<Report, Int>()
                override fun run() {
                    fun inFrame(location: Location): Boolean {
                        if (location.world != player.world) return false
                        val delta = location.toVector().subtract(player.location.toVector()).setY(0.0)
                        val look = player.location.direction.setY(0.0)
                        return delta.lengthSquared() <= 256.0 && (delta.lengthSquared() < 0.01 ||
                            (look.lengthSquared() > 0.001 && look.normalize().dot(delta.normalize()) >= 0.5))
                    }
                    val visible = Targeting.select(playerData, TargetType.Enemy, includeStealth = false)
                        .filterIsInstance<PlayerData>().filter { inFrame(it.player.location) }.map { it.uniqueId }.toSet()
                    players.keys.retainAll(visible)
                    visible.forEach { players[it] = (players[it] ?: 0) + 1 }
                    val corpses = reports.filter { it.death && inFrame(it.location) }.toSet()
                    deaths.keys.retainAll(corpses)
                    corpses.forEach { deaths[it] = (deaths[it] ?: 0) + 1 }
                    if (players.values.count { it >= 20 } >= 2 || deaths.values.any { it >= 40 }) {
                        stacks = (stacks + 1).coerceAtMost(3); syncCoverage(); cancel(); return
                    }
                    if (++ticks >= 80) cancel()
                    if (ticks % 5 == 0) {
                        val start = player.eyeLocation
                        for (angle in -60..60 step 15) particles.line(start, start.clone().add(
                            start.direction.setY(0.0).normalize().rotateAroundY(Math.toRadians(angle.toDouble())).multiply(16.0)), Particle.COMPOSTER, 0.8)
                    }
                }
                override fun onCancel() { filming = false; lease.close() }
            }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L)
            return true
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>취재"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>자신은 지도를 통해 다른 플레이어끼리 전투가 발생한 위치를 알 수 있다.",
            "<gray>또한 플레이어가 사망한 위치를 알 수 있다."
        )
    }
}
