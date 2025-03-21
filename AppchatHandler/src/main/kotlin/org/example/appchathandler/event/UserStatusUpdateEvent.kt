package org.example.appchathandler.event

import org.springframework.context.ApplicationEvent

class UserStatusUpdateEvent(
    source: Any,
    val userId: Long,
    val status: Int
) : ApplicationEvent(source) 