package cn.edu.ncwu.flow.model

data class ActivityItem(
    val id: String,
    val name: String,
    val organizer: String,
    val signUpStart: String,
    val signUpEnd: String,
    val activityStart: String,
    val activityEnd: String,
    val state: String,
    val signedUp: Boolean,
    val currentParticipants: Int?,
    val participantLimit: Int?,
    val imageUrl: String?,
    val customFormKey: String?,
    val taskSignUp: Boolean,
    val seatSelection: Boolean,
    val activityMode: String = "0",
    val verificationState: String = "",
    val verificationEnd: String = "",
    val details: String = "",
    val location: String = "",
    val registrationAvailable: Boolean = true,
)

data class CompetitionItem(
    val id: String,
    val name: String,
    val college: String,
    val startDate: String,
    val endDate: String,
    val state: String,
    val details: String = "",
)

data class ImportedVerificationCode(
    val activityName: String,
    val code: String,
    val studentId: String,
    val studentName: String,
    val verifiedAt: String,
)

enum class SortOrder { END_DATE_ASC, END_DATE_DESC }

data class CompetitionMessageAttachment(
    val label: String,
    val url: String,
)

data class CompetitionMessage(
    val id: String,
    val title: String,
    val date: String,
    val content: String,
    val attachments: List<CompetitionMessageAttachment> = emptyList(),
)

data class ScoreTerm(
    val schoolYear: String,
    val schoolTerm: String,
    val termIndex: Int,
    val total: Double,
    val volunteer: Double,
    val categories: Map<String, Double>,
)

data class RegisteredActivityItem(
    val id: String,
    val activityId: String,
    val name: String,
    val type: String,
    val semester: String,
    val activityStart: String,
    val activityEnd: String,
    val verificationEnd: String,
    val signupTime: String,
    val status: String,
    val score: Double,
    val bonusScore: Double,
)

data class RegisteredActivityPage(
    val items: List<RegisteredActivityItem>,
    val page: Int,
    val totalPages: Int,
    val totalSize: Int,
)

sealed interface LoadResult<out T> {
    data class Success<T>(val data: T) : LoadResult<T>
    data object AuthenticationRequired : LoadResult<Nothing>
    data class Error(val message: String) : LoadResult<Nothing>
}
