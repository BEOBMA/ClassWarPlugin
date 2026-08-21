package org.beobma.classWarPlugin.gameClass

enum class Rank(
    val displayName: String,
    color: String,
) {
    SPECIAL("Special", "#FF0000"),
    L("L", "#FFA500"),
    S("S", "#FF00FF"),
    A("A", "#00FF00"),
    B("B", "#00BFFF"),
    C("C", "#FFD700");

    val formattedName: String = "<bold><color:$color>$displayName</color></bold>"
}
