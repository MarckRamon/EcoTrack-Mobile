package com.example.ecotrack.models

import com.google.gson.annotations.SerializedName

data class SecurityQuestionsResponse(
    @SerializedName("questions")
    val questions: List<SecurityQuestion> = emptyList(),
    val answers: List<String>? = null
)

data class SecurityQuestion(
    @SerializedName("id")
    val id: String = "",
    
    @SerializedName("questionId")
    val questionId: String = "",
    
    @SerializedName("text")
    val questionText: String? = null,
    
    @SerializedName("questionText")
    val questionTextAlt: String? = null,
    
    @SerializedName("answer")
    val answer: String? = null
) {
    // Get the effective question text from either field
    fun getEffectiveQuestionText(): String {
        return questionText ?: questionTextAlt ?: getDefaultQuestionText()
    }
    
    // Generate a default question text based on the question ID
    private fun getDefaultQuestionText(): String {
        val effectiveId = if (id.isNotEmpty()) id else questionId
        return when (effectiveId) {
            "FIRST_PET_NAME" -> "What was the name of your first pet?"
            "BIRTH_CITY" -> "In what city were you born?"
            "FAVORITE_COLOR" -> "What is your favorite color?"
            "MOTHERS_MAIDEN_NAME" -> "What is your mother's maiden name?"
            "FIRST_SCHOOL" -> "What was the name of your first school?"
            "CHILDHOOD_NICKNAME" -> "What was your childhood nickname?"
            else -> "Question: $effectiveId"
        }
    }
    
    override fun toString(): String {
        return getEffectiveQuestionText()
    }
}

data class SecurityQuestionAnswer(
    @SerializedName("questionId")
    val questionId: String,
    
    @SerializedName("answer")
    val answer: String
)

/**
 * This class represents the answer format needed for the backend's ForgotPasswordRequest
 */
data class QuestionAnswer(
    @SerializedName("questionId")
    val questionId: String,
    
    @SerializedName("answer")
    val answer: String
)

data class UserSecurityQuestionsResponse(
    @SerializedName("securityQuestions")
    val securityQuestions: List<SecurityQuestion> = emptyList()
) {
    override fun toString(): String {
        return "UserSecurityQuestionsResponse(questions=${securityQuestions.size}, " +
               "questionIds=${securityQuestions.map { it.questionId.ifEmpty { it.id } }}, " +
               "questionTexts=${securityQuestions.map { it.getEffectiveQuestionText() }}, " +
               "answers=${securityQuestions.map { it.answer }})"
    }
} 