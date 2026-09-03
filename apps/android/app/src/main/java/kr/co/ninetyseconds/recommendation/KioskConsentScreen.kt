package kr.co.ninetyseconds.recommendation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.ninetyseconds.recommendation.domain.ConsentStatus
import kr.co.ninetyseconds.recommendation.domain.ParticipantProfile
import kr.co.ninetyseconds.recommendation.domain.ProjectConfiguration

private enum class EntryField { NAME, PHONE, BIRTH }

private data class StartCopy(
    val title: String, val subtitle: String, val name: String, val phone: String, val birth: String,
    val gender: String, val male: String, val female: String, val other: String,
    val start: String, val anonymous: String, val privacyTitle: String,
    val privacy1: String, val privacy2: String, val privacy3: String,
    val delete: String, val clear: String, val keyboardHint: String,
    val errorName: String, val errorPhone: String, val errorBirth: String, val errorGender: String,
)

private val startCopies = mapOf(
    "ko" to StartCopy("AI 치유 꽃 추천", "\"당신의 마음 꽃은 무엇입니까?\"", "이름을 입력해주세요. (최대 5자)", "전화번호를 입력해주세요.", "생년월일 (예: 950101)", "성별", "남성", "여성", "논바이너리", "분석 시작하기", "개인정보 없이\n바로 시작", "<개인정보 수집·이용 안내>", "1. 목적: 본인 확인 및 안내 메시지 발송", "2. 수집 항목: 성명, 연락처, 생년월일, 성별", "3. 보유 기간: 서비스 종료 후 지체 없이 파기", "지우기", "전체삭제", "왼쪽 입력칸을 터치하면 키보드가 나타납니다.", "이름을 입력해주세요.", "전화번호를 올바르게 입력해주세요.", "생년월일 6자리를 입력해주세요.", "성별을 선택해주세요."),
    "en" to StartCopy("AI Healing Flower Guide", "\"What is the flower of your heart?\"", "Please enter your name.", "Please enter your phone number.", "Birth date (e.g. 950101)", "Gender", "Male", "Female", "Non-binary", "Start Analysis", "Start without\npersonal info", "<Privacy Notice>", "1. Purpose: Verification and guide messages", "2. Items: Name, phone, birth date, gender", "3. Retention: Deleted promptly after service", "Delete", "Clear", "Touch an input field to show the keyboard.", "Please enter your name.", "Please enter a valid phone number.", "Please enter 6 digits for your birth date.", "Please select your gender."),
    "zh" to StartCopy("AI 疗愈花推荐", "\"你的心之花是什么？\"", "请输入您的姓名。", "请输入您的电话号码。", "出生日期（例如：950101）", "性别", "男", "女", "非二元", "开始分析", "无需个人信息\n直接开始", "<个人信息收集·使用说明>", "1. 目的：用于身份确认及发送通知信息", "2. 收集项目：姓名、联系方式、出生日期、性别", "3. 保存期限：服务结束后立即销毁", "删除", "全部删除", "点击左侧输入框即可显示键盘。", "请输入姓名。", "请输入正确的电话号码。", "请输入6位出生日期。", "请选择性别。"),
    "ja" to StartCopy("AI 癒やしの花おすすめ", "\"あなたの心の花は何ですか？\"", "お名前を入力してください。", "電話番号を入力してください。", "生年月日（例：950101）", "性別", "男性", "女性", "ノンバイナリー", "分析を開始する", "個人情報なしで\n開始", "<個人情報の収集・利用について>", "1. 目的：本人確認および案内メッセージ送信", "2. 収集項目：氏名、連絡先、生年月日、性別", "3. 保有期間：サービス終了後、遅滞なく破棄", "削除", "すべて削除", "左側の入力欄をタッチするとキーボードが表示されます。", "名前を入力してください。", "正しい電話番号を入力してください。", "生年月日を6桁で入力してください。", "性別を選択してください。"),
)

@Composable
internal fun KioskConsentScreen(
    config: ProjectConfiguration,
    onLanguageChange: (String) -> Unit,
    onSelect: (ConsentStatus, ParticipantProfile?) -> Unit,
    onSettings: () -> Unit,
) {
    val language = config.selectedLanguage
    val copy = startCopies[language] ?: startCopies.getValue("ko")
    var nameKeys by remember(language) { mutableStateOf("") }
    var phone by remember(language) { mutableStateOf("") }
    var birth by remember(language) { mutableStateOf("") }
    var gender by remember(language) { mutableStateOf("") }
    var activeField by remember(language) { mutableStateOf(EntryField.NAME) }
    var shifted by remember(language) { mutableStateOf(false) }
    var error by remember(language) { mutableStateOf<String?>(null) }
    val name = if (language == "ko") assembleHangul(nameKeys) else nameKeys

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Image(
            painter = painterResource(R.drawable.taean_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .10f)))
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 34.dp)
                .zIndex(2f).clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = .72f)).padding(4.dp),
        ) {
            listOf("ko" to "한국어", "en" to "English", "zh" to "中文", "ja" to "日本語").forEach { (code, label) ->
                Text(
                    label,
                    color = if (code == language) Color.White else Color(0xFF66656A),
                    fontWeight = if (code == language) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(28.dp))
                        .background(if (code == language) Color(0xFFE191A9) else Color.Transparent)
                        .clickable(enabled = code != language) { onLanguageChange(code) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 52.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(50.dp))
            Text(copy.title, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(copy.subtitle, fontSize = 19.sp, color = Color.White, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(20.dp))
            Surface(
                color = Color(0xFFEFC7D1).copy(alpha = .84f),
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.fillMaxWidth(.86f).wrapContentHeight(),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 28.dp, top = 24.dp, end = 28.dp, bottom = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(34.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(.95f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            KioskInput(name.ifBlank { copy.name }, name.isBlank(), activeField == EntryField.NAME) { activeField = EntryField.NAME }
                            KioskInput(formatPhone(phone).ifBlank { copy.phone }, phone.isBlank(), activeField == EntryField.PHONE) { activeField = EntryField.PHONE }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GenderSelector(copy, gender, { gender = it }, Modifier.weight(.85f))
                                KioskInput(birth.ifBlank { copy.birth }, birth.isBlank(), activeField == EntryField.BIRTH, Modifier.weight(1.35f)) { activeField = EntryField.BIRTH }
                            }
                            error?.let { Text(it, color = Color(0xFF9A203E), fontWeight = FontWeight.Bold) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        error = when {
                                            name.isBlank() -> copy.errorName
                                            phone.length !in 10..11 -> copy.errorPhone
                                            birth.length != 6 -> copy.errorBirth
                                            gender.isBlank() -> copy.errorGender
                                            else -> null
                                        }
                                        if (error == null) onSelect(ConsentStatus.CONSENTED, ParticipantProfile(name, phone, expandBirthDate(birth), gender))
                                    },
                                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFC34770)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF5E2540), containerColor = Color.White.copy(alpha = .35f)),
                                    modifier = Modifier.weight(1.45f).height(64.dp),
                                ) { Text(copy.start, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                                Button(
                                    onClick = { onSelect(ConsentStatus.DECLINED, null) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF606873), contentColor = Color(0xFFFFE17A)),
                                    modifier = Modifier.weight(.9f).height(64.dp),
                                ) { Text(copy.anonymous, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
                            }
                        }
                        KioskKeyboard(
                            language = language,
                            numeric = activeField != EntryField.NAME,
                            shifted = shifted,
                            copy = copy,
                            modifier = Modifier.weight(1.15f),
                            onShift = { shifted = !shifted },
                            onKey = { key ->
                                when (activeField) {
                                    EntryField.NAME -> if (name.length < 10) { nameKeys += if (shifted && language != "ko") key.uppercase() else key; shifted = false }
                                    EntryField.PHONE -> if (phone.length < 11) phone += key.filter(Char::isDigit)
                                    EntryField.BIRTH -> if (birth.length < 6) birth += key.filter(Char::isDigit)
                                }
                            },
                            onDelete = {
                                when (activeField) {
                                    EntryField.NAME -> nameKeys = nameKeys.dropLast(1)
                                    EntryField.PHONE -> phone = phone.dropLast(1)
                                    EntryField.BIRTH -> birth = birth.dropLast(1)
                                }
                            },
                            onClear = {
                                when (activeField) {
                                    EntryField.NAME -> nameKeys = ""
                                    EntryField.PHONE -> phone = ""
                                    EntryField.BIRTH -> birth = ""
                                }
                            },
                        )
                    }
                    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .30f)).padding(horizontal = 36.dp, vertical = 14.dp)) {
                        Text(copy.privacyTitle, color = Color(0xFFB52F55), fontWeight = FontWeight.Bold)
                        Text("${copy.privacy1}    ${copy.privacy2}    ${copy.privacy3}", color = Color(0xFF8D3B52), fontSize = 12.sp)
                    }
                }
            }
        }
        Text("⚙", fontSize = 22.sp, modifier = Modifier.align(Alignment.BottomEnd).clickable(onClick = onSettings).padding(18.dp))
    }
}

@Composable
private fun KioskInput(value: String, placeholder: Boolean, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = .88f), shape = RoundedCornerShape(34.dp),
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFC34770) else Color(0xFFBE8298)),
        modifier = modifier.fillMaxWidth().height(58.dp).clickable(onClick = onClick),
    ) { Box(Modifier.padding(horizontal = 22.dp), contentAlignment = Alignment.CenterStart) { Text(value, color = if (placeholder) Color(0xFF77777D) else Color(0xFF25252A), fontSize = 16.sp, fontWeight = if (placeholder) FontWeight.Normal else FontWeight.SemiBold) } }
}

@Composable
private fun GenderSelector(copy: StartCopy, selected: String, onSelect: (String) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        KioskInput(when (selected) { "M" -> copy.male; "F" -> copy.female; "N" -> copy.other; else -> copy.gender }, selected.isBlank(), false) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf("M" to copy.male, "F" to copy.female, "N" to copy.other).forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(value); open = false }) }
        }
    }
}

@Composable
private fun KioskKeyboard(language: String, numeric: Boolean, shifted: Boolean, copy: StartCopy, modifier: Modifier, onShift: () -> Unit, onKey: (String) -> Unit, onDelete: () -> Unit, onClear: () -> Unit) {
    val rows = when {
        numeric -> listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(copy.clear, "0", copy.delete))
        language == "ko" -> listOf(listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"), listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ"), listOf("Shift", "ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ", copy.delete), listOf("Space"))
        language == "ja" -> listOf(listOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ"), listOf("い", "き", "し", "ち", "に", "ひ", "み", "ゆ", "り", "を"), listOf("う", "く", "す", "つ", "ぬ", "ふ", "む", "よ", "る", "ん"), listOf("え", "け", "せ", "て", "ね", "へ", "め", "れ", copy.delete), listOf("お", "こ", "そ", "と", "の", "ほ", "も", "ろ", "Space"))
        else -> listOf(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"), listOf("Shift", "z", "x", "c", "v", "b", "n", "m", copy.delete), listOf("Space"))
    }
    val shiftMap = mapOf("ㅂ" to "ㅃ", "ㅈ" to "ㅉ", "ㄷ" to "ㄸ", "ㄱ" to "ㄲ", "ㅅ" to "ㅆ", "ㅐ" to "ㅒ", "ㅔ" to "ㅖ")
    Column(modifier, verticalArrangement = Arrangement.Top) {
        if (!numeric && language in setOf("zh", "ja")) Text(if (language == "zh") "拼音键盘" else "ひらがなキーボード", color = Color(0xFF6B5360), modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp))
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { raw ->
                    val label = when { shifted && language == "ko" -> shiftMap[raw] ?: raw; shifted && !numeric && raw.length == 1 -> raw.uppercase(); else -> raw }
                    val special = raw in setOf("Shift", "Space", copy.delete, copy.clear)
                    Button(
                        onClick = { when (raw) { "Shift" -> onShift(); "Space" -> onKey(" "); copy.delete -> onDelete(); copy.clear -> onClear(); else -> onKey(label) } },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (raw == copy.delete) Color(0xFFC95C78) else Color.White.copy(alpha = .86f), contentColor = if (raw == copy.delete) Color.White else Color(0xFF46434A)),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        modifier = Modifier.weight(if (raw == "Space") 8f else if (special) 1.5f else 1f).height(if (numeric) 62.dp else 65.dp).padding(vertical = 3.dp),
                    ) { Text(label, fontSize = if (special) 13.sp else 18.sp) }
                }
            }
        }
    }
}

private fun formatPhone(value: String): String = when {
    value.length <= 3 -> value
    value.length <= 7 -> "${value.take(3)}-${value.drop(3)}"
    else -> "${value.take(3)}-${value.drop(3).take(4)}-${value.drop(7)}"
}

private fun expandBirthDate(value: String): String {
    if (value.length != 6) return value
    val century = if ((value.take(2).toIntOrNull() ?: 0) > 30) "19" else "20"
    return "$century${value.take(2)}-${value.substring(2, 4)}-${value.takeLast(2)}"
}

private fun assembleHangul(keys: String): String {
    val initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    val vowels = listOf("ㅏ","ㅐ","ㅑ","ㅒ","ㅓ","ㅔ","ㅕ","ㅖ","ㅗ","ㅘ","ㅙ","ㅚ","ㅛ","ㅜ","ㅝ","ㅞ","ㅟ","ㅠ","ㅡ","ㅢ","ㅣ")
    val finals = listOf("","ㄱ","ㄲ","ㄳ","ㄴ","ㄵ","ㄶ","ㄷ","ㄹ","ㄺ","ㄻ","ㄼ","ㄽ","ㄾ","ㄿ","ㅀ","ㅁ","ㅂ","ㅄ","ㅅ","ㅆ","ㅇ","ㅈ","ㅊ","ㅋ","ㅌ","ㅍ","ㅎ")
    val out = StringBuilder(); var l = -1; var v = -1; var t = 0
    fun flush() { if (l >= 0 && v >= 0) out.append((0xAC00 + (l * 21 + v) * 28 + t).toChar()) else if (l >= 0) out.append(initials[l]); l = -1; v = -1; t = 0 }
    keys.forEach { ch ->
        val li = initials.indexOf(ch); val vi = vowels.indexOf(ch.toString())
        if (vi >= 0) {
            if (l < 0) out.append(ch) else if (v < 0) v = vi else if (t > 0) { val moved = finals[t].first(); t = 0; flush(); l = initials.indexOf(moved); v = vi } else { flush(); out.append(ch) }
        } else if (li >= 0) {
            if (l < 0) l = li else if (v < 0) { flush(); l = li } else if (t == 0 && finals.indexOf(ch.toString()) > 0) t = finals.indexOf(ch.toString()) else { flush(); l = li }
        } else { flush(); out.append(ch) }
    }
    flush(); return out.toString()
}
