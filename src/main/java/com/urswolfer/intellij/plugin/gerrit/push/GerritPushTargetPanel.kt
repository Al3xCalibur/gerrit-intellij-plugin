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
package com.urswolfer.intellij.plugin.gerrit.push

import com.intellij.dvcs.push.RepositoryNodeListener
import com.intellij.dvcs.push.ui.PushTargetTextField
import com.intellij.dvcs.push.ui.RepositoryNode
import com.intellij.dvcs.push.ui.RepositoryWithBranchPanel
import com.intellij.openapi.diagnostic.Logger
import git4idea.push.GitPushSupport
import git4idea.push.GitPushTarget
import git4idea.push.GitPushTargetPanel
import git4idea.repo.GitRepository
import java.lang.reflect.Field

class GerritPushTargetPanel(
    support: GitPushSupport,
    repository: GitRepository,
    defaultTarget: GitPushTarget?,
    gerritPushOptionsPanel: GerritPushOptionsPanel
) : GitPushTargetPanel(support, repository, defaultTarget) {
    private var branch: String? = null

    init {
        var initialBranch: String? = null
        if (defaultTarget != null) {
            initialBranch = defaultTarget.branch.nameForRemoteOperations
        }
        gerritPushOptionsPanel.gerritPushExtensionPanel.registerGerritPushTargetPanel(this, initialBranch)
    }

    fun initBranch(branch: String?, pushToGerritByDefault: Boolean) {
        setBranch(branch)
        try {
            val myFireOnChangeActionField = getField("myFireOnChangeAction")
            val myFireOnChangeAction = myFireOnChangeActionField[this] as Runnable?
            if (myFireOnChangeAction != null) {
                val repoPanelField = myFireOnChangeAction.javaClass.getDeclaredField("val\$repoPanel")
                repoPanelField.isAccessible = true
                val repoPanel = repoPanelField[myFireOnChangeAction] as RepositoryWithBranchPanel<GitPushTarget>
                repoPanel.addRepoNodeListener(object : RepositoryNodeListener<GitPushTarget> {
                    override fun onTargetChanged(newTarget: GitPushTarget) {}

                    override fun onSelectionChanged(isSelected: Boolean) {
                        if (isSelected) {
                            updateBranchTextField(myFireOnChangeAction)
                        }
                    }

                    override fun onTargetInEditMode(s: String) {}
                })

                if (pushToGerritByDefault) {
                    updateBranchTextField(myFireOnChangeAction)
                }
            }
        } catch (e: NoSuchFieldException) {
            LOG.error(e)
        } catch (e: IllegalAccessException) {
            LOG.error(e)
        }
        updateBranch(branch)
    }

    fun updateBranch(branch: String?) {
        setBranch(branch)
        try {
            val myFireOnChangeActionField = getField("myFireOnChangeAction")
            val myFireOnChangeAction = myFireOnChangeActionField[this] as Runnable?
            if (myFireOnChangeAction != null) {
                val repoNodeField = myFireOnChangeAction.javaClass.getDeclaredField("val\$repoNode")
                repoNodeField.isAccessible = true
                val repoNode = repoNodeField[myFireOnChangeAction] as RepositoryNode
                if (repoNode.isChecked) {
                    updateBranchTextField(myFireOnChangeAction)
                }
            }
        } catch (e: NoSuchFieldException) {
            LOG.error(e)
        } catch (e: IllegalAccessException) {
            LOG.error(e)
        }
    }

    private fun updateBranchTextField(myFireOnChangeAction: Runnable) {
        try {
            val myTargetEditorField = getField("myTargetEditor")
            val myTargetEditor = myTargetEditorField[this] as PushTargetTextField
            myTargetEditor.setText(branch)

            fireOnChange()

            myFireOnChangeAction.run()
        } catch (e: NoSuchFieldException) {
            LOG.error(e)
        } catch (e: IllegalAccessException) {
            LOG.error(e)
        }
    }

    @Throws(NoSuchFieldException::class)
    private fun getField(fieldName: String): Field {
        val field = GitPushTargetPanel::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field
    }

    fun setBranch(branch: String?) {
        if (branch.isNullOrEmpty() || branch.endsWith("/")) {
            this.branch = null
            return
        }
        this.branch = branch.trim { it <= ' ' }
    }

    companion object {
        private val LOG = Logger.getInstance(GerritPushTargetPanel::class.java)
    }
}
