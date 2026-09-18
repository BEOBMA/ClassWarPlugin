package org.beobma.classWarPlugin.game

/** 서로 독립적으로 켜고 끌 수 있는 경기 규칙이다. */
enum class MatchModifier(val displayName: String, val description: String) {
    DUAL("듀얼", "플레이어마다 서로 다른 클래스 두 개를 사용합니다."),
    TAIL_TAG("꼬리잡기", "지정된 상대 팀만 공격할 수 있습니다."),
    TEAM("팀", "설정된 인원으로 팀을 만들고 아군 공격을 차단합니다."),
    COOPERATIVE("공동", "한 조가 이동·공격과 핫바·스킬 조작을 나눠 맡습니다."),
}

/** 공동 모드에서 한 참가자에게 허용되는 조작 묶음이다. */
enum class CooperativeRole(
    val configName: String,
    val displayName: String,
    val canMove: Boolean,
    val canBasicAttack: Boolean,
    val canChangeHotbar: Boolean,
    val canUseSkills: Boolean,
) {
    MOVEMENT_COMBAT("movement-combat", "이동·기본 공격", true, true, false, false),
    HOTBAR_SKILLS("hotbar-skills", "핫바·스킬", false, false, true, true);

    companion object {
        fun fromConfig(value: String): CooperativeRole? = entries.firstOrNull {
            it.configName.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
        }
    }
}

/** 선택된 토글들의 불변 조합이다. */
enum class PrimaryMode { CLASSIC, GROWTH }

data class MatchMode(val modifiers: Set<MatchModifier> = emptySet(), val primary: PrimaryMode = PrimaryMode.CLASSIC) {
    val isGrowth: Boolean get() = primary == PrimaryMode.GROWTH
    val assignedClassCount: Int get() = if (MatchModifier.DUAL in modifiers) 2 else 1
    val usesTailTagRules: Boolean get() = MatchModifier.TAIL_TAG in modifiers
    val usesTeamRules: Boolean get() = MatchModifier.TEAM in modifiers
    val usesCooperativeRules: Boolean get() = MatchModifier.COOPERATIVE in modifiers
    val hasAllies: Boolean get() = usesTeamRules || usesCooperativeRules
    val allowsParasite: Boolean get() = !usesTailTagRules
    val displayName: String
        get() = (if (isGrowth) "<green><bold>성장</bold></green>" + if (modifiers.isEmpty()) "" else " + " else "") +
            if (modifiers.isEmpty()) { if (isGrowth) "" else "<red><bold>클래식</bold></red>" } else modifiers
            .sortedBy { it.ordinal }
            .joinToString(" <dark_gray>+</dark_gray> ") { "<gold><bold>${it.displayName}</bold></gold>" }
    val description: String
        get() = (if (isGrowth) "<green>지역 탐험·사냥·스탯·장비 성장 <red>${org.beobma.classWarPlugin.growth.GrowthSettings.WARNING} " else "") +
            if (modifiers.isEmpty()) "<gray>모든 상대와 싸워 마지막 생존자가 됩니다." else modifiers
            .sortedBy { it.ordinal }
            .joinToString(" <dark_gray>/</dark_gray> ") { "<gray>${it.description}" }

    fun toggled(modifier: MatchModifier): MatchMode =
        copy(modifiers = if (modifier in modifiers) modifiers - modifier else modifiers + modifier)

    fun serialize(): String = (if (isGrowth) "GROWTH;" else "") + modifiers.sortedBy { it.ordinal }.joinToString(",") { it.name }

    /** 인원수와 설정까지 포함해 실제 시작 가능한 조합인지 검사한다. */
    fun validate(settings: GameConfiguration, playerCount: Int): String? {
        validateRules(settings)?.let { return it }
        if (playerCount < 2) return "참가자가 2명 이상이어야 게임을 시작할 수 있습니다."
        if (usesTeamRules) {
            if (settings.teamPlayersPerTeam < 2) return "팀 인원은 2명 이상이어야 합니다."
            if (playerCount % settings.teamPlayersPerTeam != 0) {
                return "참가자 ${playerCount}명이 팀당 ${settings.teamPlayersPerTeam}명으로 딱 맞게 나뉘지 않습니다."
            }
            if (playerCount / settings.teamPlayersPerTeam < 2) return "팀이 두 개 이상 만들어져야 합니다."
        }
        if (usesCooperativeRules) {
            if (settings.cooperativePlayersPerGroup < 2) return "공동 조 인원은 2명 이상이어야 합니다."
            if (playerCount % settings.cooperativePlayersPerGroup != 0) {
                return "참가자 ${playerCount}명이 공동 조당 ${settings.cooperativePlayersPerGroup}명으로 딱 맞게 나뉘지 않습니다."
            }
            if (!usesTeamRules && playerCount / settings.cooperativePlayersPerGroup < 2) {
                return "공동 조가 두 개 이상 만들어져야 합니다."
            }
        }
        if (usesTeamRules && usesCooperativeRules &&
            settings.teamPlayersPerTeam % settings.cooperativePlayersPerGroup != 0
        ) {
            return "팀+공동 모드는 팀 인원(${settings.teamPlayersPerTeam})이 공동 조 인원(${settings.cooperativePlayersPerGroup})으로 나누어져야 합니다."
        }
        return null
    }

    fun validateRules(settings: GameConfiguration): String? {
        if (!isGrowth) return null
        if (!settings.borderEnabled) return "성장 모드에는 자기장 설정이 필요합니다."
        if (settings.fixedSpawnEnabled) return "성장 모드는 무작위 지역에서 시작하므로 고정 스폰을 꺼 주세요."
        if (settings.borderInitialSize !in 64.0..768.0) return "성장 모드 초기 자기장은 64~768블록이어야 합니다."
        if (settings.borderMinimumSize > settings.borderInitialSize / 2) return "최소 자기장을 초기 크기의 절반 이하로 설정하세요."
        return null
    }

    companion object {
        val CLASSIC = MatchMode()
        val GROWTH = MatchMode(primary = PrimaryMode.GROWTH)
        val TAIL_TAG = MatchMode(setOf(MatchModifier.TAIL_TAG))
        val DUAL = MatchMode(setOf(MatchModifier.DUAL))
        val TAIL_TAG_DUAL = MatchMode(setOf(MatchModifier.TAIL_TAG, MatchModifier.DUAL))

        fun deserialize(value: String): MatchMode? {
            if (value.startsWith("GROWTH;")) return deserialize(value.removePrefix("GROWTH;"))?.copy(primary = PrimaryMode.GROWTH)
            if (value.isBlank()) return CLASSIC
            val parsed = value.split(',').map { token ->
                MatchModifier.entries.firstOrNull { it.name == token } ?: return null
            }
            return MatchMode(parsed.toSet())
        }
    }
}
