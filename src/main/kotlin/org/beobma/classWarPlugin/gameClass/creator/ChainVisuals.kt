package org.beobma.classWarPlugin.gameClass.creator

import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.sin

internal object ChainVisuals {
    fun spawnOffset(height: Double, tilt: Double, angle: Double) =
        Vector(cos(angle) * height * tilt, height, sin(angle) * height * tilt)
    // Capture at cast time; entering/leaving the domain cannot change an in-flight hit.
    fun damage(inDomain: Boolean) = if (inDomain) 0.2 else 2.0
}
