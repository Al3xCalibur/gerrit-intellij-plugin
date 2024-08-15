/*
 * Copyright 2013-2014 Urs Wolfer
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

import com.google.common.base.Strings
import com.google.gerrit.extensions.api.changes.AbandonInput
import com.google.gerrit.extensions.common.ChangeInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.ui.SafeHtmlTextEditor
import javax.swing.JComponent

/**
 * @author Urs Wolfer
 */
// proxy class below is registered
open class AbandonAction : AbstractLoggedInChangeAction("Abandon", "Abandon Change", AllIcons.Actions.Cancel) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val selectedChange = getSelectedChange(e)
        if (selectedChange != null && !canAbandon(selectedChange)) {
            e.presentation.isEnabled = false
        }
    }

    private fun canAbandon(selectedChange: ChangeInfo): Boolean {
        if (selectedChange.actions == null) {
            // if there are absolutely no actions, assume an older Gerrit instance
            // which does not support receiving actions
            // return false once we drop Gerrit < 2.9 support
            return true
        }
        val abandonAction = selectedChange.actions["abandon"]
        return abandonAction != null && abandonAction.enabled
    }

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val selectedChange = getSelectedChange(anActionEvent) ?: return

        val abandonInput = AbandonInput()

        val project = anActionEvent.getData(PlatformDataKeys.PROJECT)!!
        val editor = SafeHtmlTextEditor(project)
        val dialog = AbandonDialog(project, true, editor)
        dialog.show()
        if (!dialog.isOK) {
            return
        }
        val message = editor.messageField.text.trim { it <= ' ' }
        if (!Strings.isNullOrEmpty(message)) {
            abandonInput.message = message
        }

        gerritUtil.postAbandon(selectedChange.id, abandonInput, project)
    }

    private class AbandonDialog(project: Project?, canBeParent: Boolean, private val editor: SafeHtmlTextEditor) :
        DialogWrapper(project, canBeParent) {
        init {
            title = "Abandon Change"
            setOKButtonText("Abandon")
            init()
        }

        override fun createCenterPanel(): JComponent {
            return editor
        }

        override fun getPreferredFocusedComponent(): JComponent {
            return editor.messageField
        }
    }

    class Proxy : AbandonAction() {
        private val delegate: AbandonAction = GerritModule.getInstance<AbandonAction>()

        override fun update(e: AnActionEvent) {
            delegate.update(e)
        }

        override fun actionPerformed(e: AnActionEvent) {
            delegate.actionPerformed(e)
        }
    }
}
