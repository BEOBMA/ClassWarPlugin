package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity

class Ghost : GameClass(), GameStatusHandler {
    override val name = "<gray>유령"
    override val rank = Rank.C
    override val classItemMaterial = Material.WHITE_STAINED_GLASS_PANE
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())

    override fun onBattleStart() {
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 1.0
        player.health = 1.0
        particles.spawn(player, Particle.SOUL, count = 35, spread = 0.65, speed = 0.06)
        sounds.play(player, Sound.ENTITY_VEX_AMBIENT, volume = 0.65f, pitch = 0.55f)
    }
    override fun onGameTimePasses() = Unit

    private inner class Passive : BasePassive(), OnHitHandler {
        override val name = "<bold>유령화"
        override val description = listOf(
            "<gray>패시브", "", "<gray>최대 체력이 1로 고정된다.",
            "<gray>기본 공격 적중 시 적 최대 체력의 50%에 해당하는 {keyword:TrueDamage}를 입힌다."
        )

        override fun onAttackHit(context: DamageContext) {
            val target = context.target.entity as? LivingEntity ?: return
            context.isCancelled = true
            val maximumHealth = target.getAttribute(Attribute.MAX_HEALTH)?.value ?: target.health
            context.target.damage(maximumHealth * 0.5, DamageType.True, playerData, damagePath = DamagePath.SKILL)
            particles.spawn(target, Particle.SOUL, count = 28, spread = 0.5, speed = 0.08)
            sounds.play(target, Sound.ENTITY_ALLAY_DEATH, volume = 0.8f, pitch = 0.62f)
        }
    }
}
