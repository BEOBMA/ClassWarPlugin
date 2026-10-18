package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val PRISM_COOLDOWN_SECONDS = 0
private const val LIGHT_COOLDOWN_SECONDS = 8
private const val DOMAIN_COOLDOWN_SECONDS = 300

class Kaleidoskop : GameClass(), org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler {
    private val runtime by lazy { org.beobma.classWarPlugin.gameClass.kaleidoscope.KaleidoscopeRuntime(abilityScope) }
    override fun onBattleStart() = runtime.start()
    override fun onGameTimePasses() = Unit
    override fun onSuspend() = runtime.clearForSuspend()
    override val classId = "kaleidoskop"
    override val name = "<gray>만화경"
    override val rank = Rank.SPECIAL
    override val classItemMaterial = Material.AMETHYST_SHARD
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        DomainSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private inner class RedSkill : Skill() {
        override val definitionId = "kaleidoskop/prism"
        override val name = "<bold>분광기"
        override val description = listOf(
            "<gray>{g:range:10}칸 내의 바라보는 블럭 표면에 분광기를 설치한다. (최대 7개)",
            "<gray>웅크린 상태에서 사용하면 설치된 모든 분광기를 제거한다.",
            "",
            "<gray>분광기 주변 8칸 이내에 다른 분광기가 존재한다면",
            "<gray>분광기 사이가 거울로 연결된다.",
            "<gray>확산된 빛은 연결된 분광기로 전달되며, 한 번의 천광에서 각 분광기는 한 번만 반응한다."
        )
        override val cooldown = PRISM_COOLDOWN_SECONDS
        override fun isUseSuccess() = runtime.canPlace()

        override fun use(): Boolean {
            return runtime.place()
        }
    }

    private inner class OrangeSkill : Skill() {
        override val definitionId = "kaleidoskop/light"
        override val name = "<bold>천광"
        override val description = listOf(
            "<gray>{g:range:8}칸 내의 바라보는 블럭에 0.7초 후 하늘에서 내려오는 레이저를 발사한다.",
            "<gray>레이저에 적중한 모든 적에게 {g:damage:5}의 피해를 입힌다.",
            "",
            "<gray>레이저가 떨어진 곳 주변 4칸 내에 분광기가 존재한다면",
            "<gray>분광기가 빛을 확산시켜 주변 4칸까지 빛의 파장을 일으킨다.",
            "<gray>빛의 파장에 적중한 모든 적은 {g:damage:1}의 피해를 입는다."
        )
        override val cooldown = LIGHT_COOLDOWN_SECONDS
        override fun isUseSuccess() = runtime.canFire()

        override fun use(): Boolean {
            return runtime.fire()
        }
    }

    private inner class DomainSkill : Skill() {
        override val definitionId = "kaleidoskop/domain"
        override val isDomainExpansion = true
        override val name = "<bold>「영역 전개」-「만화경」"
        override val description = listOf(
            "<gray>16초간 40칸 너비의 {keyword:Area}을 전개한다.",
            "",
            "{keyword:Area}에서 영역 내부의 모든 공간이 분광기로 연결된다.",
            "<gray>이 분광기에 확산된 빛은 1의 피해 대신, 0.1의 피해를 입힌다.",
            "",
            "<gray>영역 종료 후, 자신이 소환한 모든 분광기는 제거된다.",
            "<gray>또한 20초간 컬라이더스코프 패시브가 발동하지 않는다."
        )
        override val cooldown = DOMAIN_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return runtime.expand()
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold><#ff5757>컬<#ff9e45>라<#ffe36b>이<#62e899>더<#59d9ff>스<#7287ff>코<#cc83ff>프</bold>"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>분광기에 확산된 빛의 파장에 적중한 적에게 {keyword:Brightness}를 1 부여한다."
        )
    }
}
