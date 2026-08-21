package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.SkillManager.getConeTargets
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Bleeding
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.bukkit.scheduler.BukkitRunnable

class Knight : GameClass() {
    override val name = "<gray>기사"
    override val rank = Rank.C
    override val classItemMaterial = Material.IRON_SWORD
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        YellowSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )


    private class Weapon : BaseWeapon() {
        override val name = "<gray>장검"
        override val description = listOf("<gray>무기 설명")
        override val material = Material.IRON_SWORD
    }

    private class RedSkill : Skill() {
        override val name = "<red><bold>내려베기"
        override val description = listOf(
            "<gray>2칸 내의 바라보는 적에게 6의 피해를 입히고 3초간 {keyword:Bleeding}을 3 부여한다.",
            "",
            Keyword.Bleeding.description!!,
            Keyword.AbnormalStatusDamage.description!!
        )
        override val cooldown = 10

        override fun use() {
            val target = playerData.shotLaserGetEntityData(2.0, TargetType.Enemy, false) ?: run {
                player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                return
            }
            target.damage(6.0, DamageType.Normal, playerData)
            val targetPlayer = target as? PlayerData
            if (targetPlayer != null) {
                val status = targetPlayer.getOrCreateStatus(playerData) { Bleeding() }
                status.applyStatus(duration = 3, 3)
            }
        }

        override fun isUseSuccess(): Boolean {
            playerData.shotLaserGetEntityData(2.0, TargetType.Enemy, false) ?: run {
                player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                return false
            }
            return true
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<red><bold>가로베기"
        override val description = listOf(
            "<gray>바라보는 방향으로 검을 휘두른다.",
            "<gray>적중한 모든 적에게 5의 피해를 입히고 3초간 {keyword:Bleeding}을 5 부여한다.",
            "",
            Keyword.Bleeding.description!!,
            Keyword.AbnormalStatusDamage.description!!
        )
        override val cooldown = 10

        override fun use() {
            val targets = playerData.getConeTargets(5.0, 100.0, TargetType.Enemy, false)
            targets.forEach {
                it.damage(5.0, DamageType.Normal, playerData)
                val targetPlayer = it as? PlayerData
                if (targetPlayer != null) {
                    val status = targetPlayer.getOrCreateStatus(playerData) { Bleeding() }
                    status.applyStatus(duration = 3, powerSet = 5)
                }
            }
        }
    }

    private class YellowSkill : Skill(), WhenHitHandler {
        override val name = "<yellow><bold>패링"
        override val description = listOf(
            "<gray>기본 공격 피격 직전 사용 시 해당 피해를 무효로 한다.",
            "<gray>성공 시 재사용 대기 시간이 초기화된다."
        )
        override val cooldown = 30

        override fun use() {
            activateParry()
        }

        private var isParry = false

        override fun whenAttackHit(event: DamageContext) {
            if (isParry) {
                isParry = false
                event.isCancelled = true
                CooldownManager.resetCooldown(player, this)
            }
        }

        fun activateParry() {
            isParry = true
            val task = object : BukkitRunnable() {
                override fun run() {
                    isParry = false
                }
            }.runTaskLater(ClassWarPlugin.instance, 2L)
            playerData.trackTask(task)
        }
    }

    private class Passive : BasePassive(), OnHitHandler {
        override val name = "<dark_red><bold>피로 벼려낸 검"
        override val description = listOf(
            "<gray>기본 공격 적중 시 3초간 적에게 {keyword:Bleeding}을 1 부여한다.",
            "<gray>그리고 즉시 {keyword:Bleeding}을 발동한다.",
            "",
            Keyword.Bleeding.description!!,
            Keyword.AbnormalStatusDamage.description!!
        )

        override fun onAttackHit(event: DamageContext) {
            val entityData = event.target as? PlayerData ?: return
            val status = entityData.getOrCreateStatus(playerData) { Bleeding() }
            status.applyStatus(duration = 3, powerSet = 1)

            entityData.damage(status.power.toDouble(), DamageType.StatusAbnormality, playerData)
            status.updatePower(status.power / 2)
        }

    }
}
