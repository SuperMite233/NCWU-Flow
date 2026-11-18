package cn.edu.ncwu.flow.data

import cn.edu.ncwu.flow.model.ActivityItem
import cn.edu.ncwu.flow.model.CompetitionItem
import cn.edu.ncwu.flow.model.CompetitionMessage
import cn.edu.ncwu.flow.model.CompetitionMessageAttachment
import cn.edu.ncwu.flow.model.ImportedVerificationCode
import cn.edu.ncwu.flow.model.RegisteredActivityItem
import cn.edu.ncwu.flow.model.RegisteredActivityPage
import cn.edu.ncwu.flow.model.ScoreTerm
import org.json.JSONObject
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal object CampusParser {
    fun parseActivities(body: String, requestedState: String? = null): List<ActivityItem> {
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val id = item.optString("activity_id")
                if (id.isBlank()) continue
                val signedUp = item.optString("signup_id").isNotBlank() ||
                    item.optString("signup_flag") == "1" || item.optString("is_signup") == "1"
                val rawState = firstValue(
                    item,
                    "signup_state", "signup_status", "registration_state",
                    "activity_running_state", "activity_state", "state", "status",
                ).ifBlank { requestedState ?: "报名中" }
                val currentParticipants = item.optString("signup_number").toIntOrNull()
                val participantLimit = item.optString("signup_limit").toIntOrNull()
                val state = if (currentParticipants != null && participantLimit != null &&
                    participantLimit > 0 && currentParticipants >= participantLimit
                ) {
                    "名额已满"
                } else rawState
                add(
                    ActivityItem(
                        id = id,
                        name = item.optString("activity_name", "未命名活动"),
                        organizer = item.optString("hold_unit", "未公布"),
                        signUpStart = item.optString("signup_start"),
                        signUpEnd = item.optString("signup_end"),
                activityStart = item.optString("activity_start"),
                activityEnd = item.optString("activity_end"),
                state = state,
                signedUp = signedUp,
                currentParticipants = currentParticipants,
                participantLimit = participantLimit,
                        imageUrl = item.optString("activity_img").takeIf(String::isNotBlank),
                        customFormKey = item.optString("customform_key").takeIf(String::isNotBlank),
                        taskSignUp = item.optString("task_signup_flag") == "1",
                seatSelection = item.optString("seat_flag") == "1",
                activityMode = item.optString("activity_mode", item.optString("activity_type", "0")),
                verificationState = verificationState(item, state),
                verificationEnd = firstValue(item, "verify_end", "verification_end", "check_end", "activity_verify_end", "verify_deadline"),
                details = firstValue(item, "activity_content", "activity_introduction", "activity_desc", "content", "remark"),
                location = firstValue(item, "activity_address", "activity_addr", "activity_place", "hold_address", "hold_place", "place", "address"),
                        registrationAvailable = !signedUp && isRegistrationOpen(
                            state,
                            currentParticipants,
                            participantLimit,
                        ),
                    ),
                )
            }
        }
    }

    fun parseCompetitions(body: String, baseUrl: String): List<CompetitionItem> {
        val document = Jsoup.parse(body, baseUrl)
        val timelineItems = document.select(".time-item, .time-info-content.time-step-box")
            .ifEmpty {
                document.select(".time-content").map { content -> content.parent() ?: content }
            }
            .distinct()
        return timelineItems.mapNotNull { element ->
            val content = element.selectFirst(".time-content") ?: element
            val headings = content.children().filter { child -> child.tagName() == "dt" }
            val name = headings.getOrNull(0)?.text().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            val details = content.children().filter { child -> child.tagName() == "dd" }
            fun detailValue(vararg labels: String): String = details.firstOrNull { detail ->
                labels.any { label -> detail.text().contains(label) }
            }?.selectFirst("span")?.text()?.trim()
                ?: details.firstOrNull { detail -> labels.any { label -> detail.text().contains(label) } }
                    ?.text()?.substringAfter('：')?.trim().orEmpty()

            val dates = details.mapNotNull { detail -> detail.selectFirst("span") }
            val registrationControl = element.selectFirst("button[onclick*=baoming], a[onclick*=baoming], a[href*=baoming]")
            val registrationTarget = listOf(
                registrationControl?.attr("onclick").orEmpty(),
                registrationControl?.attr("href").orEmpty(),
            ).joinToString(" ")
            val detailText = element.selectFirst(".time-desc, .competition-desc, .detail, .content, .description, .intro, .remark")
                ?.text()?.trim()
                .orEmpty()
                .ifBlank {
                    content.select("dd, .state").text().trim()
                }
            CompetitionItem(
                id = Regex("baoming\\s*\\(\\s*['\"]?(\\d+)", RegexOption.IGNORE_CASE)
                    .find(registrationTarget)?.groupValues?.get(1)
                    ?: Regex("(?:id|competitionId)=([0-9]+)", RegexOption.IGNORE_CASE)
                        .find(registrationTarget)?.groupValues?.get(1).orEmpty(),
                name = name,
                college = detailValue("承办学院", "承办单位", "主办单位").ifBlank {
                    headings.getOrNull(1)?.text()?.substringAfter("：", "未公布") ?: "未公布"
                },
                startDate = detailValue("报名开始", "开始时间").ifBlank {
                    dates.getOrNull(0)?.text().orEmpty()
                },
                endDate = detailValue("报名结束", "报名截止", "截止时间").ifBlank {
                    dates.getOrNull(1)?.text().orEmpty()
                },
                state = element.selectFirst(".state span, .state, .time-state, .status")
                    ?.text().orEmpty().ifBlank { "未知" },
                details = detailText,
            )
        }.distinctBy { item -> item.id.ifBlank { "${item.name}|${item.startDate}|${item.endDate}" } }
    }

    fun parseImportedVerificationCodes(rows: List<List<String>>, studentId: String): List<ImportedVerificationCode> {
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim() }
        val headerLooksValid = header.any { it.contains("验证码") || it.contains("学号") || it.contains("活动") }
        val dataRows = if (headerLooksValid) rows.drop(1) else rows
        val indexes = if (headerLooksValid) {
            mapOf(
                "activity" to (header.indexOfFirst { it.contains("活动") }.takeIf { it >= 0 } ?: 0),
                "code" to (header.indexOfFirst { it.contains("验证码") || it.contains("验证") }.takeIf { it >= 0 } ?: 1),
                "student" to (header.indexOfFirst { it.contains("学号") || it.contains("学籍") }.takeIf { it >= 0 } ?: 2),
                "name" to (header.indexOfFirst { it.contains("姓名") }.takeIf { it >= 0 } ?: 3),
                "verifiedAt" to (header.indexOfFirst { it.contains("时间") }.takeIf { it >= 0 } ?: 4),
            )
        } else mapOf("activity" to 0, "code" to 1, "student" to 2, "name" to 3, "verifiedAt" to 4)
        return dataRows.mapNotNull { row ->
            val rowStudent = row.getOrNull(indexes.getValue("student")).orEmpty().trim()
            val code = row.getOrNull(indexes.getValue("code")).orEmpty().trim()
            if (code.isBlank() || (studentId.isNotBlank() && rowStudent != studentId)) return@mapNotNull null
            ImportedVerificationCode(
                activityName = row.getOrNull(indexes.getValue("activity")).orEmpty().trim(),
                code = code,
                studentId = rowStudent,
                studentName = row.getOrNull(indexes.getValue("name")).orEmpty().trim(),
                verifiedAt = row.getOrNull(indexes.getValue("verifiedAt")).orEmpty().trim(),
            )
        }
    }

    private fun firstValue(item: JSONObject, vararg keys: String): String =
        keys.asSequence().map { item.optString(it).trim() }.firstOrNull { it.isNotBlank() }.orEmpty()

    private fun verificationState(item: JSONObject, state: String): String {
        val rawValues = statusValues(item).map { it.second }
        val raw = rawValues.joinToString(" ")
        val deadline = firstValue(item, "verify_end", "verification_end", "check_end", "activity_verify_end", "verify_deadline")
        val numericCalculated = rawValues.any { it == "1" || it == "2" } &&
            (item.optDouble("score_value", 0.0) != 0.0 || item.optDouble("score_plus", 0.0) != 0.0)
        return when {
            state.contains("过期") || state.contains("失效") || raw.contains("过期") || raw.contains("失效") ||
                isPast(deadline.ifBlank { item.optString("activity_end") }) -> "验证已过期"
            state.contains("待验证") -> "待验证"
            raw.contains("未核算") || raw.contains("未验证") || raw.contains("待核算") || raw.contains("待审核") ||
            rawValues.any { it == "0" || it.equals("uncalculated", true) } -> "未核算"
            raw.contains("已核算") || raw.contains("已验证") || raw.contains("已完成") || raw.contains("已通过") ||
                raw.equals("calculated", true) || numericCalculated -> "已核算"
            raw.isNotBlank() -> raw
            else -> ""
        }
    }

    private fun isRegistrationOpen(state: String, current: Int?, limit: Int?): Boolean {
        val closedByState = listOf("名额已满", "满员", "不在报名范围", "不可报名", "已结束", "报名结束", "报名关闭", "未开始", "即将开始")
            .any(state::contains)
        if (closedByState || (current != null && limit != null && limit > 0 && current >= limit)) return false
        return state.contains("报名中") || state.contains("正在报名") || state.contains("开放") || state.contains("可报名")
    }

    private fun statusValues(item: JSONObject): List<Pair<String, String>> = buildList {
        val keys = item.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = item.optString(key).trim()
            val normalized = key.lowercase().replace("_", "")
            val namedStatus = normalized in setOf(
                "verifystate", "verificationstate", "scorestate", "checkstate",
                "calculatestate", "verifystatus", "scorestatus", "status",
                "activityverifystate", "activitycheckstate", "iscalculated", "isverified",
            )
            val genericState = (key.endsWith("_state", true) || key.endsWith("State", true)) &&
                (value.contains("核算") || value.contains("验证") || value.contains("审核") || value.contains("过期") || value.contains("失效"))
            if ((namedStatus || genericState) && value.isNotBlank() && !value.equals("null", true)) add(key to value)
        }
    }

    private fun statusText(item: JSONObject): String = statusValues(item).joinToString(" ") { it.second }

    fun parseScores(body: String): List<ScoreTerm> {
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
        val names = listOf(
            "思想政治和道德修养", "社会实践与志愿服务", "科学技术与创新创业",
            "文化艺术与身心健康", "学生团体与社会工作", "技能培训",
        )
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val termIndex = item.optInt("school_term_index", index)
                val categories = names.mapIndexed { categoryIndex, name ->
                    name to item.optDouble("activity_type${categoryIndex + 1}_score", 0.0)
                }.toMap()
                add(
                    ScoreTerm(
                        schoolYear = item.optString("school_year", "未公布"),
                        schoolTerm = item.optString("school_term", ""),
                        termIndex = termIndex,
                        total = item.optDouble("score_value", categories.values.sum()),
                        volunteer = item.optDouble("score_plus", 0.0),
                        categories = categories,
                    ),
                )
            }
        }
    }

    fun parseRegisteredActivities(body: String, page: Int): RegisteredActivityPage {
        val root = JSONObject(body)
        val data = root.optJSONArray("data") ?: org.json.JSONArray()
        val items = buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val id = item.optString("id", item.optString("signup_id"))
                if (id.isBlank()) continue
                add(
                    RegisteredActivityItem(
                        id = id,
                        activityId = item.optString("activity_id"),
                        name = item.optString("activity_name", "未命名活动"),
                        type = item.optString("activity_type_name", item.optString("activity_type")),
                        semester = listOf(item.optString("school_year"), item.optString("school_term"))
                            .filter(String::isNotBlank).joinToString(" "),
                        activityStart = item.optString("activity_start"),
                        activityEnd = item.optString("activity_end"),
                        verificationEnd = firstValue(item, "verify_end", "verification_end", "check_end", "activity_verify_end", "verify_deadline"),
                        signupTime = item.optString("signup_time"),
                        status = registeredVerificationState(item),
                        score = item.optDouble("score_value", 0.0),
                        bonusScore = item.optDouble("score_plus", 0.0),
                    ),
                )
            }
        }
        val totalSize = root.optInt("totalSize", root.optInt("total", items.size))
        val totalPages = root.optInt("totalPage", ((totalSize + 9) / 10).coerceAtLeast(1))
        return RegisteredActivityPage(items, page, totalPages.coerceAtLeast(1), totalSize)
    }

    private fun registeredVerificationState(item: JSONObject): String {
        val rawValues = statusValues(item).map { it.second }
        val text = rawValues.joinToString(" ")
        val activityState = item.optString("activity_state").trim()
        val numericCalculated = rawValues.any { it == "1" || it == "2" || it.equals("calculated", true) } &&
            (item.optDouble("score_value", 0.0) != 0.0 || item.optDouble("score_plus", 0.0) != 0.0)
        return when {
            activityState == "4" -> "未核算"
            activityState == "5" -> "已核算"
            text.contains("过期") || text.contains("失效") -> "验证已过期"
            text.contains("未核算") || text.contains("未验证") || text.contains("待核算") || text.contains("待审核") ||
                rawValues.any { it == "0" || it.equals("uncalculated", true) } -> "未核算"
            text.contains("已核算") || text.contains("已验证") || text.contains("已完成") || text.contains("已通过") ||
                numericCalculated -> "已核算"
            text.isNotBlank() -> text
            else -> "未核算"
        }
    }

    private fun isPast(value: String): Boolean {
        if (value.isBlank()) return false
        val formats = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"),
        )
        formats.firstNotNullOfOrNull { formatter -> runCatching { LocalDateTime.parse(value.trim(), formatter) }.getOrNull() }
            ?.let { return it.isBefore(LocalDateTime.now()) }
        runCatching { LocalDate.parse(value.trim()).atTime(LocalTime.MAX) }.getOrNull()
            ?.let { return it.isBefore(LocalDateTime.now()) }
        return false
    }

    internal fun isActivityExpired(item: ActivityItem): Boolean =
        item.state.contains("已结束") || item.state.contains("活动结束") ||
            isPast(item.activityEnd)

    fun parseCompetitionMessages(body: String, baseUrl: String): List<CompetitionMessage> {
        val document = Jsoup.parse(body, baseUrl)
        return document.select(".news-item").mapNotNull { element ->
            val link = element.selectFirst("a[onclick*=queryMessageById]")
                ?: element.selectFirst("a[href]")
            val onclick = link?.attr("onclick").orEmpty()
            val id = Regex("queryMessageById\\((\\d+)").find(onclick)?.groupValues?.get(1)
                ?: Regex("id=(\\d+)").find(link?.attr("href").orEmpty())?.groupValues?.get(1)
                ?: return@mapNotNull null
            val dateNode = element.selectFirst(".news-time")
            val date = dateNode?.text()?.trim().orEmpty()
            val title = element.selectFirst(".news-til")?.text()?.trim()
                ?: link?.text()?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            val info = element.selectFirst(".news-info")
            val content = info?.clone()
                ?.apply { select("a").remove() }
                ?.text()
                ?.trim()
                .orEmpty()
                .ifBlank { info?.text()?.trim().orEmpty() }
            val attachments = info?.select("a[href]")?.map {
                CompetitionMessageAttachment(it.text().trim().ifBlank { "附件" }, it.absUrl("href"))
            }.orEmpty()
            CompetitionMessage(id, title, date, content, attachments)
        }.distinctBy { it.id }
    }
}
