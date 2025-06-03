package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Duelist : GameClass() {
    override val name = "<gray>결투가"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.SPECTRAL_ARROW
    override val weapon = DuelistsSword()

    override var skills: List<Skill> = listOf(
        DuelistsRedSkill(),
        DuelistsOrangeSkill(),
        DuelistsYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        DuelistsPassive()
    )
}

class DuelistsSword : Weapon() {
    override val name = "<gray>무기 이름"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.WOODEN_SWORD
}

class DuelistsRedSkill : Skill() {
    override val name = "<bold>Attaque"
    override val description = listOf(
        "<gray>바라보는 방향으로 레이피어를 내지른다.",
        "<gray>적중한 적에게 7의 피해를 입힌다.",
        "<gray>적중 시 이 스킬을 'Composée'로 강화하여 재사용할 수 있다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 구현 예정
        return true
    }
}

class DuelistsOrangeSkill : Skill() {
    override val name = "<bold>Bond-en-avant"
    override val description = listOf(
        "<gray>바라보는 방향으로 짧게 도약한다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 구현 예정
        return true
    }
}

class DuelistsYellowSkill : Skill() {
    override val name = "<bold>결투 선포"
    override val description = listOf(
        "<gray>바라보는 적에게 15초간 결투를 선포한다.",
        "<gray>자신과 적은 서로의 공격으로 <gold><bold>받는 피해가 50% 증가</bold><gray>한다.",
        "<gray>서로 결투 진행중인 대상을 제외한 다른 대상에게 <gold><bold>받는 피해는 50% 감소</bold><gray>한다.",
        "<gray>결투중인 대상에게 기본 스킬을 3번 연속 적중 성공 시 추가로 6의 피해를 입힌다."
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        // TODO: 구현 예정
        return true
    }
}

class DuelistsPassive : Passive() {
    override val name = "<bold>자세 흐트러짐"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>스킬 적중 실패 시 다음 공격으로 <gold><bold>받는 피해는 25% 증가</bold><gray>한다."
    )
}