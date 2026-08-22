package org.beobma.classWarPlugin.keyword

enum class Keyword(val string: String, val description: String? = null) {
    Arrow("<gold><bold>화살</bold><gray>"),
    Invalidity("<dark_gray><bold>무효</bold><gray>"),
    Stealth(
        "<light_purple><bold>은신</bold><gray>",
        "{keyword:Stealth}: 살아있는 적 플레이어에게 자신의 모습과 장비가 보이지 않으며 대상 지정 스킬의 대상이 되지 않는다."
    ),
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
    Card(
        "<yellow><bold>카드</bold><gray>",
        "{keyword:Card}: 1~10까지의 숫자 카드가 존재한다."
    ),
    Untargetability(
        "<dark_gray><bold>대상 지정 불가</bold><gray>",
        "{keyword:Untargetability}: 이미 적용된 효과를 제외하고, 효과의 대상이 되지 않는다."
    ),
    Abyss(
        "<#9B59FF><bold>심연</bold><gray>",
        "{keyword:Abyss}: 시야가 극도로 좁아지고 치명타 공격을 할 수 없다."
    ),
    Silence(
        "<dark_gray><bold>침묵</bold><gray>",
        "{keyword:Silence}: 스킬을 사용할 수 없다."
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
        "<aqua><bold>동상</bold><gray>",
        "{keyword:Frostbite}: 5초간 <gold><bold>이동 속도가 (수치 x 5)% 만큼 감소</bold><gray>한다. 수치가 10 이상이면 {keyword:Frostbite}을 제거하고 {keyword:Freezing} 상태가 된다."
    ),
    Freezing(
        "<white><bold>빙결</bold><gray>",
        "{keyword:Freezing}: 3초간 {keyword:Stun}과 동일한 효과를 적용하며, 지속 시간동안 기본 공격 피격 시 {keyword:Freezing} 상태가 해제되고 피해량의 50% 만큼 추가 {keyword:AbnormalStatusDamage}를 입는다."
    ),
    DimensionMarker(
        "<blue><bold>차원 표식</bold><gray>",
        "{keyword:DimensionMarker}: 최대 수치는 4이며, 지속 시간이 연장되지 않는다."
    ),
    Erosion(
        "<blue><bold>잠식</bold><gray>",
        "{keyword:Erosion}: 8초간 지속되며, 특정 스킬로 소모된다."
    ),
    Bullet(
        "<gold><bold>탄환</bold><gray>",
        "{keyword:Bullet}: 특정 스킬이나 공격으로 소모된다."
    );

    companion object {
        private val explanationPrefix = "^\\s*\\{keyword:[A-Za-z]+}:".toRegex()
        private val keywordToken = "\\{keyword:([A-Za-z]+)}".toRegex()
        private val keywordsByName by lazy { entries.associateBy { it.name } }
        private val registeredExplanations by lazy { entries.mapNotNull { it.description }.toSet() }

        /** 짧은 키워드 사용 문장이 아니라, 키워드 사전에서 덧붙인 해설 줄인지 판별한다. */
        fun isExplanation(line: String): Boolean =
            line in registeredExplanations || explanationPrefix.containsMatchIn(line)

        /** 설명 본문과 키워드 해설에서 참조한 모든 키워드의 해설을 등장 순서대로 반환한다. */
        fun explanationsFor(lines: List<String>): List<String> {
            val keywords = linkedSetOf<Keyword>()
            val pending = ArrayDeque<Keyword>()

            fun collect(line: String) {
                keywordToken.findAll(line)
                    .mapNotNull { match -> keywordsByName[match.groupValues[1]] }
                    .filter(keywords::add)
                    .forEach(pending::addLast)
            }

            lines.forEach(::collect)
            while (pending.isNotEmpty()) {
                pending.removeFirst().description?.let(::collect)
            }

            return keywords.mapNotNull { it.description }
        }
    }
}
