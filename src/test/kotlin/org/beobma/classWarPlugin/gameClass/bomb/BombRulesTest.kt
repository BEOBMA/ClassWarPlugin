package org.beobma.classWarPlugin.gameClass.bomb

import kotlin.test.*

class BombRulesTest {
    @Test fun `first blast uses selected bomb count subsequent hits are two`() {
        for(count in 1..BombRules.LIMIT) {
            assertEquals(count.toDouble(),BombRules.damage(count,false))
            assertEquals(2.0,BombRules.damage(count,true))
        }
    }
    @Test fun `five bombs consume fifty charge and detonate after quarter second`() {
        assertEquals(5,BombRules.LIMIT)
        assertEquals(50,BombRules.LIMIT*BombRules.COST)
        assertEquals(5,BombRules.FUSE_TICKS)
    }
}
