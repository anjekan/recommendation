package kr.co.ninetyseconds.recommendation

import android.content.Context
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kr.co.ninetyseconds.recommendation.application.LocalRecommendationEngine
import kr.co.ninetyseconds.recommendation.application.Recommend
import kr.co.ninetyseconds.recommendation.application.RuntimeModeProvider
import kr.co.ninetyseconds.recommendation.application.RuntimeRecommendationEngine
import kr.co.ninetyseconds.recommendation.data.config.ProjectConfigImporter
import kr.co.ninetyseconds.recommendation.data.local.LocalDataStore
import kr.co.ninetyseconds.recommendation.data.remote.HttpRecommendationEngine
import kr.co.ninetyseconds.recommendation.data.remote.HttpRecommendationEventSync
import kr.co.ninetyseconds.recommendation.domain.EmotionCode
import kr.co.ninetyseconds.recommendation.domain.ConsentStatus
import kr.co.ninetyseconds.recommendation.domain.EmotionProfile
import kr.co.ninetyseconds.recommendation.domain.EmotionScore
import kr.co.ninetyseconds.recommendation.domain.ProjectConfiguration
import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.domain.RecommendationRequest
import kr.co.ninetyseconds.recommendation.domain.SessionId
import kr.co.ninetyseconds.recommendation.domain.RuntimeMode
import kr.co.ninetyseconds.recommendation.domain.ParticipantProfile
import kr.co.ninetyseconds.recommendation.domain.DecisionSource
import kr.co.ninetyseconds.recommendation.domain.JourneyLocation
import kr.co.ninetyseconds.recommendation.domain.JourneyStop
import kr.co.ninetyseconds.recommendation.domain.LocationId
import kr.co.ninetyseconds.recommendation.domain.RecommendationItem
import kr.co.ninetyseconds.recommendation.domain.RecommendationItemId
import kr.co.ninetyseconds.recommendation.domain.ports.RecommendationEngine

class AppContainer(
    private val context: Context,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val localData = LocalDataStore.create(context)
    private val runtimeSettings = RuntimeSettingsStore(context)
    private val importer = ProjectConfigImporter(BuildConfig.VERSION_CODE)
    private val localEngine = LocalRecommendationEngine(localData.projectCatalog, localData.recommendationEvents, clock)
    private val remoteEngine = RecommendationEngine { request ->
        val settings = runtimeSettings.load()
        HttpRecommendationEngine(settings.serverBaseUrl, settings.kioskKey).recommend(request)
    }
    private val runtimeEngine = RuntimeRecommendationEngine(
        RuntimeModeProvider { runtimeSettings.load().mode },
        localEngine,
        remoteEngine,
    )
    private val recommendUseCase = Recommend(
        runtimeEngine,
        localData.recommendationEvents,
        localData.recommendationEvents,
    )
    private val sessionId = SessionId(UUID.randomUUID().toString())
    private var configuration: ProjectConfiguration? = null

    suspend fun start(preferredLanguage: String = "ko"): ProjectConfiguration {
        configuration?.takeIf { it.selectedLanguage == preferredLanguage }?.let { return it }
        val json = context.assets.open(runtimeSettings.load().projectConfigAsset).bufferedReader().use { it.readText() }
        val imported = importer.import(json, preferredLanguage)
        localData.projectCatalog.replace(imported.catalog)
        configuration = imported
        syncPendingEvents()
        return imported
    }

    fun settings(): RuntimeSettings = runtimeSettings.load()

    suspend fun updateSettings(settings: RuntimeSettings): ProjectConfiguration {
        runtimeSettings.save(settings)
        configuration = null
        return start()
    }

    suspend fun recommend(
        emotion: EmotionCode,
        stressScore: Int = 0,
        consentStatus: ConsentStatus = ConsentStatus.NOT_ASKED,
        participant: ParticipantProfile? = null,
    ): RecommendationDecision {
        val config = configuration ?: start()
        val journeySenseCodes = if (config.catalog.projectId.value.contains("UIRYEONG", ignoreCase = true)) {
            richJourneySenseCodes(emotion.value)
        } else {
            emptyList()
        }
        val decision = recommendUseCase(
            RecommendationRequest(
                requestId = UUID.randomUUID().toString(),
                projectId = config.catalog.projectId,
                sessionId = sessionId,
                emotionProfile = EmotionProfile(listOf(EmotionScore(emotion, 1.0))),
                requestedAt = Instant.now(clock),
                kioskId = runtimeSettings.load().kioskId,
                stressScore = stressScore,
                language = config.selectedLanguage,
                consentStatus = consentStatus,
                participant = participant,
                conditionCode = emotion.value,
                journeySenseCodes = journeySenseCodes,
            ),
        )
        syncPendingEvents()
        return decision
    }

    suspend fun demoRecommend(emotion: EmotionCode, stressScore: Int = 24): RecommendationDecision {
        val config = configuration ?: start()
        if (config.catalog.items.isEmpty() && config.catalog.projectId.value.contains("UIRYEONG", ignoreCase = true)) {
            fun item(id: String, title: String) = RecommendationItem(
                id = RecommendationItemId(id),
                locationId = LocationId(id),
                title = title,
                imageRef = null,
                supportedEmotions = setOf(emotion),
            )
            val themeHall = item("RICH_THEME_HALL", "부자1번지 상설 주제관")
            val kidzania = item("RICH_KIDZANIA", "리치 키자니아 직업체험")
            val snackZone = item("SNACK_ZONE", "리치 스낵존")
            return RecommendationDecision(
                requestId = UUID.randomUUID().toString(),
                item = themeHall,
                source = DecisionSource.LOCAL,
                decidedAt = Instant.now(clock),
                journey = listOf(
                    JourneyStop(1, "INSIGHT", themeHall, JourneyLocation("RICH_THEME_HALL", themeHall.title, 83.8, 41.2)),
                    JourneyStop(2, "ACTION", kidzania, JourneyLocation("RICH_KIDZANIA", kidzania.title, 51.1, 27.4)),
                    JourneyStop(3, "TASTE", snackZone, JourneyLocation("SNACK_ZONE", snackZone.title, 37.5, 42.7)),
                ),
            )
        }
        return localEngine.recommend(
            RecommendationRequest(
                requestId = UUID.randomUUID().toString(),
                projectId = config.catalog.projectId,
                sessionId = sessionId,
                emotionProfile = EmotionProfile(listOf(EmotionScore(emotion, 1.0))),
                requestedAt = Instant.now(clock),
                kioskId = "DEMO-KIOSK",
                stressScore = stressScore,
                language = config.selectedLanguage,
                consentStatus = ConsentStatus.DECLINED,
                participant = null,
            ),
        )
    }

    private suspend fun syncPendingEvents() {
        val settings = runtimeSettings.load()
        if (settings.mode == RuntimeMode.LOCAL) return
        runCatching {
            val pending = localData.recommendationEvents.pending(limit = 100)
            val accepted = HttpRecommendationEventSync(settings.serverBaseUrl, settings.kioskKey).sync(pending)
            localData.recommendationEvents.markSynced(accepted)
        }
    }

    private fun richJourneySenseCodes(conditionCode: String): List<String> = when (conditionCode) {
        "JOY" -> listOf("ACTION", "TASTE", "INSIGHT")
        "EXCITED" -> listOf("ACTION", "LISTENING", "TASTE")
        "THRILL" -> listOf("INSIGHT", "ACTION", "TASTE")
        "INTEREST" -> listOf("INSIGHT", "INTUITION", "TASTE")
        "TENSION" -> listOf("SCENT", "INSIGHT", "TASTE")
        "HEAVINESS" -> listOf("SCENT", "TASTE", "INSIGHT")
        "LOW_ENERGY", "DROWSY" -> listOf("TASTE", "INSIGHT", "SCENT")
        "LOOSE" -> listOf("INSIGHT", "TASTE", "SCENT")
        "COMFORT" -> listOf("INSIGHT", "TASTE", "ACTION")
        "STABLE" -> listOf("INSIGHT", "LISTENING", "TASTE")
        "LEISURE" -> listOf("ACTION", "LISTENING", "TASTE")
        else -> emptyList()
    }
}
