package com.example.ecotrack.models

import com.google.gson.annotations.SerializedName

data class SecurityQuestionsResponse(
    @SerializedName("questions")
    val questions: List<SecurityQuestion>,
    val answers: List<String>? = null
)

data class SecurityQuestion(
    @SerializedName("questionId")
    val id: String,
    
    @SerializedName("questionText")
    val questionText: String,
    
    @SerializedName("answer")
    val answer: String? = null
)

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