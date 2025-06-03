package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class GunBlader : GameClass() {
    override val name = "<gray>총검사"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.WOODEN_SWORD
    override val weapon = GunBladersSword()

    override var skills: List<Skill> = listOf(
        GunBladersRedSkill(),
        GunBladersOrangeSkill(),
        GunBladersYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        GunBladersPassive1(),
        GunBladersPassive2()
    )
}

class GunBladersSword : Weapon() {
    override val name = "<gray>총검"
    override val description = listOf(
        "<gray>우클릭 시 장전된 탄환을 소모하여 사격한다.",
        "<gray>적중한 적은 3의 피해를 입는다.",
        "<gray>자신의 기본 공격에 피격된 직후가 아니라면 피해량이 1까지 감소한다."
    )
    override val material = Material.WOODEN_SWORD
}

class GunBladersRedSkill : Skill() {
    override val name = "<bold>연격"
    override val description = listOf(
        "<gray>2칸 내의 바라보는 적에게 5의 피해를 입힌다.",
        "<gray>탄환을 소모하면 추가로 10초간 ${Keyword.Vibration.string}을 5 부여한다."
    )
    override val cooldown = 4

    override fun use(): Boolean {
        // TODO: 스킬 효과 구현 예정
        return true
    }
}

class GunBladersOrangeSkill : Skill() {
    override val name = "<bold>진탕"
    override val description = listOf(
        "<gray>2칸 내의 바라보는 적에게 7의 피해를 입힌다.",
        "<gray>탄환을 소모하면 추가로 ${Keyword.VibrationExplosion.string}을 적용한다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 스킬 효과 구현 예정
        return true
    }
}

class GunBladersYellowSkill : Skill() {
    override val name = "<bold>난격"
    override val description = listOf(
        "<gray>자신 주위 적에게 기본 공격과 사격을 번갈아 진행한다.",
        "<gray>기본 공격 시 ${Keyword.Vibration.string}을 2 부여하고.",
        "<gray>사격 시 ${Keyword.VibrationExplosion.string}을 적용한다.",
        "<gray>사격 시 탄환이 부족하다면 공격이 종료된다."
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        // TODO: 스킬 효과 구현 예정
        return true
    }
}

class GunBladersPassive1 : Passive() {
    override val name = "<bold>쇄진탄"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>기본 공격 3회 적중 시 <gold><bold>쇄진탄</bold><gray>을 얻는다.",
        "<gray>3초간 기본 공격을 사용하지 않으면 최대 6발까지 장전한다.",
        "",
        "<gold><bold>쇄진탄</bold><gray>: 이 탄환을 소모한 공격 적중 시 피해량이 ${Keyword.TrueDamage.string}로 전환된다.",
        dictionary[Keyword.TrueDamage] ?: ""
    )
}

class GunBladersPassive2 : Passive() {
    override val name = "<bold>격진탄"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>적 사망 시 <red><bold>격진탄</bold><gray>을 즉시 3발 장전한다.",
        "<gray>직접 처치 시 대신 6발 장전한다.",
        "",
        "<red><bold>격진탄</bold><gray>: 이 탄환을 소모한 공격 적중 시 피해량이 50% 증가하고 피해량이 ${Keyword.TrueDamage.string}로 전환된다.",
        "<gray>이 효과는 ${Keyword.VibrationExplosion.string}에도 적용된다.",
        dictionary[Keyword.TrueDamage] ?: ""
    )
}