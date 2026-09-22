package org.beobma.classWarPlugin.gameClass.referee

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import org.beobma.classWarPlugin.domain.DomainInteriors
import org.beobma.classWarPlugin.domain.DomainPresentation
import org.beobma.classWarPlugin.domain.DomainSession
import org.bukkit.*
import org.bukkit.entity.Player
import java.time.Duration
import kotlin.math.*

/** Local coordinates, independent of the caster's head rotation. No entities or world mutations. */
internal object CourtGlyphs {
    data class Point(val x: Double, val y: Double, val z: Double)
    fun line(a: Point, b: Point, samples: Int = 12) = List(samples + 1) { i ->
        val t = i.toDouble() / samples
        Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)
    }
    fun seal(size: Double, y: Double = 0.12): List<Point> = buildList {
        val corners = listOf(Point(-size, y, 0.0), Point(0.0, y, -size), Point(size, y, 0.0), Point(0.0, y, size))
        corners.indices.forEach { addAll(line(corners[it], corners[(it + 1) % 4], 10)) }
        addAll(line(Point(-size * 0.5, y, 0.0), Point(size * 0.5, y, 0.0), 10))
        addAll(line(Point(0.0, y, -size * 0.5), Point(0.0, y, size * 0.5), 10))
    }
    fun scales(tilt: Double): List<Point> = buildList {
        val angle = tilt.coerceIn(-0.3, 0.3)
        addAll(line(Point(0.0, 2.2, -2.5), Point(0.0, 5.8, -2.5), 18))
        val left = Point(-2.6 * cos(angle), 5 + 2.6 * sin(angle), -2.5)
        val right = Point(2.6 * cos(angle), 5 - 2.6 * sin(angle), -2.5)
        addAll(line(left, right, 26))
        for (tip in listOf(left, right)) {
            addAll(line(tip, Point(tip.x, tip.y - 1.5, tip.z), 8))
            val rim = (0..16).map { i ->
                val a = PI + i * PI / 16
                Point(tip.x + cos(a) * 0.8, tip.y - 1.5 + sin(a) * 0.45, tip.z)
            }
            addAll(rim)
            addAll(line(Point(tip.x - 0.8, tip.y - 1.5, tip.z), Point(tip.x + 0.8, tip.y - 1.5, tip.z), 8))
        }
    }
}

internal object RefereeEffects {
    val ash = Particle.DustOptions(Color.fromRGB(103, 100, 109), 0.95f)
    val ember = Particle.DustOptions(Color.fromRGB(113, 41, 35), 1.1f)
    val crimson = Particle.DustOptions(Color.fromRGB(142, 19, 32), 1.1f)
    private val jade = Particle.DustOptions(Color.fromRGB(62, 116, 101), 1.1f)

    fun draw(origin: Location, points: List<CourtGlyphs.Point>, color: Particle.DustOptions = ember) {
        points.forEach { p -> origin.world.spawnParticle(Particle.DUST,
            origin.clone().add(p.x, p.y, p.z), 1, 0.0, 0.0, 0.0, 0.0, color) }
    }
    fun sound(at: Location, sound: Sound, volume: Float, pitch: Float) =
        at.world.playSound(at, sound, SoundCategory.PLAYERS, volume, pitch)

    fun mark(at: Location) {
        draw(at, CourtGlyphs.seal(0.7), ash)
        draw(at, CourtGlyphs.line(CourtGlyphs.Point(0.0, 0.3, 0.0), CourtGlyphs.Point(0.0, 1.6, 0.0), 8))
        sound(at, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.25f, 0.6f)
    }
    fun leap(at: Location) {
        draw(at, CourtGlyphs.seal(1.1), ash)
        sound(at, Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.55f, 0.7f)
    }
    fun strike(at: Location) {
        draw(at, CourtGlyphs.line(CourtGlyphs.Point(0.0, 3.0, 0.0), CourtGlyphs.Point(0.0, 0.0, 0.0), 22), ash)
        draw(at, CourtGlyphs.seal(1.8), ember)
        at.world.spawnParticle(Particle.SWEEP_ATTACK, at.clone().add(0.0, 0.7, 0.0), 1)
        sound(at, Sound.BLOCK_HEAVY_CORE_HIT, 0.8f, 0.65f)
        sound(at, Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 0.7f, 0.6f)
    }
    fun indict(at: Location) {
        draw(at, CourtGlyphs.seal(1.25), crimson)
        val top = at.clone().add(0.0, 2.4, 0.0)
        draw(top, CourtGlyphs.line(CourtGlyphs.Point(-0.45, 0.4, 0.0), CourtGlyphs.Point(0.45, -0.4, 0.0), 12), crimson)
        draw(top, CourtGlyphs.line(CourtGlyphs.Point(0.45, 0.4, 0.0), CourtGlyphs.Point(-0.45, -0.4, 0.0), 12), crimson)
        sound(at, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 0.65f)
        sound(at, Sound.BLOCK_CHISELED_BOOKSHELF_INSERT, 0.7f, 0.85f)
    }
    fun rejected(player: Player) = player.playSound(player.location, Sound.BLOCK_DECORATED_POT_INSERT_FAIL,
        SoundCategory.PLAYERS, 0.35f, 0.9f)

    fun plea(at: Location) {
        draw(at, CourtGlyphs.seal(1.0), ash)
        sound(at, Sound.ITEM_BOOK_PAGE_TURN, 0.55f, 1.25f)
    }
    fun trial(session: DomainSession, tick: Int, answered: Boolean) {
        if (tick % 4 == 0) {
            val tilt = sin(tick * 0.04) * if (answered) 0.025 else 0.16
            draw(session.center, CourtGlyphs.scales(tilt), ash)
            // Each disappearing notch represents a second, not a fabricated verdict hint.
            val remaining = (20 - tick / 20).coerceIn(0, 20)
            val notches = (0 until remaining).flatMap { i ->
                val x = -2.85 + i * 0.3
                CourtGlyphs.line(CourtGlyphs.Point(x, 0.18, -1.3), CourtGlyphs.Point(x, 0.18, -1.6), 1)
            }
            draw(session.center, notches, if (remaining <= 5) crimson else ember)
        }
        val beat = if (tick >= 300) 10 else 40
        if (tick % beat == 0) session.players().forEach {
            it.playSound(it.location, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 0.28f, 0.6f)
        }
    }
    fun verdict(session: DomainSession, at: Location, verdict: Verdict) {
        val innocent = verdict.severity == 0
        val color = if (innocent) jade else crimson
        draw(at, CourtGlyphs.seal(2.0), color)
        draw(at, CourtGlyphs.seal(1.4, 0.35), ash)
        val text = when (verdict.severity) { 0 -> "무죄 · 혐의 해소"; 1 -> "경형"; 2 -> "중형"; 3 -> "가중 중형"; else -> "사형" }
        session.players().forEach {
            it.showTitle(Title.title(Component.text(if (verdict.perjury) "위증 확인 — 선고" else "대천칭 — 선고",
                NamedTextColor.DARK_RED), Component.text(text, if (innocent) NamedTextColor.GREEN else NamedTextColor.RED)
                .decorate(TextDecoration.BOLD), Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(300))))
            it.playSound(it.location, if (innocent) Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE else Sound.BLOCK_HEAVY_CORE_HIT,
                SoundCategory.PLAYERS, 0.8f, if (innocent) 0.7f else 0.5f)
            it.playSound(it.location, Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, SoundCategory.PLAYERS, 0.6f, 0.65f)
        }
    }

    fun courtroom(radius: Int) = DomainInteriors.courtroom(radius).map {
        it.copy(material = if (it.material == Material.DARK_OAK_PLANKS) Material.POLISHED_DEEPSLATE else Material.CHISELED_POLISHED_BLACKSTONE)
    }
}

/** Oppressive black-and-crimson ritual. All cues are finite and owned by the domain session. */
internal class RefereeCourtEffects(private val session: DomainSession) : DomainPresentation {
    private val center get() = session.center
    private val radius = session.definition.radius - 2.0
    private var ticks = 0
    private fun rumble(volume: Float, pitch: Float) {
        session.players().forEach {
            it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, SoundCategory.PLAYERS, volume, pitch)
        }
    }
    private fun smoke(at: Location, count: Int = 3) {
        at.world.spawnParticle(Particle.SMOKE, at, count, 0.25, 0.35, 0.25, 0.01)
    }
    override fun start() {
        RefereeEffects.draw(center, CourtGlyphs.seal(1.2), RefereeEffects.crimson)
        rumble(0.8f, 0.5f)
        RefereeEffects.sound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 0.5f)
    }
    override fun formation(progress: Double) {
        if (ticks++ % 3 != 0) return
        val p = progress.coerceIn(0.0, 1.0)
        RefereeEffects.draw(center, CourtGlyphs.seal((radius * p).coerceAtLeast(0.3)), RefereeEffects.crimson)
        // Broken, climbing fissures rather than bright pillars of light.
        for (side in listOf(-1, 1)) for (depth in listOf(-1, 1)) {
            val x = side * radius * 0.55
            val z = depth * radius * 0.55
            val line = CourtGlyphs.line(CourtGlyphs.Point(x, 0.2, z), CourtGlyphs.Point(x, p * radius * 0.65, z), 12)
            RefereeEffects.draw(center, line.filterIndexed { index, _ -> index % 3 != 1 }, RefereeEffects.ember)
            smoke(center.clone().add(x, p * radius * 0.65, z))
        }
        if (ticks % 18 == 1) rumble((0.25 + p * 0.3).toFloat(), 0.5f)
    }
    override fun reveal(subtitle: Boolean) {
        session.players().forEach {
            it.showTitle(Title.title(Component.text("「영역 전개」", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
                Component.text("「대천칭」", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(session.definition.titleDurationMillis + 1000), Duration.ZERO)))
        }
        if (subtitle) {
            RefereeEffects.sound(center, Sound.BLOCK_HEAVY_CORE_PLACE, 1.0f, 0.5f)
            RefereeEffects.draw(center, CourtGlyphs.scales(0.0), RefereeEffects.crimson)
            rumble(0.85f, 0.5f)
        } else {
            RefereeEffects.sound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 0.5f)
            RefereeEffects.draw(center, CourtGlyphs.seal(radius), RefereeEffects.crimson)
            smoke(center.clone().add(0.0, 0.5, 0.0), 12)
        }
    }
    override fun activate() {
        RefereeEffects.sound(center, Sound.BLOCK_HEAVY_CORE_HIT, 0.85f, 0.5f)
        RefereeEffects.draw(center, CourtGlyphs.seal(radius * 0.8), RefereeEffects.ember)
    }
    override fun sustain(tick: Int) {
        if (tick % 6 != 0) return
        RefereeEffects.draw(center, CourtGlyphs.seal(radius), RefereeEffects.ember)
        for (side in listOf(-1, 1)) {
            val at = center.clone().add(side * radius * 0.7, 0.4, 0.0)
            RefereeEffects.draw(at, CourtGlyphs.seal(0.45), RefereeEffects.crimson)
            if (tick % 18 == 0) smoke(at, 2)
        }
        if (tick > 0 && tick % 100 == 0) rumble(0.25f, 0.5f)
    }
    override fun beginDissolve() {
        RefereeEffects.sound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.7f, 0.5f)
    }
    override fun dissolve(tick: Int) {
        if (tick % 3 == 0) {
            val remaining = (1 - tick / 40.0).coerceIn(0.0, 1.0)
            RefereeEffects.draw(center, CourtGlyphs.seal(radius * remaining, 0.12), RefereeEffects.crimson)
            for (side in listOf(-1, 1)) smoke(center.clone().add(side * radius * remaining, 0.3, 0.0), 4)
        }
        if (tick == 40) RefereeEffects.sound(center, Sound.BLOCK_DEEPSLATE_BREAK, 0.8f, 0.5f)
    }
}
