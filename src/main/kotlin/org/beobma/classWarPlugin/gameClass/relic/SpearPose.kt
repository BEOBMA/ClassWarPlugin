package org.beobma.classWarPlugin.gameClass.relic

import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

internal object SpearPose {
    fun create(heading: Vector3f): Transformation {
        val rotation = Quaternionf().rotationTo(Vector3f(0f, 1f, 0f), Vector3f(heading).normalize())
        // NONE context: trident tip (0,-4/16,0), special renderer flips Y/Z,
        // item centering translates (-.5,-.5,-.5), then ItemDisplay rotates Y by pi.
        // Cancel the resulting tip pivot AFTER scale/rotation, not in world axes.
        val translation = Vector3f(0.5f, -0.25f, 0.5f).mul(1.7f).rotate(rotation).negate()
        return Transformation(translation, rotation, Vector3f(1.7f), Quaternionf())
    }
}
