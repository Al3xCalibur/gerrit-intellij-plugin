/*
 * Copyright 2013-2015 Urs Wolfer
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

import com.google.common.base.Optional
import com.google.common.collect.*
import com.google.gerrit.extensions.common.*
import com.google.inject.Inject
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowser
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.containers.ContainerUtil
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil
import com.urswolfer.intellij.plugin.gerrit.git.RevisionFetcher
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser.ChangesWithCommitMessageProvider
import com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser.CommitDiffBuilder
import com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser.CommitDiffBuilder.ChangesProvider
import com.urswolfer.intellij.plugin.gerrit.ui.changesbrowser.SelectBaseRevisionAction
import com.urswolfer.intellij.plugin.gerrit.util.*
import git4idea.GitCommit
import git4idea.history.GitHistoryUtils
import java.util.*

/**
 * @author Thomas Forrer
 */
class RepositoryChangesBrowserProvider @Inject constructor(
    private val gerritGitUtil: GerritGitUtil,
    private val gerritUtil: GerritUtil,
    private val notificationService: NotificationService,
    private val log: Logger,
    private val changeNodeDecorators: Set<GerritChangeNodeDecorator>,
    private val selectedRevisions: SelectedRevisions
) {
    private lateinit var selectBaseRevisionAction: SelectBaseRevisionAction

    fun get(project: Project, changeListPanel: GerritChangeListPanel): GerritRepositoryChangesBrowser {
        selectBaseRevisionAction = SelectBaseRevisionAction(selectedRevisions)

        val table = changeListPanel.table

        val changesBrowser = GerritRepositoryChangesBrowser(project)
        changesBrowser.diffAction.registerCustomShortcutSet(CommonShortcuts.getDiff(), table)
        changesBrowser.viewerScrollPane.border = IdeBorderFactory.createBorder(SideBorder.LEFT or SideBorder.TOP)
        changesBrowser.setChangeNodeDecorator(changesBrowser.changeNodeDecorator)

        changeListPanel.addListSelectionListener { changeInfo: ChangeInfo ->
            changesBrowser.setSelectedChange(
                changeInfo
            )
        }
        return changesBrowser
    }

    inner class GerritRepositoryChangesBrowser(private val project: Project) : CommittedChangesBrowser(project) {
        private var selectedChange: ChangeInfo? = null
        private var baseRevision: Pair<String, RevisionInfo>? = null

        init {
            selectBaseRevisionAction.addRevisionSelectedListener { revisionInfo: Pair<String, RevisionInfo>? ->
                baseRevision = revisionInfo
                updateChangesBrowser()
            }
            selectedRevisions.addObserver { o: Observable?, arg: Any ->
                if (selectedChange != null && selectedChange!!.id == arg) {
                    updateChangesBrowser()
                }
            }
        }

        override fun updateDiffContext(chain: DiffRequestChain) {
            super.updateDiffContext(chain)
            chain.putUserData<ChangeInfo>(GerritUserDataKeys.CHANGE, selectedChange)
            chain.putUserData<Pair<String, RevisionInfo>>(
                GerritUserDataKeys.BASE_REVISION,
                baseRevision
            )
        }

        override fun createToolbarActions(): List<AnAction> {
            return ContainerUtil.prepend(super.createToolbarActions(), selectBaseRevisionAction, Separator())
        }

        fun setSelectedChange(changeInfo: ChangeInfo) {
            selectedChange = changeInfo
            gerritUtil.getChangeDetails(changeInfo._number, project) { changeDetails: ChangeInfo ->
                if (selectedChange!!.id == changeDetails.id) {
                    selectedChange = changeDetails
                    baseRevision = null
                    selectBaseRevisionAction.setSelectedChange(selectedChange)
                    for (decorator in changeNodeDecorators) {
                        decorator.onChangeSelected(project, selectedChange)
                    }
                    updateChangesBrowser()
                }
            }
        }

        protected fun updateChangesBrowser() {
            viewer.setEmptyText("Loading...")
            setChangesToDisplay(emptyList())
            val gitRepository = gerritGitUtil.getRepositoryForGerritProject(project, selectedChange!!.project)
            if (gitRepository == null) {
                viewer.setEmptyText("Diff cannot be displayed as no local repository was found")
                return
            }

            val revisions = selectedChange!!.revisions
            val revisionId = selectedRevisions[selectedChange]
            val currentRevision = revisions[revisionId]
            val revisionFetcher =
                RevisionFetcher(gerritUtil, gerritGitUtil, notificationService, project, gitRepository)
                    .addRevision(revisionId, currentRevision)
            val baseRevision = baseRevision
            if (baseRevision != null) {
                revisionFetcher.addRevision(baseRevision.first, baseRevision.second)
            }
            revisionFetcher.fetch {
                val totalDiff: Collection<Change>
                try {
                    val gitRepositoryRoot = gitRepository.root
                    val changesProvider: ChangesProvider = ChangesWithCommitMessageProvider()
                    val currentCommit = getCommit(gitRepositoryRoot, revisionId)
                    if (baseRevision != null) {
                        val baseCommit = getCommit(gitRepositoryRoot, baseRevision.first)
                        totalDiff = CommitDiffBuilder(project, gitRepositoryRoot, baseCommit, currentCommit)
                            .withChangesProvider(changesProvider).diff
                    } else {
                        totalDiff = changesProvider.provide(currentCommit)
                    }
                } catch (e: VcsException) {
                    log.warn("Error getting Git commit details.", e)
                    val notification = NotificationBuilder(
                        project, "Cannot show change",
                        "Git error occurred while getting commit. Please check if Gerrit is configured as remote " +
                                "for the currently used Git repository."
                    )
                    notificationService.notifyError(notification)
                    return@fetch null
                }

                ApplicationManager.getApplication().invokeLater {
                    viewer.setEmptyText("No changes")
                    setChangesToDisplay(Lists.newArrayList(totalDiff))
                }
                null
            }
        }

        @Throws(VcsException::class)
        private fun getCommit(gitRepositoryRoot: VirtualFile, revisionId: String?): GitCommit {
            // -1: limit; log exactly this commit; git show would do this job also, but there is no api in GitHistoryUtils
            // ("git show hash" <-> "git log hash -1")
            val history = GitHistoryUtils.history(project, gitRepositoryRoot, revisionId, "-1")
            return Iterables.getOnlyElement(history)
        }

        val changeNodeDecorator: ChangeNodeDecorator
            get() = object : ChangeNodeDecorator {
                override fun decorate(change: Change, component: SimpleColoredComponent, isShowFlatten: Boolean) {
                    for (decorator in changeNodeDecorators) {
                        decorator.decorate(project, change, component, selectedChange)
                    }
                }

                override fun preDecorate(change: Change, renderer: ChangesBrowserNodeRenderer, showFlatten: Boolean) {
                }
            }
    }
}
