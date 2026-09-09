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
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.Disarm
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Particle
import org.bukkit.Sound
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

    private inner class Ammo : StatusAbnormality() {
        override val name = "<gold>탄창"
        override val description = listOf("<gray>일반 탄환 6발과 마탄환 1발")
        override val canRemove = false
        override val isClassMechanic = true
        override var maxPower: Int? = 7
        override var duration: Int? = null
        override fun actionBarText() = if (reloading) "<yellow>재장전 중" else "<gold>탄환 $bullets / 마탄환 ${if (magic) 1 else 0}"
    }

    private fun syncAmmo() { playerData.getOrCreateStatus(playerData) { Ammo() }.updatePower(bullets + if (magic) 1 else 0) }
    override fun onBattleStart() { bullets = 6; magic = true; reloading = false; firing = false; nextShot = 0; syncAmmo() }
    override fun onGameTimePasses() {}

    private fun reloadIfEmpty() {
        syncAmmo()
        if (bullets > 0 || magic || reloading) return
        reloading = true
        val lease = ControlLease(abilityScope, playerStatus)
        lease.allow(Control.ATTACK, false)
        lease.allow(Control.SKILL, false)
        object : AbilityRunnable(abilityScope) {
            override fun run() { bullets = 6; magic = true; reloading = false; syncAmmo(); sounds.playTo(player, Sound.ITEM_CROSSBOW_LOADING_END) }
            override fun onCancel() { lease.close() }
        }.runTaskLater(ClassWarPlugin.instance, 40L)
    }

    private fun shoot(amount: Double, basic: Boolean, knockback: Boolean = false) {
        val start = player.eyeLocation
        val target = playerData.shotLaserGetEntityData(32.0, TargetType.Enemy, false)
        val end = target?.entity?.location?.add(0.0, 1.0, 0.0) ?: start.clone().add(start.direction.multiply(32.0))
        particles.line(start, end, Particle.CRIT, 0.25)
        sounds.play(player, Sound.ENTITY_FIREWORK_ROCKET_BLAST, pitch = 0.8f)
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
        shoot(3.0, true)
        if (cursed) playerData.damage(2.0, DamageType.Normal, playerData, damagePath = DamagePath.STATUS_EFFECT)
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
                shoot(5.0, false, true)
                reloadIfEmpty()
                return true
            }
            val shots = bullets
            if (shots <= 0) return false
            bullets = 0
            firing = true
            syncAmmo()
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
