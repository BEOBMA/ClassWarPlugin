package org.beobma.classWarPlugin.gameClass.firearm

import kotlin.random.Random

object FirearmRoster {
    val ids = listOf("hunter", "sturmtruppe", "spezialeinheitsmitglied", "schwerekavallerie", "gun-blader", "sniper", "freikugel")
    fun next(previous: String?, random: Random = Random.Default): String = ids.filter { it != previous }.random(random)
}
