package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val CONSTELLATION_GUIDANCE_COOLDOWN_SECONDS = 30
private const val CONSTELLATION_RING_COOLDOWN_SECONDS = 30
private const val CONSTELLATION_DOMAIN_COOLDOWN_SECONDS = 300

class Constellations : GameClass(), org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler,
    org.beobma.classWarPlugin.gameClass.handler.ConfirmedHitHandler,
    org.beobma.classWarPlugin.gameClass.handler.StatusApplicationHandler {
    private lateinit var runtime: org.beobma.classWarPlugin.gameClass.constellations.ConstellationRuntime
    override fun onBattleStart() {
        runtime = org.beobma.classWarPlugin.gameClass.constellations.ConstellationRuntime(abilityScope)
        runtime.start()
    }
    override fun onGameTimePasses() = Unit
    override fun onConfirmedHit(context: org.beobma.classWarPlugin.damage.DamageContext) {
        if (::runtime.isInitialized) runtime.confirmed(context)
    }
    override fun onStatusApplied(target: org.beobma.classWarPlugin.entity.EntityData, status: org.beobma.classWarPlugin.status.StatusAbnormality) {
        if (::runtime.isInitialized) runtime.applied(target, status)
    }
    override fun onSuspend() { if (::runtime.isInitialized) runtime.cancelChallenge() }
    override val classId = "constellations"
    override val name = "<gray>별자리"
    override val rank = Rank.SPECIAL
    override val classItemMaterial = Material.NETHER_STAR
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        DomainSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive(),
        PassiveTwo()
    )

    private inner class RedSkill : Skill() {
        override val definitionId = "constellations/guidance"
        override val name = "<bold>별의 인도"
        override val description = listOf(
            "<gray>자신은 최대 5초간 {keyword:Disarm}, {keyword:Silence} 상태가 되며 받는 피해가 50% 감소된다.",
            "<gray>6줄 인벤토리가 열리고, 8개의 별 아이템이 무작위 위치에 생성된다.",
            "<gray>별 아이템의 순서는 아이템의 개수로 식별할 수 있다.",
            "",
            "<gray>5초 안에 무작위로 배치된 별 아이템을 1번부터 순서대로 클릭한다.",
            "<gray>시간 만료, 잘못된 별 선택, 창 닫기 또는 모든 별 선택 시 스킬이 종료된다.",
            "",
            "<gray>스킬 종료 시 자신 주위에 (선택하는데 성공한 별 아이템의 순서의 수를 모두 더한 값)개의 별을 소환한다.",
            "<gray>별은 하늘에서 자신 주변으로 마법진 모양으로 낙하하며, 아주 약한 유도 성능을 가진다.",
            "",
            "<gray>별에 적중한 적에게 0.1의 {keyword:TrueDamage}를 입히고",
            "<gray>10초간 출혈, 화상, 광휘, 동상 중 무작위 상태이상을 1 부여한다.",
            "<gray>이 효과로 부여하는 상태이상 각각의 수치는 4를 초과할 수 없다."
        )
        override val cooldown = CONSTELLATION_GUIDANCE_COOLDOWN_SECONDS

        override fun use(): Boolean {
            if (!runtime.guidance()) return false
            if (runtime.inDomain) multiplyCurrentCooldown(0.0)
            return true
        }
    }

    private inner class OrangeSkill : Skill() {
        override val definitionId = "constellations/ring"
        override val name = "<bold>고리"
        override val description = listOf(
            "<gray>공전 패시브로 인해 4개의 별이 공전 중인 적에게만 사용할 수 있다.",
            "",
            "<gray>10칸 내의 바라보는 적에게 공전 중인 모든 별을 회전시킨다.",
            "<gray>3초간 회전 속도가 점차 증가하다, 이후 고리를 이루며 모여들고, 적에게 {keyword:Settlement}을 적용한다.",
            "<gray>이후 해당 적에게 공전 중인 모든 별은 소멸한다."
        )
        override val cooldown = CONSTELLATION_RING_COOLDOWN_SECONDS

        override fun use(): Boolean {
            if (!runtime.ring()) return false
            if (runtime.inDomain) multiplyCurrentCooldown(0.0)
            return true
        }
    }

    // 다른 영역 스킬과 달리, 땅이 존재하지 않으며 50칸 너비는 우주 배경임.
    // 땅이 존재하지 않지만, 보이지 않는 배리어로 밟을 수 있으며, 이 떄문에 어느 각도에서나 별이 소환될 수 있음.
    private inner class DomainSkill : Skill() {
        override val definitionId = "constellations/domain"
        override val name = "<bold>「영역 전개」-「별이 빛나는 밤」"
        override val description = listOf(
            "<gray>16초간 50칸 너비의 {keyword:Area}을 전개한다.",
            "",
            "{keyword:Area}에서 자신이 생성하는 모든 별은 필중하며",
            "<gray>별은 하늘 뿐 아니라 360도 모든 각도에서 소환될 수 있다.",
            "<gray>적은 별에 스킬을 사용하거나 기본 공격을 하여 파괴할 수 있다.",
            "",
            "{keyword:Area}이 지속되는 동안 자신의 기본 스킬의 재사용 대기 시간은 최대 0초가 된다.",
            "",
            "<gray>영역 종료 후, 자신이 부여한 모든 상태이상과 공전 패시브를 통해 소환한 별은 제거된다.",
            "<gray>또한 20초간 공전 패시브가 작동하지 않는다."
        )
        override val cooldown = CONSTELLATION_DOMAIN_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return runtime.expand()
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>별 부르미"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 2회 적중 시 적 주변에 별을 소환한다.",
            "<gray>별은 하늘에서 낙하하며, 어느정도의 유도 성능을 가진다.",
            "",
            "<gray>별에 적중한 적에게 0.1의 {keyword:TrueDamage}를 입히고",
            "<gray>10초간 출혈, 화상, 광휘, 동상 중 무작위 상태이상을 1 부여한다.",
            "<gray>이 효과로 부여하는 상태이상 각각의 수치는 4를 초과할 수 없다."
        )
    }

    private class PassiveTwo : BasePassive() {
        override val name = "<bold>공전"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>적에게 출혈, 화상, 광휘, 동상 상태이상을 부여할 때",
            "<gray>10초간 해당 적의 주변을 공전하는 작은 별을 1개 소환한다. (상태이상마다 1개)",
            "<gray>새로운 상태이상을 부여할 때마다 해당 적의 주변을 공전하는 별의 지속시간은 초기화된다."
        )
    }
}
