package org.beobma.classWarPlugin.gameClass

/** 클래스 추첨 가중치와 표시 색상을 구분하는 등급이다. */
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
