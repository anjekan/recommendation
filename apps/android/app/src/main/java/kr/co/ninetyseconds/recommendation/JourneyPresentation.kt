package kr.co.ninetyseconds.recommendation

import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision

internal data class JourneyPresentationModel(
    val senseCodes: List<String>,
    val stopLabels: List<String>,
    val expectedStopCount: Int,
) {
    val actualStopCount: Int = stopLabels.size

    fun operationNotice(language: String?): String? =
        journeyOperationNotice(actualStopCount, expectedStopCount, language)
}

internal fun journeyOperationNotice(actualStopCount: Int, expectedStopCount: Int, language: String?): String? {
    if (actualStopCount >= expectedStopCount) return null
    return when (language?.lowercase()) {
        "en" -> "Due to current operations, only $actualStopCount location${if (actualStopCount == 1) " is" else "s are"} available."
        "zh" -> "根据当前运营情况，仅为您推荐${actualStopCount}个地点。"
        "ja" -> "現在の運営状況により、${actualStopCount}か所のみご案内します。"
        else -> "현재 운영 상황에 따라 ${actualStopCount}개 장소만 안내합니다."
    }
}

internal fun RecommendationDecision.toJourneyPresentation(): JourneyPresentationModel {
    val orderedJourney = journey.sortedBy { it.order }
    val labels = if (orderedJourney.isEmpty()) {
        listOf("1. ${item.title}")
    } else {
        orderedJourney.mapIndexed { index, stop -> "${index + 1}. ${stop.item.title}" }
    }
    return JourneyPresentationModel(
        senseCodes = orderedJourney.map { it.senseCode },
        stopLabels = labels,
        expectedStopCount = expectedJourneyStopCount,
    )
}
