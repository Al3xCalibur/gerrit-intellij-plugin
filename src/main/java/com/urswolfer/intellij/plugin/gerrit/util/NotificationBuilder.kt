/*
 * Copyright 2013 Urs Wolfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.urswolfer.intellij.plugin.gerrit.util

import com.google.common.base.Optional
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * @author Thomas Forrer
 */
class NotificationBuilder(val project: Project?, private val title: String, private val message: String?) {
    private var type = NotificationType.INFORMATION

    private var listener: Optional<NotificationListener> = Optional.absent()
    private var showBalloon = true

    fun listener(listener: NotificationListener): NotificationBuilder {
        this.listener = Optional.of(listener)
        return this
    }

    fun type(type: NotificationType): NotificationBuilder {
        this.type = type
        return this
    }

    fun showBalloon(): NotificationBuilder {
        this.showBalloon = true
        return this
    }

    fun hideBalloon(): NotificationBuilder {
        this.showBalloon = false
        return this
    }

    fun get(): Notification {
        val notification = Notification(GERRIT_NOTIFICATION_GROUP, title, message!!, type)
        if (listener.isPresent) {
            notification.setListener(listener.get())
        }
        if (!showBalloon) {
            notification.expire()
        }
        return notification
    }

    companion object {
        private const val GERRIT_NOTIFICATION_GROUP = "gerrit"
    }
}
