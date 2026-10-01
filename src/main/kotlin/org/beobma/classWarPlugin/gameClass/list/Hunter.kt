package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon

// 밸런스 조정 상수
private const val HUNTER_RELOAD_COOLDOWN_SECONDS = 6

class Hunter : org.beobma.classWarPlugin.gameClass.firearm.FirearmClass(org.beobma.classWarPlugin.gameClass.firearm.FirearmProfile.SHOTGUN) {
    override val classId = "hunter"
    override val name = "<gray>사냥꾼"
    override val rank = Rank.A
    override val classItemMaterial = Material.IRON_HORSE_ARMOR
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf()

    // 사격하면 자연스럽게 반동이 생기며, 반동은 실제 산탄총처럼 상당히 강함
    // 8발의 탄환을 맞추려면 진짜 코앞에 있어야 함.
    // 탄환은 실제 산탄총처럼 퍼져 나가며 무작위성이 어느정도 존재함
    private class Weapon : BaseWeapon() {
        override val name = "<gray>산탄총"
        override val description = listOf(
            "<gray>우클릭 시 {keyword:Bullet}을 2 소모하여 바라보는 방향으로 사격한다.",
            "",
            "<gray>사격 시 20발의 탄환을 방사형으로 발사한다.",
            "<gray>적중 시 적중한 탄환 1발당 {g:damage:1}의 피해를 입는다. (단일 대상은 최대 8발에만 적중될 수 있다.)"
        )
        override val material = Material.IRON_HORSE_ARMOR
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "hunter/red-skill"
        override val name = "<bold>재장전"
        override val description = listOf(
            "<gray>모든 {keyword:Bullet}을 버린다.",
            "<gray>{g:reload:4}초 동안 재장전하여 {keyword:Bullet}을 4 얻는다. (최대 4)",
            "<gray>재장전하는 동안 <gold><bold>이동 속도가 {g:speed:60}% 감소</bold><gold>한다."
        )
        override val cooldown = HUNTER_RELOAD_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return beginReload()
        }
        override fun isUseSuccess() = canReload()
    }
}
