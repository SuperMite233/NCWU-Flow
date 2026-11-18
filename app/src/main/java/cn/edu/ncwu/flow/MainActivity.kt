package cn.edu.ncwu.flow

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.edu.ncwu.flow.data.CampusRepository
import cn.edu.ncwu.flow.data.CredentialSettings
import cn.edu.ncwu.flow.data.LoginMode
import cn.edu.ncwu.flow.background.BackgroundSettings
import cn.edu.ncwu.flow.background.ScanCategory
import cn.edu.ncwu.flow.model.ActivityItem
import cn.edu.ncwu.flow.model.CompetitionItem
import cn.edu.ncwu.flow.model.CompetitionMessage
import cn.edu.ncwu.flow.model.ImportedVerificationCode
import cn.edu.ncwu.flow.model.RegisteredActivityItem
import cn.edu.ncwu.flow.model.RegisteredActivityPage
import cn.edu.ncwu.flow.model.ScoreTerm
import cn.edu.ncwu.flow.model.SortOrder
import cn.edu.ncwu.flow.ui.theme.NcwuFlowTheme
import cn.edu.ncwu.flow.ui.theme.ThemeMode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val incomingImportUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CookieManager.getInstance().setAcceptCookie(true)
        incomingImportUri.value = intent.extractImportUri()
        setContent {
            NcwuFlowApp(
                initialImportUri = incomingImportUri.value,
                onImportConsumed = { incomingImportUri.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingImportUri.value = intent.extractImportUri()
    }

    private fun Intent.extractImportUri(): Uri? = when {
        action == Intent.ACTION_SEND -> getParcelableExtra(Intent.EXTRA_STREAM)
            ?: clipData?.getItemAt(0)?.uri
        action == Intent.ACTION_VIEW -> data
        else -> null
    }
}

@Composable
private fun SecondaryMenu(labels: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            FilterChip(selected = selected == index, onClick = { onSelected(index) }, label = { Text(label) })
        }
    }
}

@Composable
private fun VerificationScreen(
    activities: List<ActivityItem>,
    importedCodes: List<ImportedVerificationCode>,
    onImportCodes: () -> Unit,
    onLogin: () -> Unit,
    onVerifyCode: (String, ActivityItem?) -> Unit,
    onVerifyQr: (String) -> Unit,
    onOpenOfficial: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val scanner = remember {
        GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build(),
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("输入活动验证码", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("验证码") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { onVerifyCode(code, null) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("提交验证") }
                        OutlinedButton(
                            onClick = {
                                scanner.startScan().addOnSuccessListener { result -> result.rawValue?.let(onVerifyQr) }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("扫描二维码")
                        }
                    }
                    OutlinedButton(onClick = onImportCodes, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.Default.Article, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("导入验证码表格")
                    }
                    Text("支持学校验证二维码；需要定位或人脸时请打开官方验证页。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onOpenOfficial) { Text("打开学校官方验证页") }
                }
            }
        }
        if (importedCodes.isNotEmpty()) {
            item {
                Text("已导入验证码（按当前学号匹配）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            items(importedCodes, key = { "imported-${it.activityName}-${it.code}" }) { imported ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    ListItem(
                        headlineContent = { Text(imported.activityName.ifBlank { "未命名活动" }) },
                        supportingContent = { Text("验证码：${imported.code}") },
                        trailingContent = { Text(imported.verifiedAt.ifBlank { "待验证" }, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
        item { Text("待验证活动", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        val pending = activities.filter {
            val pendingState = it.state.contains("待验证") ||
                it.verificationState.contains("待验证") ||
                it.verificationState.contains("未核算")
            pendingState && !it.verificationState.contains("过期") && !isVerificationExpired(it)
        }
        if (pending.isEmpty()) {
            item { Text("当前没有待验证活动", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(pending, key = { it.id }) { item ->
                val imported = importedCodes.firstOrNull { code ->
                    code.activityName.isNotBlank() &&
                        (code.activityName == item.name || code.activityName.contains(item.name) || item.name.contains(code.activityName))
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = {
                            Text(
                                listOf(
                                    item.activityEnd.ifBlank { "活动时间未公布" },
                                    item.verificationState.ifBlank { "待验证" },
                                    if (imported != null) "已匹配验证码" else "未匹配验证码",
                                ).joinToString(" · "),
                            )
                        },
                        leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                imported?.let {
                                    TextButton(onClick = { code = it.code }) { Text("填入") }
                                }
                                TextButton(onClick = { onVerifyCode(code, item) }) { Text("验证") }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreScreen(state: SourceState<ScoreTerm>, goal: Double, onRefresh: () -> Unit, onLogin: () -> Unit) {
    val latest = state.items.filter { it.termIndex < 100 }.maxByOrNull { it.termIndex }
    SourceListLayout(
        loading = state.loading,
        authenticationRequired = state.authenticationRequired,
        error = state.error,
        empty = state.items.isEmpty(),
        emptyText = "暂无成绩数据",
        onLogin = onLogin,
        onRefresh = onRefresh,
    ) {
        latest?.let { term ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("最新学期目标", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${term.schoolYear} ${term.schoolTerm} · ${term.total.formatScore()} / ${goal.formatScore()} 分")
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { (term.total / goal.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        items(state.items.filter { it.termIndex < 100 }, key = { "${it.schoolYear}-${it.schoolTerm}-${it.termIndex}" }) { term ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${term.schoolYear} ${term.schoolTerm}".trim(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("总计 ${term.total.formatScore()} 分 · 志愿服务加分 ${term.volunteer.formatScore()} 分", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    term.categories.forEach { (name, score) -> DetailLine(name, "${score.formatScore()} 分") }
                }
            }
        }
    }
}

@Composable
private fun RegisteredActivityScreen(state: PagedSourceState<RegisteredActivityPage>, onRefresh: (Int) -> Unit, onLogin: () -> Unit) {
    val page = state.data
    Column(Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.authenticationRequired -> StateMessage(Icons.AutoMirrored.Filled.Login, "需要登录", "请先登录第二课堂。", "登录", onLogin)
            state.error != null -> StateMessage(Icons.Default.Refresh, "加载失败", state.error, "重试") { onRefresh(page?.page ?: 1) }
            page == null || page.items.isEmpty() -> StateMessage(Icons.Default.Person, "暂无已报活动", "报名后将在这里按页展示。", "刷新") { onRefresh(1) }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(page.items, key = { it.id }) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                AssistChip(onClick = {}, label = { Text(item.status) })
                            }
                            if (item.type.isNotBlank()) Text(item.type, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("活动时间：${item.activityStart.ifBlank { "未公布" }} 至 ${item.activityEnd.ifBlank { "未公布" }}", style = MaterialTheme.typography.bodySmall)
                            Text("成绩：${item.score.formatScore()} 分 · 加分：${item.bonusScore.formatScore()} 分", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onRefresh(page.page - 1) }, enabled = page.page > 1) { Text("上一页") }
                        Text("第 ${page.page} / ${page.totalPages} 页", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onRefresh(page.page + 1) }, enabled = page.page < page.totalPages) { Text("下一页") }
                    }
                }
            }
        }
    }
}

private fun Double.formatScore(): String = if (this % 1.0 == 0.0) toInt().toString() else String.format(java.util.Locale.CHINA, "%.1f", this)

private enum class Destination(val label: String) {
    ACTIVITIES("第二课堂"), COMPETITIONS("竞赛列表"), SETTINGS("设置")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NcwuFlowApp(
    initialImportUri: Uri? = null,
    onImportConsumed: () -> Unit = {},
    viewModel: MainViewModel = viewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val activities by viewModel.activities.collectAsStateWithLifecycle()
    val competitions by viewModel.competitions.collectAsStateWithLifecycle()
    val competitionMessages by viewModel.competitionMessages.collectAsStateWithLifecycle()
    val scores by viewModel.scores.collectAsStateWithLifecycle()
    val registeredActivities by viewModel.registeredActivities.collectAsStateWithLifecycle()
    val importedVerificationCodes by viewModel.importedVerificationCodes.collectAsStateWithLifecycle()
    val scoreGoal by viewModel.scoreGoal.collectAsStateWithLifecycle()
    val backgroundSettings by viewModel.backgroundSettings.collectAsStateWithLifecycle()
    val credentialSettings by viewModel.credentialSettings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var destination by rememberSaveable { mutableStateOf(Destination.ACTIVITIES) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setNotificationsEnabled(granted) }
    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importVerificationFile) }

    LaunchedEffect(initialImportUri) {
        initialImportUri?.let {
            viewModel.importVerificationFile(it)
            onImportConsumed()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { snackbar.showSnackbar(it) }
    }

    NcwuFlowTheme(themeMode) {
        val context = LocalContext.current
        val systemDark = isSystemInDarkTheme()
        val darkBars = themeMode == ThemeMode.DARK || (themeMode == ThemeMode.SYSTEM && systemDark)
        SideEffect {
            (context as? ComponentActivity)?.enableEdgeToEdge(
                statusBarStyle = if (darkBars) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                },
                navigationBarStyle = if (darkBars) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                },
            )
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(destination.label, fontWeight = FontWeight.SemiBold)
                            if (destination != Destination.SETTINGS) {
                                Text(
                                    "华北水利水电大学",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            bottomBar = {
                NavigationBar {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = {
                                Icon(
                                    when (item) {
                                        Destination.ACTIVITIES -> Icons.Default.EventAvailable
                                        Destination.COMPETITIONS -> Icons.Default.School
                                        Destination.SETTINGS -> Icons.Default.Settings
                                    },
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            AnimatedContent(destination, label = "navigation") { current ->
                when (current) {
                    Destination.ACTIVITIES -> ActivityScreen(
                        state = activities,
                        onRefresh = viewModel::refreshActivities,
                        onAuthenticated = {
                            viewModel.refreshActivities(); viewModel.refreshScores(); viewModel.refreshRegisteredActivities()
                        },
                        onSignUp = viewModel::signUp,
                        onVerifyCode = viewModel::verifyCode,
                        onVerifyQr = viewModel::verifyQr,
                        importedCodes = importedVerificationCodes,
                        onImportCodes = { importFileLauncher.launch(arrayOf(
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "text/csv",
                            "text/plain",
                            "application/octet-stream",
                        )) },
                        scores = scores,
                        scoreGoal = scoreGoal,
                        onRefreshScores = viewModel::refreshScores,
                        registered = registeredActivities,
                        onRefreshRegistered = viewModel::refreshRegisteredActivities,
                        modifier = Modifier.padding(padding),
                    )
                    Destination.COMPETITIONS -> CompetitionScreen(
                        state = competitions,
                        onRefresh = viewModel::refreshCompetitions,
                        onAuthenticated = { viewModel.refreshCompetitions(); viewModel.refreshCompetitionMessages() },
                        messages = competitionMessages,
                        onRefreshMessages = viewModel::refreshCompetitionMessages,
                        modifier = Modifier.padding(padding),
                    )
                    Destination.SETTINGS -> SettingsScreen(
                        mode = themeMode,
                        backgroundSettings = backgroundSettings,
                        onModeChanged = viewModel::setThemeMode,
                        onBackgroundChanged = { enabled ->
                            viewModel.setBackgroundEnabled(enabled)
                            if (enabled && backgroundSettings.notificationsEnabled && Build.VERSION.SDK_INT >= 33) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onNotificationsChanged = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= 33) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else viewModel.setNotificationsEnabled(enabled)
                        },
                        onIntervalChanged = viewModel::setScanInterval,
                        onCategoryChanged = viewModel::setScanCategory,
                        credentialSettings = credentialSettings,
                        onSaveCredentials = viewModel::saveCredentials,
                        onUseManualLogin = viewModel::useManualLogin,
                        onUseStoredLogin = viewModel::useStoredLogin,
                        onClearStoredCredentials = viewModel::clearStoredCredentials,
                        scoreGoal = scoreGoal,
                        onScoreGoalChanged = viewModel::setScoreGoal,
                        onClearSessions = viewModel::clearSessions,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

private enum class ActivitySection(val label: String) {
    SIGNUP("活动报名"), VERIFY("活动验证"), SCORES("成绩单"), REGISTERED("已报活动")
}

@Composable
private fun ActivityScreen(
    state: SourceState<ActivityItem>,
    onRefresh: () -> Unit,
    onAuthenticated: () -> Unit,
    onSignUp: (ActivityItem) -> Unit,
    onVerifyCode: (String, ActivityItem?) -> Unit,
    onVerifyQr: (String) -> Unit,
    importedCodes: List<ImportedVerificationCode>,
    onImportCodes: () -> Unit,
    scores: SourceState<ScoreTerm>,
    scoreGoal: Double,
    onRefreshScores: () -> Unit,
    registered: PagedSourceState<RegisteredActivityPage>,
    onRefreshRegistered: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var showOfficialVerification by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(ActivitySection.SIGNUP) }
    var detail by remember { mutableStateOf<ActivityItem?>(null) }
    var pendingSignUp by remember { mutableStateOf<ActivityItem?>(null) }

    if (showLogin) {
        SchoolWebPage(
            title = "登录第二课堂",
            initialUrl = CampusRepository.ACTIVITY_URL,
            completion = { url -> url.contains("dekt.ncwu.edu.cn/sys/stu/activity") },
            onClose = { showLogin = false },
            onCompleted = { showLogin = false; onAuthenticated() },
        )
        return
    }
    if (showOfficialVerification) {
        SchoolWebPage(
            title = "学校官方活动验证",
            initialUrl = "https://dekt.ncwu.edu.cn/sys/stu/signup",
            completion = { false },
            onClose = { showOfficialVerification = false },
            onCompleted = {},
        )
        return
    }

    Column(modifier.fillMaxSize()) {
        SecondaryMenu(ActivitySection.entries.map { it.label }, section.ordinal) { section = ActivitySection.entries[it] }
        when (section) {
            ActivitySection.SIGNUP -> SourceListLayout(
                loading = state.loading, authenticationRequired = state.authenticationRequired,
                error = state.error, empty = state.items.isEmpty(), emptyText = "当前没有活动",
                onLogin = { showLogin = true }, onRefresh = onRefresh,
            ) {
                items(state.items, key = { it.id }) { activity ->
                    ActivityCard(item = activity, onClick = { detail = activity }, onSignUp = {
                        if (!activity.state.contains("即将")) {
                            if (activity.customFormKey != null || activity.taskSignUp || activity.seatSelection) detail = activity else pendingSignUp = activity
                        }
                    })
                }
            }
            ActivitySection.VERIFY -> VerificationScreen(
                activities = state.items,
                importedCodes = importedCodes,
                onImportCodes = onImportCodes,
                onLogin = { showLogin = true },
                onVerifyCode = onVerifyCode,
                onVerifyQr = onVerifyQr,
                onOpenOfficial = { showOfficialVerification = true },
            )
            ActivitySection.SCORES -> ScoreScreen(scores, scoreGoal, onRefreshScores, onLogin = { showLogin = true })
            ActivitySection.REGISTERED -> RegisteredActivityScreen(registered, onRefreshRegistered, onLogin = { showLogin = true })
        }
    }

    detail?.let { item ->
        ActivityDetailDialog(
            item = item,
            onDismiss = { detail = null },
            onOfficialDetail = { detail = null },
            onSignUp = {
                detail = null
                if (item.customFormKey != null || item.taskSignUp || item.seatSelection) {
                    // Complex forms are completed on the school's official page inside this app.
                } else pendingSignUp = item
            },
        )
    }

    pendingSignUp?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingSignUp = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            title = { Text("确认报名") },
            text = { Text("将使用你当前的第二课堂登录状态报名“${item.name}”。报名后请留意活动验证要求。") },
            confirmButton = {
                Button(onClick = { pendingSignUp = null; onSignUp(item) }) { Text("确认报名") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSignUp = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ActivityCard(item: ActivityItem, onClick: () -> Unit, onSignUp: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(onClick = {}, label = { Text(item.state.ifBlank { "报名中" }) })
            }
            Text(item.organizer, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MetaRow(Icons.Default.CalendarMonth, "报名截止", item.signUpEnd.ifBlank { "未公布" })
            item.currentParticipants?.let { current ->
                Text(
                    "已报名 $current / ${item.participantLimit?.toString() ?: "不限"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onSignUp,
                enabled = item.registrationAvailable,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(
                    when {
                        item.signedUp -> "已报名"
                        item.state.contains("名额已满") || item.state.contains("满员") -> "名额已满"
                        item.state.contains("不在报名范围") -> "不在报名范围"
                        !item.registrationAvailable && item.state.contains("结束") -> "报名已结束"
                        item.state.contains("待验证") -> "去验证"
                        item.state.contains("即将") -> "尚未开始"
                        !item.registrationAvailable -> "暂不可报名"
                        else -> "报名"
                    },
                )
            }
        }
    }
}

@Composable
private fun ActivityDetailDialog(
    item: ActivityItem,
    onDismiss: () -> Unit,
    onOfficialDetail: () -> Unit,
    onSignUp: () -> Unit,
) {
    var showOfficial by remember { mutableStateOf(false) }
    if (showOfficial) {
        SchoolWebPage(
            title = "活动详情",
            initialUrl = "https://dekt.ncwu.edu.cn/sys/stu/activity/detail?activity_id=${item.id}",
            completion = { false },
            onClose = { showOfficial = false; onOfficialDetail() },
            onCompleted = {},
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailLine("主办单位", item.organizer)
                DetailLine("报名时间", "${item.signUpStart.ifBlank { "未公布" }} 至 ${item.signUpEnd.ifBlank { "未公布" }}")
                DetailLine("活动时间", "${item.activityStart.ifBlank { "未公布" }} 至 ${item.activityEnd.ifBlank { "未公布" }}")
                if (item.location.isNotBlank()) DetailLine("活动地址", item.location)
                if (item.verificationState.isNotBlank()) DetailLine("核算状态", item.verificationState)
                if (item.details.isNotBlank()) Text(item.details)
                TextButton(onClick = { showOfficial = true }) { Text("查看学校原始详情与附件") }
            }
        },
        confirmButton = {
            Button(onClick = if (item.customFormKey != null || item.taskSignUp || item.seatSelection) {
                { showOfficial = true }
            } else onSignUp, enabled = item.registrationAvailable) {
                Text(
                    when {
                        item.signedUp -> "已报名"
                        item.state.contains("名额已满") || item.state.contains("满员") -> "名额已满"
                        item.state.contains("不在报名范围") -> "不在报名范围"
                        !item.registrationAvailable && item.state.contains("结束") -> "报名已结束"
                        item.state.contains("即将") -> "尚未开始"
                        item.customFormKey != null || item.taskSignUp || item.seatSelection -> "前往填写报名信息"
                        !item.registrationAvailable -> "暂不可报名"
                        else -> "报名"
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun CompetitionScreen(
    state: SourceState<CompetitionItem>,
    onRefresh: () -> Unit,
    onAuthenticated: () -> Unit,
    messages: SourceState<CompetitionMessage>,
    onRefreshMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var order by rememberSaveable { mutableStateOf(SortOrder.END_DATE_ASC) }
    var section by rememberSaveable { mutableStateOf(0) }
    var showOldEnded by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<CompetitionItem?>(null) }
    var messageDetail by remember { mutableStateOf<CompetitionMessage?>(null) }

    if (showLogin) {
        SchoolWebPage(
            title = "登录竞赛系统",
            initialUrl = CampusRepository.COMPETITION_ENTRY_URL,
            completion = { url -> url.contains("/queryAllTimeLineNo") },
            onClose = { showLogin = false },
            onCompleted = { showLogin = false; onAuthenticated() },
        )
        return
    }

    val sorted = remember(state.items, order, showOldEnded) {
        val comparator = compareBy<CompetitionItem> { parseSchoolDate(it.endDate) ?: LocalDate.MAX }
        val filtered = if (showOldEnded) state.items else state.items.filterNot { item ->
            val end = parseSchoolDate(item.endDate)
            val explicitlyEnded = item.state.contains("结束") || item.state.contains("截止")
            explicitlyEnded && end != null && end.isBefore(LocalDate.now().minusWeeks(1))
        }
        if (order == SortOrder.END_DATE_ASC) filtered.sortedWith(comparator)
        else filtered.sortedWith(comparator.reversed())
    }
    Column(modifier.fillMaxSize()) {
        SecondaryMenu(listOf("竞赛报名", "竞赛消息"), section) { section = it }
        if (section == 0) {
            SourceListLayout(
                loading = state.loading, authenticationRequired = state.authenticationRequired,
                error = state.error, empty = sorted.isEmpty(), emptyText = "暂无竞赛信息",
                onLogin = { showLogin = true }, onRefresh = onRefresh,
                header = {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp)); Text("按报名截止日期", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        FilterChip(selected = order == SortOrder.END_DATE_ASC, onClick = { order = SortOrder.END_DATE_ASC }, label = { Text("近 → 远") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = order == SortOrder.END_DATE_DESC, onClick = { order = SortOrder.END_DATE_DESC }, label = { Text("远 → 近") })
                    }
                    if (!showOldEnded && state.items.size != sorted.size) {
                        Text("已隐藏 ${state.items.size - sorted.size} 项超过一周的已结束竞赛", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        TextButton(onClick = { showOldEnded = true }) { Text("显示更早竞赛") }
                    }
                },
            ) { items(sorted, key = { "${it.id}-${it.name}" }) { item -> CompetitionCard(item = item, onClick = { detail = item }) } }
        } else {
            SourceListLayout(
                loading = messages.loading, authenticationRequired = messages.authenticationRequired,
                error = messages.error, empty = messages.items.isEmpty(), emptyText = "暂无竞赛消息",
                onLogin = { showLogin = true }, onRefresh = onRefreshMessages,
            ) { items(messages.items, key = { it.id }) { item -> CompetitionMessageCard(item) { messageDetail = item } } }
        }
    }

    detail?.let { item ->
        AlertDialog(
            onDismissRequest = { detail = null },
            icon = { Icon(Icons.Default.School, contentDescription = null) },
            title = { Text(item.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailLine("承办学院", item.college)
                    DetailLine("报名开始", item.startDate.ifBlank { "未公布" })
                    DetailLine("报名结束", item.endDate.ifBlank { "未公布" })
                    DetailLine("当前状态", item.state)
                    if (item.details.isNotBlank()) Text(item.details)
                }
            },
            confirmButton = { Button(onClick = { detail = null }) { Text("完成") } },
        )
    }
    messageDetail?.let { message ->
        AlertDialog(
            onDismissRequest = { messageDetail = null },
            icon = { Icon(Icons.Default.Article, contentDescription = null) },
            title = { Text(message.title) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text(message.date, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { Text(message.content.ifBlank { "暂无正文" }) }
                    message.attachments.forEach { attachment -> item { Text(attachment.label, color = MaterialTheme.colorScheme.primary) } }
                }
            },
            confirmButton = { TextButton(onClick = { messageDetail = null }) { Text("关闭") } },
        )
    }
}

@Composable
private fun CompetitionMessageCard(item: CompetitionMessage, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(item.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.content.ifBlank { "点击查看消息详情" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompetitionCard(item: CompetitionItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = if (item.state.contains("进行")) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(item.state, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            Text(item.college, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            MetaRow(Icons.Default.CalendarMonth, "报名截止", item.endDate.ifBlank { "未公布" })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    mode: ThemeMode,
    backgroundSettings: BackgroundSettings,
    onModeChanged: (ThemeMode) -> Unit,
    onBackgroundChanged: (Boolean) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onIntervalChanged: (Long) -> Unit,
    onCategoryChanged: (ScanCategory) -> Unit,
    credentialSettings: CredentialSettings,
    onSaveCredentials: (String, String) -> Unit,
    onUseManualLogin: () -> Unit,
    onUseStoredLogin: () -> Unit,
    onClearStoredCredentials: () -> Unit,
    scoreGoal: Double,
    onScoreGoalChanged: (Double) -> Unit,
    onClearSessions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearCredentials by remember { mutableStateOf(false) }
    var goalText by rememberSaveable(scoreGoal) { mutableStateOf(scoreGoal.toString()) }
    var username by rememberSaveable(credentialSettings.username) { mutableStateOf(credentialSettings.username) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("外观", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column {
                    ThemeMode.entries.forEachIndexed { index, item ->
                        ListItem(
                            headlineContent = {
                                Text(when (item) { ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色" })
                            },
                            leadingContent = {
                                Icon(
                                    when (item) { ThemeMode.SYSTEM -> Icons.Default.Tune; ThemeMode.LIGHT -> Icons.Default.LightMode; ThemeMode.DARK -> Icons.Default.DarkMode },
                                    contentDescription = null,
                                )
                            },
                            trailingContent = {
                                FilterChip(
                                    selected = mode == item,
                                    onClick = { onModeChanged(item) },
                                    label = { Text(if (mode == item) "已选择" else "选择") },
                                )
                            },
                        )
                        if (index < ThemeMode.entries.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
        item {
            Text("校园网站登录", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("登录方式", fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = credentialSettings.mode == LoginMode.MANUAL,
                            onClick = onUseManualLogin,
                            label = { Text("每次手动输入") },
                        )
                        FilterChip(
                            selected = credentialSettings.mode == LoginMode.STORED,
                            onClick = onUseStoredLogin,
                            enabled = credentialSettings.hasStoredPassword,
                            label = { Text("本机保存密码") },
                        )
                    }
                    Text(
                        "保存模式会在启动、后台扫描及登录态失效时自动续登。密码由 Android Keystore 加密，仅保存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("学号") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (credentialSettings.hasStoredPassword) "密码（修改时请重新输入）" else "密码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                )
                            }
                        },
                    )
                    if (credentialSettings.lastStatus.isNotBlank()) {
                        Text(
                            "状态：${credentialSettings.lastStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (credentialSettings.lastStatus.contains("错误")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onSaveCredentials(username, password); password = "" },
                            enabled = username.isNotBlank() && password.isNotBlank(),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("保存并验证")
                        }
                        if (credentialSettings.hasStoredPassword) {
                            TextButton(onClick = { confirmClearCredentials = true }, modifier = Modifier.height(48.dp)) { Text("删除凭证") }
                        }
                    }
                }
            }
        }
        item {
            Text("成绩目标", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("最新学期总目标（分）", fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = goalText, onValueChange = { goalText = it }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(onClick = { goalText.toDoubleOrNull()?.let(onScoreGoalChanged) }, modifier = Modifier.height(48.dp)) { Text("保存") }
                    }
                }
            }
        }
        item {
            Text("后台扫描", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column {
                    ListItem(
                        headlineContent = { Text("允许后台扫描") },
                        supportingContent = { Text("联网时由系统定期运行，不常驻前台") },
                        leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = backgroundSettings.enabled,
                                onCheckedChange = onBackgroundChanged,
                                modifier = Modifier.semantics { contentDescription = "允许后台扫描" },
                            )
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("发现新信息时通知") },
                        supportingContent = { Text("首次扫描仅建立基线，不发送通知") },
                        leadingContent = { Icon(Icons.Default.EventAvailable, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = backgroundSettings.notificationsEnabled,
                                onCheckedChange = onNotificationsChanged,
                                enabled = backgroundSettings.enabled,
                                modifier = Modifier.semantics { contentDescription = "发现新信息时通知" },
                            )
                        },
                    )
                    HorizontalDivider()
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("扫描频率", fontWeight = FontWeight.Medium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf(15L to "15 分钟", 30L to "30 分钟", 60L to "1 小时", 180L to "3 小时", 360L to "6 小时").forEach { (minutes, label) ->
                                FilterChip(
                                    selected = backgroundSettings.intervalMinutes == minutes,
                                    onClick = { onIntervalChanged(minutes) },
                                    label = { Text(label) },
                                    enabled = backgroundSettings.enabled,
                                )
                            }
                        }
                        Text("扫描类别", fontWeight = FontWeight.Medium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ScanCategory.entries.forEach { category ->
                                FilterChip(
                                    selected = backgroundSettings.category == category,
                                    onClick = { onCategoryChanged(category) },
                                    label = {
                                        Text(when (category) {
                                            ScanCategory.ALL -> "全部"
                                            ScanCategory.ACTIVITIES_ONLY -> "仅第二课堂"
                                            ScanCategory.COMPETITIONS_ONLY -> "仅竞赛"
                                        })
                                    },
                                    enabled = backgroundSettings.enabled,
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)); Text("隐私与登录", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                ListItem(
                    headlineContent = { Text("清除校园网站登录状态") },
                    supportingContent = { Text("仅移除登录 Cookie，不会删除已加密保存的凭证") },
                    leadingContent = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                    trailingContent = { TextButton(onClick = { confirmClear = true }) { Text("清除") } },
                )
            }
        }
        item {
            Text(
                "数据直接来自学校网站。网页结构变化后可能需要更新解析规则。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除登录状态？") },
            text = { Text("之后打开第二课堂或竞赛列表时，需要重新在学校统一身份认证页登录。") },
            confirmButton = { Button(onClick = { confirmClear = false; onClearSessions() }) { Text("清除") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
    if (confirmClearCredentials) {
        AlertDialog(
            onDismissRequest = { confirmClearCredentials = false },
            title = { Text("删除本机凭证？") },
            text = { Text("将删除已加密保存的学号和密码，并切换回手动登录模式。") },
            confirmButton = {
                Button(onClick = { confirmClearCredentials = false; password = ""; onClearStoredCredentials() }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmClearCredentials = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SourceListLayout(
    loading: Boolean,
    authenticationRequired: Boolean,
    error: String?,
    empty: Boolean,
    emptyText: String,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        header?.invoke()
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.semantics { contentDescription = "正在加载" })
            }
            authenticationRequired -> StateMessage(
                icon = Icons.AutoMirrored.Filled.Login,
                title = "需要登录",
            body = "请登录学校统一身份认证；也可在设置中启用本机加密凭证自动登录。",
                button = "登录",
                onClick = onLogin,
            )
            error != null -> StateMessage(Icons.Default.Refresh, "加载失败", error, "重试", onRefresh)
            empty -> StateMessage(Icons.Default.EventAvailable, emptyText, "可以稍后刷新查看最新信息。", "刷新", onRefresh)
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun StateMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    button: String,
    onClick: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(18.dp).size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClick, modifier = Modifier.height(48.dp)) { Text(button) }
        }
    }
}

@Composable
private fun MetaRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label：$value", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable
private fun SchoolWebPage(
    title: String,
    initialUrl: String,
    completion: (String) -> Boolean,
    onClose: () -> Unit,
    onCompleted: () -> Unit,
) {
    var completed by remember { mutableStateOf(false) }
    val competitionLogin = remember(initialUrl) { initialUrl.contains(COMPETITION_HOST) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        val dialogView = LocalView.current
        val imeVisible = WindowInsets.isImeVisible
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            onDispose { }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                if (!imeVisible) {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        },
                    )
                }
                AndroidView(
            factory = { context ->
                WebView(context).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.setSupportMultipleWindows(false)
                    settings.saveFormData = false
                    settings.userAgentString = CampusRepository.MOBILE_USER_AGENT
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val host = request.url.host.orEmpty()
                            if (
                                request.isForMainFrame &&
                                request.url.scheme == "http" &&
                                host == COMPETITION_HOST
                            ) {
                                // The competition CAS callback is emitted as HTTP even though the
                                // server supports HTTPS. Upgrade it without exposing login tickets
                                // over cleartext or weakening the app-wide network security policy.
                                view.loadUrl(request.url.buildUpon().scheme("https").build().toString())
                                return true
                            }
                            return request.isForMainFrame && !(host == "ncwu.edu.cn" || host.endsWith(".ncwu.edu.cn"))
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            if (!completed && completion(url)) {
                                completed = true
                                CookieManager.getInstance().flush()
                                view.postDelayed({ onCompleted() }, 500)
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            val host = runCatching { android.net.Uri.parse(url).host }.getOrNull()
                            if (host == AUTH_SERVER_HOST) {
                                view.evaluateJavascript(AUTH_SERVER_LAYOUT_FIX, null)
                            } else if (
                                competitionLogin && host == COMPETITION_HOST &&
                                (url.contains("/Signupindex") || url.contains("/Signupindexdl"))
                            ) {
                                view.loadUrl(CampusRepository.COMPETITION_LIST_URL)
                            }
                        }
                    }
                    loadUrl(initialUrl)
                }
            },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
            onRelease = { it.stopLoading(); it.destroy() },
        )
            }
        }
    }
}

private const val AUTH_SERVER_HOST = "authserver.ncwu.edu.cn"
private const val COMPETITION_HOST = "xskjjs.ncwu.edu.cn"

/**
 * The school's mobile login stylesheet calculates a zero-height wrapper in Android WebView,
 * which clips the entire form. Apply the correction only on the university auth server and
 * enlarge its unusually short (about 22 CSS px) form fields to touch-friendly dimensions.
 */
private val AUTH_SERVER_LAYOUT_FIX =
    """
    (() => {
        const setStyles = (element, styles) => {
            if (!element) return;
            Object.entries(styles).forEach(([name, value]) =>
                element.style.setProperty(name, value, 'important')
            );
        };

        [document.documentElement, document.body, document.getElementById('mobile-login')]
            .forEach(element => setStyles(element, {
                height: 'auto',
                'min-height': '100%',
                'overflow-y': 'auto',
                '-webkit-overflow-scrolling': 'touch'
            }));

        document.querySelectorAll('#mobile-login .wrapper1, #mobile-login .wrapper2')
            .forEach(element => setStyles(element, {
                height: 'auto',
                'min-height': '100%',
                position: 'relative',
                top: '0',
                bottom: 'auto',
                overflow: 'visible',
                'padding-bottom': 'max(180px, 35vh)',
                'box-sizing': 'border-box'
            }));

        document.querySelectorAll('#mobile-login .message_login')
            .forEach(element => setStyles(element, {
                height: 'auto',
                'min-height': '100vh',
                overflow: 'visible',
                'box-sizing': 'border-box'
            }));

        document.querySelectorAll('#mobile-login .footer')
            .forEach(element => setStyles(element, {
                display: 'none'
            }));

        if (!document.getElementById('ncwu-app-login-fix')) {
            const style = document.createElement('style');
            style.id = 'ncwu-app-login-fix';
            style.textContent = `
                @media (orientation: landscape) and (max-height: 500px) {
                    #mobile-login .templdate1,
                    #mobile-login .logo {
                        display: none !important;
                    }
                    #mobile-login .message_login {
                        min-height: auto !important;
                        padding: 12px 0 180px !important;
                        justify-content: flex-start !important;
                    }
                    #mobile-login .login {
                        margin-top: 0 !important;
                    }
                }
            `;
            document.head.appendChild(style);
        }

        document.querySelectorAll(
            '#mobile-login .login input[type="text"], ' +
            '#mobile-login .login input[type="password"], ' +
            '#mobile-login .login input[type="tel"], ' +
            '#fm1 #username, #fm1 #passwordShow'
        ).forEach(element => setStyles(element, {
            height: '56px',
            'min-height': '56px',
            'font-size': '20px',
            'line-height': '28px',
            padding: '12px 40px 12px 10px',
            'box-sizing': 'border-box'
        }));

        document.querySelectorAll('#mobile-login .username, #mobile-login .password, #mobile-login .validatecode')
            .forEach(element => setStyles(element, {
                'min-height': '68px',
                'box-sizing': 'border-box'
            }));

        document.querySelectorAll('#mobile-login .login, #mobile-login .login_btn')
            .forEach(element => setStyles(element, {
                width: '86vw',
                'max-width': '360px',
                'min-width': '0'
            }));

        document.querySelectorAll('#fm1, #fm1 .nav-content, #fm1 .tabcontent0')
            .forEach(element => setStyles(element, {
                width: '100%',
                height: 'auto',
                overflow: 'visible'
            }));

        document.querySelectorAll(
            '#mobile-login .login_btn input[type="submit"], ' +
            '#mobile-login .login_btn input[type="button"], ' +
            '#mobile-login .login_btn button, #fm1 #passbutton'
        )
            .forEach(element => setStyles(element, {
                height: '48px',
                'min-height': '48px',
                'font-size': '18px'
            }));

        const revealFocusedElement = () => {
            const element = document.activeElement;
            if (element && ['INPUT', 'BUTTON'].includes(element.tagName)) {
                [0, 150, 450].forEach(delay => setTimeout(
                    () => element.scrollIntoView({block: 'center', behavior: 'auto'}),
                    delay
                ));
            }
        };

        document.querySelectorAll('input, button').forEach(element => {
            element.addEventListener('focus', revealFocusedElement);
        });
        window.visualViewport?.addEventListener('resize', revealFocusedElement);
    })();
    """.trimIndent()

private val schoolDateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
private fun parseSchoolDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value.trim(), schoolDateFormatter) }.getOrNull()

private val activityDateTimeFormatters = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
    DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"),
)

private fun parseActivityDateTime(value: String): LocalDateTime? {
    val normalized = value.trim()
    if (normalized.isBlank()) return null
    activityDateTimeFormatters.firstNotNullOfOrNull { formatter ->
        runCatching { LocalDateTime.parse(normalized, formatter) }.getOrNull()
    }?.let { return it }
    runCatching { LocalDate.parse(normalized, schoolDateFormatter).atTime(LocalTime.MAX) }.getOrNull()?.let { return it }
    runCatching { LocalDate.parse(normalized).atTime(LocalTime.MAX) }.getOrNull()?.let { return it }
    return null
}

private fun isVerificationExpired(item: ActivityItem): Boolean {
    val end = item.verificationEnd.ifBlank { item.activityEnd }
    return parseActivityDateTime(end)?.isBefore(LocalDateTime.now()) == true
}
