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
import kr.co.ninetyseconds.recommendation.analysis.MeasurementCoordinator
import kr.co.ninetyseconds.recommendation.analysis.MeasurementPhase
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
                        val emotion = current.config.mapAnalysisLabel(label, stress)
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
    Text("카메라로 현재 상태를 측정하고\n감정상태에 어울리는 꽃을 추천합니다.")
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

    val coordinator = remember { MeasurementCoordinator() }
    var snapshot by remember { mutableStateOf<MeasurementSnapshot?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(coordinator.reset()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            val current = snapshot
            progress = coordinator.tick(
                faceDetected = current?.faceDetected == true,
                vital = current?.vital,
                emotionLabel = current?.emotion?.label,
            )
            progress.result?.let {
                onComplete(it.emotionLabel, it.stressScore)
                break
            }
        }
    }

    cameraError?.let { message ->
        Centered {
            Text("카메라를 시작하지 못했습니다.", style = MaterialTheme.typography.headlineSmall)
            Text(message, color = MaterialTheme.colorScheme.error)
            Button(onClick = onCancel) { Text("처음으로") }
        }
        return
    }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            CameraMeasurementPreview(
                modifier = Modifier.fillMaxSize(),
                onSnapshot = { snapshot = it },
                onError = { cameraError = it },
            )
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(progress.phase.message)
                Text("${progress.secondsRemaining}초", style = MaterialTheme.typography.headlineLarge)
                snapshot?.vital?.let { Text("심박 ${it.heartRateBpm} · 호흡 ${it.respiratoryRateRpm}") }
                Text(config.theme.name)
                Button(onClick = onCancel) { Text("측정 취소") }
            }
        }
    }
}

@Composable
private fun CameraMeasurementPreview(
    modifier: Modifier,
    onSnapshot: (MeasurementSnapshot) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val currentSnapshotListener by rememberUpdatedState(onSnapshot)
    val currentErrorListener by rememberUpdatedState(onError)
    val analyzerResult = remember {
        runCatching {
            MeasurementFrameAnalyzer(
                faceDetector = MediaPipeFaceDetector.create(context),
                emotionClassifier = OnnxEmotionClassifier.create(context),
                listener = { value -> mainExecutor.execute { currentSnapshotListener(value) } },
            )
        }
    }
    val analyzer = analyzerResult.getOrNull()
    LaunchedEffect(analyzerResult) {
        analyzerResult.exceptionOrNull()?.let { currentErrorListener(it.message ?: "분석 모델 초기화 오류") }
    }
    if (analyzer == null) return
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var disposed = false
        future.addListener({
            runCatching {
                val provider = future.get()
                if (disposed) {
                    provider.unbindAll()
                    return@runCatching
                }
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            }.onFailure {
                if (!disposed) currentErrorListener(it.message ?: "전면 카메라 연결 오류")
            }
        }, mainExecutor)
        onDispose {
            disposed = true
            if (future.isDone) runCatching { future.get().unbindAll() }
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
    val emotion = result.decision.item.supportedEmotions.firstOrNull()
    val emotionDefinition = result.config.emotions.firstOrNull { it.code == emotion }
    Text("추천 꽃", style = MaterialTheme.typography.labelLarge)
    Text(result.decision.item.title, style = MaterialTheme.typography.headlineSmall)
    emotionDefinition?.let { Text("${it.name} · ${it.message}") }
    Text("${result.decision.source} · 스트레스 ${result.stress}")
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRestart) { Text("다시 측정") }
}

@Composable
private fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content)
}

private val MeasurementPhase.message: String
    get() = when (this) {
        MeasurementPhase.WAITING_FOR_FACE -> "측정 일시 정지 · 화면 중앙에 얼굴을 맞춰주세요"
        MeasurementPhase.MEASURING -> "측정 중 · 움직이지 마세요"
        MeasurementPhase.CALIBRATING -> "신호 보정 중 · 잠시만 기다려주세요"
        MeasurementPhase.COMPLETED -> "측정 완료"
    }
