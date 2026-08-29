package org.beobma.classWarPlugin.event

import org.beobma.classWarPlugin.skill.SkillContext
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

/**
 * 기본 사용 조건을 통과한 뒤 실제 스킬 효과가 실행되기 전에 발생하는 취소 가능 이벤트다.
 * 취소 상태는 [context]에 직접 반영되며 처리기는 재사용 대기시간도 같은 컨텍스트에서 조정할 수 있다.
 */
class PlayerSkillUseEvent(
    val context: SkillContext,
) : Event(), Cancellable {
    val playerData get() = context.playerData
    val skill get() = context.skill
    val clickedItem: ItemStack get() = context.clickedItem
    val isToggle: Boolean get() = context.isToggle

    override fun isCancelled(): Boolean {
        return context.isCancelled
    }

    override fun setCancelled(cancel: Boolean) {
        context.isCancelled = cancel
    }

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }
}
