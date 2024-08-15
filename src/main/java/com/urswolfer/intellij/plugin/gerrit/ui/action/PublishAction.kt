/*
 * Copyright 2013-2016 Urs Wolfer
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

import com.google.gerrit.extensions.client.ChangeStatus
import com.google.gerrit.extensions.common.*
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.urswolfer.intellij.plugin.gerrit.GerritModule

/**
 * @author Urs Wolfer
 */
// proxy class below is registered
open class PublishAction :
    AbstractLoggedInChangeAction("Publish Draft", "Publish Draft Change", AllIcons.Actions.Forward) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val selectedChange = getSelectedChange(e)
        if (selectedChange != null && !canPublish(selectedChange)) {
            e.presentation.isEnabled = false
        }
    }

    private fun canPublish(selectedChange: ChangeInfo): Boolean {
        if (ChangeStatus.DRAFT != selectedChange.status) {
            return false
        }
        val revisionActions =
            selectedChange.revisions[selectedChange.currentRevision]!!.actions
                ?: // if there are absolutely no actions, assume an older Gerrit instance
// which does not support receiving actions
// return false once we drop Gerrit < 2.9 support
                return true
        val publishAction = revisionActions["publish"]
        return publishAction != null && java.lang.Boolean.TRUE == publishAction.enabled
    }

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.getData(PlatformDataKeys.PROJECT)

        val selectedChange = getSelectedChange(anActionEvent) ?: return

        gerritUtil.postPublish(selectedChange.id, project)
    }

    class Proxy : PublishAction() {
        private val delegate: PublishAction = GerritModule.getInstance<PublishAction>()

        override fun update(e: AnActionEvent) {
            delegate.update(e)
        }

        override fun actionPerformed(e: AnActionEvent) {
            delegate.actionPerformed(e)
        }
    }
}
