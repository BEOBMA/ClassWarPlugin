package org.beobma.classWarPlugin.gameClass.agent

import org.beobma.classWarPlugin.gameClass.list.Agent
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.list.*
import kotlin.test.*

class AgentPresentationTest {
    @Test fun `only weapon and directive resources remain visible in the action bar`() {
        assertTrue(CaduceusStatus().showInActionBar)
        assertTrue(DirectiveStatus().showInActionBar)
        assertFalse(AgentDamageDealtStatus().showInActionBar)
        assertFalse(AgentDamageTakenStatus().showInActionBar)
    }
    @Test fun `agent has no colored skill items and uses declarative weapon text`() {
        val agent = Agent()
        assertTrue(agent.skills.isEmpty())
        assertEquals(2, agent.passives.size)
        assertTrue(agent.weapon.description.any { "우클릭" in it })
        // A trailing cooldown annotation is allowed after the declarative sentence.
        assertTrue(agent.weapon.description.all { it.replace(Regex("\\s*\\([^)]*\\)$"), "").endsWith("다.") })
        assertFalse(agent.passives.flatMap { it.description }.any { "합니다" in it || "실패 시에도 다음" in it })
    }
    @Test fun `each mechanic has a separate keyword label and synchronized value`() {
        val statuses = listOf(CaduceusStatus() to Keyword.Caduceus, DirectiveStatus() to Keyword.Directive,
            AgentDamageDealtStatus() to Keyword.AgentDamageDealt, AgentDamageTakenStatus() to Keyword.AgentDamageTaken)
        statuses.forEach { (status, keyword) ->
            assertTrue(status.isClassMechanic)
            assertFalse(status.canRemove)
            assertNull(status.duration)
            assertEquals(keyword.string, status.name)
            assertTrue(status.synchronize("<gold>50%</gold>"))
            assertEquals("${keyword.string}: <gold>50%</gold>", status.actionBarText())
            assertFalse(status.synchronize("<gold>50%</gold>"))
        }
    }
    @Test fun `all nine weapon forms have distinct defined keywords`() {
        val keywords = listOf(Keyword.AgentHatchet, Keyword.AgentStiletto, Keyword.AgentBastard,
            Keyword.AgentRapier, Keyword.AgentHammer, Keyword.AgentGreatsword, Keyword.AgentLance,
            Keyword.AgentWhip, Keyword.AgentScythe)
        assertEquals(9, keywords.map { it.string }.distinct().size)
        assertTrue(keywords.all { it.requireDescription().endsWith("다.") })
    }
}
