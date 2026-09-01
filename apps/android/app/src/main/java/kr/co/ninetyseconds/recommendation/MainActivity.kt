package kr.co.ninetyseconds.recommendation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.Uri
import android.widget.ImageView
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
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clipToBounds
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
    data class Settings(val config: ProjectConfiguration, val settings: RuntimeSettings) : AppState
    data class Consent(val config: ProjectConfiguration) : AppState
    data class Measuring(
        val config: ProjectConfiguration,
        val consentStatus: ConsentStatus,
        val participant: ParticipantProfile?,
    ) : AppState
    data class Analyzing(val config: ProjectConfiguration) : AppState
    data class Result(val config: ProjectConfiguration, val label: String, val stress: Int, val decision: RecommendationDecision) : AppState
    data class MapGuide(val config: ProjectConfiguration, val decision: RecommendationDecision) : AppState
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
            is AppState.Home -> HomeScreen(
                current.config,
                container.settings(),
                onStart = { state = AppState.Consent(current.config) },
                onSettings = { state = AppState.Settings(current.config, container.settings()) },
            )
            is AppState.Settings -> RuntimeSettingsScreen(
                initial = current.settings,
                onSave = { settings -> scope.launch {
                    state = runCatching { AppState.Home(container.updateSettings(settings)) }
                        .getOrElse { AppState.Failed(it.message ?: "운영 설정을 저장하지 못했습니다.", current.config) }
                } },
                onCancel = { state = AppState.Home(current.config) },
            )
            is AppState.Consent -> ConsentScreen(
                onSelect = { consent, participant -> state = AppState.Measuring(current.config, consent, participant) },
                onCancel = { state = AppState.Home(current.config) },
            )
            is AppState.Measuring -> MeasurementScreen(
                current.config,
                onComplete = { label, stress -> scope.launch {
                    state = AppState.Analyzing(current.config)
                    state = runCatching {
                        val emotion = current.config.mapAnalysisLabel(label, stress)
                        AppState.Result(current.config, label, stress, container.recommend(emotion, stress, current.consentStatus, current.participant))
                    }.getOrElse { AppState.Failed(it.message ?: "추천에 실패했습니다.", current.config) }
                } },
                onCancel = { state = AppState.Home(current.config) },
            )
            is AppState.Analyzing -> Centered {
                CircularProgressIndicator()
                Spacer(Modifier.height(20.dp))
                Text("분석 중입니다", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text("추천 결과를 불러오고 있습니다.\n잠시만 기다려 주세요.")
            }
            is AppState.Result -> ResultScreen(
                current,
                onShowMap = { state = AppState.MapGuide(current.config, current.decision) },
                onRestart = { state = AppState.Home(current.config) },
            )
            is AppState.MapGuide -> MapGuideScreen(current) { state = AppState.Home(current.config) }
            is AppState.Failed -> Centered {
                Text("처리 오류", style = MaterialTheme.typography.headlineMedium)
                Text(current.message, color = MaterialTheme.colorScheme.error)
                current.config?.let { config -> Button(onClick = { state = AppState.Home(config) }) { Text("처음으로") } }
            }
        }
    }
}

@Composable
private fun ConsentScreen(
    onSelect: (ConsentStatus, ParticipantProfile?) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val inputTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("개인정보 입력", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("본인 확인 및 결과 안내를 위한 선택 입력입니다.\n현재 버전에서는 서버에 개인정보를 저장하지 않습니다.")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            name,
            { name = it.take(10) },
            label = { Text("이름") },
            textStyle = inputTextStyle,
            singleLine = true,
        )
        OutlinedTextField(
            phone,
            { phone = it.filter(Char::isDigit).take(11) },
            label = { Text("휴대전화번호") },
            textStyle = inputTextStyle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
        )
        OutlinedTextField(
            birthDate,
            { birthDate = it.filter(Char::isDigit).take(8) },
            label = { Text("생년월일 8자리") },
            textStyle = inputTextStyle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("남성", "여성", "기타").forEach { value ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = gender == value, onClick = { gender = value })
                    Text(value)
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            error = when {
                name.isBlank() -> "이름을 입력해 주세요."
                phone.length !in 10..11 -> "휴대전화번호를 확인해 주세요."
                birthDate.length != 8 -> "생년월일 8자리를 입력해 주세요."
                gender.isBlank() -> "성별을 선택해 주세요."
                else -> null
            }
            if (error == null) {
                onSelect(ConsentStatus.CONSENTED, ParticipantProfile(name.trim(), phone, birthDate, gender))
            }
        }) { Text("개인정보 입력 후 측정") }
        OutlinedButton(onClick = { onSelect(ConsentStatus.DECLINED, null) }) { Text("개인정보 없이 측정") }
        TextButton(onClick = onCancel) { Text("취소") }
    }
}

@Composable
private fun HomeScreen(
    config: ProjectConfiguration,
    settings: RuntimeSettings,
    onStart: () -> Unit,
    onSettings: () -> Unit,
) = Centered {
    Text(config.theme.name, style = MaterialTheme.typography.headlineMedium)
    Text("${settings.mode} · config v${config.catalog.configVersion}")
    Spacer(Modifier.height(28.dp))
    Text(config.content.homeIntroduction)
    Spacer(Modifier.height(28.dp))
    Button(onClick = onStart) { Text("측정 시작") }
    TextButton(onClick = onSettings) { Text("운영 설정") }
}

@Composable
private fun RuntimeSettingsScreen(
    initial: RuntimeSettings,
    onSave: (RuntimeSettings) -> Unit,
    onCancel: () -> Unit,
) {
    var projectAsset by remember { mutableStateOf(initial.projectConfigAsset) }
    var mode by remember { mutableStateOf(initial.mode) }
    var serverBaseUrl by remember { mutableStateOf(initial.serverBaseUrl) }
    var kioskId by remember { mutableStateOf(initial.kioskId) }
    var kioskKey by remember { mutableStateOf(initial.kioskKey) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("운영 설정", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(projectAsset, { projectAsset = it }, label = { Text("프로젝트 설정 파일") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RuntimeMode.entries.forEach { value ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == value, onClick = { mode = value })
                    Text(value.name)
                }
            }
        }
        OutlinedTextField(serverBaseUrl, { serverBaseUrl = it }, label = { Text("서버 주소") }, singleLine = true)
        OutlinedTextField(kioskId, { kioskId = it }, label = { Text("키오스크 ID") }, singleLine = true)
        OutlinedTextField(kioskKey, { kioskKey = it }, label = { Text("키오스크 키") }, singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            runCatching {
                RuntimeSettings(projectAsset.trim(), mode, serverBaseUrl.trim(), kioskId.trim(), kioskKey.trim())
            }.onSuccess(onSave).onFailure { error = it.message }
        }) { Text("저장 후 적용") }
        TextButton(onClick = onCancel) { Text("취소") }
    }
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
private fun ResultScreen(result: AppState.Result, onShowMap: () -> Unit, onRestart: () -> Unit) = Centered {
    Text("측정 결과", style = MaterialTheme.typography.headlineMedium)
    Text("분석 감정 ${result.label} · 스트레스 ${result.stress}")
    Spacer(Modifier.height(20.dp))
    val emotion = result.decision.item.supportedEmotions.firstOrNull()
    val emotionDefinition = result.config.emotions.firstOrNull { it.code == emotion }
    Text(result.config.content.resultItemLabel, style = MaterialTheme.typography.labelLarge)
    Text(result.decision.item.title, style = MaterialTheme.typography.headlineSmall)
    emotionDefinition?.let { Text("${it.name} · ${it.message}") }
    Text("${result.decision.source} · 스트레스 ${result.stress}")
    Spacer(Modifier.height(24.dp))
    Button(onClick = onShowMap) { Text(result.config.content.mapButtonLabel) }
    Button(onClick = onRestart) { Text("다시 측정") }
}

@Composable
private fun MapGuideScreen(result: AppState.MapGuide, onFinish: () -> Unit) {
    val location = result.config.catalog.locations.first { it.id == result.decision.item.locationId }
    var scale by remember { mutableFloatStateOf(1.2f) }
    var translation by remember { mutableStateOf(Offset.Zero) }
    val dashPhase by rememberInfiniteTransition(label = "route-dashes").animateFloat(
        initialValue = 0f,
        targetValue = -48f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "route-dash-phase",
    )
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFFF3ECEF)).clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
            val mapAspect = 1208f / 740f
            val mapWidth = if (maxWidth / maxHeight > mapAspect) maxHeight * mapAspect else maxWidth
            val mapHeight = mapWidth / mapAspect
            Box(
                Modifier.size(mapWidth, mapHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = translation.x
                        translationY = translation.y
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 4f)
                            val maxTranslationX = size.width * (nextScale - 1f) / 2f
                            val maxTranslationY = size.height * (nextScale - 1f) / 2f
                            val nextTranslation = translation + pan
                            scale = nextScale
                            translation = Offset(
                                x = nextTranslation.x.coerceIn(-maxTranslationX, maxTranslationX),
                                y = nextTranslation.y.coerceIn(-maxTranslationY, maxTranslationY),
                            )
                        }
                    },
            ) {
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.FIT_XY
                            contentDescription = "프로젝트 안내 지도"
                        }
                    },
                    update = { it.setImageURI(Uri.parse(result.config.theme.mapImageRef)) },
                    modifier = Modifier.fillMaxSize(),
                )
                Canvas(Modifier.fillMaxSize()) {
                    val configured = result.config.navigation.routesByLocationCode[location.code].orEmpty()
                    val route = listOf(result.config.navigation.origin) + configured +
                        MapPoint(location.markerXPercent, location.markerYPercent)
                    val path = Path().apply {
                        route.forEachIndexed { index, point ->
                            val px = (point.xPercent / 100.0).toFloat() * size.width
                            val py = (point.yPercent / 100.0).toFloat() * size.height
                            if (index == 0) moveTo(px, py) else lineTo(px, py)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFFE53935),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 7f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 18f), dashPhase),
                        ),
                    )
                }
                MapMarker(
                    mapWidth, mapHeight,
                    result.config.navigation.origin.xPercent,
                    result.config.navigation.origin.yPercent,
                    result.config.content.currentLocationLabel,
                    Color(0xFFD32F2F),
                )
                MapMarker(mapWidth, mapHeight, location.markerXPercent, location.markerYPercent, location.title, Color(0xFFFFC107))
            }
            Text(
                "${location.title} 안내",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.TopCenter).background(Color(0xDDFFFFFF)).padding(12.dp),
            )
            Text(
                result.config.content.mapGestureHint,
                modifier = Modifier.align(Alignment.BottomCenter).background(Color(0xBBFFFFFF)).padding(8.dp),
            )
            Button(
                onClick = onFinish,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Text("처음으로") }
        }
}

@Composable
private fun MapMarker(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, x: Double, y: Double, label: String, color: Color) {
    Column(
        modifier = Modifier.offset(
            x = width * (x / 100.0).toFloat() - 20.dp,
            y = height * (y / 100.0).toFloat() - 20.dp,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Color.White, modifier = Modifier.background(Color(0xCC333333)).padding(6.dp))
        Box(Modifier.size(40.dp).background(color, CircleShape))
    }
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
