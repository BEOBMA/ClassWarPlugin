package org.beobma.classWarPlugin.gameClass.relic

import org.beobma.classWarPlugin.gameClass.creator.CreationGeometry
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector

internal object SpearThrowDamage {
    fun calculate(origin: Vector, target: BoundingBox): Double =
        if (CreationGeometry.inRadius(target, origin, 4.0)) 3.0 else 6.0
}
