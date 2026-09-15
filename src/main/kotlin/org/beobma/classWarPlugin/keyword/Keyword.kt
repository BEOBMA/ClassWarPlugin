package org.beobma.classWarPlugin.keyword

enum class Keyword(
    val string: String,
    val description: String? = null,
    /** 간략 설명에서도 조작법·발동 조건 등 플레이에 필수적인 해설을 표시한다. */
    val showDescriptionInBrief: Boolean = false,
) {
    Writing("<gold><bold>작문</bold><gray>",
        "{keyword:Writing}: 제시된 한 줄을 채팅으로 그대로 작성한다. 정답마다 기본 공격 피해 +{g:writer-reward:0.1}, 받는 피해 -1%. 오답마다 기본 공격 피해 -{g:basic:0.2}, 받는 피해 +2%를 누적한다.", true),
    Caduceus("<gold><bold>카두세우스</bold><gray>",
        "{keyword:Caduceus}: 기본 공격 3회 적중 또는 10초마다 9종 중 다른 무기로 변형한다. 손도끼의 우클릭으로 도약 강타를 사용한다.", true),
    Directive("<yellow><bold>지령</bold><gray>",
        "{keyword:Directive}: 완수하면 가하는 피해가 20% 증가하고 받는 피해가 10% 감소하며 다음 단계로 진행한다. 실패하면 같은 단계에 재도전하며 가하는 피해가 5% 감소하고 받는 피해가 5% 증가한다. 가하는 피해는 최소 50%, 받는 피해는 0~150%로 제한한다.", true),
    AgentDamageDealt("<gold><bold>가하는 피해</bold><gray>", "지령의 성공과 실패로 변화하는 피해 배율이다. 최소 50%를 유지한다."),
    AgentDamageTaken("<red><bold>받는 피해</bold><gray>", "지령 완수 시 감소하고 실패 시 증가하는 피해 배율이다. 0~150%로 제한한다."),
    AgentHatchet("<#F0A04B><bold>손도끼</bold><gray>", "적중 시 약하게 밀어낸다. 무기를 우클릭하면 도약하여 내려찍는다."),
    AgentStiletto("<#BFEFFF><bold>스틸레토</bold><gray>", "방어력 20%를 무시하고 배후 공격 시 피해 {g:attack-bonus:1}을 추가한다."),
    AgentBastard("<#FFD166><bold>바스타드 소드</bold><gray>", "사거리가 10% 증가하고 같은 적에게 연속 적중할 때마다 피해 {g:attack-bonus:0.5}를 추가한다."),
    AgentRapier("<#7EE8FA><bold>레이피어</bold><gray>", "피해가 25% 감소하고 공격 속도가 크게 증가한다."),
    AgentHammer("<#D6A878><bold>망치</bold><gray>", "강하게 밀어내고 기절시킨다. 보호막에 두 배의 피해를 입힌다."),
    AgentGreatsword("<#FF916B><bold>대검</bold><gray>", "공격 속도가 감소하고 전방의 적을 휩쓸어 공격한다."),
    AgentLance("<#73CFFF><bold>랜스</bold><gray>", "이동 속도가 {g:speed-bonus:20}% 증가하고 달린 거리에 비례해 추가 피해를 입힌다."),
    AgentWhip("<#E8A0FF><bold>채찍</bold><gray>", "사거리가 300% 증가하고 적을 약하게 끌어당긴다."),
    AgentScythe("<#AD80FF><bold>낫</bold><gray>", "사거리가 50% 감소하고 적을 관통하여 이동한다. 체력이 10% 미만인 적을 처형한다."),
    Arrow("<gold><bold>화살</bold><gray>"),
    Invalidity("<dark_gray><bold>무효</bold><gray>"),
    Stealth(
        "<light_purple><bold>은신</bold><gray>",
        "{keyword:Stealth}: 살아있는 적 플레이어에게 자신의 모습과 장비가 보이지 않으며 대상 지정 스킬의 대상이 되지 않는다.",
    ),
    Mana("<blue><bold>마나</bold><gray>"),
    Burn(
        "<red><bold>화상</bold><gray>",
        "{keyword:Burn}: 지속 시간 동안 몸에 불이 붙어 화염 피해를 입는다.",
    ),
    Shield(
        "<aqua><bold>보호막</bold><gray>",
        "{keyword:Shield}: 피해를 받으면 체력보다 먼저 보호막 수치가 감소한다.",
    ),
    TrueDamage(
        "<white><bold>고정 피해</bold><gray>",
        "{keyword:TrueDamage}: 어떤 경우에도 피해량이 변하지 않는다.",
    ),
    Vibration(
        "<gold><bold>진동</bold><gray>",
        "{keyword:Vibration}: {keyword:VibrationExplosion}이 적용되면 <gold><bold>(진동 수치 x 0.5)</bold><gray> 만큼 {keyword:AbnormalStatusDamage}를 입고 {keyword:Vibration}을 제거한다.",
    ),
    VibrationExplosion(
        "<gold><bold>진동 폭발</bold><gray>",
        "{keyword:VibrationExplosion}: <gold><bold>(진동 수치 x 0.5)</bold><gray> 만큼 {keyword:AbnormalStatusDamage}를 입고 {keyword:Vibration}을 제거한다.",
        showDescriptionInBrief = true,
    ),
    AbnormalStatusDamage(
        "<green><bold>상태이상 피해</bold><gray>",
        "{keyword:AbnormalStatusDamage}: 각종 피격 시 상호작용이 일어나지 않는다.",
    ),
    Gravity("<gold><bold>중력</bold>gray>"),
    Card(
        "<yellow><bold>카드</bold><gray>",
        "{keyword:Card}: 1~10까지의 숫자 카드가 존재한다.",
    ),
    Untargetability(
        "<dark_gray><bold>대상 지정 불가</bold><gray>",
        "{keyword:Untargetability}: 이미 적용된 효과를 제외하고, 효과의 대상이 되지 않는다.",
    ),
    Abyss(
        "<#9B59FF><bold>심연</bold><gray>",
        "{keyword:Abyss}: 시야가 극도로 좁아지고 치명타 공격을 할 수 없다.",
    ),
    Silence(
        "<dark_gray><bold>침묵</bold><gray>",
        "{keyword:Silence}: 스킬을 사용할 수 없다.",
    ),
    Disarm(
        "<dark_gray><bold>무장해제</bold><gray>",
        "{keyword:Disarm}: 기본공격을 할 수 없다.",
    ),
    Bleeding(
        "<dark_red><bold>출혈</bold><gray>",
        "{keyword:Bleeding}: 기본 공격 시 수치 만큼 {keyword:AbnormalStatusDamage}를 입고 수치를 절반으로 만든다.",
        showDescriptionInBrief = true,
    ),
    RespiteHealth(
        "<dark_red><bold>유예체력</bold><gray>",
        "{keyword:RespiteHealth}: 일반적인 체력으로 간주되나, 어떤 경로로든 소멸되면 사망한다.",
        showDescriptionInBrief = true,
    ),
    Execution(
        "<dark_red><bold>처형</bold><gray>",
        "{keyword:Execution}: 모든 효과를 무시하고 사망한다.",
    ),
    Electrocution(
        "<light_purple><bold>감전</bold><gray>",
        "{keyword:Electrocution}: 20초간 <gold><bold>이동 속도가 5% 감소</bold><gray>한다. 지속 시간 도중 {keyword:Electrocution}이 다시 적용되면 {keyword:Electrocution}을 제거하고 {g:duration:2}초간 {keyword:Stun}한다.",
        showDescriptionInBrief = true,
    ),
    Stun(
        "<yellow><bold>기절</bold><gray>",
        "{keyword:Stun}: 이동, 기본 공격과 스킬 사용이 불가능하다.",
    ),
    Snare(
        "<dark_gray><bold>속박</bold><gray>",
        "{keyword:Snare}: 위치를 이동할 수 없지만 시야 회전과 공격, 스킬 사용은 가능하다.",
    ),
    Brightness(
        "<white><bold>광휘</bold><gray>",
        "{keyword:Brightness}: 수치가 5가 되면 {keyword:Brightness}를 제거하고 {g:duration:2}초간 {keyword:Snare}된다.",
        showDescriptionInBrief = true,
    ),
    Radiation(
        "<white><bold>발광</bold><gray>",
        "{keyword:Radiation}: 주변에 있는 플레이어에게 위치가 드러난다.",
    ),
    Enchantment(
        "<bold>매혹</bold><gray>",
        "{keyword:Enchantment}: {keyword:Stun}과 동일한 효과를 적용하며, 지속 시간동안 매혹을 부여한 플레이어에게로 이동한다.",
    ),
    Charge(
        "<blue><bold>충전</bold><gray>",
        "{keyword:Charge}: 웅크려서 충전하고, 특정 스킬 사용 시 소모하여 스킬을 강화한다.",
        showDescriptionInBrief = true,
    ),
    Fix(
        "<dark_gray><bold>고정</bold><gray>",
        "{keyword:Fix}: 이동과 관련된 스킬을 사용할 수 없다.",
    ),
    Frostbite(
        "<aqua><bold>동상</bold><gray>",
        "{keyword:Frostbite}: {g:duration:5}초간 <gold><bold>이동 속도가 (수치 x 5)% 만큼 감소</bold><gray>한다. 수치가 10 이상이면 {keyword:Frostbite}을 제거하고 {keyword:Freezing} 상태가 된다.",
        showDescriptionInBrief = true,
    ),
    Freezing(
        "<white><bold>빙결</bold><gray>",
        "{keyword:Freezing}: {g:duration:3}초간 {keyword:Stun}과 동일한 효과를 적용하며, 지속 시간동안 기본 공격 피격 시 {keyword:Freezing} 상태가 해제되고 피해량의 50% 만큼 추가 {keyword:AbnormalStatusDamage}를 입는다.",
        showDescriptionInBrief = true,
    ),
    DimensionMarker(
        "<blue><bold>차원 표식</bold><gray>",
        "{keyword:DimensionMarker}: 최대 수치는 4이며, 지속 시간이 연장되지 않는다.",
        showDescriptionInBrief = true,
    ),
    Erosion(
        "<blue><bold>잠식</bold><gray>",
        "{keyword:Erosion}: {g:duration:8}초간 지속되며, 특정 스킬로 소모된다.",
    ),
    Bullet(
        "<gold><bold>탄환</bold><gray>",
        "{keyword:Bullet}: 특정 스킬이나 공격으로 소모된다.",
    ),
    FreikugelBullet(
        "<gold><bold>마탄환</bold><gray>",
        "{keyword:FreikugelBullet}: {keyword:Bullet}으로 간주되며, 특정 스킬이나 공격으로 소모된다.",
    ),
    AccelerationBullet(
        "<gold><bold>가속탄</bold><gray>",
        "{keyword:AccelerationBullet}: {keyword:Bullet}으로 간주되며, 특정 스킬이나 공격으로 소모된다. (최대값 6)",
        showDescriptionInBrief = true,
    ),
    Foresight(
        "<aqua><bold>예지안</bold><gray>",
        "{keyword:Foresight}: 최대 {g:feature/foresight:30}. 피격 시 3, 적의 행동 예지 시 2를 소모한다. 10초간 전투하지 않으면 초당 1 회복한다.",
        showDescriptionInBrief = true,
    ),
    Acceleration(
        "<yellow><bold>가속</bold><gray>",
        "{keyword:Acceleration}: 최대 5. 중첩당 이동 속도와 공격 속도가 {g:speed-bonus:4}% 증가한다. 같은 적 적중으로 유지하며 6초마다 최대 1중첩을 얻는다. 다른 적을 공격하거나 같은 적에게 4초간 피해를 주지 않으면 초기화된다.",
        showDescriptionInBrief = true,
    ),
    Disposal(
        "<red><bold>처분</bold><gray>",
        "{keyword:Disposal}: 최초 사용 후 10초 안에 최대 5단계까지 이어지는 연속기. 마지막 일격을 사용하거나 제한 시간이 끝나면 종료된다.",
        showDescriptionInBrief = true,
    ),
    PhotographyStack(
        "<gold><bold>촬영 스택</bold><gray>",
        "{keyword:PhotographyStack}: 종군기자가 촬영을 완료하면 1 얻으며, 최대 3스택에서 방송 상태가 된다.",
        showDescriptionInBrief = true,
    ),
    Checkpoint(
        "<aqua><bold>체크포인트</bold><gray>",
        "{keyword:Checkpoint}: 저장된 위치와 체력으로 되돌아갈 수 있으며 지속시간 종료 시 사라진다.",
        showDescriptionInBrief = true,
    ),
    TimePhase(
        "<yellow><bold>시간대</bold><gray>",
        "{keyword:TimePhase}: 시계공의 현재 시간대이며 남은 시간이 끝나면 다음 시간대로 변경된다.",
    ),
    Invincibility(
        "<yellow><bold>무적</bold><gray>",
        "{keyword:Invincibility}: 어떠한 방법으로도 피해를 받지 않는다.",
    );

    fun requireDescription(): String = requireNotNull(description) {
        "Keyword '$name'에 설명이 등록되지 않았습니다."
    }

    /** 명령어 검색과 탭 완성에 사용하는 서식 없는 게임 내 표시명. */
    val displayName: String
        get() = miniMessageTag.replace(string, "")

    companion object {
        private val miniMessageTag = "<[^>]+>".toRegex()
        private val explanationPrefix = "^\\s*\\{keyword:[A-Za-z]+}:".toRegex()
        private val keywordToken = "\\{keyword:([A-Za-z]+)}".toRegex()
        private val keywordsByName by lazy { entries.associateBy { it.name } }
        private val registeredExplanations by lazy { entries.mapNotNull { it.description }.toSet() }

        /** 플레이어가 키워드 사전에서 조회할 수 있는 키워드 목록. */
        val describedEntries: List<Keyword> by lazy { entries.filter { it.description != null } }

        /** MiniMessage 서식을 제거한 게임 내 한글 표시명으로 키워드를 찾는다. */
        fun find(query: String): Keyword? = describedEntries.firstOrNull {
            it.displayName.equals(query.trim(), ignoreCase = true)
        }

        /** 짧은 키워드 사용 문장이 아니라, 키워드 사전에서 덧붙인 해설 줄인지 판별한다. */
        fun isExplanation(line: String): Boolean =
            line in registeredExplanations || explanationPrefix.containsMatchIn(line)

        /** 설명 본문과 키워드 해설에서 참조한 모든 키워드의 해설을 등장 순서대로 반환한다. */
        fun explanationsFor(lines: List<String>): List<String> =
            collectKeywords(lines).mapNotNull(Keyword::description)

        /** 간략 모드에서도 반드시 알아야 하는 조작법·발동 조건 해설만 반환한다. */
        fun briefExplanationsFor(lines: List<String>): List<String> =
            collectKeywords(lines)
                .filter(Keyword::showDescriptionInBrief)
                .mapNotNull(Keyword::description)

        private fun collectKeywords(lines: List<String>): Set<Keyword> {
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

            return keywords
        }
    }
}
