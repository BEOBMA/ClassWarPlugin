package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetPlayerData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Projectile
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.scheduler.BukkitRunnable

class Assassin : GameClass() {
    override val name = "<gray>암살자"
    override val description = listOf(
        "<dark_gray>근거리 암살자",
        "",
        "<gray>적의 후방에 잠입해 취약한 적을 제거합니다."
    )
    override val classItemMaterial = Material.NETHERITE_HELMET
    override val weapon = AssassinsDagger()

    override var skills: List<Skill> = listOf(
        AssassinsRedSkill(),
        AssassinsOrangeSkill(),
        AssassinsYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        AssassinsPassive()
    )
}

class AssassinsDagger : Weapon() {
    override val name = "<gray>암살자의 단검"
    override val description = listOf("<gray>빠르고 치명적이다.")
    override val material = Material.AIR
}

class AssassinsRedSkill : Skill() {
    override val name = "<gray><bold>찌르기"
    override val description = listOf(
        "<gray>2칸 내의 바라보는 적에게 6의 피해를 입힌다.",
        "<gray>대상이 자신을 바라보고 있지 않았다면 3의 피해를 추가로 입힌다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val targetData = playerData.shotLaserGetPlayerData(2.0, TargetType.Enemy, false) ?: run {
            player.sendMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
            return false
        }
        targetData.damage(6.0, DamageType.Normal, playerData)
        val viewCheck = targetData.shotLaserGetPlayerData(5.0, TargetType.Enemy, false)
        if (viewCheck != playerData) {
            targetData.damage(3.0, DamageType.Normal, playerData)
        }
        return true
    }
}

class AssassinsOrangeSkill : Skill() {
    override val name = "<gray><bold>단검 투척"
    override val description = listOf(
        "<gray>바라보는 방향으로 단검을 투척한다.",
        "<gray>단검이 적에게 적중하면 5의 피해를 입히고 해당 적의 뒤로 즉시 이동한다.",
        "<gray>단검이 블록에 적중하면 4초간 ${Keyword.Stealth.string}하고 해당 방향으로 빠르게 이동한다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val assassinsDaggerProjectile = AssassinsDaggerProjectile()
        assassinsDaggerProjectile.spawnProjectile(playerData)
        return true
    }
}

class AssassinsDaggerProjectile : Projectile() {
    override var location: Location = player.location
    override var targetType: TargetType = TargetType.Enemy
    override var speed: Double = 1.0
    override var isWallHit: Boolean = true
    override var isPlayerHit: Boolean = true
    override val isPlayerHitRemove: Boolean = true


    override fun onProjectilePlayerHit(hitPlayerData: PlayerData, location: Location) {
        val hitPlayer = hitPlayerData.player
        val hitPlayerLocation = hitPlayer.location
        val behind = hitPlayerLocation.clone().add(hitPlayerLocation.direction.normalize().multiply(-1.5))
        hitPlayerData.damage(5.0, DamageType.Normal, playerData)
        player.teleport(behind)
    }

    override fun onProjectileBlockHit(hitBlock: Block, location: Location) {
        val blockLocation = hitBlock.location.add(0.5, 0.5, 0.5)
        val direction = blockLocation.toVector().subtract(player.location.toVector()).normalize()
        val speedPerTick = 0.5
        val stealth = playerData.getOrCreateStatus { Stealth() }
        stealth.increaseDuration(5)

        val task = object : BukkitRunnable() {
            override fun run() {
                val nextLoc = player.location.add(direction.clone().multiply(speedPerTick))
                player.teleport(nextLoc)
                if (player.location.distance(blockLocation) < speedPerTick) {
                    player.teleport(blockLocation)
                    cancel()
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L)

        playerData.bukkitTasks.add(task)
    }
}

class AssassinsYellowSkill : Skill() {
    override val name = "<bold>비열한 일격"
    override val description = listOf(
        "<gray>2칸 내의 바라보는 적에게 10의 피해를 입힌다.",
        "<gray>자신이 ${Keyword.Stealth.string}중이었다면 5의 피해를 추가로 입힌다.",
        "<gray>이 스킬로 적을 처치했다면 재사용 대기시간이 75% 감소한다."
    )
    override val cooldown = 60

    override fun use(): Boolean {
        val targetData = playerData.shotLaserGetPlayerData(2.0, TargetType.Enemy, false) ?: run {
            player.sendMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
            return false
        }
        targetData.damage(10.0, DamageType.Normal, playerData)
        if (playerData.getStatus<Stealth>() == null) {
            targetData.damage(5.0, DamageType.Normal, playerData)
        }
        if (targetData.playerStatus.isDead) {
            player.setCooldown(Material.YELLOW_DYE, (cooldown * 0.25).toInt())
        }
        return true
    }
}

class AssassinsPassive : Passive() {
    override val name = "<bold>암살자의 각오"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>암살자는 완벽한 ${Keyword.Stealth.string}을 위해 기본 무기가 존재하지 않는다.",
        "<gray>${Keyword.Stealth.string}하면 ${Keyword.Untargetability.string} 상태가 된다.",
        "",
        dictionary[Keyword.Untargetability] ?: ""
    )
}