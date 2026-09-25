package org.beobma.classWarPlugin.keyword

import java.util.Locale

/** Client-side translation is intentional: the same shared item works with and without the pack. */
object StatusIcon {
    fun translationKey(keyword: String): String {
        require(keyword.matches(Regex("[A-Za-z][A-Za-z0-9]*"))) { "Invalid icon keyword: $keyword" }
        return "classwar.icon.${keyword.lowercase(Locale.ROOT)}"
    }

    fun markup(keyword: String): String =
        "<font:classwar:status_icons><white><!bold><!italic><lang_or:${translationKey(keyword)}:''></lang_or></!italic></!bold></white></font>"
}
