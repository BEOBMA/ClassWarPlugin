package org.beobma.classWarPlugin.gameClass.firearm

/** Resource-pack-free, single-line ammunition silhouettes. Rendering never changes real ammunition. */
object FirearmHud {
    fun render(magazine: FirearmMagazine): String = if (magazine.reloading) {
        val progress = 1.0 - magazine.remainingReload.toDouble() / magazine.totalReload
        reload(magazine.profile, progress)
    } else loaded(magazine.profile, magazine.bullets)

    private fun colored(color: String, text: String) = "<$color>$text</$color>"
    private fun metal(text: String) = colored("gray", text)
    private fun rounds(count: Int, capacity: Int, glyph: String, color: String): String =
        colored(color, glyph.repeat(count.coerceIn(0, capacity))) +
            colored("dark_gray", glyph.repeat(capacity - count.coerceIn(0, capacity)))

    private fun loaded(profile: FirearmProfile, bullets: Int): String = when (profile) {
        FirearmProfile.SHOTGUN -> shells(bullets)
        FirearmProfile.ASSAULT -> magazine(bullets, profile, curved = true)
        FirearmProfile.SMG -> magazine(bullets, profile, curved = false)
        FirearmProfile.MINIGUN -> belt(bullets)
    }

    private fun shells(count: Int): String = metal("[ ") + (0 until 4).joinToString(" ") {
        if (it < count) colored("red", "▰") + colored("gold", "▌")
        else colored("dark_gray", "▱▏")
    } + metal(" ]")

    private fun magazine(count: Int, profile: FirearmProfile, curved: Boolean): String =
        metal(if (curved) "╭[" else "╔[") +
            rounds(count, profile.capacity, if (curved) "╽" else "▪", if (curved) "gold" else "yellow") +
            metal(if (curved) "]╯" else "]╝")

    private fun belt(bullets: Int): String {
        // Show a feed strip plus compact reserve folds rather than 300 screen-wide cartridges.
        // Every cartridge changes the strip; only after a fold empties does the next fold feed in.
        val feed = if (bullets == 0) 0 else (bullets - 1) % 30 + 1
        val reserve = if (bullets == 0) 0 else (bullets - 1) / 30
        return metal("[") + rounds(reserve, 9, "▣", "gold") + metal("]─") +
            rounds(feed, 30, "╿", "yellow") + metal("─[≡]")
    }

    private fun reload(profile: FirearmProfile, progress: Double): String {
        val p = progress.coerceIn(0.0, 0.999999)
        return when (profile) {
            FirearmProfile.SHOTGUN -> when {
                p < 0.16 -> metal("[  ╲  ╱  ]  ") + colored("dark_gray", "▱▏  ↘")
                p < 0.84 -> {
                    val step = (p - 0.16) / 0.68 * 4
                    val inserted = step.toInt().coerceIn(0, 3)
                    val gap = " ".repeat(3 - ((step - inserted) * 3).toInt())
                    shells(inserted) + gap + colored("gold", "← ▰▌")
                }
                p < 0.93 -> shells(4) + metal("  ╲ ← ╱")
                else -> shells(4) + colored("white", "  [↔]")
            }
            FirearmProfile.ASSAULT, FirearmProfile.SMG -> {
                val curved = profile == FirearmProfile.ASSAULT
                when {
                    p < 0.22 -> metal("[   ]") + " ".repeat(1 + (p / 0.22 * 4).toInt()) +
                        colored("dark_gray", "↓ ▱")
                    p < 0.48 -> metal("[   ]   ") + colored("gold", if (curved) "╭▰▰╯ ↑" else "╔▪▪╝ ↑")
                    p < 0.78 -> magazine(profile.capacity, profile, curved) +
                        " ".repeat(1 + ((0.78 - p) / 0.30 * 4).toInt()) + colored("gold", "↑")
                    p < 0.90 -> magazine(profile.capacity, profile, curved) + metal("  [←─]")
                    else -> magazine(profile.capacity, profile, curved) + colored("white", "  [─→]")
                }
            }
            FirearmProfile.MINIGUN -> when {
                p < 0.16 -> metal("[≡]  ╱   ") + colored("dark_gray", "╿╿╿ ↘")
                p < 0.35 -> metal("[       ]  ") + colored("gold", "▣▣▣ ↑")
                p < 0.82 -> {
                    val fed = ((p - 0.35) / 0.47 * 30).toInt().coerceIn(0, 29)
                    metal("[") + rounds(9, 9, "▣", "gold") + metal("]─") +
                        rounds(fed, 30, "╿", "yellow") + colored("gold", " → ") + metal("╱[≡]")
                }
                p < 0.92 -> belt(profile.capacity) + metal("  ╲↓")
                else -> belt(profile.capacity) + colored("white", "  [↔]")
            }
        }
    }
}
