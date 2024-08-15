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
import com.intellij.openapi.project.Project
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService
import java.util.concurrent.Callable

/**
 * @author Urs Wolfer
 */
class FetchAction @Inject constructor(
    private val gerritUtil: GerritUtil,
    private val gerritGitUtil: GerritGitUtil,
    private val notificationService: NotificationService,
    private val selectedRevisions: SelectedRevisions
) {
    fun fetchChange(selectedChange: ChangeInfo, project: Project?, fetchCallback: Callable<Void?>?) {
        gerritUtil.getChangeDetails(selectedChange._number, project) { changeDetails: ChangeInfo ->
            val gitRepository = gerritGitUtil.getRepositoryForGerritProject(project, changeDetails.project)
            if (gitRepository == null) {
                val notification = NotificationBuilder(
                    project, "Error",
                    String.format("No repository found for Gerrit project: '%s'.", changeDetails.project)
                )
                notificationService.notifyError(notification)
                return@getChangeDetails
            }

            val commitHash = selectedRevisions[changeDetails]

            val firstFetchInfo = gerritUtil.getFirstFetchInfo(changeDetails) ?: return@getChangeDetails
            gerritGitUtil.fetchChange(project, gitRepository, firstFetchInfo, commitHash, fetchCallback)
        }
    }
}
