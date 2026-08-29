package org.beobma.classWarPlugin.status

/** 동일 상태에 새 지속시간을 적용할 때 기존 값을 처리하는 방식이다. */
enum class StatusDurationMode {
    /** 새 지속시간으로 교체한다. */
    Refresh,

    /** 기존 지속시간에 새 지속시간을 더한다. */
    Extend,

    /** 새 지속시간 적용을 무시한다. */
    Ignore
}
