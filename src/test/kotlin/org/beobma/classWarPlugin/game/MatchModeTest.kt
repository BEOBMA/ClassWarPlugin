package org.beobma.classWarPlugin.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import java.util.UUID

class MatchModeTest {
    @Test
    fun `tail tag dual assigns two classes and uses tail rules`() {
        assertEquals(2, MatchMode.TAIL_TAG_DUAL.assignedClassCount)
        assertTrue(MatchMode.TAIL_TAG_DUAL.usesTailTagRules)
    }

    @Test
    fun `parasite is excluded from both tail modes`() {
        assertFalse(MatchMode.TAIL_TAG.allowsParasite)
        assertFalse(MatchMode.TAIL_TAG_DUAL.allowsParasite)
        assertTrue(MatchMode.CLASSIC.allowsParasite)
        assertTrue(MatchMode.DUAL.allowsParasite)
    }

    @Test
    fun `toggles compose and serialize without dedicated detail modes`() {
        val mode = MatchMode.CLASSIC
            .toggled(MatchModifier.TEAM)
            .toggled(MatchModifier.DUAL)
            .toggled(MatchModifier.TAIL_TAG)

        assertEquals(2, mode.assignedClassCount)
        assertTrue(mode.usesTeamRules)
        assertTrue(mode.usesTailTagRules)
        assertEquals(mode, MatchMode.deserialize(mode.serialize()))
    }

    @Test
    fun `team counts must divide exactly`() {
        val team = MatchMode(setOf(MatchModifier.TEAM))
        assertNull(team.validate(GameConfiguration(startingItems = emptyList(), teamPlayersPerTeam = 2), 4))
        assertNotNull(team.validate(GameConfiguration(startingItems = emptyList(), teamPlayersPerTeam = 3), 4))

    }

    @Test
    fun `team allies are never enemies and tail tag targets teams`() {
        val first = UUID.randomUUID()
        val ally = UUID.randomUUID()
        val target = UUID.randomUUID()
        val other = UUID.randomUUID()
        val game = Game(
            mutableListOf(),
            settings = GameConfiguration(startingItems = emptyList()),
            mode = MatchMode(setOf(MatchModifier.TEAM, MatchModifier.TAIL_TAG)),
            phase = GamePhase.RUNNING,
            tickSource = { 0L },
        )
        game.combatTeams.putAll(mapOf(first to 0, ally to 0, target to 1, other to 2))
        game.tailTargetTeams[0] = 1

        assertFalse(game.areEnemies(first, ally))
        assertTrue(game.areEnemies(first, target))
        assertFalse(game.areEnemies(first, other))
    }

    @Test
    fun `removed mode cannot be selected or deserialized`() {
        assertEquals(setOf("DUAL", "TAIL_TAG", "TEAM"), MatchModifier.entries.map { it.name }.toSet())
        assertNull(MatchMode.deserialize("COOPERATIVE"))
        assertNull(MatchMode.deserialize("TEAM,COOPERATIVE"))
    }
}
