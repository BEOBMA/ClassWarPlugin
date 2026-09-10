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
import org.bukkit.event.player.PlayerInteractEvent
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon

// 밸런스 조정 상수
private const val FREIKUGEL_RED_SKILL_COOLDOWN_SECONDS = 8

class Freikugel : GameClass(), GameStatusHandler, WeaponInputHandler {
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

    private fun syncAmmo() {
        val remaining = if (reloading) (reloadUntil - game.combatTick).coerceIn(0, 40).toInt() else 0
        val normalChanged = playerData.getOrCreateStatus(playerData) { RevolverBulletStatus() }.synchronize(bullets, remaining)
        val magicChanged = playerData.getOrCreateStatus(playerData) { FreikugelBulletStatus() }.synchronize(if (magic) 1 else 0, remaining)
        if (normalChanged || magicChanged) playerData.updateStatusActionBar()
    }

    override fun onBattleStart() { bullets = 6; magic = true; reloading = false; firing = false; nextShot = 0; syncAmmo() }
    override fun onGameTimePasses() {}

    private fun reloadIfEmpty() {
        syncAmmo()
        if (bullets > 0 || magic || reloading) return
        reloading = true
        reloadUntil = game.combatTick + 40
        syncAmmo()
        particles.spawn(player.eyeLocation, Particle.SMOKE, count = 5, spread = 0.15, speed = 0.02)
        sounds.playTo(player, Sound.ITEM_CROSSBOW_LOADING_START, volume = 0.75f, pitch = 0.8f)
        object : AbilityRunnable(abilityScope) {
            var frame = 0
            override fun run() {
                if (!reloading) { cancel(); return }
                val muzzle = player.eyeLocation.add(player.eyeLocation.direction.multiply(0.6))
                CombatVisuals.ring(muzzle, muzzle.direction, 0.24, if (frame == 2) CombatVisuals.VIOLET else CombatVisuals.GOLD, 12)
                sounds.playTo(player, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, volume = 0.23f, pitch = 1.3f + frame * 0.15f)
                if (++frame >= 3) cancel()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 8L, 10L)
        val lease = ControlLease(abilityScope, playerStatus)
        lease.allow(Control.ATTACK, false)
        lease.allow(Control.SKILL, false)
        object : AbilityRunnable(abilityScope) {
            override fun run() {
                if (game.combatTick < reloadUntil) { syncAmmo(); return }
                bullets = 6; magic = true; reloading = false; syncAmmo()
                particles.spawn(player.eyeLocation, Particle.ELECTRIC_SPARK, count = 8, spread = 0.18)
                sounds.playTo(player, Sound.ITEM_CROSSBOW_LOADING_END, volume = 0.8f, pitch = 1.2f)
                sounds.playTo(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume = 0.35f, pitch = 1.7f)
                cancel()
            }
            override fun onCancel() { lease.close() }
        }.runTaskTimer(ClassWarPlugin.instance, 2L, 2L)
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
        if (knockback && target != null) target.entity.velocity = start.direction.multiply(1.5).setY(0.3)
    }

    override fun onWeaponRightClick(event: PlayerInteractEvent) {
        event.isCancelled = true
        if (reloading || firing || game.combatTick < nextShot || !playerStatus.canAttack || playerData.hasStatus<Disarm>()) return
        nextShot = game.combatTick + 40
        val cursed = bullets == 0
        if (cursed) magic = false else bullets--
        shoot(3.0, true, cursed = cursed)
        if (cursed) {
            particles.spawn(player, Particle.SOUL, count = 8, spread = 0.35)
            sounds.playTo(player, Sound.ENTITY_ENDERMAN_HURT, volume = 0.35f, pitch = 1.6f)
            playerData.damage(2.0, DamageType.Normal, playerData, damagePath = DamagePath.STATUS_EFFECT)
        }
        reloadIfEmpty()
    }

    private class Weapon : BaseWeapon() {
        override val name = "<gray>리볼버"
        override val description = listOf(
            "<gray>우클릭 시 {keyword:Bullet} 혹은 {keyword:FreikugelBullet}을 1발 소모하고 사격한다.",
            "<gray>사격은 적중한 적에게 3의 피해를 입힌다.",
            "<gray>{keyword:FreikugelBullet}을 소모하였다면 자신이 2의 피해를 입는다.",
            "<gray>이 공격은 기본 공격으로 간주한다.",
            "",
            "<dark_gray>이 효과의 재사용 대기 시간은 2초이다."
        )
        override val material = Material.IRON_HORSE_ARMOR
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "freikugel/red-skill"
        override val name = "<bold>패닝 / 퀵드로우"
        override val description = listOf(
            "<gray>바라보는 방향으로 {keyword:FreikugelBullet}을 제외한 모든 {keyword:Bullet}을 소모하여 사격한다.",
            "<gray>매 사격마다 반동이 강해지며, 이 사격은 2의 피해를 입힌다.",
            "",
            "<gray>남은 {keyword:Bullet}이 {keyword:FreikugelBullet} 뿐이라면 위 효과 대신 아래 효과로 발동된다.",
            "<gray>바라보는 방향으로 {keyword:FreikugelBullet}을 소모하여 사격한다.",
            "<gray>이 사격은 적에게 5의 피해를 입히고 밀쳐낸다."
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

    private class Passive : BasePassive() {
        override val name = "<bold>마탄환"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>{keyword:Bullet} 6발과 {keyword:FreikugelBullet} 1발을 가진 채 게임을 시작한다.",
            "<gray>{keyword:Bullet}과 {keyword:FreikugelBullet}을 모두 소모하면 2초간 재장전한다.",
            "<gray>재장전 중에는 기본 공격과 스킬을 사용할 수 없다."
        )
    }
}
