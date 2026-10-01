package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive


class Spezialeinheitsmitglied : org.beobma.classWarPlugin.gameClass.firearm.FirearmClass(org.beobma.classWarPlugin.gameClass.firearm.FirearmProfile.SMG) {
    override val classId = "spezialeinheitsmitglied"
    override val name = "<gray>특수대원"
    override val rank = Rank.A
    override val classItemMaterial = Material.IRON_HELMET
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf()

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    // 반동은 약하나, 다른 FPS 게임처럼 자연스러운 반동을 만들어야함
    private class Weapon : BaseWeapon() {
        override val name = "<gray>기관단총"
        override val description = listOf(
            "<gray>우클릭을 누르거나 누르고 있으면 바라보는 방향으로 사격한다.",
            "<gray>매 사격마다 {keyword:Bullet}을 1 소모한다.",
            "",
            "<gray>사격 시 탄환을 발사하여 적중한 적에게 {g:damage:0.2}의 피해를 입힌다."
        )
        override val material = Material.IRON_HORSE_ARMOR
    }

    private class Passive : BasePassive() {
        override val name = "<bold>전술 재장전"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>아래 조건을 만족할 때마다 자동으로 {g:reload:2}초간 재장전하여 {keyword:Bullet}을 20 얻는다. (최대 20)",
            "<gray>  - {keyword:Bullet}을 모두 소모했을 때.",
            "<gray>  - 전투에서 벗어난지 6초가 경과했을 때"
        )
    }
}
