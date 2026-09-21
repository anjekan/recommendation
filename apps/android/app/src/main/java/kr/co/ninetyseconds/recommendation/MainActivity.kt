package kr.co.ninetyseconds.recommendation

import android.Manifest
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        super.onCreate(savedInstanceState)
        enterKioskFullscreen()
        val container = (application as RecommendationApplication).container
        setContent {
            if (BuildConfig.DEBUG && intent.getBooleanExtra("measurement_design_preview", false)) {
                RecommendationTheme {
                    MeasurementPresentation(20, "화면 중앙에 얼굴을 맞춰주세요 · 디자인 미리보기", 92, 15, 31, .45f, { finish() }) {
                        Image(painterResource(R.drawable.demo_face), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            } else if (BuildConfig.DEBUG && intent.getBooleanExtra("result_design_preview", false)) {
                RecommendationTheme {
                    ResultPresentation("무언가에 흥미가 생긴 듯 보여요", listOf("INSIGHT", "TASTE", "ACTION"),
                        listOf("1. 부자1번지 상설 주제관", "2. 리치 키자니아 직업체험", "3. 리치 스낵존"),
                        null, 72, 15, "감각 여정 지도 보기", { finish() })
                }
            } else if (BuildConfig.DEBUG && intent.getBooleanExtra("map_design_preview", false)) {
                RecommendationTheme {
                    var previewConfig by remember { mutableStateOf<ProjectConfiguration?>(null) }
                    LaunchedEffect(Unit) { previewConfig = container.start() }
                    previewConfig?.let { config ->
                        val stops = config.catalog.locations.take(3).mapIndexed { i, location ->
                            DisplayMapStop(i + 1, location.code, location.title, location.markerXPercent, location.markerYPercent)
                        }.ifEmpty { listOf(
                            DisplayMapStop(1, "PREVIEW_PLAY", "리치 플레이존", 51.1, 27.4),
                            DisplayMapStop(2, "PREVIEW_DREAM", "리치 드림존", 72.8, 27.6),
                            DisplayMapStop(3, "PREVIEW_LIFE", "리치 라이프존", 48.0, 58.0),
                        ) }
                        MapPresentation(config, stops, expectedStopCount = 3) { finish() }
                    }
                }
            } else RecommendationApp(container)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterKioskFullscreen()
    }

    private fun enterKioskFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private sealed interface AppState {
    data object Loading : AppState
    data class Home(val config: ProjectConfiguration) : AppState
    data class SettingsLogin(val config: ProjectConfiguration) : AppState
    data class Settings(val config: ProjectConfiguration, val settings: RuntimeSettings) : AppState
    data class Consent(val config: ProjectConfiguration) : AppState
    data class Measuring(
        val config: ProjectConfiguration,
        val consentStatus: ConsentStatus,
        val participant: ParticipantProfile?,
    ) : AppState
    data class Analyzing(val config: ProjectConfiguration) : AppState
    data class Result(val config: ProjectConfiguration, val condition: EmotionCode, val heartRate: Int, val respiration: Int, val decision: RecommendationDecision) : AppState
    data class MapGuide(val config: ProjectConfiguration, val decision: RecommendationDecision) : AppState
    data class Failed(val message: String, val config: ProjectConfiguration? = null) : AppState
}

@Composable
fun RecommendationApp(container: AppContainer) {
    var state: AppState by remember { mutableStateOf(AppState.Loading) }
    val context = LocalContext.current
    val credentials = remember(context) { OperatorCredentialsStore(context) }
    val scope = rememberCoroutineScope()
    val recentPrimarySenses = remember { mutableListOf<String>() }
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
                onStart = { state = AppState.Measuring(current.config, ConsentStatus.DECLINED, null) },
                onSettings = { state = AppState.SettingsLogin(current.config) },
            )
            is AppState.SettingsLogin -> OperatorLoginScreen(
                onLogin = { username, password ->
                    val accepted = credentials.verify(username.trim(), password)
                    if (accepted) state = AppState.Settings(current.config, container.settings())
                    accepted
                },
                onCancel = { state = AppState.Home(current.config) },
            )
            is AppState.Settings -> RuntimeSettingsScreen(
                initial = current.settings,
                onChangePassword = credentials::changePassword,
                onSave = { settings -> scope.launch {
                    state = runCatching { AppState.Home(container.updateSettings(settings)) }
                        .getOrElse { AppState.Failed(it.message ?: "운영 설정을 저장하지 못했습니다.", current.config) }
                } },
                onCancel = { state = AppState.Home(current.config) },
            )
            is AppState.Consent -> KioskConsentScreen(
                config = current.config,
                onLanguageChange = { language -> scope.launch {
                    state = runCatching { AppState.Consent(container.start(language)) }
                        .getOrElse { AppState.Failed(it.message ?: "언어를 변경하지 못했습니다.", current.config) }
                } },
                onSelect = { consent, participant -> state = AppState.Measuring(current.config, consent, participant) },
                onSettings = { state = AppState.SettingsLogin(current.config) },
            )
            is AppState.Measuring -> MeasurementScreen(
                current.config,
                demoMode = container.settings().demoMode,
                onComplete = { label, stress, heartRate, respiration, actionUnit -> scope.launch {
                    state = AppState.Analyzing(current.config)
                    state = runCatching {
                        val emotion = current.config.mapAnalysisLabel(label, stress, actionUnit)
                        val decision = if (container.settings().demoMode) {
                            container.demoRecommend(emotion, stress)
                        } else {
                            container.recommend(emotion, stress, current.consentStatus, current.participant)
                        }
                        val balancedDecision = if (current.config.catalog.projectId.value.contains("UIRYEONG", ignoreCase = true)) {
                            balanceRichJourney(decision, recentPrimarySenses)
                        } else {
                            decision
                        }
                        AppState.Result(current.config, emotion, heartRate, respiration, balancedDecision)
                    }.getOrElse { AppState.Failed(it.message ?: "추천에 실패했습니다.", current.config) }
                } },
                onCancel = { state = AppState.Home(current.config) },
            )
            is AppState.Analyzing -> Centered {
                CircularProgressIndicator()
                Spacer(Modifier.height(20.dp))
                Text(uiText(current.config.selectedLanguage, "분석 중입니다", "Analyzing", "分析中", "分析中です"), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(uiText(current.config.selectedLanguage, "추천 결과를 불러오고 있습니다.\n잠시만 기다려 주세요.", "Loading your recommendation.\nPlease wait a moment.", "正在加载推荐结果。\n请稍候。", "おすすめ結果を読み込んでいます。\n少々お待ちください。"))
            }
            is AppState.Result -> ResultScreen(
                current,
                onShowMap = { state = AppState.MapGuide(current.config, current.decision) },
            )
            is AppState.MapGuide -> MapGuideScreen(current) { state = AppState.Home(current.config) }
            is AppState.Failed -> Centered {
                Text(uiText(current.config?.selectedLanguage, "처리 오류", "Processing error", "处理错误", "処理エラー"), style = MaterialTheme.typography.headlineMedium)
                Text(current.message, color = MaterialTheme.colorScheme.error)
                current.config?.let { config -> Button(onClick = { state = AppState.Home(config) }) { Text(uiText(config.selectedLanguage, "처음으로", "Home", "返回首页", "最初に戻る")) } }
            }
        }
    }
}

private fun balanceRichJourney(
    decision: RecommendationDecision,
    recentPrimarySenses: MutableList<String>,
): RecommendationDecision {
    val journey = decision.journey.sortedBy { it.order }
    if (journey.size < 2) return decision

    val nextIndex = journey.indexOfFirst { it.senseCode !in recentPrimarySenses }
        .takeIf { it >= 0 }
        ?: 0
    val reordered = (journey.drop(nextIndex) + journey.take(nextIndex))
        .mapIndexed { index, stop -> stop.copy(order = index + 1) }

    recentPrimarySenses += reordered.first().senseCode
    while (recentPrimarySenses.size > 2) recentPrimarySenses.removeAt(0)
    return decision.copy(item = reordered.first().item, journey = reordered)
}

@Composable
private fun LegacyConsentScreen(
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
) {
    val startInteraction = remember { MutableInteractionSource() }
    val startPressed by startInteraction.collectIsPressedAsState()
    var starting by remember { mutableStateOf(false) }
    val latestOnStart by rememberUpdatedState(onStart)
    val startScale by animateFloatAsState(
        targetValue = if (startPressed || starting) 1.08f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing), label = "start-button-scale",
    )
    LaunchedEffect(starting) {
        if (starting) {
            // Let even a quick tap show the scale feedback before navigating.
            delay(150)
            latestOnStart()
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF8ED7FF))) {
        Image(
            painter = painterResource(R.drawable.richrich_home_background),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.richrich_home_logo),
            contentDescription = "AI가 깨우는 부자의 감각",
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp).fillMaxWidth(.57f),
        )
        Image(
            painter = painterResource(R.drawable.richrich_festival_logo),
            contentDescription = "제5회 의령 리치리치 페스티벌",
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.TopStart)
                .padding(start = 24.dp, top = 20.dp)
                .size(width = 250.dp, height = 84.dp),
        )
        Image(
            painter = painterResource(R.drawable.richrich_three_generations),
            contentDescription = "3대가 함께 경험하는 부자의 감각",
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(end = 24.dp, top = 18.dp)
                .size(width = 216.dp, height = 102.dp),
        )
        Image(
            painter = painterResource(R.drawable.richrich_home_button),
            contentDescription = "바로 시작",
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 52.dp)
                .size(width = 310.dp, height = 220.dp)
                .graphicsLayer { scaleX = startScale; scaleY = startScale }
                .clickable(
                    interactionSource = startInteraction,
                    indication = null,
                    enabled = !starting,
                    role = androidx.compose.ui.semantics.Role.Button,
                    onClick = { starting = true },
                ),
        )
        Surface(
            color = Color(0xDDF4EEE5), shape = RoundedCornerShape(30.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
        ) {
            Text(
                "🔒  카메라 영상과 측정 결과는 저장되지 않으며, 익명 통계로만 집계됩니다.",
                color = Color(0xFF44352A), fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
        Text(
            "⚙",
            color = Color(0x992D251E),
            fontSize = 21.sp,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
                .clickable(onClick = onSettings)
                .padding(12.dp),
        )
    }
}

@Composable
private fun OperatorLoginScreen(
    onLogin: (String, String) -> Boolean,
    onCancel: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Color(0xFF102B55)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFF5FAFF), modifier = Modifier.width(420.dp)) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("운영 설정 로그인", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(username, { username = it; error = null }, label = { Text("아이디") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(password, { password = it; error = null }, label = { Text("비밀번호") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel) { Text("취소") }
                    Button(onClick = {
                        if (!onLogin(username, password)) {
                            password = ""
                            error = "아이디 또는 비밀번호가 올바르지 않습니다."
                        }
                    }) { Text("로그인") }
                }
            }
        }
    }
}

@Composable
private fun RuntimeSettingsScreen(
    initial: RuntimeSettings,
    onChangePassword: (String, String) -> Boolean,
    onSave: (RuntimeSettings) -> Unit,
    onCancel: () -> Unit,
) {
    var projectAsset by remember { mutableStateOf(initial.projectConfigAsset) }
    var mode by remember { mutableStateOf(initial.mode) }
    var serverBaseUrl by remember { mutableStateOf(initial.serverBaseUrl) }
    var kioskId by remember { mutableStateOf(initial.kioskId) }
    var kioskKey by remember { mutableStateOf(initial.kioskKey) }
    var demoMode by remember { mutableStateOf(initial.demoMode) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordMessage by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = demoMode, onCheckedChange = { demoMode = it })
            Spacer(Modifier.width(10.dp))
            Text("촬영용 DEMO 모드 (얼굴·서버 전송 없음)")
        }
        Spacer(Modifier.height(12.dp))
        Text("운영 비밀번호 변경", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(currentPassword, { currentPassword = it; passwordMessage = null },
                label = { Text("현재 비밀번호") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.width(180.dp))
            OutlinedTextField(newPassword, { newPassword = it; passwordMessage = null },
                label = { Text("새 비밀번호 (8자 이상)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.width(220.dp))
            OutlinedTextField(confirmPassword, { confirmPassword = it; passwordMessage = null },
                label = { Text("새 비밀번호 확인") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.width(200.dp))
        }
        TextButton(onClick = {
            passwordMessage = when {
                newPassword.length < 8 -> "새 비밀번호는 8자 이상이어야 합니다."
                newPassword != confirmPassword -> "새 비밀번호가 일치하지 않습니다."
                !onChangePassword(currentPassword, newPassword) -> "현재 비밀번호가 올바르지 않습니다."
                else -> "비밀번호가 변경되었습니다."
            }
            if (passwordMessage == "비밀번호가 변경되었습니다.") {
                currentPassword = ""
                newPassword = ""
                confirmPassword = ""
            }
        }) { Text("비밀번호 변경") }
        passwordMessage?.let { Text(it, color = if (it == "비밀번호가 변경되었습니다.") Color(0xFF187244) else MaterialTheme.colorScheme.error) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            runCatching {
                RuntimeSettings(projectAsset.trim(), mode, serverBaseUrl.trim(), kioskId.trim(), kioskKey.trim(), demoMode)
            }.onSuccess(onSave).onFailure { error = it.message }
        }) { Text("저장 후 적용") }
        TextButton(onClick = onCancel) { Text("취소") }
    }
}

@Composable
private fun MeasurementScreen(config: ProjectConfiguration, demoMode: Boolean, onComplete: (String, Int, Int, Int, Int) -> Unit, onCancel: () -> Unit) {
    if (demoMode) {
        DemoMeasurementScreen(config, onComplete, onCancel)
        return
    }
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    LaunchedEffect(Unit) { if (!permitted) permissionLauncher.launch(Manifest.permission.CAMERA) }
    if (!permitted) {
        Centered {
            Text(uiText(config.selectedLanguage, "측정을 위해 카메라 권한이 필요합니다.", "Camera permission is required for measurement.", "测量需要摄像头权限。", "測定にはカメラの許可が必要です。"))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text(uiText(config.selectedLanguage, "카메라 권한 허용", "Allow camera", "允许摄像头", "カメラを許可")) }
            Button(onClick = onCancel) { Text(uiText(config.selectedLanguage, "취소", "Cancel", "取消", "キャンセル")) }
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
                actionUnitPercent = current?.actionUnitPercent,
            )
            progress.result?.let {
                onComplete(it.emotionLabel, it.stressScore, it.vital.heartRateBpm, it.vital.respiratoryRateRpm, it.actionUnitPercent)
                break
            }
        }
    }

    cameraError?.let { message ->
        Centered {
            Text(uiText(config.selectedLanguage, "카메라를 시작하지 못했습니다.", "Could not start the camera.", "无法启动摄像头。", "カメラを起動できませんでした。"), style = MaterialTheme.typography.headlineSmall)
            Text(message, color = MaterialTheme.colorScheme.error)
            Button(onClick = onCancel) { Text(uiText(config.selectedLanguage, "처음으로", "Home", "返回首页", "最初に戻る")) }
        }
        return
    }

    val scanTransition = rememberInfiniteTransition(label = "camera-scan")
    val cameraScan by scanTransition.animateFloat(
        initialValue = .15f,
        targetValue = .85f,
        animationSpec = infiniteRepeatable(tween(1_500, easing = LinearEasing), RepeatMode.Reverse),
        label = "camera-scan-position",
    )
    MeasurementPresentation(
        progress.secondsRemaining, progress.phase.message,
        snapshot?.vital?.heartRateBpm ?: 0, snapshot?.vital?.respiratoryRateRpm ?: 0,
        snapshot?.actionUnitPercent ?: 0, cameraScan, onCancel,
    ) {
        CameraMeasurementPreview(Modifier.fillMaxSize(), { snapshot = it }, { cameraError = it })
    }
}

@Composable
private fun MeasurementPresentation(seconds: Int, message: String, heart: Int, respiration: Int, actionUnit: Int, scan: Float, onCancel: () -> Unit, preview: @Composable () -> Unit) {
    val measurementFont = remember { FontFamily(Font(R.font.sb_aggro_medium, FontWeight.Normal), Font(R.font.sb_aggro_bold, FontWeight.Bold)) }
    var instructionHeight by remember { mutableStateOf(128.dp) }
    val density = LocalDensity.current
    ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = measurementFont)) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.richrich_home_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .84f)))
        Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Surface(
            Modifier.weight(.46f).fillMaxHeight(),
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(3.dp, Color(0xFFFFDA52)),
            color = Color(0xFF063C78),
            shadowElevation = 0.dp,
        ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))) { preview() }
        Box(Modifier.fillMaxSize().padding(18.dp)) {
            MeasurementScanGuide(scan, instructionHeight)
            Text(
                "얼굴 인식 영역 · ${seconds}초 비접촉 측정",
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .border(1.5.dp, Color(0xFF43CDFF), RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF079EF7), Color(0xFF0638B1))), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Image(painterResource(R.drawable.rich_measure_gripping_guide), null,
                Modifier.align(Alignment.BottomStart).padding(bottom = (instructionHeight - 17.dp).coerceAtLeast(0.dp))
                    .offset(x = 8.dp).size(85.dp).zIndex(1f), contentScale = ContentScale.Fit)
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .onSizeChanged { instructionHeight = with(density) { it.height.toDp() } }
                    .border(1.5.dp, Color(0xFF38C6FF), RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xF0124168), Color(0xF003245D))), RoundedCornerShape(22.dp)).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("잠시 편안한 표정으로 화면을 봐주세요", fontSize = 18.sp, color = Color.White)
                Text(message, color = Color(0xFFDAEBFF), fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCancel, modifier = Modifier.width(180.dp).height(40.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFAFD6FF)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF084781), contentColor = Color.White),
                ) { Text("측정 취소", fontWeight = FontWeight.Bold) }
            }
        }
        }
        }
        val revealMeasurements = seconds <= LIVE_VALUE_REVEAL_SECONDS
        Column(Modifier.weight(.54f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            MeasurementMetricCard("♥", "맥박 (Heart Rate)", heart, "BPM", 55, 170, Color(0xFF008D92), revealValue = revealMeasurements)
            MeasurementMetricCard("≈", "호흡 (Respiration)", respiration, "RPM", 10, 35, Color(0xFF0798F2), revealValue = revealMeasurements)
            MeasurementMetricCard("☺", "표정근육 (Action Unit)", actionUnit, "%", 0, 100, Color(0xFF7146E8), "개인 무표정 대비 변화량", revealMeasurements)
        }
        }
    }
    }
}

@Composable
private fun MeasurementScanGuide(scan: Float, instructionHeight: androidx.compose.ui.unit.Dp) {
    val guideDropPx = LocalContext.current.resources.displayMetrics.ydpi * 5f / 25.4f
    Canvas(Modifier.fillMaxSize()) {
        val cyan = Color(0xFF20DEFF)
        val y = size.height * scan
        // Layered glow works on hardware-accelerated Canvas, including Android 8.
        val spread = 24.dp.toPx()
        drawRect(Brush.verticalGradient(
            listOf(Color.Transparent, cyan.copy(alpha = .08f), cyan.copy(alpha = .32f), cyan.copy(alpha = .08f), Color.Transparent),
            startY = y - spread, endY = y + spread),
            topLeft = Offset(0f, y - spread), size = androidx.compose.ui.geometry.Size(size.width, spread * 2))
        listOf(14f to .06f, 10f to .10f, 6f to .24f, 3.5f to .9f).forEach { (width, alpha) ->
            drawLine(cyan.copy(alpha = alpha), Offset(0f, y), Offset(size.width, y), width.dp.toPx())
        }
        drawLine(Color(0xFFF0FFFF), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
        val sideInset = size.minDimension * .13f
        val topInset = size.minDimension * .13f
        // Leave clear space above the peeking mascot and instruction panel.
        val bottomY = minOf(size.height * .70f, size.height - instructionHeight.toPx() - 160.dp.toPx())
            .coerceAtLeast(topInset + size.height * .30f)
        val arm = size.minDimension * .10f
        val bend = 9.dp.toPx().coerceAtMost(arm * .45f)
        listOf(
            Offset(sideInset, topInset) to Pair(1f, 1f),
            Offset(size.width - sideInset, topInset) to Pair(-1f, 1f),
            Offset(sideInset, bottomY) to Pair(1f, -1f),
            Offset(size.width - sideInset, bottomY) to Pair(-1f, -1f),
        ).forEach { (corner, direction) ->
            val (dx, dy) = direction
            val shiftedCorner = corner + Offset(0f, guideDropPx)
            val path = Path().apply {
                moveTo(shiftedCorner.x, shiftedCorner.y + arm * dy)
                lineTo(shiftedCorner.x, shiftedCorner.y + bend * dy)
                quadraticTo(shiftedCorner.x, shiftedCorner.y, shiftedCorner.x + bend * dx, shiftedCorner.y)
                lineTo(shiftedCorner.x + arm * dx, shiftedCorner.y)
            }
            listOf(24f to .025f, 18f to .045f, 13f to .08f, 9f to .18f, 6f to .75f).forEach { (width, alpha) ->
                drawPath(path, cyan.copy(alpha = alpha), style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            drawPath(path, Color(0xFFEEFFFF), style = androidx.compose.ui.graphics.drawscope.Stroke(
                3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

@Composable
private fun ColumnScope.MeasurementMetricCard(icon: String, title: String, value: Int, unit: String, min: Int, max: Int, accent: Color, footer: String = "유효 $min~$max", revealValue: Boolean = true) {
    val fraction = ((value - min).toFloat() / (max - min).coerceAtLeast(1)).coerceIn(0f, 1f)
    val placeholderTransition = rememberInfiniteTransition(label = "measurement-placeholder")
    val placeholderAlpha by placeholderTransition.animateFloat(
        initialValue = .28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "measurement-placeholder-alpha",
    )
    val hasVisibleValue = revealValue && value > 0
    Surface(
        Modifier.weight(1f).fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFD85A)),
        color = Color(0xFFF5FAFF),
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(54.dp)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = .65f), accent, accent)))
                .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                MeasurementIcon(icon, accent)
                Spacer(Modifier.width(14.dp))
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Row(Modifier.weight(1f).fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (hasVisibleValue) value.toString() else "--",
                            fontSize = 88.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accent.copy(alpha = if (hasVisibleValue) 1f else placeholderAlpha),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(unit, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color(0xFF454B62), modifier = Modifier.padding(top = 24.dp))
                    }
                    MeasurementGauge(fraction, accent)
                }
                Column(Modifier.width(180.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(when (icon) {
                        "♥" -> R.drawable.rich_measure_crown
                        "≈" -> R.drawable.rich_measure_breathing
                        else -> R.drawable.rich_measure_sunglasses
                    }), null, Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                        val mascotScale = when (icon) { "≈" -> 1.4f; "☺" -> 1.15f; else -> 1f }
                        scaleX = mascotScale
                        scaleY = mascotScale
                    }, contentScale = ContentScale.Fit)
                    Text(footer,
                        fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, color = Color(0xFF454B62))
                }
            }
        }
        }
    }
}

private const val LIVE_VALUE_REVEAL_SECONDS = 12

@Composable
private fun MeasurementIcon(kind: String, accent: Color) {
    Canvas(Modifier.size(44.dp)) {
        val w = size.width
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .35f), accent), center = Offset(w * .28f, w * .18f), radius = w * .9f))
        drawCircle(Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        if (kind == "♥") {
            val p = Path().apply {
                moveTo(w * .5f, w * .76f)
                cubicTo(w * .02f, w * .47f, w * .22f, w * .16f, w * .5f, w * .35f)
                cubicTo(w * .78f, w * .16f, w * .98f, w * .47f, w * .5f, w * .76f)
                close()
            }
            drawPath(p, Color.White)
        } else if (kind == "≈") {
            listOf(.39f, .61f).forEach { y ->
                val p = Path().apply {
                    moveTo(w * .24f, w * y)
                    cubicTo(w * .40f, w * (y - .23f), w * .60f, w * (y + .23f), w * .76f, w * y)
                }
                drawPath(p, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        } else {
            drawCircle(Color.White, w * .055f, Offset(w * .35f, w * .36f))
            drawCircle(Color.White, w * .055f, Offset(w * .65f, w * .36f))
            val p = Path().apply {
                moveTo(w * .25f, w * .53f)
                quadraticBezierTo(w * .5f, w * .95f, w * .75f, w * .53f)
                quadraticBezierTo(w * .5f, w * .64f, w * .25f, w * .53f)
                close()
            }
            drawPath(p, Color.White)
        }
    }
}

@Composable
private fun MeasurementGauge(fraction: Float, accent: Color) {
    Canvas(Modifier.fillMaxWidth().height(24.dp)) {
        val radius = 8.dp.toPx()
        val left = radius + 2.dp.toPx()
        val right = size.width - left
        val y = size.height / 2
        drawLine(Color.White, Offset(left, y), Offset(right, y), 18.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        val colors = if (accent == Color(0xFF008D92)) listOf(Color(0xFF5DCBDB), Color(0xFF008D92), Color(0xFF477AC2))
            else if (accent == Color(0xFF7146E8)) listOf(Color(0xFF008AFF), Color(0xFFA83FFF), Color(0xFFB8C6D8))
            else listOf(Color(0xFF008AFF), Color(0xFF19CBB8), Color(0xFFE7F342), Color(0xFFFF3F55))
        drawLine(Brush.horizontalGradient(colors), Offset(left, y), Offset(right, y), 12.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        val center = Offset(left + (right - left) * fraction, y)
        drawCircle(Color(0x33001E41), radius + 3.dp.toPx(), center + Offset(0f, 1.dp.toPx()))
        drawCircle(Color.White, radius + 2.dp.toPx(), center)
        drawCircle(accent, radius, center)
    }
}

@Composable
private fun DemoMeasurementScreen(config: ProjectConfiguration, onComplete: (String, Int, Int, Int, Int) -> Unit, onCancel: () -> Unit) {
    var seconds by remember { mutableIntStateOf(10) }
    val transition = rememberInfiniteTransition(label = "demo-scan")
    val scan by transition.animateFloat(
        initialValue = .15f,
        targetValue = .85f,
        animationSpec = infiniteRepeatable(tween(1_500, easing = LinearEasing), RepeatMode.Reverse),
        label = "scan-position",
    )
    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1_000)
            seconds--
        }
        onComplete("Happy", 24, 72, 15, 31)
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF15242B))) {
        Image(
            painter = painterResource(R.drawable.demo_face),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .10f)))
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * scan
            drawLine(Color(0xFFE86B82), Offset(size.width * .28f, y), Offset(size.width * .72f, y), strokeWidth = 6f)
        }
        Surface(
            color = Color(0xFFB72F50),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
        ) { Text("DEMO · ${uiText(config.selectedLanguage, "실제 측정값 아님", "Simulated data", "模拟数据", "シミュレーション")}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }
        Column(Modifier.align(Alignment.BottomCenter).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(uiText(config.selectedLanguage, "얼굴을 저장하지 않고 분석을 시연하고 있습니다", "Demonstrating analysis without capturing a face", "正在演示不采集人脸的分析", "顔を撮影せず分析を実演しています"), color = Color.White)
            Text("$seconds${uiText(config.selectedLanguage, "초", "s", "秒", "秒")}", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text("${uiText(config.selectedLanguage, "가상 심박", "Simulated heart rate", "模拟心率", "模擬心拍数")} ${72 + (10 - seconds) % 4} BPM", color = Color(0xFFFFCED8))
            TextButton(onClick = onCancel) { Text(uiText(config.selectedLanguage, "측정 취소", "Cancel", "取消", "キャンセル"), color = Color.White) }
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
    val previewView = remember { PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    } }
    val currentSnapshotListener by rememberUpdatedState(onSnapshot)
    val currentErrorListener by rememberUpdatedState(onError)
    val analyzerResult = remember {
        runCatching {
            MeasurementFrameAnalyzer(
                faceDetector = MediaPipeFaceDetector.create(context),
                emotionClassifier = OnnxEmotionClassifier.create(context),
                listener = { value -> mainExecutor.execute { currentSnapshotListener(value) } },
                errorListener = { error ->
                    mainExecutor.execute {
                        currentErrorListener(error.message ?: "분석 프레임 처리 오류")
                    }
                },
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
        var boundAnalysis: ImageAnalysis? = null
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
                boundAnalysis = analysis
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            }.onFailure {
                if (!disposed) currentErrorListener(it.message ?: "전면 카메라 연결 오류")
            }
        }, mainExecutor)
        onDispose {
            disposed = true
            boundAnalysis?.clearAnalyzer()
            if (future.isDone) runCatching { future.get().unbindAll() }
            // Analyzer resources are native. Close them on the same single-thread executor
            // only after any in-flight frame has returned, preventing use-after-close crashes.
            runCatching { analysisExecutor.execute { analyzer.close() } }
            analysisExecutor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
private fun ResultScreen(result: AppState.Result, onShowMap: () -> Unit) {
    val emotionDefinition = result.config.emotions.firstOrNull { it.code == result.condition }
    val journey = result.decision.toJourneyPresentation()
    val knownSenses = setOf("INSIGHT", "SCENT", "TASTE", "LISTENING", "ACTION", "INTUITION")
    val senseCodes = journey.senseCodes.filter { it in knownSenses }.ifEmpty {
        listOf(when (result.condition.value) {
            "JOY", "EXCITED", "LEISURE" -> "ACTION"
            "TENSION", "HEAVINESS" -> "SCENT"
            "LOW_ENERGY", "DROWSY" -> "TASTE"
            else -> "INSIGHT"
        })
    }
    ResultPresentation(emotionDefinition?.message ?: "무언가에 흥미가 생긴 듯 보여요",
        senseCodes,
        journey.stopLabels,
        journey.operationNotice(result.config.selectedLanguage),
        result.heartRate, result.respiration, result.config.content.mapButtonLabel, onShowMap)
}

@Composable
private fun ResultPresentation(message: String, senseCodes: List<String>, journey: List<String>, operationNotice: String?,
    heartRate: Int, respiration: Int, mapLabel: String, onShowMap: () -> Unit) {
    val sense = richSenseVisual(senseCodes.firstOrNull())
    val particle = subjectParticle(sense.name)
    val resultFont = remember { FontFamily(Font(R.font.sb_aggro_medium, FontWeight.Normal), Font(R.font.sb_aggro_bold, FontWeight.Bold)) }
    ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = resultFont)) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.rich_result_night_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .10f)))
        Row(Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ResultPanel(Modifier.weight(.43f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
                    ResultSenseComposition(sense, Modifier.fillMaxWidth().weight(1f))
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ResultVital("심박수", heartRate.toString(), "BPM", Modifier.weight(1f), Color(0xFF15BCD0))
                        ResultVital("호흡수", respiration.toString(), "/min", Modifier.weight(1f), Color(0xFF22B7FF))
                    }
                    Text("* 측정 데이터는 실시간 상태를 반영하며, 의학적 진단이 아닙니다.", color = Color(0xFFB9D9FF), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
            ResultPanel(Modifier.weight(.57f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(message, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD8EAFF))
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text("지금은 ", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        GoldenSenseLabel("[${sense.name}]", Modifier, 29)
                        Text("${particle} 필요한 순간이에요", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        RichSenseRadar(senseCodes, Modifier.fillMaxSize().padding(end = 60.dp))
                        Image(
                            painter = painterResource(R.drawable.rich_result_sunglasses_resting),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp).offset(y = 34.dp).width(210.dp).height(174.dp),
                        )
                    }
                    if (journey.isNotEmpty()) {
                        // One shared parent inset keeps both edges equally far from the outer panel.
                        Box(Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 17.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF103262),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFFC83D)),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 34.dp, bottom = 9.dp)) {
                                Text(
                                    journey.joinToString("  ·  "),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                operationNotice?.let { notice ->
                                    Text(
                                        "⚠ $notice",
                                        color = Color(0xFFFFDE78),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 5.dp),
                                    )
                                }
                            }
                        }
                        Surface(Modifier.align(Alignment.TopStart).padding(start = 16.dp), shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF062951), border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFFD75B))) {
                            Text("● 추천 감각 여정  ›", color = Color(0xFFFFDE4A), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                        }
                        }
                    }
                    Button(onClick = onShowMap, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC329), contentColor = Color(0xFF241607))) {
                        Text(mapLabel, fontFamily = resultFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ResultSenseComposition(sense: RichSenseVisual, modifier: Modifier = Modifier) {
    val captionDrop = with(LocalDensity.current) {
        (LocalContext.current.resources.displayMetrics.ydpi * 3f / 25.4f).toDp()
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // One reference coordinate system keeps the badge, seated mascot and caption connected.
        val u = minOf(maxWidth / 600f, maxHeight / 620f)
        Box(Modifier.size(u * 600f, u * 620f)) {
            Box(Modifier.offset(u * 75f, u * 0f).size(u * 480f)) {
                ResultBadgeEffect(sense.accent)
                Image(painterResource(sense.drawableRes), sense.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                GoldenSenseLabel(sense.name, Modifier.align(Alignment.BottomCenter).padding(bottom = u * 87f), (u.value * 64f).toInt())
            }
            Canvas(Modifier.offset(u * 18f, u * 507f).size(u * 350f, u * 19f)) {
                drawOval(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x557EB0B4), Color(0x33428CAF), Color.Transparent)))
            }
            Image(painterResource(R.drawable.rich_result_seated_guide), null,
                Modifier.offset(u * 23f, u * 327f).size(u * 260f, u * 203f), contentScale = ContentScale.Fit)
            Text("오늘의 감각, 축제처럼\n당신의 하루도 빛나기를.",
                Modifier.offset(u * 52f, u * 531f + captionDrop).width(u * 508f),
                fontSize = (u.value * 32f).sp, lineHeight = (u.value * 39f).sp,
                fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / 600f
                listOf(48f to 572f, 551f to 546f, 567f to 587f).forEach { (x, y) ->
                    val p = Path().apply {
                        moveTo(x * k, (y - 14f) * k)
                        lineTo((x + 4f) * k, (y - 4f) * k)
                        lineTo((x + 13f) * k, y * k)
                        lineTo((x + 4f) * k, (y + 4f) * k)
                        lineTo(x * k, (y + 14f) * k)
                        lineTo((x - 4f) * k, (y + 4f) * k)
                        lineTo((x - 13f) * k, y * k)
                        lineTo((x - 4f) * k, (y - 4f) * k)
                        close()
                    }
                    drawPath(p, Color(0xFFFFD965))
                }
            }
        }
    }
}

@Composable
private fun ResultPanel(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFC83D)),
        shadowElevation = 12.dp,
    ) {
        Box(Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xE80651B8), Color(0xA6093978), Color(0xE605275C))))
            .padding(4.dp)
            .border(1.dp, Brush.verticalGradient(listOf(Color(0xFFC8F4FF), Color(0x3349AFFF), Color(0xFF4BA9FF))), RoundedCornerShape(26.dp))) {
            content()
        }
    }
}

@Composable
private fun GoldenSenseLabel(text: String, modifier: Modifier = Modifier, size: Int) {
    Box(modifier.padding(horizontal = 3.dp, vertical = 2.dp)) {
        // Code-rendered lettering works for all six senses and language particles.
        listOf(Offset(-2f, 0f), Offset(2f, 0f), Offset(0f, -2f), Offset(0f, 3f)).forEach { shift ->
            Text(text, Modifier.offset(shift.x.dp, shift.y.dp), fontSize = size.sp, fontWeight = FontWeight.Bold, color = Color(0xFF552508))
        }
        Text(text, fontSize = size.sp, fontWeight = FontWeight.Bold,
            style = LocalTextStyle.current.copy(brush = Brush.verticalGradient(listOf(Color(0xFFFFFFE1), Color(0xFFFFCF49), Color(0xFFFF9815))),
                shadow = androidx.compose.ui.graphics.Shadow(Color(0x88000000), Offset(0f, 3f), 5f)))
    }
}

@Composable
private fun ResultVital(title: String, value: String, unit: String, modifier: Modifier = Modifier, accent: Color = Color(0xFFFFC83D)) {
    Surface(shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC83D)), color = Color(0xD908234A), modifier = modifier) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MeasurementIcon(if (title == "심박수") "♥" else "≈", accent)
        Column {
            Text(title, color = Color(0xFFD7E8FF))
            Row(verticalAlignment = Alignment.Bottom) { Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = accent); Text(" $unit", color = Color.White, modifier = Modifier.padding(bottom = 4.dp)) }
        }
        }
    }
}

private data class RichSenseVisual(val code: String, val name: String, val drawableRes: Int, val accent: Color)

private fun richSenseVisual(code: String?): RichSenseVisual = when (code) {
    "SCENT" -> RichSenseVisual("SCENT", "향기", R.drawable.rich_sense_scent, Color(0xFFC99BFF))
    "TASTE" -> RichSenseVisual("TASTE", "미식", R.drawable.rich_sense_taste, Color(0xFFFF715B))
    "LISTENING" -> RichSenseVisual("LISTENING", "경청", R.drawable.rich_sense_listening, Color(0xFF31C7FF))
    "ACTION" -> RichSenseVisual("ACTION", "실천", R.drawable.rich_sense_action, Color(0xFF43D27B))
    "INTUITION" -> RichSenseVisual("INTUITION", "통찰", R.drawable.rich_sense_intuition, Color(0xFF8A78FF))
    else -> RichSenseVisual("INSIGHT", "안목", R.drawable.rich_sense_insight, Color(0xFFFFB72D))
}

private fun subjectParticle(word: String): String {
    val last = word.lastOrNull() ?: return "가"
    return if (last in '가'..'힣' && (last.code - '가'.code) % 28 != 0) "이" else "가"
}

@Composable
private fun BoxScope.ResultBadgeEffect(accent: Color) {
    val transition = rememberInfiniteTransition(label = "result-badge-effect")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2_200, easing = LinearEasing), RepeatMode.Restart), label = "badge-pulse")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart), label = "badge-rays")
    Canvas(Modifier.fillMaxSize()) {
        // Match the visible medallion, whose center sits above the PNG canvas center.
        val center = Offset(size.width * .50f, size.height * .48f)
        val radius = size.minDimension * .42f
        val halo = radius * 1.36f
        drawCircle(Brush.radialGradient(
            0f to Color.Transparent, .58f to Color.Transparent, .78f to accent.copy(alpha = .26f),
            .88f to accent.copy(alpha = .40f), 1f to Color.Transparent,
            center = center, radius = halo), halo, center)
        val orbit = radius * 1.20f
        listOf(10f to .04f, 5f to .10f, 1.2f to .70f).forEach { (width, alpha) ->
            drawCircle(accent.copy(alpha = alpha), orbit, center, style = androidx.compose.ui.graphics.drawscope.Stroke(width.dp.toPx()))
        }
        repeat(2) { index ->
            val phase = (pulse + index / 2f) % 1f
            drawCircle(accent.copy(alpha = (1f - phase) * .46f), radius * (1.08f + phase * .25f), center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        repeat(8) { index ->
            val angle = Math.toRadians((rotation + index * 45f).toDouble())
            val point = Offset(center.x + kotlin.math.cos(angle).toFloat() * orbit, center.y + kotlin.math.sin(angle).toFloat() * orbit)
            val brightness = .55f + .45f * kotlin.math.sin((pulse * 2 * Math.PI + index).toFloat()).let { it * it }
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = brightness * .65f), Color.Transparent), point, 10.dp.toPx()), 10.dp.toPx(), point)
            val arm = (if (index % 3 == 0) 7f else 4f).dp.toPx()
            drawLine(Color(0xFFFFFFCD).copy(alpha = brightness), point - Offset(arm, 0f), point + Offset(arm, 0f), 1.2.dp.toPx())
            drawLine(Color(0xFFFFFFCD).copy(alpha = brightness), point - Offset(0f, arm), point + Offset(0f, arm), 1.2.dp.toPx())
        }
    }
}

@Composable
private fun RichSenseRadar(selected: List<String>, modifier: Modifier = Modifier) {
    val senses = listOf("안목" to "INSIGHT", "향기" to "SCENT", "미식" to "TASTE", "경청" to "LISTENING", "실천" to "ACTION", "통찰" to "INTUITION")
    val context = LocalContext.current
    val radarFont = remember { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.sb_aggro_medium) }
    val transition = rememberInfiniteTransition(label = "selected-senses")
    val selectedGlow by transition.animateFloat(.18f, 1f,
        infiniteRepeatable(tween(1_100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sense-glow")
    Canvas(modifier.padding(28.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) * .34f
        drawCircle(Brush.radialGradient(listOf(Color(0x553879DE), Color(0x66003285), Color(0xFF0348AD)), center, radius * 1.38f), radius * 1.38f, center)
        drawCircle(Color(0xFF4EC9FF), radius * 1.38f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        fun point(index: Int, ratio: Float): Offset {
            val angle = Math.toRadians((-90 + index * 60).toDouble())
            return Offset(center.x + kotlin.math.cos(angle).toFloat() * radius * ratio, center.y + kotlin.math.sin(angle).toFloat() * radius * ratio)
        }
        for (level in 1..4) {
            val path = Path().apply { senses.indices.forEach { i -> val p = point(i, level / 4f); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
            drawPath(path, Color(0xFF5FA9E8).copy(alpha = .48f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
        val values = senses.mapIndexed { index, pair -> selected.indexOf(pair.second).let { if (it < 0) .38f else (1f - it * .18f).coerceAtLeast(.55f) } }
        val resultPath = Path().apply { senses.indices.forEach { i -> val p = point(i, values[i]); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
        drawPath(resultPath, Brush.verticalGradient(listOf(Color(0xF5FFE978), Color(0xCCFFB52D), Color(0xAAE69A12))))
        drawPath(resultPath, Color(0xFFFFD451), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
        val nodeColors = listOf(Color(0xFFFFBB16), Color(0xFF29E799), Color(0xFFFF5897), Color(0xFF22D2FF), Color(0xFFFFAD21), Color(0xFFAA55FF))
        val rim = Path().apply { senses.indices.forEach { i -> val p = point(i, 1f); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
        drawPath(rim, Color(0xFFFFD45A), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        senses.forEachIndexed { i, sense ->
            val node = point(i, 1f)
            val isSelected = sense.second in selected.take(3)
            if (isSelected) {
                val glowRadius = (22f + 9f * selectedGlow).dp.toPx()
                drawCircle(Brush.radialGradient(listOf(nodeColors[i].copy(alpha = selectedGlow * .85f), Color.Transparent), node, glowRadius), glowRadius, node)
                drawCircle(Color(0xFFFFED9C).copy(alpha = selectedGlow), (10f + 4f * selectedGlow).dp.toPx(), node,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            }
            drawCircle(Brush.radialGradient(listOf(nodeColors[i].copy(alpha = .7f), Color.Transparent), node, 17.dp.toPx()), 17.dp.toPx(), node)
            drawCircle(nodeColors[i], 7.dp.toPx(), node)
            drawCircle(Color.White, 7.dp.toPx(), node, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            val p = point(i, 1.26f)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE; textSize = 17.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; typeface = radarFont
            }
            drawRoundRect(Color(0xEE001B48), Offset(p.x - 30.dp.toPx(), p.y - 14.dp.toPx()),
                androidx.compose.ui.geometry.Size(60.dp.toPx(), 28.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()))
            if (isSelected) {
                drawRoundRect(Color(0xFFFFDA5D).copy(alpha = selectedGlow), Offset(p.x - 30.dp.toPx(), p.y - 14.dp.toPx()),
                    androidx.compose.ui.geometry.Size(60.dp.toPx(), 28.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
            // Center the visible Korean glyphs, not the font's extra ascent/descent space.
            val textBounds = android.graphics.Rect()
            paint.getTextBounds(sense.first, 0, sense.first.length, textBounds)
            val baseline = p.y - (textBounds.top + textBounds.bottom) / 2f
            drawContext.canvas.nativeCanvas.drawText(sense.first, p.x, baseline, paint)
        }
    }
}

@Composable
private fun MapGuideScreen(result: AppState.MapGuide, onFinish: () -> Unit) {
    val latestOnFinish by rememberUpdatedState(onFinish)
    LaunchedEffect(result.decision.requestId) {
        delay(15_000)
        latestOnFinish()
    }
    val journeyLocations = result.decision.journey.sortedBy { it.order }.mapNotNull { stop ->
        stop.location?.let { location ->
            val x = location.markerXPercent ?: return@let null
            val y = location.markerYPercent ?: return@let null
            DisplayMapStop(stop.order, location.code, location.title, x, y)
        }
    }
    val legacyLocation = result.config.catalog.locations.firstOrNull { it.id == result.decision.item.locationId }
    val destinations = journeyLocations.ifEmpty {
        legacyLocation?.let { listOf(DisplayMapStop(1, it.code, it.title, it.markerXPercent, it.markerYPercent)) }.orEmpty()
    }
    MapPresentation(result.config, destinations, result.decision.expectedJourneyStopCount, onFinish)
}

@Composable
private fun MapPresentation(config: ProjectConfiguration, destinations: List<DisplayMapStop>, expectedStopCount: Int, onFinish: () -> Unit) {
    val font = remember { FontFamily(Font(R.font.sb_aggro_medium, FontWeight.Normal), Font(R.font.sb_aggro_bold, FontWeight.Bold)) }
    val base = MaterialTheme.typography
    MaterialTheme(typography = base.copy(
        bodyLarge = base.bodyLarge.copy(fontFamily = font),
        bodyMedium = base.bodyMedium.copy(fontFamily = font),
        labelLarge = base.labelLarge.copy(fontFamily = font),
        headlineSmall = base.headlineSmall.copy(fontFamily = font),
    )) {
        ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = font)) {
            MapPresentationContent(config, destinations, expectedStopCount, onFinish)
        }
    }
}

@Composable
private fun MapPresentationContent(config: ProjectConfiguration, destinations: List<DisplayMapStop>, expectedStopCount: Int, onFinish: () -> Unit) {
    if (destinations.isEmpty()) {
        Centered {
            Text(uiText(config.selectedLanguage, "표시할 지도 위치가 없습니다.", "No map location is available.", "没有可显示的地图位置。", "表示できる地図位置がありません。"))
            Button(onClick = onFinish) { Text(uiText(config.selectedLanguage, "처음으로", "Home", "返回首页", "最初に戻る")) }
        }
        return
    }
    var scale by remember(config.catalog.projectId) {
        mutableFloatStateOf(if (config.catalog.projectId.value.contains("UIRYEONG", ignoreCase = true)) 1f else 1.2f)
    }
    var translation by remember { mutableStateOf(Offset.Zero) }
    val dashPhase by rememberInfiniteTransition(label = "route-dashes").animateFloat(
        initialValue = 0f,
        targetValue = -40f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "route-dash-phase",
    )
    val operationNotice = journeyOperationNotice(destinations.size, expectedStopCount, config.selectedLanguage)
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFFF4E9CB)).clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
            val mapAspect = 16f / 10f
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
                    update = {
                        if (config.catalog.projectId.value.contains("UIRYEONG", ignoreCase = true)) {
                            it.setImageResource(R.drawable.richrich_map_sotbawi_integrated)
                            // Recede the artwork with a light veil and softer contrast;
                            // keep colored UI and route overlays unaffected.
                            val mapColors = android.graphics.ColorMatrix().apply { setSaturation(.78f) }
                            mapColors.postConcat(android.graphics.ColorMatrix(floatArrayOf(
                                .78f, 0f, 0f, 0f, 52f,
                                0f, .78f, 0f, 0f, 52f,
                                0f, 0f, .78f, 0f, 48f,
                                0f, 0f, 0f, 1f, 0f,
                            )))
                            it.colorFilter = android.graphics.ColorMatrixColorFilter(mapColors)
                        } else {
                            it.clearColorFilter()
                            it.setImageURI(Uri.parse(config.theme.mapImageRef))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                Canvas(Modifier.fillMaxSize()) {
                    val route = buildList {
                        add(config.navigation.origin)
                        destinations.forEach { destination ->
                            addAll(config.navigation.routesByLocationCode[destination.code].orEmpty())
                            add(MapPoint(destination.xPercent, destination.yPercent))
                        }
                    }
                    val path = Path().apply {
                        route.forEachIndexed { index, point ->
                            val px = (point.xPercent / 100.0).toFloat() * size.width
                            val py = (point.yPercent / 100.0).toFloat() * size.height
                            if (index == 0) moveTo(px, py) else lineTo(px, py)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 11f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 18f), dashPhase),
                        ),
                    )
                    drawPath(
                        path = path,
                        color = Color(0xFFE53935),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 6f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 18f), dashPhase),
                        ),
                    )
                }
                MapMarker(
                    mapWidth, mapHeight,
                    config.navigation.origin.xPercent,
                    config.navigation.origin.yPercent,
                    config.content.currentLocationLabel,
                    Color(0xFF00A8D9),
                )
                destinations.forEachIndexed { index, destination ->
                    val colors = listOf(Color(0xFFE53935), Color(0xFF1479E8), Color(0xFF12A94A))
                    MapMarker(mapWidth, mapHeight, destination.xPercent, destination.yPercent, "${destination.order}. ${destination.title}", colors[index % colors.size])
                }
            }
            Image(
                painter = painterResource(R.drawable.richrich_festival_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp).width(190.dp).height(76.dp),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .border(3.dp, Color(0xFF10213A), RoundedCornerShape(26.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF1169D9), Color(0xFF073C9A))), RoundedCornerShape(26.dp))
                    .padding(horizontal = 26.dp, vertical = 12.dp),
            ) {
                Text("당신을 부자로 만드는 감각을 만나러 가보세요!", color = Color.White,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(destinations.joinToString("  →  ") { "${it.order}. ${it.title}" },
                    color = Color(0xFFFFDE78), fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
                operationNotice?.let { notice ->
                    Text("⚠ $notice", color = Color(0xFFFFE7A3), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Box(
                Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 16.dp).width(278.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.richrich_map_mascot),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (-5).dp).width(164.dp).height(102.dp)
                        .graphicsLayer { scaleX = -1f }
                        .zIndex(1f),
                )
                Column(
                    Modifier.padding(top = 78.dp).fillMaxWidth()
                        .border(3.dp, Color(0xFF101B2C), RoundedCornerShape(24.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xD10C4B9F), Color(0xD1062D70))), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    Text("✦  추천 감각 여정", color = Color(0xFFFFD75B), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    destinations.forEachIndexed { index, stop ->
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Normal,
                                fontSize = 13.sp, modifier = Modifier.width(24.dp).alignByBaseline())
                            Text(stop.title, color = Color.White, fontWeight = FontWeight.Normal,
                                fontSize = 13.sp, modifier = Modifier.weight(1f).alignByBaseline())
                        }
                    }
                }
            }
            Text(
                config.content.mapGestureHint,
                style = LocalTextStyle.current.copy(baselineShift = androidx.compose.ui.text.style.BaselineShift(-.10f)),
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .border(2.dp, Color(0xFF10213A), RoundedCornerShape(20.dp))
                    .background(Color(0xE80A438B), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
            Row(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.size(width = 180.dp, height = 52.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF5A3411)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC329), contentColor = Color(0xFF241607)),
                ) {
                    MapActionIcon(true, Color(0xFF241607))
                    Spacer(Modifier.width(6.dp))
                    Text(uiText(config.selectedLanguage, "처음으로", "Home", "返回首页", "最初に戻る"), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        style = LocalTextStyle.current.copy(baselineShift = androidx.compose.ui.text.style.BaselineShift(-.10f)))
                }
            }
        }
}

@Composable
private fun MapActionIcon(home: Boolean, color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round)
        if (home) {
            // Restore the previous house glyph, centered by its visible bounds.
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = android.graphics.Color.rgb(36, 22, 7)
                textSize = w * .92f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val bounds = android.graphics.Rect()
            paint.getTextBounds("⌂", 0, 1, bounds)
            drawContext.canvas.nativeCanvas.drawText("⌂", w / 2f, w / 2f - (bounds.top + bounds.bottom) / 2f - 2.dp.toPx(), paint)
        } else {
            drawArc(color, -55f, 290f, false, Offset(w * .18f, w * .18f),
                androidx.compose.ui.geometry.Size(w * .64f, w * .64f), style = stroke)
            val arrow = Path().apply {
                moveTo(w * .12f, w * .21f); lineTo(w * .35f, w * .24f); lineTo(w * .29f, w * .46f)
            }
            drawPath(arrow, color, style = stroke)
        }
    }
}

private data class DisplayMapStop(
    val order: Int,
    val code: String,
    val title: String,
    val xPercent: Double,
    val yPercent: Double,
)

private fun uiText(language: String?, ko: String, en: String, zh: String, ja: String): String = when (language) {
    "en" -> en
    "zh" -> zh
    "ja" -> ja
    else -> ko
}

@Composable
private fun MapMarker(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, x: Double, y: Double, label: String, color: Color) {
    val markerX = width * (x / 100.0).toFloat()
    val markerY = height * (y / 100.0).toFloat()
    val labelWidth = 190.dp
    val labelX = (markerX - labelWidth / 2).coerceIn(4.dp, (width - labelWidth - 4.dp).coerceAtLeast(4.dp))
    val labelY = (markerY - 58.dp).coerceAtLeast(4.dp)
    Box(Modifier.size(width, height)) {
        Text(
            label,
            style = LocalTextStyle.current.copy(baselineShift = androidx.compose.ui.text.style.BaselineShift(-.10f)),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .offset(x = labelX, y = labelY)
                .width(labelWidth)
                .border(3.dp, Color(0xFF111827), RoundedCornerShape(9.dp))
                .background(color, RoundedCornerShape(9.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Box(
            Modifier
                .offset(x = markerX - 12.dp, y = markerY - 12.dp)
                .size(24.dp)
                .border(2.dp, Color(0xFF111827), CircleShape)
                .background(color, CircleShape),
        )
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
