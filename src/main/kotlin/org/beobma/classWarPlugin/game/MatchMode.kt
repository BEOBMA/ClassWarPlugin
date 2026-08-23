package org.beobma.classWarPlugin.game

enum class MatchMode(
    val displayName: String,
    val description: String,
    val assignedClassCount: Int = 1,
) {
    CLASSIC(
        displayName = "<red><bold>클래식</bold></red>",
        description = "<gray>모든 상대와 싸워 마지막 생존자가 됩니다.",
    ),
    TAIL_TAG(
        displayName = "<gold><bold>꼬리잡기</bold></gold>",
        description = "<gray>지정된 표적만 공격할 수 있으며, 표적 처치 시 다음 표적을 이어받습니다.",
    ),
    DUAL(
        displayName = "<light_purple><bold>듀얼</bold></light_purple>",
        description = "<gray>서로 다른 클래스 두 개를 동시에 배정받아 함께 사용합니다.",
        assignedClassCount = 2,
    ),
}
