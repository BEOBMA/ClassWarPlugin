package org.beobma.classWarPlugin.keyword


class Dictionary {
    val dictionary: HashMap<Keyword, String> = hashMapOf(
        Pair(
            Keyword.Vibration, "${Keyword.Vibration.string}: ${Keyword.VibrationExplosion.string}이 적용되면 <gold><bold>(진동 수치 x 0.5)</bold><gray> 만큼 ${Keyword.AbnormalStatusDamage.string}를 입고 ${Keyword.Vibration.string}을 제거한다."
        ),
        Pair(
            Keyword.VibrationExplosion, "${Keyword.VibrationExplosion.string}: <gold><bold>(진동 수치 x 0.5)</bold><gray> 만큼 ${Keyword.AbnormalStatusDamage.string}를 입고 ${Keyword.Vibration.string}을 제거한다."
        ),
        Pair(
            Keyword.AbnormalStatusDamage, "${Keyword.AbnormalStatusDamage.string}: 일반적으로는 피해량이 변하지 않고, 각종 피격 시 상호작용이 일어나지 않는다."
        ),
        Pair(
            Keyword.TrueDamage, "${Keyword.TrueDamage.string}: 어떤 경우에도 피해량이 변하지 않는다."
        ),
        Pair(
            Keyword.SpecialVictoryCard, "${Keyword.SpecialVictoryCard.string}: 패에 이 카드가 5장 있으면 특수승리한다. 4장 있는 경우 자신의 위치를 모든 대상이 볼 수 있게된다."
        ),
        Pair(
            Keyword.Untargetability, "${Keyword.Untargetability.string}: 이미 적용된 효과를 제외하고, 효과의 대상이 되지 않는다."
        ),
        Pair(
            Keyword.Abyss, "${Keyword.Abyss.string}: 시야가 극도로 좁아지고 치명타 공격을 할 수 없다."
        ),
        Pair(
            Keyword.Silence, "${Keyword.Silence.string}: 스킬을 사용할 수 없다."
        ),
        Pair(
            Keyword.Exile, "${Keyword.Exile.string}: 전장과 단절된 공간으로 이동한다. 이 공간은 추방된 대상끼리 공유한다."
        ),
        Pair(
            Keyword.Bleeding, "${Keyword.Bleeding.string}: 기본 공격 시 수치 만큼 ${Keyword.AbnormalStatusDamage.string}를 입고 수치를 절반으로 만든다."
        ),
        Pair(
            Keyword.RespiteHealth, "${Keyword.RespiteHealth.string}: 일반적인 체력으로 간주되나, 어떤 경로로든 소멸되면 사망한다."
        ),
        Pair(
            Keyword.Execution, "${Keyword.Execution.string}: 모든 효과를 무시하고 사망한다."
        ),
        Pair(
            Keyword.Electrocution, "${Keyword.Electrocution.string}: 20초간 <gold><bold>이동 속도가 5% 감소</bold><gray>한다. 지속 시간 도중 ${Keyword.Electrocution.string}이 다시 적용되면 ${Keyword.Electrocution.string}을 제거하고 2초간 ${Keyword.Stun.string}한다."
        ),
        Pair(
            Keyword.Brightness, "${Keyword.Brightness.string}: 수치가 10이 되면 ${Keyword.Brightness.string}를 제거하고 2초간 ${Keyword.Snare.string}된다."
        ),
        Pair(
            Keyword.Charge, "${Keyword.Charge.string}: 웅크려서 충전하고, 특정 스킬 사용 시 소모하여 스킬을 강화한다."
        ),
        Pair(
            Keyword.Frostbite, "${Keyword.Frostbite.string}: 5초간 <gold><bold>이동 속도가 (수치 x 5)% 만큼 감소</bold><gray>한다. 수치가 10 이상이면 ${Keyword.Frostbite.string}을 제거하고 ${Keyword.Freezing.string} 상태가 된다."
        ),
        Pair(
            Keyword.Freezing, "${Keyword.Freezing.string}: 3초간 ${Keyword.Stun.string}과 동일한 효과를 적용하며, 지속 시간동안 기본 공격 피격 시 ${Keyword.Freezing.string} 상태가 해제되고 피해량의 50% 만큼 ${Keyword.AbnormalStatusDamage.string}를 입는다."
        )
    )
}