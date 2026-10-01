package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.gameClass.firearm.*
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.WeaponInputHandler
import org.beobma.classWarPlugin.manager.GameClassManager.toWeaponItemStack
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.manager.SkillManager.getSkillId
import org.beobma.classWarPlugin.manager.SkillManager.markSkillItem
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.status.list.Disarm
import org.beobma.classWarPlugin.manager.ItemDescriptionManager
import org.beobma.classWarPlugin.manager.InventoryManager.skillDyeMaterial
import org.bukkit.Sound
import org.bukkit.Particle
import org.bukkit.inventory.ItemStack
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive


class FirearmsMaster : GameClass(), GameStatusHandler, WeaponInputHandler {
    override val classId = "firearmsmaster"
    override val name = "<gray>총기의 달인"
    override val rank = Rank.S
    override val classItemMaterial = Material.WOLF_ARMOR
    private var current = createGun(null)
    private var changing = false
    private var loadoutReady = false
    val activeFirearm: GameClass get() = current
    override val childAbilities get() = listOf(current)
    override val weapon: BaseWeapon get() = current.weapon
    override val skills: List<Skill> get() = if (!loadoutReady) emptyList() else current.skills.filter { it.definitionId !in (current as BorrowableFirearm).reloadSkillIds }

    private fun createGun(previous: String?): GameClass = AbilityCatalog.create(FirearmRoster.next(previous)).also {
        (it as BorrowableFirearm).reloadDisabled = true
        (it as? FirearmClass)?.weaponOwnerId = classId
    }

    override fun onBattleStart() {
        loadoutReady = true
        connectMagazine()
        installLoadout(null)
    }

    override fun onWeaponRightClick(event: PlayerInteractEvent) = AbilityExecution.with(current.abilityScope) {
        if (canUseGun()) (current as WeaponInputHandler).onWeaponRightClick(event) else event.isCancelled = true
    }
    override fun onWeaponInteractEntity(event: PlayerInteractEntityEvent) = AbilityExecution.with(current.abilityScope) {
        if (canUseGun()) (current as WeaponInputHandler).onWeaponInteractEntity(event) else event.isCancelled = true
    }
    override fun onWeaponSwapHand(event: PlayerSwapHandItemsEvent) = AbilityExecution.with(current.abilityScope) {
        if (canUseGun()) (current as WeaponInputHandler).onWeaponSwapHand(event) else event.isCancelled = true
    }

    private fun canUseGun() = abilityScope.started && !abilityScope.isClosed && !abilityScope.suspended &&
        !game.isPaused && !playerStatus.isDead && playerStatus.canAttack && playerStatus.canSkillUse &&
        !playerData.hasStatus<Disarm>() && !changing

    override fun onGameTimePasses() {
        if (current.abilityScope.started && (current as BorrowableFirearm).ammunition == 0) requestChange()
    }

    private fun connectMagazine() {
        (current as BorrowableFirearm).onMagazineEmpty = ::requestChange
    }

    private fun requestChange() {
        if (changing || abilityScope.isClosed) return
        changing = true
        // Finish every pellet/on-hit effect under the old scope before retiring that weapon.
        object : AbilityRunnable(abilityScope) {
            override fun run() {
                val old = current
                if ((old as BorrowableFirearm).ammunition > 0) { changing = false; return }
                old.onMagazineEmpty = null
                player.clearActiveItem()
                AbilityTree.end(listOf(old), EndReason.REMOVED)
                current = createGun(old.classId)
                AbilityTree.bind(listOf(this@FirearmsMaster), playerData)
                connectMagazine()
                installLoadout(old)
                AbilityTree.start(listOf(current))
                changing = false
                sounds.playTo(player, Sound.ITEM_ARMOR_EQUIP_IRON, volume = 0.8f, pitch = 1.4f)
                sounds.playTo(player, Sound.BLOCK_IRON_TRAPDOOR_OPEN, volume = 0.5f, pitch = 1.8f)
                particles.spawn(player.eyeLocation.clone().add(0.0, -0.3, 0.0), Particle.CRIT, count = 10, spread = 0.22)
            }
        }.runTask(ClassWarPlugin.instance)
    }

    private fun installLoadout(old: GameClass?) {
        val inventory = player.inventory
        val key = NamespacedKey(ClassWarPlugin.instance, "borrowed-firearm-owner")
        val owner = abilityScope.instanceId.toString()
        val retiredSkillIds = (old?.skills.orEmpty() + current.skills).map { it.id }.toSet()
        var weaponSlot: Int? = null
        for (slot in 0..40) {
            val item = inventory.getItem(slot) ?: continue
            if (getWeaponClassId(item) == classId) {
                if (weaponSlot == null || slot == inventory.heldItemSlot) weaponSlot = slot
                inventory.setItem(slot, null)
            } else if (getSkillId(item, player.uniqueId) in retiredSkillIds ||
                item.itemMeta?.persistentDataContainer?.get(key, PersistentDataType.STRING) == owner) inventory.setItem(slot, null)
        }
        // Only replace items owned by this composite. Never re-grant starting items or overwrite a partner class.
        val slot = weaponSlot ?: (0..35).firstOrNull { inventory.getItem(it)?.type?.isAir != false }
        val gun = toWeaponItemStack(player)
        if (slot != null) inventory.setItem(slot, gun)
        else player.world.dropItemNaturally(player.location, gun)
        val mini = MiniMessage.miniMessage()
        skills.forEachIndexed { index, skill ->
            val item = ItemStack(skillDyeMaterial(index)).apply {
                itemMeta = itemMeta.apply { displayName(mini.deserialize(skill.name)) }
            }
            val described = ItemDescriptionManager.applyForPlayer(item, player, skill.description, skill.briefDescription,
                ItemDescriptionManager.cooldownLines(skill.cooldown), growthClassId = skill.ownerClass.classId)
            val marked = markSkillItem(described, skill, player.uniqueId)
            marked.itemMeta = marked.itemMeta.apply { persistentDataContainer.set(key, PersistentDataType.STRING, owner) }
            val free = (1..35).firstOrNull { inventory.getItem(it)?.type?.isAir != false }
            if (free != null) inventory.setItem(free, marked)
            else player.world.dropItemNaturally(player.location, marked)
        }
    }

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class Passive : BasePassive() {
        override val name = "<bold>총기의 달인"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>게임 시작 시 무작위 총기 클래스의 무기를 얻고 해당 무기의 최대 {keyword:Bullet} 수 만큼 {keyword:Bullet}을 얻는다.",
            "<gray>{keyword:Bullet}을 모두 소모하면 무기를 버리고 버린 무기와는 다른 무작위 총기 무기를 얻는다.",
            "<gray>총기를 얻은 동안, 해당 클래스의 재장전과 관련된 스킬, 패시브를 제외한 효과를 모두 얻는다."
        )
    }
}
