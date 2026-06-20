package com.reals.app.data.repository

import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.map
import com.reals.app.data.api.AuthTokenProvider
import com.reals.app.data.api.RealsApi
import com.reals.app.data.dto.ChatExitRequestCreateRequestDto
import com.reals.app.data.dto.ChatMessageResponseDto
import com.reals.app.data.dto.ChatMessagesResponseDto
import com.reals.app.data.dto.SendMessageRequestDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatExitOutcome
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class ChatRepository(
    private val api: RealsApi,
    private val json: Json,
    tokenProvider: AuthTokenProvider,
    apiExecutor: ApiExecutor,
) : AuthenticatedRepository(tokenProvider, apiExecutor) {
    suspend fun getChat(chatId: String): ApiResult<Chat> =
        authorizedCall { authorization -> api.getChat(authorization, chatId) }
            .map { it.toDomain() }

    suspend fun getSecondChatForConnection(connectionId: String): ApiResult<Chat> =
        authorizedCall { authorization -> api.getSecondChatForConnection(authorization, connectionId) }
            .map { it.toDomain() }

    suspend fun getMessages(chatId: String, afterMessageId: String? = null): ApiResult<List<ChatMessage>> =
        authorizedCall { authorization ->
            api.getChatMessages(
                authorization = authorization,
                chatId = chatId,
                afterMessageId = afterMessageId,
                afterMessageIdAlias = afterMessageId,
            )
        }
            .map { payload -> payload.toMessageDtos().map { it.toDomain() } }

    suspend fun sendMessage(chatId: String, content: String): ApiResult<ChatMessage> =
        authorizedCall { authorization ->
            api.sendChatMessage(
                authorization = authorization,
                chatId = chatId,
                body = SendMessageRequestDto(content),
            )
        }.map { it.toDomain() }

    suspend fun getExitRequests(chatId: String): ApiResult<List<ChatExitRequest>> =
        authorizedCall { authorization -> api.getChatExitRequests(authorization, chatId) }
            .map { requests -> requests.map { it.toDomain() } }

    suspend fun requestMutualExit(
        chatId: String,
        reason: ChatExitReason?,
        details: String?,
    ): ApiResult<ChatExitRequest> =
        authorizedCall { authorization ->
            api.requestChatExit(
                authorization = authorization,
                chatId = chatId,
                body = exitBody(reason, details),
            )
        }.map { it.toDomain() }

    suspend fun acceptExitRequest(chatId: String, exitRequestId: String): ApiResult<ChatExitOutcome> =
        authorizedCall { authorization -> api.acceptChatExitRequest(authorization, chatId, exitRequestId) }
            .map { it.toDomain() }

    suspend fun rejectExitRequest(chatId: String, exitRequestId: String): ApiResult<ChatExitOutcome> =
        authorizedCall { authorization -> api.rejectChatExitRequest(authorization, chatId, exitRequestId) }
            .map { it.toDomain() }

    suspend fun timeoutExitRequest(chatId: String, exitRequestId: String): ApiResult<ChatExitOutcome> =
        authorizedCall { authorization -> api.timeoutChatExitRequest(authorization, chatId, exitRequestId) }
            .map { it.toDomain() }

    suspend fun cancelChat(
        chatId: String,
        reason: ChatExitReason?,
        details: String?,
    ): ApiResult<ChatExitOutcome> =
        authorizedCall { authorization ->
            api.cancelChat(
                authorization = authorization,
                chatId = chatId,
                body = exitBody(reason, details),
            )
        }.map { it.toDomain() }

    suspend fun safetyCancelChat(
        chatId: String,
        reason: ChatExitReason,
        details: String,
    ): ApiResult<ChatExitOutcome> =
        authorizedCall { authorization ->
            api.safetyCancelChat(
                authorization = authorization,
                chatId = chatId,
                body = exitBody(reason, details),
            )
        }.map { it.toDomain() }

    private fun exitBody(reason: ChatExitReason?, details: String?): ChatExitRequestCreateRequestDto =
        ChatExitRequestCreateRequestDto(
            reason = reason?.rawValue,
            details = details?.trim()?.ifBlank { null },
        )

    private fun JsonElement.toMessageDtos(): List<ChatMessageResponseDto> =
        if (this is JsonArray) {
            json.decodeFromJsonElement<List<ChatMessageResponseDto>>(this)
        } else {
            json.decodeFromJsonElement<ChatMessagesResponseDto>(this).messages
        }
}
