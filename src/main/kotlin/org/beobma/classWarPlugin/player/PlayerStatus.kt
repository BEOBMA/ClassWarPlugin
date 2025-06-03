package org.beobma.classWarPlugin.player

import org.bukkit.entity.Player

data class PlayerStatus(
    val player: Player,
    private var _isDead: Boolean = false,
    private var _canAttack: Boolean = true,
    private var _canSkillUse: Boolean = true,
    private var _canMove: Boolean = true,
    private var _isAttackable: Boolean = true,
    private var _isSkillTargeting: Boolean = true,

) {
    var isDead: Boolean
        get() = _isDead
        set(value) {
            // 값이 변함
            _isDead = value
        }
    var canAttack: Boolean
        get() = _canAttack
        set(value) {
            // 값이 변함
            _canAttack = value
        }
    var canSkillUse: Boolean
        get() = _canSkillUse
        set(value) {
            // 값이 변함
            _canSkillUse = value
        }
    var canMove: Boolean
        get() = _canMove
        set(value) {
            _canMove = value
        }
    var isAttackable: Boolean
        get() = _isAttackable
        set(value) {
            // 값이 변함
            _isAttackable = value
        }
    var isSkillTargeting: Boolean
        get() = _isSkillTargeting
        set(value) {
            // 값이 변함
            _isSkillTargeting = value
        }
}
