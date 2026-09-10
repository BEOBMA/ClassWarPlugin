package org.beobma.classWarPlugin.effect

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.list.FreikugelBulletStatus
import org.beobma.classWarPlugin.status.list.RevolverBulletStatus
import kotlin.test.*

class FreikugelAmmoStatusTest {
    @Test fun `normal and cursed ammunition use separate keyword states including empty state`() {
        val normal = RevolverBulletStatus()
        val cursed = FreikugelBulletStatus()
        assertEquals(Keyword.Bullet.string, normal.name)
        assertEquals(Keyword.FreikugelBullet.string, cursed.name)
        normal.synchronize(6, 0)
        cursed.synchronize(1, 0)
        assertTrue(normal.synchronize(0, 0))
        assertFalse(cursed.synchronize(1, 0))
        assertTrue(normal.showInActionBar && cursed.isClassMechanic)
        assertFalse(cursed.canRemove)
        assertEquals(1, cursed.power)
        assertTrue(cursed.synchronize(0, 40))
        assertTrue(cursed.actionBarText().contains("2.0초"))
        cursed.synchronize(0, 20)
        assertTrue(cursed.actionBarText().contains("▰▰▰▰▰"))
        assertTrue(cursed.actionBarText().contains("1.0초"))
        cursed.synchronize(1, 0)
        assertFalse(cursed.actionBarText().contains("재장전"))
        assertEquals(1, cursed.power)
    }
}
