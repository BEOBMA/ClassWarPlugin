package org.beobma.classWarPlugin.util

import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.pow
import kotlin.math.roundToInt

/** Converts the bundled trial theme into server-side note-block sounds. */
internal class CourtroomMidiPlayer private constructor(
    private val notesByTick: Map<Int, List<Note>>,
) {
    private var tick = 0

    fun tick(listeners: Collection<Player>) {
        val notes = notesByTick[tick++].orEmpty()
        if (notes.isEmpty()) return
        notes.take(MAX_NOTES_PER_TICK).forEach { note ->
            listeners.filter { it.isOnline }.forEach { listener ->
                listener.playSound(
                    listener.location,
                    note.sound,
                    SoundCategory.RECORDS,
                    note.volume,
                    note.pitch,
                )
            }
        }
    }

    private data class Note(val sound: Sound, val volume: Float, val pitch: Float)
    private data class OrderedEvent(val event: MidiEvent, val track: Int, val index: Int)

    companion object {
        private const val RESOURCE = "/music/27_Dating_Fight.mid.gz.b64"
        private const val MICROS_PER_SERVER_TICK = 50_000.0
        private const val DEFAULT_TEMPO = 500_000L
        private const val MAX_NOTES_PER_TICK = 28
        private const val BACKGROUND_VOLUME_MULTIPLIER = 0.45F
        private val fullVolumePrograms = setOf(
            1,   // Bright Acoustic Piano (GM 2)
            55,  // Orchestra Hit (GM 56)
            80,  // Square Lead (GM 81) - both tracks
            112, // Tinkle Bell (GM 113)
        )
        private val cachedNotes: Map<Int, List<Note>> by lazy(::loadNotes)

        fun create(): CourtroomMidiPlayer = CourtroomMidiPlayer(cachedNotes)

        private fun loadNotes(): Map<Int, List<Note>> {
            return runCatching {
                val encoded = CourtroomMidiPlayer::class.java.getResourceAsStream(RESOURCE)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: return emptyMap()
                val compressed = Base64.getMimeDecoder().decode(encoded)
                val midi = GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
                val sequence = MidiSystem.getSequence(ByteArrayInputStream(midi))
                if (sequence.divisionType != Sequence.PPQ) return emptyMap()

                val events = buildList {
                    sequence.tracks.forEachIndexed { trackIndex, track ->
                        repeat(track.size()) { eventIndex ->
                            add(OrderedEvent(track[eventIndex], trackIndex, eventIndex))
                        }
                    }
                }.sortedWith(
                    compareBy<OrderedEvent> { it.event.tick }
                        .thenBy { eventPriority(it.event) }
                        .thenBy { it.track }
                        .thenBy { it.index }
                )

                val programs = IntArray(16)
                val result = mutableMapOf<Int, MutableList<Note>>()
                var tempo = DEFAULT_TEMPO
                var previousMidiTick = 0L
                var elapsedMicros = 0.0
                events.forEach { ordered ->
                    val event = ordered.event
                    elapsedMicros += (event.tick - previousMidiTick) * tempo.toDouble() / sequence.resolution
                    previousMidiTick = event.tick
                    when (val message = event.message) {
                        is MetaMessage -> if (message.type == 0x51 && message.data.size >= 3) {
                            tempo = ((message.data[0].toInt() and 0xFF) shl 16 or
                                ((message.data[1].toInt() and 0xFF) shl 8) or
                                (message.data[2].toInt() and 0xFF)).toLong()
                        }
                        is ShortMessage -> when (message.command) {
                            ShortMessage.PROGRAM_CHANGE -> programs[message.channel] = message.data1
                            ShortMessage.NOTE_ON -> if (message.data2 > 0) {
                                val serverTick = (elapsedMicros / MICROS_PER_SERVER_TICK).roundToInt()
                                result.getOrPut(serverTick) { mutableListOf() } += Note(
                                    soundFor(message.channel, programs[message.channel], message.data1),
                                    noteVolume(message.channel, programs[message.channel], message.data2),
                                    notePitch(message.data1),
                                )
                            }
                        }
                    }
                }
                result
            }.getOrElse { emptyMap() }
        }

        private fun eventPriority(event: MidiEvent): Int = when (val message = event.message) {
            is MetaMessage -> if (message.type == 0x51) 0 else 3
            is ShortMessage -> if (message.command == ShortMessage.PROGRAM_CHANGE) 1 else 2
            else -> 3
        }

        private fun soundFor(channel: Int, program: Int, note: Int): Sound {
            if (channel == 9) return when (note) {
                in 35..36 -> Sound.BLOCK_NOTE_BLOCK_BASEDRUM
                in 38..40 -> Sound.BLOCK_NOTE_BLOCK_SNARE
                else -> Sound.BLOCK_NOTE_BLOCK_HAT
            }
            return when (program) {
                in 24..31 -> Sound.BLOCK_NOTE_BLOCK_GUITAR
                in 32..39 -> Sound.BLOCK_NOTE_BLOCK_BASS
                in 40..55 -> Sound.BLOCK_NOTE_BLOCK_CHIME
                in 56..63 -> Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO
                in 64..79 -> Sound.BLOCK_NOTE_BLOCK_FLUTE
                in 80..95 -> Sound.BLOCK_NOTE_BLOCK_BIT
                in 8..15 -> Sound.BLOCK_NOTE_BLOCK_BELL
                else -> Sound.BLOCK_NOTE_BLOCK_HARP
            }
        }

        private fun noteVolume(channel: Int, program: Int, velocity: Int): Float {
            val currentVolume = (0.18F + velocity / 127.0F * 0.42F).coerceAtMost(0.6F)
            val keepCurrentVolume = channel != 9 && program in fullVolumePrograms
            return if (keepCurrentVolume) currentVolume else currentVolume * BACKGROUND_VOLUME_MULTIPLIER
        }

        private fun notePitch(midiNote: Int): Float {
            var folded = midiNote
            while (folded < 54) folded += 12
            while (folded > 78) folded -= 12
            return 2.0.pow((folded - 66) / 12.0).toFloat().coerceIn(0.5F, 2.0F)
        }
    }
}
