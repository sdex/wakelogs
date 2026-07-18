package de.sanniki.wakesleuth

data class WakeEvent(
    val id: Long,
    val timestamp: Long,
    val type: String,
    val title: String,
    val details: String
)
