package org.beobma.classWarPlugin.damage

/** 피해가 발생한 경로다. 경로에 따라 클래스·상태이상 처리기가 선택된다. */
enum class DamagePath(val displayName: String) {
    BASIC_ATTACK("<yellow><bold>기본 공격</bold></yellow>"),
    RANGED_ATTACK("<gold><bold>원거리 공격</bold></gold>"),
    SKILL("<aqua><bold>스킬</bold></aqua>"),
    STATUS_EFFECT("<green><bold>상태이상</bold></green>");

    /** 근접 및 원거리 기본 공격 경로인지 여부다. */
    val isBasicAttack: Boolean
        get() = this == BASIC_ATTACK || this == RANGED_ATTACK
}
