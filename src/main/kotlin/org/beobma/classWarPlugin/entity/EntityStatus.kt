package org.beobma.classWarPlugin.entity

import org.beobma.classWarPlugin.ability.ControlLocks
import org.beobma.classWarPlugin.ability.Control

/** 전투·이동·대상 지정 가능 여부를 한곳에서 제어하는 엔티티 상태다. */
abstract class EntityStatus {
    val controlLocks = ControlLocks()
    open var isDead: Boolean = false
    var baseCanAttack = true
    var baseCanSkillUse = true
    var baseCanMove = true
    var baseAttackable = true
    var baseTargetable = true
    open var canAttack: Boolean
        get() = baseCanAttack && !controlLocks.blocks(Control.ATTACK)
        set(value) { baseCanAttack = value }
    open var canSkillUse: Boolean
        get() = baseCanSkillUse && !controlLocks.blocks(Control.SKILL)
        set(value) { baseCanSkillUse = value }
    open var canMove: Boolean
        get() = baseCanMove && !controlLocks.blocks(Control.MOVE)
        set(value) { baseCanMove = value }
    open var isAttackable: Boolean
        get() = baseAttackable && !controlLocks.blocks(Control.ATTACKABLE)
        set(value) { baseAttackable = value }
    open var isSkillTargeting: Boolean
        get() = baseTargetable && !controlLocks.blocks(Control.TARGETABLE)
        set(value) { baseTargetable = value }
}
