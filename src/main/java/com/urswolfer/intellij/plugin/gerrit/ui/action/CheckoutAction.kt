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

import com.google.gerrit.extensions.common.ChangeInfo
import com.google.inject.Inject
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.vcs.VcsException
import com.urswolfer.intellij.plugin.gerrit.GerritModule
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService
import git4idea.GitVcs
import git4idea.branch.GitBrancher
import git4idea.validators.GitNewBranchNameValidator
import java.util.*

/**
 * @author Urs Wolfer
 */
// proxy class below is registered
open class CheckoutAction : AbstractChangeAction("Checkout", "Checkout change", AllIcons.Actions.CheckOut) {
    @Inject
    private lateinit var gerritGitUtil: GerritGitUtil

    @Inject
    private lateinit var fetchAction: FetchAction

    @Inject
    private lateinit var selectedRevisions: SelectedRevisions

    @Inject
    private lateinit var notificationService: NotificationService

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val selectedChange = getSelectedChange(anActionEvent) ?: return

        val project = anActionEvent.getRequiredData(PlatformDataKeys.PROJECT)

        getChangeDetail(selectedChange, project) { changeDetails: ChangeInfo ->
            val fetchCallback = callback@{
                val brancher = project.getService(GitBrancher::class.java)
                val repository = gerritGitUtil.getRepositoryForGerritProject(project, changeDetails.project)
                if (repository == null) {
                    val notification = NotificationBuilder(
                        project, "Error",
                        "No repository found for Gerrit project: '${changeDetails.project}'."
                    )
                    notificationService.notifyError(notification)
                    return@callback
                }
                val branchName = buildBranchName(changeDetails)
                var checkedOutBranchName = branchName
                val firstFetchInfo = gerritUtil.getFirstFetchInfo(changeDetails)!!
                val remote = gerritGitUtil.getRemoteForChange(project, repository, firstFetchInfo) ?: return@callback
                var validName = false
                var i = 0
                val gitRepositories = listOf(repository)
                val newBranchNameValidator = GitNewBranchNameValidator.newInstance(gitRepositories)
                while (!validName && i < 100) { // do not loop endless - stop after 100 tries because most probably something went wrong
                    checkedOutBranchName = branchName + (if (i != 0) "_$i" else "")
                    validName = newBranchNameValidator.checkInput(checkedOutBranchName)
                    i++
                }
                val finalCheckedOutBranchName = checkedOutBranchName
                ApplicationManager.getApplication().invokeLater {
                    brancher.checkoutNewBranchStartingFrom(finalCheckedOutBranchName, "FETCH_HEAD", gitRepositories) {
                        GitVcs.runInBackground(object : Backgroundable(project, "Setting upstream branch...", false) {
                            override fun run(indicator: ProgressIndicator) {
                                try {
                                    gerritGitUtil.setUpstreamBranch(
                                        repository,
                                        remote.name + "/" + changeDetails.branch
                                    )
                                } catch (e: VcsException) {
                                    val builder = NotificationBuilder(project, "Checkout Error", e.message)
                                    notificationService.notifyError(builder)
                                }
                            }
                        })
                    }
                }
            }
            fetchAction.fetchChange(selectedChange, project, fetchCallback)
        }
    }

    private fun buildBranchName(changeDetails: ChangeInfo?): String {
        val revisionInfo = changeDetails!!.revisions[selectedRevisions[changeDetails]]
        var topic = changeDetails.topic
        if (topic == null) {
            topic = changeDetails._number.toString()
        }
        var branchName = "review/" + changeDetails.owner.name.lowercase(Locale.getDefault()) + '/' + topic
        if (revisionInfo!!._number != changeDetails.revisions.size) {
            branchName += "-patch" + revisionInfo._number
        }
        return branchName.replace(" ", "_").replace("?", "_")
    }

    class Proxy : CheckoutAction() {
        private val delegate: CheckoutAction = GerritModule.getInstance<CheckoutAction>()

        override fun actionPerformed(e: AnActionEvent) {
            delegate.actionPerformed(e)
        }
    }
}
