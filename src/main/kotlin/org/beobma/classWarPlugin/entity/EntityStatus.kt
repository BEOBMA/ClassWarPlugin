package org.beobma.classWarPlugin.entity

/** 전투·이동·대상 지정 가능 여부를 한곳에서 제어하는 엔티티 상태다. */
abstract class EntityStatus {
    open var isDead: Boolean = false
    open var canAttack: Boolean = true
    open var canSkillUse: Boolean = true
    open var canMove: Boolean = true
    open var isAttackable: Boolean = true
    open var isSkillTargeting: Boolean = true
}
