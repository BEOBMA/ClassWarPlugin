package org.beobma.classWarPlugin.effect

import org.beobma.classWarPlugin.util.DisplayOrientationUtil
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Vector3f
import java.lang.reflect.Proxy
import kotlin.test.*

class WeaponDisplayTest {
    @Test fun `blade points along flight or into ground without inherited yaw pitch`() {
        var transform: Transformation? = null
        var billboard: Display.Billboard? = null
        var itemTransform: ItemDisplay.ItemDisplayTransform? = null
        var yaw = 35f
        var pitch = 65f
        val display = Proxy.newProxyInstance(ItemDisplay::class.java.classLoader, arrayOf(ItemDisplay::class.java)) { _, method, args ->
            when (method.name) {
                "setTransformation" -> transform = args!![0] as Transformation
                "setBillboard" -> billboard = args!![0] as Display.Billboard
                "setItemDisplayTransform" -> itemTransform = args!![0] as ItemDisplay.ItemDisplayTransform
                "setRotation" -> { yaw = args!![0] as Float; pitch = args[1] as Float }
            }
            null
        } as ItemDisplay
        for (direction in listOf(Vector(1, 0, 0), Vector(-1, 0, 0), Vector(0, 0, 1),
            Vector(0, 0, -1), Vector(0, 1, 0), Vector(0, -1, 0), Vector(0.12, -1.0, 0.08), Vector(0.6, 0.3, -0.8))) {
            DisplayOrientationUtil.alignSwordBladeVertically(display, direction, 0.65f)
            val rotation = assertNotNull(transform).leftRotation
            val tip = Vector3f(-1f, 1f, 0f).normalize().rotate(rotation)
            val expected = direction.clone().normalize()
            assertEquals(expected.x, tip.x.toDouble(), 1e-5)
            assertEquals(expected.y, tip.y.toDouble(), 1e-5)
            assertEquals(expected.z, tip.z.toDouble(), 1e-5)
            assertEquals(Display.Billboard.FIXED, billboard)
            assertEquals(ItemDisplay.ItemDisplayTransform.NONE, itemTransform)
            assertEquals(0f, yaw)
            assertEquals(0f, pitch)
            assertEquals(0.65f, assertNotNull(transform).scale.x)
        }
    }
}
