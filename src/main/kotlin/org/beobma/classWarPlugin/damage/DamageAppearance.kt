package org.beobma.classWarPlugin.damage

import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.keyword.StatusIcon
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Presentation only: never participates in damage, armor, shield or execution calculations. */
enum class DamageAppearance(
    val label: String,
    val color: String,
    val scale: Float = 1f,
    val lifetime: Int = 30,
    /** Keyword icon shown only when the viewing client's resource pack defines it. */
    val iconKeyword: String? = null,
) {
    NORMAL("", "red"),
    FIXED("고정 피해", "#f2d48a", 1.2f, 34, "TrueDamage"),
    STATUS("상태 피해", "#cb9cff", 1.05f, iconKeyword = "AbnormalStatusDamage"),
    VIBRATION("진동 폭발", "#ffc44f", 1.5f, 38, "VibrationExplosion"),
    SETTLEMENT("결산", "#ff7043", 1.65f, 40, "Settlement"),
    RESONANCE("공명", "#c77dff", 1.5f, 38, "Resonance"),
    EXECUTION("처형", "#ff4265", 1.8f, 44, "Execution"),
    BLEEDING("출혈", "#d84b65", 1.05f, iconKeyword = "Bleeding"),
    SHATTER("빙결 파쇄", "#9deaff", 1.35f, 36, "Freezing"),
    BURN("화상", "#ff9c45", 1.05f, iconKeyword = "Burn"),
    POISON("◇ 독", "#a6dc65", 1.05f),
    WITHER("◇ 시듦", "#ae83bd", 1.1f),
    MAGIC("✧ 마법", "#c5a5ff", 1.15f),
    REVERSAL("반전", "#ee87dc", 1.3f, 36),
    EXPLOSION("✦ 폭발", "#ffb15c", 1.3f, 34),
    LIGHTNING("낙뢰", "#f7eb90", 1.3f, 34, "Electrocution");

    fun markup(damage: Double, outline: Boolean = false, labelOnly: Boolean = false): String {
        val number = DecimalFormat("0.##", DecimalFormatSymbols(Locale.US)).format(damage)
        val digits = if (this == FIXED) "<font:minecraft:uniform>-$number</font>" else "-$number"
        val icon = iconKeyword?.let(StatusIcon::markup).orEmpty()
        val text = if (this == EXECUTION || labelOnly) label else (if (label.isEmpty()) "" else "$label\n") + digits
        val content = icon + text
        val tint = if (outline) "black" else if (this == NORMAL) color else "gradient:#fff6db:$color"
        return "<$tint><bold>" + (if (this == NORMAL) content else "<italic>$content</italic>") + "</bold></${tint.substringBefore(':')}>"
    }
    val tilt: Float get() = if (this == NORMAL) 0f else -0.10f
    fun animatedScale(age: Int): Float = scale * if (this == NORMAL) 1f else (1f + 0.22f * (1 - age.coerceAtLeast(0) / 6f).coerceAtLeast(0f))
    fun shake(age: Int): Double = if (this == VIBRATION && age in 1..10) (if (age % 2 == 0) 1 else -1) * 0.045 * (1-age/12.0) else 0.0

    companion object {
        fun resolve(type: DamageType, path: DamagePath? = null): DamageAppearance = when {
            type == DamageType.True -> FIXED
            type == DamageType.StatusAbnormality || path == DamagePath.STATUS_EFFECT -> STATUS
            else -> NORMAL
        }
    }
}
