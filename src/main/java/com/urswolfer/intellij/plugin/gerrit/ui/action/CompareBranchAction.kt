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

import com.google.gerrit.extensions.common.*
import com.google.inject.Inject
import com.intellij.dvcs.ui.CompareBranchesDialog
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil
import com.urswolfer.intellij.plugin.gerrit.util.*
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitCompareBranchesHelper
import java.util.concurrent.Callable

/**
 * @author Urs Wolfer
 */
// proxy class below is registered
open class CompareBranchAction :
    AbstractChangeAction("Compare with Branch", "Compare change with current branch", AllIcons.Actions.Diff) {
    @Inject
    private lateinit var gerritGitUtil: GerritGitUtil

    @Inject
    private lateinit var fetchAction: FetchAction

    @Inject
    private lateinit var notificationService: NotificationService

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val selectedChange = getSelectedChange(anActionEvent) ?: return

        val project = anActionEvent.getData(PlatformDataKeys.PROJECT)
        val successCallable = {
            diffChange(project, selectedChange)
            null
        }
        fetchAction.fetchChange(selectedChange, project, successCallable)
    }

    private fun diffChange(project: Project?, changeInfo: ChangeInfo?) {
        val gitRepository = gerritGitUtil.getRepositoryForGerritProject(project, changeInfo!!.project)
        if (gitRepository == null) {
            val notification = NotificationBuilder(
                project, "Error",
                String.format("No repository found for Gerrit project: '%s'.", changeInfo.project)
            )
            notificationService.notifyError(notification)
            return
        }

        val branchName = "FETCH_HEAD"
        val currentBranch = gitRepository.currentBranch
        val currentBranchName = currentBranch?.fullName ?: gitRepository.currentRevision
        checkNotNull(currentBranchName) { "Current branch is neither a named branch nor a revision" }

        val compareInfo = gerritGitUtil.loadCommitsToCompare(listOf(gitRepository), branchName, project!!)
        ApplicationManager.getApplication().invokeLater {
            CompareBranchesDialog(
                GitCompareBranchesHelper(project),
                branchName,
                currentBranchName,
                compareInfo,
                gitRepository,
                false
            ).show()
        }
    }

    class Proxy : CompareBranchAction() {
        private val delegate: CompareBranchAction = GerritModule.getInstance<CompareBranchAction>()

        override fun actionPerformed(e: AnActionEvent) {
            delegate.actionPerformed(e)
        }
    }
}
