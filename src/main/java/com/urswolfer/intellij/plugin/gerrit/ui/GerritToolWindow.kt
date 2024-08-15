/*
 * Copyright 2013 Urs Wolfer
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.google.common.base.Strings
import com.google.gerrit.extensions.common.ChangeInfo
import com.google.inject.Inject
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowser
import com.intellij.ui.JBSplitter
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.Consumer
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import com.urswolfer.intellij.plugin.gerrit.rest.LoadChangesProxy
import com.urswolfer.intellij.plugin.gerrit.ui.filter.GerritChangesFilters
import git4idea.GitUtil
import java.util.*

/**
 * @author Urs Wolfer
 * @author Konrad Dobrzynski
 */
class GerritToolWindow @Inject constructor(
    private val gerritUtil: GerritUtil,
    private val gerritSettings: GerritSettings,
    private val changeListPanel: GerritChangeListPanel,
    private val log: Logger,
    private val changesFilters: GerritChangesFilters,
    private val repositoryChangesBrowserProvider: RepositoryChangesBrowserProvider
) {
    private lateinit var detailsPanel: GerritChangeDetailsPanel

    fun createToolWindowContent(project: Project): SimpleToolWindowPanel {
        changeListPanel.setProject(project)

        val panel = SimpleToolWindowPanel(true, true)

        val toolbar = createToolbar(project)
        toolbar.setTargetComponent(changeListPanel)
        panel.toolbar = toolbar.component

        val repositoryChangesBrowser: CommittedChangesBrowser = repositoryChangesBrowserProvider.get(project, changeListPanel)

        val detailsSplitter: JBSplitter = OnePixelSplitter(true, 0.6f)
        detailsSplitter.splitterProportionKey = "Gerrit.ListDetailSplitter.Proportion"
        detailsSplitter.setFirstComponent(changeListPanel)

        detailsPanel = GerritChangeDetailsPanel(project)
        changeListPanel.addListSelectionListener { changeInfo: ChangeInfo -> changeSelected(changeInfo, project) }
        val details = detailsPanel.component
        detailsSplitter.secondComponent = details

        val horizontalSplitter: JBSplitter = OnePixelSplitter(false, 0.7f)
        horizontalSplitter.splitterProportionKey = "Gerrit.DetailRepositoryChangeBrowser.Proportion"
        horizontalSplitter.firstComponent = detailsSplitter
        horizontalSplitter.secondComponent = repositoryChangesBrowser

        panel.setContent(horizontalSplitter)

        val repositories = GitUtil.getRepositoryManager(project).repositories
        if (repositories.isNotEmpty()) {
            reloadChanges(project, false)
        }

        registerVcsChangeListener(project)

        changeListPanel.showSetupHintWhenRequired(project)

        return panel
    }

    private fun registerVcsChangeListener(project: Project) {
        val vcsListener = VcsRepositoryMappingListener { reloadChanges(project, false) }
        project.messageBus.connect().subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, vcsListener)
    }

    private fun changeSelected(changeInfo: ChangeInfo, project: Project) {
        gerritUtil.getChangeDetails(
            changeInfo._number,
            project
        ) { changeDetails: ChangeInfo -> detailsPanel.setData(changeDetails) }
    }

    fun reloadChanges(project: Project?, requestSettingsIfNonExistent: Boolean) {
        getChanges(project, requestSettingsIfNonExistent, changeListPanel)
    }

    private fun getChanges(
        project: Project?,
        requestSettingsIfNonExistent: Boolean,
        consumer: Consumer<LoadChangesProxy>
    ) {
        val apiUrl = gerritSettings.host
        if (Strings.isNullOrEmpty(apiUrl)) {
            if (requestSettingsIfNonExistent) {
                val dialog = LoginDialog(project, gerritSettings, gerritUtil, log)
                dialog.show()
                if (!dialog.isOK) {
                    return
                }
            } else {
                return
            }
        }
        gerritUtil.getChangesForProject(changesFilters.query, project, consumer)
    }

    private fun createToolbar(project: Project): ActionToolbar {
        val groupFromConfig = ActionManager.getInstance().getAction("Gerrit.Toolbar") as DefaultActionGroup
        val group = DefaultActionGroup(groupFromConfig) // copy required (otherwise config action group gets modified)

        val filterGroup = DefaultActionGroup()
        val filters = changesFilters.filters
        for (filter in filters) {
            filterGroup.add(filter.getAction(project))
        }
        filterGroup.add(Separator())
        group.add(filterGroup, Constraints.FIRST)

        changesFilters.addObserver { observable: Observable?, o: Any? -> reloadChanges(project, true) }

        return ActionManager.getInstance().createActionToolbar("Gerrit.Toolbar", group, true)
    }
}
