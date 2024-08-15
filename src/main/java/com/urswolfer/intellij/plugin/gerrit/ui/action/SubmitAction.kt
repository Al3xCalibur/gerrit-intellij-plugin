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

import com.google.gerrit.extensions.api.changes.SubmitInput
import com.google.gerrit.extensions.common.ChangeInfo
import com.google.inject.Inject
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService

/**
 * @author Urs Wolfer
 */
// proxy class below is registered
open class SubmitAction : AbstractLoggedInChangeAction("Submit", "Submit Change", AllIcons.ToolbarDecorator.Export) {
    @Inject
    private lateinit var notificationService: NotificationService

    override fun update(e: AnActionEvent) {
        super.update(e)
        val selectedChange = getSelectedChange(e)
        if (selectedChange != null && isSubmittable(selectedChange)) {
            e.presentation.isEnabled = false
        }
    }

    private fun isSubmittable(selectedChange: ChangeInfo): Boolean {
        return !selectedChange.submittable
    }

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val selectedChange = getSelectedChange(anActionEvent) ?: return

        val project = anActionEvent.getData(PlatformDataKeys.PROJECT)!!
        val submitInput = SubmitInput()
        gerritUtil.postSubmit(selectedChange.id, submitInput, project) {
            val notification = NotificationBuilder(
                project, "Change submitted", getSuccessMessage(selectedChange)
            ).hideBalloon()
            notificationService.notifyInformation(notification)
        }
    }

    private fun getSuccessMessage(changeInfo: ChangeInfo): String {
        return "Change '${changeInfo.subject}' submitted successfully."
    }

    class Proxy : SubmitAction() {
        private val delegate: SubmitAction = GerritModule.getInstance<SubmitAction>()

        override fun update(e: AnActionEvent) {
            delegate.update(e)
        }

        override fun actionPerformed(e: AnActionEvent) {
            delegate.actionPerformed(e)
        }
    }
}
