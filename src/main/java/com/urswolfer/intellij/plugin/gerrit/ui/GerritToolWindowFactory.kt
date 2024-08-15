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

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.urswolfer.intellij.plugin.gerrit.GerritModule

/**
 * @author Urs Wolfer
 */
class GerritToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val gerritToolWindow: GerritToolWindow = GerritModule.getInstance<GerritToolWindow>()

        val projectService = project.getService(ProjectService::class.java)
        projectService.gerritToolWindow = gerritToolWindow

        val toolWindowContent = gerritToolWindow.createToolWindowContent(project)

        val contentManager = toolWindow.contentManager
        val content = ContentFactory.SERVICE.getInstance().createContent(toolWindowContent, "", false)
        contentManager.addContent(content)
        contentManager.setSelectedContent(content)
    }

    class ProjectService {
        var gerritToolWindow: GerritToolWindow? = null
    }
}
