package com.example.grabtrash.models

import com.google.gson.annotations.SerializedName

data class ForgotPasswordRequest(
    @SerializedName("identifier")
    val identifier: String,
    
    @SerializedName("answers")
    val answers: List<QuestionAnswer> = emptyList(),
    
    @SerializedName("newPassword")
    val newPassword: String
)

data class PasswordResetRequest(
    @SerializedName("identifier")
    val identifier: String,
    
    @SerializedName("newPassword")
    val newPassword: String,
    
    @SerializedName("answers")
    val answers: List<SecurityQuestionAnswer> = emptyList()
)