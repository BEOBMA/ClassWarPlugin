package org.beobma.classWarPlugin.gameClass.relic

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.*
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.effect.*
import org.beobma.classWarPlugin.gameClass.creator.CreationGeometry
import org.beobma.classWarPlugin.gameClass.constellations.StarSteering
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.status.list.*
import org.beobma.classWarPlugin.status.list.Vibration
import org.beobma.classWarPlugin.util.*
import org.bukkit.*
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.random.Random

class RelicRuntime(private val scope: AbilityScope) {
    private val owner get() = scope.playerData
    private val player get() = owner.player
    private var pending: Pair<UUID,()->Unit>? = null
    private var spear: ItemDisplay? = null
    private var saved: ItemStack? = null
    private var savedSlot = 0
    private var at: Location? = null
    private var throwOrigin = Vector()
    private var direction = Vector(0.0,0.0,1.0)
    private var attached: EntityData? = null
    private var offset = Vector()
    private var flying = false
    private var returning = false
    private var age = 0
    private val returnedHits = mutableSetOf<UUID>()
    private var exploded = false
    private val throwCooldown = SpearThrowCooldown()

    fun start() {
        scope.resources.own { spear?.remove(); spear=null; saved=null }
        object : AbilityRunnable(scope) {
            override fun run() { throwCooldown.tick(); tickSpear() }
        }.runTaskTimer(ClassWarPlugin.instance,1,1)
    }
    private fun enemies() = Targeting.select(owner,TargetType.Enemy,player.world)
    private fun valid(target: EntityData) = target.entity.isValid && !target.entity.isDead && Targeting.isEnemy(owner,target)
    fun confirmed(context: DamageContext): Boolean {
        val call = pending ?: return false
        if (call.first != context.target.entity.uniqueId) return false
        pending=null; call.second(); return true
    }
    private fun hit(target: EntityData, damage: Double, after: ()->Unit = {}) {
        val previous = pending
        pending = target.entity.uniqueId to after
        try { target.damage(damage,DamageType.Normal,owner,damagePath=DamagePath.SKILL,secondaryAttack=true) }
        finally { pending=previous }
    }
    private fun vibration(target: EntityData, power: Int) {
        target.getOrCreateStatus(owner) { Vibration() }.apply { increasePower(power); updateDuration(10) }
    }
    private fun shock(target: EntityData) { target.getOrCreateStatus(owner) { Electrocution() }.increasePower(1) }
    fun basicGungnir(target: EntityData) = vibration(target,1)
    fun basicMjolnir(target: EntityData) {
        val resonance = target.getStatus<Resonance>()?.consume() == true
        target.getOrCreateStatus(owner) { Aftermath() }.increasePower(5)
        if (!resonance) return
        SoundApi.play(target.entity.location,Sound.ITEM_TRIDENT_THUNDER,0.35f,1.4f)
        shock(target)
        val from = target.entity.boundingBox.center.toLocation(player.world)
        val ray = from.toVector().subtract(player.location.toVector()).setY(0.0)
        if (ray.lengthSquared()<1e-8) ray.copy(player.eyeLocation.direction)
        ray.normalize()
        bolt(from,from.clone().add(ray.clone().multiply(12.0)))
        enemies().filter { it !== target && CreationGeometry.contact(it.entity.boundingBox,from.toVector(),ray,12.0,0.8)!=null }.forEach(::shock)
    }
    fun throwSpear(): Boolean {
        if (spear != null || saved != null) return false
        val item = player.inventory.itemInMainHand
        if (getWeaponClassId(item) != "gungnir") return false
        if (!throwCooldown.ready) {
            player.sendMiniMessage("<red><bold>[!] 재사용 대기 중입니다.")
            return false
        }
        val start = player.eyeLocation.clone()
        throwOrigin = start.toVector()
        at=start; direction=start.direction.normalize(); flying=true; returning=false; age=0; attached=null
        spear=start.world.spawn(start,ItemDisplay::class.java).apply {
            setItemStack(ItemStack(Material.TRIDENT)); teleportDuration=1
            TemporaryDisplayManager.mark(this,owner.uniqueId)
            alignSpear(this,direction)
        }
        saved=item.clone(); savedSlot=player.inventory.heldItemSlot
        player.inventory.setItem(savedSlot,null)
        throwCooldown.onThrown()
        SoundApi.play(start,Sound.ITEM_TRIDENT_THROW,0.7f,0.8f)
        return true
    }
    fun recall(): Boolean {
        if (saved == null || returning) { player.sendMiniMessage("<red><bold>[!] 회수할 궁니르가 없습니다."); return false }
        attached?.takeIf(::valid)?.let { target -> hit(target,5.0) {
            target.getOrCreateStatus(owner) { Bleeding() }.apply { increasePower(4); updateDuration(10) }
        } }
        attached=null; returning=true; flying=false; returnedHits.clear(); exploded=false
        SoundApi.play(player.location,Sound.ITEM_TRIDENT_RETURN,0.7f,1.1f)
        return true
    }
    private fun recover() {
        val item=saved ?: return
        if (!player.inventory.getItem(savedSlot).let { it == null || it.type.isAir } && player.inventory.firstEmpty()<0) return
        if (player.inventory.getItem(savedSlot).let { it == null || it.type.isAir }) player.inventory.setItem(savedSlot,item)
        else player.inventory.addItem(item)
        saved=null; spear?.remove(); spear=null; at=null; attached=null
        SoundApi.play(player.location,Sound.ITEM_TRIDENT_RETURN,0.5f,1.5f)
    }
    private fun tickSpear() {
        val display=spear ?: return
        val from=at ?: return
        if (!display.isValid || from.world!=player.world) { recover(); return }
        if (!flying && !returning) {
            attached?.takeIf { valid(it) && it.entity.world==from.world }?.let { at=it.entity.location.clone().add(offset) }
            display.teleport(at!!)
            if (CreationGeometry.inRadius(player.boundingBox,at!!.toVector(),1.5)) recover()
            return
        }
        val goal = if (returning) player.eyeLocation.toVector() else enemies().filter(::valid)
            .filter { it.entity.boundingBox.center.clone().subtract(from.toVector()).dot(direction)>0 }
            .minByOrNull { it.entity.boundingBox.center.distanceSquared(from.toVector()) }?.entity?.boundingBox?.center
        if (goal!=null) direction=StarSteering.turn(direction,goal.clone().subtract(from.toVector()),if(returning) 0.3 else 0.015)
        val length=if(returning && goal!=null) {
            val delta=goal.clone().subtract(from.toVector())
            val alignment=if(delta.lengthSquared()>1e-8) direction.dot(delta.clone().normalize()) else 1.0
            minOf(delta.length().coerceAtLeast(0.05),if(alignment<0.95) 0.4 else 2.2)
        } else 1.6
        val wall=if(returning) null else from.world.rayTraceBlocks(from,direction,length)?.hitPosition
        val travel=wall?.distance(from.toVector()) ?: length
        val hits=enemies().filter(::valid).mapNotNull { enemy ->
            CreationGeometry.contact(enemy.entity.boundingBox,from.toVector(),direction,travel,0.15)?.let { Triple(enemy,it,it.distance(from.toVector())) }
        }.sortedBy { it.third }
        var next=from.clone().add(direction.clone().multiply(travel))
        if (returning) {
            hits.filter { returnedHits.add(it.first.entity.uniqueId) }.forEach { (target,_,_) -> hit(target,3.0) {
                if (!exploded) { exploded=true; target.getOrCreateStatus(owner) { VibrationExplosion() }.increasePower(1) }
            } }
            if (CreationGeometry.contact(player.boundingBox,from.toVector(),direction,length,0.3)!=null) { recover(); return }
        } else {
            hits.firstOrNull()?.let { (target,point,_) ->
                hit(target,SpearThrowDamage.calculate(throwOrigin,target.entity.boundingBox)) { vibration(target,3) }
                next=point.toLocation(from.world)
                attached=target; offset=next.toVector().subtract(target.entity.location.toVector()); flying=false
            }
            if (wall!=null || ++age>=100) flying=false
            if (!flying) SoundApi.play(next,Sound.ITEM_TRIDENT_HIT_GROUND,0.65f,0.65f)
        }
        ParticleApi.line(from,next,Particle.END_ROD,spacing=0.25)
        at=next; display.teleport(next)
        alignSpear(display,direction)
    }
    private fun alignSpear(display: ItemDisplay, heading: Vector) {
        display.itemDisplayTransform=ItemDisplay.ItemDisplayTransform.NONE
        display.setRotation(0f,0f)
        display.transformation=SpearPose.create(org.joml.Vector3f(
            heading.x.toFloat(),heading.y.toFloat(),heading.z.toFloat()))
    }
    private fun ground(at: Location): Location? = at.world.rayTraceBlocks(at.clone().add(0.0,1.0,0.0),
        Vector(0.0,-1.0,0.0),5.0,FluidCollisionMode.NEVER,true)?.hitPosition?.toLocation(at.world)?.add(0.0,0.15,0.0)
    fun tesla(): Boolean {
        val origin=ground(player.location) ?: run {
            player.sendMiniMessage("<red><bold>[!] 번개를 방출할 지면이 없습니다."); return false
        }
        val forward=player.location.direction.setY(0.0)
        if(forward.lengthSquared()<1e-8) forward.z=1.0
        forward.normalize()
        val bases=List(5) { forward.clone().rotateAroundY((it-2)*0.18) }
        val rays=MutableList(5) { bases[it].clone() }
        val points=MutableList(5) { origin.clone() }
        val struck=mutableSetOf<UUID>()
        SoundApi.play(origin,Sound.ENTITY_LIGHTNING_BOLT_THUNDER,0.45f,1.4f)
        origin.world.spawnParticle(Particle.ELECTRIC_SPARK,origin,45,0.8,0.2,0.8,0.12)
        origin.world.spawnParticle(Particle.END_ROD,origin,12,0.5,0.15,0.5,0.06)
        object: AbilityRunnable(scope) {
            var frame=0
            override fun run() {
                points.indices.forEach { i ->
                    if(rays[i].lengthSquared()<1e-8) return@forEach
                    val from=points[i]
                    rays[i]=bases[i].clone().rotateAroundY(kotlin.math.sin(frame*0.65+i*1.7)*0.3+Random.nextDouble(-0.08,0.08))
                    val to=ground(from.clone().add(rays[i].clone().multiply(1.5)))
                    if(to==null || kotlin.math.abs(to.y-from.y)>1.25) { rays[i]=Vector(); return@forEach }
                    val delta=to.toVector().subtract(from.toVector())
                    val length=delta.length()
                    val ray=delta.clone().normalize()
                    val wall=from.world.rayTraceBlocks(from,ray,length)?.hitPosition
                    val reach=wall?.distance(from.toVector()) ?: length
                    bolt(from,wall?.toLocation(from.world) ?: to)
                    enemies().filter { CreationGeometry.contact(it.entity.boundingBox,from.toVector(),ray,reach,0.45)!=null && struck.add(it.entity.uniqueId) }
                        .forEach { target -> hit(target,4.0) {
                            val enhanced=target.getStatus<Resonance>()?.consume()==true
                            target.getOrCreateStatus(owner) { Aftermath() }.increasePower(10); shock(target)
                            val impact=target.entity.boundingBox.center.toLocation(target.entity.world)
                            impact.world.spawnParticle(Particle.ELECTRIC_SPARK,impact,24,0.35,0.5,0.35,0.08)
                            impact.world.spawnParticle(Particle.END_ROD,impact,6,0.2,0.35,0.2,0.03)
                            if(enhanced) {
                                val at=target.entity.location
                                at.world.strikeLightningEffect(at)
                                SoundApi.play(at,Sound.ENTITY_LIGHTNING_BOLT_IMPACT,0.45f,0.8f)
                                hit(target,3.0) { shock(target) }
                            }
                        } }
                    points[i]=to
                    if(wall!=null) rays[i]=Vector()
                }
                if(++frame>=24) cancel()
            }
        }.runTaskTimer(ClassWarPlugin.instance,1,1)
        return true
    }
    private fun bolt(from: Location,to: Location) {
        val dust=Particle.DustOptions(Color.fromRGB(65,140,255),1.6f)
        val core=Particle.DustOptions(Color.fromRGB(225,250,255),0.8f)
        val delta=to.toVector().subtract(from.toVector())
        var previous=from
        for(i in 1..6) {
            val next=from.clone().add(delta.clone().multiply(i/6.0))
            if(i<6) next.add(Random.nextDouble(-0.15,0.15),Random.nextDouble(-0.15,0.15),Random.nextDouble(-0.15,0.15))
            ParticleApi.line(previous,next,Particle.ELECTRIC_SPARK,spacing=0.15)
            val segment=next.toVector().subtract(previous.toVector())
            val samples=kotlin.math.ceil(segment.length()/0.18).toInt().coerceIn(1,24)
            for(sample in 0..samples) {
                val point=previous.clone().add(segment.clone().multiply(sample.toDouble()/samples))
                point.world.spawnParticle(Particle.DUST,point,1,0.0,0.0,0.0,0.0,core)
            }
            next.world.spawnParticle(Particle.DUST,next,2,0.06,0.04,0.06,0.0,dust)
            if(i==2 || i==5) {
                val fork=next.clone().add(Random.nextDouble(-0.5,0.5),Random.nextDouble(0.2,0.65),Random.nextDouble(-0.5,0.5))
                ParticleApi.line(next,fork,Particle.ELECTRIC_SPARK,spacing=0.12)
                fork.world.spawnParticle(Particle.END_ROD,fork,1,0.0,0.0,0.0,0.0)
            }
            previous=next
        }
    }
}
