package kr.co.ninetyseconds.recommendation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.co.ninetyseconds.recommendation.analysis.LegacyEmotionAccumulator
import kr.co.ninetyseconds.recommendation.analysis.LegacyStressCalculator
import kr.co.ninetyseconds.recommendation.analysis.android.*
import kr.co.ninetyseconds.recommendation.domain.*
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
    data class Home(val config: ProjectConfiguration) : AppState
    data class Measuring(val config: ProjectConfiguration) : AppState
    data class Result(val config: ProjectConfiguration, val label: String, val stress: Int, val decision: RecommendationDecision) : AppState
    data class Failed(val message: String, val config: ProjectConfiguration? = null) : AppState
}

@Composable
fun RecommendationApp(container: AppContainer) {
    var state: AppState by remember { mutableStateOf(AppState.Loading) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(container) {
        state = runCatching { AppState.Home(container.start()) }
            .getOrElse { AppState.Failed(it.message ?: "프로젝트 설정을 불러오지 못했습니다.") }
    }
    RecommendationTheme {
        when (val current = state) {
            AppState.Loading -> Centered { CircularProgressIndicator() }
            is AppState.Home -> HomeScreen(current.config) { state = AppState.Measuring(current.config) }
            is AppState.Measuring -> MeasurementScreen(
                current.config,
                onComplete = { label, stress -> scope.launch {
                    state = runCatching {
                        val emotion = current.config.mapAnalysisLabel(label)
                        AppState.Result(current.config, label, stress, container.recommend(emotion, stress))
                    }.getOrElse { AppState.Failed(it.message ?: "추천에 실패했습니다.", current.config) }
                } },
                onCancel = { state = AppState.Home(current.config) },
            )
            is AppState.Result -> ResultScreen(current) { state = AppState.Home(current.config) }
            is AppState.Failed -> Centered {
                Text("처리 오류", style = MaterialTheme.typography.headlineMedium)
                Text(current.message, color = MaterialTheme.colorScheme.error)
                current.config?.let { config -> Button(onClick = { state = AppState.Home(config) }) { Text("처음으로") } }
            }
        }
    }
}

@Composable
private fun HomeScreen(config: ProjectConfiguration, onStart: () -> Unit) = Centered {
    Text(config.theme.name, style = MaterialTheme.typography.headlineMedium)
    Text("LOCAL · config v${config.catalog.configVersion}")
    Spacer(Modifier.height(28.dp))
    Text("카메라로 현재 상태를 측정하고\n혼잡을 분산한 장소를 추천합니다.")
    Spacer(Modifier.height(28.dp))
    Button(onClick = onStart) { Text("측정 시작") }
}

@Composable
private fun MeasurementScreen(config: ProjectConfiguration, onComplete: (String, Int) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    LaunchedEffect(Unit) { if (!permitted) permissionLauncher.launch(Manifest.permission.CAMERA) }
    if (!permitted) {
        Centered {
            Text("측정을 위해 카메라 권한이 필요합니다.")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("카메라 권한 허용") }
            Button(onClick = onCancel) { Text("취소") }
        }
        return
    }

    val accumulator = remember { LegacyEmotionAccumulator() }
    var snapshot by remember { mutableStateOf<MeasurementSnapshot?>(null) }
    var secondsLeft by remember { mutableIntStateOf(MEASUREMENT_SECONDS) }
    LaunchedEffect(snapshot?.emotion) { snapshot?.emotion?.label?.let(accumulator::add) }
    LaunchedEffect(Unit) {
        while (true) {
            while (secondsLeft > 0) { delay(1_000); secondsLeft-- }
            val vital = snapshot?.vital
            if (vital == null) {
                secondsLeft = RETRY_SECONDS
            } else {
                val label = accumulator.result()
                onComplete(label, LegacyStressCalculator.calculate(vital.heartRateBpm, vital.respiratoryRateRpm, label))
                break
            }
        }
    }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            CameraMeasurementPreview(Modifier.fillMaxSize()) { snapshot = it }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (snapshot?.faceDetected == true) "얼굴 감지됨" else "화면 중앙에 얼굴을 맞춰주세요")
                Text("${secondsLeft}초", style = MaterialTheme.typography.headlineLarge)
                snapshot?.vital?.let { Text("심박 ${it.heartRateBpm} · 호흡 ${it.respiratoryRateRpm}") }
                Text(config.theme.name)
                Button(onClick = onCancel) { Text("측정 취소") }
            }
        }
    }
}

@Composable
private fun CameraMeasurementPreview(modifier: Modifier, onSnapshot: (MeasurementSnapshot) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val analyzer = remember {
        MeasurementFrameAnalyzer(
            faceDetector = MediaPipeFaceDetector.create(context),
            emotionClassifier = OnnxEmotionClassifier.create(context),
            listener = { value -> mainExecutor.execute { onSnapshot(value) } },
        )
    }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var disposed = false
        future.addListener({
            val provider = future.get()
            if (disposed) {
                provider.unbindAll()
                return@addListener
            }
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                .also { it.setAnalyzer(analysisExecutor, analyzer) }
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }, mainExecutor)
        onDispose {
            disposed = true
            if (future.isDone) future.get().unbindAll()
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
private fun ResultScreen(result: AppState.Result, onRestart: () -> Unit) = Centered {
    Text("측정 결과", style = MaterialTheme.typography.headlineMedium)
    Text("분석 감정 ${result.label} · 스트레스 ${result.stress}")
    Spacer(Modifier.height(20.dp))
    Text("추천 장소", style = MaterialTheme.typography.labelLarge)
    Text(result.decision.item.title, style = MaterialTheme.typography.headlineSmall)
    Text("${result.decision.source} · ${result.decision.item.locationId.value}")
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRestart) { Text("다시 측정") }
}

@Composable
private fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content)
}

private const val MEASUREMENT_SECONDS = 20
private const val RETRY_SECONDS = 5
