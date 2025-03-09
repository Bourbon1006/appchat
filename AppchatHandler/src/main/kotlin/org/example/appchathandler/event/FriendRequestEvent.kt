 package org.example.appchathandler.event

import org.example.appchathandler.entity.FriendRequest
import org.springframework.context.ApplicationEvent

class FriendRequestEvent(source: Any, val friendRequest: FriendRequest) : ApplicationEvent(source)