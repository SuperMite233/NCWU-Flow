package cn.edu.ncwu.flow

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ncwu.flow.data.CampusRepository
import cn.edu.ncwu.flow.data.ActivityVerifycodeImporter
import cn.edu.ncwu.flow.data.CredentialSettings
import cn.edu.ncwu.flow.data.CredentialStore
import cn.edu.ncwu.flow.data.LoginMode
import cn.edu.ncwu.flow.background.BackgroundScanScheduler
import cn.edu.ncwu.flow.background.BackgroundSettings
import cn.edu.ncwu.flow.background.ScanCategory
import cn.edu.ncwu.flow.model.ActivityItem
import cn.edu.ncwu.flow.model.CompetitionItem
import cn.edu.ncwu.flow.model.ImportedVerificationCode
import cn.edu.ncwu.flow.model.LoadResult
import cn.edu.ncwu.flow.model.CompetitionMessage
import cn.edu.ncwu.flow.model.RegisteredActivityPage
import cn.edu.ncwu.flow.model.ScoreTerm
import cn.edu.ncwu.flow.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "display_settings")

data class SourceState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val authenticationRequired: Boolean = false,
    val error: String? = null,
)

data class PagedSourceState<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val authenticationRequired: Boolean = false,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CampusRepository(application)
    private val credentialStore = CredentialStore(application)

    private val _credentialSettings = MutableStateFlow(credentialStore.settings())
    val credentialSettings: StateFlow<CredentialSettings> = _credentialSettings

    private val _activities = MutableStateFlow(SourceState<ActivityItem>())
    val activities: StateFlow<SourceState<ActivityItem>> = _activities

    private val _competitions = MutableStateFlow(SourceState<CompetitionItem>())
    val competitions: StateFlow<SourceState<CompetitionItem>> = _competitions

    private val _competitionMessages = MutableStateFlow(SourceState<CompetitionMessage>())
    val competitionMessages: StateFlow<SourceState<CompetitionMessage>> = _competitionMessages

    private val _scores = MutableStateFlow(SourceState<ScoreTerm>())
    val scores: StateFlow<SourceState<ScoreTerm>> = _scores

    private val _registeredActivities = MutableStateFlow(PagedSourceState<RegisteredActivityPage>())
    val registeredActivities: StateFlow<PagedSourceState<RegisteredActivityPage>> = _registeredActivities

    private val _importedVerificationCodes = MutableStateFlow<List<ImportedVerificationCode>>(emptyList())
    val importedVerificationCodes: StateFlow<List<ImportedVerificationCode>> = _importedVerificationCodes

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    private val themeKey = stringPreferencesKey("theme_mode")
    private val backgroundEnabledKey = booleanPreferencesKey("background_enabled")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")
    private val intervalKey = longPreferencesKey("scan_interval_minutes")
    private val categoryKey = stringPreferencesKey("scan_category")
    private val scoreGoalKey = doublePreferencesKey("score_goal")
    val scoreGoal = application.dataStore.data
        .map { it[scoreGoalKey] ?: 100.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 100.0)
    val themeMode = application.dataStore.data
        .map { preferences ->
            runCatching { ThemeMode.valueOf(preferences[themeKey] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val backgroundSettings = application.dataStore.data
        .map { preferences ->
            BackgroundSettings(
                enabled = preferences[backgroundEnabledKey] ?: false,
                notificationsEnabled = preferences[notificationsEnabledKey] ?: true,
                intervalMinutes = preferences[intervalKey] ?: 60,
                category = runCatching {
                    ScanCategory.valueOf(preferences[categoryKey] ?: ScanCategory.ALL.name)
                }.getOrDefault(ScanCategory.ALL),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackgroundSettings())

    init {
        refreshActivities()
        refreshCompetitions()
        refreshCompetitionMessages()
        refreshScores()
        refreshRegisteredActivities()
        viewModelScope.launch {
            application.dataStore.data
                .map { preferences ->
                    BackgroundSettings(
                        enabled = preferences[backgroundEnabledKey] ?: false,
                        notificationsEnabled = preferences[notificationsEnabledKey] ?: true,
                        intervalMinutes = preferences[intervalKey] ?: 60,
                        category = runCatching {
                            ScanCategory.valueOf(preferences[categoryKey] ?: ScanCategory.ALL.name)
                        }.getOrDefault(ScanCategory.ALL),
                    )
                }
                .collect { BackgroundScanScheduler.apply(application, it) }
        }
    }

    fun refreshActivities() = viewModelScope.launch {
        _activities.value = _activities.value.copy(loading = true, error = null)
        _activities.value = when (val result = repository.loadActivities()) {
            is LoadResult.Success -> SourceState(items = result.data)
            LoadResult.AuthenticationRequired -> SourceState(authenticationRequired = true)
            is LoadResult.Error -> SourceState(error = result.message)
        }
        _credentialSettings.value = credentialStore.settings()
    }

    fun refreshCompetitionMessages() = viewModelScope.launch {
        _competitionMessages.value = _competitionMessages.value.copy(loading = true, error = null)
        _competitionMessages.value = when (val result = repository.loadCompetitionMessages()) {
            is LoadResult.Success -> SourceState(items = result.data)
            LoadResult.AuthenticationRequired -> SourceState(authenticationRequired = true)
            is LoadResult.Error -> SourceState(error = result.message)
        }
    }

    fun refreshScores() = viewModelScope.launch {
        _scores.value = _scores.value.copy(loading = true, error = null)
        _scores.value = when (val result = repository.loadScores()) {
            is LoadResult.Success -> SourceState(items = result.data)
            LoadResult.AuthenticationRequired -> SourceState(authenticationRequired = true)
            is LoadResult.Error -> SourceState(error = result.message)
        }
    }

    fun refreshRegisteredActivities(page: Int = _registeredActivities.value.data?.page ?: 1) = viewModelScope.launch {
        _registeredActivities.value = _registeredActivities.value.copy(loading = true, error = null)
        _registeredActivities.value = when (val result = repository.loadRegisteredActivities(page.coerceAtLeast(1))) {
            is LoadResult.Success -> PagedSourceState(data = result.data)
            LoadResult.AuthenticationRequired -> PagedSourceState(authenticationRequired = true)
            is LoadResult.Error -> PagedSourceState(error = result.message)
        }
    }

    fun refreshCompetitions() = viewModelScope.launch {
        _competitions.value = _competitions.value.copy(loading = true, error = null)
        _competitions.value = when (val result = repository.loadCompetitions()) {
            is LoadResult.Success -> SourceState(items = result.data)
            LoadResult.AuthenticationRequired -> SourceState(authenticationRequired = true)
            is LoadResult.Error -> SourceState(error = result.message)
        }
        _credentialSettings.value = credentialStore.settings()
    }

    fun signUp(activity: ActivityItem) = viewModelScope.launch {
        when (val result = repository.signUpActivity(activity.id)) {
            is LoadResult.Success -> {
                _messages.tryEmit(result.data)
                refreshActivities()
            }
            LoadResult.AuthenticationRequired -> {
                _activities.value = SourceState(authenticationRequired = true)
                _messages.tryEmit("登录已失效，请重新登录")
            }
            is LoadResult.Error -> _messages.tryEmit(result.message)
        }
    }

    fun verifyCode(code: String, activity: ActivityItem? = null) = viewModelScope.launch {
        if (code.isBlank()) { _messages.tryEmit("请输入验证码"); return@launch }
        when (val result = repository.verifyActivityCode(code, activity?.activityMode ?: "0", activity?.id ?: "")) {
            is LoadResult.Success -> { _messages.tryEmit(result.data); refreshActivities() }
            LoadResult.AuthenticationRequired -> _messages.tryEmit("登录已失效，请重新登录")
            is LoadResult.Error -> _messages.tryEmit(result.message)
        }
    }

    fun verifyQr(raw: String) = viewModelScope.launch {
        when (val result = repository.verifyActivityQr(raw)) {
            is LoadResult.Success -> { _messages.tryEmit(result.data); refreshActivities() }
            LoadResult.AuthenticationRequired -> _messages.tryEmit("登录已失效，请重新登录")
            is LoadResult.Error -> _messages.tryEmit(result.message)
        }
    }

    fun importVerificationFile(uri: Uri) = viewModelScope.launch {
        val studentId = credentialStore.settings().username
        ActivityVerifycodeImporter.importFromUri(getApplication(), uri, studentId)
            .onSuccess { codes ->
                _importedVerificationCodes.value = codes
                _messages.tryEmit("已导入 ${codes.size} 条当前学号验证码")
            }
            .onFailure { error -> _messages.tryEmit(error.message ?: "验证码文件导入失败") }
    }

    fun setScoreGoal(goal: Double) = viewModelScope.launch {
        getApplication<Application>().dataStore.edit { it[scoreGoalKey] = goal.coerceIn(1.0, 1000.0) }
        _messages.tryEmit("最新学期目标已保存")
    }

    fun saveCredentials(username: String, password: String) = viewModelScope.launch {
        if (username.isBlank() || password.isBlank()) {
            _messages.tryEmit("请完整填写学号和密码")
            return@launch
        }
        runCatching { credentialStore.save(username, password) }
            .onFailure { _messages.tryEmit("凭证保存失败：${it.message ?: "设备加密不可用"}") }
            .onSuccess {
                _credentialSettings.value = credentialStore.settings()
                _messages.tryEmit("凭证已加密保存，正在验证登录")
                when (val result = repository.testStoredLogin()) {
                    is LoadResult.Success -> {
                        _messages.tryEmit(result.data)
                        _credentialSettings.value = credentialStore.settings()
                        refreshAllSources()
                    }
                    is LoadResult.Error -> {
                        _messages.tryEmit(result.message)
                        _credentialSettings.value = credentialStore.settings()
                    }
                    LoadResult.AuthenticationRequired -> _messages.tryEmit("自动登录未完成，请检查凭证")
                }
            }
    }

    fun useManualLogin() {
        credentialStore.useManualMode()
        _credentialSettings.value = credentialStore.settings()
        _messages.tryEmit("已切换为每次手动登录")
    }

    fun useStoredLogin() {
        credentialStore.useStoredMode()
        _credentialSettings.value = credentialStore.settings()
        _messages.tryEmit("已启用本机凭证自动登录")
        refreshAllSources()
    }

    fun clearStoredCredentials() {
        credentialStore.clear()
        _credentialSettings.value = credentialStore.settings()
        _messages.tryEmit("本机保存的登录凭证已删除")
    }

    private fun refreshAllSources() {
        refreshActivities()
        refreshCompetitions()
        refreshCompetitionMessages()
        refreshScores()
        refreshRegisteredActivities(1)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        getApplication<Application>().dataStore.edit { it[themeKey] = mode.name }
    }

    fun setBackgroundEnabled(enabled: Boolean) = viewModelScope.launch {
        getApplication<Application>().dataStore.edit { it[backgroundEnabledKey] = enabled }
        if (enabled && credentialStore.settings().mode == LoginMode.MANUAL) {
            _messages.tryEmit("手动登录模式下，会话失效后后台扫描将暂停；建议启用本机凭证自动登录")
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        getApplication<Application>().dataStore.edit { it[notificationsEnabledKey] = enabled }
    }

    fun setScanInterval(minutes: Long) = viewModelScope.launch {
        getApplication<Application>().dataStore.edit { it[intervalKey] = minutes.coerceAtLeast(15) }
    }

    fun setScanCategory(category: ScanCategory) = viewModelScope.launch {
        getApplication<Application>().dataStore.edit { it[categoryKey] = category.name }
    }

    fun clearSessions() {
        repository.clearSessions {
            _activities.value = SourceState(authenticationRequired = true)
            _competitions.value = SourceState(authenticationRequired = true)
            _competitionMessages.value = SourceState(authenticationRequired = true)
            _scores.value = SourceState(authenticationRequired = true)
            _registeredActivities.value = PagedSourceState(authenticationRequired = true)
            _messages.tryEmit("校园网站登录状态已清除")
        }
    }
}
