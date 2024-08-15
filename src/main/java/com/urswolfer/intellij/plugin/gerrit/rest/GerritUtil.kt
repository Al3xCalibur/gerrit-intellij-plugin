/*
 * Copyright 2000-2011 JetBrains s.r.o.
 * Copyright 2013-2018 Urs Wolfer
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
package com.urswolfer.intellij.plugin.gerrit.rest

import com.google.common.base.*
import com.google.common.base.Function
import com.google.common.collect.*
import com.google.gerrit.extensions.api.GerritApi
import com.google.gerrit.extensions.api.changes.*
import com.google.gerrit.extensions.client.ListChangesOption
import com.google.gerrit.extensions.common.*
import com.google.gerrit.extensions.restapi.RestApiException
import com.google.gerrit.extensions.restapi.Url
import com.google.inject.Inject
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.Consumer
import com.urswolfer.gerrit.client.rest.GerritAuthData
import com.urswolfer.gerrit.client.rest.GerritRestApi
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory
import com.urswolfer.gerrit.client.rest.http.HttpStatusException
import com.urswolfer.intellij.plugin.gerrit.GerritSettings
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions
import com.urswolfer.intellij.plugin.gerrit.ui.LoginDialog
import com.urswolfer.intellij.plugin.gerrit.util.*
import git4idea.GitUtil
import git4idea.config.GitExecutableManager
import git4idea.config.GitVersion
import git4idea.i18n.GitBundle
import git4idea.repo.GitRemote
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.swing.event.HyperlinkEvent

/**
 * Parts based on org.jetbrains.plugins.github.GithubUtil
 *
 * @author Urs Wolfer
 * @author Konrad Dobrzynski
 */
class GerritUtil @Inject constructor(
    private val gerritSettings: GerritSettings,
    private val log: Logger,
    private val notificationService: NotificationService,
    private val gerritClient: GerritRestApi,
    private val gerritRestApiFactory: GerritRestApiFactory,
    private val certificateManagerClientBuilderExtension: CertificateManagerClientBuilderExtension,
    private val loggerHttpClientBuilderExtension: LoggerHttpClientBuilderExtension,
    private val proxyHttpClientBuilderExtension: ProxyHttpClientBuilderExtension,
    private val userAgentClientBuilderExtension: UserAgentClientBuilderExtension,
    private val selectedRevisions: SelectedRevisions
) {
    fun <T> accessToGerritWithModalProgress(
        project: Project?,
        computable: ThrowableComputable<T, Exception?>
    ): T {
        val result = AtomicReference<T>()
        val exception = AtomicReference<Exception?>()
        ProgressManager.getInstance().run(object : Task.Modal(project, "Access to Gerrit", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    result.set(computable.compute())
                } catch (e: Exception) {
                    exception.set(e)
                }
            }
        })
        if (exception.get() == null) {
            return result.get()
        }
        throw RuntimeException(exception.get())
    }

    fun postReview(
        changeId: String?,
        revision: String?,
        reviewInput: ReviewInput?,
        project: Project?,
        consumer: Consumer<Void?>
    ) {
        val supplier = Supplier<Void?> {
            try {
                gerritClient.changes().id(changeId).revision(revision).review(reviewInput)
                return@Supplier null
            } catch (e: RestApiException) {
                throw RuntimeException(e)
            }
        }
        accessGerrit(supplier, consumer, project, "Failed to post Gerrit review")
    }

    fun postSubmit(
        changeId: String?,
        submitInput: SubmitInput?,
        project: Project?,
        consumer: Consumer<Void?>
    ) {
        val supplier = Supplier<Void?> {
            try {
                gerritClient.changes().id(changeId).current().submit(submitInput)
                return@Supplier null
            } catch (e: RestApiException) {
                throw RuntimeException(e)
            }
        }
        accessGerrit(supplier, consumer, project, "Failed to submit Gerrit change")
    }

    fun postPublish(
        changeId: String?,
        project: Project?
    ) {
        val supplier = Supplier<Void?> {
            try {
                gerritClient.changes().id(changeId).publish()
                return@Supplier null
            } catch (e: RestApiException) {
                throw RuntimeException(e)
            }
        }
        accessGerrit(supplier, {}, project, "Failed to publish Gerrit change")
    }

    fun delete(
        changeId: String?,
        project: Project?
    ) {
        val supplier = Supplier<Void?> {
            try {
                gerritClient.changes().id(changeId).delete()
                return@Supplier null
            } catch (e: RestApiException) {
                throw RuntimeException(e)
            }
        }
        accessGerrit(supplier, {}, project, "Failed to delete Gerrit change")
    }

    fun postAbandon(
        changeId: String?,
        abandonInput: AbandonInput?,
        project: Project?
    ) {
        val supplier = Supplier<Void?> {
            try {
                gerritClient.changes().id(changeId).abandon(abandonInput)
                return@Supplier null
            } catch (e: RestApiException) {
                throw RuntimeException(e)
            }
        }
        accessGerrit(supplier, {}, project, "Failed to abandon Gerrit change")
    }

    fun addReviewer(
        changeId: String?,
        reviewerName: String?,
        project: Project?
    ) {
        val supplier = Supplier<Void?> {
            try {
                gerritClient.changes().id(changeId).addReviewer(reviewerName)
                return@Supplier null
            } catch (e: RestApiException) {
                throw RuntimeException(e)
            }
        }
        accessGerrit(supplier, {}, project, "Failed to add reviewer")
    }

    /**
     * Star-endpoint added in Gerrit 2.8.
     */
    fun changeStarredStatus(
        id: String?,
        starred: Boolean,
        project: Project?
    ) {
        val supplier = Supplier<Void?> {
            try {
                if (starred) {
                    gerritClient.accounts().self().starChange(id)
                } else {
                    gerritClient.accounts().self().unstarChange(id)
                }
                return@Supplier null
            } catch (e: RestApiException) {
                throw RuntimeException(e)
            }
        }
        accessGerrit(
            supplier, {}, project, "Failed to star Gerrit change " +
                    "(not supported for Gerrit versions older than 2.8)"
        )
    }

    fun setReviewed(
        changeNr: Int,
        revision: String?,
        filePath: String?,
        project: Project?
    ) {
        if (!gerritSettings.isLoginAndPasswordAvailable) {
            return
        }
        val supplier = Supplier<Void?> {
            try {
                gerritClient.changes().id(changeNr).revision(revision).setReviewed(filePath, true)
                return@Supplier null
            } catch (e: RestApiException) {
                throw RuntimeException(e)
            }
        }
        accessGerrit(supplier, {}, project, "Failed set file review status for Gerrit change")
    }

    fun getChangesToReview(project: Project?, consumer: Consumer<List<ChangeInfo>>) {
        val queryRequest = gerritClient.changes().query("is:open+reviewer:self")
            .withOption(ListChangesOption.DETAILED_ACCOUNTS)
        getChanges(queryRequest, project, consumer)
    }

    fun getChangesForProject(query: String?, project: Project?, consumer: Consumer<LoadChangesProxy>) {
        val fullQuery = if (!gerritSettings.listAllChanges) {
            appendQueryStringForProject(project, query)
        } else query
        getChanges(fullQuery, project, consumer)
    }

    fun getChanges(query: String?, project: Project?, consumer: Consumer<LoadChangesProxy>) {
        val supplier = Supplier {
            val queryRequest = gerritClient.changes().query(query)
                .withOptions(
                    EnumSet.of(
                        ListChangesOption.ALL_REVISIONS,
                        ListChangesOption.DETAILED_ACCOUNTS,
                        ListChangesOption.CHANGE_ACTIONS,
                        ListChangesOption.CURRENT_ACTIONS,
                        ListChangesOption.DETAILED_LABELS,
                        ListChangesOption.LABELS
                    )
                )
            LoadChangesProxy(queryRequest, this@GerritUtil, project)
        }
        accessGerrit(supplier, consumer, project)
    }

    fun getChanges(queryRequest: Changes.QueryRequest, project: Project?, consumer: Consumer<List<ChangeInfo>>) {
        val supplier = Supplier {
            try {
                return@Supplier queryRequest.get()
            } catch (e: RestApiException) {
                // remove special handling (-> just notify error) once we drop Gerrit < 2.9 support
                if (e is HttpStatusException) {
                    val httpStatusException = e
                    if (httpStatusException.statusCode == 400) {
                        var tryFallback = false
                        val message = httpStatusException.message
                        if (message!!.matches(".*Content:.*\"-S\".*".toRegex())) {
                            tryFallback = true
                            queryRequest.withStart(0) // remove start, trust that sortkey is set
                        }
                        if (message.matches(".*Content:.*\"(CHANGE_ACTIONS|CURRENT_ACTIONS)\".*\"-o\".*".toRegex())) {
                            tryFallback = true
                            val options = queryRequest.options
                            options.remove(ListChangesOption.CHANGE_ACTIONS)
                            options.remove(ListChangesOption.CURRENT_ACTIONS)
                            queryRequest.withOptions(options)
                        }
                        if (tryFallback) {
                            try {
                                return@Supplier queryRequest.get()
                            } catch (ex: RestApiException) {
                                notifyError(ex, "Failed to get Gerrit changes.", project)
                                return@Supplier emptyList<ChangeInfo>()
                            }
                        }
                    }
                }
                notifyError(e, "Failed to get Gerrit changes.", project)
                return@Supplier emptyList<ChangeInfo>()
            }
        }
        accessGerrit(supplier, consumer, project)
    }

    private fun appendQueryStringForProject(project: Project?, query: String?): String? {
        var query = query
        val projectQueryPart = getProjectQueryPart(project)
        query = Joiner.on('+').skipNulls().join(Strings.emptyToNull(query), Strings.emptyToNull(projectQueryPart))
        return query
    }

    private fun getProjectQueryPart(project: Project?): String {
        val repositories = GitUtil.getRepositoryManager(project!!).repositories
        if (repositories.isEmpty()) {
            showAddGitRepositoryNotification(project)
            return ""
        }

        val remotes: MutableList<GitRemote> = Lists.newArrayList()
        for (repository in repositories) {
            remotes.addAll(repository.remotes)
        }
        val projectNames = getProjectNames(remotes)
        val projectNamesWithQueryPrefix = projectNames.map { "project:" + Url.encode(it) }

        if (projectNamesWithQueryPrefix.isEmpty()) {
            return ""
        }
        return projectNamesWithQueryPrefix.joinToString("+OR+", "(", ")")
    }

    fun getProjectNames(remotes: Collection<GitRemote>): List<String?> {
        val projectNames: MutableList<String?> = Lists.newArrayList()
        for (remote in remotes) {
            for (remoteUrl in remote.urls) {
                val strippedRemoteUrl = UrlUtils.stripGitExtension(remoteUrl)
                val projectName = getProjectName(
                    gerritSettings.host, gerritSettings.cloneBaseUrl,
                    strippedRemoteUrl
                )
                if (!Strings.isNullOrEmpty(projectName) && strippedRemoteUrl.endsWith(projectName)) {
                    projectNames.add(projectName)
                }
            }
        }
        return projectNames
    }

    private fun getProjectName(gerritUrl: String, gerritCloneBaseUrl: String, url: String): String {
        var baseUrl = if (Strings.isNullOrEmpty(gerritCloneBaseUrl)) gerritUrl else gerritCloneBaseUrl
        if (!baseUrl.endsWith("/")) {
            baseUrl = "$baseUrl/"
        }

        val basePath = UrlUtils.createUriFromGitConfigString(baseUrl).path
        var path = UrlUtils.createUriFromGitConfigString(url).path

        if (path.length >= basePath.length && path.startsWith(basePath)) {
            path = path.substring(basePath.length)
        }

        path = UrlUtils.stripGitExtension(path)

        if (path.endsWith("/")) {
            path = path.substring(0, path.length - 1)
        }
        // gerrit project names usually don't start with a slash
        if (path.startsWith("/")) {
            path = path.substring(1)
        }

        return path
    }

    fun showAddGitRepositoryNotification(project: Project?) {
        val notification = NotificationBuilder(
            project, "Insufficient dependencies for Gerrit plugin",
            "Please configure a Git repository.<br/><a href='vcs'>Open Settings</a>"
        )
            .listener { notification, event ->
                if (event.eventType == HyperlinkEvent.EventType.ACTIVATED && event.description == "vcs") {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, ActionsBundle.message("group.VcsGroup.text"))
                }
            }
        notificationService.notifyWarning(notification)
    }

    fun getChangeDetails(changeNr: Int, project: Project?, consumer: Consumer<ChangeInfo>) {
        val supplier = Supplier {
            try {
                val options = EnumSet.of(
                    ListChangesOption.ALL_REVISIONS,
                    ListChangesOption.MESSAGES,
                    ListChangesOption.DETAILED_ACCOUNTS,
                    ListChangesOption.LABELS,
                    ListChangesOption.DETAILED_LABELS
                )
                try {
                    return@Supplier gerritClient.changes().id(changeNr)[options]
                } catch (e: HttpStatusException) {
                    // remove special handling (-> just notify error) once we drop Gerrit < 2.7 support
                    if (e.statusCode == 400) {
                        options.remove(ListChangesOption.MESSAGES)
                        return@Supplier gerritClient.changes().id(changeNr)[options]
                    } else {
                        throw e
                    }
                }
            } catch (e: RestApiException) {
                notifyError(e, "Failed to get Gerrit change.", project)
                return@Supplier ChangeInfo()
            }
        }
        accessGerrit(supplier, consumer, project)
    }

    /**
     * Support starting from Gerrit 2.7.
     */
    fun getComments(
        changeNr: Int,
        revision: String?,
        project: Project?,
        includePublishedComments: Boolean,
        includeDraftComments: Boolean,
        consumer: Consumer<Map<String, List<CommentInfo>>>
    ) {
        val supplier = Supplier<Map<String, List<CommentInfo>>> {
            try {
                val comments = if (includePublishedComments) {
                    gerritClient.changes().id(changeNr).revision(revision).comments()
                } else {
                    Maps.newHashMap()
                }
                val drafts = if (includeDraftComments && gerritSettings.isLoginAndPasswordAvailable) {
                    gerritClient.changes().id(changeNr).revision(revision).drafts()
                } else {
                    Maps.newHashMap()
                }

                val allComments = HashMap(drafts)
                for ((key, value) in comments) {
                    val commentInfos = allComments.getOrPut(key) { mutableListOf() }
                    commentInfos.addAll(value)
                }
                return@Supplier allComments
            } catch (e: RestApiException) {
                // remove check once we drop Gerrit < 2.7 support and fail in any case
                if (e !is HttpStatusException || e.statusCode != 404) {
                    notifyError(e, "Failed to get Gerrit comments.", project)
                }
                return@Supplier TreeMap<String, MutableList<CommentInfo>>()
            }
        }
        accessGerrit(supplier, consumer, project)
    }

    fun saveDraftComment(
        changeNr: Int,
        revision: String?,
        draftInput: DraftInput,
        project: Project?,
        consumer: Consumer<CommentInfo>
    ) {
        val supplier = Supplier {
            try {
                val commentInfo: CommentInfo
                if (draftInput.id != null) {
                    commentInfo = gerritClient.changes().id(changeNr).revision(revision)
                        .draft(draftInput.id).update(draftInput)
                } else {
                    val draftApi = gerritClient.changes().id(changeNr).revision(revision)
                        .createDraft(draftInput)
                    commentInfo = draftApi.get()
                }
                return@Supplier commentInfo
            } catch (e: RestApiException) {
                throw RuntimeException(e)
            }
        }
        accessGerrit(supplier, consumer, project, "Failed to save draft comment")
    }

    fun deleteDraftComment(
        changeNr: Int,
        revision: String?,
        draftCommentId: String?,
        project: Project?,
        consumer: Consumer<Void>
    ) {
        val supplier = Supplier<Void> {
            try {
                gerritClient.changes().id(changeNr).revision(revision).draft(draftCommentId).delete()
                return@Supplier null
            } catch (e: RestApiException) {
                throw RuntimeException(e)
            }
        }
        accessGerrit(supplier, consumer, project, "Failed to delete draft comment")
    }

    @Throws(RestApiException::class)
    private fun testConnection(gerritAuthData: GerritAuthData?): Boolean {
        // we need to test with a temporary client with probably new (unsaved) credentials
        val tempClient = createClientWithCustomAuthData(gerritAuthData)
        val query = tempClient.changes().query()
        if (gerritAuthData!!.isLoginAndPasswordAvailable) {
            query.withQuery("reviewer:self")
        }
        query.withLimit(1).get()
        return true
    }

    /**
     * Checks if user has set up correct user credentials for access in the settings.
     *
     * @return true if we could successfully login with these credentials, false if authentication failed or in the case of some other error.
     */
    fun checkCredentials(project: Project?): Boolean {
        try {
            return checkCredentials(project, gerritSettings)
        } catch (e: Exception) {
            // this method is a quick-check if we've got valid user setup.
            // if an exception happens, we'll show the reason in the login dialog that will be shown right after checkCredentials failure.
            log.info(e)
            return false
        }
    }

    fun checkCredentials(project: Project?, gerritAuthData: GerritAuthData): Boolean {
        if (Strings.isNullOrEmpty(gerritAuthData.host)) {
            return false
        }
        val result = accessToGerritWithModalProgress(project) {
            ProgressManager.getInstance().progressIndicator.text = "Trying to login to Gerrit"
            testConnection(gerritAuthData)
        }
        return result ?: false
    }

    /**
     * Shows Gerrit login settings if credentials are wrong or empty and return the list of all projects
     */
    fun getAvailableProjects(project: Project?): List<ProjectInfo>? {
        while (!checkCredentials(project)) {
            val dialog = LoginDialog(project, gerritSettings, this, log)
            dialog.show()
            if (!dialog.isOK) {
                return null
            }
        }
        // Otherwise our credentials are valid and they are successfully stored in settings
        return accessToGerritWithModalProgress(project) {
            ProgressManager.getInstance().progressIndicator.text = "Extracting info about available repositories"
            gerritClient.projects().list().get()
        }
    }

    fun getFirstFetchInfo(changeDetails: ChangeInfo?): FetchInfo? {
        if (changeDetails!!.revisions == null) {
            return null
        }
        val revisionInfo = changeDetails.revisions[selectedRevisions[changeDetails]]
        return getFirstFetchInfo(revisionInfo)
    }

    fun getFirstFetchInfo(revisionInfo: RevisionInfo?): FetchInfo? {
        if (revisionInfo == null) {
            return null
        }
        return Iterables.getFirst(revisionInfo.fetch.values, null)
    }

    fun testGitExecutable(project: Project?): Boolean {
        val version: GitVersion
        try {
            version = GitExecutableManager.getInstance().getVersion(project!!)
        } catch (e: Exception) {
            Messages.showErrorDialog(project, e.message, GitBundle.message("find.git.error.title"))
            return false
        }

        if (!version.isSupported) {
            Messages.showWarningDialog(
                project, GitBundle.message("find.git.unsupported.message", version.toString(), GitVersion.MIN),
                GitBundle.message("find.git.success.title")
            )
            return false
        }
        return true
    }

    fun getErrorTextFromException(t: Throwable): String {
        var message = t.message
        if (message == null) {
            message = "(No exception message available)"
            log.error(message, t)
        }
        return message
    }

    private fun <T> accessGerrit(supplier: Supplier<T>, consumer: Consumer<T>, project: Project?) {
        accessGerrit(supplier, consumer, project, null)
    }

    /**
     * @param errorMessage if the provided supplier throws an exception, this error message is displayed (if it is not null)
     * and the provided consumer will not be executed.
     */
    private fun <T> accessGerrit(
        supplier: Supplier<T>,
        consumer: Consumer<T>,
        project: Project?,
        errorMessage: String?
    ) {
        ApplicationManager.getApplication().invokeLater(Runnable {
            if (project!!.isDisposed) {
                return@Runnable
            }
            val backgroundTask: Backgroundable = object : Backgroundable(project, "Accessing Gerrit", true) {
                override fun run(indicator: ProgressIndicator) {
                    if (project.isDisposed) {
                        return
                    }
                    try {
                        val result = supplier.get()
                        ApplicationManager.getApplication().invokeLater(Runnable {
                            if (project.isDisposed) {
                                return@Runnable
                            }
                            consumer.consume(result)
                        })
                    } catch (e: RuntimeException) {
                        if (errorMessage != null) {
                            notifyError(e, errorMessage, project)
                        } else {
                            throw e
                        }
                    }
                }
            }
            gerritSettings.preloadPassword()
            backgroundTask.queue()
        })
    }

    private fun notifyError(throwable: Throwable, errorMessage: String, project: Project?) {
        val notification = NotificationBuilder(project, errorMessage, getErrorTextFromException(throwable))
        notificationService.notifyError(notification)
    }

    private fun createClientWithCustomAuthData(gerritAuthData: GerritAuthData?): GerritApi {
        return gerritRestApiFactory.create(
            gerritAuthData,
            certificateManagerClientBuilderExtension,
            loggerHttpClientBuilderExtension,
            proxyHttpClientBuilderExtension,
            userAgentClientBuilderExtension
        )
    }
}
