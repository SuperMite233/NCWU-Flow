package cn.edu.ncwu.flow.data

import android.content.Context
import android.webkit.CookieManager
import cn.edu.ncwu.flow.model.ActivityItem
import cn.edu.ncwu.flow.model.CompetitionItem
import cn.edu.ncwu.flow.model.CompetitionMessage
import cn.edu.ncwu.flow.model.LoadResult
import cn.edu.ncwu.flow.model.RegisteredActivityPage
import cn.edu.ncwu.flow.model.ScoreTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class CampusRepository(
    context: Context,
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) {
    private val credentialStore = CredentialStore(context.applicationContext)
    private val loginLock = Any()
    @Volatile private var credentialsRejected = false
    private val webViewCookieJar = WebViewCookieJar(cookieManager)
    private val client = OkHttpClient.Builder()
        .cookieJar(webViewCookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            if (request.url.scheme == "http" && request.url.host == COMPETITION_HOST) {
                chain.proceed(request.newBuilder().url(request.url.newBuilder().scheme("https").build()).build())
            } else chain.proceed(request)
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun loadActivities(): LoadResult<List<ActivityItem>> = ioResult("活动加载失败") {
        withAutomaticLogin(ACTIVITY_URL) {
            val all = linkedMapOf<String, ActivityItem>()
            listOf("报名中", "待验证", "即将开始报名").forEach { status ->
                val body = postForm(ACTIVITY_URL, mapOf(
                    "title" to status,
                    "activity_name_like" to "",
                    "hold_unit" to "",
                    "activity_type" to "",
                    "item_id" to "",
                )) ?: return@withAutomaticLogin LoadResult.AuthenticationRequired
                CampusParser.parseActivities(body, status).forEach { item ->
                    all[item.id] = item.copy(state = item.state.ifBlank { status })
                }
            }
            val registeredBody = postForm(
                REGISTERED_ACTIVITIES_URL,
                mapOf("page" to "1", "pagesize" to "100"),
            )
            if (registeredBody != null) {
                CampusParser.parseRegisteredActivities(registeredBody, 1).items
                    .filterNot { it.status == "已核算" }
                    .forEach { item ->
                        val id = item.activityId.ifBlank { item.id }
                        if (id.isNotBlank()) {
                            all[id] = ActivityItem(
                                id = id,
                                name = item.name,
                                organizer = item.type.ifBlank { "已报名活动" },
                                signUpStart = "",
                                signUpEnd = "",
                                activityStart = item.activityStart,
                                activityEnd = item.activityEnd,
                                state = if (item.status == "未验证") "待验证" else item.status,
                                signedUp = true,
                                currentParticipants = null,
                                participantLimit = null,
                                imageUrl = null,
                                customFormKey = null,
                                taskSignUp = false,
                                seatSelection = false,
                                verificationState = item.status,
                                verificationEnd = item.verificationEnd,
                                registrationAvailable = false,
                            )
                        }
                    }
            }
            LoadResult.Success(
                all.values.filterNot {
                    it.verificationState.contains("过期") || CampusParser.isActivityExpired(it)
                }.toList(),
            )
        }
    }

    suspend fun verifyActivityCode(
        code: String,
        activityMode: String = "0",
        activityId: String = "",
    ): LoadResult<String> = ioResult("验证码验证失败") {
        withAutomaticLogin(ACTIVITY_URL) {
            val body = postForm(CHECK_VERIFY_CODE_URL, mapOf(
                "verifycode" to code.trim(), "activity_mode" to activityMode, "activity_id" to activityId,
            )) ?: return@withAutomaticLogin LoadResult.AuthenticationRequired
            jsonMessage(body, "验证成功", "验证码验证失败")
        }
    }

    suspend fun verifyActivityQr(raw: String): LoadResult<String> = withContext(Dispatchers.IO) {
        val trimmed = raw.trim()
        val payload = parseQrPayload(trimmed)
            ?: return@withContext verifyActivityCode(trimmed)
        payload.staticCode?.let {
            return@withContext verifyActivityCode(it, payload.activityMode, payload.activityId)
        }
        if (payload.locationRequired || payload.faceRequired) {
            return@withContext LoadResult.Error("该二维码要求定位或人脸验证，请使用学校官方验证页完成")
        }
        if (payload.activityId.isBlank() && payload.code.isBlank()) {
            return@withContext LoadResult.Error("二维码中没有可用的活动验证码参数")
        }
        ioResult("二维码验证失败") {
            withAutomaticLogin(ACTIVITY_URL) {
                val body = postForm(CHECK_ER_CODE_URL, mapOf(
                    "activityid" to payload.activityId,
                    "umcodeid" to payload.codeId,
                    "verifycode" to payload.code,
                    "username" to credentialStore.settings().username.ifBlank { payload.username },
                    "activity_mode" to payload.activityMode,
                    "latitude" to "", "longitude" to "", "faceurl" to "",
                )) ?: return@withAutomaticLogin LoadResult.AuthenticationRequired
                jsonMessage(body, "验证成功", "二维码验证失败")
            }
        }
    }

    private fun parseQrPayload(raw: String): QrPayload? {
        val decoded = decodeQrText(raw)
        val jsonText = listOf(decoded, decoded.substringAfter('{', "{").let { "{$it" })
            .firstOrNull { it.trimStart().startsWith("{") }
        val json = jsonText?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (json != null) return QrPayload.fromJson(json)
        val uri = runCatching { URI(decoded) }.getOrNull()
        val query = uri?.rawQuery
            ?: decoded.substringAfter('?', "").ifBlank { decoded.takeIf { '=' in it }.orEmpty() }
        val params = query.split('&', ';')
            .mapNotNull { part ->
                val pair = part.split('=', limit = 2)
                if (pair.size == 2) pair[0].lowercase() to URLDecoder.decode(pair[1], Charsets.UTF_8.name()) else null
            }.toMap()
        if (params.isEmpty()) return null
        return QrPayload(
            activityId = params.firstValue("activityid", "activity_id", "id"),
            codeId = params.firstValue("umcodeid", "um_code_id", "codeid", "code_id"),
            code = params.firstValue("verifycode", "verify_code", "code"),
            staticCode = params.firstValue("staticverifycode", "static_verify_code").takeIf(String::isNotBlank),
            username = params.firstValue("username", "user_name"),
            activityMode = params.firstValue("activity_mode", "activitymode").ifBlank { "0" },
            locationRequired = params.booleanValue("location_switch", "locationrequired"),
            faceRequired = params.booleanValue("checkface", "facerequired"),
        )
    }

    private fun decodeQrText(raw: String): String {
        var current = raw.trim()
        repeat(2) {
            val decoded = runCatching { URLDecoder.decode(current, Charsets.UTF_8.name()) }.getOrDefault(current)
            if (decoded == current) return current
            current = decoded
        }
        return current
    }

    private fun Map<String, String>.firstValue(vararg keys: String): String =
        keys.asSequence().mapNotNull { this[it] }.firstOrNull { it.isNotBlank() }.orEmpty()

    private fun Map<String, String>.booleanValue(vararg keys: String): Boolean = firstValue(*keys).let {
        it == "1" || it.equals("true", true) || it.equals("yes", true)
    }

    suspend fun signUpActivity(activityId: String): LoadResult<String> = ioResult("报名失败") {
        withAutomaticLogin(ACTIVITY_URL) {
            val request = Request.Builder()
                .url("https://dekt.ncwu.edu.cn/sys/stu/activity/signup")
                .standardHeaders(ACTIVITY_URL)
                .post(FormBody.Builder().add("id", activityId).add("wxlogin_flag", "").build())
                .header("X-Requested-With", "XMLHttpRequest").build()
            client.newCall(request).execute().use { response ->
                val body = response.bodyText()
                if (response.isAuthenticationPage(body)) LoadResult.AuthenticationRequired
                else jsonMessage(body, "报名成功", "报名未成功，请稍后重试")
            }
        }
    }

    suspend fun loadCompetitions(): LoadResult<List<CompetitionItem>> = ioResult("竞赛加载失败") {
        withAutomaticLogin(COMPETITION_ENTRY_URL) {
            val request = Request.Builder().url(COMPETITION_LIST_URL).standardHeaders(COMPETITION_LIST_URL).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.bodyText()
                if (response.isAuthenticationPage(body) || isCompetitionSessionRejected(body)) {
                    LoadResult.AuthenticationRequired
                }
                else LoadResult.Success(CampusParser.parseCompetitions(body, response.request.url.toString()))
            }
        }
    }

    suspend fun loadCompetitionMessages(): LoadResult<List<CompetitionMessage>> = ioResult("竞赛消息加载失败") {
        withAutomaticLogin(COMPETITION_ENTRY_URL) {
            val request = Request.Builder().url(COMPETITION_MESSAGES_URL).standardHeaders(COMPETITION_MESSAGES_URL).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.bodyText()
                if (response.isAuthenticationPage(body)) LoadResult.AuthenticationRequired
                else LoadResult.Success(CampusParser.parseCompetitionMessages(body, response.request.url.toString()))
            }
        }
    }

    suspend fun loadScores(): LoadResult<List<ScoreTerm>> = ioResult("成绩加载失败") {
        withAutomaticLogin(ACTIVITY_URL) {
            val body = postForm(SCORE_LIST_URL, emptyMap()) ?: return@withAutomaticLogin LoadResult.AuthenticationRequired
            LoadResult.Success(CampusParser.parseScores(body))
        }
    }

    suspend fun loadRegisteredActivities(page: Int, pageSize: Int = 10): LoadResult<RegisteredActivityPage> =
        ioResult("已报活动加载失败") {
            withAutomaticLogin(ACTIVITY_URL) {
                val body = postForm(
                    REGISTERED_ACTIVITIES_URL,
                    mapOf("page" to page.toString(), "pagesize" to pageSize.toString()),
                ) ?: return@withAutomaticLogin LoadResult.AuthenticationRequired
                LoadResult.Success(CampusParser.parseRegisteredActivities(body, page))
            }
        }

    suspend fun testStoredLogin(): LoadResult<String> = ioResult("自动登录测试失败") {
        credentialsRejected = false
        val credentials = credentialStore.credentials()
            ?: return@ioResult LoadResult.Error("请先填写学号和密码")
        when (val result = autoLogin(ACTIVITY_URL, credentials)) {
            AutoLoginResult.Success -> LoadResult.Success("自动登录验证成功")
            AutoLoginResult.InvalidCredentials -> LoadResult.Error("学号或密码错误，请检查后重新保存")
            is AutoLoginResult.ManualRequired -> LoadResult.Error(result.message)
        }
    }

    fun clearSessions(onComplete: () -> Unit) {
        webViewCookieJar.clear()
        cookieManager.removeAllCookies { onComplete() }
        cookieManager.flush()
    }

    private inline fun <T> withAutomaticLogin(loginEntry: String, request: () -> LoadResult<T>): LoadResult<T> {
        val first = request()
        if (first !is LoadResult.AuthenticationRequired) return first
        val credentials = credentialStore.credentials() ?: return first
        if (credentialsRejected) return LoadResult.Error("保存的学号或密码错误，请在设置中更新")
        return synchronized(loginLock) {
            val afterWaiting = request()
            if (afterWaiting !is LoadResult.AuthenticationRequired) return@synchronized afterWaiting
            if (credentialsRejected) return@synchronized LoadResult.Error("保存的学号或密码错误，请在设置中更新")
            when (val login = autoLogin(loginEntry, credentials)) {
                AutoLoginResult.Success -> request()
                AutoLoginResult.InvalidCredentials -> {
                    credentialsRejected = true
                    LoadResult.Error("保存的学号或密码错误，请在设置中更新")
                }
                is AutoLoginResult.ManualRequired -> LoadResult.Error(login.message)
            }
        }
    }

    private fun autoLogin(loginEntry: String, credentials: StoredCredentials): AutoLoginResult {
        val landing = resolveLoginLanding(loginEntry)
        if (landing.url.host != AUTH_SERVER_HOST) {
            if (landing.body.isAuthenticationInterstitial()) {
                val message = "未能进入统一认证页面，请检查网络后重试"
                credentialStore.recordStatus(message)
                return AutoLoginResult.ManualRequired(message)
            }
            credentialStore.recordStatus("登录状态有效")
            return AutoLoginResult.Success
        }
        val document = Jsoup.parse(landing.body, landing.url.toString())
        val form = document.selectFirst("form#fm1")
            ?: return AutoLoginResult.ManualRequired("统一认证页面结构已变化，请改用手动登录")
        if (form.selectFirst("#authcode") != null) {
            return AutoLoginResult.ManualRequired("统一认证要求验证码，请临时使用手动登录")
        }
        val action = form.absUrl("action").ifBlank { landing.url.toString() }
        val execution = form.selectFirst("input[name=execution]")?.attr("value").orEmpty()
        if (execution.isBlank()) {
            return AutoLoginResult.ManualRequired("未获取到统一认证令牌，请稍后重试")
        }
        val modulus = loadCasModulus(document)
            ?: return AutoLoginResult.ManualRequired("未获取到统一认证加密参数，请改用手动登录")
        val body = FormBody.Builder()
            .add("username", credentials.username)
            .add("password", CasPasswordEncryptor.encrypt(credentials.password, modulus))
            .add("execution", execution)
            .add("encrypted", form.selectFirst("input[name=encrypted]")?.attr("value").orEmpty().ifBlank { "true" })
            .add("_eventId", form.selectFirst("input[name=_eventId]")?.attr("value").orEmpty().ifBlank { "submit" })
            .add("loginType", form.selectFirst("input[name=loginType]")?.attr("value").orEmpty().ifBlank { "1" })
            .add("submit", "登 录")
            .build()
        val loginRequest = Request.Builder().url(action).standardHeaders(action).post(body).build()
        client.newCall(loginRequest).execute().use { response ->
            val resultBody = response.bodyText()
            if (response.request.url.host != AUTH_SERVER_HOST) {
                credentialStore.recordStatus("自动登录成功")
                cookieManager.flush()
                return AutoLoginResult.Success
            }
            val errorText = Jsoup.parse(resultBody).select("#msg, #msg1, .errors, .form-error").text()
            return if (PASSWORD_ERROR_WORDS.any { errorText.contains(it, ignoreCase = true) } ||
                errorText.contains("用户名") && errorText.contains("密码")
            ) {
                credentialStore.recordStatus("密码错误")
                AutoLoginResult.InvalidCredentials
            } else {
                val message = errorText.ifBlank { "自动登录未完成，请使用手动登录检查认证页面" }
                credentialStore.recordStatus(message)
                AutoLoginResult.ManualRequired(message)
            }
        }
    }

    /**
     * The second-classroom gateway returns a tiny HTML page whose JavaScript clicks an
     * authserver link. OkHttp does not execute that JavaScript, so follow the link here.
     */
    private fun resolveLoginLanding(loginEntry: String): LoginLanding {
        var nextUrl = loginEntry
        var referer = loginEntry
        repeat(MAX_AUTH_TRANSITIONS) {
            val request = Request.Builder().url(nextUrl).standardHeaders(referer).get().build()
            val landing = client.newCall(request).execute().use { response ->
                LoginLanding(response.request.url, response.bodyText())
            }
            val transition = authenticationRedirect(landing.body, landing.url.toString())
                ?: return landing
            referer = landing.url.toString()
            nextUrl = transition
        }
        val request = Request.Builder().url(nextUrl).standardHeaders(referer).get().build()
        return client.newCall(request).execute().use { response ->
            LoginLanding(response.request.url, response.bodyText())
        }
    }

    private fun String.isAuthenticationInterstitial(): Boolean =
        contains("统一认证中心跳转", ignoreCase = true) ||
            contains("authserver.ncwu.edu.cn/authserver/login", ignoreCase = true)

    private fun postForm(url: String, params: Map<String, String>): String? {
        val form = FormBody.Builder().apply { params.forEach { (key, value) -> add(key, value) } }.build()
        val request = Request.Builder().url(url).standardHeaders(url).post(form)
            .header("X-Requested-With", "XMLHttpRequest").build()
        client.newCall(request).execute().use { response ->
            val body = response.bodyText()
            return if (response.isAuthenticationPage(body) || body.trimStart().startsWith("<")) null else body
        }
    }

    private fun loadCasModulus(document: org.jsoup.nodes.Document): String? {
        val scriptUrl = document.select("script[src*=login.js]").lastOrNull()?.absUrl("src").orEmpty()
        if (scriptUrl.isBlank()) return null
        val request = Request.Builder().url(scriptUrl).standardHeaders(document.location()).get().build()
        return client.newCall(request).execute().use { response ->
            val script = response.bodyText()
            Regex("getKeyPair\\(\\s*[\\\"']010001[\\\"']\\s*,\\s*[\\\"'][\\\"']\\s*,\\s*[\\\"']([0-9a-fA-F]+)")
                .find(script)?.groupValues?.get(1)
        }
    }

    private fun jsonMessage(body: String, successFallback: String, errorFallback: String): LoadResult<String> {
        val json = JSONObject(body)
        val code = json.optInt("code", Int.MIN_VALUE)
        val success = json.optBoolean("success") ||
            json.optString("success").equals("true", true) ||
            code == 200
        val message = sequenceOf("info", "msg", "message", "error")
            .map { json.optString(it) }
            .firstOrNull { it.isNotBlank() }
            ?: if (success) successFallback else errorFallback
        return if (success) LoadResult.Success(message) else LoadResult.Error(message)
    }

    private suspend inline fun <T> ioResult(error: String, crossinline block: () -> LoadResult<T>): LoadResult<T> =
        withContext(Dispatchers.IO) { runCatching { block() }.getOrElse { LoadResult.Error(it.message ?: error) } }

    private fun Request.Builder.standardHeaders(referer: String): Request.Builder = apply {
        header("Referer", referer)
        header("User-Agent", MOBILE_USER_AGENT)
    }

    private fun Response.bodyText(): String = body?.bytes()?.toString(Charsets.UTF_8).orEmpty()

    private fun Response.isAuthenticationPage(body: String): Boolean =
        request.url.host == AUTH_SERVER_HOST || body.contains("<form id=\"fm1\"") || body.contains("统一身份认证平台")

    private sealed interface AutoLoginResult {
        data object Success : AutoLoginResult
        data object InvalidCredentials : AutoLoginResult
        data class ManualRequired(val message: String) : AutoLoginResult
    }

    private data class LoginLanding(val url: HttpUrl, val body: String)

    private data class QrPayload(
        val activityId: String,
        val codeId: String,
        val code: String,
        val staticCode: String?,
        val username: String,
        val activityMode: String,
        val locationRequired: Boolean,
        val faceRequired: Boolean,
    ) {
        companion object {
            fun fromJson(json: JSONObject): QrPayload {
                fun value(vararg names: String): String = names.asSequence()
                    .map { json.optString(it).trim() }
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                fun flag(vararg names: String): Boolean = value(*names).let {
                    it == "1" || it.equals("true", true) || it.equals("yes", true)
                }
                return QrPayload(
                    activityId = value("activityid", "activity_id", "activityId", "id"),
                    codeId = value("umcodeid", "um_code_id", "umCodeId", "codeid", "code_id"),
                    code = value("verifycode", "verify_code", "verifyCode", "code"),
                    staticCode = value("staticverifycode", "static_verify_code", "staticVerifyCode")
                        .takeIf(String::isNotBlank),
                    username = value("username", "user_name", "userName"),
                    activityMode = value("activity_mode", "activitymode", "activityMode").ifBlank { "0" },
                    locationRequired = flag("location_switch", "locationrequired", "locationRequired"),
                    faceRequired = flag("checkface", "facerequired", "faceRequired"),
                )
            }
        }
    }

    private class WebViewCookieJar(private val manager: CookieManager) : CookieJar {
        private val memory = ConcurrentHashMap<String, Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { cookie ->
                memory["${cookie.name}|${cookie.domain}|${cookie.path}"] = cookie
                runCatching { manager.setCookie(url.toString(), cookie.toString()) }
            }
            if (cookies.isNotEmpty()) runCatching { manager.flush() }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val stored = runCatching { manager.getCookie(url.toString()) }.getOrNull().orEmpty()
                .split(';')
                .mapNotNull { pair -> Cookie.parse(url, pair.trim()) }
            return (memory.values.filter { it.matches(url) } + stored).distinctBy { it.name }
        }

        fun clear() {
            memory.clear()
        }
    }

    companion object {
        const val ACTIVITY_URL = "https://dekt.ncwu.edu.cn/sys/stu/activity"
        const val COMPETITION_ENTRY_URL =
            "https://authserver.ncwu.edu.cn/authserver/login?service=https%3A%2F%2Fxskjjs.ncwu.edu.cn%2FSignupindex"
        const val COMPETITION_LIST_URL = "https://xskjjs.ncwu.edu.cn/queryAllTimeLineNo"
        const val COMPETITION_MESSAGES_URL = "https://xskjjs.ncwu.edu.cn/queryAllMessage"
        const val CHECK_VERIFY_CODE_URL = "https://dekt.ncwu.edu.cn/sys/stu/signup/checkVerifyCode"
        const val CHECK_ER_CODE_URL = "https://dekt.ncwu.edu.cn/sys/stu/signup/checkErCode"
        const val SCORE_LIST_URL = "https://dekt.ncwu.edu.cn/sys/stu/score/list"
        const val REGISTERED_ACTIVITIES_URL = "https://dekt.ncwu.edu.cn/sys/stu/center/signupactivity/list"
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36"
        private const val AUTH_SERVER_HOST = "authserver.ncwu.edu.cn"
        private const val COMPETITION_HOST = "xskjjs.ncwu.edu.cn"
        private const val MAX_AUTH_TRANSITIONS = 3
        private val PASSWORD_ERROR_WORDS = listOf("密码错误", "账号或密码", "用户名或密码", "invalid credentials")

        internal fun isCompetitionSessionRejected(body: String): Boolean {
            val trimmed = body.trimStart()
            if (!trimmed.startsWith("{")) return false
            return runCatching {
                val payload = JSONObject(trimmed)
                payload.has("code") && payload.optInt("code", 200) != 200
            }.getOrDefault(false)
        }

        internal fun authenticationRedirect(body: String, baseUrl: String): String? {
            val document = Jsoup.parse(body, baseUrl)
            return document.select("a[href]")
                .asSequence()
                .map { it.absUrl("href") }
                .firstOrNull { url ->
                    runCatching {
                        val parsed = java.net.URI(url)
                        parsed.host.equals(AUTH_SERVER_HOST, ignoreCase = true) &&
                            parsed.path.startsWith("/authserver/login")
                    }.getOrDefault(false)
                }
        }
    }
}
