package org.beobma.classWarPlugin.gameClass.kaleidoscope

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.domain.*
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.SkillManager.getSkillId
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.status.list.Brightness
import org.beobma.classWarPlugin.util.*
import org.bukkit.*
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.*

class KaleidoscopeRuntime(private val scope: AbilityScope) : EffectApiAccess {
    private val owner get() = scope.playerData
    private val player get() = owner.player
    private val clock get() = scope.game.combatTick
    private data class Prism(val at: Location, val display: BlockDisplay, val ornaments: List<BlockDisplay>,
                             val normal: Vector, val color: Int, val born: Int, var pulseUntil: Int = 0)
    private data class SurfaceAim(val at: Location, val normal: Vector)
    private data class Laser(val at: Location, var age: Int = 0)
    private data class Wave(val prism: Prism, val domain: DomainSession?, var age: Int,
                            val source: Location, val travelTicks: Int,
                            val hit: MutableSet<UUID> = hashSetOf())
    private val prisms = mutableListOf<Prism>()
    private val domainPrisms = mutableListOf<Prism>()
    private val mirrors = mutableListOf<BlockDisplay>()
    private val mirrorLinks = mutableListOf<Pair<Prism,Prism>>()
    private val lasers = mutableListOf<Laser>()
    private val waves = mutableListOf<Wave>()
    private var domain: DomainSession? = null
    private var suppressedUntil = 0L
    private var tick = 0
    private var domainLinks = emptyList<Pair<Int, Int>>()
    private val combatEffects = SpectralCombatEffects()
    private lateinit var hud: KaleidoscopeStatus

    fun start() {
        hud = owner.getOrCreateStatus(owner) { KaleidoscopeStatus() }
        scope.resources.own { clearAll(); lasers.clear(); domain = null }
        object : AbilityRunnable(scope) {
            override fun onCancel() { clearAll(); lasers.clear() }
            override fun run() {
                tick++
                if (prisms.removeAll { if (!it.display.isValid) { removePrism(it); true } else false }) rebuildMirrors()
                if (tick%4==0) {
                    hud.synchronize(prisms.size,mirrorLinks.size,domain?.effectsEnabled==true,suppressedUntil-clock)
                    preview()
                }
                renderPrisms()
                lasers.toList().forEach { laser ->
                    laser.age++
                    if (laser.age < SpectralGeometry.WARNING_TICKS) warning(laser)
                    if (laser.age == SpectralGeometry.WARNING_TICKS) strike(laser.at)
                    if (laser.age >= SpectralGeometry.WARNING_TICKS)
                        combatEffects.beam(laser.at, beamTop(laser.at), laser.age-SpectralGeometry.WARNING_TICKS)
                    if (laser.age >= SpectralGeometry.WARNING_TICKS+SpectralCombatVisuals.AFTERGLOW_TICKS) lasers.remove(laser)
                }
                val waveFrame = waves.toList()
                val visible = waveFrame.indices.filter { index -> waveFrame[index].let {
                    it.age >= 0 && it.prism.display.isValid && (it.domain == null || it.domain.effectsEnabled)
                } }
                val rendering = SpectralCombatVisuals.waveAllocation(visible.size,tick).mapKeys { visible[it.key] }
                val targetsByWorld = waves.map { it.prism.at.world }.distinct().associateWith { Targeting.select(owner, TargetType.Enemy, it) }
                val moving=waveFrame.indices.filter { waveFrame[it].let { w -> w.age<0 && w.age>=-w.travelTicks } }
                val travelSelection=if(moving.isEmpty()) emptySet() else (0 until min(24,moving.size))
                    .map { moving[(Math.floorMod(tick,moving.size)*24+it)%moving.size] }.toSet()
                var chimeBudget = 3
                waveFrame.forEachIndexed { index, wave ->
                    if (!scope.isActive) return@forEachIndexed
                    if (!wave.prism.display.isValid || (wave.domain != null && !wave.domain.effectsEnabled)) { waves.remove(wave); return@forEachIndexed }
                    if (wave.age < 0) {
                        if (index in travelSelection) {
                            combatEffects.transfer(wave.source,wave.prism.at,1.0+wave.age.toDouble()/wave.travelTicks,wave.prism.color)
                        }
                        wave.age++
                        return@forEachIndexed
                    }
                    wave.age++
                    val r = min(4.0, wave.age * 0.5)
                    val previous = max(0.0, r-0.5)
                    if (wave.age == 1) {
                        wave.prism.pulseUntil=tick+10
                        if (chimeBudget-- > 0)
                            sounds.play(wave.prism.at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume = 0.42f, pitch = listOf(0.75f,0.89f,1f,1.12f,1.34f,1.5f,1.78f)[wave.prism.color%7])
                    }
                    rendering[index]?.let { samples -> combatEffects.wave(wave.prism.at,r,tick,samples) }
                    targetsByWorld.getValue(wave.prism.at.world).forEach { target ->
                        if (scope.isActive && !target.entityStatus.isDead && target.entity.uniqueId !in wave.hit && (wave.domain == null || wave.domain.contains(target.entity.location)) &&
                            SpectralGeometry.waveTouches(target.entity.boundingBox, wave.prism.at.toVector(), previous, r)) {
                            wave.hit += target.entity.uniqueId
                            target.damage(if (wave.domain == null) 1.0 else 0.1, DamageType.Normal, owner, damagePath = DamagePath.SKILL)
                            if (scope.isActive && clock >= suppressedUntil && !target.entityStatus.isDead) {
                                val brightness = target.getOrCreateStatus(owner) { Brightness() }
                                val previousPower = brightness.power
                                brightness.applyStatus(powerDelta = 1)
                                if (!brightness.applicationBlocked && scope.isActive)
                                    combatEffects.brightness(target.entity, max(previousPower+1,brightness.power),
                                        brightness !in target.statusAbnormalitys, tick)
                            }
                        }
                    }
                    if (wave.age >= 8) waves.remove(wave)
                }
                combatEffects.tick(tick)
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1, 1)
    }

    fun clearForSuspend() { domain?.close(); clearAll(); lasers.clear() }

    private fun reject(message: String): Boolean { player.sendMiniMessage("<red><bold>[!] $message"); return false }
    private fun aimSurface(range: Double): SurfaceAim? {
        val eye = player.eyeLocation
        val hit = eye.world.rayTraceBlocks(eye, eye.direction, range * ClassBalanceManager.rangeMultiplier(owner), FluidCollisionMode.NEVER, true) ?: return null
        val normal=hit.hitBlockFace?.direction?.takeIf { it.lengthSquared()>1e-8 } ?: Vector(0,1,0)
        return SurfaceAim(hit.hitPosition.toLocation(eye.world).add(normal.clone().multiply(0.38)),normal)
    }
    private fun aimed(range: Double) = aimSurface(range)?.at
    fun canPlace(): Boolean {
        if (player.isSneaking) return true
        if (prisms.size >= SpectralGeometry.PRISM_LIMIT) return reject("분광기는 최대 7개까지 설치할 수 있습니다.")
        val at = aimed(10.0) ?: return reject("10칸 내의 블록을 바라봐야 합니다.")
        return prisms.none { it.at.world == at.world && it.at.distanceSquared(at) < 0.64 } || reject("분광기가 너무 가깝습니다.")
    }
    fun canFire() = aimed(8.0) != null || reject("8칸 내의 블록을 바라봐야 합니다.")

    private fun cube(at: Location, material: Material, scale: Vector3f, rotation: Quaternionf = Quaternionf()): BlockDisplay =
        at.world.spawn(at.clone().apply { yaw=0f; pitch=0f }, BlockDisplay::class.java).apply {
            block = material.createBlockData(); isPersistent = false
            brightness = Display.Brightness(15,15); shadowRadius=0f; shadowStrength=0f
            interpolationDuration=3
            transformation = Transformation(Vector3f(scale).mul(-0.5f).rotate(rotation), rotation, scale, Quaternionf())
            TemporaryDisplayManager.mark(this, owner.uniqueId)
            val entity=this
            scope.resources.own(isAlive={ entity.isValid }) { entity.remove() }
        }
    private fun makePrism(at: Location, color: Int, normal: Vector = Vector(0,1,0), detailed: Boolean = false): Prism {
        val rotation=Quaternionf().rotationTo(Vector3f(0f,1f,0f),Vector3f(normal.x.toFloat(),normal.y.toFloat(),normal.z.toFloat()))
        val shell=cube(at,SpectralGeometry.glass[color%7],Vector3f(0.42f,0.5f,0.42f),Quaternionf(rotation).rotateZ(0.4f))
        val ornaments=if(detailed) listOf(
            cube(at,Material.SEA_LANTERN,Vector3f(0.16f),Quaternionf(rotation)),
            cube(at,SpectralGeometry.glass[(color+3)%7],Vector3f(0.56f,0.07f,0.56f),Quaternionf(rotation).rotateY(0.7f))
        ) else emptyList()
        return Prism(at,shell,ornaments,normal.clone(),color,tick)
    }
    private fun removePrism(prism: Prism) { prism.display.remove(); prism.ornaments.forEach { it.remove() } }

    fun place(): Boolean {
        if (!canPlace()) return false
        if (player.isSneaking) {
            prisms.forEach { combatEffects.prismShatter(it.at,it.color) }
            clearNormal()
            sounds.play(player, Sound.BLOCK_GLASS_BREAK, volume=0.45f, pitch=1.4f)
            return true
        }
        val surface = aimSurface(10.0) ?: return false
        val at=surface.at
        prisms += makePrism(at, prisms.size,surface.normal,detailed=true)
        rebuildMirrors()
        combatEffects.wave(at, 0.65, tick)
        sounds.play(at, Sound.BLOCK_AMETHYST_BLOCK_PLACE, volume=0.7f, pitch=1.3f)
        return true
    }
    private fun rebuildMirrors() {
        mirrors.forEach { it.remove() }; mirrors.clear()
        mirrorLinks.clear()
        prisms.groupBy { it.at.world }.values.forEach { group ->
            SpectralGeometry.edges(group.map { it.at.toVector() }).forEach { (a,b) ->
                mirrorLinks += group[a] to group[b]
                val delta = group[b].at.toVector().subtract(group[a].at.toVector())
                val rotation = Quaternionf().rotationTo(Vector3f(1f,0f,0f), Vector3f(delta.x.toFloat(),delta.y.toFloat(),delta.z.toFloat()).normalize())
                mirrors += cube(group[a].at.clone().add(delta.clone().multiply(0.5)), Material.LIGHT_BLUE_STAINED_GLASS,
                    Vector3f(delta.length().toFloat(), 1.25f, 0.025f), rotation)
                val middle=group[a].at.clone().add(delta.clone().multiply(0.5))
                // A thin silver frame follows the SAME tilted transform as the mirror plane.
                for(side in listOf(-1,1)) {
                    val up=Vector3f(0f,side*0.625f,0f).rotate(rotation)
                    mirrors += cube(middle.clone().add(up.x.toDouble(),up.y.toDouble(),up.z.toDouble()),Material.IRON_BLOCK,
                        Vector3f(delta.length().toFloat(),0.035f,0.045f),Quaternionf(rotation))
                    val edge=Vector3f(side*delta.length().toFloat()*0.5f,0f,0f).rotate(rotation)
                    mirrors += cube(middle.clone().add(edge.x.toDouble(),edge.y.toDouble(),edge.z.toDouble()),Material.IRON_BLOCK,
                        Vector3f(0.035f,1.25f,0.045f),Quaternionf(rotation))
                }
            }
        }
    }
    private fun clearNormal() { prisms.forEach(::removePrism); prisms.clear(); mirrors.forEach { it.remove() }; mirrors.clear(); mirrorLinks.clear(); waves.removeAll { it.domain == null } }
    private fun clearDomain() { domainPrisms.forEach(::removePrism); domainPrisms.clear(); domainLinks=emptyList(); waves.removeAll { it.domain != null }; combatEffects.clear() }
    private fun clearAll() { clearNormal(); clearDomain(); combatEffects.clear() }

    fun fire(): Boolean {
        val at = aimed(8.0) ?: return reject("8칸 내의 블록을 바라봐야 합니다.")
        lasers += Laser(at)
        sounds.play(at, Sound.BLOCK_BEACON_POWER_SELECT, volume=0.55f, pitch=1.6f)
        return true
    }
    private fun warning(laser: Laser) = combatEffects.warning(laser.at, beamTop(laser.at), laser.age)
    private fun beamTop(at: Location): Location {
        val active = domain?.takeIf { it.effectsEnabled && it.contains(at) }
        val top = if (active != null) active.center.y + sqrt((18.5*18.5 - (at.x-active.center.x).pow(2) - (at.z-active.center.z).pow(2)).coerceAtLeast(0.0))
                  else min(at.world.maxHeight-1.0, at.y+24)
        return at.clone().apply { y = max(at.y + 0.05, top) }
    }
    private fun strike(at: Location) {
        val active = domain?.takeIf { it.effectsEnabled && it.contains(at) }
        val top = beamTop(at)
        Targeting.select(owner, TargetType.Enemy, at.world).filter {
            (active == null || active.contains(it.entity.location)) && HitboxUtil.intersectsSegment(it.entity.boundingBox, at.toVector(), top.toVector(), 0.65)
        }.forEach { it.damage(5.0, DamageType.Normal, owner, damagePath=DamagePath.SKILL) }
        combatEffects.impact(at)
        val nodes = if (active != null) domainPrisms + prisms.filter { active.contains(it.at) } else prisms.filter { it.at.world == at.world }
        val plan=SpectralGeometry.propagation(nodes.map { it.at.toVector() },at.toVector())
        plan.forEach { (index, arrival) ->
            val parent=arrival.parent
            val source=parent?.let { nodes[it].at } ?: at
            val travel=parent?.let { arrival.tick-plan.getValue(it).tick } ?: 0
            waves += Wave(nodes[index], active, -arrival.tick, source, travel)
            if(parent==null) combatEffects.transfer(source,nodes[index].at,1.0,nodes[index].color)
        }
    }
    private fun preview() {
        if(org.beobma.classWarPlugin.effect.EffectSuppressionManager.isSuppressed(player.world)) return
        val id=getSkillId(player.inventory.itemInMainHand,player.uniqueId) ?: return
        if(scope.owner.skills.none { it.definitionId=="kaleidoskop/prism" && it.matchesId(id) } || player.isSneaking) return
        val surface=aimSurface(10.0) ?: return
        val available=prisms.size<SpectralGeometry.PRISM_LIMIT && prisms.none {
            it.at.world==surface.at.world && it.at.distanceSquared(surface.at)<0.64
        }
        val color=if(available) Color.fromRGB(140,235,255) else Color.fromRGB(240,75,90)
        val (right,up)=SpectralGeometry.surfaceFrame(surface.normal)
        fun local(at: Location) = player.spawnParticle(Particle.DUST,at,1,0.0,0.0,0.0,0.0,Particle.DustOptions(color,0.7f))
        repeat(24) { i ->
            val angle=i*PI/12
            local(surface.at.clone().add(right.clone().multiply(cos(angle)*0.38)).add(up.clone().multiply(sin(angle)*0.38)))
        }
        if(available) prisms.filter { it.at.world==surface.at.world && it.at.distanceSquared(surface.at)<=64.0 }.forEach {
            val delta=it.at.toVector().subtract(surface.at.toVector())
            repeat(4) { j -> local(surface.at.clone().add(delta.clone().multiply((j+0.5)/4))) }
        }
    }

    private fun renderPrisms() {
        if (tick%3 != 0) return
        val nodes = prisms + domainPrisms.takeIf { domain?.effectsEnabled == true }.orEmpty()
        nodes.forEachIndexed { i, prism ->
            if(prism.ornaments.isEmpty() && tick%6!=0) return@forEachIndexed
            val opening=((tick-prism.born)/6.0).coerceIn(0.2,1.0)
            val pulse=((prism.pulseUntil-tick)/10.0).coerceIn(0.0,1.0)
            val axis=Quaternionf().rotationTo(Vector3f(0f,1f,0f),Vector3f(prism.normal.x.toFloat(),prism.normal.y.toFloat(),prism.normal.z.toFloat()))
            val q=Quaternionf(axis).rotateY((tick*0.028+i).toFloat()).rotateZ(0.4f)
            val scale=Vector3f(0.42f,0.5f,0.42f).mul((opening*(1+0.22*pulse)).toFloat())
            prism.display.interpolationDuration=if(prism.ornaments.isEmpty()) 6 else 3
            prism.display.transformation=Transformation(Vector3f(scale).mul(-0.5f).rotate(q),q,scale,Quaternionf())
            prism.display.interpolationDelay=0
            prism.ornaments.getOrNull(1)?.let { disk ->
                val rotation=Quaternionf(axis).rotateY((-tick*0.018+i).toFloat())
                val size=Vector3f(0.56f,0.07f,0.56f).mul(opening.toFloat())
                disk.transformation=Transformation(Vector3f(size).mul(-0.5f).rotate(rotation),rotation,size,Quaternionf())
                disk.interpolationDelay=0
            }
            if (i%3 == tick/3%3) SpectralFx.dust(prism.at,prism.color,if(pulse>0) 1.8f else 0.8f,white=pulse>0)
        }
        if(tick%6!=0) return
        mirrorLinks.forEachIndexed { index,(a,b) ->
            val delta=b.at.toVector().subtract(a.at.toVector())
            // Glints sweep in opposite directions on the two edges of a real mirror frame.
            val q=Quaternionf().rotationTo(Vector3f(1f,0f,0f),Vector3f(delta.x.toFloat(),delta.y.toFloat(),delta.z.toFloat()).normalize())
            for(side in listOf(-1,1)) {
                val t=((tick*0.015+index*0.13)*side % 1+1)%1
                val up=Vector3f(0f,0.625f*side,0f).rotate(q)
                val head=a.at.clone().add(delta.clone().multiply(t)).add(up.x.toDouble(),up.y.toDouble(),up.z.toDouble())
                SpectralFx.dust(head,a.color,0.95f,white=true)
                repeat(3) { j ->
                    val at=a.at.clone().add(delta.clone().multiply(j/2.0)).add(up.x.toDouble(),up.y.toDouble(),up.z.toDouble())
                    SpectralFx.dust(at,a.color+j,0.6f)
                }
            }
        }
        if (domain?.effectsEnabled == true) domainLinks.filterIndexed { i,_ -> i%4 == tick/6%4 }.take(72).forEach { (a,b) ->
            val from=domainPrisms[a].at; val delta=domainPrisms[b].at.toVector().subtract(from.toVector())
            repeat(4) { j -> SpectralFx.dust(from.clone().add(delta.clone().multiply((j+0.5)/4)), a+j+tick/6, 0.65f) }
        }
    }
    private fun createDomainPrisms(session: DomainSession) {
        clearDomain()
        if (!session.effectsEnabled) return
        SpectralGeometry.lattice(session.definition.radius).forEachIndexed { i, p -> domainPrisms += makePrism(session.center.clone().add(p), i) }
        domainLinks = SpectralGeometry.edges(domainPrisms.map { it.at.toVector() })
    }
    fun expand(): Boolean {
        val success = DomainManager.expand(scope, DomainDefinition(
            name="만화경", radius=20, durationTicks=320, floor=Material.TINTED_GLASS,
            interior=SpectralGeometry::interior, interiorLightLevel=12,
            presentation={ KaleidoscopePresentation(it) },
            onStart={ domain=it; createDomainPrisms(it) },
            onClashChanged={ session, clashed -> if (clashed) clearDomain() else createDomainPrisms(session) },
            onEnd={ clearAll(); domain=null; suppressedUntil=clock+400 },
        ))
        return success || reject("현재 위치나 상태에서는 영역을 전개할 수 없습니다.")
    }
}
