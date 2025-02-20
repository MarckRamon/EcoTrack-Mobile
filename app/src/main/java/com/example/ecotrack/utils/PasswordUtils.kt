package com.example.ecotrack.utils

import at.favre.lib.crypto.bcrypt.BCrypt

object PasswordUtils {
    fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }
} 