package org.beobma.classWarPlugin.effect

import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

/**
 * 클래스 스킬의 사운드 출력을 담당하는 API.
 *
 * [play]는 위치 주변의 플레이어에게, [playTo]는 지정한 플레이어 한 명에게만 들려준다.
 */
object SoundApi {
    fun play(
        location: Location,
        sound: Sound,
        volume: Float = 1.0f,
        pitch: Float = 1.0f,
        category: SoundCategory = SoundCategory.MASTER,
    ) {
        if (EffectSuppressionManager.isSuppressed(location.world)) return
        location.world.playSound(location, sound, category, volume, pitch)
    }

    fun play(
        entity: Entity,
        sound: Sound,
        volume: Float = 1.0f,
        pitch: Float = 1.0f,
        category: SoundCategory = SoundCategory.MASTER,
    ) = play(entity.location, sound, volume, pitch, category)

    fun playTo(
        player: Player,
        sound: Sound,
        volume: Float = 1.0f,
        pitch: Float = 1.0f,
        category: SoundCategory = SoundCategory.MASTER,
        location: Location = player.location,
    ) {
        if (EffectSuppressionManager.isSuppressed(player.world) || EffectSuppressionManager.isSuppressed(location.world)) return
        player.playSound(location, sound, category, volume, pitch)
    }

    fun stop(
        player: Player,
        sound: Sound,
        category: SoundCategory = SoundCategory.MASTER,
    ) {
        player.stopSound(sound, category)
    }

    fun stopAll(player: Player) {
        player.stopAllSounds()
    }
}
