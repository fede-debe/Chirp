package com.project.chirp.api.websocket

import com.project.chirp.api.dto.ws.*
import com.project.chirp.api.mappers.toChatMessageDto
import com.project.chirp.api.websocket.ChatWebSocketHandler.Companion.PING_INTERVAL_MS
import com.project.chirp.api.websocket.ChatWebSocketHandler.Companion.PONG_TIMEOUT_MS
import com.project.chirp.domain.event.ChatParticipantLeftEvent
import com.project.chirp.domain.event.ChatParticipantsJoinedEvent
import com.project.chirp.domain.event.MessageDeletedEvent
import com.project.chirp.domain.event.ProfilePictureUpdatedEvent
import com.project.chirp.domain.type.ChatId
import com.project.chirp.domain.type.UserId
import com.project.chirp.service.ChatMessageService
import com.project.chirp.service.ChatService
import com.project.chirp.service.JwtService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.socket.*
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 1) Manages active connections.
 * 2) Properly receives and sends chat messages to all connected clients.
 * Handles WebSocket connections for chat functionality.
 * @see ChatService for chat-related operations.
 * @see JwtService for JWT token validation.
 * */
@Component
class ChatWebSocketHandler(
    private val chatMessageService: ChatMessageService,
    private val objectMapper: ObjectMapper,
    private val chatService: ChatService,
    private val jwtService: JwtService,
    private val taskScheduler: TaskScheduler
) : TextWebSocketHandler() {

    /***
     * Configuration constants for WebSocket ping and timeout
     * @see PING_INTERVAL_MS: Interval for sending WebSocket pings. Default is 30 seconds.
     * @see PONG_TIMEOUT_MS: Timeout for WebSocket pongs. After this time, the connection will be closed.
     * */
    companion object {
        private const val PING_INTERVAL_MS = 30_000L
        private const val PONG_TIMEOUT_MS = 60_000L
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    /***
     * Lock for managing concurrent access to the session maps.
     * ReentrantReadWriteLock allows multiple (threads) readers (which is not race condition) but only one writer at a time.
     * During that write, not readers are allowed.
     * */
    private val connectionLock = ReentrantReadWriteLock()

    /**
     * When a new message is sent to a chat:
     * 1) We need to see which chat this message is going to.
     * 2) Find all active websocket connections from participants of that chat.
     * 3) Send the message to all those connections.
     *
     * Not active connections not receive the update in real time, they will get
     * update by hitting a normal HTTP request. In order to do that we need to work
     * with Hashmaps. They are data structures that allows a very fast data access which
     * is critical for our use case of real-time connections.
     *
     * @see sessions: A map of session id and related UserSession.
     * @see userToSessions: A map of UserId and related session Ids.
     * A user can have multiple sessions at the same time.
     * @see userChatIds: A map of UserId and related ChatIds.
     * @see chatToSessions: A map of ChatId and related session Ids.
     * */
    private val sessions = ConcurrentHashMap<String, UserSession>()
    private val userToSessions = ConcurrentHashMap<UserId, MutableSet<String>>()
    private val userChatIds = ConcurrentHashMap<UserId, MutableSet<ChatId>>()
    private val chatToSessions = ConcurrentHashMap<ChatId, MutableSet<String>>()
    private val typingTimers = ConcurrentHashMap<ChatId, ConcurrentHashMap<UserId, ScheduledFuture<*>>>()

    /**
     * Define clear routes and paths, we adjust a HTTP that is being made here to establish a connection
     * to a WebSocket.
     * @param session Active connection that can be used to send data to a client and to receive data from a client.
     * Only authenticated users can establish a WebSocket connection.
     * */
    override fun afterConnectionEstablished(session: WebSocketSession) {
        val authHeader = session
            .handshakeHeaders
            .getFirst(HttpHeaders.AUTHORIZATION)
            ?: run {
                logger.warn("Session ${session.id} was closed due to missing Authorization header")
                session.close(CloseStatus.SERVER_ERROR.withReason("Authentication failed"))
                return
            }

        val userId = jwtService.getUserIdFromToken(authHeader)
        val username = chatService.getParticipantUsername(userId) ?: ""

        val userSession = UserSession(
            userId = userId,
            username = username,
            session = session
        )

        /***
         * Single big right change to all our maps.
         * Lock for managing concurrent access to the session maps.
         * */
        connectionLock.write {
            /***
             * Store the session in the sessions map.
             * */
            sessions[session.id] = userSession

            /***
             * Add the session to the user's set of existing sessions.
             * */
            userToSessions.compute(userId) { _, existingSessions ->
                (existingSessions ?: mutableSetOf()).apply {
                    add(session.id)
                }
            }

            /***
             * Store the user's chat IDs in the userChatIds map.
             * chatService.findChatsByUser -> db query to find all chats for a user
             * */
            val chatIds = userChatIds.computeIfAbsent(userId) {
                val chatIds = chatService.findChatsByUser(userId).map { it.id }
                ConcurrentHashMap.newKeySet<ChatId>().apply {
                    addAll(chatIds)
                }
            }

            /***
             * Store the session in the chatToSessions map for each chat.
             * */
            chatIds.forEach { chatId ->
                chatToSessions.compute(chatId) { _, sessions ->
                    (sessions ?: mutableSetOf()).apply {
                        add(session.id)
                    }
                }
            }
        }

        logger.info("Websocket connection established for user $userId")
    }

    /**
     * Handles closing a WebSocket connection.
     * @param session The WebSocket session.
     * @param status The close status.
     * */
    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        var disconnectedUserSession: UserSession? = null

        connectionLock.write {
            sessions.remove(session.id)?.let { userSession ->
                disconnectedUserSession = userSession
                val userId = userSession.userId

                userToSessions.compute(userId) { _, sessions ->
                    sessions
                        ?.apply { remove(session.id) }
                        ?.takeIf { it.isNotEmpty() }
                }

                userChatIds[userId]?.forEach { chatId ->
                    chatToSessions.compute(chatId) { _, sessions ->
                        sessions
                            ?.apply { remove(session.id) }
                            ?.takeIf { it.isNotEmpty() }
                    }
                }

                logger.info("Websocket session closed for user $userId")
            }
        }

        disconnectedUserSession?.let { userSession ->
            val userId = userSession.userId
            val activeTypingChats = mutableListOf<ChatId>()

            typingTimers.forEach { (chatId, userTimers) ->
                if (userTimers.remove(userId)?.cancel(false) != null) {
                    activeTypingChats.add(chatId)
                    if (userTimers.isEmpty()) typingTimers.remove(chatId)
                }
            }

            activeTypingChats.forEach { chatId ->
                broadcastToChat(
                    chatId = chatId,
                    message = OutgoingWebSocketMessage(
                        type = OutgoingWebSocketMessageType.TYPING_INDICATOR,
                        payload = objectMapper.writeValueAsString(
                            TypingIndicatorDto(
                                chatId = chatId,
                                userId = userId,
                                username = userSession.username,
                                isTyping = false
                            )
                        )
                    )
                )
            }
        }
    }

    /***
     * Handles a transport error for a WebSocket session.
     * @param session The WebSocket session.
     * @param exception The Throwable representing the error.
     * */
    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error("Transport error for session ${session.id}", exception)
        session.close(CloseStatus.SERVER_ERROR.withReason("Transport error"))
    }

    /**
     * Handles incoming WebSocket messages.
     * @param session The WebSocket session.
     * @param message The WebSocket message.
     * */
    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        logger.debug("Received message ${message.payload}")

        // session of the sender
        val userSession = connectionLock.read {
            sessions[session.id] ?: return
        }

        try {
            // read and deserialize the incoming WebSocket message
            val webSocketMessage = objectMapper.readValue(
                message.payload,
                IncomingWebSocketMessage::class.java
            )
            when (webSocketMessage.type) {
                IncomingWebSocketMessageType.NEW_MESSAGE -> {
                    val dto = objectMapper.readValue(
                        webSocketMessage.payload,
                        SendMessageDto::class.java
                    )
                    handleSendMessage(
                        dto = dto,
                        senderId = userSession.userId
                    )
                }
                IncomingWebSocketMessageType.TYPING_STARTED -> {
                    val dto = objectMapper.readValue(webSocketMessage.payload, TypingEventDto::class.java)
                    handleTypingEvent(session, dto.chatId, true)
                }

                IncomingWebSocketMessageType.TYPING_STOPPED -> {
                    val dto = objectMapper.readValue(webSocketMessage.payload, TypingEventDto::class.java)
                    handleTypingEvent(session, dto.chatId, false)
                }
            }
        } catch (e: JacksonException) {
            logger.warn("Could not parse message ${message.payload}", e)
            sendError(
                session = userSession.session,
                error = ErrorDto(
                    code = "INVALID_JSON",
                    message = "Incoming JSON or UUID is invalid"
                )
            )
        }
    }

    /***
     * Handles a message deletion event.
     * @param event The MessageDeletedEvent containing the chat ID and message ID.
     *
     * Like RabbitMQ, we can use annotations to listen to events and handle them.
     * TransactionPhase.AFTER_COMMIT makes sure that the event is handled after the
     * database transaction is committed and successfully completed.
     * */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onDeleteMessage(event: MessageDeletedEvent) {
        broadcastToChat(
            chatId = event.chatId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.MESSAGE_DELETED,
                payload = objectMapper.writeValueAsString(
                    DeleteMessageDto(
                        chatId = event.chatId,
                        messageId = event.messageId
                    )
                )
            )
        )
    }

    /***
     * Handles a chat participants joined event.
     * @param event The ChatParticipantsJoinedEvent containing the chat ID and user IDs.
     * Broadcasts the message to related chats and updates the session maps.
     * */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onJoinChat(event: ChatParticipantsJoinedEvent) {
        connectionLock.write {
            event.userIds.forEach { userId ->
                userChatIds.compute(userId) { _, chatIds ->
                    (chatIds ?: mutableSetOf()).apply {
                        add(event.chatId)
                    }
                }

                userToSessions[userId]?.forEach { sessionId ->
                    chatToSessions.compute(event.chatId) { _, sessions ->
                        (sessions ?: mutableSetOf()).apply { add(sessionId) }
                    }
                }
            }
        }

        broadcastToChat(
            chatId = event.chatId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.CHAT_PARTICIPANTS_CHANGED,
                payload = objectMapper.writeValueAsString(
                    ChatParticipantsChangedDto(
                        chatId = event.chatId
                    )
                )
            )
        )
    }

    /***
     * Handles a chat participant left event.
     * @param event The ChatParticipantLeftEvent containing the chat ID and user ID.
     * Broadcasts the message to related chats and updates the session maps.
     * */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onLeftChat(event: ChatParticipantLeftEvent) {
        connectionLock.write {
            userChatIds.compute(event.userId) { _, chatIds ->
                chatIds
                    ?.apply { remove(event.chatId) }
                    ?.takeIf { it.isNotEmpty() }
            }

            userToSessions[event.userId]?.forEach { sessionId ->
                chatToSessions.compute(event.chatId) { _, sessions ->
                    sessions
                        ?.apply { remove(sessionId) }
                        ?.takeIf { it.isNotEmpty() }
                }
            }
        }

        broadcastToChat(
            chatId = event.chatId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.CHAT_PARTICIPANTS_CHANGED,
                payload = objectMapper.writeValueAsString(
                    ChatParticipantsChangedDto(
                        chatId = event.chatId
                    )
                )
            )
        )
    }

    override fun handlePongMessage(session: WebSocketSession, message: PongMessage) {
        connectionLock.write {
            sessions.compute(session.id) { _, userSession ->
                userSession?.copy(
                    lastPongTimestamp = System.currentTimeMillis()
                )
            }
        }
        logger.debug("Received pong from ${session.id}")
    }

    /***
     * Sends a ping to all active WebSocket sessions.
     * */
    @Scheduled(fixedDelay = PING_INTERVAL_MS)
    fun pingClients() {
        val currentTime = System.currentTimeMillis()
        val sessionsToClose = mutableListOf<String>()

        // clear snapshot to work with at the beginning of the function
        val sessionsSnapshot = connectionLock.read { sessions.toMap() }

        sessionsSnapshot.forEach { (sessionId, userSession) ->
            try {
                if (userSession.session.isOpen) {
                    // check if the session has timed out
                    val lastPong = userSession.lastPongTimestamp
                    if (currentTime - lastPong > PONG_TIMEOUT_MS) {
                        logger.warn("Session $sessionId has timed out, closing connection.")
                        sessionsToClose.add(sessionId)
                        return@forEach
                    }

                    // send ping to the session
                    userSession.session.sendMessage(PingMessage())
                    logger.debug("Sent ping to {}", userSession.userId)
                }
            } catch (e: Exception) {
                logger.error("Could not ping session $sessionId", e)
                sessionsToClose.add(sessionId)
            }
        }

        sessionsToClose.forEach { sessionId ->
            connectionLock.read {
                sessions[sessionId]?.session?.let { session ->
                    try {
                        session.close(CloseStatus.GOING_AWAY.withReason("Ping timeout"))
                    } catch (e: Exception) {
                        logger.error("Couldn't close sessions for session ${session.id}")
                    }
                }
            }
        }
    }

    /***
     * Sends an error WebSocket message to a specific session.
     * @param session The WebSocket session.
     * @param error The ErrorDto containing the error code and message.
     * */
    private fun sendError(
        session: WebSocketSession,
        error: ErrorDto
    ) {
        val webSocketMessage = objectMapper.writeValueAsString(
            OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.ERROR,
                payload = objectMapper.writeValueAsString(error)
            )
        )

        try {
            session.sendMessage(TextMessage(webSocketMessage))
        } catch (e: Exception) {
            logger.warn("Couldn't send error message", e)
        }
    }

    /***
     * Broadcasts a WebSocket message to all active sessions for a chat.
     * @param chatId The ID of the chat.
     * @param message The WebSocket message to broadcast.
     * */
    private fun broadcastToChat(
        chatId: ChatId,
        message: OutgoingWebSocketMessage
    ) {
        // active sessions for the chat
        val chatSessions = connectionLock.read {
            chatToSessions[chatId]?.toList() ?: emptyList()
        }

        chatSessions.forEach { sessionId ->
            val userSession = connectionLock.read {
                sessions[sessionId]
            } ?: return@forEach

            sendToUser(
                userId = userSession.userId,
                message = message
            )
        }
    }

    /***
     * Handles sending a new chat message.
     * @param dto The SendMessageDto containing the chat ID, content, and optional message ID.
     * @param senderId The user ID of the sender.
     * */
    private fun handleSendMessage(
        dto: SendMessageDto,
        senderId: UserId
    ) {
        val userChatIds = connectionLock.read { this@ChatWebSocketHandler.userChatIds[senderId] } ?: return

        // Check if the user has access to the chat
        if (dto.chatId !in userChatIds) {
            return
        }

        // take dto and convert it to a real message entity that we can save in the database
        val savedMessage = chatMessageService.sendMessage(
            chatId = dto.chatId,
            senderId = senderId,
            content = dto.content,
            messageId = dto.messageId,
            attachmentUrls = dto.attachments
        )

        // Broadcast the new message to all connected clients in the chat
        broadcastToChat(
            chatId = dto.chatId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.NEW_MESSAGE,
                payload = objectMapper.writeValueAsString(
                    savedMessage.toChatMessageDto()
                )
            )
        )
    }

    /***
     * Sends a WebSocket message to a specific user, this is why we are using outgoing message type.
     * val userSessions: if we want to send a serialized version of this message to a give user we need to know
     * which session we need to send (single user can have multiple active sessions if user connects
     * from multiple devices simultaneously) With the connectionLock everything is synchronized.
     * @param userId The user ID to send the message to.
     * @param message The WebSocket message to send.
     * */
    private fun sendToUser(userId: UserId, message: OutgoingWebSocketMessage) {
        val userSessions = connectionLock.read {
            userToSessions[userId] ?: emptySet()
        }
        /***
         * Send the message to all active sessions for the user.
         * Iterate over all active sessions for the user and send the message if the session is open.
         * */
        userSessions.forEach { sessionId ->
            val userSession = connectionLock.read {
                sessions[sessionId] ?: return@forEach
            }
            if (userSession.session.isOpen) {
                try {
                    val messageJson = objectMapper.writeValueAsString(message)
                    userSession.session.sendMessage(TextMessage(messageJson))
                    logger.debug("Sent message to user {}: {}", userId, messageJson)
                } catch (e: Exception) {
                    logger.error("Error while sending message to $userId", e)
                }
            }
        }
    }

    /***
     * Handles a profile picture update event.
     * @param event The ProfilePictureUpdatedEvent containing the user ID and new URL.
     * Broadcasts the message to related chats and updates the session maps.
     * */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProfilePictureUpdated(event: ProfilePictureUpdatedEvent) {
        val userChats = connectionLock.read {
            userChatIds[event.userId]?.toList() ?: emptyList()
        }

        val dto = ProfilePictureUpdateDto(
            userId = event.userId,
            newUrl = event.newUrl,
        )

        // collect all active sessions for the user
        val sessionIds = mutableSetOf<String>()
        userChats.forEach { chatId ->
            connectionLock.read {
                chatToSessions[chatId]?.let { sessions ->
                    sessionIds.addAll(sessions)
                }
            }
        }

        val webSocketMessage = OutgoingWebSocketMessage(
            type = OutgoingWebSocketMessageType.PROFILE_PICTURE_UPDATED,
            payload = objectMapper.writeValueAsString(dto)
        )
        val messageJson = objectMapper.writeValueAsString(webSocketMessage)

        sessionIds.forEach { sessionId ->
            val userSession = connectionLock.read {
                sessions[sessionId]
            } ?: return@forEach
            try {
                if (userSession.session.isOpen) {
                    userSession.session.sendMessage(TextMessage(messageJson))
                }
            } catch (e: Exception) {
                logger.error("Could not send profile picture update to session $sessionId", e)
            }
        }
    }

    private fun handleTypingEvent(session: WebSocketSession, chatId: ChatId, isTyping: Boolean) {
        val userSession = connectionLock.read { sessions[session.id] } ?: return
        val userId = userSession.userId

        val isParticipant = connectionLock.read { userChatIds[userId]?.contains(chatId) == true }
        if (!isParticipant) {
            sendError(
                session = session,
                error = ErrorDto(code = "FORBIDDEN", message = "You are not a participant of this chat")
            )
            return
        }

        // Cancel any existing auto-stop timer for this user in this chat
        typingTimers[chatId]?.remove(userId)?.cancel(false)

        if (isTyping) {
            val future = taskScheduler.schedule(
                { handleTypingEvent(session, chatId, false) },
                Instant.now().plusSeconds(3)
            )
            typingTimers.computeIfAbsent(chatId) { ConcurrentHashMap() }[userId] = future
        } else {
            typingTimers[chatId]?.let { if (it.isEmpty()) typingTimers.remove(chatId) }
        }

        // Broadcast to all OTHER participants in the chat
        val chatSessions = connectionLock.read { chatToSessions[chatId]?.toList() ?: emptyList() }
        val message = OutgoingWebSocketMessage(
            type = OutgoingWebSocketMessageType.TYPING_INDICATOR,
            payload = objectMapper.writeValueAsString(
                TypingIndicatorDto(
                    chatId = chatId,
                    userId = userId,
                    username = userSession.username,
                    isTyping = isTyping
                )
            )
        )

        chatSessions.forEach { sessionId ->
            val targetSession = connectionLock.read { sessions[sessionId] } ?: return@forEach
            if (targetSession.userId != userId) {
                sendToUser(userId = targetSession.userId, message = message)
            }
        }
    }

    /**
     * Utility class for managing WebSocket sessions for users.
     * @param userId The user ID associated with the WebSocket session.
     * @param username The username associated with the WebSocket session.
     * @param session The WebSocket session.
     * */
    private data class UserSession(
        val userId: UserId,
        val username: String,
        val session: WebSocketSession,
        val lastPongTimestamp: Long = System.currentTimeMillis()
    )
}