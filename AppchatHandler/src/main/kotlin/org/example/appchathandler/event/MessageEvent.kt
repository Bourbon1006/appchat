package org.example.appchathandler.event

import org.example.appchathandler.dto.MessageSessionInfo

data class SessionsUpdateEvent(
    val userId: Long,
    val sessions: List<MessageSessionInfo>
) 