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
package com.urswolfer.intellij.plugin.gerrit.ui.filter

import com.google.common.base.Function
import com.google.common.base.Optional
import com.google.common.collect.*
import com.google.inject.Inject
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import com.urswolfer.intellij.plugin.gerrit.ui.BasePopupAction
import git4idea.GitRemoteBranch
import git4idea.repo.GitRepository

/**
 * @author Thomas Forrer
 */
class BranchFilter : AbstractChangesFilter() {
    @Inject
    private lateinit var gerritGitUtil: GerritGitUtil

    @Inject
    private lateinit var gerritUtil: GerritUtil

    private var value: BranchDescriptor? = null

    override fun getAction(project: Project?): AnAction {
        return BranchPopupAction(project, "Branch")
    }

    override val searchQueryPart: String?
        get() = value?.query

    inner class BranchPopupAction(private val project: Project?, filterName: String) : BasePopupAction(filterName) {
        init {
            updateFilterValueLabel("All")
        }

        override fun createActions(actionConsumer: Consumer<AnAction?>) {
            actionConsumer.consume(object : DumbAwareAction("All") {
                override fun actionPerformed(e: AnActionEvent) {
                    value = null
                    updateFilterValueLabel("All")
                    setChanged()
                    notifyObservers(project)
                }
            })
            val repositories = gerritGitUtil.getRepositories(project)
            for (repository in repositories) {
                val group = DefaultActionGroup()
                group.add(Separator(getNameForRepository(repository)))
                group.add(object : DumbAwareAction("All") {
                    override fun actionPerformed(e: AnActionEvent) {
                        value = BranchDescriptor(repository)
                        updateFilterValueLabel("All (${getNameForRepository(repository)})")
                        setChanged()
                        notifyObservers(project)
                    }
                })
                val ordering = Ordering.natural<Comparable<*>?>().onResultOf(
                    Function { obj: GitRemoteBranch? -> obj!!.nameForRemoteOperations } as Function<GitRemoteBranch?, String?>)
                val branches: List<GitRemoteBranch> = repository.branches.remoteBranches.sortedWith(ordering)
                for (branch in branches) {
                    if (branch.nameForRemoteOperations != "HEAD") {
                        group.add(object : DumbAwareAction(branch.nameForRemoteOperations) {
                            override fun actionPerformed(e: AnActionEvent) {
                                value = BranchDescriptor(repository, branch)
                                updateFilterValueLabel(
                                    String.format(
                                        "%s (%s)",
                                        branch.nameForRemoteOperations,
                                        getNameForRepository(repository)
                                    )
                                )
                                setChanged()
                                notifyObservers(project)
                            }
                        })
                    }
                }
                actionConsumer.consume(group)
            }
        }
    }

    private fun getNameForRepository(repository: GitRepository): String? {
        return gerritUtil.getProjectNames(repository.remotes).firstOrNull() ?: ""
    }

    private inner class BranchDescriptor(private val repository: GitRepository, private val branch: GitRemoteBranch? = null) {
        val query: String
            get() = if (branch != null) {
                "(project:${getNameForRepository(repository)}+branch:${branch.nameForRemoteOperations})"
            } else {
                "project:${getNameForRepository(repository)}"
            }
    }
}
