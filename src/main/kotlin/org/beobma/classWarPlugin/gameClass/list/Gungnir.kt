package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 20

class Gungnir : GameClass(), org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler,
    org.beobma.classWarPlugin.gameClass.handler.OnHitHandler,
    org.beobma.classWarPlugin.gameClass.handler.ConfirmedHitHandler,
    org.beobma.classWarPlugin.gameClass.handler.WeaponInputHandler {
    private lateinit var runtime: org.beobma.classWarPlugin.gameClass.relic.RelicRuntime
    override fun onBattleStart() { runtime=org.beobma.classWarPlugin.gameClass.relic.RelicRuntime(abilityScope); runtime.start() }
    override fun onGameTimePasses() = Unit
    override fun onAttackHit(context: org.beobma.classWarPlugin.damage.DamageContext) {
        if(!context.secondaryAttack) context.addDamageDealtMultiplier(0.5)
    }
    override fun onConfirmedHit(context: org.beobma.classWarPlugin.damage.DamageContext) {
        if(!::runtime.isInitialized) return
        if(runtime.confirmed(context)) return
        if(context.path.isBasicAttack && !context.secondaryAttack) runtime.basicGungnir(context.target)
    }
    override fun onWeaponRightClick(event: org.bukkit.event.player.PlayerInteractEvent) {
        event.isCancelled=true
        if(canThrow()) runtime.throwSpear()
    }
    override fun onWeaponInteractEntity(event: org.bukkit.event.player.PlayerInteractEntityEvent) {
        event.isCancelled=true
        if(canThrow()) runtime.throwSpear()
    }
    private fun canThrow() = ::runtime.isInitialized && !game.isPaused && playerStatus.canSkillUse &&
        !playerData.hasStatus<org.beobma.classWarPlugin.status.list.Silence>() &&
        !playerData.hasStatus<org.beobma.classWarPlugin.status.list.Disarm>() &&
        !playerData.hasStatus<org.beobma.classWarPlugin.status.list.Stun>()
    override val classId = "gungnir"
    override val name = "<gray>궁니르"
    override val rank = Rank.S
    override val classItemMaterial = Material.TRIDENT
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
    )

    // 생긴 형태만 삼지창 텍스쳐를 사용.
    // 실제 성능은 철 검
    private class Weapon : BaseWeapon() {
        override val name = "<gray>궁니르"
        override val description = listOf(
            "<gray>기본 공격 시 피해량이 50% 감소한다.",
            "<gray>대신 기본 공격 적중 시 10초간 {keyword:Vibration}을 1 부여한다.",
            "",
            "<gray>우클릭 시 바라보는 방향으로 궁니르를 던진다.",
            "<gray>투척 후 ${org.beobma.classWarPlugin.gameClass.relic.SpearThrowCooldown.SECONDS}초간 다시 투척할 수 없으며, 회수해도 대기 시간은 유지된다.",
            "<gray>투척된 궁니르는 적중 시 6의 피해를 입히고, {keyword:Vibration}을 3 부여한다.",
            "<gray>투척 시작 위치에서 4칸 이내의 대상에게 적중하면 투척 피해가 3으로 감소한다.",
            "<gray>던진 즉시 궁니르는 인벤토리에서 사라진다.",
            "",
            "<dark_gray>궁니르가 적이나 블록에 적중하면 해당 위치에 박힌다.",
            "<dark_gray>박힌 궁니르에 다가가면 궁니르를 회수할 수 있다.",
            "<dark_gray>투척된 궁니르는 아주 약한 유도 성능을 가진다."
        )
        override val material = Material.TRIDENT
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "gungnir/recall"
        override val name = "<bold>회수"
        override val description = listOf(
            "<gray>인벤토리에 궁니르가 없을 때에만 사용할 수 있다.",
            "",
            "<gray>궁니르가 자신에게 되돌아오며 경로에 있는 모든 적에게 3의 피해를 입힌다.",
            "<gray>처음 적중한 적에게는 10초간 {keyword:VibrationExplosion}을 적용한다.",
            "",
            "<gray>만약 궁니르가 적에게 박혀있었다면 해당 적에게는 5의 피해를 입히고",
            "<gray>{keyword:Bleeding}을 4 부여한다.",
            "",
            "<dark_gray>이 스킬은 궁니르가 날아가는 도중에도 사용할 수 있다."
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return runtime.recall()
        }
    }
}
