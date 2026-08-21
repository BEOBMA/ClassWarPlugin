package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Mana
import org.beobma.classWarPlugin.status.list.Shield
import org.beobma.classWarPlugin.status.list.Vibration
import org.beobma.classWarPlugin.status.list.VibrationExplosion
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.damage.DamageContext
import org.bukkit.Material

class LandWizard : GameClass(), GameStatusHandler {
    override val name = "<gray>대지 마법사"
    override val rank = Rank.C
    override val classItemMaterial = Material.SANDSTONE
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    override fun onBattleStart() {
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        mana.increasePower(100)
    }

    override fun onGameTimePasses() {
        val mana = playerData.getOrCreateStatus(playerData) { Mana() }
        mana.increasePower(10)
    }


    private class Weapon : BaseWeapon() {
        override val name = "<gray>대인용 지팡이"
        override val description = listOf("<gray>검처럼 사용할 수 있는 지팡이.")
        override val material = Material.WOODEN_SWORD
    }

    private class RedSkill : Skill() {
        override val name = "<gold><bold>지진"
        override val description = listOf(
            "{keyword:Mana}를 20 소모하고 사용할 수 있다.",
            "",
            "<gray>사용 시 주위 모든 적에게 2의 피해를 입히고 10초간 {keyword:Vibration}을 2 부여한다.",
            "",
            Keyword.Vibration.description!!,
            Keyword.AbnormalStatusDamage.description!!
        )
        override val cooldown = 2

        override fun use() {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            mana.decreasePower(20)
            val targets = playerData.radius(player.location, TargetType.Enemy, 4.0, false)
            targets.forEach {
                val vibration = it.getOrCreateStatus(playerData) { Vibration() }
                vibration.applyStatus(duration = 10, powerDelta = 2)
                it.damage(2.0, DamageType.Normal, playerData)
            }
        }

        override fun isUseSuccess(): Boolean {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            if (mana.power < 20) {
                player.sendMiniMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
                return false
            }
            return true
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<gold><bold>탄성 반발"
        override val description = listOf(
            "{keyword:Mana}를 100 소모하고 사용할 수 있다.",
            "",
            "<gray>사용 시 5초간 <aqua><bold>8의 피해를 막는 보호막</bold><gray>을 얻고 주위 모든 적에게 {keyword:VibrationExplosion}을 적용한다.",
            "",
            Keyword.VibrationExplosion.description!!,
            Keyword.AbnormalStatusDamage.description!!
        )
        override val cooldown = 10

        override fun use() {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            val shield = playerData.addStatus(Shield(), playerData)

            mana.decreasePower(100)
            shield.applyStatus(duration = 5, powerDelta = 8)

            val targets = playerData.radius(player.location, TargetType.Enemy, 4.0, false)
            targets.forEach {
                val vibrationExplosion = it.addStatus(VibrationExplosion(), playerData)
                vibrationExplosion.applyStatus(duration = 1, powerDelta = 1)
            }
        }

        override fun isUseSuccess(): Boolean {
            val mana = playerData.getOrCreateStatus(playerData) { Mana() }
            if (mana.power < 100) {
                player.sendMiniMessage("<red><bold>[!] 마나가 부족하여 스킬을 사용할 수 없습니다.")
                return false
            }
            return true
        }
    }

    private class Passive : BasePassive(), WhenHitHandler {
        override val name = "<gold><bold>암석화"
        override val description = listOf(
            "<gray>기본 공격 피격 시 <gold><bold>받는 피해가 30% 감소</bold><gray>한다."
        )

        override fun whenAttackHit(event: DamageContext) {
            event.addDamageDealtMultiplier(0.7)
        }
    }
}
