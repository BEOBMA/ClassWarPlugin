package org.beobma.classWarPlugin.keyword

enum class Keyword(val string: String, val description: String? = null) {
    Arrow("<gold><bold>화살</bold><gray>"),
    Invalidity("<dark_gray><bold>무효</bold><gray>"),
    Stealth("<light_purple><bold>은신</bold><gray>"),
    Mana("<blue><bold>마나</bold><gray>"),
    Burn("<red><bold>화상</bold><gray>"),
    Shield("<aqua><bold>보호막</bold><gray>"),
    TrueDamage(
        "<white><bold>고정 피해</bold><gray>",
        "{keyword:TrueDamage}: 어떤 경우에도 피해량이 변하지 않는다."
    ),
    Vibration(
        "<gold><bold>진동</bold><gray>",
        "{keyword:Vibration}: {keyword:VibrationExplosion}이 적용되면 <gold><bold>(진동 수치 x 0.5)</bold><gray> 만큼 {keyword:AbnormalStatusDamage}를 입고 {keyword:Vibration}을 제거한다."
    ),
    VibrationExplosion(
        "<gold><bold>진동 폭발</bold><gray>",
        "{keyword:VibrationExplosion}: <gold><bold>(진동 수치 x 0.5)</bold><gray> 만큼 {keyword:AbnormalStatusDamage}를 입고 {keyword:Vibration}을 제거한다."
    ),
    AbnormalStatusDamage(
        "<green><bold>상태이상 피해</bold><gray>",
        "{keyword:AbnormalStatusDamage}: 각종 피격 시 상호작용이 일어나지 않는다."
    ),
    Gravity("<gold><bold>중력</bold>gray>"),
    SpecialVictoryCard(
        "<yellow><bold>특수승리 카드</bold><gray>",
        "{keyword:SpecialVictoryCard}: 패에 이 카드가 5장 있으면 특수승리한다. 4장 있는 경우 자신의 위치를 모든 대상이 볼 수 있게된다."
    ),
    Untargetability(
        "<dark_gray><bold>대상 지정 불가</bold><gray>",
        "{keyword:Untargetability}: 이미 적용된 효과를 제외하고, 효과의 대상이 되지 않는다."
    ),
    Abyss(
        "<black><bold>심연</bold><gray>",
        "{keyword:Abyss}: 시야가 극도로 좁아지고 치명타 공격을 할 수 없다."
    ),
    Silence(
        "<dark_gray><bold>침묵</bold><gray>",
        "{keyword:Silence}: 스킬을 사용할 수 없다."
    ),
    Exile(
        "<dark_gray><bold>추방</bold><gray>",
        "{keyword:Exile}: 전장과 단절된 공간으로 이동한다. 이 공간은 추방된 대상끼리 공유한다."
    ),
    Bleeding(
        "<dark_red><bold>출혈</bold><gray>",
        "{keyword:Bleeding}: 기본 공격 시 수치 만큼 {keyword:AbnormalStatusDamage}를 입고 수치를 절반으로 만든다."
    ),
    RespiteHealth(
        "<dark_red><bold>유예체력</bold><gray>",
        "{keyword:RespiteHealth}: 일반적인 체력으로 간주되나, 어떤 경로로든 소멸되면 사망한다."
    ),
    Execution(
        "<dark_red><bold>처형</bold><gray>",
        "{keyword:Execution}: 모든 효과를 무시하고 사망한다."
    ),
    Electrocution(
        "<light_purple><bold>감전</bold><gray>",
        "{keyword:Electrocution}: 20초간 <gold><bold>이동 속도가 5% 감소</bold><gray>한다. 지속 시간 도중 {keyword:Electrocution}이 다시 적용되면 {keyword:Electrocution}을 제거하고 2초간 {keyword:Stun}한다."
    ),
    Stun("<yellow><bold>기절</bold><gray>"),
    Snare("<dark_gray><bold>속박</bold><gray>"),
    Brightness(
        "<white><bold>광휘</bold><gray>",
        "{keyword:Brightness}: 수치가 10이 되면 {keyword:Brightness}를 제거하고 2초간 {keyword:Snare}된다."
    ),
    Charge(
        "<blue><bold>충전</bold><gray>",
        "{keyword:Charge}: 웅크려서 충전하고, 특정 스킬 사용 시 소모하여 스킬을 강화한다."
    ),
    Fix("<dark_gray><bold>고정</bold><gray>"),
    Frostbite(
        "<dark_blue><bold>동상</bold><gray>",
        "{keyword:Frostbite}: 5초간 <gold><bold>이동 속도가 (수치 x 5)% 만큼 감소</bold><gray>한다. 수치가 10 이상이면 {keyword:Frostbite}을 제거하고 {keyword:Freezing} 상태가 된다."
    ),
    Freezing(
        "<white><bold>빙결</bold><gray>",
        "{keyword:Freezing}: 3초간 {keyword:Stun}과 동일한 효과를 적용하며, 지속 시간동안 기본 공격 피격 시 {keyword:Freezing} 상태가 해제되고 피해량의 50% 만큼 {keyword:AbnormalStatusDamage}를 입는다."
    ),
    DimensionMarker("<blue><bold>차원 표식</bold><gray>")
}
