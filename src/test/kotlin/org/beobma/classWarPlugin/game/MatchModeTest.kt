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
            .toggled(MatchModifier.COOPERATIVE)
            .toggled(MatchModifier.DUAL)
            .toggled(MatchModifier.TAIL_TAG)

        assertEquals(2, mode.assignedClassCount)
        assertTrue(mode.usesTeamRules)
        assertTrue(mode.usesCooperativeRules)
        assertTrue(mode.usesTailTagRules)
        assertEquals(mode, MatchMode.deserialize(mode.serialize()))
    }

    @Test
    fun `team and cooperative counts must divide exactly and nest`() {
        val team = MatchMode(setOf(MatchModifier.TEAM))
        assertNull(team.validate(GameConfiguration(startingItems = emptyList(), teamPlayersPerTeam = 2), 4))
        assertNotNull(team.validate(GameConfiguration(startingItems = emptyList(), teamPlayersPerTeam = 3), 4))

        val combined = MatchMode(setOf(MatchModifier.TEAM, MatchModifier.COOPERATIVE))
        assertNull(combined.validate(
            GameConfiguration(startingItems = emptyList(), teamPlayersPerTeam = 4, cooperativePlayersPerGroup = 2),
            8,
        ))
        assertNotNull(combined.validate(
            GameConfiguration(startingItems = emptyList(), teamPlayersPerTeam = 3, cooperativePlayersPerGroup = 2),
            6,
        ))
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
    fun `cooperative roles split controls`() {
        assertTrue(CooperativeRole.MOVEMENT_COMBAT.canMove)
        assertTrue(CooperativeRole.MOVEMENT_COMBAT.canBasicAttack)
        assertFalse(CooperativeRole.MOVEMENT_COMBAT.canUseSkills)
        assertFalse(CooperativeRole.HOTBAR_SKILLS.canMove)
        assertTrue(CooperativeRole.HOTBAR_SKILLS.canChangeHotbar)
        assertTrue(CooperativeRole.HOTBAR_SKILLS.canUseSkills)

        val mover = UUID.randomUUID()
        val skillUser = UUID.randomUUID()
        val game = Game(
            mutableListOf(),
            settings = GameConfiguration(startingItems = emptyList()),
            mode = MatchMode(setOf(MatchModifier.COOPERATIVE)),
            phase = GamePhase.RUNNING,
            tickSource = { 0L },
        )
        game.cooperativeRoles[mover] = CooperativeRole.MOVEMENT_COMBAT
        game.cooperativeRoles[skillUser] = CooperativeRole.HOTBAR_SKILLS
        assertTrue(game.canPerform(mover, CooperativeAction.MOVE))
        assertFalse(game.canPerform(mover, CooperativeAction.USE_SKILL))
        assertFalse(game.canPerform(skillUser, CooperativeAction.BASIC_ATTACK))
        assertTrue(game.canPerform(skillUser, CooperativeAction.CHANGE_HOTBAR))
    }
}
