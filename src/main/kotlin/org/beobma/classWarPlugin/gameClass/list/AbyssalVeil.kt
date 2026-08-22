package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Flooring
import org.beobma.classWarPlugin.skill.Projectile
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Abyss
import org.beobma.classWarPlugin.status.list.Mana
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class AbyssalVeil : GameClass() {
    override val name = "<gray>심연 장막"
    override val rank = Rank.C
    override val classItemMaterial = Material.BLACK_CONCRETE
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class RedSkill : Skill() {
        override val name = "<bold>검은 연기"
        override val description = listOf(
            "<gray>자신 위치에 8초간 유지되는 검은 연기를 형성한다.",
            "<gray>연기 속에 들어간 자신은 연기에 가려 숨겨지지만, 적은 숨겨지지 않는다.",
            "",
            "<dark_gray>웅크린 상태에서 사용하면 4칸 내의 바라보는 블럭에 연기를 형성할 수도 있다."
        )
        override val cooldown = 35

        override fun use() {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            val smoke = Smoke()
            smoke.inject(playerData)

            smoke.location = if (player.isSneaking) {
                playerData.shotLaserGetBlock(4.0)?.location?.add(0.5, 1.0, 0.5) ?: run {
                    player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                    return
                }
            } else {
                player.location.clone()
            }
            smoke.spawnFlooring(playerData)
            mana.decreasePower(40)
        }

        override fun isUseSuccess(): Boolean {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            if (mana.power < 40) {
                player.sendMiniMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
                return false
            }
            if (player.isSneaking) {
                playerData.shotLaserGetBlock(4.0)?.location?.add(0.5, 1.0, 0.5) ?: run {
                    player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                    return false
                }
            }

            return true
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<bold>잠식"
        override val description = listOf(
            "<gray>바라보는 방향으로 잠식된 연기를 발사한다.",
            "<gray>적중한 모든 적에게 5의 피해를 입히고 4초간 {keyword:Abyss} 상태로 만든다.",
            "<gray>대상이 {keyword:Erosion} 상태였다면 소모하여 대상을 3초간 {keyword:Silence> 상태로 만든다.",
        )
        override val cooldown = 35

        override fun use() {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }

            val projectile = ProjectileSmoke()
            projectile.location = player.location.clone()

            projectile.spawnProjectile(playerData)
            mana.decreasePower(60)
        }

        override fun isUseSuccess(): Boolean {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            if (mana.power < 60) {
                player.sendMiniMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
                return false
            }
            return true
        }
    }


    private class Smoke : Flooring() {
        private val applied = mutableListOf<PlayerData>()

        override lateinit var location: Location
        override var radius: Double = 5.0
        override var targetType: TargetType = TargetType.Enemy
        override var time: Int? = 4

        override fun onFlooringEntityHit(hitEntityData: EntityData, location: Location) {
            val hitPlayerData = hitEntityData as? PlayerData ?: return
            hitPlayerData.player.isGlowing = true
            applied.add(hitPlayerData)
        }

        override fun onFlooringEntityOut(hitEntityData: EntityData, location: Location) {
            val hitPlayerData = hitEntityData as? PlayerData ?: return
            hitPlayerData.player.isGlowing = false
            applied.remove(hitPlayerData)
        }

        override fun onFlooringEnd() {
            applied.forEach { playerData ->
                playerData.player.isGlowing = false
            }
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>잠식"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 적중 시 대상을 {keyword:Erosion} 상태로 만든다."
        )
    }

    private class ProjectileSmoke : Projectile() {
        override lateinit var location: Location
        override var targetType: TargetType = TargetType.Enemy
        override var speed: Double = 0.5
        override var isWallHit: Boolean = false
        override var isPlayerHit: Boolean = true
        override val isPlayerHitRemove: Boolean = false

        private val hitSet = mutableSetOf<PlayerData>()

        override fun onProjectileEntityHit(hitEntityData: EntityData, location: Location) {
            val hitPlayerData = hitEntityData as? PlayerData
            if (hitPlayerData != null && hitSet.add(hitPlayerData)) {
                val abyss = hitPlayerData.getOrCreateStatus(playerData) { Abyss() }
                abyss.applyStatus(duration = 3)
                hitPlayerData.damage(5.0, DamageType.Normal, playerData)
                return
            }
            hitEntityData.damage(5.0, DamageType.Normal, playerData)
        }
    }
}
