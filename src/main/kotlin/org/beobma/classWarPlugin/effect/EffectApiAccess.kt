package org.beobma.classWarPlugin.effect

/**
 * 스킬 계열 클래스에서 효과 API를 짧은 이름으로 사용할 수 있게 한다.
 */
interface EffectApiAccess {
    val sounds: SoundApi
        get() = SoundApi

    val particles: ParticleApi
        get() = ParticleApi
}
