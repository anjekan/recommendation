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
        configuration?.let { return it }
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
            ),
        )
        syncPendingEvents()
        return decision
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
}
