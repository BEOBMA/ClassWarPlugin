package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val ARTIST_RED_SKILL_COOLDOWN_SECONDS = 40

class Artist : GameClass(), org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler,
    org.beobma.classWarPlugin.gameClass.handler.ConfirmedHitHandler,
    org.beobma.classWarPlugin.status.handler.StatusPlayerMoveHandler {
    private val runtime by lazy { org.beobma.classWarPlugin.gameClass.art.ArtistRuntime(abilityScope) }
    override fun onBattleStart() = runtime.start()
    override fun onGameTimePasses() = Unit
    override fun onConfirmedHit(context: org.beobma.classWarPlugin.damage.DamageContext) {
        if (context.path.isBasicAttack && !context.secondaryAttack) runtime.paintAround(context.target.entity.location, 3, 200)
    }
    override fun onPlayerMove(event: org.bukkit.event.player.PlayerMoveEvent, playerData: org.beobma.classWarPlugin.entity.player.PlayerData) = runtime.lockView(event)
    override val classId = "artist"
    override val name = "<gray>화가"
    override val rank = Rank.S
    override val classItemMaterial = Material.BRUSH
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive(),
        PassiveTwo()
    )


    private inner class RedSkill : Skill(), org.beobma.classWarPlugin.skill.MovementSkill {
        override val definitionId = "artist/overpaint"
        override val name = "<bold>덧칠"
        override val description = listOf(
            "<gray>{g:duration:10}초간 {keyword:Disability}, {keyword:Stealth} 상태가 된다.",
            "",
            "<gray>하늘 높은 곳으로 올라가 아래를 바라보는 방향으로 시점이 고정된다.",
            "<gray>행동 불가 상태에서도 WASD 키로 고정된 화면 기준 상하좌우로 이동할 수 있다.",
            "<gray>마우스를 움직여도 시점이나 위치는 변경되지 않는다.",
            "",
            "<gray>스킬 지속 중 자신 아래 3x3 범위 내에 있는 모든 적에게 초당 {g:damage:2}의 피해를 입힌다.",
            "<gray>또한 자신 아래 3x3 범위 내에 있는 공기에 드러난 모든 블럭을 15초간 색체 블럭으로 만든다.",
            "",
            "<dark_gray>자신의 Y축 높이는 가장 높은 블럭보다 20칸 높은 곳으로 설정된다.",
            "<dark_gray>스킬 종료 후 자신은 원래 위치로 이동한다."
        )
        override val cooldown = ARTIST_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return runtime.overpaint(growthDuration(200))
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>색채 블럭"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 적중 시 대상 주변 3칸 범위의 블럭을 칠해 10초간 색채 블럭으로 만든다.",
            "",
            "<gray>자신은 색체 블럭에 닿은 상태에서 <gold><bold>이동 속도가 20% 증가</bold><gray>한다.",
            "<gray>색체 블럭에 닿은 적은 초당 색체 블럭 단계에 비례한 피해를 입는다. (최소 0.01, 최대 4)",
        )
    }

    private class PassiveTwo : BasePassive() {
        override val name = "<bold>덧칠"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>색체 블럭 위에서 적이 피해를 입을 때마다 주변 색체 블럭의 단계가 증가한다.",
            "<gray>색체 단계가 증가할 수록, 색체 블럭은 알록달록한 색에서 붉은 색으로 변화한다."
        )
    }
}
