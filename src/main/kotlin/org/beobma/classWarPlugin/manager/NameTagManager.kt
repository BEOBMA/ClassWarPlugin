package org.beobma.classWarPlugin.manager

import org.bukkit.Bukkit
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import java.util.Collections
import java.util.IdentityHashMap

/** 경기 중 참가자의 머리 위 이름표를 숨기고 기존 스코어보드 팀 설정을 복원한다. */
object NameTagManager {
    private const val HIDDEN_TEAM_NAME = "cw_hide_names"

    private class ScoreboardState {
        val originalTeamOptions: MutableMap<Team, Team.OptionStatus> = IdentityHashMap()
        var hiddenTeam: Team? = null
    }

    private val states: MutableMap<Scoreboard, ScoreboardState> = IdentityHashMap()

    /** 온라인 플레이어가 사용하는 모든 스코어보드에서 [playerNames]의 이름표를 숨긴다. */
    fun hideAll(playerNames: Collection<String>) {
        if (playerNames.isEmpty()) return
        val scoreboards = Collections.newSetFromMap(IdentityHashMap<Scoreboard, Boolean>())
        Bukkit.getOnlinePlayers().forEach { scoreboards.add(it.scoreboard) }
        scoreboards.forEach { scoreboard -> hideOn(scoreboard, playerNames) }
    }

    /** 변경했던 팀 옵션을 되돌리고 임시 숨김 팀을 제거한다. */
    fun restoreAll() {
        states.forEach { (scoreboard, state) ->
            state.originalTeamOptions.forEach { (team, option) ->
                if (scoreboard.getTeam(team.name) === team) {
                    team.setOption(Team.Option.NAME_TAG_VISIBILITY, option)
                }
            }
            state.hiddenTeam?.let { team ->
                if (scoreboard.getTeam(team.name) === team) team.unregister()
            }
        }
        states.clear()
    }

    private fun hideOn(scoreboard: Scoreboard, playerNames: Collection<String>) {
        val state = states.getOrPut(scoreboard, ::ScoreboardState)
        playerNames.forEach { playerName ->
            val currentTeam = scoreboard.getEntryTeam(playerName)
            if (currentTeam != null && currentTeam !== state.hiddenTeam) {
                state.originalTeamOptions.putIfAbsent(
                    currentTeam,
                    currentTeam.getOption(Team.Option.NAME_TAG_VISIBILITY),
                )
                currentTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
                return@forEach
            }

            val hiddenTeam = state.hiddenTeam ?: createHiddenTeam(scoreboard).also { state.hiddenTeam = it }
            hiddenTeam.addEntry(playerName)
        }
    }

    private fun createHiddenTeam(scoreboard: Scoreboard): Team {
        if (scoreboard.getTeam(HIDDEN_TEAM_NAME) == null) {
            return scoreboard.registerNewTeam(HIDDEN_TEAM_NAME).apply {
                setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
            }
        }

        for (suffix in 1..999) {
            val teamName = "cw_hide_$suffix"
            if (scoreboard.getTeam(teamName) == null) {
                return scoreboard.registerNewTeam(teamName).apply {
                    setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
                }
            }
        }
        error("이름표 숨김용 스코어보드 팀을 생성할 수 없습니다.")
    }
}
