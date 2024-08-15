/*
 *
 *  * Copyright 2013-2014 Urs Wolfer
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package com.urswolfer.intellij.plugin.gerrit.git

import com.google.gerrit.extensions.common.RevisionInfo
import com.intellij.openapi.project.Project
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService
import git4idea.repo.GitRepository

/**
 * This class helps to simultaneously fetch multiple revisions from a git repository.
 *
 * @author Thomas Forrer
 */
class RevisionFetcher(
    private val gerritUtil: GerritUtil,
    private val gerritGitUtil: GerritGitUtil,
    private val notificationService: NotificationService,
    private val project: Project,
    private val gitRepository: GitRepository
) {
    private val revisionInfoList: MutableMap<String, RevisionInfo?> = mutableMapOf()
    private val fetchCallbacks: MutableList<HasBeenCalled> = mutableListOf()

    fun addRevision(commitHash: String, revisionInfo: RevisionInfo?): RevisionFetcher {
        revisionInfoList[commitHash] = revisionInfo
        return this
    }

    /**
     * Fetch the changes for the provided revisions.
     * @param callback the callback will be executed as soon as all revisions have been fetched successfully
     */
    fun fetch(callback: () -> Unit) {
        for ((key, value) in revisionInfoList) {
            val called = HasBeenCalled()
            fetchCallbacks.add(called)
            fetchChange(key, value) {
                called.called = true
                synchronized(fetchCallbacks) {
                    if (fetchCallbacks.all { it.called }) {
                        callback()
                    }
                }
            }
        }
    }

    private fun fetchChange(commitHash: String, revisionInfo: RevisionInfo?, callback: () -> Unit) {
        val fetchInfo = gerritUtil.getFirstFetchInfo(revisionInfo)
        if (fetchInfo == null) {
            notifyError()
        } else {
            gerritGitUtil.fetchChange(project, gitRepository, fetchInfo, commitHash, callback)
        }
    }

    private fun notifyError() {
        val notification = NotificationBuilder(
            project, "Cannot fetch changes",
            "No fetch information provided. If you are using Gerrit 2.8 or later, " +
                    "you need to install the plugin 'download-commands' in Gerrit."
        )
        notificationService.notifyError(notification)
    }

    private inner class HasBeenCalled(var called: Boolean = false)
}
