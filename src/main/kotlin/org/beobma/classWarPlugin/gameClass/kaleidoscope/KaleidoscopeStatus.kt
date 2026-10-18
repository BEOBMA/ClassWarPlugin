package org.beobma.classWarPlugin.gameClass.kaleidoscope

import org.beobma.classWarPlugin.status.StatusAbnormality

/** Shares the standard status action bar; never sends a competing action bar packet. */
internal class KaleidoscopeStatus : StatusAbnormality() {
    override val name = "<light_purple>분광기</light_purple>"
    override val description = listOf("<gray>설치된 분광기·거울 수와 컬라이더스코프의 사용 가능 상태를 표시한다.")
    override val canRemove = false
    override val isClassMechanic = true
    override val showPower = false
    override var power = 1
    private var count = 0
    private var links = 0
    private var domain = false
    private var suppressedSeconds = 0L

    fun synchronize(count: Int, links: Int, domain: Boolean, suppressedTicks: Long) {
        this.count=count.coerceIn(0,SpectralGeometry.PRISM_LIMIT)
        this.links=links.coerceAtLeast(0); this.domain=domain
        suppressedSeconds=(suppressedTicks.coerceAtLeast(0)+19)/20
    }
    override fun actionBarText(): String {
        val slots=(0 until SpectralGeometry.PRISM_LIMIT).joinToString("") {
            if(it<count) "<${SpectralFx.hex(it)}>◆</${SpectralFx.hex(it)}>" else "<dark_gray>◇</dark_gray>"
        }
        val state=when {
            suppressedSeconds>0 -> "<dark_gray>광휘 봉인 <gray>${suppressedSeconds}초</gray></dark_gray>"
            domain -> "<aqua>전역 연결</aqua>"
            else -> "<gray>거울 <white>$links</white></gray>"
        }
        return "$name $slots <dark_gray>·</dark_gray> $state"
    }
}
