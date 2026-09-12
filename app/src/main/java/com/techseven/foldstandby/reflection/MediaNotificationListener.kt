package com.techseven.foldstandby.reflection

import android.service.notification.NotificationListenerService

/**
 * Enables [MediaSessionManager.getActiveSessions] so Flex Reflection can seek/pause
 * the paired video app. User must grant notification access once.
 */
class MediaNotificationListener : NotificationListenerService()
