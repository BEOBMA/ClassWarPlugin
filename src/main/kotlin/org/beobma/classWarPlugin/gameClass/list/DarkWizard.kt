package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.skill.*
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
        val mana = playerData.getOrCreateStatus { Mana() }
        mana.updatePower(100)
    }

    override fun onGameTimePasses() {
        val mana = playerData.getOrCreateStatus { Mana() }
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
        "${Keyword.Mana.string}를 40 소모하고 사용할 수 있다.",
        "",
        "<gray>자신 위치에 4초간 유지되는 연기를 형성한다.",
        "<gray>연기 속에 들어온 아군은 연기에 가려 숨겨지지만, 적은 숨겨지지 않는다.",
        "<dark_gray>웅크린 상태에서 사용하면 4칸 내의 바라보는 블럭에 연기를 형성할 수도 있다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus { Mana() }
        if (mana.power < 40) {
            player.sendMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }

        val smoke = DarkWizardsSmoke()

        smoke.location = if (player.isSneaking) {
            playerData.shotLaserGetBlock(4.0)?.location?.add(0.5, 1.0, 0.5) ?: run {
                player.sendMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
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

    override var location: Location = player.location
    override var radius: Double = 5.0
    override var targetType: TargetType = TargetType.Enemy
    override var time: Int? = 4

    override fun onFlooringPlayerHit(hitPlayerData: PlayerData, location: Location) {
        hitPlayerData.player.isGlowing = true
        applied.add(hitPlayerData)
    }

    override fun onFlooringPlayerOut(hitPlayerData: PlayerData, location: Location) {
        hitPlayerData.player.isGlowing = false
        applied.remove(playerData)
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
        "${Keyword.Mana.string}를 60 소모하고 사용할 수 있다.",
        "",
        "<gray>바라보는 방향으로 잠식된 연기를 발사한다.",
        "<gray>적중한 모든 적에게 5의 피해를 입히고 3초간 ${Keyword.Abyss.string} 상태를 적용한다.",
        "",
        dictionary[Keyword.Abyss] ?: ""
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus { Mana() }
        if (mana.power < 60) {
            player.sendMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }

        val projectile = DarkWizardsProjectileSmoke()

        projectile.spawnProjectile(playerData)
        mana.decreasePower(60)
        return true
    }
}

class DarkWizardsProjectileSmoke : Projectile() {
    override var location: Location = player.location
    override var targetType: TargetType = TargetType.Enemy
    override var speed: Double = 0.5
    override var isWallHit: Boolean = false
    override var isPlayerHit: Boolean = true
    override val isPlayerHitRemove: Boolean = false

    private val hitSet = mutableSetOf<PlayerData>()

    override fun onProjectilePlayerHit(hitPlayerData: PlayerData, location: Location) {
        if (hitSet.add(hitPlayerData)) {
            val abyss = hitPlayerData.getOrCreateStatus { Abyss() }
            abyss.updateDuration(3)
            hitPlayerData.damage(5.0, DamageType.Normal, playerData)
        }
    }
}

class DarkWizardsYellowSkill : Skill() {
    private val abyssPlayers = mutableSetOf<PlayerData>()

    override val name = "<bold>심연의 공포"
    override val description = listOf(
        "${Keyword.Mana.string}를 100 소모하고 사용할 수 있다.",
        "",
        "<gray>5초간 전장을 연기로 가득 채워 모든 대상을 ${Keyword.Abyss.string} 상태로 만든다.",
        "<gray>이 효과 발동 전을 기준으로 한 번이라도 ${Keyword.Abyss.string} 상태였던 적은 추가로 지속 시간동안 ${Keyword.Silence.string} 상태가 된다.",
        "",
        dictionary[Keyword.Abyss] ?: "",
        dictionary[Keyword.Silence] ?: ""
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus { Mana() }
        if (mana.power < 100) {
            player.sendMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }

        val allPlayers = playerData.radius(player.location, TargetType.All, 1000.0, true)
        val enemies = playerData.radius(player.location, TargetType.Enemy, 1000.0, false)

        enemies.forEach {
            if (abyssPlayers.contains(it)) {
                val silence = it.getOrCreateStatus { Silence() }
                silence.updateDuration(5)
            }
        }

        allPlayers.forEach {
            val abyss = it.getOrCreateStatus { Abyss() }
            abyss.updateDuration(5)
        }

        abyssPlayers.addAll(allPlayers)
        mana.updatePower(mana.power - 100)
        return true
    }
}
