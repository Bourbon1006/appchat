package org.example.appchathandler.event

import org.springframework.context.ApplicationEvent
import org.example.appchathandler.entity.FriendRequest

class FriendRequestNotificationEvent(
    source: Any,
    val friendRequest: FriendRequest
) : ApplicationEvent(source) 