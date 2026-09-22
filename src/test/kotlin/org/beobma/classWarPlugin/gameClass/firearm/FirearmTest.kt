package org.beobma.classWarPlugin.gameClass.firearm

import org.beobma.classWarPlugin.ability.AbilityCatalog
import org.beobma.classWarPlugin.gameClass.list.FirearmsMaster
import org.beobma.classWarPlugin.growth.GrowthClassCatalog
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import kotlin.random.Random
import kotlin.test.*

class FirearmTest {
    @Test fun `shotgun spends two rounds per blast and never overdrafts`() {
        val magazine = FirearmMagazine(FirearmProfile.SHOTGUN)
        assertEquals(4, magazine.bullets)
        assertTrue(magazine.shoot(0))
        assertEquals(2, magazine.bullets)
        assertFalse(magazine.shoot(0))
        assertFalse(magazine.shoot(13))
        assertTrue(magazine.shoot(14))
        assertEquals(0, magazine.bullets)
        assertFalse(magazine.shoot(28))
    }

    @Test fun `all automatic weapons obey tick cadence and their declared capacity`() {
        for (profile in FirearmProfile.entries.filter { it.automatic }) {
            val magazine = FirearmMagazine(profile)
            for (shot in 0 until profile.capacity) {
                val tick = shot.toLong() * profile.interval
                assertTrue(magazine.shoot(tick))
                assertFalse(magazine.shoot(tick))
                if (profile.interval > 1) assertFalse(magazine.shoot(tick + 1))
            }
            assertEquals(0, magazine.bullets)
            assertFalse(magazine.shoot(10000))
        }
    }

    @Test fun `reload discards remaining ammunition and restores capacity at the exact last tick`() {
        for (profile in FirearmProfile.entries) {
            val magazine = FirearmMagazine(profile)
            assertTrue(magazine.reload())
            assertEquals(0, magazine.bullets)
            assertFalse(magazine.reload())
            repeat(profile.reloadTicks - 1) {
                assertFalse(magazine.tick())
                assertFalse(magazine.shoot(10000))
            }
            assertTrue(magazine.tick())
            assertFalse(magazine.reloading)
            assertEquals(profile.capacity, magazine.bullets)
            assertFalse(magazine.tick())
        }
    }

    @Test fun `growth reload timing can shorten but cannot produce an invalid timer`() {
        val magazine = FirearmMagazine(FirearmProfile.ASSAULT)
        magazine.reload(30)
        repeat(29) { assertFalse(magazine.tick()) }
        assertTrue(magazine.tick())
        magazine.reload(0)
        assertEquals(1, magazine.totalReload)
        assertTrue(magazine.tick())
    }

    @Test fun `pellet cap and fractional damage remain exact`() {
        assertEquals(20, FirearmProfile.SHOTGUN.pellets)
        assertEquals(0.0, FirearmProfile.SHOTGUN.damageForHits(0))
        assertEquals(3.0, FirearmProfile.SHOTGUN.damageForHits(3))
        assertEquals(8.0, FirearmProfile.SHOTGUN.damageForHits(20))
        assertEquals(0.1, FirearmProfile.MINIGUN.damageForHits(1))
        assertEquals(0.2, FirearmProfile.ASSAULT.damageForHits(1))
        assertEquals(0.2, FirearmProfile.SMG.damageForHits(1))
    }

    @Test fun `spread is normalized bounded random and stable for vertical aim`() {
        val random = Random(451)
        for (forward in listOf(Vector(0, 0, 1), Vector(0, 1, 0), Vector(0, -1, 0))) {
            val values = List(100) { FirearmClass.spreadDirection(forward, 0.24, random) }
            assertTrue(values.all { it.length().isFinite() && kotlin.math.abs(it.length() - 1.0) < 1e-9 })
            assertTrue(values.all { it.dot(forward) >= 1.0 / kotlin.math.sqrt(1.0 + 0.24 * 0.24) - 1e-9 })
            assertTrue(values.distinct().size > 90)
        }
        assertEquals(Vector(0, 0, 1), FirearmClass.spreadDirection(Vector(0, 0, 1), 0.0, random))
    }

    @Test fun `ballistics hit body bounds rather than feet and cannot cross the wall distance`() {
        val body = BoundingBox(-0.3, 0.0, 4.7, 0.3, 1.8, 5.3)
        assertEquals(4.7, HitboxUtil.rayIntersectionDistance(body, Vector(0.0, 1.6, 0.0), Vector(0, 0, 1), 20.0))
        assertNull(HitboxUtil.rayIntersectionDistance(body, Vector(0.0, 1.6, 0.0), Vector(0, 0, 1), 4.0))
        assertNull(HitboxUtil.rayIntersectionDistance(body, Vector(0.0, 2.0, 0.0), Vector(0, 0, 1), 20.0))
    }

    @Test fun `master selects only implemented guns and never immediately repeats`() {
        val random = Random(51)
        var previous: String? = null
        val observed = mutableSetOf<String>()
        repeat(1000) {
            val next = FirearmRoster.next(previous, random)
            assertNotEquals(previous, next)
            observed += next
            previous = next
        }
        assertEquals(FirearmRoster.ids.toSet(), observed)
        assertFalse("firearmsmaster" in observed)
        observed.forEach { assertIs<BorrowableFirearm>(AbilityCatalog.create(it)) }
    }

    @Test fun `new classes are enabled independent and have growth coverage`() {
        for (id in FirearmRoster.ids + "firearmsmaster") {
            val first = AbilityCatalog.create(id)
            val second = AbilityCatalog.create(id)
            assertEquals(id, first.classId)
            assertNotSame(first, second)
            assertTrue(id in AbilityCatalog.enabledClassIds())
            assertTrue(id in GrowthClassCatalog.styles)
            assertTrue(first.weapon.description.none { "커스텀 무기 설명" in it })
        }
    }

    @Test fun `master owns one fresh child with reloading disabled`() {
        repeat(30) {
            val master = FirearmsMaster()
            val gun = master.activeFirearm
            assertEquals(listOf(gun), master.childAbilities)
            assertSame(gun.weapon, master.weapon)
            assertTrue((gun as BorrowableFirearm).reloadDisabled)
            if (gun is FirearmClass) assertEquals(master.classId, gun.weaponOwnerId)
            assertTrue(master.skills.isEmpty()) // Populated after initial inventory layout, without duplicate guns.
        }
    }
}
