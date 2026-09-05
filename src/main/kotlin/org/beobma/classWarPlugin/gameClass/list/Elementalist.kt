package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.ElementCastMode
import org.beobma.classWarPlugin.util.ElementalistRuntime
import org.bukkit.Material
import org.bukkit.event.entity.EntityDamageEvent
import org.beobma.classWarPlugin.skill.Passive as BasePassive

private const val ELEMENTALIST_MANIFEST_COOLDOWN_SECONDS = 1
private const val ELEMENTALIST_RELEASE_COOLDOWN_SECONDS = 1
private const val ELEMENTALIST_ATTUNE_COOLDOWN_SECONDS = 1
private const val ELEMENTALIST_TRANSPOSE_COOLDOWN_SECONDS = 0

class Elementalist : GameClass(), GameStatusHandler, GameEndHandler, PlayerDeathHandler,
    EnvironmentalDamageHandler {

    override val classId = "elementalist"
    override val name = "<gray>원소술사"

    override val rank = Rank.L

    override val classItemMaterial = Material.STICK

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        YellowSkill(),
        GreenSkill(),
    )

    override var passives: List<BasePassive> = listOf(
        Passive(),
        PassiveTwo(),
        PassiveThree(),
        PassiveFour(),
    )

    private var runtime: ElementalistRuntime? = null

    override fun onBattleStart() {
        runtime?.cleanup()
        runtime = ElementalistRuntime(playerData).also(ElementalistRuntime::start)
    }

    override fun onGameTimePasses() = Unit

    override fun onGameEnd() {
        runtime?.cleanup()
        runtime = null
    }

    override fun onPlayerDeath() = onGameEnd()

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (runtime?.consumeFallImmunity(event) == true) event.isCancelled = true
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "elementalist/red-skill"
        override val name = "<bold>발현"

        override val description = listOf(
            "<gray>저장된 원소가 있을 때에만 사용할 수 있다.",
            "<gray>사용 후 원소는 소모된다.",
            "",
            "<gray>저장된 원소를 발현시킨다.",
            "<gray> - 발현:",
            "<gray>  - 흙: 암석을 발사하여 처음 적중한 적에게 4의 피해를 입히고 밀쳐낸다.",
            "<gray>  - 불: 화염구를 발사하여 처음 적중한 적에게 3의 피해를 입히고 3초간 {keyword:Burn} 상태로 만든다.",
            "<gray>  - 물: 물로 만들어진 창을 발사하여 적중한 최대 2명의 적에게 2.5의 피해를 입히고 이동 속도를 10% 감소시킨다.",
            "<gray>  - 공기: 압축된 공기를 발사하여 적중한 모든 적에게 2의 피해를 입힌다. 이 피해는 방어력을 20% 무시한다.",
        )

        override val cooldown = ELEMENTALIST_MANIFEST_COOLDOWN_SECONDS

        override fun isUseSuccess(): Boolean = runtime?.canCast(ElementCastMode.MANIFEST) == true

        override fun use(): Boolean = runtime?.cast(ElementCastMode.MANIFEST) ?: false
    }

    private inner class OrangeSkill : Skill() {
        override val definitionId = "elementalist/orange-skill"
        override val name = "<bold>방출"

        override val description = listOf(
            "<gray>저장된 원소가 있을 때에만 사용할 수 있다.",
            "<gray>사용 후 원소는 소모된다.",
            "",
            "<gray>저장된 원소를 방출시킨다.",
            "<gray> - 방출:",
            "<gray>  - 흙: 바라보는 방향으로 4초간 돌벽을 설치한다.",
            "<gray>  - 불: 자신의 위치에 4초간 불타는 지대를 만들어 적이 {keyword:Burn} 상태라면 {keyword:Burn} 지속시간은 줄어들지 않는다.",
            "<gray>  - 물: 자신의 위치에 5초간 물의 영역을 만들어 적의 이동 속도를 20% 감소시키고 1초마다 자신의 체력을 1 회복한다.",
            "<gray>  - 공기: 바라보는 방향으로 3초간 유지되는 소용돌이를 만들어 날린다. 주변 적은 중앙으로 끌어당겨진다.",
        )

        override val cooldown = ELEMENTALIST_RELEASE_COOLDOWN_SECONDS

        override fun isUseSuccess(): Boolean = runtime?.canCast(ElementCastMode.RELEASE) == true

        override fun use(): Boolean = runtime?.cast(ElementCastMode.RELEASE) ?: false
    }

    private inner class YellowSkill : Skill() {
        override val definitionId = "elementalist/yellow-skill"
        override val name = "<bold>감응"

        override val description = listOf(
            "<gray>저장된 원소가 있을 때에만 사용할 수 있다.",
            "<gray>사용 후 원소는 소모된다.",
            "",
            "<gray>저장된 원소에 감응한다.",
            "<gray> - 감응:",
            "<gray>  - 흙: 3초간 자신이 받는 피해가 20% 감소하고, 자신의 이동 속도가 20% 감소한다.",
            "<gray>  - 불: 바라보는 방향으로 빠르게 돌진하며, 지나간 경로에 3초간 불길을 남긴다. 불길에 닿은 적은 1초간 {keyword:Burn} 상태가 된다.",
            "<gray>  - 물: 자신에게 적용된 침묵, 속박을 제외한 부정적인 상태이상 하나를 해제한다.",
            "<gray>  - 공기: 바라보는 방향을 향해 하늘로 크게 도약한다. 이후 처음 받는 낙하 피해는 0이 된다.",
        )

        override val cooldown = ELEMENTALIST_ATTUNE_COOLDOWN_SECONDS

        override fun isUseSuccess(): Boolean = runtime?.canCast(ElementCastMode.ATTUNE) == true

        override fun use(): Boolean = runtime?.cast(ElementCastMode.ATTUNE) ?: false
    }

    private inner class GreenSkill : Skill() {
        override val definitionId = "elementalist/green-skill"
        override val name = "<bold>전위"

        override val description = listOf(
            "<gray>원소 배열의 가장 앞 원소와 그 뒤 원소의 순서를 교체한다.",
            "<gray>앞 원소가 저장된 상태였다면 저장을 해제하고 그 뒤 원소를 저장 상태로 만든다."
        )

        override val cooldown = ELEMENTALIST_TRANSPOSE_COOLDOWN_SECONDS

        override fun isUseSuccess(): Boolean = runtime?.canTranspose() == true

        override fun use(): Boolean = runtime?.transpose() ?: false
    }

    private class Passive : BasePassive() {
        override val name = "<bold>4원소"

        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>자신은 원소 배열을 가지며, 원소를 소모한 후 자동으로 {keyword:Charge}을 20 소모하여 다음 원소를 저장한다.",
            "<gray>원소 배열에서 앞으로 저장할 원소 5개의 순서를 항상 볼 수 있다.",
            "<gray>원소 배열의 요소는 흙, 불, 물, 공기 중 무작위로 배치된다.",
            "<gray>최근 사용 원소와 연계 시간이 표시되며, 배열의 표식으로 다음 공명·융합·해방 여부를 미리 확인할 수 있다.",
            "<light_purple>✦ 공명 강화</light_purple> <dark_gray>|</dark_gray> <aqua>◇ 공명</aqua> " +
                "<dark_gray>|</dark_gray> <yellow>◆ 융합</yellow> <dark_gray>|</dark_gray> <gold>★ 해방</gold>",
        )

    }

    private class PassiveTwo : BasePassive() {
        override val name = "<bold>융합"

        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>3초 이내에 2개의 원소를 소모하면 융합이 발생한다.",
            "<gray>",
            "<gray> - 융합:",
            "<gray>  - 불 → 공기: 불 원소를 소모한 스킬 효과가 공기 스킬을 소모한 스킬의 효과를 따라 퍼진다.",
            "<gray>  - 공기 → 불: 불 원소를 소모한 스킬 적중 시 큰 폭발이 발생하여 추가 피해를 2 입힌다.",
            "<gray>  - 불 → 물: 물 원소를 소모한 스킬 적중 시 적중한 위치에 3초간 지속되는 증기구름이 생성된다.",
            "<gray>  - 물 → 불: 불 원소를 소모한 스킬 적중 시 대상의 이동 속도 감소 효과와 {keyword:Burn} 상태가 제거되고 2의 {keyword:TrueDamage}를 입힌다.",
            "<gray>  - 불 → 흙: 흙 원소를 소모한 스킬 효과가 불 원소의 특성도 갖게 효과가 변한다.",
            "<gray>  - 흙 → 불: 불 원소를 소모한 스킬 적중 시 적중한 위치 아래에서 1초 후 추가 폭발이 발생하여 2의 피해를 입히고 공중으로 띄운다.",
            "<gray>  - 물 → 흙: 흙 원소를 소모한 스킬의 효과에 닿거나 영향을 받은 적의 이동 속도가 50% 추가로 감소한다.",
            "<gray>  - 흙 → 물: 물 원소를 소모한 스킬 적중 시 4초간 대상이 받는 피해를 15% 증가시킨다.",
            "<gray>  - 물 → 공기: 공기 원소를 소모한 스킬 적중 시 {keyword:Freezing} 상태로 만든다.",
            "<gray>  - 공기 → 물: 물 원소를 소모한 스킬의 효과 범위가 대폭 증가한다.",
            "<gray>  - 흙 → 공기: 공기 원소를 소모한 스킬의 효과 주변에 있는 적의 시야가 감소한다.",
            "<gray>  - 공기 → 흙: 흙 원소를 소모한 스킬 적중 시 대상을 넉백시키는 대신 지면으로 떨어트리고 3초간 {keyword:Fix}시킨다.",
        )
    }

    private class PassiveThree : BasePassive() {
        override val name = "<bold>공명"

        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>3초 이내에 같은 원소를 2개 소모하면 공명이 발생한다.",
            "<gray>",
            "<gray> - 공명:",
            "<gray>  - 흙 → 흙: 다음 흙 원소를 소모한 스킬의 범위가 증가하고, 방어/제어 효과가 강화된다.",
            "<gray>  - 불 → 불: 다음 불 원소를 소모한 스킬의 피해가 25% 증가하고, {keyword:Burn} 지속시간이 2초 연장된다.",
            "<gray>  - 물 → 물: 다음 물 원소를 소모한 스킬의 회복량, 둔화 효과가 50% 증가한다.",
            "<gray>  - 공기 → 공기: 다음 공기 원소를 소모한 스킬의 사거리 및 이동거리가 40% 증가한다.",
        )
    }

    private class PassiveFour : BasePassive() {
        override val name = "<bold>해방"

        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>3초 이내에 서로 다른 원소를 3개 소모하면 해방이 발생한다.",
            "<gray>",
            "<gray> - 해방:",
            "<gray>  - 불 ↔ 물 ↔ 공기: 마지막 원소를 소모한 스킬이 적중한 위치에 4초간 폭풍을 생성한다.",
            "<gray>    - 폭풍은 주변 적을 끌어당기고, 초당 0.25의 피해를 입히며 간헐적으로 번개가 떨어져 1의 피해를 입힌다.",
            "<gray>  - 흙 ↔ 물 ↔ 공기: 마지막 원소를 소모한 스킬 사용 시 4초간 자신 주변에 생명의 영역을 생성한다.",
            "<gray>    - 생명의 영역에서 자신은 초당 체력을 1 회복하고, 적의 이동속도가 20% 감소한다.",
            "<gray>  - 흙 ↔ 불 ↔ 공기: 마지막 원소를 소모한 스킬이 적중한 위치에 대규모 분화를 일으킨다.",
            "<gray>    - 지면이 폭발하여 적을 공중으로 띄우며 2의 피해를 입히고, 암석 파편이 낙하하여 파편당 1의 피해를 입힌다.",
            "<gray>  - 흙 ↔ 불 ↔ 물  : 마지막 원소를 소모한 스킬에 적중한 모든 적을 1.5초간 흑요석 감옥에 가둔다.",
            "<gray>    - 자신은 4초간 <aqua><bold>4의 피해를 막는 {keyword:Shield}을 얻는다.",
        )
    }
}
