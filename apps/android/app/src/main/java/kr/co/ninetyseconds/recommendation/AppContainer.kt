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
import kr.co.ninetyseconds.recommendation.domain.EmotionCode
import kr.co.ninetyseconds.recommendation.domain.EmotionProfile
import kr.co.ninetyseconds.recommendation.domain.EmotionScore
import kr.co.ninetyseconds.recommendation.domain.ProjectConfiguration
import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.domain.RecommendationRequest
import kr.co.ninetyseconds.recommendation.domain.SessionId
import kr.co.ninetyseconds.recommendation.domain.RuntimeMode

class AppContainer(
    private val context: Context,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val localData = LocalDataStore.create(context)
    private val importer = ProjectConfigImporter(BuildConfig.VERSION_CODE)
    private val localEngine = LocalRecommendationEngine(localData.projectCatalog, localData.recommendationEvents, clock)
    private val remoteEngine = HttpRecommendationEngine(BuildConfig.RECOMMENDATION_BASE_URL, BuildConfig.KIOSK_KEY)
    private val runtimeEngine = RuntimeRecommendationEngine(RuntimeModeProvider { RuntimeMode.HYBRID }, localEngine, remoteEngine)
    private val recommendUseCase = Recommend(runtimeEngine, localData.recommendationEvents)
    private val sessionId = SessionId(UUID.randomUUID().toString())
    private var configuration: ProjectConfiguration? = null

    suspend fun start(preferredLanguage: String = "ko"): ProjectConfiguration {
        configuration?.let { return it }
        val json = context.assets.open(DEFAULT_CONFIG_ASSET).bufferedReader().use { it.readText() }
        val imported = importer.import(json, preferredLanguage)
        localData.projectCatalog.replace(imported.catalog)
        configuration = imported
        return imported
    }

    suspend fun recommend(emotion: EmotionCode, stressScore: Int = 0): RecommendationDecision {
        val config = configuration ?: start()
        return recommendUseCase(
            RecommendationRequest(
                requestId = UUID.randomUUID().toString(),
                projectId = config.catalog.projectId,
                sessionId = sessionId,
                emotionProfile = EmotionProfile(listOf(EmotionScore(emotion, 1.0))),
                requestedAt = Instant.now(clock),
                kioskId = DEFAULT_KIOSK_ID,
                stressScore = stressScore,
                language = config.selectedLanguage,
            ),
        )
    }

    private companion object {
        const val DEFAULT_CONFIG_ASSET = "taean-flower-project-config.json"
        const val DEFAULT_KIOSK_ID = "LOCAL-KIOSK"
    }
}
