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
import org.beobma.classWarPlugin.gameClass.warCorrespondent.RecordingGeometry
import org.beobma.classWarPlugin.gameClass.warCorrespondent.RecordingProgress
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.Disarm
import org.beobma.classWarPlugin.status.list.Stun
import org.beobma.classWarPlugin.status.handler.StatusPlayerMoveHandler
import org.beobma.classWarPlugin.util.*
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.Color
import org.beobma.classWarPlugin.effect.CombatVisuals
import org.bukkit.util.Vector
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val WARCORRESPONDENT_RED_SKILL_COOLDOWN_SECONDS = 20

class WarCorrespondent : GameClass(), WeaponInputHandler, OnSkillUseHandler, GameStatusHandler, MovementInputHandler, StatusPlayerMoveHandler {
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
    private var cameraCooldownDisplay: BukkitTask? = null
    private var recording: BukkitTask? = null
    private var recordingAim: RecordingGeometry? = null
    private var filming = false
    private var stacks = 0
    private val broadcasting get() = stacks >= 3
    private val recordingColor = Particle.DustOptions(Color.fromRGB(255, 65, 65), 1.1f)
    private val fieldColor = Particle.DustOptions(Color.fromRGB(100, 145, 255), 0.85f)
    private val screenColor = Particle.DustOptions(Color.fromRGB(95, 110, 245), 1.0f)
    private val borderColor = Particle.DustOptions(Color.fromRGB(125, 230, 255), 1.15f)
    private val progressColor = Particle.DustOptions(Color.fromRGB(90, 255, 180), 1.1f)
    private val remainingColor = Particle.DustOptions(Color.fromRGB(75, 90, 120), 0.9f)
    fun mapReports(): List<Report> = reports.toList()

    private inner class Coverage : StatusAbnormality() {
        override val name = Keyword.PhotographyStack.string
        override val description = listOf(Keyword.PhotographyStack.requireDescription())
        override val canRemove = false
        override val isClassMechanic = true
        override var maxPower: Int? = 3
        override var duration: Int? = null
        override fun actionBarText() = "$name <gold>$stacks / 3" + if (broadcasting) " <red><bold>● 방송 중</bold>" else ""
    }
    private fun syncCoverage() { playerData.getOrCreateStatus(playerData) { Coverage() }.updatePower(stacks) }
    override fun onBattleStart() {
        stacks = 0; reports.clear(); syncCoverage()
        abilityScope.resources.own {
            camera?.cancel(); camera = null
            recording?.cancel(); recording = null; recordingAim = null
            cameraCooldownDisplay?.cancel(); cameraCooldownDisplay = null
            reports.clear()
        }
    }
    override fun onGameTimePasses() { reports.removeAll { !it.death && game.combatTick - it.tick > 1200 } }
    override fun onSuspend() { camera?.cancel(); recording?.cancel() }
    override fun onResume() {
        player.setCooldown(Material.OBSERVER, (cameraReady - game.combatTick).coerceIn(0, 60).toInt())
    }
    override fun onPlayerMove(event: org.bukkit.event.player.PlayerMoveEvent, playerData: PlayerData) {
        val aim = recordingAim ?: return
        event.to = aim.lockView(event.to)
    }
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
        drawCameraRange(origin)
        sounds.play(player, Sound.BLOCK_PISTON_CONTRACT, volume = 0.35f, pitch = 1.8f)
        particles.spawn(player.eyeLocation.clone().add(player.eyeLocation.direction.multiply(0.45)), Particle.ELECTRIC_SPARK, count = 7, spread = 0.1)
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
                if (isCancelled || abilityScope.isClosed) return
                if (ticks % 8 == 0) {
                    val lens = player.eyeLocation.add(player.eyeLocation.direction.multiply(0.55))
                    CombatVisuals.tracer(lens, target.entity.boundingBox.center.toLocation(player.world),
                        if (broadcasting) CombatVisuals.GOLD else CombatVisuals.CYAN)
                }
                if (ticks % 4 == 0) drawProgressRing(target.entity.location, ticks / 60.0)
                if (ticks % 5 == 0) drawCameraRange(origin)
                if (ticks % 12 == 0) {
                    drawViewfinder(target.entity.location.add(0.0, target.entity.height / 2, 0.0))
                    if (broadcasting) particles.spawn(target.entity, Particle.ELECTRIC_SPARK, count = 4, spread = 0.3)
                }
                if (ticks == 1 || ticks % 10 == 0) playShutter()
            }
            override fun onCancel() {
                camera = null
                if (!abilityScope.isClosed && !playerStatus.isDead) startCameraCooldown(ticks)
                if (!abilityScope.isClosed && !abilityScope.suspended && player.isOnline && !playerStatus.isDead && !game.isPaused) {
                    if (ticks >= 60) playCompletion()
                    else sounds.playTo(player, Sound.UI_BUTTON_CLICK, volume = 0.25f, pitch = 0.8f)
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L)
    }

    private fun startCameraCooldown(filmedTicks: Int) {
        val duration = RecordingGeometry.cameraCooldownTicks(filmedTicks)
        cameraReady = game.combatTick + duration
        cameraCooldownDisplay?.cancel()
        if (player.isOnline) player.setCooldown(Material.OBSERVER, duration)
        // Native overlays use real ticks. Refresh while combat is paused, and once on resume.
        cameraCooldownDisplay = object : AbilityRunnable(abilityScope, policy = TickPolicy.SESSION) {
            var wasPaused = game.isPaused
            override fun run() {
                val remaining = (cameraReady - game.combatTick).coerceIn(0, 60).toInt()
                if (remaining == 0 || playerStatus.isDead || !abilityScope.isActive) { cancel(); return }
                if (player.isOnline && (game.isPaused || wasPaused)) player.setCooldown(Material.OBSERVER, remaining)
                wasPaused = game.isPaused
            }
            override fun onCancel() {
                if (player.isOnline) player.setCooldown(Material.OBSERVER, 0)
                cameraCooldownDisplay = null
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L)
    }

    private fun playShutter() {
        sounds.playTo(player, Sound.UI_BUTTON_CLICK, volume = 0.32f, pitch = 1.65f)
        sounds.playTo(player, Sound.BLOCK_PISTON_CONTRACT, volume = 0.16f, pitch = 1.9f)
        val lens = player.eyeLocation.add(player.eyeLocation.direction.multiply(0.6))
        CombatVisuals.ring(lens, lens.direction, 0.16, CombatVisuals.SILVER, 12)
    }

    private fun playCompletion() {
        sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_PLING, volume = 0.65f, pitch = 1.4f)
        CombatVisuals.pulse(abilityScope, player.location.add(0.0, 0.15, 0.0), Vector(0.0, 1.0, 0.0),
            if (broadcasting) 1.7 else 1.0, if (broadcasting) CombatVisuals.GOLD else CombatVisuals.CYAN)
        object : AbilityRunnable(abilityScope) {
            override fun run() {
                sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_CHIME, volume = 0.55f, pitch = 1.8f)
            }
        }.runTaskLater(ClassWarPlugin.instance, 3L)
    }

    private fun drawCameraRange(origin: Location) {
        repeat(80) { index ->
            val angle = Math.PI * 2 * index / 80
            particles.spawn(origin.clone().add(kotlin.math.cos(angle) * RecordingGeometry.CAMERA_RANGE, 0.12,
                kotlin.math.sin(angle) * RecordingGeometry.CAMERA_RANGE), Particle.DUST, borderColor)
        }
    }

    private fun drawProgressRing(feet: Location, progress: Double) {
        val completed = (progress.coerceIn(0.0, 1.0) * 40).toInt()
        repeat(40) { index ->
            val angle = -Math.PI / 2 + Math.PI * 2 * index / 40
            val point = feet.clone().add(kotlin.math.cos(angle) * 0.85, 0.12, kotlin.math.sin(angle) * 0.85)
            particles.spawn(point, Particle.DUST, if (index < completed) progressColor else remainingColor)
        }
    }

    private fun drawViewfinder(center: Location) {
        val (right, up) = CombatVisuals.plane(center.toVector().subtract(player.eyeLocation.toVector()))
        fun point(x: Double, y: Double) = center.clone().add(right.clone().multiply(x)).add(up.clone().multiply(y))
        // Four corner brackets keep the subject visible; the scan line follows the lens plane.
        for (x in listOf(-1.0, 1.0)) for (y in listOf(-1.0, 1.0)) {
            val corner = point(x * 0.6, y * 0.8)
            particles.line(corner, point(x * 0.32, y * 0.8), Particle.DUST, borderColor, 0.09)
            particles.line(corner, point(x * 0.6, y * 0.5), Particle.DUST, borderColor, 0.09)
        }
        val scanY = -0.65 + (game.combatTick % 40) / 40.0 * 1.3
        particles.line(point(-0.5, scanY), point(0.5, scanY), Particle.DUST, progressColor, 0.15)
        particles.spawn(point(0.7, 0.95), Particle.DUST, recordingColor)
    }

    private fun drawRecordingCone(geometry: RecordingGeometry, origin: Location, enhanced: Boolean = false) {
        val scanRadius = 2.0 + (game.combatTick % 40) / 40.0 * 14.0
        val scanHeight = 0.15 + (game.combatTick % 40) / 40.0 * 4.0
        fun draw(points: List<RecordingGeometry.Point>, color: Particle.DustOptions) {
            points.forEach {
                val scanning = if (it.y < 0.2) kotlin.math.abs(kotlin.math.hypot(it.x, it.z) - scanRadius) < 0.5
                    else kotlin.math.abs(it.y - scanHeight) < 0.3
                particles.spawn(origin.clone().add(it.x, it.y, it.z), Particle.DUST,
                    if (scanning) { if (enhanced) recordingColor else progressColor } else color)
            }
        }
        draw(geometry.floorGrid, fieldColor)
        draw(geometry.screenGrid, screenColor)
        draw(geometry.screenBorder, if (enhanced) recordingColor else borderColor)
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
            "<gray>4초간 처음 지정한 방향의 16칸, 120도 부채꼴 범위를 촬영한다.",
            "<gray>촬영 중에는 시선을 회전할 수 없다.",
            "<gray>촬영 중 아래 조건을 만족하면 촬영을 완료하고 {keyword:PhotographyStack}을 1 얻는다.",
            "<gray>  - 자신을 제외한 적 플레이어 2명 이상을 1초 이상 촬영",
            "<gray>  - 적 플레이어가 사망한 위치를 2초 이상 촬영",
            "",
            "<gray>{keyword:PhotographyStack}이 3스택이 되면 자신은 방송 상태에 돌입한다.",
            "<gray>방송 상태에서는 이 스킬이 강화되어 매 틱마다 범위 내 적에게 무적 시간을 무시하는 0.1의 {keyword:TrueDamage}를 입힌다.",
            "<gray>스킬이 종료될 때 범위 내에 있던 모든 적은 3초간 {keyword:Stun}한다.",
            "",
            "<gray>이 스킬 사용 중 기본 공격, 스킬을 사용할 수 없다.",
            "<gray>이 스킬은 Y축의 영향을 받지 않고 촬영할 수 있다."
        )
        override val cooldown = WARCORRESPONDENT_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            if (filming) return false
            camera?.cancel()
            filming = true
            val enhanced = broadcasting
            val initial = player.location
            val geometry = RecordingGeometry(initial.yaw, initial.pitch)
            recordingAim = geometry
            drawRecordingCone(geometry, initial, enhanced)
            sounds.play(player, Sound.BLOCK_BEACON_POWER_SELECT, volume = 0.55f, pitch = 1.6f)
            if (enhanced) {
                CombatVisuals.pulse(abilityScope, initial.clone().add(0.0, 0.2, 0.0), Vector(0.0, 1.0, 0.0), 1.8, CombatVisuals.GOLD)
                sounds.play(player, Sound.BLOCK_BEACON_ACTIVATE, volume = 0.6f, pitch = 0.9f)
            }
            particles.circle(player.location.add(0.0, 0.2, 0.0), Particle.ELECTRIC_SPARK, 0.8, 24)
            val lease = ControlLease(abilityScope, playerStatus)
            lease.allow(Control.ATTACK, false); lease.allow(Control.SKILL, false)
            recording = object : AbilityRunnable(abilityScope, cancelOnDisconnect = true) {
                val progress = RecordingProgress<UUID, Report>(enhanced)
                override fun run() {
                    if (player.world != initial.world) { cancel(); return }
                    val origin = player.location
                    fun inFrame(location: Location): Boolean {
                        return location.world == origin.world && geometry.contains(location.x - origin.x, location.z - origin.z)
                    }
                    fun currentTargets() = Targeting.select(playerData, TargetType.Enemy, includeStealth = enhanced).filter {
                        val box = it.entity.boundingBox
                        geometry.intersects(box.minX - origin.x, box.minZ - origin.z, box.maxX - origin.x, box.maxZ - origin.z)
                    }
                    val subjects = currentTargets()
                    val visible = subjects.filterIsInstance<PlayerData>()
                    val corpses = reports.filter { it.death && inFrame(it.location) }.toSet()
                    val result = progress.advance(visible.map { it.uniqueId }.toSet(), corpses)
                    if (enhanced) {
                        for (target in subjects) {
                            target.damage(0.1, DamageType.True, playerData, isInvincibilityTimeIgnore = true)
                            if (isCancelled || abilityScope.isClosed || playerStatus.isDead) return
                            if (progress.elapsed % 10 == 0) {
                                val center = target.entity.boundingBox.center.toLocation(origin.world)
                                CombatVisuals.ring(center, center.toVector().subtract(player.eyeLocation.toVector()),
                                    0.6, CombatVisuals.GOLD, 16)
                            }
                        }
                        if (progress.elapsed % 4 == 0) drawProgressRing(origin, progress.elapsed / 80.0)
                    }
                    if (progress.elapsed == 1 || progress.elapsed % 4 == 0 || result == RecordingProgress.Result.COMPLETED) {
                        if (!enhanced) {
                            visible.forEach { drawProgressRing(it.player.location, progress.playerProgress(it.uniqueId)) }
                            corpses.forEach { drawProgressRing(it.location, progress.deathProgress(it)) }
                        }
                    }
                    if (result == RecordingProgress.Result.COMPLETED) {
                        if (enhanced) {
                            // Only natural completion stuns. onCancel solely releases controls/resources.
                            for (target in currentTargets()) {
                                target.getOrCreateStatus(playerData) { Stun() }.applyStatus(duration = 3, powerSet = 1)
                                val center = target.entity.boundingBox.center.toLocation(origin.world)
                                CombatVisuals.pulse(abilityScope, center,
                                    center.toVector().subtract(player.eyeLocation.toVector()), 1.1, CombatVisuals.GOLD)
                                particles.spawn(center, Particle.ELECTRIC_SPARK, count = 16, spread = 0.4, speed = 0.08)
                            }
                            drawRecordingCone(geometry, origin, true)
                            playCompletion()
                            sounds.play(origin, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume = 0.65f, pitch = 0.7f)
                            cancel(); return
                        }
                        stacks = (stacks + 1).coerceAtMost(3); syncCoverage()
                        particles.circle(player.location.add(0.0, 1.0, 0.0), Particle.END_ROD, 1.0, 24)
                        playCompletion()
                        if (broadcasting) {
                            particles.spawn(player, Particle.FIREWORK, count = 24, spread = 0.6, speed = 0.08)
                            sounds.play(player, Sound.BLOCK_BEACON_ACTIVATE, volume = 0.55f, pitch = 1.5f)
                        }
                        cancel(); return
                    }
                    if (result == RecordingProgress.Result.EXPIRED) {
                        sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_BASS, volume = 0.3f, pitch = 0.8f)
                        cancel(); return
                    }
                    if (progress.elapsed % 5 == 0) drawRecordingCone(geometry, origin, enhanced)
                    if ((subjects.isNotEmpty() || corpses.isNotEmpty()) && (progress.elapsed == 1 || progress.elapsed % 10 == 0)) playShutter()
                }
                override fun onCancel() {
                    filming = false; recordingAim = null; recording = null; lease.close()
                }
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
