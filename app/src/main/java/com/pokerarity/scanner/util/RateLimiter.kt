package com.pokerarity.scanner.util

import android.content.Context
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Rate limiter for preventing DOS attacks via repeated requests.
 * Tracks requests over a sliding time window.
 * All rate limit violations are logged to SecurityAuditLogger.
 */
class RateLimiter(
    private val maxRequestsPerMinute: Int = 10,
    private val windowMs: Long = 60_000L,  // 1 minute
    private val context: Context? = null
) {
    private val requestTimestamps = ConcurrentLinkedQueue<Long>()
    private val auditLogger: SecurityAuditLogger? = context?.let { SecurityAuditLogger.getInstance(it) }

    /**
     * Check if a new request is allowed based on rate limiting rules.
     * Returns true if request is within limits, false if rate-limited.
     */
    fun canProcess(identifier: String? = null): Boolean {
        val now = System.currentTimeMillis()
        
        // Remove timestamps older than the window
        while (requestTimestamps.isNotEmpty() && 
               now - requestTimestamps.peek() > windowMs) {
            requestTimestamps.poll()
        }
        
        // Check if we've exceeded the limit
        return if (requestTimestamps.size >= maxRequestsPerMinute) {
            auditLogger?.logRateLimitExceeded(identifier ?: "unknown")
            false  // Rate limited
        } else {
            requestTimestamps.offer(now)
            true  // Request allowed
        }
    }

    /**
     * Get the number of requests in the current window
     */
    fun getRequestCount(): Int = requestTimestamps.size

    /**
     * Reset the rate limiter
     */
    fun reset() {
        requestTimestamps.clear()
    }
}
