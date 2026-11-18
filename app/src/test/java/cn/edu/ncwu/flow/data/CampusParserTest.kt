package cn.edu.ncwu.flow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusParserTest {
    @Test
    fun parsesActivityJson() {
        val json = """
            {"data":[{"activity_id":"a1","activity_name":"节水宣传","hold_unit":"水利学院",
            "signup_end":"2026-09-02 18:00","activity_running_state":"报名中","signup_number":"12",
            "signup_limit":"30","task_signup_flag":"0","seat_flag":"0"}]}
        """.trimIndent()

        val item = CampusParser.parseActivities(json).single()
        assertEquals("a1", item.id)
        assertEquals(12, item.currentParticipants)
        assertFalse(item.signedUp)
    }

    @Test
    fun parsesCompetitionTimelineHtml() {
        val html = """
            <div class="time-item"><div class="time-head-time"><span>2026年09月04日</span></div>
            <dl class="time-content"><dt>挑战杯</dt><dt>承办学院：团委</dt>
            <dd><b>报名开始时间：</b><span>2026年08月29日</span></dd>
            <dd><b>报名结束时间：</b><span>2026年09月04日</span></dd>
            <div class="state"><span>正在进行</span></div><button onclick="baoming(105)">队长报名</button>
            </dl></div>
        """.trimIndent()

        val item = CampusParser.parseCompetitions(html, "https://xskjjs.ncwu.edu.cn").single()
        assertEquals("105", item.id)
        assertEquals("团委", item.college)
        assertTrue(item.state.contains("进行"))
    }

    @Test
    fun recognizesCompetitionSessionFailurePayload() {
        assertTrue(CampusRepository.isCompetitionSessionRejected("""{"msg":null,"code":500}"""))
        assertFalse(CampusRepository.isCompetitionSessionRejected("""{"msg":"ok","code":200}"""))
        assertFalse(CampusRepository.isCompetitionSessionRejected("<div class=\"time-item\"></div>"))
    }

    @Test
    fun followsSecondClassroomAuthenticationInterstitial() {
        val html = """
            <html><body onload="go()">
              <a id="url" href="https://authserver.ncwu.edu.cn/authserver/login?service=https%3A%2F%2Fsec.ncwu.edu.cn%2FloginFromCas%2F">
                统一认证中心跳转中...
              </a>
            </body></html>
        """.trimIndent()

        assertEquals(
            "https://authserver.ncwu.edu.cn/authserver/login?service=https%3A%2F%2Fsec.ncwu.edu.cn%2FloginFromCas%2F",
            CampusRepository.authenticationRedirect(html, "https://dekt.ncwu.edu.cn/rump_frontend/login/"),
        )
        assertEquals(null, CampusRepository.authenticationRedirect("<html>活动列表</html>", "https://dekt.ncwu.edu.cn/sys/stu/activity"))
    }

    @Test
    fun parsesCurrentCompetitionTimelineStructure() {
        val html = """
            <div class="time-info-content time-step-box">
              <div class="time-head-time"><span>2026年09月05日</span></div>
              <dl class="time-content">
                <dt>大学生创新竞赛</dt>
                <dd><b>承办学院：</b><span>创新创业学院</span></dd>
                <dd><b>报名截止时间：</b><span>2026年09月20日</span></dd>
                <dd><b>报名开始时间：</b><span>2026年09月01日</span></dd>
                <div class="time-state">报名中</div>
                <a onclick="baoming('208')">报名</a>
              </dl>
            </div>
        """.trimIndent()

        val item = CampusParser.parseCompetitions(html, "https://xskjjs.ncwu.edu.cn").single()
        assertEquals("208", item.id)
        assertEquals("创新创业学院", item.college)
        assertEquals("2026年09月01日", item.startDate)
        assertEquals("2026年09月20日", item.endDate)
        assertEquals("报名中", item.state)
        assertTrue(item.details.contains("报名截止时间"))
    }

    @Test
    fun parsesActivityStateFallbackAndScores() {
        val activity = CampusParser.parseActivities("""{"data":[{"activity_id":"a2","activity_name":"待验证活动"}]}""", "待验证").single()
        assertEquals("待验证", activity.state)

        val scores = CampusParser.parseScores("""{"data":[{"school_year":"2025-2026","school_term":"1","school_term_index":1,"activity_type1_score":4,"score_value":12.5,"score_plus":1.5}]}""")
        assertEquals(12.5, scores.single().total, 0.01)
        assertEquals(4.0, scores.single().categories.values.first(), 0.01)
    }

    @Test
    fun parsesActivityRegistrationAndVerificationStates() {
        val json = """
            {"data":[
              {"activity_id":"open","activity_name":"可报名","activity_running_state":"报名中","verify_state":"0"},
              {"activity_id":"full","activity_name":"名额已满","activity_running_state":"报名中（名额已满）","signup_number":"30","signup_limit":"30"},
              {"activity_id":"range","activity_name":"不在范围","activity_running_state":"不在报名范围"},
              {"activity_id":"pending","activity_name":"待验证","activity_running_state":"待验证","verify_state":"0","activity_content":"活动说明","activity_address":"龙子湖校区"},
              {"activity_id":"closed","activity_name":"已结束","activity_running_state":"已结束","verify_state":"1","score_value":"2","signup_id":"s1"}
            ]}
        """.trimIndent()

        val items = CampusParser.parseActivities(json)
        assertTrue(items.first { it.id == "open" }.registrationAvailable)
        assertEquals("未核算", items.first { it.id == "open" }.verificationState)
        assertFalse(items.first { it.id == "full" }.registrationAvailable)
        assertFalse(items.first { it.id == "range" }.registrationAvailable)
        assertFalse(items.first { it.id == "pending" }.registrationAvailable)
        assertEquals("待验证", items.first { it.id == "pending" }.verificationState)
        assertEquals("活动说明", items.first { it.id == "pending" }.details)
        assertEquals("龙子湖校区", items.first { it.id == "pending" }.location)
        assertFalse(items.first { it.id == "closed" }.registrationAvailable)
        assertEquals("已核算", items.first { it.id == "closed" }.verificationState)
    }

    @Test
    fun matchesImportedVerificationCodesByStudentId() {
        val rows = listOf(
            listOf("活动名称", "验证码", "学号", "姓名", "验证时间"),
            listOf("其他活动", "wrong", "student-other", "甲", ""),
            listOf("我的活动", "sample-code", "student-test", "乙", ""),
        )

        val matched = CampusParser.parseImportedVerificationCodes(rows, "student-test")
        assertEquals(1, matched.size)
        assertEquals("我的活动", matched.single().activityName)
        assertEquals("sample-code", matched.single().code)
    }

    @Test
    fun importedVerificationCodesUseFixedColumnsWhenHeadersAreUnreadable() {
        val rows = listOf(
            listOf("�����", "��֤��", "ѧ��", "����", "��֤ʱ��"),
            listOf("������������", "sample-code", "student-test", "�ﳩ", ""),
        )

        val matched = CampusParser.parseImportedVerificationCodes(rows, "student-test")
        assertEquals("sample-code", matched.single().code)
        assertEquals("student-test", matched.single().studentId)
    }

    @Test
    fun parsesRegisteredActivitiesPage() {
        val page = CampusParser.parseRegisteredActivities("""{"data":[{"id":"s1","activity_id":"a1","activity_name":"节水宣传","verify_state":"1","score_value":2}],"totalSize":11,"totalPage":2}""", 1)
        assertEquals(1, page.items.size)
        assertEquals(2, page.totalPages)
        assertEquals("已核算", page.items.single().status)
    }

    @Test
    fun registeredUncalculatedTextWinsOverNumericCalculatedFlag() {
        val page = CampusParser.parseRegisteredActivities(
            """{"data":[{"id":"s2","activity_id":"a2","activity_name":"待核算活动","verify_state":"1","score_state":"未核算"}]}""",
            1,
        )
        assertEquals("未核算", page.items.single().status)
    }

    @Test
    fun registeredActivityStateCodesOverrideUnrelatedNumericFields() {
        val page = CampusParser.parseRegisteredActivities(
            """{"data":[
                {"id":"s4","activity_id":"a4","activity_name":"未核算","activity_state":"4","verify_state":"1","score_type":"0","score_value":3},
                {"id":"s5","activity_id":"a5","activity_name":"已核算","activity_state":"5","verify_state":"1","score_type":"0","score_value":3}
            ]}""",
            1,
        )
        assertEquals("未核算", page.items.first { it.id == "s4" }.status)
        assertEquals("已核算", page.items.first { it.id == "s5" }.status)
    }

    @Test
    fun fullActivityIsLabeledAsFull() {
        val item = CampusParser.parseActivities(
            """{"data":[{"activity_id":"full","activity_name":"满员","activity_running_state":"报名中","signup_number":"10","signup_limit":"10"}]}""",
        ).single()
        assertEquals("名额已满", item.state)
        assertFalse(item.registrationAvailable)
    }

    @Test
    fun oldPendingActivityIsMarkedExpired() {
        val item = CampusParser.parseActivities(
            """{"data":[{"activity_id":"old","activity_name":"水美华水","activity_running_state":"待验证","activity_end":"2020-01-01 18:00:00","verify_state":"0"}]}""",
        ).single()
        assertEquals("验证已过期", item.verificationState)
    }

    @Test
    fun parsesCompetitionMessages() {
        val html = """
            <div class="news-item"><a onclick="queryMessageById(9)"><span class="news-til">竞赛通知</span></a>
            <div class="news-time"><span>2026</span><b>08/30</b></div><div class="news-info">报名开始<a href="/file.pdf">通知</a></div></div>
        """.trimIndent()
        val message = CampusParser.parseCompetitionMessages(html, "https://xskjjs.ncwu.edu.cn").single()
        assertEquals("9", message.id)
        assertEquals("竞赛通知", message.title)
        assertEquals(1, message.attachments.size)
    }

    @Test
    fun keepsCompetitionMessageBodyWhenThereIsNoAttachmentLink() {
        val html = """
            <div class="news-item">
              <p class="news-time"><b>09/20</b><span>2026</span></p>
              <b class="news-til">竞赛正文</b>
              <span class="news-info"><p>这是竞赛通知正文。</p></span>
              <a class="btn" onclick="queryMessageById(10)">查看详情</a>
            </div>
        """.trimIndent()

        val message = CampusParser.parseCompetitionMessages(html, "https://xskjjs.ncwu.edu.cn").single()
        assertEquals("这是竞赛通知正文。", message.content)
    }

    @Test
    fun encryptsPasswordLikeSchoolCasScript() {
        assertEquals(
            "229f4ed267584faa8b6f60e54e7e815491c2957c3c420a5ef4758eaf719219fcdf2bf366272ef3e5a53816554e4cc78fe085a8cbe6e738de75efc95c8be11584793aecb9f0f61869189de9eba6359963b1a81748e1053fb8a9b1a76ff7d0046deb7c9d88f89b0e603a96fb0b1162af06bb28e4a5e15889c26cbe12805bd3a8fc",
            CasPasswordEncryptor.encrypt("test"),
        )
    }
}
