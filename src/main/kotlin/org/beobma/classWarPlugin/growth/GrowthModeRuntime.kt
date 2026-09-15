package org.beobma.classWarPlugin.growth

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.mob.MobEntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.game.GamePhase
import org.beobma.classWarPlugin.manager.DamageManager
import org.beobma.classWarPlugin.manager.GameManager
import org.beobma.classWarPlugin.manager.MapTransferBorderManager
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager
import org.beobma.classWarPlugin.manager.PlayerManager.refreshClassItemDescriptions
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.*
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

/** All resources belong to one match; no global entity scans in the combat tick. */
class GrowthModeRuntime(val game: Game, val world: World) : AutoCloseable {
    val settings = game.settings.growth
    val sessionId: UUID = UUID.randomUUID()
    val presentation = GrowthPresentation(this)
    val players = game.playerDatas.filterIsInstance<PlayerData>().associate { it.uniqueId to GrowthPlayerState() }.toMutableMap()
    var layout: RegionLayout? = null; private set
    var schedule: RegionSchedule? = null; private set
    private val terrain = GrowthTerrain(game, world)
    private val mini = MiniMessage.miniMessage()
    private val random = Random.Default
    private var closed = false
    private var started = false
    private var seconds = 0
    private var phaseSeconds = 0
    private var finalSeconds = 0
    private var bar: BossBar? = null
    private val waitingForSlot = mutableSetOf<UUID>()
    private val spellReady = mutableMapOf<UUID, Long>()
    private val oldBorder = world.worldBorder.let { BorderSnapshot(it.center.clone(), it.size, it.damageAmount, it.damageBuffer,
        it.warningDistance, it.warningTime) }
    private data class BorderSnapshot(val center: Location, val size: Double, val damage: Double, val buffer: Double,
        val warningDistance: Int, val warningTime: Int)
    private data class Camp(val regionId: Int, val location: Location, val type: EntityType)
    data class MobRecord(val data: MobEntityData, val regionId: Int, val home: Location, val level: Int,
        val event: GrowthEventDefinition? = null, val expires: Int? = null)
    val mobs = mutableMapOf<UUID, MobRecord>()
    private val camps = mutableListOf<Camp>()
    private var spawnWave = 0
    private val events = GrowthEventTracker()
    private data class EventDrop(val entity: Item, val definition: GrowthEventDefinition, val regionId: Int, val expires: Int)
    private val drops = mutableMapOf<UUID, EventDrop>()
    val entityKey get() = NamespacedKey(ClassWarPlugin.instance, "growth-entity")

    fun participants() = game.playerDatas.filterIsInstance<PlayerData>()
    private fun living() = participants().filter { !it.entityStatus.isDead && !it.player.isDead }
    private fun online() = living().filter { it.player.isOnline }
    fun notify(message: String) { participants().filter { it.player.isOnline }.forEach { it.player.sendMessage(mini.deserialize(message)) } }

    fun prepare(count: Int, done: (String?) -> Unit) {
        notify("<yellow>성장 지역 준비 중… ${GrowthSettings.WARNING}")
        terrain.prepare { result, error ->
            if (closed) return@prepare
            if (error != null || result == null) { done(error ?: "지역 생성 실패"); return@prepare }
            layout = result
            // Align the real border with the integer column domain used by the region raster.
            game.roundCenterX = result.surface.originX + result.surface.size / 2.0
            game.roundCenterZ = result.surface.originZ + result.surface.size / 2.0
            if (spawnLocations(count).size != count) { done("지역 안에서 참가자 사이의 최소 거리를 확보하지 못했습니다."); return@prepare }
            buildCamps()
            // Forecast the seeded schedule: fixed events choose a region that is still SAFE at spawn time.
            val forecastMap = RegionLayout(result.surface, result.regions.map { it.copy() }, result.labels, result.seed)
            val forecast = RegionSchedule(forecastMap, settings.warningsPerPeriod, result.seed)
            val reserved = mutableListOf<Location>()
            settings.events.filter { settings.eventsEnabled }.sortedBy { it.phaseIndex }.forEach { event ->
                while (forecast.phaseIndex < event.phaseIndex && forecast.finalRegion == null) forecast.advance()
                var planned = false
                if (forecast.phaseIndex == event.phaseIndex) {
                    val candidates = forecastMap.regions.filter { it.state == RegionState.SAFE &&
                        (event.terrainTags.isEmpty() || it.terrain in event.terrainTags) }
                    for (region in candidates.shuffled(random)) {
                        val loc = List(512) { region.walkable.random(random) }.asSequence().map(::location)
                            .firstOrNull { walkableNow(it) && reserved.none { other -> other.distanceSquared(it) < 16 } } ?: continue
                        events.plan(event, region.id, loc)
                        reserved += loc
                        planned = true
                        break
                    }
                }
                if (!planned) notify("<yellow>${event.id}: 예정 시간의 적합한 안전 위치가 없어 이번 경기에서는 생략됩니다.")
            }
            notify("<green>${result.regions.size}개 지역 준비 완료 <gray>(seed=${result.seed}). /cw growth regions 로 확인하세요.")
            done(null)
        }
    }

    fun spawnLocations(count: Int): List<Location> {
        val map = layout ?: return emptyList()
        val candidates = map.regions.filter { it.state == RegionState.SAFE }.shuffled()
        if (candidates.isEmpty()) return emptyList()
        val found = mutableListOf<Location>()
        val occupied = if (started) online().filter { it.player.world == world }.map { it.player.location } else emptyList()
        for (i in 0 until count) {
            var chosen: Location? = null
            for (region in candidates.drop(i % candidates.size) + candidates.take(i % candidates.size)) {
                for (cell in List(256) { region.walkable.random(random) }) {
                    val loc = location(cell)
                    if (!isSafeLocation(loc) || !walkableNow(loc)) continue
                    val min = game.settings.minimumPlayerDistance * game.settings.minimumPlayerDistance
                    if ((found + occupied).any { it.distanceSquared(loc) < min }) continue
                    chosen = loc; break
                }
                if (chosen != null) break
            }
            if (chosen == null) return emptyList()
            found += chosen
        }
        return found
    }

    private fun location(cell: Int): Location {
        val s = layout!!.surface
        return Location(world, s.originX + cell % s.size + 0.5, s.heights[cell].toDouble(), s.originZ + cell / s.size + 0.5)
    }
    private fun walkableNow(loc: Location): Boolean =
        GrowthTerrain.safeFloor(world.getBlockAt(loc.blockX, loc.blockY - 1, loc.blockZ).type) &&
            GrowthTerrain.clear(world.getBlockAt(loc.blockX, loc.blockY, loc.blockZ).type) &&
            GrowthTerrain.clear(world.getBlockAt(loc.blockX, loc.blockY + 1, loc.blockZ).type)

    fun start() {
        if (started || closed) return
        started = true
        val map = checkNotNull(layout)
        schedule = RegionSchedule(map, settings.warningsPerPeriod, map.seed)
        world.worldBorder.apply {
            setCenter(game.roundCenterX, game.roundCenterZ); size = map.surface.size.toDouble()
            damageAmount = 0.0 // One custom damage source prevents double border damage.
        }
        world.setGameRule(GameRules.ADVANCE_TIME, false)
        bar = BossBar.bossBar(mini.deserialize("<green>성장 모드"), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS)
        game.borderBossBar = bar
        online().forEach { it.player.showBossBar(bar!!); refresh(it); refreshClassItemDescriptions(it) }
        transitionNotice()
        respawnMobs()
        spawnEvents()
        game.tasks.add(object : BukkitRunnable() {
            override fun run() {
                if (closed || game.phase != GamePhase.RUNNING) { cancel(); return }
                presentation.tick()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 2L, 2L))
        game.tasks.add(object : BukkitRunnable() {
            override fun run() {
                if (closed || game.phase != GamePhase.RUNNING) { cancel(); return }
                if (game.isPaused || MapTransferBorderManager.isExpanded(world)) {
                    mobs.values.forEach { it.data.entity.setAI(false) }; return
                }
                tick()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 20L, 20L))
    }

    private fun tick() {
        seconds++
        val clock = schedule ?: return
        if (clock.finalRegion == null) {
            if (++phaseSeconds >= settings.periodSeconds) nextPhase()
        } else {
            finalSeconds++
            val progress = (finalSeconds.toDouble() / settings.finalShrinkSeconds).coerceIn(0.0, 1.0)
            val square = checkNotNull(clock.finalRegion!!.finalSquare)
            val s = layout!!.surface
            val tx = s.originX + square.x + square.width / 2.0
            val tz = s.originZ + square.z + square.depth / 2.0
            world.worldBorder.setCenter(game.roundCenterX + (tx - game.roundCenterX) * progress,
                game.roundCenterZ + (tz - game.roundCenterZ) * progress)
            world.worldBorder.size = s.size.toDouble() +
                (game.settings.borderMinimumSize - s.size) * progress
        }
        val remaining = if (clock.finalRegion == null) settings.periodSeconds - phaseSeconds
            else (settings.finalShrinkSeconds - finalSeconds).coerceAtLeast(0)
        bar?.name(mini.deserialize("<green>${clock.day}일차 ${if (clock.night) "밤" else "낮"} <white>| ${clock.remaining.size}지역 | " +
            if (clock.finalRegion == null) "전환 ${remaining}초" else "최종 자기장 ${remaining}초"))
        bar?.progress((remaining.toFloat() / if (clock.finalRegion == null) settings.periodSeconds else settings.finalShrinkSeconds).coerceIn(0f, 1f))
        for (data in online()) {
            refresh(data)
            if (data.player.world != world) continue
            if (!isSafeLocation(data.player.location)) {
                DamageManager.clearAttributions(listOf(data.uniqueId))
                data.player.damage(settings.forbiddenDamage,
                    DamageSource.builder(DamageType.OUTSIDE_BORDER).build())
            } else if (players.getValue(data.uniqueId).has(GrowthEffect.REGEN) && seconds % 5 == 0) heal(data, maxHealth(data) * 0.02)
            if (closed) return
        }
        val mobTargets = online().map { it.player }.filter { it.world == world && isSafeLocation(it.location) }
        mobs.values.toList().forEach { record ->
            val entity = record.data.entity
            if (!entity.isValid || entity.isDead || record.expires?.let { seconds >= it } == true ||
                layout!!.regions[record.regionId].state == RegionState.FORBIDDEN) {
                removeMob(entity.uniqueId); return@forEach
            }
            entity.setAI(entity is Monster)
            if (entity.world != world || entity.location.distanceSquared(record.home) > 144 ||
                layout!!.at(entity.location.x, entity.location.z)?.id != record.regionId) {
                entity.teleport(record.home); (entity as? Mob)?.target = null
            }
            if (entity is Mob && entity is Monster) entity.target = mobTargets
                .minByOrNull { it.location.distanceSquared(entity.location) }
                ?.takeIf { it.location.distanceSquared(entity.location) < 144 }
        }
        drops.values.toList().filter { seconds >= it.expires ||
            layout!!.regions[it.regionId].state == RegionState.FORBIDDEN || !it.entity.isValid }.forEach {
            events.resolve(it.entity.uniqueId); it.entity.remove(); drops.remove(it.entity.uniqueId)
        }
    }

    fun nextPhase(): Boolean {
        val clock = schedule ?: return false
        if (closed || game.isPaused || clock.finalRegion != null) return false
        clock.advance(); phaseSeconds = 0
        transitionNotice()
        mobs.values.filter { layout!!.regions[it.regionId].state == RegionState.FORBIDDEN }.map { it.data.entity.uniqueId }.forEach(::removeMob)
        if (!clock.night) respawnMobs()
        spawnEvents()
        return true
    }
    private fun transitionNotice() {
        val clock = schedule!!
        world.time = if (clock.night) 14000 else 1000
        notify("<gold>${clock.day}일차 ${if (clock.night) "밤" else "낮"}")
        clock.finalRegion?.let { notify("<red>최종 지역 ${it.name}! 지금부터 자기장이 최소 크기까지 줄어듭니다.") }
    }
    fun isSafeLocation(loc: Location): Boolean {
        if (loc.world != world) return false
        val region = layout?.at(loc.x, loc.z) ?: return false
        if (region.state == RegionState.FORBIDDEN) return false
        return schedule?.finalRegion == null || world.worldBorder.isInside(loc)
    }

    private fun buildCamps() {
        camps.clear()
        if (settings.maximumMobs == 0 || settings.mobsPerRegion == 0) return
        val regionalCamps = layout!!.regions.map { region ->
            val selected = mutableListOf<Location>()
            val regionCamps = mutableListOf<Camp>()
            for (cell in List(512) { region.walkable.random(random) }) {
                if (selected.size >= settings.mobsPerRegion) break
                val loc = location(cell)
                if (selected.any { it.distanceSquared(loc) < 36 } || !walkableNow(loc)) continue
                selected += loc
                val type = when (selected.size % 4) { 1 -> EntityType.COW; 2 -> EntityType.ZOMBIE; 3 -> EntityType.PIG; else -> EntityType.SKELETON }
                regionCamps += Camp(region.id, loc, type)
            }
            regionCamps
        }
        camps += GrowthPopulation.distribute(regionalCamps, settings.maximumMobs)
    }
    fun respawnMobs() {
        if (!started || game.isPaused || closed) return
        // One-shot event monsters survive the ordinary daily camp reset.
        mobs.filterValues { it.event == null }.keys.toList().forEach(::removeMob)
        // Participant arithmetic mean, frozen for the entire day's spawn wave.
        val level = participants().map { players.getValue(it.uniqueId).level }.average().takeIf { it.isFinite() }?.roundToInt()?.coerceAtLeast(1) ?: 1
        val wave = ++spawnWave
        val iterator = camps.iterator()
        game.tasks.add(object : BukkitRunnable() {
            override fun run() {
                if (closed || wave != spawnWave || game.phase != GamePhase.RUNNING) { cancel(); return }
                if (game.isPaused) return
                repeat(4) {
                    if (iterator.hasNext()) spawnCamp(iterator.next(), level)
                }
                if (!iterator.hasNext()) cancel()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L))
    }

    private fun spawnCamp(camp: Camp, level: Int, event: GrowthEventDefinition? = null): MobRecord? {
            if (!isSafeLocation(camp.location) || !walkableNow(camp.location)) return null
            val entity = world.spawnEntity(camp.location, camp.type) as LivingEntity
            entity.persistentDataContainer.set(entityKey, PersistentDataType.STRING, "mob")
            entity.isPersistent = false; entity.removeWhenFarAway = false
            entity.setCanPickupItems(false)
            if (entity is Ageable) entity.setAdult()
            if (entity is Zombie) entity.isBaby = false
            entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0 + level * 5
            entity.health = entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            entity.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = 2.0 + level * 0.4
            entity.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.20
            entity.equipment?.setHelmet(org.bukkit.inventory.ItemStack(Material.CHAINMAIL_HELMET))
            entity.customName(mini.deserialize("<yellow>Lv.$level <white>${if (entity is Monster) "지역 몬스터" else "야생 동물"}"))
            entity.isCustomNameVisible = true
            entity.setAI(entity is Monster)
            val data = MobEntityData(entity, game)
            game.playerDatas.add(data)
            return MobRecord(data, camp.regionId, camp.location, level, event, event?.let { seconds + it.lifetimeSeconds })
                .also { mobs[entity.uniqueId] = it }
    }
    private fun removeMob(id: UUID) {
        val record = mobs.remove(id) ?: return
        events.resolve(id)
        record.data.statusAbnormalitys.toList().forEach { it.remove() }
        StatusAbnormalityManager.unregisterAllTickingStatuses(record.data.statusAbnormalitys)
        record.data.bukkitTasks.forEach { it.cancel() }
        game.playerDatas.remove(record.data)
        DamageManager.clearAttributions(listOf(id))
        record.data.entity.remove()
    }
    fun mobDeath(id: UUID, killerId: UUID?) {
        val record = mobs[id] ?: return
        val killer = living().firstOrNull { it.uniqueId == killerId }
        if (killer != null) {
            val state = players.getValue(killer.uniqueId)
            award(killer, ((settings.mobExperience + record.level * 3) * if (state.has(GrowthEffect.HUNTER)) 1.2 else 1.0).roundToInt())
            val chance = settings.dropChance + state.stat(GrowthStat.LUCK) * 0.001 + if (state.has(GrowthEffect.FORTUNE)) 0.10 else 0.0
            if (record.event != null) grant(killer, record.event.reward)
            else if (random.nextDouble() < chance.coerceIn(0.0, 1.0)) grant(killer, GrowthItems.ordinary.random(random).id)
        }
        removeMob(id)
    }
    fun playerDeath(victim: UUID, killerId: UUID?) {
        spellReady.remove(victim)
        if (victim == killerId || killerId == null || !game.areEnemies(killerId, victim)) return
        living().firstOrNull { it.uniqueId == killerId }?.let { award(it, settings.playerExperience) }
    }
    fun award(data: PlayerData, amount: Int) {
        val state = players[data.uniqueId] ?: return
        val levels = state.gain(amount, settings)
        refresh(data)
        if (levels > 0) {
            data.player.sendMessage(mini.deserialize("<green>Lv.${state.level}! <gold>미배분 ${state.points}점 <gray>레벨업 아이템을 우클릭해 배분하세요."))
            data.player.playSound(data.player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.1f)
            // Private feedback does not reveal stealthed players to their enemies.
            data.player.spawnParticle(Particle.HAPPY_VILLAGER, data.player.location.clone().add(0.0, 1.0, 0.0), 35, 0.5, 0.7, 0.5, 0.0)
            data.player.spawnParticle(Particle.ENCHANT, data.player.location.clone().add(0.0, 1.0, 0.0), 45, 0.5, 0.8, 0.5, 0.4)
        }
    }
    fun grant(data: PlayerData, id: String): Boolean {
        val item = GrowthItems.byId(id) ?: return false
        val state = players[data.uniqueId] ?: return false
        if (state.inventory.add(id)) data.player.sendMessage(mini.deserialize("<gold>[장비 획득] ${item.name} <gray>Shift + F로 장비 보관함 열기"))
        else award(data, settings.mobExperience)
        return true
    }
    fun refresh(data: PlayerData) {
        val s = players[data.uniqueId] ?: return
        if (started && data.player.isOnline && !data.player.isDead && !data.entityStatus.isDead) {
            data.player.level = s.level
            data.player.exp = s.experienceProgress(settings)
            if (GrowthControls.sync(data.player, s, this)) waitingForSlot.remove(data.uniqueId)
            else if (waitingForSlot.add(data.uniqueId)) data.player.sendMessage(mini.deserialize(
                "<yellow>인벤토리가 가득 찼습니다. 빈칸이 생기면 레벨업 아이템이 지급됩니다."))
            presentation.refreshArmor(data)
        }
        data.attributeEffects.setContribution("growth/health", Attribute.MAX_HEALTH,
            (1.0 + (s.level - 1) * settings.healthPerLevel / 20.0) * if (s.has(GrowthEffect.TITAN)) 1.15 else 1.0)
        data.attributeEffects.setContribution("growth/speed", Attribute.MOVEMENT_SPEED,
            1.0 + (s.stat(GrowthStat.AGILITY) * 0.0015).coerceAtMost(0.15) + if (s.has(GrowthEffect.HASTE)) 0.08 else 0.0)
        data.attributeEffects.setContribution("growth/attack", Attribute.ATTACK_SPEED, 1.0 + (s.stat(GrowthStat.AGILITY) * 0.004).coerceAtMost(0.35))
        data.attributeEffects.refresh()
    }
    private fun maxHealth(data: PlayerData) = data.player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
    private fun healthFraction(entity: LivingEntity) =
        entity.health / (entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0).coerceAtLeast(0.001)
    private fun heal(data: PlayerData, amount: Double) {
        if (data.player.isOnline && !data.player.isDead && !data.entityStatus.isDead)
            data.player.health = (data.player.health + amount).coerceAtMost(maxHealth(data))
    }
    fun onSkill(data: PlayerData) { if (players[data.uniqueId]?.has(GrowthEffect.SPELLBLADE) == true) spellReady[data.uniqueId] = game.combatTick + 100 }
    fun beforeDamage(context: DamageContext) {
        val a = players[context.attacker.uniqueId] ?: return
        val target = context.target.entity as? LivingEntity ?: return
        GrowthCombatEquipment.apply(context, a, (context.target as? PlayerData)?.let { players[it.uniqueId] },
            healthFraction(context.attacker.player), healthFraction(target))
        if (a.has(GrowthEffect.EXECUTE) && target.health < (target.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0) * 0.3)
            context.addDamageDealtMultiplier(1.15)
        if (context.path.isBasicAttack && !context.secondaryAttack && a.has(GrowthEffect.SPELLBLADE) &&
            (spellReady[context.attacker.uniqueId] ?: -1) >= game.combatTick && a.trigger("spellblade", game.combatTick, 6)) {
            spellReady.remove(context.attacker.uniqueId); context.addDamageDealtMultiplier(1.25)
        }
        val t = (context.target as? PlayerData)?.let { players[it.uniqueId] } ?: return
        if (t.has(GrowthEffect.WARD)) context.addDamageTakenMultiplier(0.9)
        if (t.has(GrowthEffect.BARRIER) && t.trigger("barrier", game.combatTick, 20)) context.addDamageTakenMultiplier(0.7)
    }
    fun afterDamage(context: DamageContext) {
        val a = players[context.attacker.uniqueId]
        if (a?.has(GrowthEffect.LIFESTEAL) == true && a.trigger("lifesteal", game.combatTick, 1)) heal(context.attacker, (context.damage * 0.06).coerceAtMost(2.0))
        val target = context.target as? PlayerData ?: return
        val t = players[target.uniqueId] ?: return
        if (t.has(GrowthEffect.SECOND_WIND)) afterHitRecovery(target)
    }

    private fun afterHitRecovery(target: PlayerData) {
        game.tasks.add(org.bukkit.Bukkit.getScheduler().runTask(ClassWarPlugin.instance, Runnable {
            val state = players[target.uniqueId] ?: return@Runnable
            if (closed || game.phase != GamePhase.RUNNING || game.isPaused || target.player.isDead || target.entityStatus.isDead) return@Runnable
            if (target.player.health < maxHealth(target) * 0.3 && state.trigger("second-wind", game.combatTick, 30))
                heal(target, maxHealth(target) * 0.08)
        }))
    }
    fun reduceMobDamage(player: Player, event: org.bukkit.event.entity.EntityDamageByEntityEvent) {
        val state = players[player.uniqueId] ?: return
        event.damage *= GrowthCombatEquipment.incoming(state::has, healthFraction(player))
        if (state.has(GrowthEffect.WARD)) event.damage *= 0.9
        if (state.has(GrowthEffect.BARRIER) && state.trigger("barrier", game.combatTick, 20)) event.damage *= 0.7
        if (state.has(GrowthEffect.SECOND_WIND)) participants().firstOrNull { it.uniqueId == player.uniqueId }?.let(::afterHitRecovery)
    }
    fun ownsEntity(id: UUID) = id in mobs || id in drops || presentation.ownsEntity(id)
    private fun spawnEvents() {
        if (!settings.eventsEnabled) return
        val phase = schedule?.phaseIndex ?: return
        for (entry in events.takeDue(phase)) {
            val event = entry.definition
            val region = layout!!.regions.getOrNull(entry.regionId)
            if (region == null || region.state == RegionState.FORBIDDEN) {
                notify("<gray>[이벤트] ${event.id}: 해당 지역 부재 또는 금지로 등장하지 않습니다."); continue
            }
            // Never silently move an objective away from its announced map position.
            val loc = entry.location.clone()
            if (!isSafeLocation(loc) || !walkableNow(loc)) {
                notify("<gray>[이벤트] ${event.id}: 예정 위치가 막혀 등장하지 않습니다."); continue
            }
            val definition = GrowthItems.byId(event.reward)!!
            if (event.mobType != null) {
                val level = participants().map { players.getValue(it.uniqueId).level }.average()
                    .takeIf { it.isFinite() }?.roundToInt()?.coerceAtLeast(1) ?: 1
                val record = spawnCamp(Camp(region.id, loc, event.mobType), level, event) ?: continue
                events.activate(event.id, record.data.entity.uniqueId)
                notify("<gold>[한정 몬스터] ${region.name}에 ${definition.name} 수호자 등장! 처치 시 장비 획득 (${event.lifetimeSeconds}초).")
                continue
            }
            val stack = org.bukkit.inventory.ItemStack(definition.material)
            val item = world.dropItem(loc, stack)
            item.persistentDataContainer.set(entityKey, PersistentDataType.STRING, "event")
            item.isPersistent = false; item.isInvulnerable = true; item.setGravity(false)
            item.velocity = org.bukkit.util.Vector(); item.customName(mini.deserialize("<gold>${definition.name}")); item.isCustomNameVisible = true
            drops[item.uniqueId] = EventDrop(item, event, region.id, seconds + event.lifetimeSeconds)
            events.activate(event.id, item.uniqueId)
            notify("<gold>[한정 이벤트] ${region.name}에 ${definition.name} 등장! ${event.lifetimeSeconds}초 동안 획득 가능합니다.")
        }
    }
    fun claim(item: Item, player: Player): Boolean {
        val drop = drops[item.uniqueId] ?: return false
        val data = online().firstOrNull { it.uniqueId == player.uniqueId } ?: return true
        if (game.isPaused || seconds >= drop.expires || !isSafeLocation(item.location)) return true
        events.resolve(item.uniqueId); drops.remove(item.uniqueId); item.remove(); grant(data, drop.definition.reward); return true
    }
    fun eventMarkers(): List<GrowthEventMarker> {
        if (closed || !settings.eventsEnabled) return emptyList()
        return events.visible(schedule?.phaseIndex ?: 0).mapNotNull { entry ->
            val definition = entry.definition
            val active = entry.state == GrowthEventState.ACTIVE
            val entity = entry.entityId?.let { mobs[it]?.data?.entity ?: drops[it]?.entity }
            val expired = entry.entityId?.let { id -> mobs[id]?.expires ?: drops[id]?.expires }?.let { seconds >= it } == true
            if (active && (entity == null || !entity.isValid || entity.isDead || expired ||
                    layout?.regions?.getOrNull(entry.regionId)?.state == RegionState.FORBIDDEN)) return@mapNotNull null
            val monster = definition.mobType != null
            val name = GrowthItems.byId(definition.reward)!!.name + if (monster) " 수호자" else ""
            val label = if (active) "★ $name · 등장 중" else
                "$name · ${definition.day}일차 ${if (definition.night) "밤" else "낮"} 예정"
            GrowthEventMarker(label, (entity?.location ?: entry.location).clone(), active, monster)
        }
    }

    override fun close() {
        if (closed) return
        closed = true; spawnWave++; terrain.close()
        presentation.close()
        mobs.keys.toList().forEach(::removeMob)
        drops.values.forEach { it.entity.remove() }; drops.clear()
        events.clear()
        participants().forEach { data ->
            if (data.player.isOnline) {
                GrowthControls.remove(data.player)
                bar?.let { data.player.hideBossBar(it) }
                if (data.player.openInventory.topInventory.holder is GrowthMenu) data.player.closeInventory()
            }
            data.attributeEffects.setContribution("growth/health", Attribute.MAX_HEALTH, null)
            data.attributeEffects.setContribution("growth/speed", Attribute.MOVEMENT_SPEED, null)
            data.attributeEffects.setContribution("growth/attack", Attribute.ATTACK_SPEED, null)
        }
        ClassWarPlugin.instance.logger.info("[Growth] match ended: seed=${layout?.seed}, days=${schedule?.day}, levels=${players.values.map { it.level }}")
        players.clear()
    }
    fun restoreBorder() {
        world.worldBorder.apply {
            center = oldBorder.center; size = oldBorder.size; damageAmount = oldBorder.damage; damageBuffer = oldBorder.buffer
            warningDistance = oldBorder.warningDistance; warningTime = oldBorder.warningTime
        }
    }
}
