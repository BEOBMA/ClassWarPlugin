package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Flooring
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Projectile
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Abyss
import org.beobma.classWarPlugin.status.list.Mana
import org.beobma.classWarPlugin.status.list.Silence
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.Material

class DarkWizard : GameClass(), GameStatusHandler {
    override val name = "<gray>어둠 마법사"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.BLACK_CONCRETE
    override val weapon = DarkWizardsStaff()

    override var skills: List<Skill> = listOf(
        DarkWizardsRedSkill(),
        DarkWizardsOrangeSkill(),
        DarkWizardsYellowSkill()
    )

    override var passives: List<Passive> = listOf()

    override fun onBattleStart() {
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        mana.updatePower(100)
    }

    override fun onGameTimePasses() {
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        mana.increasePower(10)
    }
}

class DarkWizardsStaff : Weapon() {
    override val name = "<gray>지팡이 대용 검"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.WOODEN_SWORD
}

class DarkWizardsRedSkill : Skill() {
    override val name = "<bold>검은 연기"
    override val description = listOf(
        "{keyword:Mana}를 40 소모하고 사용할 수 있다.",
        "",
        "<gray>자신 위치에 4초간 유지되는 연기를 형성한다.",
        "<gray>연기 속에 들어온 아군은 연기에 가려 숨겨지지만, 적은 숨겨지지 않는다.",
        "<dark_gray>웅크린 상태에서 사용하면 4칸 내의 바라보는 블럭에 연기를 형성할 수도 있다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        if (mana.power < 40) {
            player.sendMiniMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }

        val smoke = DarkWizardsSmoke()
        smoke.inject(playerData)

        smoke.location = if (player.isSneaking) {
            playerData.shotLaserGetBlock(4.0)?.location?.add(0.5, 1.0, 0.5) ?: run {
                player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                return false
            }
        } else {
            player.location.clone()
        }
        smoke.spawnFlooring(playerData)
        mana.decreasePower(40)
        return true
    }
}

class DarkWizardsSmoke : Flooring() {
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

class DarkWizardsOrangeSkill : Skill() {
    override val name = "<bold>잠식"
    override val description = listOf(
        "{keyword:Mana}를 60 소모하고 사용할 수 있다.",
        "",
        "<gray>바라보는 방향으로 잠식된 연기를 발사한다.",
        "<gray>적중한 모든 적에게 5의 피해를 입히고 3초간 {keyword:Abyss} 상태를 적용한다.",
        "",
        Keyword.Abyss.description ?: ""
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        if (mana.power < 60) {
            player.sendMiniMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }

        val projectile = DarkWizardsProjectileSmoke()
        projectile.location = player.location.clone()

        projectile.spawnProjectile(playerData)
        mana.decreasePower(60)
        return true
    }
}

class DarkWizardsProjectileSmoke : Projectile() {
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

class DarkWizardsYellowSkill : Skill() {
    private val abyssPlayers = mutableSetOf<PlayerData>()

    override val name = "<bold>심연의 공포"
    override val description = listOf(
        "{keyword:Mana}를 100 소모하고 사용할 수 있다.",
        "",
        "<gray>5초간 전장을 연기로 가득 채워 모든 대상을 {keyword:Abyss} 상태로 만든다.",
        "<gray>이 효과 발동 전을 기준으로 한 번이라도 {keyword:Abyss} 상태였던 적은 추가로 지속 시간동안 {keyword:Silence} 상태가 된다.",
        "",
        Keyword.Abyss.description ?: "",
        Keyword.Silence.description ?: ""
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        if (mana.power < 100) {
            player.sendMiniMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }

        val allPlayers = playerData.radius(player.location, TargetType.All, 1000.0, true)
        val enemies = playerData.radius(player.location, TargetType.Enemy, 1000.0, false)

        enemies.filterIsInstance<PlayerData>().forEach { enemy ->
            if (abyssPlayers.contains(enemy)) {
                val silence = enemy.getOrCreateStatus(playerData) { Silence() }
                silence.applyStatus(duration = 5)
            }
        }

        allPlayers.filterIsInstance<PlayerData>().forEach { playerTarget ->
            val abyss = playerTarget.getOrCreateStatus(playerData) { Abyss() }
            abyss.applyStatus(duration = 5)
        }

        abyssPlayers.addAll(allPlayers.filterIsInstance<PlayerData>())
        mana.updatePower(mana.power - 100)
        return true
    }
}
