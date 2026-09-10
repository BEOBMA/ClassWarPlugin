@file:Suppress("DEPRECATION")

package org.beobma.classWarPlugin.damage

import com.google.common.base.Function
import org.bukkit.damage.DamageSource
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier
import java.lang.reflect.Proxy
import kotlin.test.*

class VanillaArmorIgnoreTest {
    private inline fun <reified T> stub(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        error("Unexpected call: ${method.name}")
    } as T

    private fun event(absorption: Double = 1.0): EntityDamageEvent {
        val modifiers = linkedMapOf(DamageModifier.BASE to 10.0, DamageModifier.ARMOR to -4.0,
            DamageModifier.RESISTANCE to -1.2, DamageModifier.MAGIC to -.48, DamageModifier.ABSORPTION to -absorption)
        val functions = modifiers.mapValues { Function<Double, Double> { 0.0 } }
        return EntityDamageEvent(stub<LivingEntity>(), EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            stub<DamageSource>(), modifiers, functions)
    }

    @Test fun `reduced armor preserves resistance enchantments and absorption`() {
        val event = event()
        VanillaArmorIgnore.applyModifiers(event, 10.0, .8, 1.0)
        assertEquals(-3.2, event.getDamage(DamageModifier.ARMOR), .0001)
        assertEquals(-1.36, event.getDamage(DamageModifier.RESISTANCE), .0001)
        assertEquals(-.544, event.getDamage(DamageModifier.MAGIC), .0001)
        assertEquals(3.896, event.finalDamage, .0001)
    }
    @Test fun `absorption is recalculated rather than accidentally bypassed`() {
        val event = event(4.32)
        VanillaArmorIgnore.applyModifiers(event, 10.0, .8, 10.0)
        assertEquals(0.0, event.finalDamage, .0001)
        assertEquals(-4.896, event.getDamage(DamageModifier.ABSORPTION), .0001)
    }
    @Test fun `ordinary armor remains unchanged`() {
        val event = event()
        val original = event.finalDamage
        VanillaArmorIgnore.applyModifiers(event, 10.0, 1.0, 1.0)
        assertEquals(original, event.finalDamage, .0001)
    }
}
