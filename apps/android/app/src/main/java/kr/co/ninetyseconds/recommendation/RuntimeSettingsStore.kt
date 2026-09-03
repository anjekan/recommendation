package kr.co.ninetyseconds.recommendation

import android.content.Context
import kr.co.ninetyseconds.recommendation.domain.RuntimeMode

data class RuntimeSettings(
    val projectConfigAsset: String,
    val mode: RuntimeMode,
    val serverBaseUrl: String,
    val kioskId: String,
    val kioskKey: String,
    val demoMode: Boolean = false,
) {
    init {
        require(projectConfigAsset.isNotBlank()) { "Project configuration asset cannot be blank" }
        require(serverBaseUrl.startsWith("http://") || serverBaseUrl.startsWith("https://")) {
            "Server address must use HTTP(S)"
        }
        require(kioskId.isNotBlank()) { "Kiosk id cannot be blank" }
        require(kioskKey.isNotBlank()) { "Kiosk key cannot be blank" }
    }
}

class RuntimeSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("runtime-settings", Context.MODE_PRIVATE)

    fun load(): RuntimeSettings = RuntimeSettings(
        projectConfigAsset = preferences.getString(PROJECT_ASSET, null) ?: DEFAULT_PROJECT_ASSET,
        mode = preferences.getString(MODE, null)?.let(RuntimeMode::valueOf) ?: RuntimeMode.HYBRID,
        serverBaseUrl = preferences.getString(SERVER_BASE_URL, null) ?: BuildConfig.RECOMMENDATION_BASE_URL,
        kioskId = preferences.getString(KIOSK_ID, null) ?: DEFAULT_KIOSK_ID,
        kioskKey = preferences.getString(KIOSK_KEY, null) ?: BuildConfig.KIOSK_KEY,
        demoMode = preferences.getBoolean(DEMO_MODE, false),
    )

    fun save(settings: RuntimeSettings) {
        preferences.edit()
            .putString(PROJECT_ASSET, settings.projectConfigAsset)
            .putString(MODE, settings.mode.name)
            .putString(SERVER_BASE_URL, settings.serverBaseUrl.trimEnd('/'))
            .putString(KIOSK_ID, settings.kioskId)
            .putString(KIOSK_KEY, settings.kioskKey)
            .putBoolean(DEMO_MODE, settings.demoMode)
            .apply()
    }

    private companion object {
        const val PROJECT_ASSET = "project-config-asset"
        const val MODE = "runtime-mode"
        const val SERVER_BASE_URL = "server-base-url"
        const val KIOSK_ID = "kiosk-id"
        const val KIOSK_KEY = "kiosk-key"
        const val DEMO_MODE = "demo-mode"
        const val DEFAULT_PROJECT_ASSET = "taean-flower-project-config.json"
        const val DEFAULT_KIOSK_ID = "LOCAL-KIOSK"
    }
}
