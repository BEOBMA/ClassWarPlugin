package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ability.AbilityExecution

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.GameManager.gameClassList
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val CONTRACTOR_CONTRACT_COOLDOWN_SECONDS = 8
private const val CONTRACTOR_ORANGE_COOLDOWN_SECONDS = 12
private const val CONTRACTOR_YELLOW_COOLDOWN_SECONDS = 16
private const val CONTRACTOR_GREEN_COOLDOWN_SECONDS = 90

class Contractor : GameClass() {
    override val classId = "contractor"
    override val name = "<gray>청부업자"
    override val rank = Rank.S
    override val classItemMaterial = Material.SULFUR_CUBE_BUCKET
    override var skills: List<Skill> = listOf(RedSkill(), OrangeSkill(), YellowSkill(), GreenSkill())
    override var passives: List<BasePassive> = listOf(Passive(), PassiveTwo())

    private class RedSkill : Skill() {
        override val definitionId = "contractor/red-skill"
        override val name = "<bold>찌르기"
        override val description = listOf(
            "<gray>바라보는 방향으로 잛게 칼을 찔러 적에게 3의 피해를 입힌다.",
            "<gray>적중 시 재사용 대기 시간이 3초 감소하며, 다음 찌르기가 강화된다.",
            "",
            "<gray>강화된 찌르기 발동 시 사거리가 소폭 증가하고",
            "<gray>적중 여부와 관계 없이 사거리 끝자락에 단검을 생성한다."
        )
        override val cooldown = CONTRACTOR_CONTRACT_COOLDOWN_SECONDS


        override fun isUseSuccess(): Boolean { return true }

        override fun use(): Boolean { return true }
    }

    private class OrangeSkill : Skill() {
        override val definitionId = "contractor/orange-skill"
        override val name = "<bold>순보"
        override val description = listOf(
            "<gray>8칸 내의 바라보는 적의 뒤 또는 단검의 위치로 순간이동한다.",
            "<gray>이동 경로에 있던 모든 적에게 2의 피해를 입힌다.",
            "<gray>단검을 회수하면 이 스킬의 재사용 대기 시간이 초기화된다.",
            "",
            "<dark_gray>이 스킬 대신 검을 우클릭하여 사용할 수도 있다."
        )
        override val cooldown = CONTRACTOR_ORANGE_COOLDOWN_SECONDS


        override fun isUseSuccess(): Boolean { return true }

        override fun use(): Boolean { return true }
    }

    private class YellowSkill : Skill() {
        override val definitionId = "contractor/yellow-skill"
        override val name = "<bold>암살"
        override val description = listOf(
            "<gray>바라보는 방향으로 단검을 던지고 자신은 약간 뒤로 이동한다.",
            "<gray>단검이 적에게 적중하면 2의 피해를 입히고, 적 뒤에 단검을 생성한다.",
            "<gray>이 스킬에 적중한 적은 5초간 단검을 회수하여 입히는 피해가 추가로 2번 적중한다."
        )
        override val cooldown = CONTRACTOR_YELLOW_COOLDOWN_SECONDS


        override fun isUseSuccess(): Boolean { return true }

        override fun use(): Boolean { return true }
    }

    //        †
    //
    //    †   나   †
    //
    //        †
    // 위와 같은 형태
    private class GreenSkill : Skill() {
        override val definitionId = "contractor/green-skill"
        override val name = "<bold>장부 정리"
        override val description = listOf(
            "<gray>자신 주변 십자 범위로 4개의 단검을 생성한다.",
            "<gray>주변 모든 적에게 2의 피해를 입힌다.",
            "<gray>십자 범위 내에 벽이 존재한다면 단검은 벽에서 멈춰서 생성된다."
        )
        override val cooldown = CONTRACTOR_GREEN_COOLDOWN_SECONDS


        override fun isUseSuccess(): Boolean { return true }

        override fun use(): Boolean { return true }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>회수"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>단검에 닿으면 단검 주위 8칸 이내의 적 하나에게 단검을 던져 1의 {keyword:TrueDamage}를 입힌다.",
            "",
            "<dark_gray>암살 스킬에 적중된 적을 우선적으로 공격하며, 체력이 낮은 적을 우선적으로 공격한다."
        )
    }

    private class PassiveTwo : BasePassive() {
        override val name = "<bold>깔끔한 처리"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>적에게 스킬로 피해를 입힐 때마다 처리 스택을 1 얻는다. (최대 5)",
            "<gray>처리 중첩이 최대치일 때 소모하여 다음 기본 공격 적중 시 적중한 적 뒤에 단검을 생성한다."
        )
    }
}
