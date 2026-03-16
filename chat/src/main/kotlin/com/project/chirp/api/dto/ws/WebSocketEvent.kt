package com.project.chirp.api.dto.ws

/***
 * WebSocket message types.
 *
 * @see IncomingWebSocketMessageType: when client sends a message to the server.
 * @see OutgoingWebSocketMessageType: type of messages the server can send to any client.
 * @see IncomingWebSocketMessage: dto for type ans payload. This structure works well with
 * type safe JSON parsing(especially on mobile side with Kotlin).
 * The client first parses the class to then check the type and based on the type, it can
 * deserialize the payload which is also JSON serialized. Since the payload structure can
 * change, we simply serialize payload json and based on the type, the client can conditionally
 * parse and deserialize the payload. (Example: this is a NEW_MESSAGE type, it means this must
 * be the specific format that the payload is in)
 *
 *
 * @see OutgoingWebSocketMessage: when server sends a message to the client.
 */
enum class IncomingWebSocketMessageType {
    NEW_MESSAGE,
    TYPING_STARTED,
    TYPING_STOPPED
}

enum class OutgoingWebSocketMessageType {
    NEW_MESSAGE,
    MESSAGE_DELETED,
    PROFILE_PICTURE_UPDATED,
    CHAT_PARTICIPANTS_CHANGED,
    TYPING_INDICATOR,
    ERROR
}

data class IncomingWebSocketMessage(
    val type: IncomingWebSocketMessageType,
    val payload: String
)

data class OutgoingWebSocketMessage(
    val type: OutgoingWebSocketMessageType,
    val payload: String
)