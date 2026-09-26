package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 40

class Mjolnir : GameClass(), org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler,
    org.beobma.classWarPlugin.gameClass.handler.ConfirmedHitHandler,
    org.beobma.classWarPlugin.gameClass.handler.ResonanceResourceUser {
    private lateinit var runtime: org.beobma.classWarPlugin.gameClass.relic.RelicRuntime
    override fun onBattleStart() { runtime=org.beobma.classWarPlugin.gameClass.relic.RelicRuntime(abilityScope); runtime.start() }
    override fun onGameTimePasses() = Unit
    override fun onConfirmedHit(context: org.beobma.classWarPlugin.damage.DamageContext) {
        if(!::runtime.isInitialized) return
        if(runtime.confirmed(context)) return
        if(context.path.isBasicAttack && !context.secondaryAttack) runtime.basicMjolnir(context.target)
    }
    override val classId = "mjolnir"
    override val name = "<gray>묠니르"
    override val rank = Rank.S
    override val classItemMaterial = Material.MACE
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf()

    // 생긴 형태만 철퇴 텍스쳐를 사용.
    // 실제 성능은 철 검
    private class Weapon : BaseWeapon() {
        override val name = "<gray>묠니르"
        override val description = listOf(
            "<gray>기본 공격 적중 시 대상에게 {keyword:Aftermath}을 5 부여한다.",
            "",
            "{keyword:Resonance}이 있는 적에게 기본 공격 적중 시 {keyword:Resonance}을 1 소모하고 해당 적과 그 뒤의 모든 적에게",
            "{keyword:Electrocution}을 부여한다.",
            "<dark_gray>뒤쪽 전도 범위는 12칸이다."
        )
        override val material = Material.MACE
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "mjolnir/tesla"
        override val name = "<bold>테슬라"
        override val description = listOf(
            "<gray>바라보는 방향으로 제어 불가능한 번개를 방출한다.",
            "<gray>번개는 지면을 따라 좌우로 흔들리며 최대 약 36칸까지 5갈래로 나아가며",
            "<gray>적중한 적에게 4의 피해를 입힌다.",
            "<gray>추가로 {keyword:Aftermath}을 10 부여하고 {keyword:Electrocution}을 부여한다.",
            "",
            "{keyword:Resonance}이 있는 적에게 적중 시 {keyword:Resonance}을 1 소모하고",
            "<gray>해당 적의 위치에 번개를 떨어트려 3의 피해를 추가로 입히고 {keyword:Electrocution}을 부여한다."
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return runtime.tesla()
        }
    }
}
