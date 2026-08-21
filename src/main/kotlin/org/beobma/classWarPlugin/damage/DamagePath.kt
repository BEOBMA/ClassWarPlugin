package org.beobma.classWarPlugin.damage

enum class DamagePath(val displayName: String) {
    BASIC_ATTACK("<yellow><bold>기본 공격</bold></yellow>"),
    RANGED_ATTACK("<gold><bold>원거리 공격</bold></gold>"),
    SKILL("<aqua><bold>스킬</bold></aqua>"),
    STATUS_EFFECT("<green><bold>상태이상</bold></green>");

    val isBasicAttack: Boolean
        get() = this == BASIC_ATTACK || this == RANGED_ATTACK
}
