package com.example.auth

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class AuthRateLimiter(
    private val limit: Int = 10,
    private val windowSeconds: Long = 60
) {
    private val hits = ConcurrentHashMap<String, MutableList<Long>>()

    fun deny(remoteIp: String, op: String): Boolean {
        val key = "$op:$remoteIp"
        val now = Instant.now().epochSecond
        val cutoff = now - windowSeconds
        val list = hits.computeIfAbsent(key) { mutableListOf() }
        synchronized(list) {
            list.removeAll { it < cutoff }
            if (list.size >= limit) return true
            list.add(now)
        }
        if (hits.size > 5000) {
            hits.entries.removeIf { (_, v) -> synchronized(v) { v.isEmpty() } }
        }
        return false
    }
}
