/*
 * Copyright 2013-2015 Urs Wolfer
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.urswolfer.intellij.plugin.gerrit.git

import com.google.gerrit.extensions.common.*
import com.google.inject.Inject
import com.intellij.dvcs.util.CommitCompareInfo
import com.intellij.openapi.application.Application
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsShortCommitDetailsImpl
import com.intellij.vcs.log.impl.VcsUserImpl
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.util.*
import git4idea.GitCommit
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.commands.*
import git4idea.fetch.GitFetchSupport
import git4idea.history.GitHistoryUtils
import git4idea.merge.GitConflictResolver
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.util.GitUntrackedFilesHelper

/**
 * @author Urs Wolfer
 */
class GerritGitUtil @Inject constructor(
    private val git: Git,
    private val application: Application,
    private val virtualFileManager: VirtualFileManager,
    private val gerritSettings: GerritSettings,
    private val notificationService: NotificationService
) {
    fun getRepositories(project: Project): Iterable<GitRepository> {
        val repositoryManager = GitUtil.getRepositoryManager(project)
        return repositoryManager.repositories
    }

    fun getRepositoryForGerritProject(project: Project, gerritProjectName: String): GitRepository? {
        val repositoriesFromRoots = getRepositories(project)
        for (repository in repositoriesFromRoots) {
            for (remote in repository.remotes) {
                if (remote.name == gerritProjectName) {
                    return repository
                }
                for (remoteUrl in remote.urls) {
                    if (UrlUtils.stripGitExtension(remoteUrl).endsWith(gerritProjectName)) {
                        return repository
                    }
                }
            }
        }
        return null
    }

    fun getRemoteForChange(
        project: Project?,
        gitRepository: GitRepository,
        fetchInfo: FetchInfo
    ): GitRemote? {
        val url = fetchInfo.url
        for (remote in gitRepository.remotes) {
            val repositoryUrls: MutableList<String> = ArrayList()
            repositoryUrls.addAll(remote.urls)
            repositoryUrls.addAll(remote.pushUrls)
            for (repositoryUrl in repositoryUrls) {
                if (UrlUtils.urlHasSameHost(repositoryUrl, url)
                    || UrlUtils.urlHasSameHost(repositoryUrl, gerritSettings.cloneBaseUrlOrHost)
                ) {
                    return remote
                }
            }
        }
        val notification = NotificationBuilder(
            project, "Error",
            "Could not fetch commit because no remote url matches Gerrit host.<br/>" +
                    "Git repository: '${gitRepository.presentableUrl}'."
        )
        notificationService.notifyError(notification)
        return null
    }

    fun fetchChange(
        project: Project,
        gitRepository: GitRepository,
        fetchInfo: FetchInfo,
        commitHash: String,
        fetchCallback: (() -> Unit)?
    ) {
        GitVcs.runInBackground(object : Backgroundable(project, "Fetching...", false) {
            override fun run(indicator: ProgressIndicator) {
                val remote: GitRemote?
                val fetch: String
                val commitIsFetched = checkIfCommitIsFetched(gitRepository, commitHash)
                if (commitIsFetched) {
                    // 'git fetch' works with a local path instead of a remote -> this way FETCH_HEAD is set
                    remote = GitRemote(
                        gitRepository.root.path,
                        emptyList(), emptySet(), emptyList(), emptyList()
                    )
                    fetch = commitHash
                } else {
                    remote = getRemoteForChange(project, gitRepository, fetchInfo)
                    if (remote == null) {
                        return
                    }
                    fetch = fetchInfo.ref
                }
                val result = GitFetchSupport.fetchSupport(project).fetch(gitRepository, remote, fetch)
                result.showNotificationIfFailed()

                try {
                    fetchCallback?.invoke()
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }
        })
    }

    fun cherryPickChange(project: Project?, changeInfo: ChangeInfo?, revisionId: String?) {
        FileDocumentManager.getInstance().saveAllDocuments()
        ChangeListManagerImpl.getInstanceImpl(project!!).blockModalNotifications()

        object : Backgroundable(project, "Cherry-picking...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val gitRepository = getRepositoryForGerritProject(project, changeInfo!!.project)
                    if (gitRepository == null) {
                        val notification = NotificationBuilder(
                            project, "Error",
                            "No repository found for Gerrit project: '${changeInfo.project}'."
                        )
                        notificationService.notifyError(notification)
                        return
                    }

                    val virtualFile = gitRepository.root

                    val notLoaded = "Not loaded"
                    val notLoadedUser: VcsUser = VcsUserImpl(notLoaded, notLoaded)
                    val gitCommit: VcsShortCommitDetails = VcsShortCommitDetailsImpl(
                        HashImpl.build(revisionId!!),
                        emptyList(),
                        0,
                        virtualFile,
                        notLoaded,
                        notLoadedUser,
                        notLoadedUser,
                        0
                    )

                    cherryPick(gitRepository, gitCommit, git, project)
                } finally {
                    application.invokeLater {
                        virtualFileManager.syncRefresh()
                        ChangeListManagerImpl.getInstanceImpl(project).unblockModalNotifications()
                    }
                }
            }
        }.queue()
    }

    /**
     * A lot of this code is based on: git4idea.cherrypick.GitCherryPicker#cherryPick() (which is private)
     */
    private fun cherryPick(
        repository: GitRepository, commit: VcsShortCommitDetails,
        git: Git, project: Project
    ): Boolean {
        val conflictDetector = GitSimpleEventDetector(GitSimpleEventDetector.Event.CHERRY_PICK_CONFLICT)
        val localChangesOverwrittenDetector =
            GitSimpleEventDetector(GitSimpleEventDetector.Event.LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK)
        val untrackedFilesDetector =
            GitUntrackedFilesOverwrittenByOperationDetector(repository.root)
        val result = git.cherryPick(
            repository, commit.id.asString(), false, true,
            conflictDetector, localChangesOverwrittenDetector, untrackedFilesDetector
        )
        if (result.success()) {
            return true
        } else if (conflictDetector.hasHappened()) {
            return CherryPickConflictResolver(
                project, repository.root,
                commit.id.toShortString(), commit.author.name,
                commit.subject
            ).merge()
        } else if (untrackedFilesDetector.wasMessageDetected()) {
            val description = "Some untracked working tree files would be overwritten by cherry-pick.<br/>" +
                    "Please move, remove or add them before you can cherry-pick. <a href='view'>View them</a>"

            GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(
                project, repository.root,
                untrackedFilesDetector.relativeFilePaths,
                "cherry-pick", description
            )
            return false
        } else if (localChangesOverwrittenDetector.hasHappened()) {
            notificationService.notifyError(
                NotificationBuilder(
                    project, "Cherry-Pick Error",
                    "Your local changes would be overwritten by cherry-pick.<br/>Commit your changes or stash them to proceed."
                )
            )
            return false
        } else {
            notificationService.notifyError(
                NotificationBuilder(
                    project, "Cherry-Pick Error",
                    result.errorOutputAsHtmlString
                )
            )
            return false
        }
    }


    /**
     * Copy of: git4idea.cherrypick.GitCherryPicker.CherryPickConflictResolver (which is private)
     */
    private class CherryPickConflictResolver(
        project: Project, root: VirtualFile,
        commitHash: String, commitAuthor: String, commitMessage: String
    ) : GitConflictResolver(project, setOf(root), makeParams(commitHash, commitAuthor, commitMessage)) {
        override fun notifyUnresolvedRemain() {
            // we show a [possibly] compound notification after cherry-picking all commits.
        }

        companion object {
            private fun makeParams(commitHash: String, commitAuthor: String, commitMessage: String): Params {
                val params = Params()
                params.setErrorNotificationTitle("Cherry-picked with conflicts")
                params.setMergeDialogCustomizer(
                    CherryPickMergeDialogCustomizer(
                        commitHash,
                        commitAuthor,
                        commitMessage
                    )
                )
                return params
            }
        }
    }


    /**
     * Copy of: git4idea.cherrypick.GitCherryPicker.CherryPickMergeDialogCustomizer (which is private)
     */
    private class CherryPickMergeDialogCustomizer(
        private val myCommitHash: String,
        private val myCommitAuthor: String,
        private val myCommitMessage: String
    ) : MergeDialogCustomizer() {
        override fun getMultipleFileMergeDescription(files: MutableCollection<VirtualFile>): String {
            return "<html>Conflicts during cherry-picking commit <code>" + myCommitHash + "</code> made by " + myCommitAuthor + "<br/>" +
                    "<code>\"" + myCommitMessage + "\"</code></html>"
        }

        override fun getLeftPanelTitle(file: VirtualFile): String {
            return "Local changes"
        }

        override fun getRightPanelTitle(file: VirtualFile, lastRevisionNumber: VcsRevisionNumber?): String {
            return "<html>Changes from cherry-pick <code>$myCommitHash</code>"
        }
    }

    fun checkIfCommitIsFetched(repository: GitRepository, commitHash: String): Boolean {
        val listener = FormattedGitLineHandlerListener()
        val h = GitLineHandler(repository.project, repository.root, GitCommand.SHOW)
        h.setSilent(false)
        h.setStdoutSuppressed(false)
        h.addParameters(commitHash)
        h.addParameters("--format=short")
        h.endOptions()
        h.addLineListener(listener)
        val gitCommandResult = git.runCommand { h }
        val success = gitCommandResult.success()
        val output = gitCommandResult.output
        val isCommit = output.isNotEmpty() && output[0].startsWith("commit")
        return success && isCommit
    }

    private fun loadCommitsToCompare(
        repository: GitRepository,
        branchName: String,
        project: Project
    ): Pair<List<GitCommit>, List<GitCommit>> {
        val headToBranch: List<GitCommit>
        val branchToHead: List<GitCommit>
        try {
            headToBranch = GitHistoryUtils.history(project, repository.root, "..$branchName")
            branchToHead = GitHistoryUtils.history(project, repository.root, "$branchName..")
        } catch (e: VcsException) {
            // we treat it as critical and report an error
            throw RuntimeException(
                "Couldn't get [git log .." + branchName + "] on repository [" + repository.root + "]",
                e
            )
        }
        return Pair.create(headToBranch, branchToHead)
    }

    fun loadCommitsToCompare(
        repositories: Collection<GitRepository>,
        branchName: String,
        project: Project
    ): CommitCompareInfo {
        val compareInfo = CommitCompareInfo()
        for (repository in repositories) {
            val listListPair = loadCommitsToCompare(repository, branchName, project)
            compareInfo.put(repository, listListPair.first, listListPair.second)
        }
        return compareInfo
    }

    @Throws(VcsException::class)
    fun setUpstreamBranch(repository: GitRepository?, remoteBranch: String) {
        val listener = FormattedGitLineHandlerListener()
        val h = GitLineHandler(repository!!.project, repository.root, GitCommand.BRANCH)
        h.setSilent(false)
        h.setStdoutSuppressed(false)
        h.addParameters("-u", "remotes/$remoteBranch")
        h.endOptions()
        h.addLineListener(listener)
        val gitCommandResult = git.runCommand { h }
        if (!gitCommandResult.success()) {
            throw VcsException(listener.htmlMessage)
        }
    }

    private class FormattedGitLineHandlerListener : GitLineHandlerListener {
        private val messages: MutableList<String> = ArrayList()

        override fun onLineAvailable(s: String, key: Key<*>?) {
            val message = if (s.startsWith("\t")) {
                "<b>" + s.substring(1) + "</b>"
            } else
                s
            messages.add(message)
        }

        override fun processTerminated(i: Int) {
        }

        override fun startFailed(throwable: Throwable) {
        }

        val htmlMessage: String
            get() = messages.joinToString("<br/>")
    }
}
