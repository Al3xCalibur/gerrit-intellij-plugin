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
package com.urswolfer.intellij.plugin.gerrit.ui

import com.google.common.base.Strings
import com.google.gerrit.extensions.common.*
import com.google.inject.Inject
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import com.urswolfer.intellij.plugin.gerrit.util.*
import java.util.*

/**
 * @author Urs Wolfer
 */
// proxy class below is registered
open class GerritUpdatesNotificationComponent : ProjectComponent, Consumer<List<ChangeInfo>> {
    @Inject
    private lateinit var gerritUtil: GerritUtil
    @Inject
    private lateinit var gerritSettings: GerritSettings
    @Inject
    private lateinit var notificationService: NotificationService

    private var timer: Timer? = null
    private val notifiedChanges: MutableSet<String> = HashSet()
    private var project: Project? = null

    override fun projectOpened() {
        handleNotification()
        setupRefreshTask()
    }

    override fun projectClosed() {
        cancelPendingNotificationTasks()
        notifiedChanges.clear()
    }

    override fun getComponentName(): String {
        return "GerritUpdatesNotificationComponent"
    }

    fun handleConfigurationChange() {
        cancelPendingNotificationTasks()
        setupRefreshTask()
    }

    fun handleNotification() {
        if (!gerritSettings.reviewNotifications) {
            return
        }

        if (Strings.isNullOrEmpty(gerritSettings.host)
            || Strings.isNullOrEmpty(gerritSettings.login)
        ) {
            return
        }

        gerritUtil.getChangesToReview(project, this)
    }

    override fun consume(changes: List<ChangeInfo>) {
        var newChange = false
        for (change in changes) {
            if (!notifiedChanges.contains(change.id)) {
                newChange = true
                break
            }
        }
        if (newChange) {
            val stringBuilder = StringBuilder()
            stringBuilder.append("<ul>")
            for (change in changes) {
                stringBuilder
                    .append("<li>")
                    .append(if (!notifiedChanges.contains(change.changeId)) "<strong>NEW: </strong>" else "")
                    .append(change.project)
                    .append(": ")
                    .append(change.subject)
                    .append(" (Owner: ").append(change.owner.name).append(')')
                    .append("</li>")

                notifiedChanges.add(change.id)
            }
            stringBuilder.append("</ul>")
            val notification = NotificationBuilder(
                project,
                "Gerrit Changes waiting for my review",
                stringBuilder.toString()
            )
            notificationService.notifyInformation(notification)
        }
    }

    private fun cancelPendingNotificationTasks() {
        val timer = timer
        if (timer != null) {
            timer.cancel()
            this.timer = null
        }
    }

    private fun setupRefreshTask() {
        val refreshTimeout = gerritSettings.refreshTimeout.toLong()
        if (!gerritSettings.automaticRefresh || refreshTimeout <= 0) return
        val timer = timer ?: Timer().also { this.timer = it }
        timer.schedule(CheckReviewTask(), refreshTimeout * 60 * 1000)
    }

    open fun setProject(project: Project?) {
        this.project = project
    }

    private inner class CheckReviewTask : TimerTask() {
        override fun run() {
            handleNotification()

            val refreshTimeout = gerritSettings.refreshTimeout.toLong()
            if (gerritSettings.automaticRefresh && refreshTimeout > 0) {
                timer!!.schedule(CheckReviewTask(), refreshTimeout * 60 * 1000)
            }
        }
    }

    private class Proxy(project: Project?) : GerritUpdatesNotificationComponent() {
        private val delegate: GerritUpdatesNotificationComponent = GerritModule.getInstance<GerritUpdatesNotificationComponent>()

        init {
            delegate.setProject(project)
        }

        override fun projectOpened() {
            delegate.projectOpened()
        }

        override fun projectClosed() {
            delegate.projectClosed()
        }

        override fun getComponentName(): String {
            return delegate.componentName
        }

        override fun setProject(project: Project?) {
            delegate.setProject(project)
        }
    }
}
