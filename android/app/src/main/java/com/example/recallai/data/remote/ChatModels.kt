package com.example.recallai.data.remote

data class ChatSession(
    val sessionId: String,
    val userId: String? = null,
    val startTime: String? = null,
    val status: String? = null
)

data class CreateSessionResponse(
    val message: String,
    val sessionId: String
)

data class ChatMessageRequest(
    val message: String
)

data class ChatAnalysis(
    val emotionalState: String? = null,
    val themes: List<String>? = null,
    val riskLevel: Int? = null,
    val recommendedApproach: String? = null,
    val progressIndicators: List<String>? = null
)

data class ChatProgress(
    val emotionalState: String? = null,
    val riskLevel: Int? = null
)

data class ChatMetadata(
    val progress: ChatProgress? = null
)

data class ChatMessageResponse(
    val response: String,
    val message: String,
    val analysis: ChatAnalysis? = null,
    val metadata: ChatMetadata? = null
)

data class SessionMessage(
    val role: String,
    val content: String,
    val timestamp: String? = null
)