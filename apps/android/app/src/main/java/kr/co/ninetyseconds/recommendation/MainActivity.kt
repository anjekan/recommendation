package kr.co.ninetyseconds.recommendation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.co.ninetyseconds.recommendation.domain.ProjectConfiguration
import kr.co.ninetyseconds.recommendation.domain.RecommendationDecision
import kr.co.ninetyseconds.recommendation.ui.theme.RecommendationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as RecommendationApplication).container
        setContent { RecommendationApp(container) }
    }
}

private sealed interface AppState {
    data object Loading : AppState
    data class Ready(
        val configuration: ProjectConfiguration,
        val decision: RecommendationDecision? = null,
        val recommending: Boolean = false,
    ) : AppState
    data class Failed(val message: String) : AppState
}

@Composable
fun RecommendationApp(container: AppContainer) {
    var state: AppState by remember { mutableStateOf(AppState.Loading) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(container) {
        state = runCatching { AppState.Ready(container.start()) }
            .getOrElse { AppState.Failed(it.message ?: "프로젝트 설정을 불러오지 못했습니다.") }
    }

    RecommendationTheme {
        RecommendationContent(
            state = state,
            onRecommend = {
                val ready = state as? AppState.Ready ?: return@RecommendationContent
                scope.launch {
                    state = ready.copy(recommending = true)
                    state = runCatching {
                        ready.copy(
                            decision = container.recommend(ready.configuration.emotions.first().code),
                            recommending = false,
                        )
                    }.getOrElse { AppState.Failed(it.message ?: "추천에 실패했습니다.") }
                }
            },
        )
    }
}

@Composable
private fun RecommendationContent(state: AppState, onRecommend: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state) {
                AppState.Loading -> CircularProgressIndicator()
                is AppState.Failed -> {
                    Text("초기화 오류", style = MaterialTheme.typography.headlineMedium)
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
                is AppState.Ready -> {
                    Text(state.configuration.theme.name, style = MaterialTheme.typography.headlineMedium)
                    Text("LOCAL · config v${state.configuration.catalog.configVersion}")
                    Spacer(Modifier.height(24.dp))
                    state.decision?.let {
                        Text("추천 장소", style = MaterialTheme.typography.labelLarge)
                        Text(it.item.title, style = MaterialTheme.typography.headlineSmall)
                        Text("${it.source} · ${it.item.locationId.value}")
                        Spacer(Modifier.height(24.dp))
                    }
                    Button(onClick = onRecommend, enabled = !state.recommending) {
                        Text(if (state.recommending) "추천 중..." else "로컬 추천 테스트")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun RecommendationContentPreview() {
    RecommendationTheme { RecommendationContent(AppState.Loading, onRecommend = {}) }
}
