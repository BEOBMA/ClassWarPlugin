package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.handler.*
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.status.list.RevolverBulletStatus
import org.beobma.classWarPlugin.status.list.FreikugelBulletStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.updateStatusActionBar
import org.beobma.classWarPlugin.status.list.Disarm
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.Color
import org.beobma.classWarPlugin.effect.CombatVisuals
import org.beobma.classWarPlugin.gameClass.mechanics.RevolverReloadFrame
import org.bukkit.event.player.PlayerInteractEvent
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon

// 밸런스 조정 상수
private const val FREIKUGEL_RED_SKILL_COOLDOWN_SECONDS = 8

class Freikugel : GameClass(), GameStatusHandler, WeaponInputHandler,
    org.beobma.classWarPlugin.gameClass.firearm.BorrowableFirearm {
    override var reloadDisabled = false
    override var onMagazineEmpty: (() -> Unit)? = null
    override val ammunition get() = bullets + if (magic) 1 else 0
    override val reloadSkillIds = emptySet<String>()
    override val classId = "freikugel"
    override val name = "<gray>마탄의 사수"
    override val rank = Rank.A
    override val classItemMaterial = Material.CRYING_OBSIDIAN
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private var bullets = 6
    private var magic = true
    private var reloading = false
    private var firing = false
    private var nextShot = 0L
    private var reloadUntil = 0L
    private var normalStatus: RevolverBulletStatus? = null
    private var magicStatus: FreikugelBulletStatus? = null

    private fun syncAmmo() {
        val remaining = if (reloading) (reloadUntil - game.combatTick).coerceIn(0, 40).toInt() else 0
        val normal = normalStatus ?: RevolverBulletStatus().also { playerData.addStatus(it, playerData); normalStatus = it }
        val special = magicStatus ?: FreikugelBulletStatus().also { playerData.addStatus(it, playerData); magicStatus = it }
        val normalChanged = normal.synchronize(bullets, remaining)
        val magicChanged = special.synchronize(if (magic) 1 else 0, remaining)
        if (normalChanged || magicChanged) playerData.updateStatusActionBar()
    }

    override fun onBattleStart() { bullets = 6; magic = true; reloading = false; firing = false; nextShot = 0; syncAmmo() }
    override fun onGameTimePasses() {}

    private fun reloadIfEmpty() {
        syncAmmo()
        renderCylinder()
        if (bullets > 0 || magic || reloading) return
        if (reloadDisabled) { if (!firing) onMagazineEmpty?.invoke(); return }
        reloading = true
        reloadUntil = game.combatTick + org.beobma.classWarPlugin.growth.GrowthScaling.cooldown(playerData, 40, classId)
        syncAmmo()
        particles.spawn(player.eyeLocation, Particle.SMOKE, count = 5, spread = 0.15, speed = 0.02)
        sounds.playTo(player, Sound.ITEM_CROSSBOW_LOADING_START, volume = 0.75f, pitch = 0.8f)
        sounds.playTo(player, Sound.BLOCK_IRON_TRAPDOOR_OPEN, volume = 0.35f, pitch = 1.7f)
        val lease = ControlLease(abilityScope, playerStatus)
        lease.allow(Control.ATTACK, false)
        lease.allow(Control.SKILL, false)
        object : AbilityRunnable(abilityScope) {
            var previousLoaded = 0
            var lockPlayed = false
            override fun run() {
                if (game.combatTick < reloadUntil) {
                    val frame = RevolverReloadFrame.at((reloadUntil - game.combatTick).toInt())
                    renderCylinder(frame)
                    if (frame.loadedChambers > previousLoaded) {
                        sounds.playTo(player, Sound.BLOCK_TRIPWIRE_CLICK_ON, volume = 0.45f, pitch = 1.2f + frame.loadedChambers * 0.08f)
                        previousLoaded = frame.loadedChambers
                    }
                    if (frame.elapsed >= 34 && !lockPlayed) {
                        lockPlayed = true
                        sounds.playTo(player, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, volume = 0.5f, pitch = 1.4f)
                    }
                    syncAmmo()
                    return
                }
                bullets = 6; magic = true; reloading = false; syncAmmo()
                renderCylinder()
                particles.spawn(player.eyeLocation, Particle.ELECTRIC_SPARK, count = 8, spread = 0.18)
                sounds.playTo(player, Sound.ITEM_CROSSBOW_LOADING_END, volume = 0.8f, pitch = 1.2f)
                sounds.playTo(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume = 0.35f, pitch = 1.7f)
                cancel()
            }
            override fun onCancel() { lease.close(); reloading = false }
        }.runTaskTimer(ClassWarPlugin.instance, 2L, 2L)
    }

    /** Six radial chambers and one violet center; the open cylinder ejects cases, then inserts each round. */
    private fun renderCylinder(frame: RevolverReloadFrame? = null) {
        val eye = player.eyeLocation
        val forward = eye.direction
        val (right, up) = CombatVisuals.plane(forward)
        val open = frame?.let { kotlin.math.sin(Math.PI * it.elapsed / 40.0) * 0.22 } ?: 0.0
        val center = eye.clone().add(forward.clone().multiply(0.85))
            .add(right.clone().multiply(0.30 + open)).add(up.clone().multiply(-0.35))
        val rotation = (frame?.elapsed?.toDouble() ?: (6 - bullets) * 5.0) * Math.PI / 30.0
        val loaded = frame?.loadedChambers ?: bullets
        CombatVisuals.ring(center, forward, 0.24, CombatVisuals.SILVER, 16)
        repeat(6) { index ->
            val angle = rotation + index * Math.PI / 3.0
            val radial = right.clone().multiply(kotlin.math.cos(angle)).add(up.clone().multiply(kotlin.math.sin(angle)))
            val chamber = center.clone().add(radial.clone().multiply(0.155))
            val color = if (index < loaded) CombatVisuals.GOLD else Color.fromRGB(62, 65, 74)
            particles.spawn(chamber, Particle.DUST, Particle.DustOptions(color, 0.65f))
            if (frame != null && frame.elapsed < 8) {
                val progress = frame.elapsed / 8.0
                val casing = chamber.clone().add(radial.multiply(progress * 0.25))
                    .subtract(forward.clone().multiply(progress * 0.2)).add(0.0, -progress * progress * 0.5, 0.0)
                particles.spawn(casing, Particle.DUST, Particle.DustOptions(CombatVisuals.GOLD, 0.45f))
            } else if (frame != null && frame.elapsed in 8..31 && index == loaded) {
                val insertion = (frame.elapsed - 8) % 4 / 4.0
                particles.spawn(chamber.clone().subtract(forward.clone().multiply((1.0 - insertion) * 0.32)),
                    Particle.DUST, Particle.DustOptions(CombatVisuals.GOLD, 0.6f))
            }
        }
        val magicReady = if (frame != null) frame.elapsed >= 34 else magic
        particles.spawn(center, Particle.DUST,
            Particle.DustOptions(if (magicReady) CombatVisuals.VIOLET else Color.fromRGB(45, 30, 55), 0.8f))
    }

    private fun shoot(amount: Double, basic: Boolean, knockback: Boolean = false, cursed: Boolean = false) {
        val start = player.eyeLocation
        val target = playerData.shotLaserGetEntityData(32.0, TargetType.Enemy, false)
        val end = target?.entity?.boundingBox?.center?.toLocation(start.world)
            ?: start.world.rayTraceBlocks(start, start.direction, 32.0)?.hitPosition?.toLocation(start.world)
            ?: start.clone().add(start.direction.multiply(32.0))
        val muzzle = start.clone().add(start.direction.multiply(0.6))
        particles.spawn(muzzle, if (cursed) Particle.SOUL_FIRE_FLAME else Particle.FLAME, count = 6, spread = 0.08, speed = 0.02)
        particles.spawn(muzzle, Particle.SMOKE, count = 4, spread = 0.1, speed = 0.03)
        CombatVisuals.tracer(muzzle, end, if (cursed) CombatVisuals.VIOLET else CombatVisuals.GOLD, spiral = cursed)
        CombatVisuals.ring(muzzle, start.direction, if (cursed) 0.32 else 0.18,
            if (cursed) CombatVisuals.VIOLET else CombatVisuals.GOLD, 16)
        CombatVisuals.ring(end, start.direction, if (cursed) 0.6 else 0.25,
            if (cursed) CombatVisuals.VIOLET else CombatVisuals.SILVER, 20)
        particles.spawn(end, if (cursed) Particle.SOUL else Particle.CRIT, count = if (cursed) 12 else 7, spread = 0.18, speed = 0.05)
        sounds.play(player, Sound.ENTITY_FIREWORK_ROCKET_BLAST, volume = if (firing) 0.55f else 0.85f, pitch = if (cursed) 0.65f else 1.15f)
        sounds.play(muzzle, Sound.ENTITY_IRON_GOLEM_ATTACK, volume = if (firing) 0.18f else 0.3f, pitch = if (cursed) 0.75f else 1.7f)
        if (cursed) {
            CombatVisuals.pulse(abilityScope, end, start.direction, 1.0, CombatVisuals.VIOLET)
            particles.spawn(end, Particle.SOUL_FIRE_FLAME, count = 18, spread = 0.4, speed = 0.06)
            sounds.play(end, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, volume = 0.45f, pitch = 1.45f)
            object : AbilityRunnable(abilityScope) {
                var frame = 0
                override fun run() {
                    CombatVisuals.tracer(muzzle, end, CombatVisuals.VIOLET)
                    if (++frame >= 2) cancel()
                }
            }.runTaskTimer(ClassWarPlugin.instance, 3L, 3L)
            particles.circle(muzzle, Particle.ENCHANT, 0.35, 12)
            sounds.play(player, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume = 0.65f, pitch = 0.75f)
        }
        target?.damage(amount, DamageType.Normal, playerData,
            damagePath = if (basic) DamagePath.RANGED_ATTACK else DamagePath.SKILL)
        if (knockback && target != null) target.entity.velocity = org.beobma.classWarPlugin.growth.GrowthScaling.knockback(playerData, start.direction.multiply(1.5).setY(0.3))
    }

    override fun onWeaponRightClick(event: PlayerInteractEvent) {
        event.isCancelled = true
        if (reloading || firing || game.combatTick < nextShot || !playerStatus.canAttack || playerData.hasStatus<Disarm>()) return
        nextShot = game.combatTick + 40
        val cursed = bullets == 0
        if (cursed) magic = false else bullets--
        if (!cursed) shoot(3.0, true)
        if (cursed) {
            particles.spawn(player, Particle.SOUL, count = 8, spread = 0.35)
            sounds.playTo(player, Sound.ENTITY_ENDERMAN_HURT, volume = 0.35f, pitch = 1.6f)
            playerData.damage(2.0, DamageType.Normal, playerData, damagePath = DamagePath.STATUS_EFFECT)
        }
        reloadIfEmpty()
    }

    // 마탄을 소모한 공격 시, 탄환이 날아가 피해를 입히는 효과는 없고 자신만 피해를 입고 끝나야 함.
    private class Weapon : BaseWeapon() {
        override val name = "<gray>리볼버"
        override val description = listOf(
            "<gray>우클릭 시 {keyword:Bullet} 혹은 {keyword:FreikugelBullet}을 1발 소모하고 사격한다.",
            "<gray>{keyword:Bullet} 사격은 적중한 적에게 {g:ranged:3}의 피해를 입힌다.",
            "<gray>{keyword:FreikugelBullet}을 소모하면 탄환을 발사하지 않고 자신만 {g:damage:2}의 피해를 입는다.",
            "<gray>이 공격은 기본 공격으로 간주한다.",
            "",
            "<dark_gray>이 효과의 재사용 대기 시간은 {g:cooldown:2}초이다."
        )
        override val material = Material.IRON_HORSE_ARMOR
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "freikugel/red-skill"
        override val name = "<bold>패닝 / 퀵드로우"
        override val description = listOf(
            "<gray>바라보는 방향으로 {keyword:FreikugelBullet}을 제외한 모든 {keyword:Bullet}을 소모하여 사격한다.",
            "<gray>매 사격마다 반동이 강해지며, 이 사격은 {g:damage:2}의 피해를 입힌다.",
            "",
            "<gray>남은 {keyword:Bullet}이 {keyword:FreikugelBullet} 뿐이라면 위 효과 대신 아래 효과로 발동된다.",
            "<gray>바라보는 방향으로 {keyword:FreikugelBullet}을 소모하여 사격한다.",
            "<gray>이 사격은 적에게 {g:damage:5}의 피해를 입히고 밀쳐낸다."
        )
        override val cooldown = FREIKUGEL_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            if (reloading || firing) return false
            if (bullets == 0 && magic) {
                magic = false
                shoot(5.0, false, true, cursed = true)
                particles.circle(player.location.add(0.0, 0.1, 0.0), Particle.SOUL_FIRE_FLAME, 0.9, 24)
                reloadIfEmpty()
                return true
            }
            val shots = bullets
            if (shots <= 0) return false
            bullets = 0
            firing = true
            syncAmmo()
            sounds.playTo(player, Sound.ITEM_ARMOR_EQUIP_IRON, volume = 0.6f, pitch = 1.5f)
            object : AbilityRunnable(abilityScope) {
                var fired = 0
                override fun run() {
                    if (!playerStatus.canSkillUse) { cancel(); return }
                    shoot(2.0, false)
                    fired++
                    val aim = player.location
                    player.setRotation(aim.yaw, (aim.pitch - fired * 1.5f).coerceAtLeast(-90f))
                    if (fired >= shots) cancel()
                }
                override fun onCancel() {
                    firing = false
                    if (!abilityScope.isClosed && !playerStatus.isDead) reloadIfEmpty()
                }
            }.runTaskTimer(ClassWarPlugin.instance, 1L, 3L)
            return true
        }
    }

    // 탄약이 표시될 때, 진짜 리볼버처럼 표시되도록 연출 강화. 재장전 또한 리볼버 장전처럼 보이도록 연출 강화
    private class Passive : BasePassive() {
        override val name = "<bold>마탄환"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>{keyword:Bullet} 6발과 {keyword:FreikugelBullet} 1발을 가진 채 게임을 시작한다.",
            "<gray>{keyword:Bullet}과 {keyword:FreikugelBullet}을 모두 소모하면 {g:reload:2}초간 재장전한다.",
            "<gray>재장전 중에는 기본 공격과 스킬을 사용할 수 없다."
        )
    }
}
