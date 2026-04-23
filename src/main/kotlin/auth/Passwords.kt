package com.example.auth

import org.mindrot.jbcrypt.BCrypt

object Passwords {
    fun hash(raw: String): String = BCrypt.hashpw(raw, BCrypt.gensalt())
    fun verify(raw: String, hash: String): Boolean = BCrypt.checkpw(raw, hash)
}
