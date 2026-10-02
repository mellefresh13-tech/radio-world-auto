package com.mellefresh13.radio

object StreamAvailabilityPolicy {
    fun isPlayable(status: String): Boolean =
        status.isBlank() || status.equals("online", ignoreCase = true)
}
