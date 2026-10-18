package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val EXTEND_COOLDOWN_SECONDS = 20

class SnakeSword : GameClass(), org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler,
    org.beobma.classWarPlugin.gameClass.handler.WeaponInputHandler,
    org.beobma.classWarPlugin.gameClass.handler.OnHitHandler,
    org.beobma.classWarPlugin.gameClass.handler.ConfirmedHitHandler {
    private val runtime by lazy { org.beobma.classWarPlugin.gameClass.snakesword.SnakeSwordRuntime(abilityScope) }
    override fun onBattleStart() = runtime.start()
    override fun onGameTimePasses() = Unit
    fun swing() = runtime.swing()
    override fun onWeaponLeftClick(event: org.bukkit.event.player.PlayerInteractEvent) {
        event.isCancelled = true
        swing()
    }
    override fun onAttackDamage(context: org.beobma.classWarPlugin.damage.DamageContext) {
        // Only the native weapon strike is suppressed; the replacement sweep remains a basic attack.
        if (context.weaponClassId == classId && !context.secondaryAttack) context.isCancelled = true
    }
    override fun onConfirmedHit(context: org.beobma.classWarPlugin.damage.DamageContext) = runtime.confirm(context)
    override val classId = "snake-sword"
    override val name = "<gray>사복검"
    override val rank = Rank.A
    override val classItemMaterial = Material.IRON_CHAIN
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf(
        RedSkill(),
    )

    override var passives: List<BasePassive> = listOf(
    )

    private class Weapon : BaseWeapon() {
        override val name = "<gray>사복검"
        override val description = listOf(
            "<gray>이 무기로 직접 가하는 피해는 0이 된다.",
            "<gray>이 무기의 공격 속도가 50% 감소한다.",
            "",
            "<gray>기본 공격 시전 시 전방 {g:range:7}칸의 넓은 영역을 사복검으로 휩쓸어 적중한 모든 적에게 {g:basic:5}의 피해를 입힌다.",
            "<gray>연결된 검날은 관성과 장력을 받아 유연하게 휘어지며, 검끝이 손잡이를 뒤따라 휩쓴다.",
            "<gray>이 피해는 기본 공격 피해로 간주된다."
        )
        override val material = Material.IRON_SWORD
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "snake-sword/extend"
        override val name = "<bold>연장"
        override val description = listOf(
            "<gray>{g:duration:5}초간 {keyword:Disarm} 상태가 되고, 바라보는 방향으로 사복검을 {g:range:12}칸까지 길게 늘린다.",
            "<gray>시선을 움직이면 연결된 검날이 관성과 장력에 따라 자연스럽게 휘어지며 따라온다.",
            "<gray>사복검의 속도에 비례하여 적중한 모든 적에게 피해를 입힌다. (최대 {g:damage:5})",
            "<gray>움직이지 않는 검날은 피해를 주지 않으며, 같은 대상은 0.5초마다 다시 적중할 수 있다.",
            "",
            "<dark_gray>한 번의 시전에서 같은 대상에게 적중할 때마다 피해량이 절반씩, 최소 {g:damage:1}까지 감소한다."
        )
        override val cooldown = EXTEND_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return runtime.extend()
        }
    }

}
