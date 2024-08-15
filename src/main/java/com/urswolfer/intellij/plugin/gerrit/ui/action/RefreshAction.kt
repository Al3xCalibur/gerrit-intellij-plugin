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
package com.urswolfer.intellij.plugin.gerrit.ui.action

import com.google.inject.Inject
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.intellij.openapi.project.DumbAware
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.ui.GerritToolWindowFactory.ProjectService
import com.urswolfer.intellij.plugin.gerrit.ui.GerritUpdatesNotificationComponent

/**
 * @author Urs Wolfer
 */
// proxy class below is registered
open class RefreshAction : AnAction("Refresh", "Refresh changes list", AllIcons.Actions.Refresh), DumbAware,
    UpdateInBackground {
    @Inject
    private lateinit var gerritUpdatesNotificationComponent: GerritUpdatesNotificationComponent

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT)
        val projectService = project!!.getService(ProjectService::class.java)
        val gerritToolWindow = projectService.gerritToolWindow
        gerritToolWindow!!.reloadChanges(project, true)
        gerritUpdatesNotificationComponent.handleNotification()
    }

    class Proxy : RefreshAction() {
        private val delegate: RefreshAction = GerritModule.getInstance<RefreshAction>()

        override fun actionPerformed(e: AnActionEvent) {
            delegate.actionPerformed(e)
        }
    }
}
