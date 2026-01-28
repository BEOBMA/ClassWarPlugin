package org.beobma.classWarPlugin.entity

abstract class EntityStatus {
    open var isDead: Boolean = false
    open var canAttack: Boolean = true
    open var canSkillUse: Boolean = true
    open var canMove: Boolean = true
    open var isAttackable: Boolean = true
    open var isSkillTargeting: Boolean = true
}
