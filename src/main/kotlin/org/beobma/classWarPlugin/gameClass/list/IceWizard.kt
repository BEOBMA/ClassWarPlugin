package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.event.PlayerSkillDamageByPlayerEvent
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.OnHitHandler
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.gameClass.WhenHitHandler
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.skill.*
import org.beobma.classWarPlugin.status.list.Frostbite
import org.beobma.classWarPlugin.status.list.Mana
import org.beobma.classWarPlugin.status.list.MoveSpeedDecrease
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

class IceWizard : GameClass(), GameStatusHandler {
    override val name = "<gray>얼음 마법사"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.BLUE_ICE
    override val weapon = IceWizardsStaff()

    override var skills: List<Skill> = listOf(
        IceWizardsRedSkill(),
        IceWizardsOrangeSkill(),
        IceWizardsYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        IceWizardsPassive()
    )

    override fun onBattleStart() {
        val mana = playerData.getOrCreateStatus { Mana() }
        mana.increasePower(100)
    }

    override fun onGameTimePasses() {
        val mana = playerData.getOrCreateStatus { Mana() }
        mana.increasePower(10)
    }
}

class IceWizardsStaff : Weapon() {
    override val name = "<gray>무기 이름"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.WOODEN_SWORD
}

class IceWizardsRedSkill : Skill() {
    private var bukkitTask: BukkitTask? = null
    private val applyDamagePlayerDatas: MutableMap<PlayerData, Int> = mutableMapOf()

    override val name = "<dark_blue><bold>눈폭풍"
    override val description = listOf(
        "<gray>사용 시 활성화되고 다시 사용 시 비활성화되는 스킬.",
        "<gray>초당 ${Keyword.Mana.string}를 10 소모하고 활성화할 수 있다.",
        "",
        "<gray>활성화 시 자신 주위 모든 적에게 초당 3의 피해를 입히고 ${Keyword.Frostbite.string}을 2 부여한다.",
        "",
        dictionary[Keyword.Frostbite] ?: "",
        dictionary[Keyword.Freezing] ?: "",
        dictionary[Keyword.AbnormalStatusDamage] ?: ""
    )
    override val cooldown = 1

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus { Mana() }

        if (bukkitTask != null) {
            bukkitTask?.cancel()
            bukkitTask = null
            applyDamagePlayerDatas.clear()
            return true
        }

        bukkitTask = object : BukkitRunnable() {
            override fun run() {
                if (mana.power < 10) {
                    cancel()
                    bukkitTask = null
                    return
                }
                val targets = playerData.radius(player.location, TargetType.Enemy, 3.0, false)
                targets.forEach {
                    if (applyDamagePlayerDatas.getOrDefault(it, 0) == 0) return@forEach
                    val frostbite = it.getOrCreateStatus { Frostbite() }
                    applyDamagePlayerDatas[it] = applyDamagePlayerDatas.getOrDefault(it, 20) - 1
                    it.damage(3.0, DamageType.Normal, playerData)
                    frostbite.increasePower(2)
                    frostbite.updateDuration(5)
                }
                mana.decreasePower(10)
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L)
        return true
    }
}

class IceWizardsOrangeSkill : Skill() {
    override val name = "<dark_blue><bold>고드름"
    override val description = listOf(
        "${Keyword.Mana.string}를 40 소모하고 사용할 수 있다.",
        "",
        "<gray>사용 시 바라보는 방향으로 고드름을 발사한다.",
        "<gray>적중한 적에게 8의 피해를 입히고 ${Keyword.Frostbite.string}을 4 부여한다.",
        "<gray>스킬 적중 시 소모한 ${Keyword.Mana.string}의 50%를 돌려받는다.",
        "",
        dictionary[Keyword.Frostbite] ?: "",
        dictionary[Keyword.Freezing] ?: "",
        dictionary[Keyword.AbnormalStatusDamage] ?: ""
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus { Mana() }
        if (mana.power < 40) {
            player.sendMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }

        mana.decreasePower(40)
        IceWizardsIcicleProjectile().spawnProjectile(playerData)
        return true
    }
}

class IceWizardsIcicleProjectile : Projectile() {
    override var location: Location = player.location
    override var targetType: TargetType = TargetType.Enemy
    override var speed: Double = 1.0
    override var isWallHit: Boolean = true
    override var isPlayerHit: Boolean = true
    override val isPlayerHitRemove: Boolean = true

    override fun onProjectilePlayerHit(hitPlayerData: PlayerData, location: Location) {
        val frostbite = hitPlayerData.getOrCreateStatus { Frostbite() }
        val mana = playerData.getOrCreateStatus { Mana() }
        hitPlayerData.damage(8.0, DamageType.Normal, playerData)
        frostbite.increasePower(4)
        frostbite.updateDuration(5)
        mana.increasePower(20)
    }
}

class IceWizardsYellowSkill : Skill() {
    override val name = "<dark_blue><bold>얼음의 창"
    override val description = listOf(
        "${Keyword.Mana.string}를 100 소모하고 사용할 수 있다.",
        "",
        "<gray>사용 시 8칸 내의 바라보는 블럭 주위에 얼음으로 만들어진 창을 생성해 떨어트린다.",
        "<gray>적중한 적에게 15의 피해를 입히고 ${Keyword.Frostbite.string}을 7 부여한다.",
        "<gray>스킬 적중 시 소모한 ${Keyword.Mana.string}를 돌려받는다.",
        "<dark_gray>웅크린 상태에서 사용하면 자신의 위치에 창을 떨어트릴 수도 있다.",
        "",
        dictionary[Keyword.Frostbite] ?: "",
        dictionary[Keyword.Freezing] ?: "",
        dictionary[Keyword.AbnormalStatusDamage] ?: ""
    )
    override val cooldown = 30

    override fun use(): Boolean {
        val mana = playerData.getOrCreateStatus { Mana() }
        if (mana.power < 100) {
            player.sendMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
            return false
        }
        val meteor = IceWizardsIceSpear()


        val location = if (player.isSneaking) {
            player.location.add(0.0, 5.0, 0.0)
        } else {
            playerData.shotLaserGetBlock(8.0)?.location?.add(0.0, 5.0, 0.0) ?: run {
                player.sendMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                return false
            }
        }
        meteor.location = location

        mana.decreasePower(100)
        meteor.spawnMeteor(playerData)
        return true
    }
}

class IceWizardsIceSpear : Meteor() {
    override var location: Location = player.location
    override var speed: Double = 1.0
    override var isWallHit: Boolean = true
    override var targetType: TargetType = TargetType.Enemy

    override fun onMeteorPlayerHit(hitPlayerData: PlayerData, location: Location) {
        val mana = playerData.getOrCreateStatus { Mana() }
        val frostbite = hitPlayerData.getOrCreateStatus { Frostbite() }
        hitPlayerData.damage(15.0, DamageType.Normal, playerData)
        frostbite.increasePower(7)
        frostbite.updateDuration(5)
        mana.increasePower(100)
    }
}

class IceWizardsPassive : Passive(), OnHitHandler, WhenHitHandler {
    override val name = "<dark_blue><bold>극저온"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>스킬 적중 시 5초간 적중한 적 주위에 접근 시 <gold><bold>이동 속도가 25% 감소</bold><gray>하는 영역을 생성한다.",
        "<gray>영역의 영향을 받은 적에게 ${Keyword.Frostbite.string}을 2 부여한다.",
        "<gray>이 효과는 영역 당 같은 대상에게 1번만 발동할 수 있다.",
        "",
        dictionary[Keyword.Frostbite] ?: "",
        dictionary[Keyword.Freezing] ?: "",
        dictionary[Keyword.AbnormalStatusDamage] ?: ""
    )

    override fun onHit(
        skillDamageEvent: PlayerSkillDamageByPlayerEvent?,
        attackDamageEvent: EntityDamageByEntityEvent?
    ) {
        return
    }

    override fun onAttackHit(event: EntityDamageByEntityEvent) {
        return
    }

    override fun onSkillAttackHit(event: PlayerSkillDamageByPlayerEvent) {
        IceWizardsFrostZone(event.entity.player.location.clone()).spawnFlooring(playerData)
    }

    override fun whenHit(
        skillDamageEvent: PlayerSkillDamageByPlayerEvent?,
        attackDamageEvent: EntityDamageByEntityEvent?
    ) {
        return
    }

    override fun whenAttackHit(event: EntityDamageByEntityEvent) {
        return
    }
    override fun whenSkillAttackHit(event: PlayerSkillDamageByPlayerEvent) {
        return
    }
}

class IceWizardsFrostZone(override var location: Location) : Flooring() {
    override var radius: Double = 4.0
    override var targetType: TargetType = TargetType.Enemy
    override var time: Int? = 100

    private val hitPlayerDatas: MutableList<PlayerData> = mutableListOf()

    override fun onFlooringPlayerHit(hitPlayerData: PlayerData, location: Location) {
        if (hitPlayerData in hitPlayerDatas) return
        val moveSpeedDecrease = hitPlayerData.addStatus(MoveSpeedDecrease())
        val frostbite = hitPlayerData.getOrCreateStatus { Frostbite() }
        moveSpeedDecrease.increasePower(25)
        frostbite.increasePower(2)
        frostbite.updateDuration(5)
        moveSpeedDecrease.setContinueWhileIf { hitPlayerDatas.contains(playerData) }
        hitPlayerDatas.add(hitPlayerData)
    }

    override fun onFlooringPlayerOut(hitPlayerData: PlayerData, location: Location) {
        hitPlayerDatas.remove(hitPlayerData)
    }

    override fun onFlooringEnd() {
        hitPlayerDatas.clear()
    }
}