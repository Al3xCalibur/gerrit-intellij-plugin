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

import com.google.gerrit.extensions.common.ChangeInfo
import com.google.inject.Inject
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil
import icons.DvcsImplIcons

/**
 * @author Urs Wolfer
 */
// proxy class below is registered
open class CherryPickAction : AbstractChangeAction(
    "Cherry-Pick (No Commit)",
    "Cherry-Pick change into active changelist without committing",
    DvcsImplIcons.CherryPick
) {
    @Inject
    private lateinit var gerritGitUtil: GerritGitUtil

    @Inject
    private lateinit var fetchAction: FetchAction

    @Inject
    private lateinit var selectedRevisions: SelectedRevisions

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val selectedChange = getSelectedChange(anActionEvent) ?: return
        val project = anActionEvent.getData(PlatformDataKeys.PROJECT)!!

        getChangeDetail(selectedChange, project) { changeInfo: ChangeInfo ->
            val fetchCallback = {
                ApplicationManager.getApplication().invokeLater {
                    gerritGitUtil.cherryPickChange(
                        project,
                        changeInfo,
                        selectedRevisions[changeInfo]
                    )
                }
            }
            fetchAction.fetchChange(selectedChange, project, fetchCallback)
        }
    }

    class Proxy : CherryPickAction() {
        private val delegate: CherryPickAction = GerritModule.getInstance<CherryPickAction>()

        override fun actionPerformed(e: AnActionEvent) {
            delegate.actionPerformed(e)
        }
    }
}
