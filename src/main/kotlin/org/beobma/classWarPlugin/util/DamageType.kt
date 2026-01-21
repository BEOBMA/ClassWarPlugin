package org.beobma.classWarPlugin.util

enum class DamageType(val isFixed: Boolean) {
    Normal(false),
    True(true),
    StatusAbnormality(true)
}
