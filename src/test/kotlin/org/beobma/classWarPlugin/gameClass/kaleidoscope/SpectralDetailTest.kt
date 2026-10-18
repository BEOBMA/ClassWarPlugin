package org.beobma.classWarPlugin.gameClass.kaleidoscope

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.util.Vector
import kotlin.test.*

class SpectralDetailTest {
    @Test fun `light travel parents are connected mirrors and preserve arrival delays`() {
        val points=listOf(Vector(),Vector(6,0,0),Vector(6,0,6),Vector(0,0,6),Vector(12,0,6),Vector(90,0,0))
        val plan=SpectralGeometry.propagation(points,Vector())
        assertEquals(mapOf(0 to 0,1 to 3,2 to 6,3 to 3,4 to 9),plan.mapValues { it.value.tick })
        assertNull(plan.getValue(0).parent)
        plan.forEach { (index,arrival) -> arrival.parent?.let { parent ->
            assertTrue(points[index].distance(points[parent])<=8)
            assertTrue(plan.getValue(parent).tick<arrival.tick)
        } }
    }
    @Test fun `preview basis remains orthonormal on all block faces`() {
        for(normal in listOf(Vector(0,1,0),Vector(0,-1,0),Vector(1,0,0),Vector(-1,0,0),Vector(0,0,1),Vector(0,0,-1))) {
            val original=normal.clone()
            val (right,up)=SpectralGeometry.surfaceFrame(normal)
            assertEquals(1.0,right.length(),1e-9); assertEquals(1.0,up.length(),1e-9)
            assertEquals(0.0,right.dot(up),1e-9)
            assertEquals(0.0,right.dot(normal),1e-9); assertEquals(0.0,up.dot(normal),1e-9)
            assertEquals(original,normal)
        }
        assertTrue(SpectralGeometry.surfaceFrame(Vector()).first.length().isFinite())
    }
    @Test fun `rose window and framed walls retain a safe bounded dissolution load`() {
        val cells=SpectralGeometry.interior(20)
        assertTrue(cells.count { it.y>=0 } in 300..600)
        assertTrue(cells.any { it.y==14 && it.material==Material.SEA_LANTERN })
        assertTrue(cells.any { it.y in 4..13 && it.material==Material.POLISHED_DIORITE })
        assertTrue(cells.any { it.y == -1 && it.material==Material.POLISHED_BLACKSTONE })
        assertTrue(cells.none { it.y in 0..3 })
    }
    @Test fun `status HUD uses seven slots and rounds suppression up without extra timers`() {
        val status=KaleidoscopeStatus()
        fun text()=PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(status.actionBarText()))
        status.synchronize(3,2,false,0)
        assertContains(text(),"◆◆◆◇◇◇◇"); assertContains(text(),"거울 2")
        assertFalse(text().contains("<"))
        status.synchronize(7,21,true,0)
        assertContains(text(),"전역 연결")
        status.synchronize(0,0,false,21)
        assertContains(text(),"봉인 2초")
        status.synchronize(0,0,false,0)
        assertFalse(text().contains("봉인"))
        assertTrue(status.isClassMechanic)
    }
    @Test fun `domain score is bounded playable and ends with the domain`() {
        assertTrue(SpectralScore.notes(-10).isEmpty())
        assertTrue(SpectralScore.notes(320).isEmpty())
        val all=(0 until 320).flatMap { tick ->
            val notes=SpectralScore.notes(tick)
            assertTrue(notes.size<=2)
            if(tick%10!=0) assertTrue(notes.isEmpty())
            notes
        }
        assertEquals(40,all.size)
        assertTrue(all.all { it.pitch in 0.5f..2f })
        assertTrue(all.map { it.pitch }.distinct().size>7)
    }
}
