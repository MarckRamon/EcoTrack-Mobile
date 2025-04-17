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
    
    @SerializedName("text")
    val questionText: String? = null,
    
    @SerializedName("answer")
    val answer: String? = null
) {
    override fun toString(): String {
        return questionText ?: "Unknown Question"
    }
}

data class SecurityQuestionAnswer(
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
               "questionIds=${securityQuestions.map { it.id }}, " +
               "questionTexts=${securityQuestions.map { it.questionText }}, " +
               "answers=${securityQuestions.map { it.answer }})"
    }
} 